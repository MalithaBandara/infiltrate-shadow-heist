package test

import game.scene.PackedAtlas
import korlibs.image.atlas.MutableAtlas
import korlibs.image.bitmap.Bitmap32
import korlibs.image.format.readBitmapSlice
import korlibs.io.async.suspendTest
import korlibs.io.file.std.localCurrentDirVfs
import kotlin.test.*

/**
 * The atlases tools/art/pack_sprite_atlases.py packs ahead of time (resources/atlas, read by
 * PackedAtlas) hold exactly what the game used to pack at run time: for every frame, built the old
 * way - each source frame in art/frames read into a KorGE MutableAtlas - the frame's pixels and the
 * 2-pixel border around it (KorGE extrudes the frame's own edge into it; bilinear sampling reads it
 * at the frame's edge) are equal, premultiplied value for value, to the packed page's. Also the
 * same frame sizes, and no more pages than the runtime atlas needed.
 *
 * Mirrors the packer's ATLASES table, which mirrors PlayerAnimations' and GuardAnimations' frame
 * constants - those loaders check the counts themselves (PackedAtlas.frames).
 */
class SpriteAtlasTest {

    private val atlases = mapOf(
        "player_core" to listOf(
            Triple("player/idle", 1, 45), Triple("player/walk", 1, 40), Triple("player/jump", 1, 44),
            Triple("player/crouch", 1, 34), Triple("player/crouchwalk", 1, 144), Triple("player/climb", 70, 155)
        ),
        "player_swing" to listOf(Triple("player/swing", 1, 52)),
        "player_push" to listOf(Triple("player/pushtransition", 1, 32), Triple("player/push", 1, 40)),
        "player_wind" to listOf(Triple("player/windtransition", 1, 29), Triple("player/windwalk", 1, 25)),
        "guard" to listOf(Triple("guard/idle", 1, 48), Triple("guard/walk", 1, 38)),
    )

    @Test
    fun everyPackedFrameAndItsBorderMatchesTheRuntimeAtlas() = suspendTest {
        val border = 2
        var frames = 0
        for ((name, clips) in atlases) {
            val packed = PackedAtlas.load(name)
            val old = MutableAtlas<Unit>(2048, 2048, growMethod = MutableAtlas.GrowMethod.NEW_IMAGES)
            for ((folder, first, count) in clips) {
                val newFrames = packed.frames(folder.substringAfter('/'), count)
                for ((i, n) in (first until first + count).withIndex()) {
                    val was = localCurrentDirVfs["art/frames/$folder/${n.toString().padStart(4, '0')}.png"].readBitmapSlice(atlas = old)
                    val now = newFrames[i]
                    assertEquals(was.width to was.height, now.width to now.height, "$folder/$n: size")
                    val wasPage = was.base as Bitmap32
                    val nowPage = now.base as Bitmap32
                    assertEquals(wasPage.premultiplied, nowPage.premultiplied, "$name: premultiplied")
                    for (y in -border until was.height + border) for (x in -border until was.width + border) {
                        val a = wasPage.getRgbaRaw(was.left + x, was.top + y)
                        val b = nowPage.getRgbaRaw(now.left + x, now.top + y)
                        if (a != b) fail("$folder/$n: pixel ($x, $y) is $b, the runtime atlas had $a")
                    }
                    frames++
                }
            }
            assertTrue(packed.pages.size <= old.allBitmaps.size, "$name: ${packed.pages.size} pages, the runtime atlas needed ${old.allBitmaps.size}")
        }
        assertEquals(atlases.values.sumOf { c -> c.sumOf { it.third } }, frames)
    }
}
