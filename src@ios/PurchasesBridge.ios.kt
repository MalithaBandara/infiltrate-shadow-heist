package com.sample.demo.purchases

class IosPurchasesBridge : PurchasesBridge {
    // No RevenueCat SDK on iOS yet - purchases-kmp-core is deliberately not a
    // dependency here (see build.gradle.kts / .junie/guidelines.md). This is a
    // deliberate no-op, not an unfinished real integration: purchase() must return
    // false, never true, so callers don't unlock content nothing was actually
    // paid for.
    override fun initialize(apiKey: String) {
    }

    override fun purchase(packageId: String, onResult: (Boolean) -> Unit) {
        onResult(false)
    }

    override fun isSubscribed(onResult: (Boolean) -> Unit) {
        onResult(false)
    }
}

actual fun getPurchasesBridge(): PurchasesBridge = IosPurchasesBridge()
