import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.lexilabs.basic.ads.AdUnitId
import app.lexilabs.basic.ads.BasicAds
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.composable.BannerAd

// SPIKE / THROWAWAY - AdMob (app.lexilabs.basic:basic-ads) feasibility spike only, see
// .junie/guidelines.md "AdMob (basic-ads) feasibility spike". Not the real ad integration -
// mirrors PaywallUsage.kt's own pattern (a real call to the SDK's public API, using the
// library's own built-in Google test ad unit ID, forcing the Kotlin/Native compiler+linker to
// actually pull in and resolve Google-Mobile-Ads-SDK's compiled code) so that
// :paywall-build:linkDebugFrameworkIosSimulatorArm64 is a genuine test, not just a compile of
// unused, dead-strippable code.
@OptIn(DependsOnGoogleMobileAds::class)
@Composable
fun AdMobSpikeUsage() {
    BasicAds.Initialize()
    Box(modifier = Modifier.fillMaxSize()) {
        BannerAd(adUnitId = AdUnitId.BANNER_DEFAULT)
    }
}
