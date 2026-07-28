package com.garfiec.librechat.core.common.extensions

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlin.math.pow
import kotlin.time.Clock

fun <T> Flow<T>.retryWithBackoff(
    maxRetries: Int = 3,
    initialDelay: Long = 1000L,
    maxDelay: Long = 30000L,
    factor: Double = 2.0,
): Flow<T> = retryWhen { cause, attempt ->
    if (attempt >= maxRetries) return@retryWhen false
    val delayMs = (initialDelay * factor.pow(attempt.toDouble())).toLong().coerceAtMost(maxDelay)
    delay(delayMs)
    true
}

fun <T> Flow<T>.throttleFirst(periodMillis: Long): Flow<T> = flow {
    var lastTime = 0L
    collect { value ->
        val currentTime = Clock.System.now().toEpochMilliseconds()
        if (currentTime - lastTime >= periodMillis) {
            lastTime = currentTime
            emit(value)
        }
    }
}
