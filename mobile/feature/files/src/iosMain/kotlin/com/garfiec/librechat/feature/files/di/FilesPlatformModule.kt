package com.garfiec.librechat.feature.files.di

import com.garfiec.librechat.feature.files.platform.FileReader
import com.garfiec.librechat.feature.files.platform.IosFileReader
import org.koin.core.module.Module
import org.koin.dsl.module

actual val filesPlatformModule: Module = module {
    single<FileReader> { IosFileReader() }
}
