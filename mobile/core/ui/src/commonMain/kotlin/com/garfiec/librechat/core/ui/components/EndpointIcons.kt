package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.EModelEndpoint
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.ic_agents
import com.garfiec.librechat.core.ui.resources.ic_anthropic
import com.garfiec.librechat.core.ui.resources.ic_azure
import com.garfiec.librechat.core.ui.resources.ic_bedrock
import com.garfiec.librechat.core.ui.resources.ic_google
import com.garfiec.librechat.core.ui.resources.ic_openai
import org.jetbrains.compose.resources.painterResource

/**
 * Returns a Painter for the given endpoint's brand icon, or null if none is available.
 */
@Composable
fun endpointIconPainter(endpoint: EModelEndpoint): Painter? = when (endpoint) {
    EModelEndpoint.OPENAI -> painterResource(Res.drawable.ic_openai)
    EModelEndpoint.AZURE_OPENAI -> painterResource(Res.drawable.ic_azure)
    EModelEndpoint.GOOGLE -> painterResource(Res.drawable.ic_google)
    EModelEndpoint.ANTHROPIC -> painterResource(Res.drawable.ic_anthropic)
    EModelEndpoint.BEDROCK -> painterResource(Res.drawable.ic_bedrock)
    EModelEndpoint.AGENTS -> painterResource(Res.drawable.ic_agents)
    EModelEndpoint.ASSISTANTS -> painterResource(Res.drawable.ic_openai)
    EModelEndpoint.AZURE_ASSISTANTS -> painterResource(Res.drawable.ic_azure)
    EModelEndpoint.CUSTOM -> null
}

/**
 * String-based variant of [endpointIconPainter].
 */
@Composable
fun endpointIconPainter(endpoint: String?): Painter? = when (endpoint) {
    "openAI" -> painterResource(Res.drawable.ic_openai)
    "azureOpenAI" -> painterResource(Res.drawable.ic_azure)
    "google" -> painterResource(Res.drawable.ic_google)
    "anthropic" -> painterResource(Res.drawable.ic_anthropic)
    "bedrock" -> painterResource(Res.drawable.ic_bedrock)
    "agents" -> painterResource(Res.drawable.ic_agents)
    "assistants" -> painterResource(Res.drawable.ic_openai)
    "azureAssistants" -> painterResource(Res.drawable.ic_azure)
    else -> null
}

/**
 * Returns true if the endpoint icon is monochrome and should be tinted with
 * the theme's onSurfaceVariant color. Brand-colored icons (Anthropic, Google,
 * Azure, Bedrock) should be rendered with [Color.Unspecified] to preserve their colors.
 */
fun isMonochromeEndpointIcon(endpoint: String?): Boolean = when (endpoint) {
    "openAI", "assistants", "agents" -> true
    else -> false
}

/**
 * Material icon fallback for endpoint names that don't have a bundled drawable.
 * Used by both the model picker and the conversation/drawer rows so unknown endpoint
 * names render consistently across surfaces.
 */
internal fun endpointFallbackIconVector(endpointName: String?): ImageVector = when (endpointName) {
    EndpointConstants.AGENTS -> Icons.Outlined.Create
    "assistants", "azureAssistants" -> Icons.Outlined.AutoAwesome
    else -> Icons.Outlined.SmartToy
}

/**
 * Renders an endpoint icon, preferring a remote URL when supplied (for custom endpoints
 * with `iconURL` configured) and falling back to the bundled brand glyph or a Material
 * icon when no URL or brand drawable is available.
 *
 * Mirrors the web frontend's `URLIcon`/`MinimalIcon` precedence (without the red
 * AlertCircle overlay on load failure — mobile silently falls back to the glyph).
 *
 * [glyphTint] colors monochrome bundled glyphs and the unknown-endpoint Material fallback.
 * Brand-colored painters (Anthropic, Google, Azure, Bedrock) ignore this and render with
 * their own colors. The size in [modifier] is overridden by the [size] parameter.
 */
@Composable
fun EndpointIcon(
    endpointName: String?,
    iconUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    contentDescription: String? = null,
    glyphTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    var loadFailed by remember(iconUrl) { mutableStateOf(false) }
    if (iconUrl != null && !loadFailed) {
        AsyncImage(
            model = iconUrl,
            contentDescription = contentDescription,
            modifier = modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
            onError = { loadFailed = true },
        )
    } else {
        EndpointGlyph(endpointName, size, contentDescription, glyphTint, modifier)
    }
}

@Composable
private fun EndpointGlyph(
    endpointName: String?,
    size: Dp,
    contentDescription: String?,
    glyphTint: Color,
    modifier: Modifier = Modifier,
) {
    val painter = endpointIconPainter(endpointName)
    if (painter != null) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            modifier = modifier.size(size),
            tint = if (isMonochromeEndpointIcon(endpointName)) glyphTint else Color.Unspecified,
        )
    } else {
        Icon(
            imageVector = endpointFallbackIconVector(endpointName),
            contentDescription = contentDescription,
            modifier = modifier.size(size),
            tint = glyphTint,
        )
    }
}
