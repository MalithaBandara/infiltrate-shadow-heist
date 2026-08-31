/**
 * Mirrors the exact key transform KorGE 6.0.0's `DarwinNativeStorage` applies before touching
 * `NSUserDefaults` on iOS (`getKey(key) = "$PREFIX$key"` in
 * `korlibs.korge.service.storage.DarwinNativeStorage`, confirmed by reading KorGE's real source
 * from the local Gradle cache). `paywall-build` has no KorGE dependency, so this can't be reused
 * directly - it's re-declared here to stay byte-for-byte compatible with `:game`'s own storage.
 */
object KorgeStorageKey {
    const val PREFIX = "org.korge.storage."

    fun iosKey(key: String): String = "$PREFIX$key"
}
