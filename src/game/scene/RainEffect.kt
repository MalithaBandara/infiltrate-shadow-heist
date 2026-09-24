package game.scene

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
 * - Dual volumetric depth: a dimmer background layer behind world geometry and a brighter,
 *   faster foreground layer in front of gameplay.
 * - High visual clarity: solid bright streak core with subpixel antialiasing and motion-blur
 *   elongation, rendering distinctly on mobile displays against dark backgrounds.
 * - Viewport-space wrapping: particles wrap around the active camera window plus margins, so zero
 *   particles are simulated or drawn off-screen regardless of level width.
 * - Realistic lightning: multi-pulse strobe profile (strike, dip, intense return stroke, flicker,
 *   smooth exponential fade) coupled with a sky-branching silhouette bolt.
 * - Physics-accurate thunder: acoustic propagation delay (speed of light vs sound) between the
 *   blinding flash and the heavy rolling thunderclap.
 */
object RainAssets {
    const val DROP_TEX_W = 6
    const val DROP_TEX_H = 48

    val dropTexture: Bitmap32 by lazy { createDropTexture() }
    val dropSlice: BmpSlice by lazy { dropTexture.sliceWithSize(0, 0, DROP_TEX_W, DROP_TEX_H) }

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
}

class RainEffect(
    bgLayer: Container,
    fgLayer: Container,
    initialCanvasW: Double,
    initialCanvasH: Double
) {
    companion object {
        const val BACK_DROP_COUNT = 110
        const val FRONT_DROP_COUNT = 140
        const val TOTAL_DROPS = BACK_DROP_COUNT + FRONT_DROP_COUNT

        // Wind angle: drops fall down and slightly to the right (~11.3 degrees from vertical)
        const val WIND_ANGLE_DEG = -11.3
        const val WIND_SLOPE = 0.20 // tan(11.3 deg) ~ 0.20

        const val MARGIN_X = 100.0
        const val MARGIN_Y = 120.0
    }

    private class DropState(
        var x: Double,
        var y: Double,
        val speedX: Double,
        val speedY: Double,
        val parallax: Double,
        val baseAlpha: Double,
        val img: Image
    )

    private val backContainer = bgLayer.container()
    private val fgContainer = fgLayer.container()

    // Full-screen lightning flash overlay (layered on top of world/effects, beneath HUD)
    private val flashRect: SolidRect = fgContainer.solidRect(initialCanvasW, initialCanvasH, Colors["#e0f2fe"]).also {
        it.alpha = 0.0
        it.visible = false
    }

    // Sky lightning bolt segments (layered in background sky)
    private val boltContainer: Container = fgContainer.container().also { it.visible = false }
    private val boltSegments: List<SolidRect> = (0 until 8).map {
        boltContainer.solidRect(3.5, 3.5, Colors["#f0f9ff"])
    }

    private val drops: Array<DropState>

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

        // 1. Background drops (mid-scale, subtle depth behind structures)
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
            val alpha = Random.nextDouble(0.38, 0.55)
            img.alpha = alpha
            val x = Random.nextDouble(-MARGIN_X, initialCanvasW + MARGIN_X)
            val y = Random.nextDouble(-MARGIN_Y, initialCanvasH + MARGIN_Y)
            img.xy(x, y)
            dropList.add(DropState(x, y, speedX, speedY, parallax = 0.20, baseAlpha = alpha, img = img))
        }

        // 2. Foreground drops (crisp, bright, long streaks in front of player and crates)
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
            val alpha = Random.nextDouble(0.70, 0.92)
            img.alpha = alpha
            val x = Random.nextDouble(-MARGIN_X, initialCanvasW + MARGIN_X)
            val y = Random.nextDouble(-MARGIN_Y, initialCanvasH + MARGIN_Y)
            img.xy(x, y)
            dropList.add(DropState(x, y, speedX, speedY, parallax = 0.85, baseAlpha = alpha, img = img))
        }

        drops = dropList.toTypedArray()
    }

    private fun triggerLightning(canvasW: Double) {
        isFlashing = true
        flashElapsed = 0.0
        thunderDelay = Random.nextDouble(0.4, 0.9)
        hasThunderPlayed = false

        // Generate jagged sky bolt in foreground sky
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

    fun update(
        dtSec: Double,
        canvasW: Double,
        canvasH: Double,
        worldViewX: Double,
        sounds: GameSounds,
        sfxVolume: Float,
        coroutineContext: CoroutineContext
    ) {
        val camDeltaX = if (prevWorldX.isNaN()) 0.0 else (worldViewX - prevWorldX)
        prevWorldX = worldViewX

        val wrapW = canvasW + 2 * MARGIN_X
        val wrapH = canvasH + 2 * MARGIN_Y

        // 1. Update rain drop positions
        for (i in drops.indices) {
            val d = drops[i]
            d.x += d.speedX * dtSec + camDeltaX * d.parallax
            d.y += d.speedY * dtSec

            if (d.y > canvasH + MARGIN_Y) {
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

        // 2. Lightning & Thunder progression
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
