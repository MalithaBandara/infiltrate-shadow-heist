package com.infiltrate.billing

/**
 * JVM desktop stub for StoreBilling.
 * 
 * Allows desktop preview / development testing without RevenueCat iOS/Android binaries.
 */
actual object StoreBilling {
    private var initialized = false
    private var mockPurchasedRemoveAds = false

    actual fun isConfigured(): Boolean = initialized

    actual fun initialize(apiKey: String) {
        initialized = apiKey.isNotBlank()
    }

    actual fun purchase(packageId: String, onResult: (success: Boolean, error: String?) -> Unit) {
        // Desktop mock: simulate purchase success for testing UI flows
        if (packageId == "remove_ads") {
            mockPurchasedRemoveAds = true
        }
        onResult(true, null)
    }

    actual fun fetchLocalizedPrices(packageIds: List<String>, onResult: (Map<String, String>) -> Unit) {
        // No real store on desktop preview - callers fall back to their own placeholder display.
        onResult(emptyMap())
    }

    actual fun restorePurchases(onResult: (success: Boolean, error: String?) -> Unit) {
        if (mockPurchasedRemoveAds) {
            onResult(true, null)
        } else {
            onResult(false, "NO PREVIOUS PURCHASES FOUND")
        }
    }
}
