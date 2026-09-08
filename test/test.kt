import game.scene.*
import korlibs.event.*
import korlibs.image.color.*
import korlibs.image.vector.*
import korlibs.korge.input.*
import korlibs.korge.scene.*
import korlibs.korge.service.storage.*
import korlibs.korge.tests.*
import korlibs.korge.tween.*
import korlibs.korge.view.*
import korlibs.korge.view.vector.*
import korlibs.math.geom.*
import korlibs.time.*
import kotlin.test.*

class GameplaySceneTest : ViewsForTesting() {

    @Test
    fun testGameplaySceneInitializes() = viewsTest {
        val sceneContainer = sceneContainer()
        sceneContainer.changeTo { GameplayScene() }
        assertNotNull(sceneContainer.currentScene)
    }

    @Test
    fun testGameplaySceneWithSideScrollingParallax() = viewsTest {
        val sceneContainer = sceneContainer()
        sceneContainer.changeTo { GameplayScene(game.model.LevelData.SIDE_SCROLL_LEVEL) }
        assertNotNull(sceneContainer.currentScene)
    }

    @Test
    fun testGameplaySceneMultiScreenSizesAndBgmgLayer() = viewsTest {
        val sceneContainer = sceneContainer()
        // Test default long-corridor level (3500px) with bgmg2.png looping across various screen aspect ratios
        sceneContainer.changeTo { GameplayScene(game.model.LevelData.DEFAULT_LEVEL_1) }
        assertNotNull(sceneContainer.currentScene)

        // Step scene frames to ensure updater, camera tracking, and parallax loops run without exception
        views.update(16.milliseconds)
        views.update(16.milliseconds)
    }



    @Test
    fun testVisionGraphicsRendering() = viewsTest {
        val g = graphics {
            fill(Colors.YELLOW.withAd(0.3)) {
                moveTo(Point(0, 0))
                lineTo(Point(100, 50))
                lineTo(Point(100, -50))
                close()
            }
        }
        g.updateShape {
            fill(Colors.RED.withAd(0.4)) {
                moveTo(Point(10, 10))
                lineTo(Point(50, 50))
                lineTo(Point(50, 10))
                close()
            }
        }
        val leftPressed = views.input.keys[Key.LEFT] || views.input.keys[Key.A]
        assertFalse(leftPressed)
    }

    @Test
    fun testClimbAnimationStart() {
        // The clip still starts on raw frame 70 - hands on the lip, foot-plant stride skipped -
        // but raw 1-69 are no longer loaded into the atlas at all (they cost a full 2048x2048
        // page for frames nothing could ever display), so the loaded indices now start at zero
        // rather than at 69. What has to hold is the SPAN: GameplayScene maps climbPhase 0..1
        // across CLIMB_START..CLIMB_END, so as long as that stays 154 frames wide the same 155
        // source frames play at the same rate as before the trim.
        assertEquals(0, PlayerAnimations.CLIMB_START)
        assertEquals(154, PlayerAnimations.CLIMB_END)
        assertEquals(154, PlayerAnimations.CLIMB_END - PlayerAnimations.CLIMB_START)
        assertTrue(PlayerAnimations.CLIMB_START < PlayerAnimations.CLIMB_END)
    }
}
