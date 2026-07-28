package com.garfiec.librechat.approval

import com.garfiec.librechat.core.network.api.PendingInteractionDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

internal data class UserInputOption(
    val label: String,
    val description: String,
)

internal data class UserInputQuestion(
    val id: String,
    val header: String,
    val question: String,
    val isOther: Boolean,
    val isSecret: Boolean,
    val options: List<UserInputOption>,
)

internal fun PendingInteractionDto.approvalTitle(): String = when (method) {
    "item/commandExecution/requestApproval" -> "允许执行命令？"
    "item/fileChange/requestApproval" -> "允许修改文件？"
    "item/permissions/requestApproval" -> "允许访问权限？"
    "item/tool/requestUserInput" -> "Codex 需要您的确认"
    else -> "Codex 请求审批"
}

internal fun PendingInteractionDto.approvalDetail(): String {
    val source = params ?: return "等待处理"
    if (method == "item/tool/requestUserInput") {
        return userInputQuestions().firstOrNull()?.question ?: "请选择后继续"
    }
    if (method == "item/fileChange/requestApproval") {
        return source.fileChangeApprovalDetail()
    }
    val lines = buildList {
        source.stringValue("reason")?.let { add(it) }
        source.stringValue("command")?.let { add("命令：${it.compactCommand()}") }
        (source.stringValue("cwd") ?: source.stringValue("path"))?.let { add("位置：$it") }
        source["permissions"]?.readableValue()?.takeIf { it.isNotBlank() }?.let { add("权限：$it") }
    }
    return lines.distinct().joinToString("\n").ifBlank { method.substringAfterLast('/') }
}

private fun JsonObject.fileChangeApprovalDetail(): String {
    val sections = buildList {
        stringValue("reason")?.let(::add)
        (stringValue("cwd") ?: stringValue("path"))?.let { add("位置：$it") }
        val changes = get("changes") as? JsonArray
        changes.orEmpty().mapNotNullTo(this) { element ->
            val change = element as? JsonObject ?: return@mapNotNullTo null
            val path = change.stringValue("path") ?: return@mapNotNullTo null
            val kind = when (change.stringValue("kind")) {
                "add" -> "新增"
                "delete" -> "删除"
                else -> "编辑"
            }
            val header = "### $kind `$path`"
            val diff = change.stringValue("diff")
            if (diff.isNullOrBlank()) header else "$header\n\n```diff\n${diff.take(MAX_APPROVAL_DIFF_CHARS)}\n```"
        }
    }
    return sections.distinct().joinToString("\n\n").ifBlank { "等待文件修改详情" }
}

internal fun PendingInteractionDto.userInputQuestions(): List<UserInputQuestion> {
    val questions = params?.get("questions") as? JsonArray ?: return emptyList()
    return questions.mapNotNull { element ->
        val source = element as? JsonObject ?: return@mapNotNull null
        val id = source.stringValue("id") ?: return@mapNotNull null
        val options = (source["options"] as? JsonArray).orEmpty().mapNotNull { optionElement ->
            val option = optionElement as? JsonObject ?: return@mapNotNull null
            val label = option.stringValue("label") ?: return@mapNotNull null
            UserInputOption(
                label = label,
                description = option.stringValue("description").orEmpty(),
            )
        }
        UserInputQuestion(
            id = id,
            header = source.stringValue("header").orEmpty(),
            question = source.stringValue("question") ?: source.stringValue("header").orEmpty(),
            isOther = (source["isOther"] as? JsonPrimitive)?.booleanOrNull == true,
            isSecret = (source["isSecret"] as? JsonPrimitive)?.booleanOrNull == true,
            options = options,
        )
    }
}

private fun JsonObject.stringValue(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonElement.readableValue(): String = when (this) {
    is JsonPrimitive -> contentOrNull.orEmpty()
    is JsonArray -> joinToString("、") { it.readableValue() }
    is JsonObject -> entries.joinToString("、") { (key, value) -> "$key: ${value.readableValue()}" }
}

private fun String.compactCommand(): String {
    val normalized = replace(Regex("\\s+"), " ").trim()
    return if (normalized.length <= 360) normalized else normalized.take(357) + "..."
}

private const val MAX_APPROVAL_DIFF_CHARS = 48_000
