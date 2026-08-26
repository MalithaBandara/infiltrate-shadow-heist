import game.scene.*
import korlibs.image.color.*
import korlibs.korge.*
import korlibs.korge.scene.*
import korlibs.math.geom.*

suspend fun main() = Korge(
    windowSize = Size(800, 480),
    backgroundColor = Colors["#16161d"],
    title = "Infiltrate: Shadow Heist"
) {
    val sceneContainer = sceneContainer()
    sceneContainer.changeTo { GameplayScene() }
}
