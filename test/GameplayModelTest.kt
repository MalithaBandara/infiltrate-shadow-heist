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
        val hiddenPlayer = Player(x = 180.0, y = 332.0)
        val isHiddenSpotted = VisionSystem.isPlayerSpotted(guard, hiddenPlayer, listOf(crate))
        assertFalse(isHiddenSpotted, "Player behind crate must NOT be spotted by guard")

        // Player walked past crate into clear view (x = 330, between crate and guard)
        val visiblePlayer = Player(x = 330.0, y = 332.0)
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
        world.player.y = 380.0 - 48.0

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
        world.player.y = 380.0 - 48.0
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
        world.player.y = 332.0

        // Move right towards guard
        for (i in 0 until 60) {
            world.update(dt = 1.0 / 60.0, moveInput = 1.0, jumpInput = false)
        }

        // Player width is 26.0, guard left is 500.0 -> player should stop at 474.0 (500 - 26)
        assertEquals(474.0, world.player.x, 0.01, "Player should collide with guard's left edge and not pass through")

        // Place player to the right of guard at x = 550
        world.player.x = 550.0
        world.player.y = 332.0

        // Move left towards guard
        for (i in 0 until 60) {
            world.update(dt = 1.0 / 60.0, moveInput = -1.0, jumpInput = false)
        }

        // Guard right edge is 500.0 + 26.0 = 526.0 -> player should stop at 526.0
        assertEquals(526.0, world.player.x, 0.01, "Player should collide with guard's right edge and not pass through")

        // Test guard pushing stationary player
        val movingGuard = Guard(
            x = 480.0,
            y = 332.0,
            patrolMinX = 300.0,
            patrolMaxX = 700.0,
            speed = 60.0,
            facing = -1.0 // Guard moving left towards player at 450
        )
        val pushWorld = world.copy(guard = movingGuard)
        pushWorld.player.x = 450.0

        // Guard moves left 30px (from 480 to 450, guard.left = 450)
        pushWorld.update(dt = 0.5, moveInput = 0.0, jumpInput = false)
        assertEquals(450.0, movingGuard.x, 0.01, "Guard should have reached x = 450")
        assertEquals(424.0, pushWorld.player.x, 0.01, "Guard moving into stationary player should push player to guard.left - width (450 - 26 = 424)")
    }

    @Test
    fun testDistanceScaledDetectionRate() {
        // Test 1: Close range (player 40px away from guard, e.g. at x = 460)
        val closeWorld = GameWorld.createDefault().copy(occluders = emptyList())
        closeWorld.guard.x = 500.0
        closeWorld.guard.facing = -1.0 // Facing left
        closeWorld.guard.speed = 0.0
        closeWorld.player.x = 460.0 // ~44px from guard eye (504)
        closeWorld.player.y = 332.0

        closeWorld.update(dt = 0.1, moveInput = 0.0, jumpInput = false)
        val closeAlertProgress = closeWorld.alertProgress

        // Test 2: Far range (player 230px away from guard, e.g. at x = 270)
        val farWorld = GameWorld.createDefault().copy(occluders = emptyList())
        farWorld.guard.x = 500.0
        farWorld.guard.facing = -1.0 // Facing left
        farWorld.guard.speed = 0.0
        farWorld.player.x = 270.0 // ~234px from guard eye (504), near max range 260
        farWorld.player.y = 332.0

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
        world.player.y = 332.0

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
}
