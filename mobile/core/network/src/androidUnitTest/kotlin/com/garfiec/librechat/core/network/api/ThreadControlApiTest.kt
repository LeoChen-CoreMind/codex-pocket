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
    fun `cancel queued delete carries a JSON object body`() = runTest {
        var path = ""
        var body = ""
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            body = String(request.body.toByteArray())
            respond(
                content = """{"cancelled":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        ThreadControlApi(jsonClient(engine)).cancelQueued("thread-1", "message-1")

        assertThat(path).isEqualTo("/api/threads/thread-1/queue/message-1")
        assertThat(body).isEqualTo("{}")
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

    @Test
    fun `submit serializes steer and uses queue as the wire default`() = runTest {
        val bodies = mutableListOf<String>()
        val engine = MockEngine { request ->
            bodies += String(request.body.toByteArray())
            respond(
                content = """{"status":"queued"}""",
                status = HttpStatusCode.Accepted,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = ThreadControlApi(jsonClient(engine))

        api.submit(
            "thread-1",
            SubmitThreadMessageRequest(
                clientMessageId = "steer-1",
                text = "guide now",
                deliveryMode = DeliveryMode.STEER,
                expectedTurnId = "turn-7",
            ),
        )
        api.submit(
            "thread-1",
            SubmitThreadMessageRequest(
                clientMessageId = "queue-1",
                text = "wait until done",
                deliveryMode = DeliveryMode.QUEUE,
                expectedTurnId = "turn-7",
            ),
        )

        assertThat(bodies[0]).contains("\"deliveryMode\":\"steer\"")
        assertThat(bodies[0]).contains("\"expectedTurnId\":\"turn-7\"")
        // Production JSON omits default values; the Bridge schema defaults an omitted mode to queue.
        assertThat(bodies[1]).doesNotContain("\"deliveryMode\"")
    }

    @Test
    fun `retry policy serializes all user settings`() = runTest {
        var path = ""
        var body = ""
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            body = String(request.body.toByteArray())
            respond(
                content = """{"policy":{"enabled":true,"maxRetries":7,"untilSuccess":true,"delaySeconds":9,"retryPrompt":"continue safely"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val saved = ThreadControlApi(jsonClient(engine)).updateRetryPolicy(
            "thread-1",
            RetryPolicyDto(
                enabled = true,
                maxRetries = 7,
                untilSuccess = true,
                delaySeconds = 9,
                retryPrompt = "continue safely",
            ),
        )

        assertThat(path).isEqualTo("/api/threads/thread-1/retry")
        assertThat(body).contains("\"maxRetries\":7")
        assertThat(body).contains("\"untilSuccess\":true")
        assertThat(body).contains("\"delaySeconds\":9")
        assertThat(body).contains("\"retryPrompt\":\"continue safely\"")
        assertThat(saved.untilSuccess).isTrue()
    }

    @Test
    fun `retry policy includes fields that equal client defaults`() = runTest {
        var body = ""
        val engine = MockEngine { request ->
            body = String(request.body.toByteArray())
            respond(
                content = """{"policy":{"enabled":false,"maxRetries":3,"untilSuccess":false,"delaySeconds":5,"retryPrompt":"$DEFAULT_RETRY_PROMPT"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        ThreadControlApi(jsonClient(engine)).updateRetryPolicy("thread-1", RetryPolicyDto())

        assertThat(body).contains("\"enabled\":false")
        assertThat(body).contains("\"maxRetries\":3")
        assertThat(body).contains("\"untilSuccess\":false")
        assertThat(body).contains("\"delaySeconds\":5")
        assertThat(body).contains("\"retryPrompt\"")
    }

    @Test
    fun `cancel retry post carries a JSON object body`() = runTest {
        var body = ""
        val engine = MockEngine { request ->
            body = String(request.body.toByteArray())
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        ThreadControlApi(jsonClient(engine)).cancelRetry("thread-1")

        assertThat(body).isEqualTo("{}")
    }
}
