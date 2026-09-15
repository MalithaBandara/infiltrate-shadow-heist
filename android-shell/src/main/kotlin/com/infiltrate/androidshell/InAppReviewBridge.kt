package com.sample.demo.review

interface InAppReviewBridge {
    fun requestReview()
}

object AndroidInAppReviewBridgeState {
    var onReviewRequested: (() -> Unit)? = null

    fun requestReview() {
        onReviewRequested?.invoke()
    }
}

private class AndroidInAppReviewBridge : InAppReviewBridge {
    override fun requestReview() = AndroidInAppReviewBridgeState.requestReview()
}

fun getInAppReviewBridge(): InAppReviewBridge = AndroidInAppReviewBridge()
