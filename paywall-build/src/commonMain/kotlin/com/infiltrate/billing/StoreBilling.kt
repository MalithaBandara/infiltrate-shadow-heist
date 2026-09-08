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
}
