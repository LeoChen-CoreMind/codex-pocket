package com.garfiec.librechat.core.network.di

import com.garfiec.librechat.core.common.di.KoinQualifiers
import com.garfiec.librechat.core.network.sse.SseHttpTransport
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.core.module.Module
import org.koin.dsl.module

actual val networkPlatformModule: Module = module {
    single<HttpClientEngineFactory<*>> { OkHttp }
    single { SseHttpTransport(get(KoinQualifiers.Streaming)) }
}
