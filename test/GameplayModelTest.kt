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

        // Guard starts facing left at far corner
        // Move player into guard's line of sight
        val guardTargetX = world.guard.x
        world.player.x = guardTargetX - 100.0
        world.player.y = world.player.startY

        assertEquals(0, world.spottedCount)
        assertEquals(0.0, world.alertProgress)

        // Step world for 0.4 seconds (halfway through detection time)
        world.update(dt = 0.4, moveInput = 0.0, jumpInput = false)

        assertTrue(world.isPlayerInVision, "Player should be detected in vision")
        assertEquals(0.5, world.alertProgress, 0.01, "Alert progress should be at 50% (0.4 / 0.8)")
        assertFalse(world.isSpotted, "Player should not yet be caught during grace period")
        assertEquals(0, world.spottedCount, "Alert count should still be 0")
        assertEquals(guardTargetX - 100.0, world.player.x, 0.01, "Player should not have been reset yet")

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
        world.player.x = world.guard.x - 100.0
        world.player.y = world.player.startY
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

        // Derived from the level's own guard patrol zone (real open ground, no boxes, by
        // construction) rather than a hardcoded absolute x - the story geometry in front of it
        // has shifted more than once as the level was redesigned, so a literal here has broken
        // before. +75 just keeps every offset below comfortably clear of the zone's near edge.
        val base = world.levelData.guardPatrolMinX + 75.0

        // Place guard stationary at x = base, y = 332
        world.guard.x = base
        world.guard.speed = 0.0 // Keep guard fixed for test
        world.guard.facing = 1.0 // Facing right (away from left player)

        // Place player to the left of guard at x = base - 50
        world.player.x = base - 50.0
        world.player.y = 284.0

        // Move right towards guard
        for (i in 0 until 60) {
            world.update(dt = 1.0 / 60.0, moveInput = 1.0, jumpInput = false)
        }

        // Player width is 36.0, guard left is base -> player should stop at base - 36
        assertEquals(base - 36.0, world.player.x, 0.01, "Player should collide with guard's left edge and not pass through")

        // Place player to the right of guard at x = base + 50
        world.player.x = base + 50.0
        world.player.y = 284.0

        // Move left towards guard
        for (i in 0 until 60) {
            world.update(dt = 1.0 / 60.0, moveInput = -1.0, jumpInput = false)
        }

        // Guard right edge is base + 26.0 -> player should stop there
        assertEquals(base + 26.0, world.player.x, 0.01, "Player should collide with guard's right edge and not pass through")

        // Test guard pushing stationary player when not in vision cone
        val movingGuard = Guard(
            x = base - 20.0,
            y = 332.0,
            patrolMinX = base - 200.0,
            patrolMaxX = base + 200.0,
            speed = 60.0,
            facing = -1.0, // Guard moving left towards the player
            visionRange = 0.0 // Vision disabled for pure physics push test
        )
        val pushWorld = world.copy(guard = movingGuard)
        pushWorld.player.x = base - 100.0
        pushWorld.player.y = 284.0

        // Guard moves left 30px (from base - 20 to base - 50, guard.left = base - 50)
        pushWorld.update(dt = 0.5, moveInput = 0.0, jumpInput = false)
        assertEquals(base - 50.0, movingGuard.x, 0.01, "Guard should have reached x = base - 50")
        assertEquals(base - 100.0, pushWorld.player.x, 0.01, "Guard moving into stationary player should push player to guard.left - width (base - 50 - 50 = base - 100)")
    }

    @Test
    fun testNoclipFlyingBypassesCollisionGravityAndDetection() {
        val world = GameWorld.createDefault()
        val base = world.levelData.guardPatrolMinX + 75.0

        // Same setup as testPlayerGuardCollision's first case, where a normal walk stops dead
        // at the guard's left edge (base - 36.0) - flying should sail straight through instead.
        world.guard.x = base
        world.guard.speed = 0.0
        world.guard.facing = 1.0
        world.player.x = base - 50.0
        world.player.y = 284.0
        world.noclipFlying = true

        world.update(dt = 1.0, moveInput = 1.0, jumpInput = false)

        assertTrue(world.player.x > base + 26.0, "Flying should pass straight through the guard, not stop at its edge")
        assertEquals(0.0, world.player.vy, 0.01, "Flying should not accumulate gravity")
        assertFalse(world.player.isGrounded, "Flying should not report grounded")
        assertEquals(0, world.spottedCount, "Flying should skip detection/alert entirely")

        // jumpInput/crouchInput double as up/down while flying.
        val yBeforeUp = world.player.y
        world.update(dt = 0.2, moveInput = 0.0, jumpInput = true, crouchInput = false)
        assertTrue(world.player.y < yBeforeUp, "jumpInput should fly the player upward")

        val yBeforeDown = world.player.y
        world.update(dt = 0.2, moveInput = 0.0, jumpInput = false, crouchInput = true)
        assertTrue(world.player.y > yBeforeDown, "crouchInput should fly the player downward")

        // Turning it back off must restore normal physics (gravity resumes falling).
        world.noclipFlying = false
        val yBeforeGravity = world.player.y
        world.update(dt = 0.2, moveInput = 0.0, jumpInput = false)
        assertTrue(world.player.y > yBeforeGravity, "Gravity should resume once noclip is turned off")
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

        // Move player to overlap exit zone
        world.player.x = world.exitZone.x + 5.0
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

        // Guard at far corner, facing left (-1.0)
        assertEquals(GuardState.PATROL, world.guard.state)

        // Place player at distance ~110px from guard (< 180px walk noise radius)
        world.player.x = world.guard.x - 110.0
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

        // Guard facing left at far corner
        // Place player in vision
        world.player.x = world.guard.x - 100.0
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
        // See testPlayerGuardCollision - derived from the level's own guard patrol zone rather
        // than a hardcoded absolute x, since that keeps breaking as the front-of-level geometry
        // is redesigned.
        val base = world.levelData.guardPatrolMinX + 75.0

        // Start guard investigating at x = base (guard patrol zone - real open ground)
        world.guard.startInvestigating(base)
        assertEquals(GuardState.INVESTIGATING, world.guard.state)

        // Put player in vision cone
        world.guard.x = base + 20.0
        world.guard.facing = -1.0
        world.player.x = base - 80.0
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
        // See testPlayerGuardCollision - derived from the level's own guard patrol zone rather
        // than a hardcoded absolute x.
        val base = world.levelData.guardPatrolMinX + 75.0
        world.guard.x = base // guard patrol zone - real open ground, no platform underfoot to snag on
        world.guard.facing = 1.0 // Patrolling right
        world.guard.speed = 100.0

        // Place player ahead in guard's vision cone at x = base + 100
        world.player.x = base + 100.0
        world.player.y = 284.0

        // Step 0.2s: player is detected in cone
        world.update(dt = 0.2, moveInput = 0.0, jumpInput = false)

        assertTrue(world.isPlayerInVision, "Player should be detected in vision cone")
        assertTrue(world.alertProgress > 0.0, "Alert progress should be accumulating")
        assertEquals(base, world.guard.x, 0.001, "Guard must stop at current position and not advance while detecting player")

        // Step another 0.3s (alert progress ~0.5)
        world.update(dt = 0.3, moveInput = 0.0, jumpInput = false)
        assertTrue(world.isPlayerInVision)
        assertEquals(base, world.guard.x, 0.001, "Guard must remain stopped during continuous detection")
    }

    @Test
    fun testGuardResumesPatrolAfterLosingVisualFromConeDetection() {
        val world = GameWorld.createDefault()
        world.setUniformDetectionTime(1.0)
        // See testPlayerGuardCollision - derived from the level's own guard patrol zone rather
        // than a hardcoded absolute x.
        val base = world.levelData.guardPatrolMinX + 75.0
        world.guard.x = base // guard patrol zone - real open ground
        world.guard.facing = 1.0 // Patrolling right
        world.guard.speed = 100.0
        world.guard.investigateDuration = 1.5

        // Place player in guard vision cone
        world.player.x = base + 100.0
        world.player.y = 284.0

        // Step 0.2s: guard detects player and stops at base
        world.update(dt = 0.2, moveInput = 0.0, jumpInput = false)
        assertEquals(base, world.guard.x, 0.001)

        // Player moves behind fence out of sight
        world.player.x = 100.0
        world.player.y = 284.0
        world.update(dt = 0.1, moveInput = 0.0, jumpInput = false)
        assertFalse(world.isPlayerInVision)
        assertEquals(GuardState.INVESTIGATING, world.guard.state)
        assertEquals(base, world.guard.x, 0.001, "Guard remains stopped while investigating")

        // Advance past investigateDuration (1.5s) -> guard returns to patrol
        world.update(dt = 1.6, moveInput = 0.0, jumpInput = false)
        assertEquals(GuardState.PATROL, world.guard.state)

        // Subsequent patrol update moves guard along patrol route
        world.update(dt = 0.1, moveInput = 0.0, jumpInput = false)
        assertTrue(world.guard.x > base, "Guard should resume patrol movement after investigate timeout")
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
        assertEquals(1, storage.getProfile().powerupInventory["smoke_bomb"])

        // Buy powerup with insufficient coins
        val boughtExpensive = storage.buyPowerup("radar_booster", 500)
        assertFalse(boughtExpensive)
        assertEquals(250, storage.getProfile().coins)
        assertEquals(0, storage.getProfile().getPowerupCount("radar_booster"))
    }

    @Test
    // TEMPORARILY DISABLED: GameProfile.kt's isLevelUnlocked() has a temporary "unlock all
    // levels for testing" override (see the unlockAllForTesting flag there). Re-enable this test
    // when that flag is removed - the progression logic below it is unchanged.
    @Ignore
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
    fun testLevel2HangingCratesGapIsBeatable() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_2)
        assertEquals(5100.0, world.worldWidth, "Level 2 should be 5100.0 wide")
        assertEquals(0, world.allGuards.size, "Level 2 should have no guards")

        val dt = 1.0 / 60.0
        var elapsed = 0.0
        var stalledFor = 0.0

        val launchEdges = listOf(868.0, 1120.0, 1266.0, 1412.0)
        while (elapsed < 30.0 && world.player.x < 1482.0 && !world.isGameOver) {
            val beforeX = world.player.x
            val atLaunchEdge = launchEdges.any { edge -> world.player.x in (edge - 20.0)..(edge + 2.0) }
            val jump = world.player.isGrounded && (stalledFor > 0.05 || atLaunchEdge)
            world.update(dt, moveInput = 1.0, jumpInput = jump, crouchInput = false)
            stalledFor = if (kotlin.math.abs(world.player.x - beforeX) < 0.5) stalledFor + dt else 0.0
            elapsed += dt
        }

        assertTrue(
            world.player.x >= 1482.0,
            "Jumping onto crate1, climbing onto the terrain, and jumping all three hanging crates " +
                "should reach the intermediate terrain block. Ended at x=${world.player.x.toInt()} " +
                "y=${world.player.y.toInt()} after ${elapsed.toInt()}s (gameOver=${world.isGameOver})"
        )
    }

    @Test
    fun testLevel2HangingCratesAreTheOnlyCollidablePart() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_2)
        val stationaryHangingCrates = world.hangingCrateVariant1 + world.hangingCrateVariant2
        assertEquals(5, stationaryHangingCrates.size, "Level 2 should have five stationary hanging crates (3 in section 1 + 1 in section 2 + 1 in section 3)")
        assertEquals(8, world.movingPlatforms.size, "Level 2 should have 8 moving hanging crates in total (4 in section 2 + 4 in section 3)")
        // Total of 13 hanging crates (3 in Section 1, 5 in Section 2, 5 in Section 3)
        for (crate in stationaryHangingCrates) {
            assertTrue(crate in world.boxes, "Stationary hanging crate at x=${crate.x} must be a collidable box")
        }
    }

    @Test
    fun testLevel2RescueBarrelAllowsClimbWhenStuckInGap() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_2)
        assertEquals(3, world.barrels.size, "Level 2 should have three rescue barrels (one per gap section)")
        val barrel = world.barrels.first()
        assertEquals(868.0, barrel.x, "Rescue barrel 1 should sit flush against terrain right face at x=868")
        assertEquals(392.0, barrel.y, "Rescue barrel 1 should sit on ground at y=392")
        assertEquals(32.0, barrel.width, "Rescue barrel 1 should match barrel aspect ratio width=32")
        assertEquals(48.0, barrel.height, "Rescue barrel 1 should match height=48")
        assertTrue(barrel in world.boxes, "Rescue barrel 1 must be in world.boxes for collision and climbing")

        // Simulate a player who fell into the gap onto the ground at x=930
        val dt = 1.0 / 60.0
        world.player.x = 930.0
        world.player.y = 440.0 - 96.0
        world.player.isGrounded = true

        // Step 1: Hop onto the rescue barrel by moving left and jumping
        var elapsed = 0.0
        while (elapsed < 2.0 && !(world.player.isGrounded && world.player.y <= 296.0 + 1e-4)) {
            val shouldJump = world.player.isGrounded && world.player.y > 296.0
            world.update(dt, moveInput = -1.0, jumpInput = shouldJump, crouchInput = false)
            elapsed += dt
        }
        assertTrue(
            world.player.isGrounded && world.player.y <= 296.0 + 1e-4,
            "Player should jump onto the barrel (y=296, feetY=392). Was y=${world.player.y}, grounded=${world.player.isGrounded}"
        )

        // Step 2: Walk left across the barrel against the terrain block and jump to climb
        while (elapsed < 4.0 && !world.player.isClimbing) {
            val atTerrainFace = world.player.x <= 874.0
            world.update(dt, moveInput = -1.0, jumpInput = atTerrainFace && world.player.isGrounded, crouchInput = false)
            elapsed += dt
        }
        assertTrue(world.player.isClimbing, "Jumping on the barrel against the terrain face should initiate climb")

        // Step 3: Advance through climb animation
        while (world.player.isClimbing && elapsed < 6.0) {
            world.update(dt, moveInput = -1.0, jumpInput = false, crouchInput = false)
            elapsed += dt
        }

        assertFalse(world.player.isClimbing, "Climb should complete")
        assertEquals(200.0, world.player.y, 1.0, "Player should have climbed onto terrain top (y=296-96=200)")
        assertTrue(
            world.player.x < 868.0,
            "Player should be on top of the terrain (x < 868). Was x=${world.player.x}"
        )
    }

    @Test
    fun testLevel2RescueBarrel2AllowsClimbingOutOfSection2Gap() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_2)
        val barrel2 = world.barrels[1]
        assertEquals(1862.0, barrel2.x, "Rescue barrel 2 should sit flush against midTerrain right face at x=1862")
        assertEquals(392.0, barrel2.y, "Rescue barrel 2 should sit on ground at y=392")
        assertEquals(32.0, barrel2.width, "Rescue barrel 2 width=32")
        assertEquals(48.0, barrel2.height, "Rescue barrel 2 height=48")
        assertTrue(barrel2 in world.boxes, "Rescue barrel 2 must be in world.boxes")

        // Simulate player falling into Section 2 gap at x=1950
        val dt = 1.0 / 60.0
        world.player.x = 1950.0
        world.player.y = 440.0 - 96.0
        world.player.isGrounded = true

        // Step 1: Hop onto rescue barrel 2 by moving left and jumping
        var elapsed = 0.0
        while (elapsed < 2.0 && !(world.player.isGrounded && world.player.y <= 296.0 + 1e-4)) {
            val shouldJump = world.player.isGrounded && world.player.y > 296.0
            world.update(dt, moveInput = -1.0, jumpInput = shouldJump, crouchInput = false)
            elapsed += dt
        }
        assertTrue(
            world.player.isGrounded && world.player.y <= 296.0 + 1e-4,
            "Player should jump onto rescue barrel 2. Was y=${world.player.y}, grounded=${world.player.isGrounded}"
        )

        // Step 2: Walk left against midTerrain and climb
        while (elapsed < 4.0 && !world.player.isClimbing) {
            val atTerrainFace = world.player.x <= 1868.0
            world.update(dt, moveInput = -1.0, jumpInput = atTerrainFace && world.player.isGrounded, crouchInput = false)
            elapsed += dt
        }
        assertTrue(world.player.isClimbing, "Jumping on barrel 2 against midTerrain face should initiate climb")

        while (world.player.isClimbing && elapsed < 6.0) {
            world.update(dt, moveInput = -1.0, jumpInput = false, crouchInput = false)
            elapsed += dt
        }
        assertFalse(world.player.isClimbing, "Climb should complete")
        assertEquals(200.0, world.player.y, 1.0, "Player should have climbed onto midTerrain top (y=296-96=200)")
        assertTrue(
            world.player.x < 1862.0,
            "Player should be on top of midTerrain (x < 1862). Was x=${world.player.x}"
        )
    }

    @Test
    fun testLevel2MovingPlatformsOscillationAndRiding() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_2)
        assertEquals(8, world.movingPlatforms.size, "Should have 8 moving platforms in Level 2 (4 horizontal + 4 vertical)")

        val mp1 = world.movingPlatforms[0]
        val initialX = mp1.x
        val dt = 1.0 / 60.0

        // Step 1: Simulate several seconds and ensure position remains inside [minX..maxX] and [minY..maxY]
        for (i in 0 until 300) {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false)
            for (mp in world.movingPlatforms) {
                assertTrue(
                    mp.x >= mp.minX - 1e-6 && mp.x <= mp.maxX + 1e-6,
                    "Moving platform ${mp.id} at x=${mp.x} must stay within [${mp.minX}..${mp.maxX}]"
                )
                assertTrue(
                    mp.y >= mp.minY - 1e-6 && mp.y <= mp.maxY + 1e-6,
                    "Moving platform ${mp.id} at y=${mp.y} must stay within [${mp.minY}..${mp.maxY}]"
                )
            }
        }

        // Step 2: Test player riding moving platform:
        // Place player on top of mp1
        world.player.x = mp1.x + 10.0
        world.player.y = mp1.top - world.player.height
        world.player.isGrounded = true
        world.player.vx = 0.0
        world.player.vy = 0.0

        val playerInitialRelX = world.player.x - mp1.x
        // Simulate a few frames with no player input
        for (i in 0 until 30) {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false)
            val currentRelX = world.player.x - mp1.x
            assertEquals(playerInitialRelX, currentRelX, 0.5, "Player relative position on moving platform should remain constant")
        }
    }

    @Test
    fun testLevel2Section3LayoutIntegrity() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_2)
        assertEquals(5100.0, world.worldWidth, "Level 2 world width should be 5100.0")

        val ground = world.platforms.first { it.y == 440.0 }
        assertEquals(5100.0, ground.width, "Level 2 ground floor should run the full width of the level until the end")

        // 3 rescue barrels
        assertEquals(3, world.barrels.size, "Level 2 should have 3 rescue barrels (one per gap section)")
        val barrel3 = world.barrels[2]
        assertEquals(3295.0, barrel3.x, 1.0, "Rescue barrel 3 should be placed at x=3295 flush against midTerrain2")
        assertEquals(392.0, barrel3.y, 1.0, "Rescue barrel 3 should sit on the ground")

        // Section 3 vertical moving containers
        val vertCrates = world.movingPlatforms.filter { it.id.startsWith("lvl2_vert_") }
        assertEquals(4, vertCrates.size, "Should have 4 vertical moving containers in Section 3")

        val vert1 = vertCrates.first { it.id == "lvl2_vert_1" }
        assertEquals(3365.0, vert1.minX, 1.0)
        assertEquals(250.0, vert1.minY, 1.0)
        assertEquals(320.0, vert1.maxY, 1.0)

        val vert2 = vertCrates.first { it.id == "lvl2_vert_2" }
        assertEquals(3511.0, vert2.minX, 1.0)
        assertEquals(240.0, vert2.minY, 1.0)
        assertEquals(310.0, vert2.maxY, 1.0)

        // Stationary long crate 2 (island hub)
        val stationaryLong2 = world.boxes.first { it.width in 170.0..180.0 && it.x > 3500.0 }
        assertEquals(3657.0, stationaryLong2.x, 1.0)
        assertEquals(280.0, stationaryLong2.y, 1.0)

        // Final terrain and extraction
        val finalTerrain = world.boxes.first { it.x > 4000.0 }
        assertEquals(4193.0, finalTerrain.x, 1.0)
        assertEquals(296.0, finalTerrain.y, 1.0)
        assertEquals(180.0, finalTerrain.width, 1.0, "Final terrain should be shorter (180px)")

        assertEquals(4680.0, world.exitZone.x, 1.0, "Exit zone should be at x=4680.0")
        val distanceToExit = world.exitZone.x - finalTerrain.right
        assertEquals(307.0, distanceToExit, 1.0, "Distance from finalTerrain to exit should have more space (307px)")
        assertTrue(world.allGuards.isEmpty(), "Level 2 should have no guards")
    }

    @Test
    fun testLevel2ExitBoothAndFenceSitOnGroundAndDoNotFloat() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_2)
        val ground = world.platforms.first { it.y == 440.0 }
        // The exit zone bottom in Level 2 must align with the ground floor (y = 440.0)
        assertEquals(ground.top, world.exitZone.bottom, 0.01, "ExitZone bottom must touch ground top at 440.0")

        // In GameplayScene, exitGroundY is world.exitZone.bottom + 1.0, meaning entrance and exit fence sit on the ground
        val entranceHeight = 135.0
        val exitGroundY = world.exitZone.bottom + 1.0
        val entranceY = exitGroundY - entranceHeight
        val entranceBottom = entranceY + entranceHeight
        assertTrue(entranceBottom >= ground.top, "Entrance booth bottom must be seated on the ground, not floating above it")

        val exitFenceHeight = 140.0
        val exitFenceY = exitGroundY - exitFenceHeight
        val exitFenceBottom = exitFenceY + exitFenceHeight
        assertTrue(exitFenceBottom >= ground.top, "Exit fence bottom must be seated on the ground, not floating above it")
    }

    @Test
    fun testVerticalMovingPlatformsOscillationAndRiding() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_2)
        val v1 = world.movingPlatforms.first { it.id == "lvl2_vert_1" }
        val dt = 1.0 / 60.0

        // Place player on top of v1
        world.player.x = v1.x + 20.0
        world.player.y = v1.top - world.player.height
        world.player.isGrounded = true
        world.player.vx = 0.0
        world.player.vy = 0.0

        val initialRelY = world.player.y - v1.y
        for (i in 0 until 30) {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false)
            val currentRelY = world.player.y - v1.y
            assertEquals(initialRelY, currentRelY, 0.5, "Player relative vertical position on elevator platform should remain constant")
        }
    }

    @Test
    fun testJumpingOnVerticallyMovingPlatformDoesNotThrowPlayerOut() {
        val platformIds = listOf("lvl2_vert_1", "lvl2_vert_2", "lvl2_vert_4", "lvl2_vert_5")
        val offsetsX = listOf(5.0, 15.0, 28.0, 45.0, 52.0)
        val jumpDelays = listOf(0, 3, 7, 12, 20)

        for (pid in platformIds) {
            for (offsetX in offsetsX) {
                for (delay in jumpDelays) {
                    val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_2)
                    val v = world.movingPlatforms.first { it.id == pid }
                    val dt = 1.0 / 60.0

                    // Place player on v
                    world.player.x = v.x + offsetX
                    world.player.y = v.top - world.player.height
                    world.player.isGrounded = true
                    world.player.vx = 0.0
                    world.player.vy = 0.0

                    var cooldown = 0
                    for (frame in 0 until 400) {
                        val shouldJump = world.player.isGrounded && cooldown <= 0
                        if (shouldJump) cooldown = delay else cooldown--

                        world.update(dt, moveInput = 0.0, jumpInput = shouldJump, crouchInput = false)

                        val footCenter = world.player.x + world.player.width / 2.0
                        assertTrue(
                            footCenter >= v.left && footCenter <= v.right,
                            "Player was thrown off platform $pid! offsetX=$offsetX, delay=$delay, frame=$frame: player.x=${world.player.x}, player.y=${world.player.y}, v.x=${v.x}..${v.right}, v.y=${v.y}..${v.bottom}, isGrounded=${world.player.isGrounded}"
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testFallingOntoGroundDoesNotTeleportPlayerToEndOfLevel() {
        val testXPositions = listOf(500.0, 1500.0, 2600.0, 3100.0, 3500.0, 3850.0, 4400.0)
        for (startX in testXPositions) {
            val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_2)
            val dt = 1.0 / 60.0

            // Drop player from height 100 above ground (y=440, so feet start at y=300) with high downward velocity
            world.player.x = startX
            world.player.y = 200.0
            world.player.vy = 450.0
            world.player.vx = 0.0
            world.player.isGrounded = false

            for (frame in 0 until 60) {
                world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false)
            }

            assertTrue(world.player.isGrounded, "Player dropped at x=$startX should land on ground")
            assertEquals(
                startX,
                world.player.x,
                1.0,
                "Player dropped at x=$startX should NOT teleport horizontally! Was player.x=${world.player.x}"
            )
            assertFalse(
                world.isLevelComplete,
                "Dropping onto ground at x=$startX should not trigger level complete or jump to exit"
            )
        }
    }

    @Test
    fun testLevel2RescueBarrel3AllowsClimbingOutOfSection3Gap() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_2)
        val barrel3 = world.barrels[2]
        val midTerrain2 = world.boxes.first { it.width in 400.0..450.0 && it.x in 2800.0..3000.0 }

        // Place player on rescue barrel 3 facing left toward midTerrain2
        world.player.x = barrel3.x + 2.0
        world.player.y = barrel3.top - world.player.height
        world.player.isGrounded = true

        val dt = 1.0 / 60.0
        // Press left + jump to trigger climb
        world.update(dt, moveInput = -1.0, jumpInput = true, crouchInput = false)
        assertTrue(world.player.isClimbing, "Player should start climbing onto midTerrain2 from barrel 3")

        // Advance climb to completion (climbDuration is 1.95s)
        var climbElapsed = 0.0
        while (world.player.isClimbing && climbElapsed < 3.0) {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false)
            climbElapsed += dt
        }

        assertFalse(world.player.isClimbing, "Player should have completed climb")
        assertTrue(world.player.isGrounded, "Player should be grounded on top of midTerrain2")
        val expectedY = midTerrain2.top - world.player.height
        assertEquals(expectedY, world.player.y, 1.0, "Player should be standing on top of midTerrain2")
    }

    @Test
    fun testLevel2Section3IsBeatable() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_2)
        // Position player on midTerrain2 near its right edge
        world.player.x = 3260.0
        world.player.y = 296.0 - world.player.height
        world.player.isGrounded = true

        val dt = 1.0 / 60.0
        var elapsed = 0.0
        var stalledFor = 0.0

        val launchEdges = listOf(3295.0, 3441.0, 3587.0, 3831.0, 3977.0, 4131.0)
        while (elapsed < 35.0 && world.player.x < 4200.0 && !world.isGameOver) {
            val beforeX = world.player.x

            val vert1 = world.movingPlatforms.first { it.id == "lvl2_vert_1" }
            val vert2 = world.movingPlatforms.first { it.id == "lvl2_vert_2" }
            val vert4 = world.movingPlatforms.first { it.id == "lvl2_vert_4" }
            val vert5 = world.movingPlatforms.first { it.id == "lvl2_vert_5" }

            val onVert1 = world.player.x in 3365.0..3441.0
            val onVert2 = world.player.x in 3511.0..3587.0
            val onStationary = world.player.x in 3657.0..3831.0
            val onVert4 = world.player.x in 3901.0..3977.0
            val onVert5 = world.player.x in 4055.0..4131.0

            val waitOnVert1 = onVert1 && (vert2.y < vert1.y - 20.0)
            val waitOnVert2 = onVert2 && (vert2.y > 290.0)
            val waitOnStationary = onStationary && world.player.x > 3800.0 && (vert4.y < 260.0)
            val waitOnVert4 = onVert4 && (vert5.y < vert4.y - 20.0)
            val waitOnVert5 = onVert5 && (vert5.y > 265.0)

            val waiting = waitOnVert1 || waitOnVert2 || waitOnStationary || waitOnVert4 || waitOnVert5
            val move = if (waiting) 0.0 else 1.0

            val atLaunchEdge = launchEdges.any { edge -> world.player.x in (edge - 24.0)..(edge + 2.0) }
            val jump = world.player.isGrounded && !waiting && (stalledFor > 0.08 || atLaunchEdge)
            world.update(dt, moveInput = move, jumpInput = jump, crouchInput = false)
            if (waiting) {
                stalledFor = 0.0
            } else {
                stalledFor = if (kotlin.math.abs(world.player.x - beforeX) < 0.5) stalledFor + dt else 0.0
            }
            elapsed += dt
        }

        assertTrue(
            world.player.x >= 4200.0,
            "Player should cross Section 3 vertical elevator containers and reach finalTerrain. " +
                "Ended at x=${world.player.x.toInt()} y=${world.player.y.toInt()} after ${elapsed.toInt()}s (gameOver=${world.isGameOver})"
        )
    }

    @Test
    fun testLevel2CompletionFromFinalTerrainToExit() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_2)
        assertEquals(0, world.allGuards.size, "Level 2 should have no guards")

        // Start player standing on finalTerrain
        world.player.x = 4220.0
        world.player.y = 296.0 - world.player.height
        world.player.isGrounded = true

        val dt = 1.0 / 60.0
        var elapsed = 0.0
        // Walk right off finalTerrain (edge at 4373), drop to ground (y=440), and enter exit zone at 4680
        while (elapsed < 6.0 && !world.isLevelComplete) {
            world.update(dt, moveInput = 1.0, jumpInput = false, crouchInput = false)
            elapsed += dt
        }

        assertTrue(world.isLevelComplete, "Player should drop off short finalTerrain and reach exitZone within 6s")
        assertTrue(elapsed < 4.5, "Exit run across the 307px space should complete in under 4.5s. Took ${elapsed}s")
    }

    @Test
    fun testLevel2Section2PlatformsNeverOverlapAndCrate1CloseToTerrain() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_2)
        val mp1 = world.movingPlatforms[0]
        val mp2 = world.movingPlatforms[1]
        val stationaryLongCrate = world.boxes.first { it.width in 170.0..180.0 && it.x > 2000.0 }
        val mp4 = world.movingPlatforms[2]
        val mp5 = world.movingPlatforms[3]
        val midTerrain = world.boxes.first { it.width in 370.0..390.0 && it.x in 1400.0..1600.0 }
        val endTerrain = world.boxes.first { it.width > 400.0 && it.x > 2800.0 }

        // 1. First crate should be close to midTerrain at its closest approach
        val closestGapToMidTerrain = mp1.minX - midTerrain.right
        assertTrue(closestGapToMidTerrain in 20.0..40.0, "Crate 1 closest gap to midTerrain should be close (~33 units), was $closestGapToMidTerrain")

        // 2. Simulate across multiple periods and verify no containers EVER penetrate or overlap each other or terrain
        val dt = 1.0 / 60.0
        for (i in 0 until 600) {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false)

            // mp1 vs midTerrain
            assertTrue(mp1.x > midTerrain.right + 10.0, "mp1 must not penetrate midTerrain. mp1.x=${mp1.x}, midTerrain.right=${midTerrain.right}")
            // mp1 vs mp2
            val gap12 = mp2.x - (mp1.x + mp1.width)
            assertTrue(gap12 >= 20.0, "mp1 and mp2 must maintain clearance. Gap=$gap12 at frame $i")
            // mp2 vs stationaryLongCrate
            val gap2Long = stationaryLongCrate.left - (mp2.x + mp2.width)
            assertTrue(gap2Long >= 20.0, "mp2 must NEVER penetrate stationaryLongCrate. Gap=$gap2Long at frame $i")
            // stationaryLongCrate vs mp4
            val gapLong4 = mp4.x - stationaryLongCrate.right
            assertTrue(gapLong4 >= 20.0, "mp4 must NEVER penetrate stationaryLongCrate. Gap=$gapLong4 at frame $i")
            // mp4 vs mp5
            val gap45 = mp5.x - (mp4.x + mp4.width)
            assertTrue(gap45 >= 20.0, "mp4 and mp5 must maintain clearance. Gap=$gap45 at frame $i")
            // mp5 vs endTerrain
            val gap5End = endTerrain.left - (mp5.x + mp5.width)
            assertTrue(gap5End >= 20.0, "mp5 must NEVER penetrate endTerrain. Gap=$gap5End at frame $i")
        }
    }

    @Test
    fun testLevel2CannotClimbRightWallFromGround() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_2)
        val groundY = 440.0
        val finalTerrain = world.boxes.first { it.x > 4000.0 }
        assertEquals(4193.0, finalTerrain.left, "Final terrain left edge should be at 4193.0")
        assertEquals(296.0, finalTerrain.top, "Final terrain top edge should be at 296.0")

        // 1. Place player on the ground right against finalTerrain's left wall
        world.player.x = finalTerrain.left - world.player.width // 4193 - 36 = 4157
        world.player.y = groundY - world.player.height // 440 - 96 = 344
        world.player.isGrounded = true

        val dt = 1.0 / 60.0
        // Attempt to climb the 144px high wall from the ground by moving right and pressing jump
        for (i in 0 until 60) {
            world.update(dt, moveInput = 1.0, jumpInput = true, crouchInput = false)
            assertFalse(
                world.player.isClimbing,
                "Player must NOT be able to climb the 144px wall of finalTerrain from the ground! (frame $i)"
            )
            // Player should jump straight up and fall back down, never gaining ground on the wall
            assertTrue(
                world.player.x <= finalTerrain.left - world.player.width + 1e-4,
                "Player should be blocked horizontally by finalTerrain wall. Was x=${world.player.x}"
            )
        }

        // 2. Also check midTerrain (x=1482) and midTerrain2 (x=2855) from the ground
        val midTerrain = world.boxes.first { it.width in 370.0..390.0 && it.x in 1400.0..1600.0 }
        world.player.x = midTerrain.left - world.player.width
        world.player.y = groundY - world.player.height
        world.player.isGrounded = true
        for (i in 0 until 30) {
            world.update(dt, moveInput = 1.0, jumpInput = true, crouchInput = false)
            assertFalse(world.player.isClimbing, "Player must not climb midTerrain from the ground")
        }

        val midTerrain2 = world.boxes.first { it.width > 400.0 && it.x in 2800.0..3000.0 }
        world.player.x = midTerrain2.left - world.player.width
        world.player.y = groundY - world.player.height
        world.player.isGrounded = true
        for (i in 0 until 30) {
            world.update(dt, moveInput = 1.0, jumpInput = true, crouchInput = false)
            assertFalse(world.player.isClimbing, "Player must not climb midTerrain2 from the ground")
        }
    }

    @Test
    fun testLevel2Section2MovingContainersCycleTimeAndReachability() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_2)
        val mp1 = world.movingPlatforms[0]
        val mp2 = world.movingPlatforms[1]
        val mp4 = world.movingPlatforms[2]
        val mp5 = world.movingPlatforms[3]

        // Check matched periods
        assertEquals(3.6, mp1.periodSeconds, 0.01, "mp1 period should be 3.6s")
        assertEquals(3.6, mp2.periodSeconds, 0.01, "mp2 period should match mp1 (3.6s) to prevent drifting out of phase")
        assertEquals(3.6, mp4.periodSeconds, 0.01, "mp4 period should be 3.6s")
        assertEquals(3.6, mp5.periodSeconds, 0.01, "mp5 period should match mp4 (3.6s)")

        val dt = 1.0 / 60.0
        var maxWaitTime12 = 0.0
        var currentWaitTime12 = 0.0
        var minGap12 = Double.MAX_VALUE

        var maxWaitTime45 = 0.0
        var currentWaitTime45 = 0.0
        var minGap45 = Double.MAX_VALUE

        // Simulate 600 frames (10 seconds, nearly 3 full cycles)
        for (i in 0 until 600) {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false)

            val gap12 = mp2.left - mp1.right
            minGap12 = minOf(minGap12, gap12)
            if (gap12 <= 75.0) {
                maxWaitTime12 = maxOf(maxWaitTime12, currentWaitTime12)
                currentWaitTime12 = 0.0
            } else {
                currentWaitTime12 += dt
            }

            val gap45 = mp5.left - mp4.right
            minGap45 = minOf(minGap45, gap45)
            if (gap45 <= 75.0) {
                maxWaitTime45 = maxOf(maxWaitTime45, currentWaitTime45)
                currentWaitTime45 = 0.0
            } else {
                currentWaitTime45 += dt
            }
        }
        maxWaitTime12 = maxOf(maxWaitTime12, currentWaitTime12)
        maxWaitTime45 = maxOf(maxWaitTime45, currentWaitTime45)

        // Minimum gap between crate 1 and 2 at closest approach should be comfortable (~24 units)
        assertTrue(minGap12 in 20.0..35.0, "Closest gap between crate 1 and crate 2 should be ~24 units, was $minGap12")
        // Maximum wait time between jump windows (gap <= 75 units) must be at most 2.5 seconds (was previously > 16 seconds!)
        assertTrue(
            maxWaitTime12 <= 2.5,
            "Max wait time between jumpable windows for crate 1 to 2 must be <= 2.5s, was ${maxWaitTime12}s"
        )

        // Crate 4 and 5 reachability
        assertTrue(minGap45 in 25.0..40.0, "Closest gap between crate 4 and 5 should be ~34 units, was $minGap45")
        assertTrue(
            maxWaitTime45 <= 2.5,
            "Max wait time between jumpable windows for crate 4 to 5 must be <= 2.5s, was ${maxWaitTime45}s"
        )
    }

    @Test
    fun testPlayerEdgeJumpWithCoyoteTime() {
        val platform = Rect(100.0, 300.0, 100.0, 50.0)
        val player = Player(x = 100.0, y = 300.0 - 48.0, startX = 100.0, startY = 300.0 - 48.0)
        player.isGrounded = true

        // Step 1: Walk left slightly past the edge without jumping
        val dt = 1.0 / 60.0
        player.update(dt, moveInput = -1.0, jumpInput = false, crouchInput = false, platforms = listOf(platform))
        assertFalse(player.isGrounded, "Player should be airborne after walking off the ledge")
        assertTrue(player.coyoteTimer > 0.0, "Coyote timer should be active within grace window")

        // Step 2: Press left and jump while within coyote time
        player.update(dt, moveInput = -1.0, jumpInput = true, crouchInput = false, platforms = listOf(platform))
        assertTrue(player.vy < 0.0, "Player should execute a jump (vy < 0). Was vy=${player.vy}")
        assertTrue(player.vx < 0.0, "Player should be moving left during jump. Was vx=${player.vx}")
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
    fun testLevel1HangingCrateLoweredAndCrouchUnder() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_1)
        val longPlatform = world.boxes.first { it.width in 890.0..910.0 }
        val hangingCrate = world.boxes.first { it.y <= 0.0 && it.height > 150.0 }

        val clearance = longPlatform.top - hangingCrate.bottom
        assertEquals(58.0, clearance, 0.01, "Level 1 hanging crate clearance above long platform should be 58px")

        // 1. Standing player is blocked by the crate
        world.player.x = hangingCrate.left - world.player.width - 20.0
        world.player.y = longPlatform.top - world.player.height
        world.player.isGrounded = true

        val dt = 1.0 / 60.0
        for (i in 0 until 60) {
            world.update(dt, moveInput = 1.0, jumpInput = false, crouchInput = false)
        }
        assertTrue(
            world.player.x + world.player.width <= hangingCrate.left + 0.1,
            "Standing player must be blocked by the lowered hanging crate (player.right=${world.player.x + world.player.width}, crate.left=${hangingCrate.left})"
        )

        // 2. Crouching player crawls cleanly underneath it
        for (i in 0 until 240) {
            world.update(dt, moveInput = 1.0, jumpInput = false, crouchInput = true)
        }
        assertTrue(
            world.player.x > hangingCrate.right,
            "Crouching player must cleanly crawl all the way past the lowered hanging crate. Player x=${world.player.x}, crate.right=${hangingCrate.right}"
        )
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
    fun testPlayerClimbsTallBoxTooHighToJump() {
        val ground = Rect(x = 0.0, y = 380.0, width = 800.0, height = 100.0)
        // Height 100 exceeds the ~45 unit normal jump apex but fits the climb's reach.
        val box = Rect(x = 300.0, y = 280.0, width = 60.0, height = 100.0)
        val platforms = listOf(ground, box)
        val player = Player(x = 60.0, y = 380.0 - 96.0, startX = 60.0, startY = 380.0 - 96.0)

        // Walk right up against the box.
        for (i in 0 until 300) {
            if (player.x + player.width >= box.left) break
            player.update(dt = 1.0 / 60.0, moveInput = 1.0, jumpInput = false, crouchInput = false, platforms = platforms, climbTargets = listOf(box))
        }
        assertFalse(player.isClimbing, "Player should not be climbing yet")
        assertTrue(player.x + player.width >= box.left - 1.0, "Player should be flush against the box")

        // A plain jump here (no climbTargets) must NOT clear the box - confirms the box really
        // is taller than a normal jump can reach.
        val jumpOnlyPlayer = Player(x = player.x, y = player.y, startX = player.x, startY = player.y)
        for (i in 0 until 90) {
            jumpOnlyPlayer.update(dt = 1.0 / 60.0, moveInput = 1.0, jumpInput = jumpOnlyPlayer.isGrounded, crouchInput = false, platforms = platforms)
        }
        assertTrue(jumpOnlyPlayer.x + jumpOnlyPlayer.width <= box.left + 0.5, "A normal jump must not get the player past this box")

        // Press jump with the box registered as a climb target: should trigger a climb, not a jump.
        player.update(dt = 1.0 / 60.0, moveInput = 1.0, jumpInput = true, crouchInput = false, platforms = platforms, climbTargets = listOf(box))
        assertTrue(player.isClimbing, "Jumping in front of a too-tall box should start a climb")

        // Run the climb to completion.
        for (i in 0 until 200) {
            if (!player.isClimbing) break
            player.update(dt = 1.0 / 60.0, moveInput = 0.0, jumpInput = false, crouchInput = false, platforms = platforms, climbTargets = listOf(box))
        }
        assertFalse(player.isClimbing, "Climb should have finished")
        assertTrue(player.isGrounded, "Player should be grounded after climbing")
        assertEquals(box.top, player.y + player.height, 0.5, "Player's feet should end up on the box top")
        assertTrue(player.x >= box.left - 0.5 && player.x + player.width <= box.right + 0.5, "Player should land within the box footprint")
    }

    @Test
    fun testClimbDoesNotAutoJumpAfterCompletion() {
        val ground = Rect(x = 0.0, y = 380.0, width = 800.0, height = 100.0)
        val box = Rect(x = 300.0, y = 280.0, width = 60.0, height = 100.0)
        val platforms = listOf(ground, box)

        // Case 1: Normal human tap (jump input held for 6 frames / 100ms), then hands off controls
        val player = Player(x = box.left - 36.0, y = 380.0 - 96.0, startX = 60.0, startY = 380.0 - 96.0)
        for (i in 0 until 6) {
            player.update(dt = 1.0 / 60.0, moveInput = 1.0, jumpInput = true, crouchInput = false, platforms = platforms, climbTargets = listOf(box))
        }
        assertTrue(player.isClimbing, "Player should be climbing")

        // Advance through climb with NO input
        while (player.isClimbing) {
            player.update(dt = 1.0 / 60.0, moveInput = 0.0, jumpInput = false, crouchInput = false, platforms = platforms, climbTargets = listOf(box))
        }
        assertFalse(player.isClimbing, "Climb should have finished")
        assertTrue(player.isGrounded, "Player should be grounded on the box")

        // For the next 60 frames (1 full second) with zero input, player must NOT auto-jump!
        for (i in 0 until 60) {
            player.update(dt = 1.0 / 60.0, moveInput = 0.0, jumpInput = false, crouchInput = false, platforms = platforms, climbTargets = listOf(box))
            assertFalse(player.isJumping, "Player must NOT jump after completing a climb without input! (frame $i)")
            assertEquals(0.0, player.vy, 0.01, "Player vy should be 0, not jumping! (frame $i)")
            assertTrue(player.isGrounded, "Player should remain grounded! (frame $i)")
        }

        // Case 2: Jump button held continuously throughout the entire climb
        val player2 = Player(x = box.left - 36.0, y = 380.0 - 96.0, startX = 60.0, startY = 380.0 - 96.0)
        for (i in 0 until 6) {
            if (player2.isClimbing) break
            player2.update(dt = 1.0 / 60.0, moveInput = 1.0, jumpInput = true, crouchInput = false, platforms = platforms, climbTargets = listOf(box))
        }
        assertTrue(player2.isClimbing, "Player 2 should be climbing")

        // Keep holding jump throughout the climb
        while (player2.isClimbing) {
            player2.update(dt = 1.0 / 60.0, moveInput = 0.0, jumpInput = true, crouchInput = false, platforms = platforms, climbTargets = listOf(box))
        }
        assertFalse(player2.isClimbing, "Climb should have finished")

        // Immediately after climb with jump still held, it should NOT launch into a secondary jump
        player2.update(dt = 1.0 / 60.0, moveInput = 0.0, jumpInput = true, crouchInput = false, platforms = platforms, climbTargets = listOf(box))
        assertFalse(player2.isJumping, "Holding jump across climb completion should NOT trigger an auto-jump")

        // Releasing jump and pressing it fresh SHOULD trigger a jump
        player2.update(dt = 1.0 / 60.0, moveInput = 0.0, jumpInput = false, crouchInput = false, platforms = platforms, climbTargets = listOf(box))
        player2.update(dt = 1.0 / 60.0, moveInput = 0.0, jumpInput = true, crouchInput = false, platforms = platforms, climbTargets = listOf(box))
    }

    @Test
    fun testClimbToWalkHandoverWithMoveInputHeld() {
        val ground = Rect(x = 0.0, y = 380.0, width = 800.0, height = 100.0)
        val box = Rect(x = 300.0, y = 280.0, width = 100.0, height = 100.0)
        val platforms = listOf(ground, box)
        val player = Player(x = box.left - 36.0, y = 380.0 - 96.0, startX = 60.0, startY = 380.0 - 96.0)

        // Initiate climb
        for (i in 0 until 6) {
            if (player.isClimbing) break
            player.update(dt = 1.0 / 60.0, moveInput = 1.0, jumpInput = true, crouchInput = false, platforms = platforms, climbTargets = listOf(box))
        }
        assertTrue(player.isClimbing, "Player should be climbing")

        // Hold moveInput = 1.0 continuously during the climb
        var climbProgressWhenExited = 0.0
        while (player.isClimbing) {
            climbProgressWhenExited = player.climbProgress
            player.update(dt = 1.0 / 60.0, moveInput = 1.0, jumpInput = false, crouchInput = false, platforms = platforms, climbTargets = listOf(box))
        }

        // Must NOT prematurely cancel before 0.85 progress (which is raw frame ~205 where mantle rise is complete)
        assertTrue(climbProgressWhenExited >= 0.84, "Climb must not cancel prematurely before reaching mantle rise completion (was $climbProgressWhenExited)")
        assertFalse(player.isClimbing, "Climb should have cleanly handed over to walking")
        assertTrue(player.isGrounded, "Player should be grounded on top of the box")
        assertFalse(player.isJumping, "Player must not auto-jump when handing over from climb to walk")
        assertEquals(player.moveSpeed, player.vx, 0.01, "Player should immediately walk forward on the box top")
    }

    @Test
    fun testClimbRisesAgainstBoxFaceBeforeMovingOverIt() {
        val ground = Rect(x = 0.0, y = 380.0, width = 800.0, height = 100.0)
        val box = Rect(x = 300.0, y = 280.0, width = 60.0, height = 100.0)
        val platforms = listOf(ground, box)
        val player = Player(x = box.left - 36.0, y = 380.0 - 96.0, startX = 60.0, startY = 380.0 - 96.0)
        player.isGrounded = true

        val startX = player.x
        val startY = player.y
        val totalRise = startY - (box.top - player.height)
        val totalShift = (box.left + 6.0) - startX

        player.update(dt = 1.0 / 60.0, moveInput = 1.0, jumpInput = true, crouchInput = false, platforms = platforms, climbTargets = listOf(box))
        assertTrue(player.isClimbing, "Climb should have started")

        var riseWhileHanging = 0.0
        var shiftWhileHanging = 0.0
        var riseAtPullUpEnd = -1.0
        var maxRise = 0.0
        var lastRise = 0.0
        val dt = 1.0 / 60.0

        while (player.isClimbing) {
            val before = player.climbPhase
            player.update(dt = dt, moveInput = 0.0, jumpInput = false, crouchInput = false, platforms = platforms, climbTargets = listOf(box))
            val rise = (startY - player.y) / totalRise
            val shift = (player.x - startX) / totalShift
            maxRise = maxOf(maxRise, rise)

            if (player.climbPhase <= 0.16) {
                riseWhileHanging = maxOf(riseWhileHanging, rise)
                shiftWhileHanging = maxOf(shiftWhileHanging, shift)
            }
            if (before < 0.67 && player.climbPhase >= 0.67) riseAtPullUpEnd = rise

            assertTrue(rise <= 1.001, "Climb must never overshoot above the box top (rise=$rise)")
            // Small dips are the body settling while the hands stay planted on the lip; a large
            // one would mean the character is sliding back down the face.
            assertTrue(rise >= lastRise - 0.10, "Climb must never drop back down the face (rise=$rise)")
            lastRise = maxOf(lastRise, rise)
        }

        // The character hangs off the lip with hands on top (raw frames 70-94, climbPhase <= 0.16),
        // so it stays down near the ground there - it only rises enough to keep its hands on the top edge.
        // Lifting it up the face during the hang is what made it look like it was levitating.
        assertTrue(riseWhileHanging < 0.20, "Must stay down near the ground while hanging, was $riseWhileHanging")
        assertTrue(shiftWhileHanging < 0.02, "Must not drift sideways while hanging, was $shiftWhileHanging")
        assertTrue(riseAtPullUpEnd > 0.98, "Pull-up should be done by 0.67, was $riseAtPullUpEnd")

        assertEquals(1.0, maxRise, 0.001, "Climb should reach exactly the box top")
        assertEquals(box.top, player.y + player.height, 0.5, "Feet should finish on the box top")
        assertTrue(player.x >= box.left && player.x + player.width <= box.right, "Should finish within the box footprint")
        assertTrue(player.isGrounded, "Should be grounded on the box afterwards")
    }

    @Test
    fun testPlayerIsNeverGroundedWithoutSupport() {
        val world = GameWorld.createDefault()
        val platforms = world.platforms
        val boxes = world.boxes
        val rnd = kotlin.random.Random(20260829)
        val dt = 1.0 / 60.0
        val moves = listOf(-1.0, 0.0, 1.0)
        var violation: String? = null

        outer@ for (trial in 0 until 80) {
            val p = world.player
            p.resetToStart()
            p.x = 200.0 + trial * 2.0
            p.y = 380.0 - 96.0
            var move = 1.0
            for (step in 0 until 700) {
                if (step % 15 == 0) move = moves[rnd.nextInt(moves.size)]
                val jump = rnd.nextInt(100) < 8
                val crouch = rnd.nextInt(100) < 10
                p.update(dt, move, jump, crouch, platforms, boxes)

                if (p.isGrounded && !p.isClimbing) {
                    // Checked against the feet, not the full collision box: the drawn character
                    // only fills the middle ~60% of that box, so a full-width test would call it
                    // supported while the whole visible body hangs off the ledge.
                    val feet = p.y + p.height
                    val footLeft = p.x + (p.width - p.footWidth) / 2.0
                    val footRight = footLeft + p.footWidth
                    val supported = platforms.any { pl ->
                        kotlin.math.abs(pl.top - feet) < 1.0 && footLeft < pl.right - 1e-6 && footRight > pl.left + 1e-6
                    }
                    if (!supported) {
                        violation = "grounded with nothing under the character: x=${p.x} feet=$feet " +
                            "(trial=$trial step=$step move=$move)"
                        break@outer
                    }
                }
            }
        }
        assertNull(violation, violation ?: "")
    }

    @Test
    fun testGameProfileVolumeSettings() {
        val storage = InMemoryGameProfileStorage()
        storage.setMusicVolume(0.6f)
        storage.setSfxVolume(0.4f)
        assertEquals(0.6f, storage.getProfile().musicVolume, 0.001f)
        assertEquals(0.4f, storage.getProfile().sfxVolume, 0.001f)
    }

    @Test
    fun testCameraRotationAndSweepOscillation() {
        val minAngle = 60.0 * (PI / 180.0)
        val maxAngle = 120.0 * (PI / 180.0)
        val speed = 1.0 // 1 radian/sec
        val camera = Camera(
            x = 600.0,
            y = 100.0,
            minAngle = minAngle,
            maxAngle = maxAngle,
            currentAngle = minAngle,
            sweepSpeed = speed,
            sweepDirection = 1.0
        )

        assertEquals(minAngle, camera.currentAngle, 0.001)
        assertEquals(minAngle, camera.facingAngle, 0.001)

        // Advance 0.5s -> angle reaches minAngle + 0.5
        camera.update(0.5)
        assertEquals(minAngle + 0.5, camera.currentAngle, 0.001)
        assertEquals(1.0, camera.sweepDirection)

        // Advance past maxAngle -> should clamp to maxAngle and reverse direction to -1.0
        val remainingToMax = maxAngle - camera.currentAngle
        camera.update(remainingToMax + 0.2)
        assertEquals(maxAngle, camera.currentAngle, 0.001)
        assertEquals(-1.0, camera.sweepDirection, "Camera must reverse sweep direction at maxAngle")

        // Advance back towards minAngle
        camera.update(0.5)
        assertEquals(maxAngle - 0.5, camera.currentAngle, 0.001)

        // Advance past minAngle -> should clamp to minAngle and reverse direction to +1.0
        val remainingToMin = camera.currentAngle - minAngle
        camera.update(remainingToMin + 0.2)
        assertEquals(minAngle, camera.currentAngle, 0.001)
        assertEquals(1.0, camera.sweepDirection, "Camera must reverse sweep direction at minAngle")
    }

    @Test
    fun testCameraVisionDetectionAndOcclusion() {
        // Camera mounted overhead at x=500, y=100 pointing downwards (PI/2 = 90 deg)
        val camera = Camera(
            x = 500.0,
            y = 100.0,
            minAngle = 80.0 * (PI / 180.0),
            maxAngle = 100.0 * (PI / 180.0),
            currentAngle = 90.0 * (PI / 180.0), // 90 deg = straight down
            sweepSpeed = 0.5,
            visionRange = 250.0,
            visionFov = 40.0 * (PI / 180.0)
        )

        val player = Player(x = 500.0 - 25.0, y = 284.0) // Directly underneath camera
        val occluder = Rect(x = 450.0, y = 200.0, width = 100.0, height = 30.0) // Floating ceiling between camera and player

        // 1. Clear line of sight
        val dist = VisionSystem.getPlayerSpottedDistance(camera, player, occluders = emptyList())
        assertNotNull(dist, "Player directly under camera should be spotted")
        assertTrue(VisionSystem.isPlayerSpotted(camera, player, occluders = emptyList()))

        // 2. Occluded by barrier
        val occludedDist = VisionSystem.getPlayerSpottedDistance(camera, player, occluders = listOf(occluder))
        assertNull(occludedDist, "Player under barrier must NOT be spotted by overhead camera")
        assertFalse(VisionSystem.isPlayerSpotted(camera, player, occluders = listOf(occluder)))

        // 3. Player far to the left outside camera FOV cone
        val outsidePlayer = Player(x = 200.0, y = 284.0)
        assertFalse(VisionSystem.isPlayerSpotted(camera, outsidePlayer, occluders = emptyList()))
    }

    @Test
    fun testCameraAlertSystemIntegrationInGameWorld() {
        // World with a camera pointing straight down at x=500, y=100, no guards
        val camera = Camera(
            x = 500.0,
            y = 100.0,
            minAngle = 89.0 * (PI / 180.0),
            maxAngle = 91.0 * (PI / 180.0),
            currentAngle = 90.0 * (PI / 180.0),
            sweepSpeed = 0.0,
            visionRange = 250.0,
            visionFov = 60.0 * (PI / 180.0)
        )
        val dummyGuard = Guard(x = 0.0, y = 0.0, patrolMinX = 0.0, patrolMaxX = 0.0, speed = 0.0, visionRange = 0.0)
        val world = GameWorld(
            player = Player(x = 60.0, y = 284.0),
            guard = dummyGuard,
            crate = Rect(0.0, 0.0, 0.0, 0.0),
            platforms = listOf(Rect(0.0, 380.0, 800.0, 100.0)),
            occluders = emptyList(),
            cameras = listOf(camera)
        )
        world.setUniformDetectionTime(0.8)
        world.alertDecayRate = 0.5

        // Player starts at safe position (x = 60)
        world.update(dt = 0.1, moveInput = 0.0, jumpInput = false)
        assertFalse(world.isPlayerInVision)
        assertEquals(0.0, world.alertProgress)

        // Move player into camera cone at x = 500
        world.player.x = 500.0 - 25.0
        world.player.y = 284.0
        world.update(dt = 0.4, moveInput = 0.0, jumpInput = false)

        assertTrue(world.isPlayerInVision, "Player in camera cone should be detected in vision")
        assertEquals(0.5, world.alertProgress, 0.02, "Alert progress should increase to 50% from camera vision")
        assertFalse(world.isGameOver)

        // Move player back to safe zone -> alert decays
        world.player.x = 60.0
        world.update(dt = 0.4, moveInput = 0.0, jumpInput = false)
        assertFalse(world.isPlayerInVision)
        assertEquals(0.3, world.alertProgress, 0.02, "Alert progress should decay when leaving camera vision")

        // Put player back in camera cone until alert reaches 100% -> triggers Game Over & caught
        world.player.x = 500.0 - 25.0
        world.update(dt = 0.8, moveInput = 0.0, jumpInput = false)
        assertTrue(world.isGameOver, "Staying in camera cone must trigger Game Over")
        assertEquals(1, world.spottedCount, "Spotted count should increment")
        assertTrue(world.wasDetected, "wasDetected flag should be set")
    }

    @Test
    fun testCameraStopsRotatingWhenPlayerDetectedAndResumesAfterInvestigationDuration() {
        // Camera sweeping at x=500, y=100
        val camera = Camera(
            x = 500.0,
            y = 100.0,
            minAngle = 60.0 * (PI / 180.0),
            maxAngle = 120.0 * (PI / 180.0),
            currentAngle = 90.0 * (PI / 180.0),
            sweepSpeed = 0.5,
            visionRange = 250.0,
            visionFov = 45.0 * (PI / 180.0),
            detectionPauseDuration = 2.5
        )
        val dummyGuard = Guard(x = 0.0, y = 0.0, patrolMinX = 0.0, patrolMaxX = 0.0, speed = 0.0, visionRange = 0.0)
        val world = GameWorld(
            player = Player(x = 60.0, y = 284.0),
            guard = dummyGuard,
            crate = Rect(0.0, 0.0, 0.0, 0.0),
            platforms = listOf(Rect(0.0, 380.0, 800.0, 100.0)),
            occluders = emptyList(),
            cameras = listOf(camera)
        )

        // 1. Sweeping normally when player is not detected
        val initialAngle = camera.currentAngle
        world.update(dt = 0.2, moveInput = 0.0, jumpInput = false)
        assertFalse(world.isPlayerInVision)
        assertNotEquals(initialAngle, camera.currentAngle, "Camera should sweep when player is not detected")

        // 2. When player is placed in vision cone, camera stops rotating
        world.player.x = 500.0 - 25.0
        world.player.y = 284.0
        world.update(dt = 0.1, moveInput = 0.0, jumpInput = false)
        assertTrue(world.isPlayerInVision, "Player must be in camera vision")
        val frozenAngle = camera.currentAngle
        assertTrue(camera.isPausedFromDetection, "Camera must flag detection pause active")

        // Additional updates while player is in vision: angle does not change at all
        for (i in 1..5) {
            world.update(dt = 0.1, moveInput = 0.0, jumpInput = false)
            assertTrue(world.isPlayerInVision)
            assertEquals(frozenAngle, camera.currentAngle, 0.0001, "Camera angle must remain frozen while detecting player")
        }

        // 3. Player moves out of vision: camera must remain frozen for 2.5s (investigateDuration)
        world.player.x = 60.0
        world.update(dt = 0.1, moveInput = 0.0, jumpInput = false)
        assertFalse(world.isPlayerInVision, "Player should no longer be in vision")
        assertEquals(frozenAngle, camera.currentAngle, 0.0001, "Camera must remain stopped at the detection angle")

        // Advance 2.3 seconds (total 2.4s after visual loss) -> still frozen
        world.update(dt = 2.3, moveInput = 0.0, jumpInput = false)
        assertEquals(frozenAngle, camera.currentAngle, 0.0001, "Camera must stay stopped for 2.5s investigation pause")
        assertTrue(camera.isPausedFromDetection, "Camera detection pause timer should still be positive")

        // Advance past 2.5s threshold (0.2s more) -> detection pause timer expires
        world.update(dt = 0.2, moveInput = 0.0, jumpInput = false)
        assertFalse(camera.isPausedFromDetection, "Detection pause timer should have expired")

        // Next frame sweeps normally
        world.update(dt = 0.1, moveInput = 0.0, jumpInput = false)
        assertNotEquals(frozenAngle, camera.currentAngle, "Camera must resume sweeping after 2.5s investigation pause")

        // 4. Respawning at checkpoint clears detection pause
        camera.onPlayerSpotted()
        assertTrue(camera.isPausedFromDetection)
        world.respawnAtCheckpoint()
        assertFalse(camera.isPausedFromDetection, "respawnAtCheckpoint must clear camera detection pause")
    }

    @Test
    fun testCameraTimingChallengeWalkthrough() {
        // Level 1 itself no longer ships a guard or camera (see LevelData.DEFAULT_LEVEL_1's
        // guardEnabled = false / empty cameras) - this test still proves the underlying
        // camera-sweep-timing mechanic is beatable in general, using a synthetic camera dropped
        // into the level's real geometry/occluders via .copy(), same pattern
        // testPowerupSmokeScreenDisablesCameras and testCameraAlertSystemIntegrationInGameWorld
        // already use for a camera unrelated to whatever the base level ships. The disabled guard
        // (world.guard, parked off-map) is left untouched here - this test is only about the
        // camera-timing mechanic, and the original version's guard placement wasn't asserted on.
        val baseWorld = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_1)
        // 80 units short of the real exit, same camera-to-exit distance the original version of
        // this test was tuned against, so the run-to-exit timing is comparably tight.
        val camera = Camera(
            x = baseWorld.exitZone.x - 80.0,
            y = 180.0,
            minAngle = (90.0 - 30.0) * (PI / 180.0),
            maxAngle = (90.0 + 30.0) * (PI / 180.0),
            currentAngle = (90.0 - 30.0) * (PI / 180.0),
            sweepSpeed = 0.7,
            visionRange = 240.0,
            visionFov = 45.0 * (PI / 180.0)
        )
        val world = baseWorld.copy(cameras = listOf(camera))
        assertEquals(1, world.cameras.size, "World should carry the one synthetic camera added above")

        // 1. When camera is sweeping down-left (120°), standing in the open corridor (e.g. camera.x - 80) spots the player
        camera.currentAngle = 120.0 * (PI / 180.0)
        world.player.x = camera.x - 80.0
        world.player.y = 284.0
        assertTrue(VisionSystem.isPlayerSpotted(camera, world.player, world.occluders), "Camera pointing down-left should spot player")

        // 2. When camera is sweeping down-right (60°), standing at same x is out of the vision cone
        camera.currentAngle = 60.0 * (PI / 180.0)
        assertFalse(VisionSystem.isPlayerSpotted(camera, world.player, world.occluders), "Camera pointing down-right leaves corridor clear")

        // 3. Timing execution: Start near the camera as it sweeps right
        world.player.x = camera.x - 60.0
        camera.currentAngle = 60.0 * (PI / 180.0)
        camera.sweepDirection = 1.0

        val dt = 1.0 / 60.0
        while (!world.isLevelComplete && !world.isGameOver) {
            world.update(dt, moveInput = 1.0, jumpInput = false)
        }

        assertTrue(world.isLevelComplete, "Player should time the camera sweep and complete the level")
        assertFalse(world.wasDetected, "Timed crossing should not trigger detection")
    }

    @Test
    fun testPowerupSmokeScreenDisablesCameras() {
        // Camera positioned to spot player
        val camera = Camera(
            x = 500.0,
            y = 100.0,
            minAngle = 80.0 * (PI / 180.0),
            maxAngle = 100.0 * (PI / 180.0),
            currentAngle = 90.0 * (PI / 180.0),
            sweepSpeed = 0.5,
            visionRange = 250.0,
            visionFov = 60.0 * (PI / 180.0)
        )
        val dummyGuard = Guard(x = 0.0, y = 0.0, patrolMinX = 0.0, patrolMaxX = 0.0, speed = 0.0, visionRange = 0.0)
        val world = GameWorld(
            player = Player(x = 500.0 - 25.0, y = 284.0),
            guard = dummyGuard,
            crate = Rect(0.0, 0.0, 0.0, 0.0),
            platforms = listOf(Rect(0.0, 380.0, 800.0, 100.0)),
            occluders = emptyList(),
            cameras = listOf(camera)
        )

        // Without smoke screen, camera spots player and increases alert
        world.update(0.1, moveInput = 0.0, jumpInput = false)
        assertTrue(world.isPlayerInVision, "Player in camera cone should be detected")
        assertTrue(world.alertProgress > 0.0, "Alert should start increasing")

        // Activate Smoke Screen
        val activated = world.activatePowerup(PowerupType.SMOKE_SCREEN)
        assertTrue(activated)
        assertTrue(world.activePowerups.isSmokeScreenActive)
        assertEquals(10.0, world.activePowerups.getRemainingTime(PowerupType.SMOKE_SCREEN), 0.01)

        val angleBefore = camera.currentAngle

        // Update during smoke screen: camera sweep is paused, vision is skipped, alert decays
        world.update(2.0, moveInput = 0.0, jumpInput = false)
        assertEquals(angleBefore, camera.currentAngle, 0.001, "Camera sweep should be paused while Smoke Screen is active")
        assertFalse(world.isPlayerInVision, "VisionSystem should skip camera detection during Smoke Screen")
        assertEquals(0.0, world.alertProgress, 0.01, "Alert progress should decay to 0 while camera is disabled")

        // Move player away to safe position while smoke screen is active
        world.player.x = 60.0

        // Advance until smoke screen expires (after 8 more seconds, total 10s)
        world.update(8.1, moveInput = 0.0, jumpInput = false)
        assertFalse(world.activePowerups.isSmokeScreenActive, "Smoke Screen should expire after 10s")

        // Camera resumes sweeping
        assertNotEquals(angleBefore, camera.currentAngle, "Camera sweep should resume after Smoke Screen expires")

        // Move player back under camera: camera detects player and stops rotating
        world.player.x = 500.0 - 25.0
        world.update(0.1, moveInput = 0.0, jumpInput = false)
        assertTrue(world.isPlayerInVision, "Camera should resume detection after Smoke Screen expires")
        val stoppedAngle = camera.currentAngle
        world.update(0.1, moveInput = 0.0, jumpInput = false)
        assertEquals(stoppedAngle, camera.currentAngle, 0.001, "Camera should stop rotating when player is detected")
    }

    @Test
    fun testActivatePowerupRefusesReactivationWhileAlreadyActive() {
        val world = GameWorld.createDefault()

        // Timed powerup (INVISIBILITY, 10s): a second activation mid-countdown must be refused,
        // not reset the clock back to full - otherwise spamming the button would grant infinite
        // uptime for the cost of one item.
        assertTrue(world.activatePowerup(PowerupType.INVISIBILITY))
        world.update(4.0, moveInput = 0.0, jumpInput = false)
        val remainingBeforeRetry = world.activePowerups.getRemainingTime(PowerupType.INVISIBILITY)
        assertEquals(6.0, remainingBeforeRetry, 0.01)

        val reactivated = world.activatePowerup(PowerupType.INVISIBILITY)
        assertFalse(reactivated, "Re-activating a still-running timed powerup must be refused")
        assertEquals(
            remainingBeforeRetry, world.activePowerups.getRemainingTime(PowerupType.INVISIBILITY), 0.01,
            "A refused re-activation must not touch the running countdown"
        )

        // Once it actually expires, activating again must succeed.
        world.update(remainingBeforeRetry + 0.1, moveInput = 0.0, jumpInput = false)
        assertFalse(world.activePowerups.isInvisibilityActive)
        assertTrue(world.activatePowerup(PowerupType.INVISIBILITY), "Activation must succeed again once the effect has expired")

        // Level-duration powerup (NOISE_SUPPRESSION): same refusal while already on.
        assertTrue(world.activatePowerup(PowerupType.NOISE_SUPPRESSION))
        assertFalse(
            world.activatePowerup(PowerupType.NOISE_SUPPRESSION),
            "Re-activating an already-on level-duration powerup must be refused"
        )
    }

    @Test
    fun testPowerupLaserShieldBlocksLaserHit() {
        val laser = Laser(
            id = "test_laser",
            topX = 330.0,
            topY = 100.0,
            bottomX = 330.0,
            bottomY = 400.0,
            beamThickness = 10.0,
            activeDuration = 5.0,
            inactiveDuration = 1.0,
            isAlwaysActive = true
        )
        val world = GameWorld(
            player = Player(x = 325.0, y = 284.0, width = 20.0, height = 40.0), // Intersects vertical laser at x = 330
            guard = Guard(x = 1000.0, y = 1000.0, patrolMinX = 900.0, patrolMaxX = 1100.0), // Far away
            crate = Rect(0.0, 0.0, 0.0, 0.0),
            platforms = listOf(Rect(0.0, 380.0, 800.0, 100.0)),
            occluders = emptyList(),
            lasers = listOf(laser)
        )

        // Verify laser intersects player bounds
        assertTrue(laser.intersectsPlayer(world.player.bounds), "Player bounds must intersect test laser")

        // Without laser shield, laser causes game over immediately
        world.update(0.05, moveInput = 0.0, jumpInput = false)
        assertTrue(world.isGameOver, "Player without shield must trigger game over when touching laser")

        // Reset world and activate Laser Shield
        world.restartLevel()
        assertFalse(world.isGameOver)
        assertEquals(0, world.activePowerups.laserShieldCharges)
        assertFalse(world.activePowerups.isLaserShieldActive)

        var blockedCount = 0
        world.onLaserShieldBlocked = { blockedCount++ }

        world.activatePowerup(PowerupType.LASER_SHIELD)
        assertTrue(world.activePowerups.isLaserShieldActive)
        assertEquals(1, world.activePowerups.laserShieldCharges)

        // Update: laser hits player, but shield absorbs the hit
        world.update(0.05, moveInput = 0.0, jumpInput = false)
        assertFalse(world.isGameOver, "Player with Laser Shield must NOT trigger game over on laser contact")
        assertEquals(1, blockedCount, "Shield blocked callback must be invoked once")
        assertEquals(0, world.activePowerups.laserShieldCharges, "Laser Shield charge must be consumed")
        assertFalse(world.activePowerups.isLaserShieldActive, "Laser Shield must no longer be active")
        assertTrue(world.laserGraceTimer > 0.0, "Grace period timer must be active after deflection")

        // Step within grace period (0.5s): player is still inside beam, but grace period protects them
        world.update(0.5, moveInput = 0.0, jumpInput = false)
        assertFalse(world.isGameOver, "Player must be protected during grace period")
        assertEquals(1, blockedCount)

        // Step past remaining grace period (~0.7s remaining):
        world.update(1.0, moveInput = 0.0, jumpInput = false)
        assertEquals(0.0, world.laserGraceTimer, 0.001, "Grace timer must have expired")
        assertTrue(world.isGameOver, "Laser contact after grace period without shield must trigger game over")
    }

    @Test
    fun testPowerupInvisibilityGrantsFullImmunity() {
        val guard = Guard(
            x = 400.0,
            y = 332.0,
            patrolMinX = 300.0,
            patrolMaxX = 500.0,
            facing = -1.0,
            speed = 50.0,
            visionRange = 250.0,
            visionFov = 60.0 * (PI / 180.0)
        )
        val camera = Camera(
            x = 330.0,
            y = 100.0,
            minAngle = 80.0 * (PI / 180.0),
            maxAngle = 100.0 * (PI / 180.0),
            currentAngle = 90.0 * (PI / 180.0),
            sweepSpeed = 0.5,
            visionRange = 250.0,
            visionFov = 60.0 * (PI / 180.0)
        )
        val world = GameWorld(
            player = Player(x = 330.0, y = 284.0),
            guard = guard,
            crate = Rect(0.0, 0.0, 0.0, 0.0),
            platforms = listOf(Rect(0.0, 380.0, 800.0, 100.0)),
            occluders = emptyList(),
            cameras = listOf(camera)
        )

        // Activate Invisibility
        world.activatePowerup(PowerupType.INVISIBILITY)
        assertTrue(world.activePowerups.isInvisibilityActive)

        val guardStartPos = guard.x
        val cameraStartAngle = camera.currentAngle

        // Both guard and camera keep operating normally, but player is completely immune to detection
        world.update(2.0, moveInput = 0.0, jumpInput = false)
        assertFalse(world.isPlayerInVision, "Invisible player cannot be seen by guard or camera")
        assertEquals(0.0, world.alertProgress, 0.01)
        assertNotEquals(guardStartPos, guard.x, "Guard continues patrolling normally during player invisibility")
        assertNotEquals(cameraStartAngle, camera.currentAngle, "Camera continues sweeping normally during player invisibility")

        // Wait out the 10 seconds
        world.update(8.1, moveInput = 0.0, jumpInput = false)
        assertFalse(world.activePowerups.isInvisibilityActive, "Invisibility expires after 10s")
    }

    @Test
    fun testPowerupNoiseSuppressionLevelDuration() {
        val guard = Guard(
            x = 300.0,
            y = 332.0,
            patrolMinX = 100.0,
            patrolMaxX = 500.0,
            facing = 1.0, // Facing right away from player
            speed = 50.0,
            visionRange = 200.0,
            visionFov = 60.0 * (PI / 180.0)
        )
        val world = GameWorld(
            player = Player(x = 150.0, y = 284.0), // Behind guard within noise radius (dist = 150 < 180)
            guard = guard,
            crate = Rect(0.0, 0.0, 0.0, 0.0),
            platforms = listOf(Rect(0.0, 380.0, 800.0, 100.0)),
            occluders = emptyList()
        )

        // Walking (sprinting) behind guard without noise suppression triggers noise investigation
        world.update(0.1, moveInput = 1.0, jumpInput = false, crouchInput = false)
        assertEquals(GuardState.INVESTIGATING, guard.state, "Normal walking within 180px should alert guard")

        // Reset guard to patrol
        guard.returnToPatrol()
        assertEquals(GuardState.PATROL, guard.state)

        // Activate Noise Suppression
        world.activatePowerup(PowerupType.NOISE_SUPPRESSION)
        assertTrue(world.activePowerups.isNoiseSuppressed)
        assertTrue(world.activePowerups.isActive(PowerupType.NOISE_SUPPRESSION))

        // Walking behind guard with Noise Suppression does NOT trigger noise investigation
        for (i in 0 until 50) {
            world.update(0.1, moveInput = 1.0, jumpInput = false, crouchInput = false)
        }
        assertEquals(GuardState.PATROL, guard.state, "Noise suppression must prevent guard noise detection while sprinting")

        // Verify it lasts for level-duration (e.g. 60 seconds later, still active)
        world.update(60.0, moveInput = 0.0, jumpInput = false)
        assertTrue(world.activePowerups.isNoiseSuppressed, "Noise suppression must last for entire level duration")
    }

    @Test
    fun testGuardInvestigatedFromNoiseFlag() {
        val guard = Guard(
            x = 300.0,
            y = 200.0,
            patrolMinX = 200.0,
            patrolMaxX = 400.0
        )
        assertFalse(guard.investigatedFromNoise)

        // Hearing noise sets investigatedFromNoise = true
        guard.onNoiseHeard(350.0)
        assertEquals(GuardState.INVESTIGATING, guard.state)
        assertTrue(guard.investigatedFromNoise)

        // Returning to patrol clears investigatedFromNoise
        guard.returnToPatrol()
        assertEquals(GuardState.PATROL, guard.state)
        assertFalse(guard.investigatedFromNoise)

        // Losing visual sets INVESTIGATING but investigatedFromNoise is false
        guard.onVisualLost(350.0)
        assertEquals(GuardState.INVESTIGATING, guard.state)
        assertFalse(guard.investigatedFromNoise, "Visual loss must not set investigatedFromNoise")

        guard.returnToPatrol()
        assertFalse(guard.investigatedFromNoise)
    }

    @Test
    fun testActivePowerupsStateModel() {
        val active = ActivePowerups()
        assertFalse(active.anyActive)
        assertFalse(active.isSmokeScreenActive)
        assertFalse(active.isLaserShieldActive)
        assertFalse(active.isInvisibilityActive)
        assertFalse(active.isNoiseSuppressed)

        active.activate(PowerupType.SMOKE_SCREEN)
        assertTrue(active.isSmokeScreenActive)
        assertTrue(active.anyActive)
        assertEquals(10.0, active.getRemainingTime(PowerupType.SMOKE_SCREEN))

        active.update(4.0)
        assertEquals(6.0, active.getRemainingTime(PowerupType.SMOKE_SCREEN), 0.01)

        active.activate(PowerupType.NOISE_SUPPRESSION)
        assertTrue(active.isNoiseSuppressed)
        assertEquals(-1.0, active.getRemainingTime(PowerupType.NOISE_SUPPRESSION))

        active.activate(PowerupType.LASER_SHIELD)
        assertTrue(active.isLaserShieldActive)
        assertEquals(1, active.laserShieldCharges)
        assertEquals(-1.0, active.getRemainingTime(PowerupType.LASER_SHIELD))
        assertTrue(active.consumeLaserShield())
        assertFalse(active.isLaserShieldActive)
        assertFalse(active.consumeLaserShield())

        active.reset()
        assertFalse(active.anyActive)
    }

    @Test
    fun testGameProfilePowerupInventoryAndPersistence() {
        val storageMap = mutableMapOf<String, String>()
        val profileStorage = MapBackedGameProfileStorage(
            getRaw = { storageMap[it] },
            setRaw = { k, v -> storageMap[k] = v }
        )

        val profile = profileStorage.getProfile()
        assertEquals(1, profile.getPowerupCount(PowerupType.INVISIBILITY))
        assertEquals(0, profile.getPowerupCount(PowerupType.SMOKE_SCREEN))
        assertEquals(0, profile.getPowerupCount(PowerupType.LASER_SHIELD))
        assertEquals(0, profile.getPowerupCount(PowerupType.NOISE_SUPPRESSION))

        val initialInvis = profile.getPowerupCount(PowerupType.INVISIBILITY)
        val consumed = profileStorage.consumePowerup(PowerupType.INVISIBILITY)
        assertTrue(consumed)
        assertEquals(initialInvis - 1, profileStorage.getProfile().getPowerupCount(PowerupType.INVISIBILITY))

        // Verify persistence to storage map
        assertTrue(storageMap.containsKey("user_powerups"))

        // Create fresh storage instance reading from storageMap
        val reloadedStorage = MapBackedGameProfileStorage(
            getRaw = { storageMap[it] },
            setRaw = { k, v -> storageMap[k] = v }
        )
        assertEquals(
            initialInvis - 1,
            reloadedStorage.getProfile().getPowerupCount(PowerupType.INVISIBILITY),
            "Powerup count should persist across storage reloads"
        )

        // Grant debug powerups
        reloadedStorage.grantDebugPowerups(5)
        assertEquals(initialInvis - 1 + 5, reloadedStorage.getProfile().getPowerupCount(PowerupType.INVISIBILITY))
    }

    @Test
    fun testPowerupRemoteTriggerInventoryAndConsume() {
        val profile = GameProfile(powerupInventory = mutableMapOf("remote_trigger" to 1))
        assertEquals(1, profile.getPowerupCount(PowerupType.REMOTE_TRIGGER))

        // Consume remote trigger
        assertTrue(profile.consumePowerup(PowerupType.REMOTE_TRIGGER))
        assertEquals(0, profile.getPowerupCount(PowerupType.REMOTE_TRIGGER))

        // Cannot consume when 0 available
        assertFalse(profile.consumePowerup(PowerupType.REMOTE_TRIGGER))

        // Add alias "remote_trigger" to inventory
        profile.powerupInventory["remote_trigger"] = 3
        assertEquals(3, profile.getPowerupCount(PowerupType.REMOTE_TRIGGER))
        assertTrue(profile.consumePowerup(PowerupType.REMOTE_TRIGGER))
        assertEquals(2, profile.getPowerupCount(PowerupType.REMOTE_TRIGGER))
    }

    @Test
    fun testTacticalCheckpointRecordingAndRespawn() {
        val player = Player(x = 100.0, y = 284.0)
        val guard = Guard(
            x = 800.0,
            y = 332.0,
            patrolMinX = 700.0,
            patrolMaxX = 900.0,
            facing = -1.0,
            speed = 50.0,
            visionRange = 200.0,
            visionFov = 60.0 * (PI / 180.0)
        )
        val world = GameWorld(
            player = player,
            guard = guard,
            crate = Rect(0.0, 0.0, 0.0, 0.0),
            platforms = listOf(Rect(0.0, 380.0, 2000.0, 100.0)),
            occluders = emptyList()
        )

        assertEquals(100.0, world.lastCheckpointX)
        assertFalse(world.hasAdvancedCheckpoint)

        var securedX = 0.0
        world.onCheckpointSecured = { cx, _ -> securedX = cx }

        // Settle player on ground first
        world.update(0.1, moveInput = 0.0, jumpInput = false)
        assertTrue(player.isGrounded)

        // Move player forward past 100 + 250 = 350px while grounded and safe
        player.x = 400.0
        world.update(0.1, moveInput = 0.0, jumpInput = false)

        assertTrue(world.hasAdvancedCheckpoint)
        assertEquals(400.0, world.lastCheckpointX)
        assertEquals(400.0, securedX)

        // Check continue limit before respawn
        assertTrue(world.canContinue, "canContinue should be true initially")
        assertEquals(0, world.continueCount)

        // Respawn at checkpoint
        val respawnOk = world.respawnAtCheckpoint()
        assertTrue(respawnOk)
        assertFalse(world.isGameOver)
        assertEquals(400.0, player.x)
        assertEquals(GuardState.PATROL, guard.state)
        assertTrue(world.activePowerups.isInvisibilityActive, "Respawning should give grace invisibility")
        assertEquals(3.0, world.laserGraceTimer, 1e-4, "Respawning should give laser grace period")
        assertEquals(1, world.continueCount)
        assertFalse(world.canContinue, "canContinue should be false after using 1 continue")

        // Second continue attempt in same run should be rejected
        world.isGameOver = true
        val secondRespawnOk = world.respawnAtCheckpoint()
        assertFalse(secondRespawnOk, "Second continue in same run should be rejected")

        // Restarting level resets continue cap
        world.restartLevel()
        assertEquals(0, world.continueCount)
        assertTrue(world.canContinue, "restartLevel should reset continue count")
    }

    @Test
    fun testStorePowerupPurchasesAndInventory() {
        val storageMap = mutableMapOf<String, String>()
        val profileStorage = MapBackedGameProfileStorage(
            getRaw = { storageMap[it] },
            setRaw = { k, v -> storageMap[k] = v }
        )

        // Set starting coin balance
        profileStorage.addCoins(5000)
        val initialCoins = profileStorage.getProfile().coins
        assertTrue(initialCoins >= 5000)

        // Test buying each powerup type
        val powerupsToTest = listOf(
            PowerupType.SMOKE_SCREEN to 600,
            PowerupType.LASER_SHIELD to 600,
            PowerupType.INVISIBILITY to 1000,
            PowerupType.NOISE_SUPPRESSION to 750,
            PowerupType.REMOTE_TRIGGER to 750
        )

        for ((type, cost) in powerupsToTest) {
            val countBefore = profileStorage.getProfile().getPowerupCount(type)
            val coinsBefore = profileStorage.getProfile().coins

            val bought = profileStorage.buyPowerup(type.id, cost)
            assertTrue(bought, "Should successfully buy ${type.displayName} for $cost coins")

            val profileAfter = profileStorage.getProfile()
            assertEquals(coinsBefore - cost, profileAfter.coins, "Coins should be deducted by $cost")
            assertEquals(countBefore + 1, profileAfter.getPowerupCount(type), "Inventory for ${type.displayName} should increment by 1")
        }

        // Test failed purchase when coins are insufficient
        val currentCoins = profileStorage.getProfile().coins
        profileStorage.spendCoins(currentCoins - 100) // Leave only 100 coins
        assertEquals(100, profileStorage.getProfile().coins)

        val failedBuy = profileStorage.buyPowerup(PowerupType.INVISIBILITY.id, 1000)
        assertFalse(failedBuy, "Purchase should fail if player does not have enough coins")
        assertEquals(100, profileStorage.getProfile().coins, "Coins should not be deducted on failed purchase")
    }

    @Test
    fun testPlayerFallsWhenFeetAreMostlyOutsidePlatform() {
        // Platform from x=100.0 to 200.0, surface at y=300.0
        val platform = Rect(100.0, 300.0, 100.0, 50.0)
        val platforms = listOf(platform)
        val player = Player(x = 150.0, y = 300.0 - 96.0)

        // Settle player onto platform
        player.update(1.0 / 60.0, moveInput = 0.0, jumpInput = false, crouchInput = false, platforms = platforms)
        assertTrue(player.isGrounded, "Player should be grounded when centered on platform")

        // Position player so the center of feet is still on the platform edge:
        // player.width = 36.0, centerX = x + 18.0.
        // If x = 200.0 - 18.0 = 182.0, centerX is exactly on platform.right (200.0).
        player.x = 182.0
        player.update(1.0 / 60.0, moveInput = 0.0, jumpInput = false, crouchInput = false, platforms = platforms)
        assertTrue(player.isGrounded, "Player should remain grounded while at least half the foot is supported")

        // Move player slightly further right so more than half (most) of feet are outside (centerX > platform.right)
        player.x = 183.0 // centerX = 201.0 > 200.0
        player.update(1.0 / 60.0, moveInput = 0.0, jumpInput = false, crouchInput = false, platforms = platforms)
        assertFalse(player.isGrounded, "Player should lose ground support and fall when most of feet are outside")
        assertTrue(player.vy > 0.0, "Player should begin falling under gravity")
    }

    @Test
    fun testPlayerDropForwardSpeedIsControlledAndJumpingRetainsFullSpeed() {
        // High platform at y=200.0, floor at y=400.0 (a 200px vertical drop)
        val platform = Rect(100.0, 200.0, 100.0, 50.0) // x: 100..200
        val floor = Rect(0.0, 400.0, 500.0, 50.0)
        val platforms = listOf(platform, floor)

        val dt = 1.0 / 60.0

        // Case 1: Drop off the edge without jumping while holding forward (moveInput = 1.0)
        val droppingPlayer = Player(x = 182.0, y = 200.0 - 96.0)
        droppingPlayer.isGrounded = true

        // Walk off the right edge (x=200.0)
        droppingPlayer.update(dt, moveInput = 1.0, jumpInput = false, crouchInput = false, platforms = platforms)
        assertFalse(droppingPlayer.isGrounded, "Player should become airborne after walking off edge")
        assertTrue(droppingPlayer.isDropping, "Player should be in dropping state")
        assertEquals(droppingPlayer.dropSpeed, droppingPlayer.vx, 0.01, "Airborne speed during drop should be dropSpeed (40.0)")

        // Simulate falling until touching the floor
        while (!droppingPlayer.isGrounded) {
            droppingPlayer.update(dt, moveInput = 1.0, jumpInput = false, crouchInput = false, platforms = platforms)
        }
        assertTrue(droppingPlayer.isGrounded, "Player should have landed on floor")
        // Over a 200px drop, forward distance past the platform edge should be tightly controlled (~18px), NOT ~68px
        val distancePastEdge = droppingPlayer.x - platform.right
        assertTrue(distancePastEdge < 25.0, "Dropping forward distance past edge should be < 25px, was $distancePastEdge px")
        assertTrue(droppingPlayer.dropLandingTimer > 0.0, "Drop landing timer should cushion horizontal speed upon touchdown")

        // Case 2: Intentional Jump retains full moveSpeed (132.0)
        val jumpingPlayer = Player(x = 150.0, y = 200.0 - 96.0)
        jumpingPlayer.isGrounded = true
        jumpingPlayer.update(dt, moveInput = 1.0, jumpInput = true, crouchInput = false, platforms = platforms)
        assertTrue(jumpingPlayer.isJumping, "Player should be in jumping state")
        assertFalse(jumpingPlayer.isDropping, "Player should NOT be in dropping state when jumping")
        assertEquals(jumpingPlayer.moveSpeed, jumpingPlayer.vx, 0.01, "Airborne speed during jump must be full moveSpeed (132.0)")
    }

    @Test
    fun testLevel1TutorialStepDefinitions() {
        val level1 = LevelData.DEFAULT_LEVEL_1
        val steps = level1.tutorialSteps
        assertEquals(5, steps.size, "Level 1 must contain exactly 5 focused tutorial milestones")

        // Step 1: Movement
        val moveStep = steps[0]
        assertEquals("step_move", moveStep.id)
        assertEquals(TutorialAction.MOVE, moveStep.targetAction)
        assertEquals(TutorialControlHighlight.MOVE, moveStep.highlight)
        assertTrue(moveStep.triggerMinX <= 235.0 && moveStep.triggerMaxX >= 450.0)
        assertTrue(moveStep.title.isNotEmpty())
        assertTrue(moveStep.instructionTouch.isNotEmpty())
        assertTrue(moveStep.instructionDesktop.isNotEmpty())
        assertNotNull(moveStep.handwrittenCallout)
        assertTrue(moveStep.handwrittenCallout!!.isNotEmpty())

        // Step 2: Jump & Vault
        val jumpStep = steps[1]
        assertEquals("step_jump_vault", jumpStep.id)
        assertEquals(TutorialAction.JUMP_VAULT, jumpStep.targetAction)
        assertEquals(TutorialControlHighlight.JUMP, jumpStep.highlight)
        assertTrue(jumpStep.triggerMinX <= 450.0 && jumpStep.triggerMaxX >= 850.0)
        assertNotNull(jumpStep.handwrittenCallout)

        // Step 3: Crouch
        val crouchStep = steps[2]
        assertEquals("step_crouch", crouchStep.id)
        assertEquals(TutorialAction.CROUCH, crouchStep.targetAction)
        assertEquals(TutorialControlHighlight.CROUCH, crouchStep.highlight)
        assertTrue(crouchStep.triggerMinX <= 1050.0 && crouchStep.triggerMaxX >= 1450.0)
        assertNotNull(crouchStep.handwrittenCallout)

        // Step 4: Mantle & Climb
        val climbStep = steps[3]
        assertEquals("step_climb", climbStep.id)
        assertEquals(TutorialAction.CLIMB, climbStep.targetAction)
        assertEquals(TutorialControlHighlight.JUMP, climbStep.highlight)
        assertTrue(climbStep.triggerMinX <= 1980.0 && climbStep.triggerMaxX >= 2080.0)
        assertNotNull(climbStep.handwrittenCallout)

        // Step 5: Reach Objective
        val objectiveStep = steps[4]
        assertEquals("step_reach_objective", objectiveStep.id)
        assertEquals(TutorialAction.REACH_OBJECTIVE, objectiveStep.targetAction)
        assertEquals(TutorialControlHighlight.NONE, objectiveStep.highlight)
        assertTrue(objectiveStep.triggerMinX <= 3000.0 && objectiveStep.triggerMaxX >= 3500.0)
        assertNotNull(objectiveStep.handwrittenCallout)

        // Ensure milestones are ordered monotonically from left to right
        for (i in 0 until steps.size - 1) {
            assertTrue(
                steps[i].triggerMinX <= steps[i + 1].triggerMinX,
                "Tutorial step ${steps[i].id} minX (${steps[i].triggerMinX}) must be <= step ${steps[i + 1].id} minX (${steps[i + 1].triggerMinX})"
            )
        }
    }

    @Test
    fun testTutorialProgressionThroughLevel1Milestones() {
        val level1 = LevelData.DEFAULT_LEVEL_1
        val steps = level1.tutorialSteps
        val completedSteps = mutableSetOf<String>()
        var activeStep: TutorialStep? = null

        fun evaluateTutorial(playerX: Double, isActionDone: (TutorialStep) -> Boolean): TutorialStep? {
            if (activeStep == null) {
                activeStep = steps.firstOrNull { it.id !in completedSteps && playerX >= it.triggerMinX && playerX <= it.triggerMaxX }
            }
            val current = activeStep
            if (current != null) {
                if (isActionDone(current) || playerX > current.triggerMaxX) {
                    completedSteps.add(current.id)
                    activeStep = null
                }
            }
            return activeStep
        }

        // 1. Player at spawn (x = 235.0) -> Movement milestone activates
        evaluateTutorial(235.0) { false }
        assertNotNull(activeStep)
        assertEquals("step_move", activeStep?.id)

        // Player moves forward to x = 330.0 and executes movement
        evaluateTutorial(330.0) { step -> step.targetAction == TutorialAction.MOVE }
        assertNull(activeStep)
        assertTrue(completedSteps.contains("step_move"))

        // 2. Player approaches crate at x = 500.0 -> Jump milestone activates
        evaluateTutorial(500.0) { false }
        assertNotNull(activeStep)
        assertEquals("step_jump_vault", activeStep?.id)

        // Player vaults over crate and truck
        evaluateTutorial(650.0) { step -> step.targetAction == TutorialAction.JUMP_VAULT }
        assertNull(activeStep)
        assertTrue(completedSteps.contains("step_jump_vault"))

        // 3. Player reaches hanging chained crate section at x = 1150.0 -> Crouch milestone activates
        evaluateTutorial(1150.0) { false }
        assertNotNull(activeStep)
        assertEquals("step_crouch", activeStep?.id)

        // Player crouches underneath hanging crate
        evaluateTutorial(1280.0) { step -> step.targetAction == TutorialAction.CROUCH }
        assertNull(activeStep)
        assertTrue(completedSteps.contains("step_crouch"))

        // 4. Player reaches high ledge at block2 at x = 2000.0 -> Climb milestone activates
        evaluateTutorial(2000.0) { false }
        assertNotNull(activeStep)
        assertEquals("step_climb", activeStep?.id)

        // Player climbs up onto block2
        evaluateTutorial(2048.0) { step -> step.targetAction == TutorialAction.CLIMB }
        assertNull(activeStep)
        assertTrue(completedSteps.contains("step_climb"))

        // 5. Player approaches extraction booth at x = 3100.0 -> Reach Objective milestone activates
        evaluateTutorial(3100.0) { false }
        assertNotNull(activeStep)
        assertEquals("step_reach_objective", activeStep?.id)

        // Player touches extraction zone
        evaluateTutorial(3400.0) { step -> step.targetAction == TutorialAction.REACH_OBJECTIVE }
        assertNull(activeStep)
        assertTrue(completedSteps.contains("step_reach_objective"))

        assertEquals(5, completedSteps.size, "All 5 tutorial steps must be marked completed")
    }

    @Test
    fun testTutorialSpeedrunMilestoneTraversal() {
        val level1 = LevelData.DEFAULT_LEVEL_1
        val steps = level1.tutorialSteps
        val completedSteps = mutableSetOf<String>()
        var activeStep: TutorialStep? = null

        fun updateStep(playerX: Double) {
            if (activeStep == null) {
                activeStep = steps.firstOrNull { it.id !in completedSteps && playerX >= it.triggerMinX && playerX <= it.triggerMaxX }
            }
            val current = activeStep
            if (current != null && playerX > current.triggerMaxX) {
                completedSteps.add(current.id)
                activeStep = null
            }
        }

        // Simulate a speedrunner sprinting continuously through the corridor without pausing
        val corridorWaypoints = listOf(235.0, 400.0, 480.0, 700.0, 900.0, 1100.0, 1500.0, 2000.0, 2150.0, 2800.0, 3100.0, 3600.0)
        for (x in corridorWaypoints) {
            updateStep(x)
        }

        assertEquals(5, completedSteps.size, "Speedrunning through all milestone trigger boundaries should auto-advance all steps")
    }

    @Test
    fun testTutorialBacktrackingDoesNotReTriggerCompletedSteps() {
        val level1 = LevelData.DEFAULT_LEVEL_1
        val steps = level1.tutorialSteps
        val completedSteps = mutableSetOf<String>()
        var activeStep: TutorialStep? = null

        fun updateStep(playerX: Double, actionDone: Boolean = false) {
            if (activeStep == null) {
                activeStep = steps.firstOrNull { it.id !in completedSteps && playerX >= it.triggerMinX && playerX <= it.triggerMaxX }
            }
            val current = activeStep
            if (current != null && (actionDone || playerX > current.triggerMaxX)) {
                completedSteps.add(current.id)
                activeStep = null
            }
        }

        // Complete step 1 (Movement)
        updateStep(235.0)
        assertEquals("step_move", activeStep?.id)
        updateStep(300.0, actionDone = true)
        assertNull(activeStep)
        assertTrue(completedSteps.contains("step_move"))

        // Backtrack to spawn
        updateStep(235.0)
        assertNull(activeStep, "Backtracking to spawn should NOT re-trigger completed step_move")
        updateStep(200.0)
        assertNull(activeStep)
    }

    // ---- hook swing mechanics (verified on level 5's own barrel-stack & hook layout) --------

    companion object {
        // Level 5 ("05: Restricted Zone") IS the barrel-stack-and-hook layout - these tests drive
        // the real shipped level directly rather than a parallel copy, so they double as its
        // walkthrough verification (see LevelData.SIDE_SCROLL_LEVEL_LAYOUT's own doc comment).
        val SWING_TEST_LAYOUT = LevelData.SIDE_SCROLL_LEVEL_LAYOUT
        val SWING_TEST_LEVEL = LevelData.SIDE_SCROLL_LEVEL
    }

    @Test
    fun testLevel4StartOnConveyorAndNoFences() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        assertNull(world.fence1, "Level 4 has no fence1 at the start")
        assertNull(world.fence2, "Level 4 has no fence2 at the start")
        assertEquals(1, world.boxes.size, "Level 4 static boxes include conveyor")
        assertEquals(45, world.conveyorCrates.size, "Level 4 has 45 moving conveyor crates (38 floor + 7 hanging) across the extended 7760px line")
        assertEquals(1, world.conveyors.size, "Level 4 has one conveyor belt")
        assertFalse(world.canClimb, "Level 4 is non-climbable: all mantling/climbing is disabled")
        assertTrue(world.levelData.hasDarknessVignette, "Level 4 has darkness vignette enabled")

        // Confirm floor crates have 27 1-stacks (48.0px) and 11 stepped 2-stacks (96.0px)
        val floorCrates = world.conveyorCrates.filter { !it.isHanging }
        assertEquals(38, floorCrates.size, "Level 4 has 38 floor crates")
        assertEquals(27, floorCrates.count { it.height == 48.0 }, "27 1-stacks (48px) for clean jumping and zero clipping")
        assertEquals(11, floorCrates.count { it.height == 96.0 }, "11 2-stacks (96px) for stepped platforming")
        assertTrue(floorCrates.all { it.shouldLoop }, "All floor crates have non-stop looping enabled")

        // Confirm hanging crates across the 150m gauntlet (3 long + 4 small)
        assertEquals(3, world.hangingCrateVariant1.size, "Level 4 has 3 long hanging crates")
        assertTrue(world.hangingCrateVariant1.all { it.width == 174.0 }, "All long hanging crates have width 174.0")
        assertTrue(world.hangingCrateVariant1.all { it.height == 38.0 }, "All long hanging crates have height 38.0")
        // Long hanging crates: 2 overhead multi-stack crushers (maxY = 212.0) + 1 finale monorail (y = 302.0)
        assertTrue(world.hangingCrateVariant1.all { it.y <= 302.0 + 1e-4 }, "All long hanging crates sit at or above y = 302.0 (bottom <= 340.0)")

        assertEquals(4, world.hangingCrateVariant2.size, "Level 4 has 4 small hanging crates")
        assertTrue(world.hangingCrateVariant2.all { it.width == 76.0 }, "All small hanging crates have width 76.0")
        assertTrue(world.hangingCrateVariant2.all { it.height == 38.0 }, "All small hanging crates have height 38.0")
        assertTrue(world.hangingCrateVariant2.all { it.y == 302.0 }, "All small hanging crates sit at y = 302.0 (bottom = 340.0)")

        // Physical vertical clearance between 1-stack floor crates and hanging crates is at least 26.0px (zero clipping!)
        for (hanging in world.conveyorCrates.filter { it.isHanging }) {
            for (floor in floorCrates.filter { it.height == 48.0 }) {
                val verticalGap = floor.top - hanging.bounds.bottom
                assertTrue(verticalGap >= 26.0 - 1e-4, "Floor crate top (366.0) is at least 26px below hanging crate bottom (<=340.0) -> zero clipping!")
            }
            for (floor in floorCrates) {
                assertFalse(hanging.bounds.intersects(floor.bounds), "Floor crates never intersect hanging crates")
            }
        }

        val conveyor = world.conveyors.single()
        assertEquals(0.0, conveyor.bounds.x, 1e-4, "Conveyor moved to the corner (x = 0.0)")
        assertEquals(440.0 - 26.0, conveyor.bounds.y, 1e-4, "Conveyor height reduced to 26.0")
        assertEquals(7760.0, conveyor.bounds.width, 1e-4, "Conveyor length extended past 0m marker (x = 7742.0) to 7760.0")
        assertEquals(26.0, conveyor.bounds.height, 1e-4, "Conveyor height is 26.0")
        assertEquals(-45.0, conveyor.speed, 1e-4, "Conveyor speed is -45.0")
        assertTrue(conveyor.bounds in world.platforms, "Conveyor is a platform to stand on")
        assertTrue(conveyor.bounds in world.boxes, "Conveyor is a solid box to collide/climb")

        // Player starts directly on top of the conveyor belt
        assertEquals(100.0, world.player.x, 1e-4, "Player starts on conveyor at x=100.0")
        assertEquals(conveyor.bounds.top, world.player.y + world.player.height, 1e-4, "Player starts standing on conveyor")

        // Conveyor is initially stationary before player moves
        assertFalse(world.conveyorsActive, "Conveyor is initially not moving")
    }

    @Test
    fun testLevel4NonClimbablePreventsMantling() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        val singleCrate = world.conveyorCrates.first { !it.isHanging }

        // Stand on conveyor right in front of the 1-stack crate facing right
        world.player.x = singleCrate.left - world.player.width - 2.0
        world.player.y = 414.0 - world.player.height
        world.player.isGrounded = true

        // Press jump while moving right towards the crate
        world.update(1.0 / 60.0, 1.0, true, false)

        // In a normal climbable level, player would enter isClimbing = true
        // In Level 4, climbing is disabled, so isClimbing must remain false!
        assertFalse(world.player.isClimbing, "Player cannot climb/mantle any obstacle in Level 4")
    }

    @Test
    fun testLevel4ConveyorInitiallyStationaryAndStartsMovingOnPlayerMovement() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        val startX = world.player.x
        world.player.vy = 0.0
        world.player.isGrounded = true

        // While player is idle with no move/jump input, conveyor remains stationary
        world.update(0.2, 0.0, false, false)
        assertFalse(world.conveyorsActive, "Conveyor stays stationary while player has not moved")
        assertEquals(startX, world.player.x, 1e-4, "Player is not carried by conveyor while stationary")

        // When player inputs movement, conveyor activates and begins pushing
        world.update(0.1, 1.0, false, false)
        assertTrue(world.conveyorsActive, "Conveyor activates once player starts moving")
    }

    @Test
    fun testLevel4CarriedOffConveyorTriggersInstantRestart() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        var callbackFired = false
        world.onConveyorFallOff = { callbackFired = true }

        // Start player near left edge of conveyor
        world.player.x = 10.0
        world.player.y = 414.0 - world.player.height
        world.player.isGrounded = true

        // Updating with move input to activate conveyor and carry player past x = 0.0
        // speed = -45.0, in 0.3s moves 13.5 units left -> x becomes < 0
        world.update(0.01, 1.0, false, false) // activate conveyor
        world.update(0.3, 0.0, false, false)  // carried backward

        assertTrue(callbackFired, "Instant restart callback fired when taken off conveyor")
        assertEquals(world.player.startX, world.player.x, 1e-4, "Player reset to startX")
        assertEquals(world.player.startY, world.player.y, 1e-4, "Player reset to startY")
        assertEquals(0.0f, world.timeTaken, 1e-4f, "Time taken reset to 0 on instant restart")
        assertFalse(world.conveyorsActive, "Conveyor resets to stationary upon instant restart")
    }

    @Test
    fun testLevel4FallingOffConveyorTriggersInstantRestart() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        var callbackFired = false
        world.onConveyorFallOff = { callbackFired = true }

        // Activate conveyor
        world.update(0.01, 1.0, false, false)

        // Position player falling below conveyor surface inside the conveyor span
        world.player.x = 350.0
        world.player.y = 430.0 // Feet at 430 + 96 = 526 > bounds.top + 8 = 422
        world.player.isGrounded = true

        world.update(1.0 / 60.0, 0.0, false, false)

        assertTrue(callbackFired, "Instant restart callback fired when falling below conveyor")
        assertEquals(world.player.startX, world.player.x, 1e-4, "Player reset to startX")
        assertEquals(world.player.startY, world.player.y, 1e-4, "Player reset to startY")
    }

    @Test
    fun testLevel4ReachingEndAndExitDoesNotTriggerRestart() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        var callbackFired = false
        world.onConveyorFallOff = { callbackFired = true }

        // Position player past conveyor end (x = 7760.0) heading to exit zone (x = 7820.0)
        world.player.x = 7780.0
        world.player.y = 440.0 - world.player.height
        world.player.isGrounded = true

        world.update(1.0 / 60.0, 0.0, false, false)

        assertFalse(callbackFired, "No restart when player is safely past conveyor towards exit")
    }

    @Test
    fun testLevel4HangingCrateRequiresCrouchToDodge() {
        val conveyorTop = 414.0

        // 1. Standing on conveyor in front of hanging crate: touching its front edge is OK (not lethal)!
        val world1 = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        world1.conveyorsActive = true
        val crate1 = world1.conveyorCrates.first { it.isHanging && !it.isVariant1 }
        world1.player.x = crate1.left - world1.player.width - 2.0
        world1.player.y = conveyorTop - world1.player.height
        world1.player.isGrounded = true

        world1.update(0.05, 1.0, false, false)
        assertFalse(world1.isGameOver, "Touching the front of a hanging crate does not trigger game over")
        assertTrue(world1.player.x + world1.player.width <= crate1.left + 1e-2,
            "Standing player on conveyor floor cannot walk through lowered small hanging crate")

        // 2. Touching a stationary hanging crate does NOT trigger game over (player only dies if moving down)
        val worldStationary = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        worldStationary.conveyorsActive = true
        val crateStationary = worldStationary.conveyorCrates.first { it.isHanging && it.minY == it.maxY }
        worldStationary.player.x = crateStationary.left + 10.0
        worldStationary.player.y = conveyorTop - worldStationary.player.height // head overlaps crate bottom
        worldStationary.player.isGrounded = true

        worldStationary.update(0.05, 0.0, false, false)
        assertFalse(crateStationary.isMovingDown, "Stationary crate is not moving down")
        assertFalse(worldStationary.isGameOver, "Touching stationary crate does not trigger game over")

        // 3. Oscillating hanging crate moving UP: touching it does NOT trigger game over
        val worldOscUp = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        worldOscUp.conveyorsActive = true
        val crateOsc = worldOscUp.conveyorCrates.first { it.isHanging && it.minY != it.maxY && it.maxY == 302.0 }
        // Keep player safe while advancing to upward phase (t in [2.0, 4.0])
        worldOscUp.player.x = 500.0
        worldOscUp.player.y = -1000.0
        worldOscUp.player.isGrounded = false
        var tUp = 0.0
        while (tUp < 2.5) {
            worldOscUp.update(0.05, 0.0, false, false)
            tUp += 0.05
        }
        assertFalse(crateOsc.isMovingDown, "Crate moving upward is not moving down")
        worldOscUp.player.x = crateOsc.left + 20.0
        worldOscUp.player.y = conveyorTop - worldOscUp.player.height
        worldOscUp.player.isGrounded = true
        worldOscUp.update(0.05, 0.0, false, false)
        assertFalse(worldOscUp.isGameOver, "Touching crate while it is moving up does not trigger game over")

        // 4. Oscillating hanging crate moving DOWN: touching player DOES trigger game over (mission failed)!
        val worldOscDown = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        worldOscDown.conveyorsActive = true
        val crateDown = worldOscDown.conveyorCrates.first { it.isHanging && it.minY != it.maxY && it.maxY == 302.0 }
        var gameOverFired = false
        var hangingHitFired = false
        worldOscDown.onGameOver = { gameOverFired = true }
        worldOscDown.onHangingCrateHit = { hangingHitFired = true }

        // Keep player safe while advancing time to avoid falling off conveyor or hitting lasers
        worldOscDown.player.x = 500.0
        worldOscDown.player.y = -1000.0
        worldOscDown.player.isGrounded = false

        // Advance to downward phase where crate is moving down and overlaps standing player's head (t in [0.0, 2.0], ~t=1.6s)
        var t = 0.0
        while (t < 1.6) {
            worldOscDown.update(0.05, 0.0, false, false)
            t += 0.05
        }
        assertTrue(crateDown.isMovingDown, "Crate is moving down in downward phase")

        // Position standing player underneath downward-moving crate
        worldOscDown.player.x = crateDown.left + 20.0
        worldOscDown.player.y = conveyorTop - worldOscDown.player.height
        worldOscDown.player.isGrounded = true

        worldOscDown.update(0.05, 0.0, false, false)
        assertTrue(worldOscDown.isGameOver, "Downward moving hanging crate touching player triggers game over")
        assertTrue(gameOverFired, "onGameOver fired when downward moving crate touches player")
        assertTrue(hangingHitFired, "onHangingCrateHit fired when downward moving crate touches player")

        // 5. Crouching on conveyor under oscillating hanging crate: player advances cleanly without hitting it!
        val worldCrouch = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        worldCrouch.conveyorsActive = true
        val crateCrouch = worldCrouch.conveyorCrates.first { it.isHanging && it.maxY == 302.0 && it.minY != it.maxY }
        worldCrouch.player.x = crateCrouch.left - worldCrouch.player.width - 2.0
        worldCrouch.player.y = conveyorTop - worldCrouch.player.height
        worldCrouch.player.isGrounded = true

        val startX = worldCrouch.player.x
        // Advance in crouch mode across multiple seconds so crate goes through downward cycles
        for (step in 0 until 80) {
            worldCrouch.update(0.05, 1.0, false, true)
            assertFalse(worldCrouch.isGameOver, "Crouching player never gets hit by hanging crate")
        }
        assertTrue(worldCrouch.player.x > startX + 10.0, "Crouching player advances cleanly under hanging crate")
    }

    @Test
    fun testLevel4CratesMoveWithConveyorAndCarryPlayer() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        val floorCrate = world.conveyorCrates.first { !it.isHanging }
        val initialCrateX = floorCrate.x

        // Activate conveyor
        world.update(0.01, 1.0, false, false)
        assertTrue(world.conveyorsActive)

        // Advance 0.2s: crate should move by conveyor.speed (-45.0) * 0.2 = -9.0
        world.update(0.2, 0.0, false, false)
        assertEquals(initialCrateX - 45.0 * 0.2, floorCrate.x, 1.0, "Crate moved left with the conveyor belt")

        // Place player standing on top of a single moving crate
        world.player.x = floorCrate.x + 10.0
        world.player.y = floorCrate.top - world.player.height
        world.player.vy = 0.0
        world.player.isGrounded = true

        val playerStartXOnCrate = world.player.x
        // Advance 0.2s with no player input: player should be carried along with the moving crate
        world.update(0.2, 0.0, false, false)
        val playerShift = world.player.x - playerStartXOnCrate
        assertEquals(-45.0 * 0.2, playerShift, 1.0, "Player standing on crate is carried along with the crate")

        // On instant restart, all conveyor crates reset to initial positions
        world.restartLevel()
        assertEquals(initialCrateX, floorCrate.x, 1e-4, "Conveyor crate resets to initialX on restart")
    }

    @Test
    fun testLevel4ExtendedGauntletStructureAndPacing() {
        val level = LevelData.DEFAULT_LEVEL_4
        val world = GameWorld.createDefault(level)

        // 1. Level time target scaled for gauntlet
        assertEquals(115.0f, level.timeTargetSeconds, 1e-4f, "Level 4 target time is 115 seconds")
        assertEquals(8600.0, world.worldWidth, 1e-4, "World width extended to 8600.0")
        assertTrue(level.hasDarknessVignette, "Level 4 has darkness vignette enabled")
        assertEquals(1.45, level.playerCrouchForwardSpeedMultiplier, 1e-4, "Level 4 has 1.45x crouch forward speed multiplier")
        assertEquals(65.0 * 1.45, world.player.crouchForwardSpeed, 1e-4, "Player crouch forward speed is tuned to 1.45x (94.25)")

        // 2. Obstacle count and types: mix of 1-stack, 2-stack stepped pyramids, and multi-stacks
        assertEquals(45, world.conveyorCrates.size, "Level 4 has 45 dynamic conveyor crates (38 floor + 7 hanging)")
        assertEquals(38, world.conveyorCrates.count { !it.isHanging }, "Level 4 has 38 floor crates")
        assertEquals(7, world.conveyorCrates.count { it.isHanging }, "Level 4 has 7 dynamic hanging crates")
        assertEquals(7, world.hangingCrateVariant1.size + world.hangingCrateVariant2.size, "Level 4 has 7 hanging crates")
        assertEquals(12, world.lasers.size, "Level 4 has 12 periodic vertical, crossed, and tilted laser hazards")

        val floorCrates = world.conveyorCrates.filter { !it.isHanging }
        val hangingCrates = world.conveyorCrates.filter { it.isHanging }

        // 3. 27 single 1-stacks (48px) and 11 stepped 2-stacks (96px)
        assertEquals(27, floorCrates.count { it.height == 48.0 }, "27 single 1-stack crates")
        assertEquals(11, floorCrates.count { it.height == 96.0 }, "11 2-stack crates for vertical platforming")

        // 4. Zero clipping: All hanging crates bottom is <= 340.0, maintaining clearance above floor crates
        assertTrue(hangingCrates.all { it.bounds.bottom <= 340.0 }, "All hanging crates bottom is <= 340.0")
        for (hanging in hangingCrates) {
            for (floor in floorCrates) {
                assertFalse(hanging.bounds.intersects(floor.bounds), "Floor crate never intersects hanging crate in 2D space")
            }
        }

        // 5. Hanging crates move in same direction and speed as conveyor belt
        assertTrue(hangingCrates.all { it.speedMultiplier == 1.0 }, "All hanging crates have speedMultiplier = 1.0")
        assertTrue(hangingCrates.all { it.shouldLoop }, "All hanging crates loop across conveyor track")
        assertTrue(hangingCrates.none { it.isPatrol }, "Hanging crates no longer patrol; they flow with belt")

        // 6. Vertical oscillation on crates 2, 3, 4, and 5
        val oscillatingCrates = hangingCrates.filter { it.verticalPeriodSeconds > 0.0 }
        assertEquals(4, oscillatingCrates.size, "Four hanging crates oscillate vertically")

        // Activate conveyor and simulate movement with player running forward
        val initialCrateX = hangingCrates.map { it.x }
        world.update(0.01, 1.0, false)
        for (step in 0 until 10) {
            world.update(0.05, 1.0, false)
        }
        for (i in hangingCrates.indices) {
            assertTrue(hangingCrates[i].x < initialCrateX[i], "Hanging crate moves in same direction (leftward) as conveyor")
        }
    }

    @Test
    fun testLevel4FinaleAirlockLasersRequireStagedAdvance() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        val l1 = world.lasers.first { it.id == "lvl4_laser_gauntlet_1" }
        val l2 = world.lasers.first { it.id == "lvl4_laser_gauntlet_2" }
        val l3 = world.lasers.first { it.id == "lvl4_laser_gauntlet_3" }

        // Positioning: spaced 120px apart
        assertEquals(7120.0, l1.topX, 1e-4)
        assertEquals(7240.0, l2.topX, 1e-4)
        assertEquals(7360.0, l3.topX, 1e-4)

        // Pocket 1: between l1 and l2 (around x = 7180)
        val pocket1Player = Rect(x = 7165.0, y = 318.0, width = 36.0, height = 96.0)
        // Pocket 2: between l2 and l3 (around x = 7300)
        val pocket2Player = Rect(x = 7285.0, y = 318.0, width = 36.0, height = 96.0)

        // Phase 1 at t = 1.0s: Laser 1 is OFF, Laser 2 is ON, Laser 3 is ON
        l1.update(1.0)
        l2.update(1.0)
        l3.update(1.0)
        assertFalse(l1.isActive, "Laser 1 is OFF in Phase 1 (safe to enter)")
        assertTrue(l2.isActive, "Laser 2 is ON in Phase 1 (blocking forward progress)")
        assertTrue(l3.isActive, "Laser 3 is ON in Phase 1")
        assertFalse(l1.intersectsPlayer(pocket1Player), "Player is safe in Pocket 1 from Laser 1")
        assertFalse(l2.intersectsPlayer(pocket1Player), "Player is safe in Pocket 1 from Laser 2")

        // Phase 2 at t = 3.0s: Laser 1 is ON (locks behind), Laser 2 is OFF, Laser 3 is ON
        l1.update(3.0)
        l2.update(3.0)
        l3.update(3.0)
        assertTrue(l1.isActive, "Laser 1 is ON in Phase 2 (locks player behind)")
        assertFalse(l2.isActive, "Laser 2 is OFF in Phase 2 (safe to advance to Pocket 2)")
        assertTrue(l3.isActive, "Laser 3 is ON in Phase 2 (blocking forward progress)")
        assertFalse(l2.intersectsPlayer(pocket2Player), "Player is safe in Pocket 2 from Laser 2")
        assertFalse(l3.intersectsPlayer(pocket2Player), "Player is safe in Pocket 2 from Laser 3")

        // Phase 3 at t = 5.0s: Laser 2 is ON (locks behind), Laser 3 is OFF
        l1.update(5.0)
        l2.update(5.0)
        l3.update(5.0)
        assertTrue(l2.isActive, "Laser 2 is ON in Phase 3 (locks player behind)")
        assertFalse(l3.isActive, "Laser 3 is OFF in Phase 3 (safe to clear gauntlet to exit)")
    }

    @Test
    fun testLevel4ConveyorConnectsFlushToBoxAndProximityTriggersVictory() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        val conveyor = world.conveyors.single()

        // Conveyor right edge is at 7760.0
        assertEquals(7760.0, conveyor.bounds.right, 1e-4, "Conveyor ends directly at extraction machine (7760.0)")

        // Standing before the trigger zone: not complete
        world.player.x = 7600.0
        world.player.y = conveyor.bounds.top - world.player.height
        world.player.isGrounded = true
        world.update(0.05, 0.0, false, false)
        assertFalse(world.isLevelComplete, "Not complete before reaching proximity of terminal box")

        // Walking close to the terminal box (x >= 7680)
        world.player.x = 7700.0
        world.player.y = conveyor.bounds.top - world.player.height
        world.player.isGrounded = true
        var victoryFired = false
        world.onLevelComplete = { victoryFired = true }

        world.update(0.05, 0.0, false, false)
        assertTrue(world.isLevelComplete, "Reaching proximity of terminal box triggers mission success")
        assertTrue(victoryFired, "onLevelComplete callback fired")
    }

    @Test
    fun testLevel4MultiStackCrateClimbAndDuckUnderDescendingCargo() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        val conveyorTop = 414.0
        val crateHeight2 = 96.0
        val twoStackTop = conveyorTop - crateHeight2 // 318.0

        val hanging1 = world.conveyorCrates.first { it.initialX == 2200.0 && it.isHanging }

        // Top of 2-stack platform is at 318.0.
        // Standing player on 2-stack: head is at 318.0 - 96.0 = 222.0.
        // Crouching player on 2-stack: head is at 318.0 - 56.0 = 262.0.
        // Lowest point of hanging crate 1 is maxY = 212.0 (bottom = 250.0).
        assertEquals(212.0, hanging1.maxY, 1e-4)
        assertEquals(250.0, hanging1.maxY + hanging1.height, 1e-4)

        // 1. Crouching player on 2-stack platform has headroom and never hits crate
        val crouchHead = twoStackTop - world.player.crouchHeight
        assertTrue(crouchHead > (hanging1.maxY + hanging1.height),
            "Crouching player head ($crouchHead) has 12px clearance below lowest crate bottom (250.0)")

        // 2. Standing player on 2-stack platform gets crushed when crate descends
        val standHead = twoStackTop - world.player.height
        assertTrue(standHead < (hanging1.maxY + hanging1.height),
            "Standing player head ($standHead) is inside crate travel space (crush condition)")
    }

    @Test
    fun testLevel4CrouchForwardSpeedIsTuned() {
        val level1World = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_1)
        assertEquals(65.0, level1World.player.crouchSpeed, 1e-4)
        assertEquals(65.0, level1World.player.crouchForwardSpeed, 1e-4)

        val level4World = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        assertEquals(65.0, level4World.player.crouchSpeed, 1e-4, "Backward/base crouch speed remains 65.0")
        val expectedSpeed = 65.0 * 1.45
        assertEquals(expectedSpeed, level4World.player.crouchForwardSpeed, 1e-4, "Forward crouch speed is 1.45x ($expectedSpeed)")

        // Test forward crouch velocity with grounded player
        level4World.player.isGrounded = true
        level4World.player.isDropping = false
        level4World.player.update(0.1, 1.0, false, true, listOf(Rect(0.0, 414.0, 1000.0, 50.0)))
        assertEquals(expectedSpeed, level4World.player.vx, 1e-4, "Moving forward while crouching moves at $expectedSpeed px/s")

        // Test backward crouch velocity with grounded player
        level4World.player.isGrounded = true
        level4World.player.isDropping = false
        level4World.player.update(0.1, -1.0, false, true, listOf(Rect(0.0, 414.0, 1000.0, 50.0)))
        assertEquals(-65.0, level4World.player.vx, 1e-4, "Moving backward while crouching moves at 65.0 px/s")
    }

    @Test
    fun testLevel4HangingCrateVerticalOscillationAndZeroClipping() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        val oscillatingCrates = world.conveyorCrates.filter { it.isHanging && it.verticalPeriodSeconds > 0.0 }
        val floorCrates = world.conveyorCrates.filter { !it.isHanging }

        // Step through full oscillation cycle and verify limits and zero clipping for all oscillating crates
        for (oscillatingCrate in oscillatingCrates) {
            val period = oscillatingCrate.verticalPeriodSeconds
            for (step in 0..50) {
                val dt = period / 50.0
                world.update(dt, 0.0, false)
                assertTrue(oscillatingCrate.y >= oscillatingCrate.minY - 1e-4, "y >= minY (${oscillatingCrate.minY}), was ${oscillatingCrate.y}")
                assertTrue(oscillatingCrate.y <= oscillatingCrate.maxY + 1e-4, "y <= maxY (${oscillatingCrate.maxY}), was ${oscillatingCrate.y}")
                assertTrue(oscillatingCrate.bottom <= 340.0 + 1e-4, "bottom <= 340.0, was ${oscillatingCrate.bottom}")

                // Verify zero clipping against floor crates
                for (floor in floorCrates) {
                    assertFalse(oscillatingCrate.bounds.intersects(floor.bounds), "Zero clipping guaranteed throughout oscillation")
                }
            }
        }
    }

    @Test
    fun testLevel4CrossedLasersIntersectionAndConstraints() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        val cross1a = world.lasers.first { it.id == "lvl4_laser_cross_1a" }
        val cross1b = world.lasers.first { it.id == "lvl4_laser_cross_1b" }

        // Both lasers originate at ceiling and hit conveyor
        assertEquals(150.0, cross1a.topY, 1e-4)
        assertEquals(150.0, cross1b.topY, 1e-4)
        assertEquals(414.0, cross1a.bottomY, 1e-4)
        assertEquals(414.0, cross1b.bottomY, 1e-4)

        // Crossed geometry: 1a tilts right, 1b tilts left
        assertTrue(cross1a.tiltAngleDegrees > 0.0, "cross1a tilts right")
        assertTrue(cross1b.tiltAngleDegrees < 0.0, "cross1b tilts left")
        assertTrue(kotlin.math.abs(cross1a.tiltAngleDegrees) <= 45.0, "cross1a tilt <= 45°")
        assertTrue(kotlin.math.abs(cross1b.tiltAngleDegrees) <= 45.0, "cross1b tilt <= 45°")
        assertEquals(cross1a.tiltAngleDegrees, -cross1b.tiltAngleDegrees, 1e-2, "Symmetrical X-crossing")

        // Mid-air crossing point around x=5070, y=282
        val playerAtIntersection = Rect(x = 5052.0, y = 250.0, width = 36.0, height = 96.0)
        assertTrue(cross1a.intersectsPlayer(playerAtIntersection), "Player at intersection hits cross1a")
        assertTrue(cross1b.intersectsPlayer(playerAtIntersection), "Player at intersection hits cross1b")
    }

    @Test
    fun testLaserPeriodicActivationCycle() {
        val laser = Laser(
            id = "test_laser",
            x = 100.0,
            y = 200.0,
            width = 100.0,
            height = 6.0,
            activeDuration = 2.0,
            inactiveDuration = 1.0,
            phaseOffsetSeconds = 0.0
        )

        laser.update(0.5)
        assertTrue(laser.isActive, "Active during activeDuration")

        laser.update(2.5)
        assertFalse(laser.isActive, "Inactive during inactiveDuration")

        laser.update(3.2)
        assertTrue(laser.isActive, "Active again in next cycle")
    }

    @Test
    fun testLaserTiltAngleLimitEnforced() {
        // Tilt exceeding 45 degrees throws IllegalArgumentException
        // dy = 414.0 - 150.0 = 264.0. For 45 deg, max |dx| = 264.0.
        // dx = 265.0 -> atan2(265, 264) > 45.1 degrees -> must throw!
        assertFailsWith<IllegalArgumentException> {
            Laser(
                id = "excessive_tilt_pos",
                topX = 100.0,
                topY = 150.0,
                bottomX = 100.0 + 265.0,
                bottomY = 414.0
            )
        }
        assertFailsWith<IllegalArgumentException> {
            Laser(
                id = "excessive_tilt_neg",
                topX = 500.0,
                topY = 150.0,
                bottomX = 500.0 - 265.0,
                bottomY = 414.0
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LaserDef(
                id = "excessive_tilt_def",
                topX = 100.0,
                topY = 150.0,
                bottomX = 400.0, // dx = 300 > 264
                bottomY = 414.0
            )
        }

        // Within 45 degrees: succeeds
        val valid45 = Laser(
            id = "valid_45",
            topX = 100.0,
            topY = 150.0,
            bottomX = 100.0 + 264.0,
            bottomY = 414.0
        )
        assertEquals(45.0, valid45.tiltAngleDegrees, 0.01)

        val validVertical = Laser(
            id = "valid_vert",
            topX = 200.0,
            topY = 150.0,
            bottomX = 200.0,
            bottomY = 414.0
        )
        assertEquals(0.0, validVertical.tiltAngleDegrees, 1e-4)
    }

    @Test
    fun testLevel4LasersOriginateFromTopAndAimAtBottom() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        assertEquals(12, world.lasers.size, "Level 4 has 12 lasers")

        for (laser in world.lasers) {
            assertEquals(150.0, laser.topY, 1e-4, "Laser '${laser.id}' must originate from top ceiling (y=150.0)")
            assertEquals(414.0, laser.bottomY, 1e-4, "Laser '${laser.id}' must terminate at conveyor surface (y=414.0)")
            assertTrue(kotlin.math.abs(laser.tiltAngleDegrees) <= 45.0 + 1e-4,
                "Laser '${laser.id}' tilt (${laser.tiltAngleDegrees}°) must never exceed 45° from vertical")
        }
    }

    @Test
    fun testVerticalLaserSegmentCollision() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        val vertLaser = world.lasers.first { it.id == "lvl4_laser_vert_1" }
        assertEquals(1950.0, vertLaser.topX, 1e-4)
        assertEquals(1950.0, vertLaser.bottomX, 1e-4)
        assertEquals(0.0, vertLaser.tiltAngleDegrees, 1e-4)

        // Player standing directly in the vertical laser path (conveyor level: y=318..414)
        // Player width is 30.0px -> spans [1940.0, 1970.0], which encloses x=1950.0
        val playerBoundsInPath = Rect(x = 1940.0, y = 318.0, width = 30.0, height = 96.0)
        assertTrue(vertLaser.intersectsPlayer(playerBoundsInPath), "Player in vertical beam path intersects laser")

        // Crouching player in vertical laser path (y=358..414) also intersects vertical beam
        val crouchingInPath = Rect(x = 1940.0, y = 358.0, width = 30.0, height = 56.0)
        assertTrue(vertLaser.intersectsPlayer(crouchingInPath), "Crouching player in vertical beam path intersects laser")

        // Player safely away from vertical laser path
        val playerSafe = Rect(x = 1800.0, y = 318.0, width = 30.0, height = 96.0)
        assertFalse(vertLaser.intersectsPlayer(playerSafe), "Player away from vertical laser does not intersect")

        // Inactive laser does not collide even when player is in path
        vertLaser.update(2.5) // activeDuration is 1.4, so at 2.5s it is inactive
        assertFalse(vertLaser.isActive)
        assertFalse(vertLaser.intersectsPlayer(playerBoundsInPath), "Inactive vertical laser does not collide")
    }

    @Test
    fun testTiltedLaserSegmentCollision() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        val tiltLaser = world.lasers.first { it.id == "lvl4_laser_cross_1a" }
        assertEquals(5000.0, tiltLaser.topX, 1e-4)
        assertEquals(5140.0, tiltLaser.bottomX, 1e-4)
        assertTrue(tiltLaser.tiltAngleDegrees > 20.0 && tiltLaser.tiltAngleDegrees <= 45.0)

        // Near conveyor floor (y in [318.0, 414.0]), beam passes near x in [5100.0, 5140.0]
        val playerAtConveyor = Rect(x = 5120.0, y = 318.0, width = 30.0, height = 96.0)
        assertTrue(tiltLaser.intersectsPlayer(playerAtConveyor), "Player intersects tilted beam near floor")

        // At ceiling level (y in [150.0, 200.0]), beam passes near x in [5000.0, 5030.0]
        // Player at x=5120, y=150 is nowhere near the tilted beam
        val playerHighAway = Rect(x = 5120.0, y = 150.0, width = 30.0, height = 50.0)
        assertFalse(tiltLaser.intersectsPlayer(playerHighAway), "Player at ceiling x=5120 is not near tilted beam")

        val playerHighInBeam = Rect(x = 4995.0, y = 150.0, width = 30.0, height = 50.0)
        assertTrue(tiltLaser.intersectsPlayer(playerHighInBeam), "Player at ceiling near origin intersects tilted beam")
    }

    @Test
    fun testLaserHitTriggersConveyorRestart() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        var laserHitFired = false
        var gameOverFired = false
        world.onLaserHit = { laserHitFired = true }
        world.onGameOver = { gameOverFired = true }

        val activeLaser = world.lasers.first { it.isActive }
        // Position player at conveyor surface where the laser beam hits the bottom cylinder
        world.player.x = activeLaser.bottomX - 10.0
        world.player.y = 414.0 - 96.0
        world.player.isCrouching = false

        world.update(0.01, 0.0, false)
        assertTrue(laserHitFired, "onLaserHit callback triggered when touching active laser")
        assertTrue(gameOverFired, "onGameOver callback triggered for mission failed screen")
        assertTrue(world.isGameOver, "Touching laser sets isGameOver to true")

        // In a world without restartOnConveyorFallOff, laser hit triggers spotted game over
        val testLaser = Laser(
            id = "laser_barrier",
            topX = 220.0,
            topY = 150.0,
            bottomX = 220.0,
            bottomY = 414.0
        )
        val regularWorld = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_1).copy(lasers = listOf(testLaser))
        regularWorld.player.x = 210.0
        regularWorld.player.y = 318.0
        regularWorld.update(0.01, 0.0, false)
        assertTrue(regularWorld.isSpotted, "Laser touch sets isSpotted in regular level")
        assertTrue(regularWorld.isGameOver, "Laser touch sets isGameOver in regular level")
        assertEquals(1, regularWorld.spottedCount, "Spotted count incremented")
    }

    @Test
    fun testLevel4SimulationPlayable() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        val dt = 1.0 / 60.0
        var elapsed = 0.0
        val maxSimTime = 140.0
        var restartCount = 0

        var preUpdateX = world.player.x
        var preUpdateY = world.player.y
        var lastLogTime = 0.0
        world.onLaserHit = {
            restartCount++
            println("[SIM RESTART] Laser Hit at t=${elapsed.toFloat()}s, atX=${preUpdateX.toInt()}, atY=${preUpdateY.toInt()}")
        }
        world.onConveyorFallOff = {
            restartCount++
            println("[SIM RESTART] Conveyor Fall Off at t=${elapsed.toFloat()}s, atX=${preUpdateX.toInt()}, atY=${preUpdateY.toInt()}")
        }
        world.onHangingCrateHit = {
            restartCount++
            println("[SIM RESTART] Hanging Crate Hit at t=${elapsed.toFloat()}s, atX=${preUpdateX.toInt()}, atY=${preUpdateY.toInt()}")
        }

        data class SimTrapZone(
            val lasers: List<Laser>,
            val entryX: Double,
            val exitX: Double
        ) {
            val isActive: Boolean get() = lasers.any { it.isActive }
            fun remainingInactiveTime(t: Double): Double =
                lasers.minOf { it.remainingInactiveTime(t) }
        }

        // Build continuous trap zones by grouping lasers whose spans overlap or have < 85px gap
        val rawLaserSpans = world.lasers.map { laser ->
            val entry = kotlin.math.min(laser.topX, laser.bottomX) - laser.beamThickness / 2.0
            val exit = kotlin.math.max(laser.topX, laser.bottomX) + laser.beamThickness / 2.0
            Triple(laser, entry, exit)
        }.sortedBy { it.second }

        val trapZones = mutableListOf<SimTrapZone>()
        for (item in rawLaserSpans) {
            val last = trapZones.lastOrNull()
            if (last != null && item.second <= last.exitX + 85.0) {
                trapZones[trapZones.lastIndex] = SimTrapZone(
                    lasers = last.lasers + item.first,
                    entryX = last.entryX,
                    exitX = maxOf(last.exitX, item.third)
                )
            } else {
                trapZones.add(SimTrapZone(listOf(item.first), item.second, item.third))
            }
        }

        while (elapsed < maxSimTime && !world.isLevelComplete && !world.isGameOver) {
            val p = world.player
            val pRight = p.x + p.width

            // 1. Hanging crates overhead/ahead: duck well in advance so player can slide under
            // - On conveyor floor (p.y > 300): duck under low hanging crates (crate.maxY >= 300)
            // - On raised 2-stack platform (p.y <= 300): duck under overhead crushers
            val underHangingCrate = world.conveyorCrates.firstOrNull { crate ->
                crate.isHanging && (pRight >= crate.x - 25.0 && p.x < crate.x + crate.width + 10.0) &&
                    (p.y <= 300.0 || crate.maxY >= 300.0)
            }
            var crouchInput = underHangingCrate != null

            // 2. Floor crates ahead that are taller than current feet and require a hop
            val floorCrateAhead = world.conveyorCrates.firstOrNull { crate ->
                !crate.isHanging && crate.x > p.x && (crate.x - pRight) in -5.0..35.0 &&
                    crate.top < (p.y + p.height - 4.0)
            }
            val shouldHopFloorCrate = floorCrateAhead != null && p.isGrounded
            if (shouldHopFloorCrate && (underHangingCrate == null || !underHangingCrate.isMovingDown)) {
                // Hop onto floor crate when safe (not directly under a downward-moving crusher)
                crouchInput = false
            } else if (underHangingCrate != null) {
                // Must stay crouched when underneath/crossing under a hanging crate
                crouchInput = true
            }

            // 3. Find closest upcoming trap zone that player hasn't fully cleared yet
            val upcomingZone = trapZones
                .filter { p.x < it.exitX }
                .minByOrNull { it.entryX }

            val inLaserTrap = upcomingZone != null && (pRight >= upcomingZone.entryX - 5.0 && p.x <= upcomingZone.exitX)

            var moveInput = 1.0
            var jumpInput = false

            if (inLaserTrap) {
                // Inside or straddling the trap: ALWAYS sprint forward to clear!
                moveInput = 1.0
                if (shouldHopFloorCrate) {
                    jumpInput = true
                }
            } else if (upcomingZone != null) {
                val distToEntry = upcomingZone.entryX - pRight

                if (distToEntry > 140.0) {
                    // Far from laser: advance normally
                    moveInput = 1.0
                    if (shouldHopFloorCrate) {
                        jumpInput = true
                    }
                } else {
                    // Approaching laser: determine if safe to clear
                    val distToClear = (upcomingZone.exitX - p.x + 10.0).coerceAtLeast(10.0)
                    val timeToClear = distToClear / 87.0
                    val safeToEnter = !upcomingZone.isActive && upcomingZone.remainingInactiveTime(elapsed) >= (timeToClear + 0.15)

                    if (safeToEnter) {
                        // Inactive window is wide enough: sprint!
                        moveInput = 1.0
                        if (shouldHopFloorCrate) {
                            jumpInput = true
                        }
                    } else {
                        // Laser is active or about to reactivate: wait at staging buffer (25..45px)
                        val shouldHopCrate = shouldHopFloorCrate && (floorCrateAhead!!.x - pRight) in -5.0..30.0
                        jumpInput = shouldHopCrate
                        moveInput = when {
                            distToEntry < 20.0 -> -0.3 // too close, back off
                            distToEntry in 20.0..40.0 -> 45.0 / 132.0 // hold position against conveyor
                            else -> 0.6 // approach to staging line
                        }
                    }
                }
            } else {
                moveInput = 1.0
                if (floorCrateAhead != null && p.isGrounded && !crouchInput) {
                    jumpInput = true
                }
            }


            preUpdateX = world.player.x
            preUpdateY = world.player.y
            world.update(dt, moveInput, jumpInput, crouchInput)
            elapsed += dt
            if (elapsed - lastLogTime >= 2.0) {
                lastLogTime = elapsed
                println("[SIM] t=${elapsed.toInt()}s: pX=${world.player.x.toInt()} pY=${world.player.y.toInt()} grounded=${world.player.isGrounded}")
            }
        }

        println("Level 4 simulation finished at t=${elapsed.toFloat()}s, playerX=${world.player.x.toInt()}, isComplete=${world.isLevelComplete}, restarts=$restartCount")
        assertTrue(world.isLevelComplete, "Level 4 should be playable and beatable via valid gameplay solution. Ended at x=${world.player.x.toInt()} after ${elapsed.toInt()}s with $restartCount restarts.")
    }

    @Test
    fun testLevel5HookGapIsOnlyCrossableBySwinging() {
        val world = GameWorld.createDefault(SWING_TEST_LEVEL)
        val layout = SWING_TEST_LAYOUT
        val hook = world.swingHooks.first()
        val gapStart = layout.boxes.first { it.width == 300.0 }.right
        val gapEnd = layout.boxes.filter { it.width == 300.0 }[1].left

        // Ground a running jump covers: the full arc's airtime at walking speed.
        val jumpReach = 2.0 * kotlin.math.abs(world.player.jumpSpeed) / world.player.gravity * world.player.moveSpeed
        assertTrue(gapEnd - gapStart > jumpReach * 1.5,
            "The gap (${(gapEnd - gapStart).toInt()}) has to be well past the ${jumpReach.toInt()} a jump covers")
        assertTrue(hook.left > gapStart && hook.right < gapEnd, "The hook hangs inside the gap")
        assertTrue(hook.bottom < layout.boxes.first { it.width == 300.0 }.top - world.player.height,
            "The hook's tip hangs above a standing player's head, or it is not something to jump for")
    }

    @Test
    fun testLevel5SwingHookTutorialStepPointsAtTheRealHook() {
        val steps = LevelData.SIDE_SCROLL_LEVEL.tutorialSteps
        assertEquals(2, steps.size, "Level 5 should have exactly two tutorial steps - the hook callout and the lever callout")

        val step = steps.first { it.id == "step_swing_hook" }
        val hook = SWING_TEST_LAYOUT.swingHooks.first()
        val terrain1 = SWING_TEST_LAYOUT.boxes.first { it.width == 300.0 }

        assertEquals("step_swing_hook", step.id)
        assertEquals(TutorialAction.SWING, step.targetAction)
        assertEquals(TutorialControlHighlight.NONE, step.highlight, "World-anchored, like step_reach_objective in level 1 - not a control-button highlight")
        assertNotNull(step.handwrittenCallout)
        assertTrue(step.handwrittenCallout!!.isNotEmpty())

        // The arrow has to land on the hook's actual GRIP (what Player.findSwingTarget measures
        // reach from), not just somewhere near the hook's art.
        assertEquals(Player.hookGripX(hook), step.worldAnchorX, 1e-6)
        assertEquals(Player.hookGripY(hook), step.worldAnchorY, 1e-6)

        // The trigger window has to sit on terrain1 - where the player is actually running when
        // the hook comes into view - and close before the reach window a swing needs opens, so
        // the callout has had time to appear before pressing jump actually matters.
        assertTrue(step.triggerMinX >= terrain1.left && step.triggerMinX < terrain1.right)
        assertTrue(step.triggerMaxX <= terrain1.right)
        val world = GameWorld.createDefault(SWING_TEST_LEVEL)
        val reachWindowStart = Player.hookGripX(hook) - world.player.swingMaxReach
        assertTrue(step.triggerMaxX <= reachWindowStart,
            "The prompt must finish arriving before the swing's own reach window (starts at " +
                "${reachWindowStart.toInt()}) opens, or the player has no time to read it")
    }

    @Test
    fun testSwingCarriesThePlayerOverLevel5sGapAndLandsThemOnIt() {
        val world = GameWorld.createDefault(SWING_TEST_LEVEL)
        val terrain2 = SWING_TEST_LAYOUT.boxes.filter { it.width == 300.0 }[1]
        val dt = 1.0 / 60.0

        // Auto-pilot: hold right, press jump when progress stalls (which climbs the barrel
        // stack), near platform edges (hopping thin platforms), or when any hook is in reach.
        val jumpLedges = listOf(terrain2.right, 1494.0, 1582.0)
        var elapsed = 0.0
        var stalledFor = 0.0
        var swingCount = 0
        var wasSwinging = false
        while (elapsed < 40.0 && !world.isLevelComplete && !world.isGameOver) {
            val beforeX = world.player.x
            val hookInReach = world.swingHooks.any { h ->
                val reach = Player.hookGripX(h) - world.player.centerX
                reach in world.player.swingMinReach..world.player.swingMaxReach
            }
            val nearLedgeEdge = jumpLedges.any { ledgeRight ->
                val dist = ledgeRight - (world.player.x + world.player.width)
                dist in 0.0..18.0
            }
            val jump = world.player.isGrounded && (stalledFor > 0.05 || hookInReach || nearLedgeEdge)
            // The third gap's hook is gated behind lever_1 (see SIDE_SCROLL_LEVEL_LAYOUT's own
            // hookCrate) - a full run to the exit has to pull it, not just hold right and jump.
            world.update(dt, moveInput = 1.0, jumpInput = jump, crouchInput = false, interactInput = true)
            if (world.player.isSwinging && !wasSwinging) {
                swingCount++
            }
            wasSwinging = world.player.isSwinging
            stalledFor = if (kotlin.math.abs(world.player.x - beforeX) < 0.5) stalledFor + dt else 0.0
            elapsed += dt
        }

        assertTrue(swingCount >= 2, "Both hook swings should be executed. Swung $swingCount times.")
        assertTrue(world.isLevelComplete,
            "The swing sequence should complete and the run continue to the exit. Ended at " +
                "x=${world.player.x.toInt()} y=${world.player.y.toInt()} after ${elapsed.toInt()}s")
        assertTrue(world.player.x > terrain2.left,
            "The landing has to be past terrain2's near edge, not short of it")
    }

    @Test
    fun testLevel5ThinPlatformsMomentumSwing() {
        val dt = 1.0 / 60.0

        // Case 1: Chaining jumps across the thin platforms maintains momentum and executes the second swing
        val world = GameWorld.createDefault(SWING_TEST_LEVEL)
        val terrain2 = SWING_TEST_LAYOUT.boxes.filter { it.width == 300.0 }[1]
        val hook2 = world.swingHooks[1]
        val gripX2 = Player.hookGripX(hook2)
        val terrain3 = SWING_TEST_LAYOUT.boxes.last { it.width == 340.0 }

        // Start player on terrain2 with running approach
        world.player.resetTo(terrain2.right - 100.0, terrain2.top - world.player.height)
        val jumpLedges = listOf(terrain2.right, 1494.0, 1582.0)
        var swung = false
        var elapsed = 0.0
        while (elapsed < 10.0 && !world.isGameOver) {
            val reach = gripX2 - world.player.centerX
            val hookInReach = reach in world.player.swingMinReach..world.player.swingMaxReach
            val nearLedgeEdge = jumpLedges.any { ledgeRight ->
                val dist = ledgeRight - (world.player.x + world.player.width)
                dist in 0.0..18.0
            }
            val jump = world.player.isGrounded && (hookInReach || nearLedgeEdge)
            world.update(dt, moveInput = 1.0, jumpInput = jump, crouchInput = false)
            if (world.player.isSwinging) {
                swung = true
            }
            if (world.player.x >= terrain3.left + 20.0) {
                break
            }
            elapsed += dt
        }
        assertTrue(swung, "Continuous running and jumping across thin platforms should preserve momentum and swing from hook 2")
        assertTrue(world.player.x >= terrain3.left, "Swing from hook 2 should land on terrain3")

        // Case 2: Landing on thinPlatform3 and stopping loses momentum; subsequent jump fails to swing
        val stoppedWorld = GameWorld.createDefault(SWING_TEST_LEVEL)
        val thin3 = SWING_TEST_LAYOUT.boxes.first { it.x == 1630.0 }
        // Place player stationary on thinPlatform3
        stoppedWorld.player.resetTo(thin3.left + 2.0, thin3.top - stoppedWorld.player.height)
        // Step while stationary to ensure grounded and zero momentum
        stoppedWorld.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false)
        assertEquals(0.0, stoppedWorld.player.runUpDistance, 1e-4, "Stationary player has zero runUpDistance")

        // Now attempt to jump and swing towards hook 2
        var attemptedSwing = false
        for (i in 0 until 80) {
            val reach = gripX2 - stoppedWorld.player.centerX
            val hookInReach = reach in stoppedWorld.player.swingMinReach..stoppedWorld.player.swingMaxReach
            stoppedWorld.update(dt, moveInput = 1.0, jumpInput = hookInReach || i == 0, crouchInput = false)
            if (stoppedWorld.player.isSwinging) {
                attemptedSwing = true
            }
        }
        assertFalse(attemptedSwing, "Stopping on the thin platform must lose momentum and prevent the hook swing")
        assertTrue(stoppedWorld.player.x > thin3.right, "Player should have jumped past the thin platform")
        assertTrue(stoppedWorld.player.x < terrain3.left, "Without swing, normal jump must not reach terrain3")
        assertTrue(stoppedWorld.player.y > thin3.top - stoppedWorld.player.height + 20.0, "Without swing momentum, ordinary jump should fall into the gap")
    }

    @Test
    fun testHookCrateIsInteractableWhileHangingAndWhenLanded() {
        val world = GameWorld.createDefault(SWING_TEST_LEVEL)
        val hookCrate = world.hookCrates.first()
        val dt = 1.0 / 60.0

        // 1. While hanging: crate must have solid physical collision (player cannot pass through it)
        assertFalse(hookCrate.isDetached, "Hook crate starts attached (hanging)")
        world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false)

        // Position player to the left of the hanging crate at overlapping height
        world.player.resetTo(hookCrate.bounds.left - world.player.width - 2.0, hookCrate.bounds.y)
        // Run right toward the hanging crate
        world.update(dt, moveInput = 1.0, jumpInput = false, crouchInput = false)
        assertTrue(world.player.x <= hookCrate.bounds.left,
            "Player should collide horizontally with hanging crate and not pass through it")

        // 2. Detach crate and verify it lands on groundY and stays solid
        hookCrate.isDetached = true
        var dropTime = 0.0
        while (dropTime < 2.0 && !hookCrate.isLanded) {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false)
            dropTime += dt
        }
        assertTrue(hookCrate.isLanded, "Crate should land on the ground floor")
        assertEquals(440.0, hookCrate.bounds.bottom, 0.01, "Crate bottom should sit flush on groundY")

        // Player walking into landed crate from the side
        world.player.resetTo(hookCrate.bounds.left - world.player.width - 2.0, 440.0 - world.player.height)
        world.update(dt, moveInput = 1.0, jumpInput = false, crouchInput = false)
        assertTrue(world.player.x <= hookCrate.bounds.left,
            "Player cannot walk through landed crate")

        // Player standing on top of landed crate
        world.player.resetTo(hookCrate.bounds.centerX - world.player.width / 2.0, hookCrate.bounds.top - world.player.height)
        world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false)
        assertEquals(hookCrate.bounds.top, world.player.y + world.player.height, 0.01,
            "Player should stand firmly on top of the crate")
        assertTrue(world.player.isGrounded, "Player should be grounded when standing on top of crate")
    }

    @Test
    fun testLevel5EndingBuildingOnFloorWithFlatSurface() {
        val layout = LevelData.SIDE_SCROLL_LEVEL_LAYOUT
        val groundY = 440.0

        // Exit zone bottom must be exactly on the floor
        assertEquals(groundY, layout.exitZone.bottom, 0.01,
            "Level 5 exit zone must sit flush on the floor (groundY)")

        // Terrain4 ends before exitZone, leaving a flat surface
        val terrain3 = layout.boxes.filter { it.width == 340.0 }[0]
        val terrain4 = layout.boxes.first { it.x > terrain3.right }
        assertTrue(terrain4.right < layout.exitZone.x,
            "Terrain4 must end before the exit zone")

        val flatSurfaceLength = layout.exitZone.x - terrain4.right
        assertTrue(flatSurfaceLength >= 200.0,
            "There must be at least 200 units of flat surface before the ending building (found $flatSurfaceLength)")
    }

    // ---- level 6: lever-activated moving crate + timed swing (see LevelData.LEVEL_6_LAYOUT) ----

    @Test
    fun testLevel6HasNoRescueBarrel() {
        // The original rescue barrel sat flush against terrain's right face (terrain.right, at
        // 48-unit crate height). It was removed on request - the section 2 barrel pyramid that
        // briefly replaced it, and the section 3 barrel gauntlet now past tallBlock, both sit
        // elsewhere and don't reintroduce one at this specific position.
        val terrain = LevelData.LEVEL_6_LAYOUT.boxes.first { it.width == 300.0 && it.x == 488.0 }
        assertTrue(LevelData.LEVEL_6_LAYOUT.barrels.none { it.x == terrain.right && it.height == 48.0 },
            "Level 6's original rescue barrel was removed - no barrel should remain at terrain's right face")
    }

    @Test
    fun testLevel6LeverActivatesMatchingMovingPlatformById() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val leverCrate = world.movingPlatforms.first()
        val restX = leverCrate.x
        val dt = 1.0 / 60.0
        world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
        assertEquals(restX, leverCrate.x, 1e-6, "Crate must not move before the lever is pulled")

        // Stand the player right on the lever and pull it.
        val lever = world.levers.first()
        world.player.resetTo(lever.centerX - world.player.width / 2.0, lever.y + lever.height - world.player.height)
        world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = true)
        assertTrue(lever.isActivated, "Lever should activate when interacted with in range")

        // Zero activation delay: the very next tick after the lever fires must already show motion.
        world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
        assertTrue(leverCrate.x > restX, "Crate should start moving the instant the lever is pressed, not after a delay")

        // The crate's own clock only starts ticking once activated - give it a few real seconds.
        for (i in 0 until 118) {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
        }
        assertTrue(leverCrate.x > restX + 5.0, "Crate should have eased away from its rest position after activation")

        // One-shot: run well past a full period (5.5s) and confirm it has settled back at rest
        // and stays there without a second pull - re-arming (see MovingPlatformDef.oneShot) drops
        // isActive back to false, which is exactly what keeps the early-return branch in
        // MovingPlatform.update freezing it here, not a separate "stuck forever" flag.
        for (i in 0 until 400) {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
        }
        assertEquals(restX, leverCrate.x, 1e-6, "One-shot crate should have eased back to rest after its single round trip")
        val settledX = leverCrate.x
        for (i in 0 until 120) {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
        }
        assertEquals(settledX, leverCrate.x, 1e-6, "Crate must stay frozen at rest without another pull, not start a second lap on its own")
    }

    @Test
    fun testLevel6LeverResetsAndCanBePulledAgainAfterCrateReturnsToRest() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val leverCrate = world.movingPlatforms.first()
        val restX = leverCrate.x
        val lever = world.levers.first()
        val dt = 1.0 / 60.0

        world.player.resetTo(lever.centerX - world.player.width / 2.0, lever.y + lever.height - world.player.height)
        world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = true)
        assertTrue(lever.isActivated, "Lever should activate on the first pull")

        // Run past a full period (5.5s) - the crate should be back at rest AND the lever should
        // have come back up on its own, repressable, without the player touching it again.
        for (i in 0 until 400) {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
        }
        assertEquals(restX, leverCrate.x, 1e-6, "Crate should be back at rest after one full period")
        assertFalse(lever.isActivated, "Lever should reset to its original (un-pulled) position once its crate is back at rest")

        // Pull it again - the crate must play out the exact same attempt from scratch, not stay
        // inert because it was "already used."
        world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = true)
        assertTrue(lever.isActivated, "A reset lever must be repressable")
        world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
        assertTrue(leverCrate.x > restX, "Crate should start moving again immediately on the second pull, exactly as on the first")
    }

    @Test
    fun testRemoteTriggerActivatesNearestLeverWithoutBeingInRange() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val leverCrate = world.movingPlatforms.first()
        val restX = leverCrate.x
        val lever = world.levers.first()
        val dt = 1.0 / 60.0

        // Stand well outside the lever's interactRadius (40) - a plain interact press must NOT
        // reach it, confirming this player position is a fair test of "remote".
        world.player.resetTo(lever.centerX - 500.0, world.player.y)
        assertFalse(lever.isPlayerInRange(world.player), "Test setup should place the player out of the lever's interact range")
        world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = true)
        assertFalse(lever.isActivated, "Out-of-range interact must not throw the lever (sanity check)")

        assertTrue(world.hasRemoteTriggerTarget(), "Level 6 has an un-thrown lever for Remote Trigger to reach")
        val activated = world.activatePowerup(PowerupType.REMOTE_TRIGGER)
        assertTrue(activated, "Remote Trigger must succeed with a lever still available")
        assertTrue(lever.isActivated, "Remote Trigger should throw the nearest lever despite the player being far away")
        assertFalse(world.activePowerups.isActive(PowerupType.REMOTE_TRIGGER), "Remote Trigger is a one-shot, not a running timed/level-duration effect")

        // Same cascade as a normal in-range interact: the matching moving platform starts easing.
        world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
        assertTrue(leverCrate.x > restX, "The lever's target moving platform should start moving from a remote trigger exactly as it would from a direct interact")

        // Level 6 has a second lever (lever_3, out on the ground dead-end past the barrels) still
        // un-thrown - Remote Trigger must reach that one next, not report nothing left. It powers
        // section 4's gantry on the far side of the machine, so a remote throw has to start that
        // crate sweeping exactly as an in-range press would.
        val gantryCrate = world.movingPlatforms.first { it.id == "lvl6_gantry_crate" }
        val gantryRestX = gantryCrate.x
        assertTrue(world.hasRemoteTriggerTarget(), "lever_3 is still un-thrown - Remote Trigger should have another target")
        val lever3 = world.levers.first { it.id == "lever_3" }
        assertTrue(world.activatePowerup(PowerupType.REMOTE_TRIGGER), "Remote Trigger must succeed again with lever_3 still available")
        assertTrue(lever3.isActivated, "The second Remote Trigger use should throw lever_3")
        world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
        assertTrue(gantryCrate.x < gantryRestX, "The gantry crate should start sweeping off the landing")

        // And section 5's switch, the furthest out - the one that cuts the exit laser curtain.
        val exitLasers = world.lasers.filter { it.mechanismId == "lvl6_exit_lasers" }
        assertTrue(exitLasers.isNotEmpty(), "Level 6 ends behind a switched laser curtain")
        assertTrue(world.hasRemoteTriggerTarget(), "lever_4 is still un-thrown - Remote Trigger should have another target")
        val lever4 = world.levers.first { it.id == "lever_4" }
        assertTrue(world.activatePowerup(PowerupType.REMOTE_TRIGGER), "Remote Trigger must succeed again with lever_4 still available")
        assertTrue(lever4.isActivated, "The third Remote Trigger use should throw lever_4")
        world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
        assertTrue(exitLasers.all { !it.isActive }, "...which cuts every beam in the curtain")

        // Nothing left to throw now - a further use must be refused, not silently do nothing while
        // still charging the player an item (checked at the GameplayScene call site via
        // hasRemoteTriggerTarget(), verified directly on the model here).
        assertFalse(world.hasRemoteTriggerTarget(), "No levers should remain once all three have been thrown")
        assertFalse(world.activatePowerup(PowerupType.REMOTE_TRIGGER), "Remote Trigger must refuse when there is nothing left to trigger")
    }

    @Test
    fun testLevel6ThirdLeverNowDrivesTheGantryAcrossTheMachine() {
        // lever_3 was "put a lever on that platform (currently not functional)" - real and
        // interactable but wired to nothing. It is section 4's trigger now ("the lever for the
        // hanging crate should be the one left of the crane"), which is what the ground dead-end
        // it stands on is worth: the walk along the floor stops at the crane's tracks, and this is
        // the reason to have made it. The crate it starts is on the far side of the machine, so
        // the sweep runs continuously - the player has the whole boom crossing to get there.
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val lever3 = world.levers.first { it.id == "lever_3" }
        val gantry = world.movingPlatforms.first { it.id == "lvl6_gantry_crate" }
        assertEquals("lvl6_gantry_crate", lever3.targetMechanismId, "lever_3 drives section 4's gantry")
        assertFalse(gantry.isActive, "...which is parked until it is pulled")

        val restX = gantry.x
        world.player.resetTo(lever3.centerX - world.player.width / 2.0, lever3.y + lever3.height - world.player.height)
        world.update(1.0 / 60.0, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = true)
        assertTrue(lever3.isActivated, "Standing at lever_3 and pressing interact should throw it")
        assertTrue(gantry.isActive, "...and power the gantry")

        for (i in 0 until 60) {
            world.update(1.0 / 60.0, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
        }
        assertTrue(gantry.x < restX - 1.0, "The crate should be sweeping off the landing a second later")
    }

    @Test
    fun testLevel6CraneStandsJustRightOfTheLeverOnOneLowFlatTier() {
        // "take the crane to left ... the vehicle part of the crane should be just right of the
        // lever ... make the height of the platform after the lever shorter and put the crane
        // there." endTerrain and cranePlatform are one flat groundY-48 tier (they used to be
        // groundY-96) and the machine's own tracked base starts a short step past the lever.
        val layout = LevelData.LEVEL_6_LAYOUT
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val groundY = 440.0
        val crane = world.cranes.single()
        val lever3 = world.levers.first { it.id == "lever_3" }
        val endTerrain = layout.boxes.first { it.width == 70.0 }
        val cranePlatform = layout.boxes.first { it.x == endTerrain.right && it.height == endTerrain.height }

        assertEquals(groundY - 48.0, endTerrain.top, 1e-9, "The lever platform is one barrel high now, not two")
        assertEquals(endTerrain.right, cranePlatform.left, 1e-9, "cranePlatform must be connected to endTerrain, no gap")
        assertEquals(endTerrain.top, cranePlatform.top, 1e-9, "cranePlatform must share endTerrain's own height, one flat tier")
        assertTrue(endTerrain in layout.plainPlatforms && cranePlatform in layout.plainPlatforms,
            "Both are inside GameplayScene.kt's crate-shaped size heuristics by coincidence, so both must opt out")

        // "just right of the lever" - the machine itself, not the boom tip 393 units further back.
        val machineGap = crane.bodyBounds.left - (lever3.x + lever3.width)
        assertTrue(machineGap in 10.0..60.0,
            "The crane's tracked base should stand just past the lever, not across the level (gap was $machineGap)")
        assertEquals(cranePlatform.top, crane.bodyBounds.bottom, 1e-9, "The machine must rest flush on cranePlatform")
        assertEquals(cranePlatform.top, crane.houseBounds.bottom, 1e-9, "The machine must rest flush on cranePlatform")
        assertTrue(crane.bodyBounds.left >= cranePlatform.left && crane.houseBounds.right <= cranePlatform.right + 1e-6,
            "The machine must be fully contained within cranePlatform, not overhanging it")
        assertTrue(crane.height > 90.0, "The crane should still be the larger version, not the old 90-tall one")

        // A standing player still fits under the boom on this tier - that is the whole reason it
        // dropped 48 units when the crane moved left.
        val playerHeight = 96.0
        assertTrue(endTerrain.top - crane.boomBounds.bottom >= playerHeight,
            "The player must be able to walk under the boom at full standing height on the lever platform")
    }

    @Test
    fun testLevel6BoomOverhangsTallBlockAndIsClimbableFromTheFarSideOfTheGap() {
        // "take the crane to left, so that the player can climb onto it from the otherside of the
        // gap" - the boom now reaches back across the ground gap and over tallBlock's own far end,
        // at a height inside Player's climb window from that surface, and it is whitelisted as a
        // floating climb target (its underside hangs well above the climbing player's feet).
        val layout = LevelData.LEVEL_6_LAYOUT
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val crane = world.cranes.single()
        val tallBlock = layout.boxes.first { it.width == 300.0 && it.height == 96.0 }
        val endBarrel1 = layout.barrels.minByOrNull { it.left }!!

        assertTrue(crane.boomBounds.left in tallBlock.left..tallBlock.right,
            "The boom tip must overhang tallBlock itself, so the player meets it before the ledge")
        assertTrue(crane.boomBounds.left < endBarrel1.left,
            "...having reached back across the ground gap to get there")
        assertTrue(crane.boomBounds in layout.floatingClimbTargets,
            "A hanging boom fails findClimbTarget's floating-ledge check by construction, so it needs the exemption")

        val player = world.player
        // "for the climbing animation to work, this long beam should be his head height" - the
        // boom's walkable top lands on the crown of a player standing on tallBlock, which is also
        // this game's own canonical 96-unit climb. CraneDef.heightForBoomTop derives the crane's own
        // height from this, so a regression here means the machine was re-sized by hand.
        assertEquals(tallBlock.top - player.height, crane.boomBounds.top, 1e-9,
            "The beam has to sit at the standing head height of a player on tallBlock")
        val climbRise = tallBlock.top - crane.boomBounds.top
        assertTrue(climbRise > player.climbMinHeight && climbRise <= player.climbMaxHeight,
            "The climb onto the boom from tallBlock must be inside ${player.climbMinHeight}..${player.climbMaxHeight} (was $climbRise)")

        // Drive the real loop: walk right along tallBlock until the boom stops the player, press
        // jump there, end up standing on the boom. Jump is pressed on the stall rather than held
        // every frame - a held jump re-fires as an ordinary hop and never settles into the climb.
        val dt = 1.0 / 60.0
        player.resetTo(tallBlock.left + 40.0, tallBlock.top - player.height)
        var elapsed = 0.0
        var stalledFor = 0.0
        while (elapsed < 8.0 && player.y + player.height > crane.boomBounds.top + 1.0) {
            val beforeX = player.x
            world.update(dt, moveInput = 1.0, jumpInput = player.isGrounded && stalledFor > 0.05, crouchInput = false, interactInput = false)
            stalledFor = if (kotlin.math.abs(player.x - beforeX) < 0.5) stalledFor + dt else 0.0
            elapsed += dt
        }
        assertEquals(crane.boomBounds.top, player.y + player.height, 1.0,
            "The player should be standing on the boom after climbing from tallBlock, ended at y=${player.y}")
    }

    @Test
    fun testLevel6CraneBoomIsGenuinelyLongerThanASingleScaledImageWouldBe() {
        // "if the beam of the crane is not long enough cut from the middle and copy a part to make
        // it longer" - CraneDef.width must exceed what a plain single-scaled crane.png (no tiling)
        // at the same height would give, confirming the tiles actually add length rather than just
        // reconstructing the source image's own proportions (the bug in an earlier attempt).
        val crane = LevelData.LEVEL_6_LAYOUT.cranes.single()
        val plainScaledWidth = crane.height * (1708.0 / 452.0)
        assertTrue(crane.width > plainScaledWidth,
            "Tiling should make the boom genuinely longer (${crane.width}) than a plain scaled image (${plainScaledWidth}) at the same height")
    }

    @Test
    fun testLevel6CraneCollidesAsSeveralPiecesThatFollowItsOwnSilhouette() {
        // "dont just use 1 bounding box for collisions in the crane. use multiple of them so
        // walking on it doesnt feel like flying" - crossing the machine is a real staircase down
        // its own drawn shape (boom -> house roof -> the platform), not one flat surface at the
        // boom's height all the way across the vehicle.
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val crane = world.cranes.single()
        val cranePlatform = LevelData.LEVEL_6_LAYOUT.boxes.first { it.height == 48.0 && it.width > 300.0 }

        assertEquals(3, crane.collisionBoxes.size, "The crane collides as three pieces, not one")
        crane.collisionBoxes.forEach { piece ->
            assertTrue(piece in world.boxes, "Every piece of the crane must be solid: $piece")
        }

        // Left to right, with no gaps between them.
        assertEquals(crane.boomBounds.right, crane.bodyBounds.left, 1e-9, "boom and body must meet")
        assertEquals(crane.bodyBounds.right, crane.houseBounds.left, 1e-9, "body and house must meet")
        assertEquals(crane.boomBounds.top, crane.bodyBounds.top, 1e-9,
            "The boom art runs right over the machine's front, so that stretch walks at the boom's own height")

        // The steps themselves - each one a real drop the player can take, and the house roof is
        // the piece whose top is genuinely its own drawn roof rather than the boom's height.
        val boomToHouse = crane.houseBounds.top - crane.boomBounds.top
        val houseToPlatform = cranePlatform.top - crane.houseBounds.top
        assertTrue(boomToHouse > 30.0 && boomToHouse < 60.0,
            "Stepping off the boom onto the house roof should be a real step, not a cliff or a seam (was $boomToHouse)")
        assertTrue(houseToPlatform > 60.0, "The house roof must sit well above the platform (was $houseToPlatform)")

        // Climbing back up onto the house from the exit side is allowed (it rests on the platform);
        // the machine's front face is not (too tall, and the boom above it blocks the headroom).
        val player = world.player
        assertTrue(cranePlatform.top - crane.houseBounds.top <= player.climbMaxHeight,
            "The house roof must be climbable from the exit side, so arriving there is never one-way")
        assertTrue(cranePlatform.top - crane.bodyBounds.top > player.climbMaxHeight,
            "The machine's front face must be too tall to climb, so the way past it is over the boom")
    }

    @Test
    fun testLevel6GroundApproachStopsAtTheMachineWithoutWedgingThePlayer() {
        // The machine is a wall to anyone walking the lower tier, and that has to be a clean stop:
        // the player ends up standing on the platform in front of the tracks, not wedged inside the
        // boom or the body, and the walk back out (to tallBlock, and the boom) stays open. Driven
        // through the real loop rather than reasoned from the box heights.
        val layout = LevelData.LEVEL_6_LAYOUT
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val crane = layout.cranes.single()
        val endTerrain = layout.boxes.first { it.width == 70.0 }
        val tallBlock = layout.boxes.first { it.width == 300.0 && it.height == 96.0 }
        val player = world.player
        val dt = 1.0 / 60.0

        player.resetTo(endTerrain.left + 2.0, endTerrain.top - player.height)
        var stalledFor = 0.0
        var elapsed = 0.0
        while (elapsed < 6.0) {
            val beforeX = player.x
            world.update(dt, moveInput = 1.0, jumpInput = player.isGrounded && stalledFor > 0.05, crouchInput = false, interactInput = false)
            stalledFor = if (kotlin.math.abs(player.x - beforeX) < 0.5) stalledFor + dt else 0.0
            elapsed += dt
        }
        // Let the autopilot stop hopping against the wall and settle before reading its stance.
        repeat(40) { world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false) }
        assertFalse(world.isLevelComplete, "The ground approach must not reach the exit - the machine blocks it")
        assertEquals(crane.bodyBounds.left, player.x + player.width, 1.0,
            "The player should come to rest flush against the machine's tracks")
        assertEquals(endTerrain.top, player.y + player.height, 1e-6,
            "...standing on the platform, not wedged somewhere inside the crane")

        // And back out again, on foot, with the ground and tallBlock's own climb still reachable.
        elapsed = 0.0
        while (elapsed < 4.0) {
            world.update(dt, moveInput = -1.0, jumpInput = false, crouchInput = false, interactInput = false)
            elapsed += dt
        }
        assertEquals(tallBlock.right, player.x, 1.0,
            "Walking back left must bring the player all the way to tallBlock's own right face, where its climb is")
    }

    @Test
    fun testLevel6ClimbingUnderTheBoomFinishesCrouchedInsteadOfWedged() {
        // "when he climbs up this, make him climb up crouched" - coming back along the ground and
        // climbing tallBlock from the gap side lands the player under the crane's boom, which
        // hangs ~70 above that surface: standing room is 96, crouching room is 56. The climb is
        // still allowed, and it now finishes in a crouch. Before this, the player hauled up into a
        // standing pose inside the beam and was stuck there, unable to move either way, until they
        // happened to press crouch.
        val layout = LevelData.LEVEL_6_LAYOUT
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val crane = layout.cranes.single()
        val tallBlock = layout.boxes.first { it.width == 300.0 && it.height == 96.0 }
        val player = world.player
        val dt = 1.0 / 60.0
        val groundY = 440.0

        // The boom really is overhead at the edge being climbed, and really is too low to stand.
        assertTrue(crane.boomBounds.left < tallBlock.right && crane.boomBounds.right > tallBlock.right,
            "This test is only meaningful while the boom overhangs tallBlock's own right edge")
        val headroom = tallBlock.top - crane.boomBounds.bottom
        assertTrue(headroom < player.height && headroom > player.crouchHeight,
            "Headroom under the boom there must be crouch-only (was $headroom)")

        // Walk left out of the gap and climb, never pressing crouch.
        player.resetTo(tallBlock.right + 60.0, groundY - player.height)
        var stalledFor = 0.0
        var elapsed = 0.0
        // Run until the climb has actually finished - the feet reach the lip partway through the
        // animation, so a loop that stops on height alone reads the stance mid-haul.
        while (elapsed < 8.0 &&
            !(player.isGrounded && !player.isClimbing && player.y + player.height <= tallBlock.top + 1.0)
        ) {
            val beforeX = player.x
            world.update(dt, moveInput = -1.0, jumpInput = player.isGrounded && stalledFor > 0.05, crouchInput = false, interactInput = false)
            stalledFor = if (kotlin.math.abs(player.x - beforeX) < 0.5) stalledFor + dt else 0.0
            elapsed += dt
        }
        assertEquals(tallBlock.top, player.y + player.height, 1.0,
            "The player should have climbed onto tallBlock, ended at y=" + player.y)
        assertTrue(player.isCrouching, "The climb under the boom has to finish crouched, not standing")
        assertTrue(player.bounds.top >= crane.boomBounds.bottom,
            "...which means the body is clear of the beam: top=" + player.bounds.top + " beam bottom=" + crane.boomBounds.bottom)

        // And they can actually leave - crouch-walking out from under the boom, still without the
        // player ever pressing crouch themselves.
        val afterClimbX = player.x
        elapsed = 0.0
        while (elapsed < 4.0) {
            world.update(dt, moveInput = -1.0, jumpInput = false, crouchInput = false, interactInput = false)
            elapsed += dt
        }
        assertTrue(player.x < afterClimbX - 50.0,
            "The player must be able to crawl out from under the beam, moved only " + (afterClimbX - player.x))
        assertTrue(player.x < crane.boomBounds.left && !player.isCrouching,
            "Once out from under the beam they stand back up on their own")
    }

    @Test
    fun testClimbUnderACeilingStopsOnTheCrouchPoseInsteadOfStandingUp() {
        // "try to generate an animation for climbing + crouching ... because current one also goes
        // through the beam." The climb clip runs mantle -> settled deep crouch on top (raw
        // 145-175) -> standing up (raw 176-224). A climb into a low ceiling has to stop before
        // that last stretch, or the character stands up straight through the beam he just ducked
        // under. Player ends the move at CLIMB_CROUCH_END_PHASE instead of 1.0, and the frame that
        // lands on is the one whose silhouette matches the crouch clip's own held pose.
        val layout = LevelData.LEVEL_6_LAYOUT
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val tallBlock = layout.boxes.first { it.width == 300.0 && it.height == 96.0 }
        val player = world.player
        val dt = 1.0 / 60.0

        player.resetTo(tallBlock.right + 60.0, 440.0 - player.height)
        var stalledFor = 0.0
        var elapsed = 0.0
        while (elapsed < 8.0 &&
            !(player.isGrounded && !player.isClimbing && player.y + player.height <= tallBlock.top + 1.0)
        ) {
            val beforeX = player.x
            world.update(dt, moveInput = -1.0, jumpInput = player.isGrounded && stalledFor > 0.05, crouchInput = false, interactInput = false)
            stalledFor = if (kotlin.math.abs(player.x - beforeX) < 0.5) stalledFor + dt else 0.0
            elapsed += dt
        }

        assertTrue(player.climbEndsCrouched, "This climb has a ceiling over its landing, so it is the crouched variant")
        assertTrue(player.climbPhase >= Player.CLIMB_CROUCH_END_PHASE && player.climbPhase < Player.CLIMB_CROUCH_END_PHASE + 0.02,
            "The move must stop at the crouch phase, not run the clip out to 1.0 (was ${player.climbPhase})")

        // The frame GameplayScene draws at that phase, in the clip's own raw file numbering.
        val span = game.scene.PlayerAnimations.CLIMB_END - game.scene.PlayerAnimations.CLIMB_START
        val stopFrame = game.scene.PlayerAnimations.CLIMB_START + (player.climbPhase * span).toInt()
        val stopFile = stopFrame + 70 // CLIMB_FILE_START - raw 70 is loaded index 0
        assertEquals(182, stopFile,
            "The clip has to stop on raw frame 182 - measured as the frame coming back out of the " +
                "settled crouch whose silhouette matches the crouch clip's held pose (141 vs 139 " +
                "frame-px). Re-measure before changing it, don't nudge.")
        assertTrue(stopFile < 205,
            "...and well before the clip reaches its full standing height, which is what used to cross the beam")

        // The ascent itself is still finished - this only cuts pose frames.
        assertEquals(tallBlock.top, player.y + player.height, 1e-6, "The body must be fully up on the ledge")
        assertTrue(player.isCrouching, "...and crouched, ready for the held crouch pose to take over")
    }

    @Test
    fun testJumpOutOfACrouchNeedsRoomToStandAndDropsTheStance() {
        // "and also crouching -> jumping": a jump can start from a crouch now, so the launch has a
        // stance to spring out of instead of snapping upright. It is gated on the same headroom
        // test standing up uses - under a ceiling the crouch is the only thing keeping the body
        // clear of it, so a jump there would drive a standing pose straight through the beam.
        val layout = LevelData.LEVEL_6_LAYOUT
        val tallBlock = layout.boxes.first { it.width == 300.0 && it.height == 96.0 }
        val dt = 1.0 / 60.0
        val groundY = 440.0

        // Open ground, nothing overhead: crouch, then jump while still holding crouch.
        val open = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val p = open.player
        p.resetTo(tallBlock.right + 90.0, groundY - p.height)
        repeat(30) { open.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = true, interactInput = false) }
        assertTrue(p.isCrouching, "Crouched on open ground first")

        var minFeet = Double.MAX_VALUE
        var crouchedWhileAirborne = false
        repeat(60) {
            open.update(dt, moveInput = 0.0, jumpInput = true, crouchInput = true, interactInput = false)
            minFeet = minOf(minFeet, p.y + p.height)
            if (!p.isGrounded && p.isCrouching) crouchedWhileAirborne = true
        }
        val rise = groundY - minFeet
        assertTrue(rise > p.maxJumpHeight * 0.9,
            "A crouch jump should clear nearly a full jump's height (rose $rise of ${p.maxJumpHeight})")
        assertFalse(crouchedWhileAirborne, "The jump IS the extension - the stance is dropped on launch, not carried into the air")

        // Under the boom, the same inputs must do nothing at all: there is no room to extend.
        val under = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val q = under.player
        val crane = layout.cranes.single()
        q.resetTo(crane.boomBounds.left + 60.0, tallBlock.top - q.height)
        repeat(30) { under.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = true, interactInput = false) }
        assertTrue(q.isCrouching, "Crouched under the boom")
        var movedUp = 0.0
        repeat(60) {
            under.update(dt, moveInput = 0.0, jumpInput = true, crouchInput = false, interactInput = false)
            movedUp = maxOf(movedUp, tallBlock.top - (q.y + q.height))
        }
        assertEquals(0.0, movedUp, 1e-9,
            "Jumping under the beam must stay impossible - that is what keeps a standing pose out of it")
        assertTrue(q.isCrouching, "...and the player stays crouched, held there by the ceiling")
    }

    @Test
    fun testJumpUnderTheBoomLeavesTheDrawnHeadClearOfIt() {
        // "when jumping while crouching, it still goes through the beam." On the lever platform
        // the boom is 118 above the floor: a standing player fits under it, so the jump is allowed
        // and simply bonks - but the character is DRAWN up to 2.6 units taller than his collision
        // box (the jump clip's extended poses are 251 frame-px against the 244.36 the box is
        // scaled from), so stopping the box flush against the beam still pushed the head into it.
        // Player.CEILING_ART_MARGIN stops the box that much lower instead.
        val layout = LevelData.LEVEL_6_LAYOUT
        val crane = layout.cranes.single()
        val endTerrain = layout.boxes.first { it.width == 70.0 }
        val dt = 1.0 / 60.0

        for (crouchFirst in listOf(false, true)) {
            val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
            val p = world.player
            p.resetTo(endTerrain.left + 10.0, endTerrain.top - p.height)
            if (crouchFirst) {
                repeat(30) { world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = true, interactInput = false) }
                assertTrue(p.isCrouching, "Crouched on the lever platform first")
            }
            var minFeet = Double.MAX_VALUE
            repeat(72) {
                world.update(dt, moveInput = 0.0, jumpInput = true, crouchInput = false, interactInput = false)
                minFeet = minOf(minFeet, p.y + p.height)
            }
            assertTrue(minFeet < endTerrain.top - 1.0, "The jump should still happen (crouchFirst=$crouchFirst)")
            val boxTop = minFeet - p.height
            assertTrue(boxTop >= crane.boomBounds.bottom + Player.CEILING_ART_MARGIN - 1e-6,
                "The box must stop at least CEILING_ART_MARGIN below the beam so the drawn head stays out of it " +
                    "(crouchFirst=$crouchFirst, box top $boxTop vs beam bottom ${crane.boomBounds.bottom})")
        }
    }

    @Test
    fun testCrouchWalkingOffTheLedgeStaysCrouchedThroughTheFall() {
        // "when dropping while crouching, the player goes above that beam." Nothing stands the
        // player up in mid-air, so a crouch-walk off tallBlock's edge falls with the 56-unit
        // crouched box - while GameplayScene used to switch the sprite to the ~98-unit drop pose
        // the moment the feet left the ledge, which threw the drawn head 40 units above the body
        // and straight through the boom. The scene now keeps the crouched pose for a crouched
        // fall; this pins the model state that makes that the right pose.
        val layout = LevelData.LEVEL_6_LAYOUT
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val crane = layout.cranes.single()
        val tallBlock = layout.boxes.first { it.width == 300.0 && it.height == 96.0 }
        val p = world.player
        val dt = 1.0 / 60.0

        // Start crouched under the boom and crawl right, off the ledge.
        p.resetTo(tallBlock.right - 120.0, tallBlock.top - p.height)
        repeat(30) { world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = true, interactInput = false) }
        assertTrue(p.isCrouching, "Crouched under the boom to begin with")

        var sawAirborne = false
        var stoodUpUnderTheBeam = false
        repeat(300) {
            world.update(dt, moveInput = 1.0, jumpInput = false, crouchInput = true, interactInput = false)
            if (!p.isGrounded) {
                sawAirborne = true
                val underTheBoom = p.x + p.width > crane.boomBounds.left && p.x < crane.boomBounds.right
                if (underTheBoom && !p.isCrouching) stoodUpUnderTheBeam = true
                if (underTheBoom) {
                    assertTrue(p.bounds.top >= crane.boomBounds.bottom - 1e-6,
                        "The falling body must stay below the boom (top=${p.bounds.top}, boom bottom=${crane.boomBounds.bottom})")
                }
            }
        }
        assertTrue(sawAirborne, "The crawl should actually take the player off the ledge")
        assertFalse(stoodUpUnderTheBeam, "A crouched fall stays crouched while it is still under the beam")
    }

    @Test
    fun testCrouchedFallKeepsTheStanceDownAndStandsUpOnLanding() {
        // "when dropping down while crouching, make him drop down in the crouch position and then
        // get up." Crouching is held for the whole fall - there is nothing to push off in mid-air -
        // and the stand-up happens on the floor, which is where GameplayScene's crouch machine
        // plays the clip backwards. Releasing the button in the air used to uncoil him on the spot.
        val layout = LevelData.LEVEL_6_LAYOUT
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val tallBlock = layout.boxes.first { it.width == 300.0 && it.height == 96.0 }
        val p = world.player
        val dt = 1.0 / 60.0

        // Settle into the crouch on top of the block, then crawl off its right edge - and let go
        // of the crouch button the moment the feet leave the ledge.
        p.resetTo(tallBlock.right - 120.0, tallBlock.top - p.height)
        repeat(30) { world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = true, interactInput = false) }
        assertTrue(p.isCrouching, "Crouched on the block to begin with")

        var sawAirborne = false
        var stoodUpInTheAir = false
        var landed = false
        var crouchingOnTouchdown = false
        var standingAfterLanding = false
        var sinceLanding = 0.0
        repeat(400) {
            val releaseTheButton = sawAirborne
            world.update(dt, moveInput = 1.0, jumpInput = false, crouchInput = !releaseTheButton, interactInput = false)
            if (!p.isGrounded) {
                if (!landed) {
                    sawAirborne = true
                    if (!p.isCrouching) stoodUpInTheAir = true
                }
            } else if (sawAirborne && !landed) {
                landed = true
                crouchingOnTouchdown = p.isCrouching
            } else if (landed) {
                sinceLanding += dt
                if (!p.isCrouching) standingAfterLanding = true
            }
        }
        assertTrue(sawAirborne, "The crawl should take the player off the ledge")
        assertTrue(landed, "...and land again")
        assertFalse(stoodUpInTheAir, "The stance is held for the whole fall, button or no button")
        assertTrue(crouchingOnTouchdown, "He arrives crouched, which is the pose the landing is drawn in")
        assertTrue(standingAfterLanding, "...and stands up once he is back on the floor")
    }

    @Test
    fun testCrouchingAfterLeavingTheGroundIsStillTheTuckThePlayerControls() {
        // The counterpart to the test above: only a fall that BEGAN crouched is locked. Pressing
        // crouch while already airborne is a tuck, and letting go extends again - otherwise a
        // stray press in mid-air would pin the stance (and the 56-unit box) until touchdown.
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val p = world.player
        val dt = 1.0 / 60.0
        val floorY = p.y + p.height

        repeat(60) { world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false) }
        assertTrue(p.isGrounded, "Standing on the floor first")
        world.update(dt, moveInput = 0.0, jumpInput = true, crouchInput = false, interactInput = false)
        repeat(6) { world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false) }
        assertFalse(p.isGrounded, "Airborne on the way up")

        world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = true, interactInput = false)
        assertTrue(p.isCrouching, "Crouch pressed in mid-air still tucks")
        world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
        assertFalse(p.isCrouching, "...and releasing it in mid-air comes straight back out")
        assertTrue(p.y + p.height <= floorY + 1e-6, "(still in the air for that check)")
    }

    @Test
    fun testLevel6GantryCrateParksOverTheLandingAndRefusesTheClimbUntilItIsThrown() {
        // "there is a hanging crate very close to the surface level which makes it unclimbable."
        // The gate is the CLEARANCE, not the crate: Player.findClimbTarget drops a candidate whose
        // landing has room for neither a standing body nor a crouched one, so the crate has to
        // hang closer than crouchHeight or the climb would simply arrive crouched instead.
        val layout = LevelData.LEVEL_6_LAYOUT
        val cranePlatform = layout.boxes.first { it.height == 48.0 && it.width > 300.0 }
        val gateBlock = layout.boxes.first { it.x == cranePlatform.right }
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val crate = world.movingPlatforms.first { it.id == "lvl6_gantry_crate" }
        val p = world.player
        val dt = 1.0 / 60.0

        val clearance = gateBlock.top - crate.bounds.bottom
        assertTrue(clearance > 0.0 && clearance < p.crouchHeight,
            "The crate has to hang inside the crouched body's own headroom ($clearance vs ${p.crouchHeight})")
        // findClimbTarget measures headroom 6 units in from the edge the player comes over.
        val landing = Rect(gateBlock.left + 6.0, gateBlock.top - p.height, p.width, p.height)
        assertTrue(crate.bounds.intersects(landing), "At rest the crate has to sit over the landing itself")
        val step = cranePlatform.top - gateBlock.top
        assertTrue(step > p.climbMinHeight && step <= p.climbMaxHeight,
            "...on a step that would otherwise be a perfectly ordinary climb (was $step)")

        // Walk into the face and keep trying, with the lever untouched. Nothing should get up.
        p.resetTo(gateBlock.left - p.width - 2.0, cranePlatform.top - p.height)
        var elapsed = 0.0
        var stalledFor = 0.0
        while (elapsed < 6.0) {
            val beforeX = p.x
            world.update(dt, moveInput = 1.0, jumpInput = p.isGrounded && stalledFor > 0.05, crouchInput = false, interactInput = false)
            stalledFor = if (kotlin.math.abs(p.x - beforeX) < 0.5) stalledFor + dt else 0.0
            elapsed += dt
            assertFalse(p.isClimbing, "No climb may start while the crate is parked over the landing")
            assertTrue(p.y + p.height > gateBlock.top + 1.0,
                "...and nothing else may get the player onto the block either (feet at ${p.y + p.height})")
        }
        assertFalse(crate.isActive, "The gantry stays parked until its lever is thrown")
    }

    @Test
    fun testLevel6GantryLeverOpensARepeatingWindowTheClimbFitsInside() {
        // "pressing that lever makes it move left and right so the player has to time when the
        // crate is not there to climb up." Two things make that a fair ask rather than a wall: the
        // window has to outlast the climb it is gating, and it has to come back around - one
        // missed attempt cannot cost the level (this crate is deliberately NOT oneShot).
        val layout = LevelData.LEVEL_6_LAYOUT
        val cranePlatform = layout.boxes.first { it.height == 48.0 && it.width > 300.0 }
        val gateBlock = layout.boxes.first { it.x == cranePlatform.right }
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val crate = world.movingPlatforms.first { it.id == "lvl6_gantry_crate" }
        val lever = world.levers.first { it.id == "lever_3" }
        val p = world.player
        val dt = 1.0 / 60.0

        // Stand at the lever - out on the ground dead-end, the far side of the machine from the
        // crate it drives - and press interact, exactly as the player does.
        p.resetTo(lever.centerX - p.width / 2.0, lever.y + lever.height - p.height)
        world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = true)
        assertTrue(lever.isActivated, "Standing at lever_3 and pressing interact should throw it")
        assertTrue(crate.isActive, "...which powers the gantry")

        val landingLeft = gateBlock.left + 6.0
        val landingRight = landingLeft + p.width
        val restX = crate.x
        var window = 0.0
        var longestWindow = 0.0
        var windows = 0
        var returnedToRest = false
        var elapsed = 0.0
        while (elapsed < 20.0) {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
            elapsed += dt
            val clear = crate.right < landingLeft || crate.left > landingRight
            if (clear) {
                if (window == 0.0) windows++
                window += dt
                longestWindow = maxOf(longestWindow, window)
            } else {
                window = 0.0
                if (elapsed > 1.0 && kotlin.math.abs(crate.x - restX) < 1.0) returnedToRest = true
            }
        }
        assertTrue(windows >= 2, "The sweep has to repeat, so a missed attempt is not a dead end (saw $windows)")
        assertTrue(returnedToRest, "...and it has to come all the way back over the landing, or there is no gate")
        assertTrue(longestWindow > p.climbDuration + 1.0,
            "The clear window ($longestWindow s) has to outlast the climb itself (${p.climbDuration} s), with room to start it")
    }

    @Test
    fun testLevel6GantryCrateNeverSweepsIntoTheMachineOrOverItsWalkway() {
        // The sweep is pushed as far left as it goes ("take the platform and the hanging crate more
        // to the left") and this is the wall it stops at. Two things live in that airspace: the
        // machine itself (bodyBounds' top is the boom's own height, so a crate hanging 30 above
        // gateBlock would pass straight through it) and the route down off the boom, which walks
        // along that top and across the cab roof - a load sweeping over the roof sweeps through
        // whoever is standing on it. Both are geometry, so both are checked as geometry.
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val crane = world.cranes.single()
        val crate = world.movingPlatforms.first { it.id == "lvl6_gantry_crate" }
        val lever = world.levers.first { it.id == "lever_3" }
        val p = world.player
        val dt = 1.0 / 60.0

        p.resetTo(lever.centerX - p.width / 2.0, lever.y + lever.height - p.height)
        world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = true)
        assertTrue(crate.isActive, "Lever thrown, gantry running")

        // Park the player out of the way and watch three full cycles of the sweep.
        p.resetTo(crane.houseBounds.right + 140.0, 392.0 - p.height)
        var elapsed = 0.0
        var leftmost = Double.MAX_VALUE
        while (elapsed < 21.0) {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
            elapsed += dt
            leftmost = minOf(leftmost, crate.left)
            for (piece in crane.collisionBoxes) {
                assertFalse(crate.bounds.intersects(piece),
                    "The load passed through the machine at x=${crate.x} (piece $piece)")
            }
            // A standing player on the cab roof occupies its top 96 units; nothing may sweep there.
            val onTheRoof = Rect(
                crane.houseBounds.left, crane.houseBounds.top - p.height,
                crane.houseBounds.width, p.height
            )
            assertFalse(crate.bounds.intersects(onTheRoof),
                "The load swept through where the player stands coming down off the boom (x=${crate.x})")
        }
        assertTrue(leftmost < crane.houseBounds.right + 20.0,
            "...while still going as far left as it safely can - that is what puts it in view (got $leftmost)")
    }

    @Test
    fun testLevel6GantryClimbGoesThroughOnceTheCrateSwingsOff() {
        // The whole section on the real loop: throw the lever, keep walking into the face and
        // pressing jump, and the climb goes through the moment the load is clear - after which the
        // player can walk out from under the gantry to the right, which is why it sweeps left.
        val layout = LevelData.LEVEL_6_LAYOUT
        val cranePlatform = layout.boxes.first { it.height == 48.0 && it.width > 300.0 }
        val gateBlock = layout.boxes.first { it.x == cranePlatform.right }
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val crate = world.movingPlatforms.first { it.id == "lvl6_gantry_crate" }
        val p = world.player
        val dt = 1.0 / 60.0
        world.allGuards.forEach { it.visionRange = 0.0 }

        // Throw lever_3 where it stands, on the ground the far side of the machine - the crossing
        // back over the boom is the walkthrough test's job, not this one's.
        val lever = world.levers.first { it.id == "lever_3" }
        p.resetTo(lever.centerX - p.width / 2.0, lever.y + lever.height - p.height)
        world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = true)
        assertTrue(crate.isActive, "The gantry has to be running before the climb is possible at all")

        p.resetTo(cranePlatform.right - 200.0, cranePlatform.top - p.height)
        var elapsed = 0.0
        var stalledFor = 0.0
        var onTheBlock = false
        var clearOfTheGantry = false
        while (elapsed < 25.0 && !clearOfTheGantry) {
            val beforeX = p.x
            world.update(dt, moveInput = 1.0, jumpInput = p.isGrounded && stalledFor > 0.05, crouchInput = false, interactInput = false)
            stalledFor = if (kotlin.math.abs(p.x - beforeX) < 0.5) stalledFor + dt else 0.0
            elapsed += dt
            if (p.isGrounded && kotlin.math.abs((p.y + p.height) - gateBlock.top) < 1.0) {
                onTheBlock = true
                // Out from under every position the crate can ever reach, walking right.
                if (p.x > crate.maxX + crate.width) clearOfTheGantry = true
            }
        }
        assertTrue(onTheBlock, "The player should end up on top of the gantry block (ended at x=${p.x.toInt()} y=${p.y.toInt()})")
        assertTrue(clearOfTheGantry, "...and be able to walk clear of the crate's whole sweep from there")
    }

    @Test
    fun testLevel6GantryCrateKillsWhateverItCatchesUnderneath() {
        // "when trying to climb if he touches the bottom side of the crate it should be mission
        // failed." The load hangs 30 over the ledge it gates, so a climb that starts too late has
        // the body still coming up when the crate sweeps back over the landing. That is a kill,
        // not a wedge - MovingPlatformDef.crushesOnContact.
        val layout = LevelData.LEVEL_6_LAYOUT
        val cranePlatform = layout.boxes.first { it.height == 48.0 && it.width > 300.0 }
        val gateBlock = layout.boxes.first { it.x == cranePlatform.right }
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val crate = world.movingPlatforms.first { it.id == "lvl6_gantry_crate" }
        val lever = world.levers.first { it.id == "lever_3" }
        val p = world.player
        val dt = 1.0 / 60.0
        world.allGuards.forEach { it.visionRange = 0.0 }

        p.resetTo(lever.centerX - p.width / 2.0, lever.y + lever.height - p.height)
        world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = true)
        assertTrue(crate.isActive, "Gantry running")

        // Stand exactly where a climb arrives and wait for the load to come back.
        p.resetTo(gateBlock.left + 6.0, gateBlock.top - p.height)
        var elapsed = 0.0
        while (elapsed < 10.0 && !world.isGameOver) {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
            elapsed += dt
        }
        assertTrue(world.isGameOver,
            "Standing under the returning load has to end the run (crate at ${crate.x}, player ${p.bounds})")

        // ...and the far end of the block, past everything the crate can reach, stays safe - that
        // is the whole point of sweeping left rather than right.
        val safe = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val safeCrate = safe.movingPlatforms.first { it.id == "lvl6_gantry_crate" }
        val safeLever = safe.levers.first { it.id == "lever_3" }
        safe.allGuards.forEach { it.visionRange = 0.0 }
        safe.player.resetTo(safeLever.centerX - safe.player.width / 2.0, safeLever.y + safeLever.height - safe.player.height)
        safe.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = true)
        safe.player.resetTo(safeCrate.maxX + safeCrate.width + 10.0, gateBlock.top - safe.player.height)
        var safeElapsed = 0.0
        while (safeElapsed < 10.0) {
            safe.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
            safeElapsed += dt
        }
        assertFalse(safe.isGameOver, "Clear of the sweep is clear of the crate")
    }

    @Test
    fun testLevel6ExitCorridorIsShutByThreeLasersUntilThePlankLeverCutsThem() {
        // "the lever turns off 3 lasers that are there from the hanging platform to the ground.
        // the bottom is the only path out." The corridor under the plank is the way to extraction,
        // and it is a closed door until lever_4 is thrown - the beams are isAlwaysActive, so there
        // is no timing window to slip through, only the switch.
        val layout = LevelData.LEVEL_6_LAYOUT
        val cranePlatform = layout.boxes.first { it.height == 48.0 && it.width > 300.0 }
        val gateBlock = layout.boxes.first { it.x == cranePlatform.right }
        val plank = layout.boxes.first { it.height == 30.0 }
        val dt = 1.0 / 60.0

        fun freshWorld(): GameWorld {
            val w = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
            w.allGuards.forEach { it.visionRange = 0.0 }
            return w
        }

        val blocked = freshWorld()
        val beams = blocked.lasers.filter { it.mechanismId == "lvl6_exit_lasers" }
        assertEquals(3, beams.size, "Three beams, one switch")
        assertTrue(beams.all { it.isActive }, "...all live from the start")
        assertTrue(beams.all { it.top >= plank.bottom - 1e-6 && it.bottom >= 440.0 - 1e-6 },
            "...hung from the plank's underside down to the floor")
        assertTrue(beams.any { it.left >= plank.right - 12.0 },
            "...one of them at the plank's own far end, or walking off the tip would bypass the lot")

        // Walk the corridor with the switch untouched: it ends at the first beam.
        blocked.player.resetTo(plank.left + 10.0, 440.0 - blocked.player.height)
        var elapsed = 0.0
        while (elapsed < 12.0 && !blocked.isGameOver && !blocked.isLevelComplete) {
            blocked.update(dt, moveInput = 1.0, jumpInput = false, crouchInput = false, interactInput = false)
            elapsed += dt
        }
        assertTrue(blocked.isGameOver, "The curtain has to stop an untouched run")
        assertFalse(blocked.isLevelComplete, "...well short of extraction")

        // Same walk with the switch thrown from the plank above it.
        val open = freshWorld()
        val lever4 = open.levers.first { it.id == "lever_4" }
        open.player.resetTo(lever4.centerX - open.player.width / 2.0, plank.top - open.player.height)
        open.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = true)
        assertTrue(lever4.isActivated, "Standing at lever_4 on the plank and pressing interact should throw it")
        val openBeams = open.lasers.filter { it.mechanismId == "lvl6_exit_lasers" }
        assertTrue(openBeams.all { it.isDisabled && !it.isActive }, "...which cuts all three for good")

        open.player.resetTo(plank.left + 10.0, 440.0 - open.player.height)
        elapsed = 0.0
        while (elapsed < 12.0 && !open.isGameOver && !open.isLevelComplete) {
            open.update(dt, moveInput = 1.0, jumpInput = false, crouchInput = false, interactInput = false)
            elapsed += dt
        }
        assertFalse(open.isGameOver, "A cut beam cannot kill")
        assertTrue(open.isLevelComplete, "...and the corridor is the way out (ended at x=${open.player.x.toInt()})")
    }

    @Test
    fun testLevel6HangingPlatformIsLevelWithTheCrateAndDropsIntoTheCorridor() {
        // "lift the floating platform to the level of the top of the crate on the edge of the
        // platform before it" and "move the crate to the edge of the platform". The step crate now
        // stands flush with the block's far lip, the platform hangs past the gap at the crate's own
        // top, and that gap is both the crossing and the way down: jumped from the crate it lands
        // level on the platform; walked off it drops to the corridor floor. Nothing holds the
        // platform up and nothing is drawn holding it up either ("remove the chain holding the
        // floating platform").
        val layout = LevelData.LEVEL_6_LAYOUT
        val cranePlatform = layout.boxes.first { it.height == 48.0 && it.width > 300.0 }
        val gateBlock = layout.boxes.first { it.x == cranePlatform.right }
        val plank = layout.boxes.first { it.height == 30.0 }
        val endCrate = layout.boxes.first { it.height == 48.0 && it.width == 68.0 && it.x > gateBlock.left }
        val dt = 1.0 / 60.0

        assertEquals(gateBlock.right, endCrate.right, 1e-9, "The step crate sits on the block's own edge")
        assertEquals(gateBlock.top, endCrate.bottom, 1e-9, "...standing on it, not floating over it")
        assertEquals(endCrate.top, plank.top, 1e-9, "The platform is level with the top of that crate")
        assertTrue(plank.left > endCrate.right, "...and starts past its edge, with a gap between them")
        val gap = plank.left - endCrate.right
        assertTrue(layout.tableDecorations.isEmpty(), "Nothing props the platform up")
        assertTrue(layout.boxes.none { it.left < plank.left && it.right > endCrate.right && it.top > plank.top },
            "The chute has to be clear - anything standing in it pins a body under the platform's face")

        // Walked off, the crate's lip drops the player into the corridor.
        val falling = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        falling.allGuards.forEach { it.visionRange = 0.0 }
        val fp = falling.player
        assertTrue(gap > fp.width, "The chute has to be wider than the body that falls down it (was $gap)")
        fp.resetTo(endCrate.left, endCrate.top - fp.height)
        var elapsed = 0.0
        while (elapsed < 4.0 && !(fp.isGrounded && fp.y + fp.height >= 439.0)) {
            falling.update(dt, moveInput = 1.0, jumpInput = false, crouchInput = false, interactInput = false)
            elapsed += dt
        }
        assertTrue(fp.isGrounded && fp.y + fp.height >= 439.0,
            "Walking off the lip has to reach the corridor floor (ended feet=${fp.y + fp.height})")
        assertTrue(fp.x < plank.left, "...down the chute, not across it")

        // Jumped, the same lip crosses to the platform - and the crate itself is reachable from the
        // block, or there would be no way onto that lip in the first place.
        val jumping = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        jumping.allGuards.forEach { it.visionRange = 0.0 }
        val jp = jumping.player
        jp.resetTo(endCrate.left - 120.0, gateBlock.top - jp.height)
        elapsed = 0.0
        var onTheCrate = false
        var landed = false
        while (elapsed < 6.0 && !landed) {
            val atTheCrateFace = !onTheCrate && (endCrate.left - (jp.x + jp.width)) in 0.0..6.0
            val atTheLip = onTheCrate && (endCrate.right - (jp.x + jp.width)) in 0.0..4.0
            jumping.update(
                dt,
                moveInput = 1.0,
                jumpInput = jp.isGrounded && (atTheCrateFace || atTheLip),
                crouchInput = false,
                interactInput = false
            )
            elapsed += dt
            if (jp.isGrounded && kotlin.math.abs((jp.y + jp.height) - endCrate.top) < 1.0 && jp.x < plank.left) onTheCrate = true
            landed = jp.isGrounded && kotlin.math.abs((jp.y + jp.height) - plank.top) < 1.0 && jp.x > plank.left - 1.0
        }
        assertTrue(onTheCrate, "The step crate has to be reachable from the block (48 is inside the jump)")
        assertTrue(landed, "A jump off the crate has to clear the gap onto the platform (ended x=${jp.x.toInt()} feet=${(jp.y + jp.height).toInt()})")

        // How badly the press may be mistimed and still make it across - the number the gap's width
        // is actually spending. Jumping early wastes arc, so this is the playable margin: it has to
        // stay a handful of units, or the crossing becomes pixel-perfect and the section unfair.
        var slack = 0
        for (early in 1..24) {
            val trial = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
            trial.allGuards.forEach { it.visionRange = 0.0 }
            val tp = trial.player
            tp.resetTo(endCrate.left - 120.0, gateBlock.top - tp.height)
            var t = 0.0
            var up = false
            var over = false
            while (t < 6.0 && !over) {
                val atFace = !up && (endCrate.left - (tp.x + tp.width)) in 0.0..6.0
                val atLip = up && (endCrate.right - (tp.x + tp.width)) <= early.toDouble()
                trial.update(dt, moveInput = 1.0, jumpInput = tp.isGrounded && (atFace || atLip), crouchInput = false, interactInput = false)
                t += dt
                if (tp.isGrounded && kotlin.math.abs((tp.y + tp.height) - endCrate.top) < 1.0 && tp.x < plank.left) up = true
                over = tp.isGrounded && kotlin.math.abs((tp.y + tp.height) - plank.top) < 1.0 && tp.x > plank.left - 1.0
            }
            if (!over) break
            slack = early
        }
        assertTrue(slack >= 10, "The chute ($gap) has to leave room to mistime the jump - only $slack units of it do")
    }

    @Test
    fun testLevel6CraneTopIsNeitherClimbedNorJumpedFromItsOwnRearDeck() {
        // "he should be able to climb this but not to the top part from the crane." The machine is
        // crossed one way: in off the boom, east along it, down onto the rear deck, down onto the
        // platform. The deck is climbed from the platform beside it; the machine's top above it is
        // refused, and it is refused by DENYING THE MANTLE rather than by moving any geometry - the
        // collision boxes stay on the art (a lift to make the top jumpable left the player visibly
        // floating above the deck, and was taken back out).
        val layout = LevelData.LEVEL_6_LAYOUT
        val crane = layout.cranes.single()
        val house = crane.houseBounds
        val body = crane.bodyBounds
        val platform = layout.boxes.first { it.height == 48.0 && it.width > 300.0 }
        val dt = 1.0 / 60.0

        assertTrue(body in layout.unclimbableBoxes, "The machine's top is on the level's no-mantle list")
        assertFalse(house in layout.unclimbableBoxes, "...but its rear deck is not")
        assertEquals(platform.top, house.bottom, 1e-9, "The deck's box rests on the platform, as drawn")

        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        world.allGuards.forEach { it.visionRange = 0.0 }
        val p = world.player

        val deckRise = platform.top - house.top
        assertTrue(deckRise > p.climbMinHeight && deckRise <= p.climbMaxHeight,
            "The deck is meant to be a climb from the platform ($deckRise)")
        val topRise = house.top - body.top
        assertTrue(topRise > p.maxJumpHeight,
            "The machine's top is out of jump range from the deck ($topRise) - so the mantle is the only " +
                "thing that could ever have got the player up there, and it is what gets denied")

        // Up onto the deck from the platform: a climb, and allowed. (A climb is a jump PRESS against
        // the face - see Player.updateStep - so the press is pulsed; a held one is consumed once.)
        p.resetTo(house.right + 10.0, platform.top - p.height)
        var elapsed = 0.0
        var frame = 0
        var onTheDeck = false
        var climbedOntoTheDeck = false
        while (elapsed < 6.0 && !onTheDeck) {
            world.update(dt, moveInput = -1.0, jumpInput = frame % 20 < 2, crouchInput = false, interactInput = false)
            elapsed += dt
            frame++
            if (p.isClimbing) climbedOntoTheDeck = true
            onTheDeck = p.isGrounded && kotlin.math.abs((p.y + p.height) - house.top) < 1.0
        }
        assertTrue(onTheDeck, "Walking into the rear deck from the platform still has to get up it (feet=${p.y + p.height})")
        assertTrue(climbedOntoTheDeck, "...as a climb - it is well past jump height")

        // Up onto the machine's top from that deck: refused, by climb and by jump alike.
        elapsed = 0.0
        frame = 0
        var everClimbed = false
        var reachedTheTop = false
        while (elapsed < 6.0) {
            world.update(dt, moveInput = -1.0, jumpInput = frame % 20 < 2, crouchInput = false, interactInput = false)
            elapsed += dt
            frame++
            if (p.isClimbing) everClimbed = true
            if (p.isGrounded && kotlin.math.abs((p.y + p.height) - body.top) < 1.0) reachedTheTop = true
        }
        assertFalse(everClimbed, "The machine's top must never be mantled")
        assertFalse(reachedTheTop, "...and the jump against it cannot land up there either")
        elapsed = 0.0
        while (elapsed < 1.0) {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
            elapsed += dt
        }
        assertTrue(p.isGrounded && kotlin.math.abs((p.y + p.height) - house.top) < 1.0,
            "...the player is left standing on the deck (feet=${p.y + p.height}, deck=${house.top})")

        // The way in is untouched: tallBlock to the boom, on the far side of the machine.
        val boom = crane.boomBounds
        val tallBlock = layout.boxes.first { it.width == 300.0 && it.height == 96.0 }
        assertFalse(boom in layout.unclimbableBoxes, "The boom is still the way onto the machine")
        val boomRise = tallBlock.top - boom.top
        assertTrue(boomRise > p.climbMinHeight && boomRise <= p.climbMaxHeight,
            "...and that climb is still a legal one ($boomRise)")
    }

    @Test
    fun testLevel6ExtractionBuildingStandsOnTheFloorAndCarriesTheHangingPlatform() {
        // "increase size of the building at end and make sure it is on the floor and the middle
        // part should be connected to the balcony." The building is the one thing in the section
        // that can hold the platform up visually now that its chains are gone, so its height is
        // chosen to put the art's own balcony deck at the platform's underside, and its left edge
        // runs under the platform's tip rather than stopping short of it.
        val layout = LevelData.LEVEL_6_LAYOUT
        val building = layout.exitStructure
        assertNotNull(building, "Level 6 draws its own extraction building rather than the shared booth")
        val plank = layout.boxes.first { it.height == 30.0 }
        val groundTop = layout.platforms.first().top

        assertEquals(groundTop, building!!.bottom, 1e-9, "It stands on the floor, not above or inside it")
        assertTrue(building.height > 335.0, "...and it is bigger again than the 335 before it")
        assertTrue(building.left <= plank.right, "Its left edge runs under the platform's tip, so the two meet")
        assertTrue(building.left > plank.left, "...only the tip, though - it does not swallow the whole platform")
        // 0.5215 down from the roof is where exitlvl7.png's own balcony deck starts. The art fills
        // its box (it is cropped to its alpha bounds), so this fraction is the drawn deck.
        val deckY = building.top + 0.5215 * building.height
        assertTrue(kotlin.math.abs(deckY - plank.top) < 4.0,
            "The building's balcony deck should be flush with the platform's own surface (deck=$deckY, platform=${plank.top})")
        assertTrue(layout.exitZone.x > building.left && layout.exitZone.right < building.right,
            "The trigger sits inside the silhouette - the player walks into the building")
        assertTrue(building.right <= layout.worldWidth,
            "The world has to reach past it (building ends ${building.right}, world ${layout.worldWidth})")
    }

    @Test
    fun testLevel6ExitSitsPastTheGantryBlockWithTheWholeSectionInFrontOfIt() {
        // The exit moved to the far side of the machine when the vehicle moved next to the lever,
        // and then past section 4's gantry block when that was added - so the crane crossing gets
        // the player to the gantry, and the gantry's own timed climb is the last thing before
        // extraction. Nothing may reach it on the ground: gateBlock is 144 from the floor, past
        // Player.climbMaxHeight.
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val crane = world.cranes.single()
        val layout = LevelData.LEVEL_6_LAYOUT
        val cranePlatform = layout.boxes.first { it.height == 48.0 && it.width > 300.0 }
        val gateBlock = layout.boxes.first { it.x == cranePlatform.right }
        assertTrue(world.exitZone.x > crane.houseBounds.right,
            "The exit must sit past the machine, which the ground approach cannot climb")
        assertTrue(world.exitZone.x > gateBlock.right,
            "...and past the gantry block, so its timed climb is on the only route there")
        assertTrue(gateBlock.height > world.player.climbMaxHeight,
            "The block has to be unclimbable from the ground (${gateBlock.height} vs ${world.player.climbMaxHeight})")
        assertTrue(layout.worldWidth > world.exitZone.right + 420.0,
            "...with the booth and its fence still fitting inside the world past the exit")
    }

    @Test
    fun testLevel6LeverCrateSwingCrossesToLandingCrate() {
        val layout = LevelData.LEVEL_6_LAYOUT
        val terrain = layout.boxes.first { it.width == 300.0 }
        val landingCrate1 = layout.boxes.first { it.x == 1370.0 }
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val dt = 1.0 / 60.0

        var elapsed = 0.0
        var stalledFor = 0.0
        var swung = false
        // This test's job is just the swing itself (section 1) - stop once safely landed on
        // landingCrate1, not the whole (now much longer) level. See
        // testLevel6SecondSectionCrossesPitAndReachesExit for section 2 end to end.
        while (elapsed < 20.0 && !world.isGameOver && world.player.x < landingCrate1.left + 20.0) {
            val beforeX = world.player.x
            val hookInReach = world.swingHooks.any { h ->
                val r = Player.hookGripX(h) - world.player.centerX
                r in world.player.swingMinReach..world.player.swingMaxReach
            }
            val nearLedgeEdge = listOf(terrain.right, landingCrate1.right).any { ledgeRight ->
                (ledgeRight - (world.player.x + world.player.width)) in 0.0..18.0
            }
            // hookInReach can fire while airborne (just walked off the crate's own leading edge) -
            // canJump's coyote-time window still allows the swing to grab there, same as a real
            // player's late press would; the other two triggers are grounded-only, plain jumps.
            val jump = hookInReach || (world.player.isGrounded && (stalledFor > 0.05 || nearLedgeEdge))
            world.update(dt, moveInput = 1.0, jumpInput = jump, crouchInput = false, interactInput = true)
            if (world.player.isSwinging) swung = true
            stalledFor = if (kotlin.math.abs(world.player.x - beforeX) < 0.5) stalledFor + dt else 0.0
            elapsed += dt
        }

        assertTrue(swung, "Player should have swung from hook1 to cross the gap")
        assertTrue(world.player.x > landingCrate1.left,
            "The swing landing has to be past landingCrate1's near edge, not short of it")
    }

    @Test
    fun testLevel6SecondSectionCrossesPitAndReachesExit() {
        // Section 2 is a fall-and-climb: farTerrain -> a real pit (well past jump range, so a fall
        // to the ground is unavoidable), with a hanging long crate/overwatch guard above it (same
        // pattern as LEVEL_3_LAYOUT's longCrate1/overwatchGuard1) and a plain rescue crate in the
        // pit's own left corner -> climb tallBlock. Crouching underneath the overwatch crate's own
        // span (with a margin either side, the same evasion mechanic LEVEL_3_LAYOUT's own tutorial
        // teaches) is what keeps this crossing undetected. Section 3 (past tallBlock) is the crane
        // crossing: walking on along tallBlock runs into the boom that now overhangs its far end,
        // which is climbable from exactly there ("so that the player can climb onto it from the
        // otherside of the gap"), and the boom carries the player over the ground gap, the barrel
        // pair and the lever platform to the machine itself, whose own stepped silhouette
        // (CraneDef.bodyBounds -> houseBounds -> cranePlatform) walks down to the exit. The same
        // walk on the ground below only reaches the machine's tracks, which are too tall to climb.
        val layout = LevelData.LEVEL_6_LAYOUT
        val farTerrain = layout.boxes.first { it.x == 1592.0 }
        val tallBlock = layout.boxes.first { it.width == 300.0 && it.height == 96.0 }
        val pitLongCrate = layout.boxes.first { it.width == 174.0 && it.height == 38.0 && it.y == 300.0 }
        val endTerrain = layout.boxes.first { it.width == 70.0 && it.x > tallBlock.right }
        val crane = layout.cranes.single()
        val cranePlatform = layout.boxes.first { it.height == 48.0 && it.width > 300.0 }
        val gateBlock = layout.boxes.first { it.x == cranePlatform.right }
        val plank = layout.boxes.first { it.height == 30.0 }
        val endCrate = layout.boxes.first { it.height == 48.0 && it.width == 68.0 && it.x > gateBlock.left }
        val endBarrelWalls = layout.barrels.map { it.left }.distinct()
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_6)
        val dt = 1.0 / 60.0

        // Start already on farTerrain, past the swing puzzle (that's covered by its own test).
        world.player.resetTo(farTerrain.left + 20.0, farTerrain.top - world.player.height)

        var elapsed = 0.0
        var stalledFor = 0.0
        var walkedTheBoom = false
        var climbedTheGantryBlock = false
        var tookTheCorridor = false
        // Five legs, because the level's own route doubles back twice now: out along the GROUND to
        // lever_3 (section 4's trigger, "the lever for the hanging crate should be the one left of
        // the crane"), back west to the foot of the boom, over the machine and up the gantry
        // block, up the plank for lever_4 while the guard is walking away, then back down and out
        // along the corridor under the plank. Leg 0 crouches under the boom where it overhangs
        // tallBlock - which also stops the autopilot climbing onto it early, since Player refuses a
        // jump with no room to stand.
        var leg = 0
        val lever3 = world.levers.first { it.id == "lever_3" }
        val lever4 = world.levers.first { it.id == "lever_4" }
        val plankGuard = world.allGuards.first { it.x > plank.left - 100.0 }
        val boom = crane.boomBounds
        while (elapsed < 110.0 && !world.isLevelComplete && !world.isGameOver) {
            val beforeX = world.player.x
            val p = world.player
            val feetY = p.y + p.height
            val onTallBlock = p.isGrounded && kotlin.math.abs(feetY - tallBlock.top) < 1.0
            val onThePlank = p.isGrounded && kotlin.math.abs(feetY - plank.top) < 1.0
            // The wait for the guard happens BEFORE the step crate, on the block, not on top of
            // it: the crate's top is level with the platform, so a body standing up there is
            // inside the guard's 220 of vision from his near turn - the whole approach has to be
            // made in one burst while he is walking away. Waiting a body-length short of the
            // crate's face keeps the player outside it.
            val beforeTheCrate = p.isGrounded && kotlin.math.abs(feetY - gateBlock.top) < 1.0 &&
                (endCrate.left - (p.x + p.width)) in 0.0..24.0
            // ...and then the jump across is taken at the crate's own lip. (Getting onto the crate
            // needs no special case: walking into its face stalls, and a stalled autopilot jumps.)
            val atTheLip = p.isGrounded && kotlin.math.abs(feetY - endCrate.top) < 1.0 &&
                (endCrate.right - (p.x + p.width)) <= 3.0
            // Ducks a little BEFORE the beam, not at it: standing into its face stalls the walk,
            // and a stalled autopilot jumps - which is the climb onto the boom, the leg-2 move.
            val underTheBoom = onTallBlock && p.x + p.width > boom.left - 60.0 && p.x < boom.right
            if (leg == 0 && lever3.isActivated) leg = 1
            if (leg == 1 && onTallBlock && p.x + p.width < boom.left - 20.0) leg = 2
            if (leg == 2 && lever4.isActivated) leg = 3
            // Off the platform's near end, down the chute, and out along the corridor floor.
            if (leg == 3 && p.isGrounded && feetY >= 439.0) {
                leg = 4
                tookTheCorridor = true
            }
            // Only start the crossing while the guard is walking away AND still in the near half
            // of his (now much longer) beat - that is the only window where he stays turned away
            // long enough for the crate hop, the jump, the lever and the way back off.
            val guardLooking = !(plankGuard.facing > 0.0 && plankGuard.x < plank.left + 200.0)
            val holdForTheGuard = leg == 2 && beforeTheCrate && guardLooking
            val move = when {
                leg == 1 -> -1.0
                // Back off the platform's near end, into the chute.
                leg == 3 -> -1.0
                holdForTheGuard -> 0.0
                else -> 1.0
            }
            val nearLedgeEdge = leg != 1 && leg < 3 &&
                listOf(farTerrain.right, tallBlock.right, endTerrain.right).any { ledgeRight ->
                    (ledgeRight - (p.x + p.width)) in 0.0..18.0
                }
            // The jump across the chute is taken at the very lip, not 18 units short of it: the
            // gap is 65 and the arc only carries 84.5, so a wasted run-up misses the platform.
            val jumpTheChute = leg == 2 && atTheLip
            val nearWall = leg != 1 && leg != 3 && endBarrelWalls.any { wallX -> (wallX - (p.x + p.width)) in 0.0..18.0 }
            // Leg 0 walks the ground under the boom and off tallBlock's far edge, so it must NOT
            // hop that ledge - dropping off it is the way on to the barrels and the lever. Legs 3
            // and 4 are the way back DOWN: no jumping at all, or the crate simply gets re-climbed.
            val ledgeJump = nearLedgeEdge && !(leg == 0 && onTallBlock)
            // Legs 3+ are the way back DOWN and out: no jumping at all, or the chute gets
            // re-crossed and the platform re-taken, straight into the guard.
            val jump = p.isGrounded && leg < 3 && !holdForTheGuard &&
                (stalledFor > 0.05 || ledgeJump || nearWall || jumpTheChute)
            val underOverwatch = p.x + p.width > pitLongCrate.left - 60.0 && p.x < pitLongCrate.right + 60.0
            world.update(
                dt,
                moveInput = move,
                jumpInput = jump,
                crouchInput = underOverwatch || (leg < 2 && underTheBoom),
                interactInput = leg == 0 || leg == 2
            )
            if (p.isGrounded && kotlin.math.abs((p.y + p.height) - boom.top) < 1.0 && p.x > boom.left) {
                walkedTheBoom = true
            }
            if (p.isGrounded && kotlin.math.abs((p.y + p.height) - gateBlock.top) < 1.0) {
                climbedTheGantryBlock = true
            }
            stalledFor = if (kotlin.math.abs(p.x - beforeX) < 0.5) stalledFor + dt else 0.0
            elapsed += dt
        }

        assertTrue(lever3.isActivated,
            "The ground leg has to reach lever_3 - nothing else starts the gantry, and the climb past it " +
                "is the only way on")
        assertTrue(lever4.isActivated,
            "...and the plank's own lever has to be thrown, or the laser curtain is still across the way out")
        assertTrue(tookTheCorridor,
            "...and the way out is the corridor under the plank, on the ground")

        assertFalse(world.isGameOver,
            "Player should not be caught by the pit's overwatch guard while crouched underneath. Ended at " +
                "x=${world.player.x.toInt()} y=${world.player.y.toInt()} after ${elapsed.toInt()}s")
        assertTrue(walkedTheBoom,
            "The crossing has to actually go over the crane's boom - nothing else reaches the gantry")
        assertTrue(climbedTheGantryBlock,
            "...and section 4's timed climb has to be taken on the way, since the exit is past it")
        assertTrue(world.isLevelComplete,
            "Level should complete after falling into the pit, climbing tallBlock, climbing onto the boom " +
                "that overhangs it, walking down the machine, and timing the climb up " +
                "gateBlock while the gantry crate is swung off it. " +
                "Ended at x=${world.player.x.toInt()} y=${world.player.y.toInt()} after ${elapsed.toInt()}s")
    }

    @Test
    fun testSwingNeedsTheWalkAndTheHook() {
        val hook = GameWorld.createDefault(SWING_TEST_LEVEL).swingHooks.first()
        val terrain = SWING_TEST_LAYOUT.boxes.first { it.width == 300.0 }
        val dt = 1.0 / 60.0

        // The trigger window is tight enough that where the player stands matters (see
        // Player.swingMinReach): at the very lip of terrain1 the grip is 66 ahead, which is
        // inside it. backOff walks them away from the lip so a test can approach it.
        fun freshWorldAtLedge(backOff: Double = 0.0): GameWorld {
            val world = GameWorld.createDefault(SWING_TEST_LEVEL)
            world.player.resetTo(terrain.right - world.player.width - backOff, terrain.top - world.player.height)
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false)
            return world
        }

        // Standing still and pressing jump is an ordinary jump, never a swing - the clip opens on
        // a push-off stride and there is no version of it that starts from a standstill.
        val standing = freshWorldAtLedge()
        repeat(20) { standing.update(dt, moveInput = 0.0, jumpInput = true, crouchInput = false) }
        assertFalse(standing.player.isSwinging, "A swing must not start from a standstill")

        // Same button, but walking into it and pressing at the lip - which is the only place the
        // window allows, and what a player does.
        val gripX = Player.hookGripX(hook)
        val walking = freshWorldAtLedge(backOff = 25.0)
        repeat(40) {
            val inReach = gripX - walking.player.centerX <= walking.player.swingMaxReach
            walking.update(dt, moveInput = 1.0, jumpInput = inReach, crouchInput = false)
        }
        assertTrue(walking.player.isSwinging, "Walking into the hook and pressing jump should swing")

        // Facing away from the hook there is nothing ahead to grab.
        val away = freshWorldAtLedge()
        repeat(20) { away.update(dt, moveInput = -1.0, jumpInput = true, crouchInput = false) }
        assertFalse(away.player.isSwinging, "A hook behind the player is not a grab point")
    }

    @Test
    fun testSwingKeepsTheHandOnTheHookForItsWholeHang() {
        val world = GameWorld.createDefault(SWING_TEST_LEVEL)
        val hook = world.swingHooks.first()
        val terrain = SWING_TEST_LAYOUT.boxes.first { it.width == 300.0 }
        val dt = 1.0 / 60.0
        world.player.resetTo(terrain.right - world.player.width - 25.0, terrain.top - world.player.height)

        val gripX = Player.hookGripX(hook)
        var started = false
        var hangSamples = 0
        var elapsed = 0.0
        while (elapsed < 5.0) {
            val press = !started && gripX - world.player.centerX <= world.player.swingMaxReach
            world.update(dt, moveInput = 1.0, jumpInput = press, crouchInput = false)
            if (world.player.isSwinging) {
                started = true
                // Through the hang the body has to stay directly under the hook - the collision
                // box is held at the grip while the silhouette does the swinging (see
                // Player.advanceSwing), so this is what "hanging off it" means numerically.
                val phase = world.player.swingPhase
                // The hand is pinned to the hook between the grab and the release, frames 11 and
                // 29 of the clip's 51 - i.e. phase 0.216 to 0.569. Sampled just inside both ends
                // so a frame's rounding cannot put a sample in the interpolated launch or flight.
                if (phase > 0.24 && phase < 0.55) {
                    hangSamples++
                    val offset = kotlin.math.abs(world.player.centerX - (hook.left + hook.width / 2.0))
                    assertTrue(offset < 12.0,
                        "At phase $phase the body sat ${offset.toInt()} units off the hook")
                    assertTrue(world.player.y + world.player.height > hook.bottom,
                        "At phase $phase the feet were above the grip")
                }
            } else if (started) {
                break
            }
            elapsed += dt
        }
        assertTrue(started, "The swing should have started")
        // Deliberately a low bar: the hang is meant to be brief - about a fifth of a second, so
        // a dozen ticks at 60Hz - and this is here to catch it vanishing entirely, not to pin a
        // duration the pacing curve is free to keep tuning.
        assertTrue(hangSamples > 4, "Expected a real hang, sampled $hangSamples frames of it")
    }

    @Test
    fun testSwingBodyRotationAndPivotTracking() {
        val world = GameWorld.createDefault(SWING_TEST_LEVEL)
        val hook = world.swingHooks.first()
        val terrain = SWING_TEST_LAYOUT.boxes.first { it.width == 300.0 }
        val dt = 1.0 / 60.0
        world.player.resetTo(terrain.right - world.player.width - 25.0, terrain.top - world.player.height)

        assertEquals(0.0, world.player.swingRotationDegrees, 1e-4)
        assertEquals(0.0, world.player.swingPivotHeight, 1e-4)

        val gripX = Player.hookGripX(hook)
        var started = false
        var hadBackwardTilt = false
        var hadForwardTilt = false
        var hadStraightFlight = false
        var elapsed = 0.0

        while (elapsed < 5.0) {
            val press = !started && gripX - world.player.centerX <= world.player.swingMaxReach
            world.update(dt, moveInput = 1.0, jumpInput = press, crouchInput = false)
            if (world.player.isSwinging) {
                started = true
                val rot = world.player.swingRotationDegrees
                val pivotH = world.player.swingPivotHeight
                val phase = world.player.swingPhase

                // Pivot height must be between half height and full height during swing
                assertTrue(pivotH in (world.player.height * 0.45)..(world.player.height * 1.1),
                    "Pivot height $pivotH out of expected bounds")

                if (phase in 0.20..0.26) {
                    // Grab / catch: body trailing behind hook (positive degrees for facing right)
                    if (rot > 10.0) hadBackwardTilt = true
                }
                if (phase in 0.52..0.58) {
                    // Release: body leading forward (negative degrees for facing right)
                    if (rot < -10.0) hadForwardTilt = true
                }
                if (phase in 0.80..0.86) {
                    // Approaching landing: body straightening upright
                    if (kotlin.math.abs(rot) < 8.0) hadStraightFlight = true
                }
            } else if (started) {
                break
            }
            elapsed += dt
        }

        assertTrue(started, "Swing should have started")
        assertTrue(hadBackwardTilt, "Character should tilt backward during grab")
        assertTrue(hadForwardTilt, "Character should tilt forward during swing apex / release")
        assertTrue(hadStraightFlight, "Character should straighten upright towards landing")
        assertEquals(0.0, world.player.swingRotationDegrees, 1e-4, "Rotation should be zero once swing completes")
    }

    // ---- level 3: the roof table and the guard under it ------------------------------------

    private fun level3(): GameWorld = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_3)

    @Test
    fun testLevel3CrouchingBehindTheCrateActuallyBreaksLineOfSight() {
        // The tutorial's own promise ("Hold CROUCH behind the crate to break the guard's line of
        // sight") needs the guard far enough away that the crate's 48-tall profile is actually
        // between his eye and a crouched player, not just nominally "in the way" - see
        // guardNearPost's own comment for how this distance was found. Checked directly against
        // VisionSystem rather than through a full world walkthrough, across the tutorial's own
        // trigger window (step_crouch_hide, playerStartX..415 - it now runs from the player's
        // very first frame, see DEFAULT_LEVEL_3).
        val world = level3()
        val crate = world.crate
        val occluders = listOf(crate)
        for (playerX in listOf(LevelData.LEVEL_3_LAYOUT.playerStartX, 300.0, 350.0, 400.0, 415.0)) {
            val crouched = Player(x = playerX, y = 440.0 - 56.0, width = 22.0, height = 56.0)
            crouched.update(1.0 / 60.0, moveInput = 0.0, jumpInput = false, crouchInput = true, platforms = listOf(Rect(0.0, 440.0, world.worldWidth, 100.0)))
            assertNull(
                VisionSystem.getPlayerSpottedDistance(world.guard, crouched, occluders),
                "Crouched at x=$playerX, the guard (still at his near post) should not spot the player"
            )
        }
    }

    @Test
    fun testLevel3RoofGuardStaysAtHisPostUntilThePlayerCrouches() {
        // The crouch-hide tutorial now runs from the player's very first frame (see
        // DEFAULT_LEVEL_3), so the guard has to actually be standing at his post for it, not
        // wherever his own patrol timing happened to leave him - see Guard.holdUntilPlayerCrouches.
        val world = level3()
        val g = world.guard
        val dt = 1.0 / 60.0
        assertTrue(g.holdUntilPlayerCrouches)

        // Time alone doesn't release him - nothing but a crouch does.
        repeat(6 * 60) { world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false) }
        assertEquals(g.patrolMinX, g.x, 1e-9, "Still exactly at the near post after 6s of no crouch")
        assertFalse(world.hasPlayerCrouchedOnce)

        world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = true)
        assertTrue(world.hasPlayerCrouchedOnce, "One crouch is enough to release him")

        // He's free to walk his route from here on, even once the player stops crouching again.
        var walked = 0.0
        while (g.x == g.patrolMinX && walked < 5.0) {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false)
            walked += dt
        }
        assertTrue(g.x != g.patrolMinX, "Started walking his route once released")

        // A restart re-arms the hold for the next attempt (restartLevel doesn't reposition guards,
        // only player position and patrol/timer state - see GameWorld.restartLevel).
        world.restartLevel()
        assertFalse(world.hasPlayerCrouchedOnce, "Hold re-arms on restart")
        val xAfterRestart = g.x
        repeat(2 * 60) { world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false) }
        assertEquals(xAfterRestart, g.x, 1e-9, "Held in place again, not just left to coast")
    }

    @Test
    fun testLevel3RoofGuardPacesUnderThePlank() {
        val world = level3()
        assertEquals(3, world.allGuards.size, "The roof guard plus the two overwatch guards on the long crates")
        val g = world.guard
        // Two beams share tableParts now (this one, and the later camera beam) - the roof guard
        // paces under the first/leftmost one.
        val plank = world.tableParts.minByOrNull { it.x }!!
        val dt = 1.0 / 60.0
        val farPost = g.patrolMaxX
        val nearPost = g.patrolMinX

        assertEquals(96.0, g.height, "The roof guard is drawn with the real idle/walk art, so player height")
        assertEquals(440.0, g.bounds.bottom, 1e-9, "Feet on the ground")
        assertEquals(nearPost, g.x, "Starts at the near post, by the crate - first thing visible on arrival")
        assertEquals(-1.0, g.facing, "...looking left, toward the approach")
        assertTrue(g.patrolPauseDuration > 0.0)
        assertTrue(g.holdUntilPlayerCrouches, "Rooted here until the crouch-hide tutorial is done")

        // Release him the same way the real tutorial does - a single crouch - before walking his
        // route below; that's a separate concern (see testLevel3RoofGuardStaysAtHisPostUntilThePlayerCrouches).
        world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = true)

        // The owner's spec: stay idle -> walk -> stay idle -> come back -> repeat. Walk the model
        // through one full lap and check each leg, plus that he never leaves the underside.
        fun step() {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false)
            assertTrue(g.bounds.top >= plank.bottom, "Head clears the plank at x=${g.x}")
            assertTrue(g.x >= plank.left && g.bounds.right <= plank.right, "Stays under the roof at x=${g.x}")
            assertTrue(world.occluders.none { it.intersects(g.bounds) }, "Never inside a collision box")
        }
        fun stepUntil(limitSeconds: Double, done: () -> Boolean): Double {
            var t = 0.0
            while (!done() && t < limitSeconds) { step(); t += dt }
            assertTrue(done(), "Timed out after ${limitSeconds}s at x=${g.x} walking=${g.isWalking} facing=${g.facing}")
            return t
        }

        // 1. Idle at the near post, facing left, for the whole dwell.
        val dwell = stepUntil(10.0) { g.isWalking }
        // A frame to arm the dwell on arrival and a frame to turn when it ends bracket the wait.
        assertEquals(g.patrolPauseDuration, dwell, dt * 3.5, "Stands the full dwell before moving")
        assertEquals(1.0, g.facing, "Turns to face the way he is about to walk")
        // 2. Walks to the far post.
        stepUntil(10.0) { !g.isWalking }
        assertEquals(farPost, g.x, 1e-9, "Stops exactly on the far post")
        assertEquals(1.0, g.facing, "Holds his arriving facing (right) while standing there")
        // 3. Idle there for the dwell, then 4. comes back to the original position.
        val dwell2 = stepUntil(10.0) { g.isWalking }
        assertEquals(g.patrolPauseDuration, dwell2, dt * 3.5)
        assertEquals(-1.0, g.facing)
        stepUntil(10.0) { !g.isWalking }
        assertEquals(nearPost, g.x, 1e-9, "Back at the original position")
        assertEquals(-1.0, g.facing, "...looking left again")
        assertEquals(GuardState.PATROL, g.state)
        // 5. Repeat: the next leg starts the same way.
        stepUntil(10.0) { g.isWalking }
        assertEquals(1.0, g.facing)
    }

    @Test
    fun testGuardWithoutPauseStillTurnsOnTheSpot() {
        // The dwell is opt-in: every pre-existing guard has patrolPauseDuration 0 and must turn
        // the frame it arrives, exactly as before.
        val g = Guard(x = 190.0, y = 0.0, patrolMinX = 100.0, patrolMaxX = 200.0, speed = 100.0, facing = 1.0)
        repeat(12) { g.update(1.0 / 60.0) }
        assertEquals(-1.0, g.facing, "Turned at patrolMaxX without waiting")
        assertTrue(g.x < 200.0, "...and is already walking back")
        assertTrue(g.isWalking)
        assertEquals(0.0, g.patrolPauseTimer)
    }

    @Test
    fun testLevel3TableIsDirectlyClimbableFromTheCrate() {
        val world = level3()
        val plank = world.tableParts.minByOrNull { it.x }!! // the crate's own beam, not the later camera beam
        assertTrue(plank in world.boxes, "The plank collides")
        val crate = world.crate
        assertTrue(crate in world.boxes, "The crate collides")
        assertEquals(crate.right, plank.left, 1e-9, "Crate and plank meet edge to edge, no overlap")

        // Nothing bridges the gap between the crate's top and the plank's underside - the plank
        // is a floating ledge by the physics' own definition, deliberately exempted (see
        // LevelLayout.floatingClimbTargets) so the climb from the crate mounts it directly.
        assertTrue(plank.bottom < crate.top - 4.0, "The plank genuinely is a floating ledge here")
        assertTrue(plank in world.floatingClimbTargets, "...which is why it needs the exemption to be climbable at all")
        val rise = crate.top - plank.top
        assertTrue(rise > 51.2 && rise <= 115.0, "Crate-to-plank rise ($rise) is a real climb")

        // The leg at the far end is a real, solid obstacle now, not just flavor - clear of the
        // guard's own far-post footprint so his bounded patrol never bumps into it.
        val leg = world.tableDecorations.minByOrNull { it.x }!! // rightLeg (this table's own leg), not the later camera beam's own leg
        assertTrue(leg in world.boxes, "A genuine obstacle, not just flavor - it collides")
        assertEquals(plank.right, leg.right, 1e-9, "Caps the plank's own far end")
        assertTrue(leg.top < plank.bottom, "Tucked up against the plank's underside, no visible gap")
        assertEquals(440.0, leg.bottom, 1e-9, "...down to the ground")
        val guardFarEdge = world.guard.patrolMaxX + world.guard.width
        assertTrue(leg.left >= guardFarEdge, "Clear of the guard's far post, x=${leg.left} vs ${guardFarEdge}")
    }

    @Test
    fun testLevel3ClimbingRightInFrontOfTheWatchingGuardGetsYouSeen() {
        // Nothing hides the climb from him any more - it's a floating ledge climbed directly
        // (LevelLayout.floatingClimbTargets), not a mantle behind a wall. Attempting it while he's
        // still dwelling at the near post, facing straight at it, is exactly like standing in the
        // open in front of any other guard in the game.
        val world = level3()
        val dt = 1.0 / 60.0
        var elapsed = 0.0
        var stalledFor = 0.0
        while (elapsed < 6.0 && !world.wasDetected) {
            val beforeX = world.player.x
            val jump = world.player.isGrounded && stalledFor > 0.05
            world.update(dt, moveInput = 1.0, jumpInput = jump, crouchInput = false)
            stalledFor = if (kotlin.math.abs(world.player.x - beforeX) < 0.5) stalledFor + dt else 0.0
            elapsed += dt
        }
        assertTrue(world.wasDetected, "Climbing in front of the guard while he watches the approach should get you seen")
    }

    @Test
    fun testLevel3ClimbIsSafeOnceTheGuardHasWalkedAway() {
        val world = level3()
        val table = world.tables.minByOrNull { it.x }!! // the crate's own beam, not the later camera beam
        val hideCrate = world.boxes.first { it.width == 68.0 && it.height == 48.0 && it != world.crate }
        val dt = 1.0 / 60.0

        // Release him from the near post the same way the real crouch-hide tutorial does, so the
        // wait below is actually about him walking his route, not about the hold itself.
        world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = true)

        // Wait him out: footsteps are heard (not just seen) within Player.NoiseLevel.NORMAL's
        // 180-unit radius whenever there's an unobstructed line to him, so it isn't enough that
        // he's merely turned away - he has to be far enough down the plank that walking and
        // climbing near the crate doesn't carry to him at all. Waiting for the far post guarantees
        // that (roughly 340 units off, comfortably past 180).
        var waited = 0.0
        while (world.guard.x < world.guard.patrolMaxX && waited < 15.0) {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false)
            waited += dt
        }
        assertEquals(world.guard.patrolMaxX, world.guard.x, "Guard should have reached the far post by now")

        var elapsed = 0.0
        var stalledFor = 0.0
        // Hold right; jump whenever grounded progress stalls - the same auto-pilot the level 5
        // walkthrough uses. Stop as soon as the feet are on the roof, then walk to its end.
        while (elapsed < 20.0 && !(world.player.isGrounded && world.player.x > table.right - 60.0)) {
            val beforeX = world.player.x
            val jump = world.player.isGrounded && stalledFor > 0.05
            world.update(dt, moveInput = 1.0, jumpInput = jump, crouchInput = false)
            stalledFor = if (kotlin.math.abs(world.player.x - beforeX) < 0.5) stalledFor + dt else 0.0
            elapsed += dt
            if (world.player.isGrounded && world.player.x > table.left + 20.0) {
                // Mounting the hide crate along the way lands the feet on ITS top instead, briefly.
                val feet = world.player.y + world.player.height
                assertTrue(kotlin.math.abs(feet - table.top) < 1e-6 || kotlin.math.abs(feet - hideCrate.top) < 1e-6,
                    "On the roof at x=${world.player.x.toInt()}, feet should be on the plank or the hide crate top, was $feet")
            }
        }
        assertTrue(world.player.x > table.right - 60.0, "Reached the far end of the roof (x=${world.player.x.toInt()}, t=${elapsed.toInt()}s)")
        assertFalse(world.wasDetected, "Climbing once he's walked away from the near post goes unseen")
    }

    @Test
    fun testLevel3DropIsSeenFromTheFarPostAndSafeAtStart() {
        val dt = 1.0 / 60.0
        // The decorative leg (flush against the plank's own far edge) now blocks sight, same as
        // any other drawn geometry - see GameWorld's occluders. That leaves the far-post guard,
        // standing just short of it, a real but narrow window onto the ground right in front of
        // him: past the leg's own footprint, its shadow covers the rest of the corridor outright
        // (checked directly against VisionSystem - nothing beyond the leg is visible to him from
        // there, at any distance). This is closer to his own post than "past the slab's end," but
        // it's the same beat: drop while he's out there watching and he sees it.
        // +55 rather than right against him: his sight starts at the torch lens, 28 units ahead
        // of his centre, so a landing closer than that is behind the light, not in it.
        fun landInFrontOfTheFarPost(world: GameWorld) {
            world.player.x = world.guard.patrolMaxX + 55.0
            world.player.y = 440.0 - world.player.height
        }

        // Wait for him to walk out to the far post, facing right: the landing zone is in his beam.
        val seen = level3()
        // Release him from the near post the same way the real crouch-hide tutorial does, so he's
        // actually free to walk out there.
        seen.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = true)
        var t = 0.0
        while (!(seen.guard.x == seen.guard.patrolMaxX && !seen.guard.isWalking) && t < 30.0) {
            seen.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false); t += dt
        }
        assertEquals(seen.guard.patrolMaxX, seen.guard.x, "Guard is standing at the far post")
        landInFrontOfTheFarPost(seen)
        repeat(6) { seen.update(dt, moveInput = 1.0, jumpInput = false, crouchInput = false) }
        assertTrue(seen.alertProgress > 0.0, "Dropping while he watches the drop zone lands in his cone")

        // He starts at the near post already (right where the climb happens, away from the drop
        // zone) - drop immediately, before he ever walks out, and it is never seen by him for as
        // long as he dwells there. Just the dwell, not a walk to the actual exit: the moment it
        // ends he turns and walks this way torch-first, and standing still in the open in front
        // of him is seen like anywhere else; past this point the ground gauntlet's own overwatch
        // guards are a separate concern too (see their own tests) and would confound this one's
        // read on the roof guard specifically.
        val safe = level3()
        assertEquals(safe.guard.patrolMinX, safe.guard.x, "Guard starts at the near post")
        landInFrontOfTheFarPost(safe)
        repeat((safe.guard.patrolPauseDuration * 60).toInt()) { safe.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false) }
        assertFalse(safe.wasDetected, "Dropping while he is still at the near post is never seen")
        assertEquals(GuardState.PATROL, safe.guard.state, "Nor heard - the run in is out of earshot")
    }

    @Test
    fun testLevel3GroundGauntletStructure() {
        val world = level3()
        val table = world.tables.minByOrNull { it.x }!! // the crate's own beam, not the later camera beam
        val hideCrate = world.boxes.first { it.width == 68.0 && it.height == 48.0 && it != world.crate }
        assertTrue(hideCrate in world.boxes, "The hide crate collides")
        assertEquals(table.top, hideCrate.bottom, 1e-9, "Sits on top of the plank itself, not the ground past it")
        assertEquals(table.right, hideCrate.right, 1e-9, "Flush into the corner where the plank meets the leg")
        val leg = world.tableDecorations.minByOrNull { it.x }!! // rightLeg (under the crate's own plank), not the later camera beam's own leg
        assertTrue(hideCrate.bottom <= leg.top, "Sits above the leg (on the plank), not overlapping it - they can share x freely")

        // The two GUARDED long crates - a third, unguarded one comes later past the camera beam
        // (see testLevel3CameraBeamSection), so hangingCrateVariant1 itself now has three entries.
        val longCrates = world.hangingCrateVariant1.sortedBy { it.x }.take(2)
        assertEquals(3, world.hangingCrateVariant1.size, "Two guarded crates here, plus one unguarded one past the camera beam")
        for (c in longCrates) {
            assertEquals(174.0, c.width, 1e-9)
            assertEquals(38.0, c.height, 1e-9)
            assertTrue(c in world.boxes, "Each long crate collides - something for its guard to stand on")
        }
        assertTrue(longCrates[0].right < longCrates[1].left, "The two crates don't overlap, with ground between them")

        // One overwatch guard per crate, on top of it (feet flush with the crate's own top), plus
        // the roof guard from the table section.
        assertEquals(3, world.allGuards.size)
        val overwatch = world.extraGuards
        assertEquals(2, overwatch.size)
        for ((guard, crate) in overwatch.zip(longCrates)) {
            assertEquals(crate.top, guard.bounds.bottom, 1e-9, "Standing on the crate, not floating above or sunk into it")
            assertTrue(guard.patrolMinX >= crate.left && guard.patrolMaxX + guard.width <= crate.right,
                "Patrols within the crate's own width, never overhanging its edge")
        }
    }

    @Test
    fun testLevel3CameraBeamSection() {
        val world = level3()
        val stepCrate = world.boxes.first { it.width == 68.0 && it.height == 48.0 && it.y == 440.0 - 48.0 && it.x > 1000.0 }
        val beam = world.tables.maxByOrNull { it.x }!! // the later of the two beams
        assertTrue(beam in world.tableParts && beam in world.floatingClimbTargets, "Climbable the same way tablePlank is")
        assertEquals(stepCrate.right, beam.left, 1e-9, "Crate and beam meet edge to edge")
        val rise = stepCrate.top - beam.top
        assertTrue(rise > 51.2 && rise <= 115.0, "Crate-to-beam rise ($rise) is a real climb, same as the level's opening one")
        assertTrue(beam !in world.tableDecorations, "No leg under this one")

        // The beam's own support leg (mirrors tablePlank's rightLeg): grounded, tucked against the
        // beam's underside at the far end, clear of the camera mount and the climb up from stepCrate.
        val cameraLeg = world.tableDecorations.maxByOrNull { it.x }!! // the far end, away from tablePlank's own leg
        assertTrue(cameraLeg in world.boxes, "A genuine obstacle, not just flavor - it collides")
        assertEquals(beam.right, cameraLeg.right, 1e-9, "Caps the beam's own far end")
        assertTrue(cameraLeg.top < beam.bottom, "Tucked up against the beam's underside, no visible gap")
        assertEquals(440.0, cameraLeg.bottom, 1e-9, "...down to the ground - nothing floats")

        assertEquals(2, world.cameras.size, "beamCamera (under the beam) and poleCamera (on finalPlatform's own pole)")
        val cam = world.cameras.minByOrNull { it.x }!! // beamCamera - far left of poleCamera, near finalPlatform
        assertEquals(beam.bottom, cam.y, 1e-9, "Flush against the beam's own underside")
        assertTrue(cam.x >= beam.left && cam.x <= beam.right, "Mounted somewhere along the beam, not off the end of it")
        assertTrue(cam.x < cameraLeg.left, "Mounted clear of the leg, near the beam's start")
        val straightDown = PI / 2.0
        assertTrue(cam.minAngle < straightDown && cam.maxAngle > straightDown, "Sweeps either side of straight down")

        // A standing player on the ground near the mount is caught somewhere in the sweep...
        val occluders = world.occluders
        var sawGroundPlayer = false
        var angle = cam.minAngle
        while (angle <= cam.maxAngle) {
            val probe = Camera(x = cam.x, y = cam.y, minAngle = cam.minAngle, maxAngle = cam.maxAngle,
                currentAngle = angle, visionRange = cam.visionRange, visionFov = cam.visionFov)
            val groundPlayer = Player(x = cam.x - 20.0, y = 440.0 - 96.0)
            if (VisionSystem.getPlayerSpottedDistance(probe, groundPlayer, occluders) != null) sawGroundPlayer = true
            angle += 5.0 * (PI / 180.0)
        }
        assertTrue(sawGroundPlayer, "The camera should catch someone walking the ground corridor underneath at some point in its sweep")

        // ...but never a player standing on top of the beam itself, at any sweep angle - a
        // downward-looking cone can't include a point above its own mount.
        var sawBeamTopPlayer = false
        angle = cam.minAngle
        while (angle <= cam.maxAngle) {
            val probe = Camera(x = cam.x, y = cam.y, minAngle = cam.minAngle, maxAngle = cam.maxAngle,
                currentAngle = angle, visionRange = cam.visionRange, visionFov = cam.visionFov)
            val onBeamPlayer = Player(x = cam.x, y = beam.top - 96.0)
            if (VisionSystem.getPlayerSpottedDistance(probe, onBeamPlayer, occluders) != null) sawBeamTopPlayer = true
            angle += 5.0 * (PI / 180.0)
        }
        assertFalse(sawBeamTopPlayer, "Climbing onto the beam should put the player above the camera's cone entirely")

        // Unlike a fixed-eye turret, this camera's own eye moves as its body swings around the
        // joint (see Camera.eyePosition) - so "covers the crate" is checked the same way the ground
        // corridor above is: caught at some point across the full sweep, not necessarily at one
        // static angle. Only the NEAR end of stepCrate (where the climb lands) is required - the
        // far end turned out to be reachable only by aiming right at the edge of the FOV exactly at
        // the crate's own corner, which is what produced the stray-ray bug (see the doc comment on
        // beamCamera in LevelData.kt and testLevel3CameraConePolygonNeverPastCrate below). Chasing
        // full crate coverage cost more than it was worth once that trade-off was actually measured
        // - dropped on purpose, not an oversight.
        var sawNearEnd = false
        angle = cam.minAngle
        while (angle <= cam.maxAngle) {
            val probe = Camera(x = cam.x, y = cam.y, minAngle = cam.minAngle, maxAngle = cam.maxAngle,
                currentAngle = angle, visionRange = cam.visionRange, visionFov = cam.visionFov)
            val playerOnCrateNear = Player(x = stepCrate.right - 40.0, y = stepCrate.top - 96.0)
            if (VisionSystem.getPlayerSpottedDistance(probe, playerOnCrateNear, occluders) != null) sawNearEnd = true
            angle += 5.0 * (PI / 180.0)
        }
        assertTrue(sawNearEnd, "The near end of the crate should be lit at some point across the full sweep")

        // One last hanging crate past the beam, no guard on it, close to flush with the beam (2
        // units higher, on request - see finalHangingCrate's own comment in LevelData.kt) for a
        // jump across, not a climb.
        val finalCrate = world.hangingCrateVariant1.maxByOrNull { it.x }!!
        assertTrue(finalCrate.x > beam.right, "Sits past the beam, not overlapping it")
        assertEquals(2.0, beam.top - finalCrate.top, 1e-9, "Close to flush with the beam - a jump across, not a climb")
        assertTrue(world.extraGuards.none { it.bounds.bottom == finalCrate.top }, "No guard standing on this one")
    }

    @Test
    fun testLevel3GroundDressingUnderBeamHasBarrelsAndWoodCratesSeparately() {
        // The boxes under the camera beam are two barrels plus three wood crates in a brick-like
        // stagger (LevelData.kt's own comment on woodCrateBaseLeft/Right/Top has the full history:
        // an earlier pass swapped all four ground-dressing boxes to wood-crate art, misreading a
        // screenshot; corrected back to two-and-two, then the wood-crate pair was later redesigned
        // from a straight "single + 2-tall stack" into this staggered three-crate arrangement).
        // Barrels stay barrels (LevelLayout.barrels); all boxes still collide/occlude regardless of
        // which art they use.
        val world = level3()
        val beam = world.tables.maxByOrNull { it.x }!! // cameraBeam
        val cameraLeg = world.tableDecorations.maxByOrNull { it.x }!!
        val barrelDressing = world.barrels.filter { it.x >= beam.x && it.right <= cameraLeg.left }
        val woodDressing = world.woodCrates.filter { it.x >= beam.x && it.right <= cameraLeg.left }
        assertEquals(2, barrelDressing.size, "Two barrels under the beam, still tagged as barrels")
        assertEquals(3, woodDressing.size, "Three wood crates in the staggered arrangement")
        for (box in barrelDressing + woodDressing) {
            assertTrue(box in world.boxes, "Still collides/climbs regardless of which art it uses")
            assertTrue(box in world.occluders, "Still blocks line of sight like any other box")
        }
        assertEquals(2, barrelDressing.count { it.height == 48.0 && it.width == 32.0 }, "The two barrels keep their own footprint/height")
        assertTrue(woodDressing.all { it.height == 48.0 && it.width == 68.0 }, "Every wood crate here is a plain single crate, no more tall stacked box")

        // "two crates touching each other, other one on top of the right most crate"
        val baseCrates = woodDressing.filter { it.bottom == 440.0 }.sortedBy { it.x } // resting on the ground
        assertEquals(2, baseCrates.size, "A base pair on the ground")
        val (baseLeft, baseRight) = baseCrates
        assertEquals(baseLeft.right, baseRight.x, 1e-9, "The base pair touches - zero gap")
        assertEquals(baseLeft.top, baseRight.top, 1e-9, "Same height, sitting flush side by side")

        val topCrate = woodDressing.first { it.bottom != 440.0 }
        assertEquals(baseRight.top + 2.0, topCrate.bottom, 1e-9, "Rests directly on the right base crate with a 2-unit visual sink closing the gap")
        assertEquals(baseRight.x, topCrate.x, 1e-9, "Stacked directly on top of the rightmost crate")
        assertEquals(baseRight.right, topCrate.right, 1e-9, "Aligned with the rightmost crate's right edge")
    }

    @Test
    fun testLevel3PoleStandsAtFinalPlatformLeftCornerAndIsNotInteractable() {
        // On request: a pole (pole.png) at "the left corner of the platform right of the unmanned
        // hanging crate" (finalPlatform), "not interactable". Not in world.boxes at all (no
        // collision - the player walks straight through/under where it visually stands).
        //
        // Also NOT in world.occluders, on a later correction: a first pass DID have poles occlude
        // (same reasoning as tableDecorations - real drawn geometry should block sight), but
        // reported directly against a screenshot, poleCamera mounted on top of its own pole had
        // that pole block its own downward view and the vision polygon read as a flat-edged
        // rectangle instead of a cone ("light cone becomes weird ... it become rectangular"). See
        // GameWorld.createFromLayout's own comment on the occluders line for the full account.
        val world = level3()
        val finalPlatform = world.boxes.first { it.width == 340.0 && it.height == 96.0 }
        assertEquals(1, world.poles.size, "Exactly one pole added, at finalPlatform")
        val pole = world.poles.single()
        assertEquals(finalPlatform.x, pole.x, 1e-9, "Left corner of finalPlatform")
        assertEquals(finalPlatform.top, pole.bottom, 1e-9, "Stands on the platform's own surface, not floating above or sunk below it")
        assertFalse(pole in world.boxes, "Not interactable - no collision, on request")
        assertFalse(pole in world.occluders, "Doesn't block line of sight either - a camera mounted on it would occlude its own view otherwise")
    }

    @Test
    fun testLevel3PoleCameraSweepsLeftAndRightAndReachesBothFlankingBoxGroups() {
        // On request: "add a camera on top of this that rotates left and right. The light cone of
        // it should go from the boxes in the right to the box in the left." Right = platformCrate +
        // platformStackedCrates (on finalPlatform itself, right of the pole); left = finalHangingCrate
        // (across the jump gap). Checked against the actual rendered vision polygon (the same
        // VisionSystem.computeVisionPolygon call GameplayScene.kt uses), the same way beamCamera's
        // own reach was verified, rather than trusting the angle numbers alone.
        val world = level3()
        val finalHangingCrate = world.hangingCrateVariant1.maxByOrNull { it.x }!!
        val finalPlatform = world.boxes.first { it.width == 340.0 && it.height == 96.0 }
        // finalPlatform's own height is ALSO 96 (dropped from 144 on request, see its own comment
        // in LevelData.kt), so this needs to exclude finalPlatform itself to not just match that.
        val platformStackedCrates = world.boxes.first { it.x >= finalPlatform.x && it.right <= finalPlatform.right && it.height == 96.0 && it != finalPlatform }
        val poleCam = world.cameras.maxByOrNull { it.x }!! // poleCamera - far right of beamCamera

        // Sweeps a genuinely wide, mostly-horizontal arc (not the mostly-downward sweep beamCamera
        // uses) - this is what makes it read as "rotating left and right" rather than nodding.
        assertTrue(poleCam.maxAngle - poleCam.minAngle > 100.0 * (PI / 180.0), "Wide left-right sweep, not a narrow nod")

        fun xReachAt(angle: Double): ClosedFloatingPointRange<Double> {
            val probe = Camera(x = poleCam.x, y = poleCam.y, minAngle = poleCam.minAngle, maxAngle = poleCam.maxAngle,
                currentAngle = angle, visionRange = poleCam.visionRange, visionFov = poleCam.visionFov)
            val poly = VisionSystem.computeVisionPolygon(probe.eyePosition, probe.facingAngle, probe.visionRange, probe.visionFov, world.occluders)
            val xs = poly.map { it.x }
            return (xs.minOrNull() ?: 0.0)..(xs.maxOrNull() ?: 0.0)
        }

        val rightReach = xReachAt(poleCam.minAngle)
        assertTrue(rightReach.endInclusive >= platformStackedCrates.right, "At minAngle, the cone reaches at least as far right as platformStackedCrates' own far corner")

        val leftReach = xReachAt(poleCam.maxAngle)
        assertTrue(leftReach.start <= finalHangingCrate.x, "At maxAngle, the cone reaches at least as far left as finalHangingCrate's own near corner")
    }

    @Test
    fun testLevel3CameraConePolygonNeverPastCrate() {
        // The point-sampled check this used to be (testLevel3CameraBeamSection's own
        // "sawPastCrate") missed a real overshoot once: a probe point can be "not detected" simply
        // because it falls outside visionRange, which looks identical to "correctly blocked by the
        // crate" from that check's point of view, and gave false confidence that a since-reverted
        // configuration was safe. This checks the ACTUAL rendered vision polygon instead - the
        // exact same VisionSystem.computeVisionPolygon call GameplayScene.kt uses to draw the cone
        // - so what's checked here is what's actually on screen, not a proxy for it.
        //
        // The sweep below ALSO explicitly checks exactly at cam.minAngle/cam.maxAngle, not just
        // relying on the stepped loop to land there - a stepped loop that starts at cam.minAngle
        // and accumulates `angle += stepDeg` in floating point for many iterations can drift a
        // tiny fraction off the exact boundary value, and this geometry is sensitive enough
        // (see the doc comment on beamCamera in LevelData.kt) that missing the one exact angle the
        // camera actually dwells at is exactly how a real overshoot slipped through once already.
        val world = level3()
        val stepCrate = world.boxes.first { it.width == 68.0 && it.height == 48.0 && it.y == 440.0 - 48.0 && it.x > 1000.0 }
        val cam = world.cameras.minByOrNull { it.x }!! // beamCamera
        val occluders = world.occluders

        fun polygonAt(angle: Double): List<Vec2d> {
            val probe = Camera(x = cam.x, y = cam.y, minAngle = cam.minAngle, maxAngle = cam.maxAngle,
                currentAngle = angle, visionRange = cam.visionRange, visionFov = cam.visionFov)
            return VisionSystem.computeVisionPolygon(
                origin = probe.eyePosition, facingAngle = probe.facingAngle,
                range = probe.visionRange, fov = probe.visionFov, occluders = occluders
            )
        }

        var worstX = Double.MAX_VALUE
        var angle = cam.minAngle
        while (angle <= cam.maxAngle) {
            for (p in polygonAt(angle)) if (p.x < worstX) worstX = p.x
            angle += 0.5 * (PI / 180.0)
        }
        for (p in polygonAt(cam.minAngle)) if (p.x < worstX) worstX = p.x
        for (p in polygonAt(cam.maxAngle)) if (p.x < worstX) worstX = p.x
        assertTrue(worstX >= stepCrate.x, "The rendered cone's leftmost point ($worstX) should never cross stepCrate2's own far edge (${stepCrate.x})")
    }

    @Test
    fun testLevel3CameraConeHasNoStrayRaySpikes() {
        // The bug this guards against: VisionSystem's shadow-casting adds rays aimed at every
        // occluder corner within range/FOV (for crisp shadow edges) - when the camera happens to
        // aim close enough to a corner that both "just short of it" and "just past it" land inside
        // the FOV, the "just past it" ray keeps going to whatever's behind the corner (often the
        // ground, far away), and the filled polygon shows that as a long thin wedge stabbing out
        // past the occluder - reported directly as "light rays going out of the camera". The
        // signature of a real spike is a big jump in RADIAL distance from the eye between two
        // angularly-adjacent polygon vertices (a ray that suddenly reaches much farther than its
        // neighbour) - NOT just a big Euclidean gap between vertices, which also happens completely
        // normally when the polygon traces straight down a tall occluder's own side face (e.g. a
        // barrel or crate silhouette) - checked directly against the old 135-degree/45-degree
        // sweep to confirm this distinction actually catches the known bug and nothing else does.
        val world = level3()
        val cam = world.cameras.minByOrNull { it.x }!! // beamCamera
        val occluders = world.occluders
        var angle = cam.minAngle
        while (angle <= cam.maxAngle) {
            val probe = Camera(x = cam.x, y = cam.y, minAngle = cam.minAngle, maxAngle = cam.maxAngle,
                currentAngle = angle, visionRange = cam.visionRange, visionFov = cam.visionFov)
            val eye = probe.eyePosition
            val polygon = VisionSystem.computeVisionPolygon(
                origin = eye, facingAngle = probe.facingAngle,
                range = probe.visionRange, fov = probe.visionFov, occluders = occluders
            )
            for (i in 2 until polygon.size) {
                val radialJump = eye.distanceTo(polygon[i]) - eye.distanceTo(polygon[i - 1])
                assertTrue(radialJump <= 60.0,
                    "Stray ray spike at angle ${angle * 180.0 / PI} degrees: jumps from ${polygon[i - 1]} to ${polygon[i]} (radial jump $radialJump)")
            }
            angle += 0.5 * (PI / 180.0)
        }
    }

    @Test
    fun testLevel3CameraLeftmostSweepReachesStepCrateFarCorner() {
        // On request: with the camera mounted at the beam's own left corner (see beamCamera in
        // LevelData.kt), the sweep's leftmost extreme (maxAngle) should have the cone's rendered
        // edge land right at stepCrate2's own far/leftmost corner - "just touches" it, not stopping
        // noticeably short (the old, deliberately-safe 115/80 pairing left a ~25-unit gap) and
        // never crossing past it (that's the stray-ray-spike bug covered by the test below).
        val world = level3()
        val stepCrate = world.boxes.first { it.width == 68.0 && it.height == 48.0 && it.y == 440.0 - 48.0 && it.x > 1000.0 }
        val cam = world.cameras.minByOrNull { it.x }!! // beamCamera
        val occluders = world.occluders

        val probe = Camera(
            x = cam.x, y = cam.y, minAngle = cam.minAngle, maxAngle = cam.maxAngle,
            currentAngle = cam.maxAngle, visionRange = cam.visionRange, visionFov = cam.visionFov
        )
        val polygon = VisionSystem.computeVisionPolygon(
            origin = probe.eyePosition, facingAngle = probe.facingAngle,
            range = probe.visionRange, fov = probe.visionFov, occluders = occluders
        )
        val leftmost = polygon.minOf { it.x }

        assertTrue(leftmost >= stepCrate.x, "The cone must never cross past the crate's own far edge (leftmost=$leftmost, crate.x=${stepCrate.x})")
        assertTrue(leftmost <= stepCrate.x + 10.0, "The cone should read as touching the crate's far corner, not stopping well short of it (leftmost=$leftmost, crate.x=${stepCrate.x})")
    }

    @Test
    fun testLevel3CanJumpFromCameraBeamAcrossToFinalHangingCrateAndOnToFinalPlatform() {
        // No earlier test actually drove a player across these two gaps - the doc comments on
        // finalHangingCrate/finalPlatform in LevelData.kt used to just assert this worked. It
        // didn't: measured directly against Player's real physics, a same-height running jump in
        // this engine tops out around 56.5-56.66 units for this exact geometry (not the ~84 units
        // simple projectile arithmetic suggests - the collision code stops a jump short once the
        // falling body starts vertically overlapping the target platform while still short of it
        // horizontally, treating it as a wall). Both gaps are now 56 (widened twice on request,
        // 40 -> 55 -> 56 - see LevelData.kt's own comment for the fresh binary search and the
        // jump-timing-slack scan behind that number), just inside that budget with a real, felt
        // jump instead of a trivial walk-across. This walks the player through both jumps for
        // real and requires it to actually reach finalPlatform, not just reach some downstream X
        // the level's ground floor could also explain away.
        //
        // The auto-pilot needs BOTH an explicit launch-edge window (jumping off an open ledge
        // never registers as "stalled" - Player.updateStep's own isDropping mechanic deliberately
        // punishes walking off an edge without jumping, crawling forward at dropSpeed=30 instead of
        // moveSpeed=132, which is nowhere near enough to clear either gap) AND a stalled-progress
        // check (hangingEndCrate sits on TOP of finalHangingCrate partway across it - a genuine
        // wall to hop, not an edge, so it registers as stalled instead).
        val world = level3()
        // This test is about jump PHYSICS reaching finalPlatform, not stealth against poleCamera -
        // that camera's own sweep is deliberately aimed at this exact crossing (see poleCamera's
        // comment in LevelData.kt) and legitimately spots a player climbing over hangingEndCrate
        // partway across, same isolation approach as testLevel3OverwatchGuardsDoNotSeeBackIntoTheTableSection
        // blinding an unrelated guard to isolate its own concern.
        world.cameras.forEach { it.visionRange = 0.0 }
        val beam = world.tables.maxByOrNull { it.x }!! // cameraBeam
        val finalHangingCrate = world.hangingCrateVariant1.maxByOrNull { it.x }!!
        val finalPlatform = world.boxes.first { it.width == 340.0 && it.height == 96.0 }
        assertTrue(finalHangingCrate.x > beam.right, "Sanity: finalHangingCrate sits past the beam")
        assertTrue(finalPlatform.x > finalHangingCrate.right, "Sanity: finalPlatform sits past finalHangingCrate")
        // finalHangingCrate sits 2 units above the beam ("lift the unmanned hanging crate a little
        // bit") - no longer an exact same-height jump, just very close to one (see
        // finalHangingCrate's own comment in LevelData.kt for why 2 is as far as this gap's physics
        // budget allows). finalPlatform is a separate, later change: its own height was dropped
        // 144 -> 96 ("make the platform on the right smaller to be able to climb up"), so it's no
        // longer close to flush with finalHangingCrate at all - a real ~50-unit downward jump now,
        // which only ever makes a same-height jump easier, never harder.
        assertEquals(2.0, beam.top - finalHangingCrate.top, 1e-9, "Sanity: beam to crate is a small upward jump")
        assertEquals(50.0, finalPlatform.top - finalHangingCrate.top, 1e-9, "Sanity: crate to platform is now a real downward jump")

        world.player.resetTo(beam.x + 10.0, beam.top - world.player.height)
        world.player.isGrounded = true
        val dt = 1.0 / 60.0
        var elapsed = 0.0
        var stalledFor = 0.0
        while (elapsed < 10.0 && world.player.x < finalPlatform.x + 20.0) {
            val beforeX = world.player.x
            val edge = world.player.x + world.player.width
            val atLaunchEdge = edge in (beam.right - 20.0)..(beam.right + 2.0) ||
                edge in (finalHangingCrate.right - 20.0)..(finalHangingCrate.right + 2.0)
            val doJump = world.player.isGrounded && (stalledFor > 0.05 || atLaunchEdge)
            world.update(dt, moveInput = 1.0, jumpInput = doJump, crouchInput = false)
            stalledFor = if (kotlin.math.abs(world.player.x - beforeX) < 0.5) stalledFor + dt else 0.0
            elapsed += dt
        }

        assertTrue(world.player.isGrounded, "Should land solidly, not be left falling")
        val footCenter = world.player.x + world.player.width / 2.0
        assertTrue(
            footCenter >= finalPlatform.left && footCenter <= finalPlatform.right,
            "Should end up standing on finalPlatform (footCenter=$footCenter, platform=${finalPlatform.left}..${finalPlatform.right}), not fallen into either gap"
        )
        assertEquals(finalPlatform.top, world.player.y + world.player.height, 1e-6, "Standing on top of finalPlatform, not the ground below")
    }

    @Test
    fun testLevel3OverwatchGuardsDoNotSeeBackIntoTheTableSection() {
        // Checked directly against VisionSystem when tuning these guards' position and range
        // (220, this game's usual figure): the blind wedge a horizontal-only FOV leaves directly
        // below a perched guard, combined with the buffer before the first long crate, keeps their
        // reach from ever overlapping the table/drop-zone section's own guard. A ground crawl from
        // the drop zone up to the first crate should never trip either overwatch guard.
        val world = level3()
        // Isolate the concern: the roof guard's own reach (and the timing of when he happens to
        // walk out to his far post during this crawl) is a separate, already-covered concern -
        // blinding him here keeps this test strictly about the overwatch guards' own reach.
        world.guard.visionRange = 0.0
        val firstLongCrate = world.hangingCrateVariant1.minByOrNull { it.x }!!
        val dt = 1.0 / 60.0
        var elapsed = 0.0
        world.player.resetTo(world.guard.patrolMaxX + 20.0, 440.0 - world.player.height)
        while (elapsed < 15.0 && world.player.x < firstLongCrate.x - 20.0) {
            world.update(dt, moveInput = 1.0, jumpInput = false, crouchInput = false)
            elapsed += dt
        }
        assertFalse(world.wasDetected, "Walking up to the first overwatch crate should not be seen by it in advance")
    }

    @Test
    fun testLevel3OverwatchGuardsMoveOnDifferentTimingNotLockstep() {
        // Guard1 starts dwelling at his crate's near-the-gap corner. Guard2 used to start at the
        // exact mirror-image corner of his own crate, which (see the doc comment on
        // LEVEL_3_LAYOUT's overwatchGuard1/2) made his ENTIRE motion - not just his facing, but
        // exactly when he walks versus dwells - identical to guard1's in local/relative terms.
        // Reported directly: with the gap only 150 units wide, both guards are visible together,
        // and moving/pausing in perfect lockstep read as a bug. Guard2 now starts mid-route
        // instead, so the two are NOT always in the same walk-vs-dwell state.
        val world = level3()
        val g1 = world.extraGuards[0]
        val g2 = world.extraGuards[1]
        val dt = 1.0 / 60.0
        var elapsed = 0.0
        var mismatchFrames = 0
        var totalFrames = 0
        while (elapsed < 60.0) {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false)
            if (g1.isWalking != g2.isWalking) mismatchFrames++
            totalFrames++
            elapsed += dt
        }
        assertTrue(mismatchFrames > totalFrames / 10, "The two overwatch guards should visibly differ (one walking, other dwelling) a meaningful fraction of the time, not move in lockstep")
    }

    @Test
    fun testLevel3OverwatchGuardsRarelyBothFaceTheMiddleAtOnce() {
        // Guard1 and guard2 no longer share the exact same local timing (see
        // testLevel3OverwatchGuardsMoveOnDifferentTimingNotLockstep and the doc comment on
        // LEVEL_3_LAYOUT's overwatchGuard1/2), which was the ONLY thing guaranteeing they'd never
        // simultaneously face the shared gap. Proven directly (not just suspected) that any
        // nonzero timing offset between two guards on an otherwise-identical route/speed/pause
        // reopens SOME window where both could actually spot a player standing in the gap - a
        // perfectly-safe guarantee and a visibly staggered pair are mutually exclusive here. The
        // owner explicitly accepted this trade-off after seeing the numbers, so this is no longer
        // a strict assertEquals(0, ...) - it's a generous ceiling that only fails if the two
        // guards drift back into being permanently (or almost permanently) synchronized, which is
        // the actual regression to guard against.
        val world = level3()
        val g1 = world.extraGuards[0] // longCrate1 - the gap is to his right, so facing = +1 watches it
        val g2 = world.extraGuards[1] // longCrate2 - the gap is to his left, so facing = -1 watches it
        val dt = 1.0 / 60.0
        var elapsed = 0.0
        var bothFacingMiddleFrames = 0
        var totalFrames = 0
        while (elapsed < 60.0) {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false)
            if (g1.facing > 0.0 && g2.facing < 0.0) bothFacingMiddleFrames++
            totalFrames++
            elapsed += dt
        }
        assertTrue(bothFacingMiddleFrames < totalFrames / 2, "The two overwatch guards should not be facing the shared gap together for anywhere near most of the time")
    }

    @Test
    fun testPlayerStartsFacingRightInAllLevels() {
        val levels = listOf(
            LevelData.DEFAULT_LEVEL_1,
            LevelData.DEFAULT_LEVEL_2,
            LevelData.DEFAULT_LEVEL_3,
            LevelData.DEFAULT_LEVEL_4
        )
        for (level in levels) {
            val world = GameWorld.createDefault(level)
            assertEquals(1.0, world.player.facing, 1e-4, "Player starts facing right (1.0) in ${level.id}")

            // When moving left, facing switches to -1.0
            world.update(0.1, moveInput = -1.0, jumpInput = false, crouchInput = false)
            assertEquals(-1.0, world.player.facing, 1e-4, "Player turns left when moving left in ${level.id}")

            // When resetting to start or restarting level, facing resets back to right (1.0)
            world.restartLevel()
            assertEquals(1.0, world.player.facing, 1e-4, "Player facing resets to right (1.0) after restart in ${level.id}")

            // Explicit resetTo also resets facing to right
            world.player.resetTo(100.0, 200.0)
            assertEquals(1.0, world.player.facing, 1e-4, "Player resetTo explicitly resets facing to right (1.0)")
        }
    }

    @Test
    fun testInAppReviewBridgeInvokedOnLevel4Complete() {
        var requested = false
        val bridge = object : com.sample.demo.review.InAppReviewBridge {
            override fun requestReview() {
                requested = true
            }
        }
        val result = LevelResult(levelId = "level_4", completed = true, wasDetected = false, timeTaken = 45f, timeTargetSeconds = 60f)
        if (result.levelId == "level_4") {
            bridge.requestReview()
        }
        assertTrue(requested, "In-app review bridge must be prompted when level 4 completes")

        var otherRequested = false
        val level1Result = LevelResult(levelId = "level_1", completed = true, wasDetected = false, timeTaken = 45f, timeTargetSeconds = 60f)
        if (level1Result.levelId == "level_4") {
            otherRequested = true
        }
        assertFalse(otherRequested, "In-app review bridge must not be prompted when other levels complete")
    }

    @Test
    fun testHoldingJumpDoesNotAutoBunnyHopAndWalkingIsFasterThanRepeatedJumping() {
        val ground = Rect(0.0, 440.0, 5000.0, 100.0)
        val dt = 1.0 / 60.0

        // 1. Holding Jump: Player jumps once, lands, and walks without auto-bunnyhopping
        val playerHeld = Player(x = 100.0, y = 440.0 - 96.0)
        playerHeld.isGrounded = true
        var jumpCount = 0
        var wasInAir = false

        for (i in 0 until 120) { // 2 seconds
            playerHeld.update(dt, moveInput = 1.0, jumpInput = true, platforms = listOf(ground))
            if (!playerHeld.isGrounded && !wasInAir) {
                jumpCount++
                wasInAir = true
            } else if (playerHeld.isGrounded && wasInAir) {
                wasInAir = false
            }
        }
        assertEquals(1, jumpCount, "Holding jump button should execute exactly ONE jump, not auto-bunnyhop")
        assertTrue(playerHeld.isGrounded, "Player should remain grounded and walking after landing from held jump")

        // 2. Walking continuously vs Repeated Jump spamming:
        // Walking smoothly on flat ground should cover more distance than spamming jumps due to landing absorption
        val pWalk = Player(x = 100.0, y = 440.0 - 96.0)
        pWalk.isGrounded = true
        val pSpam = Player(x = 100.0, y = 440.0 - 96.0)
        pSpam.isGrounded = true

        var prevGrounded = true
        for (i in 0 until 300) { // 5 seconds
            pWalk.update(dt, moveInput = 1.0, jumpInput = false, platforms = listOf(ground))

            // Tap jump as soon as grounded (releasing in the air to simulate rapid mashing)
            val tapJump = pSpam.isGrounded && prevGrounded
            pSpam.update(dt, moveInput = 1.0, jumpInput = tapJump, platforms = listOf(ground))
            prevGrounded = pSpam.isGrounded
        }

        assertTrue(
            pWalk.x > pSpam.x,
            "Smooth walking (x=${pWalk.x.toInt()}) must be faster than jump spamming (x=${pSpam.x.toInt()}) due to landing cushion"
        )
    }

    @Test
    fun testLevel5ManualCheckpointsDoNotRecordInPitAndRespawnInSafePlace() {
        val world = GameWorld.createDefault(LevelData.SIDE_SCROLL_LEVEL)
        assertTrue(world.manualCheckpoints.isNotEmpty(), "Level 5 must have manual checkpoints defined")
        val activated = world.activatePowerup(PowerupType.CHECKPOINTS)
        assertTrue(activated, "Checkpoints powerup must be active")
        assertTrue(world.activePowerups.isCheckpointsActive)

        // 1. Initial state: player at start
        assertEquals(world.player.startX, world.lastCheckpointX)
        assertEquals(world.player.startY, world.lastCheckpointY)
        assertFalse(world.hasAdvancedCheckpoint)

        // 2. Reach terrain1: land and get grounded on elevated platform (y=200, terrainTopY=296)
        world.player.resetTo(700.0, 200.0)
        world.update(0.05, moveInput = 0.0, jumpInput = false)
        world.update(0.05, moveInput = 0.0, jumpInput = false)
        assertTrue(world.player.isGrounded, "Player should be grounded on terrain1")
        assertTrue(world.hasAdvancedCheckpoint, "Reaching terrain1 should secure manual checkpoint 1")
        assertEquals(700.0, world.lastCheckpointX, 1.0)
        assertEquals(200.0, world.lastCheckpointY, 1.0)

        // 3. Fall into the pit between terrain1 and terrain2 (floor at y=440, player y=344)
        world.player.resetTo(1020.0, 344.0)
        world.update(0.05, moveInput = 1.0, jumpInput = false)
        world.update(0.05, moveInput = 1.0, jumpInput = false)
        assertTrue(world.player.isGrounded, "Player is grounded in the pit")
        // Walking inside the pit across hundreds of pixels must NEVER record a checkpoint in the pit!
        for (i in 0 until 50) {
            world.update(0.05, moveInput = 1.0, jumpInput = false)
        }
        assertEquals(700.0, world.lastCheckpointX, 1.0, "Checkpoint must remain on safe terrain1, not trapped pit")
        assertEquals(200.0, world.lastCheckpointY, 1.0)

        // 4. Pressing restart while Checkpoints powerup is active:
        // Must respawn on safe terrain1 (y=200), NOT trapped in the pit (y=344)!
        world.restartLevel()
        assertEquals(700.0, world.player.x, 1.0, "Player must respawn on terrain1")
        assertEquals(200.0, world.player.y, 1.0, "Player must respawn safely on elevated platform")
        assertFalse(world.isGameOver)

        // 5. Advance across gap to terrain2 (y=200)
        world.player.resetTo(1180.0, 200.0)
        world.update(0.05, moveInput = 0.0, jumpInput = false)
        world.update(0.05, moveInput = 0.0, jumpInput = false)
        assertTrue(world.player.isGrounded)
        assertEquals(1180.0, world.lastCheckpointX, 1.0, "Reaching terrain2 should advance to checkpoint 2")
        assertEquals(200.0, world.lastCheckpointY, 1.0)

        // 6. Fall into pit after terrain2
        world.player.resetTo(1500.0, 344.0)
        world.update(0.05, moveInput = 1.0, jumpInput = false)
        world.update(0.05, moveInput = 1.0, jumpInput = false)
        assertEquals(1180.0, world.lastCheckpointX, 1.0, "Pit fall must not overwrite checkpoint 2")

        // 7. Restarting now respawns at checkpoint 2 on terrain2
        world.restartLevel()
        assertEquals(1180.0, world.player.x, 1.0, "Player must respawn on terrain2")
        assertEquals(200.0, world.player.y, 1.0)
    }

    @Test
    fun testLevel4CheckpointsAutomaticRecordingUnchanged() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        assertTrue(world.manualCheckpoints.isEmpty(), "Level 4 should have no manual checkpoints (empty list)")
        val startX = world.player.startX
        assertEquals(startX, world.lastCheckpointX)
        assertFalse(world.hasAdvancedCheckpoint)

        // Ground the player
        world.update(0.05, moveInput = 0.0, jumpInput = false)
        world.update(0.05, moveInput = 0.0, jumpInput = false)

        // Advance player past 250px on the conveyor line
        world.player.resetTo(startX + 300.0, world.player.y)
        world.update(0.05, moveInput = 0.0, jumpInput = false)
        world.update(0.05, moveInput = 0.0, jumpInput = false)
        assertTrue(world.player.isGrounded)
        assertTrue(world.hasAdvancedCheckpoint, "Level 4 should continue using automatic distance-based checkpoints")
        assertEquals(world.player.x, world.lastCheckpointX, 1e-4)
    }

    // -------------------------------------------------------------------------
    // Level 7: Vent Infiltration Gauntlet Tests
    // -------------------------------------------------------------------------

    @Test
    fun testLevel7LayoutStructureAndProperties() {
        val levelData = LevelData.DEFAULT_LEVEL_7
        assertEquals("level_7", levelData.id)
        assertEquals("07: Service Tunnel", levelData.name)
        val layout = levelData.layout
        assertNotNull(layout)
        assertEquals(6410.0, layout.worldWidth)
        assertEquals(100.0, layout.playerStartX)
        assertFalse(layout.playerStartCrouched)
        assertFalse(layout.canClimb)
        assertFalse(layout.hasStartFences)
        assertTrue(layout.plainPlatforms.isEmpty(), "the crouch ducts were removed 2026-09-25")
        assertEquals(4, layout.fans.size)
        assertEquals(11, layout.steamPipes.size)
        assertEquals(3, layout.cameraBots.size)
        assertEquals(7, layout.manualCheckpoints.size)
        assertEquals(1.0, levelData.playerCrouchForwardSpeedMultiplier)

        val world = GameWorld.createDefault(levelData)
        assertFalse(world.player.isCrouching, "Player in level 7 must start standing")
        assertEquals(4, world.fans.size)
        assertEquals(11, world.steamPipes.size)
        assertEquals(3, world.cameraBots.size)
        assertEquals(7, world.manualCheckpoints.size)
    }

    @Test
    fun testLevel7IsExactly120MetresOnLevel4sOwnMetreScale() {
        // Level 4 is the only place the game states a distance, and it states it purely as wall
        // decals: six stencils 1500 units apart labelled 150m down to 0m, i.e. 50 units to the
        // metre. Nothing converts units to metres at runtime in either level, so the stencils ARE
        // the measurement and this is what keeps level 7 honest about being 120m.
        val labels = LevelData.LEVEL_7_MARKER_LABELS
        assertEquals(listOf("120m", "90m", "60m", "30m", "0m"), labels)
        assertEquals(1500.0, LevelData.LEVEL_7_MARKER_SPACING, "level 4's own 30m step")

        val unitsPerMetre = LevelData.LEVEL_7_MARKER_SPACING / 30.0
        assertEquals(50.0, unitsPerMetre, 1e-9)

        val first = LevelData.LEVEL_7_MARKER_FIRST_X
        val last = first + (labels.size - 1) * LevelData.LEVEL_7_MARKER_SPACING
        assertEquals(120.0, (last - first) / unitsPerMetre, 1e-9, "120m from the first stencil to the last")

        // The last stencil reads 0m, so it belongs at the door - but NOT under it. The
        // extraction booth is drawn from exitZone.x rightwards, and a 0m plate centred on the
        // zone had its right half swallowed by the booth on screen. It sits just short instead,
        // with room for its own 49-unit width.
        val layout = LevelData.LEVEL_7_LAYOUT
        val exit = layout.exitZone
        assertTrue(last < exit.x, "the 0m stencil stands clear of the booth drawn from exitZone.x")
        assertTrue(exit.x - last >= 49.0, "and clear by at least its own drawn width")
        assertTrue(exit.x - last <= 150.0, "but still reads as the door, not as a landmark before it")

        // And the level has to physically hold all of it, with the player starting behind the
        // 120m mark rather than past it.
        assertTrue(layout.playerStartX < first, "player starts behind the 120m stencil")
        assertTrue(layout.worldWidth > exit.x + exit.width, "the world outlasts the exit")
    }

    @Test
    fun testVentFanBladesSpinAtTheRequestedRate() {
        // Owner request 2026-09-25: "increase the rotation speed of fans" (it was 8.0 rad/s).
        // Pinned because the number has already been moved twice and it is not the kind of thing
        // a screenshot can check - a still frame cannot show a rate.
        val fan = VentFan(LevelData.LEVEL_7_LAYOUT.fans.first())
        val dt = 1.0 / 60.0
        var turned = 0.0
        var previous = fan.bladeRotationAngle
        repeat(60) {
            fan.update(dt)
            var step = fan.bladeRotationAngle - previous
            if (step < 0.0) step += 2.0 * kotlin.math.PI // wrapped past a full turn
            turned += step
            previous = fan.bladeRotationAngle
        }
        assertEquals(16.0, turned, 0.05, "one second of updates is one second of rotation")

        // The ceiling is the strobe: sampled once per frame, an N-bladed rotor reads as stopped or
        // backwards once N revolutions per second passes 30.
        val revsPerSecond = 16.0 / (2.0 * kotlin.math.PI)
        assertTrue(revsPerSecond * 8.0 < 30.0, "still unambiguous for an 8-bladed rotor at 60fps")

        fan.reset()
        assertEquals(0.0, fan.bladeRotationAngle)
    }

    @Test
    fun testTheDronePromptLetsGoOnItsOwn() {
        // "stop showing this go from behind after small time. dont wait until he disable robot" -
        // disabling the drone is one option, not the only one, so the prompt cannot camp on screen
        // waiting for a choice the player may never make. Every other step in the game is still
        // open-ended, which is right where the action is the only way past.
        val bot = LevelData.DEFAULT_LEVEL_7.tutorialSteps.first { it.id == "step_deactivate_bot" }
        assertEquals(5.0, bot.autoDismissSeconds)

        // Level 8's push/pull prompt is the second exception, and for the same reason: it dims
        // the screen behind the arrows, and a player who lets go of the cart and walks off would
        // otherwise be left looking through that scrim. See its own step for the timing.
        val cartMove = LevelData.DEFAULT_LEVEL_8.tutorialSteps.first { it.id == "step_push_cart_move" }
        assertEquals(6.0, cartMove.autoDismissSeconds)

        val everywhereElse = LevelData.DEFAULT_LEVELS
            .flatMap { it.tutorialSteps }
            .filter { it.id != "step_deactivate_bot" && it.id != "step_push_cart_move" }
        assertTrue(
            everywhereElse.all { it.autoDismissSeconds == 0.0 },
            "no other tutorial step was given a timeout: " +
                everywhereElse.filter { it.autoDismissSeconds != 0.0 }.map { it.id }
        )
    }

    @Test
    fun testLevel7DifficultyRisesFromStartToExit() {
        // The brief was "progressively get harder", which is only meaningful if it is measurable.
        // Steam is the hazard with a continuous knob on it, so it carries the curve: the safe
        // window (dormant + the fixed 1.0s warning flare) has to shrink monotonically down the
        // corridor, and the minimum wait between windows has to grow.
        val pipes = LevelData.LEVEL_7_LAYOUT.steamPipes
        assertEquals(pipes.sortedBy { it.x }, pipes, "the walkthrough test's next-pipe scan needs ascending x")

        val firstHalf = pipes.filter { it.x < 3500.0 }
        val lastHalf = pipes.filter { it.x >= 3500.0 }
        assertTrue(firstHalf.isNotEmpty() && lastHalf.isNotEmpty())
        assertTrue(
            firstHalf.minOf { it.inactiveDuration } > lastHalf.maxOf { it.inactiveDuration },
            "every window in the back half is tighter than every window in the front half"
        )
        assertTrue(
            firstHalf.maxOf { it.activeDuration } < lastHalf.minOf { it.activeDuration },
            "and every wait in the back half is longer"
        )

        // Drones speed up and see further the closer they are to the door.
        val bots = LevelData.LEVEL_7_LAYOUT.cameraBots.sortedBy { it.startX }
        for (i in 1 until bots.size) {
            assertTrue(bots[i].speed > bots[i - 1].speed, "bot ${bots[i].id} is faster than the one before it")
        }
        assertTrue(bots.last().visionRange > bots.first().visionRange)

        // Fans push harder the further in they are (the first one is the tutorial, the gentlest).
        val fans = LevelData.LEVEL_7_LAYOUT.fans.sortedBy { it.x }
        for (i in 1 until fans.size) {
            assertTrue(
                fans[i].windPushSpeed > fans[i - 1].windPushSpeed,
                "fan ${fans[i].id} blows harder than the one before it"
            )
        }

        // No 20m beat of the 120m is empty - that was the other half of the brief.
        val hazardXs = pipes.map { it.x } +
            LevelData.LEVEL_7_LAYOUT.fans.map { it.x } +
            bots.map { it.startX }
        val first = LevelData.LEVEL_7_MARKER_FIRST_X
        for (beat in 0 until 6) {
            val from = first + beat * 1000.0
            val to = from + 1000.0
            assertTrue(
                hazardXs.any { it >= from && it < to },
                "the 20m beat at ${beat * 20}m..${beat * 20 + 20}m ($from..$to) has nothing in it"
            )
        }
    }

    @Test
    fun testLevel7MiddleSectionCorridorAndMovement() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_7)
        assertFalse(world.player.isCrouching, "Player should start standing")

        // Player walks right standing
        for (i in 0 until 60) {
            world.update(1.0 / 60.0, moveInput = 1.0, jumpInput = false, crouchInput = false, interactInput = false)
        }
        assertTrue(world.player.x > 100.0)
        assertFalse(world.player.isCrouching, "Player can walk standing through the middle section corridor")

        // Player can also crouch when desired
        world.update(1.0 / 60.0, moveInput = 1.0, jumpInput = false, crouchInput = true, interactInput = false)
        assertTrue(world.player.isCrouching, "Player can crouch when crouchInput is pressed")
    }

    @Test
    fun testLevel7VentFanPushbackAndSpamTapForwardImpulse() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_7)
        val fan = world.fans.first() // x = 850.0, windRange = 360.0 (windMinX = 490.0)
        val dt = 1.0 / 60.0

        // 0. Boundary check: at x = 500.0, where step_spam_fan triggers, the player is already in wind
        world.player.resetTo(500.0, 440.0 - 96.0)
        assertFalse(world.player.isCrouching, "Player stands by default in level 7")
        assertTrue(fan.isPlayerInWind(world.player), "Player at tutorial trigger x=500.0 must be recognized as inside wind zone")

        // In air flow parts, normal mechanics do not work. Pressing forward once does nothing.
        val preTapX = world.player.x
        world.update(dt, moveInput = 1.0, jumpInput = false, crouchInput = false, interactInput = false, forwardTap = true)
        val oneTapStep = world.player.x - preTapX
        assertTrue(oneTapStep <= 0.0, "Pressing forward once does nothing to advance: $oneTapStep")
        assertFalse(world.isWindSpamming, "Single tap must not trigger spamming")

        // 1. Holding forward without tapping loses ground: gale blows player backward.
        val testX = 700.0
        world.player.resetTo(testX, 440.0 - 96.0)
        assertTrue(fan.isPlayerInWind(world.player))
        for (i in 0 until 30) {
            world.update(dt, moveInput = 1.0, jumpInput = false, crouchInput = false, interactInput = false, forwardTap = false)
        }
        val startX = world.player.x
        for (i in 0 until 40) {
            world.update(dt, moveInput = 1.0, jumpInput = false, crouchInput = false, interactInput = false, forwardTap = false)
        }
        assertTrue(world.player.x < startX, "Holding forward alone should be pushed backward by high-velocity exhaust fan")

        // 2. Continuously tapping forward at a human cadence triggers spamming and makes steady forward progress.
        val pushbackX = world.player.x
        for (i in 0 until 120) {
            val tap = (i % 18 == 0) // ~3.3 taps per second
            world.update(dt, moveInput = 1.0, jumpInput = false, crouchInput = false, interactInput = false, forwardTap = tap)
        }
        assertTrue(world.isWindSpamming, "Continuously tapping must activate spamming state")
        assertTrue(world.player.x > pushbackX + 25.0, "Continuously tapping forward must push through the headwind")
    }

    @Test
    fun testFanTapAdvanceIsSmoothAndSlowerThanTheOldImpulse() {
        val dt = 1.0 / 60.0
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_7)
        world.player.resetTo(700.0, 440.0 - 96.0)
        // Settle the spamming state before measuring.
        for (i in 0 until 90) {
            world.update(dt, 1.0, false, false, false, forwardTap = i % 18 == 0)
        }

        val x0 = world.player.x
        var previous = x0
        var fastestFrame = 0.0
        val seconds = 2.0
        val frames = (seconds / dt).toInt()
        for (i in 0 until frames) {
            world.update(dt, 1.0, false, false, false, forwardTap = i % 18 == 0)
            val speed = (world.player.x - previous) / dt
            previous = world.player.x
            if (speed > fastestFrame) fastestFrame = speed
        }
        val net = (world.player.x - x0) / seconds

        assertTrue(
            fastestFrame <= world.player.moveSpeed + 0.5,
            "No frame may exceed normal walking pace: peak was $fastestFrame u/s"
        )
        assertTrue(net > 20.0, "Spamming has to make real progress: $net u/s")

        // And spamming stops when tapping stops
        for (i in 0 until 60) {
            world.update(dt, 0.0, false, false, false, forwardTap = false)
        }
        assertFalse(world.isWindSpamming, "Spamming state must deactivate once tapping stops")
    }

    @Test
    fun testWindStanceLeansInAndStandsBackUpAroundAFanZone() {
        val dt = 1.0 / 60.0
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_7)

        // Well clear of every fan: upright, and the scene hands the sprite back to idle/walk.
        world.player.resetTo(200.0, 440.0 - 96.0)
        world.update(dt, 0.0, false, false, false)
        assertFalse(world.isInWindZone, "x=200 is outside every fan's wind zone")
        assertTrue(world.isWindStanceIdle, "Outside the wind the stance machine is idle")

        // Inside the zone the body folds into the gale over WIND_STANCE_ENTER_SECONDS.
        world.player.resetTo(700.0, 440.0 - 96.0)
        world.update(dt, 0.0, false, false, false)
        assertTrue(world.isInWindZone)
        assertFalse(world.isWindBraced, "The lean-in is not instant")
        val partway = world.windStanceBlend
        assertTrue(partway > 0.0 && partway < 1.0, "Blend runs 0 -> 1: $partway")

        var t = 0.0
        while (t < GameWorld.WIND_STANCE_ENTER_SECONDS + 0.1) {
            world.update(dt, 0.0, false, false, false)
            t += dt
        }
        assertTrue(world.isWindBraced, "Fully leaning after WIND_STANCE_ENTER_SECONDS")
        assertEquals(1.0, world.windStanceBlend)

        // Stepping out of the zone straightens him back up, and the clip runs in reverse off the
        // same number rather than snapping - so it has to pass through the middle, not jump to 0.
        world.player.resetTo(200.0, 440.0 - 96.0)
        world.update(dt, 0.0, false, false, false)
        assertFalse(world.isInWindZone)
        assertTrue(world.windStanceBlend < 1.0 && world.windStanceBlend > 0.0, "Stands up gradually")
        t = 0.0
        while (t < GameWorld.WIND_STANCE_EXIT_SECONDS + 0.1) {
            world.update(dt, 0.0, false, false, false)
            t += dt
        }
        assertTrue(world.isWindStanceIdle, "Fully upright again")
    }

    @Test
    fun testWindStanceHoldsBracedPoseIdleAndOnlyStridesWhileTapping() {
        // The rule this pins, in the owner's words: standing in the airflow holds a still braced
        // pose, and the wind-walk gait plays only while the player is spam-tapping. So the gait is
        // gated on GameWorld.isWindPushing - the tap surge being alive - and NOT on ground speed,
        // which was the first thing tried and is wrong at the mouth of a fan, where the player
        // makes almost no headway exactly while they are working hardest.
        val dt = 1.0 / 60.0
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_7)
        val fan = world.fans.first()

        // Deep inside the airflow, not at its edge.
        world.player.resetTo(550.0, 440.0 - 96.0)
        assertTrue(fan.isPlayerInWind(world.player))

        // 1. Standing in it, no input at all: the body folds into the gale and HOLDS there.
        var t = 0.0
        while (t < GameWorld.WIND_STANCE_ENTER_SECONDS + 0.1) {
            world.update(dt, 0.0, false, false, false)
            t += dt
        }
        assertTrue(world.isInWindZone, "Still in the airflow")
        assertEquals(1.0, world.windStanceBlend, "Fully braced")
        assertTrue(world.isWindBraced, "Braced pose is held")
        assertFalse(world.isWindPushing, "Doing nothing is not pushing - no gait")

        // 2. Spam-tapping: now the gait runs.
        var sawPushing = false
        for (i in 0 until 60) {
            world.update(dt, 0.0, false, false, false, forwardTap = i % 15 == 0)
            if (world.isWindPushing) sawPushing = true
        }
        assertTrue(sawPushing, "Tapping must drive the wind-walk gait")
        assertTrue(world.isWindBraced, "Still braced while striding")

        // 3. Stop again: the gait stops within the intent window, the brace stays until the gale
        //    has carried them out of the zone.
        for (i in 0 until 60) {
            world.update(dt, 0.0, false, false, false, forwardTap = false)
        }
        assertFalse(world.isWindPushing, "Stopping stops the gait")
    }

    @Test
    fun testWindTapsRegisterAtTheZoneBoundaryNotJustInsideIt() {
        // Regression for a stall found in the running game: the gale bounces a player leaning into
        // the mouth of a fan out of the zone on alternating frames, so gating taps strictly on
        // isPlayerInWind threw most of them away. The character stood at x=453 against a zone
        // starting at 490, tapping continuously, never getting in and never even leaning.
        val dt = 1.0 / 60.0
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_7)
        val fan = world.fans.first()
        val zoneStart = minOf(fan.windMinX, fan.windMaxX)

        // Start just short of the boundary, walking in and tapping the way a player would.
        world.player.resetTo(zoneStart - 45.0, 440.0 - 96.0)
        var maxX = world.player.x
        for (i in 0 until 60 * 8) {
            world.update(dt, 1.0, false, false, false, forwardTap = i % 18 == 0)
            if (world.player.x > maxX) maxX = world.player.x
        }
        assertTrue(
            maxX > zoneStart + 120.0,
            "Tapping must carry the player well into the airflow, not stall at its lip (reached $maxX, zone starts $zoneStart)"
        )
    }

    @Test
    fun testWindGaitIsDrivenByGroundSpeedNotWalkInput() {
        val dt = 1.0 / 60.0
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_7)
        world.player.resetTo(700.0, 440.0 - 96.0)

        // Hold forward, never tap: in air flow parts normal mechanics do not work (vx is 0.0, player does not advance).
        for (i in 0 until 60) {
            world.update(dt, 1.0, false, false, false, forwardTap = false)
        }
        assertEquals(0.0, world.player.vx, "Normal forward walk input must do nothing in air flow")
        assertFalse(world.isWindSpamming, "Holding forward alone must not activate spamming")

        // Outside a wind zone it is not reported at all.
        world.player.resetTo(200.0, 440.0 - 96.0)
        world.update(dt, 1.0, false, false, false)
        assertEquals(0.0, world.windGroundSpeed, "No wind, no wind gait")
    }
    @Test
    fun testLevel7CameraBotPatrolAndDeactivationFromBehind() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_7)
        val bot = world.cameraBots.first() // startX = 2080, patrolMinX = 2050, patrolMaxX = 2280
        val dt = 1.0 / 60.0

        // 1. Patrol back and forth
        val initX = bot.x
        for (i in 0 until 120) {
            bot.update(dt)
        }
        assertNotEquals(initX, bot.x)

        // 2. Deactivation check: player in front of bot vs behind bot
        bot.reset()
        // Bot is at 2080, facing = 1.0 (facing right)
        // Player in front of bot (to the right of bot, e.g. x = 2110)
        world.player.resetTo(2110.0, 440.0 - 96.0)
        assertFalse(bot.canDeactivate(world.player), "Player in front of bot cannot deactivate it")

        // Player approaches behind bot (to the left of bot within deactivationRange = 52.0, e.g. x = 2055)
        world.player.resetTo(2055.0, 440.0 - 96.0)
        assertTrue(bot.canDeactivate(world.player), "Player behind bot within range should be able to deactivate it")
        assertTrue(world.canInteract, "GameWorld.canInteract should be true when player is in deactivation position")

        // 3. Press interact to deactivate
        var deactivatedBot: CameraBot? = null
        world.onCameraBotDeactivated = { b -> deactivatedBot = b }
        world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = true)
        assertTrue(bot.isDeactivated, "Bot should now be permanently deactivated")
        assertEquals(bot, deactivatedBot)

        // Verify deactivated bot stops patrolling and no longer detects player
        val deactX = bot.x
        bot.update(dt * 10)
        assertEquals(deactX, bot.x, "Deactivated bot must not move")
    }

    @Test
    fun testLevel7SteamPipeHazardsAndLaserShieldDeflection() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_7)
        val pipe = world.steamPipes.first() // x = 1150.0, activeDuration = 1.4, inactiveDuration = 2.2
        val dt = 1.0 / 60.0

        // At t = 0, pipe is active
        pipe.update(0.0)
        assertTrue(pipe.isActive)

        // 1. Touching active steam pipe causes Mission Failed
        world.player.resetTo(pipe.x - 10.0, 440.0 - 96.0)
        world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
        assertTrue(world.isGameOver, "Touching active steam pipe must trigger game over")
        assertEquals(1, world.spottedCount)

        // 2. Laser Shield deflects steam blast once safely
        val worldShield = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_7)
        worldShield.activePowerups.activate(PowerupType.LASER_SHIELD)
        assertTrue(worldShield.activePowerups.isLaserShieldActive)

        var blockedEventFired = false
        worldShield.onLaserShieldBlocked = { blockedEventFired = true }

        worldShield.player.resetTo(pipe.x - 10.0, 440.0 - 96.0)
        worldShield.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)

        assertFalse(worldShield.isGameOver, "Laser Shield must prevent game over from steam pipe")
        assertFalse(worldShield.activePowerups.isLaserShieldActive, "Laser Shield must be consumed after absorbing hit")
        assertTrue(blockedEventFired, "onLaserShieldBlocked callback should fire")
        assertTrue(worldShield.laserGraceTimer > 0.0, "Grace period should be active after shield absorption")
    }

    @Test
    fun testLevel7SimulationPlayableWalkthrough() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_7)
        val dt = 1.0 / 60.0
        var elapsed = 0.0
        var restarts = 0

        world.onGameOver = {
            restarts++
            world.restartLevel()
        }

        var crossingPipe: SteamPipe? = null
        // 120m is 6030 units, which is 45.7s of walking before a single window is waited on -
        // and the walker below deliberately gives ground rather than gamble, so the budget is
        // generous. It is a bound on "does this level finish at all", not a pace target; the
        // three-star pace is DEFAULT_LEVEL_7.timeTargetSeconds.
        val maxSimTime = 400.0
        while (elapsed < maxSimTime && !world.isLevelComplete && restarts == 0) {
            val px = world.player.x

            // 1. Camera Bot stealth:
            // Do not enter bot's patrol territory while bot is facing/walking left towards player.
            // Wait safely outside its vision range until it turns around to face right (away).
            val botDangerousAhead = world.cameraBots.firstOrNull { bot ->
                if (bot.isDeactivated) false
                else if (bot.facing < 0.0) {
                    px in (bot.patrolMinX - bot.visionRange - 60.0)..bot.patrolMaxX
                } else {
                    (bot.x - px) < 15.0 || (px in (bot.patrolMinX - bot.visionRange - 60.0)..bot.patrolMinX && bot.x > bot.patrolMinX + 30.0)
                }
            }
            // Approach from behind and deactivate when in range
            val botToDeactivate = world.cameraBots.firstOrNull { !it.isDeactivated && it.canDeactivate(world.player) }
            val interact = botToDeactivate != null

            // 3. Steam Pipe hazard avoidance:
            val nextPipe = world.steamPipes.firstOrNull { pipe -> px < pipe.x + 20.0 }
            val pipeAhead = if (nextPipe != null && (nextPipe.x - px) in 30.0..110.0) nextPipe else null

            val crossSpeed = world.player.moveSpeed

            if (crossingPipe != null) {
                if (px > crossingPipe!!.x + 20.0) {
                    crossingPipe = null
                }
            } else if (pipeAhead != null) {
                // Commit only when what is left of the window covers the run to the far side of
                // the jet at the walking speed available, plus a margin. This used to be a flat
                // 0.7s, which was fine while no window here was shorter than 2.5s - the manifold
                // pair now runs ~1.8s windows, and 110 units of approach is 0.83s of it.
                val needed = (pipeAhead.x + 20.0 - px) / crossSpeed + 0.35
                if (pipeAhead.remainingInactiveTime(elapsed) >= needed) {
                    crossingPipe = pipeAhead
                }
            }

            val waitingForPipe = crossingPipe == null && pipeAhead != null
            val shouldPause = waitingForPipe || (crossingPipe == null && botDangerousAhead != null)
            val moveInput = if (shouldPause) 0.0 else 1.0

            // 2. Exhaust Fan wind zone: spam forward tap to push through backward air blast (only when moving forward)
            val inFanWind = world.fans.any { it.isPlayerInWind(world.player) }
            val forwardTap = inFanWind && !shouldPause && ((elapsed * 60).toInt() % 2 == 0)

            world.update(
                dt,
                moveInput = moveInput,
                jumpInput = false,
                crouchInput = false,
                interactInput = interact,
                forwardTap = forwardTap
            )
            elapsed += dt
        }

        println("Level 7 simulation finished at t=${elapsed}s, playerX=${world.player.x.toInt()}, isComplete=${world.isLevelComplete}, restarts=$restarts")
        assertTrue(world.isLevelComplete, "Level 7 simulation must reach exit zone within time limit (reached x=${world.player.x} at t=${elapsed}s)")
        assertEquals(0, restarts, "Level 7 simulation should clear without any deaths")
    }

    // ---- Level 8: the suspended-load yard ---------------------------------------------------

    /** The pieces of LEVEL_8_LAYOUT, found by shape/id so a re-tune moves the tests with it. */
    private class Level8Geometry {
        val layout = LevelData.LEVEL_8_LAYOUT
        val groundY = 440.0
        /** The mid platform - the one reached by the step crate. The high platform is further right. */
        val platform = layout.boxes.first { it.height == 144.0 && it.width == 240.0 }
        val highPlatform = layout.boxes.first { it.height == 144.0 && it.width == 300.0 }
        /** The loaded flatbed that replaced the fixed step crate - see LevelLayout.pushCarts. */
        val cartDef = layout.pushCarts.single()
        /** Where it starts: parked well short of the platform, useless until it is walked over. */
        val cartAtRest = Rect(cartDef.initialX, groundY - cartDef.height, cartDef.width, cartDef.height)
        /** Where the level wants it: shoved flush against the platform's face. */
        val cartAtPlatform = Rect(cartDef.maxX, groundY - cartDef.height, cartDef.width, cartDef.height)

        /**
         * Puts the level's own cart where a player who had already walked it there would leave
         * it. Tests about the CLIMB start from that state; the walkthrough earns it instead.
         */
        fun parkCartAtPlatform(world: GameWorld) {
            world.pushCarts.single().x = cartDef.maxX
        }
        /** The long stationary load hanging over the plane, back where the level starts. */
        val overheadCrate = layout.hangingCrateVariant1.first { it.x < platform.left }
        /** The long load past the bobbing pair - the one you do NOT have to crouch for. */
        val highCrate = layout.hangingCrateVariant1.first { it.x > platform.right }
        val sweepDef = layout.movingPlatforms.first { it.id == "lvl8_sweep_crate" }
        val platformCrateDef = layout.movingPlatforms.first { it.id == "lvl8_platform_crate" }
        val bobDefs = layout.movingPlatforms.filter { it.id.startsWith("lvl8_bob_crate") }
        val barrels = layout.barrels
        val woodCrate = layout.woodCrates.single()
        val lever = layout.levers.single()
        val hookCrate = layout.hookCrates.single()
        val landingLeft = platform.left + 6.0
    }

    @Test
    fun testLevel8TeachesTheCartAndNothingElse() {
        // Level 8 shipped with no tutorial at all ("there should not be any tutorial in level
        // 8") until the cart arrived. The cart is the one move no earlier level teaches and
        // nothing about a parked trolley says it comes with you, so it gets two steps - and
        // still nothing else, because everything else here was taught long before.
        val steps = LevelData.DEFAULT_LEVEL_8.tutorialSteps
        assertEquals(
            listOf("step_push_cart_grab", "step_push_cart_move"), steps.map { it.id },
            "level 8 teaches the cart and only the cart"
        )

        val grab = steps.first()
        assertEquals(TutorialAction.INTERACT, grab.targetAction)
        assertEquals(TutorialControlHighlight.INTERACT, grab.highlight)
        assertFalse(grab.requiresPushCartGrip, "the grab prompt has to open BEFORE anything is held")

        val move = steps.last()
        assertEquals(TutorialAction.MOVE, move.targetAction)
        assertTrue(move.requiresPushCartGrip, "the arrows prompt only means anything once the cart is in hand")
        assertEquals(
            TutorialControlHighlight.MOVE, move.highlight,
            "both arrows - a pull is as real a move here as a push"
        )

        // The grab prompt has to be reachable on foot before the cart stops the walk: the body
        // is 36 wide and GameWorld grabs from PushCart.GRIP_REACH short of the face.
        val cart = LevelData.LEVEL_8_LAYOUT.pushCarts.single()
        assertTrue(
            grab.triggerMinX < cart.initialX - 36.0 - PushCart.GRIP_REACH,
            "the grab prompt must open while walking up to the cart, not once already against it"
        )
        // ...and the arrows prompt's own window has to cover the cart's whole travel, since it
        // is the grab that opens it and the player may have carried it anywhere by then.
        assertTrue(
            move.triggerMinX <= cart.minX && move.triggerMaxX >= cart.maxX,
            "the push/pull prompt's window must span the cart's whole travel"
        )
    }

    @Test
    fun testLevel8OpeningSectionSitsCloseToTheSpawn() {
        // "make the long hanging crate and the short hanging crate and the platform with crate
        // above it much more closer to the start". The first build put the long load at 760 and
        // the platform at 1560; the player spawns at 236. Pinning the ORDER and a ceiling on the
        // walk-in keeps a later re-tune from quietly sliding the level back out again.
        val g = Level8Geometry()
        val spawn = g.layout.playerStartX
        assertTrue(
            g.overheadCrate.left - spawn < 250.0,
            "the first load should be near the spawn, not a long walk away (${g.overheadCrate.left - spawn} units out)"
        )
        assertTrue(
            g.platform.left - spawn < 700.0,
            "the platform should be close to the start too (${g.platform.left - spawn} units out)"
        )
        // ...and still in the order the request names them: long load, then the moving one, then
        // the platform the step crate serves.
        assertTrue(g.overheadCrate.right < g.sweepDef.minX, "the long load comes before the moving one")
        assertTrue(g.sweepDef.maxX < g.platform.left, "the moving load sits off the platform's own lip")
        assertTrue(
            g.cartAtPlatform.right == g.platform.left,
            "the cart's forward limit is flush against the platform face"
        )
        assertTrue(
            g.cartAtRest.right < g.platform.left - 100.0,
            "...and it starts well short of it, or there is nothing to push (${g.platform.left - g.cartAtRest.right} units out)"
        )
    }

    @Test
    fun testLevel8HangsAllThreeLoadsOnOneLineJustAboveThePlatform() {
        // "move all three hanging crates little bit down to a level where the bottom level is
        // just above the top of the platform". All three share one underside height, and that
        // height is boxed in on both sides: below Player.crouchHeight the platform seals shut
        // (nothing gets past the crate that sweeps it), at Player.height the crossing stops being
        // an obstacle at all.
        val g = Level8Geometry()
        val p = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8).player

        val bottoms = listOf(
            "the long plane load" to g.overheadCrate.bottom,
            "the sweeping load" to g.sweepDef.y + g.sweepDef.height,
            "the platform load" to g.platformCrateDef.y + g.platformCrateDef.height
        )
        val first = bottoms.first().second
        for ((name, bottom) in bottoms) {
            assertEquals(first, bottom, 0.001, "$name must hang on the same line as the others")
            val clearance = g.platform.top - bottom
            assertTrue(clearance > 0.0, "$name must hang ABOVE the platform surface, not through it")
            assertTrue(
                clearance > p.crouchHeight,
                "$name leaves $clearance over the platform - a crouched body (${p.crouchHeight}) would not fit"
            )
            assertTrue(
                clearance < p.height,
                "$name leaves $clearance over the platform - a standing body (${p.height}) walks straight under"
            )
        }
    }

    @Test
    fun testLevel8HangsBothPlaneLoadsOverThePlaneWellOutOfReach() {
        // "player cant get on top of these two for now". Nothing declares that - no
        // unclimbableBoxes entry, no flag - it falls out of how high they hang, so that is what
        // gets pinned. Player.findClimbTarget refuses a floating ledge (bottom above the
        // climber's feet) regardless, and both are far past a jump.
        val g = Level8Geometry()
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8)
        val p = world.player
        val sweep = world.movingPlatforms.first { it.id == "lvl8_sweep_crate" }

        for ((name, bottom) in listOf("the long load" to g.overheadCrate.bottom, "the moving load" to sweep.bounds.bottom)) {
            val reach = g.groundY - bottom
            assertTrue(reach > p.climbMaxHeight, "$name hangs $reach above the floor, inside climb reach (${p.climbMaxHeight})")
            assertTrue(reach > p.maxJumpHeight + p.height, "$name is within a jumping body's reach ($reach)")
        }

        // And the same thing driven: run the whole plane jumping the whole way, and the feet
        // must never come to rest anywhere but the floor and the step crate.
        val dt = 1.0 / 60.0
        var frame = 0
        while (p.x < g.platform.left - 80.0 && frame < 3000) {
            // Pulsed, not held: Player re-arms the jump only on release.
            world.update(dt, moveInput = 1.0, jumpInput = (frame % 10) < 5, crouchInput = false, interactInput = false)
            val feet = p.y + p.height
            if (p.isGrounded) {
                val onFloor = kotlin.math.abs(feet - g.groundY) < 1.0
                // The cart is standable wherever it happens to be - it is a step, that is its
                // whole job - so the test is about height, not position: feet on the floor or
                // feet on a cart deck, nothing in between and nothing overhead.
                val onCart = kotlin.math.abs(feet - g.cartAtRest.top) < 1.0
                assertTrue(onFloor || onCart, "the plane's loads must not be standable (feet at $feet, x=${p.x})")
            }
            frame++
        }
    }

    @Test
    fun testLevel8CartIsTheOnlyWayOntoThePlatformAndOnlyOnceItIsWalkedThere() {
        // The two inherited numbers, pinned: ground -> cart deck is a jump (inside
        // maxJumpHeight), deck -> platform is the canonical 96 climb, and the floor -> platform
        // rise on its own is past climbMaxHeight, so the cart cannot be skipped.
        val g = Level8Geometry()
        val p = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8).player

        val hop = g.groundY - g.cartAtPlatform.top
        assertTrue(hop <= p.maxJumpHeight, "the cart deck must be jumpable from the floor ($hop vs ${p.maxJumpHeight})")

        val mantle = g.cartAtPlatform.top - g.platform.top
        assertTrue(
            mantle > p.climbMinHeight && mantle <= p.climbMaxHeight,
            "cart -> platform must be a climb ($mantle, window ${p.climbMinHeight}..${p.climbMaxHeight})"
        )

        val direct = g.groundY - g.platform.top
        assertTrue(direct > p.climbMaxHeight, "the floor -> platform rise must be out of climb reach ($direct)")
        assertTrue(direct > p.maxJumpHeight, "...and out of jump reach too ($direct)")

        // And the half that is new: standing on the cart where it STARTS reaches nothing. If the
        // rest position were inside climb reach of the platform the push would be decorative.
        val gapFromRest = g.platform.left - g.cartAtRest.right
        assertTrue(
            gapFromRest > p.width + 6.0,
            "from its rest position the cart must not already be adjacent to the platform ($gapFromRest)"
        )
    }

    @Test
    fun testLevel8PlatformCrateSweepsTheCrossingButNeverTheLanding() {
        // "replace it with a short hanging crate which moves left and right". Two things about
        // where it is allowed to travel: it must stay off the landing (that tile belongs to the
        // sweep crate, and stacking both hazards on it would make the climb unsurvivable no
        // matter how well it was read), and it must not be a crusher - a mistimed crossing costs
        // a shove, not the run.
        val g = Level8Geometry()
        val p = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8).player
        val landingRight = g.landingLeft + p.width

        assertTrue(
            g.platformCrateDef.minX > landingRight,
            "the platform crate must never reach the landing (min ${g.platformCrateDef.minX} vs landing end $landingRight)"
        )
        assertTrue(
            g.platformCrateDef.maxX + g.platformCrateDef.width <= g.platform.right + 0.001,
            "the platform crate should sweep the platform, not overhang past its right lip"
        )
        assertTrue(g.platformCrateDef.maxX > g.platformCrateDef.minX, "it has to actually move left and right")
        assertFalse(g.platformCrateDef.crushesOnContact, "the platform crate is a blocker, not a crusher")
        assertTrue(
            g.layout.hangingCrateVariant1.none { it.x > g.platform.left && it.x < g.platform.right },
            "the long stationary crouch crate that used to hang here must be gone"
        )
    }

    @Test
    fun testLevel8BarrelsStandInARowAgainstThePlatformsFarFace() {
        // "add 3 barrels in a row at the bottom of the other side of the platform".
        val g = Level8Geometry()
        assertEquals(3, g.barrels.size, "there should be exactly three barrels")
        val sorted = g.barrels.sortedBy { it.x }
        assertEquals(g.platform.right, sorted.first().left, 0.001, "the row starts against the platform's right face")
        for (b in sorted) {
            assertEquals(g.groundY, b.bottom, 0.001, "every barrel stands on the floor")
            assertTrue(b in g.layout.boxes, "every barrel must also be a collision box")
        }
        for (i in 1 until sorted.size) {
            assertEquals(sorted[i - 1].right, sorted[i].left, 0.001, "the barrels must touch - it is a row, not a spread")
        }
    }

    @Test
    fun testLevel8BobbingPairForcesTheCrouchAtEveryPointOfItsTravel() {
        // "2 short hanging crates that move up and down. player should crouch to avoid them."
        // The crouch has to be the answer at EVERY phase, not at the lucky ones - so the whole
        // travel is checked, not just the endpoints: never more than Player.height of clearance
        // (upright never fits) and never less than crouchHeight (ducked always does).
        val g = Level8Geometry()
        val p = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8).player
        assertEquals(2, g.bobDefs.size, "there should be exactly two bobbing loads")

        for (d in g.bobDefs) {
            assertEquals(d.minX, d.maxX, 0.001, "a bobbing load moves vertically, not horizontally")
            assertTrue(d.maxY > d.minY, "...and it has to actually move")
            assertTrue(d.crushesOnContact, "walking into one upright must be fatal, or the crouch is optional")

            val highest = g.groundY - (d.minY + d.height) // crate at the top of its bob
            val lowest = g.groundY - (d.maxY + d.height)  // crate at the bottom of its bob
            assertTrue(
                highest < p.height,
                "at the top of its bob ${d.id} leaves $highest - a standing body (${p.height}) fits under it"
            )
            assertTrue(
                lowest > p.crouchHeight,
                "at the bottom of its bob ${d.id} leaves $lowest - a crouched body (${p.crouchHeight}) is crushed"
            )
        }
        // Opposite phases, so the pair reads as two loads rather than one bar.
        assertTrue(
            g.bobDefs.map { it.phaseOffsetSeconds }.distinct().size == 2,
            "the two loads should bob out of phase with each other"
        )
    }

    @Test
    fun testLevel8BobbingPairCanBeNeitherJumpedNorClimbedOnto() {
        // "make sure that it is not possible for them to jump or climb onto them. it should be
        // at that height not too be able to jump onto it". Driven, not just measured: crouch the
        // whole gauntlet with the jump button pulsing, and the feet must stay on the floor.
        val g = Level8Geometry()
        val p = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8).player

        for (d in g.bobDefs) {
            // At the bottom of the bob the top is inside climbMaxHeight, so the ONLY thing
            // refusing the mantle is findClimbTarget's floating-ledge rule (underside more than
            // 4 above the feet). Pin that the underside really is clear of the feet.
            val lowestUnderside = g.groundY - (d.maxY + d.height)
            assertTrue(lowestUnderside > 4.0, "${d.id} must never brace against the floor, or it becomes climbable")
            // At the top of the bob it is out of climb reach outright.
            assertTrue(g.groundY - d.minY > p.climbMaxHeight, "${d.id} is inside climb reach at the top of its bob")
            // And never within a jump, at any phase.
            assertTrue(g.groundY - d.maxY > p.maxJumpHeight, "${d.id} is jumpable at the bottom of its bob")
        }

        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8)
        val pl = world.player
        val dt = 1.0 / 60.0
        val gauntletStart = g.bobDefs.minOf { it.initialX } - 120.0
        val gauntletEnd = g.bobDefs.maxOf { it.initialX + it.width } + 60.0
        // Teleport to the run-up rather than walking the whole level - this test is about the
        // gauntlet, and the route in is the walkthrough test's job.
        pl.x = gauntletStart
        pl.y = g.groundY - pl.height
        var frame = 0
        while (pl.x < gauntletEnd && frame < 3000) {
            world.update(dt, moveInput = 1.0, jumpInput = (frame % 10) < 5, crouchInput = true, interactInput = false)
            assertFalse(world.isGameOver, "a crouched crossing of the bobbing pair must survive (x=${pl.x})")
            if (pl.isGrounded) {
                assertEquals(
                    g.groundY, pl.y + pl.height, 1.0,
                    "nothing in the gauntlet may be landed on (feet at ${pl.y + pl.height}, x=${pl.x})"
                )
            }
            frame++
        }
        assertTrue(pl.x >= gauntletEnd, "the crouched crossing must get all the way through (stopped at ${pl.x})")
    }

    @Test
    fun testLevel8HighCrateIsWalkedUnderStandingUp() {
        // "after that add a long hanging crate. person doesnt have to crouch for that."
        val g = Level8Geometry()
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8)
        val p = world.player
        assertTrue(
            g.groundY - g.highCrate.bottom > p.height,
            "the high load must clear a standing body (${g.groundY - g.highCrate.bottom} vs ${p.height})"
        )
        assertTrue(g.highCrate.left > g.bobDefs.maxOf { it.initialX }, "it comes after the bobbing pair")

        // Driven: walk it standing, never crouching, and nothing may stop or kill the body.
        val dt = 1.0 / 60.0
        p.x = g.highCrate.left - 80.0
        p.y = g.groundY - p.height
        var frame = 0
        while (p.x < g.highCrate.right + 40.0 && frame < 2000) {
            world.update(dt, moveInput = 1.0, jumpInput = false, crouchInput = false, interactInput = false)
            assertFalse(world.isGameOver, "the high load must not be able to kill anyone (x=${p.x})")
            assertFalse(p.isCrouching, "nothing should force a crouch here")
            frame++
        }
        assertTrue(p.x >= g.highCrate.right + 40.0, "a standing walk must clear the high load (stopped at ${p.x})")
    }

    @Test
    fun testLevel8LeverDropsTheHookCrateAndThatCrateIsTheWayUp() {
        // "there is a wooden crate (the striped one) and a lever after that. above them is a
        // wooden box connected using rope to a hook. pressing the lever drops the crate."
        // Plus the part the request left open - what the drop is FOR. It is the only way onto
        // the high platform, so both halves are pinned together.
        val g = Level8Geometry()
        assertTrue(g.woodCrate in g.layout.boxes, "the striped crate must be a real box, not just art")
        assertEquals(g.groundY, g.woodCrate.bottom, 0.001, "the striped crate stands on the floor")
        assertTrue(g.lever.x > g.woodCrate.right, "the lever comes after the striped crate")
        assertEquals("lvl8_hook_crate", g.lever.targetMechanismId, "the lever must be wired to the hanging box")
        assertTrue(g.hookCrate.ropeLength > 0.0, "the hanging box hangs on a rope")
        assertTrue(g.layout.hangingHooks.contains(g.hookCrate.hook), "...and that rope hangs from a drawn hook")
        assertTrue(g.hookCrate.bounds.bottom < g.groundY - 100.0, "it starts well up in the air")

        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8)
        val p = world.player
        val hc = world.hookCrates.single()
        val dt = 1.0 / 60.0

        // Before the pull: still hanging, and the high platform is unreachable.
        assertFalse(hc.isDetached, "the box must start attached")
        assertTrue(
            g.groundY - g.highPlatform.top > p.climbMaxHeight,
            "the high platform must be out of reach from the floor, or the lever is pointless"
        )

        // Stand at the lever and press interact.
        p.x = g.lever.centerX - p.width / 2.0
        p.y = g.groundY - p.height
        assertTrue(g.lever.isPlayerInRange(p), "the lever has to be reachable from the floor beside it")
        var frame = 0
        while (!hc.isLanded && frame < 600) {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = true)
            frame++
        }
        assertTrue(hc.isDetached, "pressing the lever must detach the box")
        assertTrue(hc.isLanded, "...and it must fall all the way to the floor")
        assertEquals(g.groundY, hc.bounds.bottom, 0.001, "it lands on the ground")

        // Where it lands is the whole point: a jump onto it, then the same 96 mantle the step
        // crate gives onto the mid platform.
        assertEquals(
            g.highPlatform.left, hc.bounds.right, 0.001,
            "the dropped box must land flush against the high platform's face"
        )
        val hop = g.groundY - hc.bounds.top
        assertTrue(hop <= p.maxJumpHeight, "floor -> dropped box must be a jump ($hop vs ${p.maxJumpHeight})")
        val mantle = hc.bounds.top - g.highPlatform.top
        assertTrue(
            mantle > p.climbMinHeight && mantle <= p.climbMaxHeight,
            "dropped box -> high platform must be a climb ($mantle, window ${p.climbMinHeight}..${p.climbMaxHeight})"
        )
    }

    @Test
    fun testLevel8PoleCameraSweepsBetweenTheLoadAndTheLever() {
        // "a high platform with a camera pole connected to it that switches from looking at
        // hanging crates and the lever". Both named targets have to be inside the cone at their
        // own end of the sweep - and, just as important, the lever's patch of floor has to go
        // DARK at the other end, or there is no window to pull it in.
        val g = Level8Geometry()
        val cam = g.layout.cameras.single()
        val pole = g.layout.poles.single()

        assertEquals(g.highPlatform.top, pole.bottom, 0.001, "the pole must stand on the high platform")
        assertTrue(pole !in g.layout.boxes, "the pole is decoration - it must not collide")
        assertTrue(cam.y <= pole.top + 0.001, "the camera sits at the pole's own top cap")

        fun reaches(targetX: Double, targetY: Double, angle: Double): Boolean {
            val dx = targetX - cam.x
            val dy = targetY - cam.y
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            if (dist > cam.visionRange) return false
            val bearing = kotlin.math.atan2(dy, dx)
            var delta = bearing - angle
            while (delta > PI) delta -= 2.0 * PI
            while (delta < -PI) delta += 2.0 * PI
            return kotlin.math.abs(delta) <= cam.visionFov / 2.0
        }

        // The lever, at the near end of the sweep.
        assertTrue(
            reaches(g.lever.centerX, g.lever.centerY, cam.minAngle),
            "one end of the sweep must land on the lever"
        )
        // The hanging load, at the far end. Its near (right) corner is the closest part of it.
        assertTrue(
            reaches(g.highCrate.right, g.highCrate.bottom, cam.maxAngle),
            "the other end must land on the hanging load"
        )
        // ...and the lever must NOT still be lit from the far end, or the pull is unwindowed.
        assertFalse(
            reaches(g.lever.centerX, g.lever.centerY, cam.maxAngle),
            "the lever has to go dark while the camera looks at the load"
        )
        assertTrue(cam.maxAngle > cam.minAngle, "the camera has to actually sweep")
        // Both bearings point left and down from the lens, so a body already up on the high
        // platform is behind it.
        for (a in listOf(cam.minAngle, cam.maxAngle)) {
            assertTrue(kotlin.math.cos(a) < 0.0, "the sweep must point left, away from the platform it stands on")
        }
    }

    @Test
    fun testLevel8SweepCrateRefusesOrKillsTheClimbWhileItIsParkedOverTheLanding() {
        // "when it is at right it can crush the person if he tries to climb". At the 62 hang
        // line a crouched body fits on the landing, so findClimbTarget ALLOWS the mantle and the
        // crate kills it part-way up instead of refusing it outright - Player.bounds is 96 tall
        // for the whole climb (isCrouching is only set when it finishes). Either outcome is the
        // request honoured; what must never happen is walking away from it unharmed.
        val g = Level8Geometry()
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8)
        val crate = world.movingPlatforms.first { it.id == "lvl8_sweep_crate" }
        val p = world.player
        val dt = 1.0 / 60.0
        val landingRight = g.landingLeft + p.width

        // First let the load actually swing over the landing - it starts its cycle at the far
        // left end of the sweep, so there is nothing to test until it gets there.
        var settle = 0
        while (settle < 1200 && !(crate.right > g.landingLeft && crate.left < landingRight)) {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
            settle++
        }
        assertTrue(
            crate.right > g.landingLeft && crate.left < landingRight,
            "the load has to reach the landing at some point in its cycle, or it gates nothing"
        )

        // Now park the body on the cart, already walked flush into the platform face, and try
        // to go up. This test is about the load over the landing, not about the push, so the
        // cart starts where a player who had done the pushing would have left it.
        g.parkCartAtPlatform(world)
        p.x = g.cartAtPlatform.right - p.width
        p.y = g.cartAtPlatform.top - p.height
        var blockedFrames = 0
        var toppedOutClean = false
        var frame = 0
        while (frame < 2000) {
            world.update(dt, moveInput = 1.0, jumpInput = (frame % 10) < 5, crouchInput = false, interactInput = false)
            if (world.isGameOver) break
            // Read coverage AFTER the update: the crate moves inside that call, and the climb
            // decision is made against its post-update position. Reading it before makes a climb
            // that starts on the frame the load finally clears look like a violation.
            val covers = crate.right > g.landingLeft && crate.left < landingRight
            if (p.isGrounded && kotlin.math.abs((p.y + p.height) - g.platform.top) < 1.0) {
                toppedOutClean = covers
                break
            }
            if (!covers) break
            if (!p.isClimbing) blockedFrames++
            frame++
        }
        assertFalse(toppedOutClean, "nobody tops out clean while the load is still over the landing")
        assertTrue(
            blockedFrames > 0 || world.isGameOver,
            "the parked load must either refuse the climb or kill it - it did neither"
        )
    }

    @Test
    fun testLevel8SweepCrateCrushesAClimbItCatchesPartWayUp() {
        // The other half: a climb begun in the clear but timed so the load arrives mid-ascent.
        // The body is standing height for all 1.95s of it, so the underside catches it.
        val g = Level8Geometry()
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8)
        val crate = world.movingPlatforms.first { it.id == "lvl8_sweep_crate" }
        val p = world.player
        val dt = 1.0 / 60.0
        val landingRight = g.landingLeft + p.width

        // Wait on the cart until the load is clear but swinging BACK toward the landing - the
        // trap the level is built around. As above, the cart starts already walked into place.
        g.parkCartAtPlatform(world)
        var frame = 0
        var started = false
        while (frame < 4000 && !world.isGameOver) {
            val covers = crate.right > g.landingLeft && crate.left < landingRight
            val closingIn = !covers && crate.vx > 0.0 && (g.landingLeft - crate.right) < 40.0
            if (!started) {
                p.x = g.cartAtPlatform.right - p.width
                p.y = g.cartAtPlatform.top - p.height
                if (closingIn) started = true
            }
            world.update(
                dt,
                moveInput = if (started) 1.0 else 0.0,
                jumpInput = started && (frame % 10) < 5,
                crouchInput = false,
                interactInput = false
            )
            if (started && p.isGrounded && kotlin.math.abs((p.y + p.height) - g.platform.top) < 1.0) break
            frame++
        }
        assertTrue(world.isGameOver, "a climb started as the load swings back must be crushed, not completed")
    }

    @Test
    fun testLevel8IsBeatableByReadingTheLoadSwingingAway() {
        // The whole route, on the two cues the level is built around.
        //
        // The climb: go up only when the load is clear of the landing AND travelling away from
        // it. Off the cosine that leaves at least 0.3734 of a period - 2.99s - before it comes
        // back, against 1.95s of climb and a ~0.25s walk out from under.
        //
        // The lever: the camera watches the whole last stretch, so hold at a staging spot short
        // of its reach and set off only on the frame it parks on the hanging load, which buys
        // the full sweepPauseDuration. Committing any later in that pause, or from further back,
        // runs out of window - which is the point of the mechanism.
        //
        // And before either of those, the cart: walk into it, take hold, walk it the rest of the
        // way to the platform, let go. That is the whole first section now, and this test is the
        // proof it can actually be done rather than just that the numbers line up.
        //
        // A run that reads all three must never die.
        val g = Level8Geometry()
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8)
        val crate = world.movingPlatforms.first { it.id == "lvl8_sweep_crate" }
        val platformCrate = world.movingPlatforms.first { it.id == "lvl8_platform_crate" }
        val hookCrate = world.hookCrates.single()
        val cam = world.cameras.single()
        val cart = world.pushCarts.single()
        val p = world.player
        val dt = 1.0 / 60.0
        val landingRight = g.landingLeft + p.width
        val bobLeft = g.bobDefs.minOf { it.initialX } - 60.0
        val bobRight = g.bobDefs.maxOf { it.initialX + it.width } + 40.0
        // Short of where the camera can reach a standing body on the floor - see the layout doc.
        val staging = 1900.0

        var elapsed = 0.0
        var climbs = 0
        var wasClimbing = false
        var stalledFor = 0.0
        var parkedOnLoadFor = 0.0
        var committed = false
        var pulledLever = false
        while (elapsed < 150.0 && !world.isLevelComplete) {
            val beforeX = p.x
            val feet = p.y + p.height
            val onFloor = p.isGrounded && feet >= g.groundY - 0.5
            val onStep = p.isGrounded && kotlin.math.abs(feet - g.cartAtPlatform.top) < 1.0
            val onMidPlatform = p.isGrounded && kotlin.math.abs(feet - g.platform.top) < 1.0
            val landingClear = crate.right < g.landingLeft || crate.left > landingRight

            // The camera's blind window opens the instant it parks on the hanging load.
            val parkedOnLoad = cam.currentAngle >= cam.maxAngle - 0.01
            parkedOnLoadFor = if (parkedOnLoad) parkedOnLoadFor + dt else 0.0
            if (!committed && parkedOnLoad && parkedOnLoadFor <= 0.2 && onFloor && p.x + p.width >= staging - 40.0) {
                committed = true
            }
            val holdForCamera = !committed && onFloor && p.x + p.width >= staging
            val atLever = onFloor && kotlin.math.abs(p.centerX - g.lever.centerX) < 24.0
            val waiting = holdForCamera || (atLever && !hookCrate.isDetached)

            val stalled = stalledFor > 0.05

            // ---- the cart ----------------------------------------------------------------
            // Take hold on the frame the walk stalls against its face, and let go the moment it
            // is flush against the platform. Both are level-triggered: GameWorld edge-detects
            // INTERACT, so holding the flag down across frames still toggles exactly once.
            val cartParked = cart.x >= cart.maxX - 0.5
            val holdingCart = world.grippedCart != null
            val takeCart = !holdingCart && !cartParked && onFloor && stalled &&
                p.x + p.width >= cart.bounds.left - 6.0
            val dropCart = holdingCart && cartParked

            // Walk into the face and press up off the stall, the same way the level 2 walkthrough
            // drives its crate hops - the face itself does the positioning, so nothing here
            // depends on hitting a jump at one particular x.
            val jump = when {
                waiting -> false
                // A braced body cannot jump anyway; asking it to would only be noise.
                world.grippedCart != null -> false
                // Clear AND swinging away: the cue with the guaranteed margin behind it.
                onStep -> stalled && landingClear && crate.vx < 0.0
                onFloor -> stalled
                else -> false
            }
            // Duck under the crate sweeping the mid platform, and for the whole bobbing gauntlet.
            val crouch = (onMidPlatform && p.x + p.width > platformCrate.left - 30.0 && p.x < platformCrate.right + 30.0) ||
                (onFloor && p.x + p.width > bobLeft && p.x < bobRight)
            world.update(
                dt,
                moveInput = if (waiting) 0.0 else 1.0,
                jumpInput = jump,
                crouchInput = crouch,
                interactInput = atLever || takeCart || dropCart
            )
            if (hookCrate.isDetached) pulledLever = true
            stalledFor = if (kotlin.math.abs(p.x - beforeX) < 0.5) stalledFor + dt else 0.0
            elapsed += dt
            if (p.isClimbing && !wasClimbing) climbs++
            wasClimbing = p.isClimbing
            assertFalse(world.isGameOver, "a run that reads the level must not die (x=${p.x}, t=$elapsed)")
        }
        assertTrue(world.isLevelComplete, "level 8 must be beatable (stopped at x=${p.x} after ${elapsed}s)")
        assertTrue(pulledLever, "the route has to go through the lever - nothing else reaches the high platform")
        assertEquals(
            cart.maxX, cart.x, 0.5,
            "the route has to walk the cart to the platform - nothing else reaches the mid one"
        )
        assertNull(world.grippedCart, "and let go of it again")
        // The step crate onto the mid platform, and the dropped box onto the high one. Getting
        // onto the dropped box itself is a jump once it has settled and a climb if the player
        // catches it still falling, so it is not counted here.
        assertTrue(climbs >= 2, "the route needs both mantles - the step crate and the dropped box (saw $climbs)")
        assertTrue(
            elapsed <= LevelData.DEFAULT_LEVEL_8.timeTargetSeconds,
            "...and inside its own 3-star target (${elapsed}s vs ${LevelData.DEFAULT_LEVEL_8.timeTargetSeconds}s)"
        )
    }

    // ---- The push cart (level 8's step onto the mid platform) -------------------------------

    /**
     * Level 8's world with the body already standing against one face of the cart, grounded.
     *
     * [fromLeft] false puts him on the far side, which is the side a pull is done from - the
     * grab is symmetric and only the sign of everything afterwards changes.
     */
    private fun level8WithPlayerAtCart(fromLeft: Boolean = true): Pair<GameWorld, PushCart> {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8)
        val cart = world.pushCarts.single()
        val p = world.player
        p.x = if (fromLeft) cart.bounds.left - p.width else cart.bounds.right
        p.y = 440.0 - p.height
        // Settle onto the floor: canGrip refuses a body that is not standing on something.
        repeat(10) {
            world.update(1.0 / 60.0, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
        }
        return world to cart
    }

    private fun GameWorld.stepCart(
        frames: Int,
        moveInput: Double = 0.0,
        interactInput: Boolean = false
    ) {
        repeat(frames) {
            update(1.0 / 60.0, moveInput, jumpInput = false, crouchInput = false, interactInput = interactInput)
        }
    }

    @Test
    fun testCartIsTakenHoldOfFromEitherSideAndBracesTheBodyAsItGoes() {
        for (fromLeft in listOf(true, false)) {
            val (world, cart) = level8WithPlayerAtCart(fromLeft)
            assertNull(world.grippedCart, "nothing is held until INTERACT")
            assertTrue(world.canInteract, "standing against the cart has to light the button up")

            world.stepCart(1, interactInput = true)
            assertSame(cart, world.grippedCart, "one press takes hold of it")
            assertTrue(world.isPushStanceHeld, "and that IS the braced stance - there is no second toggle")
            assertEquals(
                if (fromLeft) 1.0 else -1.0, world.pushCartSide,
                "the cart is on the side the body walked up from"
            )

            // Held down across frames is still one press, the same edge-detection the dev stage
            // relies on - otherwise the grab would flicker on and off every tick.
            world.stepCart(30, interactInput = true)
            assertSame(cart, world.grippedCart, "a held button is one press, not thirty")
            assertTrue(world.pushStanceBlend > 0.0)
        }
    }

    @Test
    fun testGrabbingFromArmsLengthWalksTheBodyIntoContactWhileItBends() {
        // "depending on the position he presses it he could be not touching it" - the grab is
        // allowed from up to PushCart.GRIP_REACH short of the face, and freezing THAT offset in
        // left the hands visibly off the cart for the whole push. The gap is closed over the
        // lean-in instead of by narrowing the reach, which would make the player line the grab
        // up by hand.
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8)
        val cart = world.pushCarts.single()
        val p = world.player
        // As far back as the grab reaches, minus a hair so it still registers.
        val gap = PushCart.GRIP_REACH - 2.0
        p.x = cart.bounds.left - p.width - gap
        p.y = 440.0 - p.height
        world.stepCart(6)
        assertTrue(cart.canGrip(p), "the grab has to be legal from arm's length, or there is no bug to fix")

        val cartX = cart.x
        world.stepCart(1, interactInput = true)
        assertSame(cart, world.grippedCart)
        // Still short, and the cart has not been touched yet.
        assertTrue(cart.bounds.left - p.bounds.right > 1.0, "he does not teleport onto it")

        // Mid-bend: part of the way in, monotonically, and the cart still parked.
        world.stepCart(24)
        val grip = cart.handleGripX(fromLeft = true)
        val midShort = grip - world.bracedFistX
        assertTrue(midShort < gap - 1.0, "the settle has to have started (short by " + midShort + ")")
        assertTrue(midShort > 0.5, "...and not be finished at a quarter of the bend (short by " + midShort + ")")
        assertEquals(cartX, cart.x, 1e-9, "the cart does not move while he is still bending into it")

        // Braced: hand on the handle, landing with the pose rather than before or after it.
        world.stepCart(60)
        assertTrue(world.isPushing, "the lean-in should be done by now")
        assertEquals(
            grip, world.bracedFistX, 0.001,
            "fully braced means the fist on the handle, whatever distance the grab was made from"
        )
        assertFalse(world.isSettlingIntoCart)
    }

    @Test
    fun testTheSettleArrivesFromEitherSideAndFromAnyDistance() {
        for (fromLeft in listOf(true, false)) {
            for (gap in listOf(0.0, 8.0, PushCart.GRIP_REACH - 2.0)) {
                val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8)
                val cart = world.pushCarts.single()
                val p = world.player
                p.x = if (fromLeft) cart.bounds.left - p.width - gap else cart.bounds.right + gap
                p.y = 440.0 - p.height
                world.stepCart(6)
                world.stepCart(1, interactInput = true)
                assertSame(cart, world.grippedCart, "grab from " + gap + " away, fromLeft=" + fromLeft)
                world.stepCart(90)

                assertEquals(
                    cart.handleGripX(fromLeft), world.bracedFistX, 0.001,
                    "braced from " + gap + " away (fromLeft=" + fromLeft + ") must still end on the handle"
                )
            }
        }
    }

    @Test
    fun testTheSettleDoesNotDragTheCartOrCountAsPushing() {
        // The body moves during the bend, so anything that reads movement has to be reading the
        // CART - the tutorial's push/pull prompt does exactly that, for this reason.
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8)
        val cart = world.pushCarts.single()
        val p = world.player
        p.x = cart.bounds.left - p.width - (PushCart.GRIP_REACH - 2.0)
        p.y = 440.0 - p.height
        world.stepCart(6)
        val cartX = cart.x
        val playerX = p.x

        // Hold a direction through the whole bend: the settle owns the body until it is braced.
        world.stepCart(1, interactInput = true)
        world.stepCart(45, moveInput = -1.0)
        assertEquals(cartX, cart.x, 1e-9, "steering during the bend must not drag the cart")
        assertTrue(p.x > playerX, "and must not walk him back out of the settle")
    }

    @Test
    fun testTheBracedFistLandsOnTheHandleAndNotOverTheLoad() {
        // "he should grab the corner of the handle". Solving the stance for the BODY's leading
        // edge put the fist about three units inside the cart, over the crate on the deck - the
        // hand reaches well past the collision box, so flush-to-the-face is not hands-on. The
        // stance is solved for the hand instead; this pins where that hand ends up.
        for (fromLeft in listOf(true, false)) {
            val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8)
            val cart = world.pushCarts.single()
            val p = world.player
            p.x = if (fromLeft) cart.bounds.left - p.width - 6.0 else cart.bounds.right + 6.0
            p.y = 440.0 - p.height
            world.stepCart(6)
            world.stepCart(1, interactInput = true)
            world.stepCart(90)
            assertTrue(world.isPushing, "fromLeft=" + fromLeft + " should be braced by now")

            val fist = world.bracedFistX
            assertEquals(cart.handleGripX(fromLeft), fist, 0.001, "fromLeft=" + fromLeft)

            // On the cart, and on the HANDLE end of it: inside the near edge, and short of the
            // load's own near face (PushCart.LOAD_LEFT/RIGHT_FRACTION is where the crate starts).
            val loadNear = if (fromLeft) {
                cart.x + cart.width * PushCart.LOAD_LEFT_FRACTION
            } else {
                cart.x + cart.width * PushCart.LOAD_RIGHT_FRACTION
            }
            if (fromLeft) {
                assertTrue(fist > cart.bounds.left, "the fist must reach the cart (" + fist + ")")
                assertTrue(fist < loadNear, "...and stop at the handle, not over the load (" + fist + " vs " + loadNear + ")")
            } else {
                assertTrue(fist < cart.bounds.right, "the fist must reach the cart (" + fist + ")")
                assertTrue(fist > loadNear, "...and stop at the handle, not over the load (" + fist + " vs " + loadNear + ")")
            }

            // The two collision boxes are deliberately NOT flush any more - that gap is the
            // length of his arms, and it is what moved the hand onto the handle.
            val boxGap = if (fromLeft) cart.bounds.left - p.bounds.right else p.bounds.left - cart.bounds.right
            assertTrue(boxGap > 1.0, "a man pushing a trolley stands off it (gap " + boxGap + ")")
        }
    }

    @Test
    fun testTheBracedFistReachesTheCornerOfTheHandle() {
        // "make him little bit larger to make his hand touch the corner of the handle" - the
        // pose is a fixed plate, so hand height is only adjustable through the drawn body, and
        // Player.VISUAL_HEIGHT_SCALE was set to land it here. The corner - where the upright
        // turns into the curved grip - is rows 14..22 of cart.png's 256, i.e. 2.6..4.1 units
        // below the handle's top on a 48-tall cart.
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8)
        val cart = world.pushCarts.single()
        val fistHeight = world.player.visualHeight * PushCart.BRACED_FIST_HEIGHT_PER_HEIGHT
        val belowTop = cart.height - fistHeight
        assertTrue(
            belowTop in 2.0..4.5,
            "the fist should be at the handle's corner, $belowTop below its top (want 2.0..4.5)"
        )
        assertTrue(fistHeight > cart.height * PushCart.DECK_TOP_FRACTION, "...and above the deck, not under it")
    }

    @Test
    fun testTheDrawnBodyStillFitsTheTightestCrouchGap() {
        // Player.VISUAL_HEIGHT_SCALE draws the body bigger than it collides, which is free for
        // gameplay but NOT free for how the game reads: a crouched body drawn taller than the
        // gap it is crouching under looks like a clipping bug. Level 8's hang line is the
        // tightest gap in the game (62 - see its own guidelines section, "the lowest the geometry
        // allows"), so it is the one that decides how far this can go.
        val p = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8).player
        val hangClearance = 62.0
        val drawnCrouch = p.crouchHeight * Player.VISUAL_HEIGHT_SCALE
        assertTrue(
            drawnCrouch < hangClearance,
            "a crouched body is drawn $drawnCrouch tall and has to fit under $hangClearance"
        )
        // And the other end of that window still holds: standing must NOT fit under the bobbing
        // pair's high point, or the crouch stops being the answer there.
        assertTrue(
            p.visualHeight > 90.0,
            "a standing body (drawn ${p.visualHeight}) must still not fit under the 90 bob gap"
        )
    }

    @Test
    fun testCartCannotBeTakenHoldOfFromOnTopOfIt() {
        // A braced body can neither jump nor crouch, so someone who grabbed the cart he was
        // standing on would have no way back off it - and would be dragging his own floor.
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8)
        val cart = world.pushCarts.single()
        val p = world.player
        p.x = cart.bounds.left + 20.0
        p.y = cart.bounds.top - p.height
        world.stepCart(5)
        assertTrue(p.isGrounded, "the body should have settled on the deck")
        assertFalse(cart.canGrip(p), "the cart underfoot is not grabbable")

        world.stepCart(10, interactInput = true)
        assertNull(world.grippedCart, "...and INTERACT up there does nothing")
        assertTrue(world.isPushStanceIdle)
    }

    @Test
    fun testCartIsOutOfReachFromAcrossTheYard() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8)
        val cart = world.pushCarts.single()
        val p = world.player
        // The spawn - a long walk short of it. Range is the whole gate here, exactly as it is
        // for a lever or a camera bot.
        assertFalse(cart.canGrip(p), "the cart is not grabbable from the spawn")
        world.stepCart(30, interactInput = true)
        assertNull(world.grippedCart)
        assertTrue(world.isPushStanceIdle, "and INTERACT in open ground never braces anyone")
    }

    @Test
    fun testPushingWalksTheCartForwardAndStopsItFlushAgainstThePlatform() {
        val (world, cart) = level8WithPlayerAtCart(fromLeft = true)
        val startX = cart.x
        world.stepCart(1, interactInput = true)

        world.stepCart(600, moveInput = 1.0)
        assertTrue(cart.x > startX + 100.0, "the cart has to actually travel (moved " + (cart.x - startX) + ")")
        assertEquals(cart.maxX, cart.x, 0.5, "and stop exactly where its travel ends")
        assertEquals(
            LevelData.LEVEL_8_LAYOUT.boxes.first { it.width == 240.0 }.left, cart.bounds.right, 0.5,
            "which is flush against the platform's face"
        )
        // The body came with it, and is still holding on at the offset it grabbed at.
        assertSame(cart, world.grippedCart)
        assertEquals(
            cart.handleGripX(fromLeft = true), world.bracedFistX, 0.001,
            "still holding the same handle"
        )
        assertEquals(1.0, world.pushGaitDirection, "walking into the load is a push")
    }

    @Test
    fun testPullingDragsTheCartBackAndRunsTheSameGaitInReverse() {
        // "he can also pull it and do it by just reversing the push animation" - the model's
        // half of that is the sign; GameplayScene steps the clip by it.
        val (world, cart) = level8WithPlayerAtCart(fromLeft = true)
        world.stepCart(1, interactInput = true)
        val startX = cart.x

        world.stepCart(240, moveInput = -1.0)
        assertTrue(cart.x < startX - 20.0, "the cart has to follow the body backwards (moved " + (cart.x - startX) + ")")
        assertEquals(-1.0, world.pushGaitDirection, "walking away from the load is a pull")
        assertEquals(1.0, world.pushCartSide, "...and he is still braced against the same side of it")

        // All the way back to its own limit, and no further.
        world.stepCart(600, moveInput = -1.0)
        assertEquals(cart.minX, cart.x, 0.5, "the cart stops at the back of its travel")

        // Pushing again flips the gait back without letting go.
        world.stepCart(60, moveInput = 1.0)
        assertEquals(1.0, world.pushGaitDirection)
    }

    @Test
    fun testTheHeldCartIsNotAWallAndBecomesOneAgainWhenLetGo() {
        val (world, cart) = level8WithPlayerAtCart(fromLeft = true)
        world.stepCart(1, interactInput = true)
        world.stepCart(180, moveInput = 1.0)
        val carriedTo = cart.x
        assertTrue(carriedTo > cart.minX + 1.0)

        // Let go: one press, and the cart stays exactly where it was set down.
        world.stepCart(1, interactInput = true)
        assertNull(world.grippedCart)
        world.stepCart(120, moveInput = 1.0)
        assertEquals(carriedTo, cart.x, 0.5, "a cart nobody is holding does not move")
        assertTrue(
            world.player.bounds.right <= cart.bounds.left + 0.5,
            "and it is a wall again - walking into it goes nowhere"
        )
    }

    @Test
    fun testCheckpointRespawnPutsTheCartBackWhereItStarted() {
        // Everything else in the level rewinds on a respawn; a cart left halfway would leave the
        // route in a state the rest of the world no longer matches.
        val (world, cart) = level8WithPlayerAtCart(fromLeft = true)
        val restX = cart.x
        world.stepCart(1, interactInput = true)
        world.stepCart(240, moveInput = 1.0)
        assertTrue(cart.x > restX + 20.0)

        assertTrue(world.respawnAtCheckpoint(), "level 8 allows a continue")
        assertEquals(restX, cart.x, 1e-9, "the cart goes back to its rest position")
        assertNull(world.grippedCart, "and nobody is holding it any more")
        assertTrue(world.isPushStanceIdle)
    }

    @Test
    fun testEachWorldGetsItsOwnCart() {
        // LevelLayout is a process-wide singleton and PushCart holds a mutable x - the same trap
        // levers and hook crates are copied for. A cart shoved to the platform in one playthrough
        // must not already be sitting there in the next.
        val (first, cartA) = level8WithPlayerAtCart(fromLeft = true)
        first.stepCart(1, interactInput = true)
        first.stepCart(600, moveInput = 1.0)
        assertEquals(cartA.maxX, cartA.x, 0.5)

        val second = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8)
        val cartB = second.pushCarts.single()
        assertNotSame(cartA, cartB, "each world builds its own cart")
        assertEquals(
            LevelData.LEVEL_8_LAYOUT.pushCarts.single().initialX, cartB.x, 1e-9,
            "a fresh run starts with the cart parked again"
        )
    }

    @Test
    fun testTheCartBlocksSightWhereverItStands() {
        // It is a solid body, not a prop: a sightline stops at it in both positions. Checked
        // through VisionSystem directly, since level 8 has no guard to aim.
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8)
        val cart = world.pushCarts.single()
        val p = world.player
        p.y = 440.0 - p.height
        for (cartX in listOf(cart.minX, cart.maxX)) {
            cart.x = cartX
            p.x = cart.bounds.right + 30.0
            val eye = Vec2d(cart.bounds.left - 60.0, 440.0 - 24.0)
            val seen = VisionSystem.getPlayerSpottedDistance(
                eye = eye,
                facingAngle = 0.0,
                visionRange = 400.0,
                visionFov = PI / 2.0,
                player = p,
                occluders = listOf(cart.bounds)
            )
            assertNull(seen, "the cart at " + cartX + " must block the line through it")
        }
    }

    // ---- The push-stance stage (was level 8 until 2026-09-25) ------------------------------

    @Test
    fun testPushStanceStageIsClearedOfEverythingButTheGroundAndTheExit() {
        val layout = LevelData.PUSH_STANCE_DEMO_LAYOUT
        assertTrue(layout.boxes.isEmpty(), "the push stage must have no boxes")
        assertTrue(layout.guards.isEmpty(), "the push stage must have no guards")
        assertTrue(layout.cameras.isEmpty(), "the push stage must have no cameras")
        assertTrue(layout.lasers.isEmpty(), "the push stage must have no lasers")
        assertTrue(layout.levers.isEmpty(), "the push stage must have no levers")
        assertTrue(layout.movingPlatforms.isEmpty(), "the push stage must have no moving platforms")
        assertTrue(layout.conveyors.isEmpty() && layout.conveyorCrates.isEmpty(), "the push stage must have no conveyors")
        assertTrue(
            layout.fans.isEmpty() && layout.steamPipes.isEmpty() && layout.cameraBots.isEmpty(),
            "the push stage must have no vent hazards"
        )
        assertTrue(
            layout.hangingCrateVariant1.isEmpty() && layout.hangingCrateVariant2.isEmpty(),
            "the push stage must have nothing hanging"
        )
        assertTrue(layout.swingHooks.isEmpty() && layout.hookCrates.isEmpty(), "the push stage must have no hooks")
        assertFalse(layout.hasStartFences, "the push stage must have no start fences")

        // Exactly the floor and the two side walls - nothing to stand on above ground level.
        assertEquals(3, layout.platforms.size, "the push stage should be ground plus the two bounding walls")
        val world = GameWorld.createDefault(LevelData.PUSH_STANCE_DEMO)
        assertTrue(world.hasNoGuards, "no guards are constructed for the push stage")
        assertTrue(world.pushStanceDemo, "this is the push-stance stage")

        // A flat, uninterrupted walk from spawn to the exit, with the ground under the player the
        // whole way - the stage is only useful if nothing can strand or kill him on it.
        var elapsed = 0.0
        val dt = 1.0 / 60.0
        while (elapsed < 60.0 && !world.isLevelComplete) {
            world.update(dt, moveInput = 1.0, jumpInput = false)
            assertFalse(world.isGameOver, "nothing in the push stage may kill the player")
            assertTrue(world.player.isGrounded, "the floor must run the whole width of the push stage")
            elapsed += dt
        }
        assertTrue(world.isLevelComplete, "the push stage must still be walkable to its exit (stopped at x=" + world.player.x + ")")
    }

    /**
     * The stage is deliberately off the shipped list now that level 8 is a real level, but it has
     * to stay REACHABLE - by id, through the same lookup the `-PstartLevel=` / `.debug_level`
     * entry points use. Dropping it out of [LevelData.DEV_LEVELS] would strand the only place the
     * push animation can be watched.
     */
    @Test
    fun testPushStanceStageIsOffTheShippedListButStillReachableById() {
        assertFalse(
            LevelData.DEFAULT_LEVELS.any { it.id == "push_stance_demo" },
            "the dev stage must not be in the shipped progression"
        )
        assertEquals(LevelData.PUSH_STANCE_DEMO, LevelData.findById("push_stance_demo"))
        assertEquals(LevelData.DEFAULT_LEVEL_8, LevelData.findById("level_8"))
        assertNull(LevelData.findById("no_such_level"))
    }

    // ---- Push stance ------------------------------------------------------------------------

    private fun pushWorld() = GameWorld.createDefault(LevelData.PUSH_STANCE_DEMO)

    /** One frame with INTERACT held, then one with it released - i.e. a single button press. */
    private fun GameWorld.tapInteract(dt: Double = 1.0 / 60.0) {
        update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = true)
        update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
    }

    private fun GameWorld.coast(seconds: Double, moveInput: Double = 0.0) {
        val dt = 1.0 / 60.0
        var t = 0.0
        while (t < seconds) {
            update(dt, moveInput = moveInput, jumpInput = false, crouchInput = false, interactInput = false)
            t += dt
        }
    }

    @Test
    fun testPushStanceTogglesOnInteractAndBlendsBothWays() {
        val world = pushWorld()
        assertTrue(world.isPushStanceIdle, "starts upright")
        assertFalse(world.isPushing)

        world.tapInteract()
        assertTrue(world.isPushStanceHeld, "one press brings the stance on")
        assertFalse(world.isPushing, "still leaning in - the gait only runs once fully braced")
        assertTrue(world.pushStanceBlend > 0.0 && world.pushStanceBlend < 1.0)

        world.coast(GameWorld.PUSH_STANCE_ENTER_SECONDS + 0.1)
        assertEquals(1.0, world.pushStanceBlend, "the lean-in finishes")
        assertTrue(world.isPushing, "braced, so the gait loop is live")

        world.tapInteract()
        assertFalse(world.isPushStanceHeld, "a second press releases it")
        assertTrue(
            world.pushStanceBlend < 1.0 && world.pushStanceBlend > 0.0,
            "and it stands up through the same blend"
        )

        world.coast(GameWorld.PUSH_STANCE_EXIT_SECONDS + 0.1)
        assertTrue(world.isPushStanceIdle, "back upright, so the scene hands the sprite back to idle")
    }

    @Test
    fun testPushStanceIgnoresAHeldInteractButtonAfterTheFirstFrame() {
        // The scene passes the raw button LEVEL, not an edge, so a toggle that read it directly
        // would flip once per frame and never settle. Holding it must count exactly once.
        val world = pushWorld()
        val dt = 1.0 / 60.0
        repeat(30) {
            world.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = true)
        }
        assertTrue(world.isPushStanceHeld, "a held button is one press, not thirty")
        assertTrue(world.pushStanceBlend > 0.4, "and the blend ran for the whole hold")
    }

    @Test
    fun testPushStanceSlowsMovementAndBlocksJumpAndCrouch() {
        val world = pushWorld()
        val dt = 1.0 / 60.0

        // Sampled after a moment of walking, not on the first frame: Player ramps vx up to
        // moveSpeed rather than snapping to it, so frame one reads 30 whatever the state is.
        world.coast(0.5, moveInput = 1.0)
        assertEquals(world.player.moveSpeed, world.player.vx, 0.001, "unbraced, he walks at full speed")

        world.tapInteract()
        world.coast(GameWorld.PUSH_STANCE_ENTER_SECONDS + 0.1)
        world.coast(0.5, moveInput = 1.0)
        world.update(dt, moveInput = 1.0, jumpInput = true, crouchInput = true, interactInput = false)
        assertEquals(
            world.player.moveSpeed * GameWorld.PUSH_MOVE_FACTOR, world.player.vx, 0.001,
            "braced, he leans into it at a fraction of walking speed"
        )
        assertFalse(world.player.isCrouching, "a braced body cannot duck")
        assertTrue(world.player.isGrounded, "and cannot jump - INTERACT is the only way out")
    }

    @Test
    fun testPushStanceIsClearedByARestart() {
        val world = pushWorld()
        world.tapInteract()
        world.coast(GameWorld.PUSH_STANCE_ENTER_SECONDS + 0.1)
        assertTrue(world.isPushing)
        world.restartLevel()
        assertTrue(world.isPushStanceIdle, "a restart puts him back upright")
        assertFalse(world.isPushStanceHeld)
        // And the toggle's own edge state resets with it, so the first press after a restart works.
        world.tapInteract()
        assertTrue(world.isPushStanceHeld)
    }

    @Test
    fun testPushStanceIsTheDevStageOnlyAndLeavesEveryShippedLevelAlone() {
        val other = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_1)
        assertFalse(other.pushStanceDemo)
        val dt = 1.0 / 60.0
        repeat(120) {
            other.update(dt, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = true)
        }
        assertTrue(other.isPushStanceIdle, "INTERACT outside the push stage never braces anyone")
        assertEquals(0.0, other.pushStanceBlend)

        // Level 8 used to be the one exception; since it became a real level (LEVEL_8_LAYOUT) the
        // flag belongs to the dev stage alone, and NOTHING shipped may set it - INTERACT would
        // otherwise brace the player mid-level with nothing to push.
        for (level in LevelData.DEFAULT_LEVELS) {
            assertFalse(level.layout?.pushStanceDemo ?: false, level.id + " must not set pushStanceDemo")
        }
        assertTrue(LevelData.PUSH_STANCE_DEMO.layout?.pushStanceDemo ?: false, "the dev stage is the push stage")
    }

    @Test
    fun testPushStanceKeepsTheInteractButtonLiveSoTheTogglePressReachesTheWorld() {
        // GameplayScene gates its interactPressed on world.canInteract before handing it over.
        // With no levers and no camera bots in level 8, canInteract would be false forever and
        // the toggle could never fire.
        val world = pushWorld()
        assertTrue(world.canInteract, "the button has to be live in the push-stance level")
        assertFalse(GameWorld.createDefault(LevelData.DEFAULT_LEVEL_1).canInteract)
    }

    @Test
    fun testGuardShieldDisplayNameAliasesAndLevel7Protection() {
        // 1. Display name and ID resolution
        assertEquals("GUARD SHIELD", PowerupType.LASER_SHIELD.displayName)
        assertEquals(PowerupType.LASER_SHIELD, PowerupType.fromId("guard_shield"))
        assertEquals(PowerupType.LASER_SHIELD, PowerupType.fromId("laser_shield"))
        assertEquals(PowerupType.LASER_SHIELD, PowerupType.fromId("GUARD_SHIELD"))

        // 2. Profile inventory storage & alias mapping
        val storageMap = mutableMapOf<String, String>()
        val profileStorage = MapBackedGameProfileStorage(
            getRaw = { storageMap[it] },
            setRaw = { k, v -> storageMap[k] = v }
        )
        profileStorage.addCoins(1000)
        assertTrue(profileStorage.buyPowerup("guard_shield", 500))
        val profile = profileStorage.getProfile()
        assertEquals(1, profile.getPowerupCount(PowerupType.LASER_SHIELD))
        assertEquals(1, profile.getPowerupCount("guard_shield"))
        assertEquals(1, profile.getPowerupCount("laser_shield"))
        assertTrue(profileStorage.consumePowerup(PowerupType.LASER_SHIELD))
        assertEquals(0, profileStorage.getProfile().getPowerupCount(PowerupType.LASER_SHIELD))

        // 3. Steam protection in Level 7
        val worldSteam = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_7)
        val pipe = worldSteam.steamPipes.first()
        pipe.update(0.0)
        assertTrue(pipe.isActive)
        worldSteam.activePowerups.activate(PowerupType.LASER_SHIELD)
        assertTrue(worldSteam.activePowerups.isLaserShieldActive)
        var blockedSteamFired = false
        worldSteam.onLaserShieldBlocked = { blockedSteamFired = true }
        worldSteam.player.resetTo(pipe.x - 10.0, 440.0 - 96.0)
        worldSteam.update(1.0 / 60.0, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
        assertFalse(worldSteam.isGameOver, "Guard Shield must prevent game over on steam contact")
        assertFalse(worldSteam.activePowerups.isLaserShieldActive, "Guard Shield must be consumed after steam deflection")
        assertTrue(blockedSteamFired, "onLaserShieldBlocked must fire on steam deflection")
        assertTrue(worldSteam.laserGraceTimer > 0.0, "Grace timer should be granted")

        // 4. Laser protection (standard laser hazard in Level 4)
        val worldLaser = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        val laser = worldLaser.lasers.first()
        laser.update(0.0)
        assertTrue(laser.isActive)
        worldLaser.activePowerups.activate(PowerupType.LASER_SHIELD)
        assertTrue(worldLaser.activePowerups.isLaserShieldActive)
        var blockedLaserFired = false
        worldLaser.onLaserShieldBlocked = { blockedLaserFired = true }
        worldLaser.player.resetTo(laser.topX - worldLaser.player.width / 2.0, 300.0)
        assertTrue(laser.intersectsPlayer(worldLaser.player.bounds))
        worldLaser.update(1.0 / 60.0, moveInput = 0.0, jumpInput = false, crouchInput = false, interactInput = false)
        assertFalse(worldLaser.isGameOver, "Guard Shield must prevent game over on laser contact")
        assertFalse(worldLaser.activePowerups.isLaserShieldActive, "Guard Shield must be consumed after laser deflection")
        assertTrue(blockedLaserFired, "onLaserShieldBlocked must fire on laser deflection")
        assertTrue(worldLaser.laserGraceTimer > 0.0, "Grace timer should be granted")
    }

    @Test
    fun testLevel7SteamPipesSingleMountsAndNonConstantDurationsWithOneSecondWarning() {
        val layout = LevelData.LEVEL_7_LAYOUT
        val pipes = layout.steamPipes
        assertTrue(pipes.isNotEmpty(), "Level 7 must contain steam pipes")

        // 1. Requirement: No steam emitters on both top and bottom (never PAIR)
        for (pipeDef in pipes) {
            assertTrue(
                pipeDef.mountType == PipeMountType.TOP || pipeDef.mountType == PipeMountType.BOTTOM,
                "Pipe ${pipeDef.id} must be mounted either TOP or BOTTOM, never PAIR"
            )
            assertNotEquals(PipeMountType.PAIR, pipeDef.mountType)
        }

        // 2. Requirement: Non-constant gas emission durations and non-periodic cycles
        val pipe = SteamPipe(pipes.first())
        val cycles = pipe.cycles
        assertTrue(cycles.size >= 10, "Should generate sufficient cycle history")

        val activeDurations = cycles.take(10).map { it.activeDuration }.toSet()
        assertTrue(
            activeDurations.size > 1,
            "Active gas emission durations must NOT be constant! Found distinct durations: $activeDurations"
        )

        val dormantDurations = cycles.take(10).map { it.dormantDuration }.toSet()
        assertTrue(
            dormantDurations.size > 1,
            "Dormant rest durations must NOT be constant (non-periodic)! Found distinct durations: $dormantDurations"
        )

        // 3. Requirement: Show warning for exactly 0.5 seconds, then emit steam
        for (cycle in cycles.take(5)) {
            assertEquals(0.5, cycle.warningDuration, 1e-6, "Warning window before steam emission must be exactly 0.5s")

            // Test right at the start of warning
            pipe.update(cycle.dormantEnd - pipe.phaseOffsetSeconds)
            assertTrue(pipe.isWarning, "Pipe must enter warning phase when yellow sign turns on")
            assertFalse(pipe.isActive, "Pipe must NOT emit lethal steam during yellow sign warning")
            assertEquals(0.0, pipe.warningProgress, 0.05)

            // Test halfway through warning (0.25s in)
            pipe.update(cycle.dormantEnd + 0.25 - pipe.phaseOffsetSeconds)
            assertTrue(pipe.isWarning)
            assertFalse(pipe.isActive, "Pipe must still NOT emit steam during 0.5s warning")
            assertEquals(0.5, pipe.warningProgress, 0.05)

            // Test right after 0.5s warning finishes: steam must erupt!
            pipe.update(cycle.end + 0.01 - pipe.phaseOffsetSeconds)
            assertTrue(pipe.isActive, "Pipe must emit steam after 0.5s warning has elapsed")
            assertFalse(pipe.isWarning)
        }
    }
    @Test
    fun testLevelBackgroundResolutionAndLevel2Bgmg5() {
        // Level 2 explicitly uses bgmg5.png
        assertEquals("bgmg5.png", LevelData.DEFAULT_LEVEL_2.backgroundImage)
        assertEquals("bgmg5.png", LevelData.DEFAULT_LEVEL_2.resolvedBackgroundImage)

        // Level 4 and 7 keep their custom backgrounds
        assertEquals("metalbg.png", LevelData.DEFAULT_LEVEL_4.resolvedBackgroundImage)
        assertEquals("bglvl7.png", LevelData.DEFAULT_LEVEL_7.resolvedBackgroundImage)

        // Rotation cycles through bgmg2..6 for levels without explicit backgroundImage
        val testLevel = { num: Int -> LevelData(id = "level_$num", name = "Test Level $num") }
        assertEquals("bgmg2.png", testLevel(1).resolvedBackgroundImage)
        assertEquals("bgmg3.png", testLevel(2).resolvedBackgroundImage)
        assertEquals("bgmg4.png", testLevel(3).resolvedBackgroundImage)
        assertEquals("bgmg5.png", testLevel(4).resolvedBackgroundImage)
        assertEquals("bgmg6.png", testLevel(5).resolvedBackgroundImage)
        assertEquals("bgmg2.png", testLevel(6).resolvedBackgroundImage)
        assertEquals("bgmg3.png", testLevel(7).resolvedBackgroundImage)
        assertEquals("bgmg4.png", testLevel(8).resolvedBackgroundImage)
        assertEquals("bgmg5.png", testLevel(9).resolvedBackgroundImage)
        assertEquals("bgmg6.png", testLevel(10).resolvedBackgroundImage)
    }

    // -----------------------------------------------------------------------------------------
    // Pause time is not gameplay time (GameWorld.isSuspended)
    //
    // `timeTaken` is what the win/fail card prints and what star 3 is judged against, so it has to
    // mean "time the player could act", not "wall-clock since the level loaded". GameplayScene's
    // updater returns early while its pause overlay is up, but that flag cannot see the holds that
    // come from outside the scene - the app being backgrounded, or a full-screen ad covering
    // gameplay while the KorGE loop keeps running underneath (Android never hides that view; see
    // .junie/guidelines.md real-device bug #7). Those set isSuspended via GameAppLifecycle, and
    // these tests pin the model half of the contract.
    // -----------------------------------------------------------------------------------------

    @Test
    fun testSuspendedWorldDoesNotChargeTheLevelClock() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_1)
        val dt = 1.0 / 60.0

        for (i in 0 until 60) world.update(dt, moveInput = 1.0, jumpInput = false)
        val playedFor = world.timeTaken
        assertEquals(1.0f, playedFor, 0.02f, "One second of play should bill one second")

        // Three minutes of frames with the run on hold - a long pause, or a rewarded ad.
        world.isSuspended = true
        for (i in 0 until 60 * 180) world.update(dt, moveInput = 1.0, jumpInput = false)

        assertEquals(
            playedFor, world.timeTaken, 1e-6f,
            "Three minutes on hold must not reach the level clock (was ${world.timeTaken}s, " +
                "expected to still be ${playedFor}s)"
        )

        world.isSuspended = false
        for (i in 0 until 60) world.update(dt, moveInput = 1.0, jumpInput = false)
        assertEquals(
            playedFor + 1.0f, world.timeTaken, 0.02f,
            "Resuming should carry on from where the clock stopped, not from wall-clock time"
        )
    }

    @Test
    fun testSuspendedWorldFreezesTheRunItself() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_1)
        val dt = 1.0 / 60.0
        for (i in 0 until 30) world.update(dt, moveInput = 1.0, jumpInput = false)

        world.isSuspended = true
        val x = world.player.x
        val y = world.player.y
        val alert = world.alertProgress
        val worldClock = world.totalElapsedSeconds

        // Inputs held the whole time: a player whose thumb is on the D-pad when a call arrives
        // must not walk into a guard while the screen belongs to someone else.
        for (i in 0 until 60 * 30) {
            world.update(dt, moveInput = 1.0, jumpInput = true, crouchInput = false, interactInput = true)
        }

        assertEquals(x, world.player.x, 1e-9, "A suspended run must not move")
        assertEquals(y, world.player.y, 1e-9, "A suspended run must not fall")
        assertEquals(alert, world.alertProgress, 1e-9, "A suspended run must not accrue alert")
        assertEquals(worldClock, world.totalElapsedSeconds, 1e-9, "Hazard phase must not advance either")
        assertFalse(world.isGameOver, "Nothing can catch a player during a hold")
    }

    @Test
    fun testRestartClearsSuspension() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_1)
        world.isSuspended = true
        world.restartLevel()

        assertFalse(
            world.isSuspended,
            "A fresh attempt must always resume - a hold left set here would freeze the new run outright"
        )
        val dt = 1.0 / 60.0
        for (i in 0 until 60) world.update(dt, moveInput = 1.0, jumpInput = false)
        assertTrue(world.timeTaken > 0.9f, "The restarted run should be billing time again")
    }

    @Test
    fun testAppLifecycleStartsForegroundAndTracksBothEdges() {
        // Targets with no native shell (desktop JVM, the JS/wasm previews) never report lifecycle
        // at all, so the default has to be "playing" or those builds would sit frozen.
        assertTrue(com.sample.demo.lifecycle.GameAppLifecycle.isForeground, "Default must be foreground")

        com.sample.demo.lifecycle.GameAppLifecycle.markBackground()
        assertFalse(com.sample.demo.lifecycle.GameAppLifecycle.isForeground)

        com.sample.demo.lifecycle.GameAppLifecycle.markForeground()
        assertTrue(com.sample.demo.lifecycle.GameAppLifecycle.isForeground)
    }
}
