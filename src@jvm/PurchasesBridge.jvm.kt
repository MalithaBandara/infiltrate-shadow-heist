package com.sample.demo.purchases

class JvmPurchasesBridge : PurchasesBridge {
    override fun initialize(apiKey: String) {
        println("[JvmPurchasesBridge] Desktop JVM no-op stub initialized")
    }

    override fun purchase(packageId: String, onResult: (Boolean) -> Unit) {
        println("[JvmPurchasesBridge] Desktop JVM purchase stub called for: $packageId")
        onResult(true)
    }

    override fun isSubscribed(onResult: (Boolean) -> Unit) {
        println("[JvmPurchasesBridge] Desktop JVM isSubscribed stub called")
        onResult(false)
    }
}

actual fun getPurchasesBridge(): PurchasesBridge = JvmPurchasesBridge()
