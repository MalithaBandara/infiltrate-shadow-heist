package game.scene

import game.model.*
import korlibs.image.bitmap.Bitmap
import korlibs.image.bitmap.Bitmap32
import korlibs.image.bitmap.BmpSlice
import korlibs.image.bitmap.slice
import korlibs.image.bitmap.sliceWithSize
import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.korge.view.*
import korlibs.math.geom.Point
import korlibs.math.geom.degrees
import korlibs.math.geom.radians
import kotlin.math.*

/**
 * High-performance, mobile-optimized procedural textures and visual presentation components
 * for Level 7's ventilation duct infiltration gauntlet.
 *
 * Extracted outside GameplayScene.sceneMain to keep sceneMain well within JVM 64KB method bytecode limits.
 */
object VentFxAssets {

    /**
     * Four distinct vapour puffs on one 128x128 page. Steam jets and the fan's airborne haze both
     * draw from it, so a screen full of vapour is still a single texture bind.
     *
     * Four variants rather than one, because a jet is 18 stamps of the same sprite: a single puff
     * repeated reads as a column of identical discs no matter how the motion is tuned. Rotating or
     * mirroring one puff at runtime would do the same job, but a negative `scaleX` on an `Image` is
     * a known corrupter on this project's Android GL backend (see `.junie/guidelines.md` bug #8) and
     * a rotation needs a centre anchor, so the variation is baked into the page instead - it costs
     * 64 KB once and nothing per frame.
     */
    val vaporPuffSheet: Bitmap32 by lazy { createVaporPuffSheet() }

    /** The four 64x64 puffs of [vaporPuffSheet], ready to hand to `image(...)`. */
    val vaporPuffSlices: List<BmpSlice> by lazy {
        val sheet = vaporPuffSheet
        listOf(
            sheet.sliceWithSize(0, 0, PUFF_SIZE, PUFF_SIZE),
            sheet.sliceWithSize(PUFF_SIZE, 0, PUFF_SIZE, PUFF_SIZE),
            sheet.sliceWithSize(0, PUFF_SIZE, PUFF_SIZE, PUFF_SIZE),
            sheet.sliceWithSize(PUFF_SIZE, PUFF_SIZE, PUFF_SIZE, PUFF_SIZE)
        )
    }

    /**
     * Two rows of one wisp: row 0 has its leading tip at x = 0 (for air blowing LEFT), row 1 is the
     * same wisp mirrored, for a fan blowing right. Baked rather than flipped with a negative
     * `scaleX` for the reason given on [vaporPuffSheet].
     */
    val windStreakSheet: Bitmap32 by lazy { createWindStreakSheet() }

    /** Leading tip at the sprite's own left edge - use for `windDirection < 0`. */
    val windStreakLeft: BmpSlice by lazy { windStreakSheet.sliceWithSize(0, 0, STREAK_W, STREAK_H) }

    /** Leading tip at the sprite's own right edge - use for `windDirection > 0`. */
    val windStreakRight: BmpSlice by lazy { windStreakSheet.sliceWithSize(0, STREAK_H, STREAK_W, STREAK_H) }

    val botEyeGlowBitmap: Bitmap32 by lazy { createBotEyeGlowBitmap() }

    const val PUFF_SIZE = 64
    const val STREAK_W = 96
    const val STREAK_H = 20

    /**
     * `Bitmap32(w, h)` is flagged PREMULTIPLIED, so whatever RGB is written is what the renderer
     * blends - it never divides the colour back out by alpha. Every generator in this file used to
     * write straight-alpha colour into one, which makes a particle's feathered edge as bright as its
     * core: that is why the old wind streaks read as hard-edged glowing scratches instead of air,
     * and why the old steam puffs had a rim rather than a falloff. Write colour through this.
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

    /** Integer hash for the lattice below. Int overflow wraps on every Kotlin target. */
    private fun hash2(ix: Int, iy: Int, seed: Int): Double {
        var h = ix * 374761393 + iy * 668265263 + seed * 1274126177
        h = (h xor (h shr 13)) * 1103515245
        h = h xor (h shr 16)
        return ((h ushr 8) and 0xFFFF).toDouble() / 65535.0
    }

    /** Smoothstep-interpolated value noise. Build-time only - never called from a frame. */
    private fun valueNoise(x: Double, y: Double, seed: Int): Double {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        val sx = fx * fx * (3.0 - 2.0 * fx)
        val sy = fy * fy * (3.0 - 2.0 * fy)
        val n00 = hash2(x0, y0, seed)
        val n10 = hash2(x0 + 1, y0, seed)
        val n01 = hash2(x0, y0 + 1, seed)
        val n11 = hash2(x0 + 1, y0 + 1, seed)
        val a = n00 + (n10 - n00) * sx
        val b = n01 + (n11 - n01) * sx
        return a + (b - a) * sy
    }

    /**
     * One condensing-vapour puff: a radial falloff whose silhouette is eaten away by two octaves of
     * value noise, with the same noise thinning the interior. The ragged edge is the whole point -
     * a clean gaussian disc is what made the old plume read as a stack of identical blobs.
     */
    private fun writePuff(bmp: Bitmap32, ox: Int, oy: Int, seed: Int) {
        val c = (PUFF_SIZE - 1) / 2.0
        for (y in 0 until PUFF_SIZE) {
            for (x in 0 until PUFF_SIZE) {
                val dx = (x - c) / c
                val dy = (y - c) / c
                val r = hypot(dx, dy)
                if (r >= 1.0) {
                    bmp.setRgba(ox + x, oy + y, RGBA(0, 0, 0, 0))
                    continue
                }
                val n = 0.62 * valueNoise(x / 13.0, y / 13.0, seed) +
                    0.38 * valueNoise(x / 5.5, y / 5.5, seed * 7 + 13)
                // Noise pushes the effective outer radius in and out around the circle
                val edge = 0.56 + 0.44 * n
                val t = r / edge
                if (t >= 1.0) {
                    bmp.setRgba(ox + x, oy + y, RGBA(0, 0, 0, 0))
                    continue
                }
                val radial = 1.0 - t * t
                val density = (radial * radial * (0.50 + 0.50 * n)).coerceIn(0.0, 1.0)
                // Dense vapour is near white; the thin fringe cools towards the duct's own grey-blue
                bmp.setRgba(
                    ox + x, oy + y,
                    premul(
                        (188 + 67 * density).toInt(),
                        (202 + 53 * density).toInt(),
                        (214 + 41 * density).toInt(),
                        (236.0 * density).toInt()
                    )
                )
            }
        }
    }

    private fun createVaporPuffSheet(): Bitmap32 {
        val bmp = Bitmap32(PUFF_SIZE * 2, PUFF_SIZE * 2)
        writePuff(bmp, 0, 0, 17)
        writePuff(bmp, PUFF_SIZE, 0, 53)
        writePuff(bmp, 0, PUFF_SIZE, 101)
        writePuff(bmp, PUFF_SIZE, PUFF_SIZE, 211)
        return bmp
    }

    /**
     * A dust-laden air wisp: a soft tapered core that thins and breaks up along its own length.
     * Drawn only a few units tall, so the vertical falloff lives in 20 rows to survive the squash.
     */
    private fun createWindStreakSheet(): Bitmap32 {
        val bmp = Bitmap32(STREAK_W, STREAK_H * 2)
        val cy = (STREAK_H - 1) / 2.0
        for (x in 0 until STREAK_W) {
            val u = x.toDouble() / (STREAK_W - 1.0)
            // u = 0 is the leading tip, u = 1 the dissipating tail that trails back towards the fan
            val along = when {
                u < 0.12 -> (u / 0.12).pow(0.75)
                u < 0.30 -> 1.0
                else -> ((1.0 - u) / 0.70).pow(1.35)
            }.coerceIn(0.0, 1.0)
            val n = valueNoise(u * 7.0, 0.5, 31)
            val thickness = ((0.45 + 0.55 * along) * (0.75 + 0.50 * n)).coerceAtLeast(0.18)
            for (y in 0 until STREAK_H) {
                val dy = (abs(y - cy) / cy) / thickness
                val across = exp(-3.4 * dy * dy)
                val k = (along * across).coerceIn(0.0, 1.0)
                val a = (205.0 * k * (0.70 + 0.45 * n)).toInt()
                val col = premul((176 + 62 * k).toInt(), (190 + 58 * k).toInt(), (202 + 50 * k).toInt(), a)
                bmp.setRgba(x, y, col)
                bmp.setRgba(STREAK_W - 1 - x, STREAK_H + y, col)
            }
        }
        return bmp
    }

    private fun createBotEyeGlowBitmap(): Bitmap32 {
        val size = 16
        val bmp = Bitmap32(size, size)
        val center = 7.5
        val maxDist = 7.5

        for (y in 0 until size) {
            for (x in 0 until size) {
                val dist = hypot(x - center, y - center)
                if (dist <= maxDist) {
                    val t = dist / maxDist
                    val alpha = ((1.0 - t).pow(1.5) * 255).toInt().coerceIn(0, 255)
                    // Security bot optical sensor cyan glow
                    bmp.setRgba(x, y, premul(56, 189, 248, alpha))
                } else {
                    bmp.setRgba(x, y, RGBA(0, 0, 0, 0))
                }
            }
        }
        return bmp
    }
}

/**
 * The turbine's housing, its spinning rotor and the moving air it throws down the duct.
 *
 * What makes the air read as air (each point replaced something that read as a machine):
 *
 * - **The jet decays.** Speed falls off with distance from the blades, so wisps sprint out of the
 *   rotor and crowd together as they run out of push. Every wisp used to cross the whole zone at
 *   one constant speed and snap back to the fan, which is a conveyor belt, not a jet.
 * - **Nothing keeps its lane.** A wisp is re-seeded when it reaches the end of the zone - new lane,
 *   speed, length, sway and brightness - so the pattern never repeats. The old streaks each had one
 *   permanent y for the whole level, and eighteen fixed lanes cycling is the single most obvious
 *   tell that this is a sprite loop.
 * - **No strobe.** The old wisps flickered at `sin(t * 15)` and snaked through `sin(x * 0.022 +
 *   t * 11)` - roughly 2 Hz of brightness flicker and a spatial wave they rode up and down. Air
 *   does neither. They now sway slowly (0.7..1.7 rad/s, 1-3 units) and the whole jet breathes on
 *   one shared ~0.18 Hz gust.
 * - **Length tracks speed**, the way motion blur does: long out of the blades, short once slowed.
 * - **Normal blending, not ADD.** This duct's wall is brightly lit (see `bglvl7.png`); additive
 *   white barely moved it, which is why the old wind was faint scratches. Air that occludes the
 *   wall behind it is both more readable and more correct.
 */
class VentFanVisual(
    private val fan: VentFan,
    private val container: Container,
    private val bladesContainer: Container,
    private val streaks: List<Image>,
    private val hazePuffs: List<Image>
) {
    private val dir: Double = if (fan.windDirection < 0.0) -1.0 else 1.0

    /** The blade face the air leaves from. */
    private val originX: Double = if (dir < 0.0) fan.x else fan.x + fan.width

    private val windLen: Double = (fan.windMaxX - fan.windMinX).coerceAtLeast(10.0)
    private val centerY: Double = fan.y + fan.height / 2.0

    // Per-wisp state. Allocated once; `seedStreak`/`seedHaze` rewrite entries in place, so the
    // updater below never allocates.
    private val sDist = DoubleArray(streaks.size)
    private val sSpeed = DoubleArray(streaks.size)
    private val sLane = DoubleArray(streaks.size)
    private val sLen = DoubleArray(streaks.size)
    private val sThick = DoubleArray(streaks.size)
    private val sSwayAmp = DoubleArray(streaks.size)
    private val sSwayFreq = DoubleArray(streaks.size)
    private val sPhase = DoubleArray(streaks.size)
    private val sAlpha = DoubleArray(streaks.size)

    private val hDist = DoubleArray(hazePuffs.size)
    private val hSpeed = DoubleArray(hazePuffs.size)
    private val hLane = DoubleArray(hazePuffs.size)
    private val hSize = DoubleArray(hazePuffs.size)
    private val hSwayFreq = DoubleArray(hazePuffs.size)
    private val hPhase = DoubleArray(hazePuffs.size)
    private val hAlpha = DoubleArray(hazePuffs.size)

    private var rng: Int = (fan.x * 31.0).toInt() xor 0x5bf03635

    private fun rnd(): Double {
        rng = rng * 1664525 + 1013904223
        return (((rng ushr 8) and 0xFFFFFF).toDouble()) / 16777216.0
    }

    private fun rnd(lo: Double, hi: Double): Double = lo + (hi - lo) * rnd()

    init {
        for (i in streaks.indices) seedStreak(i, initial = true)
        for (i in hazePuffs.indices) seedHaze(i, initial = true)
    }

    private fun seedStreak(i: Int, initial: Boolean) {
        // `initial` spreads the first generation over the whole zone so it is already full of air on
        // frame one; a re-seed starts at the blades.
        sDist[i] = if (initial) rnd() * windLen else rnd(0.0, 12.0)
        sSpeed[i] = fan.windPushSpeed * rnd(1.45, 2.40)
        // Triangular distribution: a duct fan throws most of its air down the middle, and clustering
        // the lanes there also keeps the wisps off the ceiling and floor plates.
        sLane[i] = centerY + (rnd() + rnd() - 1.0) * (fan.height * 0.42)
        sLen[i] = rnd(40.0, 92.0)
        sThick[i] = rnd(3.2, 6.8)
        sSwayAmp[i] = rnd(1.0, 3.2)
        sSwayFreq[i] = rnd(0.7, 1.7)
        sPhase[i] = rnd() * 6.2831853
        sAlpha[i] = rnd(0.45, 1.0)
    }

    private fun seedHaze(i: Int, initial: Boolean) {
        hDist[i] = if (initial) rnd() * windLen else rnd(0.0, 20.0)
        hSpeed[i] = fan.windPushSpeed * rnd(0.75, 1.25)
        hLane[i] = centerY + (rnd() + rnd() - 1.0) * (fan.height * 0.44)
        hSize[i] = rnd(22.0, 42.0)
        hSwayFreq[i] = rnd(0.4, 0.9)
        hPhase[i] = rnd() * 6.2831853
        hAlpha[i] = rnd(0.5, 1.0)
    }

    fun update(dt: Double, totalElapsedSeconds: Double, cullLeft: Double, cullRight: Double) {
        val visualMinX = minOf(fan.x - fan.height / 2.0, fan.windMinX)
        val visualMaxX = maxOf(fan.x + fan.width + fan.height / 2.0, fan.windMaxX)
        val isVisible = visualMaxX >= cullLeft && visualMinX <= cullRight
        container.visible = isVisible
        if (!isVisible) return

        bladesContainer.rotation = fan.bladeRotationAngle.radians

        // One slow gust for the whole jet. Deliberately far below the old per-streak 15 rad/s
        // shimmer: a turbine surges, it does not flicker.
        val gust = 0.86 + 0.14 * sin(totalElapsedSeconds * 1.15)

        for (i in streaks.indices) {
            // Momentum bleeds off with distance - fast at the blades, drifting at the far edge.
            val decay = 1.0 - (sDist[i] / windLen).coerceIn(0.0, 1.0)
            val speedFrac = 0.30 + 0.70 * decay
            sDist[i] += sSpeed[i] * speedFrac * dt
            if (sDist[i] >= windLen) seedStreak(i, initial = false)

            val d = sDist[i]
            val p = (d / windLen).coerceIn(0.0, 1.0)
            val worldX = originX + dir * d
            // The jet opens out into a shallow cone as it loses momentum, and the sway is scaled by
            // distance: air leaves the blades straight and only picks up turbulence downstream.
            val y = sLane[i] + (sLane[i] - centerY) * 0.55 * p +
                sSwayAmp[i] * p * sin(totalElapsedSeconds * sSwayFreq[i] + sPhase[i])
            // Length tracks speed, the way motion blur does: long out of the rotor, short once slowed.
            val len = sLen[i] * (0.60 + 0.50 * (1.0 - p))
            val fade = when {
                p < 0.06 -> p / 0.06
                p > 0.72 -> (1.0 - p) / 0.28
                else -> 1.0
            }.coerceIn(0.0, 1.0)

            val img = streaks[i]
            // Scale written to the transform, NOT `size()` - see the warning in `SteamPipeVisual`.
            img.scaleX = len / VentFxAssets.STREAK_W
            img.scaleY = sThick[i] / VentFxAssets.STREAK_H
            // Both sheet rows put the leading tip at the sprite's own leading edge, so the sprite
            // always starts at the travel point and trails back towards the fan.
            img.xy(if (dir < 0.0) worldX else worldX - len, y - sThick[i] / 2.0)
            // Air is thickest where it leaves the blades and thins as the jet spreads out.
            img.alpha = (0.80 * sAlpha[i] * fade * gust * (1.0 - 0.45 * p)).coerceIn(0.0, 1.0)
        }

        for (i in hazePuffs.indices) {
            val decay = 1.0 - (hDist[i] / windLen).coerceIn(0.0, 1.0)
            hDist[i] += hSpeed[i] * (0.35 + 0.65 * decay) * dt
            if (hDist[i] >= windLen) seedHaze(i, initial = false)

            val d = hDist[i]
            val p = (d / windLen).coerceIn(0.0, 1.0)
            val worldX = originX + dir * d
            val y = hLane[i] + (hLane[i] - centerY) * 0.70 * p +
                4.5 * sin(totalElapsedSeconds * hSwayFreq[i] + hPhase[i])
            // Haze diffuses as it travels, so it grows while it dims
            val size = hSize[i] * (0.70 + 0.85 * p)
            val fade = if (p < 0.10) p / 0.10 else ((1.0 - p) / 0.90).pow(0.7)

            val img = hazePuffs[i]
            img.scaleX = size / VentFxAssets.PUFF_SIZE
            img.scaleY = size / VentFxAssets.PUFF_SIZE
            img.xy(worldX - size / 2.0, y - size / 2.0)
            img.alpha = (0.30 * hAlpha[i] * fade * gust * (1.0 - 0.40 * p)).coerceIn(0.0, 1.0)
        }
    }

    companion object {
        /** Wisps per fan. Only the fan the player is at is ever updated or drawn (see culling). */
        private const val STREAK_COUNT = 16
        private const val HAZE_COUNT = 8

        fun createAll(
            worldView: Container,
            fans: List<VentFan>,
            bladeBitmap: Bitmap? = null,
            coverBitmap: Bitmap? = null
        ): List<VentFanVisual> {
            val (bladeSlice, coverSlice) = getFanSlices(bladeBitmap, coverBitmap)
            return fans.map { fan ->
                val cont = worldView.container()

                val centerX = fan.x + fan.width / 2.0
                val centerY = fan.y + fan.height / 2.0
                val fanSize = fan.height

                // 1. Dark circular duct cavity behind blades
                val cavity = cont.graphics().xy(centerX, centerY)
                cavity.updateShape {
                    // Outer flange shadow / housing ring
                    fill(Colors["#080c14"]) {
                        circle(Point(0.0, 0.0), fanSize * 0.49)
                    }
                    // Deep inner duct tunnel
                    fill(Colors["#0f172a"]) {
                        circle(Point(0.0, 0.0), fanSize * 0.44)
                    }
                }

                // 2. Rotating Turbine Rotor (fan blades behind cover)
                val bladesCont = cont.container().xy(centerX, centerY)
                if (bladeSlice != null) {
                    bladesCont.image(bladeSlice).also { img ->
                        img.scaleX = fanSize / bladeSlice.width.toDouble()
                        img.scaleY = fanSize / bladeSlice.height.toDouble()
                        img.xy(-fanSize / 2.0, -fanSize / 2.0)
                    }
                } else {
                    val bladeLength = (fan.height * 0.44).coerceAtLeast(10.0)
                    val bladeW = 6.0
                    for (b in 0 until 4) {
                        val angleDeg = b * 90.0
                        val blade = bladesCont.container()
                        blade.rotation = angleDeg.degrees
                        blade.solidRect(bladeLength, bladeW, Colors["#64748b"]).xy(2.0, -bladeW / 2.0)
                        blade.solidRect(bladeLength * 0.7, bladeW * 0.5, Colors["#94a3b8"]).xy(3.0, -bladeW / 2.0)
                    }
                    bladesCont.solidRect(10.0, 10.0, Colors["#334155"]).xy(-5.0, -5.0)
                    bladesCont.solidRect(6.0, 6.0, Colors["#cbd5e1"]).xy(-3.0, -3.0)
                }

                // 3. Heavy Wire Protective Mesh Grill / Cover (in front of blades)
                val coverCont = cont.container().xy(centerX, centerY)
                if (coverSlice != null) {
                    coverCont.image(coverSlice).also { img ->
                        img.scaleX = fanSize / coverSlice.width.toDouble()
                        img.scaleY = fanSize / coverSlice.height.toDouble()
                        img.xy(-fanSize / 2.0, -fanSize / 2.0)
                    }
                } else {
                    coverCont.solidRect(1.5, fan.height, Colors["#475569"]).xy(fan.width * 0.33 - fan.width / 2.0, -fan.height / 2.0)
                    coverCont.solidRect(1.5, fan.height, Colors["#475569"]).xy(fan.width * 0.66 - fan.width / 2.0, -fan.height / 2.0)
                    coverCont.solidRect(fan.width, 1.5, Colors["#334155"]).xy(-fan.width / 2.0, 0.0)
                }

                // 4. The moving air itself. Plain alpha blending - see the class doc.
                val windCont = cont.container()
                val streakSlice =
                    if (fan.windDirection < 0.0) VentFxAssets.windStreakLeft else VentFxAssets.windStreakRight
                val streaks = (0 until STREAK_COUNT).map { windCont.image(streakSlice) }
                val haze = (0 until HAZE_COUNT).map { i ->
                    windCont.image(VentFxAssets.vaporPuffSlices[i % VentFxAssets.vaporPuffSlices.size])
                }

                VentFanVisual(
                    fan = fan,
                    container = cont,
                    bladesContainer = bladesCont,
                    streaks = streaks,
                    hazePuffs = haze
                )
            }
        }

        private fun getFanSlices(bladeBmp: Bitmap?, coverBmp: Bitmap?): Pair<BmpSlice?, BmpSlice?> {
            if (bladeBmp != null && coverBmp != null && bladeBmp !== coverBmp) {
                return Pair(bladeBmp.slice(), coverBmp.slice())
            }
            val combined = bladeBmp ?: coverBmp
            if (combined != null) {
                val halfW = combined.width / 2
                return Pair(
                    combined.sliceWithSize(0, 0, halfW, combined.height),
                    combined.sliceWithSize(halfW, 0, halfW, combined.height)
                )
            }
            return Pair(null, null)
        }
    }
}

/**
 * Visual presentation for patrolling robotic camera bots with directional surveillance beam.
 */
class CameraBotVisual(
    val bot: CameraBot,
    val container: Container,
    val chassisContainer: Container,
    val eyeGlow: Image,
    val eyePupil: SolidRect,
    val eyePip: SolidRect,
    val lightCone: LightConeView,
    val sparks: SolidRect
) {
    fun update(dt: Double, totalElapsedSeconds: Double, cullLeft: Double, cullRight: Double, occluders: List<Rect>) {
        val onScreen = (bot.x + bot.visionRange >= cullLeft) && (bot.x - bot.visionRange <= cullRight)
        container.visible = onScreen
        lightCone.visible = onScreen

        if (!onScreen) return

        container.xy(bot.x, bot.y)

        // Bot chassis orientation: flip when facing left
        if (bot.facing < 0.0) {
            chassisContainer.scaleX = -1.0
            chassisContainer.x = bot.width
        } else {
            chassisContainer.scaleX = 1.0
            chassisContainer.x = 0.0
        }

        if (bot.isDeactivated) {
            // Permanent deactivated state
            lightCone.clear()
            eyeGlow.alpha = 0.0
            eyePupil.color = Colors["#334155"]
            eyePip.color = Colors["#1e293b"]
            sparks.visible = (totalElapsedSeconds % 1.5 < 0.08)
            sparks.color = Colors["#f59e0b"]
        } else {
            sparks.visible = false
            val eyePulse = 0.80 + 0.20 * sin(totalElapsedSeconds * 12.0)
            eyeGlow.alpha = 0.85 * eyePulse
            eyePupil.color = Colors["#38bdf8"]
            eyePip.color = Colors.WHITE

            // Surveillance light cone
            val origin = bot.eyePosition
            val facing = bot.facingAngle
            val poly = VisionSystem.computeVisionPolygon(
                origin = origin,
                facingAngle = facing,
                range = bot.visionRange,
                fov = bot.visionFov,
                occluders = occluders
            )
            lightCone.setBeam(
                polygon = poly,
                facingAngle = facing,
                fov = bot.visionFov,
                range = bot.visionRange,
                glowRadius = 8.0
            )
        }
    }

    companion object {
        fun createAll(worldView: Container, bots: List<CameraBot>): List<CameraBotVisual> {
            return bots.map { bot ->
                val lightCone = LightConeView().addTo(worldView)
                val cont = worldView.container().xy(bot.x, bot.y)

                val chassis = cont.container()

                // 1. Crawler Treads (Base)
                chassis.solidRect(bot.width, 7.0, Colors["#0f172a"]).xy(0.0, bot.height - 7.0)
                chassis.solidRect(bot.width - 2.0, 2.0, Colors["#334155"]).xy(1.0, bot.height - 7.0)
                // Track wheels
                for (w in 0..2) {
                    chassis.solidRect(4.0, 4.0, Colors["#475569"]).xy(3.0 + w * 9.0, bot.height - 5.5)
                }

                // 2. Armored Chassis (Middle Hull)
                chassis.solidRect(bot.width - 6.0, 11.0, Colors["#1e293b"]).xy(2.0, bot.height - 18.0)
                // Chassis bevel plate
                chassis.solidRect(bot.width - 10.0, 4.0, Colors["#334155"]).xy(4.0, bot.height - 17.0)

                // 3. Sensor Dome Turret (Front Top)
                val turretW = 12.0
                val turretH = 10.0
                val turretX = bot.width - 13.0
                val turretY = bot.height - 23.0
                chassis.solidRect(turretW, turretH, Colors["#0f172a"]).xy(turretX, turretY)
                chassis.solidRect(turretW - 2.0, 2.0, Colors["#64748b"]).xy(turretX + 1.0, turretY)

                // 4. Optical Eye Lens & Glowing Aperture
                val eyeCont = chassis.container().xy(turretX + turretW - 2.0, turretY + 4.0)
                val eyeGlow = eyeCont.image(VentFxAssets.botEyeGlowBitmap) {
                    size(14.0, 14.0)
                    blendMode = BlendMode.ADD
                }.xy(-7.0, -7.0)

                val eyePupil = eyeCont.solidRect(4.0, 4.0, Colors["#38bdf8"]).xy(-2.0, -2.0)
                val eyePip = eyeCont.solidRect(2.0, 2.0, Colors.WHITE).xy(-1.0, -1.0)

                // 5. Status Sparks on chassis when deactivated
                val sparks = chassis.solidRect(3.0, 3.0, Colors["#f59e0b"]).xy(turretX - 4.0, turretY + 2.0)
                sparks.visible = false

                CameraBotVisual(
                    bot = bot,
                    container = cont,
                    chassisContainer = chassis,
                    eyeGlow = eyeGlow,
                    eyePupil = eyePupil,
                    eyePip = eyePip,
                    lightCone = lightCone,
                    sparks = sparks
                )
            }
        }
    }
}

/**
 * The nozzle brackets and the lethal steam jet they fire across the duct.
 *
 * The old plume was invisible in play and mechanical when you did catch it, for reasons worth
 * keeping written down:
 *
 * - **It was additive white over a brightly lit wall.** `bglvl7.png`'s duct is a pale steel
 *   corridor, so adding light to it barely moved a pixel; measured against a screenshot, an active
 *   jet changed the frame by well under one grey level. This is an instant-death hazard, so it now
 *   blends normally and OCCLUDES the wall, which is both what condensing vapour does and what makes
 *   it readable.
 * - **Particles cycled `offset % jetSpan`**: each one crossed the full 136-unit corridor at a
 *   constant speed and teleported back to the nozzle, in eighteen fixed lanes. They now have real
 *   lifetimes and are re-seeded at death with a fresh reach, speed, spread and sway, so the plume's
 *   leading front is ragged and never repeats.
 * - **It did not decelerate.** Travel is now `reach * (1 - (1-life)^2)`: a hard punch out of the
 *   nozzle mouth that slows as the jet spreads, which is what sells "pressurised".
 * - **It ignored buoyancy.** Hot vapour curls back upwards once it has lost its momentum, so a
 *   ceiling nozzle's jet noses up at the end and a floor nozzle's keeps climbing. One cubic term.
 * - **It strobed.** The whole plume pulsed at `sin(t * 30)` (~4.8 Hz) and every particle wobbled at
 *   exactly 16 rad/s, so the column snaked as one body. Sway is now per-particle at 1.1..2.6 rad/s
 *   and the pressure throb is a slow 2.4 rad/s.
 * - **It appeared at full length.** Activation now re-seeds every particle at the nozzle with a
 *   staggered birth, so the jet visibly bursts out over ~0.2s.
 *
 * Honesty about the hazard is a constraint, not a style choice: `SteamPipe.bounds` kills across the
 * FULL corridor height and a fixed `jetWidth`, so [reach] stays near 1.0 of the span and the lateral
 * spread is held close to that width. Do not tune the visual shorter than the box that kills.
 */
class SteamPipeVisual(
    private val pipe: SteamPipe,
    private val container: Container,
    private val topLed: SolidRect?,
    private val botLed: SolidRect?,
    private val steamPlume: Container,
    private val particles: List<Image>,
    private val downwards: BooleanArray
) {
    private val jetSpan: Double = (pipe.bottomY - pipe.topY).coerceAtLeast(10.0)

    private val life = DoubleArray(particles.size)
    private val lifeSpan = DoubleArray(particles.size)
    private val reach = DoubleArray(particles.size)
    private val lateral = DoubleArray(particles.size)
    private val swayAmp = DoubleArray(particles.size)
    private val swayFreq = DoubleArray(particles.size)
    private val phase = DoubleArray(particles.size)
    private val baseSize = DoubleArray(particles.size)
    private val alphaScale = DoubleArray(particles.size)

    private var wasEmitting = false

    private var rng: Int = (pipe.x * 13.0).toInt() xor 0x1f123bb5

    private fun rnd(): Double {
        rng = rng * 1664525 + 1013904223
        return (((rng ushr 8) and 0xFFFFFF).toDouble()) / 16777216.0
    }

    private fun rnd(lo: Double, hi: Double): Double = lo + (hi - lo) * rnd()

    init {
        for (i in particles.indices) seedParticle(i, staggerIndex = -1)
    }

    /**
     * @param staggerIndex >= 0 gives the particle a small negative life so it is still inside the
     * nozzle, which is how the jet grows out of the mouth on the frame it switches on. -1 seeds a
     * random point in an already-running jet.
     */
    private fun seedParticle(i: Int, staggerIndex: Int) {
        life[i] = if (staggerIndex >= 0) -staggerIndex * 0.014 else rnd()
        lifeSpan[i] = rnd(0.46, 0.82)
        // Near or just past a full crossing: the hazard box spans the whole corridor, so the vapour
        // has to as well - the variation is there to ragged the leading front, not to shorten it.
        reach[i] = jetSpan * rnd(0.86, 1.06)
        lateral[i] = rnd(-1.0, 1.0) * 16.0
        swayAmp[i] = rnd(1.2, 3.8)
        swayFreq[i] = rnd(1.1, 2.6)
        phase[i] = rnd() * 6.2831853
        baseSize[i] = rnd(27.0, 47.0)
        alphaScale[i] = rnd(0.62, 1.0)
    }

    fun update(dt: Double, totalElapsedSeconds: Double, cullLeft: Double, cullRight: Double) {
        val onScreen = (pipe.x + 50.0 >= cullLeft) && (pipe.x - 50.0 <= cullRight)
        container.visible = onScreen
        if (!onScreen) return

        val active = pipe.isActive
        val warning = pipe.isWarning
        val warningProg = pipe.warningProgress

        // Status LED color
        val ledColor = when {
            active -> Colors["#ef4444"]
            warning -> if ((totalElapsedSeconds * 14.0).toInt() % 2 == 0) Colors["#f59e0b"] else Colors["#78350f"]
            else -> Colors["#10b981"]
        }
        topLed?.color = ledColor
        botLed?.color = ledColor

        val emitting = active || warning
        if (!emitting) {
            wasEmitting = false
            steamPlume.visible = false
            return
        }
        steamPlume.visible = true

        // The pre-burst only spits a short way out of the mouth; the real jet crosses the corridor.
        val reachScale = if (active) 1.0 else 0.16
        val intensity = if (active) 1.0 else (0.30 * warningProg)

        if (active && !wasEmitting) {
            // Rising edge: pull the whole plume back into the nozzle, staggered, so the jet visibly
            // punches out over ~0.2s instead of appearing fully formed the instant the valve opens.
            for (i in particles.indices) seedParticle(i, staggerIndex = i)
        }
        wasEmitting = true

        // Slow pressure throb from the supply line, not a strobe.
        val throb = 0.90 + 0.10 * sin(totalElapsedSeconds * 2.4)

        for (i in particles.indices) {
            life[i] += dt / lifeSpan[i]
            if (life[i] >= 1.0) {
                // A mid-jet recycle is a fresh puff leaving the mouth, so it restarts at zero
                // rather than at the random point `seedParticle` hands a first generation.
                seedParticle(i, staggerIndex = -1)
                life[i] = 0.0
            }
            val l = life[i]
            val p = particles[i]
            if (l <= 0.0) {
                p.alpha = 0.0
                continue
            }

            // Decelerating jet: most of the distance is covered in the first half of the life.
            val inv = 1.0 - l
            val travel = reach[i] * reachScale * (1.0 - inv * inv)
            // Hot vapour rises once it has spent its momentum - lifts a ceiling jet's nose and
            // carries a floor jet's further. Cubic, so it only bites at the tail.
            val buoyancy = 9.0 * l * l * l
            val cy = (if (downwards[i]) pipe.topY + travel else pipe.bottomY - travel) - buoyancy

            // Spread opens with distance; the sway is this particle's own, not a shared oscillator.
            val cx = pipe.x + lateral[i] * l * l +
                swayAmp[i] * l * sin(totalElapsedSeconds * swayFreq[i] + phase[i])

            // Billowing: tight and elongated along the flow at the mouth, round and wide once slow.
            // A nozzle mouth already has a width, so the jet starts thick and flares rather than
            // growing from a point.
            val w = baseSize[i] * (0.55 + 1.15 * sqrt(l)) * (0.55 + 0.45 * reachScale)
            val h = w * (1.0 + 0.85 * inv)

            val fade = (l * 7.0).coerceAtMost(1.0) * inv.pow(0.85)
            // NEVER `p.size(w, h)` in a frame loop. `View.size` writes `unscaledSize`, whose setter
            // is `scaleXY *= value / currentSize` (korge 6.0 `View.kt:399`) - it is MULTIPLICATIVE,
            // so every call re-scales relative to whatever the sprite is now. The old plume called
            // it every frame against a 48px source drawn at ~20-45, and its sprites decayed to
            // ~1e-72 units wide within a second of the level starting: that, not the additive
            // blending, is why the steam was invisible in play. `SolidRect` overrides `unscaledSize`
            // with a plain field and is safe; `Image` is not. Write the transform directly.
            p.scaleX = w / VentFxAssets.PUFF_SIZE
            p.scaleY = h / VentFxAssets.PUFF_SIZE
            p.xy(cx - w / 2.0, cy - h / 2.0)
            p.alpha = (0.95 * alphaScale[i] * fade * throb * intensity).coerceIn(0.0, 1.0)
        }
    }

    companion object {
        private const val SINGLE_COUNT = 18
        private const val PAIR_COUNT = 24

        fun createAll(worldView: Container, pipes: List<SteamPipe>): List<SteamPipeVisual> {
            return pipes.map { pipe ->
                val cont = worldView.container()

                var topLed: SolidRect? = null
                var botLed: SolidRect? = null

                val nozzleW = 26.0
                val nozzleH = 12.0

                // Top Nozzle Bracket (Mounted to ceiling at topY = 304)
                if (pipe.mountType == PipeMountType.TOP || pipe.mountType == PipeMountType.PAIR) {
                    val nTop = cont.container().xy(pipe.x - nozzleW / 2.0, pipe.topY - nozzleH)
                    // Structural collar attached to ceiling plate
                    nTop.solidRect(nozzleW, 4.0, Colors["#475569"]).xy(0.0, 0.0)
                    // Heavy conical nozzle mouth
                    nTop.solidRect(nozzleW - 6.0, nozzleH - 4.0, Colors["#1e293b"]).xy(3.0, 4.0)
                    // High-temperature nozzle mouth rim
                    nTop.solidRect(nozzleW - 10.0, 2.0, Colors["#e2e8f0"]).xy(5.0, nozzleH - 2.0)
                    // Status LED indicator pip
                    topLed = nTop.solidRect(4.0, 4.0, Colors["#10b981"]).xy(2.0, 2.0)
                }

                // Bottom Nozzle Bracket (Mounted to floor at bottomY = 440)
                if (pipe.mountType == PipeMountType.BOTTOM || pipe.mountType == PipeMountType.PAIR) {
                    val nBot = cont.container().xy(pipe.x - nozzleW / 2.0, pipe.bottomY)
                    // Structural collar attached to floor plate
                    nBot.solidRect(nozzleW, 4.0, Colors["#475569"]).xy(0.0, 0.0)
                    // Heavy conical nozzle mouth
                    nBot.solidRect(nozzleW - 6.0, nozzleH - 4.0, Colors["#1e293b"]).xy(3.0, -nozzleH + 4.0)
                    // High-temperature nozzle mouth rim
                    nBot.solidRect(nozzleW - 10.0, 2.0, Colors["#e2e8f0"]).xy(5.0, -2.0)
                    // Status LED indicator pip
                    botLed = nBot.solidRect(4.0, 4.0, Colors["#10b981"]).xy(2.0, -2.0)
                }

                // Vapour column. No ADD - see the class doc.
                val plumeCont = cont.container()
                val count = if (pipe.mountType == PipeMountType.PAIR) PAIR_COUNT else SINGLE_COUNT
                val slices = VentFxAssets.vaporPuffSlices
                val downwards = BooleanArray(count) { i ->
                    when (pipe.mountType) {
                        PipeMountType.TOP -> true
                        PipeMountType.BOTTOM -> false
                        PipeMountType.PAIR -> (i % 2 == 0)
                    }
                }
                val particles = (0 until count).map { i -> plumeCont.image(slices[i % slices.size]) }

                SteamPipeVisual(
                    pipe = pipe,
                    container = cont,
                    topLed = topLed,
                    botLed = botLed,
                    steamPlume = plumeCont,
                    particles = particles,
                    downwards = downwards
                )
            }
        }
    }
}

/**
 * Architectural framing and industrial atmosphere for Level 7's ventilation shaft.
 */
class VentCorridorVisual(
    val container: Container
) {
    companion object {
        fun create(worldView: Container, layout: LevelLayout): VentCorridorVisual {
            val cont = worldView.container()
            val groundY = 440.0

            // High-tech terminal pedestal at exit point (x = 5050)
            val terminalCont = cont.container().xy(5050.0, groundY - 48.0)
            terminalCont.solidRect(32.0, 48.0, Colors["#334155"]).xy(0.0, 0.0)
            terminalCont.solidRect(28.0, 4.0, Colors["#0284c7"]).xy(2.0, 2.0)
            // Holographic data manifest projection above terminal
            val holo = terminalCont.solidRect(24.0, 20.0, Colors["#38bdf8"].withAd(0.40)).xy(4.0, -24.0)
            holo.blendMode = BlendMode.ADD
            terminalCont.solidRect(16.0, 2.0, Colors.WHITE).xy(8.0, -14.0)

            return VentCorridorVisual(cont)
        }
    }
}
