package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.network.api.RetryPolicyDto
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.clear
import com.garfiec.librechat.feature.chat.resources.conversation_instructions
import com.garfiec.librechat.feature.chat.resources.conversation_instructions_hint
import com.garfiec.librechat.feature.chat.resources.save
import com.garfiec.librechat.feature.chat.resources.auto_retry
import com.garfiec.librechat.feature.chat.resources.auto_retry_delay
import com.garfiec.librechat.feature.chat.resources.auto_retry_forever
import com.garfiec.librechat.feature.chat.resources.auto_retry_limit
import com.garfiec.librechat.feature.chat.resources.auto_retry_prompt
import org.jetbrains.compose.resources.stringResource

private const val MAX_INSTRUCTIONS_CHARS = 32_000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationInstructionsSheet(
    initialValue: String,
    initialRetryPolicy: RetryPolicyDto,
    onDismiss: () -> Unit,
    onSave: (String, RetryPolicyDto) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    var retryEnabled by remember(initialRetryPolicy) { mutableStateOf(initialRetryPolicy.enabled) }
    var retryForever by remember(initialRetryPolicy) { mutableStateOf(initialRetryPolicy.untilSuccess) }
    var retryLimit by remember(initialRetryPolicy) { mutableStateOf(initialRetryPolicy.maxRetries.toString()) }
    var retryDelay by remember(initialRetryPolicy) { mutableStateOf(initialRetryPolicy.delaySeconds.toString()) }
    var retryPrompt by remember(initialRetryPolicy) { mutableStateOf(initialRetryPolicy.retryPrompt) }

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
            Text(text = stringResource(Res.string.conversation_instructions))
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(MAX_INSTRUCTIONS_CHARS) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp),
                label = { Text(stringResource(Res.string.conversation_instructions_hint)) },
                minLines = 7,
                supportingText = { Text("${value.length} / $MAX_INSTRUCTIONS_CHARS") },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(Res.string.auto_retry),
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = retryEnabled, onCheckedChange = { retryEnabled = it })
            }
            if (retryEnabled) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = retryForever, onCheckedChange = { retryForever = it })
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
                        value = retryLimit,
                        onValueChange = { retryLimit = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.weight(1f),
                        enabled = !retryForever,
                        label = { Text(stringResource(Res.string.auto_retry_limit)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = retryDelay,
                        onValueChange = { retryDelay = it.filter(Char::isDigit).take(3) },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(Res.string.auto_retry_delay)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = retryPrompt,
                    onValueChange = { retryPrompt = it.take(4_000) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.auto_retry_prompt)) },
                    minLines = 3,
                    supportingText = { Text("${retryPrompt.length} / 4000") },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { value = "" }) {
                    Text(stringResource(Res.string.clear))
                }
                TextButton(
                    onClick = {
                        onSave(
                            value,
                            initialRetryPolicy.copy(
                                enabled = retryEnabled,
                                maxRetries = retryLimit.toIntOrNull()?.coerceIn(1, 20) ?: 3,
                                untilSuccess = retryForever,
                                delaySeconds = retryDelay.toIntOrNull()?.coerceIn(1, 300) ?: 5,
                                retryPrompt = retryPrompt.trim().ifEmpty { initialRetryPolicy.retryPrompt },
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
