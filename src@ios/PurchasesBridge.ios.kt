package com.sample.demo.purchases

class IosPurchasesBridge : PurchasesBridge {
    override fun initialize(apiKey: String) {
        // iOS RevenueCat initialization logic
    }

    override fun purchase(packageId: String, onResult: (Boolean) -> Unit) {
        // iOS RevenueCat purchase flow
        onResult(true)
    }

    override fun isSubscribed(onResult: (Boolean) -> Unit) {
        // iOS RevenueCat subscription check
        onResult(false)
    }
}

actual fun getPurchasesBridge(): PurchasesBridge = IosPurchasesBridge()
