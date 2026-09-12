import game.model.LevelData
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
            sc.changeTo { GameplayScene(levelData) }
        }
    }
}

// Same aspect/sizing as commonMain's main.kt (src/main.kt) - kept in sync deliberately, not
// shared directly, since main.kt's own `windowSize` top-level val would collide if imported here.
private val iosWindowSize = Size(1560, 720)

suspend fun gameMain() = Korge(
    windowSize = iosWindowSize,
    virtualSize = Size(480.0 * (iosWindowSize.width / iosWindowSize.height), 480.0),
    scaleMode = ScaleMode.SHOW_ALL,
    backgroundColor = Colors["#16161d"],
    title = "Infiltrate: Shadow Heist",
) {
    val sc = sceneContainer()
    GameLevelStartBridge.bind(sc)
    sc.changeTo { GameplayScene(LevelData.DEFAULT_LEVEL_1) }
}
