package com.infiltrate.review

actual object InAppReview {
    actual fun requestReview() {
        println("[InAppReview] Desktop/JVM review requested (no-op)")
    }
}
