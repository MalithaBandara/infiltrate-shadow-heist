import kotlin.native.ObjCName
import game.model.MapBackedGameProfileStorage
import korlibs.korge.service.storage.DarwinNativeStorage

/**
 * Debug-only entry point for the `ios-shell` proof-of-concept: lets Swift verify that a value
 * `PaywallModule.framework`'s `PaywallStorage` writes is visible to `:game`'s own,
 * already-shipping storage code path - not a shortcut around it. On darwin, KorGE's
 * `Views.storage` (`NativeStorage`) is `by DarwinNativeStorage`, a plain object with no
 * dependency on a live `Views`/window, so this is usable standalone from a native trigger with no
 * KorGE scene running.
 */
@OptIn(kotlin.experimental.ExperimentalObjCName::class)
@ObjCName(name = "DebugStorageBridge")
object DebugStorageBridge {
    // A fresh MapBackedGameProfileStorage every call - its init{} loads from storage - so this
    // proves a real disk re-read, not a coincidentally-cached in-memory value from an earlier call.
    fun readCoinsForDebug(): Int = MapBackedGameProfileStorage(
        getRaw = { DarwinNativeStorage.getOrNull(it) },
        setRaw = { k, v -> DarwinNativeStorage.set(k, v) }
    ).getProfile().coins
}
