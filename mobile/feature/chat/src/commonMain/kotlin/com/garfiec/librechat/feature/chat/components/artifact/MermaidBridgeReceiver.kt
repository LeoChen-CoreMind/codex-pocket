package com.garfiec.librechat.feature.chat.components.artifact

/**
 * Per-WebView SVG sink. Immutable after construction — one receiver per
 * (cache, key) pair, allocated inside the AndroidView/UIKitView factory at the
 * mount site under a `key(mermaidKey) { ... }` block. Content/theme change
 * destroys the entire view slot, taking this receiver with it; the new slot
 * gets a fresh receiver bound to the new key.
 *
 * Per-key allocation eliminates the stale-capture race (a receiver can never
 * outlive the key it was constructed with) and the mid-render content-swap
 * race (the destroyed WebView's JS can no longer reach this receiver).
 */
internal class MermaidBridgeReceiver(
    private val cache: MermaidRenderCache,
    private val key: String,
) {
    fun onSvg(svg: String) {
        cache.put(key, svg)
    }
}
