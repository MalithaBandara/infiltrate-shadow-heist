package com.infiltrate.platform

import platform.Foundation.NSBundle

actual object PlatformInfo {
    actual val versionName: String
        get() = (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String)
            ?.takeIf { it.isNotBlank() } ?: "1.0.0"

    actual val buildNumber: String
        get() = (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)
            ?.takeIf { it.isNotBlank() } ?: "1"
}
