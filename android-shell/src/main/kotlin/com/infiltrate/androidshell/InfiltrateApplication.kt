package com.infiltrate.androidshell

import android.app.Application
import android.content.pm.ApplicationInfo
import com.layers.sdk.android.Environment
import com.layers.sdk.android.LayersAndroid

/**
 * android-shell had no Application subclass before this - MainActivity.onCreate() was standing in
 * for process-startup work. Layers' own docs (layers.com/docs/sdk/installation) initialize in
 * Application.onCreate() specifically, and that's the correct place regardless: it runs exactly
 * once per process, before any Activity, so every track() call anywhere is guaranteed to hit an
 * already-configured SDK.
 *
 * Two things deliberately differ from the vendor's copy-paste example (decompiled the real
 * layers-android-3.3.0.aar to confirm both, not assumed):
 * - No manual `LayersAndroid.track("app_open")` call: `LayersConfigBuilder`'s real constructor
 *   defaults `autoTrackAppOpen = true`, so the SDK already sends this event itself. Calling
 *   track("app_open") here too would double-count every launch.
 * - `environment` is picked from FLAG_DEBUGGABLE rather than hardcoded to PRODUCTION: the
 *   vendor's snippet hardcodes Environment.PRODUCTION unconditionally, which would report every
 *   local debug build as real production data. `enableDebug` mirrors the same flag, matching the
 *   vendor's own advice to enable it during development.
 *
 * NOT set here, deliberately left as a follow-up: `consentRequired` (decompiled default: false -
 * the SDK does not currently gate tracking on consent, and this game has no consent-collection UI
 * to pair it with; the privacy policy already covers GDPR/EEA rights via Google's ad consent flow,
 * but Layers' own consent gating is a separate, not-yet-made product decision - see
 * .junie/guidelines.md).
 */
class InfiltrateApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val isDebugBuild = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        LayersAndroid.configure(this) {
            appId = "app_a1f9dbc126c1c779"
            environment = if (isDebugBuild) Environment.DEVELOPMENT else Environment.PRODUCTION
            enableDebug = isDebugBuild
        }
    }
}
