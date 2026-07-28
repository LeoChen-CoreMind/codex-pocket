package com.garfiec.librechat.core.network.sse

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// Use prepareGet + execute to stream the response incrementally.
// client.get() buffers the entire body in memory before returning,
// which defeats SSE streaming. prepareGet().execute {} keeps the
// HTTP connection open so we can read line-by-line as data arrives.
//
// This was the original Android workaround introduced in commit f182b2b.
// It addresses Ktor-layer buffering only — sufficient for OkHttp on
// Android but NOT for Darwin/NSURLSession on iOS, which has a separate
// text/* response-buffering quirk at the OS network stack level. See
// SseHttpTransport.ios.kt for why iOS needs a totally different transport
// and what NSURLSession does wrong.
actual class SseHttpTransport(
    private val client: HttpClient,
) {
    actual fun stream(streamPath: String, resume: Boolean): Flow<ByteArray> = flow {
        client.prepareGet {
            url { path(streamPath) }
            accept(ContentType.Text.EventStream)
            if (resume) {
                parameter("resume", "true")
            }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw SseHttpStatusException(response.status.value)
            }
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(BUFFER_SIZE)
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read > 0) {
                    emit(buffer.copyOf(read))
                } else if (read < 0) {
                    break
                }
            }
        }
    }

    private companion object {
        const val BUFFER_SIZE = 8192
    }
}
