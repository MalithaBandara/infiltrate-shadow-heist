package com.infiltrate.platform

actual object PlatformInfo {
    actual val versionName: String
        get() = System.getProperty("app.version.name")
            ?: PlatformInfo::class.java.`package`?.implementationVersion
            ?: "1.0.0"

    actual val buildNumber: String
        get() = System.getProperty("app.build.number") ?: "2026.1"
}
