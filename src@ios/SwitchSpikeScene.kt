import korlibs.image.color.Colors
import korlibs.korge.input.mouse
import korlibs.korge.input.onClick
import korlibs.korge.scene.Scene
import korlibs.korge.view.SContainer
import korlibs.korge.view.addUpdater
import korlibs.korge.view.solidRect
import korlibs.korge.view.text
import korlibs.korge.view.xy
import kotlin.math.abs
import kotlin.math.sin

// SPIKE / THROWAWAY - Compose<->KorGE view-switching cost spike only (see
// .junie/guidelines.md). NOT wired into real navigation (SplashScene/MainMenuScene/etc) - only
// reachable via spikeMain() (SpikeEntry.ios.kt), which the shell's AppDelegate points at
// temporarily for this measurement. Delete this file once the spike's answer is in, unless
// promoted deliberately into real product code.
//
// Deliberately never navigates away via sceneContainer.changeTo{} - the whole point of this
// spike is that the KorGE engine + this scene stay warm/resident for the app's lifetime, and
// "switching to KorGE" from the native shell's perspective is purely a UIKit view-visibility
// toggle, not a scene change. addUpdater keeps ticking SpikeBridge.frameTicks every frame this
// scene's Views are updated, regardless of whether the view is currently visible in the UIKit
// hierarchy - that's what lets Swift detect whether GLKViewController's render loop actually
// stops when hidden, or keeps running invisibly.
class SwitchSpikeScene : Scene() {
    override suspend fun SContainer.sceneMain() {
        SpikeBridge.onSpikeSceneActivated()

        solidRect(800.0, 480.0, Colors["#7b2fff"])

        val counterText = text("ticks: 0", textSize = 28.0, color = Colors.WHITE).xy(20.0, 20.0)
        val pulse = solidRect(80.0, 80.0, Colors.WHITE).xy(360.0, 160.0)

        val endBtn = solidRect(240.0, 64.0, Colors["#ff3355"]).xy(280.0, 380.0)
        text("END LEVEL (debug)", textSize = 18.0, color = Colors.WHITE).xy(300.0, 400.0)
        endBtn.mouse { onClick { SpikeBridge.requestLevelEnd() } }

        addUpdater {
            SpikeBridge.onFrameTick()
            val t = SpikeBridge.frameTicks
            counterText.text = "ticks: $t"
            pulse.alpha = 0.4 + 0.6 * abs(sin(t / 30.0))
        }
    }
}
