package com.infiltrate.review

/**
 * Multiplatform in-app review prompt interface.
 * Prompts Google Play In-App Review on Android and StoreKit review dialog on iOS.
 */
expect object InAppReview {
    fun requestReview()
}
