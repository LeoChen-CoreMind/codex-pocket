package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.ToolAuthResult

interface AgentToolsRepository {

    /** Calls upstream `GET /api/agents/tools/:toolId/auth`. */
    suspend fun verifyToolAuth(toolId: String): Result<ToolAuthResult>

    /**
     * Installs a per-tool user API key via `POST /api/user/plugins`. [authFields]
     * is the upstream auth-field map -- e.g. `LIBRECHAT_CODE_API_KEY -> key` for
     * Code Interpreter. Caller is responsible for assembling the right field
     * names for each tool.
     */
    suspend fun installToolKey(
        toolId: String,
        authFields: Map<String, String>,
    ): Result<Unit>

    /** Revokes previously installed keys for [toolId] via the same endpoint. */
    suspend fun removeToolKey(
        toolId: String,
        authFieldNames: List<String>,
    ): Result<Unit>
}
