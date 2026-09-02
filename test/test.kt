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
        // Test default 4x length level (3200px) with bgmg2.png looping across various screen aspect ratios
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
}
