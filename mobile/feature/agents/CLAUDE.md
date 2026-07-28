# feature:agents

## Screens
- **AgentMarketplaceScreen** -- grid of agent cards with search and category filters
- **AgentDetailScreen** -- full agent info with "Start Chat" action

## Navigation
- Sealed interface: `AgentsRoute : NavKey` with typed route classes
- Routes: `AgentMarketplace` (grid), `AgentDetail(agentId: String)` (detail), `AgentEditorCreate`, `AgentEditorEdit(agentId: String)` (all `@Serializable`)
- Feature entries registered via `EntryProviderScope<NavKey>.agentsEntries()`
- `onStartChat(agentId)` callback navigates to chat with the selected agent

## Marketplace ViewModel
- `AgentMarketplaceViewModel` uses server-side pagination via `AgentRepository.getAgentsPaginated()`
- Page size: 10 agents per page
- Categories fetched from `getAgentCategories()` API (server-driven, not client-derived)
- `onCategorySelected()` toggles category filter and resets to page 1
- `onSearchQueryChanged()` debounces 500ms, then resets to page 1 with server-side search
- `loadMore()` appends next page; no-ops if already loading or no more pages
- Pull-to-refresh resets to page 1
- **Gotcha**: Search and category changes reset pagination — always load from page 1 when filters change

## Agent Card Layout
- Card: 136dp height, endpoint avatar + name (2-line clamp) + description (2-line clamp) + "By {author}"
- Category badge at top-right corner
- Tap opens agent detail

## Agent Detail
- `AgentDetailViewModel` loads single agent by ID
- Shows: large avatar, name, description, capabilities/tools list
- Actions: Pin/Unpin, Copy link, "Start Chat" button
- "Start Chat" flow: sets `endpoint: "agents"` + `agent_id`, creates conversation template, navigates to new chat

## Data Layer
- `AgentRepository` in `:core:data` wraps `AgentsApi` from `:core:network`
- API: `GET /api/agents` (list), `GET /api/agents/:id` (detail), `GET /api/agents/categories`
- Categories are server-driven, not hardcoded; special values: "promoted" (Top Picks), "all"

## Infinite Scroll
- `LazyVerticalGrid` with `rememberLazyGridState()` + `derivedStateOf` for scroll detection
- Triggers `loadMore()` when last visible item is within 3 of the end
- Shows `CircularProgressIndicator` in a full-span grid item while loading more

## Spec Notes (not yet implemented)
- Category tabs with slide animation (`AnimatedContent` with `slideInHorizontally`)
- Permission check: `PermissionTypes.MARKETPLACE` with `Permissions.USE`

### Agent Editor — Advanced Sections
- `AgentEditorScreen` now includes 6 collapsible sections below the basic fields:
  - `AgentActionsPanel` — OpenAPI action CRUD (domain, type, auth)
  - `AgentMcpToolsSelector` — hierarchical MCP tools grouped by server, checkbox selection
  - `AgentCodeInterpreterSection` / `AgentFileSearchSection` — simple capability toggles
  - `AgentSharingSection` — visibility (Private/Team/Public) + collaborative toggle
  - `AgentHandoffConfig` — select agents for handoff, displayed as InputChips
- `AgentEditorViewModel` depends on both `AgentRepository` and `McpRepository`
- **Gotcha**: MCP tools load requires a separate `McpRepository.getTools()` call; they're not bundled with agent data
- **Gotcha**: `isPublic`/`isCollaborative` map to the sharing section, not individual toggles in the agent model
