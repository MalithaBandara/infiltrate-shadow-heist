package game.scene

import com.sample.demo.ads.getContinueAdBridge
import com.sample.demo.analytics.getAnalyticsBridge
import com.sample.demo.nav.getLevelExitBridge
import game.model.*
import game.scene.UiComponents.COLOR_PRIMARY
import game.scene.UiComponents.COLOR_ACCENT_CYAN
import game.scene.UiComponents.COLOR_ACCENT_GOLD
import game.scene.UiComponents.COLOR_ACCENT_GREEN
import game.scene.UiComponents.COLOR_BORDER_CYAN
import game.scene.UiComponents.COLOR_BORDER_GOLD
import game.scene.UiComponents.COLOR_BORDER_GREEN
import game.scene.UiComponents.COLOR_BORDER_RED
import game.scene.UiComponents.COLOR_TEXT_LIGHT
import game.scene.UiComponents.COLOR_TEXT_MUTED
import game.scene.UiComponents.createButton
import game.scene.UiComponents.drawPlayIcon
import game.scene.UiComponents.drawQuitIcon
import game.scene.UiComponents.drawStar
import game.scene.UiComponents.uiGraphics
import korlibs.audio.sound.*
import korlibs.event.*
import korlibs.image.bitmap.*
import korlibs.image.color.*
import korlibs.image.font.*
import korlibs.image.format.*
import korlibs.image.vector.*
import korlibs.io.async.*
import korlibs.io.file.std.*
import korlibs.korge.input.*
import korlibs.korge.time.*
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

    private var bgMusicChannel: SoundChannel? = null

    override suspend fun sceneDestroy() {
        super.sceneDestroy()
        try {
            bgMusicChannel?.stop()
            bgMusicChannel = null
        } catch (_: Throwable) {}
    }

    override suspend fun SContainer.sceneMain() {
        val canvasW = sceneWidth.toDouble().coerceAtLeast(800.0)
        val canvasH = sceneHeight.toDouble().coerceAtLeast(480.0)

        // --- Loading screen -------------------------------------------------------------
        // Shown immediately, before any load below runs, and dismissed only once every load
        // in this function has actually finished - so a slow cold load (mobile, first launch)
        // shows real progress instead of a blank/grey frame.
        val loadingBgBitmap = try { resourcesVfs["loadingbg.png"].readBitmap() } catch (_: Throwable) { null }
        val loadingLogoBitmap = try { resourcesVfs["logo_main.png"].readBitmap() } catch (_: Throwable) { null }
        // Same torn-paper texture as the main menu's PLAY button (Res.drawable.button1 there) -
        // stretched to fit, matching the existing precedent for these button textures elsewhere
        // in this file (UiComponents.createButton's heistStyle path): plain stretch, not 9-sliced,
        // since 9-slicing this exact art was already tried and reverted for visible seams.
        val loadingBarTextureBitmap = try { resourcesVfs["button1.png"].readBitmap() } catch (_: Throwable) { null }
        val loadingFont = try { resourcesVfs["BebasNeue-Regular.ttf"].readTtfFont() } catch (_: Throwable) { DefaultTtfFont }

        val loadingRoot = container()
        if (loadingBgBitmap != null) {
            loadingRoot.image(loadingBgBitmap) { size(canvasW, canvasH) }
        } else {
            loadingRoot.solidRect(canvasW, canvasH, Colors.BLACK)
        }

        val loadingLogoWidth = canvasW * 0.34
        if (loadingLogoBitmap != null) {
            val logoScale = loadingLogoWidth / loadingLogoBitmap.width
            loadingRoot.image(loadingLogoBitmap) { scale(logoScale) }
                .xy((canvasW - loadingLogoBitmap.width * logoScale) / 2.0, canvasH * 0.26)
        }

        val loadingBarWidth = canvasW * 0.30
        val loadingBarHeight = canvasH * 0.045
        val loadingBarX = (canvasW - loadingBarWidth) / 2.0
        val loadingBarY = canvasH * 0.58

        // Fill container holds just the (re-created-per-step) texture image; the frame is drawn
        // separately, after/on top of it, so the border stays crisp instead of being covered by
        // the fill each time it's rebuilt.
        val loadingBarFillContainer = loadingRoot.container().xy(loadingBarX, loadingBarY)
        var loadingBarFillView: View? = null
        fun setLoadingProgress(fraction: Double) {
            val fillWidth = loadingBarWidth * fraction.coerceIn(0.0, 1.0)
            loadingBarFillView?.removeFromParent()
            loadingBarFillView = if (fillWidth <= 0.0) {
                null
            } else if (loadingBarTextureBitmap != null) {
                loadingBarFillContainer.image(loadingBarTextureBitmap) { size(fillWidth, loadingBarHeight) }
            } else {
                loadingBarFillContainer.solidRect(fillWidth, loadingBarHeight, Colors.WHITE)
            }
        }
        setLoadingProgress(0.0)
        loadingRoot.uiGraphics().xy(loadingBarX, loadingBarY).updateShape {
            stroke(Colors.WHITE.withAd(0.85), StrokeInfo(thickness = 1.5)) {
                rect(0.0, 0.0, loadingBarWidth, loadingBarHeight)
            }
        }

        val loadingLabel = loadingRoot.text(
            "L O A D I N G . . .",
            textSize = loadingBarHeight * 0.62,
            font = loadingFont,
            color = Colors.WHITE
        )
        loadingLabel.graphicsRenderer = GraphicsRenderer.GPU
        loadingLabel.xy((canvasW - loadingLabel.width) / 2.0, loadingBarY + loadingBarHeight + canvasH * 0.035)

        // Standard loading-text blink: visible for most of the cycle, then gone for a brief
        // instant, then straight back - not a gradual pulse or an irregular flicker. Period
        // lengthened (and the visible share raised) so it vanishes much less often than before.
        val blinkPeriodSeconds = 2.2
        val blinkVisibleFraction = 0.88
        var loadingFlickerT = 0.0
        val loadingFlickerHandle = loadingLabel.addUpdater { dt ->
            loadingFlickerT += dt.seconds
            val phase = (loadingFlickerT % blinkPeriodSeconds) / blinkPeriodSeconds
            alpha = if (phase < blinkVisibleFraction) 1.0 else 0.0
        }

        fun dismissLoadingScreen() {
            loadingFlickerHandle.close()
            loadingRoot.removeFromParent()
        }

        // One full frame so the loading screen is actually painted before the (synchronous,
        // potentially slow) loads below ever get a chance to block the render loop.
        delayFrame()

        val totalLoadSteps = 19
        var loadStepsDone = 0
        suspend fun markLoadProgress() {
            loadStepsDone++
            setLoadingProgress(loadStepsDone.toDouble() / totalLoadSteps)
            delayFrame()
        }

        // Every reported "grey screen, nothing loads" bug this session has turned out to be an
        // uncaught exception somewhere in this setup, on a repeat load (RESTART/QUIT/continue-ad)
        // rather than the first one - each fix so far targeted a specific guessed cause (audio
        // priming frequency, ad-callback timing) and none of them were confirmed to be the actual
        // one, because a silent failure here leaves nothing to diagnose from except "it's grey."
        // This catches whatever actually throws and puts the real exception on screen instead of
        // guessing again - PlayerAnimations.load() in particular has no try/catch anywhere in it
        // (unlike every other asset load in this function, which already defaults to null on
        // failure) and allocates a brand new GPU texture atlas on every single call, which is a
        // real, concrete candidate for something that degrades across repeated reloads in a way
        // audio never would - but this is deliberately not a fix aimed at that one theory, it's
        // instrumentation so the next report says what actually failed.
        val (world, playerAnimations, sounds) = try {
            val loadedWorld = GameWorld.createDefault(levelData)
            markLoadProgress()
            val loadedAnimations = PlayerAnimations.load()
            markLoadProgress()
            val loadedSounds = GameAudio.load()
            markLoadProgress()
            Triple(loadedWorld, loadedAnimations, loadedSounds)
        } catch (e: Throwable) {
            dismissLoadingScreen()
            solidRect(sceneWidth, sceneHeight, Colors["#16161d"])
            text(
                "LEVEL LOAD FAILED\n\n${e::class.simpleName}: ${e.message}\n\n${e.stackTraceToString().take(1200)}",
                textSize = 14.0,
                color = Colors.RED
            ).xy(16.0, 16.0)
            return
        }
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

        val worldZoom = 1.35
        val baseGroundY = 410.0

        val bgFileName = levelData.resolvedBackgroundImage
        val bgmgBitmap = try { resourcesVfs[bgFileName].readBitmap() } catch (_: Throwable) { null }
        markLoadProgress()
        val crateBitmap = try { resourcesVfs["crate.png"].readBitmap() } catch (_: Throwable) { null }
        markLoadProgress()
        val chainedCrateBitmap = try { resourcesVfs["chainedcrate.png"].readBitmap() } catch (_: Throwable) { null }
        markLoadProgress()
        val chainedCrate2Bitmap = try { resourcesVfs["chainedcrate2.png"].readBitmap() } catch (_: Throwable) { null }
        markLoadProgress()
        val fenceBitmap = try { resourcesVfs["fence.png"].readBitmap() } catch (_: Throwable) { null }
        markLoadProgress()
        val fence2Bitmap = try { resourcesVfs["fence2.png"].readBitmap() } catch (_: Throwable) { null }
        markLoadProgress()
        val barrelBitmap = try { resourcesVfs["barrel.png"].readBitmap() } catch (_: Throwable) { null }
        markLoadProgress()
        val truckBitmap = try { resourcesVfs["truck.png"].readBitmap() } catch (_: Throwable) { null }
        markLoadProgress()
        val entranceBitmap = try { resourcesVfs["entrance.png"].readBitmap() } catch (_: Throwable) { null }
        markLoadProgress()
        val exitFenceBitmap = try { resourcesVfs["exitfence.png"].readBitmap() } catch (_: Throwable) { null }
        markLoadProgress()
        val leftBtnBitmap = try { resourcesVfs["left.png"].readBitmap() } catch (_: Throwable) { null }
        markLoadProgress()
        val rightBtnBitmap = try { resourcesVfs["right.png"].readBitmap() } catch (_: Throwable) { null }
        markLoadProgress()
        val crouchBtnBitmap = try { resourcesVfs["crouch.png"].readBitmap() } catch (_: Throwable) { null }
        markLoadProgress()
        val jumpBtnBitmap = try { resourcesVfs["jump.png"].readBitmap() } catch (_: Throwable) { null }
        markLoadProgress()
        val interactBtnBitmap = try { resourcesVfs["interact.png"].readBitmap() } catch (_: Throwable) { null }
        markLoadProgress()
        // The main menu's torn-paper button strips. They already live in resources/ (the Compose
        // menu reads its own copies out of composeResources), so the pause menu can be built from
        // the very same art rather than a lookalike drawn in vectors.
        val paperBtnBitmaps = listOf("button1.png", "button2.png", "button3.png", "button4.png")
            .map { name -> try { resourcesVfs[name].readBitmap() } catch (_: Throwable) { null } }
        markLoadProgress()

        dismissLoadingScreen()

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

        // Exit Point / Extraction Zone - a checkpoint booth image (entrance.png, tightly cropped
        // to just the booth silhouette) plus the fence segment that stood beside it in the
        // original, wider source composite (exitfence.png - cropped straight from the same
        // original entrance.png source asset in Downloads/charAnimations/assets, at the seam
        // where its own fence posts end and the booth's begin, so it's the exact fence the booth
        // was originally drawn next to, baked-in crates and all - not the level's starting fence
        // texture). Both are purely decorative (no collision box of their own) - world.exitZone is
        // still the real trigger the player has to touch to complete the level, sized to span
        // almost the booth's own width instead of a narrow strip somewhere inside it, and the
        // booth is left-aligned flush with it, so there's no separate alignment guess to get
        // wrong: touching any part of the visible structure ends the level. Both are sized by
        // height with their own aspect ratio so neither is stretched/squashed; the booth's 135
        // keeps it a small guard booth rather than the towering wall 200 rendered as once it was
        // just the booth silhouette and not the whole cropped scene, and the fence's 140 matches
        // the level's own starting fence height for visual consistency. The fence sits after the
        // booth (the player reaches the booth first, then the fence behind it) rather than before
        // it. entrance.png is pre-mirrored on disk (not flipped with scaleX = -1 at render time -
        // a negative scaleX on an Image corrupts the draw into a torn, mostly-transparent mess on
        // this KorGE/OpenGL backend, discovered via the identical bug on the truck below) so the
        // roof overhang leans out towards the player's approach.
        if (entranceBitmap != null) {
            val entranceHeight = 135.0
            val entranceWidth = entranceHeight * (entranceBitmap.width.toDouble() / entranceBitmap.height.toDouble())
            val entranceY = baseGroundY - entranceHeight
            worldView.image(entranceBitmap) {
                size(entranceWidth, entranceHeight)
            }.xy(world.exitZone.x, entranceY)
            if (exitFenceBitmap != null) {
                val exitFenceHeight = 140.0
                val exitFenceWidth = exitFenceHeight * (exitFenceBitmap.width.toDouble() / exitFenceBitmap.height.toDouble())
                worldView.image(exitFenceBitmap) {
                    size(exitFenceWidth, exitFenceHeight)
                }.xy(world.exitZone.x + entranceWidth, baseGroundY - exitFenceHeight)
            }
        }

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
            // 3. Barrels (three together, just past the start gates - jump on, walk across, jump off)
            else if (box in world.barrels && barrelBitmap != null) {
                boxContainer.image(barrelBitmap) {
                    size(box.width, box.height)
                }.xy(0.0, 0.0)
            }
            // 4. Truck (parked next to the small crate, climbed onto en route to the long platform).
            // Collision is 3 separate tiers (front/middle/back, see GameWorld.kt) but the image is
            // one continuous truck, so it's drawn once - against the first tier - spanning the
            // whole footprint (world.truck, the union of all 3 parts); the other two tiers get no
            // separate visual of their own so the image isn't stretched into 3 squashed copies.
            // truck.png is pre-mirrored on disk so the hood (the low front tier the player climbs
            // onto first) faces the crate, with the cab and bed stretching away towards the long
            // platform - NOT flipped with scaleX = -1 at render time. That was the original
            // approach (matching how entrance.png's flip used to work) and it corrupted the whole
            // image into a torn, mostly see-through mess: fine detail like the wheel/axle lattice
            // survived here and there, but the solid cab/bed silhouette almost entirely vanished,
            // letting the sky and its parallax reflection show straight through where a solid
            // black truck should have been - a negative-scaleX bug in this KorGE/OpenGL backend,
            // confirmed by A/B testing the exact same image and position with only the sign of
            // scaleX changed. Pre-flipping the source PNG sidesteps the bug entirely.
            else if (box in world.truckParts && truckBitmap != null) {
                if (box === world.truckParts.first()) {
                    val truckRect = world.truck ?: box
                    worldView.image(truckBitmap) {
                        size(truckRect.width, truckRect.height)
                    }.xy(truckRect.x, truckRect.y)
                }
            }
            // 5. Hanging Chained Crate (matches bounding box exactly)
            else if (box.y <= 0.0 && box.height > 150.0 && chainedCrateBitmap != null) {
                boxContainer.image(chainedCrateBitmap) {
                    size(box.width, box.height)
                }.xy(0.0, 0.0)
            }
            // 5b. Hanging jump-crate (a gap crossing, not the ceiling obstacle above):
            // Only the rectangular part (the crate body) is interactable/collidable with the user.
            // The chain and diagonal rigging above it are drawn in light black to visually show they
            // are non-collidable background elements.
            // Variant 1 uses chainedcrate.png (wide container), Variant 2 uses chainedcrate2.png (shorter crates).
            else if (box in world.hangingCrateVariant1 || box in world.hangingCrateVariant2) {
                val isVariant1 = box in world.hangingCrateVariant1
                val sourceBmp = if (isVariant1) chainedCrateBitmap else chainedCrate2Bitmap
                if (sourceBmp != null) {
                    val cropX = if (isVariant1) 26 else 235
                    val cropY = if (isVariant1) 1222 else 1134
                    val cropW = if (isVariant1) 971 else 555
                    val cropH = if (isVariant1) 226 else 287
                    val scale = box.width / cropW.toDouble()

                    // 1. Crate: the solid rectangular platform at the bottom of the asset.
                    // Fits the interactive boxContainer (box.width, box.height) exactly.
                    val crateSlice = sourceBmp.slice(RectangleInt(cropX, cropY, cropW, cropH))
                    boxContainer.image(crateSlice) {
                        size(box.width, box.height)
                    }.xy(0.0, 0.0)

                    // 2. Chain & rigging: the light-black chain above the crate up to image top.
                    // Purely visual in worldView with NO collision box, so the player can freely jump
                    // onto and stand on the crate without getting blocked by chains.
                    val chainDrawH = cropY * scale
                    val chainTopY = box.y - chainDrawH
                    val chainSlice = sourceBmp.slice(RectangleInt(cropX, 0, cropW, cropY))
                    worldView.image(chainSlice) {
                        size(box.width, chainDrawH)
                    }.xy(box.x, chainTopY)

                    // 3. Tiled vertical chain extending up to the ceiling (-400.0)
                    val linkSrcH = 160
                    val linkDrawH = linkSrcH * scale
                    val linkSlice = sourceBmp.slice(RectangleInt(cropX, 0, cropW, linkSrcH))
                    var tileY = chainTopY - linkDrawH
                    while (tileY >= -400.0) {
                        worldView.image(linkSlice) {
                            size(box.width, linkDrawH)
                        }.xy(box.x, tileY)
                        tileY -= linkDrawH
                    }
                }
            }
            // 6. Step Crate (matches bounding box exactly)
            else if (box.height < 70.0 && box.width < 150.0 && crateBitmap != null) {
                boxContainer.image(crateBitmap) {
                    size(box.width, box.height)
                }.xy(0.0, 0.0)
            }
            // 7. Long Structural Platforms and Blocks (Solid blocks with tiny rough edge irregularities)
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
        // Edge-detected per guard rather than played on every frame a guard stays INVESTIGATING -
        // this cue is "a guard just noticed something", not an ambient loop. Starts false for every
        // guard, which is correct even if a level ever spawned one already investigating: the first
        // frame would then read as "returned to patrol, still investigating" (a no-edge no-op), not
        // a false trigger.
        val guardWasInvestigating = BooleanArray(world.allGuards.size)

        // Cameras: vision cones first beneath bodies
        val cameraCones = world.cameras.map { worldView.graphics() }
        val cameraContainers = world.cameras.map { c -> worldView.container().xy(c.x, c.y) }
        val cameraMounts = world.cameras.mapIndexed { i, c ->
            // Mount & body: distinct grey housing
            cameraContainers[i].solidRect(c.width, c.height, Colors["#34495e"])
            cameraContainers[i].solidRect(c.width - 4.0, c.height - 4.0, Colors["#2c3e50"]).xy(2.0, 2.0)
            cameraContainers[i].solidRect(6.0, 6.0, Colors["#e74c3c"]).xy((c.width - 6.0) / 2.0, (c.height - 6.0) / 2.0)
        }
        val cameraWasDetecting = BooleanArray(world.cameras.size)

        // Player View
        val playerContainer = worldView.container().xy(world.player.x, world.player.y)
        val playerSourceFrameHeight = PlayerAnimations.SOURCE_FRAME_HEIGHT
        val playerSourceSilhouetteHeight = PlayerAnimations.SOURCE_SILHOUETTE_HEIGHT
        val playerSourceFeetY = PlayerAnimations.SOURCE_FEET_Y // Ground line within the frame
        val playerVisualHeight = world.player.height
        val playerBaseScale = playerVisualHeight / playerSourceSilhouetteHeight
        val playerFeetAnchorY = playerSourceFeetY / playerSourceFrameHeight
        val idleFeetOffset = (playerSourceFeetY - PlayerAnimations.IDLE_FEET_Y) * playerBaseScale
        val crouchFeetOffset = (playerSourceFeetY - PlayerAnimations.CROUCH_FEET_Y) * playerBaseScale
        val jumpLandFeetOffset = (playerSourceFeetY - PlayerAnimations.JUMP_LAND_FEET_Y) * playerBaseScale
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
        val musicVolume = { profileStorage.getProfile().musicVolume }

        fun syncBgMusicVolume() {
            val baseVol = GameAudio.BG_MUSIC_GAIN * musicVolume().toDouble()
            val effectiveVol = if (isPaused || world.isGameOver || world.isLevelComplete) {
                baseVol * 0.35
            } else {
                baseVol
            }
            val channel = bgMusicChannel
            if (channel == null) {
                if (effectiveVol > 0.001 && sounds.bgMusic != null) {
                    try {
                        bgMusicChannel = sounds.bgMusic.playForever(coroutineContext).also {
                            it.volume = effectiveVol.coerceIn(0.0, 1.0)
                        }
                    } catch (_: Throwable) {}
                }
            } else {
                try {
                    channel.volume = effectiveVol.coerceIn(0.0, 1.0)
                } catch (_: Throwable) {}
            }
        }
        syncBgMusicVolume()

        // One click for every pressable thing in the scene. Deliberate presses (pause, the
        // pause-menu strips, the Mission Failed buttons) use the full weight; the on-screen
        // D-pad uses the quiet one, because it fires on every movement input and would otherwise
        // become the loudest recurring sound in a level.
        val playClick = { gain: Double -> sounds.uiClick.playSfx(sfxContext, gain, sfxVolume()) }

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
            btn.onDown { paint(true, true); playClick(GameAudio.UI_CLICK_GAIN) }
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
            levelData.objectiveHint.uppercase(), textSize = 12.0, font = bebasFont, color = COLOR_TEXT_LIGHT
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
            "OBJECTIVE: ${levelData.objectiveHint.uppercase()}",
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
        pauseBtn.onDown { drawPauseBtn(true, true); playClick(GameAudio.UI_CLICK_GAIN) }
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

            // singleTouch, not mouse - see createImgBtn's comment on the same swap.
            btn.singleTouch {
                start {
                    onTouchChange(true)
                    drawState(true)
                    playClick(GameAudio.HUD_TAP_GAIN)
                }
                end {
                    onTouchChange(false)
                    drawState(false)
                }
                endAnywhere {
                    onTouchChange(false)
                    drawState(false)
                }
                moveAnywhere {
                    if (btn.hitTest(it.global) == null) {
                        onTouchChange(false)
                        drawState(false)
                    }
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
        //    Crouch sits level with jump (the base of the triangle) and interact sits centred
        //    above the midpoint between them (the apex) - all three the same distance apart, so
        //    the three centres form an equilateral triangle, apex up, all three buttons the same
        //    size (unlike the old fan-shaped arc, which had crouch and interact at two different
        //    angles off a bigger jump button).
        //
        //    Gaps between neighbouring buttons are 12px - tight enough that each cluster reads as
        //    one control surface, wide enough that a thumb pad landing between two of them still
        //    resolves to the one it is closest to.
        val isControlsSwapped = profileStorage.getProfile().controlsSwapped
        val edgeInset = 46.0                      // clear of the side gesture strips
        val bottomInset = 38.0                    // clear of the home indicator
        val moveRadius = 54.0
        val actionRadius = 48.0                   // uniform size for jump/crouch/interact - the old crouch button's size
        val jumpRadius = actionRadius
        val crouchRadius = actionRadius
        val interactRadius = actionRadius

        val controlsY = canvasH - bottomInset - moveRadius

        val btnGap = 12.0
        val moveSpan = moveRadius * 2.0 + btnGap
        val moveLeftX = if (isControlsSwapped) canvasW - edgeInset - moveRadius - moveSpan else edgeInset + moveRadius
        val moveRightX = if (isControlsSwapped) canvasW - edgeInset - moveRadius else edgeInset + moveRadius + moveSpan

        // Jump is the hub; crouch and interact hang off it on one arc.
        val jumpX = if (isControlsSwapped) 210.0 else canvasW - 210.0
        val jumpY = canvasH - bottomInset - jumpRadius
        val outward = if (isControlsSwapped) -1.0 else 1.0

        // Equilateral triangle: crouch is level with jump (0 degrees) at the gap-clearing
        // distance; interact is that same distance from jump at 60 degrees, which puts it
        // exactly above the midpoint of the jump-crouch base, the same distance from crouch too -
        // all three buttons pairwise equidistant.
        val actionArcRadius = jumpRadius + crouchRadius + btnGap
        val crouchAngle = 0.0
        val interactAngle = 60.0 * PI / 180.0
        val crouchX = jumpX + outward * cos(crouchAngle) * actionArcRadius
        val crouchY = jumpY - sin(crouchAngle) * actionArcRadius
        val interactX = jumpX + outward * cos(interactAngle) * actionArcRadius
        val interactY = jumpY - sin(interactAngle) * actionArcRadius

        // Image Buttons for controls
        fun createImgBtn(cx: Double, cy: Double, radius: Double, bmp: Bitmap?, fallbackColor: RGBA, fallbackDraw: ShapeBuilder.() -> Unit, onTouch: (Boolean) -> Unit) {
            if (bmp != null) {
                val btn = controlsContainer.container().xy(cx - radius, cy - radius)
                val img = btn.image(bmp) { size(radius * 2.0, radius * 2.0) }

                // singleTouch, not mouse: mouse{} tracks one pointer for the whole scene, so
                // holding this button while a second finger presses another one drops whichever
                // press came first. singleTouch tracks each finger by its own id, independently
                // per button, so multiple on-screen controls can be held down at once.
                btn.singleTouch {
                    start { onTouch(true); img.alpha = 0.6; playClick(GameAudio.HUD_TAP_GAIN) }
                    end { onTouch(false); img.alpha = 1.0 }
                    endAnywhere { onTouch(false); img.alpha = 1.0 }
                    moveAnywhere { if (btn.hitTest(it.global) == null) { onTouch(false); img.alpha = 1.0 } }
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
                    playClick(GameAudio.UI_CLICK_GAIN)
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
            bgMusicChannel?.stop()
            bgMusicChannel = null
            sceneContainer.changeTo { GameplayScene(levelData) }
        }

        pauseOverlay.createPaperMenuBtn(
            "QUIT", paperBtnBitmaps[2], pauseBtnW, pauseBtnH, pauseBtnX, pauseBtnY0 + 2 * (pauseBtnH + pauseBtnGap),
            iconDrawer = { drawQuitIcon(false) }
        ) {
            bgMusicChannel?.stop()
            bgMusicChannel = null
            getLevelExitBridge().requestReturnToMenu()
            sceneContainer.changeTo { GameplayScene(levelData) }
        }

        pauseOverlay.visible = false

        // ==========================================
        // 2. CAUGHT / GAME OVER OVERLAY (Heist Dossier styling, own content)
        // ==========================================
        // Takes the pause overlay's STYLE - full-bleed near-black scrim, stacked Bebas
        // typography with no card/badge chrome, torn-paper buttons instead of glassy tactical
        // pills - without collapsing this screen's own content into pause's. Every label and the
        // recon tip keep their original wording; only the chrome changed. Buttons are wider than
        // pause's (480 vs 300) because "CONTINUE (WATCH AD)" doesn't fit at pause's width without
        // either shrinking the text below the paper button's usual scale or renaming it - this
        // keeps the actual label and just gives it the room it needs.
        val caughtOverlay = container()
        caughtOverlay.solidRect(canvasW, canvasH, Colors["#07080A"].withAd(0.92))

        val caughtBtnW = 480.0
        val caughtBtnH = pauseBtnH
        val caughtBtnGap = pauseBtnGap
        val caughtBlockH = 150.0 + 3 * caughtBtnH + 2 * caughtBtnGap
        val caughtBlockTop = (canvasH - caughtBlockH) / 2.0
        val caughtBtnX = (canvasW - caughtBtnW) / 2.0

        val caughtTitle = caughtOverlay.text("MISSION FAILED", textSize = 52.0, font = bebasFont, color = COLOR_BORDER_RED)
        caughtTitle.graphicsRenderer = GraphicsRenderer.GPU
        caughtTitle.xy((canvasW - caughtTitle.width) / 2.0, caughtBlockTop)

        val caughtSubtitle = caughtOverlay.text(
            "SPOTTED AND APPREHENDED BY GUARD PATROL", textSize = 14.0, font = bebasFont, color = COLOR_TEXT_MUTED
        )
        caughtSubtitle.graphicsRenderer = GraphicsRenderer.GPU
        caughtSubtitle.xy((canvasW - caughtSubtitle.width) / 2.0, caughtBlockTop + 62.0)

        // Same recon tip as before the redesign, just unboxed: a small gold heading over the
        // original two lines of advice, instead of the bordered "TACTICAL RECON INTEL" panel.
        val caughtTipHeading = caughtOverlay.text("TACTICAL RECON INTEL", textSize = 11.0, font = bebasFont, color = COLOR_BORDER_GOLD)
        caughtTipHeading.graphicsRenderer = GraphicsRenderer.GPU
        caughtTipHeading.xy((canvasW - caughtTipHeading.width) / 2.0, caughtBlockTop + 90.0)

        val caughtTipLine1 = caughtOverlay.text(
            "Crouch-walk to eliminate movement noise.", textSize = 10.0, font = bebasFont, color = COLOR_TEXT_MUTED
        )
        caughtTipLine1.graphicsRenderer = GraphicsRenderer.GPU
        caughtTipLine1.alpha = 0.85
        caughtTipLine1.xy((canvasW - caughtTipLine1.width) / 2.0, caughtBlockTop + 106.0)

        val caughtTipLine2 = caughtOverlay.text(
            "Stay out of guard vision cones and use shipping crates as cover.",
            textSize = 10.0, font = bebasFont, color = COLOR_TEXT_MUTED
        )
        caughtTipLine2.graphicsRenderer = GraphicsRenderer.GPU
        caughtTipLine2.alpha = 0.85
        caughtTipLine2.xy((canvasW - caughtTipLine2.width) / 2.0, caughtBlockTop + 120.0)

        val caughtBtnY0 = caughtBlockTop + 150.0

        // Watch a rewarded ad to continue the same run. Only requests the ad here - the actual
        // restart happens in the update loop below, gated on the bridge reporting the ad was
        // genuinely watched, so a failed/declined ad just leaves this overlay's other buttons
        // usable instead of stranding the player. See .junie/guidelines.md "AdMob (basic-ads)
        // feasibility spike" and src/ContinueAdBridge.kt.
        caughtOverlay.createPaperMenuBtn(
            "CONTINUE (WATCH AD)", paperBtnBitmaps[0], caughtBtnW, caughtBtnH, caughtBtnX, caughtBtnY0,
            iconDrawer = { drawPlayIcon(false) }
        ) {
            getContinueAdBridge().requestContinueAd()
            getAnalyticsBridge().track("watch_ad_continue_requested", mapOf("level_id" to levelData.id))
        }

        caughtOverlay.createPaperMenuBtn(
            "RETRY INFILTRATION", paperBtnBitmaps[1], caughtBtnW, caughtBtnH, caughtBtnX, caughtBtnY0 + caughtBtnH + caughtBtnGap,
            iconDrawer = { drawRestartIcon(paperInk) }
        ) {
            bgMusicChannel?.stop()
            bgMusicChannel = null
            sceneContainer.changeTo { GameplayScene(levelData) }
        }

        caughtOverlay.createPaperMenuBtn(
            "RETURN TO MENU", paperBtnBitmaps[2], caughtBtnW, caughtBtnH, caughtBtnX, caughtBtnY0 + 2 * (caughtBtnH + caughtBtnGap),
            iconDrawer = { drawQuitIcon(false) }
        ) {
            bgMusicChannel?.stop()
            bgMusicChannel = null
            getLevelExitBridge().requestReturnToMenu()
            sceneContainer.changeTo { GameplayScene(levelData) }
        }

        caughtOverlay.visible = false

        // ==========================================
        // 3. LEVEL COMPLETE OVERLAY (Heist Dossier styling, own content)
        // ==========================================
        // Takes the pause overlay's STYLE - full-bleed near-black scrim, stacked Bebas
        // typography with no card/badge chrome, torn-paper buttons instead of glassy tactical
        // pills - without collapsing this screen's own content into pause's. The original badge
        // line, star breakdown, bounty stats and all three original button labels are unchanged;
        // only the chrome (card panel, boxed badge/bounty box, pill buttons) is gone. Buttons are
        // wider than pause's (420 vs 300) so "NEXT MISSION"/"ALL CLEAR!" still fit at the paper
        // button's usual text scale instead of needing to be renamed. Title stays green to read
        // as a success state, mirroring caught's red, and is sized down from pause's 52 to 36
        // since this screen carries far more content (stars, breakdown, stats) that still needs
        // to fit one screen with no scrolling - reasoned from the same screenshot dimensions the
        // rest of this file's UI passes work from, not measured on a real device.
        val winContainer = container()
        winContainer.solidRect(canvasW, canvasH, Colors["#07080A"].withAd(0.92))

        val winBtnW = 420.0
        val winBtnH = pauseBtnH
        val winBtnGap = pauseBtnGap
        val winBtnX = (canvasW - winBtnW) / 2.0
        val winColumnW = 480.0
        val winColumnX = (canvasW - winColumnW) / 2.0

        val winBlockH = 463.0
        val winBlockTop = (canvasH - winBlockH) / 2.0

        val winTitle = winContainer.text("HEIST COMPLETED!", textSize = 36.0, font = bebasFont, color = COLOR_BORDER_GREEN)
        winTitle.graphicsRenderer = GraphicsRenderer.GPU
        winTitle.xy((canvasW - winTitle.width) / 2.0, winBlockTop)

        val winSubtitle = winContainer.text(
            "MISSION ACCOMPLISHED // EXTRACTION SUCCESS", textSize = 13.0, font = bebasFont, color = COLOR_BORDER_GOLD
        )
        winSubtitle.graphicsRenderer = GraphicsRenderer.GPU
        winSubtitle.xy((canvasW - winSubtitle.width) / 2.0, winBlockTop + 44.0)

        val winStarsGraphics = winContainer.uiGraphics().xy(0.0, 0.0)
        val winStarsCy = winBlockTop + 107.0

        val star1Label = winContainer.text("Star 1: Extraction Complete", textSize = 11.0, font = bebasFont, color = COLOR_TEXT_LIGHT)
        star1Label.graphicsRenderer = GraphicsRenderer.GPU
        star1Label.xy(winColumnX, winBlockTop + 148.0)
        val star2Label = winContainer.text("Star 2: Undetected (Ghost)", textSize = 11.0, font = bebasFont, color = COLOR_TEXT_LIGHT)
        star2Label.graphicsRenderer = GraphicsRenderer.GPU
        star2Label.xy(winColumnX, winBlockTop + 163.0)
        val star3Label = winContainer.text("Star 3: Fast Time (≤ ${levelData.timeTargetSeconds.toInt()}s)", textSize = 11.0, font = bebasFont, color = COLOR_TEXT_LIGHT)
        star3Label.graphicsRenderer = GraphicsRenderer.GPU
        star3Label.xy(winColumnX, winBlockTop + 178.0)

        val statsLabel = winContainer.text("", textSize = 12.0, font = bebasFont, color = COLOR_BORDER_CYAN)
        statsLabel.graphicsRenderer = GraphicsRenderer.GPU
        statsLabel.xy(winColumnX, winBlockTop + 211.0)
        val coinsEarnedLabel = winContainer.text("", textSize = 13.0, font = bebasFont, color = COLOR_BORDER_GOLD)
        coinsEarnedLabel.graphicsRenderer = GraphicsRenderer.GPU
        coinsEarnedLabel.xy(winColumnX, winBlockTop + 227.0)
        val bestLabel = winContainer.text("", textSize = 11.0, font = bebasFont, color = COLOR_BORDER_GREEN)
        bestLabel.graphicsRenderer = GraphicsRenderer.GPU
        bestLabel.xy(winColumnX, winBlockTop + 243.0)

        val winBtnY0 = winBlockTop + 279.0

        val allLevels = LevelData.DEFAULT_LEVELS
        val currentLevelIndex = allLevels.indexOfFirst { it.id == levelData.id }
        val nextLevel = if (currentLevelIndex >= 0 && currentLevelIndex + 1 < allLevels.size) allLevels[currentLevelIndex + 1] else null

        if (nextLevel != null) {
            winContainer.createPaperMenuBtn(
                "NEXT MISSION", paperBtnBitmaps[0], winBtnW, winBtnH, winBtnX, winBtnY0,
                iconDrawer = { drawPlayIcon(false) }
            ) {
                bgMusicChannel?.stop()
                bgMusicChannel = null
                sceneContainer.changeTo { GameplayScene(nextLevel) }
            }
        } else {
            winContainer.createPaperMenuBtn(
                "ALL CLEAR!", paperBtnBitmaps[0], winBtnW, winBtnH, winBtnX, winBtnY0,
                iconDrawer = { drawPlayIcon(false) }
            ) {
                // Lands on the menu's default screen (MainMenu), not Missions specifically -
                // NavigationRoot remounts fresh every time gameplay hides it, so there is
                // currently no way to tell it which screen to come back to. See
                // getLevelExitBridge()'s doc comment.
                bgMusicChannel?.stop()
                bgMusicChannel = null
                getLevelExitBridge().requestReturnToMenu()
                sceneContainer.changeTo { GameplayScene(levelData) }
            }
        }

        winContainer.createPaperMenuBtn(
            "RETRY", paperBtnBitmaps[1], winBtnW, winBtnH, winBtnX, winBtnY0 + winBtnH + winBtnGap,
            iconDrawer = { drawRestartIcon(paperInk) }
        ) {
            bgMusicChannel?.stop()
            bgMusicChannel = null
            sceneContainer.changeTo { GameplayScene(levelData) }
        }

        winContainer.createPaperMenuBtn(
            "MAIN MENU", paperBtnBitmaps[2], winBtnW, winBtnH, winBtnX, winBtnY0 + 2 * (winBtnH + winBtnGap),
            iconDrawer = { drawQuitIcon(false) }
        ) {
            bgMusicChannel?.stop()
            bgMusicChannel = null
            getLevelExitBridge().requestReturnToMenu()
            sceneContainer.changeTo { GameplayScene(levelData) }
        }

        winContainer.visible = false

        world.onLevelComplete = {
            val result = world.getLevelResult()
            levelStorage.saveResult(result)
            val bestResult = levelStorage.getBestResult(result.levelId) ?: result

            getAnalyticsBridge().track(
                "level_complete",
                mapOf(
                    "level_id" to result.levelId,
                    "stars" to result.starCount,
                    "time_taken_seconds" to result.timeTaken,
                    "alerts" to world.spottedCount
                )
            )

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
                val starPositions = listOf(canvasW / 2.0 - 60.0, canvasW / 2.0, canvasW / 2.0 + 60.0)
                val starsEarned = listOf(result.star1, result.star2, result.star3)

                for (i in 0 until 3) {
                    val cx = starPositions[i]
                    val cy = winStarsCy
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
            getAnalyticsBridge().track(
                "mission_failed",
                mapOf("level_id" to levelData.id, "alerts" to world.spottedCount)
            )
        }

        var totalElapsedSeconds = 0.0

        // Main game update loop
        addUpdater { dt ->
            // Checked unconditionally (ahead of the isGameOver early-return below), since that's
            // exactly the state this fires in: the native shell has shown the rewarded ad while
            // this scene stayed alive in the background, and grants the continue once the player
            // actually watched it. Restarts the same way "RETRY INFILTRATION" already does.
            if (getContinueAdBridge().consumeContinueGranted()) {
                getAnalyticsBridge().track("watch_ad_continue_granted", mapOf("level_id" to levelData.id))
                sounds.toastSuccess.playSfx(sfxContext, GameAudio.TOAST_SUCCESS_GAIN, sfxVolume())
                bgMusicChannel?.stop()
                bgMusicChannel = null
                // Hidden immediately, not left for changeTo to sort out: this scene (with its
                // MISSION FAILED overlay still visible) stays on screen for however many frames
                // the transition to the new GameplayScene instance takes, which is exactly the
                // "continue menu flashes for a split second" the ad-continue flow was showing.
                caughtOverlay.visible = false
                sceneContainer.stage?.launchImmediately { sceneContainer.changeTo { GameplayScene(levelData) } }
                return@addUpdater
            }

            if (views.input.keys.justPressed(Key.ESCAPE) || views.input.keys.justPressed(Key.P)) {
                if (!world.isLevelComplete && !world.isGameOver) {
                    isPaused = !isPaused
                }
            }

            pauseOverlay.visible = isPaused

            syncBgMusicVolume()

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


            playerSprite.y = world.player.height + when {
                playerAnimState == "idle" -> idleFeetOffset
                // Only the held/entering/exiting stance, not crouchwalk - the walk cycle's
                // alternating planted/swinging foot is supposed to look uneven, this offset is
                // only for the settled two-feet-down pose.
                playerAnimState == "crouch" -> crouchFeetOffset
                else -> 0.0
            }
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
                if (jumpPhase == "land") {
                    playerSprite.y += jumpLandFeetOffset
                }

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
            if (world.player.isClimbing) {
                playerFacingLeft = world.player.facing < 0.0
            } else if (moveInput < 0) {
                playerFacingLeft = true
            } else if (moveInput > 0) {
                playerFacingLeft = false
            }
            playerSprite.scaleX = playerBaseScale * (if (playerFacingLeft) -1.0 else 1.0)
            playerSprite.scaleY = playerBaseScale

            playerSprite.x = world.player.width / 2.0

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

                val isInvestigating = g.state == GuardState.INVESTIGATING
                if (isInvestigating && !guardWasInvestigating[i]) {
                    sounds.guardInvestigate.playSfx(sfxContext, GameAudio.GUARD_INVESTIGATE_GAIN, sfxVolume())
                }
                guardWasInvestigating[i] = isInvestigating
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
                val isDetecting = world.cameras[i] in world.detectingCameras
                if (isDetecting && !cameraWasDetecting[i]) {
                    sounds.cameraDetect.playSfx(sfxContext, GameAudio.CAMERA_DETECT_GAIN, sfxVolume())
                }
                cameraWasDetecting[i] = isDetecting
                paintPip(
                    cameraPips[i],
                    if (world.activePowerups.isSmokeScreenActive) 0.0
                    else pipFor(isDetecting, false)
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
