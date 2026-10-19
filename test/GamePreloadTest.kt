package test

import game.scene.GamePreload
import game.scene.GuardAnimations
import game.scene.PlayerAnimations
import game.scene.SceneAssets
import korlibs.io.async.suspendTest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.test.*

/**
 * GamePreload hands the first level exactly what it would have loaded itself: every preloaded
 * bitmap and font comes back from SceneAssets as one shared instance (a caller arriving while it is
 * still being decoded waits for it rather than decoding a second copy), the player's and the
 * guards' atlases load from it, and nothing is left pending once the level has taken it all.
 */
class GamePreloadTest {

    @Test
    fun theFirstLevelTakesEveryPreloadedAssetAndNothingIsDecodedTwice() = suspendTest {
        GamePreload.start()
        // Straight away, before the preload can have finished: two callers at once get one bitmap.
        val (art, artMinified) = GamePreload.LOADING_SCREEN_BITMAPS.first()
        val pair = coroutineScope {
            val a = async { SceneAssets.bitmap(art, artMinified) }
            val b = async { SceneAssets.bitmap(art, artMinified) }
            a.await() to b.await()
        }
        assertNotNull(pair.first, "$art did not load")
        assertSame(pair.first, pair.second, "$art was decoded twice")

        val player = PlayerAnimations.load(emptySet())
        assertSame(player.idle, PlayerAnimations.load(emptySet()).idle, "the core atlas was built twice")
        val guards = GuardAnimations.load()
        assertSame(guards, GuardAnimations.load())

        GamePreload.joinForTesting()
        for ((bitmap, minified) in GamePreload.BITMAPS) {
            val bmp = assertNotNull(SceneAssets.bitmap(bitmap, minified), "$bitmap did not load")
            assertSame(bmp, SceneAssets.bitmap(bitmap, minified))
            assertEquals(minified, bmp.mipmaps, "$bitmap: mipmaps")
        }
        for (font in GamePreload.FONTS) {
            assertSame(SceneAssets.font(font), SceneAssets.font(font))
        }
        assertEquals(emptySet(), SceneAssets.pendingPreloadsForTesting(), "preloads left untaken")
    }
}
