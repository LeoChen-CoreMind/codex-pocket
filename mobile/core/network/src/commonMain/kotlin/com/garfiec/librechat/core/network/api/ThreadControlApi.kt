package com.garfiec.librechat.core.network.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.path
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DeliveryMode {
    @SerialName("queue") QUEUE,
    @SerialName("steer") STEER,
}

@Serializable
data class QueuedMessageDto(
    val clientMessageId: String,
    val text: String,
    val mode: String = "default",
    val model: String? = null,
    val reasoningEffort: String? = null,
    val approvalMode: String = "request",
    val enqueuedAt: Long = 0,
)

@Serializable
data class ThreadActivityDto(
    val active: Boolean = false,
    val turnId: String? = null,
    val source: String? = null,
    val steerable: Boolean = false,
    val queuePaused: Boolean = false,
    val queue: List<QueuedMessageDto> = emptyList(),
    val retryPolicy: RetryPolicyDto = RetryPolicyDto(),
    val retryStatus: RetryStatusDto? = null,
)

@Serializable
data class RetryPolicyDto(
    val enabled: Boolean = false,
    val maxRetries: Int = 3,
    val untilSuccess: Boolean = false,
    val delaySeconds: Int = 5,
    val retryPrompt: String = DEFAULT_RETRY_PROMPT,
)

@Serializable
data class RetryStatusDto(
    val state: String,
    val turnId: String,
    val turnStatus: String,
    val reason: String,
    val retryCount: Int = 0,
    val scheduledAt: Long? = null,
)

@Serializable
data class RetryPolicyResponse(val policy: RetryPolicyDto)

@Serializable
data class SubmitThreadMessageRequest(
    val clientMessageId: String,
    val text: String,
    val mode: String = "default",
    val model: String? = null,
    val reasoningEffort: String? = null,
    val fullAccess: Boolean = false,
    val fileIds: List<String> = emptyList(),
    val deliveryMode: DeliveryMode = DeliveryMode.QUEUE,
    val expectedTurnId: String? = null,
)

@Serializable
data class SubmitThreadMessageResponse(
    val status: String,
    val position: Int? = null,
    val turnId: String? = null,
    val fallbackReason: String? = null,
)

@Serializable
data class InterruptThreadRequest(val expectedTurnId: String? = null)

@Serializable
data class InterruptThreadResponse(
    val interrupted: Boolean,
    val stale: Boolean = false,
    val source: String? = null,
)

@Serializable
data class SteerQueuedMessageResponse(
    val steered: Boolean,
    val reason: String? = null,
    val turnId: String? = null,
)

@Serializable
private data class QueueTextRequest(val text: String)

@Serializable
private data class QueueOrderRequest(val clientMessageIds: List<String>)

@Serializable
private class EmptyJsonRequest

class ThreadControlApi(private val client: HttpClient) {
    suspend fun activity(threadId: String): ThreadActivityDto =
        client.get { url { path("api/threads/$threadId/activity") } }.body()

    suspend fun submit(threadId: String, request: SubmitThreadMessageRequest): SubmitThreadMessageResponse =
        client.post {
            url { path("api/threads/$threadId/messages") }
            setBody(request)
        }.body()

    suspend fun interrupt(threadId: String, expectedTurnId: String?): InterruptThreadResponse =
        client.post {
            url { path("api/threads/$threadId/interrupt") }
            setBody(InterruptThreadRequest(expectedTurnId))
        }.body()

    suspend fun updateQueued(threadId: String, clientMessageId: String, text: String) {
        client.patch {
            url { path("api/threads/$threadId/queue/$clientMessageId") }
            setBody(QueueTextRequest(text))
        }
    }

    suspend fun cancelQueued(threadId: String, clientMessageId: String) {
        client.delete {
            url { path("api/threads/$threadId/queue/$clientMessageId") }
            setBody(EmptyJsonRequest())
        }
    }

    suspend fun steerQueued(
        threadId: String,
        clientMessageId: String,
        expectedTurnId: String,
    ): SteerQueuedMessageResponse = client.post {
        url { path("api/threads/$threadId/queue/$clientMessageId/steer") }
        setBody(InterruptThreadRequest(expectedTurnId))
    }.body()

    suspend fun reorderQueue(threadId: String, clientMessageIds: List<String>) {
        client.put {
            url { path("api/threads/$threadId/queue/order") }
            setBody(QueueOrderRequest(clientMessageIds))
        }
    }

    suspend fun pauseQueue(threadId: String): ThreadActivityDto =
        client.post {
            url { path("api/threads/$threadId/queue/pause") }
            setBody(EmptyJsonRequest())
        }.body()

    suspend fun resumeQueue(threadId: String): ThreadActivityDto =
        client.post {
            url { path("api/threads/$threadId/queue/resume") }
            setBody(EmptyJsonRequest())
        }.body()

    suspend fun updateRetryPolicy(threadId: String, policy: RetryPolicyDto): RetryPolicyDto =
        client.put {
            url { path("api/threads/$threadId/retry") }
            setBody(policy)
        }.body<RetryPolicyResponse>().policy

    suspend fun cancelRetry(threadId: String): ThreadActivityDto =
        client.post {
            url { path("api/threads/$threadId/retry/cancel") }
            setBody(EmptyJsonRequest())
        }.body()
}

const val DEFAULT_RETRY_PROMPT =
    "刚才的任务异常中断。请先检查当前会话和工作区状态，从中断处继续，避免重复已经完成的操作。"
