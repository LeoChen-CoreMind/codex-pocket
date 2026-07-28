package com.garfiec.librechat.core.common.di

import com.garfiec.librechat.core.common.AppInfo
import com.garfiec.librechat.core.common.IosAppInfo
import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.common.network.IosConnectivityObserver
import org.koin.core.module.Module
import org.koin.dsl.module

actual val commonPlatformModule: Module = module {
    single<ConnectivityObserver> { IosConnectivityObserver() }
    single<AppInfo> { IosAppInfo() }
}
