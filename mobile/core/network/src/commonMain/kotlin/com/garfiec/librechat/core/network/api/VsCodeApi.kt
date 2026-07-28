package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.FileObject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.parameter
import io.ktor.http.path
import kotlinx.serialization.Serializable

@Serializable
data class VsCodeInstanceDto(
    val instanceId: String,
    val editorName: String = "VS Code",
    val windowTitle: String,
    val workspaceName: String? = null,
    val workspaceFolders: List<String> = emptyList(),
    val processId: Int,
    val extensionHostPid: Int,
    val machineName: String,
    val vscodeVersion: String,
    val lastSeenAt: Long,
    val online: Boolean,
    val bound: Boolean,
)

@Serializable
data class OnlineConversationDto(
    val instanceId: String,
    val threadId: String,
    val title: String,
    val active: Boolean = false,
    val editorName: String,
    val windowTitle: String,
    val workspaceName: String? = null,
    val workspaceFolders: List<String> = emptyList(),
    val machineName: String,
    val lastSeenAt: Long,
    val bound: Boolean = false,
)

@Serializable
data class OnlineConversationsResponse(
    val data: List<OnlineConversationDto> = emptyList(),
    val boundInstanceId: String? = null,
)

@Serializable
data class ThreadInstructionsDto(val instructions: String? = null)

@Serializable
private data class UpdateThreadInstructionsRequest(val instructions: String?)

@Serializable
data class VsCodeInstancesResponse(
    val data: List<VsCodeInstanceDto> = emptyList(),
    val boundInstanceId: String? = null,
)

@Serializable
private data class VsCodeBindingRequest(val instanceId: String?)

@Serializable
private data class VsCodeCommandRequest(val type: String, val threadId: String? = null)

@Serializable
data class WorkspaceRootDto(val id: String, val name: String, val path: String)

@Serializable
data class WorkspaceRootsResponse(val data: List<WorkspaceRootDto> = emptyList())

@Serializable
data class WorkspaceEntryDto(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long? = null,
    val modifiedAt: String? = null,
    val type: String,
)

@Serializable
data class WorkspaceEntriesResponse(
    val root: WorkspaceRootDto,
    val path: String,
    val data: List<WorkspaceEntryDto> = emptyList(),
)

@Serializable
data class WorkspaceFileResponse(
    val root: WorkspaceRootDto,
    val path: String,
    val type: String,
    val size: Long,
    val binary: Boolean,
    val content: String? = null,
)

@Serializable
private data class OpenFileRequest(
    val type: String = "openFile",
    val rootId: String,
    val path: String,
)

@Serializable
private data class ImportWorkspaceFileRequest(
    val rootId: String,
    val path: String,
)

@Serializable
data class FtpStatusDto(
    val running: Boolean = false,
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = "",
    val root: String? = null,
)

@Serializable
private data class StartFtpRequest(val port: Int = 2121)

@Serializable
private data class EmptyRequest(val request: Boolean = true)

class VsCodeApi(
    private val client: HttpClient,
) {
    suspend fun getInstances(): VsCodeInstancesResponse =
        client.get { url { path("api/vscode/instances") } }.body()

    suspend fun getOnlineConversations(): OnlineConversationsResponse =
        client.get { url { path("api/vscode/online-conversations") } }.body()

    suspend fun bind(instanceId: String?): VsCodeInstancesResponse =
        client.post {
            url { path("api/vscode/bind") }
            setBody(VsCodeBindingRequest(instanceId))
        }.body()

    suspend fun newChat() {
        client.post {
            url { path("api/vscode/command") }
            setBody(VsCodeCommandRequest(type = "newChat"))
        }
    }

    suspend fun openThread(threadId: String) {
        client.post {
            url { path("api/vscode/command") }
            setBody(VsCodeCommandRequest(type = "openThread", threadId = threadId))
        }
    }

    suspend fun closeThread(instanceId: String, threadId: String) {
        client.post {
            url { path("api/vscode/instances/$instanceId/threads/$threadId/close") }
            setBody(EmptyRequest())
        }
    }

    suspend fun getThreadInstructions(threadId: String): ThreadInstructionsDto =
        client.get { url { path("api/convos/$threadId/instructions") } }.body()

    suspend fun updateThreadInstructions(threadId: String, instructions: String?): ThreadInstructionsDto =
        client.put {
            url { path("api/convos/$threadId/instructions") }
            setBody(UpdateThreadInstructionsRequest(instructions))
        }.body()

    suspend fun getWorkspaceRoots(): WorkspaceRootsResponse =
        client.get { url { path("api/workspace/roots") } }.body()

    suspend fun getWorkspaceEntries(rootId: String, pathValue: String): WorkspaceEntriesResponse =
        client.get {
            url { path("api/workspace/entries") }
            parameter("rootId", rootId)
            parameter("path", pathValue)
        }.body()

    suspend fun getWorkspaceFile(rootId: String, pathValue: String): WorkspaceFileResponse =
        client.get {
            url { path("api/workspace/file") }
            parameter("rootId", rootId)
            parameter("path", pathValue)
        }.body()

    suspend fun downloadWorkspaceFile(
        rootId: String,
        pathValue: String,
        onProgress: (receivedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): ByteArray = client.downloadBytesWithProgress(
        request = {
            url { path("api/workspace/raw") }
            parameter("rootId", rootId)
            parameter("path", pathValue)
        },
        onProgress = onProgress,
    )

    suspend fun openWorkspaceFile(rootId: String, pathValue: String) {
        client.post {
            url { path("api/vscode/command") }
            setBody(OpenFileRequest(rootId = rootId, path = pathValue))
        }
    }

    suspend fun importWorkspaceFile(rootId: String, pathValue: String): FileObject =
        client.post {
            url { path("api/workspace/import") }
            setBody(ImportWorkspaceFileRequest(rootId = rootId, path = pathValue))
        }.body()

    suspend fun getFtpStatus(): FtpStatusDto =
        client.get { url { path("api/ftp/status") } }.body()

    suspend fun startFtp(port: Int = 2121): FtpStatusDto =
        client.post {
            url { path("api/ftp/start") }
            setBody(StartFtpRequest(port))
        }.body()

    suspend fun stopFtp(): FtpStatusDto =
        client.post {
            url { path("api/ftp/stop") }
            setBody(EmptyRequest())
        }.body()
}
