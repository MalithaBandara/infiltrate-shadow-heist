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
import game.scene.UiComponents.createToast
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
    val levelData: LevelData = LevelData.DEFAULT_LEVEL_1,
    // True only for the fresh instance QUIT/RETURN TO MENU reloads in the background so a later
    // "start level" gets a clean state - see the class doc comment on KorGE never being hidden
    // behind the Compose menu. That instance sits idle forever behind the menu (nothing ever
    // reactivates it; the next real play creates yet another GameplayScene), so it must never
    // start bgmusic - real bug, reported as "menu music" playing after quitting to the main menu.
    private val startDormant: Boolean = false
) : Scene() {

    private var bgMusicChannel: SoundChannel? = null

    /** Stops whichever of korlibs' channel / the native mixer's music voice is actually active. */
    private fun stopBgMusic() {
        try {
            bgMusicChannel?.stop()
        } catch (_: Throwable) {}
        bgMusicChannel = null
        GameAudio.stopNativeMusic()
    }

    override suspend fun sceneDestroy() {
        super.sceneDestroy()
        stopBgMusic()
    }

    override suspend fun SContainer.sceneMain() {
        val canvasW = sceneWidth.toDouble().coerceAtLeast(800.0)
        val canvasH = sceneHeight.toDouble().coerceAtLeast(480.0)

        // --- Loading screen -------------------------------------------------------------
        val loadingBgBitmap = SceneAssets.bitmap("loadingbg.png", minified = false)
        val loadingLogoBitmap = SceneAssets.bitmap("logo_main.png", minified = false)
        val loadingBarTextureBitmap = SceneAssets.bitmap("button1.png", minified = false)
        val loadingFont = SceneAssets.font("BebasNeue-Regular.ttf")

        val loadingScreen = setupLoadingScreen(
            canvasW, canvasH, loadingBgBitmap, loadingLogoBitmap, loadingBarTextureBitmap, loadingFont
        )

        // One full frame so the loading screen is actually painted before the loads below
        delayFrame()

        val totalLoadSteps = 24
        var loadStepsDone = 0
        suspend fun markLoadProgress() {
            loadStepsDone++
            loadingScreen.setProgress(loadStepsDone.toDouble() / totalLoadSteps)
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
            loadingScreen.dismiss()
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
        val bgmgBitmap = SceneAssets.bitmap(bgFileName, minified = false)
        markLoadProgress()
        val crateBitmap = SceneAssets.bitmap("crate.png")
        markLoadProgress()
        val chainedCrateBitmap = SceneAssets.bitmap("chainedcrate.png", minified = false)
        markLoadProgress()
        val chainedCrate2Bitmap = SceneAssets.bitmap("chainedcrate2.png", minified = false)
        markLoadProgress()
        val fenceBitmap = SceneAssets.bitmap("fence.png")
        markLoadProgress()
        val fence2Bitmap = SceneAssets.bitmap("fence2.png")
        markLoadProgress()
        val barrelBitmap = SceneAssets.bitmap("barrel.png")
        markLoadProgress()
        val hookBitmap = SceneAssets.bitmap("hook.png")
        markLoadProgress()
        val truckBitmap = SceneAssets.bitmap("truck.png")
        markLoadProgress()
        val entranceBitmap = SceneAssets.bitmap("entrance.png", minified = false)
        markLoadProgress()
        val exitFenceBitmap = SceneAssets.bitmap("exitfence.png", minified = false)
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
            .map { name -> SceneAssets.bitmap(name, minified = false) }
        markLoadProgress()
        // The main menu's briefing sheet (MainMenuScreen.MissionDossierCard). Checked in twice
        // for the same reason the button strips are - Korge reads resources/, the Compose menu
        // reads its own composeResources/ copy, and the two builds share no asset pipeline.
        val dossierBitmap = SceneAssets.bitmap("dossier_paper.png", minified = false)
        markLoadProgress()
        // The store's own gadget art, reused at HUD size, so an item looks in the quick-slot
        // exactly like it looked on the card that sold it. Order matches `gadgetTypes` below.
        val gadgetBitmaps = listOf(
            "gadget_jammer.png", "gadget_darts.png", "gadget_invis.png",
            "gadget_boots.png", "gadget_prototype.png"
        ).map { SceneAssets.bitmap(it) }
        markLoadProgress()
        // The quick-slot's own "gadgets are here" mark before it's tapped open. Replaces the
        // earlier hand-drawn vector polygon with real bolt art - tight-cropped from
        // Downloads/charAnimations/assets/lighting.png to its alpha bounds (102x235) then
        // resized to 32x64 POT. White source art, tinted at draw time via colorMul exactly like
        // the paper-strip buttons already are, so it still goes white/green with gadget state.
        val gadgetBoltBitmap = SceneAssets.bitmap("gadget_bolt.png")
        markLoadProgress()
        // The MISSION SUCCESSFUL card: a desk of case photos with the header already printed on
        // the sheet, so the results screen draws no title of its own and only fills the blank
        // paper under it, and the three painted gold stars it awards, which ship as one strip.
        val successBgBitmap = SceneAssets.bitmap("success3.png", minified = false)
        markLoadProgress()
        val starsBitmap = SceneAssets.bitmap("stars.png", minified = false)
        // Column runs measured off the strip's alpha channel - the three stars are hand-painted
        // and none of them is the same width as its neighbours, so they are cut individually
        // rather than into equal thirds, which would clip one and off-centre another.
        val starSlices = starsBitmap?.let {
            listOf(
                it.sliceWithSize(69, 33, 636, 611),
                it.sliceWithSize(760, 33, 647, 611),
                it.sliceWithSize(1464, 33, 641, 611)
            )
        }
        markLoadProgress()

        loadingScreen.dismiss()

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
            // Pinned to entrance.png's authored 531x612 rather than read off the loaded bitmap.
            // Same value, but the number no longer moves if the file is ever resampled - it used
            // to be `entranceBitmap.width / entranceBitmap.height`, which quietly made the stored
            // aspect load-bearing: the exit fence below is positioned at `exitZone.x +
            // entranceWidth`, so re-encoding this asset would have shifted the extraction point.
            val entranceWidth = entranceHeight * (531.0 / 612.0)
            val exitGroundY = world.exitZone.bottom + 1.0
            val entranceY = exitGroundY - entranceHeight
            cullable(
                worldView.image(entranceBitmap) {
                    size(entranceWidth, entranceHeight)
                }.xy(world.exitZone.x, entranceY),
                world.exitZone.x, entranceWidth
            )
            if (exitFenceBitmap != null) {
                val exitFenceHeight = 140.0
                // Pinned to exitfence.png's authored 1039x466, for the reason given just above.
                val exitFenceWidth = exitFenceHeight * (1039.0 / 466.0)
                val exitFenceY = exitGroundY - exitFenceHeight
                cullable(
                    worldView.image(exitFenceBitmap) {
                        size(exitFenceWidth, exitFenceHeight)
                    }.xy(world.exitZone.x + entranceWidth, exitFenceY),
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
            // 3. Barrels (jump on, walk across, jump off / rescue climb points). A barrel wall
            // taller than one barrel (e.g. level 3's stacked climb obstacle) is a single tall
            // collision/climb box under the hood - see LEVEL_3_LAYOUT's comment - so it's tiled
            // here in real barrel-height (48) increments from the ground up instead of being
            // stretched into one distorted image, the same tiling approach used for the hanging
            // crates' chain above.
            else if (box in world.barrels && barrelBitmap != null) {
                val barrelTileHeight = 48.0
                if (box.height <= barrelTileHeight + 0.01) {
                    boxContainer.image(barrelBitmap) {
                        size(box.width, box.height)
                    }.xy(0.0, 0.0)
                } else {
                    var remaining = box.height
                    var tileY = box.height
                    while (remaining > 0.0) {
                        val h = minOf(barrelTileHeight, remaining)
                        tileY -= h
                        boxContainer.image(barrelBitmap) {
                            size(box.width, h)
                        }.xy(0.0, tileY)
                        remaining -= h
                    }
                }
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

        // Chain-and-hooks dangling from off-screen above. Both the decorative ones and the ones
        // the player can swing from draw identically and on purpose - a usable hook is recognised
        // by where it hangs, the same way a climbable box is recognised by its height, not by a
        // marker. Neither has a collision box. hook.png is one tall image scaled to each Rect's
        // own width/height rather than tiled, since there's no crate at the bottom needing a fit.
        if (hookBitmap != null) {
            val allHooks = levelData.layout?.let { it.hangingHooks + it.swingHooks }.orEmpty()
            for (hook in allHooks) {
                cullable(
                    worldView.image(hookBitmap) {
                        size(hook.width, hook.height)
                    }.xy(hook.x, hook.y),
                    hook.x, hook.width
                )
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
        var dropFromWalk = false
        var climbExitTimer = 0.0
        var swingExitTimer = 0.0
        var swingImpactSoundPlayed = false

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

        // -1.0 is a sentinel, not a real volume: it means "nothing applied yet", distinct from a
        // legitimate 0.0 (muted). See syncBgMusicVolume's own doc comment for why this exists.
        var bgMusicAppliedVolume = -1.0

        /**
         * Tries [GameAudio.startNativeMusic] first - Android's software mixer, one continuous
         * stream for the whole process (see GameSfxOutput's own doc comment for why that
         * replaced korlibs' bgMusicChannel entirely on that platform). Every other platform, or
         * Android if the native engine failed to start, falls through to korlibs' own
         * `playForever` exactly as before.
         *
         * Either way this was an unconditional `channel.volume = effectiveVol` every single frame
         * (up to 120/sec on this project's uncapped render loop) - a real volume-set call
         * regardless of whether the value had actually changed, which read as intermittent static
         * independent of which SFX backend gameplay one-shots used. Two real fixes, applied to
         * both paths: skip the call outright when nothing changed (below the RAMP_PER_SEC step
         * size), and ramp toward a changed target over [GameAudio.BG_MUSIC_VOLUME_RAMP_PER_SEC]
         * rather than stepping it instantly - an un-ramped gain jump is a textbook click (a
         * waveform discontinuity), most reproducible on pause's baseVol -> baseVol*0.35 step.
         */
        fun syncBgMusicVolume(dtSec: Double = 0.0) {
            if (startDormant) return
            val baseVol = GameAudio.BG_MUSIC_GAIN * musicVolume().toDouble()
            val targetVol = (if (isPaused || world.isGameOver || world.isLevelComplete) {
                baseVol * 0.35
            } else {
                baseVol
            }).coerceIn(0.0, 1.0)

            fun rampedVolume(): Double {
                val step = GameAudio.BG_MUSIC_VOLUME_RAMP_PER_SEC * dtSec
                return when {
                    bgMusicAppliedVolume < 0.0 -> targetVol
                    bgMusicAppliedVolume < targetVol -> min(targetVol, bgMusicAppliedVolume + step)
                    bgMusicAppliedVolume > targetVol -> max(targetVol, bgMusicAppliedVolume - step)
                    else -> bgMusicAppliedVolume
                }
            }

            if (GameAudio.startNativeMusic()) {
                val next = rampedVolume()
                if (abs(next - bgMusicAppliedVolume) <= 0.0005) return
                GameAudio.setNativeMusicVolume(next.toFloat())
                bgMusicAppliedVolume = next
                return
            }

            val channel = bgMusicChannel
            if (channel == null) {
                if (targetVol > 0.001 && sounds.bgMusic != null) {
                    try {
                        bgMusicChannel = sounds.bgMusic.playForever(coroutineContext).also {
                            it.volume = targetVol
                        }
                        bgMusicAppliedVolume = targetVol
                    } catch (_: Throwable) {}
                }
            } else {
                val next = rampedVolume()
                if (abs(next - bgMusicAppliedVolume) <= 0.0005) return
                try {
                    channel.volume = next
                    bgMusicAppliedVolume = next
                } catch (_: Throwable) {}
            }
        }
        syncBgMusicVolume()

        // One click for every pressable thing in the scene. Deliberate presses (pause, the
        // pause-menu strips, the Mission Failed buttons) use the full weight; the on-screen
        // D-pad uses the quiet one, because it fires on every movement input and would otherwise
        // become the loudest recurring sound in a level.
        val playClick = { gain: Double -> sounds.uiClick.playSfx(sfxContext, gain, sfxVolume(), GameAudio.SfxFile.UI_CLICK) }

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

        // Same contract as the climb's: Player.swingPhase picks the frame here, so the pose and
        // the position it was measured from stay in step.
        val swingFrameSpan = PlayerAnimations.SWING_END - PlayerAnimations.SWING_START

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

        // --- Objectives panel: what the run is for, and how it is going --------------------
        // One block, up for the whole run. It replaces the pair this used to be - a mission toast
        // that named the level and dissolved, then a one-line objective strip that took over its
        // corner - because two things that say the same thing at different times is one thing too
        // many, and neither of them ever said whether the bonus was still alive.
        //
        // The markers are the point. An objective still in play is an open circle, one that has
        // been met is ticked, and one that can no longer be met is crossed - so a player who has
        // just blown the time bonus is told, rather than finding out on the results card.
        //
        // The optional line is the TIME target, not the no-detection one, for a reason worth
        // recording: being detected ends the run outright (GameWorld sets isGameOver in the same
        // breath as wasDetected), so a no-detection row could never actually show the cross - it
        // would be an open circle for every frame the player is ever alive to see it. The clock
        // is the only optional objective in this game with a live failure state.
        // No chrome behind or beside any of it: the block is white type and white marks straight
        // onto the level, and the whole thing sits flush at the 24px HUD inset now that the rule
        // that used to occupy that gutter is gone. Worth knowing what that costs - the sky in
        // these levels is bright and the ground is black, light type has to survive both, and
        // there is nothing left to separate it from either.
        val objPanel = hudLayer.container().xy(24.0, 20.0)

        val objTitle = objPanel.text(
            "OBJECTIVES", textSize = 15.0, font = bebasFont, color = COLOR_PRIMARY
        )
        objTitle.graphicsRenderer = GraphicsRenderer.GPU
        objTitle.xy(0.0, 4.0)

        // Sized up from 11/9.5: the rows are still smaller than the heading, which is what the
        // hierarchy is for, but 9.5px of condensed type at this canvas scale was legible in a
        // still and not in motion. Row pitch, the marks and their rings all move with it - a
        // bigger row on the old 13px pitch would have closed the gap between the two lines.
        val objMarkX = 7.0
        val objTextX = 21.0
        val objRow1Y = 22.0
        val objRow2Y = 37.0
        val objMarkR = 5.4

        val objMainText = objPanel.text(
            levelData.objectiveHint.uppercase(), textSize = 12.5, font = bebasFont, color = COLOR_TEXT_LIGHT
        )
        objMainText.graphicsRenderer = GraphicsRenderer.GPU
        objMainText.xy(objTextX, objRow1Y)

        // "(OPTIONAL)" stays its own view, in the same ink as the objective beside it. It is
        // still a separate view rather than one string because the gap after it is set from its
        // measured width, and because the qualifier may yet want its own treatment.
        val objOptTag = objPanel.text(
            "(OPTIONAL)", textSize = 12.5, font = bebasFont, color = COLOR_TEXT_LIGHT
        )
        objOptTag.graphicsRenderer = GraphicsRenderer.GPU
        objOptTag.xy(objTextX, objRow2Y)

        val objOptText = objPanel.text(
            "FINISH UNDER ${clockText(levelData.timeTargetSeconds)}",
            textSize = 12.5, font = bebasFont, color = COLOR_TEXT_LIGHT
        )
        objOptText.graphicsRenderer = GraphicsRenderer.GPU
        objOptText.xy(objTextX + objOptTag.width + 4.0, objRow2Y)

        // Ring and mark are drawn together in one layer per row. All three states are white, so
        // the shape inside the ring is the only thing carrying the state - an empty ring is still
        // in play, a tick is met, a cross is gone.
        //
        // Every state draws at FULL white. An earlier version dimmed the open ring to half alpha
        // to mark it as unresolved, which multiplied with the panel's own 0.88 into a 1.3px line
        // at 0.44 over a bright sky - arithmetically present, visually absent. Nothing here can
        // afford to be subtle any more: with the plate gone these marks are competing with
        // whatever the level happens to put behind them.
        val objMainMark = objPanel.uiGraphics().xy(objMarkX, objRow1Y + 5.2)
        val objOptMark = objPanel.uiGraphics().xy(objMarkX, objRow2Y + 5.2)

        // 0 open, 1 met, 2 out of reach. Held so the shapes are rebuilt only when a marker
        // actually changes rather than on every frame, the same guard the powerup dock uses.
        var objMainState = 0
        var objOptState = 0
        fun setObjMark(mark: Graphics, state: Int) {
            val color = COLOR_PRIMARY
            mark.updateShape {
                clear()
                stroke(color, StrokeInfo(thickness = 1.9)) { circle(Point(0.0, 0.0), objMarkR) }
                when (state) {
                    1 -> drawTickIcon(objMarkR * 0.78, color)
                    2 -> drawCrossIcon(objMarkR * 0.78, color)
                }
            }
        }
        setObjMark(objMainMark, 0)
        setObjMark(objOptMark, 0)

        objPanel.alpha = 0.0
        val objPanelAlpha = 1.0
        val objPanelFadeSeconds = 0.5

        // --- Pause: two bars, no button around them ---------------------------------------
        // Top-right corner, opposite the objectives block, and stripped of its glass disc for the
        // same reason that block lost its plate. The tap target does NOT shrink with the artwork:
        // a transparent rect the size of the old disc stays underneath, because hit-testing here
        // is geometric and two 5px bars would otherwise be all there is left to hit.
        val pauseRadius = 21.0
        val pauseBtn = hudLayer.container().xy(canvasW - 14.0 - pauseRadius * 2.0, 20.0)
        pauseBtn.solidRect(pauseRadius * 2.0, pauseRadius * 2.0, Colors.TRANSPARENT)
        val pauseBg = pauseBtn.uiGraphics()
        // The bars are drawn off-centre toward the gadget slot's side of this box (not the
        // symmetric pauseRadius +-7/+2 they started at) so the two HUD icons visually sit close
        // together rather than each centred in its own 42px box with the tap-target padding
        // showing as a gap between them. The tap target itself (the transparent rect above)
        // keeps its full size and position - only the drawn bars move.
        val pauseBarsShiftLeft = 4.0
        fun drawPauseBtn(isHover: Boolean, isDown: Boolean) {
            pauseBg.updateShape {
                clear()
                val barCol = when {
                    isDown -> COLOR_ACCENT_CYAN
                    isHover -> Colors.WHITE
                    else -> Colors.WHITE.withAd(0.92)
                }
                fill(barCol) {
                    roundRect(pauseRadius - 7.0 - pauseBarsShiftLeft, pauseRadius - 8.5, 5.0, 17.0, 1.8, 1.8)
                    roundRect(pauseRadius + 2.0 - pauseBarsShiftLeft, pauseRadius - 8.5, 5.0, 17.0, 1.8, 1.8)
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
                    start { onTouch(true); img.alpha = 0.6 }
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
        // GADGET QUICK-SLOT
        // ==========================================
        // One slot, sitting immediately left of pause, in place of the row of chips that used to
        // run across the bottom of the screen. There are six gadgets in the store now and that
        // number is still going up; a chip each was 422px of permanent furniture laid across the
        // bottom-centre, which in a game about reading guard cones is the worst strip of screen
        // to spend. This slot is the same 42px wide at six gadgets as it would be at twenty.
        //
        // The button carries no gadget of its own and no plate under it - just a white bolt on
        // the level saying "gadgets are here", in the same chrome-free language as the objectives
        // block and the pause bars.
        //
        // It is a one-way reveal, not a toggle. The first tap trades the bolt for the row of what
        // the player is carrying and the bolt is done for the run; the row then stays put, and
        // tapping a gadget spends it without closing anything. So the corner costs one icon to a
        // player who never opens it, and after that it costs exactly what is being carried - and
        // nobody has to re-open a menu mid-chase to reach the thing they already went looking for
        // once. The row is NOT modal and has no scrim behind it: it is permanent, and a permanent
        // full-canvas catcher would swallow every movement input for the rest of the level. Two taps to use something rather than one, which is the price of not having
        // to choose or remember what is bound to the button before the shooting starts.
        //
        // CHECKPOINT is deliberately in neither. It is not fired - it is spent for you by the
        // caught overlay's RESPAWN button once a run has already ended - so putting it behind a
        // control that means "tap to use" would misdescribe it.
        val gadgetTypes = listOf(
            PowerupType.SMOKE_SCREEN,
            PowerupType.PHANTOM_CLOAK,
            PowerupType.INVISIBILITY,
            PowerupType.NOISE_SUPPRESSION,
            PowerupType.PROTOTYPE
        )

        // 42x42 matches the pause button's box exactly. The boxes now sit flush (no gutter left
        // between them at all) because closing the visible distance between the two icons turned
        // out to need the drawn art shifted off-centre inside each box (see slotBoltShiftRight
        // below and pauseBarsShiftLeft above) - the boxes touching is just the other half of that,
        // not the thing doing the work on its own. Each box is still the same 42 tap target it
        // always was, just with no dead strip between them.
        val slotSize = 42.0
        val slotIconSize = 30.0
        val slotGap = 3.0
        val slotX = canvasW - 14.0 - pauseRadius * 2.0 - slotGap - slotSize
        val slotY = 20.0
        val trayExpandSeconds = 0.17

        fun tryActivatePowerup(type: PowerupType) {
            if (world.isLevelComplete || world.isGameOver || isPaused) return
            if (profileStorage.consumePowerup(type)) {
                world.activatePowerup(type)
                // The slot reads the per-frame cache, and this can fire from a key press earlier
                // in the same frame, so re-read rather than show a stale count for a tick.
                refreshProfile()
            }
        }

        val gadgetLayer = controlsContainer.container()

        // --- The row -----------------------------------------------------------------------
        var gadgetsShown = false
        var trayExpand = 0.0
        // Which gadgets the row was last built for, as a bitmask. Stock runs out during a level,
        // so the row has to re-pack when it does - but only then, not every frame.
        var trayBuiltFor = -1
        val gadgetTray = gadgetLayer.container()
        gadgetTray.visible = false

        class TrayEntry(
            val type: PowerupType,
            val box: Container,
            val count: Text,
            val frame: Graphics,
            /** Where this entry sits once the tray has finished expanding. */
            var restX: Double = 0.0,
            var lastLabel: String = "",
            var lastLive: Boolean? = null
        )

        // The art is fixed per entry and scaled exactly once, at construction. It is NOT one
        // sprite that swaps its bitmap, because View.size() multiplies the existing scale by
        // (requested / current local bounds) rather than setting it outright - so a swapping
        // sprite compounds its own scale every time, which is how the first cut of this ended up
        // drawing 512px gadget art across the whole corner. Setting `scale` from the bitmap's own
        // width says what is meant and cannot compound.
        val trayEntries = gadgetTypes.mapIndexed { i, type ->
            val box = gadgetTray.container()
            box.solidRect(slotSize, slotSize, Colors.TRANSPARENT)
            val frame = box.uiGraphics()
            gadgetBitmaps[i]?.let { bmp ->
                box.image(bmp).also {
                    it.scale = slotIconSize / bmp.width.toDouble()
                    it.xy((slotSize - slotIconSize) / 2.0, 2.0)
                }
            }
            val count = box.text("", textSize = 12.5, font = bebasFont, color = COLOR_BORDER_GOLD)
            count.graphicsRenderer = GraphicsRenderer.GPU
            box.visible = false
            box.mouse {
                onClick {
                    playClick(GameAudio.UI_CLICK_GAIN)
                    tryActivatePowerup(type)
                }
            }
            TrayEntry(type, box, count, frame)
        }

        // Laid out right-to-left from the slot, so the tray unrolls into the empty top-centre
        // band rather than over the objectives block in the opposite corner. Five carried
        // gadgets come to 242px and stop well short of it.
        // A gadget that is live but out of stock still gets a place, so an effect that is
        // running is never absent from the only screen that reports it.
        fun trayOwned(): List<PowerupType> = gadgetTypes.filter {
            cachedProfile.getPowerupCount(it) > 0 || world.activePowerups.isActive(it)
        }

        fun traySignature(owned: List<PowerupType>): Int =
            owned.fold(0) { acc, t -> acc or (1 shl gadgetTypes.indexOf(t)) }

        /**
         * Positions the row. [animate] is true only for the reveal itself, where every entry
         * starts stacked under the bolt and travels out - so the expansion reads as that one icon
         * becoming several. Every later call is a re-pack after something ran out, and those
         * place the entries directly: replaying the unfold each time a gadget was spent would
         * turn a routine layout change into an animation the player has to wait through.
         */
        fun layoutTray(animate: Boolean) {
            val owned = trayOwned()
            trayBuiltFor = traySignature(owned)
            // The row ends ON the button's own square rather than beside it - the last entry lands
            // exactly where the bolt was, since the bolt is gone by then.
            val startX = slotX - (owned.size - 1) * (slotSize + slotGap)
            for (entry in trayEntries) {
                val rank = owned.indexOf(entry.type)
                entry.box.visible = rank >= 0
                if (rank < 0) continue
                entry.restX = startX + rank * (slotSize + slotGap)
                entry.box.xy(if (animate) slotX else entry.restX, slotY)
                entry.box.alpha = if (animate) 0.0 else 1.0
                entry.lastLabel = ""
                entry.lastLive = null
                entry.frame.updateShape {
                    clear()
                    fill(Colors["#05070A"].withAd(0.62)) {
                        roundRect(0.0, 0.0, slotSize, slotSize, 9.0, 9.0)
                    }
                    stroke(COLOR_PRIMARY.withAd(0.34), StrokeInfo(thickness = 1.3)) {
                        roundRect(0.95, 0.95, slotSize - 1.9, slotSize - 1.9, 8.5, 8.5)
                    }
                }
            }
        }

        // Runs every frame the row is up, because a live gadget's countdown is shown here.
        // Guarded on the rendered string so the text bounds read that re-centres it only happens
        // ten times a second rather than sixty.
        fun refreshTrayLabels() {
            for (entry in trayEntries) {
                if (!entry.box.visible) continue
                val live = world.activePowerups.isActive(entry.type)
                val rem = world.activePowerups.getRemainingTime(entry.type)
                val label = when {
                    live && entry.type.isLevelDuration -> "ON"
                    live -> "${(rem * 10).toInt() / 10.0}s"
                    else -> "${cachedProfile.getPowerupCount(entry.type)}"
                }
                if (entry.lastLabel == label && entry.lastLive == live) continue
                entry.lastLabel = label
                entry.lastLive = live
                entry.count.text = label
                entry.count.color = if (live) COLOR_BORDER_GREEN else COLOR_BORDER_GOLD
                entry.count.xy(slotSize - entry.count.width - 3.0, slotSize - 15.0)
            }
        }

        fun revealGadgets() {
            refreshProfile()
            layoutTray(animate = true)
            refreshTrayLabels()
            gadgetsShown = true
            trayExpand = 0.0
            gadgetTray.visible = true
        }

        // --- The slot ----------------------------------------------------------------------
        val gadgetSlot = gadgetLayer.container().xy(slotX, slotY)
        // The transparent rect is the whole tap target now that there is no plate to hit. Without
        // it the button would be a 20x23 bolt with holes in it, since hit-testing here is
        // geometric - the same allowance the pause bars were given when their disc came off.
        gadgetSlot.solidRect(slotSize, slotSize, Colors.TRANSPARENT)
        // The drain bar for a live gadget, and nothing else. It sits under the bolt rather than
        // around it because there is no longer a frame to run it along.
        val slotDrain = gadgetSlot.uiGraphics()
        // Centred vertically like the pause bars, so the two icons sit on one line - but shifted
        // toward the pause button horizontally (see pauseBarsShiftLeft above), for the same reason
        // pause's own bars moved: centring both icons in their own 42px box left a wide dead gap
        // of tap-target padding between them. Real bolt art when it loaded; falls back to the old
        // drawn polygon (same as every other SceneAssets load in this file) if the PNG is ever
        // missing, rather than leaving the corner blank.
        // Widened well past the source art's own 102:235 aspect (~11 wide at this height) -
        // asked to read as thicker/bolder in the corner, not just taller.
        val slotBoltW = 16.0
        val slotBoltH = 22.0
        val slotBoltShiftRight = 2.0
        val slotIconImg: Image? = gadgetBoltBitmap?.let { bmp ->
            gadgetSlot.image(bmp).also {
                it.size(slotBoltW, slotBoltH)
                it.xy(slotSize / 2.0 - slotBoltW / 2.0 + slotBoltShiftRight, slotSize / 2.0 - slotBoltH / 2.0)
            }
        }
        val slotIconFallback: Graphics? = if (slotIconImg == null) {
            gadgetSlot.uiGraphics().xy(slotSize / 2.0 + slotBoltShiftRight, slotSize / 2.0)
        } else null
        val slotIcon: View = slotIconImg ?: slotIconFallback!!

        gadgetSlot.singleTouch {
            start { slotIcon.alpha = 0.55 }
            end {
                slotIcon.alpha = 1.0
                if (!gadgetsShown) {
                    playClick(GameAudio.UI_CLICK_GAIN)
                    revealGadgets()
                }
            }
            endAnywhere { slotIcon.alpha = 1.0 }
        }

        // Redraw guards. The bolt only changes colour when something goes live or expires; the
        // drain bar only when its fraction has moved a visible step. updateShape re-tessellates
        // everything it is handed, so neither runs on a frame where it would come out identical.
        var slotLastSpan = -2.0
        var slotLastLive: Boolean? = null

        val gadgetKeys = listOf(Key.N1, Key.N2, Key.N3, Key.N4, Key.N5)

        // ==========================================
        // 1. PAUSE OVERLAY (Heist Dossier - matches the main menu)
        // ==========================================
        val pauseOverlay = setupPauseOverlay(
            canvasW = canvasW,
            canvasH = canvasH,
            levelName = levelData.name,
            bebasFont = bebasFont,
            paperBtnBitmaps = paperBtnBitmaps,
            paperInk = paperInk,
            playClick = playClick,
            onResume = {
                isPaused = false
            },
            onRestart = {
                stopBgMusic()
                sceneContainer.changeTo { GameplayScene(levelData) }
            },
            onQuit = {
                stopBgMusic()
                getLevelExitBridge().requestReturnToMenu()
                sceneContainer.changeTo { GameplayScene(levelData, startDormant = true) }
            }
        )

        val allLevels = LevelData.DEFAULT_LEVELS
        val currentLevelIndex = allLevels.indexOfFirst { it.id == levelData.id }
        val nextLevel = if (currentLevelIndex >= 0 && currentLevelIndex + 1 < allLevels.size) allLevels[currentLevelIndex + 1] else null

        // ==========================================
        // 2. END-OF-RUN DOSSIER SHEET (MISSION FAILED)
        // ==========================================
        val caughtOverlay = setupCaughtOverlay(
            canvasW = canvasW,
            canvasH = canvasH,
            dossierBitmap = dossierBitmap,
            bebasFont = bebasFont,
            handwrittenFont = handwrittenFont,
            paperBtnBitmaps = paperBtnBitmaps,
            levelData = levelData,
            paperInk = paperInk,
            playClick = playClick,
            onRequestContinueAd = {
                getContinueAdBridge().requestContinueAd()
                getAnalyticsBridge().track("watch_ad_continue_requested", mapOf("level_id" to levelData.id))
            },
            onRetry = {
                stopBgMusic()
                sceneContainer.changeTo { GameplayScene(levelData) }
            },
            onReturnToMenu = {
                stopBgMusic()
                getLevelExitBridge().requestReturnToMenu()
                sceneContainer.changeTo { GameplayScene(levelData, startDormant = true) }
            }
        )

        // ==========================================
        // 3. MISSION SUCCESSFUL OVERLAY
        // ==========================================
        val winOverlay = setupWinOverlay(
            canvasW = canvasW,
            canvasH = canvasH,
            successBgBitmap = successBgBitmap,
            starSlices = starSlices,
            paperBtnBitmaps = paperBtnBitmaps,
            bebasFont = bebasFont,
            levelData = levelData,
            nextLevel = nextLevel,
            paperInk = paperInk,
            playClick = playClick,
            onRetry = {
                stopBgMusic()
                sceneContainer.changeTo { GameplayScene(levelData) }
            },
            onReturnToMenu = {
                stopBgMusic()
                getLevelExitBridge().requestReturnToMenu()
                sceneContainer.changeTo { GameplayScene(levelData, startDormant = true) }
            },
            onNextMission = {
                stopBgMusic()
                if (nextLevel != null) {
                    sceneContainer.changeTo { GameplayScene(nextLevel) }
                } else {
                    getLevelExitBridge().requestReturnToMenu()
                    sceneContainer.changeTo { GameplayScene(levelData, startDormant = true) }
                }
            }
        )

        world.onLevelComplete = {
            val result = world.getLevelResult()
            // Checked before saveResult() overwrites it - this is how many *distinct* levels have
            // ever been completed, not a per-play counter, used to gate the level-exit
            // interstitial (InterstitialAdLimiter.MIN_LEVELS_COMPLETED) so early levels stay
            // ad-free regardless of how many times this one has been replayed.
            val alreadyCompletedBefore = levelStorage.getBestResult(result.levelId)?.completed == true
            levelStorage.saveResult(result)
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

            // Both objectives resolve at the same instant the level does - the primary by
            // definition, the optional one against the clock it was racing.
            objMainState = 1
            setObjMark(objMainMark, 1)
            if (objOptState != 2) {
                objOptState = if (result.star3) 1 else 2
                setObjMark(objOptMark, objOptState)
            }

            winOverlay.show(result, earnedCoins)
        }

        world.onGameOver = {
            val best = levelStorage.getBestResult(levelData.id)
            caughtOverlay.show(world.timeTaken, world.spottedCount, best, profileStorage.getProfile().coins)

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
            // Hoisted above every early-return in this block (including the pause/game-over one)
            // so syncBgMusicVolume's ramp below has a real per-frame delta even on frames that
            // return before the "active gameplay" dtSec further down - see that call's own site.
            val dtSec = dt.seconds.coerceIn(0.0, 0.1)

            // Checked unconditionally (ahead of the isGameOver early-return below), since that's
            // exactly the state this fires in: the native shell has shown the rewarded ad while
            // this scene stayed alive in the background, and grants the continue once the player
            // actually watched it. Restarts the same way "RETRY INFILTRATION" already does.
            if (getContinueAdBridge().consumeContinueGranted()) {
                getAnalyticsBridge().track("watch_ad_continue_granted", mapOf("level_id" to levelData.id))
                sounds.toastSuccess.playSfx(sfxContext, GameAudio.TOAST_SUCCESS_GAIN, sfxVolume(), GameAudio.SfxFile.TOAST_SUCCESS)
                stopBgMusic()
                // Hidden immediately, not left for changeTo to sort out: this scene (with its
                // MISSION FAILED overlay still visible) stays on screen for however many frames
                // the transition to the new GameplayScene instance takes, which is exactly the
                // "continue menu flashes for a split second" the ad-continue flow was showing.
                caughtOverlay.hide()
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

            syncBgMusicVolume(dtSec)

            if (isPaused || world.isLevelComplete || world.isGameOver) {
                if (world.isLevelComplete || world.isGameOver) {
                    tutorialLayer.visible = false
                }
                tutorialDarkOverlay.updateShape { clear() }
                tutorialHighlightGraphics.updateShape { clear() }
                return@addUpdater
            }

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

            // Number keys select and fire in one press, so a desktop player never has to open
            // the tray at all - the slot follows along and shows what was last used.
            for (i in gadgetTypes.indices) {
                if (views.input.keys.justPressed(gadgetKeys[i])) {
                    tryActivatePowerup(gadgetTypes[i])
                }
            }

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

            // Swing animation machine: above climb, for the same reason climb is above jump.
            // Player.isSwinging drives x/y from the clip's own grip curves (Player.advanceSwing),
            // so every machine below would otherwise fight it - isGrounded is false throughout.
            if (world.player.isSwinging) {
                if (playerAnimState != "swing") {
                    playerAnimState = "swing"
                    landingAbsorb = false
                    swingImpactSoundPlayed = false
                    // The push-off is a jump, and the clip opens on one, so it gets the jump's
                    // grunt. There is no dedicated swing sample.
                    sounds.climb.playSfx(sfxContext, GameAudio.CLIMB_GAIN, sfxVolume(), GameAudio.SfxFile.CLIMB)
                    playerSprite.playAnimationLooped(playerAnimations.swing, manualFrameTime)
                }
                // Play landing impact sound right as the feet plant on the far ledge (frame 44.5)
                if (!swingImpactSoundPlayed && world.player.swingPhase >= (44.0 / 51.0)) {
                    swingImpactSoundPlayed = true
                    sounds.impact.playSfx(sfxContext, GameAudio.LANDING_GAIN, sfxVolume(), GameAudio.SfxFile.IMPACT)
                }
                playerSprite.setFrame(
                    (world.player.swingPhase * swingFrameSpan).toInt().coerceIn(0, swingFrameSpan)
                )
            } else if (world.player.isClimbing) {
                if (playerAnimState != "climb") {
                    playerAnimState = "climb"
                    sounds.climb.playSfx(sfxContext, GameAudio.CLIMB_GAIN, sfxVolume(), GameAudio.SfxFile.CLIMB)
                    playerSprite.playAnimationLooped(playerAnimations.climb, manualFrameTime)
                }
                val frame = climbFirstFrame + (world.player.climbPhase * climbFrameSpan).toInt()
                playerSprite.setFrame(frame.coerceIn(climbFirstFrame, climbLastFrame))
            } else {
                if (playerAnimState == "swing") {
                    // The swing clip already carries its own complete landing absorption and standup
                    // over planted feet (frames 44.5 to 51). Hand over directly to walk or idle without
                    // triggering jump landingAbsorb, which would otherwise flash a jarring 2-frame squat.
                    playerAnimState = "none"
                    swingExitTimer = 0.20
                } else if (swingExitTimer > 0.0) {
                    swingExitTimer = maxOf(0.0, swingExitTimer - dtSec)
                }
                if (playerAnimState == "climb") {
                    // Feet planting on the ledge - fires right here, unconditionally, rather than
                    // only in the walk-handover branch below: that branch is gated on the player
                    // still holding a direction the instant the climb ends, so a climb followed by
                    // standing still played no sound at all.
                    playerAnimState = "none"
                    climbExitTimer = 0.20
                    val step = if (stepAlternate) sounds.stepB else sounds.stepA
                    stepAlternate = !stepAlternate
                    step.playSfx(sfxContext, GameAudio.STEP_GAIN, sfxVolume(), if (step === sounds.stepA) GameAudio.SfxFile.STEP_A else GameAudio.SfxFile.STEP_B)
                } else if (climbExitTimer > 0.0) {
                    climbExitTimer = maxOf(0.0, climbExitTimer - dtSec)
                }

                // Jump / Airborne animation machine
                if (playerAnimState != "jump" && !world.player.isGrounded) {
                    val wasMoving = (playerAnimState == "walk") || world.player.isMoving || abs(world.player.vx) > 5.0
                    playerAnimState = "jump"
                    landingAbsorb = false  // cancel any in-progress absorption
                    jumpStartY = world.player.y
                    jumpPhaseElapsed = 0.0
                    playerSprite.playAnimationLooped(playerAnimations.jump, manualFrameTime)
                    // If moving upward, it's an intentional jump; if falling downwards, it's stepping/falling off a ledge
                    jumpPhase = if (world.player.vy < 0.0) "launch" else "drop"
                    dropFromWalk = wasMoving && jumpPhase == "drop"
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
                            sounds.impact.playSfx(sfxContext, GameAudio.LANDING_GAIN, sfxVolume(), GameAudio.SfxFile.IMPACT)
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
                            val wasMoving = (playerAnimState == "walk") || world.player.isMoving || abs(world.player.vx) > 5.0
                            jumpPhase = if (world.player.vy < 0.0) "launch" else "drop"
                            dropFromWalk = wasMoving && jumpPhase == "drop"
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
                && playerAnimState != "crouchwalk" && playerAnimState != "climb"
                && playerAnimState != "swing" && playerAnimState != "landAbsorb") {
                val wantsWalk = world.player.isMoving
                if (wantsWalk) {
                    if (playerAnimState != "walk") {
                        val fromClimb = climbExitTimer > 0.0
                        val fromSwing = swingExitTimer > 0.0
                        climbExitTimer = 0.0
                        swingExitTimer = 0.0
                        playerAnimState = "walk"
                        playerSprite.playAnimationLooped(playerAnimations.walk, manualFrameTime)
                        if (fromClimb || fromSwing) {
                            // Handover directly from climb mantle or swing landing into athletic push-off:
                            // Start at Walk frame 4 rather than 0 so there is no upright pop or sluggish lean-in.
                            // No footstep here - climb now plays its own foot-plant sound the instant it ends
                            // (see playerAnimState == "climb" above) and swing already has its landing impact,
                            // so one straight into a walk would otherwise double up.
                            walkCycleProgress = 0.0
                            walkInTransition = true
                            walkTransitionStartFrame = 4
                            val framesRemaining = PlayerAnimations.WALK_TRANSITION_END - walkTransitionStartFrame
                            val totalFrames = PlayerAnimations.WALK_TRANSITION_END - PlayerAnimations.WALK_TRANSITION_START
                            walkTransitionCurrentDuration = walkTransitionDuration * (framesRemaining.toDouble() / totalFrames)
                            walkTransitionElapsed = 0.0
                            playerSprite.setFrame(walkTransitionStartFrame)
                        } else if (stationaryElapsed >= 0.15) {
                            // Only play lean-in transition if starting from a sustained stationary stop
                            walkCycleProgress = 0.0
                            walkInTransition = true
                            walkTransitionStartFrame = PlayerAnimations.WALK_TRANSITION_START
                            walkTransitionCurrentDuration = walkTransitionDuration
                            walkTransitionElapsed = 0.0
                            playerSprite.setFrame(PlayerAnimations.WALK_TRANSITION_START)
                            val step = if (stepAlternate) sounds.stepB else sounds.stepA
                            stepAlternate = !stepAlternate
                            step.playSfx(sfxContext, GameAudio.STEP_GAIN, sfxVolume(), if (step === sounds.stepA) GameAudio.SfxFile.STEP_A else GameAudio.SfxFile.STEP_B)
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

            if (!world.player.isSwinging) {
                playerSprite.y = world.player.height + when {
                    playerAnimState == "idle" -> idleFeetOffset
                    // Only the held/entering/exiting stance, not crouchwalk - the walk cycle's
                    // alternating planted/swinging foot is supposed to look uneven, this offset is
                    // only for the settled two-feet-down pose.
                    playerAnimState == "crouch" -> crouchFeetOffset
                    playerAnimState == "climb" -> climbFeetOffset
                    else -> 0.0
                }
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
                        val fallProgress = (world.player.vy / (world.player.maxFallSpeed * 0.7)).coerceIn(0.0, 1.0)
                        if (dropFromWalk) {
                            // Forward moving drop: start with athletic stride into the air (Jump 5..8)
                            // before smoothly uncurling into touchdown extension (Jump 21..26)
                            if (fallProgress < 0.35) {
                                val t = (fallProgress / 0.35).coerceIn(0.0, 1.0)
                                5 + (t * 3).toInt().coerceIn(0, 3)
                            } else {
                                val t = ((fallProgress - 0.35) / 0.65).coerceIn(0.0, 1.0)
                                (21 + (t * (jumpTouchdownFrame - 21)).toInt()).coerceIn(21, jumpTouchdownFrame)
                            }
                        } else {
                            // Stepping/dropping off a ledge stationary: keep legs extended downward
                            val dropFrameStart = 22
                            dropFrameStart + (fallProgress * (jumpTouchdownFrame - dropFrameStart)).toInt()
                        }
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
                                step.playSfx(sfxContext, GameAudio.STEP_GAIN, sfxVolume(), if (step === sounds.stepA) GameAudio.SfxFile.STEP_A else GameAudio.SfxFile.STEP_B)
                            }
                        }
                    }
                }
            }

            // Flip sprite to face direction
            if (world.player.isSwinging) {
                playerFacingLeft = world.player.facing < 0.0
            } else if (world.player.isClimbing) {
                playerFacingLeft = world.player.facing < 0.0
            } else if (moveInput < 0) {
                playerFacingLeft = true
            } else if (moveInput > 0) {
                playerFacingLeft = false
            }
            playerSprite.scaleX = playerBaseScale * (if (playerFacingLeft) -1.0 else 1.0)
            playerSprite.scaleY = playerBaseScale

            if (world.player.isSwinging) {
                val rot = world.player.swingRotationDegrees.degrees
                playerSprite.rotation = rot
                val rad = rot.radians
                val pivotH = world.player.swingPivotHeight
                playerSprite.x = world.player.width / 2.0 - pivotH * sin(rad)
                playerSprite.y = world.player.height - pivotH * (1.0 - cos(rad))
            } else {
                playerSprite.rotation = 0.degrees
                playerSprite.x = world.player.width / 2.0
            }

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
                    sounds.guardInvestigate.playSfx(sfxContext, GameAudio.GUARD_INVESTIGATE_GAIN, sfxVolume(), GameAudio.SfxFile.GUARD_INVESTIGATE)
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

            // --- Gadgets -----------------------------------------------------------------
            // The bolt and the row it turns into are the only place a live gadget is reported;
            // the chip row that used to do it, and the "ACTIVE: ..." status line before that, are
            // both gone.
            val currentProfile = cachedProfile
            val liveGadget = gadgetTypes.firstOrNull { world.activePowerups.isActive(it) }
            gadgetLayer.visible = liveGadget != null || gadgetTypes.any {
                currentProfile.getPowerupCount(it) > 0
            }

            if (gadgetsShown) {
                refreshTrayLabels()
                if (trayExpand < 1.0) {
                    trayExpand = (trayExpand + dtSec / trayExpandSeconds).coerceAtMost(1.0)
                    val e = easeOutCubic(trayExpand)
                    for (entry in trayEntries) {
                        if (!entry.box.visible) continue
                        entry.box.x = slotX + (entry.restX - slotX) * e
                        entry.box.alpha = e
                    }
                } else if (traySignature(trayOwned()) != trayBuiltFor) {
                    // Something ran out (or a level-long effect outlived its last unit). Re-pack
                    // so the row stays flush against the corner instead of leaving a hole.
                    layoutTray(animate = false)
                    refreshTrayLabels()
                }
            } else {
                // Before the reveal the bolt is also the status light: white while idle, green for
                // as long as something is running, with a drain bar under it. After the reveal it
                // is gone and each gadget reports its own countdown in the row.
                val liveSpan = when {
                    liveGadget == null -> -1.0
                    liveGadget.isLevelDuration -> 1.0
                    else -> (world.activePowerups.getRemainingTime(liveGadget) / liveGadget.duration)
                        .coerceIn(0.0, 1.0)
                }
                val isLive = liveGadget != null
                if (slotLastLive != isLive) {
                    slotLastLive = isLive
                    val color = if (isLive) COLOR_BORDER_GREEN else Colors.WHITE
                    if (slotIconImg != null) {
                        slotIconImg.colorMul = color
                    } else {
                        slotIconFallback?.updateShape {
                            clear()
                            drawPowerupIcon(9.0, color)
                        }
                    }
                }
                // Quantised to fortieths: the bar is 26px wide, so anything finer redraws it for
                // a sub-pixel change.
                val liveStep = if (liveSpan < 0.0) -1.0 else (liveSpan * 40.0).toInt() / 40.0
                if (slotLastSpan != liveStep) {
                    slotLastSpan = liveStep
                    slotDrain.updateShape {
                        clear()
                        if (isLive) {
                            val barW = 26.0
                            fill(COLOR_PRIMARY.withAd(0.22)) {
                                roundRect((slotSize - barW) / 2.0, slotSize - 6.0, barW, 2.6, 1.3, 1.3)
                            }
                            fill(COLOR_BORDER_GREEN) {
                                roundRect((slotSize - barW) / 2.0, slotSize - 6.0, barW * liveSpan, 2.6, 1.3, 1.3)
                            }
                        }
                    }
                }
            }
            gadgetSlot.visible = !gadgetsShown

            // Objectives panel: fades up once at the start, then only redraws when a marker
            // changes. The clock is the one that can turn during play - the moment the run passes
            // the target the bonus is gone, and the panel says so instead of leaving the player
            // to discover it on the results card.
            if (objPanel.alpha < objPanelAlpha) {
                objPanel.alpha = (objPanel.alpha + dtSec / objPanelFadeSeconds * objPanelAlpha)
                    .coerceAtMost(objPanelAlpha)
            }
            if (objOptState == 0 && world.timeTaken > levelData.timeTargetSeconds) {
                objOptState = 2
                setObjMark(objOptMark, 2)
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
                    sounds.cameraDetect.playSfx(sfxContext, GameAudio.CAMERA_DETECT_GAIN, sfxVolume(), GameAudio.SfxFile.CAMERA_DETECT)
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
    /**
     * The menu's coin, in pigment rather than in neon.
     *
     * Same construction as `MenuComponents.drawCoinIcon` (rim, recessed face, grooved ring,
     * embossed diamond) so the reward on the results card is recognisably the same object the
     * player spends in the store - but struck in aged golds, because that art is drawn on a dark
     * panel and #FFD54F on this card's paper reads as a sticker rather than as a coin. Centred on
     * the origin, like every other icon here.
     */
    private fun ShapeBuilder.drawCoinIcon(r: Double) {
        fill(Colors["#3E2723"].withAd(0.45)) { circle(Point(0.0, r * 0.12), r) }
        fill(Colors["#9A6E17"]) { circle(Point(0.0, 0.0), r) }
        fill(Colors["#C79A34"]) { circle(Point(0.0, 0.0), r * 0.78) }
        stroke(Colors["#7A5510"].withAd(0.7), StrokeInfo(thickness = r * 0.10)) {
            circle(Point(0.0, 0.0), r * 0.78)
        }
        val e = r * 0.40
        fill(Colors["#7A5510"]) {
            moveTo(Point(0.0, -e))
            lineTo(Point(e * 0.75, 0.0))
            lineTo(Point(0.0, e))
            lineTo(Point(-e * 0.75, 0.0))
            close()
        }
    }

    /**
     * Pass/fail marks for the results card's objective list, centred on the origin like every
     * other icon here and sized by [r], the radius of the box they sit in. Drawn as strokes
     * rather than filled glyphs so they read as something struck onto the page by hand.
     */
    private fun ShapeBuilder.drawTickIcon(r: Double, color: RGBA) {
        stroke(color, StrokeInfo(thickness = r * 0.40)) {
            moveTo(Point(-r * 0.68, r * 0.02))
            lineTo(Point(-r * 0.20, r * 0.52))
            lineTo(Point(r * 0.70, -r * 0.56))
        }
    }

    /**
     * The universal powerup bolt, centred on the origin, [r] being half its height. Drawn rather
     * than loaded because it stands for the whole category and belongs to no one gadget - and
     * because a six-point polygon in flat white stays crisp at the 23px it is used at, which
     * downscaled 512px artwork does not.
     */
    private fun ShapeBuilder.drawPowerupIcon(r: Double, color: RGBA) {
        fill(color) {
            moveTo(Point(0.10 * r, -1.00 * r))
            lineTo(Point(-0.90 * r, 0.20 * r))
            lineTo(Point(-0.20 * r, 0.20 * r))
            lineTo(Point(-0.20 * r, 1.00 * r))
            lineTo(Point(0.80 * r, -0.20 * r))
            lineTo(Point(0.10 * r, -0.20 * r))
            close()
        }
    }

    private fun ShapeBuilder.drawCrossIcon(r: Double, color: RGBA) {
        stroke(color, StrokeInfo(thickness = r * 0.40)) {
            moveTo(Point(-r * 0.52, -r * 0.52))
            lineTo(Point(r * 0.52, r * 0.52))
            moveTo(Point(r * 0.52, -r * 0.52))
            lineTo(Point(-r * 0.52, r * 0.52))
        }
    }

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

    companion object {
        private const val ICON_COLUMN = 0.35
        private const val LABEL_COLUMN = 0.42
        private const val CENTERED_ICON_WIDTH = 22.0
        private const val CENTERED_ICON_GAP = 12.0
    }

    private class LoadingScreenHandle(
        val setProgress: (Double) -> Unit,
        val dismiss: () -> Unit
    )

    private class CaughtOverlayHandle(
        val container: Container,
        val show: (timeTaken: Float, alerts: Int, best: LevelResult?, coins: Int) -> Unit,
        val hide: () -> Unit
    )

    private class WinOverlayHandle(
        val container: Container,
        val show: (result: LevelResult, earnedCoins: Int) -> Unit
    )

    private class WinReveal(val view: View, val start: Double, val baseX: Double, val slide: Double)

    private fun clockText(t: Float): String {
        val total = t.toInt().coerceAtLeast(0)
        val mins = total / 60
        val secs = total % 60
        return "${if (mins < 10) "0$mins" else "$mins"}:${if (secs < 10) "0$secs" else "$secs"}"
    }

    private fun secondsText(t: Float): String = "${(t * 10).toInt() / 10.0}S"

    private fun easeOutBack(p: Double): Double {
        val c1 = 1.70158
        val q = p - 1.0
        return 1.0 + (c1 + 1.0) * q * q * q + c1 * q * q
    }

    private fun easeOutCubic(p: Double): Double {
        val q = 1.0 - p
        return 1.0 - q * q * q
    }

    private fun Container.paperMenuBtnWidth(label: String, height: Double, font: Font, paperInk: RGBA): Double {
        val probe = text(label.uppercase(), textSize = height * 0.44, font = font, color = paperInk)
        probe.graphicsRenderer = GraphicsRenderer.GPU
        val w = probe.width + CENTERED_ICON_WIDTH + CENTERED_ICON_GAP + height * 0.64
        probe.removeFromParent()
        return w
    }

    private fun Container.createPaperMenuBtn(
        label: String,
        texture: Bitmap?,
        width: Double,
        height: Double,
        x: Double,
        y: Double,
        font: Font,
        paperInk: RGBA,
        centered: Boolean = false,
        playClick: (Double) -> Unit,
        iconDrawer: ShapeBuilder.() -> Unit,
        onClick: suspend () -> Unit
    ): Container {
        val btn = container().xy(x, y)
        val img = if (texture != null) btn.image(texture) { size(width, height) } else null
        if (img == null) {
            btn.uiGraphics().updateShape {
                fill(Colors["#F6F4EE"]) { roundRect(0.0, 0.0, width, height, 2.0, 2.0) }
            }
        }
        val iconG = btn.uiGraphics()
        iconG.updateShape { iconDrawer() }
        val text = btn.text(label.uppercase(), textSize = height * 0.44, font = font, color = paperInk)
        text.graphicsRenderer = GraphicsRenderer.GPU

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

    private fun SContainer.setupLoadingScreen(
        canvasW: Double,
        canvasH: Double,
        loadingBgBitmap: Bitmap?,
        loadingLogoBitmap: Bitmap?,
        loadingBarTextureBitmap: Bitmap?,
        loadingFont: Font
    ): LoadingScreenHandle {
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

        val loadingBarFillContainer = loadingRoot.container().xy(loadingBarX, loadingBarY)
        var loadingBarFillView: View? = null
        fun setProgress(fraction: Double) {
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
        setProgress(0.0)
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

        val blinkPeriodSeconds = 2.2
        val blinkVisibleFraction = 0.88
        var loadingFlickerT = 0.0
        val loadingFlickerHandle = loadingLabel.addUpdater { dt ->
            loadingFlickerT += dt.seconds
            val phase = (loadingFlickerT % blinkPeriodSeconds) / blinkPeriodSeconds
            alpha = if (phase < blinkVisibleFraction) 1.0 else 0.0
        }

        return LoadingScreenHandle(
            setProgress = ::setProgress,
            dismiss = {
                loadingFlickerHandle.close()
                loadingRoot.removeFromParent()
            }
        )
    }

    private fun SContainer.setupPauseOverlay(
        canvasW: Double,
        canvasH: Double,
        levelName: String,
        bebasFont: Font,
        paperBtnBitmaps: List<Bitmap?>,
        paperInk: RGBA,
        playClick: (Double) -> Unit,
        onResume: () -> Unit,
        onRestart: suspend () -> Unit,
        onQuit: suspend () -> Unit
    ): Container {
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

        val pauseSubtitle = pauseOverlay.text(
            levelName.uppercase(), textSize = 14.0, font = bebasFont, color = COLOR_TEXT_MUTED
        )
        pauseSubtitle.graphicsRenderer = GraphicsRenderer.GPU
        pauseSubtitle.xy((canvasW - pauseSubtitle.width) / 2.0, pauseBlockTop + 62.0)

        val pauseBtnY0 = pauseBlockTop + 52.0 + 8.0 + 18.0 + 30.0

        pauseOverlay.createPaperMenuBtn(
            "RESUME", paperBtnBitmaps[0], pauseBtnW, pauseBtnH, pauseBtnX, pauseBtnY0,
            bebasFont, paperInk, playClick = playClick,
            iconDrawer = { drawPlayIcon(false) }
        ) {
            pauseOverlay.visible = false
            onResume()
        }

        pauseOverlay.createPaperMenuBtn(
            "RESTART", paperBtnBitmaps[1], pauseBtnW, pauseBtnH, pauseBtnX, pauseBtnY0 + pauseBtnH + pauseBtnGap,
            bebasFont, paperInk, playClick = playClick,
            iconDrawer = { drawRestartIcon(paperInk) }
        ) { onRestart() }

        pauseOverlay.createPaperMenuBtn(
            "QUIT", paperBtnBitmaps[2], pauseBtnW, pauseBtnH, pauseBtnX, pauseBtnY0 + 2 * (pauseBtnH + pauseBtnGap),
            bebasFont, paperInk, playClick = playClick,
            iconDrawer = { drawQuitIcon(false) }
        ) { onQuit() }

        pauseOverlay.visible = false
        return pauseOverlay
    }

    private fun SContainer.setupCaughtOverlay(
        canvasW: Double,
        canvasH: Double,
        dossierBitmap: Bitmap?,
        bebasFont: Font,
        handwrittenFont: Font,
        paperBtnBitmaps: List<Bitmap?>,
        levelData: LevelData,
        paperInk: RGBA,
        playClick: (Double) -> Unit,
        onRequestContinueAd: () -> Unit,
        onRetry: suspend () -> Unit,
        onReturnToMenu: suspend () -> Unit
    ): CaughtOverlayHandle {
        val resScrim = Colors["#07080A"].withAd(0.92)
        val inkStrong = Colors["#17140F"]
        val inkBody = Colors["#17140F"].withAd(0.78)
        val inkFaint = Colors["#17140F"].withAd(0.55)
        val inkRuleColor = Colors["#17140F"].withAd(0.34)
        val inkGold = Colors["#A8781A"]
        val stampRed = Colors["#96222A"]

        val DOSSIER_ASPECT = 1.5
        val DOSSIER_TILT = (-5.2).degrees

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

        val docX = sheetW * 0.15
        val docW = sheetW * 0.76
        val docY = sheetH * 0.055
        val docH = sheetH * 0.805
        fun dpx(x: Double): Double = docX + x * S - sheetW / 2.0
        fun dpy(y: Double): Double = docY + y * S - sheetH / 2.0

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

        fun Container.inkStamp(
            cx: Double, cy: Double, w: Double, h: Double, label: String, color: RGBA
        ): Text {
            val stamp = container().xy(dpx(cx), dpy(cy))
            stamp.rotation = (-10.0).degrees
            stamp.alpha = 0.82
            val halfW = w * S / 2.0
            val halfH = h * S / 2.0
            stamp.uiGraphics().updateShape {
                stroke(color, StrokeInfo(thickness = 6.0 * S)) { rect(-halfW, -halfH, w * S, h * S) }
                stroke(color, StrokeInfo(thickness = 1.2 * S)) {
                    rect(-halfW + 7.0 * S, -halfH + 7.0 * S, w * S - 14.0 * S, h * S - 14.0 * S)
                }
            }
            val t = stamp.text(label, textSize = 20.0 * S, font = bebasFont, color = color)
            t.graphicsRenderer = GraphicsRenderer.GPU
            t.xy(-t.width / 2.0, -t.height / 2.0)
            return t
        }

        val missionFileNo = (Regex("^(\\d+)").find(levelData.name)?.groupValues?.get(1)
            ?: levelData.id.filter { it.isDigit() }.ifEmpty { "1" }).padStart(2, '0')
        val missionTitleText = levelData.name.replaceFirst(Regex("^\\d+:\\s*"), "").uppercase()

        fun Container.inkMasthead(sectionTitle: String) {
            inkText(missionFileNo, 26.0, inkStrong, 0.0, 0.0)
            inkRule(34.0, 0.0, docW / S * 0.95, dashedTail = true)
            inkText(missionTitleText, 12.0, inkFaint, 0.0, 44.0)
            inkText(sectionTitle, 26.0, inkStrong, 0.0, 60.0)
            inkRule(96.0, 0.0, docW / S)
        }

        val resBtnBlockH = 3.0 * resBtnH + 2.0 * resBtnGap
        val resBtnY0 = (canvasH - resBtnBlockH) / 2.0

        val caughtOverlay = container()
        caughtOverlay.solidRect(canvasW, canvasH, resScrim)

        val caughtInk = caughtOverlay.createDossierSheet()
        caughtInk.inkMasthead("SITUATION REPORT")

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
            "Tap the bolt beside pause to bring out your gadgets."
        )
        for ((i, line) in caughtTips.withIndex()) {
            caughtInk.inkText(line, 14.0, inkBody, 8.0, 268.0 + i * 18.0, font = handwrittenFont)
        }

        caughtOverlay.createPaperMenuBtn(
            "CONTINUE (WATCH AD)", paperBtnBitmaps[0], resBtnW, resBtnH, resGroupX, resBtnY0,
            bebasFont, paperInk, playClick = playClick,
            iconDrawer = { drawPlayIcon(false) }
        ) { onRequestContinueAd() }

        caughtOverlay.createPaperMenuBtn(
            "RETRY INFILTRATION", paperBtnBitmaps[1], resBtnW, resBtnH, resGroupX, resBtnY0 + resBtnH + resBtnGap,
            bebasFont, paperInk, playClick = playClick,
            iconDrawer = { drawRestartIcon(paperInk) }
        ) { onRetry() }

        caughtOverlay.createPaperMenuBtn(
            "RETURN TO MENU", paperBtnBitmaps[2], resBtnW, resBtnH, resGroupX, resBtnY0 + 2.0 * (resBtnH + resBtnGap),
            bebasFont, paperInk, playClick = playClick,
            iconDrawer = { drawQuitIcon(false) }
        ) { onReturnToMenu() }

        caughtOverlay.visible = false

        return CaughtOverlayHandle(
            container = caughtOverlay,
            show = { timeTaken, alerts, best, coins ->
                caughtOverlay.visible = true
                setCaughtStatus("APPREHENDED", stampRed)
                setCaughtAlerts("$alerts", inkStrong)
                setCaughtTime(secondsText(timeTaken), inkStrong)
                setCaughtRecord(
                    if (best != null) "${best.starCount}/3 - ${secondsText(best.timeTaken)}" else "NO RECORD",
                    if (best != null) inkStrong else inkFaint
                )
                setCaughtCoins("$coins", inkGold)
            },
            hide = { caughtOverlay.visible = false }
        )
    }

    private fun SContainer.setupWinOverlay(
        canvasW: Double,
        canvasH: Double,
        successBgBitmap: Bitmap?,
        starSlices: List<BmpSlice>?,
        paperBtnBitmaps: List<Bitmap?>,
        bebasFont: Font,
        levelData: LevelData,
        nextLevel: LevelData?,
        paperInk: RGBA,
        playClick: (Double) -> Unit,
        onRetry: suspend () -> Unit,
        onReturnToMenu: suspend () -> Unit,
        onNextMission: suspend () -> Unit
    ): WinOverlayHandle {
        val inkStrong = Colors["#17140F"]
        val inkFaint = Colors["#17140F"].withAd(0.55)
        val inkRuleColor = Colors["#17140F"].withAd(0.34)
        val inkGold = Colors["#A8781A"]
        val stampRed = Colors["#96222A"]
        val stampGreen = Colors["#25603A"]

        val winContainer = container()
        val winScrim = winContainer.solidRect(canvasW, canvasH, Colors["#07080A"].withAd(0.80))

        val winCardAspect = if (successBgBitmap != null) {
            successBgBitmap.width.toDouble() / successBgBitmap.height.toDouble()
        } else {
            1.44
        }
        var winCardH = canvasH * 0.92
        var winCardW = winCardH * winCardAspect
        if (winCardW > canvasW * 0.74) {
            winCardW = canvasW * 0.74
            winCardH = winCardW / winCardAspect
        }
        val winCardY = (canvasH - winCardH) / 2.0

        val winCardPivot = winContainer.container().xy(canvasW / 2.0, winCardY + winCardH / 2.0)
        winCardPivot.alpha = 0.0
        winCardPivot.scaleX = 0.90
        winCardPivot.scaleY = 0.90
        val winCard = winCardPivot.container().xy(-winCardW / 2.0, -winCardH / 2.0)
        if (successBgBitmap != null) {
            winCard.image(successBgBitmap) { size(winCardW, winCardH) }
        } else {
            winCard.uiGraphics().updateShape {
                fill(Colors["#D8D2C4"]) { rect(0.0, 0.0, winCardW, winCardH) }
            }
        }

        val WS = (winCardW / 640.0).coerceIn(0.5, 1.6)
        val winTextL = winCardW * 0.315
        val winTextR = winCardW * 0.725
        val winTextW = winTextR - winTextL
        val winCx = (winTextL + winTextR) / 2.0
        val winTop = winCardH * 0.375

        fun Container.winText(
            value: String, size: Double, color: RGBA, x: Double, y: Double,
            font: Font = bebasFont
        ): Text {
            val t = text(value, textSize = size * WS, font = font, color = color)
            t.graphicsRenderer = GraphicsRenderer.GPU
            t.xy(x, y)
            return t
        }

        val winReveals = ArrayList<WinReveal>()
        val WIN_REVEAL_DUR = 0.30
        fun <T : View> T.revealAt(start: Double, slide: Double = 0.0): T {
            winReveals.add(WinReveal(this, start, x, slide))
            visible = false
            return this
        }

        val WIN_CARD_POP = 0.30
        val WIN_STAR_0 = 0.46
        val WIN_STAR_STEP = 0.28
        val WIN_STAR_DUR = 0.42
        val WIN_ROW_0 = WIN_STAR_0 + 0.18
        val WIN_PAYOUT_AT = 1.52
        val WIN_BUTTONS_AT = 1.74
        val WIN_ANIM_END = WIN_BUTTONS_AT + WIN_REVEAL_DUR

        val missionFileNo = (Regex("^(\\d+)").find(levelData.name)?.groupValues?.get(1)
            ?: levelData.id.filter { it.isDigit() }.ifEmpty { "1" }).padStart(2, '0')
        val missionTitleText = levelData.name.replaceFirst(Regex("^\\d+:\\s*"), "").uppercase()

        winCard.container().xy(winCx, winTop).also { holder ->
            val t = holder.winText("MISSION $missionFileNo - $missionTitleText", 12.0, inkFaint, 0.0, 0.0)
            t.xy(-t.width / 2.0, 0.0)
        }.revealAt(0.22)

        val winStarH = winCardH * 0.115
        val winStarStep = winStarH * 1.42
        val winStarsCy = winTop + 52.0 * WS
        fun starSlotSize(i: Int): Pair<Double, Double> {
            val slice = starSlices?.get(i)
            val w = if (slice != null) {
                winStarH * (slice.width.toDouble() / slice.height.toDouble())
            } else {
                winStarH
            }
            return w to winStarH
        }
        fun starSlotXY(i: Int): Pair<Double, Double> = (winCx + (i - 1) * winStarStep) to winStarsCy

        fun Container.starSprite(i: Int): Container {
            val (w, h) = starSlotSize(i)
            val (x, y) = starSlotXY(i)
            val holder = container().xy(x, y)
            val slice = starSlices?.get(i)
            if (slice != null) {
                holder.image(slice) { size(w, h) }.xy(-w / 2.0, -h / 2.0)
            } else {
                holder.uiGraphics().updateShape {
                    drawStar(0.0, 0.0, h / 2.0, h * 0.2, inkGold)
                }
            }
            return holder
        }

        for (i in 0 until 3) {
            winCard.starSprite(i).also {
                it.colorMul = Colors["#4A443A"]
                it.alpha = 0.34
            }.revealAt(0.26)
        }
        val winStars = (0 until 3).map { i -> winCard.starSprite(i).also { it.visible = false } }
        val winStarEarned = booleanArrayOf(false, false, false)

        val winRowH = 19.0 * WS
        val winListTop = winTop + 88.0 * WS
        val winMarkR = 8.0 * WS
        val winRowLabels = listOf(
            levelData.objectiveHint.uppercase(),
            "NO ALERTS RAISED",
            "TARGET TIME ${clockText(levelData.timeTargetSeconds)}"
        )
        val winRows = (0 until 3).map { i ->
            winCard.container().xy(winTextL, winListTop + i * winRowH)
                .revealAt(WIN_ROW_0 + i * WIN_STAR_STEP, slide = 14.0 * WS)
        }
        for (i in 0 until 3) winRows[i].winText(winRowLabels[i], 14.0, inkStrong, 0.0, 0.0)
        val winRowMarks = (0 until 2).map { i ->
            winRows[i].uiGraphics().xy(winTextW - winMarkR, 7.5 * WS)
        }
        val winTimeValue = winRows[2].winText("", 14.0, inkFaint, 0.0, 0.0)

        val winRuleY = winTop + 150.0 * WS
        val winPayoutRow = winCard.container().xy(winTextL, winRuleY)
        winPayoutRow.uiGraphics().updateShape {
            fill(inkRuleColor) { rect(0.0, 0.0, winTextW, 1.6 * WS) }
        }
        val winBountyCaption = winPayoutRow.winText("BOUNTY", 11.0, inkFaint, 0.0, 8.0 * WS)
        winBountyCaption.xy((winTextW - winBountyCaption.width) / 2.0, winBountyCaption.y)
        val winCoinR = 7.5 * WS
        val winCoinGap = 6.0 * WS
        val winBountyCoin = winPayoutRow.uiGraphics()
        winBountyCoin.updateShape { drawCoinIcon(winCoinR) }
        val winBountyValue = winPayoutRow.winText("", 20.0, inkGold, 0.0, 20.0 * WS)
        winPayoutRow.revealAt(WIN_PAYOUT_AT)

        val winButtons = winContainer.container()
        val winCardX = (canvasW - winCardW) / 2.0
        val winBtnL = winCardX + winCardW * 0.06
        val winBtnR = winCardX + winCardW * 0.94
        val winBtnGap = 12.0 * WS
        val winBtnW = (winBtnR - winBtnL - 2.0 * winBtnGap) / 3.0
        val winNextLabel = if (nextLevel != null) "NEXT MISSION" else "ALL CLEAR!"
        val winBtnLabels = listOf("RETRY", "MAIN MENU", winNextLabel)
        var winBtnH = winCardH * 0.092
        var winBtnFitGuard = 0
        while (winBtnFitGuard++ < 6) {
            val widest = winBtnLabels.maxOf { winButtons.paperMenuBtnWidth(it, winBtnH, bebasFont, paperInk) }
            if (widest <= winBtnW) break
            winBtnH *= (winBtnW / widest).coerceAtLeast(0.85)
        }
        winBtnH = winBtnH.coerceIn(20.0, 52.0)
        val winBtnY = winCardY + winCardH * 0.885

        winButtons.createPaperMenuBtn(
            "RETRY", paperBtnBitmaps[1], winBtnW, winBtnH, winBtnL, winBtnY,
            bebasFont, paperInk, centered = true, playClick = playClick,
            iconDrawer = { drawRestartIcon(paperInk) }
        ) { onRetry() }

        winButtons.createPaperMenuBtn(
            "MAIN MENU", paperBtnBitmaps[2], winBtnW, winBtnH, winBtnL + winBtnW + winBtnGap, winBtnY,
            bebasFont, paperInk, centered = true, playClick = playClick,
            iconDrawer = { drawQuitIcon(false) }
        ) { onReturnToMenu() }

        winButtons.createPaperMenuBtn(
            winNextLabel, paperBtnBitmaps[0], winBtnW, winBtnH,
            winBtnL + 2.0 * (winBtnW + winBtnGap), winBtnY,
            bebasFont, paperInk, centered = true, playClick = playClick,
            iconDrawer = { drawPlayIcon(false) }
        ) { onNextMission() }
        winButtons.revealAt(WIN_BUTTONS_AT)

        val winSkipCatcher = winContainer.solidRect(canvasW, canvasH, Colors.TRANSPARENT)
        var winAnimT = -1.0
        winSkipCatcher.mouse { onClick { if (winAnimT >= 0.0) winAnimT = WIN_ANIM_END } }
        winScrim.mouse { onClick { if (winAnimT >= 0.0) winAnimT = WIN_ANIM_END } }

        winContainer.addUpdater { dt ->
            if (!winContainer.visible || winAnimT < 0.0) return@addUpdater
            if (winAnimT >= WIN_ANIM_END) {
                winSkipCatcher.visible = false
            } else {
                winAnimT += dt.seconds
            }
            val t = winAnimT

            val cardP = (t / WIN_CARD_POP).coerceIn(0.0, 1.0)
            val cardE = easeOutBack(cardP)
            winCardPivot.alpha = (cardP * 2.0).coerceAtMost(1.0)
            winCardPivot.scaleX = 0.90 + 0.10 * cardE
            winCardPivot.scaleY = winCardPivot.scaleX

            for (r in winReveals) {
                val p = ((t - r.start) / WIN_REVEAL_DUR).coerceIn(0.0, 1.0)
                r.view.visible = p > 0.0
                if (p <= 0.0) continue
                val e = easeOutCubic(p)
                r.view.alpha = e
                if (r.slide != 0.0) r.view.x = r.baseX - r.slide * (1.0 - e)
            }

            for (i in 0 until 3) {
                val star = winStars[i]
                if (!winStarEarned[i]) {
                    star.visible = false
                    continue
                }
                val p = ((t - (WIN_STAR_0 + i * WIN_STAR_STEP)) / WIN_STAR_DUR).coerceIn(0.0, 1.0)
                star.visible = p > 0.0
                if (p <= 0.0) continue
                val e = easeOutBack(p)
                star.alpha = (p * 4.0).coerceAtMost(1.0)
                star.scaleX = 1.0 + 1.0 * (1.0 - e)
                star.scaleY = star.scaleX
                star.rotation = (-22.0).degrees * (1.0 - e)
            }
        }

        winContainer.visible = false

        return WinOverlayHandle(
            container = winContainer,
            show = { result, earnedCoins ->
                winContainer.visible = true
                val starsEarned = listOf(result.star1, result.star2, result.star3)
                for (i in 0 until 3) {
                    winStarEarned[i] = starsEarned[i]
                }
                for ((i, met) in listOf(result.star1, result.star2).withIndex()) {
                    winRowMarks[i].updateShape {
                        clear()
                        if (met) drawTickIcon(winMarkR, stampGreen) else drawCrossIcon(winMarkR, stampRed)
                    }
                }
                winTimeValue.text = clockText(result.timeTaken)
                winTimeValue.color = if (result.star3) stampGreen else stampRed
                winTimeValue.xy(winTextW - winTimeValue.width, 0.0)

                winBountyValue.text = "$earnedCoins"
                val bountyGroupW = winCoinR * 2.0 + winCoinGap + winBountyValue.width
                val bountyGroupX = (winTextW - bountyGroupW) / 2.0
                winBountyValue.xy(bountyGroupX + winCoinR * 2.0 + winCoinGap, winBountyValue.y)
                winBountyCoin.xy(
                    bountyGroupX + winCoinR,
                    winBountyValue.y + winBountyValue.height / 2.0
                )
                winAnimT = 0.0
            }
        )
    }
}
