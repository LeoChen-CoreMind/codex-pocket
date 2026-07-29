package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.model.usage.ContextUsage
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.action_search
import com.garfiec.librechat.feature.chat.resources.action_share
import com.garfiec.librechat.feature.chat.resources.auto_retry
import com.garfiec.librechat.feature.chat.resources.conversation_instructions
import org.jetbrains.compose.resources.stringResource

/**
 * The chat top bar's overflow menu, shared by the Android and iOS floating top bars so the items,
 * ordering, gating, and icons stay identical across platforms. Each action dismisses the menu
 * before running. Items are gated by the same `interface.*` flags the web header uses.
 */
@Composable
internal fun ChatOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    conversationId: String?,
    sharedLinksEnabled: Boolean,
    contextUsage: ContextUsage?,
    contextUsageEnabled: Boolean,
    contextBarPlacement: ContextBarPlacement,
    onShowContextDetails: () -> Unit,
    onOpenSearch: () -> Unit,
    onEditInstructions: () -> Unit,
    onEditRetrySettings: () -> Unit,
    onShare: () -> Unit,
    onExit: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        offset = DpOffset(x = 0.dp, y = 8.dp),
    ) {
        if (conversationId != null) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.action_search)) },
                onClick = {
                    onDismiss()
                    onOpenSearch()
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.conversation_instructions)) },
            onClick = {
                onDismiss()
                onEditInstructions()
            },
            leadingIcon = {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
            },
        )
        if (conversationId != null) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.auto_retry)) },
                onClick = {
                    onDismiss()
                    onEditRetrySettings()
                },
                leadingIcon = {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                },
            )
        }
        // Context-usage gauge, surfaced here as a regular menu item when the user picked the
        // overflow-menu placement. Tapping it dismisses the menu and the host (ChatFloatingTopBar)
        // opens the breakdown sheet — that modal can't be opened from inside this popup without
        // nesting modal surfaces.
        val menuContextUsage = contextUsage
        if (contextBarPlacement == ContextBarPlacement.OVERFLOW_MENU &&
            contextUsageEnabled &&
            menuContextUsage != null &&
            menuContextUsage.usedTokens > 0
        ) {
            ContextUsageMenuItem(
                usage = menuContextUsage,
                onClick = {
                    onDismiss()
                    onShowContextDetails()
                },
            )
        }
        if (conversationId != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            // No conversation-title header here: a long title's full (untruncated) width is what a
            // single-line Text reports as its max intrinsic width, and DropdownMenu measures at
            // IntrinsicSize.Max — so the title would stretch the whole menu to full screen width.
            if (sharedLinksEnabled) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.action_share)) },
                    onClick = {
                        onDismiss()
                        onShare()
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Share, contentDescription = null)
                    },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = {
                    Text(
                        "退出对话",
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    onDismiss()
                    onExit()
                },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Outlined.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
            )
        }
    }
}
