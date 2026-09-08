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
import game.scene.UiComponents.drawCurvedArrow
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
        val loadingBgBitmap = SceneAssets.bitmap("loadingbg.png")
        val loadingLogoBitmap = SceneAssets.bitmap("logo_main.png")
        // Same torn-paper texture as the main menu's PLAY button (Res.drawable.button1 there) -
        // stretched to fit, matching the existing precedent for these button textures elsewhere
        // in this file (UiComponents.createButton's heistStyle path): plain stretch, not 9-sliced,
        // since 9-slicing this exact art was already tried and reverted for visible seams.
        val loadingBarTextureBitmap = SceneAssets.bitmap("button1.png")
        val loadingFont = SceneAssets.font("BebasNeue-Regular.ttf")

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

        val totalLoadSteps = 20
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
        val bgmgBitmap = SceneAssets.bitmap(bgFileName)
        markLoadProgress()
        val crateBitmap = SceneAssets.bitmap("crate.png")
        markLoadProgress()
        val chainedCrateBitmap = SceneAssets.bitmap("chainedcrate.png")
        markLoadProgress()
        val chainedCrate2Bitmap = SceneAssets.bitmap("chainedcrate2.png")
        markLoadProgress()
        val fenceBitmap = SceneAssets.bitmap("fence.png")
        markLoadProgress()
        val fence2Bitmap = SceneAssets.bitmap("fence2.png")
        markLoadProgress()
        val barrelBitmap = SceneAssets.bitmap("barrel.png")
        markLoadProgress()
        val truckBitmap = SceneAssets.bitmap("truck.png")
        markLoadProgress()
        val entranceBitmap = SceneAssets.bitmap("entrance.png")
        markLoadProgress()
        val exitFenceBitmap = SceneAssets.bitmap("exitfence.png")
        markLoadProgress()
        val leftBtnBitmap = SceneAssets.bitmap("left.png")
        markLoadProgress()
        val rightBtnBitmap = SceneAssets.bitmap("right.png")
        markLoadProgress()
        val crouchBtnBitmap = SceneAssets.bitmap("crouch.png")
        markLoadProgress()
        val jumpBtnBitmap = SceneAssets.bitmap("jump.png")
        markLoadProgress()
        val interactBtnBitmap = SceneAssets.bitmap("interact.png")
        markLoadProgress()
        // The main menu's torn-paper button strips. They already live in resources/ (the Compose
        // menu reads its own copies out of composeResources), so the pause menu can be built from
        // the very same art rather than a lookalike drawn in vectors.
        val paperBtnBitmaps = listOf("button1.png", "button2.png", "button3.png", "button4.png")
            .map { name -> SceneAssets.bitmap(name) }
        markLoadProgress()
        // The main menu's briefing sheet (MainMenuScreen.MissionDossierCard). Checked in twice
        // for the same reason the button strips are - Korge reads resources/, the Compose menu
        // reads its own composeResources/ copy, and the two builds share no asset pipeline.
        val dossierBitmap = SceneAssets.bitmap("dossier_paper.png")
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

        // Off-screen culling. KorGE does no frustum culling of its own, so every child of worldView
        // submits its geometry every frame no matter where the camera is - and this level is 3900
        // units wide against roughly 1040 visible, so most of it is off screen at any moment.
        // Static world decor registers its world-space x-span here as it is built, and the updater
        // toggles `visible` from the camera window once a frame. Registered by known rect rather
        // than a measured bound because every one of these already has its rect to hand.
        //
        // Deliberately static-only: the player, guards, cameras and moving platforms are left out,
        // since a span captured once would go stale the moment they move (their detection pips and
        // vision cones are children of those same containers, so they are covered by the omission).
        class CullTarget(val view: View, val left: Double, val right: Double)
        val cullTargets = ArrayList<CullTarget>()
        fun cullable(view: View, worldLeft: Double, worldWidth: Double) {
            cullTargets.add(CullTarget(view, worldLeft, worldLeft + worldWidth))
        }

        // Floors, walkways and boundary walls (Solid black platforms with tiny rough edge irregularities)
        for (platform in world.platforms) {
            if (platform in world.boxes) continue
            if (platform.width >= 1000.0 && platform.height >= 1000.0) continue // Skip bounds walls
            if (!isSideScrolling && (platform.x < 0 || platform.x >= 800)) continue

            val platCont = worldView.container().xy(platform.x, platform.y)
            renderRoughBlock(platCont, platform.width, platform.height, seed = (platform.x * 47.0 + platform.y).toLong())
            cullable(platCont, platform.x, platform.width)
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
            cullable(
                worldView.image(entranceBitmap) {
                    size(entranceWidth, entranceHeight)
                }.xy(world.exitZone.x, entranceY),
                world.exitZone.x, entranceWidth
            )
            if (exitFenceBitmap != null) {
                val exitFenceHeight = 140.0
                val exitFenceWidth = exitFenceHeight * (exitFenceBitmap.width.toDouble() / exitFenceBitmap.height.toDouble())
                cullable(
                    worldView.image(exitFenceBitmap) {
                        size(exitFenceWidth, exitFenceHeight)
                    }.xy(world.exitZone.x + entranceWidth, baseGroundY - exitFenceHeight),
                    world.exitZone.x + entranceWidth, exitFenceWidth
                )
            }
        }

        fun renderHangingCrate(
            parent: Container,
            width: Double,
            height: Double,
            crateY: Double = 0.0,
            isVariant1: Boolean
        ) {
            val sourceBmp = (if (isVariant1) chainedCrateBitmap else chainedCrate2Bitmap) ?: return
            val cropX = if (isVariant1) 26 else 235
            val cropY = if (isVariant1) 1222 else 1134
            val cropW = if (isVariant1) 971 else 555
            val cropH = if (isVariant1) 226 else 287
            val scale = width / cropW.toDouble()

            // 1. Crate: the solid rectangular platform at the bottom of the asset.
            val crateSlice = sourceBmp.slice(RectangleInt(cropX, cropY, cropW, cropH))
            parent.image(crateSlice) {
                size(width, height)
            }.xy(0.0, crateY)

            // 2. Chain & rigging: the lighter slate chain above the crate up to image top.
            // Purely visual with NO collision box, so the player can freely jump onto the crate.
            val chainDrawH = cropY * scale
            val chainTopY = crateY - chainDrawH
            val chainSlice = sourceBmp.slice(RectangleInt(cropX, 0, cropW, cropY))
            parent.image(chainSlice) {
                size(width, chainDrawH)
            }.xy(0.0, chainTopY)

            // 3. Tiled vertical chain extending up to the ceiling (-1000.0)
            val linkSrcH = 160
            val linkDrawH = linkSrcH * scale
            val linkSlice = sourceBmp.slice(RectangleInt(cropX, 0, cropW, linkSrcH))
            var tileY = chainTopY - linkDrawH
            while (tileY >= -1000.0) {
                parent.image(linkSlice) {
                    size(width, linkDrawH)
                }.xy(0.0, tileY)
                tileY -= linkDrawH
            }
        }

        // Tactical boxes, step crates, hanging chained crates, and perimeter fences
        for (box in world.boxes) {
            if (box.width <= 0.0) continue
            val boxContainer = worldView.container().xy(box.x, box.y)
            cullable(boxContainer, box.x, box.width)

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
            // 3. Barrels (jump on, walk across, jump off / rescue climb points)
            else if (box in world.barrels && barrelBitmap != null) {
                boxContainer.image(barrelBitmap) {
                    size(box.width, box.height)
                }.xy(0.0, 0.0)
            }
            // 4. Truck (parked next to the small crate, climbed onto en route to the long platform).
            else if (box in world.truckParts && truckBitmap != null) {
                if (box === world.truckParts.first()) {
                    val truckRect = world.truck ?: box
                    cullable(
                        worldView.image(truckBitmap) {
                            size(truckRect.width, truckRect.height)
                        }.xy(truckRect.x, truckRect.y),
                        truckRect.x, truckRect.width
                    )
                }
            }
            // 5. Hanging Chained Crate (ceiling obstacle in level 1)
            else if (box.y <= 0.0 && box.height > 150.0 && chainedCrateBitmap != null) {
                boxContainer.image(chainedCrateBitmap) {
                    size(box.width, box.height)
                }.xy(0.0, 0.0)
            }
            // 5b. Hanging jump-crate (stationary gap crossing in level 2)
            else if (box in world.hangingCrateVariant1 || box in world.hangingCrateVariant2) {
                boxContainer.removeFromParent()
                val crateCont = worldView.container().xy(box.x, box.y)
                cullable(crateCont, box.x, box.width)
                renderHangingCrate(crateCont, box.width, box.height, 0.0, box in world.hangingCrateVariant1)
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

        // Moving hanging containers (dynamic platforming)
        val movingPlatformContainers = world.movingPlatforms.map { mp ->
            val crateCont = worldView.container().xy(mp.x, mp.y)
            renderHangingCrate(crateCont, mp.width, mp.height, 0.0, mp.isVariant1)
            crateCont
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
        playerSprite.playAnimationLooped(playerAnimations.idle, PlayerAnimations.IDLE_FRAME_TIME_MS.milliseconds)
        var playerAnimState = "idle"
        var playerFacingLeft = true
        var isFirstCameraFrame = true

        val jumpLaunchFrame = PlayerAnimations.JUMP_LAUNCH_START
        val jumpAirborneFrame = PlayerAnimations.JUMP_RISE_START
        val jumpApexFrame = PlayerAnimations.JUMP_APEX
        val jumpTouchdownFrame = PlayerAnimations.JUMP_TOUCHDOWN
        val jumpLandFrame = PlayerAnimations.JUMP_LAND_START
        val jumpLastFrame = PlayerAnimations.JUMP_LAND_END
        val jumpLaunchDuration = 0.05
        val jumpLandDuration = 0.24
        var jumpPhase = "none"
        var jumpPhaseElapsed = 0.0
        var jumpStartY = world.player.y

        // Landing absorption: when the player lands while moving, play a brief cushion of the
        // initial touchdown frames (27..28) before handing over to the forward walk stride (frame 5..17).
        // This eliminates the jarring pop from airborne/squat to full-speed run stride.
        var landingAbsorb = false
        var landingAbsorbElapsed = 0.0
        val landingAbsorbDuration = 0.05
        val landingAbsorbFrames = 2


        // Crouch: entering/exiting are the down/up transition played once; holding pins the last
        // frame. crouchFrameProgress is continuous (not just a phase flag) so re-toggling crouch
        // mid-transition reverses smoothly from wherever the animation currently is, instead of
        // snapping to a fixed pose first.
        // Audio trigger state. Footsteps are edge-triggered off the same distance-driven gait
        // cycle that picks the walk frame, so a step fires when the foot lands rather than on a
        // timer that drifts against the animation whenever speed changes.
        var stepAlternate = false
        // One profile read per frame, not four. InMemoryGameProfileStorage.getProfile() hands back
        // a deep copy - a fresh GameProfile plus a copied unlocked-level set plus a copied powerup
        // map - so reading a single volume float allocated three objects. The updater was doing
        // that for the music volume, again for the powerup HUD, and once more per footstep, which
        // at 60fps is a few hundred short-lived objects a second on a heap that is already under
        // pressure from the texture atlas. Refreshed at the top of the updater (and immediately
        // after anything that mutates the profile) so a change made in the menus still lands on
        // the very next frame, exactly as it did when every call re-read storage.
        var cachedProfile = profileStorage.getProfile()
        val refreshProfile = { cachedProfile = profileStorage.getProfile() }
        val sfxVolume = { cachedProfile.sfxVolume }
        val musicVolume = { cachedProfile.musicVolume }

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
        val walkTransitionDuration = 0.28
        var walkTransitionElapsed = 0.0
        var walkInTransition = false
        var walkTransitionStartFrame = PlayerAnimations.WALK_TRANSITION_START
        var walkTransitionCurrentDuration = walkTransitionDuration
        var stationaryElapsed = 0.20
        var crouchStationaryElapsed = 0.25
        val manualFrameTime = 1_000_000.milliseconds

        val bebasFont = SceneAssets.font("BebasNeue-Regular.ttf")
        val handwrittenFont = SceneAssets.font("handwritten.ttf", fallback = bebasFont)

        // A pause-menu button in the main menu's language: a torn white paper strip with the
        // label and icon stamped on it in ink. Same textures, same ink colour, same Bebas face,
        // so pausing does not drop the player into a different-looking game.
        val paperInk = Colors["#17140F"]

        // Shared layout columns for every paper button, as fractions of its width. Taken off the
        // Compose menu's own proportions, where the icon sits about a third in and the label
        // starts just past it.
        val ICON_COLUMN = 0.35
        val LABEL_COLUMN = 0.42

        // A centred strip carries its icon and label as one group in the middle of the button,
        // rather than on the two shared columns above. Used by the end-of-run cards, whose
        // buttons sit side by side in a row and are each cut to their own label's width - there
        // is no column for them to share, and hanging a short label like RETRY off a fixed 35%
        // column just pushes it into the button's right half.
        val CENTERED_ICON_WIDTH = 22.0
        val CENTERED_ICON_GAP = 12.0

        /**
         * Width a `centered = true` strip needs to hold [label] at [height] without crowding its
         * torn edges - the label measured for real, plus the icon, its gap and a symmetric inset.
         *
         * Measured rather than estimated because Bebas is condensed and its advance widths differ
         * enough between platforms that a per-character guess would fit on desktop and clip on a
         * phone. The probe text is added and removed inside this call, so nothing renders.
         */
        fun Container.paperMenuBtnWidth(label: String, height: Double): Double {
            val probe = text(label.uppercase(), textSize = height * 0.44, font = bebasFont, color = paperInk)
            probe.graphicsRenderer = GraphicsRenderer.GPU
            val w = probe.width + CENTERED_ICON_WIDTH + CENTERED_ICON_GAP + height * 0.64
            probe.removeFromParent()
            return w
        }

        fun Container.createPaperMenuBtn(
            label: String,
            texture: Bitmap?,
            width: Double,
            height: Double,
            x: Double,
            y: Double,
            centered: Boolean = false,
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
            if (centered) {
                val contentW = CENTERED_ICON_WIDTH + CENTERED_ICON_GAP + text.width
                val contentX = (width - contentW) / 2.0
                iconG.xy(contentX + CENTERED_ICON_WIDTH / 2.0, height / 2.0)
                text.xy(contentX + CENTERED_ICON_WIDTH + CENTERED_ICON_GAP, (height - text.height) / 2.0 - 1.0)
            } else {
                iconG.xy(width * ICON_COLUMN, height / 2.0)
                text.xy(width * LABEL_COLUMN, (height - text.height) / 2.0 - 1.0)
            }

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

        // --- Tutorial Tactical Callout & Action Guidance Overlay ----------------------------
        val tutorialSteps = levelData.tutorialSteps
        val tutorialLayer = container().xy(0.0, 0.0)
        val tutorialDarkOverlay = tutorialLayer.uiGraphics()

        // Highlight container for rendering bright button textures above the dark scrim
        val tutorialHighlightContainer = tutorialLayer.container().xy(0.0, 0.0)
        val hlLeftImg = if (leftBtnBitmap != null) tutorialHighlightContainer.image(leftBtnBitmap) {
            xy(moveLeftX - moveRadius, controlsY - moveRadius)
            size(moveRadius * 2.0, moveRadius * 2.0)
            visible = false
        } else null
        val hlRightImg = if (rightBtnBitmap != null) tutorialHighlightContainer.image(rightBtnBitmap) {
            xy(moveRightX - moveRadius, controlsY - moveRadius)
            size(moveRadius * 2.0, moveRadius * 2.0)
            visible = false
        } else null
        val hlJumpImg = if (jumpBtnBitmap != null) tutorialHighlightContainer.image(jumpBtnBitmap) {
            xy(jumpX - jumpRadius, jumpY - jumpRadius)
            size(jumpRadius * 2.0, jumpRadius * 2.0)
            visible = false
        } else null
        val hlCrouchImg = if (crouchBtnBitmap != null) tutorialHighlightContainer.image(crouchBtnBitmap) {
            xy(crouchX - crouchRadius, crouchY - crouchRadius)
            size(crouchRadius * 2.0, crouchRadius * 2.0)
            visible = false
        } else null
        val hlInteractImg = if (interactBtnBitmap != null) tutorialHighlightContainer.image(interactBtnBitmap) {
            xy(interactX - interactRadius, interactY - interactRadius)
            size(interactRadius * 2.0, interactRadius * 2.0)
            visible = false
        } else null

        val tutorialHighlightGraphics = tutorialLayer.uiGraphics()
        val tutorialHandwrittenText = tutorialLayer.text("", textSize = 28.0, font = handwrittenFont, color = Colors.WHITE)

        tutorialLayer.alpha = 0.0
        tutorialLayer.visible = false

        // ==========================================
        // TACTICAL POWERUP QUICK-DOCK (Dynamic Floating)
        // ==========================================
        data class PowerupHudButton(
            val type: PowerupType,
            val keyNum: String,
            val btnContainer: Container,
            val bg: Graphics,
            val nameText: Text,
            val countText: Text,
            /**
             * Last drained-underline fraction this chip's [bg] was built with, or null if it has
             * never been built. The chip's vector shape only changes when the powerup goes
             * active/inactive or its timer bar moves, but updateShape re-tessellates the rounded
             * rect, its stroke and the underline every time it is called - so the updater compares
             * against this and skips the rebuild when the chip would come out identical. -1.0
             * stands for "inactive", which is a single fixed shape.
             */
            var lastDrawnSpan: Double? = null,
            /**
             * Inputs the count label was last built from. The label only has three sources - live
             * or not, the inventory count, and the timer rounded to a tenth - so comparing those
             * skips both the string build and, more importantly, the `countText.width` read used
             * to re-centre it, which forces a text bounds measurement. `lastActive` starts null so
             * the first frame always renders.
             */
            var lastActive: Boolean? = null,
            var lastCount: Int = -1,
            var lastTenths: Int = -1
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
                // The HUD chips read the per-frame cache, and this can fire from a key press
                // earlier in the same frame, so re-read rather than show a stale count for a tick.
                refreshProfile()
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
        // 2 & 3. END-OF-RUN DOSSIER SHEETS (MISSION FAILED / HEIST COMPLETE)
        // ==========================================
        // Both end-of-run screens are one design with two fills of content, and that design is
        // the main menu's briefing sheet: the torn `dossier_paper.png` with the debrief printed
        // on it in ink, a rubber stamp for the verdict, and the menu's own torn-paper strips as
        // the actions - stacked in a column to the left of the sheet, which is the main menu's
        // whole composition (button column left, dossier sheet right).
        //
        // An earlier pass built these as dark #141416 cards with hairline borders and coin pills,
        // borrowed from the Compose store/missions screens. Those screens are real, but they are
        // the game's *chrome*; the sheet is its *identity*, and a heist debrief is exactly the
        // thing that belongs on paper. Don't reintroduce dark panels, hairline strokes or the
        // coin pill here - on a page they read as a different app pasted over the game.
        //
        // Every ink value below is MainMenuScreen.MissionDossierCard's, including the sheet's
        // 1.5 aspect (never stretch it on one axis - that pulls the torn edge), its content
        // insets as fractions of the sheet, and the -5.2 degree tilt that squares the type to the
        // paper rather than to the screen. See that file's own comments for how each was measured.
        val resScrim = Colors["#07080A"].withAd(0.92)

        val inkStrong = Colors["#17140F"]
        val inkBody = Colors["#17140F"].withAd(0.78)
        val inkFaint = Colors["#17140F"].withAd(0.55)
        val inkRuleColor = Colors["#17140F"].withAd(0.34)
        // Aged gold and stamp inks, not the menu's neon accents: #FFD700 and #00E676 are tuned to
        // glow on a near-black panel and read as highlighter on paper. These are pigments.
        val inkGold = Colors["#A8781A"]
        val stampRed = Colors["#96222A"]
        val stampGreen = Colors["#25603A"]

        val DOSSIER_ASPECT = 1.5
        val DOSSIER_TILT = (-5.2).degrees

        // Sheet as tall as the canvas allows, button column beside it, the pair centred. S is the
        // one knob: if the two together overrun a narrow canvas the whole group shrinks, so the
        // sheet keeps its aspect and the type keeps its proportions instead of the columns
        // colliding. It is 1.0 on the 1040x480 canvas main.kt and MainActivity both use.
        val sheetH0 = min(416.0, canvasH - 48.0)
        val sheetW0 = sheetH0 * DOSSIER_ASPECT
        val resBtnW0 = 300.0
        val resBtnH0 = 52.0
        val resBtnGap0 = 14.0
        val resColGap0 = 44.0
        val S = min(1.0, (canvasW - 56.0) / (resBtnW0 + resColGap0 + sheetW0))

        val sheetH = sheetH0 * S
        val sheetW = sheetW0 * S
        val resBtnW = resBtnW0 * S
        val resBtnH = resBtnH0 * S
        val resBtnGap = resBtnGap0 * S
        val resGroupW = resBtnW + resColGap0 * S + sheetW
        val resGroupX = (canvasW - resGroupW) / 2.0
        val sheetX = resGroupX + resBtnW + resColGap0 * S
        val sheetY = (canvasH - sheetH) / 2.0

        // The block of paper the type may actually sit on, as fractions of the sheet. Measured on
        // the artwork in MissionDossierCard - the left inset is the widest because the number
        // rides the folder tab, and the foot is deepest because the tilt drops the last line.
        val docX = sheetW * 0.15
        val docW = sheetW * 0.76
        val docY = sheetH * 0.055
        val docH = sheetH * 0.805
        // Ink coordinates are relative to the block, but the tilted layer pivots on the sheet's
        // centre (Korge rotates about a view's own origin, so the layer is placed there and its
        // children carry the offset) - these two convert one to the other.
        fun dpx(x: Double): Double = docX + x * S - sheetW / 2.0
        fun dpy(y: Double): Double = docY + y * S - sheetH / 2.0

        /** Sheet artwork plus the tilted ink layer everything else is drawn into. */
        fun Container.createDossierSheet(): Container {
            val sheet = container().xy(sheetX, sheetY)
            if (dossierBitmap != null) {
                sheet.image(dossierBitmap) { size(sheetW, sheetH) }
            } else {
                sheet.uiGraphics().updateShape {
                    fill(Colors["#D8D2C4"]) { rect(0.0, 0.0, sheetW, sheetH) }
                }
            }
            val ink = sheet.container().xy(sheetW / 2.0, sheetH / 2.0)
            ink.rotation = DOSSIER_TILT
            return ink
        }

        fun Container.inkText(
            value: String, size: Double, color: RGBA, x: Double, y: Double,
            font: Font = bebasFont
        ): Text {
            val t = text(value, textSize = size * S, font = font, color = color)
            t.graphicsRenderer = GraphicsRenderer.GPU
            t.xy(dpx(x), dpy(y))
            return t
        }

        /**
         * A printed rule across the form. [dashedTail] breaks the last stretch into three ticks,
         * the way the sheet's upper rule does on the main menu - it stops short of the paperclip
         * painted into the artwork's top-right corner rather than running under it.
         */
        fun Container.inkRule(y: Double, fromX: Double, toX: Double, dashedTail: Boolean = false) {
            uiGraphics().updateShape {
                val yy = dpy(y)
                val weight = 1.6 * S
                if (!dashedTail) {
                    fill(inkRuleColor) { rect(dpx(fromX), yy, (toX - fromX) * S, weight) }
                } else {
                    val solidTo = toX - 54.0
                    fill(inkRuleColor) {
                        rect(dpx(fromX), yy, (solidTo - fromX) * S, weight)
                        for (i in 0 until 3) rect(dpx(solidTo + 6.0 + i * 16.0), yy, 10.0 * S, weight)
                    }
                }
            }
        }

        /**
         * A filled-in field: the printed label with the answer written under it, both left-aligned
         * on the same x. Returns the setter, since every answer here is only known once the run
         * ends.
         *
         * Stacked, rather than the label-left / value-flush-right row with dot leaders this
         * started as. The sheet is tilted 5.2 degrees, which lifts anything to the right of the
         * label by tan(5.2) per unit - across a column wide enough to hold a form row that is
         * nearly a full row of rise, and the value ends up sitting beside the label ABOVE it and
         * reading as that line's answer. Confirmed on screen, not predicted. Stacking puts label
         * and value on one x, so the tilt carries the pair together and the reading is safe at any
         * tilt. Anything that spans the sheet horizontally has the same problem - keep new fields
         * stacked.
         */
        fun Container.inkField(x: Double, y: Double, label: String): (String, RGBA) -> Unit {
            inkText(label, 11.0, inkFaint, x, y)
            val valueText = text("", textSize = 17.0 * S, font = bebasFont, color = inkStrong)
            valueText.graphicsRenderer = GraphicsRenderer.GPU
            return { value, color ->
                valueText.text = value
                valueText.color = color
                valueText.xy(dpx(x), dpy(y + 13.0))
            }
        }

        /**
         * The verdict, as a rubber stamp slapped across the form at its own angle.
         *
         * Returns the second line inside the stamp, empty until a caller fills it - the win
         * sheet's rating goes there rather than under the stamp, where it was landing on the
         * stamp's own bottom edge on one side and the rule below it on the other. A stamp's
         * corners swing well past half its width at this angle, so leave it room.
         */
        fun Container.inkStamp(
            cx: Double, cy: Double, w: Double, h: Double, label: String, color: RGBA,
            hasSubLine: Boolean = false
        ): Text {
            val stamp = container().xy(dpx(cx), dpy(cy))
            stamp.rotation = (-10.0).degrees
            // Not quite opaque: stamp ink sits on the paper's tooth, it does not cover it.
            stamp.alpha = 0.82
            val halfW = w * S / 2.0
            val halfH = h * S / 2.0
            stamp.uiGraphics().updateShape {
                // Heavy outer band with a hairline inside it - a rubber stamp's border is a wide
                // ring of ink, and a single thin rectangle reads as a UI box drawn on the page.
                stroke(color, StrokeInfo(thickness = 6.0 * S)) { rect(-halfW, -halfH, w * S, h * S) }
                stroke(color, StrokeInfo(thickness = 1.2 * S)) {
                    rect(-halfW + 7.0 * S, -halfH + 7.0 * S, w * S - 14.0 * S, h * S - 14.0 * S)
                }
            }
            val t = stamp.text(label, textSize = 20.0 * S, font = bebasFont, color = color)
            t.graphicsRenderer = GraphicsRenderer.GPU
            // Only lifted off centre when a second line is actually going to be written under
            // it - a lone label riding high in the box reads as a mis-centred box, not a stamp.
            t.xy(-t.width / 2.0, -t.height / 2.0 - if (hasSubLine) 8.0 * S else 0.0)
            val subLine = stamp.text("", textSize = 13.0 * S, font = bebasFont, color = color)
            subLine.graphicsRenderer = GraphicsRenderer.GPU
            return subLine
        }

        val allLevels = LevelData.DEFAULT_LEVELS
        val currentLevelIndex = allLevels.indexOfFirst { it.id == levelData.id }
        val nextLevel = if (currentLevelIndex >= 0 && currentLevelIndex + 1 < allLevels.size) allLevels[currentLevelIndex + 1] else null

        val missionFileNo = (Regex("^(\\d+)").find(levelData.name)?.groupValues?.get(1)
            ?: levelData.id.filter { it.isDigit() }.ifEmpty { "1" }).padStart(2, '0')
        val missionTitleText = levelData.name.replaceFirst(Regex("^\\d+:\\s*"), "").uppercase()

        fun secondsText(t: Float): String = "${(t * 10).toInt() / 10.0}S"

        /** The sheet's masthead: file number, ruled tail, chapter, section title, ruled foot. */
        fun Container.inkMasthead(sectionTitle: String) {
            inkText(missionFileNo, 26.0, inkStrong, 0.0, 0.0)
            inkRule(34.0, 0.0, docW / S * 0.95, dashedTail = true)
            inkText(missionTitleText, 12.0, inkFaint, 0.0, 44.0)
            inkText(sectionTitle, 26.0, inkStrong, 0.0, 60.0)
            inkRule(96.0, 0.0, docW / S)
        }

        // The button strips are the pause menu's, at the same 300x52 and on the same two shared
        // icon/label columns - a stack of actions is a solved problem in this game and this is
        // that same stack, not a new one.
        val resBtnBlockH = 3.0 * resBtnH + 2.0 * resBtnGap
        val resBtnY0 = (canvasH - resBtnBlockH) / 2.0

        // ------------------------------------------------------------------
        // MISSION FAILED
        // ------------------------------------------------------------------
        // The form carries the debrief this screen never gave - what happened, how many times,
        // how long the run lasted, the standing record and the purse - with the verdict stamped
        // beside it. The recon tip keeps its original wording and moves into the margin in the
        // handwritten face, where a pencilled note belongs. Lines are hand-wrapped: Korge's Text
        // does not wrap.
        val caughtOverlay = container()
        caughtOverlay.solidRect(canvasW, canvasH, resScrim)

        val caughtInk = caughtOverlay.createDossierSheet()
        caughtInk.inkMasthead("SITUATION REPORT")

        // Two columns of fields down the left of the form, the verdict stamped in the space to
        // their right. 137 is half the 0.58 of the page the fields get; the rest is the stamp's.
        val fieldCol = docW / S * 0.29
        val setCaughtStatus = caughtInk.inkField(0.0, 108.0, "OPERATIVE STATUS")
        val setCaughtAlerts = caughtInk.inkField(fieldCol, 108.0, "ALERTS RAISED")
        val setCaughtTime = caughtInk.inkField(0.0, 150.0, "TIME ELAPSED")
        val setCaughtRecord = caughtInk.inkField(fieldCol, 150.0, "MISSION RECORD")
        val setCaughtCoins = caughtInk.inkField(0.0, 192.0, "COINS ON HAND")

        caughtInk.inkStamp(docW / S - 104.0, 160.0, 176.0, 68.0, "MISSION FAILED", stampRed)

        caughtInk.inkRule(236.0, 0.0, docW / S)
        caughtInk.inkText("Recon notes", 16.0, inkFaint, 0.0, 246.0, font = handwrittenFont)
        val caughtTips = listOf(
            "Crouch-walk to eliminate movement noise.",
            "Stay out of guard vision cones and use shipping crates as cover.",
            "Powerups sit on the HUD - one tap spends one."
        )
        for ((i, line) in caughtTips.withIndex()) {
            caughtInk.inkText(line, 14.0, inkBody, 8.0, 268.0 + i * 18.0, font = handwrittenFont)
        }

        // Watch a rewarded ad to continue the same run. Only requests the ad here - the actual
        // restart happens in the update loop below, gated on the bridge reporting the ad was
        // genuinely watched, so a failed/declined ad just leaves the other two strips usable
        // instead of stranding the player. See .junie/guidelines.md "AdMob (basic-ads)
        // feasibility spike" and src/ContinueAdBridge.kt.
        caughtOverlay.createPaperMenuBtn(
            "CONTINUE (WATCH AD)", paperBtnBitmaps[0], resBtnW, resBtnH, resGroupX, resBtnY0,
            iconDrawer = { drawPlayIcon(false) }
        ) {
            getContinueAdBridge().requestContinueAd()
            getAnalyticsBridge().track("watch_ad_continue_requested", mapOf("level_id" to levelData.id))
        }

        caughtOverlay.createPaperMenuBtn(
            "RETRY INFILTRATION", paperBtnBitmaps[1], resBtnW, resBtnH, resGroupX, resBtnY0 + resBtnH + resBtnGap,
            iconDrawer = { drawRestartIcon(paperInk) }
        ) {
            bgMusicChannel?.stop()
            bgMusicChannel = null
            sceneContainer.changeTo { GameplayScene(levelData) }
        }

        caughtOverlay.createPaperMenuBtn(
            "RETURN TO MENU", paperBtnBitmaps[2], resBtnW, resBtnH, resGroupX, resBtnY0 + 2.0 * (resBtnH + resBtnGap),
            iconDrawer = { drawQuitIcon(false) }
        ) {
            bgMusicChannel?.stop()
            bgMusicChannel = null
            getLevelExitBridge().requestReturnToMenu()
            sceneContainer.changeTo { GameplayScene(levelData) }
        }

        caughtOverlay.visible = false

        // ------------------------------------------------------------------
        // HEIST COMPLETE
        // ------------------------------------------------------------------
        // Same form, filled in for a clean run: the three stars struck at the head of the column,
        // a line per objective saying whether it was earned and why not, the verdict stamped
        // beside them, and the purse written out along the foot.
        val winContainer = container()
        winContainer.solidRect(canvasW, canvasH, resScrim)

        val winInk = winContainer.createDossierSheet()
        winInk.inkMasthead("OBJECTIVE REVIEW")

        // Stars struck at the head of the column, the three objectives as fields under them, the
        // verdict stamped alongside with the rating written beneath it.
        val winStarsGraphics = winInk.uiGraphics()
        val winStarsCy = 134.0
        val objCol = docW / S * 0.195
        val setWinStar1 = winInk.inkField(0.0, 178.0, "EXTRACTION")
        val setWinStar2 = winInk.inkField(objCol, 178.0, "UNDETECTED")
        val setWinStar3 = winInk.inkField(objCol * 2.0, 178.0, "FAST (${levelData.timeTargetSeconds.toInt()}S)")

        val winRating = winInk.inkStamp(docW / S - 104.0, 156.0, 176.0, 68.0, "HEIST COMPLETE", stampGreen, hasSubLine = true)

        winInk.inkRule(236.0, 0.0, docW / S)
        winInk.inkText("Bounty banked", 16.0, inkFaint, 0.0, 246.0, font = handwrittenFont)
        val winBountyAmount = winInk.inkText("", 32.0, inkGold, 0.0, 266.0)
        val winBountyCaption = winInk.inkText("", 12.0, inkBody, 0.0, 282.0)
        val winMultiplier = winInk.inkText("", 14.0, inkFaint, 0.0, 300.0, font = handwrittenFont)
        val setWinTime = winInk.inkField(objCol * 2.0, 252.0, "RUN TIME")
        val setWinAlerts = winInk.inkField(objCol * 3.0, 252.0, "ALERTS")
        val setWinRecord = winInk.inkField(objCol * 2.0, 294.0, "OPERATIVE RECORD")

        winContainer.createPaperMenuBtn(
            if (nextLevel != null) "NEXT MISSION" else "ALL CLEAR!",
            paperBtnBitmaps[0], resBtnW, resBtnH, resGroupX, resBtnY0,
            iconDrawer = { drawPlayIcon(false) }
        ) {
            bgMusicChannel?.stop()
            bgMusicChannel = null
            if (nextLevel != null) {
                sceneContainer.changeTo { GameplayScene(nextLevel) }
            } else {
                // Lands on the menu's default screen (MainMenu), not Missions specifically -
                // NavigationRoot remounts fresh every time gameplay hides it, so there is
                // currently no way to tell it which screen to come back to. See
                // getLevelExitBridge()'s doc comment.
                getLevelExitBridge().requestReturnToMenu()
                sceneContainer.changeTo { GameplayScene(levelData) }
            }
        }

        winContainer.createPaperMenuBtn(
            "RETRY", paperBtnBitmaps[1], resBtnW, resBtnH, resGroupX, resBtnY0 + resBtnH + resBtnGap,
            iconDrawer = { drawRestartIcon(paperInk) }
        ) {
            bgMusicChannel?.stop()
            bgMusicChannel = null
            sceneContainer.changeTo { GameplayScene(levelData) }
        }

        winContainer.createPaperMenuBtn(
            "MAIN MENU", paperBtnBitmaps[2], resBtnW, resBtnH, resGroupX, resBtnY0 + 2.0 * (resBtnH + resBtnGap),
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
            // Checked before saveResult() overwrites it - this is how many *distinct* levels have
            // ever been completed, not a per-play counter, used to gate the level-exit
            // interstitial (InterstitialAdLimiter.MIN_LEVELS_COMPLETED) so early levels stay
            // ad-free regardless of how many times this one has been replayed.
            val alreadyCompletedBefore = levelStorage.getBestResult(result.levelId)?.completed == true
            levelStorage.saveResult(result)
            val bestResult = levelStorage.getBestResult(result.levelId) ?: result
            if (!alreadyCompletedBefore) {
                profileStorage.incrementLevelsCompleted()
            }

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

            winStarsGraphics.updateShape {
                val starsEarned = listOf(result.star1, result.star2, result.star3)
                for (i in 0 until 3) {
                    drawStar(
                        cx = dpx(21.0 + i * objCol),
                        cy = dpy(winStarsCy),
                        outerR = 21.0 * S,
                        innerR = 8.5 * S,
                        fillColor = if (starsEarned[i]) inkGold else inkRuleColor
                    )
                }
            }

            val timeTakenStr = secondsText(result.timeTaken)
            fun starRow(setter: (String, RGBA) -> Unit, earned: Boolean, missedText: String) {
                setter(if (earned) "EARNED" else missedText, if (earned) inkStrong else inkFaint)
            }
            starRow(setWinStar1, result.star1, "MISSED")
            starRow(setWinStar2, result.star2, "${world.spottedCount} ALERT(S)")
            starRow(setWinStar3, result.star3, "MISSED - $timeTakenStr")

            winRating.text = "${result.starCount}/3 STARS"
            winRating.xy(-winRating.width / 2.0, 8.0 * S)

            winBountyAmount.text = "+$earnedCoins"
            winBountyCaption.text = "COINS"
            winBountyCaption.xy(dpx(0.0) + winBountyAmount.width + 9.0 * S, dpy(282.0))
            winMultiplier.text = if (multiplier > 1) "2x Shadow Pass applied" else ""

            setWinTime(timeTakenStr, inkStrong)
            setWinAlerts("${world.spottedCount}", if (world.spottedCount == 0) inkStrong else inkFaint)
            setWinRecord(
                "${bestResult.starCount}/3 - ${secondsText(bestResult.timeTaken)}",
                inkStrong
            )
        }

        world.onGameOver = {
            caughtOverlay.visible = true

            val best = levelStorage.getBestResult(levelData.id)
            setCaughtStatus("APPREHENDED", stampRed)
            setCaughtAlerts("${world.spottedCount}", inkStrong)
            setCaughtTime(secondsText(world.timeTaken), inkStrong)
            setCaughtRecord(
                if (best != null) "${best.starCount}/3 - ${secondsText(best.timeTaken)}" else "NO RECORD",
                if (best != null) inkStrong else inkFaint
            )
            setCaughtCoins("${profileStorage.getProfile().coins}", inkGold)

            getAnalyticsBridge().track(
                "mission_failed",
                mapOf("level_id" to levelData.id, "alerts" to world.spottedCount)
            )
        }

        var totalElapsedSeconds = 0.0

        // Tutorial Controller State
        var currentTutorialStep: TutorialStep? = null
        val completedTutorialStepIds = mutableSetOf<String>()
        var tutorialAlpha = 0.0
        var isTutorialFadingIn = false
        var isTutorialFadingOut = false
        var tutorialPulseTimer = 0.0
        var stepActionCompleted = false
        var stepActionTimer = 0.0
        var stepActivatedX = 0.0
        var lastUsedKeyboard = false

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

            // Everything below reads volumes and the powerup inventory off this one snapshot.
            refreshProfile()

            syncBgMusicVolume()

            if (isPaused || world.isLevelComplete || world.isGameOver) {
                if (world.isLevelComplete || world.isGameOver) {
                    tutorialLayer.visible = false
                }
                tutorialDarkOverlay.updateShape { clear() }
                tutorialHighlightGraphics.updateShape { clear() }
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

            // Track whether keyboard or touch was most recently used for adaptive tutorial prompt text
            if (views.input.keys[Key.LEFT] || views.input.keys[Key.RIGHT] || views.input.keys[Key.A] ||
                views.input.keys[Key.D] || views.input.keys[Key.W] || views.input.keys[Key.S] ||
                views.input.keys[Key.SPACE] || views.input.keys[Key.UP] || views.input.keys[Key.DOWN] ||
                views.input.keys[Key.C] || views.input.keys[Key.E] || views.input.keys[Key.F]
            ) {
                lastUsedKeyboard = true
            }
            if (touchLeft || touchRight || touchJump || touchCrouch || touchInteract) {
                lastUsedKeyboard = false
            }

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

            // -----------------------------------------------------------------
            // Tutorial Controller Progression
            // -----------------------------------------------------------------
            if (tutorialSteps.isNotEmpty()) {
                val playerX = world.player.x
                tutorialPulseTimer += dtSec

                // If no active step, check if player has entered a trigger window of an uncompleted milestone
                if (currentTutorialStep == null && !isTutorialFadingOut) {
                    val candidate = tutorialSteps.firstOrNull { step ->
                        step.id !in completedTutorialStepIds &&
                            playerX >= step.triggerMinX &&
                            playerX <= step.triggerMaxX
                    }
                    if (candidate != null) {
                        currentTutorialStep = candidate
                        stepActivatedX = playerX
                        stepActionCompleted = false
                        stepActionTimer = 0.0
                        isTutorialFadingIn = true
                        isTutorialFadingOut = false
                        tutorialLayer.visible = true
                    }
                }

                // If a step is active, evaluate action completion or boundary traversal
                val step = currentTutorialStep
                if (step != null) {
                    if (!stepActionCompleted && !isTutorialFadingOut) {
                        val actionDone = when (step.targetAction) {
                            TutorialAction.MOVE -> (moveInput != 0.0 && playerX >= stepActivatedX + 30.0) || playerX > step.triggerMaxX
                            TutorialAction.JUMP_VAULT -> jumpPressed || world.player.isJumping || world.player.isClimbing || (world.player.y < baseGroundY - 96.0 - 20.0) || playerX > step.triggerMaxX
                            TutorialAction.CROUCH -> crouchPressed || world.player.isCrouching || playerX > step.triggerMaxX
                            TutorialAction.CLIMB -> jumpPressed || world.player.isClimbing || (world.player.y < baseGroundY - 96.0 - 50.0) || playerX > step.triggerMaxX
                            TutorialAction.REACH_OBJECTIVE -> world.player.bounds.intersects(world.exitZone) || playerX >= world.exitZone.x
                        }
                        if (actionDone) {
                            stepActionCompleted = true
                            stepActionTimer = 0.0
                        }
                    }

                    if (stepActionCompleted && !isTutorialFadingOut) {
                        stepActionTimer += dtSec
                        // Brief dwell (~0.4s) after action completion so player perceives the success
                        if (stepActionTimer >= 0.4 || playerX > step.triggerMaxX + 50.0) {
                            completedTutorialStepIds.add(step.id)
                            isTutorialFadingIn = false
                            isTutorialFadingOut = true
                        }
                    }

                    // Handle fade in
                    if (isTutorialFadingIn) {
                        tutorialAlpha = (tutorialAlpha + dtSec / 0.2).coerceAtMost(1.0)
                        if (tutorialAlpha >= 1.0) {
                            isTutorialFadingIn = false
                        }
                    }

                    // Handle fade out
                    if (isTutorialFadingOut) {
                        tutorialAlpha = (tutorialAlpha - dtSec / 0.3).coerceAtLeast(0.0)
                        if (tutorialAlpha <= 0.0) {
                            isTutorialFadingOut = false
                            currentTutorialStep = null
                            tutorialLayer.visible = false
                        }
                    }

                    tutorialLayer.alpha = tutorialAlpha

                    // Render dark overlay dimming the rest of the screen (disabled for world-anchored objective)
                    val highlight = step.highlight
                    val darkAlpha = if (highlight == TutorialControlHighlight.NONE) 0.0 else (0.58 * tutorialAlpha).coerceIn(0.0, 0.70)
                    tutorialDarkOverlay.updateShape {
                        clear()
                        if (darkAlpha > 0.001) {
                            fill(Colors.BLACK.withAd(darkAlpha)) {
                                rect(0.0, 0.0, canvasW, canvasH)
                            }
                        }
                    }

                    // Render highlighted active buttons with full brightness above dark scrim
                    val isBright = tutorialAlpha > 0.001 && !isTutorialFadingOut
                    val showLeft = isBright && (highlight == TutorialControlHighlight.MOVE)
                    val showRight = isBright && (highlight == TutorialControlHighlight.MOVE || highlight == TutorialControlHighlight.MOVE_RIGHT)
                    val showJump = isBright && (highlight == TutorialControlHighlight.JUMP)
                    val showCrouch = isBright && (highlight == TutorialControlHighlight.CROUCH)
                    val showInteract = isBright && (highlight == TutorialControlHighlight.INTERACT)

                    hlLeftImg?.visible = showLeft
                    hlLeftImg?.alpha = tutorialAlpha
                    hlRightImg?.visible = showRight
                    hlRightImg?.alpha = tutorialAlpha
                    hlJumpImg?.visible = showJump
                    hlJumpImg?.alpha = tutorialAlpha
                    hlCrouchImg?.visible = showCrouch
                    hlCrouchImg?.alpha = tutorialAlpha
                    hlInteractImg?.visible = showInteract
                    hlInteractImg?.alpha = tutorialAlpha

                    // Position handwritten callout (world-anchored for objective, screen-centered for controls)
                    val calloutText = step.handwrittenCallout ?: step.title
                    tutorialHandwrittenText.text = calloutText

                    val isWorldAnchored = (highlight == TutorialControlHighlight.NONE)
                    val textX: Double
                    val textY: Double

                    if (isWorldAnchored) {
                        // World-anchored objective callout: fixed to the checkpoint building in game world
                        val worldTextX = 3270.0
                        val worldTextY = 220.0
                        textX = worldTextX * worldZoom + worldView.x
                        textY = worldTextY * worldZoom + worldView.y
                    } else {
                        textX = (canvasW - tutorialHandwrittenText.width) / 2.0
                        textY = 185.0
                    }
                    tutorialHandwrittenText.xy(textX, textY)

                    // Render hand-drawn curved arrow
                    if (tutorialAlpha > 0.001 && !isTutorialFadingOut) {
                        tutorialHighlightGraphics.visible = true
                        tutorialHighlightGraphics.updateShape {
                            clear()

                            when (highlight) {
                                TutorialControlHighlight.MOVE, TutorialControlHighlight.MOVE_RIGHT -> {
                                    if (!isControlsSwapped) {
                                        val startX = textX + 30.0
                                        val startY = textY + 36.0
                                        val targetX = (moveLeftX + moveRightX) / 2.0
                                        val targetY = controlsY - moveRadius - 10.0
                                        val ctrlX = (startX + targetX) / 2.0 - 15.0
                                        val ctrlY = startY + 45.0
                                        drawCurvedArrow(
                                            startX = startX,
                                            startY = startY,
                                            ctrlX = ctrlX,
                                            ctrlY = ctrlY,
                                            endX = targetX,
                                            endY = targetY,
                                            arrowColor = Colors.WHITE.withAd(tutorialAlpha),
                                            thickness = 2.6,
                                            headLength = 16.0
                                        )
                                    } else {
                                        val startX = textX + tutorialHandwrittenText.width - 30.0
                                        val startY = textY + 36.0
                                        val targetX = (moveLeftX + moveRightX) / 2.0
                                        val targetY = controlsY - moveRadius - 10.0
                                        val ctrlX = (startX + targetX) / 2.0 + 15.0
                                        val ctrlY = startY + 45.0
                                        drawCurvedArrow(
                                            startX = startX,
                                            startY = startY,
                                            ctrlX = ctrlX,
                                            ctrlY = ctrlY,
                                            endX = targetX,
                                            endY = targetY,
                                            arrowColor = Colors.WHITE.withAd(tutorialAlpha),
                                            thickness = 2.6,
                                            headLength = 16.0
                                        )
                                    }
                                }
                                TutorialControlHighlight.JUMP -> {
                                    if (!isControlsSwapped) {
                                        val startX = textX + tutorialHandwrittenText.width - 25.0
                                        val startY = textY + 36.0
                                        val targetX = jumpX - jumpRadius - 6.0
                                        val targetY = jumpY - 6.0
                                        val ctrlX = (startX + targetX) / 2.0 - 15.0
                                        val ctrlY = startY + 50.0
                                        drawCurvedArrow(
                                            startX = startX,
                                            startY = startY,
                                            ctrlX = ctrlX,
                                            ctrlY = ctrlY,
                                            endX = targetX,
                                            endY = targetY,
                                            arrowColor = Colors.WHITE.withAd(tutorialAlpha),
                                            thickness = 2.6,
                                            headLength = 16.0
                                        )
                                    } else {
                                        val startX = textX + 25.0
                                        val startY = textY + 36.0
                                        val targetX = jumpX + jumpRadius + 6.0
                                        val targetY = jumpY - 6.0
                                        val ctrlX = (startX + targetX) / 2.0 + 15.0
                                        val ctrlY = startY + 50.0
                                        drawCurvedArrow(
                                            startX = startX,
                                            startY = startY,
                                            ctrlX = ctrlX,
                                            ctrlY = ctrlY,
                                            endX = targetX,
                                            endY = targetY,
                                            arrowColor = Colors.WHITE.withAd(tutorialAlpha),
                                            thickness = 2.6,
                                            headLength = 16.0
                                        )
                                    }
                                }
                                TutorialControlHighlight.CROUCH -> {
                                    if (!isControlsSwapped) {
                                        val startX = textX + tutorialHandwrittenText.width - 20.0
                                        val startY = textY + 36.0
                                        val targetX = crouchX - crouchRadius - 6.0
                                        val targetY = crouchY - 6.0
                                        val ctrlX = (startX + targetX) / 2.0 - 15.0
                                        val ctrlY = startY + 50.0
                                        drawCurvedArrow(
                                            startX = startX,
                                            startY = startY,
                                            ctrlX = ctrlX,
                                            ctrlY = ctrlY,
                                            endX = targetX,
                                            endY = targetY,
                                            arrowColor = Colors.WHITE.withAd(tutorialAlpha),
                                            thickness = 2.6,
                                            headLength = 16.0
                                        )
                                    } else {
                                        val startX = textX + 20.0
                                        val startY = textY + 36.0
                                        val targetX = crouchX + crouchRadius + 6.0
                                        val targetY = crouchY - 6.0
                                        val ctrlX = (startX + targetX) / 2.0 + 15.0
                                        val ctrlY = startY + 50.0
                                        drawCurvedArrow(
                                            startX = startX,
                                            startY = startY,
                                            ctrlX = ctrlX,
                                            ctrlY = ctrlY,
                                            endX = targetX,
                                            endY = targetY,
                                            arrowColor = Colors.WHITE.withAd(tutorialAlpha),
                                            thickness = 2.6,
                                            headLength = 16.0
                                        )
                                    }
                                }
                                TutorialControlHighlight.INTERACT -> {
                                    if (!isControlsSwapped) {
                                        val startX = textX + tutorialHandwrittenText.width - 20.0
                                        val startY = textY + 36.0
                                        val targetX = interactX - interactRadius - 6.0
                                        val targetY = interactY - 6.0
                                        val ctrlX = (startX + targetX) / 2.0 - 15.0
                                        val ctrlY = startY + 50.0
                                        drawCurvedArrow(
                                            startX = startX,
                                            startY = startY,
                                            ctrlX = ctrlX,
                                            ctrlY = ctrlY,
                                            endX = targetX,
                                            endY = targetY,
                                            arrowColor = Colors.WHITE.withAd(tutorialAlpha),
                                            thickness = 2.6,
                                            headLength = 16.0
                                        )
                                    } else {
                                        val startX = textX + 20.0
                                        val startY = textY + 36.0
                                        val targetX = interactX + interactRadius + 6.0
                                        val targetY = interactY - 6.0
                                        val ctrlX = (startX + targetX) / 2.0 + 15.0
                                        val ctrlY = startY + 50.0
                                        drawCurvedArrow(
                                            startX = startX,
                                            startY = startY,
                                            ctrlX = ctrlX,
                                            ctrlY = ctrlY,
                                            endX = targetX,
                                            endY = targetY,
                                            arrowColor = Colors.WHITE.withAd(tutorialAlpha),
                                            thickness = 2.6,
                                            headLength = 16.0
                                        )
                                    }
                                }
                                TutorialControlHighlight.NONE -> {
                                    val worldTargetX = 3425.0
                                    val worldTargetY = 320.0
                                    val targetScreenX = worldTargetX * worldZoom + worldView.x
                                    val targetScreenY = worldTargetY * worldZoom + worldView.y

                                    val startX = textX + tutorialHandwrittenText.width * 0.75
                                    val startY = textY + 28.0
                                    val ctrlX = (startX + targetScreenX) / 2.0 + 20.0
                                    val ctrlY = (startY + targetScreenY) / 2.0 - 15.0
                                    drawCurvedArrow(
                                        startX = startX,
                                        startY = startY,
                                        ctrlX = ctrlX,
                                        ctrlY = ctrlY,
                                        endX = targetScreenX,
                                        endY = targetScreenY,
                                        arrowColor = Colors.WHITE.withAd(tutorialAlpha),
                                        thickness = 2.6,
                                        headLength = 16.0
                                    )
                                }
                            }
                        }
                    } else {
                        tutorialHighlightGraphics.updateShape { clear() }
                    }
                }
            } else {
                if (tutorialLayer.visible) {
                    tutorialLayer.visible = false
                    tutorialLayer.alpha = 0.0
                }
                hlLeftImg?.visible = false
                hlRightImg?.visible = false
                hlJumpImg?.visible = false
                hlCrouchImg?.visible = false
                hlInteractImg?.visible = false
                tutorialDarkOverlay.updateShape { clear() }
                tutorialHighlightGraphics.updateShape { clear() }
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
            for (i in world.movingPlatforms.indices) {
                val mp = world.movingPlatforms[i]
                movingPlatformContainers[i].xy(mp.x, mp.y)
            }

            // Camera: Center player on zoomed gameplay worldView, clamped to level bounds
            val currentCanvasW = sceneWidth.toDouble().coerceAtLeast(800.0)
            val currentCanvasH = sceneHeight.toDouble().coerceAtLeast(480.0)
            val halfScreen = currentCanvasW / 2.0
            val playerCenterX = world.player.x + world.player.width / 2.0
            val desiredWorldViewX = halfScreen - playerCenterX * worldZoom
            val minWorldViewX = currentCanvasW - world.worldWidth * worldZoom
            val targetWorldViewX = desiredWorldViewX.coerceIn(minWorldViewX.coerceAtMost(0.0), 0.0)
            if (isFirstCameraFrame) {
                worldView.x = targetWorldViewX
                isFirstCameraFrame = false
            } else {
                val camFactor = (1.0 - kotlin.math.exp(-16.0 * dtSec)).coerceIn(0.0, 1.0)
                worldView.x += (targetWorldViewX - worldView.x) * camFactor
            }
            val baseWorldViewY = currentCanvasH - (baseGroundY + 70.0) * worldZoom
            worldView.y = baseWorldViewY

            // Cull static decor outside the camera window. Runs after worldView.x settles for this
            // frame so the test uses the position actually about to be drawn. The margin is a full
            // half-screen rather than something tight - the saving is in not submitting the far end
            // of a 3900-unit level, not in trimming the last few units at the edge, and a generous
            // margin means nothing can pop in at the boundary.
            val cullLeft = -worldView.x / worldZoom - currentCanvasW / (2.0 * worldZoom)
            val cullRight = cullLeft + currentCanvasW / worldZoom + currentCanvasW / worldZoom
            for (t in cullTargets) {
                t.view.visible = t.right >= cullLeft && t.left <= cullRight
            }

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

            if (world.player.isMoving) {
                stationaryElapsed = 0.0
                crouchStationaryElapsed = 0.0
            } else {
                stationaryElapsed += dtSec
                crouchStationaryElapsed += dtSec
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

                // Jump / Airborne animation machine
                if (playerAnimState != "jump" && !world.player.isGrounded) {
                    playerAnimState = "jump"
                    landingAbsorb = false  // cancel any in-progress absorption
                    jumpStartY = world.player.y
                    jumpPhaseElapsed = 0.0
                    playerSprite.playAnimationLooped(playerAnimations.jump, manualFrameTime)
                    // If moving upward, it's an intentional jump; if falling downwards, it's stepping/falling off a ledge
                    jumpPhase = if (world.player.vy < 0.0) "launch" else "drop"
                } else if (playerAnimState == "jump") {
                    jumpPhaseElapsed += dtSec
                    when (jumpPhase) {
                        "launch" -> if (jumpPhaseElapsed >= jumpLaunchDuration || world.player.vy >= 0.0) {
                            jumpPhase = "air"
                            jumpPhaseElapsed = 0.0
                        }
                        // The "land" phase plays the recovery clip through to a standing pose -
                        // right if the player is stopped, but wrong if they're still holding a
                        // direction: world x keeps advancing on physics regardless of animation
                        // phase, so riding out the standing-recovery frames while already moving
                        // reads as gliding forward in a standing pose for those 0.24s before the
                        // walk cut-over. Moving into the touchdown skips straight past it instead.
                        "air", "drop" -> if (world.player.isGrounded) {
                            sounds.impact.playSfx(sfxContext, GameAudio.LANDING_GAIN, sfxVolume())
                            if (world.player.isMoving) {
                                // Cushion the landing impact before transitioning into the forward walk stride.
                                jumpPhase = "none"
                                playerAnimState = "none"
                                landingAbsorb = true
                                landingAbsorbElapsed = 0.0
                            } else {
                                jumpPhase = "land"
                                jumpPhaseElapsed = 0.0
                            }
                        }
                        "land" -> if (world.player.isMoving) {
                            // Started moving during landing recovery: transition smoothly into forward stride
                            jumpPhase = "none"
                            playerAnimState = "walk"
                            walkInTransition = true
                            walkTransitionStartFrame = 5
                            val framesRemaining = PlayerAnimations.WALK_TRANSITION_END - walkTransitionStartFrame
                            val totalFrames = PlayerAnimations.WALK_TRANSITION_END - PlayerAnimations.WALK_TRANSITION_START
                            walkTransitionCurrentDuration = walkTransitionDuration * (framesRemaining.toDouble() / totalFrames)
                            walkTransitionElapsed = 0.0
                            walkCycleProgress = 0.0
                            playerSprite.playAnimationLooped(playerAnimations.walk, manualFrameTime)
                            playerSprite.setFrame(walkTransitionStartFrame)
                        } else if (jumpPhaseElapsed >= jumpLandDuration) {
                            jumpPhase = "none"
                            playerAnimState = "none"
                        }
                        else -> if (!world.player.isGrounded) {
                            jumpPhase = if (world.player.vy < 0.0) "launch" else "drop"
                            jumpPhaseElapsed = 0.0
                            jumpStartY = world.player.y
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
                            playerSprite.playAnimationLooped(playerAnimations.crouchwalk, manualFrameTime)
                            // Only restart the full 91-frame transition if starting from a sustained still crouch
                            if (crouchStationaryElapsed >= 0.20) {
                                crouchwalkInTransition = true
                                crouchwalkTransitionProgress = 0.0
                                crouchwalkCycleProgress = 0.0
                            } else {
                                crouchwalkInTransition = false
                            }
                        } else if (playerAnimState == "crouchwalk" && !world.player.isMoving && crouchStationaryElapsed >= 0.10) {
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
                            if (t >= 1.0) crouchwalkInTransition = false
                        } else {
                            if (world.player.isMoving) {
                                crouchwalkCycleProgress = (crouchwalkCycleProgress + cyclesMoved) % 1.0
                            }
                            val loopLength = PlayerAnimations.CROUCHWALK_LOOP_LENGTH
                            playerSprite.setFrame(
                                PlayerAnimations.CROUCHWALK_LOOP_START +
                                    (crouchwalkCycleProgress * loopLength).toInt().coerceIn(0, loopLength - 1)
                            )
                        }
                    }
                }
            }

            // Landing absorption: plays a brief cushion from the jump's touchdown frames
            // before handing off into the forward walk stride.
            if (landingAbsorb) {
                landingAbsorbElapsed += dtSec
                val t = (landingAbsorbElapsed / landingAbsorbDuration).coerceIn(0.0, 1.0)
                if (playerAnimState != "landAbsorb") {
                    playerAnimState = "landAbsorb"
                    playerSprite.playAnimationLooped(playerAnimations.jump, manualFrameTime)
                }
                val absorbFrame = jumpLandFrame + (t * (landingAbsorbFrames - 1)).toInt()
                playerSprite.setFrame(absorbFrame.coerceIn(jumpLandFrame, jumpLandFrame + landingAbsorbFrames - 1))

                if (t >= 1.0) {
                    landingAbsorb = false
                    if (world.player.isMoving) {
                        playerAnimState = "walk"
                        walkInTransition = true
                        walkTransitionStartFrame = 5
                        val framesRemaining = PlayerAnimations.WALK_TRANSITION_END - walkTransitionStartFrame
                        val totalFrames = PlayerAnimations.WALK_TRANSITION_END - PlayerAnimations.WALK_TRANSITION_START
                        walkTransitionCurrentDuration = walkTransitionDuration * (framesRemaining.toDouble() / totalFrames)
                        walkTransitionElapsed = 0.0
                        walkCycleProgress = 0.0
                        playerSprite.playAnimationLooped(playerAnimations.walk, manualFrameTime)
                        playerSprite.setFrame(walkTransitionStartFrame)
                    } else {
                        jumpPhase = "land"
                        jumpPhaseElapsed = landingAbsorbElapsed
                        playerAnimState = "jump"
                        playerSprite.playAnimationLooped(playerAnimations.jump, manualFrameTime)
                    }
                }
            }

            if (!landingAbsorb && playerAnimState != "jump" && playerAnimState != "crouch"
                && playerAnimState != "crouchwalk" && playerAnimState != "climb" && playerAnimState != "landAbsorb") {
                val wantsWalk = world.player.isMoving
                if (wantsWalk) {
                    if (playerAnimState != "walk") {
                        playerAnimState = "walk"
                        playerSprite.playAnimationLooped(playerAnimations.walk, manualFrameTime)
                        // Only play lean-in transition if starting from a sustained stationary stop
                        if (stationaryElapsed >= 0.15) {
                            walkCycleProgress = 0.0
                            walkInTransition = true
                            walkTransitionStartFrame = PlayerAnimations.WALK_TRANSITION_START
                            walkTransitionCurrentDuration = walkTransitionDuration
                            walkTransitionElapsed = 0.0
                            playerSprite.setFrame(PlayerAnimations.WALK_TRANSITION_START)
                            val step = if (stepAlternate) sounds.stepB else sounds.stepA
                            stepAlternate = !stepAlternate
                            step.playSfx(sfxContext, GameAudio.STEP_GAIN, sfxVolume())
                        } else {
                            walkInTransition = false
                        }
                    }
                } else {
                    // Only transition to idle if stationary for at least 0.10s (prevents direction reversal stutter)
                    if (playerAnimState != "idle" && stationaryElapsed >= 0.10) {
                        playerAnimState = "idle"
                        walkInTransition = false
                        playerSprite.playAnimationLooped(playerAnimations.idle, PlayerAnimations.IDLE_FRAME_TIME_MS.milliseconds)
                    }
                }
            }

            val climbFeetOffset = if (playerAnimState == "climb") {
                val phase = world.player.climbPhase
                if (phase >= 0.60) {
                    val t = ((phase - 0.60) / 0.25).coerceIn(0.0, 1.0)
                    t * idleFeetOffset
                } else {
                    0.0
                }
            } else {
                0.0
            }

            playerSprite.y = world.player.height + when {
                playerAnimState == "idle" -> idleFeetOffset
                // Only the held/entering/exiting stance, not crouchwalk - the walk cycle's
                // alternating planted/swinging foot is supposed to look uneven, this offset is
                // only for the settled two-feet-down pose.
                playerAnimState == "crouch" -> crouchFeetOffset
                playerAnimState == "climb" -> climbFeetOffset
                else -> 0.0
            }
            if (playerAnimState == "jump") {
                val maxJumpHeight =
                    (world.player.jumpSpeed * world.player.jumpSpeed) / (2.0 * world.player.gravity)

                val frameIndex = when (jumpPhase) {
                    "launch" -> {
                        val t = (jumpPhaseElapsed / jumpLaunchDuration).coerceIn(0.0, 1.0)
                        jumpLaunchFrame + (t * (jumpAirborneFrame - jumpLaunchFrame)).toInt()
                    }
                    "air" -> if (world.player.vy < 0.0) {
                        // Rising: tuck legs towards apex as height increases
                        val altitudeProgress = ((jumpStartY - world.player.y) / maxJumpHeight).coerceIn(0.0, 1.0)
                        jumpAirborneFrame + (altitudeProgress * (jumpApexFrame - jumpAirborneFrame)).toInt()
                    } else {
                        // Falling: smoothly uncurl from apex to extended legs based on fall speed
                        val fallProgress = (world.player.vy / (world.player.maxFallSpeed * 0.7)).coerceIn(0.0, 1.0)
                        jumpApexFrame + (fallProgress * (jumpTouchdownFrame - jumpApexFrame)).toInt()
                    }
                    "drop" -> {
                        // Stepping/dropping off a ledge: keep legs extended downward rather than tucking knees up
                        val dropFrameStart = 22
                        val fallProgress = (world.player.vy / (world.player.maxFallSpeed * 0.7)).coerceIn(0.0, 1.0)
                        dropFrameStart + (fallProgress * (jumpTouchdownFrame - dropFrameStart)).toInt()
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
            } else if (playerAnimState == "walk") {
                if (walkInTransition) {
                    walkTransitionElapsed += dtSec
                    val t = (walkTransitionElapsed / walkTransitionCurrentDuration).coerceIn(0.0, 1.0)
                    val span = PlayerAnimations.WALK_TRANSITION_END - walkTransitionStartFrame
                    val currentFrame = (walkTransitionStartFrame + (t * span).toInt())
                        .coerceIn(walkTransitionStartFrame, PlayerAnimations.WALK_TRANSITION_END)
                    playerSprite.setFrame(currentFrame)
                    // The transition runs straight into the loop's first frame in the source
                    // footage, so handing over at the end is seamless.
                    if (t >= 1.0) {
                        walkInTransition = false
                        walkCycleProgress = 0.0
                    }
                } else {
                    val previousPhase = walkCycleProgress
                    if (world.player.isMoving) {
                        walkCycleProgress =
                            (walkCycleProgress + abs(world.player.vx) * dtSec / walkCycleDistance) % 1.0
                    }
                    val loopLength = PlayerAnimations.WALK_LOOP_LENGTH
                    playerSprite.setFrame(
                        PlayerAnimations.WALK_LOOP_START +
                            (walkCycleProgress * loopLength).toInt().coerceIn(0, loopLength - 1)
                    )

                    // A footstep for each contact phase the cycle passed this tick. Written as a
                    // crossing test rather than "is the phase near X" so it still fires exactly
                    // once at low frame rates or high speed, and survives the wrap at 1.0.
                    if (world.player.isMoving) {
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
            val currentProfile = cachedProfile
            var hasAnyVisiblePowerup = false

            for (btn in powerupHudButtons) {
                val count = currentProfile.getPowerupCount(btn.type)
                val isActive = world.activePowerups.isActive(btn.type)
                val remTime = world.activePowerups.getRemainingTime(btn.type)

                if (count > 0 || isActive) {
                    hasAnyVisiblePowerup = true
                    btn.btnContainer.visible = true
                    val accent = if (isActive) COLOR_BORDER_GREEN else COLOR_ACCENT_CYAN
                    // Live powerups get a filled underline that drains with their timer, so the
                    // chip carries the countdown instead of a separate status readout. -1.0 marks
                    // the inactive chip, whose shape never varies.
                    val span = when {
                        !isActive -> -1.0
                        btn.type.isLevelDuration -> 1.0
                        else -> (remTime / btn.type.duration).coerceIn(0.0, 1.0)
                    }
                    // Only a change in that fraction changes a single pixel of this chip, and
                    // updateShape re-tessellates the whole thing, so an unchanged chip is skipped.
                    if (btn.lastDrawnSpan != span) {
                        btn.lastDrawnSpan = span
                        btn.bg.updateShape {
                            clear()
                            val fillCol = if (isActive) accent.withAd(0.22) else Colors["#0A0C10"].withAd(0.55)
                            fill(fillCol) { roundRect(0.0, 0.0, powerupBtnW, powerupBtnH, powerupBtnRadius, powerupBtnRadius) }
                            stroke(accent.withAd(if (isActive) 0.95 else 0.45), StrokeInfo(thickness = if (isActive) 2.0 else 1.6)) {
                                roundRect(0.5, 0.5, powerupBtnW - 1.0, powerupBtnH - 1.0, powerupBtnRadius, powerupBtnRadius)
                            }
                            if (isActive) {
                                fill(accent) {
                                    roundRect(10.0, powerupBtnH - 7.0, (powerupBtnW - 20.0) * span, 3.0, 1.5, 1.5)
                                }
                            }
                        }
                    }
                    // Same idea as the shape above, for the label: rebuild it only when one of the
                    // three things it is made of actually changes.
                    val tenths = if (isActive && !btn.type.isLevelDuration) (remTime * 10).toInt() else -1
                    if (btn.lastActive != isActive || btn.lastCount != count || btn.lastTenths != tenths) {
                        btn.lastActive = isActive
                        btn.lastCount = count
                        btn.lastTenths = tenths
                        if (isActive) {
                            btn.countText.text = if (btn.type.isLevelDuration) "ON" else "${tenths / 10.0}s"
                            btn.countText.color = COLOR_BORDER_GREEN
                            btn.nameText.color = COLOR_TEXT_LIGHT
                        } else {
                            btn.countText.text = "x$count"
                            btn.countText.color = COLOR_BORDER_GOLD
                            btn.nameText.color = COLOR_TEXT_MUTED
                        }
                        btn.countText.xy((powerupBtnW - btn.countText.width) / 2.0, 22.0)
                    }
                } else {
                    btn.btnContainer.visible = false
                }
                // The re-centre that used to sit here ran for every chip every frame, hidden ones
                // included, and each call measured the text's bounds. It only ever has an effect
                // when the label changes, so it now lives inside the guard above.
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
