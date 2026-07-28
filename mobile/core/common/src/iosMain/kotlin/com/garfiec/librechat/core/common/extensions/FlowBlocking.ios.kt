package com.garfiec.librechat.core.common.extensions

import kotlinx.coroutines.flow.Flow

actual fun <T> firstBlocking(flow: Flow<T>, default: T): T = default
