import game.model.DeviceScreen
import kotlin.native.ObjCName

/**
 * The Compose half of the screen-metrics bridge, and it has to be its own object for a reason
 * that is easy to miss: on iOS, `:game` and `paywall-build` are two separately compiled
 * Kotlin/Native frameworks (`GameMain` and `PaywallModule` - they cannot call each other at all,
 * see `.junie/guidelines.md`), and each one compiles its own copy of `src/game/model`. So there
 * are **two** `game.model.DeviceScreen` singletons in the process, one per framework, and
 * publishing to `GameScreenMetricsBridge` (GameMain) leaves this one empty.
 *
 * `AppDelegate.swift` therefore calls both. On Android there is one process, one classpath and
 * one `DeviceScreen`, so `MainActivity` publishes once and both halves see it.
 *
 * Compose only reads the safe area from here (`ui/Responsive.kt`'s `safeAreaPadding`) - it takes
 * its own size from `BoxWithConstraints`, which is always right - but the screen size is published
 * alongside it so the object is a complete, consistent reading rather than insets with nothing to
 * scale them against.
 */
@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "MenuScreenMetricsBridge", exact = true)
object MenuScreenMetricsBridge {

    fun publishScreenSize(widthPt: Double, heightPt: Double) {
        val current = DeviceScreen.metrics
        DeviceScreen.publish(
            widthDp = widthPt,
            heightDp = heightPt,
            safeLeftDp = current?.safeArea?.left ?: 0.0,
            safeTopDp = current?.safeArea?.top ?: 0.0,
            safeRightDp = current?.safeArea?.right ?: 0.0,
            safeBottomDp = current?.safeArea?.bottom ?: 0.0,
        )
    }

    fun publishSafeArea(leftPt: Double, topPt: Double, rightPt: Double, bottomPt: Double) {
        DeviceScreen.publishSafeArea(leftPt, topPt, rightPt, bottomPt)
    }
}
