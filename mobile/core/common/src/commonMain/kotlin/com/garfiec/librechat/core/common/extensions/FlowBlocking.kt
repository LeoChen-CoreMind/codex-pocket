package com.garfiec.librechat.core.common.extensions

import kotlinx.coroutines.flow.Flow

/**
 * Reads the first value from [flow] synchronously via `runBlocking`, returning
 * [default] if the flow is empty or the read fails for any reason.
 *
 * Used so that initial UI state (e.g. persisted sidebar open/close) is available
 * before first composition, avoiding visible flicker on cold start.
 */
expect fun <T> firstBlocking(flow: Flow<T>, default: T): T
