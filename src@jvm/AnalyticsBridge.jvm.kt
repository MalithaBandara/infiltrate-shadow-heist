package com.sample.demo.analytics

// Desktop JVM no-op stub - no Layers SDK on this platform. See AnalyticsBridge.kt.
class JvmAnalyticsBridge : AnalyticsBridge {
    override fun track(event: String, properties: Map<String, Any>) {
        println("[JvmAnalyticsBridge] track(\"$event\", $properties) - desktop JVM no-op stub")
    }
}

actual fun getAnalyticsBridge(): AnalyticsBridge = JvmAnalyticsBridge()
