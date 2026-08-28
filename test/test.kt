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
    fun testSplashSceneInitializes() = viewsTest {
        val sceneContainer = sceneContainer()
        sceneContainer.changeTo { SplashScene() }
        assertNotNull(sceneContainer.currentScene)
    }

    @Test
    fun testMainMenuSceneInitializes() = viewsTest {
        val sceneContainer = sceneContainer()
        sceneContainer.changeTo { MainMenuScene() }
        assertNotNull(sceneContainer.currentScene)
    }

    @Test
    fun testLevelSelectSceneInitializes() = viewsTest {
        val sceneContainer = sceneContainer()
        sceneContainer.changeTo { LevelSelectScene() }
        assertNotNull(sceneContainer.currentScene)
    }

    @Test
    fun testStoreSceneInitializes() = viewsTest {
        val sceneContainer = sceneContainer()
        sceneContainer.changeTo { StoreScene() }
        assertNotNull(sceneContainer.currentScene)
    }

    @Test
    fun testSettingsSceneInitializes() = viewsTest {
        val sceneContainer = sceneContainer()
        sceneContainer.changeTo { SettingsScene() }
        assertNotNull(sceneContainer.currentScene)
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
