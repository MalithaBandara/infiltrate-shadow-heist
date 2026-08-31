import korlibs.korge.Korge
import korlibs.korge.scene.sceneContainer
import korlibs.math.geom.Size

// SPIKE / THROWAWAY - separate iOS-only entry point so the switch spike doesn't touch the real
// commonMain src/main.kt (which stays pointed at SplashScene as normal). Only
// ShellAppDelegate.ios.kt's entry lambda points here, and only temporarily for this spike - see
// .junie/guidelines.md.
suspend fun spikeMain() = Korge(
    windowSize = Size(1280, 720),
    virtualSize = Size(800, 480),
    title = "Infiltrate: Shadow Heist (switch spike)",
) {
    sceneContainer().changeTo { SwitchSpikeScene() }
}
