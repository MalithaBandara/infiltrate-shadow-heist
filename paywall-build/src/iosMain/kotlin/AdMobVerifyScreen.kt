package com.infiltrate.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lexilabs.basic.ads.AdUnitId
import app.lexilabs.basic.ads.BasicAds
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.composable.BannerAd
import kotlin.native.ObjCName

// SPIKE / THROWAWAY - AdMob on-device verification only, see .junie/guidelines.md "AdMob
// (basic-ads) feasibility spike". Round 1 of this on-device check created its own separate
// ComposeUIViewController shown by swapping window.rootViewController to it - that crashed
// (SIGABRT inside Compose's own setContent/BackgroundInputView machinery, no AdMob symbols in
// the trace) while the MainMenu's own ComposeUIViewController was still alive underneath it.
// Working theory: two live ComposeUIViewControllers in one process at once is the trigger.
// Round 2: no second ComposeUIViewController at all - this content renders inside the SAME
// scene MainMenuComposeScreen already owns (see MainMenuComposeViewController.kt), as a tiny
// 1dp invisible element alongside the real NavigationRoot, present unconditionally from launch.
@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "AdMobVerifyBridge", exact = true)
object AdMobVerifyBridge {
    var initializeCalled: Boolean = false
        private set
    var bannerLoaded: Boolean = false
        private set

    fun markInitializeCalled() {
        initializeCalled = true
    }

    fun markBannerLoaded() {
        bannerLoaded = true
    }
}

@OptIn(DependsOnGoogleMobileAds::class)
@Composable
fun AdMobVerifyContent() {
    BasicAds.Initialize()
    AdMobVerifyBridge.markInitializeCalled()
    // 1dp, not zero - some Compose ad-rendering paths skip work entirely for a
    // zero-size container; this is deliberately still practically invisible.
    Box(modifier = Modifier.size(1.dp)) {
        BannerAd(
            adUnitId = AdUnitId.BANNER_DEFAULT,
            onLoad = { AdMobVerifyBridge.markBannerLoaded() },
        )
    }
}
