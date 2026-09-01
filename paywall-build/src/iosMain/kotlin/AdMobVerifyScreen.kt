import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import app.lexilabs.basic.ads.AdUnitId
import app.lexilabs.basic.ads.BasicAds
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.composable.BannerAd
import kotlin.native.ObjCName
import platform.UIKit.UIViewController

// SPIKE / THROWAWAY - AdMob on-device verification only, see .junie/guidelines.md "AdMob
// (basic-ads) feasibility spike". The link-only spike (AdMobSpikeUsage.kt) already proved the
// SDK compiles and links; this proves it actually RUNS - BasicAds.Initialize() completes and a
// real BannerAd load either succeeds or reports a concrete error, observed on a real iOS
// Simulator via the same result-file pattern already used for the storage bridge and level
// transition checks.
@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "AdMobVerifyBridge", exact = true)
object AdMobVerifyBridge {
    var initializeCalled: Boolean = false
        private set
    var bannerLoaded: Boolean = false
        private set
    var bannerLoadError: String? = null
        private set

    fun markInitializeCalled() {
        initializeCalled = true
    }

    fun markBannerLoaded() {
        bannerLoaded = true
    }

    fun markBannerLoadError(message: String) {
        bannerLoadError = message
    }
}

@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "AdMobVerifyScreen", exact = true)
object AdMobVerifyScreen {
    fun makeViewController(): UIViewController =
        ComposeUIViewController {
            AdMobVerifyContent()
        }
}

@OptIn(DependsOnGoogleMobileAds::class)
@Composable
private fun AdMobVerifyContent() {
    BasicAds.Initialize()
    AdMobVerifyBridge.markInitializeCalled()
    Box(modifier = Modifier.fillMaxSize()) {
        // basic-ads' BannerAd Composable doesn't expose a dedicated onError callback, only
        // onLoad - a real load failure (e.g. no network, bad ad unit ID) surfaces as onLoad
        // simply never firing, which the Swift-side poll timeout already treats as a genuine
        // "did not load" result rather than assuming success.
        BannerAd(
            adUnitId = AdUnitId.BANNER_DEFAULT,
            onLoad = { AdMobVerifyBridge.markBannerLoaded() },
        )
    }
}
