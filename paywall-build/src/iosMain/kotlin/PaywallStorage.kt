import kotlin.native.ObjCName
import platform.Foundation.NSUserDefaults

/**
 * Reads/writes the SAME on-disk store `:game`'s KorGE `views.storage` uses on iOS, without
 * depending on KorGE at all - `paywall-build` is a separately compiled framework.
 *
 * Confirmed against KorGE 6.0.0's real `DarwinNativeStorage` source (not assumed): it backs
 * `views.storage` with a *named* `NSUserDefaults` suite (`"korge"`, deliberately not
 * `.standardUserDefaults`) and prefixes every key with `KorgeStorageKey.PREFIX`. This only reads
 * the same store as `:game` when both run in the same app process/bundle - a named suite with no
 * App Group is scoped to the app's own container, not shared across separate processes.
 *
 * `@ObjCName` pins the exported Swift name to `PaywallStorage` (`PaywallStorage.shared`) instead
 * of Kotlin/Native's default framework-name-prefixed export (confirmed elsewhere in this project:
 * KorGE's own generated bootstrap exports its `NewAppDelegate` object as `GameMainNewAppDelegate`
 * for its `GameMain` framework - the same prefixing would otherwise apply here as
 * `PaywallModulePaywallStorage` for this `PaywallModule` framework).
 */
@OptIn(kotlin.experimental.ExperimentalObjCName::class)
@ObjCName(name = "PaywallStorage")
object PaywallStorage {
    private val defaults = NSUserDefaults(suiteName = "korge")

    fun getRaw(key: String): String? = defaults.objectForKey(KorgeStorageKey.iosKey(key))?.toString()

    fun setRaw(key: String, value: String) {
        defaults.setObject(value, KorgeStorageKey.iosKey(key))
        defaults.synchronize()
    }
}
