import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Proves `paywall-build`'s storage-key contract stays byte-for-byte compatible with `:game`'s
 * KorGE `views.storage` on iOS, WITHOUT touching a real `NSUserDefaults` - this dev environment
 * has no Xcode/iOS simulator to run that against. What this test actually verifies: the two
 * sides agree on which keys exist and how the same raw store gets addressed and formatted. It
 * does NOT prove the real OS-level store is shared - that still needs on-device/simulator
 * verification once the native shell embeds `PaywallModule.framework` into `:game`'s app target.
 */
class StorageKeyCompatibilityTest {

    /** Stand-in for the raw `NSUserDefaults` dictionary both sides ultimately read/write. */
    private class FakeNativeDefaults {
        val raw = mutableMapOf<String, String>()
        fun setObject(value: String, forKey: String) { raw[forKey] = value }
        fun objectForKey(key: String): String? = raw[key]
    }

    /** `:game`'s side: what KorGE's `DarwinNativeStorage` does under `views.storage[key] = value`. */
    private fun gameSet(store: FakeNativeDefaults, key: String, value: String) {
        store.setObject(value, forKey = "${KorgeStorageKey.PREFIX}$key")
    }

    private fun gameGet(store: FakeNativeDefaults, key: String): String? =
        store.objectForKey("${KorgeStorageKey.PREFIX}$key")

    /** `paywall-build`'s side: the same shape as `PaywallStorage`, minus the real iOS-only defaults instance. */
    private fun paywallSet(store: FakeNativeDefaults, key: String, value: String) {
        store.setObject(value, forKey = KorgeStorageKey.iosKey(key))
    }

    private fun paywallGet(store: FakeNativeDefaults, key: String): String? =
        store.objectForKey(KorgeStorageKey.iosKey(key))

    // The discrete keys MapBackedGameProfileStorage in :game's GameProfile.kt actually persists today.
    private val gameProfileKeys = listOf(
        "user_coins" to "150",
        "user_is_premium" to "true",
        "user_music_vol" to "0.8",
        "user_sfx_vol" to "1.0",
        "user_controls_swapped" to "true",
        "user_language" to "en",
        "user_unlocked_levels" to "level_1;level_4;level_2",
    )

    @Test
    fun gameWriteIsReadableByPaywall() {
        val store = FakeNativeDefaults()
        for ((key, value) in gameProfileKeys) {
            gameSet(store, key, value)
            assertEquals(value, paywallGet(store, key), "paywall-build must read what :game wrote for '$key'")
        }
    }

    @Test
    fun paywallWriteIsReadableByGame() {
        val store = FakeNativeDefaults()
        for ((key, value) in gameProfileKeys) {
            paywallSet(store, key, value)
            assertEquals(value, gameGet(store, key), ":game must read what paywall-build wrote for '$key'")
        }
    }

    @Test
    fun rawKeysMatchKorgesOwnPrefixScheme() {
        for ((key, _) in gameProfileKeys) {
            assertEquals("org.korge.storage.$key", KorgeStorageKey.iosKey(key))
        }
    }

    @Test
    fun unrelatedKeysDoNotCollide() {
        val store = FakeNativeDefaults()
        gameSet(store, "user_coins", "150")
        assertNull(paywallGet(store, "user_is_premium"), "distinct keys must not read each other's value")
    }
}
