package com.garfiec.librechat.approval

import com.garfiec.librechat.core.network.api.PendingInteractionDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalPresentationTest {
    @Test
    fun `file approval displays every file and its diff`() {
        val params = Json.parseToJsonElement(
            """
            {
              "reason": "需要修复同步逻辑",
              "changes": [
                {
                  "path": "bridge/src/index.ts",
                  "kind": "update",
                  "diff": "-old line\n+new line"
                },
                {
                  "path": "vscode-companion/extension.js",
                  "kind": "delete",
                  "diff": "-refreshThread()"
                }
              ]
            }
            """.trimIndent(),
        ).jsonObject
        val request = PendingInteractionDto(
            requestId = "approval-1",
            method = "item/fileChange/requestApproval",
            params = params,
            createdAt = 1L,
        )

        val detail = request.approvalDetail()

        assertTrue(detail.contains("需要修复同步逻辑"))
        assertTrue(detail.contains("编辑 `bridge/src/index.ts`"))
        assertTrue(detail.contains("删除 `vscode-companion/extension.js`"))
        assertTrue(detail.contains("-old line\n+new line"))
        assertTrue(detail.contains("-refreshThread()"))
    }
}
