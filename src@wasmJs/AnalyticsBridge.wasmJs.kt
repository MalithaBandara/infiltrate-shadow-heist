package com.sample.demo.analytics

// No-op stub - Layers is only integrated on Android so far. See AnalyticsBridge.kt.
class WasmJsAnalyticsBridge : AnalyticsBridge {
    override fun track(event: String, properties: Map<String, Any>) {}
}

actual fun getAnalyticsBridge(): AnalyticsBridge = WasmJsAnalyticsBridge()
