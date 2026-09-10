package game.scene

import korlibs.image.bitmap.Bitmap
import korlibs.image.bitmap.mipmaps
import korlibs.image.font.DefaultTtfFont
import korlibs.image.font.TtfFont
import korlibs.image.font.readTtfFont
import korlibs.image.format.readBitmap
import korlibs.io.file.std.resourcesVfs
import korlibs.math.isPowerOfTwo

/**
 * Process-wide cache for the static art and fonts `GameplayScene` draws with.
 *
 * `sceneMain()` used to call `readBitmap()` twenty times and `readTtfFont()` twice on every
 * single scene load - not just the first. A scene load happens on RESTART, on QUIT-then-relaunch,
 * on watch-ad-to-continue and on every move to the next level, so a normal session re-decodes
 * roughly 87MB of PNG and re-uploads it all as GPU textures over and over, throwing the previous
 * copies away as garbage each time. The frames are static content - the same crate, the same
 * fence, the same touch buttons in every level and every replay - so there is no reason a second
 * `GameplayScene` needs to decode its own copy.
 *
 * This is the same fix, for the same reason, that [PlayerAnimations] already applies to the
 * player atlas: see the note there, and "Real device bugs" #5 in `.junie/guidelines.md`, which
 * flagged exactly these remaining per-scene bitmap loads as the untreated other half of it.
 * Unlike the atlas they degrade silently (every call site defaults to `null` on failure) rather
 * than throwing, so the cost showed up as reload stalls and GC pressure rather than a crash.
 *
 * Bounded by the number of distinct assets, not by how many times a level is replayed. Caching
 * every level's background costs a few MB of resident bitmaps that the old code would eventually
 * have collected, which is a good trade against re-decoding one on every level change.
 *
 * Only successful loads are cached. A failure is left uncached so a missing file behaves exactly
 * as it did before - a silent `null` that is retried next time - rather than being remembered as
 * permanently absent for the rest of the process.
 *
 * Not synchronized: scene loading runs on KorGE's main coroutine. If two loads ever did overlap
 * the worst case is decoding the same asset twice and keeping one, which is wasteful but correct.
 */
object SceneAssets {

    private val bitmaps = HashMap<String, Bitmap>()
    private val fonts = HashMap<String, TtfFont>()

    /**
     * Cached [readBitmap], `null` on failure exactly like the inline try/catch it replaces.
     *
     * [minified] says whether this asset is drawn *smaller* than it is stored - true for world
     * art, props and HUD icons, false for the near-fullscreen art (backgrounds, loading screen,
     * dossier paper, the menu button strips) which is already drawn at or above its own
     * resolution and has nothing to gain. When true the bitmap asks for mipmaps, and if it is
     * not a power of two in both dimensions it says so once, loudly, because in that case the
     * request is silently ignored - see [warnIfNotPowerOfTwo].
     *
     * Pass `minified = false` for anything sub-sliced too (`chainedcrate`, `stars`): mip levels
     * average across the slice boundaries and bleed neighbouring cutouts into each other.
     */
    suspend fun bitmap(name: String, minified: Boolean = true): Bitmap? {
        bitmaps[name]?.let { return it }
        val loaded = try {
            resourcesVfs[name].readBitmap()
        } catch (_: Throwable) {
            return null
        }
        if (minified) {
            loaded.mipmaps(true)
            warnIfNotPowerOfTwo(name, loaded.width, loaded.height)
        }
        bitmaps[name] = loaded
        return loaded
    }

    /**
     * KorGE only builds mipmaps for textures that are a power of two in BOTH dimensions
     * (`AGTexture.doMipmaps`: `requestMipmaps && width.isPowerOfTwo && height.isPowerOfTwo`).
     * There is no error and no warning when that check fails - `mipmaps(true)` just quietly does
     * nothing, which is exactly how every asset in this project went years without mipmaps while
     * the code looked like it had asked for them.
     *
     * So ask here instead. Any minified asset that is not POT gets one line on stdout naming the
     * nearest sizes, once per process. This is the guardrail for art added later: drop in a new
     * 2000x1500 prop and the next run tells you it is over-resolution, rather than it silently
     * costing 12 MB of texture memory forever. See ".junie/guidelines.md" -> "Adding new art".
     */
    private fun warnIfNotPowerOfTwo(name: String, width: Int, height: Int) {
        if (width.isPowerOfTwo && height.isPowerOfTwo) return
        println(
            "[SceneAssets] '$name' is ${width}x$height - NOT power-of-two, so mipmaps are " +
                "silently skipped for it. Resample to a POT size >= its 3x device draw size " +
                "(see .junie/guidelines.md -> 'Adding new art')."
        )
    }

    /** Cached [readTtfFont], falling back to [fallback] (default [DefaultTtfFont]) on failure. */
    suspend fun font(name: String, fallback: TtfFont = DefaultTtfFont): TtfFont {
        fonts[name]?.let { return it }
        val loaded = try {
            resourcesVfs[name].readTtfFont()
        } catch (_: Throwable) {
            return fallback
        }
        fonts[name] = loaded
        return loaded
    }
}
