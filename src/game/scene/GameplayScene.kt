package game.scene

import game.model.*
import korlibs.event.*
import korlibs.image.color.*
import korlibs.korge.scene.*
import korlibs.korge.view.*
import korlibs.korge.view.vector.*
import korlibs.math.geom.*
import korlibs.time.*
import kotlin.math.*

class GameplayScene : Scene() {

    override suspend fun SContainer.sceneMain() {
        val world = GameWorld.createDefault()

        // Background
        solidRect(800.0, 480.0, Colors["#16161d"])

        // Ground and Walls
        for (platform in world.platforms) {
            if (platform == world.crate) continue // Drawn separately for custom styling
            if (platform.x < 0 || platform.x >= 800) continue // Skip boundary walls from visual drawing

            // Ground base
            solidRect(platform.width, platform.height, Colors["#242530"]).xy(platform.x, platform.y)
            // Ground top border highlight
            solidRect(platform.width, 3.0, Colors["#454859"]).xy(platform.x, platform.y)
        }

        // Patrol Path Indicator on the floor
        val patrolMarkerY = world.guard.y + world.guard.height + 1.0
        solidRect(
            world.guard.patrolMaxX - world.guard.patrolMinX + world.guard.width,
            2.0,
            Colors["#3a3d52"]
        ).xy(world.guard.patrolMinX, patrolMarkerY)

        // Exit Point / Extraction Zone (Win condition marker placed beyond guard)
        val exitContainer = container().xy(world.exitZone.x, world.exitZone.y)
        exitContainer.solidRect(world.exitZone.width, world.exitZone.height, Colors["#145a32"].withAd(0.75))
        // Exit border
        exitContainer.solidRect(world.exitZone.width, 3.0, Colors["#2ecc71"]).xy(0.0, 0.0)
        exitContainer.solidRect(3.0, world.exitZone.height, Colors["#2ecc71"]).xy(0.0, 0.0)
        exitContainer.solidRect(3.0, world.exitZone.height, Colors["#2ecc71"]).xy(world.exitZone.width - 3.0, 0.0)
        // Exit sign text
        exitContainer.text("EXIT", textSize = 11.0, color = Colors["#a9dfbf"]).xy(8.0, 8.0)

        // Crate (Occluder & Platform)
        val crateContainer = container().xy(world.crate.x, world.crate.y)
        crateContainer.solidRect(world.crate.width, world.crate.height, Colors["#6d4c41"])
        crateContainer.solidRect(world.crate.width, 4.0, Colors["#8d6e63"]).xy(0.0, 0.0)
        crateContainer.solidRect(world.crate.width, 4.0, Colors["#5d4037"]).xy(0.0, world.crate.height - 4.0)
        // Crate cross planks
        crateContainer.solidRect(4.0, world.crate.height, Colors["#5d4037"]).xy(0.0, 0.0)
        crateContainer.solidRect(4.0, world.crate.height, Colors["#5d4037"]).xy(world.crate.width - 4.0, 0.0)
        crateContainer.solidRect(world.crate.width, 4.0, Colors["#5d4037"]).xy(0.0, world.crate.height / 2.0 - 2.0)

        // Guard Vision Cone (Rendered beneath entities)
        val visionConeGraphics = graphics()

        // Guard View (Red / Dark Orange silhouette)
        val guardContainer = container().xy(world.guard.x, world.guard.y)
        val guardBody = guardContainer.solidRect(world.guard.width, world.guard.height, Colors["#e74c3c"])
        val guardVisor = guardContainer.solidRect(8.0, 4.0, Colors["#ffffff"]).xy(world.guard.width - 8.0, 10.0)

        // Player View (Grey rectangle sprite)
        val playerContainer = container().xy(world.player.x, world.player.y)
        val playerBody = playerContainer.solidRect(world.player.width, world.player.height, Colors["#b0bec5"])
        val playerEye = playerContainer.solidRect(6.0, 4.0, Colors["#37474f"]).xy(world.player.width - 8.0, 8.0)

        // UI / HUD
        val hudContainer = container().xy(16.0, 16.0)
        val titleText = hudContainer.text(
            "INFILTRATE: SHADOW HEIST",
            textSize = 18.0,
            color = Colors["#ecf0f1"]
        ).xy(0.0, 0.0)

        val instructionText = hudContainer.text(
            "Controls: [A / D] or [Left / Right] Move | [W / Up / Space] Jump | Reach EXIT without getting caught!",
            textSize = 13.0,
            color = Colors["#95a5a6"]
        ).xy(0.0, 24.0)

        val statusText = hudContainer.text(
            "Status: STEALTH (Alerts: 0)",
            textSize = 14.0,
            color = Colors["#2ecc71"]
        ).xy(0.0, 46.0)

        // Alert banner flash on detection
        var alertBannerTimer = 0.0
        val alertBanner = solidRect(800.0, 40.0, Colors["#c0392b"].withAd(0.85)).xy(0.0, 120.0)
        val alertText = text("! CAUGHT BY GUARD !", textSize = 20.0, color = Colors.WHITE).xy(300.0, 128.0)
        alertBanner.visible = false
        alertText.visible = false

        world.onSpotted = { guard, player ->
            alertBannerTimer = 0.8 // Show caught banner for 0.8 seconds
            alertBanner.visible = true
            alertText.visible = true
        }

        // Level Complete Overlay
        val winBanner = solidRect(800.0, 90.0, Colors["#145a32"].withAd(0.92)).xy(0.0, 180.0)
        val winTitle = text("LEVEL COMPLETE!", textSize = 28.0, color = Colors["#2ecc71"]).xy(270.0, 195.0)
        val winSubtext = text("Infiltration successful! Exit reached.", textSize = 14.0, color = Colors["#d5f5e3"]).xy(278.0, 235.0)
        winBanner.visible = false
        winTitle.visible = false
        winSubtext.visible = false

        world.onLevelComplete = {
            winBanner.visible = true
            winTitle.visible = true
            winSubtext.text = "Infiltration successful! Exit reached with ${world.spottedCount} alert(s)."
            winSubtext.visible = true
        }

        var totalElapsedSeconds = 0.0

        // Main game update loop
        addUpdater { dt ->
            val dtSec = dt.seconds.coerceIn(0.0, 0.1)
            totalElapsedSeconds += dtSec

            // Read Inputs
            val leftPressed = views.input.keys[Key.LEFT] || views.input.keys[Key.A]
            val rightPressed = views.input.keys[Key.RIGHT] || views.input.keys[Key.D]
            val jumpPressed = views.input.keys[Key.UP] || views.input.keys[Key.W] || views.input.keys[Key.SPACE]

            val moveInput = when {
                leftPressed && !rightPressed -> -1.0
                rightPressed && !leftPressed -> 1.0
                else -> 0.0
            }

            // Update domain world simulation
            world.update(dtSec, moveInput, jumpPressed)

            // Sync visual positions
            playerContainer.xy(world.player.x, world.player.y)
            guardContainer.xy(world.guard.x, world.guard.y)

            // Update player facing indicator
            if (moveInput < 0) {
                playerEye.x = 2.0
            } else if (moveInput > 0) {
                playerEye.x = world.player.width - 8.0
            }

            // Update guard visor position
            if (world.guard.facing >= 0) {
                guardVisor.x = world.guard.width - 8.0
            } else {
                guardVisor.x = 0.0
            }

            // Render Vision Cone with dynamic color & flashing detection cue
            val visionPolygon = VisionSystem.computeVisionPolygon(
                origin = world.guard.eyePosition,
                facingAngle = world.guard.facingAngle,
                range = world.guard.visionRange,
                fov = world.guard.visionFov,
                occluders = world.occluders
            )

            val alertProgress = world.alertProgress
            val coneColor = when {
                alertBannerTimer > 0.0 -> {
                    Colors["#e74c3c"].withAd(0.55)
                }
                alertProgress > 0.0 -> {
                    // Flash / pulse effect when player is detected in cone
                    val pulse = 0.5 + 0.5 * sin(totalElapsedSeconds * 16.0)
                    val r = (241 + (231 - 241) * alertProgress).toInt().coerceIn(0, 255)
                    val g = (196 + (76 - 196) * alertProgress).toInt().coerceIn(0, 255)
                    val b = (15 + (60 - 15) * alertProgress).toInt().coerceIn(0, 255)
                    val baseAlpha = 0.25 + 0.30 * alertProgress
                    val pulsedAlpha = (baseAlpha + 0.15 * pulse * alertProgress).coerceIn(0.1, 0.75)
                    RGBA(r, g, b, (pulsedAlpha * 255).toInt())
                }
                else -> {
                    Colors["#f1c40f"].withAd(0.22)
                }
            }

            visionConeGraphics.updateShape {
                if (visionPolygon.isNotEmpty()) {
                    fill(coneColor) {
                        val first = visionPolygon.first()
                        moveTo(Point(first.x, first.y))
                        for (i in 1 until visionPolygon.size) {
                            val pt = visionPolygon[i]
                            lineTo(Point(pt.x, pt.y))
                        }
                        close()
                    }
                }
            }

            // Update HUD Status & Alerts
            if (alertBannerTimer > 0.0) {
                alertBannerTimer -= dtSec
                if (alertBannerTimer <= 0.0) {
                    alertBanner.visible = false
                    alertText.visible = false
                }
            }

            when {
                world.isLevelComplete -> {
                    statusText.text = "Status: LEVEL COMPLETE"
                    statusText.color = Colors["#2ecc71"]
                }
                alertBannerTimer > 0.0 -> {
                    statusText.text = "Status: CAUGHT! (Alerts: ${world.spottedCount})"
                    statusText.color = Colors["#e74c3c"]
                }
                world.alertProgress > 0.0 -> {
                    val percent = (world.alertProgress * 100).toInt()
                    statusText.text = "Status: SUSPICIOUS [$percent%] (Alerts: ${world.spottedCount})"
                    statusText.color = Colors["#e67e22"]
                }
                else -> {
                    statusText.text = "Status: STEALTH (Alerts: ${world.spottedCount})"
                    statusText.color = Colors["#2ecc71"]
                }
            }
        }
    }
}
