package game.scene

import game.model.Rect
import korlibs.image.bitmap.Bitmap32
import korlibs.image.bitmap.BmpSlice
import korlibs.image.bitmap.sliceWithSize
import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.korge.view.*
import korlibs.math.geom.degrees
import korlibs.math.geom.radians
import kotlin.coroutines.CoroutineContext
import kotlin.math.*
import kotlin.random.Random

/**
 * Ultra-low overhead, mobile-optimized procedural rain and atmospheric lightning/thunder system.
 *
 * Designed specifically for atmospheric stealth gameplay (Level 2: Cargo Yard) with zero GC
 * pressure:
 * - Particle representation: fixed pool of recycled [Image] views sharing a single 6x48
 *   procedurally generated premultiplied rain streak texture slice.
 * - Dual volumetric depth: a dimmer, slower background layer and a brighter, faster foreground
 *   layer. **Both now sit BEHIND the world** - see the layering note on [RainEffect] itself.
 * - Viewport-space wrapping: particles wrap around the active camera window plus margins, so zero
 *   particles are simulated or drawn off-screen regardless of level width.
 * - Surface splashes: a foreground drop that reaches a platform/crate top is consumed there and
 *   leaves a short-lived expanding crown, recycled from its own fixed pool.
 * - Realistic lightning: multi-pulse strobe profile (strike, dip, intense return stroke, flicker,
 *   smooth exponential fade) coupled with a sky-branching silhouette bolt.
 * - Physics-accurate thunder: acoustic propagation delay (speed of light vs sound) between the
 *   blinding flash and the heavy rolling thunderclap.
 */
object RainAssets {
    const val DROP_TEX_W = 6
    const val DROP_TEX_H = 48

    const val SPLASH_TEX_W = 16
    const val SPLASH_TEX_H = 10

    val dropTexture: Bitmap32 by lazy { createDropTexture() }
    val dropSlice: BmpSlice by lazy { dropTexture.sliceWithSize(0, 0, DROP_TEX_W, DROP_TEX_H) }

    val splashTexture: Bitmap32 by lazy { createSplashTexture() }
    val splashSlice: BmpSlice by lazy { splashTexture.sliceWithSize(0, 0, SPLASH_TEX_W, SPLASH_TEX_H) }

    /**
     * Premultiplied RGBA color builder for KorGE's Bitmap32 (see guidelines.md gotcha #13).
     */
    private fun premul(r: Int, g: Int, b: Int, a: Int): RGBA {
        val aa = a.coerceIn(0, 255)
        return RGBA(
            (r.coerceIn(0, 255) * aa) / 255,
            (g.coerceIn(0, 255) * aa) / 255,
            (b.coerceIn(0, 255) * aa) / 255,
            aa
        )
    }

    private fun createDropTexture(): Bitmap32 {
        val bmp = Bitmap32(DROP_TEX_W, DROP_TEX_H)
        val cx = DROP_TEX_W / 2.0
        // Crisp, bright rain streak: silver-white with subtle icy cyan
        val r = 235
        val g = 245
        val b = 255

        for (y in 0 until DROP_TEX_H) {
            val py = y.toDouble() / (DROP_TEX_H - 1).toDouble()
            // Long motion-blur streak profile:
            // 0.0..0.15: entry fade from tail
            // 0.15..0.85: bright steady body streak
            // 0.85..1.0: rounded droplet head
            val vAlpha = when {
                py < 0.15 -> (py / 0.15)
                py <= 0.85 -> 0.85 + 0.15 * ((py - 0.15) / 0.70)
                else -> 1.0 - 0.45 * ((py - 0.85) / 0.15).pow(2)
            }

            // Streak width radius: slender 1.0px at tail, 1.8px at head
            val radius = 1.0 + 0.9 * py
            for (x in 0 until DROP_TEX_W) {
                val dx = abs(x + 0.5 - cx)
                if (dx < radius) {
                    val rf = (1.0 - (dx / radius).pow(1.4)).coerceIn(0.0, 1.0)
                    val alpha = (vAlpha * rf * 255.0).toInt().coerceIn(0, 255)
                    if (alpha > 0) {
                        bmp.setRgba(x, y, premul(r, g, b, alpha))
                    }
                }
            }
        }
        return bmp
    }

    /**
     * The impact crown a drop leaves on a surface, seen side-on: two arms of spray rising from
     * the contact point and flaring outwards, brightest at their tips, over a faint ripple
     * smeared along the surface itself.
     *
     * It is drawn ANCHORED AT ITS BOTTOM EDGE (see RainEffect's splash pool), so the last row is
     * the surface the drop landed on and everything above it stands up off that surface.
     *
     * Deliberately a V opening UPWARD rather than an arch closed over the top: an arch - the top
     * half of an ellipse outline, which is the obvious thing to reach for - reads as a dome or a
     * bubble sitting on the floor, not as water leaving it.
     */
    private fun createSplashTexture(): Bitmap32 {
        val bmp = Bitmap32(SPLASH_TEX_W, SPLASH_TEX_H)
        val cx = SPLASH_TEX_W / 2.0
        val baseY = SPLASH_TEX_H.toDouble()
        // Half-width the crown has flared to at its top, and how far up it stands.
        val maxHalfWidth = SPLASH_TEX_W / 2.0 - 1.2
        val riseH = SPLASH_TEX_H - 1.5
        // Half-thickness of each arm, in pixels.
        val armThickness = 1.5

        val r = 235
        val g = 245
        val b = 255

        for (y in 0 until SPLASH_TEX_H) {
            val py = y + 0.5
            // How far up the crown stands: 0 at the contact line, 1 at the droplet tips.
            val h = ((baseY - py) / riseH).coerceIn(0.0, 1.0)
            // Flares fast off the contact point and then widens slowly, so the arms curve out
            // instead of running away as a straight cone.
            val halfWidth = maxHalfWidth * h.pow(0.55)
            for (x in 0 until SPLASH_TEX_W) {
                val ax = abs(x + 0.5 - cx)
                val band = (1.0 - abs(ax - halfWidth) / armThickness).coerceIn(0.0, 1.0)
                // The droplets that make a crown visible are at its tips, not at its feet.
                val crown = band * (0.35 + 0.65 * h)
                // A faint ripple along the contact line, so the crown has a footing.
                val ripple = if (y >= SPLASH_TEX_H - 2) {
                    (1.0 - ax / (SPLASH_TEX_W / 2.0)).coerceIn(0.0, 1.0).pow(1.6) * 0.30
                } else 0.0
                val alpha = ((crown + ripple).coerceIn(0.0, 1.0) * 255.0).toInt()
                if (alpha > 0) {
                    bmp.setRgba(x, y, premul(r, g, b, alpha))
                }
            }
        }
        return bmp
    }
}

/**
 * @param bgLayer  where the far, dim drop layer is parented.
 * @param fgLayer  where the near drop layer and the impact splashes are parented. **Both layers
 *   are handed the same background container by [GameplayScene] now** (owner request 2026-09-25:
 *   "put the rain effect behind the characters and all the elements"), so the whole curtain draws
 *   over the sky and behind every crate, guard and the player. "Foreground" still names the near
 *   half of the volumetric pair - bigger, faster, brighter, and the only half that splashes - not
 *   a position in front of the world.
 * @param flashLayer where the lightning wash and the sky bolt go. This one stays IN FRONT of the
 *   world (below the HUD): a flash behind the level would light the sky and leave the yard dark,
 *   which is the opposite of what a strike looks like. Defaults to [fgLayer] for callers that
 *   do not separate them (the tests do not).
 * @param splashSurfaces world-space rects whose TOP edge a drop can land on - platforms and boxes.
 *   Walls are filtered out by height (see [MAX_SURFACE_HEIGHT]). Empty disables splashes entirely.
 *   This is read ONCE, into a static height map, so moving platforms are deliberately not in it:
 *   rain lands on the floor under a level 2 container rather than on the container. That is the
 *   whole reason the lookup is O(1) per drop instead of a scan, and a missing crown on a crate
 *   that is itself sliding sideways is not something the eye picks out of a downpour.
 */
class RainEffect(
    bgLayer: Container,
    fgLayer: Container,
    initialCanvasW: Double,
    initialCanvasH: Double,
    flashLayer: Container = fgLayer,
    splashSurfaces: List<Rect> = emptyList()
) {
    companion object {
        // Drop counts and alphas were both roughly halved on 2026-09-25 ("it is too opaque and
        // too much rain. make it less rain and more transparent"). The curtain also moved behind
        // the world in the same pass, so what is left reads against the sky rather than over the
        // player - which is why the near layer keeps its size and speed and only gives up count
        // and opacity. Both are pure look knobs; nothing in the level depends on them.
        const val BACK_DROP_COUNT = 40
        const val FRONT_DROP_COUNT = 55
        const val TOTAL_DROPS = BACK_DROP_COUNT + FRONT_DROP_COUNT

        // Wind angle: drops fall down and slightly to the right (~11.3 degrees from vertical)
        const val WIND_ANGLE_DEG = -11.3
        const val WIND_SLOPE = 0.20 // tan(11.3 deg) ~ 0.20

        const val MARGIN_X = 100.0
        const val MARGIN_Y = 120.0

        /** Pooled impact crowns. See SPLASH_CHANCE for why this many is enough. */
        const val SPLASH_COUNT = 22

        /** Seconds an impact crown lives: expand, fade, recycle. */
        const val SPLASH_LIFE = 0.24

        /**
         * Fraction of surface hits that actually spawn a crown. The near layer lands on the order
         * of a hundred drops a second on a level as flat as the Cargo Yard; splashing every one of
         * them is both a wall of white and more live views than the pool holds. A third of them
         * keeps ~8 alive at a time against a pool of [SPLASH_COUNT], with headroom for a burst.
         */
        const val SPLASH_CHANCE = 0.34

        /** Peak alpha of a crown - deliberately under the near layer's own, so it reads as spray. */
        const val SPLASH_ALPHA = 0.34

        /** Screen-space width/height a crown is drawn at before its own expansion curve. */
        const val SPLASH_DRAW_W = 15.0
        const val SPLASH_DRAW_H = 9.4

        /**
         * Anything taller than this in [splashSurfaces] is a wall, not a floor. GameWorld's left
         * and right walls are 1200 tall with their tops at y = -400, so without this the height
         * map would report a landing surface far above the sky at both ends of every level.
         */
        const val MAX_SURFACE_HEIGHT = 400.0

        /** World units per height-map bucket - see [surfaceTops]. */
        const val SURFACE_BUCKET = 16.0
    }

    private class DropState(
        var x: Double,
        var y: Double,
        val speedX: Double,
        val speedY: Double,
        val parallax: Double,
        val baseAlpha: Double,
        val img: Image,
        /** Drawn length of the streak in screen px - where its head is, relative to [y]. */
        val length: Double,
        /** Only the near layer is world-locked enough for its landings to mean anything. */
        val isForeground: Boolean
    )

    private class SplashState(val img: Image) {
        var worldX: Double = 0.0
        var worldY: Double = 0.0
        var elapsed: Double = 0.0
        var alive: Boolean = false
    }

    private val backContainer = bgLayer.container()
    private val fgContainer = fgLayer.container()
    private val splashContainer = fgLayer.container()

    // Full-screen lightning flash overlay (layered on top of world/effects, beneath HUD)
    private val flashRect: SolidRect = flashLayer.solidRect(initialCanvasW, initialCanvasH, Colors["#e0f2fe"]).also {
        it.alpha = 0.0
        it.visible = false
    }

    // Sky lightning bolt segments
    private val boltContainer: Container = flashLayer.container().also { it.visible = false }
    private val boltSegments: List<SolidRect> = (0 until 8).map {
        boltContainer.solidRect(3.5, 3.5, Colors["#f0f9ff"])
    }

    private val drops: Array<DropState>
    private val splashes: Array<SplashState>

    /**
     * Highest landable surface top per [SURFACE_BUCKET]-wide column of the level, or NaN where
     * there is none. Built once: a per-drop O(1) lookup, against an O(surfaces) scan per drop per
     * frame, which at ~55 near drops and a few dozen crates is the difference between free and
     * not. Highest rather than nearest because a drop over a crate lands on the crate.
     */
    private val surfaceTops: DoubleArray
    private val surfaceOriginX: Double

    // Lightning & Thunder timeline
    private var lightningTimer: Double = Random.nextDouble(2.5, 4.5)
    private var isFlashing: Boolean = false
    private var flashElapsed: Double = 0.0
    private var thunderDelay: Double = 0.0
    private var hasThunderPlayed: Boolean = false

    private var prevWorldX: Double = Double.NaN

    init {
        val slice = RainAssets.dropSlice
        val dropList = ArrayList<DropState>(TOTAL_DROPS)

        // 1. Background drops (mid-scale, subtle depth, far side of the yard)
        for (i in 0 until BACK_DROP_COUNT) {
            val scaleYVal = Random.nextDouble(1.2, 1.6)
            val img = backContainer.image(slice).apply {
                anchor(0.5, 0.0)
                rotation = WIND_ANGLE_DEG.degrees
                scaleX = 0.85
                scaleY = scaleYVal
            }
            val speedY = Random.nextDouble(650.0, 780.0)
            val speedX = speedY * WIND_SLOPE
            val alpha = Random.nextDouble(0.16, 0.26)
            img.alpha = alpha
            val x = Random.nextDouble(-MARGIN_X, initialCanvasW + MARGIN_X)
            val y = Random.nextDouble(-MARGIN_Y, initialCanvasH + MARGIN_Y)
            img.xy(x, y)
            dropList.add(
                DropState(
                    x, y, speedX, speedY, parallax = 0.20, baseAlpha = alpha, img = img,
                    length = RainAssets.DROP_TEX_H * scaleYVal, isForeground = false
                )
            )
        }

        // 2. Foreground drops (longer, faster streaks nearer the camera - and the ones that land)
        for (i in 0 until FRONT_DROP_COUNT) {
            val scaleYVal = Random.nextDouble(1.8, 2.5)
            val img = fgContainer.image(slice).apply {
                anchor(0.5, 0.0)
                rotation = WIND_ANGLE_DEG.degrees
                scaleX = 1.1
                scaleY = scaleYVal
            }
            val speedY = Random.nextDouble(920.0, 1150.0)
            val speedX = speedY * WIND_SLOPE
            val alpha = Random.nextDouble(0.30, 0.44)
            img.alpha = alpha
            val x = Random.nextDouble(-MARGIN_X, initialCanvasW + MARGIN_X)
            val y = Random.nextDouble(-MARGIN_Y, initialCanvasH + MARGIN_Y)
            img.xy(x, y)
            dropList.add(
                DropState(
                    x, y, speedX, speedY, parallax = 0.85, baseAlpha = alpha, img = img,
                    length = RainAssets.DROP_TEX_H * scaleYVal, isForeground = true
                )
            )
        }

        drops = dropList.toTypedArray()

        // 3. The impact-crown pool. Never call View.size() on these again - it is MULTIPLICATIVE
        // (guidelines.md gotcha #12), and the expansion curve below writes scaleX/scaleY every
        // frame. The base scale that maps the texture to SPLASH_DRAW_W/H is folded into it.
        val splashSlice = RainAssets.splashSlice
        splashes = Array(SPLASH_COUNT) {
            SplashState(
                splashContainer.image(splashSlice).apply {
                    anchor(0.5, 1.0)
                    visible = false
                }
            )
        }

        // 4. The landing height map.
        val landable = splashSurfaces.filter { it.height in 0.0..MAX_SURFACE_HEIGHT && it.width > 0.0 }
        if (landable.isEmpty()) {
            surfaceTops = DoubleArray(0)
            surfaceOriginX = 0.0
        } else {
            var minX = Double.MAX_VALUE
            var maxX = -Double.MAX_VALUE
            for (s in landable) {
                if (s.left < minX) minX = s.left
                if (s.right > maxX) maxX = s.right
            }
            val bucketCount = (((maxX - minX) / SURFACE_BUCKET).toInt() + 2).coerceIn(1, 8192)
            val tops = DoubleArray(bucketCount) { Double.NaN }
            for (s in landable) {
                val i0 = (((s.left - minX) / SURFACE_BUCKET).toInt()).coerceIn(0, bucketCount - 1)
                val i1 = (((s.right - minX) / SURFACE_BUCKET).toInt()).coerceIn(0, bucketCount - 1)
                for (i in i0..i1) {
                    val cur = tops[i]
                    if (cur.isNaN() || s.top < cur) tops[i] = s.top
                }
            }
            surfaceTops = tops
            surfaceOriginX = minX
        }
    }

    /** Highest landable surface at [worldX], or NaN where the level has no floor under it. */
    private fun surfaceTopAt(worldX: Double): Double {
        if (surfaceTops.isEmpty()) return Double.NaN
        val idx = ((worldX - surfaceOriginX) / SURFACE_BUCKET).toInt()
        if (idx < 0 || idx >= surfaceTops.size) return Double.NaN
        return surfaceTops[idx]
    }

    private fun spawnSplash(worldX: Double, worldY: Double) {
        for (i in splashes.indices) {
            val s = splashes[i]
            if (!s.alive) {
                s.worldX = worldX
                s.worldY = worldY
                s.elapsed = 0.0
                s.alive = true
                s.img.visible = true
                return
            }
        }
        // Pool exhausted - drop the splash rather than allocating. At SPLASH_CHANCE this is rare
        // and a missing crown in a downpour is invisible; a per-frame allocation would not be.
    }

    private fun triggerLightning(canvasW: Double) {
        isFlashing = true
        flashElapsed = 0.0
        thunderDelay = Random.nextDouble(0.4, 0.9)
        hasThunderPlayed = false

        // Generate jagged sky bolt
        val startX = Random.nextDouble(canvasW * 0.25, canvasW * 0.75)
        var curX = startX
        var curY = 0.0
        val mainSteps = 5

        for (s in 0 until mainSteps) {
            val stepLen = Random.nextDouble(35.0, 52.0)
            val nextX = curX + Random.nextDouble(-28.0, 32.0)
            val nextY = curY + stepLen
            val dx = nextX - curX
            val dy = nextY - curY
            val len = hypot(dx, dy)
            val angle = atan2(dy, dx)
            boltSegments[s].xy(curX, curY).size(len, 3.5).rotation = angle.radians
            boltSegments[s].visible = true
            curX = nextX
            curY = nextY
        }

        // 2-segment branch forked from step 2
        val forkStartX = boltSegments[2].x
        val forkStartY = boltSegments[2].y
        var fx = forkStartX
        var fy = forkStartY
        for (b in 0 until 2) {
            val nextX = fx + Random.nextDouble(20.0, 40.0)
            val nextY = fy + Random.nextDouble(22.0, 38.0)
            val dx = nextX - fx
            val dy = nextY - fy
            val len = hypot(dx, dy)
            val angle = atan2(dy, dx)
            val segIdx = mainSteps + b
            boltSegments[segIdx].xy(fx, fy).size(len, 2.2).rotation = angle.radians
            boltSegments[segIdx].visible = true
            fx = nextX
            fy = nextY
        }
        boltSegments[7].visible = false

        boltContainer.visible = true
    }

    /**
     * [worldViewY] and [worldZoom] are the other two thirds of worldView's transform. The drops
     * themselves live in screen space and get by on parallax drift, but a splash has to sit on a
     * world surface while the camera pans, so it needs the whole mapping. Both default to an
     * identity mapping for callers with no world behind them (the tests).
     */
    fun update(
        dtSec: Double,
        canvasW: Double,
        canvasH: Double,
        worldViewX: Double,
        sounds: GameSounds,
        sfxVolume: Float,
        coroutineContext: CoroutineContext,
        worldViewY: Double = 0.0,
        worldZoom: Double = 1.0
    ) {
        val camDeltaX = if (prevWorldX.isNaN()) 0.0 else (worldViewX - prevWorldX)
        prevWorldX = worldViewX

        val wrapW = canvasW + 2 * MARGIN_X
        val wrapH = canvasH + 2 * MARGIN_Y
        val zoom = if (worldZoom > 0.0) worldZoom else 1.0
        val splashesEnabled = surfaceTops.isNotEmpty()

        // 1. Update rain drop positions
        for (i in drops.indices) {
            val d = drops[i]
            d.x += d.speedX * dtSec + camDeltaX * d.parallax
            d.y += d.speedY * dtSec

            // A near drop that reaches a floor or a crate top is consumed there and leaves a
            // crown, instead of sailing on down behind the level geometry. The head of the streak
            // is what lands, not its anchor, which is a whole drop-length higher up.
            var landed = false
            if (splashesEnabled && d.isForeground) {
                val headY = d.y + d.length
                if (headY >= 0.0 && headY <= canvasH) {
                    val headWorldX = (d.x + d.length * WIND_SLOPE - worldViewX) / zoom
                    val top = surfaceTopAt(headWorldX)
                    if (!top.isNaN()) {
                        val headWorldY = (headY - worldViewY) / zoom
                        val prevWorldY = headWorldY - (d.speedY * dtSec) / zoom
                        if (headWorldY >= top && prevWorldY < top) {
                            if (Random.nextDouble() < SPLASH_CHANCE) spawnSplash(headWorldX, top)
                            landed = true
                        }
                    }
                }
            }

            if (landed) {
                // Re-seed just above the top edge rather than subtracting a wrap height: a drop
                // that lands high up (a crate top) is most of a screen short of the bottom, so
                // wrapping it would park it far off-screen and thin the curtain out for a moment.
                d.y = -MARGIN_Y - Random.nextDouble(0.0, 30.0)
                d.x = Random.nextDouble(-MARGIN_X, canvasW + MARGIN_X)
            } else if (d.y > canvasH + MARGIN_Y) {
                d.y -= wrapH
                d.x = Random.nextDouble(-MARGIN_X, canvasW + MARGIN_X)
            } else if (d.y < -MARGIN_Y) {
                d.y += wrapH
            }

            if (d.x > canvasW + MARGIN_X) {
                d.x -= wrapW
            } else if (d.x < -MARGIN_X) {
                d.x += wrapW
            }

            d.img.xy(d.x, d.y)
        }

        // 2. Impact crowns: expand outward, pop up and settle, fade out. Positioned from the world
        // transform every frame so they stay stuck to the surface while the camera pans.
        if (splashesEnabled) {
            val baseScaleX = SPLASH_DRAW_W / RainAssets.SPLASH_TEX_W
            val baseScaleY = SPLASH_DRAW_H / RainAssets.SPLASH_TEX_H
            for (i in splashes.indices) {
                val s = splashes[i]
                if (!s.alive) continue
                s.elapsed += dtSec
                val p = s.elapsed / SPLASH_LIFE
                if (p >= 1.0) {
                    s.alive = false
                    s.img.visible = false
                    continue
                }
                s.img.scaleX = baseScaleX * (0.40 + 1.10 * p)
                s.img.scaleY = baseScaleY * (0.55 + 0.55 * sin(p * PI))
                s.img.alpha = SPLASH_ALPHA * (1.0 - p).pow(1.5)
                s.img.xy(s.worldX * zoom + worldViewX, s.worldY * zoom + worldViewY)
            }
        }

        // 3. Lightning & Thunder progression
        lightningTimer -= dtSec
        if (lightningTimer <= 0.0 && !isFlashing) {
            triggerLightning(canvasW)
        }

        if (isFlashing) {
            flashElapsed += dtSec

            // Lightning flash multi-pulse profile:
            // 0.00-0.05: initial strike (0.70)
            // 0.05-0.08: dip (0.25)
            // 0.08-0.14: main bright return stroke (0.92)
            // 0.14-0.19: secondary flicker (0.40)
            // 0.19-0.45: exponential fade to 0.0
            val flashAlpha = when {
                flashElapsed < 0.05 -> 0.70
                flashElapsed < 0.08 -> 0.25
                flashElapsed < 0.14 -> 0.92
                flashElapsed < 0.19 -> 0.40
                flashElapsed < 0.45 -> {
                    val p = (flashElapsed - 0.19) / 0.26
                    (0.40 * (1.0 - p) * (1.0 - p)).coerceAtLeast(0.0)
                }
                else -> 0.0
            }

            flashRect.size(canvasW, canvasH)
            if (flashAlpha > 0.001) {
                flashRect.visible = true
                flashRect.alpha = flashAlpha
            } else {
                flashRect.visible = false
            }

            // Hide bolt after return stroke ends
            if (flashElapsed >= 0.14) {
                boltContainer.visible = false
            }

            // Fire thunder after speed-of-sound delay
            if (flashElapsed >= thunderDelay && !hasThunderPlayed) {
                hasThunderPlayed = true
                sounds.thunder?.playSfx(
                    coroutineContext,
                    gain = GameAudio.THUNDER_GAIN,
                    sfxVolume = sfxVolume,
                    clipFile = GameAudio.SfxFile.THUNDER
                )
            }

            if (flashElapsed >= 0.45 && hasThunderPlayed) {
                isFlashing = false
                flashRect.alpha = 0.0
                flashRect.visible = false
                lightningTimer = Random.nextDouble(8.0, 16.0)
            }
        } else {
            flashRect.alpha = 0.0
            flashRect.visible = false
        }
    }
}
