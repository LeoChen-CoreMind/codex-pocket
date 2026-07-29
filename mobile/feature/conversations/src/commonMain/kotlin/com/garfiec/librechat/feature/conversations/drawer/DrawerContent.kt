package com.garfiec.librechat.feature.conversations.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.common.extensions.toRelativeTimeString
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.ui.components.EndpointIcon
import com.garfiec.librechat.core.network.api.OnlineConversationDto
import com.garfiec.librechat.feature.conversations.components.ConversationActionEffects
import com.garfiec.librechat.feature.conversations.components.LocalRelativeTimeReference
import com.garfiec.librechat.feature.conversations.components.ProjectActionsMenu
import com.garfiec.librechat.feature.conversations.components.ProjectDeleteDialog
import com.garfiec.librechat.feature.conversations.components.ProjectNameDialog
import com.garfiec.librechat.feature.conversations.components.ProvideRelativeTimeReference
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.cd_clear_search
import com.garfiec.librechat.feature.conversations.resources.cd_collapse_section
import com.garfiec.librechat.feature.conversations.resources.cd_conversation_actions
import com.garfiec.librechat.feature.conversations.resources.cd_expand_section
import com.garfiec.librechat.feature.conversations.resources.cd_search
import com.garfiec.librechat.feature.conversations.resources.chats
import com.garfiec.librechat.feature.conversations.resources.files
import com.garfiec.librechat.feature.conversations.resources.library
import com.garfiec.librechat.feature.conversations.resources.new_chat
import com.garfiec.librechat.feature.conversations.resources.no_conversations_found
import com.garfiec.librechat.feature.conversations.resources.pinned
import com.garfiec.librechat.feature.conversations.resources.project_new
import com.garfiec.librechat.feature.conversations.resources.project_unassigned
import com.garfiec.librechat.feature.conversations.resources.projects
import com.garfiec.librechat.feature.conversations.resources.projects_all
import com.garfiec.librechat.feature.conversations.resources.search_conversations_placeholder
import com.garfiec.librechat.feature.conversations.resources.settings
import com.garfiec.librechat.feature.conversations.resources.skills
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.delay

// Pre-computed shapes to avoid creating new ones per item per frame
private val ItemShape = RoundedCornerShape(8.dp)
private val ActiveIndicatorShape = RoundedCornerShape(2.dp)

// Sliding pill toggle: the rounded track, its slightly-tighter moving thumb, and each icon+label
// cell's fixed size (equal widths so the thumb offset is a whole-cell step).
private val PillTrackShape = RoundedCornerShape(12.dp)
private val PillThumbShape = RoundedCornerShape(8.dp)
private val DrawerTabCellWidth = 88.dp
private val DrawerTabCellHeight = 34.dp

// Pulls a tappable drawer row in from the edges and clips its ripple to [ItemShape], so every row
// reads as the same inset, rounded button instead of a full-bleed rectangular highlight. Apply
// before .clickable so the indication is bounded by the rounded shape.
private fun Modifier.drawerRowShape(): Modifier =
    padding(horizontal = 4.dp, vertical = 1.dp).clip(ItemShape)

// Gap between the conversation row's bottom edge and the long-press menu's top edge.
private val MenuVerticalGap = 4.dp

/**
 * Stateful DrawerContent that collects its own state from the ViewModel.
 */
@Composable
fun DrawerContent(
    onNewChat: () -> Unit,
    onConversationClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onAgentsClick: () -> Unit,
    onFilesClick: () -> Unit,
    onSkillsClick: () -> Unit,
    accounts: List<AccountUiModel>,
    modifier: Modifier = Modifier,
    onOpenProjectsIndex: () -> Unit = {},
    onSwitchAccount: (String) -> Unit = {},
    onAddAccount: () -> Unit = {},
    // Round-robin swipe switch (in place, drawer stays open) + sheet remove — both are nav-shell
    // account operations, hoisted in because DrawerViewModel owns only drawer data now.
    onSwitchAccountInPlace: (String) -> Unit = {},
    onRemoveAccount: (String) -> Unit = {},
    // Fired when the user deletes the conversation currently open in the pane: move the pane off the
    // now-gone thread. Distinct from [onNewChat] because that also closes the phone drawer — here the
    // drawer stays open so the user can keep browsing (defaults to [onNewChat] if a host doesn't wire it).
    onActiveConversationDelete: () -> Unit = onNewChat,
    viewModel: DrawerViewModel = koinViewModel(),
    vscodeViewModel: VsCodeBindingViewModel = koinViewModel(),
) {
    val uiState by viewModel.drawerUiState.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val inlineProjectChats by viewModel.inlineProjectChats.collectAsStateWithLifecycle()
    val libraryTab by viewModel.drawerLibraryTab.collectAsStateWithLifecycle()
    val vscodeState by vscodeViewModel.uiState.collectAsStateWithLifecycle()
    val scopedUiState = if (vscodeState.boundInstance?.online == true) {
        uiState
    } else {
        uiState.copy(
            groupedConversations = emptyList(),
            favoriteConversations = emptyList(),
            pinnedConversations = emptyList(),
        )
    }

    LaunchedEffect(vscodeState.scopeRevision) {
        if (vscodeState.scopeRevision > 0) viewModel.refreshConversations()
    }

    LaunchedEffect(vscodeState.boundInstance?.instanceId, vscodeState.boundInstance?.online) {
        if (vscodeState.boundInstance?.online != true) return@LaunchedEffect
        while (true) {
            delay(4_000)
            viewModel.refreshRuntimeStatuses()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            vscodeViewModel.refreshOnline()
            delay(2_000)
        }
    }

    // Account switcher: the header chip opens the roster sheet; remove asks for confirmation.
    // Switch/add callbacks come from the host (they also close the drawer); remove goes straight to
    // the ViewModel against the tapped row's id — no captured drawer state (Nav3 stale-closure rule).
    var showAccountSheet by remember { mutableStateOf(false) }
    var removeAccountTarget by remember { mutableStateOf<AccountUiModel?>(null) }
    var showVsCodePicker by remember { mutableStateOf(false) }

    val controlledNewChat = {
        vscodeViewModel.newChat()
        onNewChat()
    }
    val controlledConversationClick: (String) -> Unit = { conversationId ->
        vscodeViewModel.openThread(conversationId)
        onConversationClick(conversationId)
    }
    val onlineConversationClick: (OnlineConversationDto) -> Unit = { online ->
        vscodeViewModel.openOnlineConversation(online.instanceId, online.threadId) {
            onConversationClick(online.threadId)
        }
    }

    // Side-effects for the long-press action menu (share-link copy, export file-save, navigate to
    // a duplicated conversation, error toasts). Lives in feature/conversations so it owns the
    // clipboard/toast/file-save plumbing and its localized strings.
    ConversationActionEffects(
        events = viewModel.events,
        onNavigateToConversation = controlledConversationClick,
        onNavigateToNewChat = onActiveConversationDelete,
    )

    DrawerContent(
        uiState = scopedUiState,
        footerContent = {
            Spacer(modifier = Modifier.height(8.dp))
            DrawerFooterItem(
                icon = Icons.Default.Computer,
                label = vscodeState.boundInstance?.let { instance ->
                    "${instance.editorName} · ${instance.workspaceName ?: instance.windowTitle}"
                } ?: "选择编辑器窗口",
                onClick = {
                    vscodeViewModel.refresh()
                    showVsCodePicker = true
                },
            )
            accounts.firstOrNull { it.isActive }?.let { active ->
                Spacer(modifier = Modifier.height(8.dp))
                // Footer row: Settings (icon + label) on the left takes the width; the account
                // avatar (icon only, tap to switch) sits on the right.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DrawerFooterItem(
                        icon = Icons.Default.Settings,
                        label = stringResource(Res.string.settings),
                        onClick = onSettingsClick,
                        modifier = Modifier.weight(1f),
                    )
                    AccountChip(
                        account = active,
                        onClick = { showAccountSheet = true },
                        // Gmail/YouTube-style: swipe the avatar up/down to round-robin accounts
                        // without opening the sheet. Switches in place via the ViewModel so the user
                        // can swipe through several accounts against the same avatar. The sheet path
                        // keeps the drawer open too (the host no longer closes it on switch).
                        // Disabled (null) with a single account.
                        onSwitchAdjacent = if (accounts.size > 1) {
                            { delta -> adjacentAccountId(accounts, delta)?.let(onSwitchAccountInPlace) }
                        } else {
                            null
                        },
                    )
                }
            }
        },
        onSearchQueryChange = viewModel::onSearchQueryChanged,
        onNewChat = controlledNewChat,
        onConversationClick = controlledConversationClick,
        onlineConversations = vscodeState.onlineConversations,
        onlineError = vscodeState.error,
        closingOnlineConversationKeys = vscodeState.closingConversationKeys,
        onOnlineConversationClick = onlineConversationClick,
        onExitOnlineConversation = { online ->
            vscodeViewModel.closeThread(online.instanceId, online.threadId)
        },
        onAgentsClick = onAgentsClick,
        onFilesClick = onFilesClick,
        onSkillsClick = onSkillsClick,
        onRefresh = viewModel::refreshConversations,
        onLoadMore = viewModel::loadMoreConversations,
        projects = projects,
        onLoadProjects = viewModel::loadProjects,
        onOpenProjectsIndex = onOpenProjectsIndex,
        selectedTab = libraryTab ?: DrawerTab.Chats,
        onSelectTab = viewModel::setDrawerLibraryTab,
        inlineProjectChats = if (vscodeState.boundInstance?.online == true) {
            inlineProjectChats
        } else {
            InlineProjectChatsState()
        },
        onToggleProject = viewModel::toggleProjectExpanded,
        onCreateProject = viewModel::createProject,
        onRenameProject = viewModel::renameProject,
        onDeleteProject = viewModel::deleteProject,
        modifier = modifier,
    )

    if (showAccountSheet) {
        AccountSwitcherSheet(
            accounts = accounts,
            onSwitchAccount = { accountId ->
                showAccountSheet = false
                onSwitchAccount(accountId)
            },
            onRemoveAccountRequest = { removeAccountTarget = it },
            onAddAccount = {
                showAccountSheet = false
                onAddAccount()
            },
            onDismiss = { showAccountSheet = false },
        )
    }

    if (showVsCodePicker) {
        VsCodeInstancePicker(
            state = vscodeState,
            onRefresh = vscodeViewModel::refresh,
            onSelect = { instanceId ->
                vscodeViewModel.bind(instanceId) {
                    onNewChat()
                    showVsCodePicker = false
                }
            },
            onDismiss = { showVsCodePicker = false },
        )
    }

    removeAccountTarget?.let { target ->
        RemoveAccountDialog(
            account = target,
            onConfirm = {
                onRemoveAccount(target.accountId)
                removeAccountTarget = null
            },
            onDismiss = { removeAccountTarget = null },
        )
    }
}

@Composable
private fun VsCodeInstancePicker(
    state: VsCodeBindingUiState,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("绑定编辑器窗口") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    state.loading && state.instances.isEmpty() -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    state.instances.isEmpty() -> {
                        Text("没有检测到在线 Companion。请确认目标编辑器已安装并激活扩展；刚完成安装时可能需要重新加载一次编辑器窗口。")
                    }
                    else -> state.instances.forEach { instance ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ItemShape)
                                .clickable(enabled = instance.online) { onSelect(instance.instanceId) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = instance.instanceId == state.boundInstanceId,
                                onClick = if (instance.online) {
                                    { onSelect(instance.instanceId) }
                                } else {
                                    null
                                },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = instance.workspaceName ?: instance.windowTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "PID ${instance.processId} · ${if (instance.online) "在线" else "离线"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                instance.workspaceFolders.firstOrNull()?.let { workspace ->
                                    Text(
                                        text = workspace,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                state.error?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRefresh) { Text("刷新") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DrawerContent(
    uiState: DrawerUiState,
    onSearchQueryChange: (String) -> Unit,
    onNewChat: () -> Unit,
    onConversationClick: (String) -> Unit,
    onlineConversations: List<OnlineConversationDto> = emptyList(),
    onlineError: String? = null,
    closingOnlineConversationKeys: Set<String> = emptySet(),
    onOnlineConversationClick: (OnlineConversationDto) -> Unit = {},
    onExitOnlineConversation: (OnlineConversationDto) -> Unit = {},
    onAgentsClick: () -> Unit,
    onFilesClick: () -> Unit,
    onSkillsClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Slot below the footer links (Files, Agents, …) — the stateful wrapper puts the Settings row
    // and the account avatar here, at the bottom of the drawer.
    footerContent: (@Composable () -> Unit)? = null,
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    projects: List<ChatProject> = emptyList(),
    onLoadProjects: () -> Unit = {},
    // Projects tab (segmented toggle above the list): the folder list + inline chat accordion, plus
    // an escape hatch to the full-page projects index for advanced controls.
    onOpenProjectsIndex: () -> Unit = {},
    // Persisted Chats/Projects toggle selection (controlled by the caller).
    selectedTab: DrawerTab = DrawerTab.Chats,
    onSelectTab: (DrawerTab) -> Unit = {},
    inlineProjectChats: InlineProjectChatsState = InlineProjectChatsState(),
    onToggleProject: (String) -> Unit = {},
    onCreateProject: (String) -> Unit = {},
    onRenameProject: (String, String) -> Unit = { _, _ -> },
    onDeleteProject: (String) -> Unit = {},
) {
    // Invisible anchor that claims initial focus so the search field below
    // doesn't auto-focus and pop the keyboard when the drawer opens. Tapping
    // the search field still focuses it normally. See Android focus docs
    // ("Change focus behavior"): redirect initial focus to a non-input element.
    val focusAnchor = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusAnchor.requestFocus() }
    }

    // One ticker for every row the drawer renders, so the relative-time labels below
    // actually advance instead of freezing at whatever they said when composed.
    ProvideRelativeTimeReference {
        Column(
            modifier = modifier
                .fillMaxHeight()
                .width(300.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 16.dp),
        ) {
            Spacer(
                modifier = Modifier
                    .size(1.dp)
                    .focusRequester(focusAnchor)
                    .focusable(),
            )

            // Search field is hidden by default and revealed by the toggle beside "New Chat". Seed the
            // toggle from the current query so a restored search stays visible across recompositions.
            var searchExpanded by remember { mutableStateOf(uiState.searchQuery.isNotEmpty()) }
            val searchFocusRequester = remember { FocusRequester() }

            // Focus the field (and pop the keyboard) only when the user opens search explicitly — the
            // focusAnchor above still steals initial focus so opening the drawer doesn't do this.
            LaunchedEffect(searchExpanded) {
                if (searchExpanded) {
                    runCatching { searchFocusRequester.requestFocus() }
                }
            }

            // "New Chat" button at top, with a search toggle to its right.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onNewChat,
                    modifier = Modifier.weight(1f),
                    shape = ItemShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(Res.string.new_chat),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Collapsing clears the query so the list resets to its normal (unsearched) state.
                Surface(
                    onClick = {
                        searchExpanded = !searchExpanded
                        if (!searchExpanded) onSearchQueryChange("")
                    },
                    modifier = Modifier.fillMaxHeight(),
                    shape = ItemShape,
                    color = if (searchExpanded) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (searchExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = stringResource(Res.string.cd_search),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Search bar — revealed only when toggled on.
            AnimatedVisibility(visible = searchExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(Res.string.cd_search),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(Res.string.cd_clear_search),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        },
                        placeholder = {
                            Text(
                                text = stringResource(Res.string.search_conversations_placeholder),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        singleLine = true,
                        shape = ItemShape,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .focusRequester(searchFocusRequester),
                    )
                }
            }

            // Chats / Projects toggle above the list — shown only where projects are supported. When
            // hidden (older server / no permission) the drawer is always the recents list.
            val projectsTabAvailable = uiState.projectsEnabled
            if (projectsTabAvailable) {
                Spacer(modifier = Modifier.height(8.dp))
                // Section heading + a compact icon pill that slides between the recents and projects
                // views (the label names the whole section; the pill toggles what the list shows).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.library),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                    DrawerTabToggle(
                        selectedTab = selectedTab,
                        onSelect = onSelectTab,
                    )
                }
                // Keep the folder counts fresh whenever the user opens the Projects tab.
                val currentOnLoadProjects by rememberUpdatedState(onLoadProjects)
                LaunchedEffect(selectedTab) {
                    if (selectedTab == DrawerTab.Projects) currentOnLoadProjects()
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val showProjectsTab = projectsTabAvailable && selectedTab == DrawerTab.Projects

            // Conversation list with pinned items and date groups.
            val listState = rememberLazyListState()
            val currentOnLoadMore by rememberUpdatedState(onLoadMore)

            // Historical rows are deliberately click-only. Online rows below own the sole long-press action.
            val renderConversationItem: @Composable (String, DrawerConversationDisplayData) -> Unit = { rowKey, data ->
                DrawerConversationItem(
                    data = data,
                    onClick = { onConversationClick(data.conversationId) },
                )
            }

            val shouldLoadMore = remember {
                derivedStateOf {
                    val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    val totalItems = listState.layoutInfo.totalItemsCount
                    lastVisibleItem >= totalItems - 8 && totalItems > 0
                }
            }

            LaunchedEffect(shouldLoadMore.value) {
                if (shouldLoadMore.value && uiState.hasMore && !uiState.isLoadingMore) {
                    currentOnLoadMore()
                }
            }

            if (!showProjectsTab) {
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.weight(1f),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                    if (onlineConversations.isNotEmpty() && uiState.searchQuery.isEmpty()) {
                        stickyHeader(key = "online_header") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                            ) {
                                SectionHeader(
                                    icon = Icons.Default.Computer,
                                    title = "在线列表",
                                )
                            }
                        }
                        items(
                            items = onlineConversations,
                            key = { "online_${it.instanceId}_${it.threadId}" },
                            contentType = { "online_conversation" },
                        ) { online ->
                            OnlineConversationItem(
                                data = online,
                                closing = "${online.instanceId}:${online.threadId}" in closingOnlineConversationKeys,
                                onClick = { onOnlineConversationClick(online) },
                                onExit = { onExitOnlineConversation(online) },
                            )
                        }
                        item(key = "online_divider") {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                    if (onlineError != null && uiState.searchQuery.isEmpty()) {
                        item(key = "online_error") {
                            Text(
                                text = onlineError,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }

                    // Pinned section (v0.8.7) — pinned conversations surfaced above favorites. This is
                    // their canonical home: when shown, they're filtered out of the date-grouped buckets
                    // (see ConversationListStateHolder.withoutPinned) so they don't appear twice. The
                    // section is hidden during search, where pinned rows instead surface in the results.
                    if (uiState.pinnedConversations.isNotEmpty() && uiState.searchQuery.isEmpty()) {
                        item(key = "pinned_header") {
                            SectionHeader(
                                icon = Icons.Default.PushPin,
                                title = stringResource(Res.string.pinned),
                            )
                        }

                        items(
                            items = uiState.pinnedConversations,
                            key = { "pin_${it.conversationId}" },
                            contentType = { "conversation" },
                        ) { data ->
                            renderConversationItem("pin_${data.conversationId}", data)
                        }

                        item(key = "pinned_divider") {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }

                    if (uiState.groupedConversations.isEmpty() && uiState.searchQuery.isNotEmpty()) {
                        item(key = "empty_search") {
                            Text(
                                text = stringResource(Res.string.no_conversations_found),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                            )
                        }
                    }

                    uiState.groupedConversations.forEach { (dateGroup, displayItems) ->
                        stickyHeader(key = "header_$dateGroup") {
                            Text(
                                text = dateGroup,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .padding(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 12.dp,
                                        bottom = 4.dp,
                                    ),
                            )
                        }

                        items(
                            items = displayItems,
                            key = { it.conversationId },
                            contentType = { "conversation" },
                        ) { data ->
                            renderConversationItem(data.conversationId, data)
                        }
                    }

                    if (uiState.isLoadingMore) {
                        item(key = "loading_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                    }
                }
            } else {
                DrawerProjectsList(
                    projects = projects,
                    inlineProjectChats = inlineProjectChats,
                    onToggleProject = onToggleProject,
                    onOpenProjectsIndex = onOpenProjectsIndex,
                    onCreateProject = onCreateProject,
                    onRenameProject = onRenameProject,
                    onDeleteProject = onDeleteProject,
                    renderChat = renderConversationItem,
                    modifier = Modifier.weight(1f),
                )
            }

            // Bottom section: divider + footer links
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            if (uiState.skillsEnabled) {
                DrawerFooterItem(
                    icon = Icons.Default.Extension,
                    label = stringResource(Res.string.skills),
                    onClick = onSkillsClick,
                )
            }
            DrawerFooterItem(
                icon = Icons.Default.Folder,
                label = stringResource(Res.string.files),
                onClick = onFilesClick,
            )

            footerContent?.invoke()

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

}

/**
 * Sliding-pill toggle for the drawer's two list modes: a rounded track holding two equal-width
 * icon+label cells (chat / workspaces) with a highlighted thumb that animates between them. Sits
 * inline to the right of the section heading; tapping a cell selects that mode.
 */
@Composable
private fun DrawerTabToggle(
    selectedTab: DrawerTab,
    onSelect: (DrawerTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbOffsetFraction by animateFloatAsState(
        targetValue = if (selectedTab == DrawerTab.Chats) 0f else 1f,
        label = "DrawerTabThumb",
    )
    Box(
        modifier = modifier
            .clip(PillTrackShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(3.dp)
            .height(DrawerTabCellHeight),
    ) {
        // Moving highlight behind the active cell; offset by a whole cell for the selected side.
        Box(
            modifier = Modifier
                .width(DrawerTabCellWidth)
                .fillMaxHeight()
                .offset(x = DrawerTabCellWidth * thumbOffsetFraction)
                .clip(PillThumbShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        )
        Row {
            DrawerTabToggleCell(
                icon = Icons.AutoMirrored.Filled.Chat,
                label = stringResource(Res.string.chats),
                selected = selectedTab == DrawerTab.Chats,
                onClick = { onSelect(DrawerTab.Chats) },
            )
            DrawerTabToggleCell(
                icon = Icons.Default.Workspaces,
                label = stringResource(Res.string.projects),
                selected = selectedTab == DrawerTab.Projects,
                onClick = { onSelect(DrawerTab.Projects) },
            )
        }
    }
}

/** One icon+label cell of [DrawerTabToggle]; its tint flips when it becomes the selected side. */
@Composable
private fun DrawerTabToggleCell(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .width(DrawerTabCellWidth)
            .fillMaxHeight()
            .clip(PillThumbShape)
            .clickable(role = Role.Tab, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = contentColor,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/**
 * The Projects tab of the drawer — an inline folder browser reached via the pill toggle. Lists the
 * Unassigned bucket + project folders; tapping a folder
 * expands its chats inline (single-expand accordion via [onToggleProject]) rendered through
 * [renderChat] so the rows and their long-press actions match the recents list. Owns its own
 * create/rename/delete dialog state; [onOpenProjectsIndex] is the escape hatch to the full-page index.
 */
@Composable
private fun DrawerProjectsList(
    projects: List<ChatProject>,
    inlineProjectChats: InlineProjectChatsState,
    onToggleProject: (String) -> Unit,
    onOpenProjectsIndex: () -> Unit,
    onCreateProject: (String) -> Unit,
    onRenameProject: (String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    renderChat: @Composable (String, DrawerConversationDisplayData) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpenId by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ChatProject?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatProject?>(null) }

    // Expanded body for the open folder: a spinner while its page loads, an empty note, or the chat
    // rows. Single-expand, so inlineProjectChats always holds the currently open folder's chats.
    val expandedChats: @Composable (String) -> Unit = { projectId ->
        when {
            inlineProjectChats.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
            inlineProjectChats.conversations.isEmpty() -> {
                Text(
                    text = stringResource(Res.string.no_conversations_found),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            else -> {
                inlineProjectChats.conversations.forEach { data ->
                    renderChat("projchat_${projectId}_${data.conversationId}", data)
                }
            }
        }
    }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        // Action row: link to the full-page index (advanced controls) + create a new folder.
        item(key = "projects_actions") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenProjectsIndex) {
                    Text(
                        text = stringResource(Res.string.projects_all),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        items(items = projects, key = { it.id }) { folder ->
            ProjectFolderAccordion(
                name = folder.name,
                conversationCount = folder.conversationCount,
                expanded = inlineProjectChats.expandedProjectId == folder.id,
                onToggle = { onToggleProject(folder.id) },
                menuContent = null,
                expandedContent = { expandedChats(folder.id) },
            )
        }
    }

    if (showCreateDialog) {
        ProjectNameDialog(
            title = stringResource(Res.string.project_new),
            initialName = "",
            onConfirm = {
                onCreateProject(it)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    renameTarget?.let { target ->
        ProjectNameDialog(
            title = target.name,
            initialName = target.name,
            onConfirm = {
                onRenameProject(target.id, it)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { target ->
        ProjectDeleteDialog(
            projectName = target.name,
            onConfirm = {
                onDeleteProject(target.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

/**
 * A project folder row that expands/collapses its chats inline (accordion) in the Projects tab. The
 * chevron points down when open and right when collapsed; [menuContent] is the optional trailing
 * overflow (null for the Unassigned bucket), and [expandedContent] renders the chats when open.
 */
@Composable
private fun ProjectFolderAccordion(
    name: String,
    conversationCount: Int?,
    expanded: Boolean,
    onToggle: () -> Unit,
    expandedContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    menuContent: (@Composable () -> Unit)? = null,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "ProjectChevronRotation",
    )
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawerRowShape()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (conversationCount != null) {
                Text(
                    text = conversationCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (menuContent != null) {
                Box { menuContent() }
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(chevronRotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            // Slight indent nests the chats under their folder.
            Column(modifier = Modifier.padding(start = 12.dp)) {
                expandedContent()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnlineConversationItem(
    data: OnlineConversationDto,
    closing: Boolean,
    onClick: () -> Unit,
    onExit: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ItemShape)
                .combinedClickable(
                    enabled = !closing,
                    role = Role.Button,
                    onClick = onClick,
                    onLongClickLabel = "退出对话",
                    onLongClick = { menuOpen = true },
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Computer,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(data.editorName)
                        append(" · ")
                        append(data.workspaceName ?: data.windowTitle)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (closing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text("退出对话") },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                },
                onClick = {
                    menuOpen = false
                    onExit()
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerConversationItem(
    data: DrawerConversationDisplayData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (data.isActive) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 1.dp)
                .fillMaxWidth()
                .background(backgroundColor, ItemShape)
                .clip(ItemShape)
                .clickable(
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (data.isActive) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.primary, ActiveIndicatorShape),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            EndpointIcon(
                endpointName = data.endpoint,
                iconUrl = data.endpointIconUrl,
                size = 18.dp,
                glyphTint = if (data.isRunning || data.isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (data.isActive) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Relative time is formatted here rather than in the ViewModel mapping: it depends
                // on the clock, so a pre-formatted value in immutable state goes stale. The
                // reference is a key, not just an input — it is the thing that advances, and
                // without it this memo would never recompute. Same shape as ConversationItem.
                val reference = LocalRelativeTimeReference.current
                val subtitle = remember(data.model, data.projectName, data.isRunning, data.updatedAt, reference) {
                    buildString {
                        if (data.isRunning) append("正在运行")
                        data.projectName?.takeIf { it.isNotBlank() }?.let { project ->
                            if (isNotEmpty()) append(" · ")
                            append(project.take(24))
                        }
                        data.model?.let { model ->
                            if (isNotEmpty()) append(" · ")
                            append(model.take(20))
                        }
                        val relativeTime = data.updatedAt?.toRelativeTimeString(reference)
                        if (!relativeTime.isNullOrEmpty()) {
                            if (isNotEmpty()) append(" \u00B7 ")
                            append(relativeTime)
                        }
                    }
                }
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

        }
    }
}

/**
 * Drawer section header (icon + title), used by the pinned section. With a non-null
 * [onToggle] the whole row is tappable (a full 48dp touch target) and shows a trailing chevron that
 * points down when expanded and right when collapsed; with a null [onToggle] it renders as a plain,
 * non-interactive, compact header.
 */
@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    collapsed: Boolean = false,
    onToggle: (() -> Unit)? = null,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (collapsed) -90f else 0f,
        label = "SectionChevronRotation",
    )
    // The interactive header keeps its label/icon visually small but claims a 48dp-tall hit area so
    // it's comfortably tappable; the static header stays compact so it doesn't waste vertical space.
    val rowModifier = if (onToggle != null) {
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .drawerRowShape()
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(
                    if (collapsed) Res.string.cd_expand_section else Res.string.cd_collapse_section,
                ),
                onClick = onToggle,
            )
            .padding(horizontal = 12.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
    }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (onToggle != null) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(chevronRotation),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DrawerFooterItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawerRowShape()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
