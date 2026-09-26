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
 * - Realistic sky lightning (no full-screen white flash): a 32-segment fractal midpoint-displaced
 *   main channel + multi-tier forks with a hot white-cyan plasma core and soft electric-blue
 *   corona, rendered in the distant sky BEHIND the world silhouettes with stepped-leader
 *   propagation, main return stroke, and secondary dart-leader restrike along the main trunk.
 * - Physics-accurate thunder: acoustic propagation delay (speed of light vs sound) between the
 *   sky strike and the rolling thunderclap.
 */
object RainAssets {
    const val DROP_TEX_W = 6
    const val DROP_TEX_H = 48

    const val SPLASH_TEX_W = 16
    const val SPLASH_TEX_H = 10

    const val BOLT_TEX_W = 32
    const val BOLT_TEX_H = 16
    /** Rounded joint overhang (in texture px) at each end of [boltTexture]. */
    const val BOLT_CAP_PX = 2.0
    /** Effective distance between start vertex and end vertex inside [boltTexture]. */
    const val BOLT_SPAN_PX = BOLT_TEX_W - 2.0 * BOLT_CAP_PX
    const val BOLT_ANCHOR_X = BOLT_CAP_PX / BOLT_TEX_W

    const val CLOUD_GLOW_W = 32
    const val CLOUD_GLOW_H = 16

    val dropTexture: Bitmap32 by lazy { createDropTexture() }
    val dropSlice: BmpSlice by lazy { dropTexture.sliceWithSize(0, 0, DROP_TEX_W, DROP_TEX_H) }

    val splashTexture: Bitmap32 by lazy { createSplashTexture() }
    val splashSlice: BmpSlice by lazy { splashTexture.sliceWithSize(0, 0, SPLASH_TEX_W, SPLASH_TEX_H) }

    val boltTexture: Bitmap32 by lazy { createBoltTexture() }
    val boltSlice: BmpSlice by lazy { boltTexture.sliceWithSize(0, 0, BOLT_TEX_W, BOLT_TEX_H) }

    val cloudGlowTexture: Bitmap32 by lazy { createCloudGlowTexture() }
    val cloudGlowSlice: BmpSlice by lazy { cloudGlowTexture.sliceWithSize(0, 0, CLOUD_GLOW_W, CLOUD_GLOW_H) }

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

    /**
     * Single plasma segment slice with an incandescent white-cyan inner core, electric ice-blue
     * intermediate sheath, and soft radial atmospheric corona. Caps at `x < BOLT_CAP_PX` and
     * `x > BOLT_TEX_W - BOLT_CAP_PX` taper smoothly so consecutive segments anchored at their
     * vertices blend continuously without notched gaps or bright joint blobs.
     */
    private fun createBoltTexture(): Bitmap32 {
        val bmp = Bitmap32(BOLT_TEX_W, BOLT_TEX_H)
        val cy = BOLT_TEX_H / 2.0
        val maxR = cy - 0.5
        val x0 = BOLT_CAP_PX
        val x1 = BOLT_TEX_W - BOLT_CAP_PX

        for (y in 0 until BOLT_TEX_H) {
            val dy = (y + 0.5) - cy
            for (x in 0 until BOLT_TEX_W) {
                val px = x + 0.5
                val clampedX = px.coerceIn(x0, x1)
                val dx = px - clampedX
                val dist = hypot(dx, dy)
                val r = (dist / maxR).coerceIn(0.0, 1.0)
                if (r >= 1.0) continue

                // Longitudinal cap taper so two overlapping segment ends sum to ~1.0 without a dot
                val capFactor = if (dx == 0.0) {
                    1.0
                } else {
                    (1.0 - abs(dx) / (BOLT_CAP_PX + 0.5)).coerceIn(0.0, 1.0) * 0.72
                }

                // Cross-sectional plasma profile:
                // r < 0.22: white-hot core
                // 0.22..0.50: bright electric cyan-blue channel
                // 0.50..1.00: soft outer corona glow
                val radialAlpha: Double
                val red: Int
                val green: Int
                val blue: Int
                if (r < 0.22) {
                    val t = r / 0.22
                    radialAlpha = 1.0 - 0.12 * t * t
                    red = (252 - 20 * t).toInt()
                    green = (255 - 6 * t).toInt()
                    blue = 255
                } else if (r < 0.50) {
                    val t = (r - 0.22) / 0.28
                    radialAlpha = 0.88 * (1.0 - t).pow(1.6) + 0.26 * t
                    red = (232 - 82 * t).toInt()
                    green = (249 - 44 * t).toInt()
                    blue = 255
                } else {
                    val t = (r - 0.50) / 0.50
                    radialAlpha = 0.26 * (1.0 - t).pow(2.2)
                    red = (150 - 35 * t).toInt()
                    green = (205 - 30 * t).toInt()
                    blue = 255
                }

                val a = (radialAlpha * capFactor * 255.0).toInt().coerceIn(0, 255)
                if (a > 0) {
                    bmp.setRgba(x, y, premul(red, green, blue, a))
                }
            }
        }
        return bmp
    }

    /**
     * Soft localized cloud-entry glow where the main leader emerges from the upper storm deck.
     */
    private fun createCloudGlowTexture(): Bitmap32 {
        val bmp = Bitmap32(CLOUD_GLOW_W, CLOUD_GLOW_H)
        val cx = CLOUD_GLOW_W / 2.0
        for (y in 0 until CLOUD_GLOW_H) {
            val ny = (y + 0.5) / CLOUD_GLOW_H
            for (x in 0 until CLOUD_GLOW_W) {
                val nx = ((x + 0.5) - cx) / (CLOUD_GLOW_W / 2.0)
                val d = hypot(nx, ny).coerceIn(0.0, 1.0)
                val falloff = (1.0 - d).pow(2.4)
                val a = (falloff * 255.0).toInt().coerceIn(0, 255)
                if (a > 0) {
                    bmp.setRgba(x, y, premul(175, 222, 255, a))
                }
            }
        }
        return bmp
    }
}

/**
 * @param bgLayer  where the sky lightning bolt and the far, dim drop layer are parented.
 * @param fgLayer  where the near drop layer and the impact splashes are parented. **Both layers
 *   are handed the same background container by [GameplayScene] now** (owner request 2026-09-25:
 *   "put the rain effect behind the characters and all the elements"), so the whole curtain and
 *   sky lightning draw over the sky and behind every crate, guard and the player.
 * @param flashLayer retained for call-site compatibility; no full-screen white flash is ever drawn
 *   (owner request 2026-09-26: "dont flash the screen with white when lightning").
 * @param splashSurfaces world-space rects whose TOP edge a drop can land on - platforms and boxes.
 *   Walls are filtered out by height (see [MAX_SURFACE_HEIGHT]). Empty disables splashes entirely.
 */
class RainEffect(
    bgLayer: Container,
    fgLayer: Container,
    initialCanvasW: Double,
    initialCanvasH: Double,
    @Suppress("UNUSED_PARAMETER") flashLayer: Container = fgLayer,
    splashSurfaces: List<Rect> = emptyList()
) {
    companion object {
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

        const val SPLASH_CHANCE = 0.34
        const val SPLASH_ALPHA = 0.34

        /** Screen-space width/height a crown is drawn at before its own expansion curve. */
        const val SPLASH_DRAW_W = 15.0
        const val SPLASH_DRAW_H = 9.4

        const val MAX_SURFACE_HEIGHT = 400.0
        const val SURFACE_BUCKET = 16.0

        /** 16 fractal midpoint-displaced main channel segments + 16 secondary/tertiary fork segments. */
        const val MAIN_BOLT_SEGMENTS = 16
        const val BRANCH_BOLT_SEGMENTS = 16
        const val TOTAL_BOLT_SEGMENTS = MAIN_BOLT_SEGMENTS + BRANCH_BOLT_SEGMENTS
        const val BOLT_DURATION = 0.28
    }

    private class DropState(
        var x: Double,
        var y: Double,
        val speedX: Double,
        val speedY: Double,
        val parallax: Double,
        val baseAlpha: Double,
        val img: Image,
        val length: Double,
        val isForeground: Boolean
    )

    private class SplashState(val img: Image) {
        var worldX: Double = 0.0
        var worldY: Double = 0.0
        var elapsed: Double = 0.0
        var alive: Boolean = false
    }

    private class BoltSegmentState(val img: Image) {
        var isMainTrunk: Boolean = true
        var progressAlongBolt: Double = 0.0
        var baseThickness: Double = 1.0
        var baseAlpha: Double = 1.0
    }

    // Sky lightning bolt sits in bgLayer BEFORE the rain containers so it illuminates the distant
    // sky behind the rain curtain and behind all foreground world silhouettes (crates, guards, player).
    // No full-screen white flash rect is created or rendered.
    private val boltContainer: Container = bgLayer.container().also { it.visible = false }
    private val cloudGlowImg: Image = boltContainer.image(RainAssets.cloudGlowSlice).apply {
        anchor(0.5, 0.0)
        visible = false
    }
    private val boltSegments: Array<BoltSegmentState> = Array(TOTAL_BOLT_SEGMENTS) {
        BoltSegmentState(
            boltContainer.image(RainAssets.boltSlice).apply {
                anchor(RainAssets.BOLT_ANCHOR_X, 0.5)
                visible = false
            }
        )
    }

    // Pre-allocated scratch buffers for zero-allocation fractal midpoint displacement
    private val trunkX = DoubleArray(MAIN_BOLT_SEGMENTS + 1)
    private val trunkY = DoubleArray(MAIN_BOLT_SEGMENTS + 1)

    private val backContainer = bgLayer.container()
    private val fgContainer = fgLayer.container()
    private val splashContainer = fgLayer.container()

    private val drops: Array<DropState>
    private val splashes: Array<SplashState>

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

        // 3. The impact-crown pool.
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
    }

    private fun placeBoltSegment(
        idx: Int,
        x0: Double,
        y0: Double,
        x1: Double,
        y1: Double,
        isMainTrunk: Boolean,
        progress: Double,
        thickness: Double,
        alpha: Double
    ) {
        val seg = boltSegments[idx]
        val dx = x1 - x0
        val dy = y1 - y0
        val len = hypot(dx, dy).coerceAtLeast(1.0)
        seg.isMainTrunk = isMainTrunk
        seg.progressAlongBolt = progress.coerceIn(0.0, 1.0)
        seg.baseThickness = thickness
        seg.baseAlpha = alpha
        // Write scaleX/scaleY directly rather than View.size() (guidelines.md gotcha #12)
        seg.img.xy(x0, y0)
        seg.img.rotation = atan2(dy, dx).radians
        seg.img.scaleX = len / RainAssets.BOLT_SPAN_PX
        seg.img.scaleY = thickness
        seg.img.alpha = 0.0
        seg.img.visible = true
    }

    private fun buildBranch(
        startSegIdx: Int,
        segCount: Int,
        rootX: Double,
        rootY: Double,
        rootProgress: Double,
        lateralDir: Double,
        startThickness: Double,
        startAlpha: Double
    ): Pair<Double, Double> {
        var cx = rootX
        var cy = rootY
        var subForkX = rootX
        var subForkY = rootY
        for (i in 0 until segCount) {
            val frac = i.toDouble() / segCount.toDouble()
            val stepY = Random.nextDouble(14.0, 25.0) * (1.0 - 0.15 * frac)
            val stepX = lateralDir * Random.nextDouble(9.0, 22.0) + Random.nextDouble(-6.0, 6.0)
            val nx = cx + stepX
            val ny = cy + stepY
            val t = startThickness * (1.0 - 0.58 * ((i + 1).toDouble() / segCount.toDouble()))
            val a = startAlpha * (1.0 - 0.55 * frac)
            placeBoltSegment(
                idx = startSegIdx + i,
                x0 = cx,
                y0 = cy,
                x1 = nx,
                y1 = ny,
                isMainTrunk = false,
                progress = rootProgress + 0.08 * (i + 1),
                thickness = t,
                alpha = a
            )
            if (i == 1) {
                subForkX = nx
                subForkY = ny
            }
            cx = nx
            cy = ny
        }
        return Pair(subForkX, subForkY)
    }

    private fun triggerLightning(canvasW: Double, canvasH: Double) {
        isFlashing = true
        flashElapsed = 0.0
        thunderDelay = Random.nextDouble(0.4, 0.9)
        hasThunderPlayed = false

        boltContainer.xy(0.0, 0.0)
        val startX = Random.nextDouble(canvasW * 0.20, canvasW * 0.80)
        val endX = (startX + Random.nextDouble(-canvasW * 0.14, canvasW * 0.14))
            .coerceIn(canvasW * 0.10, canvasW * 0.90)
        val endY = canvasH * Random.nextDouble(0.62, 0.78)

        // Cloud origin glow localized strictly to the upper sky where the leader exits the clouds
        cloudGlowImg.xy(startX, -6.0)
        cloudGlowImg.scaleX = 180.0 / RainAssets.CLOUD_GLOW_W
        cloudGlowImg.scaleY = 60.0 / RainAssets.CLOUD_GLOW_H
        cloudGlowImg.alpha = 0.0
        cloudGlowImg.visible = true

        // 1. Iterative 4-pass midpoint displacement for the 16-segment main lightning channel
        trunkX[0] = startX
        trunkY[0] = -6.0
        trunkX[MAIN_BOLT_SEGMENTS] = endX
        trunkY[MAIN_BOLT_SEGMENTS] = endY

        var step = MAIN_BOLT_SEGMENTS
        while (step >= 2) {
            val half = step / 2
            var i = 0
            while (i < MAIN_BOLT_SEGMENTS) {
                val mid = i + half
                val spanY = trunkY[i + step] - trunkY[i]
                val jitterScale = if (step == MAIN_BOLT_SEGMENTS) 0.36 else 0.44
                trunkX[mid] = 0.5 * (trunkX[i] + trunkX[i + step]) +
                    Random.nextDouble(-1.0, 1.0) * spanY * jitterScale
                trunkY[mid] = 0.5 * (trunkY[i] + trunkY[i + step]) +
                    Random.nextDouble(-0.10, 0.10) * spanY
                i += step
            }
            step = half
        }

        for (s in 0 until MAIN_BOLT_SEGMENTS) {
            val p = s.toDouble() / MAIN_BOLT_SEGMENTS.toDouble()
            // Main plasma channel tapers gently from cloud base toward the horizon
            val thickness = 0.92 - 0.34 * p
            placeBoltSegment(
                idx = s,
                x0 = trunkX[s],
                y0 = trunkY[s],
                x1 = trunkX[s + 1],
                y1 = trunkY[s + 1],
                isMainTrunk = true,
                progress = p,
                thickness = thickness,
                alpha = 0.96 - 0.12 * p
            )
        }

        // 2. Secondary and tertiary branches forking off natural bends in the main channel
        val n1 = Random.nextInt(2, 5)
        val dir1 = if (trunkX[n1] >= trunkX[n1 - 1]) 1.0 else -1.0
        val (subX, subY) = buildBranch(
            startSegIdx = 16,
            segCount = 5,
            rootX = trunkX[n1],
            rootY = trunkY[n1],
            rootProgress = n1.toDouble() / MAIN_BOLT_SEGMENTS,
            lateralDir = dir1,
            startThickness = 0.48,
            startAlpha = 0.78
        )

        val n2 = Random.nextInt(6, 9)
        buildBranch(
            startSegIdx = 21,
            segCount = 5,
            rootX = trunkX[n2],
            rootY = trunkY[n2],
            rootProgress = n2.toDouble() / MAIN_BOLT_SEGMENTS,
            lateralDir = -dir1,
            startThickness = 0.44,
            startAlpha = 0.72
        )

        val n3 = Random.nextInt(10, 13)
        val dir3 = if (Random.nextBoolean()) 1.0 else -1.0
        buildBranch(
            startSegIdx = 26,
            segCount = 4,
            rootX = trunkX[n3],
            rootY = trunkY[n3],
            rootProgress = n3.toDouble() / MAIN_BOLT_SEGMENTS,
            lateralDir = dir3,
            startThickness = 0.36,
            startAlpha = 0.62
        )

        // Tertiary twig off the first branch
        buildBranch(
            startSegIdx = 30,
            segCount = 2,
            rootX = subX,
            rootY = subY,
            rootProgress = (n1 + 2).toDouble() / MAIN_BOLT_SEGMENTS,
            lateralDir = -dir1 * 0.7,
            startThickness = 0.24,
            startAlpha = 0.48
        )

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

        // 2. Impact crowns: expand outward, pop up and settle, fade out.
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

        // 3. Sky Lightning & Thunder progression (no full-screen white flash)
        lightningTimer -= dtSec
        if (lightningTimer <= 0.0 && !isFlashing) {
            triggerLightning(canvasW, canvasH)
        }

        if (isFlashing) {
            flashElapsed += dtSec
            // Keep the active bolt anchored to the distant sky clouds (0.20 parallax)
            boltContainer.x += camDeltaX * 0.20

            if (flashElapsed < BOLT_DURATION) {
                // Realistic multi-stage discharge profile:
                // 0.00..0.04s: Stepped leader shoots downward from cloud to ground
                // 0.04..0.10s: Main return stroke blazes at peak intensity (trunk + forks)
                // 0.10..0.13s: Inter-stroke cooling dip (side branches extinguish rapidly)
                // 0.13..0.18s: Secondary dart-leader restrike surges through the main trunk
                // 0.18..0.28s: Ionization channel decay
                val leaderFront = (flashElapsed / 0.038).coerceIn(0.0, 1.2)
                val trunkIntensity: Double
                val branchIntensity: Double
                val thicknessPulse: Double

                when {
                    flashElapsed < 0.04 -> {
                        trunkIntensity = 0.72
                        branchIntensity = 0.55
                        thicknessPulse = 0.85
                    }
                    flashElapsed < 0.10 -> {
                        trunkIntensity = 1.0
                        branchIntensity = 0.95
                        thicknessPulse = 1.15
                    }
                    flashElapsed < 0.13 -> {
                        trunkIntensity = 0.28
                        branchIntensity = 0.10
                        thicknessPulse = 0.75
                    }
                    flashElapsed < 0.18 -> {
                        trunkIntensity = 0.88
                        branchIntensity = 0.22
                        thicknessPulse = 1.02
                    }
                    else -> {
                        val fade = ((BOLT_DURATION - flashElapsed) / (BOLT_DURATION - 0.18)).coerceIn(0.0, 1.0)
                        trunkIntensity = 0.88 * fade * fade
                        branchIntensity = 0.18 * fade * fade * fade
                        thicknessPulse = 0.70 + 0.30 * fade
                    }
                }

                cloudGlowImg.alpha = (trunkIntensity * 0.16).coerceIn(0.0, 0.16)

                for (i in boltSegments.indices) {
                    val seg = boltSegments[i]
                    val reachFactor = if (flashElapsed < 0.04) {
                        ((leaderFront - seg.progressAlongBolt) * 4.5).coerceIn(0.0, 1.0)
                    } else {
                        1.0
                    }
                    val stageAlpha = if (seg.isMainTrunk) trunkIntensity else branchIntensity
                    val finalAlpha = (seg.baseAlpha * stageAlpha * reachFactor).coerceIn(0.0, 1.0)
                    seg.img.alpha = finalAlpha
                    seg.img.scaleY = seg.baseThickness * thicknessPulse
                    seg.img.visible = finalAlpha > 0.005
                }
            } else {
                boltContainer.visible = false
                cloudGlowImg.visible = false
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

            if (flashElapsed >= BOLT_DURATION && hasThunderPlayed) {
                isFlashing = false
                boltContainer.visible = false
                cloudGlowImg.visible = false
                lightningTimer = Random.nextDouble(8.0, 16.0)
            }
        } else {
            boltContainer.visible = false
        }
    }
}
