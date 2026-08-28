package game.scene

import game.model.*
import game.scene.UiComponents.COLOR_BORDER_CYAN
import game.scene.UiComponents.COLOR_BORDER_GOLD
import game.scene.UiComponents.COLOR_BORDER_GREEN
import game.scene.UiComponents.COLOR_BORDER_RED
import game.scene.UiComponents.COLOR_TEXT_LIGHT
import game.scene.UiComponents.COLOR_TEXT_MUTED
import game.scene.UiComponents.createButton
import game.scene.UiComponents.createTacticalCard
import game.scene.UiComponents.drawStar
import korlibs.event.*
import korlibs.image.color.*
import korlibs.image.vector.*
import korlibs.io.async.*
import korlibs.korge.input.*
import korlibs.korge.scene.*
import korlibs.korge.service.storage.*
import korlibs.korge.view.*
import korlibs.korge.view.vector.*
import korlibs.math.geom.*
import korlibs.time.*
import kotlin.math.*

class GameplayScene(
    val levelData: LevelData = LevelData.DEFAULT_LEVEL_1
) : Scene() {

    override suspend fun SContainer.sceneMain() {
        val world = GameWorld.createDefault(levelData)
        val playerAnimations = PlayerAnimations.load()
        val levelStorage: LevelStorage = MapBackedLevelStorage(
            getRaw = { views.storage[it] },
            setRaw = { k, v -> views.storage[k] = v }
        )
        val profileStorage: GameProfileStorage = MapBackedGameProfileStorage(
            getRaw = { views.storage[it] },
            setRaw = { k, v -> views.storage[k] = v }
        )

        var isPaused = false

        // Background: Plain white background
        solidRect(800.0, 480.0, Colors.WHITE)

        // Everything inside worldView scrolls with the camera; HUD & Touch controls stay fixed.
        val worldView = container()
        val isSideScrolling = world.worldWidth > 800.0

        // Floors, walkways and walls (Solid pure black silhouette objects)
        for (platform in world.platforms) {
            if (platform in world.boxes) continue
            if (platform.width >= 1000.0 && platform.height >= 1000.0) continue // Skip bounds walls
            if (!isSideScrolling && (platform.x < 0 || platform.x >= 800)) continue

            // Main platform block - full solid black
            worldView.solidRect(platform.width, platform.height, Colors.BLACK).xy(platform.x, platform.y)
        }

        // Exit Point / Extraction Zone (Black gate with emerald neon highlight)
        val exitContainer = worldView.container().xy(world.exitZone.x, world.exitZone.y)
        exitContainer.solidRect(world.exitZone.width, world.exitZone.height, Colors.BLACK)
        exitContainer.solidRect(world.exitZone.width, 3.0, COLOR_BORDER_GREEN).xy(0.0, 0.0)
        exitContainer.solidRect(world.exitZone.width, 3.0, COLOR_BORDER_GREEN).xy(0.0, world.exitZone.height - 3.0)
        exitContainer.solidRect(3.0, world.exitZone.height, COLOR_BORDER_GREEN).xy(0.0, 0.0)
        exitContainer.solidRect(3.0, world.exitZone.height, COLOR_BORDER_GREEN).xy(world.exitZone.width - 3.0, 0.0)
        exitContainer.text("EXIT", textSize = 10.0, color = COLOR_BORDER_GREEN).xy(6.0, 6.0)

        // Boxes & obstacles: solid full black cover objects
        for (box in world.boxes) {
            if (box.width <= 0.0) continue
            val boxContainer = worldView.container().xy(box.x, box.y)
            boxContainer.solidRect(box.width, box.height, Colors.BLACK)
        }

        // Guards: vision cones first so they render beneath the bodies
        val guardCones = world.allGuards.map { worldView.graphics() }
        val guardContainers = world.allGuards.map { g -> worldView.container().xy(g.x, g.y) }
        val guardVisors = world.allGuards.mapIndexed { i, g ->
            guardContainers[i].solidRect(g.width, g.height, Colors.BLACK)
            guardContainers[i].solidRect(6.0, 4.0, Colors["#e74c3c"]).xy(g.width - 6.0, 10.0)
        }
        val guardBadges = world.allGuards.mapIndexed { i, _ ->
            guardContainers[i].text("?", textSize = 16.0, color = COLOR_BORDER_GOLD).xy(8.0, -22.0)
                .also { it.visible = false }
        }

        // Player View
        val playerContainer = worldView.container().xy(world.player.x, world.player.y)
        val playerSourceFrameHeight = PlayerAnimations.SOURCE_FRAME_HEIGHT
        val playerSourceSilhouetteHeight = PlayerAnimations.SOURCE_SILHOUETTE_HEIGHT
        val playerSourceFeetY = PlayerAnimations.SOURCE_FEET_Y // Ground line within the frame
        val playerVisualHeight = world.player.height
        val playerBaseScale = playerVisualHeight / playerSourceSilhouetteHeight
        val playerFeetAnchorY = playerSourceFeetY / playerSourceFrameHeight
        val playerSprite = playerContainer.sprite(playerAnimations.idle, Anchor2D(0.5, playerFeetAnchorY))
        playerSprite.scaleX = playerBaseScale
        playerSprite.scaleY = playerBaseScale
        playerSprite.xy(world.player.width / 2.0, world.player.height)
        playerSprite.playAnimationLooped(playerAnimations.idle)
        var playerAnimState = "idle"
        var playerFacingLeft = true

        val jumpLaunchFrame = PlayerAnimations.JUMP_LAUNCH_START
        val jumpAirborneFrame = PlayerAnimations.JUMP_RISE_START
        val jumpApexFrame = PlayerAnimations.JUMP_APEX
        val jumpTouchdownFrame = PlayerAnimations.JUMP_TOUCHDOWN
        val jumpLandFrame = PlayerAnimations.JUMP_LAND_START
        val jumpLastFrame = PlayerAnimations.JUMP_LAND_END
        val jumpLaunchDuration = 0.06
        val jumpLandDuration = 0.26
        val jumpCatchUpDuration = 0.06
        var jumpPhase = "none"
        var jumpPhaseElapsed = 0.0
        var jumpStartY = world.player.y

        // One gait cycle covers this much ground; measured off the plate so the feet stay planted.
        val walkCycleDistance = playerVisualHeight * PlayerAnimations.WALK_STRIDE_PER_HEIGHT
        var walkCycleProgress = 0.0
        // The idle->walk transition is a fixed short beat rather than distance-driven: its stride
        // is still building, so charging it the full per-frame distance would slide the feet.
        val walkTransitionDuration = 0.28
        var walkTransitionElapsed = 0.0
        var walkInTransition = false
        val manualFrameTime = 1_000_000.milliseconds

        // ==========================================
        // TOP HUD (Minimalist Panel)
        // ==========================================
        val topHud = container().xy(0.0, 0.0)
        topHud.solidRect(800.0, 52.0, Colors["#000000"].withAd(0.4))
        topHud.solidRect(800.0, 1.0, Colors.WHITE.withAd(0.3)).xy(0.0, 51.0)

        // Mission Title & Objective
        val missionTitleLabel = topHud.text(levelData.name.uppercase(), textSize = 14.0, color = COLOR_TEXT_LIGHT)
        missionTitleLabel.xy(18.0, 8.0)

        val objectiveLabel = topHud.text("OBJECTIVE: REACH EXTRACTION ZONE", textSize = 9.0, color = Colors.WHITE.withAd(0.7))
        objectiveLabel.xy(18.0, 28.0)

        // Center Stealth Threat Radar Meter
        val threatContainer = topHud.container().xy(240.0, 8.0)
        threatContainer.solidRect(320.0, 34.0, Colors["#000000"].withAd(0.5))
        threatContainer.solidRect(320.0, 1.0, Colors.WHITE.withAd(0.3)).xy(0.0, 0.0)
        threatContainer.solidRect(320.0, 1.0, Colors.WHITE.withAd(0.3)).xy(0.0, 33.0)

        val threatLabel = threatContainer.text("STEALTH 100%", textSize = 10.0, color = COLOR_BORDER_GREEN).xy(10.0, 4.0)
        val stanceBadge = threatContainer.text("[ STAND ]", textSize = 10.0, color = Colors.WHITE.withAd(0.8)).xy(230.0, 4.0)

        val threatTrack = threatContainer.solidRect(300.0, 6.0, Colors.WHITE.withAd(0.2)).xy(10.0, 20.0)
        val threatBar = threatContainer.solidRect(0.0, 6.0, COLOR_BORDER_GREEN).xy(10.0, 20.0)

        // Top Right: Timer & Pause Button
        val timeLabel = topHud.text("⏱ 0.0s", textSize = 13.0, color = COLOR_TEXT_LIGHT).xy(600.0, 16.0)

        val pauseBtn = topHud.createButton(
            text = "|| PAUSE",
            width = 84.0,
            height = 34.0,
            textSize = 11.0
        ) {
            isPaused = !isPaused
        }.xy(698.0, 8.0)

        // Alert banner flash on full detection
        var alertBannerTimer = 0.0
        val alertBanner = container().xy(0.0, 100.0)
        alertBanner.solidRect(800.0, 44.0, Colors["#000000"].withAd(0.8))
        alertBanner.solidRect(800.0, 2.0, COLOR_BORDER_RED).xy(0.0, 0.0)
        alertBanner.solidRect(800.0, 2.0, COLOR_BORDER_RED).xy(0.0, 42.0)
        val alertText = alertBanner.text("⚠ ALERT: SECURITY COMPROMISED ⚠", textSize = 17.0, color = COLOR_BORDER_RED)
        alertText.xy((800.0 - alertText.width) / 2.0, 12.0)
        alertBanner.visible = false

        // ==========================================
        // TACTILE MOBILE TOUCH CONTROLS
        // ==========================================
        var touchLeft = false
        var touchRight = false
        var touchJump = false
        var touchCrouch = false

        val controlsContainer = container().xy(0.0, 0.0)

        // Helper to create circular virtual touch button
        fun createTouchBtn(
            cx: Double,
            cy: Double,
            radius: Double,
            label: String,
            sublabel: String? = null,
            accentColor: RGBA = Colors.WHITE,
            onTouchChange: (Boolean) -> Unit
        ): Container {
            val btn = controlsContainer.container().xy(cx - radius, cy - radius)
            val bg = btn.graphics()

            fun drawState(pressed: Boolean) {
                bg.updateShape {
                    // Outer subtle ring
                    fill(if (pressed) accentColor.withAd(0.4) else Colors.WHITE.withAd(0.1)) {
                        circle(Point(radius, radius), radius)
                    }
                    // Inner area
                    fill(if (pressed) accentColor.withAd(0.6) else Colors.WHITE.withAd(0.15)) {
                        circle(Point(radius, radius), radius - 2.0)
                    }
                }
            }
            drawState(false)

            val textY = if (sublabel != null) radius - 14.0 else radius - 8.0
            val txt = btn.text(label, textSize = 14.0, color = COLOR_TEXT_LIGHT)
            txt.xy((radius * 2.0 - txt.width) / 2.0, textY)

            if (sublabel != null) {
                val sub = btn.text(sublabel, textSize = 8.0, color = accentColor)
                sub.xy((radius * 2.0 - sub.width) / 2.0, radius + 4.0)
            }

            btn.mouse {
                onDown {
                    onTouchChange(true)
                    drawState(true)
                }
                onUp {
                    onTouchChange(false)
                    drawState(false)
                }
                onOut {
                    onTouchChange(false)
                    drawState(false)
                }
            }
            return btn
        }

        // Bottom-Left D-Pad: Left & Right Movement
        val dpadY = 415.0
        createTouchBtn(cx = 55.0, cy = dpadY, radius = 32.0, label = "◀", sublabel = "LEFT", accentColor = COLOR_BORDER_CYAN) {
            touchLeft = it
        }
        createTouchBtn(cx = 135.0, cy = dpadY, radius = 32.0, label = "▶", sublabel = "RIGHT", accentColor = COLOR_BORDER_CYAN) {
            touchRight = it
        }

        // Bottom-Right Action Buttons: Jump & Crouch (Sneak)
        createTouchBtn(cx = 660.0, cy = dpadY, radius = 32.0, label = "▼", sublabel = "SNEAK", accentColor = COLOR_BORDER_GOLD) {
            touchCrouch = it
        }
        createTouchBtn(cx = 740.0, cy = dpadY, radius = 32.0, label = "▲", sublabel = "JUMP", accentColor = COLOR_BORDER_GREEN) {
            touchJump = it
        }

        // ==========================================
        // 1. PAUSE OVERLAY
        // ==========================================
        val pauseOverlay = container()
        val pauseDimBg = pauseOverlay.solidRect(800.0, 480.0, Colors.BLACK.withAd(0.82))
        val pausePanel = pauseOverlay.createTacticalCard(240.0, 75.0, 320.0, 330.0, accentColor = Colors.WHITE)

        pausePanel.solidRect(320.0, 28.0, Colors["#000000"].withAd(0.4)).xy(0.0, 0.0)
        pausePanel.solidRect(320.0, 1.0, Colors.WHITE.withAd(0.5)).xy(0.0, 27.0)
        pausePanel.text("SYSTEM PAUSED // HEIST FROZEN", textSize = 10.0, color = Colors.WHITE.withAd(0.8)).xy(14.0, 7.0)

        val pauseTitle = pausePanel.text("TACTICAL PAUSE", textSize = 20.0, color = COLOR_TEXT_LIGHT)
        pauseTitle.xy((320.0 - pauseTitle.width) / 2.0, 40.0)

        pausePanel.createButton("RESUME HEIST", width = 220.0, height = 44.0, textSize = 14.0) {
            isPaused = false
            pauseOverlay.visible = false
        }.xy(50.0, 85.0)

        pausePanel.createButton("RESTART LEVEL", width = 220.0, height = 42.0, textSize = 13.0) {
            sceneContainer.changeTo { GameplayScene(levelData) }
        }.xy(50.0, 142.0)

        pausePanel.createButton("SETTINGS & AUDIO", width = 220.0, height = 42.0, textSize = 13.0) {
            sceneContainer.changeTo { SettingsScene() }
        }.xy(50.0, 196.0)

        pausePanel.createButton("QUIT TO MENU", width = 220.0, height = 42.0, textSize = 13.0) {
            sceneContainer.changeTo { MainMenuScene() }
        }.xy(50.0, 250.0)

        pauseOverlay.visible = false

        // ==========================================
        // 2. CAUGHT / GAME OVER OVERLAY
        // ==========================================
        val caughtOverlay = container()
        caughtOverlay.solidRect(800.0, 480.0, Colors.BLACK.withAd(0.88))
        val caughtPanel = caughtOverlay.createTacticalCard(200.0, 75.0, 400.0, 330.0, accentColor = COLOR_BORDER_RED)

        caughtPanel.solidRect(400.0, 28.0, Colors["#000000"].withAd(0.4)).xy(0.0, 0.0)
        caughtPanel.solidRect(400.0, 1.0, COLOR_BORDER_RED).xy(0.0, 27.0)
        caughtPanel.text("OPERATIVE COMPROMISED // MISSION FAILED", textSize = 10.0, color = COLOR_BORDER_RED).xy(14.0, 7.0)

        val caughtTitle = caughtPanel.text("MISSION FAILED", textSize = 24.0, color = COLOR_BORDER_RED)
        caughtTitle.xy((400.0 - caughtTitle.width) / 2.0, 40.0)

        val caughtSub = caughtPanel.text("SPOTTED AND APPREHENDED BY GUARD", textSize = 12.0, color = COLOR_TEXT_LIGHT)
        caughtSub.xy((400.0 - caughtSub.width) / 2.0, 74.0)

        val tipBox = caughtPanel.container().xy(20.0, 106.0)
        tipBox.solidRect(360.0, 64.0, Colors["#000000"].withAd(0.5))
        tipBox.solidRect(2.0, 64.0, COLOR_BORDER_RED).xy(0.0, 0.0)
        tipBox.text("TACTICAL RECON TIP:", textSize = 10.0, color = COLOR_BORDER_GOLD).xy(10.0, 8.0)
        tipBox.text("Crouch-walk [SNEAK] to eliminate movement noise.\nStay out of guard vision cones and use crates as cover.", textSize = 10.0, color = COLOR_TEXT_MUTED).xy(10.0, 26.0)

        caughtPanel.createButton("RETRY INFILTRATION", width = 220.0, height = 44.0, textSize = 14.0) {
            sceneContainer.changeTo { GameplayScene(levelData) }
        }.xy(90.0, 190.0)

        caughtPanel.createButton("RETURN TO MENU", width = 220.0, height = 42.0, textSize = 13.0) {
            sceneContainer.changeTo { MainMenuScene() }
        }.xy(90.0, 246.0)

        caughtOverlay.visible = false

        // ==========================================
        // 3. LEVEL COMPLETE OVERLAY
        // ==========================================
        val winContainer = container().xy(120.0, 45.0)
        val winPanel = winContainer.createTacticalCard(0.0, 0.0, 560.0, 390.0, accentColor = COLOR_BORDER_GOLD)

        winPanel.solidRect(560.0, 28.0, Colors["#000000"].withAd(0.4)).xy(0.0, 0.0)
        winPanel.solidRect(560.0, 1.0, COLOR_BORDER_GOLD).xy(0.0, 27.0)
        winPanel.text("MISSION ACCOMPLISHED // EXTRACTION SUCCESS", textSize = 10.0, color = COLOR_BORDER_GOLD).xy(14.0, 7.0)

        val winTitle = winPanel.text("HEIST COMPLETED!", textSize = 22.0, color = COLOR_BORDER_GREEN)
        winTitle.xy((560.0 - winTitle.width) / 2.0, 36.0)

        val winStarsGraphics = winPanel.graphics().xy(0.0, 0.0)

        val star1Label = winPanel.text("Star 1: Extraction Complete", textSize = 11.0, color = COLOR_TEXT_LIGHT).xy(45.0, 122.0)
        val star2Label = winPanel.text("Star 2: Undetected (Ghost)", textSize = 11.0, color = COLOR_TEXT_LIGHT).xy(45.0, 144.0)
        val star3Label = winPanel.text("Star 3: Fast Time (≤ ${levelData.timeTargetSeconds.toInt()}s)", textSize = 11.0, color = COLOR_TEXT_LIGHT).xy(45.0, 166.0)

        // Bounty Box
        val bountyBox = winPanel.container().xy(40.0, 194.0)
        bountyBox.solidRect(480.0, 70.0, Colors["#000000"].withAd(0.5))
        bountyBox.solidRect(480.0, 1.0, COLOR_BORDER_CYAN.withAd(0.4)).xy(0.0, 0.0)

        val statsLabel = bountyBox.text("", textSize = 12.0, color = COLOR_BORDER_CYAN).xy(14.0, 10.0)
        val coinsEarnedLabel = bountyBox.text("", textSize = 13.0, color = COLOR_BORDER_GOLD).xy(14.0, 28.0)
        val bestLabel = bountyBox.text("", textSize = 11.0, color = COLOR_BORDER_GREEN).xy(14.0, 48.0)

        // Buttons Container
        val winBtnRow = winPanel.container().xy(40.0, 285.0)

        val allLevels = LevelData.DEFAULT_LEVELS
        val currentLevelIndex = allLevels.indexOfFirst { it.id == levelData.id }
        val nextLevel = if (currentLevelIndex >= 0 && currentLevelIndex + 1 < allLevels.size) allLevels[currentLevelIndex + 1] else null

        if (nextLevel != null) {
            winBtnRow.createButton("NEXT MISSION", width = 150.0, height = 44.0, textSize = 13.0) {
                sceneContainer.changeTo { GameplayScene(nextLevel) }
            }.xy(0.0, 0.0)
        } else {
            winBtnRow.createButton("ALL CLEAR!", width = 150.0, height = 44.0, textSize = 13.0) {
                sceneContainer.changeTo { LevelSelectScene() }
            }.xy(0.0, 0.0)
        }

        winBtnRow.createButton("RETRY", width = 140.0, height = 44.0, textSize = 13.0) {
            sceneContainer.changeTo { GameplayScene(levelData) }
        }.xy(165.0, 0.0)

        winBtnRow.createButton("MAIN MENU", width = 140.0, height = 44.0, textSize = 13.0) {
            sceneContainer.changeTo { MainMenuScene() }
        }.xy(320.0, 0.0)

        winContainer.visible = false

        world.onLevelComplete = {
            val result = world.getLevelResult()
            levelStorage.saveResult(result)
            val bestResult = levelStorage.getBestResult(result.levelId) ?: result

            // Calculate and award coins
            val multiplier = if (profileStorage.getProfile().isPremium) 2 else 1
            val earnedCoins = (levelData.coinRewardBase + result.starCount * levelData.coinRewardPerStar) * multiplier
            profileStorage.addCoins(earnedCoins)

            // Unlock next level in progression
            if (nextLevel != null) {
                profileStorage.unlockLevel(nextLevel.id)
            }

            winContainer.visible = true

            // Render 3 Stars
            winStarsGraphics.updateShape {
                val starPositions = listOf(220.0, 280.0, 340.0)
                val starsEarned = listOf(result.star1, result.star2, result.star3)

                for (i in 0 until 3) {
                    val cx = starPositions[i]
                    val cy = 84.0
                    val isEarned = starsEarned[i]
                    val fillColor = if (isEarned) COLOR_BORDER_GOLD else Colors["#182334"]
                    drawStar(cx, cy, outerR = 18.0, innerR = 7.5, fillColor = fillColor)
                }
            }

            star1Label.text = "Star 1: Extraction Complete — ${if (result.star1) "[EARNED]" else "[MISSED]"}"
            star1Label.color = if (result.star1) COLOR_BORDER_GOLD else COLOR_TEXT_MUTED

            star2Label.text = "Star 2: Undetected (Ghost) — ${if (result.star2) "[EARNED]" else "[MISSED - ${world.spottedCount} alert(s)]"}"
            star2Label.color = if (result.star2) COLOR_BORDER_GOLD else COLOR_TEXT_MUTED

            val timeTakenStr = ((result.timeTaken * 10).toInt() / 10.0).toString()
            star3Label.text = "Star 3: Fast Time (≤ ${result.timeTargetSeconds.toInt()}s) — ${if (result.star3) "[EARNED: ${timeTakenStr}s]" else "[MISSED: ${timeTakenStr}s]"}"
            star3Label.color = if (result.star3) COLOR_BORDER_GOLD else COLOR_TEXT_MUTED

            statsLabel.text = "HEIST RESULT: ${result.starCount}/3 STARS • TIME: ${timeTakenStr}s • ALERTS: ${world.spottedCount}"
            coinsEarnedLabel.text = "+$earnedCoins HEIST BOUNTY EARNED! ${if (profileStorage.getProfile().isPremium) "(2x Shadow Pass Multiplier Active)" else ""}"
            val bestTimeStr = ((bestResult.timeTaken * 10).toInt() / 10.0).toString()
            bestLabel.text = "OPERATIVE RECORD: ${bestResult.starCount}/3 Stars (Best Time: ${bestTimeStr}s)"
        }

        world.onGameOver = {
            caughtOverlay.visible = true
        }

        world.onSpotted = { _, _ ->
            alertBannerTimer = 0.8
            alertBanner.visible = true
        }

        var totalElapsedSeconds = 0.0

        // Main game update loop
        addUpdater { dt ->
            if (views.input.keys.justPressed(Key.ESCAPE) || views.input.keys.justPressed(Key.P)) {
                if (!world.isLevelComplete && !world.isGameOver) {
                    isPaused = !isPaused
                }
            }

            pauseOverlay.visible = isPaused

            if (isPaused || world.isLevelComplete || world.isGameOver) {
                return@addUpdater
            }

            val dtSec = dt.seconds.coerceIn(0.0, 0.1)
            totalElapsedSeconds += dtSec

            // Read Inputs (Merging Keyboard + On-Screen Touch Controls)
            val leftPressed = views.input.keys[Key.LEFT] || views.input.keys[Key.A] || touchLeft
            val rightPressed = views.input.keys[Key.RIGHT] || views.input.keys[Key.D] || touchRight
            val jumpPressed = views.input.keys[Key.UP] || views.input.keys[Key.W] || views.input.keys[Key.SPACE] || touchJump
            val crouchPressed = views.input.keys[Key.DOWN] || views.input.keys[Key.S] || views.input.keys[Key.C] ||
                    views.input.keys[Key.LEFT_CONTROL] || views.input.keys[Key.RIGHT_CONTROL] || touchCrouch

            val moveInput = when {
                leftPressed && !rightPressed -> -1.0
                rightPressed && !leftPressed -> 1.0
                else -> 0.0
            }

            // Update domain simulation
            world.update(dtSec, moveInput, jumpPressed, crouchPressed)

            // Sync visual positions
            playerContainer.xy(world.player.x, world.player.y)
            for (i in world.allGuards.indices) {
                guardContainers[i].xy(world.allGuards[i].x, world.allGuards[i].y)
            }

            // Camera: Center player, clamped to level bounds
            val cameraX = (world.player.x + world.player.width / 2.0 - 400.0)
                .coerceIn(0.0, (world.worldWidth - 800.0).coerceAtLeast(0.0))
            worldView.x = -cameraX

            // Jump animation machine
            if (playerAnimState != "jump" && !world.player.isGrounded) {
                playerAnimState = "jump"
                jumpPhase = "launch"
                jumpPhaseElapsed = 0.0
                jumpStartY = world.player.y
                playerSprite.playAnimationLooped(playerAnimations.jump, manualFrameTime)
            } else if (playerAnimState == "jump") {
                jumpPhaseElapsed += dtSec
                when (jumpPhase) {
                    "launch" -> if (jumpPhaseElapsed >= jumpLaunchDuration) {
                        jumpPhase = "air"
                        jumpPhaseElapsed = 0.0
                    }
                    "air" -> if (world.player.isGrounded) {
                        jumpPhase = "land"
                        jumpPhaseElapsed = 0.0
                    }
                    else -> if (!world.player.isGrounded) {
                        jumpPhase = "launch"
                        jumpPhaseElapsed = 0.0
                        jumpStartY = world.player.y
                    } else if (jumpPhaseElapsed >= jumpLandDuration) {
                        jumpPhase = "none"
                        playerAnimState = "none"
                    }
                }
            }

            if (playerAnimState != "jump") {
                val groundedState = if (world.player.isMoving) "walk" else "idle"
                if (groundedState != playerAnimState) {
                    // Every entry into walk - from a standstill or from landing a jump while
                    // still holding a direction - gets the lean-in, so the loop is never joined
                    // mid-stride straight out of an idle-shaped landing pose.
                    playerAnimState = groundedState
                    if (groundedState == "walk") {
                        walkInTransition = true
                        walkTransitionElapsed = 0.0
                        walkCycleProgress = 0.0
                        playerSprite.playAnimationLooped(playerAnimations.walk, manualFrameTime)
                    } else {
                        playerSprite.playAnimationLooped(playerAnimations.idle)
                    }
                }
            }

            playerSprite.y = world.player.height
            if (playerAnimState == "jump") {
                val maxJumpHeight =
                    (world.player.jumpSpeed * world.player.jumpSpeed) / (2.0 * world.player.gravity)
                val altitudeProgress = ((jumpStartY - world.player.y) / maxJumpHeight).coerceIn(0.0, 1.0)

                val frameIndex = when (jumpPhase) {
                    "launch" -> {
                        val t = (jumpPhaseElapsed / jumpLaunchDuration).coerceIn(0.0, 1.0)
                        jumpLaunchFrame + (t * (jumpAirborneFrame - jumpLaunchFrame)).toInt()
                    }
                    "air" -> if (world.player.vy < 0.0) {
                        jumpAirborneFrame + (altitudeProgress * (jumpApexFrame - jumpAirborneFrame)).toInt()
                    } else {
                        jumpApexFrame + ((1.0 - altitudeProgress) * (jumpTouchdownFrame - jumpApexFrame)).toInt()
                    }
                    else -> {
                        val t = (jumpPhaseElapsed / jumpLandDuration).coerceIn(0.0, 1.0)
                        jumpLandFrame + (t * (jumpLastFrame - jumpLandFrame)).toInt()
                    }
                }
                playerSprite.setFrame(frameIndex.coerceIn(0, jumpLastFrame))

                val holdFactor = when (jumpPhase) {
                    "launch" -> 1.0
                    "air" -> {
                        val catchUp = (jumpPhaseElapsed / jumpCatchUpDuration).coerceIn(0.0, 1.0)
                        1.0 - (1.0 - (1.0 - catchUp) * (1.0 - catchUp))
                    }
                    else -> 0.0
                }
                playerSprite.y += (jumpStartY - world.player.y) * holdFactor
            } else if (playerAnimState == "walk") {
                if (walkInTransition) {
                    walkTransitionElapsed += dtSec
                    val t = (walkTransitionElapsed / walkTransitionDuration).coerceIn(0.0, 1.0)
                    val span = PlayerAnimations.WALK_TRANSITION_END - PlayerAnimations.WALK_TRANSITION_START
                    playerSprite.setFrame(
                        PlayerAnimations.WALK_TRANSITION_START + (t * span).toInt()
                    )
                    // The transition runs straight into the loop's first frame in the source
                    // footage, so handing over at the end is seamless.
                    if (t >= 1.0) walkInTransition = false
                } else {
                    walkCycleProgress =
                        (walkCycleProgress + abs(world.player.vx) * dtSec / walkCycleDistance) % 1.0
                    val loopLength = PlayerAnimations.WALK_LOOP_LENGTH
                    playerSprite.setFrame(
                        PlayerAnimations.WALK_LOOP_START +
                            (walkCycleProgress * loopLength).toInt().coerceIn(0, loopLength - 1)
                    )
                }
            }

            // Flip sprite to face direction
            if (moveInput < 0) {
                playerFacingLeft = true
            } else if (moveInput > 0) {
                playerFacingLeft = false
            }
            playerSprite.scaleX = playerBaseScale * (if (playerFacingLeft) -1.0 else 1.0)
            playerSprite.scaleY = playerBaseScale * (if (world.player.isCrouching) 0.8 else 1.0)

            // Update guard visors and badges
            for (i in world.allGuards.indices) {
                val g = world.allGuards[i]
                guardBadges[i].visible = g.state == GuardState.INVESTIGATING
                guardVisors[i].x = if (g.facing >= 0) g.width - 6.0 else 0.0
                guardVisors[i].color =
                    if (g.state == GuardState.INVESTIGATING) COLOR_BORDER_GOLD else Colors["#e74c3c"]
            }

            val alertProgress = world.alertProgress

            // Render vision cones
            for (i in world.allGuards.indices) {
                val g = world.allGuards[i]
                val coneColor = when {
                    alertBannerTimer > 0.0 || world.isGameOver -> {
                        Colors["#ff3838"].withAd(0.55)
                    }
                    alertProgress > 0.0 -> {
                        val pulse = 0.5 + 0.5 * sin(totalElapsedSeconds * 16.0)
                        val r = (241 + (255 - 241) * alertProgress).toInt().coerceIn(0, 255)
                        val gVal = (196 + (56 - 196) * alertProgress).toInt().coerceIn(0, 255)
                        val b = (15 + (56 - 15) * alertProgress).toInt().coerceIn(0, 255)
                        val baseAlpha = 0.32 + 0.30 * alertProgress
                        val pulsedAlpha = (baseAlpha + 0.15 * pulse * alertProgress).coerceIn(0.1, 0.75)
                        RGBA(r, gVal, b, (pulsedAlpha * 255).toInt())
                    }
                    g.state == GuardState.INVESTIGATING -> {
                        Colors["#f39c12"].withAd(0.42)
                    }
                    else -> {
                        Colors["#e67e22"].withAd(0.32)
                    }
                }
                val visionPolygon = VisionSystem.computeVisionPolygon(
                    origin = g.eyePosition,
                    facingAngle = g.facingAngle,
                    range = g.visionRange,
                    fov = g.visionFov,
                    occluders = world.occluders
                )
                guardCones[i].updateShape {
                    if (visionPolygon.isNotEmpty()) {
                        fill(coneColor) {
                            val first = visionPolygon.first()
                            moveTo(Point(first.x, first.y))
                            for (p in 1 until visionPolygon.size) {
                                val pt = visionPolygon[p]
                                lineTo(Point(pt.x, pt.y))
                            }
                            close()
                        }
                    }
                }
            }

            // Update HUD Status & Threat Meter
            if (alertBannerTimer > 0.0) {
                alertBannerTimer -= dtSec
                if (alertBannerTimer <= 0.0) {
                    alertBanner.visible = false
                }
            }

            val timeStr = ((world.timeTaken * 10).toInt() / 10.0).toString()
            timeLabel.text = "⏱ ${timeStr}s"

            stanceBadge.text = if (world.player.isCrouching) "[ SNEAK (SILENT) ]" else if (world.player.isMoving) "[ SPRINT (NOISE) ]" else "[ STAND ]"
            stanceBadge.color = if (world.player.isCrouching) COLOR_BORDER_GREEN else if (world.player.isMoving) COLOR_BORDER_GOLD else COLOR_BORDER_CYAN

            when {
                world.isGameOver -> {
                    threatLabel.text = "CAUGHT // ALARM ACTIVE"
                    threatLabel.color = COLOR_BORDER_RED
                    threatBar.width = 300.0
                    threatBar.color = COLOR_BORDER_RED
                }
                alertBannerTimer > 0.0 -> {
                    threatLabel.text = "SECURITY ALERT!"
                    threatLabel.color = COLOR_BORDER_RED
                    threatBar.width = 300.0
                    threatBar.color = COLOR_BORDER_RED
                }
                world.alertProgress > 0.0 -> {
                    val percent = (world.alertProgress * 100).toInt()
                    threatLabel.text = "SUSPICIOUS [$percent%]"
                    threatLabel.color = COLOR_BORDER_GOLD
                    threatBar.width = 300.0 * world.alertProgress
                    threatBar.color = COLOR_BORDER_GOLD
                }
                world.allGuards.any { it.state == GuardState.INVESTIGATING } -> {
                    threatLabel.text = "GUARD INVESTIGATING"
                    threatLabel.color = COLOR_BORDER_GOLD
                    threatBar.width = 60.0
                    threatBar.color = COLOR_BORDER_GOLD
                }
                else -> {
                    threatLabel.text = "STEALTH 100%"
                    threatLabel.color = COLOR_BORDER_GREEN
                    threatBar.width = 0.0
                    threatBar.color = COLOR_BORDER_GREEN
                }
            }
        }
    }
}
