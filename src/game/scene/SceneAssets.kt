package game.scene

import korlibs.image.bitmap.Bitmap
import korlibs.image.bitmap.mipmaps
import korlibs.image.font.DefaultTtfFont
import korlibs.image.font.TtfFont
import korlibs.image.font.readTtfFont
import korlibs.image.format.readBitmap
import korlibs.io.file.std.resourcesVfs
import korlibs.korge.internal.KorgeInternal
import korlibs.korge.render.AgBitmapTextureManager
import korlibs.math.isPowerOfTwo
import kotlinx.coroutines.CompletableDeferred

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

    /** Every name [bitmap] was asked for since the last [beginLevel] - the art the level being built uses. */
    private val requestedThisLevel = HashSet<String>()

    /**
     * What [GamePreload] is decoding behind the menu, not yet taken. Filled on a background thread,
     * but only through the deferreds: the maps themselves are written by [preloadBitmap] /
     * [preloadFont] before that thread starts and after it only by [bitmap] / [font], on the game's
     * own coroutine. An entry is removed once taken, so a preload never pins a bitmap the cache has
     * since let go of.
     */
    private val bitmapPreloads = HashMap<String, CompletableDeferred<Bitmap?>>()
    private val fontPreloads = HashMap<String, CompletableDeferred<TtfFont?>>()

    /**
     * Test switch: [levelArt] loads everything, the way every level did before level-scoped
     * loading - the reference GameplaySceneArtTest compares each level's view tree against.
     */
    var loadAllLevelArt: Boolean = false

    /** The cached bitmaps by name, for tests. */
    internal fun cachedForTesting(): Map<String, Bitmap> = bitmaps.toMap()

    /** What the level being built has asked [bitmap] for so far, for tests. */
    internal fun requestedForTesting(): Set<String> = requestedThisLevel.toSet()

    /** Preloads not yet taken by [bitmap] / [font], for tests. */
    internal fun pendingPreloadsForTesting(): Set<String> = bitmapPreloads.keys + fontPreloads.keys

    /**
     * Reserves [name] for [GamePreload] and returns the work that decodes it - exactly as [bitmap]
     * would, [minified] included - safe to run on any thread. Until that work finishes, a [bitmap]
     * call for [name] waits for it instead of decoding a second copy. Nothing to do (an empty
     * step) for a name already cached or reserved.
     */
    internal fun preloadBitmap(name: String, minified: Boolean): suspend () -> Unit {
        if (name in bitmaps || name in bitmapPreloads) return {}
        val result = CompletableDeferred<Bitmap?>()
        bitmapPreloads[name] = result
        return { result.complete(try { decode(name, minified) } catch (_: Throwable) { null }) }
    }

    /** [preloadBitmap] for a font: [font] waits for this decode instead of starting its own. */
    internal fun preloadFont(name: String): suspend () -> Unit {
        if (name in fonts || name in fontPreloads) return {}
        val result = CompletableDeferred<TtfFont?>()
        fontPreloads[name] = result
        return { result.complete(try { resourcesVfs[name].readTtfFont() } catch (_: Throwable) { null }) }
    }

    /** A level is starting to load: what it asks for from here on is what [releaseOtherLevelsArt] keeps. */
    fun beginLevel() {
        requestedThisLevel.clear()
    }

    /**
     * [bitmap], for a level prop - but only when [needed] (the level has something that draws it),
     * else `null` without touching the disk. A `null` here takes exactly the path a missing file
     * always took at every call site, which is why [needed] must be true wherever the scene would
     * draw the asset; GameplaySceneArtTest checks that for every level.
     */
    suspend fun levelArt(name: String, needed: Boolean, minified: Boolean = true): Bitmap? =
        if (needed || loadAllLevelArt) bitmap(name, minified) else null

    /**
     * Forgets every cached bitmap the level being built did not ask for, and frees its GPU texture
     * - and the same for [also] (atlas pages the level let go of, PlayerAnimations.takeReleasedPages).
     * Call only once nothing on screen can draw them any more - GameplayScene does it after its
     * own setup, once the scene it replaced has been destroyed.
     *
     * Dropping our reference is not enough on its own. KorGE's texture manager keeps a strong
     * reference to every bitmap it has uploaded while it is under its `maxCachedMemory` budget
     * (384MB here - TextureResidency), and a plain `removeBitmap` does not stick: the manager's
     * next GC walks a list `removeBitmap` does not clear and re-adds the bitmap to its cache set,
     * where it then stays for good. What does stick is the manager's own GC deciding to drop it,
     * which it does for a bitmap not drawn since the previous GC while over budget. So: mark every
     * other uploaded texture as used, run the GC with a budget nothing fits in, twice (the first
     * pass can only catch what was not drawn since the GC before it; the second catches what the
     * old scene drew since then), and put the budget back. Everything kept stays uploaded - the
     * player's atlas pages and this level's art are not re-sent. Written against KorGE 6.0.0's
     * AgBitmapTextureManager (locked - see .junie/guidelines.md); re-check it before any upgrade.
     *
     * @return how many bitmaps were released.
     */
    @OptIn(KorgeInternal::class)
    fun releaseOtherLevelsArt(textures: AgBitmapTextureManager, also: List<Bitmap> = emptyList()): Int {
        val released = ArrayList<Bitmap>(also)
        val keysToRemove = ArrayList<String>()
        val snapshot = ArrayList(bitmaps.entries)
        for (entry in snapshot) {
            if (entry.key !in requestedThisLevel) {
                released.add(entry.value)
                keysToRemove.add(entry.key)
            }
        }
        for (k in keysToRemove) {
            bitmaps.remove(k)
        }
        if (released.isEmpty()) return 0
        repeat(2) {
            // Clears the manager's two-entry fast path, which returns a texture without marking it
            // used - a kept bitmap sitting in it would otherwise be dropped below. (It may also run
            // the manager's own regular GC, which is harmless here.)
            textures.afterRender()
            for (bmp in textures.getBitmapsWithTextureInfoCopy().keys) {
                if (released.none { it === bmp }) textures.getTextureBase(bmp)
            }
            val budget = textures.maxCachedMemory
            textures.maxCachedMemory = -1L
            try {
                textures.gc()
            } finally {
                textures.maxCachedMemory = budget
            }
        }
        return released.size
    }

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
     * Pass `minified = false` for anything sub-sliced too (`chainedcrate`): mip levels
     * average across the slice boundaries and bleed neighbouring cutouts into each other. And for
     * art already sized to under 2x its draw box (everything tools/art/prep_level_props.py makes):
     * there bilinear is clean on its own, and trilinear - which picks its level off the
     * most-minified axis - measured 30-40% softer than the full-size art.
     *
     * A name [GamePreload] already decoded behind the menu is taken from there (waiting for it if
     * it is still being decoded); a preload that failed is retried here, like any first load.
     */
    suspend fun bitmap(name: String, minified: Boolean = true): Bitmap? {
        requestedThisLevel.add(name)
        bitmaps[name]?.let {
            // A load already under way when the preload reserved the name can land first; the
            // reservation then has nothing left to hand over (and must not pin its copy).
            bitmapPreloads.remove(name)
            return it
        }
        bitmapPreloads[name]?.let { pending ->
            // Left in the map until it lands, so a second caller meanwhile waits for the same one.
            val preloaded = pending.await()
            bitmapPreloads.remove(name)
            bitmaps[name]?.let { return it }
            if (preloaded != null) {
                bitmaps[name] = preloaded
                return preloaded
            }
        }
        val loaded = decode(name, minified) ?: return null
        bitmaps[name] = loaded
        return loaded
    }

    /** One uncached load: [bitmap]'s decode, on whatever thread calls it. `null` on failure. */
    private suspend fun decode(name: String, minified: Boolean): Bitmap? {
        val loaded = try {
            resourcesVfs[name].readBitmap()
        } catch (_: Throwable) {
            return null
        }
        if (minified) {
            loaded.mipmaps(true)
            warnIfNotPowerOfTwo(name, loaded.width, loaded.height)
        }
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
        fonts[name]?.let {
            fontPreloads.remove(name) // as in [bitmap]
            return it
        }
        fontPreloads[name]?.let { pending ->
            val preloaded = pending.await()
            fontPreloads.remove(name)
            fonts[name]?.let { return it }
            if (preloaded != null) {
                fonts[name] = preloaded
                return preloaded
            }
        }
        val loaded = try {
            resourcesVfs[name].readTtfFont()
        } catch (_: Throwable) {
            return fallback
        }
        fonts[name] = loaded
        return loaded
    }
}
