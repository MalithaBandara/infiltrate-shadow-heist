package com.sample.demo.analytics

// No-op here, deliberately: unlike ContinueAdBridge/LevelExitBridge, this bridge has no UI-level
// action for a host Activity to react to via a callback - it's a fire-and-forget SDK call, so
// there's nothing to relay. :game's own separate Android target deliberately never depends on
// com.layers.sdk:layers-android directly - that artifact pulls in androidx.lifecycle/androidx.work
// 2.7.0+/2.9.0, both of which require compileSdk 34+, while :game's own build.gradle.kts stays on
// compileSdk 33 (bumping it is a separate, bigger change, out of scope here). The real
// implementation lives entirely in android-shell's own duplicate copy of this file
// (android-shell/.../AnalyticsBridge.kt, compileSdk 37, no conflict), which is what actually
// resolves getAnalyticsBridge() when GameplayScene.kt is compiled as part of that module - it
// compiles this file from source directly, not through :game's expect/actual mechanism at all
// (see android-shell/build.gradle.kts's sourceSets comment). This actual only matters for :game's
// own standalone Android target, which isn't the real shipped app.
private class AndroidAnalyticsBridge : AnalyticsBridge {
    override fun track(event: String, properties: Map<String, Any>) {}
}

actual fun getAnalyticsBridge(): AnalyticsBridge = AndroidAnalyticsBridge()
