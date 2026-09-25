package game.scene

import com.sample.demo.ads.getContinueAdBridge
import com.sample.demo.lifecycle.GameAppLifecycle
import com.sample.demo.nav.getLevelExitBridge
import com.sample.demo.review.getInAppReviewBridge
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
import korlibs.platform.Platform
import korlibs.korge.service.storage.*
import korlibs.korge.view.*
import korlibs.korge.view.filter.*
import korlibs.korge.view.vector.*
import korlibs.math.geom.*
import korlibs.time.*
import kotlin.math.*

// Guard torch beams are one colour in every state (LightConeView.DEFAULT_COLOR); see the beam
// loop in sceneMain for why.

private class ConveyorAnimator(
    val topImages: List<Image>,
    val botImages: List<Image>,
    val topTileW: Double,
    val botTileW: Double,
    val topY: Double,
    val botY: Double,
    val speed: Double
)

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

    // Canvas & Viewport dimensions
    private var canvasW: Double = 1040.0
    private var canvasH: Double = 480.0
    private val worldZoom: Double = 1.35
    private val baseGroundY: Double = 410.0

    // Runtime flags & timing
    private var isPaused: Boolean = false
    private var isFirstCameraFrame: Boolean = true
    private var totalElapsedSeconds: Double = 0.0
    private var conveyorElapsedSeconds: Double = 0.0

    // Player & movement state
    private var currentGroundingOffset: Double = 0.0
    private var playerWasGroundedBefore: Boolean = false
    private var playerAnimState: String = "idle"
    private var playerFacingLeft: Boolean = false
    private var stepAlternate: Boolean = false
    private var jumpPhase: String = "none"
    private var jumpPhaseElapsed: Double = 0.0
    private var jumpStartY: Double = 0.0
    private var dropFromWalk: Boolean = false
    private var climbExitTimer: Double = 0.0
    private var crouchFallAirborne: Boolean = false
    private var swingExitTimer: Double = 0.0
    private var swingImpactSoundPlayed: Boolean = false
    private var landingAbsorb: Boolean = false
    private var landingAbsorbElapsed: Double = 0.0
    private var crouchPhase: String = "none"
    private var crouchFrameProgress: Double = 0.0
    private var crouchJumpSpringElapsed: Double = -1.0
    private var crouchJumpSpringFrom: Double = 0.0
    private val crouchJumpSpringDuration: Double = 0.09
    private var crouchwalkCycleProgress: Double = 0.0
    private var crouchwalkTransitionProgress: Double = 0.0
    private var crouchwalkInTransition: Boolean = false
    /** Phase through the push gait loop, 0..1 - driven by distance like walkCycleProgress. */
    private var pushCycleProgress: Double = 0.0
    /** True while the gait loop is running, so a stop-start re-enters it at its first frame. */
    private var pushStriding: Boolean = false
    /** Which of the two push clips the sprite currently holds - swapping costs a rebind. */
    private var pushLoopClipLoaded: Boolean = false
    /** Facing is locked for the whole stance: a braced body drags the load back, it does not
     *  turn around. Captured when the lean-in starts. */
    private var pushFacingLeft: Boolean = false
    /** Phase through the wind-walk gait loop, 0..1 - driven by ground distance, like push. */
    private var windCycleProgress: Double = 0.0
    /** True while the wind gait is running, so a stop-start re-enters it at its first frame. */
    private var windStriding: Boolean = false
    /** Which of the two wind clips the sprite currently holds - swapping costs a rebind. */
    private var windLoopClipLoaded: Boolean = false
    /** The wind stance has set this frame's sprite; the frame driver must keep its hands off. */
    private var windOwnsSprite: Boolean = false
    private var walkCycleProgress: Double = 0.0
    private var walkTransitionElapsed: Double = 0.0
    private var walkInTransition: Boolean = false
    private var walkTransitionStartFrame: Int = PlayerAnimations.WALK_TRANSITION_START
    private var walkTransitionCurrentDuration: Double = 0.28
    private var stationaryElapsed: Double = 0.20
    private var crouchStationaryElapsed: Double = 0.25
    private var tapWalkGraceTimer: Double = 0.0

    // Vignette elements
    private var darknessVignetteImg: Image? = null
    private var darknessLeftRect: SolidRect? = null
    private var darknessRightRect: SolidRect? = null
    private var darknessTopRect: SolidRect? = null
    private var darknessBotRect: SolidRect? = null

    // Objectives state
    private var objMainState: Int = 0
    private var objOptState: Int = 0

    // Touch controls state
    private var touchLeft: Boolean = false
    private var touchRight: Boolean = false
    private var touchRightTap: Boolean = false
    private var touchJump: Boolean = false
    private var touchCrouch: Boolean = false
    private var touchInteract: Boolean = false

    // Gadgets state
    private var gadgetsShown: Boolean = false
    private var trayExpand: Double = 0.0
    private var trayBuiltFor: Int = -1
    private var slotLastSpan: Double = -2.0
    private var slotLastLive: Boolean? = null

    // Timers & FX
    private var shieldDeflectFlashTimer: Double = 0.0
    private var shieldFlareTimer: Double = 0.0
    private var bgMusicAppliedVolume: Double = -1.0
    private var cachedProfile: GameProfile = GameProfile()

    // Tutorial state
    private var currentTutorialStep: TutorialStep? = null
    private val completedTutorialStepIds = mutableSetOf<String>()
    private var tutorialAlpha: Double = 0.0
    private var isTutorialFadingIn: Boolean = false
    private var isTutorialFadingOut: Boolean = false
    private var tutorialPulseTimer: Double = 0.0
    private var stepActionCompleted: Boolean = false
    private var stepActionTimer: Double = 0.0
    private var stepActivatedX: Double = 0.0
    private var lastUsedKeyboard: Boolean = false
    private var prevRightPressed: Boolean = false

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
        canvasW = sceneWidth.toDouble().coerceAtLeast(800.0)
        canvasH = sceneHeight.toDouble().coerceAtLeast(480.0)

        // What the OS keeps of this screen, converted from the host's dp/points into this canvas's
        // own units (game.model.ScreenLayout). Landscape puts the notch / Dynamic Island on a SIDE,
        // which is exactly where the D-pad and the jump cluster live, and the home-indicator strip
        // along the bottom edge they are anchored to. Zero on desktop, on Android hardware without
        // a cutout, and whenever no host has published anything - in which case every inset below
        // falls back to the number it has always had.
        val safeInsets = DeviceScreen.safeInsetsForCanvas(canvasW, canvasH)
        val currentLanguage = (try { views.storage["user_language"] } catch (_: Throwable) { null }) ?: "en"

        // --- Loading screen -------------------------------------------------------------
        val loadingBgBitmap = SceneAssets.bitmap("loadingbg.png", minified = false)
        val loadingLogoBitmap = SceneAssets.bitmap("logo_main.png", minified = false)
        val loadingBarTextureBitmap = SceneAssets.bitmap("button1.png", minified = false)
        val loadingFont = SceneAssets.font("BebasNeue-Regular.ttf")

        val loadingScreen = setupLoadingScreen(
            canvasW, canvasH, loadingBgBitmap, loadingLogoBitmap, loadingBarTextureBitmap, loadingFont, currentLanguage
        )

        // One full frame so the loading screen is actually painted before the loads below
        delayFrame()

        val totalLoadSteps = 36
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
            loadedWorld.spawnGraceTimer = 2.0
            markLoadProgress()
            val loadedAnimations = PlayerAnimations.load()
            markLoadProgress()
            val loadedSounds = GameAudio.load()
            markLoadProgress()
            Triple(loadedWorld, loadedAnimations, loadedSounds)
        } catch (e: Throwable) {
            // Also to console, not just on-screen: the on-screen text doesn't wrap, so a long
            // exception message (e.g. a full simulator sandbox path) can render almost entirely
            // off-canvas - confirmed exactly this way in CI (2026-09-12), the on-screen render cut
            // off mid-path with no way to read the actual mismatched filename from a screenshot
            // alone. println goes to stdout, which ios-build.yml already captures via
            // --console-pty and dumps as plain, searchable text.
            println("[GameplayScene] LEVEL LOAD FAILED: ${e::class.simpleName}: ${e.message}\n${e.stackTraceToString()}")
            loadingScreen.dismiss()
            solidRect(sceneWidth, sceneHeight, Colors["#16161d"])
            text(
                "LEVEL LOAD FAILED\n\n${e::class.simpleName}: ${e.message}\n\n${e.stackTraceToString().take(1200)}",
                textSize = 14.0,
                color = Colors.RED
            ).xy(16.0, 16.0)
            return
        }
        // Unlike the player's frames this one may be absent (the iOS shell does not bundle
        // resources/ yet - see guidelines "Audio" for the known gap) and a guard without art is
        // still a guard, so a failure here falls back to the old black-rect body, the same way
        // every SceneAssets.bitmap() load degrades to null rather than taking the level down.
        val guardAnimations = try {
            GuardAnimations.load()
        } catch (e: Throwable) {
            println("[GuardAnimations] load failed, guards fall back to plain rects: $e")
            null
        }
        markLoadProgress()
        val sfxContext = coroutineContext
        val levelStorage: LevelStorage = MapBackedLevelStorage(
            getRaw = { views.storage[it] },
            setRaw = { k, v -> views.storage[k] = v }
        )
        val profileStorage: GameProfileStorage = MapBackedGameProfileStorage(
            getRaw = { views.storage[it] },
            setRaw = { k, v -> views.storage[k] = v }
        )

        isPaused = false

        val bgFileName = levelData.resolvedBackgroundImage
        val bitmaps = loadGameplayBitmaps(bgFileName, ::markLoadProgress)
        val bgmgBitmap = bitmaps.bgmgBitmap
        val crateBitmap = bitmaps.crateBitmap
        val chainedCrateBitmap = bitmaps.chainedCrateBitmap
        val chainedCrate2Bitmap = bitmaps.chainedCrate2Bitmap
        val fenceBitmap = bitmaps.fenceBitmap
        val fence2Bitmap = bitmaps.fence2Bitmap
        val barrelBitmap = bitmaps.barrelBitmap
        val woodCrateBitmap = bitmaps.woodCrateBitmap
        val poleBitmap = bitmaps.poleBitmap
        val craneBitmap = bitmaps.craneBitmap
        val tableBitmap = bitmaps.tableBitmap
        val cameraBitmap = bitmaps.cameraBitmap
        val laserEmitterBitmap = bitmaps.laserEmitterBitmap
        val conveyorTopBitmap = bitmaps.conveyorTopBitmap
        val conveyorMidBitmap = bitmaps.conveyorMidBitmap
        val conveyorBotBitmap = bitmaps.conveyorBotBitmap
        val hookBitmap = bitmaps.hookBitmap
        val truckBitmap = bitmaps.truckBitmap
        val entranceBitmap = bitmaps.entranceBitmap
        val exitFenceBitmap = bitmaps.exitFenceBitmap
        val l4endBitmap = bitmaps.l4endBitmap
        val exitLvl7Bitmap = bitmaps.exitLvl7Bitmap
        val leftBtnBitmap = bitmaps.leftBtnBitmap
        val rightBtnBitmap = bitmaps.rightBtnBitmap
        val crouchBtnBitmap = bitmaps.crouchBtnBitmap
        val jumpBtnBitmap = bitmaps.jumpBtnBitmap
        val interactBtnBitmap = bitmaps.interactBtnBitmap
        val leverBottomBitmap = bitmaps.leverBottomBitmap
        val leverTopBitmap = bitmaps.leverTopBitmap
        val ropeBitmap = bitmaps.ropeBitmap
        val ropeDissolveBitmaps = bitmaps.ropeDissolveBitmaps
        val translucentEffectAlpha = 137.0 / 255.0
        val paperBtnBitmaps = bitmaps.paperBtnBitmaps
        val victoryBtnBitmaps = bitmaps.victoryBtnBitmaps
        val failedBtnBitmaps = bitmaps.failedBtnBitmaps
        val dossierBitmap = bitmaps.dossierBitmap
        val failedBgBitmap = bitmaps.failedBgBitmap
        val gadgetBitmaps = bitmaps.gadgetBitmaps
        val gadgetBoltBitmap = bitmaps.gadgetBoltBitmap
        val successBgBitmap = bitmaps.successBgBitmap
        val starsBitmap = bitmaps.starsBitmap
        val starSlices = bitmaps.starSlices

        loadingScreen.dismiss()

        // Combined background & midground layer container (parallax rate 0.2x, looping, unzoomed at native screen height)
        val bgmgContainer = container()
        val bgmgImages = mutableListOf<Image>()
        val bgScale = if (bgFileName == "metalbg.png" && bgmgBitmap != null) {
            (1000.0 * worldZoom) / bgmgBitmap.width
        } else if (bgmgBitmap != null) {
            canvasH / bgmgBitmap.height
        } else {
            1.0
        }
        val bgmgTileW = if (bgmgBitmap != null) {
            val tileW = bgmgBitmap.width * bgScale
            val count = max(6, (canvasW / tileW).toInt() + 4)
            for (i in 0 until count) {
                val img = bgmgContainer.image(bgmgBitmap) {
                    size(tileW + 1.0, if (bgFileName == "metalbg.png") bgmgBitmap.height * bgScale else canvasH)
                }.xy(i * tileW, 0.0)
                bgmgImages.add(img)
            }
            tileW
        } else {
            bgmgContainer.solidRect(canvasW, canvasH, Colors["#16161d"])
            800.0
        }

        // Distance marker decals on Level 4 background wall (countdown stencils at 30m intervals across 150m)
        val wallDecalsContainer = bgmgContainer.container()
        if (bitmaps.wallMarkerBitmaps.isNotEmpty() && bgmgBitmap != null) {
            val milestoneLabels = listOf("150m", "120m", "90m", "60m", "30m", "0m")
            // Countdown markers spaced at 30-meter intervals (1500.0 world units apart) across the 150m conveyor:
            // 150m (start, 0m traversed): worldX = 242.0 (texX = 525 on Panel A, clearance > 220px from beams)
            // 120m (30m traversed): worldX = 1742.0 (texX = 1611 on Panel C, clearance > 200px from beams)
            //  90m (60m traversed): worldX = 3242.0 (texX = 525 on Panel A)
            //  60m (90m traversed): worldX = 4742.0 (texX = 1611 on Panel C)
            //  30m (120m traversed): worldX = 6242.0 (texX = 525 on Panel A)
            //   0m (150m traversed): worldX = 7742.0 (texX = 1611 on Panel C, right before exit gate at 7880)
            for (stepIndex in milestoneLabels.indices) {
                val label = milestoneLabels[stepIndex]
                val bmp = bitmaps.wallMarkerBitmaps[label] ?: continue
                val dw = bmp.width * bgScale
                val dh = bmp.height * bgScale
                val milestoneWorldX = 242.0 + stepIndex * 1500.0
                val dx = milestoneWorldX * worldZoom - dw / 2.0
                val dy = 412.5 * bgScale - dh / 2.0
                wallDecalsContainer.image(bmp) {
                    size(dw, dh)
                }.xy(dx, dy)
            }
        }

        // Everything inside worldView scrolls with the camera; HUD & Touch controls stay fixed.
        val worldView = container()
        worldView.scale(worldZoom)
        val initialPlayerCenterX = world.player.x + world.player.width / 2.0
        val initialDesiredWorldViewX = (canvasW / 2.0) - initialPlayerCenterX * worldZoom
        val initialMinWorldViewX = canvasW - world.worldWidth * worldZoom
        val initialWorldViewX = initialDesiredWorldViewX.coerceIn(initialMinWorldViewX.coerceAtMost(0.0), 0.0)
        val baseWorldViewY = if (bgFileName == "bglvl7.png") {
            val lvl7GroundY = world.platforms.firstOrNull { it.y >= 400.0 && it.width >= 1000.0 }?.y ?: 440.0
            canvasH * (488.0 / 724.0) - (lvl7GroundY * worldZoom)
        } else {
            canvasH - (baseGroundY + 70.0) * worldZoom
        }
        worldView.xy(initialWorldViewX, baseWorldViewY)
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
            if (bgFileName == "bglvl7.png" && platform.y >= 400.0 && platform.width >= 1000.0) {
                // Black colour platform over the black floor beam in bglvl7.png (Y=488..534 in background)
                val beamHeightWorld = (534.0 - 488.0) * (canvasH / 724.0) / worldZoom
                platCont.solidRect(platform.width, beamHeightWorld, Colors.BLACK)
            } else {
                renderRoughBlock(platCont, platform.width, platform.height, seed = (platform.x * 47.0 + platform.y).toLong())
            }
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
        // A level with its own extraction structure (LevelLayout.exitStructure - so far only level
        // 6's exitlvl7.png, the shed and its yard fence in one silhouette) draws that instead of
        // the shared booth + fence pair below. Same contract as those - purely decorative, the real
        // trigger is world.exitZone - but the box comes from the level rather than from exitZone,
        // because this one is placed against the level's own geometry: it stands on the ground and
        // its balcony deck meets the hanging platform's far tip. See LEVEL_6_LAYOUT, section 5.
        val exitStructureRect = levelData.layout?.exitStructure
        if (exitStructureRect != null && exitLvl7Bitmap != null) {
            cullable(
                worldView.image(exitLvl7Bitmap) {
                    size(exitStructureRect.width, exitStructureRect.height)
                }.xy(exitStructureRect.x, exitStructureRect.y),
                exitStructureRect.x, exitStructureRect.width
            )
        } else if (levelData.id != "level_4" && entranceBitmap != null) {
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

        // Chain & rigging above an anchor point, tiled all the way up to the ceiling (-1000.0) -
        // shared by renderHangingCrate's own chain (steps 2+3 below) and, on request ("add the
        // same effects as the chains in hanging crates"), the freestanding pole below. sourceBmp's
        // (cropX, 0, cropW, cropY) region is the chain art; cropY is also its pixel height, and
        // scale maps it from source pixels to this draw's own width. Purely visual, no collision.
        fun renderChainAbove(parent: Container, sourceBmp: Bitmap, cropX: Int, cropY: Int, cropW: Int, width: Double, topY: Double, maxTopY: Double = -1000.0) {
            val scale = width / cropW.toDouble()
            val chainDrawH = cropY * scale
            val chainTopY = topY - chainDrawH
            val chainSlice = sourceBmp.slice(RectangleInt(cropX, 0, cropW, cropY))
            parent.image(chainSlice) {
                size(width, chainDrawH)
            }.xy(0.0, chainTopY)

            val linkSrcH = 160
            val linkDrawH = linkSrcH * scale
            val linkSlice = sourceBmp.slice(RectangleInt(cropX, 0, cropW, linkSrcH))
            var tileY = chainTopY - linkDrawH
            while (tileY >= maxTopY) {
                parent.image(linkSlice) {
                    size(width, linkDrawH)
                }.xy(0.0, tileY)
                tileY -= linkDrawH
            }
        }

        fun renderHangingCrate(
            parent: Container,
            width: Double,
            height: Double,
            crateY: Double = 0.0,
            isVariant1: Boolean,
            maxTopY: Double = -1000.0
        ) {
            val sourceBmp = (if (isVariant1) chainedCrateBitmap else chainedCrate2Bitmap) ?: return
            val cropX = if (isVariant1) 26 else 235
            val cropY = if (isVariant1) 1222 else 1134
            val cropW = if (isVariant1) 971 else 555
            val cropH = if (isVariant1) 226 else 287

            // 1. Crate: the solid rectangular platform at the bottom of the asset.
            val crateSlice = sourceBmp.slice(RectangleInt(cropX, cropY, cropW, cropH))
            parent.image(crateSlice) {
                size(width, height)
            }.xy(0.0, crateY)

            // 2 & 3. Chain & rigging above the crate, tiled up to maxTopY. Purely visual with
            // NO collision box, so the player can freely jump onto the crate.
            renderChainAbove(parent, sourceBmp, cropX, cropY, cropW, width, crateY, maxTopY)
        }

        // woodcrate2.png (1536x1024, replacing the earlier woodencratenew.png on request) carries a
        // transparent margin around the actual crate silhouette too (strict alpha bbox, threshold
        // >10, same measuring method as table.png's own crop and the previous woodcrate.png crop -
        // PowerShell + System.Drawing.Bitmap.GetPixel, no Python/ImageMagick in this environment).
        // Cropped to content once, same as table.png's legSlice/plankSlice, so it sits flush on the
        // ground instead of leaving visible empty space below the art.
        val woodCrateSlice = woodCrateBitmap?.slice(RectangleInt(165, 144, 1206, 721))

        // Tactical boxes, step crates, hanging chained crates, and perimeter fences
        for (box in world.boxes) {
            if (box.width <= 0.0) continue
            val boxContainer = worldView.container().xy(box.x, box.y)
            cullable(boxContainer, box.x, box.width)

            // 0. Forced plain platforms (LevelLayout.plainPlatforms) - a box that would otherwise
            // fall into one of the crate-shaped size heuristics below (6/6b) purely by coincidence
            // of its own dimensions, but is meant to read as a plain structural block instead (e.g.
            // LEVEL_6_LAYOUT's own cranePlatform, short enough to trip rule 6b's crate look on
            // request: "replace the crate with a platform with SAME SIZE" - same size, different
            // art, so this is an explicit opt-out rather than a dimension change).
            if (box in world.plainPlatforms) {
                renderRoughBlock(boxContainer, box.width, box.height, seed = (box.x * 101.0 + box.y).toLong())
            }
            // 1. Fence 1 (Foreground starting perimeter fence)
            else if ((box == world.fence1 || (box.width in 140.0..165.0 && box.height in 130.0..155.0 && box.x < 300.0)) && fenceBitmap != null) {
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
            // collision/climb box under the hood - see LEVEL_4_LAYOUT's comment - so it's tiled
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
            // 3a. Wood crates (woodcrate2.png) - a box tagged in LevelLayout.woodCrates so it can be
            // given this distinct art without changing anything about how it collides or climbs
            // (still a plain box in world.boxes). Tiled the same way barrels are, in real 48-unit
            // increments, so a taller one draws as multiple crates stacked rather than one
            // stretched image - same reasoning as rule 6 below.
            else if (box in world.woodCrates && woodCrateSlice != null) {
                val woodCrateTileHeight = 48.0
                if (box.height <= woodCrateTileHeight + 0.01) {
                    boxContainer.image(woodCrateSlice) {
                        size(box.width, box.height)
                    }.xy(0.0, 0.0)
                } else {
                    var remaining = box.height
                    var tileY = box.height
                    while (remaining > 0.0) {
                        val h = minOf(woodCrateTileHeight, remaining)
                        tileY -= h
                        boxContainer.image(woodCrateSlice) {
                            size(box.width, h)
                        }.xy(0.0, tileY)
                        remaining -= h
                    }
                }
            }
            // 3b. Tables (table.png): drawn in their own pass below, once per art rect, since
            // the collision under one is two boxes (a plank and a leg, LevelLayout.tableParts)
            // with an open underside for a guard to stand in. Nothing to draw per box here. A
            // table decoration that also collides (LevelLayout.tableDecorations - now allowed to
            // double as a real obstacle, not just flavor) is drawn by that same dedicated pass
            // too, so it's excluded here for the same reason - one image, not two stacked on
            // top of each other.
            else if (box in world.tables || box in world.tableParts || box in world.tableDecorations) {
                // covered by the table art
            }
            // 3b2. A crane (LevelLayout.cranes) - covered by the dedicated crane-rendering pass
            // below, same reasoning as tableParts just above.
            else if (world.cranes.any { box in it.collisionBoxes }) {
                // covered by the crane art
            }
            // 3c. Conveyors (conveyor.png): drawn in dedicated conveyor pass below
            else if (world.conveyors.any { it.bounds == box }) {
                // covered by the conveyor art pass below
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
            // 6. Tactical Crates (single crates, 2 stacked, 3 stacked)
            else if (box.width in 50.0..85.0 && box.height in 40.0..160.0 && crateBitmap != null) {
                val count = (box.height / 48.0).toInt().coerceAtLeast(1)
                val tileH = box.height / count
                for (i in 0 until count) {
                    boxContainer.image(crateBitmap) {
                        size(box.width, tileH)
                    }.xy(0.0, i * tileH)
                }
            }
            // 6b. Step Crate (matches bounding box exactly)
            else if (box.height < 70.0 && box.width < 150.0 && crateBitmap != null) {
                boxContainer.image(crateBitmap) {
                    size(box.width, box.height)
                }.xy(0.0, 0.0)
            }
            // 7. Long Structural Platforms and Blocks (Solid blocks with tiny rough edge irregularities)
            else {
                if (bgFileName == "bglvl7.png" && (box.height <= 30.0 || box.width >= 1000.0)) {
                    // bglvl7.png already depicts the metal duct ceiling; avoid drawing an opaque black block across it
                } else {
                    renderRoughBlock(boxContainer, box.width, box.height, seed = (box.x * 101.0 + box.y).toLong())
                }
            }
        }

        // Tables: table.png (2048x512) bakes the leg+brace assembly into its own rightmost ~8.7%
        // (columns 1870-2048, full height) and the flat repeating slab into the rest (columns
        // 0-1870, rows 0-102 only - the slab's own band, not the transparent drop below it that
        // the leg's crop needs). Every collidable part (LevelLayout.tableParts - the mantle face
        // the player climbs and the thin slab past it) draws with the flat-slab crop regardless of
        // its own shape, so the climb face reads as a thick support bracket rather than a distinct
        // leg object; only purely decorative pieces (LevelLayout.tableDecorations) get the leg's
        // own crop, mirrored on disk (this GL backend corrupts a runtime scaleX flip) so its brace
        // leans the right way wherever that decoration happens to sit.
        if (tableBitmap != null) {
            val legSlice = tableBitmap.slice(RectangleInt(1870, 0, 178, 512))
            val plankSlice = tableBitmap.slice(RectangleInt(0, 0, 1870, 102))
            for (part in world.tableParts) {
                val partCont = worldView.container().xy(part.x, part.y)
                partCont.image(plankSlice) {
                    size(part.width, part.height)
                }.xy(0.0, 0.0)
                cullable(partCont, part.x, part.width)
            }
            // Purely decorative table pieces (LevelLayout.tableDecorations) - not in world.boxes,
            // not occluders, no part in reaching the table. A support post drawn for flavor at one
            // end while the real climb happens elsewhere; always the leg's own crop, since a plank
            // has no reason to exist without collision.
            for (deco in world.tableDecorations) {
                cullable(
                    worldView.image(legSlice) {
                        size(deco.width, deco.height)
                    }.xy(deco.x, deco.y),
                    deco.x, deco.width
                )
            }
        }

        // The visual effect that makes chainedcrate.png's own chain read as different from every
        // other (fully opaque, near-black) element in this game: sampled directly from that asset's
        // chain pixels (R=18 G=22 B=28 A=137, vs. its crate's solid R=0 G=0 B=0 A=255) - the chain
        // isn't drawn with any runtime filter, that translucency is baked into the art. Reused as a
        // runtime alpha wherever something needs that same "lighter, washed-out" look without its
        // own pre-multiplied art - currently poles (below), poleCamera (world.translucentCameras),
        // and disabled interact buttons.

        // Freestanding mounting poles (pole.png) - e.g. poleCamera's own mount in LEVEL_3_LAYOUT.
        // Not in world.boxes (no collision, "not interactable" on request) but still real drawn
        // geometry. The chain this used to have above it (renderChainAbove) was removed on request
        // ("remove the chain from the camera pole") - the helper is still there and still used by
        // renderHangingCrate, only the pole's own call to it was removed.
        if (poleBitmap != null) {
            // pole.png (1024x1536) carries a wide transparent margin around the actual pole
            // silhouette (strict alpha bbox, threshold >10, same measuring method as every other
            // asset crop in this file) - stretching the raw image into a pole's exact bounds would
            // leave visible empty space around the art. Cropped to content once, same pattern as
            // woodCrateSlice/tableBitmap's own crops.
            val poleSlice = poleBitmap.slice(RectangleInt(426, 9, 170, 1496))
            for (pole in world.poles) {
                val poleContainer = worldView.container().xy(pole.x, 0.0)
                cullable(poleContainer, pole.x, pole.width)
                poleContainer.image(poleSlice) {
                    size(pole.width, pole.height)
                }.xy(0.0, pole.y).also { it.alpha = translucentEffectAlpha }
            }
        }

        // Background cranes (see CraneDef) - e.g. LEVEL_6_LAYOUT's own one on its own small
        // platform next to lever_3. Drawn as three pieces - a fixed boom tip cap, its truss tiled
        // [CraneDef.tileCount] times, then the fixed cab/tracked-base piece - so the boom can be
        // made genuinely longer than crane.png's own natural proportions ("cut from the middle and
        // copy a part to make it longer"), not just one image scaled up (which only makes
        // everything bigger together, never actually lengthens the boom relative to the rest of
        // the machine - reported directly as "you didn't even make it longer").
        //
        // crane.png (1774x887) carries a wide transparent margin around the actual machine, plus a
        // couple of stray near-invisible pixels well past the tracks that throw off PIL's own
        // getbbox() (same lesson as table.png's own crop) - the real content is a strict alpha bbox
        // (threshold >10) of (25,262)-(1732,713), i.e. 1708x452. Within that crop: the tip cap is
        // x:0..161; the truss's own measured repeat period is 126px (autocorrelated directly
        // against the source image), taken here starting exactly where the tip cap ends (x:161) so
        // the very first tile is phase-aligned with it - an earlier attempt started the tile crop
        // at an arbitrary offset instead and the seam it left is what actually read as "weird"; the
        // cab/tracked-base is the fixed region x:685..1708 (1023 wide) - both the tip cap and tiles
        // stay at y:7..88 (the thin boom band), while the cab spans the crop's own full height.
        // CraneDef.tileCount only ever adds tiles BEYOND 161+126*n reaching 685 (n≈4.16) - fewer
        // than that just reconstructs the original image's own boom length and reads as no longer
        // at all, the exact bug being fixed here.
        if (craneBitmap != null) {
            val craneTipCapSlice = craneBitmap.slice(RectangleInt(25 + 0, 262 + 7, 161, 81))
            val craneTileSlice = craneBitmap.slice(RectangleInt(25 + 161, 262 + 7, 126, 81))
            val craneCabSlice = craneBitmap.slice(RectangleInt(25 + 685, 262, 1023, 452))
            for (crane in world.cranes) {
                val scale = crane.height / 452.0
                val tipCapWidth = scale * 161.0
                val tileWidth = scale * 126.0
                val cabWidth = scale * 1023.0
                val boomHeight = scale * 81.0
                val boomY = crane.y + scale * 7.0
                val totalWidth = tipCapWidth + crane.tileCount * tileWidth + cabWidth

                val craneContainer = worldView.container().xy(crane.x, 0.0)
                cullable(craneContainer, crane.x, totalWidth)

                var cursorX = 0.0
                craneContainer.image(craneTipCapSlice) {
                    size(tipCapWidth, boomHeight)
                }.xy(cursorX, boomY)
                cursorX += tipCapWidth
                repeat(crane.tileCount) {
                    craneContainer.image(craneTileSlice) {
                        size(tileWidth, boomHeight)
                    }.xy(cursorX, boomY)
                    cursorX += tileWidth
                }
                craneContainer.image(craneCabSlice) {
                    size(cabWidth, crane.height)
                }.xy(cursorX, crane.y)
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

        // Levers
        val leverHandles = ArrayList<Pair<Lever, Container>>()
        if (leverBottomBitmap != null && leverTopBitmap != null) {
            for (lever in world.levers) {
                // Ground the base 1.0px into the solid platform so it sits flush with zero floating gap
                val leverCont = worldView.container().xy(lever.x, lever.y + 1.0)
                cullable(leverCont, lever.x, lever.width)

                // Handle layer placed first so the pivot is seated inside the socket behind the base housing
                val handleW = 3.2
                val handleH = 20.0
                val pivotX = lever.width / 2.0
                val pivotY = 2.0
                val handleCont = leverCont.container().xy(pivotX, pivotY)
                handleCont.image(leverTopBitmap) {
                    size(handleW, handleH)
                }.xy(-handleW / 2.0, -handleH)

                handleCont.rotation = if (lever.isActivated) (25.0).degrees else (-25.0).degrees
                leverHandles.add(lever to handleCont)

                // Base housing rendered on top
                leverCont.image(leverBottomBitmap) {
                    size(lever.width, lever.height)
                }.xy(0.0, 0.0)
            }
        }

        // Hook Crates (crates attached to swing hooks via rope that block them until detached)
        class HookCrateVisual(
            val hc: HookCrate,
            val crateCont: Container,
            val ropeImage: Image?,
            var dissolveTimer: Double = 0.0
        )

        val hookCrateVisuals = world.hookCrates.map { hc ->
            var ropeImg: Image? = null

            val initialRopeBitmap = ropeDissolveBitmaps.firstOrNull() ?: ropeBitmap
            if (hc.ropeLength > 0.0 && initialRopeBitmap != null) {
                val ropeW = 13.0
                val knotOverlapHook = 5.0
                val plankOverlapCrate = 10.0
                val ropeH = hc.ropeLength + knotOverlapHook + plankOverlapCrate
                val ropeWorldX = (hc.bounds.x + hc.bounds.width / 2.0) - ropeW / 2.0
                val ropeWorldY = (hc.bounds.y - hc.ropeLength) - knotOverlapHook

                // Rope placed in worldView behind crateCont so crate planks overlap rope seamlessly
                ropeImg = worldView.image(initialRopeBitmap) {
                    size(ropeW, ropeH)
                }.xy(ropeWorldX, ropeWorldY)
                cullable(ropeImg, ropeWorldX, ropeW)
            }

            val crateCont = worldView.container().xy(hc.bounds.x, hc.bounds.y)
            cullable(crateCont, hc.bounds.x, hc.bounds.width)
            if (woodCrateSlice != null) {
                crateCont.image(woodCrateSlice) {
                    size(hc.bounds.width, hc.bounds.height)
                }.xy(0.0, 0.0)
            } else {
                crateCont.solidRect(hc.bounds.width, hc.bounds.height, Colors["#8B5A2B"])
            }
            HookCrateVisual(hc, crateCont, ropeImg)
        }

        // Moving hanging containers (dynamic platforming)
        val movingPlatformContainers = world.movingPlatforms.map { mp ->
            val crateCont = worldView.container().xy(mp.x, mp.y)
            renderHangingCrate(crateCont, mp.width, mp.height, 0.0, mp.isVariant1)
            crateCont
        }

        val isL4 = levelData.id == "level_4"

        // Level 4 Facility Interior Chamber & Overhead Crane Monorail:
        // Rendered behind crates and conveyor so newly spawned crates/cranes emerge from inside the facility.
        if (isL4) {
            val conveyorRight = world.conveyors.firstOrNull()?.bounds?.right ?: 7760.0
            val groundY = 440.0
            val chamber = worldView.container().xy(conveyorRight, groundY - 360.0)
            // Dark interior shadow depth behind the portal opening
            chamber.solidRect(180.0, 360.0, Colors["#07090d"]).xy(0.0, 0.0)
            // Vertical structural depth perspective ribs inside warehouse
            for (i in 1..4) {
                chamber.solidRect(3.0, 360.0, Colors["#131720"]).xy(i * 38.0, 0.0)
            }
            cullable(chamber, conveyorRight, 180.0)
        }

        // Conveyor crates (crates carried dynamically with the conveyor belt)
        val conveyorCrateContainers = world.conveyorCrates.map { crate ->
            val crateCont = worldView.container().xy(crate.x, crate.y)
            if (crate.isHanging) {
                renderHangingCrate(crateCont, crate.width, crate.height, 0.0, crate.isVariant1)
            } else if (crateBitmap != null) {
                val count = (crate.height / 48.0).toInt().coerceAtLeast(1)
                val tileH = crate.height / count
                for (i in 0 until count) {
                    crateCont.image(crateBitmap) {
                        size(crate.width, tileH)
                    }.xy(0.0, i * tileH)
                }
            }
            crateCont
        }

        // Laser hazards: realistic volumetric gradient beams with blooming contact flares
        val laserVisuals = LaserVisual.createAll(worldView, world.lasers, laserEmitterBitmap)

        // Level 7 Vent Infiltration: duct corridor framing, exhaust fans, camera bots, and steam pipes
        val ventFanVisuals = VentFanVisual.createAll(
            worldView, world.fans,
            bitmaps.fanBladeBitmap, bitmaps.fanCoverBitmap
        )
        val cameraBotVisuals = CameraBotVisual.createAll(
            worldView, world.cameraBots,
            bitmaps.robotBodyBitmap, bitmaps.robotWheelBitmap
        )
        val steamPipeVisuals = SteamPipeVisual.createAll(
            worldView, world.steamPipes,
            bitmaps.steamNozzleUpBitmap, bitmaps.steamNozzleDownBitmap
        )

        // Conveyors: infinite-repeating conveyor belt cut from middle of conveyor.png,
        // animated with top layer moving forward and bottom layer moving in reverse.
        val conveyorAnimators = setupConveyorAnimators(
            worldView, world.conveyors,
            conveyorTopBitmap, conveyorMidBitmap, conveyorBotBitmap,
            ::cullable
        )

        // Level 4 Extraction Terminal Machine (l4end.png): large industrial housing enclosing
        // the crate loop spawn point so newly wrapped crates emerge naturally from inside.
        if (isL4 && l4endBitmap != null) {
            val conveyorRight = world.conveyors.firstOrNull()?.bounds?.right ?: 7760.0
            val l4endHeight = 360.0
            val l4endWidth = l4endHeight * (l4endBitmap.width.toDouble() / l4endBitmap.height.toDouble())
            val groundY = 440.0
            val l4endX = conveyorRight
            val l4endY = groundY - l4endHeight
            val l4endImg = worldView.image(l4endBitmap) {
                size(l4endWidth, l4endHeight)
            }.xy(l4endX, l4endY)
            cullable(l4endImg, l4endX, l4endWidth)

            // Portal entrance frame accents:
            // 1. Overhead lintel hood with safety hazard stripes above portal (x in [l4endX - 2, l4endX + 75], y in [138, 150])
            val portalFrame = worldView.container()
            val hoodW = 75.0
            val hoodH = 10.0
            val hoodY = 140.0
            portalFrame.solidRect(hoodW, hoodH, Colors["#222732"]).xy(l4endX - 2.0, hoodY)
            var sx = l4endX - 2.0
            while (sx < l4endX + hoodW - 6.0) {
                portalFrame.solidRect(7.0, hoodH, Colors["#e5b014"]).xy(sx, hoodY)
                sx += 14.0
            }
            // Warning beacon above doorway
            portalFrame.solidRect(6.0, 6.0, Colors["#f59e0b"]).xy(l4endX + hoodW - 12.0, hoodY - 8.0)

            // 2. Heavy steel doorway baseplate connecting conveyor belt corner flush to machine (zero gap)
            val baseplate = worldView.container()
            baseplate.solidRect(6.0, 26.0, Colors["#2d3544"]).xy(l4endX - 3.0, 414.0)
            baseplate.solidRect(2.0, 26.0, Colors["#4a576e"]).xy(l4endX - 3.0, 414.0)
            cullable(portalFrame, l4endX, hoodW)
            cullable(baseplate, l4endX - 3.0, 6.0)
        }

        // Guards: torch beams first so they render beneath the bodies. LightConeView, not
        // Graphics - see its header for why the old per-frame rasterised cone was the most
        // expensive thing in the scene. Rebuilt only when the lens, facing or range changed, and
        // skipped entirely while the guard is off-screen; the arrays below hold the last inputs.
        val guardCones = world.allGuards.map { LightConeView().addTo(worldView) }
        val guardConeLensX = DoubleArray(world.allGuards.size) { Double.NaN }
        val guardConeLensY = DoubleArray(world.allGuards.size) { Double.NaN }
        val guardConeFacing = DoubleArray(world.allGuards.size) { Double.NaN }
        val guardConeRange = DoubleArray(world.allGuards.size) { Double.NaN }
        val guardContainers = world.allGuards.map { g -> worldView.container().xy(g.x, g.y) }
        // The body is the idle sprite, scaled so its standing silhouette is exactly the hitbox
        // height and anchored at the feet like the player's - see GuardAnimations for the frame
        // geometry. Without the art (load failed) it is the old black rect plus the old red
        // visor, since a featureless rect shows no facing. With the art there is no visor: the
        // silhouette shows which way he looks, and the detection pip over his head (guardPips)
        // carries state - the owner asked for the rectangle to go.
        val guardBaseScale = world.allGuards.map { g -> g.height / GuardAnimations.SOURCE_SILHOUETTE_HEIGHT }
        val guardFeetAnchorY = GuardAnimations.SOURCE_FEET_Y / GuardAnimations.SOURCE_FRAME_HEIGHT
        // Idle's back heel sits a few rows short of the ground line, so the idle clip is dropped
        // by this much; walk's frames are cut at each frame's own lowest row, so it is not.
        val guardIdleFeetOffset = world.allGuards.map { g ->
            (GuardAnimations.SOURCE_FEET_Y - GuardAnimations.IDLE_FEET_Y) * g.height / GuardAnimations.SOURCE_SILHOUETTE_HEIGHT
        }
        val guardSprites = world.allGuards.mapIndexed { i, g ->
            if (guardAnimations == null) {
                guardContainers[i].solidRect(g.width, g.height, Colors.BLACK)
                null
            } else {
                guardContainers[i].sprite(guardAnimations.idle, Anchor2D(0.5, guardFeetAnchorY)).also { sprite ->
                    sprite.scaleX = guardBaseScale[i] * (if (g.facing >= 0.0) 1.0 else -1.0)
                    sprite.scaleY = guardBaseScale[i]
                    sprite.xy(g.width / 2.0, g.height + guardIdleFeetOffset[i] + GuardAnimations.FEET_GROUND_NUDGE)
                    sprite.playAnimationLooped(guardAnimations.idle, GuardAnimations.IDLE_FRAME_TIME_MS.milliseconds)
                }
            }
        }
        // Walk is driven by distance travelled, exactly like the player's: one gait cycle per
        // (height * stride) units, so the feet stay planted at any patrol speed. Idle <-> walk
        // switches on Guard.isWalking, which the model sets per frame.
        val guardAnimWalking = BooleanArray(world.allGuards.size)
        val guardWalkProgress = DoubleArray(world.allGuards.size)
        val guardPrevX = DoubleArray(world.allGuards.size) { world.allGuards[it].x }
        val guardWalkCycleDistance = world.allGuards.map { g -> g.height * GuardAnimations.WALK_STRIDE_PER_HEIGHT }
        val guardVisors = world.allGuards.mapIndexed { i, g ->
            if (guardAnimations == null) guardContainers[i].solidRect(6.0, 4.0, Colors["#e74c3c"]).xy(g.width - 6.0, 10.0) else null
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
        val cameraContainers = world.cameras.map { c ->
            worldView.container().xy(c.x, c.y).also {
                if (c in world.translucentCameras) it.alpha = translucentEffectAlpha
            }
        }
        // cameranew2.png: same recipe as cameranew.png (ceiling-mount plate + drop neck +
        // ball-swivel joint + camera body, one silhouette, transparent background, 1536x1024) but a
        // cleaner redraw - rounded corners, no stray corner artifacts, and a small separate end-cap
        // segment past the main body. Measured directly off THIS PNG (column/row alpha scans,
        // threshold >10 - same method, numbers differ from the old asset):
        // - The mount plate sits at the top (opaque bbox y 56..~110, x 91..675, centreline
        //   x = 383), a vertical neck ~126px wide runs down to a collar band (widens to ~168 around
        //   y 445..480), then a narrow waist at y~486..494 (back to ~132..138, close to the bare
        //   neck's own width) - the seam this crop splits on.
        // - The ball joint: its leftmost point sits at (287, ~552); the neck/collar's own
        //   centreline (x = 383, the plate's own centre too) gives the joint centre as (383, 552.5)
        //   by symmetry, radius ~96.
        // - The body's long axis was fit from its TOP edge across x=750..1190 (a single straight
        //   line there, slope 0.35 the whole way - i.e. 19.29 degrees below horizontal), not from
        //   one corner, which the body's own thickness would skew off-axis. The small end-cap
        //   segment past the main body's own end doesn't affect this fit (out of that x range).
        // Split at the waist: the mount piece (plate+neck+collar) stays fixed to the beam; the lens
        // piece (ball+arm+body+end-cap) rotates around the joint to track currentAngle, same as a
        // real swivel camera. One shared scale keeps them reading as one assembly.
        // Not a larger scale that would read better on its own: Camera.kt's NECK_LENGTH (13) /
        // LENS_LENGTH (27) are the model's own gameplay geometry, and this render scale has to
        // agree with them - raw neck ~496.5px / raw lens ~1050px (pivot to the body's own front tip,
        // axial distance along the fitted long axis, not a raw corner-to-corner line) - so what's
        // lit matches what's drawn. See the comment on those constants: an earlier, larger pass
        // (23/50, tuned against the previous asset) both drew a visibly oversized camera body and
        // needed a much wider sweep to reach stepCrate2 at all, which let the cone's shallow edge
        // sail over the crate's top into the open corridor beyond it - reported directly against a
        // screenshot.
        val cameraArtScale = Camera.LENS_LENGTH / 1050.0
        val cameraMountCrop = RectangleInt(89, 54, 588, 438) // plate+neck+collar, down to the waist
        val cameraLensCrop = RectangleInt(285, 486, 1134, 492) // ball+arm+body+end-cap, from just above the waist
        // The plate's own top-centre, in raw art pixels - the point that stays flush against the
        // beam's underside (the art hangs the joint+body below the attach point on a real neck, so
        // the attach point is the plate's top, not the hitbox centre).
        val cameraMountAttachRaw = Vec2d(383.0, 56.0)
        val cameraPivotRaw = Vec2d(383.0, 552.5) // the ball joint, see measurements above
        val cameraPivotInMount = Vec2d(cameraPivotRaw.x - cameraMountCrop.x, cameraPivotRaw.y - cameraMountCrop.y)
        val cameraPivotInLens = Vec2d(cameraPivotRaw.x - cameraLensCrop.x, cameraPivotRaw.y - cameraLensCrop.y)
        val cameraAttachInMount = Vec2d(cameraMountAttachRaw.x - cameraMountCrop.x, cameraMountAttachRaw.y - cameraMountCrop.y)
        // The unrotated body's own long axis points down-right at this angle off horizontal (fit
        // above) - currentAngle's convention is 0 = right, PI/2 = straight down, so the pivot
        // container's rotation has to correct for this baseline before it can track currentAngle.
        val cameraArtBaselineAngle = atan2(154.0, 440.0)
        val cameraPivots = world.cameras.mapIndexed { i, c ->
            // Local (width/2, 0): the plate's top-centre, flush against the beam - container origin
            // xy(c.x, c.y) already sits at the beam's own underside (see cameraContainers above).
            val attachLocal = Vec2d(c.width / 2.0, 0.0)
            if (cameraBitmap != null) {
                cameraContainers[i].image(cameraBitmap.slice(cameraMountCrop)) {
                    size(cameraMountCrop.width * cameraArtScale, cameraMountCrop.height * cameraArtScale)
                }.xy(
                    attachLocal.x - cameraAttachInMount.x * cameraArtScale,
                    attachLocal.y - cameraAttachInMount.y * cameraArtScale
                )
            } else {
                // No art (load failed) - old plain grey housing, no rotating lens piece.
                cameraContainers[i].solidRect(c.width, c.height, Colors["#34495e"])
                cameraContainers[i].solidRect(c.width - 4.0, c.height - 4.0, Colors["#2c3e50"]).xy(2.0, 2.0)
            }
            // The joint's position relative to the attach point, scaled - drives the rotating
            // container's own position regardless of whether the art itself loaded.
            val pivotLocal = Vec2d(
                attachLocal.x + (cameraPivotRaw.x - cameraMountAttachRaw.x) * cameraArtScale,
                attachLocal.y + (cameraPivotRaw.y - cameraMountAttachRaw.y) * cameraArtScale
            )
            val pivot = cameraContainers[i].container().xy(pivotLocal.x, pivotLocal.y)
            if (cameraBitmap != null) {
                pivot.image(cameraBitmap.slice(cameraLensCrop)) {
                    size(cameraLensCrop.width * cameraArtScale, cameraLensCrop.height * cameraArtScale)
                }.xy(-cameraPivotInLens.x * cameraArtScale, -cameraPivotInLens.y * cameraArtScale)
            }
            pivot
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
        val walkFeetOffset = (playerSourceFeetY - PlayerAnimations.WALK_FEET_Y) * playerBaseScale
        val crouchFeetOffset = (playerSourceFeetY - PlayerAnimations.CROUCH_FEET_Y) * playerBaseScale
        val jumpLandFeetOffset = (playerSourceFeetY - PlayerAnimations.JUMP_LAND_FEET_Y) * playerBaseScale

        // Smooth Luminous Silhouette Aura (Laser Shield)
        val shieldGlowContainer = playerContainer.container().apply {
            blendMode = BlendMode.ADD
        }
        val shieldCyanTransform = ColorTransform(0f, 0f, 0f, 0.75f, 0, 229, 255, 0)
        val shieldWhiteTransform = ColorTransform(0f, 0f, 0f, 1f, 255, 255, 255, 0)
        val shieldColorFilter = ColorTransformFilter(shieldCyanTransform)
        val shieldBlurFilter = BlurFilter(radius = 1.8)
        shieldGlowContainer.filter = ComposedFilter(listOf(shieldColorFilter, shieldBlurFilter))
        shieldGlowContainer.visible = false

        fun isPlayerOnTruck(): Boolean {
            if (!world.player.isGrounded) return false
            val footCenter = world.player.x + world.player.width / 2.0
            val footY = world.player.y + world.player.height
            val truck = world.truck
            return world.truckParts.any { p ->
                footCenter >= p.left && footCenter <= p.right && kotlin.math.abs(footY - p.top) <= 3.0
            } || (truck != null && footCenter >= truck.left && footCenter <= truck.right && kotlin.math.abs(footY - truck.top) <= 3.0)
        }
        currentGroundingOffset = idleFeetOffset
        playerWasGroundedBefore = world.player.isGrounded

        val playerSprite = playerContainer.sprite(playerAnimations.idle, Anchor2D(0.5, playerFeetAnchorY))
        playerSprite.scaleX = playerBaseScale
        playerSprite.scaleY = playerBaseScale
        playerSprite.xy(world.player.width / 2.0, world.player.height + currentGroundingOffset)
        playerSprite.playAnimationLooped(playerAnimations.idle, PlayerAnimations.IDLE_FRAME_TIME_MS.milliseconds)
        playerAnimState = "idle"
        playerFacingLeft = false
        isFirstCameraFrame = true

        val shieldGlowImage = shieldGlowContainer.image(playerSprite.bitmap, Anchor2D(0.5, playerFeetAnchorY))

        shieldDeflectFlashTimer = 0.0
        val shieldFlareImage = worldView.image(LaserFxAssets.flareBitmap, Anchor2D(0.5, 0.5)).apply {
            size(52.0, 52.0)
            blendMode = BlendMode.ADD
            visible = false
        }
        shieldFlareTimer = 0.0

        val jumpLaunchFrame = PlayerAnimations.JUMP_LAUNCH_START
        val jumpAirborneFrame = PlayerAnimations.JUMP_RISE_START
        val jumpApexFrame = PlayerAnimations.JUMP_APEX
        val jumpTouchdownFrame = PlayerAnimations.JUMP_TOUCHDOWN
        val jumpLandFrame = PlayerAnimations.JUMP_LAND_START
        val jumpLastFrame = PlayerAnimations.JUMP_LAND_END
        val jumpLaunchDuration = 0.05
        val jumpLandDuration = 0.24
        jumpPhase = "none"
        jumpPhaseElapsed = 0.0
        jumpStartY = world.player.y
        dropFromWalk = false
        climbExitTimer = 0.0
        // A fall taken crouched never reaches the jump machine (it keeps the crouched pose all
        // the way down), so its touchdown has to be noticed here to get the landing thud.
        crouchFallAirborne = false
        swingExitTimer = 0.0
        swingImpactSoundPlayed = false

        // Landing absorption: when the player lands while moving, play a brief cushion of the
        // initial touchdown frames (27..28) before handing over to the forward walk stride (frame 5..17).
        // This eliminates the jarring pop from airborne/squat to full-speed run stride.
        landingAbsorb = false
        landingAbsorbElapsed = 0.0
        val landingAbsorbDuration = 0.05
        val landingAbsorbFrames = 2


        // Crouch: entering/exiting are the down/up transition played once; holding pins the last
        // frame. crouchFrameProgress is continuous (not just a phase flag) so re-toggling crouch
        // mid-transition reverses smoothly from wherever the animation currently is, instead of
        // snapping to a fixed pose first.
        // Audio trigger state. Footsteps are edge-triggered off the same distance-driven gait
        // cycle that picks the walk frame, so a step fires when the foot lands rather than on a
        // timer that drifts against the animation whenever speed changes.
        stepAlternate = false
        // One profile read per frame, not four. InMemoryGameProfileStorage.getProfile() hands back
        // a deep copy - a fresh GameProfile plus a copied unlocked-level set plus a copied powerup
        // map - so reading a single volume float allocated three objects. The updater was doing
        // that for the music volume, again for the powerup HUD, and once more per footstep, which
        // at 60fps is a few hundred short-lived objects a second on a heap that is already under
        // pressure from the texture atlas. Refreshed at the top of the updater (and immediately
        // after anything that mutates the profile) so a change made in the menus still lands on
        // the very next frame, exactly as it did when every call re-read storage.
        cachedProfile = profileStorage.getProfile()
        val refreshProfile = { cachedProfile = profileStorage.getProfile() }
        val sfxVolume = { cachedProfile.sfxVolume }
        val musicVolume = { cachedProfile.musicVolume }

        // -1.0 is a sentinel, not a real volume: it means "nothing applied yet", distinct from a
        // legitimate 0.0 (muted). See syncBgMusicVolume's own doc comment for why this exists.
        bgMusicAppliedVolume = -1.0

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
        crouchPhase = "none"
        crouchFrameProgress = 0.0

        // Crouch -> jump: the launch plays the crouch clip backwards at speed (the body springing
        // straight - which is exactly what the footage of the crouch descent is in reverse) before
        // the jump clip's own push-off takes over. Cutting straight to the jump clip instead snaps
        // the silhouette from the crouch's 139 frame-px to the launch frame's 240 in a single
        // frame, which reads as the character teleporting upright.
        crouchJumpSpringElapsed = -1.0
        crouchJumpSpringFrom = 0.0
        // Climb: Player.isClimbing drives the actual world position (see Player.advanceClimb),
        
        val crouchwalkCycleDistance = playerVisualHeight * PlayerAnimations.CROUCHWALK_STRIDE_PER_HEIGHT
        crouchwalkCycleProgress = 0.0
        // Unlike the idle->walk lean-in, the crouch-walk lean-in is distance-driven like its loop.
        // Its 91 frames are already a walk in the footage - they start on the crouch's held pose
        // and build the stride out of it - so they carry the same ground speed as the loop and a
        // fixed duration would either blur them or slide the feet. Progress is in cycles, so the
        // lean-in ends after (91 / 53) cycles of travel and runs straight into the loop's first
        // frame, which is its own next frame in the source.
        val crouchwalkTransitionCycles =
            (PlayerAnimations.CROUCHWALK_TRANSITION_END - PlayerAnimations.CROUCHWALK_TRANSITION_START + 1)
                .toDouble() / PlayerAnimations.CROUCHWALK_LOOP_LENGTH
        crouchwalkTransitionProgress = 0.0
        crouchwalkInTransition = false
        // and its climbProgress picks the frame here, so pose and position stay in step.
        val climbFirstFrame = PlayerAnimations.CLIMB_START
        val climbLastFrame = PlayerAnimations.CLIMB_END
        val climbFrameSpan = climbLastFrame - climbFirstFrame

        // Same contract as the climb's: Player.swingPhase picks the frame here, so the pose and
        // the position it was measured from stay in step.
        val swingFrameSpan = PlayerAnimations.SWING_END - PlayerAnimations.SWING_START

        // One gait cycle covers this much ground; measured off the plate so the feet stay planted.
        val walkCycleDistance = playerVisualHeight * PlayerAnimations.WALK_STRIDE_PER_HEIGHT
        walkCycleProgress = 0.0
        // Same idea for the braced push stride. See PlayerAnimations.PUSH_STRIDE_PER_HEIGHT - it
        // is measured off the LATE cycles of the plate, because the character accelerates through
        // the raw footage and the early frames describe a load that has not started moving yet.
        val pushCycleDistance = playerVisualHeight * PlayerAnimations.PUSH_STRIDE_PER_HEIGHT
        val pushTransitionLastFrame = PlayerAnimations.PUSH_TRANSITION_LAST
        pushCycleProgress = 0.0
        pushStriding = false
        pushLoopClipLoaded = false
        pushFacingLeft = false
        // Same again for the wind stride. Unlike walk and push this one is a cadence knob rather
        // than a foot-planting constraint - see PlayerAnimations.WIND_STRIDE_PER_HEIGHT.
        val windCycleDistance = playerVisualHeight * PlayerAnimations.WIND_STRIDE_PER_HEIGHT
        val windTransitionLastFrame = PlayerAnimations.WIND_TRANSITION_LAST
        // See windCycleProgress below - turns the tap surge into a believable stride cadence.
        val WIND_GAIT_EFFORT_SCALE = 0.55
        windCycleProgress = 0.0
        windStriding = false
        windLoopClipLoaded = false
        val walkTransitionDuration = 0.28
        walkTransitionElapsed = 0.0
        walkInTransition = false
        walkTransitionStartFrame = PlayerAnimations.WALK_TRANSITION_START
        walkTransitionCurrentDuration = walkTransitionDuration
        stationaryElapsed = 0.20
        crouchStationaryElapsed = 0.25
        tapWalkGraceTimer = 0.0
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
        // element is either transient (the mission toast) or diegetic (the detection pip, which
        // rides on the operative in world space). A clean run therefore shows no HUD at all over
        // the action, which is the point of the genre.
        //
        // There is also no full-screen wash on a lethal hit any more (owner request 2026-09-25).
        // A #ff0033 rect used to cover the canvas at 0.45 alpha for 0.25s whenever `onLaserHit` or
        // `onSteamPipeHit` fired. It was hooked to the shared GameWorld callbacks, so it appeared
        // on every level with a laser or a steam pipe, and it fired on the same tick as the game
        // over - painting red over the last frame the player gets to read before the MISSION
        // FAILED card, which is the frame that tells them what killed them.
        //
        // The run timer was removed from the screen, not from the game: it still runs and still
        // decides the third star, and it is reported on the results card at the end. A tenths-
        // resolution clock ticking in the player's eyeline pushes them to rush, which is exactly
        // the wrong instinct in a stealth level.
        //
        // ==========================================
        // DYNAMIC DARKNESS VIGNETTE (Stealth Vision Pool)
        // Layered directly above worldView and below hudLayer / controlsContainer / dialogs
        // ==========================================
        val darknessVignetteSize = 640
        darknessVignetteImg = null
        darknessLeftRect = null
        darknessRightRect = null
        darknessTopRect = null
        darknessBotRect = null

        if (levelData.hasDarknessVignette) {
            val darkBase = Colors["#05070A"]
            val darkMaxAlpha = 0.97
            val darkColor = darkBase.withAd(darkMaxAlpha)
            val vignetteBmp = Bitmap32(darknessVignetteSize, darknessVignetteSize)
            val cx = darknessVignetteSize / 2.0
            val cy = darknessVignetteSize / 2.0
            val r0 = 110.0
            val r1 = 250.0
            val rSpan = r1 - r0

            for (y in 0 until darknessVignetteSize) {
                val dy = y - cy
                for (x in 0 until darknessVignetteSize) {
                    val dx = x - cx
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    val alpha = when {
                        dist <= r0 -> 0.0
                        dist >= r1 -> darkMaxAlpha
                        else -> {
                            val t = (dist - r0) / rSpan
                            darkMaxAlpha * (t * t * (3.0 - 2.0 * t))
                        }
                    }
                    vignetteBmp.setRgba(x, y, darkBase.withAd(alpha))
                }
            }

            val darknessContainer = container()
            darknessVignetteImg = darknessContainer.image(vignetteBmp).size(darknessVignetteSize.toDouble(), darknessVignetteSize.toDouble())
            darknessLeftRect = darknessContainer.solidRect(1.0, 1.0, darkColor)
            darknessRightRect = darknessContainer.solidRect(1.0, 1.0, darkColor)
            darknessTopRect = darknessContainer.solidRect(1.0, 1.0, darkColor)
            darknessBotRect = darknessContainer.solidRect(1.0, 1.0, darkColor)
        }


        // Weather. Both drop layers are parented to bgmgContainer, i.e. BEHIND worldView, so the
        // whole curtain (and the splashes it leaves on the crates and the floor) draws over the
        // sky and behind every box, guard and the player - owner request 2026-09-25, "put the rain
        // effect behind the characters and all the elements". The near layer used to be a child of
        // the scene root, over the top of everything.
        //
        // The lightning wash does NOT move with them: a full-screen flash parented behind the
        // level would light the sky and leave the yard dark, which is backwards. It stays on the
        // scene root, above worldView and below hudLayer, exactly where it was.
        //
        // world.platforms already carries the level's floors, its boxes and its two side walls;
        // RainEffect filters the walls out by height and builds its own landing height map from
        // the rest, so nothing here needs to know which rect is which.
        val rainEffect = if (levelData.hasRain) {
            RainEffect(
                bgLayer = bgmgContainer,
                fgLayer = bgmgContainer,
                initialCanvasW = canvasW,
                initialCanvasH = canvasH,
                flashLayer = this,
                splashSurfaces = world.platforms
            )
        } else null

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
        val objPanel = hudLayer.container().xy(24.0 + safeInsets.left, 20.0 + safeInsets.top)

        val objTitle = objPanel.text(
            Localization.objectives(currentLanguage), textSize = 15.0, font = bebasFont, color = COLOR_PRIMARY
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
            levelData.localizedObjectiveHint(currentLanguage).uppercase(), textSize = 12.5, font = bebasFont, color = COLOR_TEXT_LIGHT
        )
        objMainText.graphicsRenderer = GraphicsRenderer.GPU
        objMainText.xy(objTextX, objRow1Y)

        // "(OPTIONAL)" stays its own view, in the same ink as the objective beside it. It is
        // still a separate view rather than one string because the gap after it is set from its
        // measured width, and because the qualifier may yet want its own treatment.
        val objOptTag = objPanel.text(
            Localization.optional(currentLanguage), textSize = 12.5, font = bebasFont, color = COLOR_TEXT_LIGHT
        )
        objOptTag.graphicsRenderer = GraphicsRenderer.GPU
        objOptTag.xy(objTextX, objRow2Y)

        val objOptText = objPanel.text(
            Localization.finishUnder(clockText(levelData.timeTargetSeconds), currentLanguage),
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
        objMainState = 0
        objOptState = 0
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
        // The top-right HUD cluster (pause bars + gadget bolt) shares one right inset with the
        // gadget slot below - keep the two in step if either moves.
        val hudRightInset = 14.0 + safeInsets.right
        val hudTopInset = 20.0 + safeInsets.top
        val pauseBtn = hudLayer.container().xy(canvasW - hudRightInset - pauseRadius * 2.0, hudTopInset)
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

        // Heard-not-seen indicator: a plain "!" badge without a background circle (owner request
        // 2026-09-20). Drawn on the same Graphics as the clock so a guard never shows both at once;
        // swapped for the real clock the instant that guard's vision actually finds the player.
        fun Graphics.drawInvestigateMark(tint: RGBA, pulse: Double) {
            updateShape {
                clear()
                fill(tint.withAd(0.80 + 0.20 * pulse)) {
                    rect(-1.7, -6.2, 3.4, 7.2)
                    rect(-1.7, 2.8, 3.4, 3.4)
                }
            }
        }

        val guardPips = world.allGuards.mapIndexed { i, g ->
            guardContainers[i].uiGraphics().xy(g.width / 2.0, -14.0).also { it.visible = false }
        }
        val cameraPips = world.cameras.mapIndexed { i, c ->
            cameraContainers[i].uiGraphics().xy(c.width / 2.0, -14.0).also { it.visible = false }
        }
        val cameraBotPips = cameraBotVisuals.map { visual ->
            visual.container.uiGraphics().xy(visual.bot.width / 2.0, -14.0).also { it.visible = false }
        }
        val cameraBotWasDetecting = BooleanArray(cameraBotVisuals.size)
        // ==========================================
        // TACTICAL MOBILE TOUCH CONTROLS (Modern GPU Vectors)
        // ==========================================
        touchLeft = false
        touchRight = false
        touchRightTap = false
        touchJump = false
        touchCrouch = false
        touchInteract = false

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
            btn.mouse {
                onDown {
                    onTouchChange(true)
                    drawState(true)
                }
                onUpAnywhere {
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
        //    Crouch sits level with jump (the base of the triangle) and interact sits centred
        //    above the midpoint between them (the apex) - all three the same distance apart, so
        //    the three centres form an equilateral triangle, apex up, all three buttons the same
        //    size (unlike the old fan-shaped arc, which had crouch and interact at two different
        //    angles off a bigger jump button).
        //
        //    Gaps between neighbouring buttons are 12px - tight enough that each cluster reads as
        //    one control surface, wide enough that a thumb pad landing between two of them still
        //    resolves to the one it is closest to.
        //
        // 3. The two edge insets are floors, not fixed values. 46 and 38 were picked against a
        //    phone whose gesture strips the app could only guess at; where a host actually
        //    reports a safe area (an iPhone's Dynamic Island sits on a SIDE in landscape and is
        //    59pt wide - well past 46 - and its home indicator runs along the bottom), the
        //    reported inset plus a small margin wins. On anything that reports nothing these stay
        //    exactly the numbers they have always been.
        val isControlsSwapped = profileStorage.getProfile().controlsSwapped
        val safeEdgeMargin = 12.0                 // breathing room past the reported cutout itself
        val edgeInsetLeft = max(46.0, safeInsets.left + safeEdgeMargin)
        val edgeInsetRight = max(46.0, safeInsets.right + safeEdgeMargin)
        val bottomInset = max(38.0, safeInsets.bottom + safeEdgeMargin)
        val moveRadius = 54.0
        val actionRadius = 48.0                   // uniform size for jump/crouch/interact - the old crouch button's size
        val jumpRadius = actionRadius
        val crouchRadius = actionRadius
        val interactRadius = actionRadius

        val controlsY = canvasH - bottomInset - moveRadius

        val btnGap = 12.0
        val moveSpan = moveRadius * 2.0 + btnGap
        val moveLeftX = if (isControlsSwapped) canvasW - edgeInsetRight - moveRadius - moveSpan else edgeInsetLeft + moveRadius
        val moveRightX = if (isControlsSwapped) canvasW - edgeInsetRight - moveRadius else edgeInsetLeft + moveRadius + moveSpan

        // Jump is the hub; crouch and interact hang off it on one arc. 210 is its distance from
        // whichever edge it sits against, measured from inside the safe area rather than from the
        // physical edge - otherwise the cluster's outermost button (crouch, at jump +
        // actionArcRadius) is the one that lands under a notch.
        val jumpX = if (isControlsSwapped) edgeInsetLeft + 164.0 else canvasW - edgeInsetRight - 164.0
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
                btn.mouse {
                    onDown { onTouch(true); img.alpha = 0.6 }
                    onUpAnywhere { onTouch(false); img.alpha = 1.0 }
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
            if (it) touchRightTap = true
        }

        // Action Buttons: Jump, Crouch, Interact (Standard Mobile Action Arc)
        createImgBtn(crouchX, crouchY, crouchRadius, crouchBtnBitmap, COLOR_ACCENT_GOLD, { drawSneakArrow(COLOR_ACCENT_GOLD) }) {
            touchCrouch = it
        }
        createImgBtn(jumpX, jumpY, jumpRadius, jumpBtnBitmap, COLOR_ACCENT_GREEN, { drawJumpArrow(COLOR_ACCENT_GREEN) }) {
            touchJump = it
        }

        val interactBtnCont: Container?
        val interactBtnImg: Image?
        if (interactBtnBitmap != null) {
            val btn = controlsContainer.container().xy(interactX - interactRadius, interactY - interactRadius)
            val img = btn.image(interactBtnBitmap) { size(interactRadius * 2.0, interactRadius * 2.0) }
            img.alpha = if (world.canInteract) 1.0 else translucentEffectAlpha
            btn.singleTouch {
                start {
                    if (world.canInteract) {
                        touchInteract = true
                        img.alpha = 0.6
                    }
                }
                end {
                    touchInteract = false
                    img.alpha = if (world.canInteract) 1.0 else translucentEffectAlpha
                }
                endAnywhere {
                    touchInteract = false
                    img.alpha = if (world.canInteract) 1.0 else translucentEffectAlpha
                }
                moveAnywhere {
                    if (btn.hitTest(it.global) == null) {
                        touchInteract = false
                        img.alpha = if (world.canInteract) 1.0 else translucentEffectAlpha
                    }
                }
            }
            btn.mouse {
                onDown {
                    if (world.canInteract) {
                        touchInteract = true
                        img.alpha = 0.6
                    }
                }
                onUpAnywhere {
                    touchInteract = false
                    img.alpha = if (world.canInteract) 1.0 else translucentEffectAlpha
                }
            }
            interactBtnCont = btn
            interactBtnImg = img
        } else {
            createTouchBtn(interactX, interactY, interactRadius, "", COLOR_ACCENT_CYAN, { drawInteractIcon(COLOR_ACCENT_CYAN) }) {
                if (world.canInteract) touchInteract = it
            }
            interactBtnCont = null
            interactBtnImg = null
        }

        // --- Tutorial Tactical Callout & Action Guidance Overlay ----------------------------
        val tutorialSteps = levelData.tutorialSteps
        val tutorialLayer = container().xy(0.0, 0.0)
        tutorialLayer.mouseEnabled = false
        tutorialLayer.mouseChildren = false
        val tutorialDarkOverlay = tutorialLayer.uiGraphics()
        tutorialDarkOverlay.mouseEnabled = false

        // Highlight container for rendering bright button textures above the dark scrim
        val tutorialHighlightContainer = tutorialLayer.container().xy(0.0, 0.0)
        tutorialHighlightContainer.mouseEnabled = false
        tutorialHighlightContainer.mouseChildren = false
        val hlLeftImg = if (leftBtnBitmap != null) tutorialHighlightContainer.image(leftBtnBitmap) {
            xy(moveLeftX - moveRadius, controlsY - moveRadius)
            size(moveRadius * 2.0, moveRadius * 2.0)
            visible = false
            mouseEnabled = false
        } else null
        val hlRightImg = if (rightBtnBitmap != null) tutorialHighlightContainer.image(rightBtnBitmap) {
            xy(moveRightX - moveRadius, controlsY - moveRadius)
            size(moveRadius * 2.0, moveRadius * 2.0)
            visible = false
            mouseEnabled = false
        } else null
        val hlJumpImg = if (jumpBtnBitmap != null) tutorialHighlightContainer.image(jumpBtnBitmap) {
            xy(jumpX - jumpRadius, jumpY - jumpRadius)
            size(jumpRadius * 2.0, jumpRadius * 2.0)
            visible = false
            mouseEnabled = false
        } else null
        val hlCrouchImg = if (crouchBtnBitmap != null) tutorialHighlightContainer.image(crouchBtnBitmap) {
            xy(crouchX - crouchRadius, crouchY - crouchRadius)
            size(crouchRadius * 2.0, crouchRadius * 2.0)
            visible = false
            mouseEnabled = false
        } else null
        val hlInteractImg = if (interactBtnBitmap != null) tutorialHighlightContainer.image(interactBtnBitmap) {
            xy(interactX - interactRadius, interactY - interactRadius)
            size(interactRadius * 2.0, interactRadius * 2.0)
            visible = false
            mouseEnabled = false
        } else null

        val tutorialHighlightGraphics = tutorialLayer.uiGraphics()
        tutorialHighlightGraphics.mouseEnabled = false
        val tutorialHandwrittenText = tutorialLayer.text("", textSize = 28.0, font = handwrittenFont, color = Colors.WHITE)
        tutorialHandwrittenText.mouseEnabled = false

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
        // PROTOTYPE deliberately excluded - it's the in-progress sixth gadget (see Powerup.kt's own
        // doc comment): a real, spendable item with a timer but no world effect yet, drawn with a
        // plain "?" mystery-box icon since it has no real art. Showing a usable-looking slot that
        // does nothing when tapped reads as broken, not mysterious - removed on request after it
        // showed up in the in-game tray (via a debug powerup grant) as an unexplained "?" icon.
        val gadgetTypes = listOf(
            PowerupType.CHECKPOINTS,
            PowerupType.REMOTE_TRIGGER,
            PowerupType.LASER_SHIELD,
            PowerupType.INVISIBILITY,
            PowerupType.NOISE_SUPPRESSION
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
        val slotX = canvasW - hudRightInset - pauseRadius * 2.0 - slotGap - slotSize
        val slotY = hudTopInset
        val trayExpandSeconds = 0.17

        fun tryActivatePowerup(type: PowerupType) {
            if (world.isLevelComplete || world.isGameOver || isPaused) return
            // Refuse before spending the item - checking only inside world.activatePowerup would
            // still burn one from inventory for an activation that silently does nothing.
            if (world.activePowerups.isActive(type)) return
            if (type == PowerupType.REMOTE_TRIGGER && !world.hasRemoteTriggerTarget()) return
            if (profileStorage.consumePowerup(type)) {
                world.activatePowerup(type)
                // The slot reads the per-frame cache, and this can fire from a key press earlier
                // in the same frame, so re-read rather than show a stale count for a tick.
                refreshProfile()
            }
        }

        val gadgetLayer = controlsContainer.container()

        // --- The row -----------------------------------------------------------------------
        gadgetsShown = false
        trayExpand = 0.0
        // Which gadgets the row was last built for, as a bitmask. Stock runs out during a level,
        // so the row has to re-pack when it does - but only then, not every frame.
        trayBuiltFor = -1
        val gadgetTray = gadgetLayer.container()
        gadgetTray.visible = false

        class TrayEntry(
            val type: PowerupType,
            val box: Container,
            val count: Text,
            val frame: Graphics,
            /** The vertical drain bar shown in place of the count while the gadget is live. */
            val drain: Graphics,
            /** Where this entry sits once the tray has finished expanding. */
            var restX: Double = 0.0,
            var lastLabel: String = "",
            var lastLive: Boolean? = null,
            /** Quantised remaining-fraction the drain bar was last redrawn at - see refreshTrayLabels. */
            var lastFraction: Double = -1.0
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
                    // Centred both axes - the box has no other fixed content competing for space
                    // (the count/drain bar below both float in the corner, over the icon).
                    it.xy((slotSize - slotIconSize) / 2.0, (slotSize - slotIconSize) / 2.0)
                }
            }
            val count = box.text("", textSize = 12.5, font = bebasFont, color = COLOR_TEXT_LIGHT)
            count.graphicsRenderer = GraphicsRenderer.GPU
            val drain = box.uiGraphics()
            box.visible = false
            box.mouse {
                onClick {
                    playClick(GameAudio.UI_CLICK_GAIN)
                    tryActivatePowerup(type)
                }
            }
            TrayEntry(type, box, count, frame, drain)
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
                entry.lastFraction = -1.0
                entry.drain.updateShape { clear() }
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

        // The drain overlay covers the full width and height of the 42x42 box (owner request 2026-09-20),
        // with corner radius 9.0 matching the box's own rounded rect.
        val drainCornerRadius = 9.0

        // Runs every frame the row is up, because a live gadget's countdown is shown here.
        // Guarded on the rendered string/quantised fraction so redraws only happen when the
        // visible state actually changes, not every frame.
        fun refreshTrayLabels() {
            for (entry in trayEntries) {
                if (!entry.box.visible) continue
                val live = world.activePowerups.isActive(entry.type)

                // Stock count only shows while idle - a live gadget shows the drain bar instead,
                // never both fighting for the same corner.
                val label = if (live) "" else "${cachedProfile.getPowerupCount(entry.type)}"
                if (entry.lastLabel != label || entry.lastLive != live) {
                    entry.lastLabel = label
                    entry.lastLive = live
                    entry.count.text = label
                    entry.count.xy(slotSize - entry.count.width - 3.0, slotSize - 15.0)
                }

                // Half-transparent white overlay across the WHOLE box, covering the icon like a
                // curtain: full the instant the gadget goes live, its bottom edge fixed and its
                // top edge sinking toward the bottom as time runs out - a liquid level draining
                // away, not a bar filling up. A level-duration gadget (isLevelDuration) has no
                // clock to drain, so it just reads as permanently full for as long as it's on,
                // per the owner's call.
                val fraction = when {
                    !live -> 0.0
                    entry.type.isLevelDuration -> 1.0
                    else -> (world.activePowerups.getRemainingTime(entry.type) / entry.type.duration)
                        .coerceIn(0.0, 1.0)
                }
                // Quantised to fortieths, same allowance as the corner bolt's own drain bar - finer
                // than that redraws this vector shape every frame for a sub-pixel height change.
                val fractionStep = if (!live) -1.0 else (fraction * 40.0).toInt() / 40.0
                if (entry.lastFraction == fractionStep) continue
                entry.lastFraction = fractionStep
                entry.drain.updateShape {
                    clear()
                    if (live) {
                        val boxW = slotSize
                        val boxH = slotSize
                        val filledH = boxH * fraction
                        // The overlay covers the entire box when active, and for ones that wear
                        // down, the overlay remains curved at the top to match the rounded box
                        // behind it. Clamped to filledH / 2 so corner radii never exceed height.
                        val corner = drainCornerRadius.coerceAtMost((filledH / 2.0).coerceAtLeast(0.0))
                        fill(Colors.WHITE.withAd(0.14)) {
                            roundRect(0.0, boxH - filledH, boxW, filledH, corner, corner)
                        }
                    }
                }
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
        slotLastSpan = -2.0
        slotLastLive = null

        val gadgetKeys = listOf(Key.N1, Key.N2, Key.N3, Key.N4, Key.N5, Key.N6)

        // ==========================================
        // 1. PAUSE OVERLAY (Heist Dossier - matches the main menu)
        // ==========================================
        val pauseOverlay = setupPauseOverlay(
            canvasW = canvasW,
            canvasH = canvasH,
            levelName = levelData.localizedName(currentLanguage),
            bebasFont = bebasFont,
            paperBtnBitmaps = paperBtnBitmaps,
            paperInk = paperInk,
            playClick = playClick,
            currentLanguage = currentLanguage,
            onResume = {
                isPaused = false
            },
            onRestart = {
                if (world.activePowerups.isCheckpointsActive) {
                    isPaused = false
                    world.respawnAtCheckpoint()
                    world.onCheckpointAutoRespawn?.invoke()
                } else {
                    stopBgMusic()
                    sceneContainer.changeTo { GameplayScene(levelData) }
                }
            },
            onQuit = {
                stopBgMusic()
                getLevelExitBridge().requestReturnToMenu()
                sceneContainer.changeTo { GameplayScene(levelData, startDormant = true) }
            }
        )

        // Temporarily restrict to the active levels for Google Play production approval, so
        // clearing the last one shows ALL CLEAR / returns to menu instead of advancing into a
        // level that is not built yet. Level 8 joined the list on 2026-09-25 (LEVEL_8_LAYOUT);
        // 9 to 12 are still name-and-description stubs with no layout.
        val allLevels = LevelData.DEFAULT_LEVELS.take(8)
        val currentLevelIndex = allLevels.indexOfFirst { it.id == levelData.id }
        val nextLevel = if (currentLevelIndex >= 0 && currentLevelIndex + 1 < allLevels.size) allLevels[currentLevelIndex + 1] else null

        // ==========================================
        // 2. MISSION FAILED OVERLAY
        // ==========================================
        val caughtOverlay = setupCaughtOverlay(
            canvasW = canvasW,
            canvasH = canvasH,
            failedBgBitmap = failedBgBitmap,
            failedBtnBitmaps = failedBtnBitmaps,
            bebasFont = bebasFont,
            paperInk = paperInk,
            playClick = playClick,
            currentLanguage = currentLanguage,
            onRequestContinueAd = {
                getContinueAdBridge().requestContinueAd()
            },
            onRetry = {
                if (world.activePowerups.isCheckpointsActive) {
                    world.respawnAtCheckpoint()
                    world.onCheckpointAutoRespawn?.invoke()
                } else {
                    stopBgMusic()
                    sceneContainer.changeTo { GameplayScene(levelData) }
                }
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
            victoryBtnBitmaps = victoryBtnBitmaps,
            bebasFont = bebasFont,
            levelData = levelData,
            nextLevel = nextLevel,
            paperInk = paperInk,
            playClick = playClick,
            currentLanguage = currentLanguage,
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

            // Calculate and award coins
            val multiplier = if (profileStorage.getProfile().isPremium) 2 else 1
            val earnedCoins = levelData.getCoinReward(result.starCount) * multiplier
            profileStorage.addCoins(earnedCoins)

            // Unlock next level in progression
            if (nextLevel != null) {
                profileStorage.unlockLevel(nextLevel.id)
            }

            // Prompt in-app review after completing level 4
            if (result.levelId == "level_4") {
                getInAppReviewBridge().requestReview()
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
            caughtOverlay.show(world.timeTaken, world.spottedCount, best, profileStorage.getProfile().coins, world.canContinue)
        }

        totalElapsedSeconds = 0.0
        conveyorElapsedSeconds = 0.0

        world.onConveyorFallOff = {
            isFirstCameraFrame = true
            totalElapsedSeconds = 0.0
            conveyorElapsedSeconds = 0.0
            playerAnimState = "idle"
            jumpPhase = "none"
            landingAbsorb = false
            swingImpactSoundPlayed = false
            swingExitTimer = 0.0
            climbExitTimer = 0.0
            playerFacingLeft = false
            playerSprite.scaleX = playerBaseScale
            playerSprite.playAnimationLooped(playerAnimations.idle, PlayerAnimations.IDLE_FRAME_TIME_MS.milliseconds)
            for (i in world.conveyorCrates.indices) {
                val crate = world.conveyorCrates[i]
                conveyorCrateContainers[i].xy(crate.x, crate.y)
                conveyorCrateContainers[i].visible = true
            }
            objOptState = 0
            setObjMark(objOptMark, 0)
            shieldDeflectFlashTimer = 0.0
            shieldFlareTimer = 0.0
            shieldFlareImage.visible = false
        }

        world.onCameraBotDeactivated = { _ ->
            sounds.toastSuccess.playSfx(sfxContext, GameAudio.TOAST_SUCCESS_GAIN, sfxVolume(), GameAudio.SfxFile.TOAST_SUCCESS)
        }
        world.onLaserShieldBlocked = {
            shieldDeflectFlashTimer = 0.15
            shieldFlareTimer = 0.25
            shieldFlareImage.xy(world.player.x + world.player.width / 2.0, world.player.y + world.player.height / 2.0)
            shieldFlareImage.visible = true
            shieldFlareImage.alpha = 1.0
            shieldFlareImage.scale = 1.0
            sounds.impact.playSfx(sfxContext, GameAudio.LANDING_GAIN, sfxVolume(), GameAudio.SfxFile.IMPACT)
        }

        world.onCheckpointAutoRespawn = {
            sounds.toastSuccess.playSfx(sfxContext, GameAudio.TOAST_SUCCESS_GAIN, sfxVolume(), GameAudio.SfxFile.TOAST_SUCCESS)
            caughtOverlay.hide()
            pauseOverlay.visible = false
            isPaused = false
            isFirstCameraFrame = true
            playerAnimState = "idle"
            jumpPhase = "none"
            landingAbsorb = false
            swingImpactSoundPlayed = false
            swingExitTimer = 0.0
            climbExitTimer = 0.0
            playerFacingLeft = false
            playerSprite.scaleX = playerBaseScale
            playerSprite.playAnimationLooped(playerAnimations.idle, PlayerAnimations.IDLE_FRAME_TIME_MS.milliseconds)
            playerContainer.xy(world.player.x, world.player.y)
            shieldDeflectFlashTimer = 0.0
            shieldFlareTimer = 0.0
            shieldFlareImage.visible = false
        }

        // Tutorial Controller State
        currentTutorialStep = null
        completedTutorialStepIds.clear()
        tutorialAlpha = 0.0
        isTutorialFadingIn = false
        isTutorialFadingOut = false
        tutorialPulseTimer = 0.0
        stepActionCompleted = false
        stepActionTimer = 0.0
        stepActivatedX = 0.0
        lastUsedKeyboard = false

        prevRightPressed = false
        var debugLevelCheckTimer = 0.0

        // Main game update loop
        addUpdater { dt ->
            // Hoisted above every early-return in this block (including the pause/game-over one)
            // so syncBgMusicVolume's ramp below has a real per-frame delta even on frames that
            // return before the "active gameplay" dtSec further down - see that call's own site.
            val dtSec = dt.seconds.coerceIn(0.0, 0.1)

            // Checked unconditionally (ahead of the isGameOver early-return below), since that's
            // exactly the state this fires in: the native shell has shown the rewarded ad while
            // this scene stayed alive in the background, and grants the continue once the player
            // actually watched it. Revives the player in-place at their last safe checkpoint.
            if (getContinueAdBridge().consumeContinueGranted()) {
                sounds.toastSuccess.playSfx(sfxContext, GameAudio.TOAST_SUCCESS_GAIN, sfxVolume(), GameAudio.SfxFile.TOAST_SUCCESS)
                caughtOverlay.hide()
                world.respawnAtCheckpoint()
                isFirstCameraFrame = true
                playerAnimState = "idle"
                jumpPhase = "none"
                landingAbsorb = false
                swingImpactSoundPlayed = false
                swingExitTimer = 0.0
                climbExitTimer = 0.0
                playerFacingLeft = false
                playerSprite.scaleX = playerBaseScale
                playerSprite.playAnimationLooped(playerAnimations.idle, PlayerAnimations.IDLE_FRAME_TIME_MS.milliseconds)
                playerContainer.xy(world.player.x, world.player.y)
                shieldDeflectFlashTimer = 0.0
                shieldFlareTimer = 0.0
                shieldFlareImage.visible = false
                return@addUpdater
            }

            if (views.input.keys.justPressed(Key.ESCAPE) || views.input.keys.justPressed(Key.P)) {
                if (!world.isLevelComplete && !world.isGameOver) {
                    isPaused = !isPaused
                }
            }

            // Desktop-only debug cheat: free flight through the level for layout inspection.
            // Platform.isJvm keeps this out of the Android/iOS builds even if a keyboard is attached.
            if (Platform.isJvm && views.input.keys.justPressed(Key.F1)) {
                if (!world.isLevelComplete && !world.isGameOver) {
                    world.noclipFlying = !world.noclipFlying
                }
            }

            // Desktop-only debug cheat: top up every gadget's inventory by 3 so they can all be
            // tried out in one run without grinding coins for them first. Uses the storage's own
            // grantDebugPowerups() (GameProfile.kt) - it already existed, wired to nothing until
            // now. It grants every PowerupType, including PROTOTYPE - harmless, since PROTOTYPE is
            // deliberately excluded from gadgetTypes above and so never renders in the tray anyway.
            // refreshProfile() re-reads the cache immediately so the tray shows the new counts the
            // same frame rather than a tick later.
            if (Platform.isJvm && views.input.keys.justPressed(Key.F2)) {
                profileStorage.grantDebugPowerups(3)
                refreshProfile()
            }

            if (Platform.isJvm) {
                debugLevelCheckTimer += dtSec
                if (debugLevelCheckTimer >= 0.15) {
                    debugLevelCheckTimer = 0.0
                    launchImmediately {
                        try {
                            val debugFile = localCurrentDirVfs[".debug_level"]
                            if (debugFile.exists()) {
                                val targetId = debugFile.readString().trim()
                                debugFile.delete()
                                val targetLevel = LevelData.findById(targetId)
                                if (targetLevel != null) {
                                    stopBgMusic()
                                    sceneContainer.changeTo { GameplayScene(targetLevel) }
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }

            pauseOverlay.visible = isPaused

            // Everything below reads volumes and the powerup inventory off this one snapshot.
            refreshProfile()

            syncBgMusicVolume(dtSec)

            // The run is on hold while the pause overlay is up OR while the shell says gameplay
            // is not in front of the player (backgrounded app, full-screen ad). Mirrored onto the
            // world so the level clock cannot advance even if some other caller drives
            // world.update() while we are here - see GameWorld.isSuspended.
            val onHold = isPaused || !GameAppLifecycle.isForeground
            world.isSuspended = onHold

            if (onHold || world.isLevelComplete || world.isGameOver) {
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
            val tapFromTouch = touchRightTap
            touchRightTap = false
            val forwardTap = views.input.keys.justPressed(Key.RIGHT) || views.input.keys.justPressed(Key.D) || tapFromTouch || (rightPressed && !prevRightPressed)
            prevRightPressed = rightPressed
            val jumpPressed = views.input.keys[Key.UP] || views.input.keys[Key.W] || views.input.keys[Key.SPACE] || touchJump
            val crouchPressed = views.input.keys[Key.DOWN] || views.input.keys[Key.S] || views.input.keys[Key.C] ||
                    views.input.keys[Key.LEFT_CONTROL] || views.input.keys[Key.RIGHT_CONTROL] || touchCrouch
            val rawInteractPressed = views.input.keys[Key.E] || views.input.keys[Key.F] || views.input.keys[Key.ENTER] || touchInteract
            val interactPressed = rawInteractPressed && world.canInteract
            val canInteract = world.canInteract
            if (!touchInteract) {
                interactBtnImg?.alpha = if (canInteract) 1.0 else translucentEffectAlpha
            }

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

            if (moveInput != 0.0 || forwardTap) {
                tapWalkGraceTimer = 0.25
            } else if (tapWalkGraceTimer > 0.0) {
                tapWalkGraceTimer = (tapWalkGraceTimer - dtSec).coerceAtLeast(0.0)
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
                            TutorialAction.MOVE -> {
                                if (step.id == "step_spam_fan") {
                                    (world.isWindSpamming && (playerX >= stepActivatedX + 35.0 || playerX >= 420.0)) || playerX > step.triggerMaxX
                                } else {
                                    (moveInput != 0.0 && playerX >= stepActivatedX + 30.0) || playerX > step.triggerMaxX
                                }
                            }
                            TutorialAction.JUMP_VAULT -> jumpPressed || world.player.isJumping || world.player.isClimbing || (world.player.y < baseGroundY - 96.0 - 20.0) || playerX > step.triggerMaxX
                            TutorialAction.CROUCH -> crouchPressed || world.player.isCrouching ||
                                // Also done if the player mantles up onto the very crate they'd
                                // otherwise hide behind (feet at/above its top surface, over its
                                // footprint) - a real alternative to hiding, not just an input to
                                // press, so it should dismiss the prompt too.
                                (world.player.isGrounded &&
                                    world.player.bounds.bottom <= world.crate.top + 4.0 &&
                                    world.player.bounds.right > world.crate.left) ||
                                playerX > step.triggerMaxX
                            TutorialAction.CLIMB -> jumpPressed || world.player.isClimbing || (world.player.y < baseGroundY - 96.0 - 50.0) || playerX > step.triggerMaxX
                            TutorialAction.REACH_OBJECTIVE -> world.player.bounds.intersects(world.exitZone) || playerX >= world.exitZone.x
                            // Tied to the swing actually starting, not just a jump press - an
                            // early/late jump that misses the hook and falls short must not
                            // dismiss the prompt as if it had succeeded.
                            TutorialAction.SWING -> world.player.isSwinging || playerX > step.triggerMaxX
                            TutorialAction.INTERACT -> interactPressed || world.levers.any { it.isActivated } || world.cameraBots.any { it.isDeactivated } || playerX > step.triggerMaxX
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
                        // World-anchored callout: fixed to something in the game world (the
                        // checkpoint building, a crate to hide behind, ...) - see step.worldTextX/Y.
                        textX = step.worldTextX * worldZoom + worldView.x
                        textY = (step.worldTextY * worldZoom + worldView.y).coerceAtLeast(15.0)
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
                                TutorialControlHighlight.MOVE_RIGHT -> {
                                    val targetX = moveRightX
                                    val targetY = controlsY - moveRadius - 8.0
                                    if (!isControlsSwapped) {
                                        val startX = textX + 60.0.coerceAtMost(tutorialHandwrittenText.width * 0.35)
                                        val startY = textY + 36.0
                                        val ctrlX = (startX + targetX) / 2.0 - 10.0
                                        val ctrlY = startY + (targetY - startY) * 0.50
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
                                        val startX = textX + tutorialHandwrittenText.width - 60.0.coerceAtMost(tutorialHandwrittenText.width * 0.35)
                                        val startY = textY + 36.0
                                        val ctrlX = (startX + targetX) / 2.0 + 10.0
                                        val ctrlY = startY + (targetY - startY) * 0.50
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
                                TutorialControlHighlight.MOVE -> {
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
                                    val targetScreenX = step.worldAnchorX * worldZoom + worldView.x
                                    val targetScreenY = step.worldAnchorY * worldZoom + worldView.y
                                    val textW = tutorialHandwrittenText.width
                                    val textH = 28.0

                                    val startX: Double
                                    val startY: Double
                                    val ctrlX: Double
                                    val ctrlY: Double

                                    if (targetScreenX > textX + textW) {
                                        // Target is to the right of the text: start cleanly off the right edge
                                        // of the text box so the arrow never intersects or overlaps the text.
                                        startX = textX + textW + 8.0
                                        startY = textY + textH * 0.5
                                        val bowY = if (step.arrowBowsLeft) 15.0 else -15.0
                                        ctrlX = (startX + targetScreenX) / 2.0
                                        ctrlY = (startY + targetScreenY) / 2.0 + bowY
                                    } else if (targetScreenX < textX) {
                                        // Target is to the left of the text: start cleanly off the left edge
                                        startX = textX - 8.0
                                        startY = textY + textH * 0.5
                                        val bowY = if (step.arrowBowsLeft) 15.0 else -15.0
                                        ctrlX = (startX + targetScreenX) / 2.0
                                        ctrlY = (startY + targetScreenY) / 2.0 + bowY
                                    } else {
                                        // Target is below/above the text (e.g. Level 1 & Level 4)
                                        startX = textX + textW * 0.75
                                        startY = textY + 28.0
                                        val bowX = if (step.arrowBowsLeft) -20.0 else 20.0
                                        ctrlX = (startX + targetScreenX) / 2.0 + bowX
                                        ctrlY = (startY + targetScreenY) / 2.0 - 15.0
                                    }

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

            // Update domain simulation (interact button activates mechanisms, jump button triggers jump/vault/climb/mantle)
            world.update(dtSec, moveInput, jumpPressed, crouchPressed, interactPressed, forwardTap = forwardTap)

            // Sync visual positions
            playerContainer.xy(world.player.x, world.player.y)
            for ((lever, handleCont) in leverHandles) {
                handleCont.rotation = if (lever.isActivated) (25.0).degrees else (-25.0).degrees
            }
            for (hcv in hookCrateVisuals) {
                hcv.crateCont.xy(hcv.hc.bounds.x, hcv.hc.bounds.y)
                val rImg = hcv.ropeImage ?: continue
                if (!hcv.hc.isDetached) {
                    hcv.dissolveTimer = 0.0
                    rImg.visible = true
                    rImg.alpha = 1.0
                    ropeDissolveBitmaps.firstOrNull()?.let { rImg.bitmap = it.slice() }
                } else {
                    hcv.dissolveTimer += dtSec
                    val dissolveDuration = 0.55
                    val progress = (hcv.dissolveTimer / dissolveDuration).coerceIn(0.0, 1.0)
                    if (progress >= 1.0) {
                        rImg.visible = false
                    } else {
                        rImg.visible = true
                        rImg.alpha = (1.0 - progress).coerceIn(0.0, 1.0)
                        if (ropeDissolveBitmaps.isNotEmpty()) {
                            val frameIdx = (progress * (ropeDissolveBitmaps.size - 1)).toInt()
                                .coerceIn(0, ropeDissolveBitmaps.size - 1)
                            ropeDissolveBitmaps[frameIdx].let { rImg.bitmap = it.slice() }
                        }
                    }
                }
            }
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
            for (i in world.conveyorCrates.indices) {
                val crate = world.conveyorCrates[i]
                conveyorCrateContainers[i].xy(crate.x, crate.y)
            }
            // Sync animated conveyor belt layers (top moving forward, bottom moving in reverse)
            if (world.conveyorsActive) {
                conveyorElapsedSeconds += dtSec
            }
            for (cAnim in conveyorAnimators) {
                val topShift = (conveyorElapsedSeconds * cAnim.speed) % cAnim.topTileW
                var topStart = topShift
                if (topStart > 0) topStart -= cAnim.topTileW
                for (i in cAnim.topImages.indices) {
                    cAnim.topImages[i].xy(topStart + i * cAnim.topTileW, cAnim.topY)
                }

                val botShift = (-conveyorElapsedSeconds * cAnim.speed) % cAnim.botTileW
                var botStart = botShift
                if (botStart > 0) botStart -= cAnim.botTileW
                for (i in cAnim.botImages.indices) {
                    cAnim.botImages[i].xy(botStart + i * cAnim.botTileW, cAnim.botY)
                }
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
            val baseWorldViewY = if (bgFileName == "bglvl7.png") {
                val lvl7GroundY = world.platforms.firstOrNull { it.y >= 400.0 && it.width >= 1000.0 }?.y ?: 440.0
                currentCanvasH * (488.0 / 724.0) - (lvl7GroundY * worldZoom)
            } else {
                currentCanvasH - (baseGroundY + 70.0) * worldZoom
            }
            worldView.y = baseWorldViewY

            val vignette = darknessVignetteImg
            if (levelData.hasDarknessVignette && vignette != null) {
                val pScreenX = (world.player.x + world.player.width / 2.0) * worldZoom + worldView.x
                val pScreenY = (world.player.y + world.player.height / 2.0) * worldZoom + worldView.y
                val halfSize = darknessVignetteSize / 2.0
                val vx = pScreenX - halfSize
                val vy = pScreenY - halfSize
                vignette.xy(vx, vy)
                darknessLeftRect?.xy(0.0, 0.0)?.size(max(0.0, vx), currentCanvasH)
                darknessRightRect?.xy(vx + darknessVignetteSize, 0.0)?.size(max(0.0, currentCanvasW - (vx + darknessVignetteSize)), currentCanvasH)
                darknessTopRect?.xy(max(0.0, vx), 0.0)?.size(min(currentCanvasW, vx + darknessVignetteSize) - max(0.0, vx), max(0.0, vy))
                darknessBotRect?.xy(max(0.0, vx), vy + darknessVignetteSize)?.size(min(currentCanvasW, vx + darknessVignetteSize) - max(0.0, vx), max(0.0, currentCanvasH - (vy + darknessVignetteSize)))
            }

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
            for (i in world.conveyorCrates.indices) {
                val crate = world.conveyorCrates[i]
                val isVis = crate.bounds.right >= cullLeft && crate.bounds.left <= cullRight
                conveyorCrateContainers[i].visible = isVis
            }
            for (visual in laserVisuals) {
                visual.update(totalElapsedSeconds, cullLeft, cullRight)
            }
            for (visual in ventFanVisuals) {
                visual.update(dtSec, totalElapsedSeconds, cullLeft, cullRight)
            }
            for (visual in cameraBotVisuals) {
                visual.update(
                    dtSec,
                    totalElapsedSeconds,
                    cullLeft,
                    cullRight,
                    world.occluders,
                    isDetecting = visual.bot in world.detectingCameraBots
                )
            }
            for (visual in steamPipeVisuals) {
                visual.update(dtSec, totalElapsedSeconds, cullLeft, cullRight)
            }

            rainEffect?.update(
                dtSec = dtSec,
                canvasW = currentCanvasW,
                canvasH = currentCanvasH,
                worldViewX = worldView.x,
                sounds = sounds,
                sfxVolume = sfxVolume(),
                coroutineContext = sfxContext,
                worldViewY = worldView.y,
                worldZoom = worldZoom
            )

            // Background parallax: 1:1 lockstep for interior warehouse wall (metalbg.png) and vent shaft (bglvl7.png), 0.2x rate for outdoor sky
            if (bgmgImages.isNotEmpty()) {
                val virtualCameraX = -worldView.x / worldZoom
                val bgParallax = if (bgFileName == "metalbg.png" || bgFileName == "bglvl7.png") worldZoom else 0.2
                val bgmgOffset = -virtualCameraX * bgParallax
                var bgmgShift = bgmgOffset % bgmgTileW
                if (bgmgShift > 0) bgmgShift -= bgmgTileW
                for (i in bgmgImages.indices) {
                    bgmgImages[i].xy(bgmgShift + i * bgmgTileW, 0.0)
                }
                wallDecalsContainer.xy(bgmgOffset, 0.0)
            }

            val isWalkingOrTapping = world.player.isMoving || (tapWalkGraceTimer > 0.0 && !world.player.isCrouching)
            if (isWalkingOrTapping) {
                stationaryElapsed = 0.0
            } else {
                stationaryElapsed += dtSec
            }
            if (world.player.isMoving) {
                crouchStationaryElapsed = 0.0
            } else {
                crouchStationaryElapsed += dtSec
            }

            // Swing animation machine: above climb, for the same reason climb is above jump.
            // Player.isSwinging drives x/y from the clip's own grip curves (Player.advanceSwing),
            // so every machine below would otherwise fight it - isGrounded is false throughout.
            if (world.player.isSwinging) {
                windOwnsSprite = false
                if (playerAnimState != "swing") {
                    playerAnimState = "swing"
                    landingAbsorb = false
                    swingImpactSoundPlayed = false
                    // The push-off is a jump, and the clip opens on one, so it gets the jump's
                    // grunt. There is no dedicated swing sample.
                    if (!world.activePowerups.isNoiseSuppressed) {
                        sounds.climb.playSfx(sfxContext, GameAudio.CLIMB_GAIN, sfxVolume(), GameAudio.SfxFile.CLIMB)
                    }
                    playerSprite.playAnimationLooped(playerAnimations.swing, manualFrameTime)
                }
                // Play landing impact sound right as the feet plant on the far ledge (frame 44.5)
                if (!swingImpactSoundPlayed && world.player.swingPhase >= (44.0 / 51.0)) {
                    swingImpactSoundPlayed = true
                    if (!world.activePowerups.isNoiseSuppressed) {
                        sounds.impact.playSfx(sfxContext, GameAudio.LANDING_GAIN, sfxVolume(), GameAudio.SfxFile.IMPACT)
                    }
                }
                playerSprite.setFrame(
                    (world.player.swingPhase * swingFrameSpan).toInt().coerceIn(0, swingFrameSpan)
                )
            } else if (world.player.isClimbing) {
                windOwnsSprite = false
                if (playerAnimState != "climb") {
                    playerAnimState = "climb"
                    if (!world.activePowerups.isNoiseSuppressed) {
                        sounds.climb.playSfx(sfxContext, GameAudio.CLIMB_GAIN, sfxVolume(), GameAudio.SfxFile.CLIMB)
                    }
                    playerSprite.playAnimationLooped(playerAnimations.climb, manualFrameTime)
                }
                val frame = climbFirstFrame + (world.player.climbPhase * climbFrameSpan).toInt()
                playerSprite.setFrame(frame.coerceIn(climbFirstFrame, climbLastFrame))
            } else if (!world.isWindStanceIdle && world.player.isGrounded && !world.player.isCrouching) {
                windOwnsSprite = true
                // Wind stance machine (level 7's exhaust fans). Built exactly like the push one
                // next door: two clips off one number, GameWorld.windStanceBlend running 0 -> 1
                // as the body folds into the gale and 1 -> 0 as it straightens back up, so the
                // transition clip is scrubbed by it in both directions and only at a full 1.0
                // does the gait loop take over.
                //
                // Unlike push this one does NOT own the sprite outright - jumping and crouching
                // stay legal in a wind zone (the duct's ceiling means crouching is often the
                // point), so those two are checked above and hand straight down to the machines
                // below. The blend keeps running underneath, so coming out of a crouch inside
                // the wind picks the lean back up where it was rather than restarting it.
                if (playerAnimState != "wind") {
                    playerAnimState = "wind"
                    landingAbsorb = false
                    walkInTransition = false
                    windCycleProgress = 0.0
                    windStriding = false
                    windLoopClipLoaded = false
                    playerSprite.playAnimationLooped(playerAnimations.windTransition, manualFrameTime)
                }
                // Standing in the airflow HOLDS the braced pose - no gait at all.
                // In air flow parts normal mechanics do not work. Pressing forward once or long-pressing
                // does nothing. Only spam clicking front starts forward movement in normal wind walk animation.
                val isPushingForwardInWind = world.isWindSpamming
                val striding = (world.windStanceBlend >= 0.25 || world.isWindBraced) && isPushingForwardInWind
                if (striding) {
                    if (!windStriding) {
                        // Always re-enter at frame 0: the loop window was chosen so that frame is
                        // the closest one to the braced lean the transition ends on (2.18 frames
                        // of motion - see PlayerAnimations.WIND_WALK_FRAMES), so a stop-start
                        // costs the smallest pose step this footage offers.
                        windCycleProgress = 0.0
                        windStriding = true
                    }
                    val previousPhase = windCycleProgress
                    // Normal wind walk animation playing at normal steady speed (1.0 cycle per second = 2 steps per second)
                    val cycleRate = 1.0
                    windCycleProgress = (windCycleProgress + cycleRate * dtSec) % 1.0
                    if (!windLoopClipLoaded) {
                        playerSprite.playAnimationLooped(playerAnimations.windWalk, manualFrameTime)
                        windLoopClipLoaded = true
                    }
                    val loopLength = PlayerAnimations.WIND_WALK_LOOP_LENGTH
                    playerSprite.setFrame(
                        (windCycleProgress * loopLength).toInt().coerceIn(0, loopLength - 1)
                    )
                    // Same crossing test the other gaits use. Reuses walk's own contact phases:
                    // this clip's stance/swing split was not separately measured, and a footstep
                    // a few hundredths early inside a roaring fan is not audible.
                    for (phase in GameAudio.STEP_PHASES) {
                        val crossed = if (windCycleProgress >= previousPhase) {
                            phase > previousPhase && phase <= windCycleProgress
                         } else {
                            phase > previousPhase || phase <= windCycleProgress
                        }
                        if (crossed && !world.activePowerups.isNoiseSuppressed) {
                            val step = if (stepAlternate) sounds.stepB else sounds.stepA
                            stepAlternate = !stepAlternate
                            step.playSfx(
                                sfxContext, GameAudio.STEP_GAIN, sfxVolume(),
                                if (step === sounds.stepA) GameAudio.SfxFile.STEP_A else GameAudio.SfxFile.STEP_B
                            )
                        }
                    }
                } else {
                    windStriding = false
                    if (windLoopClipLoaded) {
                        playerSprite.playAnimationLooped(playerAnimations.windTransition, manualFrameTime)
                        windLoopClipLoaded = false
                    }
                    // Fast responsive transition from walk into braced wind pose:
                    // immediately begins active forward lean upon touching airflow.
                    val t = world.windStanceBlend.coerceIn(0.0, 1.0)
                    val blendProgress = if (t <= 0.0) 0.0 else (0.28 + 0.72 * t).coerceIn(0.0, 1.0)
                    playerSprite.setFrame(
                        (blendProgress * windTransitionLastFrame).roundToInt().coerceIn(0, windTransitionLastFrame)
                    )
                }
            } else if (!world.isPushStanceIdle) {
                windOwnsSprite = false
                // Push stance machine. Sits up here with swing and climb because, like them, it
                // owns the sprite outright for as long as it runs - GameWorld suppresses jump and
                // crouch while braced, so none of the machines below have anything to say.
                //
                // Two clips off one number. GameWorld.pushStanceBlend runs 0 -> 1 leaning in and
                // 1 -> 0 standing back up, so the transition clip is simply scrubbed by it in both
                // directions (the crouch clip's own arrangement); only at a full 1.0 with the
                // stance still held does the gait loop take over.
                if (playerAnimState != "push") {
                    playerAnimState = "push"
                    landingAbsorb = false
                    walkInTransition = false
                    pushCycleProgress = 0.0
                    pushStriding = false
                    pushLoopClipLoaded = false
                    pushFacingLeft = playerFacingLeft
                    playerSprite.playAnimationLooped(playerAnimations.pushTransition, manualFrameTime)
                }
                val braced = world.isPushing
                // Walking is what advances the stride; standing still holds the braced rest pose,
                // exactly as walk hands back to idle and crouchwalk back to the held crouch.
                val striding = braced && world.player.isMoving
                if (striding) {
                    if (!pushStriding) {
                        // Always re-enter the loop at its first frame. The loop window was chosen
                        // so that frame is the one closest to the braced rest pose (2.82 frames of
                        // motion - see PlayerAnimations.PUSH_FRAMES), so a stop-start costs the
                        // smallest pose step the footage can offer; an arbitrary phase would not.
                        pushCycleProgress = 0.0
                        pushStriding = true
                    }
                    val previousPhase = pushCycleProgress
                    pushCycleProgress =
                        (pushCycleProgress + abs(world.player.vx) * dtSec / pushCycleDistance) % 1.0
                    if (!pushLoopClipLoaded) {
                        playerSprite.playAnimationLooped(playerAnimations.push, manualFrameTime)
                        pushLoopClipLoaded = true
                    }
                    val loopLength = PlayerAnimations.PUSH_LOOP_LENGTH
                    playerSprite.setFrame(
                        (pushCycleProgress * loopLength).toInt().coerceIn(0, loopLength - 1)
                    )
                    // Same crossing test the walk stride uses, against this gait's own contact
                    // phases - see GameAudio.PUSH_STEP_PHASES.
                    for (phase in GameAudio.PUSH_STEP_PHASES) {
                        val crossed = if (pushCycleProgress >= previousPhase) {
                            phase > previousPhase && phase <= pushCycleProgress
                        } else {
                            phase > previousPhase || phase <= pushCycleProgress
                        }
                        if (crossed && !world.activePowerups.isNoiseSuppressed) {
                            val step = if (stepAlternate) sounds.stepB else sounds.stepA
                            stepAlternate = !stepAlternate
                            step.playSfx(
                                sfxContext, GameAudio.STEP_GAIN, sfxVolume(),
                                if (step === sounds.stepA) GameAudio.SfxFile.STEP_A else GameAudio.SfxFile.STEP_B
                            )
                        }
                    }
                } else {
                    pushStriding = false
                    if (pushLoopClipLoaded) {
                        playerSprite.playAnimationLooped(playerAnimations.pushTransition, manualFrameTime)
                        pushLoopClipLoaded = false
                    }
                    // Braced but not moving pins the transition's last frame - its settled brace -
                    // rather than freezing the gait loop mid-step.
                    val t = world.pushStanceBlend.coerceIn(0.0, 1.0)
                    playerSprite.setFrame(
                        (t * pushTransitionLastFrame).roundToInt().coerceIn(0, pushTransitionLastFrame)
                    )
                }
            } else {
                windOwnsSprite = false
                if (playerAnimState == "push") {
                    // Fully upright again: the transition's own frame 0 IS the standing pose, so
                    // handing straight back to idle/walk below is a plain clip swap, not a pop.
                    playerAnimState = "none"
                    pushStriding = false
                    pushCycleProgress = 0.0
                }
                if (playerAnimState == "wind") {
                    // Same free handover as push: windtransition frame 0 IS the standing pose.
                    playerAnimState = "none"
                    windStriding = false
                    windCycleProgress = 0.0
                }
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
                    // A climb that ended crouched (Player.climbEndsCrouched - a ledge with a
                    // ceiling over it) has already played its own settle, stopping at the clip
                    // frame that matches this pose. Hand it the HELD crouch directly: letting the
                    // crouch machine below see isCrouching turn true would start it at "entering",
                    // i.e. from the clip's standing frame, snapping the character upright through
                    // the ceiling he just ducked under and then lowering him back into it.
                    if (world.player.isCrouching) {
                        playerAnimState = "crouch"
                        crouchPhase = "holding"
                        crouchFrameProgress = crouchLastFrame.toDouble()
                        playerSprite.playAnimationLooped(playerAnimations.crouch, manualFrameTime)
                        playerSprite.setFrame(crouchLastFrame)
                    }
                    val step = if (stepAlternate) sounds.stepB else sounds.stepA
                    stepAlternate = !stepAlternate
                    if (!world.activePowerups.isNoiseSuppressed) {
                        step.playSfx(sfxContext, GameAudio.STEP_GAIN, sfxVolume(), if (step === sounds.stepA) GameAudio.SfxFile.STEP_A else GameAudio.SfxFile.STEP_B)
                    }
                } else if (climbExitTimer > 0.0) {
                    climbExitTimer = maxOf(0.0, climbExitTimer - dtSec)
                }

                // Jump / Airborne animation machine.
                //
                // A CROUCHED player is excluded: crouch-walking off a ledge kept the 56-unit
                // crouch hitbox (nothing stands them up in mid-air) while this switched the sprite
                // to the drop pose, which is drawn ~98 units tall - so the head shot up 40 units
                // above where the body actually was. Under LEVEL_6_LAYOUT's boom that put the
                // drawn character straight through the beam he was crawling under, reported as
                // "when dropping while crouching, the player goes above that beam". A crouched
                // fall keeps the crouched pose, which is what the hitbox says is happening.
                if (playerAnimState != "jump" && !world.player.isGrounded && !world.player.isCrouching) {
                    val wasMoving = (playerAnimState == "walk") || world.player.isMoving || abs(world.player.vx) > 5.0
                    // Depth the stance was actually at when the jump started: mid-descent from the
                    // crouch machine's own progress, or the held pose if they were crouch-walking.
                    val leftACrouchFrom = when (playerAnimState) {
                        "crouch" -> crouchFrameProgress
                        "crouchwalk" -> crouchLastFrame.toDouble()
                        else -> -1.0
                    }
                    playerAnimState = "jump"
                    landingAbsorb = false  // cancel any in-progress absorption
                    jumpStartY = world.player.y
                    jumpPhaseElapsed = 0.0
                    // Jumped straight out of a crouch - Player allows that wherever the body has
                    // room to extend. Spring back out of the stance before the jump clip starts.
                    if (leftACrouchFrom > 0.0 && world.player.vy < 0.0) {
                        crouchJumpSpringElapsed = 0.0
                        crouchJumpSpringFrom = leftACrouchFrom.coerceIn(0.0, crouchLastFrame.toDouble())
                        playerSprite.playAnimationLooped(playerAnimations.crouch, manualFrameTime)
                    } else {
                        crouchJumpSpringElapsed = -1.0
                        playerSprite.playAnimationLooped(playerAnimations.jump, manualFrameTime)
                    }
                    // If moving upward, it's an intentional jump; if falling downwards, it's stepping/falling off a ledge
                    jumpPhase = if (world.player.vy < 0.0) "launch" else "drop"
                    dropFromWalk = wasMoving && jumpPhase == "drop"
                } else if (playerAnimState == "jump") {
                    jumpPhaseElapsed += dtSec
                    if (crouchJumpSpringElapsed >= 0.0) {
                        crouchJumpSpringElapsed += dtSec
                        if (crouchJumpSpringElapsed >= crouchJumpSpringDuration) {
                            crouchJumpSpringElapsed = -1.0
                            playerSprite.playAnimationLooped(playerAnimations.jump, manualFrameTime)
                        }
                    }
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
                            if (!world.activePowerups.isNoiseSuppressed) {
                                sounds.impact.playSfx(sfxContext, GameAudio.LANDING_GAIN, sfxVolume(), GameAudio.SfxFile.IMPACT)
                            }
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

                // Crouch animation machine: normally gated on grounded, so an airborne
                // crouch-input (edge case in the physics) still shows the jump animation rather
                // than fighting it - but a player who was ALREADY crouched when they left the
                // ground keeps the stance and the pose the whole way down (see the jump machine
                // just above). Their feet are moving through air, so the gait is held still.
                val crouchedInTheAir = world.player.isCrouching && !world.player.isGrounded
                val crouchMoving = world.player.isMoving && !crouchedInTheAir
                if (crouchedInTheAir) {
                    crouchFallAirborne = true
                } else if (crouchFallAirborne && world.player.isGrounded) {
                    crouchFallAirborne = false
                    if (!world.activePowerups.isNoiseSuppressed) {
                        sounds.impact.playSfx(sfxContext, GameAudio.LANDING_GAIN, sfxVolume(), GameAudio.SfxFile.IMPACT)
                    }
                }
                if (playerAnimState != "jump" && (world.player.isGrounded || crouchedInTheAir)) {
                    if (world.player.isCrouching) {
                        if (playerAnimState != "crouch" && playerAnimState != "crouchwalk") {
                            playerAnimState = "crouch"
                            crouchPhase = "entering"
                            playerSprite.playAnimationLooped(playerAnimations.crouch, manualFrameTime)
                        } else if (playerAnimState == "crouch" && crouchPhase == "exiting") {
                            crouchPhase = "entering"
                        }
                        
                        if (playerAnimState == "crouch" && crouchPhase == "holding" && crouchMoving) {
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
                        } else if (playerAnimState == "crouchwalk" && !crouchMoving && crouchStationaryElapsed >= 0.10) {
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
                && playerAnimState != "swing" && playerAnimState != "landAbsorb"
                && playerAnimState != "push" && playerAnimState != "wind") {
                val wantsWalk = world.player.isMoving || (tapWalkGraceTimer > 0.0 && !world.player.isCrouching)
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
                            if (!world.activePowerups.isNoiseSuppressed) {
                                step.playSfx(sfxContext, GameAudio.STEP_GAIN, sfxVolume(), if (step === sounds.stepA) GameAudio.SfxFile.STEP_A else GameAudio.SfxFile.STEP_B)
                            }
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

            val onTruck = isPlayerOnTruck()
            val targetGroundingOffset = when (playerAnimState) {
                "idle" -> idleFeetOffset
                "walk" -> if (onTruck) walkFeetOffset else 0.0
                else -> 0.0
            }
            val justLanded = world.player.isGrounded && !playerWasGroundedBefore
            playerWasGroundedBefore = world.player.isGrounded

            if (justLanded) {
                currentGroundingOffset = targetGroundingOffset
            } else if (world.player.isGrounded) {
                currentGroundingOffset += (targetGroundingOffset - currentGroundingOffset) * (dtSec * 25.0).coerceIn(0.0, 1.0)
            } else {
                currentGroundingOffset = targetGroundingOffset
            }

            if (!world.player.isSwinging) {
                playerSprite.y = world.player.height + when {
                    playerAnimState == "idle" || playerAnimState == "walk" -> currentGroundingOffset
                    playerAnimState == "crouch" || playerAnimState == "crouchwalk" -> crouchFeetOffset
                    playerAnimState == "climb" -> climbFeetOffset
                    playerAnimState == "landAbsorb" -> jumpLandFeetOffset
                    else -> 0.0
                }
            }
            // The wind stance sets its own frame up in the state machine, and it has to say so
            // here explicitly rather than rely on playerAnimState: the frame driver below is a
            // second pass keyed off that same string, and something else in this updater puts it
            // back to "walk" before the pass runs, so the walk gait was overwriting the wind clip
            // every frame. Traced frame by frame in the running game - the state machine really
            // did set "wind" and the driver really did read "walk" in the same frame.
            if (windOwnsSprite) {
                // nothing: the wind branch above already picked the frame
            } else if (playerAnimState == "jump" && crouchJumpSpringElapsed >= 0.0) {
                // The spring itself: the crouch clip run backwards from the depth the stance was
                // at to standing, over crouchJumpSpringDuration. The body is already rising on
                // physics by now, so this is deliberately fast - it is the extension, not a
                // wind-up, and a slower one would read as floating up still folded.
                val t = (crouchJumpSpringElapsed / crouchJumpSpringDuration).coerceIn(0.0, 1.0)
                playerSprite.setFrame(
                    (crouchJumpSpringFrom * (1.0 - t)).roundToInt().coerceIn(0, crouchLastFrame)
                )
                playerSprite.y += crouchFeetOffset
            } else if (playerAnimState == "jump") {
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
                    val shouldAdvanceWalk = world.player.isMoving || (tapWalkGraceTimer > 0.0 && !world.player.isCrouching)
                    if (shouldAdvanceWalk) {
                        val strideSpeed = if (tapWalkGraceTimer > 0.0) {
                            maxOf(abs(world.player.vx), world.player.moveSpeed)
                        } else {
                            abs(world.player.vx)
                        }
                        walkCycleProgress =
                            (walkCycleProgress + strideSpeed * dtSec / walkCycleDistance) % 1.0
                    }
                    val loopLength = PlayerAnimations.WALK_LOOP_LENGTH
                    playerSprite.setFrame(
                        PlayerAnimations.WALK_LOOP_START +
                            (walkCycleProgress * loopLength).toInt().coerceIn(0, loopLength - 1)
                    )

                    // A footstep for each contact phase the cycle passed this tick. Written as a
                    // crossing test rather than "is the phase near X" so it still fires exactly
                    // once at low frame rates or high speed, and survives the wrap at 1.0.
                    if (shouldAdvanceWalk) {
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
                                if (!world.activePowerups.isNoiseSuppressed) {
                                    step.playSfx(sfxContext, GameAudio.STEP_GAIN, sfxVolume(), if (step === sounds.stepA) GameAudio.SfxFile.STEP_A else GameAudio.SfxFile.STEP_B)
                                }
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
            } else if (playerAnimState == "push") {
                // Held, not followed: the whole point of the stance is that he is braced against
                // something in one direction. Walking the other way drags the load back rather
                // than spinning the braced silhouette around on the spot.
                playerFacingLeft = pushFacingLeft
            } else if (playerAnimState == "wind") {
                playerFacingLeft = moveInput < 0.0
            } else if (moveInput < 0) {
                playerFacingLeft = true
            } else if (moveInput > 0 || forwardTap) {
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

            // Laser Shield: 1-Pixel Silhouette Rim Glow & Deflection FX
            if (shieldDeflectFlashTimer > 0.0) {
                shieldDeflectFlashTimer = (shieldDeflectFlashTimer - dtSec).coerceAtLeast(0.0)
            }
            if (shieldFlareTimer > 0.0) {
                shieldFlareTimer = (shieldFlareTimer - dtSec).coerceAtLeast(0.0)
                shieldFlareImage.alpha = (shieldFlareTimer / 0.25).coerceIn(0.0, 1.0)
                shieldFlareImage.scale = 1.0 + (1.0 - shieldFlareTimer / 0.25) * 0.8
                if (shieldFlareTimer <= 0.0) {
                    shieldFlareImage.visible = false
                }
            }

            val isShieldActive = world.activePowerups.isLaserShieldActive
            val isDeflecting = shieldDeflectFlashTimer > 0.0
            shieldGlowContainer.visible = isShieldActive || isDeflecting

            if (shieldGlowContainer.visible) {
                if (isDeflecting) {
                    shieldColorFilter.colorTransform = shieldWhiteTransform
                    shieldBlurFilter.radius = 2.8
                    shieldGlowContainer.alpha = 1.0
                } else {
                    shieldColorFilter.colorTransform = shieldCyanTransform
                    shieldBlurFilter.radius = 1.8
                    val pulse = (0.75 + 0.20 * sin(totalElapsedSeconds * 5.0)).coerceIn(0.5, 1.0)
                    shieldGlowContainer.alpha = pulse
                }

                shieldGlowImage.bitmap = playerSprite.bitmap
                shieldGlowImage.xy(playerSprite.x, playerSprite.y)
                shieldGlowImage.scaleX = playerSprite.scaleX
                shieldGlowImage.scaleY = playerSprite.scaleY
                shieldGlowImage.rotation = playerSprite.rotation
            }

            // Player alpha: flickering invulnerability during grace period, or invisibility
            if (world.laserGraceTimer > 0.0) {
                val blink = (sin(totalElapsedSeconds * 35.0) > 0.0)
                playerSprite.alpha = if (blink) 0.35 else 0.85
            } else {
                playerSprite.alpha = if (world.activePowerups.isInvisibilityActive) 0.35 else 1.0
            }

            // Update guard visors and badges
            for (i in world.allGuards.indices) {
                val g = world.allGuards[i]
                // The frames face right; a left-facing guard is the same frames mirrored about
                // the hitbox centre, which the crop box is symmetric about (GuardAnimations).
                val guardSprite = guardSprites[i]
                if (guardSprite != null && guardAnimations != null) {
                    guardSprite.scaleX = guardBaseScale[i] * (if (g.facing >= 0.0) 1.0 else -1.0)
                    if (g.isWalking != guardAnimWalking[i]) {
                        guardAnimWalking[i] = g.isWalking
                        if (g.isWalking) {
                            guardWalkProgress[i] = 0.0
                            guardSprite.playAnimationLooped(guardAnimations.walk, manualFrameTime)
                            guardSprite.setFrame(0)
                            guardSprite.y = g.height + GuardAnimations.FEET_GROUND_NUDGE
                        } else {
                            guardSprite.playAnimationLooped(guardAnimations.idle, GuardAnimations.IDLE_FRAME_TIME_MS.milliseconds)
                            guardSprite.y = g.height + guardIdleFeetOffset[i] + GuardAnimations.FEET_GROUND_NUDGE
                        }
                    }
                    if (g.isWalking) {
                        guardWalkProgress[i] = (guardWalkProgress[i] + abs(g.x - guardPrevX[i]) / guardWalkCycleDistance[i]) % 1.0
                        guardSprite.setFrame((guardWalkProgress[i] * GuardAnimations.WALK_FRAMES).toInt().coerceIn(0, GuardAnimations.WALK_FRAMES - 1))
                    }
                    guardPrevX[i] = g.x
                }
                if (world.activePowerups.isPhantomCloakActive) {
                    guardBadges[i].text = "Zzz"
                    guardBadges[i].color = COLOR_BORDER_CYAN
                    guardBadges[i].visible = true
                    guardVisors[i]?.x = if (g.facing >= 0) g.width - 6.0 else 0.0
                    guardVisors[i]?.color = Colors["#34495e"]
                } else {
                    // The investigating "?" is now carried by the guard's own detection pip.
                    guardBadges[i].visible = false
                    guardVisors[i]?.x = if (g.facing >= 0) g.width - 6.0 else 0.0
                    guardVisors[i]?.color =
                        if (g.state == GuardState.INVESTIGATING) COLOR_BORDER_GOLD else Colors["#e74c3c"]
                }

                val isInvestigatingNoise = g.state == GuardState.INVESTIGATING && g.investigatedFromNoise && !world.activePowerups.isNoiseSuppressed
                if (isInvestigatingNoise && !guardWasInvestigating[i]) {
                    sounds.guardInvestigate.playSfx(sfxContext, GameAudio.GUARD_INVESTIGATE_GAIN, sfxVolume(), GameAudio.SfxFile.GUARD_INVESTIGATE)
                }
                guardWasInvestigating[i] = isInvestigatingNoise
            }

            val alertProgress = world.alertProgress

            // Render guard torch beams. One colour, in every state: the beam is the light from
            // the torch he holds, and what it is doing to the player is already told by the
            // detection pip over the guard's head (guardPips) - the old orange/gold/pulsing-red
            // colour ramp said the same thing twice and the owner asked for it to go. Cameras
            // keep theirs. The beam starts at the torch lens (Guard.eyePosition), the same point
            // the detection rays start from, so what is lit is what can see the player.
            for (i in world.allGuards.indices) {
                val g = world.allGuards[i]
                val cone = guardCones[i]
                if (world.activePowerups.isPhantomCloakActive) {
                    cone.clear()
                    guardConeLensX[i] = Double.NaN
                    continue
                }
                val lens = g.eyePosition
                // Same window as the static culling: a beam that cannot reach the screen is
                // neither raycast nor drawn. This is also what keeps level 1's parked, disabled
                // guard (x = -500) from costing anything.
                val onScreen = lens.x + g.visionRange >= cullLeft && lens.x - g.visionRange <= cullRight
                cone.visible = onScreen
                if (!onScreen) continue
                val facing = g.facingAngle
                if (lens.x != guardConeLensX[i] || lens.y != guardConeLensY[i] ||
                    facing != guardConeFacing[i] || g.visionRange != guardConeRange[i]
                ) {
                    guardConeLensX[i] = lens.x
                    guardConeLensY[i] = lens.y
                    guardConeFacing[i] = facing
                    guardConeRange[i] = g.visionRange
                    val visionPolygon = VisionSystem.computeVisionPolygon(
                        origin = lens,
                        facingAngle = facing,
                        range = g.visionRange,
                        fov = g.visionFov,
                        occluders = world.occluders
                    )
                    cone.setBeam(visionPolygon, facing, g.visionFov, g.visionRange, glowRadius = g.height * 0.08)
                }
            }

            // Render camera vision cones and status
            for (i in world.cameras.indices) {
                val c = world.cameras[i]
                // Tracks currentAngle every frame, corrected for the unrotated art's own baseline
                // (see cameraArtBaselineAngle above) - the lens visually sweeps with the cone
                // instead of sitting fixed while the light beam swings independently of it.
                cameraPivots[i].rotation = (c.currentAngle - cameraArtBaselineAngle).radians
                if (world.activePowerups.isSmokeScreenActive) {
                    cameraCones[i].updateShape { }
                } else {
                    // Camera cones stay a single steady color in every state (detection is indicated
                    // by the pip over the camera, matching the guard beam design) - no yellow/red alert ramp.
                    val coneColor = Colors.WHITE.withAd(0.32)
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

            // Detection pips. Nothing is drawn on an entity that cannot see or hear the player, so
            // a clean run has none on screen at all - the absence is the "stealth 100%" readout.
            // The clock (filling meter) is reserved for an entity that actually has eyes on the
            // player right now; hearing a noise (INVESTIGATING without vision) gets the plain "!"
            // badge instead, never the clock - the clock means "look at me, I'm building toward a
            // catch", which isn't true yet from sound alone. The instant vision confirms it (the
            // same guard that heard the noise walks into view), seeing wins and the "!" is gone.
            val pipPulse = 0.5 + 0.5 * sin(totalElapsedSeconds * 16.0)

            fun clockProgressFor(seeing: Boolean): Double = when {
                seeing && world.isGameOver -> 1.0
                seeing -> world.alertProgress.coerceAtLeast(0.05)
                else -> 0.0
            }

            fun paintPip(pip: Graphics, seeing: Boolean, investigating: Boolean) {
                when {
                    seeing -> {
                        pip.visible = true
                        val progress = clockProgressFor(true)
                        pip.drawDetectPip(progress, detectPipTint(progress), pipPulse)
                    }
                    investigating -> {
                        pip.visible = true
                        pip.drawInvestigateMark(COLOR_BORDER_GOLD, pipPulse)
                    }
                    else -> pip.visible = false
                }
            }

            for (i in world.allGuards.indices) {
                val g = world.allGuards[i]
                if (world.activePowerups.isPhantomCloakActive) {
                    guardPips[i].visible = false
                } else {
                    val heardNoise = g.state == GuardState.INVESTIGATING && g.investigatedFromNoise && !world.activePowerups.isNoiseSuppressed
                    paintPip(guardPips[i], g in world.detectingGuards, heardNoise)
                }
            }
            for (i in world.cameras.indices) {
                val isDetecting = world.cameras[i] in world.detectingCameras
                if (isDetecting && !cameraWasDetecting[i]) {
                    sounds.cameraDetect.playSfx(sfxContext, GameAudio.CAMERA_DETECT_GAIN, sfxVolume(), GameAudio.SfxFile.CAMERA_DETECT)
                }
                cameraWasDetecting[i] = isDetecting
                if (world.activePowerups.isSmokeScreenActive) {
                    cameraPips[i].visible = false
                } else {
                    paintPip(cameraPips[i], isDetecting, investigating = false)
                }
            }
            for (i in cameraBotVisuals.indices) {
                val bot = cameraBotVisuals[i].bot
                val isDetecting = bot in world.detectingCameraBots
                if (isDetecting && !cameraBotWasDetecting[i]) {
                    sounds.cameraDetect.playSfx(sfxContext, GameAudio.CAMERA_DETECT_GAIN, sfxVolume(), GameAudio.SfxFile.CAMERA_DETECT)
                }
                cameraBotWasDetecting[i] = isDetecting
                if (world.activePowerups.isSmokeScreenActive || bot.isDeactivated) {
                    cameraBotPips[i].visible = false
                } else {
                    paintPip(cameraBotPips[i], isDetecting, investigating = false)
                }
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
        val r = 7.5
        stroke(color, StrokeInfo(thickness = 3.2)) {
            val steps = 28
            for (i in 0..steps) {
                val a = (-PI * 0.35) + (PI * 1.55) * (i.toDouble() / steps)
                val px = cos(a) * r - 0.5
                val py = sin(a) * r
                if (i == 0) moveTo(Point(px, py)) else lineTo(Point(px, py))
            }
        }
        fill(color) {
            moveTo(0.0, -9.5)
            lineTo(8.5, -6.5)
            lineTo(1.5, -2.5)
            close()
        }
    }

    /**
     * Solid silhouette home/house icon for MAIN MENU with an open doorway cutout.
     */
    private fun ShapeBuilder.drawMainMenuHomeIcon(color: RGBA) {
        fill(color) {
            moveTo(0.0, -9.0)
            lineTo(9.5, -1.0)
            lineTo(7.2, -1.0)
            lineTo(7.2, 8.5)
            lineTo(2.2, 8.5)
            lineTo(2.2, 2.0)
            lineTo(-2.2, 2.0)
            lineTo(-2.2, 8.5)
            lineTo(-7.2, 8.5)
            lineTo(-7.2, -1.0)
            lineTo(-9.5, -1.0)
            close()
        }
    }

    /**
     * Double forward-pointing solid triangles for NEXT MISSION, communicating level advancement.
     */
    private fun ShapeBuilder.drawNextMissionIcon(color: RGBA) {
        fill(color) {
            // Triangle 1 (left)
            moveTo(-8.5, -7.5)
            lineTo(-1.0, 0.0)
            lineTo(-8.5, 7.5)
            close()
            // Triangle 2 (right)
            moveTo(0.5, -7.5)
            lineTo(8.0, 0.0)
            lineTo(0.5, 7.5)
            close()
        }
    }

    /**
     * Filled movie clapperboard icon for the watch-ad-to-continue button, with an empty
     * (transparent cutout) play triangle in the center and striped clapper teeth on top.
     * Centered vertically so its body and play triangle align with adjacent button text.
     */
    private fun ShapeBuilder.drawWatchAdIcon(color: RGBA) {
        fill(color) {
            // 1. Clapperboard main body with cutout center:
            // Left block & top/bottom border strips
            moveTo(-9.0, -1.5)
            lineTo(9.0, -1.5)
            lineTo(9.0, 0.7)
            lineTo(-2.5, 0.7)
            lineTo(-2.5, 5.9)
            lineTo(9.0, 5.9)
            lineTo(9.0, 7.5)
            lineTo(-9.0, 7.5)
            close()

            // Top wedge above cutout play triangle
            moveTo(-2.5, 0.7)
            lineTo(9.0, 0.7)
            lineTo(9.0, 3.3)
            lineTo(3.5, 3.3)
            close()

            // Bottom wedge below cutout play triangle
            moveTo(-2.5, 5.9)
            lineTo(3.5, 3.3)
            lineTo(9.0, 3.3)
            lineTo(9.0, 5.9)
            close()

            // 2. Angled clapper stick with striped teeth
            // Tooth 1 (left)
            moveTo(-9.0, -2.7)
            lineTo(-4.5, -3.9)
            lineTo(-3.6, -7.7)
            lineTo(-9.0, -6.3)
            close()
            // Tooth 2 (middle)
            moveTo(-2.5, -4.5)
            lineTo(2.0, -5.7)
            lineTo(3.0, -9.5)
            lineTo(-1.6, -8.1)
            close()
            // Tooth 3 (right)
            moveTo(4.0, -6.1)
            lineTo(8.5, -7.3)
            lineTo(9.0, -11.0)
            lineTo(5.0, -9.7)
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
        private const val CENTERED_ICON_WIDTH = 18.0
        private const val CENTERED_ICON_GAP = 10.0
    }

    private class LoadingScreenHandle(
        val setProgress: (Double) -> Unit,
        val dismiss: () -> Unit
    )

    private class CaughtOverlayHandle(
        val container: Container,
        val show: (timeTaken: Float, alerts: Int, best: LevelResult?, coins: Int, canContinue: Boolean) -> Unit,
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
        textSize: Double = height * 0.44,
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
        val text = btn.text(label.uppercase(), textSize = textSize, font = font, color = paperInk)
        text.graphicsRenderer = GraphicsRenderer.GPU

        val textY = (height - text.height) / 2.0
        // Center the icon vertically with the visual center of the text glyphs
        // (Bebas Neue is all-caps without descenders, so the optical center of the letters is at textY + text.height * 0.44)
        val iconY = textY + text.height * 0.44

        if (centered) {
            val contentW = CENTERED_ICON_WIDTH + CENTERED_ICON_GAP + text.width
            val contentX = (width - contentW) / 2.0
            iconG.xy(contentX + CENTERED_ICON_WIDTH / 2.0, iconY)
            text.xy(contentX + CENTERED_ICON_WIDTH + CENTERED_ICON_GAP, textY)
        } else {
            iconG.xy(width * ICON_COLUMN, iconY)
            text.xy(width * LABEL_COLUMN, textY)
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
        loadingFont: Font,
        currentLanguage: String = "en"
    ): LoadingScreenHandle {
        val loadingRoot = container()
        if (loadingBgBitmap != null) {
            loadingRoot.image(loadingBgBitmap) { size(canvasW, canvasH) }
        } else {
            loadingRoot.solidRect(canvasW, canvasH, Colors.BLACK)
        }

        // The splash is sized off the canvas HEIGHT, unlike everything else on this screen.
        //
        // The gameplay rule (game.model.ScreenLayout) keeps the authored 1040x480 rect and grows
        // the canvas around it, so a squarer screen gets extra sky rather than a bigger world.
        // That is right for a level and wrong for a title card: sized as a share of width, the
        // logo holds 34% of the width everywhere but falls from a quarter of the reference
        // phone's height to a seventh of a 4:3 iPad's, with the difference left as dead margin -
        // which is the "logo too small, bar too short on iPad" report. Scaling by
        // canvasH / DESIGN_HEIGHT instead holds the share of HEIGHT constant, which is what the
        // eye is actually measuring against.
        //
        // The ceiling guards a canvas squarer than any real device (ScreenLayout clamps at 1:1,
        // where this would otherwise reach 2.17). A 4:3 iPad lands at 1.625 and gives up 1.5%.
        //
        // Written as an aspect ratio rather than as canvasH / DESIGN_HEIGHT, which is the same
        // number only while the canvas is exactly the design rect grown to fit. ScreenLayout's
        // zoom cap broke that identity - a 4:3 canvas is now 800x600, not 1040x780 - and the
        // height-only form silently shrank the logo back to 43% of the screen on a tablet, undoing
        // the fix above. This form holds the share of height constant whatever canvas it is handed.
        val splashScale =
            (ScreenLayout.DESIGN_ASPECT * canvasH / canvasW).coerceIn(1.0, 1.6)

        val loadingLogoWidth = canvasW * 0.34 * splashScale
        val loadingLogoHeight = if (loadingLogoBitmap != null) {
            loadingLogoWidth * loadingLogoBitmap.height / loadingLogoBitmap.width
        } else {
            0.0
        }
        val loadingBarWidth = canvasW * 0.30 * splashScale
        // The bar's width drives its height so it keeps the flat sliver proportion it was drawn
        // with instead of thickening on a taller canvas. 14.4 is the reference 312 x 21.6.
        val loadingBarHeight = loadingBarWidth / 14.4
        val loadingLogoGap = 35.0 * splashScale
        val loadingLabelGap = 17.0 * splashScale
        val loadingLabelHeight = loadingBarHeight * 0.62

        // Centre the stack as one block instead of pinning each piece to its own fraction of the
        // height - fractions push the pieces apart as the canvas grows, which is the other half
        // of why this read as small and scattered on an iPad. 0.474 is where the reference
        // stack's centre already sat, so a 1040x480 canvas still renders what it always did
        // (logo top 125.0 vs 124.8, bar top 277.9 vs 278.4).
        val loadingBlockHeight = loadingLogoHeight + loadingLogoGap + loadingBarHeight +
            loadingLabelGap + loadingLabelHeight
        val loadingBlockTop = canvasH * 0.474 - loadingBlockHeight / 2.0

        if (loadingLogoBitmap != null) {
            val logoScale = loadingLogoWidth / loadingLogoBitmap.width
            loadingRoot.image(loadingLogoBitmap) { scale(logoScale) }
                .xy((canvasW - loadingLogoWidth) / 2.0, loadingBlockTop)
        }

        val loadingBarX = (canvasW - loadingBarWidth) / 2.0
        val loadingBarY = loadingBlockTop + loadingLogoHeight + loadingLogoGap

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
            Localization.loading(currentLanguage),
            textSize = loadingLabelHeight,
            font = loadingFont,
            color = Colors.WHITE
        )
        loadingLabel.graphicsRenderer = GraphicsRenderer.GPU
        val labelW = try { loadingLabel.width } catch (_: Throwable) { 120.0 }
        loadingLabel.xy((canvasW - labelW) / 2.0, loadingBarY + loadingBarHeight + loadingLabelGap)

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
        currentLanguage: String = "en",
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

        val pauseTitle = pauseOverlay.text(Localization.paused(currentLanguage), textSize = 52.0, font = bebasFont, color = Colors["#F6F4EE"])
        pauseTitle.graphicsRenderer = GraphicsRenderer.GPU
        pauseTitle.xy((canvasW - pauseTitle.width) / 2.0, pauseBlockTop)

        val pauseSubtitle = pauseOverlay.text(
            levelName.uppercase(), textSize = 14.0, font = bebasFont, color = COLOR_TEXT_MUTED
        )
        pauseSubtitle.graphicsRenderer = GraphicsRenderer.GPU
        pauseSubtitle.xy((canvasW - pauseSubtitle.width) / 2.0, pauseBlockTop + 62.0)

        val pauseBtnY0 = pauseBlockTop + 52.0 + 8.0 + 18.0 + 30.0

        pauseOverlay.createPaperMenuBtn(
            Localization.resume(currentLanguage), paperBtnBitmaps[0], pauseBtnW, pauseBtnH, pauseBtnX, pauseBtnY0,
            bebasFont, paperInk, playClick = playClick,
            iconDrawer = { drawPlayIcon(false) }
        ) {
            pauseOverlay.visible = false
            onResume()
        }

        pauseOverlay.createPaperMenuBtn(
            Localization.restart(currentLanguage), paperBtnBitmaps[1], pauseBtnW, pauseBtnH, pauseBtnX, pauseBtnY0 + pauseBtnH + pauseBtnGap,
            bebasFont, paperInk, playClick = playClick,
            iconDrawer = { drawRestartIcon(paperInk) }
        ) {
            pauseOverlay.visible = false
            onRestart()
        }

        pauseOverlay.createPaperMenuBtn(
            Localization.quit(currentLanguage), paperBtnBitmaps[2], pauseBtnW, pauseBtnH, pauseBtnX, pauseBtnY0 + 2 * (pauseBtnH + pauseBtnGap),
            bebasFont, paperInk, playClick = playClick,
            iconDrawer = { drawQuitIcon(false) }
        ) { onQuit() }

        pauseOverlay.visible = false
        return pauseOverlay
    }

    private fun SContainer.setupCaughtOverlay(
        canvasW: Double,
        canvasH: Double,
        failedBgBitmap: Bitmap?,
        failedBtnBitmaps: List<Bitmap?>,
        bebasFont: Font,
        paperInk: RGBA,
        playClick: (Double) -> Unit,
        currentLanguage: String = "en",
        onRequestContinueAd: () -> Unit,
        onRetry: suspend () -> Unit,
        onReturnToMenu: suspend () -> Unit
    ): CaughtOverlayHandle {
        val caughtContainer = container()
        val caughtScrim = caughtContainer.solidRect(canvasW, canvasH, Colors["#07080A"].withAd(0.80))

        val failBtnW = min(175.0, (canvasW - 48.0) / 3.0)
        val failBtnGap = 16.0
        val failBtnGroupW = failBtnW * 3.0 + failBtnGap * 2.0
        val failBtnL = (canvasW - failBtnGroupW) / 2.0
        val failBtnH = 62.0
        // Recomputed here rather than passed in: it is a pure function of the canvas, and these
        // overlay builders already take the canvas. Keeps the button row off the home-indicator
        // strip on a phone that reports one.
        val bottomMargin = 10.0 + DeviceScreen.safeInsetsForCanvas(canvasW, canvasH).bottom
        val failBtnY = canvasH - bottomMargin - failBtnH
        val buttonGap = 10.0
        val topMargin = 8.0

        val maxCardH = failBtnY - buttonGap - topMargin
        val cardAspect = if (failedBgBitmap != null) {
            failedBgBitmap.width.toDouble() / failedBgBitmap.height.toDouble()
        } else {
            0.94
        }
        var cardH = maxCardH
        var cardW = cardH * cardAspect
        if (cardW > canvasW * 0.74) {
            cardW = canvasW * 0.74
            cardH = cardW / cardAspect
        }
        val cardY = topMargin + (maxCardH - cardH) / 2.0

        val cardPivot = caughtContainer.container().xy(canvasW / 2.0, cardY + cardH / 2.0)
        cardPivot.alpha = 0.0
        cardPivot.scaleX = 0.90
        cardPivot.scaleY = 0.90
        val card = cardPivot.container().xy(-cardW / 2.0, -cardH / 2.0)
        if (failedBgBitmap != null) {
            card.image(failedBgBitmap) { size(cardW, cardH) }
        } else {
            card.uiGraphics().updateShape {
                fill(Colors["#D8D2C4"]) { rect(0.0, 0.0, cardW, cardH) }
            }
        }

        class CaughtReveal(val view: View, val start: Double, val baseX: Double, val slide: Double = 0.0)
        val reveals = ArrayList<CaughtReveal>()
        val REVEAL_DUR = 0.28
        fun <T : View> T.revealAt(start: Double, slide: Double = 0.0): T {
            reveals.add(CaughtReveal(this, start, x, slide))
            visible = false
            return this
        }

        val CARD_POP = 0.30
        val BUTTONS_AT = 0.22
        val ANIM_END = BUTTONS_AT + REVEAL_DUR

        val failButtons = caughtContainer.container()
        val failBtnTextSize = failBtnH * 0.36

        val continueBtn = failButtons.createPaperMenuBtn(
            Localization.continueGame(currentLanguage), failedBtnBitmaps.getOrNull(0), failBtnW, failBtnH, failBtnL, failBtnY,
            bebasFont, paperInk, centered = true, textSize = failBtnTextSize, playClick = playClick,
            iconDrawer = { drawWatchAdIcon(paperInk) }
        ) { onRequestContinueAd() }

        val retryBtn = failButtons.createPaperMenuBtn(
            Localization.retry(currentLanguage), failedBtnBitmaps.getOrNull(1), failBtnW, failBtnH, failBtnL + failBtnW + failBtnGap, failBtnY,
            bebasFont, paperInk, centered = true, textSize = failBtnTextSize, playClick = playClick,
            iconDrawer = { drawRestartIcon(paperInk) }
        ) { onRetry() }

        val menuBtn = failButtons.createPaperMenuBtn(
            Localization.mainMenu(currentLanguage), failedBtnBitmaps.getOrNull(2), failBtnW, failBtnH,
            failBtnL + 2.0 * (failBtnW + failBtnGap), failBtnY,
            bebasFont, paperInk, centered = true, textSize = failBtnTextSize, playClick = playClick,
            iconDrawer = { drawMainMenuHomeIcon(paperInk) }
        ) { onReturnToMenu() }
        failButtons.revealAt(BUTTONS_AT)

        val skipCatcher = caughtContainer.solidRect(canvasW, canvasH, Colors.TRANSPARENT)
        var animT = -1.0
        skipCatcher.mouse { onClick { if (animT >= 0.0) animT = ANIM_END } }
        caughtScrim.mouse { onClick { if (animT >= 0.0) animT = ANIM_END } }

        caughtContainer.addUpdater { dt ->
            if (!caughtContainer.visible || animT < 0.0) return@addUpdater
            if (animT >= ANIM_END) {
                skipCatcher.visible = false
            } else {
                animT += dt.seconds
            }
            val t = animT

            val cardP = (t / CARD_POP).coerceIn(0.0, 1.0)
            val cardE = easeOutBack(cardP)
            cardPivot.alpha = (cardP * 2.0).coerceAtMost(1.0)
            cardPivot.scaleX = 0.90 + 0.10 * cardE
            cardPivot.scaleY = cardPivot.scaleX

            for (r in reveals) {
                val p = ((t - r.start) / REVEAL_DUR).coerceIn(0.0, 1.0)
                r.view.visible = p > 0.0
                if (p <= 0.0) continue
                val e = easeOutCubic(p)
                r.view.alpha = e
                if (r.slide != 0.0) r.view.x = r.baseX - r.slide * (1.0 - e)
            }
        }

        caughtContainer.visible = false

        return CaughtOverlayHandle(
            container = caughtContainer,
            show = { _, _, _, _, canContinue ->
                if (canContinue) {
                    continueBtn.visible = true
                    continueBtn.x = failBtnL
                    retryBtn.x = failBtnL + failBtnW + failBtnGap
                    menuBtn.x = failBtnL + 2.0 * (failBtnW + failBtnGap)
                } else {
                    continueBtn.visible = false
                    val failBtn2Gap = 20.0
                    val failBtn2GroupW = failBtnW * 2.0 + failBtn2Gap
                    val failBtn2L = (canvasW - failBtn2GroupW) / 2.0
                    retryBtn.x = failBtn2L
                    menuBtn.x = failBtn2L + failBtnW + failBtn2Gap
                }
                caughtContainer.visible = true
                skipCatcher.visible = true
                animT = 0.0
                cardPivot.alpha = 0.0
                cardPivot.scaleX = 0.90
                cardPivot.scaleY = 0.90
                for (r in reveals) {
                    r.view.visible = false
                    r.view.alpha = 0.0
                }
            },
            hide = { caughtContainer.visible = false }
        )
    }

    private fun SContainer.setupWinOverlay(
        canvasW: Double,
        canvasH: Double,
        successBgBitmap: Bitmap?,
        starSlices: List<BmpSlice>?,
        victoryBtnBitmaps: List<Bitmap?>,
        bebasFont: Font,
        levelData: LevelData,
        nextLevel: LevelData?,
        paperInk: RGBA,
        playClick: (Double) -> Unit,
        currentLanguage: String = "en",
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

        val winBtnW = min(175.0, (canvasW - 48.0) / 3.0)
        val winBtnGap = 16.0
        val winBtnGroupW = winBtnW * 3.0 + winBtnGap * 2.0
        val winBtnL = (canvasW - winBtnGroupW) / 2.0
        val winBtnH = 56.0

        val winCardAspect = if (successBgBitmap != null) {
            successBgBitmap.width.toDouble() / successBgBitmap.height.toDouble()
        } else {
            1.50
        }
        val winCardFactor = 0.880
        // The card and its button row are centred as one group. Centre them in the canvas MINUS
        // the home-indicator strip rather than the whole canvas: when the group is tall enough to
        // need the shrink below it ends up 12 units off the bottom edge, which on a phone that
        // reports a bottom inset is underneath the indicator. Zero everywhere that reports none.
        val winSafeBottom = DeviceScreen.safeInsetsForCanvas(canvasW, canvasH).bottom
        val winUsableH = canvasH - winSafeBottom
        var winCardW = canvasW * 0.74
        var winCardH = winCardW / winCardAspect
        var totalDialogH = winCardH * winCardFactor + winBtnH
        if (totalDialogH > winUsableH - 24.0) {
            winCardH = (winUsableH - 24.0 - winBtnH) / winCardFactor
            winCardW = winCardH * winCardAspect
            totalDialogH = winCardH * winCardFactor + winBtnH
        }
        val winCardY = (winUsableH - totalDialogH) / 2.0
        val winBtnY = winCardY + winCardH * winCardFactor

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

        val localizedLevelName = levelData.localizedName(currentLanguage)
        val missionFileNo = (Regex("^(\\d+)").find(localizedLevelName)?.groupValues?.get(1)
            ?: levelData.id.filter { it.isDigit() }.ifEmpty { "1" }).padStart(2, '0')
        val missionTitleText = localizedLevelName.replaceFirst(Regex("^\\d+:\\s*"), "").uppercase()

        winCard.container().xy(winCx, winTop).also { holder ->
            val t = holder.winText("${Localization.missionLabel(currentLanguage)} $missionFileNo - $missionTitleText", 12.0, inkFaint, 0.0, 0.0)
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
            levelData.localizedObjectiveHint(currentLanguage).uppercase(),
            Localization.noAlertsRaised(currentLanguage),
            Localization.targetTime(clockText(levelData.timeTargetSeconds), currentLanguage)
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
        val winBountyCaption = winPayoutRow.winText(Localization.bounty(currentLanguage), 11.0, inkFaint, 0.0, 8.0 * WS)
        winBountyCaption.xy((winTextW - winBountyCaption.width) / 2.0, winBountyCaption.y)
        val winCoinR = 7.5 * WS
        val winCoinGap = 6.0 * WS
        val winBountyCoin = winPayoutRow.uiGraphics()
        winBountyCoin.updateShape { drawCoinIcon(winCoinR) }
        val winBountyValue = winPayoutRow.winText("", 20.0, inkGold, 0.0, 20.0 * WS)
        winPayoutRow.revealAt(WIN_PAYOUT_AT)

        val winButtons = winContainer.container()
        val winNextLabel = if (nextLevel != null) Localization.nextMission(currentLanguage) else Localization.allClear(currentLanguage)
        val winBtnTextSize = winBtnH * 0.40

        winButtons.createPaperMenuBtn(
            Localization.retry(currentLanguage), victoryBtnBitmaps.getOrNull(0), winBtnW, winBtnH, winBtnL, winBtnY,
            bebasFont, paperInk, centered = true, textSize = winBtnTextSize, playClick = playClick,
            iconDrawer = { drawRestartIcon(paperInk) }
        ) { onRetry() }

        winButtons.createPaperMenuBtn(
            Localization.mainMenu(currentLanguage), victoryBtnBitmaps.getOrNull(1), winBtnW, winBtnH, winBtnL + winBtnW + winBtnGap, winBtnY,
            bebasFont, paperInk, centered = true, textSize = winBtnTextSize, playClick = playClick,
            iconDrawer = { drawMainMenuHomeIcon(paperInk) }
        ) { onReturnToMenu() }

        winButtons.createPaperMenuBtn(
            winNextLabel, victoryBtnBitmaps.getOrNull(2), winBtnW, winBtnH,
            winBtnL + 2.0 * (winBtnW + winBtnGap), winBtnY,
            bebasFont, paperInk, centered = true, textSize = winBtnTextSize, playClick = playClick,
            iconDrawer = { drawNextMissionIcon(paperInk) }
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

    private fun setupConveyorAnimators(
        worldView: Container,
        conveyors: List<ConveyorDef>,
        conveyorTopBitmap: Bitmap?,
        conveyorMidBitmap: Bitmap?,
        conveyorBotBitmap: Bitmap?,
        cullable: (View, Double, Double) -> Unit
    ): List<ConveyorAnimator> {
        if (conveyorTopBitmap == null || conveyorMidBitmap == null || conveyorBotBitmap == null) {
            return emptyList()
        }
        val animators = mutableListOf<ConveyorAnimator>()
        for (conveyor in conveyors) {
            val bounds = conveyor.bounds
            val visualWidth = if (bounds.right >= 7700.0) bounds.width + 140.0 else bounds.width
            val clip = worldView.clipContainer(Size(visualWidth, bounds.height)).xy(bounds.x, bounds.y)
            cullable(clip, bounds.x, visualWidth)

            val totalSrcH = 289.0 // 48 (top cleats) + 5 (slit) + 183 (mid frame) + 5 (slit) + 48 (bot cleats)
            val scale = bounds.height / totalSrcH

            val topH = 48.0 * scale
            val topTileW = 132.0 * scale
            val topY = 0.0

            val midH = 183.0 * scale
            val midTileW = 364.0 * scale
            val midY = 53.0 * scale

            val botH = 48.0 * scale
            val botTileW = 132.0 * scale
            val botY = 241.0 * scale

            val midCount = (visualWidth / midTileW).toInt() + 2
            for (i in 0 until midCount) {
                clip.image(conveyorMidBitmap) {
                    size(midTileW, midH)
                }.xy(i * midTileW, midY)
            }

            val topCount = (visualWidth / topTileW).toInt() + 3
            val topImages = (0 until topCount).map { i ->
                clip.image(conveyorTopBitmap) {
                    size(topTileW, topH)
                }.xy(i * topTileW, topY)
            }

            val botCount = (visualWidth / botTileW).toInt() + 3
            val botImages = (0 until botCount).map { i ->
                clip.image(conveyorBotBitmap) {
                    size(botTileW, botH)
                }.xy(i * botTileW, botY)
            }

            animators.add(
                ConveyorAnimator(
                    topImages = topImages,
                    botImages = botImages,
                    topTileW = topTileW,
                    botTileW = botTileW,
                    topY = topY,
                    botY = botY,
                    speed = conveyor.speed
                )
            )
        }
        return animators
    }

    private suspend fun loadGameplayBitmaps(
        bgFileName: String,
        markLoadProgress: suspend () -> Unit
    ): GameplayBitmaps {
        val bgmgBitmap = SceneAssets.bitmap(bgFileName, minified = false)
        markLoadProgress()
        val wallMarkerBitmaps: Map<String, Bitmap?> = if (bgFileName == "metalbg.png") {
            listOf("150m", "120m", "90m", "60m", "30m", "0m").associateWith {
                SceneAssets.bitmap("wall_$it.png", minified = false)
            }
        } else {
            emptyMap()
        }
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
        val woodCrateBitmap = SceneAssets.bitmap("woodcrate2.png")
        markLoadProgress()
        val poleBitmap = SceneAssets.bitmap("pole.png")
        markLoadProgress()
        val craneBitmap = SceneAssets.bitmap("crane.png", minified = false)
        markLoadProgress()
        val tableBitmap = SceneAssets.bitmap("table.png")
        markLoadProgress()
        val cameraBitmap = SceneAssets.bitmap("cameranew2.png", minified = false)
        markLoadProgress()
        val laserEmitterBitmap = SceneAssets.bitmap("laseremittor.png", minified = false)
        markLoadProgress()
        val conveyorTopBitmap = SceneAssets.bitmap("conveyor_top.png", minified = false)
        markLoadProgress()
        val conveyorMidBitmap = SceneAssets.bitmap("conveyor_mid.png", minified = false)
        markLoadProgress()
        val conveyorBotBitmap = SceneAssets.bitmap("conveyor_bot.png", minified = false)
        markLoadProgress()
        val hookBitmap = SceneAssets.bitmap("hook.png")
        markLoadProgress()
        val truckBitmap = SceneAssets.bitmap("truck.png")
        markLoadProgress()
        val entranceBitmap = SceneAssets.bitmap("entrance.png", minified = false)
        markLoadProgress()
        val exitFenceBitmap = SceneAssets.bitmap("exitfence.png", minified = false)
        markLoadProgress()
        val l4endBitmap = SceneAssets.bitmap("l4end.png", minified = false)
        markLoadProgress()
        val exitLvl7Bitmap = SceneAssets.bitmap("exitlvl7.png")
        val fanBladeBitmap = SceneAssets.bitmap("fan2_blade.png") ?: SceneAssets.bitmap("fan_blade.png") ?: SceneAssets.bitmap("fan2.png") ?: SceneAssets.bitmap("fan.png")
        val fanCoverBitmap = SceneAssets.bitmap("fan2_cover.png") ?: SceneAssets.bitmap("fan_cover.png") ?: SceneAssets.bitmap("fan2.png") ?: SceneAssets.bitmap("fan.png")
        val robotBodyBitmap = SceneAssets.bitmap("robot_body.png")
        val robotWheelBitmap = SceneAssets.bitmap("robot_wheel.png")
        val steamNozzleUpBitmap = SceneAssets.bitmap("steam_nozzle_up.png")
        val steamNozzleDownBitmap = SceneAssets.bitmap("steam_nozzle_down.png")
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
        val leverBottomBitmap = SceneAssets.bitmap("lever_bottom.png")
        markLoadProgress()
        val leverTopBitmap = SceneAssets.bitmap("lever_top.png")
        markLoadProgress()
        val ropeBitmap = SceneAssets.bitmap("newrope.png") ?: SceneAssets.bitmap("rope.png")
        markLoadProgress()
        val ropeDissolveBitmaps = (0..8).map {
            SceneAssets.bitmap("rope_dissolve_$it.png") ?: (if (it == 0) ropeBitmap else null)
        }.filterNotNull()
        markLoadProgress()
        val paperBtnBitmaps = listOf("button1.png", "button2.png", "button3.png", "button4.png")
            .map { name -> SceneAssets.bitmap(name, minified = false) }
        markLoadProgress()
        val victoryBtnBitmaps = listOf("victorybutton1.png", "victorybutton2.png", "victorybutton3.png")
            .map { name -> SceneAssets.bitmap(name, minified = false) }
        markLoadProgress()
        val failedBtnBitmaps = listOf("failedbutton1.png", "failedbutton2.png", "failedbutton3.png")
            .map { name -> SceneAssets.bitmap(name, minified = false) }
        markLoadProgress()
        val dossierBitmap = SceneAssets.bitmap("dossier_paper.png", minified = false)
        markLoadProgress()
        val failedBgBitmap = SceneAssets.bitmap("failedscreen.png", minified = false)
        markLoadProgress()
        val gadgetBitmaps = listOf(
            "gadget_checkpoints.png", "gadget_checkpoint.png", "gadget_lasershield.png", "gadget_invis.png",
            "gadget_boots.png", "gadget_prototype.png"
        ).map { SceneAssets.bitmap(it) }
        markLoadProgress()
        val gadgetBoltBitmap = SceneAssets.bitmap("gadget_bolt.png")
        markLoadProgress()
        val successBgBitmap = SceneAssets.bitmap("success3.png", minified = false)
        markLoadProgress()
        val starsBitmap = SceneAssets.bitmap("stars.png", minified = false)
        val starSlices = starsBitmap?.let {
            listOf(
                it.sliceWithSize(69, 33, 636, 611),
                it.sliceWithSize(760, 33, 647, 611),
                it.sliceWithSize(1464, 33, 641, 611)
            )
        }
        markLoadProgress()

        return GameplayBitmaps(
            bgmgBitmap = bgmgBitmap,
            crateBitmap = crateBitmap,
            chainedCrateBitmap = chainedCrateBitmap,
            chainedCrate2Bitmap = chainedCrate2Bitmap,
            fenceBitmap = fenceBitmap,
            fence2Bitmap = fence2Bitmap,
            barrelBitmap = barrelBitmap,
            woodCrateBitmap = woodCrateBitmap,
            poleBitmap = poleBitmap,
            craneBitmap = craneBitmap,
            tableBitmap = tableBitmap,
            cameraBitmap = cameraBitmap,
            laserEmitterBitmap = laserEmitterBitmap,
            conveyorTopBitmap = conveyorTopBitmap,
            conveyorMidBitmap = conveyorMidBitmap,
            conveyorBotBitmap = conveyorBotBitmap,
            hookBitmap = hookBitmap,
            truckBitmap = truckBitmap,
            entranceBitmap = entranceBitmap,
            exitFenceBitmap = exitFenceBitmap,
            l4endBitmap = l4endBitmap,
            exitLvl7Bitmap = exitLvl7Bitmap,
            fanBladeBitmap = fanBladeBitmap,
            fanCoverBitmap = fanCoverBitmap,
            robotBodyBitmap = robotBodyBitmap,
            robotWheelBitmap = robotWheelBitmap,
            steamNozzleUpBitmap = steamNozzleUpBitmap,
            steamNozzleDownBitmap = steamNozzleDownBitmap,
            leftBtnBitmap = leftBtnBitmap,
            rightBtnBitmap = rightBtnBitmap,
            crouchBtnBitmap = crouchBtnBitmap,
            jumpBtnBitmap = jumpBtnBitmap,
            interactBtnBitmap = interactBtnBitmap,
            leverBottomBitmap = leverBottomBitmap,
            leverTopBitmap = leverTopBitmap,
            ropeBitmap = ropeBitmap,
            ropeDissolveBitmaps = ropeDissolveBitmaps,
            paperBtnBitmaps = paperBtnBitmaps,
            victoryBtnBitmaps = victoryBtnBitmaps,
            failedBtnBitmaps = failedBtnBitmaps,
            dossierBitmap = dossierBitmap,
            failedBgBitmap = failedBgBitmap,
            gadgetBitmaps = gadgetBitmaps,
            gadgetBoltBitmap = gadgetBoltBitmap,
            successBgBitmap = successBgBitmap,
            starsBitmap = starsBitmap,
            starSlices = starSlices,
            wallMarkerBitmaps = wallMarkerBitmaps
        )
    }

    private class GameplayBitmaps(
        val bgmgBitmap: Bitmap?,
        val crateBitmap: Bitmap?,
        val chainedCrateBitmap: Bitmap?,
        val chainedCrate2Bitmap: Bitmap?,
        val fenceBitmap: Bitmap?,
        val fence2Bitmap: Bitmap?,
        val barrelBitmap: Bitmap?,
        val woodCrateBitmap: Bitmap?,
        val poleBitmap: Bitmap?,
        val craneBitmap: Bitmap?,
        val tableBitmap: Bitmap?,
        val cameraBitmap: Bitmap?,
        val laserEmitterBitmap: Bitmap?,
        val conveyorTopBitmap: Bitmap?,
        val conveyorMidBitmap: Bitmap?,
        val conveyorBotBitmap: Bitmap?,
        val hookBitmap: Bitmap?,
        val truckBitmap: Bitmap?,
        val entranceBitmap: Bitmap?,
        val exitFenceBitmap: Bitmap?,
        val l4endBitmap: Bitmap?,
        val exitLvl7Bitmap: Bitmap?,
        val fanBladeBitmap: Bitmap? = null,
        val fanCoverBitmap: Bitmap? = null,
        val robotBodyBitmap: Bitmap? = null,
        val robotWheelBitmap: Bitmap? = null,
        val steamNozzleUpBitmap: Bitmap? = null,
        val steamNozzleDownBitmap: Bitmap? = null,
        val leftBtnBitmap: Bitmap?,
        val rightBtnBitmap: Bitmap?,
        val crouchBtnBitmap: Bitmap?,
        val jumpBtnBitmap: Bitmap?,
        val interactBtnBitmap: Bitmap?,
        val leverBottomBitmap: Bitmap? = null,
        val leverTopBitmap: Bitmap? = null,
        val ropeBitmap: Bitmap? = null,
        val ropeDissolveBitmaps: List<Bitmap> = emptyList(),
        val paperBtnBitmaps: List<Bitmap?>,
        val victoryBtnBitmaps: List<Bitmap?>,
        val failedBtnBitmaps: List<Bitmap?>,
        val dossierBitmap: Bitmap?,
        val failedBgBitmap: Bitmap?,
        val gadgetBitmaps: List<Bitmap?>,
        val gadgetBoltBitmap: Bitmap?,
        val successBgBitmap: Bitmap?,
        val starsBitmap: Bitmap?,
        val starSlices: List<BmpSlice>?,
        val wallMarkerBitmaps: Map<String, Bitmap?> = emptyMap()
    )
}
