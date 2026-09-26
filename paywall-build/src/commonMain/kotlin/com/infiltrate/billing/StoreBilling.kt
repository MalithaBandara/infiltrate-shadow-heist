package com.infiltrate.billing

/**
 * Multiplatform billing bridge for in-app purchases (backed by RevenueCat).
 * 
 * Used by StoreScreen.kt across Android, iOS, and JVM desktop preview.
 */
expect object StoreBilling {
    fun initialize(apiKey: String)
    fun purchase(packageId: String, onResult: (success: Boolean, error: String?) -> Unit)
    fun restorePurchases(onResult: (success: Boolean, error: String?) -> Unit)
    fun isConfigured(): Boolean

    /**
     * Maps each of [packageIds] to the store's own localized, currency-formatted price string
     * (e.g. "$0.99", "€1.09") via RevenueCat's offerings, so the Store UI never has to hardcode a
     * price that only matches what US buyers are actually charged. IDs with no match in the
     * fetched offering (including on error, or before configure()/initialize() has run) are simply
     * absent from the result map - callers should keep their own fallback display for those.
     */
    fun fetchLocalizedPrices(packageIds: List<String>, onResult: (Map<String, String>) -> Unit)
}
