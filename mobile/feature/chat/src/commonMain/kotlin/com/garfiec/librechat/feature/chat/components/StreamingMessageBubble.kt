package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.ui.components.AvatarImage
import com.garfiec.librechat.core.ui.components.endpointIconPainter
import com.garfiec.librechat.core.ui.components.isMonochromeEndpointIcon
import com.garfiec.librechat.feature.chat.viewmodel.StreamingPartType
import com.garfiec.librechat.feature.chat.viewmodel.StreamingTimelinePart

// Shared BubbleShape is imported from MessageBubble.kt

/**
 * Dedicated composable for rendering a message that is currently being streamed.
 * Extracted from MessageBubble.kt to avoid merge conflicts with WS4
 * (which modifies the action row in MessageBubble).
 *
 * Shows a blinking cursor at the end of the streaming content, or a standalone
 * blinking cursor when no content has arrived yet.
 */
@Composable
fun StreamingMessageBubble(
    streamingContent: String,
    senderName: String,
    senderIconUrl: String?,
    modifier: Modifier = Modifier,
    fontSizeMultiplier: Float = 1.0f,
    selectedEndpoint: String? = null,
    chatLayoutStyle: String = ChatLayoutConstants.THREAD,
    showAvatars: Boolean = true,
    showBubbles: Boolean = false,
    useKatex: Boolean = false,
    streamingTimeline: List<StreamingTimelinePart> = emptyList(),
    baseUrl: String = "",
    streamingAttachments: List<Attachment> = emptyList(),
    showImageDescriptions: Boolean = true,
) {
    if (chatLayoutStyle == ChatLayoutConstants.TWO_SIDED) {
        TwoSidedStreamingBubble(
            streamingContent = streamingContent,
            senderName = senderName,
            senderIconUrl = senderIconUrl,
            fontSizeMultiplier = fontSizeMultiplier,
            selectedEndpoint = selectedEndpoint,
            showAvatars = showAvatars,
            showBubbles = showBubbles,
            useKatex = useKatex,
            streamingTimeline = streamingTimeline,
            baseUrl = baseUrl,
            streamingAttachments = streamingAttachments,
            showImageDescriptions = showImageDescriptions,
            modifier = modifier,
        )
    } else {
        ThreadStreamingBubble(
            streamingContent = streamingContent,
            senderName = senderName,
            senderIconUrl = senderIconUrl,
            fontSizeMultiplier = fontSizeMultiplier,
            selectedEndpoint = selectedEndpoint,
            showAvatars = showAvatars,
            showBubbles = showBubbles,
            useKatex = useKatex,
            streamingTimeline = streamingTimeline,
            baseUrl = baseUrl,
            streamingAttachments = streamingAttachments,
            showImageDescriptions = showImageDescriptions,
            modifier = modifier,
        )
    }
}

@Composable
private fun ThreadStreamingBubble(
    streamingContent: String,
    senderName: String,
    senderIconUrl: String?,
    fontSizeMultiplier: Float,
    selectedEndpoint: String?,
    showAvatars: Boolean,
    showBubbles: Boolean,
    useKatex: Boolean,
    streamingTimeline: List<StreamingTimelinePart>,
    baseUrl: String,
    streamingAttachments: List<Attachment>,
    showImageDescriptions: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = 8.dp,
            ),
    ) {
        // Sender row with avatar
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showAvatars) {
                AvatarImage(
                    imageUrl = senderIconUrl,
                    fallbackText = senderName,
                    fallbackIconPainter = if (senderIconUrl == null) endpointIconPainter(selectedEndpoint) else null,
                    tintIcon = if (senderIconUrl == null) isMonochromeEndpointIcon(selectedEndpoint) else false,
                    size = 28.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = senderName,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Streaming content with blinking cursor
        val contentStartPadding = if (showAvatars) 36.dp else 0.dp
        Column(
            modifier = Modifier
                .padding(start = contentStartPadding)
                .fillMaxWidth()
                .then(
                    if (showBubbles) {
                        Modifier
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = BubbleShape,
                            )
                            .padding(12.dp)
                    } else {
                        Modifier
                    },
                )
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    val announcedText = streamingTimeline.joinToString("") { it.text }
                        .ifBlank { streamingContent }
                    contentDescription = if (announcedText.isNotBlank()) {
                        "Assistant is responding: $announcedText"
                    } else {
                        "Assistant is generating a response"
                    }
                },
        ) {
            StreamingTimelineContent(
                timeline = streamingTimeline,
                fallbackText = streamingContent,
                fontSizeMultiplier = fontSizeMultiplier,
                useKatex = useKatex,
                baseUrl = baseUrl,
                attachments = streamingAttachments,
                showImageDescriptions = showImageDescriptions,
            )
        }
    }
}

@Composable
private fun TwoSidedStreamingBubble(
    streamingContent: String,
    senderName: String,
    senderIconUrl: String?,
    fontSizeMultiplier: Float,
    selectedEndpoint: String?,
    showAvatars: Boolean,
    showBubbles: Boolean,
    useKatex: Boolean,
    streamingTimeline: List<StreamingTimelinePart>,
    baseUrl: String,
    streamingAttachments: List<Attachment>,
    showImageDescriptions: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = 8.dp,
                vertical = 4.dp,
            ),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        // Agent avatar on left
        if (showAvatars) {
            AvatarImage(
                imageUrl = senderIconUrl,
                fallbackText = senderName,
                fallbackIconPainter = if (senderIconUrl == null) endpointIconPainter(selectedEndpoint) else null,
                tintIcon = if (senderIconUrl == null) isMonochromeEndpointIcon(selectedEndpoint) else false,
                size = 28.dp,
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (showBubbles) {
                        Modifier
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = BubbleShape,
                            )
                            .padding(12.dp)
                    } else {
                        Modifier.padding(
                            horizontal = 4.dp,
                            vertical = 8.dp,
                        )
                    },
                )
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    val announcedText = streamingTimeline.joinToString("") { it.text }
                        .ifBlank { streamingContent }
                    contentDescription = if (announcedText.isNotBlank()) {
                        "Assistant is responding: $announcedText"
                    } else {
                        "Assistant is generating a response"
                    }
                },
        ) {
            Text(
                text = senderName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (showBubbles) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(modifier = Modifier.height(4.dp))
            StreamingTimelineContent(
                timeline = streamingTimeline,
                fallbackText = streamingContent,
                fontSizeMultiplier = fontSizeMultiplier,
                useKatex = useKatex,
                baseUrl = baseUrl,
                attachments = streamingAttachments,
                showImageDescriptions = showImageDescriptions,
            )
        }
    }
}

@Composable
private fun StreamingTimelineContent(
    timeline: List<StreamingTimelinePart>,
    fallbackText: String,
    fontSizeMultiplier: Float,
    useKatex: Boolean,
    baseUrl: String,
    attachments: List<Attachment>,
    showImageDescriptions: Boolean,
) {
    if (timeline.isEmpty()) {
        if (fallbackText.isNotBlank()) {
            MarkdownContent(
                text = fallbackText,
                fontSizeMultiplier = fontSizeMultiplier,
                useKatex = useKatex,
                streaming = true,
            )
        }
    } else {
        timeline.forEach { part ->
            when (part.type) {
                StreamingPartType.TEXT -> MarkdownContent(
                    text = part.text,
                    fontSizeMultiplier = fontSizeMultiplier,
                    useKatex = useKatex,
                    streaming = true,
                )
                StreamingPartType.THINKING -> ThinkingContentPart(
                    thinkingText = part.text,
                    fontSizeMultiplier = fontSizeMultiplier,
                    useKatex = useKatex,
                )
                StreamingPartType.TOOL -> part.toolCall?.let { toolCall ->
                    StreamingToolCallCard(
                        toolCall = toolCall,
                        modifier = Modifier.padding(vertical = 4.dp),
                        baseUrl = baseUrl,
                        streamingAttachments = attachments,
                        showImageDescriptions = showImageDescriptions,
                    )
                }
            }
        }
    }
    StreamingIndicator()
}
