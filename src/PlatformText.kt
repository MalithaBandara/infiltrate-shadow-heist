package game.scene

/**
 * Draws [text] with the platform's own text engine, for [GameText]: white, [pixelSize] pixels
 * high, one line per `\n`, in the system's bold or regular sans-serif face, shaped for
 * [languageTag] (a BCP 47 tag, "" for the system's). `null` where the platform has no renderer or
 * drawing failed - [GameText] then falls back to KorGE's own path.
 *
 * The game's two fonts are Latin only, and KorGE's text cannot shape Arabic, Hindi or Thai, so
 * every other script goes through here - see [GameText].
 *
 * At the root of src/, not in src/game/scene, for the same reason as AtlasMemory.kt: android-shell
 * compiles src/game/scene into its one plain Android source set, where `expect`/`actual` cannot
 * exist. The Android build that ships is android-shell's, so the copy that matters there is its
 * own plain `renderPlatformText` (android-shell/.../PlatformText.kt); src@android's is the same
 * code for :game's own Android target - keep the two in step.
 */
expect fun renderPlatformText(text: String, pixelSize: Double, bold: Boolean, languageTag: String): PlatformTextImage?
