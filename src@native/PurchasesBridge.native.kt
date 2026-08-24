package com.sample.demo.purchases

class NativePurchasesBridge : PurchasesBridge {
    override fun initialize(apiKey: String) {}
    override fun purchase(packageId: String, onResult: (Boolean) -> Unit) { onResult(true) }
    override fun isSubscribed(onResult: (Boolean) -> Unit) { onResult(false) }
}

actual fun getPurchasesBridge(): PurchasesBridge = NativePurchasesBridge()
