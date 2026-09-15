package com.sample.demo.review

private class JvmInAppReviewBridge : InAppReviewBridge {
    override fun requestReview() {
        println("[InAppReviewBridge] Review requested (no-op on desktop)")
    }
}

actual fun getInAppReviewBridge(): InAppReviewBridge = JvmInAppReviewBridge()
