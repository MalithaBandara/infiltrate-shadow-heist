package com.sample.demo.review

private class JsInAppReviewBridge : InAppReviewBridge {
    override fun requestReview() {
        // No-op for JS preview
    }
}

actual fun getInAppReviewBridge(): InAppReviewBridge = JsInAppReviewBridge()
