# core:common

Pure Kotlin utilities shared by all modules. This is the lowest layer -- no other `:core:*` module is a dependency.

## What This Module Provides

- **Result sealed class** (`result/Result.kt`): `Success<T>`, `Error(exception, message)`, `Loading`. Used by repositories and ViewModels to propagate outcomes.
- **Dispatcher & Scope DI** (`di/CommonModule.kt`): Named Koin qualifiers (`named("io")`, `named("default")`, `named("main")`) for dispatchers and `named("applicationScope")` for coroutine scope. Always inject dispatchers -- never hardcode `Dispatchers.IO`.
- **Extensions** (`extensions/`): `StringExt`, `DateExt`, `FlowExt` (includes `retryWithBackoff`, `throttleFirst`).
- **ConnectivityObserver**: Wraps Android `ConnectivityManager.NetworkCallback` to detect network changes. Used by SSE reconnection logic.

## safeApiCall Pattern

All repository implementations use this to wrap network calls:

```kotlin
suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> =
    try {
        Result.Success(block())
    } catch (e: ClientRequestException) {      // 4xx
        Result.Error(e, parseErrorMessage(e.response.bodyAsText()))
    } catch (e: ServerResponseException) {      // 5xx
        Result.Error(e, "Server error. Please try again.")
    } catch (e: HttpRequestTimeoutException) {
        Result.Error(e, "Request timed out.")
    } catch (e: IOException) {
        Result.Error(e, "Network unavailable. Check your connection.")
    } catch (e: SerializationException) {
        Result.Error(e, "Unexpected response format.")
    }
```

This lives here (not in `:core:network`) because repositories in `:core:data` call it.

## Rules

- **Pure Kotlin preferred.** Minimal Android dependencies (only what ConnectivityObserver and Koin require).
- **No network or data dependencies.** This module must not depend on `:core:network`, `:core:data`, or `:core:model`.
- Dependencies: `coroutines-core`, `coroutines-android`, Koin.
- Convention plugin: `librechat.mobile.library` + `librechat.mobile.koin`.
