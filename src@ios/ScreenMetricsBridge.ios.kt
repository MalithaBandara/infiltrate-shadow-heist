package com.sample.demo.screen

import game.model.DeviceScreen
import kotlin.native.ObjCName

/**
 * Swift -> Kotlin bridge for "how big is this screen, and how much of it does the system keep",
 * feeding `game.model.DeviceScreen` (which KorGE's canvas and every HUD inset are derived from -
 * see `game.model.ScreenLayout`).
 *
 * **Why Swift does the measuring.** Reading `UIScreen.mainScreen.bounds` from Kotlin/Native means
 * `CValue<CGRect>.useContents`, which needs an `ExperimentalForeignApi` opt-in, and this project
 * has already burned a CI round on an iOS-only cinterop compile failure that opting in did not fix
 * (`NSDate().timeIntervalSince1970`, `.junie/guidelines.md`). iOS is the one platform whose
 * compile cannot be checked on the dev machine at all, so the `CGRect` is unpacked in Swift -
 * where it is one line and cannot fail to compile - and crosses as four plain `Double`s.
 *
 * **Why size and safe area arrive separately.** `AppDelegate.swift` calls
 * `ShellAppDelegate.applicationDidFinishLaunching` (which boots KorGE) as the very first statement
 * of `didFinishLaunchingWithOptions`, so the screen size has to be published before that line, at
 * which point no window has been laid out and `safeAreaInsets` is still zero. The insets follow
 * once the window is key and visible, and are refreshed on every switch into gameplay - which is
 * always before a `GameplayScene` is built, since a level can only start from the menu.
 *
 * `@ObjCName(..., exact = true)` for the usual reason: without `exact` the linked symbol keeps the
 * framework prefix (`GameMainGameScreenMetricsBridge`) and Swift cannot see it.
 */
@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "GameScreenMetricsBridge", exact = true)
object GameScreenMetricsBridge {

    /** UIScreen/window bounds in points. Order does not matter - landscape is assumed. */
    fun publishScreenSize(widthPt: Double, heightPt: Double) {
        val current = DeviceScreen.metrics
        DeviceScreen.publish(
            widthDp = widthPt,
            heightDp = heightPt,
            // Keep whatever safe area was already measured: this is called again on every switch
            // into gameplay, long after the insets are known.
            safeLeftDp = current?.safeArea?.left ?: 0.0,
            safeTopDp = current?.safeArea?.top ?: 0.0,
            safeRightDp = current?.safeArea?.right ?: 0.0,
            safeBottomDp = current?.safeArea?.bottom ?: 0.0,
        )
    }

    /**
     * `window.safeAreaInsets` in points - in landscape that is the Dynamic Island / notch on
     * whichever side the camera ends up, and the home-indicator strip along the bottom.
     */
    fun publishSafeArea(leftPt: Double, topPt: Double, rightPt: Double, bottomPt: Double) {
        DeviceScreen.publishSafeArea(leftPt, topPt, rightPt, bottomPt)
    }
}
