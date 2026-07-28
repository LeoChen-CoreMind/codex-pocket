package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.network.api.McpDialogApi
import com.garfiec.librechat.core.network.api.McpDialogConfigDto
import com.garfiec.librechat.feature.settings.util.copyToClipboard
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun McpDialogSettingsButton(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Button(
        onClick = { open = true },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Default.Chat, contentDescription = null)
        Text("MCP对话", modifier = Modifier.padding(start = 8.dp))
    }
    if (open) McpDialogSettingsDialog(onDismiss = { open = false })
}

@Composable
private fun McpDialogSettingsDialog(
    onDismiss: () -> Unit,
    api: McpDialogApi = koinInject(),
) {
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<McpDialogConfigDto?>(null) }
    var enabled by remember { mutableStateOf(false) }
    var portText by remember { mutableStateOf("47832") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { api.config() }
            .onSuccess {
                config = it
                enabled = it.enabled
                portText = it.port.toString()
            }
            .onFailure { error = it.message }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("MCP对话") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("开启局域网 MCP 服务", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (config?.running == true) "正在监听" else "未监听",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it }, enabled = !saving)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = portText,
                    onValueChange = { value -> portText = value.filter(Char::isDigit).take(5) },
                    label = { Text("端口") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !saving,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("局域网地址", style = MaterialTheme.typography.titleSmall)
                val currentPort = portText.toIntOrNull() ?: config?.port ?: 47832
                val addresses = config?.addresses.orEmpty()
                if (addresses.isEmpty()) {
                    Text("未检测到局域网 IPv4 地址", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    addresses.forEach { address ->
                        Text("http://$address:$currentPort/mcp", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("在 AI 客户端中添加 Streamable HTTP MCP，并设置提示词。服务使用 Bridge 密钥鉴权。")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { config?.url?.let { copyToClipboard(it, "MCP URL") } },
                        enabled = config?.url != null,
                        modifier = Modifier.weight(1f),
                    ) { Text("复制地址") }
                    OutlinedButton(
                        onClick = { config?.prompt?.let { copyToClipboard(it, "MCP Prompt") } },
                        enabled = !config?.prompt.isNullOrBlank(),
                        modifier = Modifier.weight(1f),
                    ) { Text("复制提示词") }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    val port = portText.toIntOrNull()
                    if (port == null || port !in 1024..65535) {
                        error = "端口必须在 1024 到 65535 之间"
                        return@TextButton
                    }
                    saving = true
                    error = null
                    scope.launch {
                        runCatching { api.configure(enabled, port) }
                            .onSuccess { updated ->
                                config = updated
                                enabled = updated.enabled
                                portText = updated.port.toString()
                            }
                            .onFailure { error = it.message }
                        saving = false
                    }
                },
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                else Text("应用")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("关闭") } },
    )
}
