package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.ToolAuthResult
import com.garfiec.librechat.core.network.api.AgentToolsApi
import com.garfiec.librechat.core.network.api.dto.UpdateUserPluginAuthRequest

class AgentToolsRepositoryImpl(
    private val agentToolsApi: AgentToolsApi,
) : AgentToolsRepository {

    override suspend fun verifyToolAuth(toolId: String): Result<ToolAuthResult> =
        safeApiCall { agentToolsApi.verifyToolAuth(toolId) }

    override suspend fun installToolKey(
        toolId: String,
        authFields: Map<String, String>,
    ): Result<Unit> = safeApiCall {
        agentToolsApi.installPluginAuth(
            UpdateUserPluginAuthRequest(
                pluginKey = toolId,
                action = "install",
                auth = authFields,
                isEntityTool = true,
            ),
        )
    }

    override suspend fun removeToolKey(
        toolId: String,
        authFieldNames: List<String>,
    ): Result<Unit> = safeApiCall {
        agentToolsApi.installPluginAuth(
            UpdateUserPluginAuthRequest(
                pluginKey = toolId,
                action = "uninstall",
                auth = authFieldNames.associateWith { null },
                isEntityTool = true,
            ),
        )
    }
}
