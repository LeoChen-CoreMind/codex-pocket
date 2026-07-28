package com.garfiec.librechat.feature.chat.components.artifact

import android.webkit.JavascriptInterface

/**
 * Android thin adapter that exposes [MermaidBridgeReceiver.onSvg] to JavaScript
 * as `window.MermaidBridge.onSvg(svg)`. Registered via
 * `WebView.addJavascriptInterface(this, "MermaidBridge")` before `loadDataWithBaseURL`
 * so the binding is available when mermaid's IIFE runs.
 */
internal class MermaidJsBridge(private val receiver: MermaidBridgeReceiver) {
    @JavascriptInterface
    fun onSvg(svg: String) = receiver.onSvg(svg)
}
