package com.garfiec.librechat.feature.files.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.feature.files.resources.*
import com.garfiec.librechat.feature.files.resources.Res
import com.garfiec.librechat.feature.files.viewmodel.FileSortField
import com.garfiec.librechat.feature.files.viewmodel.FileSortOrder
import org.jetbrains.compose.resources.stringResource

@Composable
fun FileSortMenu(
    expanded: Boolean,
    currentSortField: FileSortField,
    currentSortOrder: FileSortOrder,
    onSortSelect: (FileSortField, FileSortOrder) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        FileSortField.entries.forEach { field ->
            val isSelected = field == currentSortField
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = field.label,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                onClick = {
                    if (isSelected) {
                        val newOrder = if (currentSortOrder == FileSortOrder.ASCENDING) {
                            FileSortOrder.DESCENDING
                        } else {
                            FileSortOrder.ASCENDING
                        }
                        onSortSelect(field, newOrder)
                    } else {
                        onSortSelect(field, FileSortOrder.DESCENDING)
                    }
                    onDismiss()
                },
                trailingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = if (currentSortOrder == FileSortOrder.ASCENDING) {
                                Icons.Default.ArrowUpward
                            } else {
                                Icons.Default.ArrowDownward
                            },
                            contentDescription = if (currentSortOrder == FileSortOrder.ASCENDING) {
                                stringResource(Res.string.cd_ascending)
                            } else {
                                stringResource(Res.string.cd_descending)
                            },
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}
