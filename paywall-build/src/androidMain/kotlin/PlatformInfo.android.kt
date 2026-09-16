package com.infiltrate.platform

import android.content.Context
import android.os.Build

actual object PlatformInfo {
    @Volatile
    private var cachedVersionName: String? = null
    @Volatile
    private var cachedBuildNumber: String? = null

    fun init(context: Context) {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            cachedVersionName = pInfo.versionName
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            cachedBuildNumber = code.toString()
        } catch (_: Throwable) {
        }
    }

    actual val versionName: String
        get() = cachedVersionName ?: "1.0.0"

    actual val buildNumber: String
        get() = cachedBuildNumber ?: "1"
}
