package com.garfiec.librechat.core.network.api

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable

internal suspend fun HttpClient.downloadBytesWithProgress(
    request: HttpRequestBuilder.() -> Unit,
    onProgress: (receivedBytes: Long, totalBytes: Long?) -> Unit,
): ByteArray {
    var output = ByteArray(64 * 1024)
    var size = 0
    prepareGet(request).execute { response ->
        val total = response.contentLength()
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(64 * 1024)
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buffer, 0, buffer.size)
            if (read < 0) break
            if (read == 0) continue
            if (size + read > output.size) {
                output = output.copyOf(maxOf(output.size * 2, size + read))
            }
            buffer.copyInto(output, destinationOffset = size, endIndex = read)
            size += read
            onProgress(size.toLong(), total)
        }
    }
    return output.copyOf(size)
}
