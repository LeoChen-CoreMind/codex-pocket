package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.datastore.ChatHeaderAlignment
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_more_options
import com.garfiec.librechat.feature.chat.resources.cd_open_drawer
import com.garfiec.librechat.feature.chat.resources.select_model
import com.garfiec.librechat.feature.chat.resources.retry_count
import com.garfiec.librechat.feature.chat.resources.retry_failed
import com.garfiec.librechat.feature.chat.resources.retry_scheduled
import com.garfiec.librechat.feature.chat.resources.retry_retrying
import com.garfiec.librechat.feature.chat.resources.retry_succeeded
import com.garfiec.librechat.feature.chat.resources.retry_exhausted
import com.garfiec.librechat.feature.chat.resources.retry_cancelled
import com.garfiec.librechat.feature.chat.resources.stop_future_retries
import com.garfiec.librechat.feature.chat.screen.rememberChatModelLabel
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * The chat screen's floating top bar, shared by Android and iOS. Chips (hamburger, the configurable
 * content bubble, optional temp-chat toggle, options) are drawn over a top-down [chatTopBarScrim]
 * so chat content scrolls up *behind* a gently dimmed status-bar region rather than being capped by
 * an opaque app bar — a ChatGPT/Telegram-style header. The in-conversation search bar is pinned
 * directly beneath the chips so the overlay measures as one unit.
 *
 * The bubble is configurable along two mobile-only axes ([ChatUiState.chatHeaderContent] /
 * [ChatUiState.chatHeaderAlignment]): it shows the conversation title,
 * the selected model (tap to open the model selector), or nothing. Showing the model here is an
 * opt-in that partially reverses the default decluttering choice of keeping model/params on the
 * composer "+" menu; the title remains the default.
 *
 * Most actions are wired straight to [viewModel]; only the triggers whose dialog hosting differs by
 * platform (preset load/save) and the navigation callbacks are passed in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatFloatingTopBar(
    uiState: ChatUiState,
    viewModel: ChatViewModel,
    onExitConversation: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenDrawer: (() -> Unit)? = null,
) {
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showContextSheet by remember { mutableStateOf(false) }
    var showInstructionsSheet by remember { mutableStateOf(false) }
    val conversationId = uiState.conversationId
    val conversationTitle = uiState.conversationTitle

    val fillWidth = uiState.chatHeaderAlignment == ChatHeaderAlignment.FILL
    val contentAlignment = when (uiState.chatHeaderAlignment) {
        ChatHeaderAlignment.LEFT, ChatHeaderAlignment.FILL -> Alignment.CenterStart
        ChatHeaderAlignment.CENTER -> Alignment.Center
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = chatTopBarScrim())
                .consumeFloatingBarTouches()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onOpenDrawer != null) {
                FloatingBarIconButton(
                    icon = Icons.Default.Menu,
                    contentDescription = stringResource(Res.string.cd_open_drawer),
                    onClick = onOpenDrawer,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // The configurable content bubble. The flexible region always reserves the space between
            // the hamburger and the right-pinned controls, so the bubble can hug its content
            // (left/center) or fill the region, and `NONE` simply leaves the region empty.
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = contentAlignment,
            ) {
                when (uiState.chatHeaderContent) {
                    ChatHeaderContent.TITLE ->
                        if (conversationId != null && !conversationTitle.isNullOrBlank()) {
                            HeaderTitleChip(
                                title = conversationTitle,
                                fillWidth = fillWidth,
                            )
                        }

                    ChatHeaderContent.MODEL -> {
                        val label = rememberChatModelLabel(
                            selectedEndpoint = uiState.selectedEndpoint,
                            selectedModel = uiState.selectedModel,
                            agents = uiState.agents,
                        )
                        FloatingBarLabelChip(
                            text = label.displayModel ?: stringResource(Res.string.select_model),
                            fillWidth = fillWidth,
                            onClick = viewModel::openModelSheet,
                        )
                    }

                    ChatHeaderContent.NONE -> Unit
                }
            }
            Spacer(modifier = Modifier.width(8.dp))

            BridgePresenceChip(
                online = uiState.bridgeOnline,
                active = uiState.isStreaming || uiState.remoteTurnActive,
            )
            Spacer(modifier = Modifier.width(8.dp))

            Box {
                FloatingBarIconButton(
                    icon = Icons.Default.MoreVert,
                    contentDescription = stringResource(Res.string.cd_more_options),
                    onClick = { showOverflowMenu = true },
                )
                ChatOverflowMenu(
                    expanded = showOverflowMenu,
                    onDismiss = { showOverflowMenu = false },
                    conversationId = conversationId,
                    sharedLinksEnabled = uiState.sharedLinksEnabled,
                    contextUsage = uiState.contextUsage,
                    contextUsageEnabled = uiState.contextUsageEnabled,
                    contextBarPlacement = uiState.contextBarPlacement,
                    onShowContextDetails = { showContextSheet = true },
                    onOpenSearch = viewModel::openSearch,
                    onEditInstructions = { showInstructionsSheet = true },
                    onShare = viewModel::shareConversation,
                    onExit = onExitConversation,
                )
            }
        }

        // The context-usage gauge's default home is just above the composer (Settings → Chat picks
        // its placement); see CommonChatInputCore. When the user routes it to the overflow menu, the
        // menu item hands the trigger here so the breakdown sheet opens outside the menu popup.
        val sheetContextUsage = uiState.contextUsage
        if (showContextSheet && sheetContextUsage != null) {
            ContextUsageSheet(
                usage = sheetContextUsage,
                tokenUsage = uiState.tokenUsage,
                onDismiss = { showContextSheet = false },
            )
        }

        // In-conversation search bar, pinned directly under the floating bar.
        AnimatedVisibility(
            visible = uiState.isSearchOpen,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            InConvoSearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChanged,
                currentMatchIndex = uiState.currentSearchMatchIndex,
                totalMatches = uiState.searchMatchIndices.size,
                onPreviousMatch = viewModel::previousSearchMatch,
                onNextMatch = viewModel::nextSearchMatch,
                onClose = viewModel::closeSearch,
            )
        }

        uiState.retryStatus?.let { status ->
            RetryStatusBanner(
                state = status.state,
                reason = status.reason,
                retryCount = status.retryCount,
                onStop = viewModel::cancelAutomaticRetry,
            )
        }
    }

    if (showInstructionsSheet) {
        ConversationInstructionsSheet(
            initialValue = uiState.modelParameters.customInstructions,
            initialRetryPolicy = uiState.retryPolicy,
            onDismiss = { showInstructionsSheet = false },
            onSave = { instructions, policy ->
                viewModel.setConversationInstructions(instructions)
                viewModel.setRetryPolicy(policy)
            },
        )
    }
}

@Composable
private fun RetryStatusBanner(
    state: String,
    reason: String,
    retryCount: Int,
    onStop: () -> Unit,
) {
    val title = when (state) {
        "scheduled" -> stringResource(Res.string.retry_scheduled)
        "retrying" -> stringResource(Res.string.retry_retrying)
        "succeeded" -> stringResource(Res.string.retry_succeeded)
        "exhausted" -> stringResource(Res.string.retry_exhausted)
        "cancelled" -> stringResource(Res.string.retry_cancelled)
        else -> stringResource(Res.string.retry_failed)
    }
    val active = state == "scheduled" || state == "retrying"
    val containerColor = when (state) {
        "succeeded" -> MaterialTheme.colorScheme.tertiaryContainer
        "scheduled", "retrying" -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (state) {
        "succeeded" -> MaterialTheme.colorScheme.onTertiaryContainer
        "scheduled", "retrying" -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                maxLines = 3,
            )
            if (retryCount > 0) {
                Text(
                    text = stringResource(Res.string.retry_count, retryCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                )
            }
        }
        if (active) {
            TextButton(onClick = onStop) {
                Text(stringResource(Res.string.stop_future_retries))
            }
        }
    }
}

@Composable
private fun BridgePresenceChip(online: Boolean, active: Boolean) {
    val color = when {
        !online -> MaterialTheme.colorScheme.error
        active -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(
            text = when {
                !online -> "离线"
                active -> "正在对话"
                else -> "在线"
            },
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun HeaderTitleChip(
    title: String,
    fillWidth: Boolean,
) {
    FloatingBarLabelChip(text = title, fillWidth = fillWidth)
}
