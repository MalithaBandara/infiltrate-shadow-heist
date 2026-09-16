package com.infiltrate.platform

/**
 * Exposes the application version name and build number dynamically per platform.
 */
expect object PlatformInfo {
    val versionName: String
    val buildNumber: String
}
