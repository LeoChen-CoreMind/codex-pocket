package com.garfiec.librechat.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Mirrors upstream `GraphEdge` (packages/data-provider/src/types/agents.ts:441-469).
 *
 * Field naming: upstream uses **camelCase** for `edgeType` and `promptKey` (NOT
 * snake_case). Annotating these with `@SerialName("edge_type")` etc. would
 * silently drop the values on round-trip, since the JSON config has
 * `explicitNulls = false`.
 *
 * `from` and `to` accept either a string or string array upstream, so we
 * round-trip them as raw `JsonElement` -- a single-string edge stays single,
 * a multi-source/multi-dest edge survives intact even though the editor only
 * surfaces singleton from/to in its add dialog for now.
 *
 * `condition` is a function on the upstream type, so it never round-trips.
 */
@Serializable
data class HandoffEdge(
    val from: JsonElement,
    val to: JsonElement,
    val edgeType: String = "handoff",
    val description: String? = null,
    val prompt: String? = null,
    val promptKey: String? = null,
    val excludeResults: Boolean? = null,
)
