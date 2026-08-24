package com.sample.demo.purchases

class AndroidPurchasesBridge : PurchasesBridge {
    override fun initialize(apiKey: String) {
        // Android RevenueCat initialization logic
    }

    override fun purchase(packageId: String, onResult: (Boolean) -> Unit) {
        // Android RevenueCat purchase flow
        onResult(true)
    }

    override fun isSubscribed(onResult: (Boolean) -> Unit) {
        // Android RevenueCat subscription check
        onResult(false)
    }
}

actual fun getPurchasesBridge(): PurchasesBridge = AndroidPurchasesBridge()
