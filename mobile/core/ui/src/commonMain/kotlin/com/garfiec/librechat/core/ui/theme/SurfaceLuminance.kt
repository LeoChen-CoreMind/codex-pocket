package com.garfiec.librechat.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.luminance

/**
 * Single source of truth for "is the current surface dark?" so that inline-vs-modal
 * paths agree on the dark-mode bit they feed into WebView/HTML themes and caches.
 * Uses [androidx.compose.ui.graphics.luminance] (Rec.709, gamma-corrected) for
 * consistency across Android and iOS.
 */
@Composable
@ReadOnlyComposable
fun isSurfaceDark(): Boolean =
    MaterialTheme.colorScheme.surface.luminance() < 0.5f
