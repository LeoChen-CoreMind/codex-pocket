package com.garfiec.librechat.approval

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.network.api.InteractionAction
import com.garfiec.librechat.core.network.api.PendingInteractionDto
import com.garfiec.librechat.core.network.api.PendingMcpDialogDto
import com.garfiec.librechat.feature.chat.components.MarkdownContent
import com.garfiec.librechat.feature.chat.components.LocalChatConversationId
import com.garfiec.librechat.feature.chat.components.LocalParsedMarkdownCache
import com.garfiec.librechat.feature.chat.components.ParsedMarkdownCache
import coil3.compose.AsyncImage

@Composable
fun ApprovalInlineContent() {
    val context = LocalContext.current
    val state by ApprovalCoordinator.state.collectAsStateWithLifecycle()
    val conversationId = LocalChatConversationId.current
    val relevant = state.pending.filter { request ->
        conversationId == null || request.threadId == null || request.threadId == conversationId
    }
    val request = relevant.firstOrNull()
    if (request == null) return
    val submitting = state.submittingRequestId == request.requestId

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        if (request.method == "item/tool/requestUserInput") {
            UserInputPanel(
                request = request,
                queueSize = relevant.size,
                submitting = submitting,
                error = state.error,
                onSubmit = { answers -> ApprovalService.answer(context, request.requestId, answers) },
                onSkip = { ApprovalService.answer(context, request.requestId, emptyMap()) },
            )
        } else {
            PermissionApprovalPanel(
                request = request,
                queueSize = relevant.size,
                submitting = submitting,
                error = state.error,
                onAction = { action -> ApprovalService.respond(context, request.requestId, action) },
            )
        }
    }
}

@Composable
fun McpDialogGlobalOverlay() {
    val context = LocalContext.current
    val state by ApprovalCoordinator.state.collectAsStateWithLifecycle()
    val request = state.mcpPending.firstOrNull() ?: return
    val submitting = state.submittingRequestId == request.requestId
    val markdownCache = remember { ParsedMarkdownCache() }
    AlertDialog(
        onDismissRequest = {},
        title = null,
        text = {
            CompositionLocalProvider(LocalParsedMarkdownCache provides markdownCache) {
                McpDialogPanel(
                    request = request,
                    submitting = submitting,
                    error = state.error,
                    onSubmit = { text, choices ->
                        ApprovalService.respondMcp(context, request.requestId, "submit", text, choices)
                    },
                    onCancel = {
                        ApprovalService.respondMcp(context, request.requestId, "cancel", "", emptyList())
                    },
                )
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun McpDialogPanel(
    request: PendingMcpDialogDto,
    submitting: Boolean,
    error: String?,
    onSubmit: (String, List<String>) -> Unit,
    onCancel: () -> Unit,
) {
    var answer by remember(request.requestId) { mutableStateOf("") }
    val selected = remember(request.requestId) { mutableStateMapOf<String, Boolean>() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(request.title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        MarkdownContent(text = request.markdown, immediate = true)
        request.images.forEach { image ->
            Spacer(Modifier.height(10.dp))
            AsyncImage(
                model = image.url,
                contentDescription = image.alt.ifBlank { null },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 320.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit,
            )
            if (image.alt.isNotBlank()) {
                Text(image.alt, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (request.choices.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                request.choices.forEach { choice ->
                    FilterChip(
                        selected = selected[choice] == true,
                        onClick = { selected[choice] = selected[choice] != true },
                        enabled = !submitting,
                        label = { Text(choice) },
                    )
                }
            }
        }
        if (request.allowText) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = answer,
                onValueChange = { answer = it },
                enabled = !submitting,
                label = { Text("回复") },
                minLines = 2,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel, enabled = !submitting) { Text("取消") }
            TextButton(
                onClick = { onSubmit(answer.trim(), selected.filterValues { it }.keys.toList()) },
                enabled = !submitting && (request.allowText || selected.any { it.value } || request.choices.isEmpty()),
            ) { Text("提交") }
        }
    }
}

@Composable
private fun UserInputPanel(
    request: PendingInteractionDto,
    queueSize: Int,
    submitting: Boolean,
    error: String?,
    onSubmit: (Map<String, List<String>>) -> Unit,
    onSkip: () -> Unit,
) {
    val questions = remember(request.requestId, request.params) { request.userInputQuestions() }
    val selectedAnswers = remember(request.requestId) { mutableStateMapOf<String, String>() }
    val complete = questions.isNotEmpty() && questions.all { !selectedAnswers[it.id].isNullOrBlank() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        val title = request.approvalTitle()
        Text(
            if (queueSize > 1) "$title · $queueSize 项待处理" else title,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(10.dp))
        questions.forEachIndexed { index, question ->
            if (index > 0) Spacer(Modifier.height(16.dp))
            if (question.header.isNotBlank()) {
                Text(question.header, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
            }
            Text(question.question, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            question.options.forEach { option ->
                val selected = selectedAnswers[question.id] == option.label
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !submitting) { selectedAnswers[question.id] = option.label }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = null,
                        enabled = !submitting,
                    )
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(option.label, style = MaterialTheme.typography.bodyMedium)
                        if (option.description.isNotBlank()) {
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (question.isOther || question.options.isEmpty()) {
                val optionLabels = question.options.mapTo(HashSet()) { it.label }
                val value = selectedAnswers[question.id].orEmpty().takeUnless { it in optionLabels }.orEmpty()
                OutlinedTextField(
                    value = value,
                    onValueChange = { selectedAnswers[question.id] = it },
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (question.options.isEmpty()) "回答" else "其他") },
                    visualTransformation = if (question.isSecret) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    maxLines = 4,
                )
            }
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onSkip, enabled = !submitting) { Text("跳过") }
            TextButton(
                onClick = {
                    onSubmit(
                        selectedAnswers.mapValues { (_, answer) -> listOf(answer.trim()) }
                            .filterValues { answers -> answers.any(String::isNotBlank) },
                    )
                },
                enabled = !submitting && complete,
            ) {
                Text("提交")
            }
        }
    }
}

@Composable
private fun PermissionApprovalPanel(
    request: PendingInteractionDto,
    queueSize: Int,
    submitting: Boolean,
    error: String?,
    onAction: (InteractionAction) -> Unit,
) {
    val title = request.approvalTitle()
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp)) {
            Text(
                if (queueSize > 1) "$title · $queueSize 项待处理" else title,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(request.approvalDetail(), style = MaterialTheme.typography.bodyMedium)
            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TextButton(
                onClick = { onAction(InteractionAction.ACCEPT) },
                enabled = !submitting,
                modifier = Modifier.weight(1f),
            ) {
                Text("允许一次")
            }
            TextButton(
                onClick = { onAction(InteractionAction.ACCEPT_FOR_SESSION) },
                enabled = !submitting,
                modifier = Modifier.weight(1f),
            ) {
                Text("本次会话")
            }
            TextButton(
                onClick = { onAction(InteractionAction.DECLINE) },
                enabled = !submitting,
                modifier = Modifier.weight(1f),
            ) {
                Text("拒绝", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
