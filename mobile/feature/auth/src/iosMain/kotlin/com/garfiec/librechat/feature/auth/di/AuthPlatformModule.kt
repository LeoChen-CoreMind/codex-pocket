package com.garfiec.librechat.feature.auth.di

import com.garfiec.librechat.feature.auth.oauth.IosOAuthLauncher
import com.garfiec.librechat.feature.auth.oauth.OAuthLauncher
import org.koin.core.module.Module
import org.koin.dsl.module

actual val authPlatformModule: Module = module {
    single<OAuthLauncher> { IosOAuthLauncher() }
}
