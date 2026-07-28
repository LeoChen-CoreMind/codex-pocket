package com.garfiec.librechat.shared

/**
 * Android-side shared module marker.
 * The Android app uses its own Koin modules directly (NetworkModule, DataModule, etc.)
 * rather than going through the shared SDK facade.
 * This source set exists for compilation symmetry with iosMain.
 */
