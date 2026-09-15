package com.sample.demo.review

// Real Android bridge state. android-shell/MainActivity sets [onReviewRequested] at startup.
object AndroidInAppReviewBridgeState {
    var onReviewRequested: (() -> Unit)? = null

    fun requestReview() {
        onReviewRequested?.invoke()
    }
}

private class AndroidInAppReviewBridge : InAppReviewBridge {
    override fun requestReview() = AndroidInAppReviewBridgeState.requestReview()
}

actual fun getInAppReviewBridge(): InAppReviewBridge = AndroidInAppReviewBridge()
