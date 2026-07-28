package com.garfiec.librechat.feature.files.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.model.FileObject
import com.garfiec.librechat.core.network.api.VsCodeApi
import com.garfiec.librechat.core.network.api.WorkspaceEntryDto
import com.garfiec.librechat.core.network.api.WorkspaceFileResponse
import com.garfiec.librechat.core.network.api.WorkspaceRootDto
import com.garfiec.librechat.core.network.api.FtpStatusDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class WorkspaceFilesUiState(
    val roots: List<WorkspaceRootDto> = emptyList(),
    val root: WorkspaceRootDto? = null,
    val path: String = "",
    val entries: List<WorkspaceEntryDto> = emptyList(),
    val preview: WorkspaceFileResponse? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val ftp: FtpStatusDto? = null,
    val isFtpLoading: Boolean = false,
    val downloadProgress: Map<String, Float?> = emptyMap(),
    val selectedFiles: Map<String, SelectedWorkspaceFile> = emptyMap(),
    val isImporting: Boolean = false,
)

data class SelectedWorkspaceFile(
    val rootId: String,
    val entry: WorkspaceEntryDto,
)

data class WorkspaceDownload(
    val filename: String,
    val mimeType: String,
    val bytes: ByteArray,
)

class WorkspaceFilesViewModel(private val api: VsCodeApi) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkspaceFilesUiState())
    val uiState: StateFlow<WorkspaceFilesUiState> = _uiState.asStateFlow()
    private val _downloads = MutableSharedFlow<WorkspaceDownload>()
    val downloads: SharedFlow<WorkspaceDownload> = _downloads

    init {
        refreshRoots()
    }

    fun refreshRoots() {
        viewModelScope.launch {
            runCatching { api.getWorkspaceRoots().data }
                .onSuccess { roots ->
                    val selected = roots.firstOrNull { it.id == _uiState.value.root?.id } ?: roots.firstOrNull()
                    _uiState.update { it.copy(roots = roots, root = selected, error = null) }
                    if (selected != null) loadDirectory(selected, _uiState.value.path)
                    else _uiState.update { it.copy(isLoading = false, entries = emptyList()) }
                }
                .onFailure { error -> _uiState.update { it.copy(isLoading = false, error = error.message) } }
        }
    }

    fun selectRoot(root: WorkspaceRootDto) {
        _uiState.update { it.copy(root = root, path = "", preview = null) }
        loadDirectory(root, "")
    }

    fun toggleSelection(entry: WorkspaceEntryDto) {
        if (entry.isDirectory) return
        val root = _uiState.value.root ?: return
        val key = "${root.id}:${entry.path}"
        _uiState.update { state ->
            val selected = state.selectedFiles
            when {
                key in selected -> state.copy(selectedFiles = selected - key)
                selected.size >= MAX_ATTACHMENTS -> state.copy(error = "一次最多选择 $MAX_ATTACHMENTS 个文件")
                else -> state.copy(
                    selectedFiles = selected + (key to SelectedWorkspaceFile(root.id, entry)),
                    error = null,
                )
            }
        }
    }

    fun isSelected(entry: WorkspaceEntryDto): Boolean {
        val root = _uiState.value.root ?: return false
        return "${root.id}:${entry.path}" in _uiState.value.selectedFiles
    }

    fun importSelected(onSuccess: (List<FileObject>) -> Unit) {
        val selected = _uiState.value.selectedFiles.values.toList()
        if (selected.isEmpty() || _uiState.value.isImporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null) }
            runCatching {
                selected.map { file -> api.importWorkspaceFile(file.rootId, file.entry.path) }
            }.onSuccess { files ->
                _uiState.update { it.copy(isImporting = false, selectedFiles = emptyMap()) }
                onSuccess(files)
            }.onFailure { error ->
                _uiState.update { it.copy(isImporting = false, error = error.message) }
            }
        }
    }

    fun open(entry: WorkspaceEntryDto) {
        val root = _uiState.value.root ?: return
        if (entry.isDirectory) loadDirectory(root, entry.path) else {
            viewModelScope.launch {
                runCatching { api.getWorkspaceFile(root.id, entry.path) }
                    .onSuccess { preview -> _uiState.update { it.copy(preview = preview, error = null) } }
                    .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
            }
        }
    }

    fun download(entry: WorkspaceEntryDto) {
        if (entry.isDirectory) return
        val root = _uiState.value.root ?: return
        if (_uiState.value.downloadProgress.containsKey(entry.path)) return
        viewModelScope.launch {
            _uiState.update { it.copy(downloadProgress = it.downloadProgress + (entry.path to null)) }
            runCatching {
                api.downloadWorkspaceFile(root.id, entry.path) { received, total ->
                    val progress = total?.takeIf { it > 0L }
                        ?.let { (received.toFloat() / it.toFloat()).coerceIn(0f, 1f) }
                    _uiState.update { state ->
                        state.copy(downloadProgress = state.downloadProgress + (entry.path to progress))
                    }
                }
            }
                .onSuccess { bytes ->
                    _uiState.update { it.copy(downloadProgress = it.downloadProgress - entry.path) }
                    _downloads.emit(
                        WorkspaceDownload(
                            filename = entry.name,
                            mimeType = entry.type.ifBlank { "application/octet-stream" },
                            bytes = bytes,
                        ),
                    )
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = error.message, downloadProgress = it.downloadProgress - entry.path)
                    }
                }
        }
    }

    fun downloadPreview() {
        val preview = _uiState.value.preview ?: return
        download(
            WorkspaceEntryDto(
                name = preview.path.substringAfterLast('/'),
                path = preview.path,
                isDirectory = false,
                size = preview.size,
                type = preview.type,
            ),
        )
        closePreview()
    }

    fun up(): Boolean {
        val state = _uiState.value
        val root = state.root ?: return false
        if (state.path.isBlank()) return false
        loadDirectory(root, state.path.substringBeforeLast('/', ""))
        return true
    }

    fun refresh() {
        val state = _uiState.value
        val root = state.root ?: return refreshRoots()
        loadDirectory(root, state.path)
    }

    fun closePreview() = _uiState.update { it.copy(preview = null) }

    fun openPreviewOnDesktop() {
        val state = _uiState.value
        val root = state.root ?: return
        val preview = state.preview ?: return
        viewModelScope.launch {
            runCatching { api.openWorkspaceFile(root.id, preview.path) }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    fun loadFtpStatus() = updateFtp { api.getFtpStatus() }

    fun startFtp() = updateFtp { api.startFtp() }

    fun stopFtp() = updateFtp { api.stopFtp() }

    private fun updateFtp(block: suspend () -> FtpStatusDto) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFtpLoading = true, error = null) }
            runCatching { block() }
                .onSuccess { ftp -> _uiState.update { it.copy(ftp = ftp, isFtpLoading = false) } }
                .onFailure { error -> _uiState.update { it.copy(isFtpLoading = false, error = error.message) } }
        }
    }

    private fun loadDirectory(root: WorkspaceRootDto, path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { api.getWorkspaceEntries(root.id, path) }
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(root = response.root, path = response.path, entries = response.data, isLoading = false)
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(isLoading = false, error = error.message) } }
        }
    }

    private companion object {
        const val MAX_ATTACHMENTS = 10
    }
}
