package com.sample.demo.review

private class WasmJsInAppReviewBridge : InAppReviewBridge {
    override fun requestReview() {
        // No-op for WasmJs preview
    }
}

actual fun getInAppReviewBridge(): InAppReviewBridge = WasmJsInAppReviewBridge()
