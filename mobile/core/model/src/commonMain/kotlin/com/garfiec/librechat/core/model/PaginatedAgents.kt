package com.garfiec.librechat.core.model

/** Wrapper for a single page of agent marketplace results. */
data class PaginatedAgents(
    val agents: List<Agent>,
    val hasMore: Boolean,
    val total: Int,
)
