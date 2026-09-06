package com.sample.demo.analytics

/**
 * Bridge for sending gameplay events to the Layers Events SDK (layers.com/docs/sdk). Same
 * "expect fun getXBridge()" shape as ContinueAdBridge.kt/LevelExitBridge.kt/PurchasesBridge.kt -
 * :game's common code (GameplayScene.kt) can't depend on `com.layers.sdk:layers-android` directly,
 * since that's an Android-only artifact and this module also targets iOS/JVM/JS/WasmJS.
 *
 * Real implementation only exists on Android (src@android/AnalyticsBridge.android.kt and the
 * duplicate plain copy in android-shell/.../AnalyticsBridge.kt - the actual shipped app).
 * LayersAndroid.configure(...) itself happens once in android-shell's Application.onCreate()
 * (InfiltrateApplication.kt), not here; this bridge only forwards already-configured track() calls.
 * Every other target gets a no-op stub, same convention as the other bridges in this project.
 *
 * purchase_success / subscription_start / trial_start are deliberately NOT tracked anywhere yet:
 * StoreScreen.kt's "purchases" (onPurchase) just grant coins/premium locally via
 * profileStorage.addCoins(...) - no RevenueCat or Play Billing call exists anywhere in this
 * codebase yet (see .junie/guidelines.md's RevenueCat sections; PurchasesBridge is still a stub).
 * Firing a real purchase event off that flow would report fake revenue to Layers. Wire those in
 * once a real payment provider is actually connected.
 */
interface AnalyticsBridge {
    fun track(event: String, properties: Map<String, Any> = emptyMap())
}

expect fun getAnalyticsBridge(): AnalyticsBridge
