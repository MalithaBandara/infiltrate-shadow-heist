import kotlin.native.ObjCName
import korlibs.render.KorgwBaseNewAppDelegate
import platform.UIKit.UIApplication

/**
 * Entry point the hand-built `ios-shell` Xcode project's Swift `AppDelegate` calls into -
 * deliberately NOT KorGE's own XcodeGen-generated bootstrap (`build/platforms/ios`), which this
 * project doesn't use for the shell app. Mirrors `korlibs.render.KorgwBaseNewAppDelegate`'s
 * documented contract exactly (read directly from KorGE 6.0.0 source): only
 * `applicationDidFinishLaunching(app)` is abstract: background/foreground/resign/terminate are
 * already concrete on the base class and callable as-is from Swift.
 *
 * `@ObjCName(..., exact = true)` pins the exported Swift name to exactly `ShellAppDelegate`
 * (exposed as `ShellAppDelegate.shared`, the standard Kotlin/Native object-export convention).
 * `exact = true` is required - confirmed the hard way in CI (2026-08-31): `name = "..."` alone,
 * without `exact`, compiles fine but does NOT override the default framework-name-prefixed
 * export (the real linked symbol was `GameMainShellAppDelegate`, not `ShellAppDelegate`).
 */
@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "ShellAppDelegate", exact = true)
object ShellAppDelegate : KorgwBaseNewAppDelegate() {
    override fun applicationDidFinishLaunching(app: UIApplication) {
        // TEMP for the Compose<->KorGE switch spike (see .junie/guidelines.md): points at
        // spikeMain() (SwitchSpikeScene) instead of the real game's main(). Revert to
        // `{ main() }` once the spike is done - does NOT touch commonMain's src/main.kt itself.
        applicationDidFinishLaunching(app) { spikeMain() }
    }
}
