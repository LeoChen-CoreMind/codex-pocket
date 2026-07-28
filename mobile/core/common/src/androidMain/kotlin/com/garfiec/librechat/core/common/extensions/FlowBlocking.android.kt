package com.garfiec.librechat.core.common.extensions

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

actual fun <T> firstBlocking(flow: Flow<T>, default: T): T =
    try {
        runBlocking { flow.first() }
    } catch (_: Exception) {
        default
    }
