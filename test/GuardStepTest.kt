package test

import game.model.*
import game.scene.GuardAnimations
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.*

/**
 * "guards moving looks like they are floating" (2026-10-26, level 11): the pen guard walks over
 * lift_2's 14-unit deck, and his height used to switch when his centre crossed its edge, drawing
 * the boot he stands on 14 inside the deck or 14 over the floor for a stride either side. He now
 * stands on what is under his weight (GuardWalkStance, from the frames) and gets there fast.
 */
class GuardStepTest {

    @Test
    fun testTheStanceTableMatchesTheWalkFrames() {
        assertEquals(GuardAnimations.WALK_FRAMES, GuardWalkStance.FRAMES)
        for (f in 0 until GuardWalkStance.FRAMES) {
            val img = ImageIO.read(File("art/frames/guard/walk/${(f + 1).toString().padStart(4, '0')}.png"))
            val col = img.width / 2.0 + GuardWalkStance.ahead(f) * GuardAnimations.SOURCE_SILHOUETTE_HEIGHT
            // The weight point is on a sole: something within a couple of columns of it touches the ground.
            val touches = (-2..2).any { d ->
                val x = (col + d).toInt().coerceIn(0, img.width - 1)
                (img.height - 3 until img.height).any { y -> ((img.getRGB(x, y) ushr 24) and 0xFF) > 128 }
            }
            assertTrue(touches, "walk frame ${f + 1}: the weight point (column ${"%.1f".format(col)}) is not on a sole")
        }
    }

    @Test
    fun testThePenGuardStandsOnWhatIsUnderHisWeightAcrossLift2() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_11)
        val guard = world.allGuards.first()
        val deck = world.lifts.first { it.id == "lift_2" }.bounds
        val floorY = guard.y + guard.height
        assertTrue(abs(deck.bottom - floorY) < 0.5, "lift_2 starts down on the pen guard's floor")

        world.player.x = 200.0
        guard.placeAt(deck.left - 80.0, 1.0)
        val dt = 1.0 / 60.0
        var onDeck = 0
        var stepsOff = 0
        var lastY = guard.y
        var crossed = false
        for (k in 0 until 60 * 6) {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
            if (k == 0) lastY = guard.y
            val feet = guard.y + guard.height
            assertTrue(abs(guard.y - lastY) <= GameWorld.GUARD_STEP_RATE * dt + 1e-6 || k == 0,
                "tick $k: the body moved ${abs(guard.y - lastY)} in one tick - a pop, not a step")
            lastY = guard.y
            if (!guard.isWalking) continue
            val weight = guard.x + guard.width / 2.0 +
                guard.facing * GuardWalkStance.ahead((guard.walkPhase * GuardWalkStance.FRAMES).toInt()) * guard.height
            val surface = if (weight >= deck.left && weight <= deck.right) deck.top else floorY
            if (surface == deck.top) { onDeck++; crossed = true }
            if (abs(feet - surface) > 3.0) stepsOff++
        }
        assertTrue(crossed, "the guard has to actually walk onto lift_2 for this to test anything")
        assertTrue(onDeck > 30, "...and along it ($onDeck ticks with his weight on the deck)")
        // Only the few ticks of each lift (14 at GUARD_STEP_RATE is 4-5 ticks) may leave him off.
        assertTrue(stepsOff <= 12, "$stepsOff walking ticks with the standing boot more than 3 off its surface")
    }
}
