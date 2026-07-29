package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.network.api.DEFAULT_RETRY_PROMPT
import com.garfiec.librechat.core.network.api.RetryPolicyDto
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.auto_retry
import com.garfiec.librechat.feature.chat.resources.auto_retry_delay
import com.garfiec.librechat.feature.chat.resources.auto_retry_forever
import com.garfiec.librechat.feature.chat.resources.auto_retry_limit
import com.garfiec.librechat.feature.chat.resources.auto_retry_prompt
import com.garfiec.librechat.feature.chat.resources.save
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AutomaticRetrySettingsSheet(
    initialPolicy: RetryPolicyDto,
    onDismiss: () -> Unit,
    onSave: (RetryPolicyDto) -> Unit,
) {
    var enabled by remember(initialPolicy) { mutableStateOf(initialPolicy.enabled) }
    var forever by remember(initialPolicy) { mutableStateOf(initialPolicy.untilSuccess) }
    var limit by remember(initialPolicy) { mutableStateOf(initialPolicy.maxRetries.toString()) }
    var delay by remember(initialPolicy) { mutableStateOf(initialPolicy.delaySeconds.toString()) }
    var prompt by remember(initialPolicy) { mutableStateOf(initialPolicy.retryPrompt) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = stringResource(Res.string.auto_retry), modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            if (enabled) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = forever, onCheckedChange = { forever = it })
                    Text(
                        text = stringResource(Res.string.auto_retry_forever),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = limit,
                        onValueChange = { limit = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.weight(1f),
                        enabled = !forever,
                        label = { Text(stringResource(Res.string.auto_retry_limit)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = delay,
                        onValueChange = { delay = it.filter(Char::isDigit).take(3) },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(Res.string.auto_retry_delay)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it.take(4_000) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.auto_retry_prompt)) },
                    minLines = 3,
                    supportingText = { Text("${prompt.length} / 4000") },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = {
                        onSave(
                            initialPolicy.copy(
                                enabled = enabled,
                                maxRetries = limit.toIntOrNull()?.coerceIn(1, 20) ?: 3,
                                untilSuccess = forever,
                                delaySeconds = delay.toIntOrNull()?.coerceIn(1, 300) ?: 5,
                                retryPrompt = prompt.trim().ifEmpty { DEFAULT_RETRY_PROMPT },
                            ),
                        )
                        onDismiss()
                    },
                ) {
                    Text(stringResource(Res.string.save))
                }
            }
        }
    }
}
