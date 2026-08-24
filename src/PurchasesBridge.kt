package com.sample.demo.purchases

interface PurchasesBridge {
    fun initialize(apiKey: String)
    fun purchase(packageId: String, onResult: (Boolean) -> Unit)
    fun isSubscribed(onResult: (Boolean) -> Unit)
}

expect fun getPurchasesBridge(): PurchasesBridge
