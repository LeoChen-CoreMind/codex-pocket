package com.garfiec.librechat.core.network.sse

import kotlinx.coroutines.flow.Flow

/**
 * Platform abstraction for the SSE GET transport.
 *
 * Exists solely to let iOS bypass NSURLSession's text-star response buffering
 * (`text/event-stream`, `text/plain`, etc.). NSURLSession does not call
 * `didReceiveData` for those Content-Types until ~512 bytes have been received
 * OR the connection closes — see KTOR-6378 "Darwin: The engine doesn't stream
 * chunked responses with small chunks" (status: Unresolved) and Apple Developer
 * Forums thread 64875 (open since 2016).
 *
 * Android and iOS implementations look totally different by design:
 *  - Android wraps the existing Ktor `prepareGet + execute` path around the
 *    shared `KoinQualifiers.Streaming` HttpClient. Behavior is unchanged.
 *  - iOS uses a raw `NWConnection` HTTP/1.1 client that avoids NSURLSession
 *    entirely for the SSE GET only. All other iOS HTTP traffic continues to
 *    use the Darwin engine, which works fine for non-text-star responses.
 *
 * Implementations emit byte chunks exactly as they arrive on the wire; all
 * retry, connectivity, and SSE line parsing logic stays in [SseClient].
 *
 * Non-success HTTP responses (anything other than 2xx) are surfaced as a
 * thrown [SseHttpStatusException] so [SseClient] can differentiate 401 /
 * 404 / other the same way it did when it held the raw Ktor response.
 */
expect class SseHttpTransport {
    fun stream(streamPath: String, resume: Boolean): Flow<ByteArray>
}

/**
 * Thrown by an [SseHttpTransport] when the server responds with a non-success
 * status code. Preserves the numeric status so [SseClient] can branch on 401 /
 * 404 / other exactly as it did before the transport abstraction was extracted.
 */
class SseHttpStatusException(
    val statusCode: Int,
    message: String = "SSE transport received HTTP $statusCode",
) : Exception(message)
