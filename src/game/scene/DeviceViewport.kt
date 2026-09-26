package game.scene

import game.model.DeviceScreen
import game.model.VirtualViewport
import korlibs.korge.scene.SceneContainer
import korlibs.korge.view.Views
import korlibs.math.geom.Size
import korlibs.platform.Platform

/**
 * Applies the device's virtual canvas (see `game.model.ScreenLayout`) to a live KorGE view tree.
 *
 * ## Why this is called at scene-start rather than only in the `Korge {}` config
 *
 * `Korge(virtualSize = ...)` is evaluated before anything is on screen, and the three hosts learn
 * their real screen size at three different moments:
 *
 * - **Desktop** knows it up front (it owns the window), so `main.kt` passes the right virtual size
 *   straight into the config and this is a no-op there.
 * - **Android** knows it in `onCreate`, which is also before `loadModule`, so `MainActivity`
 *   likewise passes it into `KorgeConfig` - but the KorGE module is loaded once and then re-targeted
 *   for every later level, so this is what keeps a later launch correct if the window ever changed.
 * - **iOS** does not. `gameMain()` runs from inside
 *   `ShellAppDelegate.applicationDidFinishLaunching`, which Swift calls as the *first* statement of
 *   its own `didFinishLaunchingWithOptions` - before the window is laid out and therefore before
 *   `AppDelegate.swift` can publish anything. Reading UIKit from Kotlin instead was considered and
 *   rejected: `UIScreen.mainScreen.bounds` is a `CValue<CGRect>` needing `useContents` and an
 *   `ExperimentalForeignApi` opt-in, and this project has already lost a CI round to a
 *   cinterop-shaped iOS-only compile failure that opting in did not fix (`NSDate().
 *   timeIntervalSince1970`, see `.junie/guidelines.md`). Swift reads the `CGRect` instead, hands
 *   over four plain `Double`s through `GameScreenMetricsBridge`, and gameplay picks them up here -
 *   which always runs later, because a level can only start after the menu has been on screen.
 *
 * So: the config gets the best size the host has at construction time, and this re-applies the
 * authoritative one every time a scene is about to be built. Calling it when nothing has changed
 * costs one comparison.
 */
object DeviceViewport {

    /**
     * Resizes [views] (and [container], whose size a [korlibs.korge.scene.Scene] copies into its
     * own `sceneWidth`/`sceneHeight` when it is created) to the current device canvas.
     *
     * **Call this BEFORE `changeTo { ... }`.** A `Scene`'s view is built lazily from
     * `sceneContainer.size` at creation, so a scene already on screen will not pick up a resize
     * here - the next one will.
     */
    fun currentViewport(): VirtualViewport {
        val m = DeviceScreen.metrics
        if (Platform.isJvm && m != null && m.widthDp > 1.0 && m.heightDp > 1.0 && m.widthDp < m.heightDp) {
            val aspect = m.widthDp / m.heightDp
            val h = if (aspect >= 0.7) 600.0 else 585.0
            val w = kotlin.math.round(h * aspect).coerceAtLeast(240.0)
            return VirtualViewport(w, h)
        }
        return DeviceScreen.viewport
    }

    fun apply(views: Views, container: SceneContainer): VirtualViewport {
        val viewport = currentViewport()
        val w = viewport.width.toInt()
        val h = viewport.height.toInt()
        if (views.virtualWidth != w || views.virtualHeight != h) {
            views.setVirtualSize(w, h)
        }
        val size = Size(viewport.width, viewport.height)
        if (container.size != size) {
            container.size = size
        }
        return viewport
    }
}
