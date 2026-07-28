package com.garfiec.librechat.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Column-by-column projection of the subset of [com.garfiec.librechat.core.model.Agent]
 * fields the marketplace + detail screens need. Not a faithful mirror of the
 * upstream agent shape — fields such as `tool_options`, `tool_resources`,
 * `additional_instructions`, `tool_kwargs`, `support_contact`, and `versions`
 * are intentionally absent to keep the cache lean.
 *
 * IMPORTANT: the agent editor must NOT source loads from this entity (via
 * `AgentRepository.getAgent` cache-hit). The editor's round-trip preservation
 * of runtime-only fields relies on reading them from the `/expanded` endpoint
 * via `getAgentForEditing`, which bypasses this cache. Routing the editor
 * through here would surface those fields as null and produce a wire payload
 * that, while currently harmless (PATCH drops nulls via `encodeDefaults=false`
 * / `explicitNulls=false`), is one serialization-config flip away from
 * silently clearing server-side data.
 */
@Entity(tableName = "agents")
data class AgentEntity(
    @PrimaryKey
    val id: String,
    val name: String?,
    val description: String?,
    val avatar: String?,
    val provider: String,
    val model: String,
    val category: String?,
    val authorName: String?,
    val isPromoted: Boolean = false,
    val conversationStarters: String?,
    val tools: String?,
    val updatedAt: Long,
)
