package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.feature.chat.model.PromptMentionDisplayData

/**
 * Dropdown menu showing slash command suggestions.
 * Appears when the user types "/" at position 0 in the input field.
 * Filters by the [PromptMentionDisplayData.command] field.
 */
@Composable
fun SlashCommandMenu(
    filteredCommands: List<PromptMentionDisplayData>,
    onCommandSelect: (PromptMentionDisplayData) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = filteredCommands.isNotEmpty(),
        onDismissRequest = { /* Dismissed by typing or selecting */ },
        modifier = modifier.heightIn(max = 240.dp),
    ) {
        filteredCommands.forEach { group ->
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = "/${group.command ?: group.name}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val oneliner = group.oneliner
                        if (!oneliner.isNullOrBlank()) {
                            Text(
                                text = oneliner,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                onClick = { onCommandSelect(group) },
            )
        }
    }
}
