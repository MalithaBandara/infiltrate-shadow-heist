import game.model.LevelData
import game.scene.GameAudio
import game.scene.GamePreload
import game.scene.GameplayScene
import game.scene.SceneAssets
import korlibs.event.*
import korlibs.graphics.*
import korlibs.graphics.log.*
import korlibs.image.bitmap.Bitmap
import korlibs.inject.*
import korlibs.io.async.*
import korlibs.korge.*
import korlibs.korge.internal.*
import korlibs.korge.render.*
import korlibs.korge.scene.*
import korlibs.korge.time.delayFrame
import korlibs.korge.view.*
import korlibs.math.geom.*
import korlibs.render.*
import korlibs.time.*
import kotlinx.coroutines.Runnable
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * Level-scoped art loading (SceneAssets.levelArt / releaseOtherLevelsArt) must change nothing on
 * screen. Every level is built twice: once with SceneAssets.loadAllLevelArt (every prop loaded, the
 * way every level used to be built) and once the normal way, and the two view trees must be the
 * same view for view - same classes, same structure, and every image showing the same named
 * asset. A prop the level draws but whose condition in GameplayScene.loadGameplayBitmaps missed
 * would be a null bitmap there, and its draw site would fall back to a plain shape or nothing -
 * a different tree. The levels are built one after another in one container, so each normal
 * build also releases the previous level's art: an image still showing a released bitmap would no
 * longer be named, and a released bitmap must also be gone from the texture manager.
 *
 * Not KorGE's ViewsForTesting: its Views carry no coroutine dispatcher, so a scene's own coroutine
 * never resumes from a file read and sceneMain stalls at the first asset (GameplaySceneTest's
 * scene tests never get past the loading screen for this reason - they only check the scene
 * object exists). [Harness] is the same set-up with the test dispatcher in the Views' context.
 */
class GameplaySceneArtTest {

    internal class Harness {
        var time = DateTime(0.0)
        val frameTime = 16.milliseconds
        val dispatcher = GameWindowCoroutineDispatcher(nowProvider = { time.unixMillisDouble.fastMilliseconds }, fast = true)
        val gameWindow = object : GameWindowLog() {
            override val coroutineDispatcher = dispatcher
            override var width: Int = 1560
            override var height: Int = 720
        }
        val viewsLog = ViewsLog(
            gameWindow + dispatcher,
            ag = AGDummy(Size(1560, 720)),
            gameWindow = gameWindow,
            timeProvider = object : TimeProvider { override fun now(): DateTime = time }
        )
        val views get() = viewsLog.views

        fun run(block: suspend Harness.() -> Unit) {
            var done = false
            var failure: Throwable? = null
            dispatcher.dispatch(views.coroutineContext, Runnable {
                launchImmediately(views.coroutineContext) {
                    try {
                        viewsLog.init()
                        viewsLog.injector.mapInstance(KorgeConfig(title = "art test", windowSize = Size(1560, 720), virtualSize = Size(1560, 720)))
                        KorgeRunner.prepareViewsBase(views, gameWindow, fixedSizeStep = frameTime.fast, forceRenderEveryFrame = true)
                        block()
                    } catch (e: Throwable) {
                        failure = e
                    } finally {
                        done = true
                    }
                }
            })
            var frames = 0
            while (!done) {
                check(++frames < 2_000_000) { "harness ran away" }
                time += frameTime
                gameWindow.dispatchRenderEvent()
                dispatcher.executePending(availableTime = 1000.0.fastMilliseconds)
            }
            failure?.let { throw it }
        }
    }

    private val levels: List<LevelData> =
        (LevelData.DEFAULT_LEVELS + LevelData.DEV_LEVELS + LevelData.SIDE_SCROLL_LEVEL).distinct()

    /**
     * One line per view, depth-first: indent, class, and for an image the asset it shows. A bitmap
     * that is no asset is just "unnamed", without its size: those are drawn at run time (a camera
     * cone's CPU-rasterised shape, sized by the last shape it drew - for a cone culled off screen,
     * by whether a frame happened to render it before it was culled, which depends on how many
     * frames the scene change took, not on any art).
     */
    private fun dump(root: View, names: Map<Bitmap, String>): List<String> {
        val out = ArrayList<String>()
        fun walk(v: View, depth: Int) {
            val art = if (v is BaseImage) {
                names[v.bitmap.base] ?: "unnamed"
            } else ""
            out += "  ".repeat(depth) + v::class.simpleName + (if (art.isNotEmpty()) " [$art]" else "")
            if (v is Container) v.forEachChild { walk(it, depth + 1) }
        }
        walk(root, 0)
        return out
    }

    private fun namesByBitmap(): Map<Bitmap, String> {
        val m = HashMap<Bitmap, String>()
        for ((name, bmp) in SceneAssets.cachedForTesting()) m[bmp] = name
        return m
    }

    /** One container for every build, so each new level replaces (and destroys) the one before, as in the game. */
    private suspend fun Harness.build(container: SceneContainer, level: LevelData, loadAll: Boolean): GameplayScene {
        SceneAssets.loadAllLevelArt = loadAll
        val scene = container.changeTo { GameplayScene(level) }
        // File reads finish on real threads, so the cap is wall time, not frames.
        val started = kotlin.time.TimeSource.Monotonic.markNow()
        while (!scene.setupComplete) {
            assertTrue(started.elapsedNow().inWholeSeconds < 120, "${level.id}: scene setup never finished")
            views.stage.delayFrame()
        }
        // A few frames of the running level, so the updater has drawn and swapped what it swaps.
        repeat(5) { views.stage.delayFrame() }
        return scene
    }

    /** A private collection of KorGE's texture manager, by reflection (JVM test only). */
    private fun AgBitmapTextureManager.privateBitmaps(field: String): List<Any?> {
        val f = AgBitmapTextureManager::class.java.getDeclaredField(field).apply { isAccessible = true }
        val v = f.get(this)
        return (v as? Iterable<*>)?.toList() ?: v.toString().let { listOf(it) }
    }

    /**
     * The KorGE-facing half on its own: art the new level keeps stays uploaded as the SAME texture
     * (never re-sent), and art it does not keep is gone from the manager entirely - not uploaded,
     * and not left in the manager's cached/referenced sets, where a plain removeBitmap would have
     * left it for good (see SceneAssets.releaseOtherLevelsArt).
     */
    @OptIn(KorgeInternal::class)
    @Test
    fun releasingAPreviousLevelsArtKeepsTheRestUploadedAndLeavesNoReferenceBehind() = Harness().run {
        val textures = views.renderContext.agBitmapTextureManager
        textures.maxCachedMemory = 384L * 1024 * 1024
        SceneAssets.beginLevel()
        val keepA = assertNotNull(SceneAssets.bitmap("crate.png"))
        val keepB = assertNotNull(SceneAssets.bitmap("barrel.png"))
        val drop = assertNotNull(SceneAssets.bitmap("cart.png"))
        val texA = textures.getTextureBase(keepA)
        val texB = textures.getTextureBase(keepB)
        textures.getTextureBase(drop)
        // A few GC cycles with none of them drawn: under budget, the manager moves all three into
        // its cached set - the state a previous level's art is in when the next level loads.
        repeat(200) { views.stage.delayFrame() }

        SceneAssets.beginLevel()
        SceneAssets.bitmap("crate.png")
        SceneAssets.bitmap("barrel.png")
        assertTrue(SceneAssets.releaseOtherLevelsArt(textures) >= 1)

        val uploaded = textures.getBitmapsWithTextureInfoCopy()
        assertTrue(uploaded.keys.none { it === drop }, "the released bitmap is still uploaded")
        assertSame(texA, textures.getTextureBase(keepA), "a kept texture was re-created")
        assertSame(texB, textures.getTextureBase(keepB), "a kept texture was re-created")
        // ...and nothing brings the released one back as a reference, however many GCs run.
        repeat(300) { views.stage.delayFrame() }
        for (field in listOf("cachedBitmaps", "referencedBitmaps", "referencedBitmapsSinceGC")) {
            assertTrue(textures.privateBitmaps(field).none { it === drop }, "released bitmap still held in $field")
        }
        assertTrue(textures.getBitmapsWithTextureInfoCopy().keys.none { it === drop })
        assertNull(SceneAssets.cachedForTesting()["cart.png"])
    }

    @OptIn(KorgeInternal::class)
    @Test
    fun everyLevelDrawsTheSameWithLevelScopedArt() = Harness().run {
        GameAudio.silentForTesting = true
        try {
            val textures = views.renderContext.agBitmapTextureManager
            val container = views.stage.sceneContainer(views)
            for (level in levels) {
                val full = build(container, level, loadAll = true)
                val fullTree = dump(full.sceneView, namesByBitmap())
                val fullArt = SceneAssets.cachedForTesting()

                val scoped = build(container, level, loadAll = false)
                val scopedTree = dump(scoped.sceneView, namesByBitmap())
                val scopedArt = SceneAssets.cachedForTesting()

                assertTrue(fullTree.size > 100, "${level.id}: the level was not built (${fullTree.size} views)")
                assertEquals(fullTree.size, scopedTree.size, "${level.id}: view count differs")
                for (i in fullTree.indices) {
                    assertEquals(fullTree[i], scopedTree[i], "${level.id}: view #$i differs")
                }

                // GamePreload decodes this art behind the menu for whichever level comes first, so
                // every level must ask for all of it (a level releases what it does not ask for),
                // and with the same mipmap choice the preload made.
                val requested = SceneAssets.requestedForTesting()
                for ((name, minified) in GamePreload.BITMAPS) {
                    assertTrue(name in requested, "${level.id}: preloaded $name is not loaded by this level")
                    assertEquals(minified, scopedArt[name]?.mipmaps, "${level.id}: preloaded $name's minified flag differs")
                }

                // What the normal build let go of is gone from the texture manager too.
                val released = fullArt.filterKeys { it !in scopedArt }
                val uploaded = textures.getBitmapsWithTextureInfoCopy().keys
                for ((name, bmp) in released) {
                    assertTrue(uploaded.none { it === bmp }, "${level.id}: $name was released but is still uploaded")
                }
                val bytes = released.values.sumOf { it.width.toLong() * it.height * 4 }
                println(
                    "[LevelArt] ${level.id}: ${scopedTree.size} views identical; keeps ${scopedArt.size} assets, " +
                        "released ${released.size} (${bytes / 1_000_000}MB): ${released.keys.sorted()}"
                )
            }
            println("[LevelArt] textures uploaded at the end: ${textures.getBitmapsWithTextureInfoCopy().size}")
        } finally {
            SceneAssets.loadAllLevelArt = false
            GameAudio.silentForTesting = false
        }
    }
}
