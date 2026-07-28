package com.garfiec.librechat.core.common.result

import kotlinx.coroutines.CancellationException

sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Error(val exception: Throwable? = null, val message: String? = null) : Result<Nothing>
    data object Loading : Result<Nothing>
}

fun <T> Result<T>.isSuccess(): Boolean = this is Result.Success
fun <T> Result<T>.isError(): Boolean = this is Result.Error
fun <T> Result<T>.isLoading(): Boolean = this is Result.Loading

fun <T> Result<T>.getOrNull(): T? = (this as? Result.Success)?.data
fun <T> Result<T>.getOrThrow(): T = when (this) {
    is Result.Success -> data
    is Result.Error -> throw exception ?: IllegalStateException(message ?: "Unknown error")
    is Result.Loading -> throw IllegalStateException("Result is still loading")
}

suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> =
    try {
        Result.Success(block())
    } catch (e: CancellationException) {
        // Cooperative cancellation must propagate — callers rely on cancel()ed jobs
        // not writing stale errors back to state (e.g. SettingsViewModel.loadUserJob).
        throw e
    } catch (e: Exception) {
        Result.Error(e, e.message ?: "An unexpected error occurred")
    }
