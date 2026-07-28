package com.garfiec.librechat.core.common.di

import com.garfiec.librechat.core.common.AndroidAppInfo
import com.garfiec.librechat.core.common.AppInfo
import com.garfiec.librechat.core.common.network.AndroidConnectivityObserver
import com.garfiec.librechat.core.common.network.ConnectivityObserver
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val commonPlatformModule: Module = module {
    single<ConnectivityObserver> { AndroidConnectivityObserver(androidContext()) }
    single<AppInfo> { AndroidAppInfo(androidContext()) }
}
