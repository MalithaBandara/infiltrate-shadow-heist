package com.infiltrate.review

import kotlinx.cinterop.ExperimentalForeignApi
import platform.StoreKit.SKStoreReviewController
import platform.UIKit.UIApplication
import platform.UIKit.UIWindowScene

actual object InAppReview {
    @OptIn(ExperimentalForeignApi::class)
    actual fun requestReview() {
        try {
            val scenes = UIApplication.sharedApplication.connectedScenes
            var targetScene: UIWindowScene? = null
            for (item in scenes) {
                val windowScene = item as? UIWindowScene
                if (windowScene != null) {
                    targetScene = windowScene
                    break
                }
            }
            if (targetScene != null) {
                SKStoreReviewController.requestReviewInScene(targetScene)
            } else {
                SKStoreReviewController.requestReview()
            }
        } catch (t: Throwable) {
            println("[InAppReview] iOS StoreKit review error: ${t.message}")
        }
    }
}
