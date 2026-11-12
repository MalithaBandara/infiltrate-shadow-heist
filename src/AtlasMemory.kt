package game.scene

/**
 * Whether [PackedAtlas] should copy its pages into heap [korlibs.image.bitmap.Bitmap32]s (`true`),
 * or keep the platform decoder's native image as it is (`false`).
 *
 * Every 2048x2048 page is 16.8MB as a Bitmap32, and the game holds up to ten of them for the whole
 * session. On Android that copy lives on the Java heap, and Play's Android vitals for 1.1.0 showed
 * it was the cause of every crash reported: `OutOfMemoryError` on ~3-4GB budget phones, whose heap
 * limit of ~192-256MB the atlases nearly fill on their own. A native Android bitmap keeps the same
 * pixels outside the Java heap, and KorGE uploads it from there (`KmlGlAndroid.texImage2D` copies
 * it into a direct buffer with `Bitmap.copyPixelsToBuffer`), so no heap copy is ever made.
 *
 * Only Android with a low heap limit answers `false`: everywhere else the Bitmap32 path is what
 * shipped and was checked on screen, so it is left exactly as it was.
 *
 * Lives at the root of src/, not in src/game/scene, for the same reason as ContinueAdBridge.kt:
 * android-shell compiles src/game/scene straight into its one plain Android source set, where
 * `expect`/`actual` cannot exist. The Android build that ships is android-shell's, so the copy
 * that matters is android-shell's own plain `atlasPagesOnHeap()` (AtlasMemory.kt there);
 * src@android/AtlasMemory.android.kt is the same logic for :game's own Android target.
 */
expect fun atlasPagesOnHeap(): Boolean
