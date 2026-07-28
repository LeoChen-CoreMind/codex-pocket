package com.garfiec.librechat.core.data.repository

import java.io.File

internal actual fun deleteDirectoryRecursively(path: String) {
    File(path).deleteRecursively()
}
