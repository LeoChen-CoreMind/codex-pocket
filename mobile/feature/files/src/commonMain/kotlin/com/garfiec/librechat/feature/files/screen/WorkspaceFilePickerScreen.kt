package com.garfiec.librechat.feature.files.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.FileObject
import com.garfiec.librechat.core.network.api.WorkspaceEntryDto
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.feature.files.platform.formatFileSize
import com.garfiec.librechat.feature.files.viewmodel.WorkspaceFilesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceFilePickerScreen(
    onBack: (() -> Unit)?,
    onConfirmSelection: (List<FileObject>) -> Unit,
    viewModel: WorkspaceFilesViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var rootsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it) }
        viewModel.dismissError()
    }
    PlatformBackHandler(enabled = state.path.isNotBlank()) { viewModel.up() }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (!viewModel.up()) onBack?.invoke() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Column {
                        Row(
                            modifier = Modifier.clickable(enabled = state.roots.size > 1) { rootsExpanded = true },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(state.root?.name ?: "电脑文件", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (state.roots.size > 1) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "切换目录",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            DropdownMenu(expanded = rootsExpanded, onDismissRequest = { rootsExpanded = false }) {
                                state.roots.forEach { root ->
                                    DropdownMenuItem(
                                        text = { Text(root.name) },
                                        onClick = {
                                            rootsExpanded = false
                                            viewModel.selectRoot(root)
                                        },
                                    )
                                }
                            }
                        }
                        Text(
                            state.path.ifBlank { state.root?.path.orEmpty() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.importSelected(onConfirmSelection) },
                        enabled = state.selectedFiles.isNotEmpty() && !state.isImporting,
                    ) {
                        if (state.isImporting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("添加 ${state.selectedFiles.size}")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            state.entries.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("当前目录没有文件")
            }

            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(state.entries, key = { it.path }) { entry ->
                    WorkspacePickerEntryRow(
                        entry = entry,
                        selected = viewModel.isSelected(entry),
                        onClick = {
                            if (entry.isDirectory) viewModel.open(entry) else viewModel.toggleSelection(entry)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspacePickerEntryRow(
    entry: WorkspaceEntryDto,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!entry.isDirectory) {
                Text(
                    entry.size?.let(::formatFileSize).orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!entry.isDirectory) {
            Checkbox(selected, onCheckedChange = { onClick() })
        }
    }
}
