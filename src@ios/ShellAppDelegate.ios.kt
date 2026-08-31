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
 * `@ObjCName` pins the exported Swift name to `ShellAppDelegate` (exposed as
 * `ShellAppDelegate.shared`, the standard Kotlin/Native object-export convention) instead of
 * relying on the framework-name-prefixing default.
 */
@OptIn(kotlin.experimental.ExperimentalObjCName::class)
@ObjCName(name = "ShellAppDelegate")
object ShellAppDelegate : KorgwBaseNewAppDelegate() {
    override fun applicationDidFinishLaunching(app: UIApplication) {
        applicationDidFinishLaunching(app) { main() }
    }
}
