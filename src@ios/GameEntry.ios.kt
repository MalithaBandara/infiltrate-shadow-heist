import game.model.DeviceScreen
import game.model.LevelData
import game.scene.DeviceViewport
import game.scene.GameplayScene
import korlibs.image.color.*
import korlibs.io.async.launchImmediately
import korlibs.korge.*
import korlibs.korge.scene.SceneContainer
import korlibs.korge.scene.sceneContainer
import korlibs.math.geom.*
import kotlin.native.ObjCName

/**
 * Real (non-spike) iOS game entry point, wired from ShellAppDelegate.ios.kt. Same Korge() setup
 * as commonMain's main() (src/main.kt), but that entry only ever picks ONE level, once, from
 * Environment["startLevel"]/args - neither of which iOS ever sets, so it would always load
 * DEFAULT_LEVEL_1 regardless of which level the player actually tapped in the Compose menu.
 *
 * android-shell's MainActivity.kt already solved this for Android via its own `activeSceneContainer`
 * var: capture the SceneContainer once when the KorGE module first loads, then re-target it with
 * `sc.changeTo { GameplayScene(levelData) }` on every subsequent level select, without reloading
 * the whole module. [GameLevelStartBridge] is the same pattern, exposed to Swift so
 * AppDelegate.swift can tell the already-warm KorGE view which level to load right before
 * revealing it, instead of always replaying whatever `gameMain()` loaded at launch.
 */
@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "GameLevelStartBridge", exact = true)
object GameLevelStartBridge {
    private var activeSceneContainer: SceneContainer? = null

    internal fun bind(sc: SceneContainer) {
        activeSceneContainer = sc
    }

    /** Called from Swift right before switching the rootViewController to the KorGE view. */
    fun startLevel(levelId: String) {
        val sc = activeSceneContainer ?: return
        val levelData = LevelData.DEFAULT_LEVELS.firstOrNull { it.id == levelId } ?: LevelData.DEFAULT_LEVEL_1
        sc.stage?.launchImmediately {
            // iOS is the platform that cannot know its screen at Korge() time (see
            // DeviceViewport's doc comment), so this is where the real canvas lands: Swift has
            // published the window's size and safe area by now, because a level can only be
            // started from a menu that has been on screen. Must precede changeTo - a Scene copies
            // sceneContainer.size when it is built.
            DeviceViewport.apply(sc.views, sc)
            sc.changeTo { GameplayScene(levelData) }
        }
    }
}

// Same aspect/sizing as commonMain's main.kt (src/main.kt) - kept in sync deliberately, not
// shared directly, since main.kt's own `windowSize` top-level val would collide if imported here.
private val iosWindowSize = Size(1560, 720)

suspend fun gameMain() = Korge(
    windowSize = iosWindowSize,
    // Best guess at construction time: whatever AppDelegate.swift managed to publish before it
    // called into ShellAppDelegate, falling back to the authored 1040x480. The canvas that
    // actually gets used is re-applied per level in startLevel() above.
    virtualSize = DeviceScreen.viewport.let { Size(it.width, it.height) },
    scaleMode = ScaleMode.SHOW_ALL,
    backgroundColor = Colors["#16161d"],
    title = "Infiltrate: Shadow Heist",
) {
    val sc = sceneContainer()
    GameLevelStartBridge.bind(sc)
    DeviceViewport.apply(views, sc)
    sc.changeTo { GameplayScene(LevelData.DEFAULT_LEVEL_1) }
}
