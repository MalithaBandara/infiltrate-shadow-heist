import game.model.DeviceScreen
import game.model.LevelData
import game.scene.*
import korlibs.image.color.*
import korlibs.io.lang.Environment
import korlibs.korge.*
import korlibs.korge.scene.*
import korlibs.math.geom.*

/**
 * Desktop window size. Galaxy S25 Ultra landscape aspect (3120x1440) at half scale, which fits a
 * 2K monitor windowed and happens to be exactly 1040x480 dp on that phone - the canvas every
 * scene is authored against (`game.model.ScreenLayout.DESIGN_WIDTH`/`DESIGN_HEIGHT`).
 *
 * Overridable for responsive testing: `./gradlew runJvm -PwindowSize=1024x768` runs the game at an
 * iPad's aspect, `-PwindowSize=932x400` at a 21:9 phone's. That is how the viewport work was
 * verified on this machine, since neither an emulator nor an iOS device is available here (see
 * `.junie/guidelines.md`). Unset, this is the reference phone and nothing changes.
 */
val windowSize: Size = parseWindowSizeOverride(Environment["windowSize"]) ?: Size(1560, 720)

private fun parseWindowSizeOverride(raw: String?): Size? {
    val parts = raw?.trim()?.lowercase()?.split("x") ?: return null
    if (parts.size != 2) return null
    val w = parts[0].trim().toDoubleOrNull() ?: return null
    val h = parts[1].trim().toDoubleOrNull() ?: return null
    if (w < 200.0 || h < 200.0) return null
    return Size(w, h)
}

suspend fun main(args: Array<String>) {
    // Desktop owns its own window, so it knows the screen it is drawing into before KorGE starts.
    // No safe area: a desktop window has no notch or home indicator.
    DeviceScreen.publish(windowSize.width.toDouble(), windowSize.height.toDouble())
    val viewport = DeviceScreen.viewport

    Korge(
        windowSize = windowSize,
        // The canvas carries the window's own aspect ratio, so ScaleMode.SHOW_ALL has nothing left
        // to letterbox - see game.model.ScreenLayout for the rule that picks it. On the reference
        // aspect this is exactly the authored 1040x480 it has always been.
        virtualSize = Size(viewport.width, viewport.height),
        scaleMode = ScaleMode.SHOW_ALL,
        backgroundColor = Colors["#16161d"],
        title = "Infiltrate: Shadow Heist",
        args = args
    ) {
        val sceneContainer = sceneContainer()
        DeviceViewport.apply(views, sceneContainer)
        val levelId = Environment["startLevel"] ?: args.firstOrNull()
        val levelData = if (levelId != null) {
            LevelData.findById(levelId) ?: LevelData.DEFAULT_LEVEL_1
        } else {
            LevelData.DEFAULT_LEVEL_1
        }
        sceneContainer.changeTo { GameplayScene(levelData) }
    }
}

suspend fun main() = main(emptyArray())
