package game.scene

import com.sample.demo.ads.getContinueAdBridge
import game.model.*
import game.scene.UiComponents.COLOR_PRIMARY
import game.scene.UiComponents.COLOR_ACCENT_CYAN
import game.scene.UiComponents.COLOR_ACCENT_GOLD
import game.scene.UiComponents.COLOR_ACCENT_GREEN
import game.scene.UiComponents.COLOR_ACCENT_RED
import game.scene.UiComponents.COLOR_BORDER_CYAN
import game.scene.UiComponents.COLOR_BORDER_GOLD
import game.scene.UiComponents.COLOR_BORDER_GREEN
import game.scene.UiComponents.COLOR_BORDER_RED
import game.scene.UiComponents.COLOR_TEXT_LIGHT
import game.scene.UiComponents.COLOR_TEXT_MUTED
import game.scene.UiComponents.createButton
import game.scene.UiComponents.createTacticalCard
import game.scene.UiComponents.drawPlayIcon
import game.scene.UiComponents.drawQuitIcon
import game.scene.UiComponents.drawStar
import game.scene.UiComponents.uiGraphics
import korlibs.event.*
import korlibs.image.bitmap.*
import korlibs.image.color.*
import korlibs.image.font.*
import korlibs.image.format.*
import korlibs.image.vector.*
import korlibs.io.async.*
import korlibs.io.file.std.*
import korlibs.korge.input.*
import korlibs.math.geom.vector.*
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
        val sounds = GameAudio.load()
        val sfxContext = coroutineContext
        val levelStorage: LevelStorage = MapBackedLevelStorage(
            getRaw = { views.storage[it] },
            setRaw = { k, v -> views.storage[k] = v }
        )
        val profileStorage: GameProfileStorage = MapBackedGameProfileStorage(
            getRaw = { views.storage[it] },
            setRaw = { k, v -> views.storage[k] = v }
        )

        var isPaused = false

        val canvasW = sceneWidth.toDouble().coerceAtLeast(800.0)
        val canvasH = sceneHeight.toDouble().coerceAtLeast(480.0)

        val worldZoom = 1.35
        val baseGroundY = 410.0

        val bgFileName = levelData.resolvedBackgroundImage
        val bgmgBitmap = try { resourcesVfs[bgFileName].readBitmap() } catch (_: Throwable) { null }
        val crateBitmap = try { resourcesVfs["crate.png"].readBitmap() } catch (_: Throwable) { null }
        val chainedCrateBitmap = try { resourcesVfs["chainedcrate.png"].readBitmap() } catch (_: Throwable) { null }
        val fenceBitmap = try { resourcesVfs["fence.png"].readBitmap() } catch (_: Throwable) { null }
        val fence2Bitmap = try { resourcesVfs["fence2.png"].readBitmap() } catch (_: Throwable) { null }
        val leftBtnBitmap = try { resourcesVfs["left.png"].readBitmap() } catch (_: Throwable) { null }
        val rightBtnBitmap = try { resourcesVfs["right.png"].readBitmap() } catch (_: Throwable) { null }
        val crouchBtnBitmap = try { resourcesVfs["crouch.png"].readBitmap() } catch (_: Throwable) { null }
        val jumpBtnBitmap = try { resourcesVfs["jump.png"].readBitmap() } catch (_: Throwable) { null }
        val interactBtnBitmap = try { resourcesVfs["interact.png"].readBitmap() } catch (_: Throwable) { null }
        // The main menu's torn-paper button strips. They already live in resources/ (the Compose
        // menu reads its own copies out of composeResources), so the pause menu can be built from
        // the very same art rather than a lookalike drawn in vectors.
        val paperBtnBitmaps = listOf("button1.png", "button2.png", "button3.png", "button4.png")
            .map { name -> try { resourcesVfs[name].readBitmap() } catch (_: Throwable) { null } }

        // Combined background & midground layer container (parallax rate 0.2x, looping, unzoomed at native screen height)
        val bgmgContainer = container()
        val bgmgImages = mutableListOf<Image>()
        val bgmgTileW = if (bgmgBitmap != null) {
            val bgScale = canvasH / bgmgBitmap.height
            val tileW = bgmgBitmap.width * bgScale
            val count = max(6, (canvasW / tileW).toInt() + 4)
            for (i in 0 until count) {
                val img = bgmgContainer.image(bgmgBitmap) {
                    size(tileW + 1.0, canvasH)
                }.xy(i * tileW, 0.0)
                bgmgImages.add(img)
            }
            tileW
        } else {
            bgmgContainer.solidRect(canvasW, canvasH, Colors["#16161d"])
            800.0
        }

        // Everything inside worldView scrolls with the camera; HUD & Touch controls stay fixed.
        val worldView = container()
        worldView.scale(worldZoom)
        val isSideScrolling = world.worldWidth > 800.0

        // Floors, walkways and boundary walls (Solid black platforms with tiny rough edge irregularities)
        for (platform in world.platforms) {
            if (platform in world.boxes) continue
            if (platform.width >= 1000.0 && platform.height >= 1000.0) continue // Skip bounds walls
            if (!isSideScrolling && (platform.x < 0 || platform.x >= 800)) continue

            val platCont = worldView.container().xy(platform.x, platform.y)
            renderRoughBlock(platCont, platform.width, platform.height, seed = (platform.x * 47.0 + platform.y).toLong())
        }

        // Exit Point / Extraction Zone (Black gate with emerald neon highlight)
        val exitContainer = worldView.container().xy(world.exitZone.x, world.exitZone.y)
        exitContainer.solidRect(world.exitZone.width, world.exitZone.height, Colors.BLACK)
        exitContainer.solidRect(world.exitZone.width, 3.0, COLOR_BORDER_GREEN).xy(0.0, 0.0)
        exitContainer.solidRect(world.exitZone.width, 3.0, COLOR_BORDER_GREEN).xy(0.0, world.exitZone.height - 3.0)
        exitContainer.solidRect(3.0, world.exitZone.height, COLOR_BORDER_GREEN).xy(0.0, 0.0)
        exitContainer.solidRect(3.0, world.exitZone.height, COLOR_BORDER_GREEN).xy(world.exitZone.width - 3.0, 0.0)
        exitContainer.text("EXIT", textSize = 10.0, color = COLOR_BORDER_GREEN).xy(6.0, 6.0)

        // Tactical boxes, step crates, hanging chained crates, and perimeter fences
        for (box in world.boxes) {
            if (box.width <= 0.0) continue
            val boxContainer = worldView.container().xy(box.x, box.y)

            // 1. Fence 1 (Foreground starting perimeter fence)
            if ((box == world.fence1 || (box.width in 140.0..165.0 && box.height in 130.0..155.0 && box.x < 300.0)) && fenceBitmap != null) {
                boxContainer.image(fenceBitmap) {
                    size(box.width, box.height + 2.0)
                }.xy(0.0, 0.0)
            }
            // 2. Fence 2 (Background starting perimeter fence)
            else if ((box == world.fence2 || (box.width in 165.0..195.0 && box.height in 130.0..155.0 && box.x < 300.0)) && fence2Bitmap != null) {
                boxContainer.image(fence2Bitmap) {
                    size(box.width, box.height + 2.0)
                }.xy(0.0, 0.0)
            }
            // 3. Hanging Chained Crate (matches bounding box exactly)
            else if (box.y <= 0.0 && box.height > 150.0 && chainedCrateBitmap != null) {
                boxContainer.image(chainedCrateBitmap) {
                    size(box.width, box.height)
                }.xy(0.0, 0.0)
            }
            // 4. Step Crate (matches bounding box exactly)
            else if (box.height < 70.0 && box.width < 150.0 && crateBitmap != null) {
                boxContainer.image(crateBitmap) {
                    size(box.width, box.height)
                }.xy(0.0, 0.0)
            }
            // 5. Long Structural Platforms and Blocks (Solid blocks with tiny rough edge irregularities)
            else {
                renderRoughBlock(boxContainer, box.width, box.height, seed = (box.x * 101.0 + box.y).toLong())
            }
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

        // Cameras: vision cones first beneath bodies
        val cameraCones = world.cameras.map { worldView.graphics() }
        val cameraContainers = world.cameras.map { c -> worldView.container().xy(c.x, c.y) }
        val cameraMounts = world.cameras.mapIndexed { i, c ->
            // Mount & body: distinct grey housing
            cameraContainers[i].solidRect(c.width, c.height, Colors["#34495e"])
            cameraContainers[i].solidRect(c.width - 4.0, c.height - 4.0, Colors["#2c3e50"]).xy(2.0, 2.0)
            cameraContainers[i].solidRect(6.0, 6.0, Colors["#e74c3c"]).xy((c.width - 6.0) / 2.0, (c.height - 6.0) / 2.0)
        }

        // Player View
        val playerContainer = worldView.container().xy(world.player.x, world.player.y)
        val playerSourceFrameHeight = PlayerAnimations.SOURCE_FRAME_HEIGHT
        val playerSourceSilhouetteHeight = PlayerAnimations.SOURCE_SILHOUETTE_HEIGHT
        val playerSourceFeetY = PlayerAnimations.SOURCE_FEET_Y // Ground line within the frame
        val playerVisualHeight = world.player.height
        val playerBaseScale = playerVisualHeight / playerSourceSilhouetteHeight
        val playerFeetAnchorY = playerSourceFeetY / playerSourceFrameHeight
        val idleFeetOffset = (playerSourceFeetY - PlayerAnimations.IDLE_FEET_Y) * playerBaseScale
        val playerSprite = playerContainer.sprite(playerAnimations.idle, Anchor2D(0.5, playerFeetAnchorY))
        playerSprite.scaleX = playerBaseScale
        playerSprite.scaleY = playerBaseScale
        playerSprite.xy(world.player.width / 2.0, world.player.height + idleFeetOffset)
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

        // Landing absorption: when the player lands while moving, play the first few frames of
        // the jump's landing clip as a brief cushion before handing over to the walk lean-in.
        // Without this, landing on a higher platform while running snaps the posture from a
        // tucked airborne pose to a fully upright walk lean-in in a single frame.
        var landingAbsorb = false
        var landingAbsorbElapsed = 0.0
        val landingAbsorbDuration = 0.12  // just the first ~half of the landing clip
        // How many landing frames to play during the absorption (frames 27..31 out of 27..43).
        val landingAbsorbFrames = 5


        // Crouch: entering/exiting are the down/up transition played once; holding pins the last
        // frame. crouchFrameProgress is continuous (not just a phase flag) so re-toggling crouch
        // mid-transition reverses smoothly from wherever the animation currently is, instead of
        // snapping to a fixed pose first.
        // Audio trigger state. Footsteps are edge-triggered off the same distance-driven gait
        // cycle that picks the walk frame, so a step fires when the foot lands rather than on a
        // timer that drifts against the animation whenever speed changes.
        var stepAlternate = false
        val sfxVolume = { profileStorage.getProfile().sfxVolume }

        val crouchLastFrame = PlayerAnimations.CROUCH_LAST
        val crouchDownDuration = 0.22
        val crouchUpDuration = 0.18
        var crouchPhase = "none"
        var crouchFrameProgress = 0.0
        // Climb: Player.isClimbing drives the actual world position (see Player.advanceClimb),
        
        val crouchwalkCycleDistance = playerVisualHeight * PlayerAnimations.CROUCHWALK_STRIDE_PER_HEIGHT
        var crouchwalkCycleProgress = 0.0
        // Unlike the idle->walk lean-in, the crouch-walk lean-in is distance-driven like its loop.
        // Its 91 frames are already a walk in the footage - they start on the crouch's held pose
        // and build the stride out of it - so they carry the same ground speed as the loop and a
        // fixed duration would either blur them or slide the feet. Progress is in cycles, so the
        // lean-in ends after (91 / 53) cycles of travel and runs straight into the loop's first
        // frame, which is its own next frame in the source.
        val crouchwalkTransitionCycles =
            (PlayerAnimations.CROUCHWALK_TRANSITION_END - PlayerAnimations.CROUCHWALK_TRANSITION_START + 1)
                .toDouble() / PlayerAnimations.CROUCHWALK_LOOP_LENGTH
        var crouchwalkTransitionProgress = 0.0
        var crouchwalkInTransition = false
        // and its climbProgress picks the frame here, so pose and position stay in step.
        val climbFirstFrame = PlayerAnimations.CLIMB_START
        val climbLastFrame = PlayerAnimations.CLIMB_END
        val climbFrameSpan = climbLastFrame - climbFirstFrame

        // One gait cycle covers this much ground; measured off the plate so the feet stay planted.
        val walkCycleDistance = playerVisualHeight * PlayerAnimations.WALK_STRIDE_PER_HEIGHT
        var walkCycleProgress = 0.0
        // The idle->walk transition is a fixed short beat rather than distance-driven: its stride
        // is still building, so charging it the full per-frame distance would slide the feet.
        val walkTransitionDuration = 0.28
        var walkTransitionElapsed = 0.0
        var walkInTransition = false
        val manualFrameTime = 1_000_000.milliseconds

        val bebasFont = try { resourcesVfs["BebasNeue-Regular.ttf"].readTtfFont() } catch (_: Throwable) { DefaultTtfFont }

        // Modern Button Builder for In-Game Overlay Modals
        fun Container.createTacticalMenuBtn(
            text: String,
            width: Double,
            height: Double,
            x: Double,
            y: Double,
            primary: Boolean = false,
            accentColor: RGBA = COLOR_ACCENT_CYAN,
            onClick: suspend () -> Unit
        ): Container {
            val btn = container().xy(x, y)
            val bg = btn.uiGraphics()
            fun drawState(hover: Boolean, down: Boolean) {
                bg.updateShape {
                    clear()
                    val fillCol = when {
                        primary && down -> accentColor.withAd(0.75)
                        primary && hover -> accentColor
                        primary -> Colors.WHITE
                        down -> accentColor.withAd(0.35)
                        hover -> Colors["#222630"].withAd(0.95)
                        else -> Colors["#12151B"].withAd(0.85)
                    }
                    val strokeCol = if (primary) Colors.TRANSPARENT else (if (hover || down) accentColor else Colors.WHITE.withAd(0.18))
                    fill(fillCol) {
                        roundRect(0.0, 0.0, width, height, 8.0, 8.0)
                    }
                    if (!primary) {
                        stroke(strokeCol, StrokeInfo(thickness = 1.2)) {
                            roundRect(0.0, 0.0, width, height, 8.0, 8.0)
                        }
                    }
                }
            }
            drawState(false, false)
            val fontCol = if (primary) Colors["#0A0C10"] else COLOR_PRIMARY
            val label = btn.text(text.uppercase(), textSize = height * 0.42, font = bebasFont, color = fontCol)
            label.graphicsRenderer = GraphicsRenderer.GPU
            label.xy((width - label.width) / 2.0, (height - label.height) / 2.0 - 1.0)

            btn.onOut { drawState(false, false) }
            btn.onOver { drawState(true, false) }
            btn.onDown { drawState(true, true) }
            btn.onUp { drawState(true, false) }
            btn.mouse { onClick { onClick() } }
            return btn
        }

        // A pause-menu button in the main menu's language: a torn white paper strip with the
        // label and icon stamped on it in ink. Same textures, same ink colour, same Bebas face,
        // so pausing does not drop the player into a different-looking game.
        val paperInk = Colors["#17140F"]

        // Shared layout columns for every paper button, as fractions of its width. Taken off the
        // Compose menu's own proportions, where the icon sits about a third in and the label
        // starts just past it.
        val ICON_COLUMN = 0.35
        val LABEL_COLUMN = 0.42

        fun Container.createPaperMenuBtn(
            label: String,
            texture: Bitmap?,
            width: Double,
            height: Double,
            x: Double,
            y: Double,
            iconDrawer: ShapeBuilder.() -> Unit,
            onClick: suspend () -> Unit
        ): Container {
            val btn = container().xy(x, y)
            // The strips are hand-torn, so their edges are part of the art - stretch to fit and
            // let the irregular edge land where it lands rather than insetting it away.
            val img = if (texture != null) btn.image(texture) { size(width, height) } else null
            if (img == null) {
                btn.uiGraphics().updateShape {
                    fill(Colors["#F6F4EE"]) { roundRect(0.0, 0.0, width, height, 2.0, 2.0) }
                }
            }
            val iconG = btn.uiGraphics()
            iconG.updateShape { iconDrawer() }
            val text = btn.text(label.uppercase(), textSize = height * 0.44, font = bebasFont, color = paperInk)
            text.graphicsRenderer = GraphicsRenderer.GPU

            // Icon column and text column are fixed fractions of the button width, not centred
            // per row. Centring each icon+label pair independently makes every row start at a
            // different x - which is what the labels being different lengths did here - whereas
            // the menu's buttons hang all four icons and all four labels on two shared columns.
            iconG.xy(width * ICON_COLUMN, height / 2.0)
            text.xy(width * LABEL_COLUMN, (height - text.height) / 2.0 - 1.0)

            fun paint(hover: Boolean, down: Boolean) {
                val tint = when {
                    down -> Colors["#BFBCB4"]
                    hover -> Colors["#FFFFFF"]
                    else -> Colors["#EFEDE6"]
                }
                img?.colorMul = tint
                if (img == null) iconG.alpha = if (down) 0.6 else 1.0
            }
            paint(false, false)
            btn.onOut { paint(false, false) }
            btn.onOver { paint(true, false) }
            btn.onDown { paint(true, true) }
            btn.onUp { paint(true, false) }
            btn.mouse { onClick { onClick() } }
            return btn
        }

        // ==========================================
        // HEADS-UP LAYER
        // ==========================================
        // Deliberately chrome-free. There is no top bar, and nothing here is permanent: every
        // element is either transient (the mission toast, the spotted flash) or diegetic (the
        // detection pip, which rides on the operative in world space). A clean run therefore
        // shows no HUD at all over the action, which is the point of the genre.
        //
        // The run timer was removed from the screen, not from the game: it still runs and still
        // decides the third star, and it is reported on the results card at the end. A tenths-
        // resolution clock ticking in the player's eyeline pushes them to rush, which is exactly
        // the wrong instinct in a stealth level.
        //
        // The stealth meter was removed as a bar and re-sited on the guards and cameras that are
        // actually looking at you - see guardPips / cameraPips.
        val hudLayer = container()

        // --- Mission toast: names the level, states the objective, then dissolves ----------
        // levelData.name already carries its own number ("01: Warehouse Infiltration"), so the
        // old "$id: $name" form printed the level twice. Just the name.
        val introToast = hudLayer.container().xy(24.0, 20.0)
        val introScrim = introToast.uiGraphics()

        val missionTitleLabel = introToast.text(
            levelData.name.uppercase(), textSize = 22.0, font = bebasFont, color = COLOR_PRIMARY
        )
        missionTitleLabel.graphicsRenderer = GraphicsRenderer.GPU
        missionTitleLabel.xy(20.0, 8.0)

        val objectiveLabel = introToast.text(
            "REACH THE EXTRACTION ZONE", textSize = 12.0, font = bebasFont, color = COLOR_TEXT_LIGHT
        )
        objectiveLabel.graphicsRenderer = GraphicsRenderer.GPU
        objectiveLabel.alpha = 0.78
        objectiveLabel.xy(20.0, 34.0)

        // The level's sky is bright and its ground is black, so neither a light nor a dark type
        // colour survives on its own. A scrim sized to the text is the only thing that reads on
        // both, and it costs nothing in permanent chrome because the whole toast dissolves.
        val introToastW = max(missionTitleLabel.width, objectiveLabel.width) + 36.0
        introScrim.updateShape {
            fill(Colors["#05070A"].withAd(0.55)) { roundRect(0.0, 0.0, introToastW, 58.0, 10.0, 10.0) }
            fill(COLOR_ACCENT_CYAN) { roundRect(9.0, 12.0, 2.5, 34.0, 1.25, 1.25) }
        }

        val introHoldSeconds = 3.0
        val introFadeSeconds = 1.2
        var introElapsed = 0.0

        // Once the toast has gone the objective does not go with it. A stealth level runs long
        // enough that "what am I actually doing here" is a real question several minutes in, and
        // the answer is one short line - cheap enough to leave up for the whole run. It takes the
        // toast's own corner, so the block reads as shrinking to its essential line rather than
        // one element leaving and a different one arriving somewhere else.
        val objectiveHud = hudLayer.container().xy(24.0, 20.0)
        val objectiveScrim = objectiveHud.uiGraphics()
        val objectiveText = objectiveHud.text(
            "OBJECTIVE: REACH THE EXTRACTION ZONE",
            textSize = 11.0, font = bebasFont, color = COLOR_TEXT_LIGHT
        )
        objectiveText.graphicsRenderer = GraphicsRenderer.GPU
        objectiveText.xy(21.0, 9.0)
        val objectiveHudW = objectiveText.width + 34.0
        objectiveScrim.updateShape {
            fill(Colors["#05070A"].withAd(0.50)) { roundRect(0.0, 0.0, objectiveHudW, 32.0, 8.0, 8.0) }
            fill(COLOR_ACCENT_CYAN) { roundRect(9.0, 8.0, 2.5, 16.0, 1.25, 1.25) }
        }
        objectiveHud.alpha = 0.0
        objectiveHud.visible = false
        val objectiveHudAlpha = 0.85
        val objectiveFadeInSeconds = 0.6

        // --- Pause: one floating glass button, in the same visual language as the D-pad -----
        // Top-right corner, opposite the objective strip. It carries a 24px inset off both edges
        // so it still clears the status bar and a rounded display corner without drifting out of
        // the corner it belongs in.
        val pauseRadius = 21.0
        val pauseBtn = hudLayer.container().xy(canvasW - 24.0 - pauseRadius * 2.0, 20.0)
        val pauseBg = pauseBtn.uiGraphics()
        fun drawPauseBtn(isHover: Boolean, isDown: Boolean) {
            pauseBg.updateShape {
                clear()
                val fillCol = if (isDown || isHover) COLOR_ACCENT_CYAN.withAd(0.32) else Colors["#0A0C10"].withAd(0.55)
                val strokeCol = if (isDown || isHover) COLOR_ACCENT_CYAN else COLOR_ACCENT_CYAN.withAd(0.45)
                fill(fillCol) { circle(Point(pauseRadius, pauseRadius), pauseRadius) }
                stroke(strokeCol, StrokeInfo(thickness = if (isDown) 2.4 else 1.6)) {
                    circle(Point(pauseRadius, pauseRadius), pauseRadius - 1.0)
                }
                fill(Colors.WHITE.withAd(0.92)) {
                    roundRect(pauseRadius - 6.4, pauseRadius - 7.5, 4.2, 15.0, 1.6, 1.6)
                    roundRect(pauseRadius + 2.2, pauseRadius - 7.5, 4.2, 15.0, 1.6, 1.6)
                }
            }
        }
        drawPauseBtn(false, false)
        pauseBtn.onOut { drawPauseBtn(false, false) }
        pauseBtn.onOver { drawPauseBtn(true, false) }
        pauseBtn.onDown { drawPauseBtn(true, true) }
        pauseBtn.onUp { drawPauseBtn(true, false) }
        pauseBtn.mouse { onClick { isPaused = !isPaused } }

        // There is deliberately no "spotted" banner. The old one was set visible by onSpotted,
        // which GameWorld fires on the very tick it also sets isGameOver - so the MISSION FAILED
        // card went up in the same frame and covered it, every time. A flash nobody can ever see
        // is not feedback; the card is the feedback, and the pip below is the warning that comes
        // before it.

        // --- Detection pips: the stealth meter, drawn on whoever is doing the detecting ------
        // Not on the player. A meter over the operative tells you that you are being seen but not
        // by what, so you cannot tell which cone to break. Drawn over the guard or camera instead,
        // it answers both questions at once and points at the thing you have to get away from.
        // Pips live in world space above each detector, so they hide themselves the moment that
        // detector loses you and cost no permanent screen space.
        val detectPipRadius = 9.5

        // Gold at first glance, red by the time it is about to fill. The ramp is continuous
        // because the interesting information is "how close am I to being caught", and a colour
        // that only changes at the very end answers that a frame too late to act on.
        fun detectPipTint(progress: Double): RGBA {
            val t = progress.coerceIn(0.0, 1.0)
            return RGBA(
                (241 + (255 - 241) * t).toInt().coerceIn(0, 255),
                (196 + (56 - 196) * t).toInt().coerceIn(0, 255),
                (15 + (56 - 15) * t).toInt().coerceIn(0, 255),
                255
            )
        }

        fun Graphics.drawDetectPip(progress: Double, tint: RGBA, pulse: Double) {
            updateShape {
                clear()
                fill(Colors["#05070A"].withAd(0.70)) { circle(Point(0.0, 0.0), detectPipRadius) }
                if (progress > 0.001) {
                    // Wedge swept clockwise from twelve o'clock, built from segments rather than
                    // an arc primitive so it renders identically on every backend.
                    fill(tint.withAd(0.34 + 0.30 * pulse)) {
                        val steps = 4 + (progress * 30).toInt()
                        moveTo(Point(0.0, 0.0))
                        for (i in 0..steps) {
                            val a = -PI / 2.0 + PI * 2.0 * progress * (i.toDouble() / steps)
                            lineTo(Point(cos(a) * (detectPipRadius - 2.0), sin(a) * (detectPipRadius - 2.0)))
                        }
                        close()
                    }
                }
                stroke(tint.withAd(0.85), StrokeInfo(thickness = 1.6)) {
                    circle(Point(0.0, 0.0), detectPipRadius)
                }
                // Exclamation once the alert is all but full - the last beat to break line of
                // sight before GameWorld calls it a catch.
                if (progress >= 0.9) {
                    fill(tint) {
                        rect(-1.7, -6.2, 3.4, 7.2)
                        rect(-1.7, 2.8, 3.4, 3.4)
                    }
                }
            }
        }

        val guardPips = world.allGuards.mapIndexed { i, g ->
            guardContainers[i].uiGraphics().xy(g.width / 2.0, -14.0).also { it.visible = false }
        }
        val cameraPips = world.cameras.mapIndexed { i, c ->
            cameraContainers[i].uiGraphics().xy(c.width / 2.0, -14.0).also { it.visible = false }
        }

        // ==========================================
        // TACTICAL MOBILE TOUCH CONTROLS (Modern GPU Vectors)
        // ==========================================
        var touchLeft = false
        var touchRight = false
        var touchJump = false
        var touchCrouch = false
        var touchInteract = false

        val controlsContainer = container().xy(0.0, 0.0)

        // Helper to create circular virtual touch button with GPU vector rendering
        fun createTouchBtn(
            cx: Double,
            cy: Double,
            radius: Double,
            sublabel: String,
            accentColor: RGBA = COLOR_ACCENT_CYAN,
            iconDrawer: ShapeBuilder.() -> Unit,
            onTouchChange: (Boolean) -> Unit
        ): Container {
            val btn = controlsContainer.container().xy(cx - radius, cy - radius)
            val bg = btn.uiGraphics()
            val iconScale = if (radius >= 42.0) 1.55 else 1.35
            val iconG = btn.uiGraphics().xy(radius, radius - 6.0)
            iconG.scale(iconScale)

            fun drawState(pressed: Boolean) {
                bg.updateShape {
                    clear()
                    val fillCol = if (pressed) accentColor.withAd(0.4) else Colors["#0A0C10"].withAd(0.55)
                    val strokeCol = if (pressed) accentColor else accentColor.withAd(0.45)
                    val strokeW = if (pressed) 2.4 else 1.6
                    fill(fillCol) {
                        circle(Point(radius, radius), radius)
                    }
                    stroke(strokeCol, StrokeInfo(thickness = strokeW)) {
                        circle(Point(radius, radius), radius - 1.0)
                    }
                }
                iconG.updateShape {
                    clear()
                    iconDrawer()
                }
            }
            drawState(false)

            val sub = btn.text(sublabel, textSize = 11.0, font = bebasFont, color = accentColor)
            sub.graphicsRenderer = GraphicsRenderer.GPU
            sub.xy((radius * 2.0 - sub.width) / 2.0, radius + 10.0)

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

        // Tactical Mobile Touch Controls Layout (Supports Left/Right Handed Swapped Mode)
        //
        // Two rules drive the numbers below.
        //
        // 1. Nothing sits in a corner. The bottom corners of a landscape phone are where the OS
        //    puts its own gestures (home indicator, back swipe) and where a notch or punch-hole
        //    eats the top ones, so every control is inset from both edges it is near. The old
        //    layout put the left chevron 18px from the left edge and the jump button 24px from
        //    the right, which is inside those zones on real hardware.
        //
        // 2. Jump anchors the action cluster and the other two sit on an arc around it. It is
        //    the most frequent press and the only timing-critical one - a jump that arrives late
        //    is a failed gap - so it takes the thumb's resting position, the largest radius, and
        //    the shortest reach, inboard of the screen edge rather than out at it.
        //
        //    Crouch and interact ride a circle centred on jump, both swept outward and upward:
        //    crouch low on the arc (held for long stretches, so the nearer of the two) and
        //    interact high on it (contextual, pressed least, furthest reach). An arc rather than
        //    a column because the thumb travels in one - every secondary button is then the same
        //    distance from where the thumb already is, instead of one being twice as far as the
        //    other.
        //
        //    Gaps between neighbouring buttons are 12px - tight enough that each cluster reads as
        //    one control surface, wide enough that a thumb pad landing between two of them still
        //    resolves to the one it is closest to.
        val isControlsSwapped = profileStorage.getProfile().controlsSwapped
        val edgeInset = 46.0                      // clear of the side gesture strips
        val bottomInset = 38.0                    // clear of the home indicator
        val moveRadius = 42.0
        val jumpRadius = 44.0
        val crouchRadius = 38.0
        val interactRadius = 38.0

        val controlsY = canvasH - bottomInset - moveRadius

        val btnGap = 12.0
        val moveSpan = moveRadius * 2.0 + btnGap
        val moveLeftX = if (isControlsSwapped) canvasW - edgeInset - moveRadius - moveSpan else edgeInset + moveRadius
        val moveRightX = if (isControlsSwapped) canvasW - edgeInset - moveRadius else edgeInset + moveRadius + moveSpan

        // Jump is the hub; crouch and interact hang off it on one arc.
        val jumpX = if (isControlsSwapped) 180.0 else canvasW - 180.0
        val jumpY = canvasH - bottomInset - jumpRadius
        val outward = if (isControlsSwapped) -1.0 else 1.0

        // One radius for both, so neither secondary is a longer reach than the other. 15 degrees
        // and 70 degrees above horizontal puts 55 degrees between them, which on this radius is
        // an 87px chord against the 76px their two radii need - the same ~11px breathing room
        // the rest of the layout uses.
        val actionArcRadius = jumpRadius + crouchRadius + btnGap
        val crouchAngle = 15.0 * PI / 180.0
        val interactAngle = 70.0 * PI / 180.0
        val crouchX = jumpX + outward * cos(crouchAngle) * actionArcRadius
        val crouchY = jumpY - sin(crouchAngle) * actionArcRadius
        val interactX = jumpX + outward * cos(interactAngle) * actionArcRadius
        val interactY = jumpY - sin(interactAngle) * actionArcRadius

        // Image Buttons for controls
        fun createImgBtn(cx: Double, cy: Double, radius: Double, bmp: Bitmap?, fallbackColor: RGBA, fallbackDraw: ShapeBuilder.() -> Unit, onTouch: (Boolean) -> Unit) {
            if (bmp != null) {
                val btn = controlsContainer.container().xy(cx - radius, cy - radius)
                val img = btn.image(bmp) { size(radius * 2.0, radius * 2.0) }
                
                btn.mouse {
                    onDown { onTouch(true); img.alpha = 0.6 }
                    onUp { onTouch(false); img.alpha = 1.0 }
                    onOut { onTouch(false); img.alpha = 1.0 }
                }
            } else {
                createTouchBtn(cx, cy, radius, "", fallbackColor, fallbackDraw, onTouch)
            }
        }

        createImgBtn(moveLeftX, controlsY, moveRadius, leftBtnBitmap, COLOR_ACCENT_CYAN, { drawLeftChevron(Colors.WHITE) }) {
            touchLeft = it
        }
        createImgBtn(moveRightX, controlsY, moveRadius, rightBtnBitmap, COLOR_ACCENT_CYAN, { drawRightChevron(Colors.WHITE) }) {
            touchRight = it
        }

        // Action Buttons: Jump, Crouch, Interact (Standard Mobile Action Arc)
        createImgBtn(crouchX, crouchY, crouchRadius, crouchBtnBitmap, COLOR_ACCENT_GOLD, { drawSneakArrow(COLOR_ACCENT_GOLD) }) {
            touchCrouch = it
        }
        createImgBtn(jumpX, jumpY, jumpRadius, jumpBtnBitmap, COLOR_ACCENT_GREEN, { drawJumpArrow(COLOR_ACCENT_GREEN) }) {
            touchJump = it
        }
        createImgBtn(interactX, interactY, interactRadius, interactBtnBitmap, COLOR_ACCENT_CYAN, { drawInteractIcon(COLOR_ACCENT_CYAN) }) {
            touchInteract = it
        }

        // ==========================================
        // TACTICAL POWERUP QUICK-DOCK (Dynamic Floating)
        // ==========================================
        data class PowerupHudButton(
            val type: PowerupType,
            val keyNum: String,
            val btnContainer: Container,
            val bg: Graphics,
            val nameText: Text,
            val countText: Text
        )

        val powerupTypes = listOf(
            PowerupType.SMOKE_SCREEN to "1",
            PowerupType.PHANTOM_CLOAK to "2",
            PowerupType.INVISIBILITY to "3",
            PowerupType.NOISE_SUPPRESSION to "4"
        )

        // Sized as touch targets first: 62x48 with a 10px gutter clears the 44px minimum on
        // every axis, which the old 66x38 chips did not. Same glass treatment as the movement
        // controls so the whole bottom edge reads as one control surface.
        val powerupBtnW = 62.0
        val powerupBtnH = 48.0
        val powerupBtnGap = 10.0
        val powerupBtnRadius = 14.0
        val totalPowerupWidth = powerupTypes.size * powerupBtnW + (powerupTypes.size - 1) * powerupBtnGap
        val startPowerupX = (canvasW - totalPowerupWidth) / 2.0
        // Same bottom inset as the movement pad, so the dock and the D-pad sit on one line and
        // neither reaches into the home-indicator strip (the old y left 14px of clearance).
        val powerupBtnY = canvasH - bottomInset - powerupBtnH

        fun tryActivatePowerup(type: PowerupType) {
            if (world.isLevelComplete || world.isGameOver || isPaused) return
            if (profileStorage.consumePowerup(type)) {
                world.activatePowerup(type)
            }
        }

        val powerupDockContainer = controlsContainer.container().xy(0.0, 0.0)
        powerupDockContainer.visible = false

        val powerupHudButtons = powerupTypes.mapIndexed { index, (type, keyNum) ->
            val bx = startPowerupX + index * (powerupBtnW + powerupBtnGap)
            val btnCont = powerupDockContainer.container().xy(bx, powerupBtnY)
            val bg = btnCont.uiGraphics()

            val nameTxt = btnCont.text(type.shortName, textSize = 10.0, font = bebasFont, color = COLOR_TEXT_MUTED)
            nameTxt.graphicsRenderer = GraphicsRenderer.GPU
            nameTxt.xy((powerupBtnW - nameTxt.width) / 2.0, 7.0)

            val countTxt = btnCont.text("x0", textSize = 17.0, font = bebasFont, color = COLOR_BORDER_GOLD)
            countTxt.graphicsRenderer = GraphicsRenderer.GPU
            countTxt.xy((powerupBtnW - countTxt.width) / 2.0, 22.0)

            btnCont.mouse {
                onClick {
                    tryActivatePowerup(type)
                }
            }

            PowerupHudButton(type, keyNum, btnCont, bg, nameTxt, countTxt)
        }

        // ==========================================
        // 1. PAUSE OVERLAY (Heist Dossier - matches the main menu)
        // ==========================================
        // Rebuilt to the main menu's look: near-black ground, a stacked lockup of heavy Bebas
        // caps over a hairline rule, and torn-paper buttons with ink labels. The old card - cyan
        // hairline border, "SYSTEM PAUSED // PROTOCOL FROZEN" badge, dark pill buttons - was
        // from the earlier tactical-HUD theme that the menu has since moved off.
        val pauseOverlay = container()
        pauseOverlay.solidRect(canvasW, canvasH, Colors["#07080A"].withAd(0.92))

        val pauseBtnW = 300.0
        val pauseBtnH = 52.0
        val pauseBtnGap = 14.0
        val pauseBlockH = 52.0 + 8.0 + 18.0 + 30.0 + 3 * pauseBtnH + 2 * pauseBtnGap
        val pauseBlockTop = (canvasH - pauseBlockH) / 2.0
        val pauseBtnX = (canvasW - pauseBtnW) / 2.0

        val pauseTitle = pauseOverlay.text("PAUSED", textSize = 52.0, font = bebasFont, color = Colors["#F6F4EE"])
        pauseTitle.graphicsRenderer = GraphicsRenderer.GPU
        pauseTitle.xy((canvasW - pauseTitle.width) / 2.0, pauseBlockTop)

        // Mission name directly under the title, no rule between them - the title is already
        // separated from the subtitle by weight and size, and the hairline only added a seam.
        val pauseSubtitle = pauseOverlay.text(
            levelData.name.uppercase(), textSize = 14.0, font = bebasFont, color = COLOR_TEXT_MUTED
        )
        pauseSubtitle.graphicsRenderer = GraphicsRenderer.GPU
        pauseSubtitle.xy((canvasW - pauseSubtitle.width) / 2.0, pauseBlockTop + 62.0)

        val pauseBtnY0 = pauseBlockTop + 52.0 + 8.0 + 18.0 + 30.0

        pauseOverlay.createPaperMenuBtn(
            "RESUME", paperBtnBitmaps[0], pauseBtnW, pauseBtnH, pauseBtnX, pauseBtnY0,
            iconDrawer = { drawPlayIcon(false) }
        ) {
            isPaused = false
            pauseOverlay.visible = false
        }

        pauseOverlay.createPaperMenuBtn(
            "RESTART", paperBtnBitmaps[1], pauseBtnW, pauseBtnH, pauseBtnX, pauseBtnY0 + pauseBtnH + pauseBtnGap,
            iconDrawer = { drawRestartIcon(paperInk) }
        ) {
            sceneContainer.changeTo { GameplayScene(levelData) }
        }

        pauseOverlay.createPaperMenuBtn(
            "QUIT", paperBtnBitmaps[2], pauseBtnW, pauseBtnH, pauseBtnX, pauseBtnY0 + 2 * (pauseBtnH + pauseBtnGap),
            iconDrawer = { drawQuitIcon(false) }
        ) {
            views.storage["nav_target"] = "menu"
            sceneContainer.changeTo { GameplayScene(levelData) }
        }

        pauseOverlay.visible = false

        // ==========================================
        // 2. CAUGHT / GAME OVER OVERLAY
        // ==========================================
        val caughtOverlay = container()
        val caughtDimBg = caughtOverlay.solidRect(canvasW, canvasH, Colors.BLACK.withAd(0.88))
        val caughtPanelW = 420.0
        val caughtPanelH = 340.0
        val caughtPanel = caughtOverlay.createTacticalCard((canvasW - caughtPanelW) / 2.0, (canvasH - caughtPanelH) / 2.0, caughtPanelW, caughtPanelH, accentColor = COLOR_BORDER_RED)

        caughtPanel.solidRect(caughtPanelW, 28.0, Colors.BLACK.withAd(0.5)).xy(0.0, 0.0)
        caughtPanel.solidRect(caughtPanelW, 1.0, COLOR_BORDER_RED.withAd(0.5)).xy(0.0, 27.0)
        val caughtBadge = caughtPanel.text("OPERATIVE COMPROMISED // MISSION FAILED", textSize = 11.0, font = bebasFont, color = COLOR_BORDER_RED).xy(14.0, 7.0)
        caughtBadge.graphicsRenderer = GraphicsRenderer.GPU

        val caughtTitle = caughtPanel.text("MISSION FAILED", textSize = 28.0, font = bebasFont, color = COLOR_BORDER_RED)
        caughtTitle.graphicsRenderer = GraphicsRenderer.GPU
        caughtTitle.xy((caughtPanelW - caughtTitle.width) / 2.0, 36.0)

        val caughtSub = caughtPanel.text("SPOTTED AND APPREHENDED BY GUARD PATROL", textSize = 12.0, font = bebasFont, color = COLOR_TEXT_MUTED)
        caughtSub.graphicsRenderer = GraphicsRenderer.GPU
        caughtSub.xy((caughtPanelW - caughtSub.width) / 2.0, 68.0)

        val tipBox = caughtPanel.container().xy(24.0, 98.0)
        tipBox.solidRect(372.0, 68.0, Colors.BLACK.withAd(0.6))
        tipBox.solidRect(3.0, 68.0, COLOR_BORDER_GOLD).xy(0.0, 0.0)
        val tipTitle = tipBox.text("TACTICAL RECON INTEL", textSize = 12.0, font = bebasFont, color = COLOR_BORDER_GOLD).xy(12.0, 8.0)
        tipTitle.graphicsRenderer = GraphicsRenderer.GPU
        tipBox.text("Crouch-walk [SNEAK] to eliminate movement noise.\nStay out of guard vision cones and use shipping crates as cover.", textSize = 10.0, color = COLOR_TEXT_MUTED).xy(12.0, 28.0)

        // Watch a rewarded ad to continue the same run. Only requests the ad here - the actual
        // restart happens in the update loop below, gated on the bridge reporting the ad was
        // genuinely watched, so a failed/declined ad just leaves this overlay's other buttons
        // usable instead of stranding the player. See .junie/guidelines.md "AdMob (basic-ads)
        // feasibility spike" and src/ContinueAdBridge.kt.
        caughtPanel.createTacticalMenuBtn("CONTINUE (WATCH AD)", width = 240.0, height = 44.0, x = 90.0, y = 186.0, primary = true, accentColor = COLOR_ACCENT_GOLD) {
            getContinueAdBridge().requestContinueAd()
        }

        caughtPanel.createTacticalMenuBtn("RETRY INFILTRATION", width = 240.0, height = 40.0, x = 90.0, y = 234.0, accentColor = COLOR_ACCENT_RED) {
            sceneContainer.changeTo { GameplayScene(levelData) }
        }

        caughtPanel.createTacticalMenuBtn("RETURN TO MENU", width = 240.0, height = 38.0, x = 90.0, y = 278.0) {
            views.storage["nav_target"] = "menu"
            sceneContainer.changeTo { GameplayScene(levelData) }
        }

        caughtOverlay.visible = false

        // ==========================================
        // 3. LEVEL COMPLETE OVERLAY
        // ==========================================
        val winPanelW = 560.0
        val winPanelH = 390.0
        val winContainer = container().xy((canvasW - winPanelW) / 2.0, (canvasH - winPanelH) / 2.0)
        val winPanel = winContainer.createTacticalCard(0.0, 0.0, winPanelW, winPanelH, accentColor = COLOR_BORDER_GOLD)

        winPanel.solidRect(winPanelW, 28.0, Colors.BLACK.withAd(0.5)).xy(0.0, 0.0)
        winPanel.solidRect(winPanelW, 1.0, COLOR_BORDER_GOLD.withAd(0.5)).xy(0.0, 27.0)
        val winBadge = winPanel.text("MISSION ACCOMPLISHED // EXTRACTION SUCCESS", textSize = 11.0, font = bebasFont, color = COLOR_BORDER_GOLD).xy(14.0, 7.0)
        winBadge.graphicsRenderer = GraphicsRenderer.GPU

        val winTitle = winPanel.text("HEIST COMPLETED!", textSize = 28.0, font = bebasFont, color = COLOR_BORDER_GREEN)
        winTitle.graphicsRenderer = GraphicsRenderer.GPU
        winTitle.xy((winPanelW - winTitle.width) / 2.0, 36.0)

        val winStarsGraphics = winPanel.uiGraphics().xy(0.0, 0.0)

        val star1Label = winPanel.text("Star 1: Extraction Complete", textSize = 13.0, font = bebasFont, color = COLOR_TEXT_LIGHT).xy(45.0, 118.0)
        star1Label.graphicsRenderer = GraphicsRenderer.GPU
        val star2Label = winPanel.text("Star 2: Undetected (Ghost)", textSize = 13.0, font = bebasFont, color = COLOR_TEXT_LIGHT).xy(45.0, 140.0)
        star2Label.graphicsRenderer = GraphicsRenderer.GPU
        val star3Label = winPanel.text("Star 3: Fast Time (≤ ${levelData.timeTargetSeconds.toInt()}s)", textSize = 13.0, font = bebasFont, color = COLOR_TEXT_LIGHT).xy(45.0, 162.0)
        star3Label.graphicsRenderer = GraphicsRenderer.GPU

        // Bounty Box
        val bountyBox = winPanel.container().xy(40.0, 192.0)
        bountyBox.solidRect(480.0, 72.0, Colors.BLACK.withAd(0.6))
        bountyBox.solidRect(480.0, 1.0, COLOR_BORDER_CYAN.withAd(0.5)).xy(0.0, 0.0)

        val statsLabel = bountyBox.text("", textSize = 13.0, font = bebasFont, color = COLOR_BORDER_CYAN).xy(14.0, 8.0)
        statsLabel.graphicsRenderer = GraphicsRenderer.GPU
        val coinsEarnedLabel = bountyBox.text("", textSize = 15.0, font = bebasFont, color = COLOR_BORDER_GOLD).xy(14.0, 28.0)
        coinsEarnedLabel.graphicsRenderer = GraphicsRenderer.GPU
        val bestLabel = bountyBox.text("", textSize = 12.0, font = bebasFont, color = COLOR_BORDER_GREEN).xy(14.0, 48.0)
        bestLabel.graphicsRenderer = GraphicsRenderer.GPU

        // Buttons Container
        val winBtnRow = winPanel.container().xy(40.0, 285.0)

        val allLevels = LevelData.DEFAULT_LEVELS
        val currentLevelIndex = allLevels.indexOfFirst { it.id == levelData.id }
        val nextLevel = if (currentLevelIndex >= 0 && currentLevelIndex + 1 < allLevels.size) allLevels[currentLevelIndex + 1] else null

        if (nextLevel != null) {
            winBtnRow.createTacticalMenuBtn("NEXT MISSION", width = 150.0, height = 44.0, x = 0.0, y = 0.0, primary = true, accentColor = COLOR_ACCENT_GREEN) {
                sceneContainer.changeTo { GameplayScene(nextLevel) }
            }
        } else {
            winBtnRow.createTacticalMenuBtn("ALL CLEAR!", width = 150.0, height = 44.0, x = 0.0, y = 0.0, primary = true, accentColor = COLOR_ACCENT_GOLD) {
                views.storage["nav_target"] = "level_select"
                sceneContainer.changeTo { GameplayScene(levelData) }
            }
        }

        winBtnRow.createTacticalMenuBtn("RETRY", width = 140.0, height = 44.0, x = 165.0, y = 0.0) {
            sceneContainer.changeTo { GameplayScene(levelData) }
        }

        winBtnRow.createTacticalMenuBtn("MAIN MENU", width = 140.0, height = 44.0, x = 320.0, y = 0.0) {
            views.storage["nav_target"] = "menu"
            sceneContainer.changeTo { GameplayScene(levelData) }
        }

        winContainer.visible = false

        world.onLevelComplete = {
            val result = world.getLevelResult()
            levelStorage.saveResult(result)
            val bestResult = levelStorage.getBestResult(result.levelId) ?: result

            // Calculate and award coins
            val multiplier = if (profileStorage.getProfile().isPremium) 2 else 1
            val earnedCoins = levelData.getCoinReward(result.starCount) * multiplier
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

        var totalElapsedSeconds = 0.0

        // Main game update loop
        addUpdater { dt ->
            // Checked unconditionally (ahead of the isGameOver early-return below), since that's
            // exactly the state this fires in: the native shell has shown the rewarded ad while
            // this scene stayed alive in the background, and grants the continue once the player
            // actually watched it. Restarts the same way "RETRY INFILTRATION" already does.
            if (getContinueAdBridge().consumeContinueGranted()) {
                sceneContainer.stage?.launchImmediately { sceneContainer.changeTo { GameplayScene(levelData) } }
                return@addUpdater
            }

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
            val interactPressed = views.input.keys[Key.E] || views.input.keys[Key.F] || views.input.keys[Key.ENTER] || touchInteract

            // Powerup Key Shortcuts
            if (views.input.keys.justPressed(Key.N1)) tryActivatePowerup(PowerupType.SMOKE_SCREEN)
            if (views.input.keys.justPressed(Key.N2)) tryActivatePowerup(PowerupType.PHANTOM_CLOAK)
            if (views.input.keys.justPressed(Key.N3)) tryActivatePowerup(PowerupType.INVISIBILITY)
            if (views.input.keys.justPressed(Key.N4)) tryActivatePowerup(PowerupType.NOISE_SUPPRESSION)

            val moveInput = when {
                leftPressed && !rightPressed -> -1.0
                rightPressed && !leftPressed -> 1.0
                else -> 0.0
            }

            // Update domain simulation (Jump or Interact triggers climb/mantle when facing climbable obstacles)
            world.update(dtSec, moveInput, jumpPressed || interactPressed, crouchPressed)

            // Sync visual positions
            playerContainer.xy(world.player.x, world.player.y)
            for (i in world.allGuards.indices) {
                guardContainers[i].xy(world.allGuards[i].x, world.allGuards[i].y)
            }
            for (i in world.cameras.indices) {
                cameraContainers[i].xy(world.cameras[i].x, world.cameras[i].y)
            }

            // Camera: Center player on zoomed gameplay worldView, clamped to level bounds
            val currentCanvasW = sceneWidth.toDouble().coerceAtLeast(800.0)
            val currentCanvasH = sceneHeight.toDouble().coerceAtLeast(480.0)
            val halfScreen = currentCanvasW / 2.0
            val playerCenterX = world.player.x + world.player.width / 2.0
            val desiredWorldViewX = halfScreen - playerCenterX * worldZoom
            val minWorldViewX = currentCanvasW - world.worldWidth * worldZoom
            worldView.x = desiredWorldViewX.coerceIn(minWorldViewX.coerceAtMost(0.0), 0.0)
            val baseWorldViewY = currentCanvasH - (baseGroundY + 70.0) * worldZoom
            worldView.y = baseWorldViewY

            // Background parallax (0.2x rate, looping) - Unzoomed at native canvas height
            if (bgmgImages.isNotEmpty()) {
                val virtualCameraX = -worldView.x / worldZoom
                val bgmgOffset = -virtualCameraX * 0.2
                var bgmgShift = bgmgOffset % bgmgTileW
                if (bgmgShift > 0) bgmgShift -= bgmgTileW
                for (i in bgmgImages.indices) {
                    bgmgImages[i].xy(bgmgShift + i * bgmgTileW, 0.0)
                }
            }

            // Climb animation machine: top priority. Player.isClimbing drives x/y itself (see
            // Player.startClimb/advanceClimb) so the jump machine below - which would otherwise
            // fire because isGrounded is false while climbing - is skipped entirely instead.
            if (world.player.isClimbing) {
                if (playerAnimState != "climb") {
                    playerAnimState = "climb"
                    sounds.climb.playSfx(sfxContext, GameAudio.CLIMB_GAIN, sfxVolume())
                    playerSprite.playAnimationLooped(playerAnimations.climb, manualFrameTime)
                }
                val frame = climbFirstFrame + (world.player.climbPhase * climbFrameSpan).toInt()
                playerSprite.setFrame(frame.coerceIn(climbFirstFrame, climbLastFrame))
            } else {
                if (playerAnimState == "climb") playerAnimState = "none"

                // Jump animation machine
                if (playerAnimState != "jump" && !world.player.isGrounded) {
                    playerAnimState = "jump"
                    landingAbsorb = false  // cancel any in-progress absorption
                    jumpPhase = "launch"
                    jumpPhaseElapsed = 0.0
                    jumpStartY = world.player.y
                    sounds.impact.playSfx(sfxContext, GameAudio.TAKEOFF_GAIN, sfxVolume())
                    playerSprite.playAnimationLooped(playerAnimations.jump, manualFrameTime)
                } else if (playerAnimState == "jump") {
                    jumpPhaseElapsed += dtSec
                    when (jumpPhase) {
                        "launch" -> if (jumpPhaseElapsed >= jumpLaunchDuration) {
                            jumpPhase = "air"
                            jumpPhaseElapsed = 0.0
                        }
                        // The "land" phase plays the recovery clip through to a standing pose -
                        // right if the player is stopped, but wrong if they're still holding a
                        // direction: world x keeps advancing on physics regardless of animation
                        // phase, so riding out the standing-recovery frames while already moving
                        // reads as gliding forward in a standing pose for those 0.26s before the
                        // walk cut-over. Moving into the touchdown skips straight past it instead.
                        "air" -> if (world.player.isGrounded) {
                            sounds.impact.playSfx(sfxContext, GameAudio.LANDING_GAIN, sfxVolume())
                            if (world.player.isMoving) {
                                // Don't snap straight to walk - play a brief landing cushion
                                // first so the posture change isn't instant (especially visible
                                // when landing on a higher platform where the descent pose is
                                // still deep). The walk lean-in starts after the absorb ends.
                                jumpPhase = "none"
                                playerAnimState = "none"
                                landingAbsorb = true
                                landingAbsorbElapsed = 0.0
                            } else {
                                jumpPhase = "land"
                                jumpPhaseElapsed = 0.0
                            }
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

                // Crouch animation machine: gated on grounded so an airborne crouch-input (edge
                // case in the physics) still shows the jump animation rather than fighting it.
                if (playerAnimState != "jump" && world.player.isGrounded) {
                    if (world.player.isCrouching) {
                        if (playerAnimState != "crouch" && playerAnimState != "crouchwalk") {
                            playerAnimState = "crouch"
                            crouchPhase = "entering"
                            sounds.crouch.playSfx(sfxContext, GameAudio.CROUCH_GAIN, sfxVolume())
                            playerSprite.playAnimationLooped(playerAnimations.crouch, manualFrameTime)
                        } else if (playerAnimState == "crouch" && crouchPhase == "exiting") {
                            crouchPhase = "entering"
                        }
                        
                        if (playerAnimState == "crouch" && crouchPhase == "holding" && world.player.isMoving) {
                            playerAnimState = "crouchwalk"
                            crouchwalkInTransition = true
                            crouchwalkTransitionProgress = 0.0
                            crouchwalkCycleProgress = 0.0
                            playerSprite.playAnimationLooped(playerAnimations.crouchwalk, manualFrameTime)
                        } else if (playerAnimState == "crouchwalk" && !world.player.isMoving) {
                            playerAnimState = "crouch"
                            crouchPhase = "holding"
                            playerSprite.playAnimationLooped(playerAnimations.crouch, manualFrameTime)
                        }
                    } else if ((playerAnimState == "crouch" || playerAnimState == "crouchwalk") && crouchPhase != "exiting") {
                        playerAnimState = "crouch"
                        crouchPhase = "exiting"
                        // Same foley on the way up, lighter. There is no separate stand-up take
                        // in the source, but it is the same cloth and joints in reverse, and
                        // leaving the exit silent when the entry is not reads as a missed cue.
                        sounds.crouch.playSfx(sfxContext, GameAudio.CROUCH_GAIN * 0.6, sfxVolume())
                        playerSprite.playAnimationLooped(playerAnimations.crouch, manualFrameTime)
                    }

                    if (playerAnimState == "crouch") {
                        when (crouchPhase) {
                            "entering" -> {
                                crouchFrameProgress = (crouchFrameProgress + dtSec / crouchDownDuration * crouchLastFrame)
                                    .coerceAtMost(crouchLastFrame.toDouble())
                                if (crouchFrameProgress >= crouchLastFrame.toDouble()) crouchPhase = "holding"
                            }
                            "exiting" -> {
                                crouchFrameProgress = (crouchFrameProgress - dtSec / crouchUpDuration * crouchLastFrame)
                                    .coerceAtLeast(0.0)
                                if (crouchFrameProgress <= 0.0) playerAnimState = "none"
                            }
                        }
                        playerSprite.setFrame(crouchFrameProgress.roundToInt().coerceIn(0, crouchLastFrame))
                    } else if (playerAnimState == "crouchwalk") {
                        val cyclesMoved = abs(world.player.vx) * dtSec / crouchwalkCycleDistance
                        if (crouchwalkInTransition) {
                            crouchwalkTransitionProgress += cyclesMoved
                            val t = (crouchwalkTransitionProgress / crouchwalkTransitionCycles).coerceIn(0.0, 1.0)
                            val span = PlayerAnimations.CROUCHWALK_TRANSITION_END - PlayerAnimations.CROUCHWALK_TRANSITION_START
                            playerSprite.setFrame(
                                PlayerAnimations.CROUCHWALK_TRANSITION_START +
                                    (t * span).toInt().coerceIn(0, span)
                            )
                            // The lean-in's last frame is the loop's first frame minus one in the
                            // source, so handing over at the end is a plain adjacent-frame step.
                            if (t >= 1.0) crouchwalkInTransition = false
                        } else {
                            crouchwalkCycleProgress = (crouchwalkCycleProgress + cyclesMoved) % 1.0
                            val loopLength = PlayerAnimations.CROUCHWALK_LOOP_LENGTH
                            playerSprite.setFrame(
                                PlayerAnimations.CROUCHWALK_LOOP_START +
                                    (crouchwalkCycleProgress * loopLength).toInt().coerceIn(0, loopLength - 1)
                            )
                        }
                    }
                }
            }

            // Landing absorption: plays a brief cushion from the jump's own landing frames
            // before the walk lean-in starts. While active, it owns the sprite — the
            // grounded-state block below is skipped so it doesn't fight for control.
            if (landingAbsorb) {
                landingAbsorbElapsed += dtSec
                val t = (landingAbsorbElapsed / landingAbsorbDuration).coerceIn(0.0, 1.0)
                // Stay on the jump sprite sheet and scrub through the first few landing frames.
                if (playerAnimState != "landAbsorb") {
                    playerAnimState = "landAbsorb"
                    playerSprite.playAnimationLooped(playerAnimations.jump, manualFrameTime)
                }
                val absorbFrame = jumpLandFrame + (t * landingAbsorbFrames).toInt()
                    .coerceAtMost(landingAbsorbFrames)
                playerSprite.setFrame(absorbFrame.coerceIn(jumpLandFrame, jumpLastFrame))

                if (t >= 1.0) {
                    // Absorption done — hand off to the walk lean-in (or idle if player stopped).
                    landingAbsorb = false
                    playerAnimState = "none"  // let the block below pick it up this same tick
                }
            }

            if (!landingAbsorb && playerAnimState != "jump" && playerAnimState != "crouch"
                && playerAnimState != "crouchwalk" && playerAnimState != "climb" && playerAnimState != "landAbsorb") {
                val groundedState = if (world.player.isMoving) "walk" else "idle"
                if (groundedState != playerAnimState) {
                    // We always play the lean-in transition when entering the walk state,
                    // whether from a standstill or landing a jump. When landing, it acts as
                    // a smooth "absorbing the impact and pushing forward" animation rather 
                    // than suddenly snapping into a mid-stride loop.
                    playerAnimState = groundedState
                    if (groundedState == "walk") {
                        walkCycleProgress = 0.0
                        playerSprite.playAnimationLooped(playerAnimations.walk, manualFrameTime)
                        // Play the lean-in transition even when landing from a jump. It acts as
                        // a nice "absorbing the landing into a run" animation sequence.
                        walkInTransition = true
                        walkTransitionElapsed = 0.0
                        playerSprite.setFrame(PlayerAnimations.WALK_TRANSITION_START)
                        // The lean-in is a real 0.28s stride but it is time-driven, so the
                        // distance-driven step triggers below cannot see it. One step here keeps
                        // the first pace of every walk from being silent.
                        val step = if (stepAlternate) sounds.stepB else sounds.stepA
                        stepAlternate = !stepAlternate
                        step.playSfx(sfxContext, GameAudio.STEP_GAIN, sfxVolume())
                    } else {
                        playerSprite.playAnimationLooped(playerAnimations.idle, 100.milliseconds)
                    }
                }
            }
            // Only meaningful on the exact tick a jump lands - if that tick went into crouch
            // instead (isCrouching held through touchdown), discard it rather than letting it
            // skip the lean-in whenever walk is next entered, possibly much later.


            playerSprite.y = world.player.height + (if (playerAnimState == "idle") idleFeetOffset else 0.0)
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
                    val previousPhase = walkCycleProgress
                    walkCycleProgress =
                        (walkCycleProgress + abs(world.player.vx) * dtSec / walkCycleDistance) % 1.0
                    val loopLength = PlayerAnimations.WALK_LOOP_LENGTH
                    playerSprite.setFrame(
                        PlayerAnimations.WALK_LOOP_START +
                            (walkCycleProgress * loopLength).toInt().coerceIn(0, loopLength - 1)
                    )

                    // A footstep for each contact phase the cycle passed this tick. Written as a
                    // crossing test rather than "is the phase near X" so it still fires exactly
                    // once at low frame rates or high speed, and survives the wrap at 1.0.
                    for (phase in GameAudio.STEP_PHASES) {
                        val crossed = if (walkCycleProgress >= previousPhase) {
                            phase > previousPhase && phase <= walkCycleProgress
                        } else {
                            phase > previousPhase || phase <= walkCycleProgress
                        }
                        if (crossed) {
                            // Alternate the two samples so a long run does not turn into one
                            // clip on repeat, which is what gives a single footstep away.
                            val step = if (stepAlternate) sounds.stepB else sounds.stepA
                            stepAlternate = !stepAlternate
                            step.playSfx(sfxContext, GameAudio.STEP_GAIN, sfxVolume())
                        }
                    }
                }
            }

            // Flip sprite to face direction
            if (moveInput < 0) {
                playerFacingLeft = true
            } else if (moveInput > 0) {
                playerFacingLeft = false
            }
            playerSprite.scaleX = playerBaseScale * (if (playerFacingLeft) -1.0 else 1.0)
            playerSprite.scaleY = playerBaseScale

            // Invisibility visual effect on player
            playerSprite.alpha = if (world.activePowerups.isInvisibilityActive) 0.35 else 1.0

            // Update guard visors and badges
            for (i in world.allGuards.indices) {
                val g = world.allGuards[i]
                if (world.activePowerups.isPhantomCloakActive) {
                    guardBadges[i].text = "Zzz"
                    guardBadges[i].color = COLOR_BORDER_CYAN
                    guardBadges[i].visible = true
                    guardVisors[i].x = if (g.facing >= 0) g.width - 6.0 else 0.0
                    guardVisors[i].color = Colors["#34495e"]
                } else {
                    // The investigating "?" is now carried by the guard's own detection pip.
                    guardBadges[i].visible = false
                    guardVisors[i].x = if (g.facing >= 0) g.width - 6.0 else 0.0
                    guardVisors[i].color =
                        if (g.state == GuardState.INVESTIGATING) COLOR_BORDER_GOLD else Colors["#e74c3c"]
                }
            }

            val alertProgress = world.alertProgress

            // Render guard vision cones
            for (i in world.allGuards.indices) {
                val g = world.allGuards[i]
                if (world.activePowerups.isPhantomCloakActive) {
                    guardCones[i].updateShape { }
                } else {
                    val coneColor = when {
                        world.isGameOver -> {
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
            }

            // Render camera vision cones and status
            for (i in world.cameras.indices) {
                val c = world.cameras[i]
                cameraMounts[i].color = if (world.activePowerups.isSmokeScreenActive) Colors["#555555"] else Colors["#e74c3c"]
                if (world.activePowerups.isSmokeScreenActive) {
                    cameraCones[i].updateShape { }
                } else {
                    val coneColor = when {
                        world.isGameOver -> {
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
                        else -> {
                            Colors["#e67e22"].withAd(0.32)
                        }
                    }
                    val visionPolygon = VisionSystem.computeVisionPolygon(
                        origin = c.eyePosition,
                        facingAngle = c.facingAngle,
                        range = c.visionRange,
                        fov = c.visionFov,
                        occluders = world.occluders
                    )
                    cameraCones[i].updateShape {
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
            }

            // Update Powerup HUD buttons and active countdown indicators. The chip is the only
            // place a live powerup is reported now - the old duplicate "ACTIVE: ..." status line
            // under the top bar said the same thing a second time, in a second place.
            val currentProfile = profileStorage.getProfile()
            var hasAnyVisiblePowerup = false

            for (btn in powerupHudButtons) {
                val count = currentProfile.getPowerupCount(btn.type)
                val isActive = world.activePowerups.isActive(btn.type)
                val remTime = world.activePowerups.getRemainingTime(btn.type)

                if (count > 0 || isActive) {
                    hasAnyVisiblePowerup = true
                    btn.btnContainer.visible = true
                    val accent = if (isActive) COLOR_BORDER_GREEN else COLOR_ACCENT_CYAN
                    btn.bg.updateShape {
                        clear()
                        val fillCol = if (isActive) accent.withAd(0.22) else Colors["#0A0C10"].withAd(0.55)
                        fill(fillCol) { roundRect(0.0, 0.0, powerupBtnW, powerupBtnH, powerupBtnRadius, powerupBtnRadius) }
                        stroke(accent.withAd(if (isActive) 0.95 else 0.45), StrokeInfo(thickness = if (isActive) 2.0 else 1.6)) {
                            roundRect(0.5, 0.5, powerupBtnW - 1.0, powerupBtnH - 1.0, powerupBtnRadius, powerupBtnRadius)
                        }
                        // Live powerups get a filled underline that drains with their timer, so
                        // the chip carries the countdown instead of a separate status readout.
                        if (isActive) {
                            val span = if (btn.type.isLevelDuration) 1.0
                                else (remTime / btn.type.duration).coerceIn(0.0, 1.0)
                            fill(accent) {
                                roundRect(10.0, powerupBtnH - 7.0, (powerupBtnW - 20.0) * span, 3.0, 1.5, 1.5)
                            }
                        }
                    }
                    if (isActive) {
                        btn.countText.text = if (btn.type.isLevelDuration) "ON" else "${((remTime * 10).toInt() / 10.0)}s"
                        btn.countText.color = COLOR_BORDER_GREEN
                        btn.nameText.color = COLOR_TEXT_LIGHT
                    } else {
                        btn.countText.text = "x$count"
                        btn.countText.color = COLOR_BORDER_GOLD
                        btn.nameText.color = COLOR_TEXT_MUTED
                    }
                } else {
                    btn.btnContainer.visible = false
                }
                btn.countText.xy((powerupBtnW - btn.countText.width) / 2.0, 22.0)
            }
            powerupDockContainer.visible = hasAnyVisiblePowerup

            // Mission toast holds, dissolves, and hands its corner to the objective strip, which
            // then stays for the rest of the run. Sequential rather than cross-faded: both carry
            // a scrim, and overlapping them stacks two translucent plates into one muddy one.
            if (introToast.visible) {
                introElapsed += dtSec
                introToast.alpha = if (introElapsed <= introHoldSeconds) 1.0
                    else (1.0 - (introElapsed - introHoldSeconds) / introFadeSeconds).coerceAtLeast(0.0)
                if (introToast.alpha <= 0.0) {
                    introToast.visible = false
                    objectiveHud.visible = true
                }
            } else if (objectiveHud.alpha < objectiveHudAlpha) {
                objectiveHud.alpha = (objectiveHud.alpha + dtSec / objectiveFadeInSeconds * objectiveHudAlpha)
                    .coerceAtMost(objectiveHudAlpha)
            }

            // Detection pips. Nothing is drawn on an entity that cannot see the player, so a
            // clean run has none on screen at all - the absence is the "stealth 100%" readout.
            val pipPulse = 0.5 + 0.5 * sin(totalElapsedSeconds * 16.0)

            fun pipFor(seeing: Boolean, investigating: Boolean): Double = when {
                seeing && world.isGameOver -> 1.0
                seeing -> world.alertProgress.coerceAtLeast(0.05)
                // Sweeping a noise it has not pinned down yet: worth a hint, not a filling meter.
                investigating -> 0.18
                else -> 0.0
            }

            fun paintPip(pip: Graphics, progress: Double) {
                if (progress <= 0.0) {
                    pip.visible = false
                } else {
                    pip.visible = true
                    pip.drawDetectPip(progress, detectPipTint(progress), pipPulse)
                }
            }

            for (i in world.allGuards.indices) {
                val g = world.allGuards[i]
                paintPip(
                    guardPips[i],
                    if (world.activePowerups.isPhantomCloakActive) 0.0
                    else pipFor(g in world.detectingGuards, g.state == GuardState.INVESTIGATING)
                )
            }
            for (i in world.cameras.indices) {
                paintPip(
                    cameraPips[i],
                    if (world.activePowerups.isSmokeScreenActive) 0.0
                    else pipFor(world.cameras[i] in world.detectingCameras, false)
                )
            }
        }
    }

    private fun renderRoughBlock(
        container: Container,
        width: Double,
        height: Double,
        seed: Long = 0L
    ) {
        val g = container.uiGraphics()
        g.updateShape {
            clear()
            fill(Colors.BLACK) {
                moveTo(0.0, height)
                lineTo(0.0, 0.0)

                // Continuous noisy top edge matching reference: a rough concrete/rooftop
                // silhouette where every segment has tiny random vertical jitter (0-2px).
                // No discrete bumps or flat gaps — just a natural, continuously irregular line.
                val rand = kotlin.random.Random(seed xor 0x8A9B2C1DL)
                var currX = 0.0
                var currY = 0.0

                while (currX < width) {
                    val segLen = rand.nextDouble(5.0, 12.0)
                    currX += segLen
                    if (currX > width) currX = width
                    // Gentle jitter: blend toward a new random target so transitions
                    // stay smooth instead of jagged. Max depth ~0.8px.
                    val targetY = -rand.nextDouble(0.0, 0.8)
                    currY = currY * 0.4 + targetY * 0.6
                    lineTo(currX, currY)
                }

                lineTo(width, 0.0)
                lineTo(width, height)
                close()
            }
        }
    }

    /** Circular refresh arrow, weighted to match drawPlayIcon's solid triangle beside it. */
    private fun ShapeBuilder.drawRestartIcon(color: RGBA) {
        stroke(color, StrokeInfo(thickness = 3.4)) {
            // Open ring, gap at the top-right where the arrowhead goes.
            val steps = 28
            for (i in 0..steps) {
                val a = (-PI / 3.0) + (2.0 * PI * 0.82) * (i.toDouble() / steps)
                val px = cos(a) * 7.8
                val py = sin(a) * 7.8
                if (i == 0) moveTo(Point(px, py)) else lineTo(Point(px, py))
            }
        }
        fill(color) {
            moveTo(Point(1.4, -11.4))
            lineTo(Point(11.4, -7.6))
            lineTo(Point(3.8, -0.6))
            close()
        }
    }

    private fun ShapeBuilder.drawLeftChevron(color: RGBA = Colors.WHITE) {
        fill(color) {
            moveTo(3.0, -8.0)
            lineTo(-4.0, 0.0)
            lineTo(3.0, 8.0)
            lineTo(5.0, 6.0)
            lineTo(0.0, 0.0)
            lineTo(5.0, -6.0)
            close()
        }
    }

    private fun ShapeBuilder.drawRightChevron(color: RGBA = Colors.WHITE) {
        fill(color) {
            moveTo(-3.0, -8.0)
            lineTo(4.0, 0.0)
            lineTo(-3.0, 8.0)
            lineTo(-5.0, 6.0)
            lineTo(0.0, 0.0)
            lineTo(-5.0, -6.0)
            close()
        }
    }

    private fun ShapeBuilder.drawJumpArrow(color: RGBA = Colors.WHITE) {
        fill(color) {
            moveTo(0.0, -9.0)
            lineTo(8.5, 0.0)
            lineTo(5.0, 2.5)
            lineTo(1.8, -0.5)
            lineTo(1.8, 8.0)
            lineTo(-1.8, 8.0)
            lineTo(-1.8, -0.5)
            lineTo(-5.0, 2.5)
            lineTo(-8.5, 0.0)
            close()
        }
    }

    private fun ShapeBuilder.drawSneakArrow(color: RGBA = Colors.WHITE) {
        fill(color) {
            moveTo(0.0, 9.0)
            lineTo(8.5, 0.0)
            lineTo(5.0, -2.5)
            lineTo(1.8, 0.5)
            lineTo(1.8, -8.0)
            lineTo(-1.8, -8.0)
            lineTo(-1.8, 0.5)
            lineTo(-5.0, -2.5)
            lineTo(-8.5, 0.0)
            close()
        }
    }

    private fun ShapeBuilder.drawInteractIcon(color: RGBA = Colors.WHITE) {
        fill(color) {
            roundRect(-6.0, -1.0, 12.0, 9.0, 2.0, 2.0)
            roundRect(-5.0, -9.0, 2.4, 9.0, 1.2, 1.2)
            roundRect(-2.0, -10.5, 2.4, 10.5, 1.2, 1.2)
            roundRect(1.0, -9.5, 2.4, 9.5, 1.2, 1.2)
            roundRect(4.0, -7.0, 2.2, 7.0, 1.1, 1.1)
            moveTo(-5.5, 2.0)
            lineTo(-9.5, -2.0)
            lineTo(-8.0, -3.5)
            lineTo(-4.0, 0.5)
            close()
        }
    }
}
