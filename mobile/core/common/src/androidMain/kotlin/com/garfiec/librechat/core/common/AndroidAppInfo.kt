package com.garfiec.librechat.core.common

import android.content.Context
import android.os.Build

internal class AndroidAppInfo(context: Context) : AppInfo {
    private val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

    override val versionName: String = packageInfo.versionName ?: "unknown"

    override val versionCode: Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

    override val gitSha: String = BuildConfig.GIT_SHA
}
