package game.scene

import korlibs.image.bitmap.*
import korlibs.image.format.*
import korlibs.io.file.std.*
import korlibs.math.geom.*

/**
 * An animation atlas packed ahead of time by tools/art/pack_sprite_atlases.py: its pages, and
 * each clip's frames as slices of them, in order.
 *
 * Replaces packing the frames into a KorGE MutableAtlas at run time - ~840 PNG reads and decodes
 * for the player and the guards, the bulk of a first level's load. Every pixel is the same,
 * including the 2-pixel border MutableAtlas extruded around each frame (bilinear sampling reads it
 * at a frame's edge): SpriteAtlasTest compares every frame and its border against atlases built
 * the old way from the source frames in art/frames.
 */
class PackedAtlas private constructor(
    /** The pages, as the heap bitmaps MutableAtlas used to build. */
    val pages: List<Bitmap32>,
    private val clips: Map<String, List<BmpSlice>>
) {
    /**
     * [clip]'s frames. [count] is what the caller loads - its *_FRAMES constant - and must be what
     * the packer packed: a mismatch means the atlas is stale, so it fails here, loudly, rather
     * than shifting every frame index after it.
     */
    fun frames(clip: String, count: Int): List<BmpSlice> {
        val frames = clips[clip] ?: error("atlas has no clip '$clip' - re-run tools/art/pack_sprite_atlases.py")
        check(frames.size == count) {
            "atlas clip '$clip' has ${frames.size} frames, the game loads $count - re-run tools/art/pack_sprite_atlases.py"
        }
        return frames
    }

    companion object {
        /** Reads `atlas/<name>.txt` and its pages, `atlas/<name>_<page>.png`. */
        suspend fun load(name: String): PackedAtlas {
            class Entry(val clip: String, val page: Int, val rect: RectangleInt)
            val entries = resourcesVfs["atlas/$name.txt"].readString().lineSequence()
                .filter { it.isNotBlank() }
                .map { line ->
                    val f = line.trim().split(' ')
                    // <clip> <file number> <page> <x> <y> <w> <h>
                    Entry(f[0], f[2].toInt(), RectangleInt(f[3].toInt(), f[4].toInt(), f[5].toInt(), f[6].toInt()))
                }
                .toList()
            val pageCount = entries.maxOf { it.page } + 1
            // toBMP32: the page type and pixel format MutableAtlas built (premultiplied Bitmap32),
            // whatever the platform decoder returns.
            val pages = (0 until pageCount).map { resourcesVfs["atlas/${name}_$it.png"].readBitmap().toBMP32() }
            val clips = LinkedHashMap<String, MutableList<BmpSlice>>()
            for (e in entries) clips.getOrPut(e.clip) { ArrayList() } += pages[e.page].slice(e.rect)
            return PackedAtlas(pages, clips)
        }
    }
}
