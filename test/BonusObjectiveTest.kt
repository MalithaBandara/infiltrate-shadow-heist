package test

import game.model.*
import kotlin.test.*

/** The per-level optional objective (LevelData.bonusObjective) that is star 2. */
class BonusObjectiveTest {

    private fun BonusObjectiveTracker.tick(
        jumping: Boolean = false,
        climbing: Boolean = false,
        crouching: Boolean = false,
        grounded: Boolean = true,
        onHanging: Boolean = false,
        atFloor: Boolean = false,
        alert: Boolean = false
    ) = observe(
        isJumping = jumping, isClimbing = climbing, isCrouching = crouching, isGrounded = grounded,
        onHangingCrate = onHanging, atFloorLevel = atFloor, alertRaised = alert
    )

    @Test
    fun testEveryShippedLevelUpToNineCarriesItsOwnOptionalObjective() {
        val expected = listOf(
            BonusObjective.BASIC_MOVES,
            BonusObjective.NO_DROP_FROM_HANGING_CRATES,
            BonusObjective.STAY_UNSEEN,
            BonusObjective.USE_A_GADGET,
            BonusObjective.SWING_FROM_A_HOOK,
            BonusObjective.STAY_UNSEEN,
            BonusObjective.DISABLE_ALL_SECURITY_BOTS,
            BonusObjective.NEVER_TOUCH_A_HANGING_CRATE,
            BonusObjective.STAY_OUT_OF_THE_FIGURES_SIGHT
        )
        assertEquals(expected, LevelData.DEFAULT_LEVELS.take(9).map { it.bonusObjective })
        for (o in BonusObjective.entries) {
            assertNotEquals(Localization.bonusObjective(o, "en"), Localization.bonusObjective(o, "fr"))
        }
    }

    @Test
    fun testBasicMovesNeedsAllThreeAndACrouchMustBeEnteredNotStartedIn() {
        val t = BonusObjectiveTracker(BonusObjective.BASIC_MOVES)
        t.tick(crouching = true) // a crouched start is not a crouch
        t.tick(jumping = true, grounded = false)
        t.tick(climbing = true)
        assertEquals(ObjectiveState.OPEN, t.state(levelComplete = false))
        assertEquals(ObjectiveState.FAILED, t.state(levelComplete = true), "Reaching the exit without the crouch misses it")
        t.tick(crouching = false)
        t.tick(crouching = true)
        assertEquals(ObjectiveState.MET, t.state(levelComplete = false), "Met the moment the third move is done")
        t.reset()
        assertEquals(ObjectiveState.OPEN, t.state(levelComplete = false))
    }

    @Test
    fun testDroppingFromAHangingCrateToTheFloorFailsButRidingOrWalkingOffTerrainDoesNot() {
        val t = BonusObjectiveTracker(BonusObjective.NO_DROP_FROM_HANGING_CRATES)
        t.tick(atFloor = true) // the start
        t.tick(atFloor = false) // terrain
        t.tick(atFloor = true) // walked off terrain to the floor
        t.tick(onHanging = true, atFloor = true) // a container ridden down near the floor
        t.tick(grounded = false)
        assertEquals(ObjectiveState.OPEN, t.state(levelComplete = false))
        t.tick(atFloor = true) // came down off it
        assertEquals(ObjectiveState.FAILED, t.state(levelComplete = false))
        t.onRespawn()
        assertEquals(ObjectiveState.FAILED, t.state(levelComplete = true), "A respawn does not undo a drop")
    }

    @Test
    fun testStayUnseenFailsOnTheFirstAlertAndIsMetOnlyAtTheExit() {
        val t = BonusObjectiveTracker(BonusObjective.STAY_UNSEEN)
        t.tick()
        assertEquals(ObjectiveState.OPEN, t.state(levelComplete = false))
        assertEquals(ObjectiveState.MET, t.state(levelComplete = true))
        t.tick(alert = true)
        assertEquals(ObjectiveState.FAILED, t.state(levelComplete = true))
    }

    @Test
    fun testFallingOffLevel2sFirstHangingCrateMissesTheOptionalObjective() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_2)
        val p = world.player
        val crate = world.hangingCrateVariant1.first { it.top < 400.0 }
        p.resetTo(crate.x + 20.0, crate.top - p.height)
        repeat(30) { world.update(1.0 / 60.0, 0.0, false) }
        assertTrue(p.isGrounded, "Standing on the hanging crate")
        assertEquals(ObjectiveState.OPEN, world.bonusObjectiveState)
        var t = 0.0
        while (t < 4.0 && world.bonusObjectiveState == ObjectiveState.OPEN) {
            world.update(1.0 / 60.0, 1.0, false)
            t += 1.0 / 60.0
        }
        assertEquals(ObjectiveState.FAILED, world.bonusObjectiveState, "Walking off it onto the yard floor is a drop")
        assertTrue(world.getLevelResult().wasDetected, "...and star 2 is missed")
    }

    @Test
    fun testDeployingAnyGadgetOnLevel4MeetsItsOptionalObjective() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        world.update(1.0 / 60.0, 0.0, false)
        assertEquals(ObjectiveState.OPEN, world.bonusObjectiveState)
        assertTrue(world.activatePowerup(PowerupType.LASER_SHIELD))
        assertEquals(ObjectiveState.MET, world.bonusObjectiveState)
        world.restartLevel()
        assertEquals(ObjectiveState.OPEN, world.bonusObjectiveState, "A restart asks for it again")
    }

    @Test
    fun testAnAchievementLeftUndoneIsMissedAtTheExit() {
        val t = BonusObjectiveTracker(BonusObjective.SWING_FROM_A_HOOK)
        t.tick()
        assertEquals(ObjectiveState.FAILED, t.state(levelComplete = true))
        t.observe(isSwinging = true)
        assertEquals(ObjectiveState.MET, t.state(levelComplete = false))
    }

    @Test
    fun testShuttingDownEveryBotCountsBotsARespawnSwitchedBackOn() {
        val t = BonusObjectiveTracker(BonusObjective.DISABLE_ALL_SECURITY_BOTS)
        t.observe(deactivatedBots = listOf("a", "b"), botCount = 3)
        t.onRespawn()
        t.observe(deactivatedBots = emptyList(), botCount = 3) // the respawn switched them back on
        assertEquals(ObjectiveState.OPEN, t.state(levelComplete = false))
        t.observe(deactivatedBots = listOf("c"), botCount = 3)
        assertEquals(ObjectiveState.MET, t.state(levelComplete = false))
    }

    @Test
    fun testTouchingAHangingCrateOnLevel8MissesItsOptionalObjective() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8)
        val p = world.player
        world.update(1.0 / 60.0, 0.0, false)
        assertEquals(ObjectiveState.OPEN, world.bonusObjectiveState)
        val load = world.movingPlatforms.first().bounds
        p.resetTo(load.x + 4.0, load.y - p.height)
        world.update(1.0 / 60.0, 0.0, false)
        assertEquals(ObjectiveState.FAILED, world.bonusObjectiveState)
    }

    @Test
    fun testALevelWithoutOneKeepsNoAlertsRaisedAsStar2() {
        // Every shipped level has one now; a level without one is still allowed.
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_12.copy(bonusObjective = null))
        assertNull(world.bonusTracker)
        assertFalse(world.getLevelResult().wasDetected)
    }

    @Test
    fun testLevel10sSwitchesAreEachUsedOnce() {
        // "asking not to press the same button twice": throwing any switch a second time is out.
        assertEquals(BonusObjective.USE_EACH_SWITCH_ONCE, LevelData.DEFAULT_LEVEL_10.bonusObjective)
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_10)
        val sw = world.doorSwitches.first { it.id == "lvl10_sw_door" }
        world.player.resetTo(sw.centerX - world.player.width / 2.0, 440.0 - 96.0)
        fun press() {
            world.update(1.0 / 60.0, 0.0, false, false, true)
            world.update(1.0 / 60.0, 0.0, false, false, false)
        }
        world.update(1.0 / 60.0, 0.0, false)
        press()
        assertEquals(ObjectiveState.OPEN, world.bonusObjectiveState, "once is fine")
        press()
        assertEquals(ObjectiveState.FAILED, world.bonusObjectiveState, "twice is not")
        world.restartLevel()
        assertEquals(ObjectiveState.OPEN, world.bonusObjectiveState, "a restart opens it again")
    }

    @Test
    fun testTheTracker_UseEachSwitchOnceAndKeepThePrisonerOutOfSight() {
        val once = BonusObjectiveTracker(BonusObjective.USE_EACH_SWITCH_ONCE)
        once.observe(switchReused = false)
        assertEquals(ObjectiveState.MET, once.state(levelComplete = true))
        once.observe(switchReused = true)
        assertEquals(ObjectiveState.FAILED, once.state(levelComplete = true))
        val hidden = BonusObjectiveTracker(BonusObjective.KEEP_THE_PRISONER_OUT_OF_SIGHT)
        hidden.observe(prisonerSeen = false)
        assertEquals(ObjectiveState.OPEN, hidden.state(levelComplete = false))
        hidden.observe(prisonerSeen = true)
        assertEquals(ObjectiveState.FAILED, hidden.state(levelComplete = true))
    }
}
