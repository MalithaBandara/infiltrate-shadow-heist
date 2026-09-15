package com.sample.demo.review

/**
 * Bridge for prompting in-app reviews.
 * GameplayScene.kt calls [requestReview] after completing level 4.
 * Real implementations exist on Android (via Google Play In-App Review) and iOS (via StoreKit).
 */
interface InAppReviewBridge {
    fun requestReview()
}

expect fun getInAppReviewBridge(): InAppReviewBridge
