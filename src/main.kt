import game.model.LevelData
import game.scene.*
import korlibs.image.color.*
import korlibs.io.lang.Environment
import korlibs.korge.*
import korlibs.korge.scene.*
import korlibs.math.geom.*

// Galaxy S25 Ultra landscape aspect (3120x1440) at half scale, fits a 2K monitor windowed.
val windowSize = Size(1560, 720)

suspend fun main(args: Array<String>) = Korge(
    windowSize = windowSize,
    // Virtual canvas matches the window's aspect ratio exactly (same 480-tall unit scenes were
    // already authored against) so scenes fill the whole window edge-to-edge instead of being
    // letterboxed against a fixed 800x480 that doesn't match modern widescreen phone aspects.
    virtualSize = Size(480.0 * (windowSize.width / windowSize.height), 480.0),
    scaleMode = ScaleMode.SHOW_ALL,
    backgroundColor = Colors["#16161d"],
    title = "Infiltrate: Shadow Heist",
    args = args
) {
    val sceneContainer = sceneContainer()
    val levelId = Environment["startLevel"] ?: args.firstOrNull()
    if (levelId != null) {
        val levelData = LevelData.DEFAULT_LEVELS.firstOrNull { it.id == levelId } ?: LevelData.DEFAULT_LEVEL_1
        sceneContainer.changeTo { GameplayScene(levelData) }
    } else {
        sceneContainer.changeTo { SplashScene() }
    }
}

suspend fun main() = main(emptyArray())

