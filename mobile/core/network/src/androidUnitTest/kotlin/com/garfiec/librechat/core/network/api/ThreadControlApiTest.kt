package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ThreadControlApiTest {

    private fun jsonClient(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(librechatJson) }
        defaultRequest {
            url("https://bridge.example.com")
            contentType(ContentType.Application.Json)
        }
    }

    @Test
    fun `queue state posts always carry a JSON object body`() = runTest {
        val requests = mutableListOf<Pair<String, String>>()
        val engine = MockEngine { request ->
            requests += request.url.encodedPath to String(request.body.toByteArray())
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = ThreadControlApi(jsonClient(engine))
        api.pauseQueue("thread-1")
        api.resumeQueue("thread-1")

        assertThat(requests.map { it.first }).containsExactly(
            "/api/threads/thread-1/queue/pause",
            "/api/threads/thread-1/queue/resume",
        ).inOrder()
        assertThat(requests.map { it.second }).containsExactly("{}", "{}")
    }

    @Test
    fun `steer post carries the active turn id`() = runTest {
        var body = ""
        val engine = MockEngine { request ->
            body = String(request.body.toByteArray())
            respond(
                content = """{"steered":true,"turnId":"turn-7"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = ThreadControlApi(jsonClient(engine))
            .steerQueued("thread-1", "message-1", "turn-7")

        assertThat(body).isEqualTo("""{"expectedTurnId":"turn-7"}""")
        assertThat(result.steered).isTrue()
    }
}
