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
 * `@ObjCName(..., exact = true)` pins the exported Swift/ObjC name to exactly `PaywallStorage`
 * (`PaywallStorage.shared`), overriding Kotlin/Native's default framework-name-prefixed export.
 * `exact = true` is required for this - confirmed the hard way in CI (2026-08-31): without it,
 * `name = "PaywallStorage"` alone compiled fine but the linker's real exported symbol was still
 * `PaywallModulePaywallStorage` (matching KorGE's own generated bootstrap, which exports its
 * `NewAppDelegate` object as `GameMainNewAppDelegate` for the same reason) - `name` without
 * `exact` does not override the default prefixing, it turned out.
 */
@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "PaywallStorage", exact = true)
object PaywallStorage {
    private val defaults = NSUserDefaults(suiteName = "korge")

    fun getRaw(key: String): String? = defaults.objectForKey(KorgeStorageKey.iosKey(key))?.toString()

    fun setRaw(key: String, value: String) {
        defaults.setObject(value, KorgeStorageKey.iosKey(key))
        defaults.synchronize()
    }
}
