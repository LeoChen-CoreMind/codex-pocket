package com.garfiec.librechat.approval

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import com.garfiec.librechat.MainActivity
import com.garfiec.librechat.R
import com.garfiec.librechat.core.network.api.BridgeEventsApi
import com.garfiec.librechat.core.network.api.BridgeInteractionsApi
import com.garfiec.librechat.core.network.api.InteractionAction
import com.garfiec.librechat.core.network.api.McpDialogApi
import com.garfiec.librechat.core.network.api.PendingMcpDialogDto
import com.garfiec.librechat.core.network.api.PendingInteractionDto
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject

data class ApprovalQueueState(
    val pending: List<PendingInteractionDto> = emptyList(),
    val mcpPending: List<PendingMcpDialogDto> = emptyList(),
    val submittingRequestId: String? = null,
    val error: String? = null,
)

object ApprovalCoordinator {
    private val mutableState = MutableStateFlow(ApprovalQueueState())
    val state = mutableState.asStateFlow()

    internal fun update(value: ApprovalQueueState) {
        mutableState.value = value
    }

    internal fun clear() {
        mutableState.value = ApprovalQueueState()
    }
}

class ApprovalService : Service() {
    private val interactionsApi: BridgeInteractionsApi by inject()
    private val eventsApi: BridgeEventsApi by inject()
    private val mcpDialogApi: McpDialogApi by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val responseMutex = Mutex()
    private var pollingJob: Job? = null
    private var displayedRequestId: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(SERVICE_NOTIFICATION_ID, serviceNotification())
        pollingJob = scope.launch { pollInteractions() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestId = intent?.getStringExtra(EXTRA_REQUEST_ID)
        val action = intent?.getStringExtra(EXTRA_ACTION)?.let { encoded ->
            InteractionAction.entries.firstOrNull { it.name == encoded }
        }
        val answers = intent?.getStringExtra(EXTRA_ANSWERS)?.let { encoded ->
            runCatching { Json.decodeFromString<Map<String, List<String>>>(encoded) }.getOrNull()
        }
        val mcpResponse = intent?.getStringExtra(EXTRA_MCP_RESPONSE)?.let { encoded ->
            runCatching { Json.decodeFromString<McpResponseIntent>(encoded) }.getOrNull()
        }
        if (requestId != null) {
            when {
                mcpResponse != null -> scope.launch {
                    respondMcp(requestId, mcpResponse.action, mcpResponse.text, mcpResponse.selectedChoices)
                }
                answers != null -> scope.launch { answer(requestId, answers) }
                action != null -> scope.launch { respond(requestId, action) }
            }
        }
        // Login state owns service startup; do not let Android resurrect it after logout.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pollingJob?.cancel()
        scope.cancel()
        ApprovalCoordinator.clear()
        displayedRequestId?.let(::cancelRequestNotification)
        super.onDestroy()
    }

    private suspend fun pollInteractions() {
        var sequence = 0L
        while (currentCoroutineContext().isActive) {
            try {
                refreshPending()
                val events = eventsApi.poll(sequence)
                sequence = if (events.resync) events.currentSequence else maxOf(sequence, events.currentSequence)
                if (events.resync || events.data.any {
                        it.type == "interaction.requested" || it.type == "interaction.resolved" ||
                            it.type == "mcp.dialog.requested" || it.type == "mcp.dialog.resolved"
                    }
                ) {
                    refreshPending()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ClientRequestException) {
                if (error.response.status == HttpStatusCode.NotFound) {
                    Logger.i { "Bridge approval API is unavailable; stopping approval service" }
                    stopSelf()
                    return
                }
                reportPollingError(error)
            } catch (error: Exception) {
                reportPollingError(error)
            }
        }
    }

    private suspend fun reportPollingError(error: Exception) {
        Logger.w(error) { "Approval polling failed; retrying" }
        ApprovalCoordinator.update(ApprovalCoordinator.state.value.copy(error = error.message))
        delay(RETRY_DELAY_MS)
    }

    private suspend fun refreshPending() = refreshMutex.withLock {
        val pending = interactionsApi.pending().data
            .filter { it.method in SUPPORTED_METHODS }
            .sortedBy { it.createdAt }
        val mcpPending = mcpDialogApi.pending().data.sortedBy { it.createdAt }
        val current = ApprovalCoordinator.state.value
        ApprovalCoordinator.update(
            ApprovalQueueState(
                pending = pending,
                mcpPending = mcpPending,
                submittingRequestId = current.submittingRequestId?.takeIf { id ->
                    pending.any { it.requestId == id } || mcpPending.any { it.requestId == id }
                },
            ),
        )
        updateRequestNotification(pending.firstOrNull(), mcpPending.firstOrNull())
    }

    private suspend fun respondMcp(
        requestId: String,
        action: String,
        text: String,
        selectedChoices: List<String>,
    ) = responseMutex.withLock {
        val current = ApprovalCoordinator.state.value
        if (current.submittingRequestId != null || current.mcpPending.none { it.requestId == requestId }) return@withLock
        ApprovalCoordinator.update(
            current.copy(
                mcpPending = current.mcpPending.filterNot { it.requestId == requestId },
                submittingRequestId = requestId,
                error = null,
            ),
        )
        try {
            mcpDialogApi.respond(requestId, action, text, selectedChoices)
            refreshPending()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Logger.w(error) { "MCP dialog response was rejected or already resolved" }
            runCatching { refreshPending() }
            val refreshed = ApprovalCoordinator.state.value
            ApprovalCoordinator.update(refreshed.copy(
                submittingRequestId = null,
                error = error.message.takeIf { refreshed.mcpPending.any { it.requestId == requestId } },
            ))
        }
    }

    private suspend fun respond(requestId: String, action: InteractionAction) = responseMutex.withLock {
        val current = ApprovalCoordinator.state.value
        if (current.submittingRequestId != null || current.pending.none { it.requestId == requestId }) {
            return@withLock
        }
        ApprovalCoordinator.update(
            current.copy(
                pending = current.pending.filterNot { it.requestId == requestId },
                submittingRequestId = requestId,
                error = null,
            ),
        )
        try {
            interactionsApi.respond(requestId, action)
            refreshPending()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Logger.w(error) { "Approval response was rejected or already resolved" }
            val refresh = runCatching { refreshPending() }
            val refreshed = ApprovalCoordinator.state.value
            ApprovalCoordinator.update(if (refresh.isFailure) {
                current.copy(submittingRequestId = null, error = error.message)
            } else {
                refreshed.copy(
                    submittingRequestId = null,
                    error = error.message.takeIf { refreshed.pending.any { it.requestId == requestId } },
                )
            })
        }
    }

    private suspend fun answer(requestId: String, answers: Map<String, List<String>>) = responseMutex.withLock {
        val current = ApprovalCoordinator.state.value
        if (current.submittingRequestId != null || current.pending.none { it.requestId == requestId }) {
            return@withLock
        }
        ApprovalCoordinator.update(
            current.copy(
                pending = current.pending.filterNot { it.requestId == requestId },
                submittingRequestId = requestId,
                error = null,
            ),
        )
        try {
            interactionsApi.answer(requestId, answers)
            refreshPending()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Logger.w(error) { "User input response was rejected or already resolved" }
            val refresh = runCatching { refreshPending() }
            val refreshed = ApprovalCoordinator.state.value
            ApprovalCoordinator.update(if (refresh.isFailure) {
                current.copy(submittingRequestId = null, error = error.message)
            } else {
                refreshed.copy(
                    submittingRequestId = null,
                    error = error.message.takeIf { refreshed.pending.any { it.requestId == requestId } },
                )
            })
        }
    }

    private fun updateRequestNotification(request: PendingInteractionDto?, mcpRequest: PendingMcpDialogDto?) {
        val requestId = request?.requestId ?: mcpRequest?.requestId
        val previous = displayedRequestId
        if (requestId == null) {
            previous?.let(::cancelRequestNotification)
            displayedRequestId = null
            return
        }
        if (previous != null && previous != requestId) cancelRequestNotification(previous)
        displayedRequestId = requestId
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            REQUEST_NOTIFICATION_ID,
            if (request != null) requestNotification(request) else mcpRequestNotification(mcpRequest!!),
        )
    }

    private fun serviceNotification() = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher_foreground)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.approval_service_active))
        .setContentIntent(openAppIntent())
        .setOngoing(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    private fun requestNotification(request: PendingInteractionDto): android.app.Notification {
        val title = request.approvalTitle().removeSuffix("？")
        val detail = request.approvalDetail()
        val builder = NotificationCompat.Builder(this, REQUEST_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(detail.take(180))
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(openAppIntent())
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        if (request.method != "item/tool/requestUserInput") {
            builder
                .addAction(0, "允许一次", responseIntent(request.requestId, InteractionAction.ACCEPT, 1))
                .addAction(0, "本次会话允许", responseIntent(request.requestId, InteractionAction.ACCEPT_FOR_SESSION, 2))
                .addAction(0, "拒绝", responseIntent(request.requestId, InteractionAction.DECLINE, 3))
        }
        return builder.build()
    }

    private fun mcpRequestNotification(request: PendingMcpDialogDto): android.app.Notification =
        NotificationCompat.Builder(this, REQUEST_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(request.title)
            .setContentText(request.markdown.replace(Regex("[#*_`>\\[\\]]"), "").take(180))
            .setContentIntent(openAppIntent())
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun responseIntent(requestId: String, action: InteractionAction, suffix: Int): PendingIntent {
        val intent = Intent(this, ApprovalService::class.java)
            .putExtra(EXTRA_REQUEST_ID, requestId)
            .putExtra(EXTRA_ACTION, action.name)
        return PendingIntent.getService(
            this,
            requestId.hashCode() * 10 + suffix,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelRequestNotification(@Suppress("UNUSED_PARAMETER") requestId: String) {
        getSystemService(NotificationManager::class.java).cancel(REQUEST_NOTIFICATION_ID)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Codex 连接服务",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                REQUEST_CHANNEL_ID,
                "Codex 权限请求",
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    companion object {
        private const val SERVICE_CHANNEL_ID = "codex_approval_service"
        private const val REQUEST_CHANNEL_ID = "codex_approval_requests"
        private const val SERVICE_NOTIFICATION_ID = 4200
        private const val REQUEST_NOTIFICATION_ID = 4201
        private const val RETRY_DELAY_MS = 3_000L
        private const val EXTRA_REQUEST_ID = "request_id"
        private const val EXTRA_ACTION = "action"
        private const val EXTRA_ANSWERS = "answers"
        private const val EXTRA_MCP_RESPONSE = "mcp_response"
        private val SUPPORTED_METHODS = setOf(
            "item/commandExecution/requestApproval",
            "item/fileChange/requestApproval",
            "item/permissions/requestApproval",
            "item/tool/requestUserInput",
        )

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ApprovalService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ApprovalService::class.java))
            ApprovalCoordinator.clear()
        }

        fun respond(context: Context, requestId: String, action: InteractionAction) {
            val intent = Intent(context, ApprovalService::class.java)
                .putExtra(EXTRA_REQUEST_ID, requestId)
                .putExtra(EXTRA_ACTION, action.name)
            context.startService(intent)
        }

        fun answer(context: Context, requestId: String, answers: Map<String, List<String>>) {
            val intent = Intent(context, ApprovalService::class.java)
                .putExtra(EXTRA_REQUEST_ID, requestId)
                .putExtra(EXTRA_ANSWERS, Json.encodeToString(answers))
            context.startService(intent)
        }

        fun respondMcp(
            context: Context,
            requestId: String,
            action: String,
            text: String,
            selectedChoices: List<String>,
        ) {
            val payload = Json.encodeToString(McpResponseIntent(action, text, selectedChoices))
            val intent = Intent(context, ApprovalService::class.java)
                .putExtra(EXTRA_REQUEST_ID, requestId)
                .putExtra(EXTRA_MCP_RESPONSE, payload)
            context.startService(intent)
        }
    }
}

@kotlinx.serialization.Serializable
private data class McpResponseIntent(
    val action: String,
    val text: String,
    val selectedChoices: List<String>,
)
