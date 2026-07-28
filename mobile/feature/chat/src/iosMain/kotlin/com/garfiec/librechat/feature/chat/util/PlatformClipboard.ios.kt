package com.garfiec.librechat.feature.chat.util

import platform.UIKit.UIPasteboard

actual fun copyToClipboard(text: String, label: String) {
    UIPasteboard.generalPasteboard.string = text
}
