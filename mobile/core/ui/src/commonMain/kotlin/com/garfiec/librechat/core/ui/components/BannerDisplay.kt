package com.garfiec.librechat.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.Banner

/**
 * Renders a vertical stack of dismissible server banners.
 *
 * Banners are filtered against [dismissedIds] so dismissed banners stay hidden.
 * Supports three visual types: "info" (blue), "warning" (amber), "error" (red).
 */
@Composable
fun BannerDisplay(
    banners: List<Banner>,
    dismissedIds: Set<String>,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleBanners = banners.filter { banner ->
        val id = banner.bannerId ?: return@filter false
        id !in dismissedIds
    }

    Column(modifier = modifier.fillMaxWidth()) {
        visibleBanners.forEach { banner ->
            val bannerId = banner.bannerId ?: return@forEach
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut(),
            ) {
                BannerCard(
                    banner = banner,
                    onDismiss = { onDismiss(bannerId) },
                )
            }
        }
    }
}

@Composable
private fun BannerCard(
    banner: Banner,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bannerType = banner.type ?: "info"
    val containerColor = when (bannerType) {
        "warning" -> Color(0xFFFFF3E0) // amber container
        "error" -> MaterialTheme.colorScheme.errorContainer
        else -> Color(0xFFE3F2FD) // blue container (info)
    }
    val contentColor = when (bannerType) {
        "warning" -> Color(0xFFE65100)
        "error" -> MaterialTheme.colorScheme.onErrorContainer
        else -> Color(0xFF0D47A1)
    }
    val icon = when (bannerType) {
        "warning" -> Icons.Default.Warning
        "error" -> Icons.Default.Warning
        else -> Icons.Default.Info
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = bannerType,
                tint = contentColor,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = banner.message ?: "",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss banner",
                    tint = contentColor,
                )
            }
        }
    }
}
