package com.garfiec.librechat.feature.files.di

import com.garfiec.librechat.feature.files.platform.AndroidFileReader
import com.garfiec.librechat.feature.files.platform.FileReader
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.Module
import org.koin.dsl.module

actual val filesPlatformModule: Module = module {
    single<FileReader> { AndroidFileReader(androidApplication()) }
}
