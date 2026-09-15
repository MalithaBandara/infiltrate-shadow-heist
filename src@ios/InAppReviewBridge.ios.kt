package com.sample.demo.review

import kotlin.native.ObjCName

@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "GameInAppReviewBridge", exact = true)
object GameInAppReviewBridge {
    var reviewRequested: Boolean = false
        private set

    fun requestReview() {
        reviewRequested = true
    }

    fun consumeReviewRequest(): Boolean {
        if (!reviewRequested) return false
        reviewRequested = false
        return true
    }
}

private class IosInAppReviewBridge : InAppReviewBridge {
    override fun requestReview() = GameInAppReviewBridge.requestReview()
}

actual fun getInAppReviewBridge(): InAppReviewBridge = IosInAppReviewBridge()
