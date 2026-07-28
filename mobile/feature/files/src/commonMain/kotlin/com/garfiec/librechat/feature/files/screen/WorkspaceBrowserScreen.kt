package com.garfiec.librechat.feature.files.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.network.api.WorkspaceEntryDto
import com.garfiec.librechat.feature.files.platform.formatFileSize
import com.garfiec.librechat.feature.files.platform.rememberWorkspaceFileSaver
import com.garfiec.librechat.feature.files.viewmodel.WorkspaceFilesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspaceBrowserScreen(
    onBack: (() -> Unit)?,
    viewModel: WorkspaceFilesViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val saveFile = rememberWorkspaceFileSaver()
    var rootsExpanded by remember { mutableStateOf(false) }
    var showFtpDialog by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it) }
        viewModel.dismissError()
    }

    LaunchedEffect(Unit) {
        viewModel.downloads.collect { download ->
            saveFile(download.filename, download.mimeType, download.bytes)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(
                            modifier = Modifier.clickable(enabled = state.roots.size > 1) { rootsExpanded = true },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(state.root?.name ?: "项目文件", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (state.roots.size > 1) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "切换项目", modifier = Modifier.size(18.dp))
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
                            text = state.path.ifBlank { state.root?.path.orEmpty() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!viewModel.up()) onBack?.invoke()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.loadFtpStatus()
                        showFtpDialog = true
                    }) {
                        Icon(Icons.Default.Dns, contentDescription = "FTP 管理")
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading && state.entries.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }

            state.root == null -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(40.dp))
                Text("请先绑定一个在线 VS Code 项目", modifier = Modifier.padding(top = 12.dp))
            }

            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(state.entries, key = { it.path }) { entry ->
                    WorkspaceEntryRow(
                        entry = entry,
                        onClick = { viewModel.open(entry) },
                        onDownload = { viewModel.download(entry) },
                        downloadProgress = state.downloadProgress[entry.path],
                        isDownloading = state.downloadProgress.containsKey(entry.path),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    state.preview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::closePreview,
            title = { Text(preview.path.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                SelectionContainer {
                    Text(
                        text = if (preview.binary) "该文件是二进制文件，可下载到手机后查看。" else preview.content.orEmpty(),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::downloadPreview) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("下载")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::closePreview) { Text("关闭") } },
        )
    }

    if (showFtpDialog) {
        val ftp = state.ftp
        val configText = ftp?.takeIf { it.running }?.let {
            "协议: FTP\n主机: ${it.host}\n端口: ${it.port}\n账号: ${it.username}\n密码: ${it.password}"
        }.orEmpty()
        AlertDialog(
            onDismissRequest = { showFtpDialog = false },
            title = { Text("MT 管理器 FTP 配置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("在 MT 管理器中新增 FTP 连接，并输入以下配置。")
                    when {
                        state.isFtpLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        ftp?.running == true -> {
                            SelectionContainer { Text(configText, fontFamily = FontFamily.Monospace) }
                            ftp.root?.let { Text("工作区: $it", style = MaterialTheme.typography.bodySmall) }
                        }
                        else -> Text("FTP 服务未开启。")
                    }
                }
            },
            confirmButton = {
                if (ftp?.running == true) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(configText)) }) { Text("复制配置") }
                } else {
                    TextButton(onClick = viewModel::startFtp, enabled = !state.isFtpLoading) { Text("开启 FTP") }
                }
            },
            dismissButton = {
                if (ftp?.running == true) {
                    TextButton(onClick = viewModel::stopFtp, enabled = !state.isFtpLoading) { Text("停止") }
                } else {
                    TextButton(onClick = { showFtpDialog = false }) { Text("关闭") }
                }
            },
        )
    }
}

@Composable
private fun WorkspaceEntryRow(
    entry: WorkspaceEntryDto,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    downloadProgress: Float?,
    isDownloading: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
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
                    text = entry.size?.let(::formatFileSize).orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isDownloading) {
                    if (downloadProgress != null) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                }
            }
        }
        if (!entry.isDirectory) {
            IconButton(onClick = onDownload, enabled = !isDownloading) {
                Icon(Icons.Default.Download, contentDescription = "下载 ${entry.name}")
            }
        }
    }
}
