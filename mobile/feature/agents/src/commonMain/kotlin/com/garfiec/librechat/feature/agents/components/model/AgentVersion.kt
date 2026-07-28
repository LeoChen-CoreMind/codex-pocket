package com.garfiec.librechat.feature.agents.components.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One revision in the agent's edit history. Mirrors upstream's `VersionRecord`
 * (client/src/components/SidePanel/Agents/Version/types.ts) — each entry is the
 * full snapshot of the agent at that save point.
 *
 * @property versionIndex Original position in the unsorted `agent.versions[]`
 *   array. Sent to the server as `version_index` on revert. Stable across the
 *   `updatedAt`-sorted display order — never recompute from the list position.
 * @property displayNumber 1-based "Version N" label as shown to the user (web
 *   computes this as `versions.length - displayIndex`; mobile precomputes here).
 * @property isActive True when this revision matches the agent's current state
 *   on the comparable surface (name, description, instructions, artifacts,
 *   capabilities, tools). Mirrors upstream `isActiveVersion`.
 * @property snapshot The raw JSON snapshot of the agent at this revision. Kept
 *   verbatim so the UI can drill into any field (tools list, capabilities, etc.)
 *   without re-fetching.
 */
data class AgentVersion(
    val versionIndex: Int,
    val displayNumber: Int,
    val updatedAt: String?,
    val createdAt: String?,
    val isActive: Boolean,
    val name: String?,
    val description: String?,
    val instructions: String?,
    val artifacts: String?,
    val capabilities: List<String>,
    val tools: List<String>,
    val snapshot: JsonObject,
)

/**
 * Returns true when [version] matches the current agent state on the fields the
 * web client compares for "active" highlighting. Upstream behavior:
 * `client/src/components/SidePanel/Agents/Version/isActiveVersion.ts`.
 *
 * Specifically, an active version has identical name/description/instructions/
 * artifacts, and identical capabilities+tools as sets. The list comparison is
 * unordered so reordering doesn't falsely invalidate the active marker.
 */
internal fun snapshotMatchesCurrent(
    snapshot: JsonObject,
    currentName: String?,
    currentDescription: String?,
    currentInstructions: String?,
    currentArtifacts: String?,
    currentCapabilities: Set<String>,
    currentTools: Set<String>,
): Boolean {
    if (snapshot.stringOrNull("name") != currentName) return false
    if (snapshot.stringOrNull("description") != currentDescription) return false
    if (snapshot.stringOrNull("instructions") != currentInstructions) return false
    if (snapshot.stringOrNull("artifacts") != currentArtifacts) return false
    if (snapshot.stringListOrEmpty("capabilities").toSet() != currentCapabilities) return false
    if (snapshot.stringListOrEmpty("tools").toSet() != currentTools) return false
    return true
}

internal fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { !it.isString || it.content.isNotEmpty() }?.contentOrNullSafe()

internal fun JsonObject.stringListOrEmpty(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()

private fun JsonPrimitive.contentOrNullSafe(): String? = try {
    if (isString) content else content.takeIf { it != "null" }
} catch (_: Throwable) {
    null
}

/**
 * Builds the display list from a raw `agent.versions[]` array.
 * Sorted newest-first by updatedAt (matches upstream VersionPanel.tsx).
 * Index assignments: [versionIndex] is the original position (used for revert),
 * [displayNumber] is `versions.size - sortedIndex` for the "Version N" label.
 */
fun buildAgentVersionList(
    rawVersions: List<JsonObject>,
    currentName: String?,
    currentDescription: String?,
    currentInstructions: String?,
    currentArtifacts: String?,
    currentCapabilities: Set<String>,
    currentTools: Set<String>,
): List<AgentVersion> {
    if (rawVersions.isEmpty()) return emptyList()

    val withIndex = rawVersions.mapIndexed { idx, snap -> idx to snap }
    val sorted = withIndex.sortedByDescending { (_, snap) ->
        snap.stringOrNull("updatedAt")
    }
    val total = rawVersions.size
    return sorted.mapIndexed { sortedIdx, (originalIdx, snap) ->
        AgentVersion(
            versionIndex = originalIdx,
            displayNumber = total - sortedIdx,
            updatedAt = snap.stringOrNull("updatedAt"),
            createdAt = snap.stringOrNull("createdAt"),
            isActive = snapshotMatchesCurrent(
                snapshot = snap,
                currentName = currentName,
                currentDescription = currentDescription,
                currentInstructions = currentInstructions,
                currentArtifacts = currentArtifacts,
                currentCapabilities = currentCapabilities,
                currentTools = currentTools,
            ),
            name = snap.stringOrNull("name"),
            description = snap.stringOrNull("description"),
            instructions = snap.stringOrNull("instructions"),
            artifacts = snap.stringOrNull("artifacts"),
            capabilities = snap.stringListOrEmpty("capabilities"),
            tools = snap.stringListOrEmpty("tools"),
            snapshot = snap,
        )
    }
}
