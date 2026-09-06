package com.sample.demo.analytics

import com.layers.sdk.android.LayersAndroid

// Same interface/logic as src/AnalyticsBridge.kt + src@android/AnalyticsBridge.android.kt, but
// without expect/actual - this module isn't a Kotlin Multiplatform project (see
// android-shell/build.gradle.kts's sourceSets comment). Mirrors ContinueAdBridge.kt/
// LevelExitBridge.kt here. LayersAndroid.configure(...) happens once in
// InfiltrateApplication.onCreate(); this just forwards already-configured track() calls.
interface AnalyticsBridge {
    fun track(event: String, properties: Map<String, Any> = emptyMap())
}

private class AndroidAnalyticsBridge : AnalyticsBridge {
    override fun track(event: String, properties: Map<String, Any>) {
        LayersAndroid.track(event, properties)
    }
}

fun getAnalyticsBridge(): AnalyticsBridge = AndroidAnalyticsBridge()
