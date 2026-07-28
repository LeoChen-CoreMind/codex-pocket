package com.garfiec.librechat.feature.agents.di

import com.garfiec.librechat.feature.agents.util.AndroidContentReader
import com.garfiec.librechat.feature.agents.util.ContentReader
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual val agentsPlatformModule: Module = module {
    single { AndroidContentReader(androidContext()) } bind ContentReader::class
}
