package com.garfiec.librechat.feature.chat.components.web

import android.view.View
import android.webkit.WebView

/**
 * Configures and tears down a WebView that lives inside a Compose
 * [androidx.compose.ui.viewinterop.AndroidView] slot in a recycling container
 * (LazyColumn).
 *
 * **Why software-rendering:** when the WebView uses the default hardware-
 * accelerated path, HWUI inserts a `GLFunctorDrawable` into a parent's display
 * list. If Compose recycles the slot (LazyColumn scroll, theme-toggle
 * `key(...)` rebuild) while a draw frame is still queued, RenderThread tries
 * to render the functor whose backing `SkSurface` has been torn down →
 * SIGSEGV inside `SkSurface::getCanvas()`. Three deferred-destroy strategies
 * (handler.post, Choreographer.postFrameCallback, skip-destroy-entirely) all
 * still raced on Android 17 (tombstones captured 2026-05-13). The only fix
 * that fully closes the window is to never put the WebView on the GL functor
 * pipeline in the first place: `LAYER_TYPE_SOFTWARE` renders the WebView into
 * an off-screen bitmap that the parent display list owns, so there is no
 * shared GL state to race over.
 *
 * Inline mermaid/HTML artifacts are all simple DOM + CSS — software rendering
 * is performance-equivalent for these. For mermaid specifically, the SVG is
 * harvested via JS bridge after the first render and the cache hit path
 * never re-runs the WebView at all.
 */
internal fun configureLazyListWebView(webView: WebView) {
    webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
}

internal fun safelyDestroyWebView(webView: WebView) {
    webView.stopLoading()
    webView.destroy()
}
