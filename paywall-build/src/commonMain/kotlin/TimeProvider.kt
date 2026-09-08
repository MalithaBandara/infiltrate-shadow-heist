package com.infiltrate.ads

/**
 * Wall-clock seconds since epoch, used only to bucket coin-ad watches by day (CoinsAdLimiter.kt).
 * Not in game.model: GameProfile.kt's shared source must stay pure stdlib across every :game
 * target (JVM/Android/iOS/JS/wasm) and Kotlin stdlib has no epoch-time API - this feature only
 * exists inside paywall-build's Compose Store screen, so it doesn't need to cross that boundary.
 */
expect fun currentEpochSeconds(): Long
