package test

import game.model.*
import kotlin.math.PI
import kotlin.test.*

class GameplayModelTest {

    @Test
    fun testPlayerMovementAndPlatformCollision() {
        val ground = Rect(0.0, 100.0, 500.0, 20.0)
        val player = Player(x = 50.0, y = 50.0, width = 20.0, height = 40.0)

        // Step physics to let player fall onto ground
        for (i in 0 until 60) {
            player.update(dt = 1.0 / 60.0, moveInput = 0.0, jumpInput = false, platforms = listOf(ground))
        }

        assertTrue(player.isGrounded, "Player should be grounded on platform")
        assertEquals(60.0, player.y, 0.01, "Player bottom (y + 40) should rest on ground top (100.0)")

        // Move right
        player.update(dt = 0.1, moveInput = 1.0, jumpInput = false, platforms = listOf(ground))
        assertTrue(player.x > 50.0, "Player should move right with positive input")

        // Jump
        player.update(dt = 1.0 / 60.0, moveInput = 0.0, jumpInput = true, platforms = listOf(ground))
        assertFalse(player.isGrounded, "Player should leave ground upon jumping")
        assertTrue(player.vy < 0, "Player vertical velocity should be negative after jumping")
    }

    @Test
    fun testPlayerWallCollision() {
        val ground = Rect(0.0, 100.0, 500.0, 20.0)
        val wall = Rect(100.0, 0.0, 20.0, 100.0)
        val player = Player(x = 75.0, y = 60.0, width = 20.0, height = 40.0)

        // Try walking right into the wall
        for (i in 0 until 30) {
            player.update(dt = 1.0 / 60.0, moveInput = 1.0, jumpInput = false, platforms = listOf(ground, wall))
        }

        // Right edge of player should not penetrate wall left edge (100.0)
        assertEquals(80.0, player.x, 0.01, "Player should stop at wall left edge (100 - 20 = 80)")
    }

    @Test
    fun testGuardPatrol() {
        val guard = Guard(
            x = 200.0,
            y = 50.0,
            patrolMinX = 100.0,
            patrolMaxX = 300.0,
            speed = 100.0,
            facing = 1.0
        )

        assertEquals(1.0, guard.facing)
        assertEquals(0.0, guard.facingAngle, 0.001)

        // Patrol right until exceeding maxX
        guard.update(1.5) // moves 150 px -> hits 300.0
        assertEquals(300.0, guard.x, 0.01)
        assertEquals(-1.0, guard.facing, "Guard should reverse direction to -1.0 at patrolMaxX")
        assertEquals(PI, guard.facingAngle, 0.001)

        // Patrol left until exceeding minX
        guard.update(2.5) // moves 250 px left -> hits 100.0
        assertEquals(100.0, guard.x, 0.01)
        assertEquals(1.0, guard.facing, "Guard should reverse direction to 1.0 at patrolMinX")
    }

    @Test
    fun testVisionOcclusionByCrate() {
        val guard = Guard(
            x = 400.0,
            y = 332.0,
            patrolMinX = 300.0,
            patrolMaxX = 500.0,
            facing = -1.0, // Facing left
            visionRange = 300.0,
            visionFov = 60.0 * (PI / 180.0)
        )

        val crate = Rect(x = 250.0, y = 300.0, width = 60.0, height = 80.0)

        // Player hidden behind crate (x = 180)
        val hiddenPlayer = Player(x = 180.0, y = 380.0 - 96.0)
        val isHiddenSpotted = VisionSystem.isPlayerSpotted(guard, hiddenPlayer, listOf(crate))
        assertFalse(isHiddenSpotted, "Player behind crate must NOT be spotted by guard")

        // Player walked past crate into clear view (x = 330, between crate and guard)
        val visiblePlayer = Player(x = 330.0, y = 380.0 - 96.0)
        val isVisibleSpotted = VisionSystem.isPlayerSpotted(guard, visiblePlayer, listOf(crate))
        assertTrue(isVisibleSpotted, "Player in clear line of sight MUST be spotted by guard")

        // If guard turns away (facing right), visible player is now outside FOV
        guard.facing = 1.0
        val isSpottedWhenFacingAway = VisionSystem.isPlayerSpotted(guard, visiblePlayer, listOf(crate))
        assertFalse(isSpottedWhenFacingAway, "Player behind guard's back must NOT be spotted")
    }

    @Test
    fun testGameWorldDetectionGracePeriodAndReset() {
        val world = GameWorld.createDefault()
        world.setUniformDetectionTime(0.8)
        world.alertDecayRate = 0.5
        val startX = world.player.startX
        val startY = world.player.startY

        // Guard starts facing left at 560
        // Move player into guard's line of sight (e.g. at x = 400)
        world.player.x = 400.0
        world.player.y = 380.0 - 96.0

        assertEquals(0, world.spottedCount)
        assertEquals(0.0, world.alertProgress)

        // Step world for 0.4 seconds (halfway through detection time)
        world.update(dt = 0.4, moveInput = 0.0, jumpInput = false)

        assertTrue(world.isPlayerInVision, "Player should be detected in vision")
        assertEquals(0.5, world.alertProgress, 0.01, "Alert progress should be at 50% (0.4 / 0.8)")
        assertFalse(world.isSpotted, "Player should not yet be caught during grace period")
        assertEquals(0, world.spottedCount, "Alert count should still be 0")
        assertEquals(400.0, world.player.x, 0.01, "Player should not have been reset yet")

        // Step world another 0.5 seconds (total 0.9s > 0.8s threshold)
        world.update(dt = 0.5, moveInput = 0.0, jumpInput = false)

        assertTrue(world.isSpotted, "World should register isSpotted = true upon reaching threshold")
        assertEquals(1, world.spottedCount, "Spotted counter should increment to 1")
        assertEquals(startX, world.player.x, 0.01, "Player should be reset to start position X")
        assertEquals(startY, world.player.y, 0.01, "Player should be reset to start position Y")
    }

    @Test
    fun testAlertProgressDecayWhenLeavingVision() {
        val world = GameWorld.createDefault()
        world.setUniformDetectionTime(0.8)
        world.alertDecayRate = 0.5

        // Put player in vision for 0.4s -> 50% alertProgress
        world.player.x = 400.0
        world.player.y = 380.0 - 96.0
        world.update(dt = 0.4, moveInput = 0.0, jumpInput = false)
        assertEquals(0.5, world.alertProgress, 0.01)

        // Hide player behind crate (x = 180)
        world.player.x = 180.0
        world.update(dt = 0.4, moveInput = 0.0, jumpInput = false)

        assertFalse(world.isPlayerInVision, "Player should now be hidden")
        // Alert progress should decay: 0.5 - 0.5 * 0.4 = 0.3
        assertEquals(0.3, world.alertProgress, 0.01, "Alert progress should decay when out of sight")

        // Wait another 0.8s -> should decay to 0.0
        world.update(dt = 0.8, moveInput = 0.0, jumpInput = false)
        assertEquals(0.0, world.alertProgress, 0.001, "Alert progress should reach 0.0")
    }

    @Test
    fun testPlayerGuardCollision() {
        val world = GameWorld.createDefault()
        // Disable vision detection during collision-only test
        world.minDetectionTime = 9999.0
        world.maxDetectionTime = 9999.0

        // Place guard stationary at x = 500, y = 332
        world.guard.x = 500.0
        world.guard.speed = 0.0 // Keep guard fixed for test
        world.guard.facing = 1.0 // Facing right (away from left player)

        // Place player to the left of guard at x = 450
        world.player.x = 450.0
        world.player.y = 284.0

        // Move right towards guard
        for (i in 0 until 60) {
            world.update(dt = 1.0 / 60.0, moveInput = 1.0, jumpInput = false)
        }

        // Player width is 36.0, guard left is 500.0 -> player should stop at 464.0 (500 - 36)
        assertEquals(464.0, world.player.x, 0.01, "Player should collide with guard's left edge and not pass through")

        // Place player to the right of guard at x = 550
        world.player.x = 550.0
        world.player.y = 284.0

        // Move left towards guard
        for (i in 0 until 60) {
            world.update(dt = 1.0 / 60.0, moveInput = -1.0, jumpInput = false)
        }

        // Guard right edge is 500.0 + 26.0 = 526.0 -> player should stop at 526.0
        assertEquals(526.0, world.player.x, 0.01, "Player should collide with guard's right edge and not pass through")

        // Test guard pushing stationary player when not in vision cone
        val movingGuard = Guard(
            x = 480.0,
            y = 332.0,
            patrolMinX = 300.0,
            patrolMaxX = 700.0,
            speed = 60.0,
            facing = -1.0, // Guard moving left towards player at 400
            visionRange = 0.0 // Vision disabled for pure physics push test
        )
        val pushWorld = world.copy(guard = movingGuard)
        pushWorld.player.x = 400.0
        pushWorld.player.y = 284.0

        // Guard moves left 30px (from 480 to 450, guard.left = 450)
        pushWorld.update(dt = 0.5, moveInput = 0.0, jumpInput = false)
        assertEquals(450.0, movingGuard.x, 0.01, "Guard should have reached x = 450")
        assertEquals(400.0, pushWorld.player.x, 0.01, "Guard moving into stationary player should push player to guard.left - width (450 - 50 = 400)")
    }

    @Test
    fun testDistanceScaledDetectionRate() {
        // Test 1: Close range (player 60px away from guard, e.g. at x = 440)
        val closeWorld = GameWorld.createDefault().copy(occluders = emptyList())
        closeWorld.guard.x = 500.0
        closeWorld.guard.facing = -1.0 // Facing left
        closeWorld.guard.speed = 0.0
        closeWorld.player.x = 440.0 // player right at 490, 10px from guard left (500)
        closeWorld.player.y = 284.0

        closeWorld.update(dt = 0.1, moveInput = 0.0, jumpInput = false)
        val closeAlertProgress = closeWorld.alertProgress

        // Test 2: Far range (player 230px away from guard, e.g. at x = 270)
        val farWorld = GameWorld.createDefault().copy(occluders = emptyList())
        farWorld.guard.x = 500.0
        farWorld.guard.facing = -1.0 // Facing left
        farWorld.guard.speed = 0.0
        farWorld.player.x = 270.0 // ~234px from guard eye (504), near max range 260
        farWorld.player.y = 284.0

        farWorld.update(dt = 0.1, moveInput = 0.0, jumpInput = false)
        val farAlertProgress = farWorld.alertProgress

        assertTrue(closeAlertProgress > 0.0, "Close alert progress should be positive")
        assertTrue(farAlertProgress > 0.0, "Far alert progress should be positive")
        assertTrue(
            closeAlertProgress >= farAlertProgress * 2.0,
            "Close range alert progress ($closeAlertProgress) should fill much faster (>2x) than far range ($farAlertProgress)"
        )
    }

    @Test
    fun testLevelCompleteWinCondition() {
        val world = GameWorld.createDefault()
        var levelCompleteCallbackCalled = false
        world.onLevelComplete = {
            levelCompleteCallbackCalled = true
        }

        assertFalse(world.isLevelComplete)

        // Move player to overlap exit zone (x = 730, y = 320)
        world.player.x = 735.0
        world.player.y = 284.0

        world.update(dt = 1.0 / 60.0, moveInput = 0.0, jumpInput = false)

        assertTrue(world.isLevelComplete, "Level should be complete when player overlaps exit zone")
        assertTrue(levelCompleteCallbackCalled, "onLevelComplete callback should have been fired")

        // Further updates should not move entities or alter status
        val guardX = world.guard.x
        world.update(dt = 1.0, moveInput = 1.0, jumpInput = false)
        assertEquals(guardX, world.guard.x, "Guard should not move once level is complete")
    }

    @Test
    fun testVisionPolygonGeneration() {
        val crate = Rect(x = 100.0, y = -50.0, width = 50.0, height = 100.0)
        val origin = Vec2d(0.0, 0.0)

        val polygon = VisionSystem.computeVisionPolygon(
            origin = origin,
            facingAngle = 0.0,
            range = 200.0,
            fov = PI / 2.0,
            occluders = listOf(crate)
        )

        assertTrue(polygon.size > 10, "Polygon should contain origin and ray hit points")
        assertEquals(origin, polygon.first(), "First vertex of vision polygon should be origin")

        // Ray directly forward (angle 0.0) hits crate front edge at x = 100.0
        val centerRayHit = GeometryUtils.castRay(origin, 0.0, 200.0, listOf(crate))
        assertEquals(100.0, centerRayHit.x, 0.01)
        assertEquals(0.0, centerRayHit.y, 0.01)
    }

    @Test
    fun testStarRatingSystemLogic() {
        // Star 1: completed == true
        // Star 2: wasDetected == false
        // Star 3: timeTaken <= timeTargetSeconds

        // Case 1: Perfect stealth speedrun
        val perfectResult = LevelResult(
            levelId = "level_1",
            completed = true,
            wasDetected = false,
            timeTaken = 10.5f,
            timeTargetSeconds = 15.0f
        )
        assertTrue(perfectResult.star1, "Star 1 should be earned when completed")
        assertTrue(perfectResult.star2, "Star 2 should be earned when undetected")
        assertTrue(perfectResult.star3, "Star 3 should be earned when time <= target")
        assertEquals(3, perfectResult.starCount)

        // Case 2: Completed, but detected (caught) and slow time
        val slowDetectedResult = LevelResult(
            levelId = "level_1",
            completed = true,
            wasDetected = true,
            timeTaken = 22.0f,
            timeTargetSeconds = 15.0f
        )
        assertTrue(slowDetectedResult.star1, "Star 1 earned for completion")
        assertFalse(slowDetectedResult.star2, "Star 2 not earned because detected")
        assertFalse(slowDetectedResult.star3, "Star 3 not earned because slow")
        assertEquals(1, slowDetectedResult.starCount)

        // Case 3: Completed undetected but slow time
        val stealthSlowResult = LevelResult(
            levelId = "level_1",
            completed = true,
            wasDetected = false,
            timeTaken = 18.0f,
            timeTargetSeconds = 15.0f
        )
        assertTrue(stealthSlowResult.star1)
        assertTrue(stealthSlowResult.star2)
        assertFalse(stealthSlowResult.star3)
        assertEquals(2, stealthSlowResult.starCount)

        // Case 4: Fast but caught (detected speedrun)
        val fastCaughtResult = LevelResult(
            levelId = "level_1",
            completed = true,
            wasDetected = true,
            timeTaken = 11.0f,
            timeTargetSeconds = 15.0f
        )
        assertTrue(fastCaughtResult.star1)
        assertFalse(fastCaughtResult.star2)
        assertTrue(fastCaughtResult.star3)
        assertEquals(2, fastCaughtResult.starCount)

        // Case 5: Incomplete level (abandoned run)
        val incompleteResult = LevelResult(
            levelId = "level_1",
            completed = false,
            wasDetected = false,
            timeTaken = 5.0f,
            timeTargetSeconds = 15.0f
        )
        assertFalse(incompleteResult.star1)
    }

    @Test
    fun testLevelStorageAndPersistence() {
        val storage: LevelStorage = InMemoryLevelStorage()

        assertNull(storage.getBestResult("level_1"))

        // Run 1: Slow stealth (2 stars: star1, star2)
        val run1 = LevelResult("level_1", completed = true, wasDetected = false, timeTaken = 20.0f, timeTargetSeconds = 15.0f)
        storage.saveResult(run1)

        val stored1 = storage.getBestResult("level_1")
        assertNotNull(stored1)
        assertEquals(2, stored1.starCount)
        assertEquals(20.0f, stored1.timeTaken)

        // Run 2: Fast but detected (2 stars: star1, star3 with faster time 11.0s)
        val run2 = LevelResult("level_1", completed = true, wasDetected = true, timeTaken = 11.0f, timeTargetSeconds = 15.0f)
        storage.saveResult(run2)

        // Merged best result should retain best undetected achievement and best time (yielding 3 stars!)
        val stored2 = storage.getBestResult("level_1")
        assertNotNull(stored2)
        assertTrue(stored2.star1)
        assertTrue(stored2.star2, "Best record should retain undetected star from Run 1")
        assertTrue(stored2.star3, "Best record should retain fast time star from Run 2")
        assertEquals(3, stored2.starCount)
        assertEquals(11.0f, stored2.timeTaken, 0.01f)

        // Test MapBacked storage serialization
        val map = mutableMapOf<String, String>()
        val mapStorage = MapBackedLevelStorage(
            getRaw = { map[it] },
            setRaw = { k, v -> map[k] = v }
        )
        mapStorage.saveResult(stored2)

        val fromMap = mapStorage.getBestResult("level_1")
        assertNotNull(fromMap)
        assertEquals(3, fromMap.starCount)
        assertEquals(11.0f, fromMap.timeTaken, 0.01f)
    }

    @Test
    fun testPlayerMovementNoiseAndCrouchTradeoff() {
        val ground = Rect(0.0, 100.0, 500.0, 20.0)
        val player = Player(x = 50.0, y = 60.0, width = 20.0, height = 40.0)

        // Settle player on ground
        for (i in 0 until 10) {
            player.update(dt = 1.0 / 60.0, moveInput = 0.0, jumpInput = false, crouchInput = false, platforms = listOf(ground))
        }

        // 1. Standing idle: Silent noise
        assertEquals(NoiseLevel.SILENT, player.currentNoiseLevel)
        assertEquals(0.0, player.currentNoiseRadius)
        assertFalse(player.isMoving)

        // 2. Walking/running: Normal noise
        player.update(dt = 0.1, moveInput = 1.0, jumpInput = false, crouchInput = false, platforms = listOf(ground))
        assertTrue(player.isMoving)
        assertFalse(player.isCrouching)
        assertEquals(NoiseLevel.NORMAL, player.currentNoiseLevel)
        assertEquals(180.0, player.currentNoiseRadius)
        val normalVx = player.vx
        assertEquals(132.0, normalVx, 0.01, "Normal walk speed should be 132.0")

        // 3. Crouch-moving: Silent noise and reduced speed (65.0)
        player.update(dt = 0.1, moveInput = 1.0, jumpInput = false, crouchInput = true, platforms = listOf(ground))
        assertTrue(player.isCrouching, "Player should be in crouching stance")
        assertEquals(NoiseLevel.SILENT, player.currentNoiseLevel, "Crouch movement MUST be silent (0.0 noise radius)")
        assertEquals(0.0, player.currentNoiseRadius)
        val crouchVx = player.vx
        assertEquals(65.0, crouchVx, 0.01, "Crouch movement speed must be slower (tradeoff for silence)")
    }

    @Test
    fun testGuardInvestigateOnNoiseEvent() {
        val world = GameWorld.createDefault()
        // Disable vision detection to isolate noise detection
        world.guard.visionRange = 0.0
        world.minDetectionTime = 9999.0
        world.maxDetectionTime = 9999.0

        // Guard at x = 560, facing left (-1.0)
        assertEquals(GuardState.PATROL, world.guard.state)

        // Place player at x = 450 (distance ~110px < 180px walk noise radius)
        world.player.x = 450.0
        world.player.y = 284.0

        // Player moves while crouching -> SILENT -> Guard remains in PATROL
        world.update(dt = 0.1, moveInput = 1.0, jumpInput = false, crouchInput = true)
        assertEquals(GuardState.PATROL, world.guard.state, "Guard must not investigate when player crouch-moves silently")

        // Player moves while walking (crouchInput = false) -> emits noise -> Guard transitions to INVESTIGATING
        world.update(dt = 0.1, moveInput = 1.0, jumpInput = false, crouchInput = false)
        assertEquals(GuardState.INVESTIGATING, world.guard.state, "Guard must transition to INVESTIGATING upon hearing noise")
        assertEquals(-1.0, world.guard.facing, "Guard should face left toward sound")
        val guardXAfterNoise = world.guard.x

        // Guard should remain stationary and look towards sound
        world.update(dt = 0.5, moveInput = 0.0, jumpInput = false, crouchInput = true)
        assertEquals(guardXAfterNoise, world.guard.x, "Guard must not move towards sound position")
        assertEquals(-1.0, world.guard.facing)

        // After investigate duration timeout, guard resumes PATROL
        world.update(dt = 2.5, moveInput = 0.0, jumpInput = false, crouchInput = true)
        assertEquals(GuardState.PATROL, world.guard.state, "Guard should return to PATROL after timeout")
    }

    @Test
    fun testGuardTurnsToSoundWithoutMovingAndResumesPatrolRoute() {
        val guard = Guard(
            x = 500.0,
            y = 332.0,
            patrolMinX = 400.0,
            patrolMaxX = 600.0,
            speed = 100.0,
            facing = 1.0, // moving right
            investigateDuration = 2.0
        )

        // Guard patrols right for 0.5s -> x moves to 550.0
        guard.update(0.5)
        assertEquals(550.0, guard.x, 0.01)
        assertEquals(1.0, guard.facing)
        assertEquals(1.0, guard.patrolFacing)

        // Guard hears sound to the left (behind it, e.g. x = 450.0)
        guard.onNoiseHeard(450.0)
        assertEquals(GuardState.INVESTIGATING, guard.state)
        assertEquals(-1.0, guard.facing, "Guard should turn around and face left toward sound")
        assertEquals(550.0, guard.x, 0.01, "Guard position must not change")

        // Update 1.0s while investigating: guard must not move
        guard.update(1.0)
        assertEquals(550.0, guard.x, 0.01, "Guard must remain stationary while investigating sound")
        assertEquals(-1.0, guard.facing, "Guard must continue looking at sound direction")

        // Update another 1.1s (total 2.1s >= 2.0s duration) -> resumes original route
        guard.update(1.1)
        assertEquals(GuardState.PATROL, guard.state)
        assertEquals(1.0, guard.facing, "Guard should resume original route moving right")

        // Further patrol updates move guard right again
        guard.update(0.1)
        assertTrue(guard.x > 550.0, "Guard should continue moving right along original route")
    }

    @Test
    fun testGuardInvestigateOnVisualLostMidAlert() {
        val world = GameWorld.createDefault()
        world.setUniformDetectionTime(0.8)
        world.alertDecayRate = 0.5

        // Guard facing left at 560
        // Place player in vision at 400
        world.player.x = 400.0
        world.player.y = 284.0

        // Step 0.3s -> alert progress builds up to ~0.375
        world.update(dt = 0.3, moveInput = 0.0, jumpInput = false)
        assertTrue(world.isPlayerInVision)
        assertTrue(world.alertProgress > 0.0)
        assertEquals(GuardState.PATROL, world.guard.state)

        // Move player behind crate (hidden)
        world.player.x = 180.0
        world.player.y = 284.0
        world.update(dt = 0.1, moveInput = 0.0, jumpInput = false)

        assertFalse(world.isPlayerInVision, "Player should be out of sight")
        assertEquals(GuardState.INVESTIGATING, world.guard.state, "Guard must transition to INVESTIGATING when losing visual mid-alert")
    }

    @Test
    fun testOnlyGuardWhoSpottedPlayerInvestigatesOnVisualLost() {
        val guard1 = Guard(
            x = 560.0,
            y = 380.0 - 48.0,
            patrolMinX = 500.0,
            patrolMaxX = 600.0,
            speed = 50.0,
            facing = -1.0,
            visionRange = 260.0
        )
        val guard2 = Guard(
            x = 750.0,
            y = 380.0 - 48.0,
            patrolMinX = 700.0,
            patrolMaxX = 790.0,
            speed = 50.0,
            facing = 1.0, // facing right away from player
            visionRange = 260.0
        )
        val player = Player(x = 400.0, y = 380.0 - 96.0, startX = 400.0, startY = 380.0 - 96.0)
        val crate = Rect(x = 250.0, y = 300.0, width = 60.0, height = 80.0)
        val world = GameWorld(
            player = player,
            guard = guard1,
            extraGuards = listOf(guard2),
            crate = crate,
            platforms = listOf(Rect(0.0, 380.0, 800.0, 100.0), crate),
            occluders = listOf(crate)
        )
        world.setUniformDetectionTime(1.0)
        world.alertDecayRate = 0.5

        // Step 1: Guard1 spots player at x=400, Guard2 is at x=750 facing right (does not see player)
        world.update(dt = 0.3, moveInput = 0.0, jumpInput = false)
        assertTrue(world.isPlayerInVision, "Guard 1 should have eyes on player")
        assertTrue(world.alertProgress > 0.0, "Alert should accumulate")
        assertEquals(GuardState.PATROL, guard1.state)
        assertEquals(GuardState.PATROL, guard2.state)

        // Step 2: Player moves behind crate at x = 180 (lost line of sight)
        world.player.x = 180.0
        world.player.y = 284.0
        world.update(dt = 0.1, moveInput = 0.0, jumpInput = false)

        assertFalse(world.isPlayerInVision, "Player is now hidden")
        assertEquals(GuardState.INVESTIGATING, guard1.state, "Guard 1 who spotted player must transition to INVESTIGATING")
        assertEquals(GuardState.PATROL, guard2.state, "Guard 2 who did not spot player must remain in PATROL")
    }

    @Test
    fun testGuardInvestigateLookAroundAndTimeoutReturnToPatrol() {
        val guard = Guard(
            x = 500.0,
            y = 332.0,
            patrolMinX = 400.0,
            patrolMaxX = 600.0,
            speed = 100.0,
            facing = 1.0,
            investigateDuration = 2.0
        )

        assertEquals(GuardState.PATROL, guard.state)

        // Trigger investigate towards target x = 450 (50px left)
        guard.startInvestigating(450.0)
        assertEquals(GuardState.INVESTIGATING, guard.state)
        assertEquals(-1.0, guard.facing, "Guard should face left toward target")
        assertEquals(500.0, guard.x, 0.01)

        // Advance timer to reach 2.0s duration -> timeout return to patrol
        guard.update(2.1)
        assertEquals(GuardState.PATROL, guard.state, "Guard should return to PATROL after investigate duration timeout")
        assertEquals(1.0, guard.facing, "Guard should resume patrol heading along original route")
    }

    @Test
    fun testGuardInvestigateRedetectionAndEscalation() {
        val world = GameWorld.createDefault()
        world.setUniformDetectionTime(0.5)

        // Start guard investigating at x = 500
        world.guard.startInvestigating(500.0)
        assertEquals(GuardState.INVESTIGATING, world.guard.state)

        // Put player in vision cone
        world.guard.x = 520.0
        world.guard.facing = -1.0
        world.player.x = 420.0
        world.player.y = 284.0

        // Step detection
        world.update(dt = 0.6, moveInput = 0.0, jumpInput = false)

        assertTrue(world.wasDetected, "wasDetected flag should be set to true on catch")
        assertEquals(1, world.spottedCount, "Spotted count should increment")
        assertEquals(GuardState.PATROL, world.guard.state, "Guard should return to patrol after catching player")
    }

    @Test
    fun testGuardStopsAtPositionWhenUserDetectedInVisionCone() {
        val world = GameWorld.createDefault().copy(occluders = emptyList())
        world.setUniformDetectionTime(1.0)
        world.guard.x = 480.0
        world.guard.facing = 1.0 // Patrolling right
        world.guard.speed = 100.0

        // Place player ahead in guard's vision cone at x = 580.0
        world.player.x = 580.0
        world.player.y = 284.0

        // Step 0.2s: player is detected in cone
        world.update(dt = 0.2, moveInput = 0.0, jumpInput = false)

        assertTrue(world.isPlayerInVision, "Player should be detected in vision cone")
        assertTrue(world.alertProgress > 0.0, "Alert progress should be accumulating")
        assertEquals(480.0, world.guard.x, 0.001, "Guard must stop at current position (480.0) and not advance while detecting player")

        // Step another 0.3s (alert progress ~0.5)
        world.update(dt = 0.3, moveInput = 0.0, jumpInput = false)
        assertTrue(world.isPlayerInVision)
        assertEquals(480.0, world.guard.x, 0.001, "Guard must remain stopped at x = 480.0 during continuous detection")
    }

    @Test
    fun testGuardResumesPatrolAfterLosingVisualFromConeDetection() {
        val world = GameWorld.createDefault()
        world.setUniformDetectionTime(1.0)
        world.guard.x = 480.0
        world.guard.facing = 1.0 // Patrolling right
        world.guard.speed = 100.0
        world.guard.investigateDuration = 1.5

        // Place player in guard vision cone
        world.player.x = 580.0
        world.player.y = 284.0

        // Step 0.2s: guard detects player and stops at 480.0
        world.update(dt = 0.2, moveInput = 0.0, jumpInput = false)
        assertEquals(480.0, world.guard.x, 0.001)

        // Player moves behind crate out of sight
        world.player.x = 100.0
        world.player.y = 284.0
        world.update(dt = 0.1, moveInput = 0.0, jumpInput = false)
        assertFalse(world.isPlayerInVision)
        assertEquals(GuardState.INVESTIGATING, world.guard.state)
        assertEquals(480.0, world.guard.x, 0.001, "Guard remains stopped while investigating")

        // Advance past investigateDuration (1.5s) -> guard returns to patrol
        world.update(dt = 1.6, moveInput = 0.0, jumpInput = false)
        assertEquals(GuardState.PATROL, world.guard.state)

        // Subsequent patrol update moves guard along patrol route
        world.update(dt = 0.1, moveInput = 0.0, jumpInput = false)
        assertTrue(world.guard.x > 480.0, "Guard should resume patrol movement after investigate timeout")
    }

    @Test
    fun testGuardNeverMovesTowardsPlayerWhenHeardOrDetected() {
        val guard = Guard(
            x = 500.0,
            y = 332.0,
            patrolMinX = 300.0,
            patrolMaxX = 700.0,
            speed = 100.0,
            facing = 1.0,
            investigateDuration = 2.0
        )

        // 1. Guard hears sound far to the left at x = 350
        guard.onNoiseHeard(350.0)
        assertEquals(500.0, guard.x, 0.001, "Guard must remain stationary at x=500")
        assertEquals(-1.0, guard.facing, "Guard must look left toward sound")

        // Step 1.0s while investigating
        guard.update(1.0)
        assertEquals(500.0, guard.x, 0.001, "Guard must NOT move towards target x=350 while investigating")

        // 2. Guard hears another sound far to the right at x = 650
        guard.onNoiseHeard(650.0)
        assertEquals(500.0, guard.x, 0.001, "Guard must remain stationary at x=500")
        assertEquals(1.0, guard.facing, "Guard must look right toward new sound")

        // Step 1.0s while investigating new sound
        guard.update(1.0)
        assertEquals(500.0, guard.x, 0.001, "Guard must NOT move towards target x=650 while investigating")

        // Step remaining 1.1s to finish investigation timeout (total 2.1s >= 2.0s)
        guard.update(1.1)
        assertEquals(GuardState.PATROL, guard.state)
        assertEquals(500.0, guard.x, 0.001, "Guard must remain at x=500 when returning to patrol")
        assertEquals(1.0, guard.facing, "Guard must resume original route moving right")

        // Next patrol step moves guard along original route
        guard.update(0.5)
        assertEquals(550.0, guard.x, 0.001, "Guard resumes patrol towards right (500 + 100*0.5 = 550)")
    }

    @Test
    fun testGameProfileStorageCoinTransactionsAndPowerups() {
        val storage = InMemoryGameProfileStorage(GameProfile(coins = 200))
        assertEquals(200, storage.getProfile().coins)

        // Add coins
        val newBalance = storage.addCoins(150)
        assertEquals(350, newBalance)
        assertEquals(350, storage.getProfile().coins)

        // Buy powerup successfully
        val boughtSmoke = storage.buyPowerup("smoke_bomb", 100)
        assertTrue(boughtSmoke)
        assertEquals(250, storage.getProfile().coins)
        assertEquals(2, storage.getProfile().powerupInventory["smoke_bomb"])

        // Buy powerup with insufficient coins
        val boughtExpensive = storage.buyPowerup("radar_booster", 500)
        assertFalse(boughtExpensive)
        assertEquals(250, storage.getProfile().coins)
        assertEquals(0, storage.getProfile().powerupInventory["radar_booster"])
    }

    @Test
    fun testGameProfileStorageLevelUnlockingProgression() {
        val levelStorage = InMemoryLevelStorage()
        val profileStorage = InMemoryGameProfileStorage()
        val levels = LevelData.DEFAULT_LEVELS

        // Level 1 should be unlocked by default
        assertTrue(profileStorage.isLevelUnlocked("level_1", levels, levelStorage))

        // Level 2 should be locked before level 1 is completed
        assertFalse(profileStorage.isLevelUnlocked("level_2", levels, levelStorage))

        // Complete level 1
        levelStorage.saveResult(
            LevelResult(
                levelId = "level_1",
                completed = true,
                wasDetected = false,
                timeTaken = 10.0f,
                timeTargetSeconds = 15.0f
            )
        )

        // Level 2 should now be unlocked via progression
        assertTrue(profileStorage.isLevelUnlocked("level_2", levels, levelStorage))
        // Level 3 should still be locked
        assertFalse(profileStorage.isLevelUnlocked("level_3", levels, levelStorage))

        // Premium pass unlocks all levels immediately
        profileStorage.activatePremium()
        assertTrue(profileStorage.getProfile().isPremium)
        assertTrue(profileStorage.isLevelUnlocked("level_3", levels, levelStorage))
    }

    @Test
    fun testSideScrollLevelIsBeatable() {
        val world = GameWorld.createDefault(LevelData.SIDE_SCROLL_LEVEL)
        assertEquals(2800.0, world.worldWidth, "Side-scroll level should be wider than the 800px screen")
        assertEquals(3, world.allGuards.size, "Level should have three guards")

        val dt = 1.0 / 60.0
        var elapsed = 0.0
        var stalledFor = 0.0

        // Auto-pilot: hold right, and jump whenever forward progress stalls while grounded.
        // That is all the intended route needs - every climb is a 36 unit step box, and the
        // upper walkways are entered by walking onto them, not by precise jumps.
        while (elapsed < 90.0 && !world.isLevelComplete && !world.isGameOver) {
            val beforeX = world.player.x
            val jump = world.player.isGrounded && stalledFor > 0.05
            world.update(dt, moveInput = 1.0, jumpInput = jump, crouchInput = false)
            stalledFor = if (kotlin.math.abs(world.player.x - beforeX) < 0.5) stalledFor + dt else 0.0
            elapsed += dt
        }

        assertTrue(
            world.isLevelComplete,
            "Walking right should reach the exit. Ended at x=${world.player.x.toInt()} y=${world.player.y.toInt()} " +
                "after ${elapsed.toInt()}s (gameOver=${world.isGameOver}, alerts=${world.spottedCount})"
        )
        assertFalse(world.wasDetected, "The intended route stays out of every guard vision cone")
        assertEquals(0, world.spottedCount, "The intended route should never trigger an alert")
    }

    @Test
    fun testSideScrollLevelUpperTiersBlockGuardSight() {
        val world = GameWorld.createDefault(LevelData.SIDE_SCROLL_LEVEL)
        val groundGuard = world.allGuards.first()

        // Standing on mid tier 1 directly above the ground guard must be hidden by the floor
        val playerOnTier = Player(x = groundGuard.x, y = 368.0 - 96.0)
        assertFalse(
            VisionSystem.isPlayerSpotted(groundGuard, playerOnTier, world.occluders),
            "A guard must not see through the walkway floor above him"
        )

        // The same spot on the ground, right in front of him, is very much visible
        val playerOnGround = Player(x = groundGuard.x - 120.0, y = 440.0 - 96.0)
        groundGuard.facing = -1.0
        assertTrue(
            VisionSystem.isPlayerSpotted(groundGuard, playerOnGround, world.occluders),
            "A guard must still see an unobstructed player on his own floor"
        )
    }

    @Test
    fun testPlayerWholeBodyPhysicsWithOverheadWallsAndCeilings() {
        val ground = Rect(x = 0.0, y = 380.0, width = 800.0, height = 50.0)
        // Overhead wall that only blocks the upper half of a standing character (e.g., from y=250 to 310, clearance below is 380 - 310 = 70px)
        val overheadWall = Rect(x = 300.0, y = 250.0, width = 100.0, height = 60.0)
        val platforms = listOf(ground, overheadWall)

        // 1. Standing player (height 96.0, on ground y = 284..380)
        val standingPlayer = Player(x = 200.0, y = 284.0, width = 50.0, height = 96.0, crouchHeight = 56.0)
        for (i in 0 until 60) {
            standingPlayer.update(dt = 1.0 / 60.0, moveInput = 1.0, jumpInput = false, crouchInput = false, platforms = platforms)
        }

        // Standing player's head/upper body (y: 284..310) intersects overhead wall (y: 250..310)
        // Player must collide with overhead wall at x = 300 - 50 = 250.0 and NOT pass through!
        assertEquals(250.0, standingPlayer.x, 0.01, "Standing player's upper body must collide with overhead wall and not pass through")

        // 2. Crouching player (crouchHeight 56.0, top at 380 - 56 = 324.0, clearance below wall is 310..380 = 70px)
        val crouchingPlayer = Player(x = 200.0, y = 284.0, width = 50.0, height = 96.0, crouchHeight = 56.0)
        for (i in 0 until 180) {
            crouchingPlayer.update(dt = 1.0 / 60.0, moveInput = 1.0, jumpInput = false, crouchInput = true, platforms = platforms)
        }

        // Crouching player fits under 70px clearance (since crouchHeight 56 <= 70) and crawls past x=300
        assertTrue(crouchingPlayer.x > 300.0, "Crouching player should crawl cleanly under overhead wall")
    }

    @Test
    fun testPlayerLegsAndBodyDoNotPenetrateObstacleEdges() {
        val ground = Rect(x = 0.0, y = 380.0, width = 800.0, height = 50.0)
        val obstacle = Rect(x = 200.0, y = 300.0, width = 60.0, height = 80.0)
        val platforms = listOf(ground, obstacle)

        val player = Player(x = 100.0, y = 284.0) // default width 36.0, height 96.0

        // Walk right into the obstacle
        for (i in 0 until 90) {
            player.update(dt = 1.0 / 60.0, moveInput = 1.0, jumpInput = false, platforms = platforms)
        }

        // The player's full body (width 36.0) must stop cleanly at obstacle.left (200.0) -> x = 164.0
        assertEquals(164.0, player.x, 0.01, "Player full body and legs must stop before obstacle left edge")
        assertTrue(player.x + player.width <= obstacle.left, "Player right edge must never penetrate inside obstacle")
    }

    @Test
    fun testPlayerHeadHitsCeilingWhenJumping() {
        val ground = Rect(x = 0.0, y = 380.0, width = 500.0, height = 20.0)
        val ceiling = Rect(x = 0.0, y = 240.0, width = 500.0, height = 20.0) // ceiling bottom at 260.0
        val platforms = listOf(ground, ceiling)

        val player = Player(x = 50.0, y = 284.0, width = 50.0, height = 96.0)
        player.isGrounded = true

        // Jump upward
        player.update(dt = 1.0 / 60.0, moveInput = 0.0, jumpInput = true, platforms = platforms)
        assertTrue(player.vy < 0, "Player should start jumping upward")

        // Step physics to let player hit the ceiling
        for (i in 0 until 10) {
            player.update(dt = 1.0 / 60.0, moveInput = 0.0, jumpInput = false, platforms = platforms)
        }

        // Player's top (y) cannot penetrate ceiling bottom (260.0)
        assertTrue(player.y >= 260.0, "Player head/top must hit ceiling bottom (260.0) and not penetrate it: y=${player.y}")
    }

    @Test
    fun testGameProfileVolumeSettings() {
        val storage = InMemoryGameProfileStorage()
        storage.setMusicVolume(0.6f)
        storage.setSfxVolume(0.4f)
        assertEquals(0.6f, storage.getProfile().musicVolume, 0.001f)
        assertEquals(0.4f, storage.getProfile().sfxVolume, 0.001f)
    }
}
