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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.clear
import com.garfiec.librechat.feature.chat.resources.conversation_instructions
import com.garfiec.librechat.feature.chat.resources.conversation_instructions_hint
import com.garfiec.librechat.feature.chat.resources.save
import org.jetbrains.compose.resources.stringResource

private const val MAX_INSTRUCTIONS_CHARS = 32_000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationInstructionsSheet(
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }

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
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { value = "" }) {
                    Text(stringResource(Res.string.clear))
                }
                TextButton(
                    onClick = {
                        onSave(value)
                        onDismiss()
                    },
                ) {
                    Text(stringResource(Res.string.save))
                }
            }
        }
    }
}
