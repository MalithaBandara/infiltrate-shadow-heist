import kotlin.native.ObjCName

// SPIKE / THROWAWAY - Compose<->KorGE view-switching cost measurement only. Not real product
// code. See .junie/guidelines.md "Compose/KorGE view switching spike" before reusing any of
// this pattern for real cross-framework signaling.
//
// Swift polls `frameTicks` via CADisplayLink to detect: (a) when the KorGE render loop has
// produced a fresh frame after being made visible again (the "switch is playable" signal), and
// (b) whether `frameTicks` keeps changing while the KorGE view is hidden (proves whether
// GLKViewController's internal display link actually stops when off-screen, or keeps burning
// CPU/battery invisibly).
@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "SpikeBridge", exact = true)
object SpikeBridge {
    var frameTicks: Int = 0
        private set

    // -1 until the spike scene's very first render-loop tick fires (only happens once per app
    // launch, since we never navigate KorGE away from this scene - subsequent "switches" are
    // pure UIKit visibility toggles of an already-warm engine, which is the architecture being
    // tested).
    var sceneReadyTick: Int = -1
        private set

    // Set by the in-scene "END LEVEL (debug)" button's onClick. Swift's poll loop checks this.
    var levelEndRequested: Boolean = false
        private set

    fun onFrameTick() {
        frameTicks++
    }

    fun onSpikeSceneActivated() {
        if (sceneReadyTick < 0) sceneReadyTick = frameTicks
    }

    fun requestLevelEnd() {
        levelEndRequested = true
    }

    fun consumeLevelEndRequest(): Boolean {
        if (!levelEndRequested) return false
        levelEndRequested = false
        return true
    }
}
