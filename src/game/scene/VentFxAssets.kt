package game.scene

import game.model.*
import korlibs.image.bitmap.Bitmap32
import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.korge.view.*
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

    val steamParticleBitmap: Bitmap32 by lazy { createSteamParticleBitmap() }
    val windStreakBitmap: Bitmap32 by lazy { createWindStreakBitmap() }
    val windMistBitmap: Bitmap32 by lazy { createWindMistBitmap() }
    val botEyeGlowBitmap: Bitmap32 by lazy { createBotEyeGlowBitmap() }

    private fun createSteamParticleBitmap(): Bitmap32 {
        val size = 48
        val bmp = Bitmap32(size, size)
        val center = 23.5
        val maxDist = 23.5

        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - center
                val dy = y - center
                val dist = hypot(dx, dy)
                if (dist <= maxDist) {
                    val t = dist / maxDist
                    // Ultra-smooth Gaussian-like radial decay with soft edge feathering
                    val alphaFactor = exp(-3.2 * t * t) * (1.0 - t.pow(2.2)).coerceIn(0.0, 1.0)
                    val a = (240 * alphaFactor).toInt().coerceIn(0, 255)
                    // High-temperature core vapor: luminous white transitioning to subtle atmospheric cyan-grey
                    val r = (245 + 10 * (1.0 - t)).toInt().coerceIn(0, 255)
                    val g = (250 + 5 * (1.0 - t)).toInt().coerceIn(0, 255)
                    val b = 255
                    bmp.setRgba(x, y, RGBA(r, g, b, a))
                } else {
                    bmp.setRgba(x, y, RGBA(0, 0, 0, 0))
                }
            }
        }
        return bmp
    }

    private fun createWindStreakBitmap(): Bitmap32 {
        val width = 72
        val height = 12
        val bmp = Bitmap32(width, height)
        val centerY = 5.5
        val maxDy = 5.5

        for (y in 0 until height) {
            val dy = abs(y - centerY) / maxDy
            val yAlpha = exp(-4.2 * dy * dy).coerceIn(0.0, 1.0)
            for (x in 0 until width) {
                // Wind flows left: x = 0 is leading front tip, x = width - 1 is trailing fan origin
                val u = x.toDouble() / (width - 1.0)
                // Asymmetric teardrop aerodynamic streamer: soft rounded nose, bright core, elongated dissipating tail
                val xAlpha = when {
                    u < 0.18 -> (u / 0.18).pow(1.2) // smooth tapered nose
                    u < 0.40 -> 1.0 // dense high-speed core
                    else -> ((1.0 - u) / 0.60).pow(1.5) // dissipating tail
                }.coerceIn(0.0, 1.0)

                val a = (220 * xAlpha * yAlpha).toInt().coerceIn(0, 255)
                // Aerodynamic gust streak: bright cyan core with atmospheric halo
                val r = (195 + 40 * xAlpha).toInt().coerceIn(0, 255)
                val g = (235 + 20 * xAlpha).toInt().coerceIn(0, 255)
                val b = 255
                bmp.setRgba(x, y, RGBA(r, g, b, a))
            }
        }
        return bmp
    }

    private fun createWindMistBitmap(): Bitmap32 {
        val size = 24
        val bmp = Bitmap32(size, size)
        val center = 11.5
        val maxDist = 11.5

        for (y in 0 until size) {
            for (x in 0 until size) {
                val dist = hypot(x - center, y - center)
                if (dist <= maxDist) {
                    val t = dist / maxDist
                    val alpha = (exp(-3.0 * t * t) * (1.0 - t) * 190).toInt().coerceIn(0, 255)
                    bmp.setRgba(x, y, RGBA(210, 245, 255, alpha))
                } else {
                    bmp.setRgba(x, y, RGBA(0, 0, 0, 0))
                }
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
                    bmp.setRgba(x, y, RGBA(56, 189, 248, alpha))
                } else {
                    bmp.setRgba(x, y, RGBA(0, 0, 0, 0))
                }
            }
        }
        return bmp
    }
}

/**
 * Visual presentation and dynamic blade rotation / organic wind particle animation for industrial vent fans.
 */
class VentFanVisual(
    val fan: VentFan,
    val container: Container,
    val bladesContainer: Container,
    val windContainer: Container,
    val windStreaks: List<Image>,
    val streakRelativeX: DoubleArray,
    val streakSpeeds: DoubleArray,
    val streakBaseYs: DoubleArray,
    val streakWaveAmplitudes: DoubleArray,
    val streakWavePhases: DoubleArray,
    val streakSpreadFactors: DoubleArray,
    val mistPuffs: List<Image>,
    val mistRelativeX: DoubleArray,
    val mistSpeeds: DoubleArray,
    val mistBaseYs: DoubleArray,
    val mistWavePhases: DoubleArray
) {
    fun update(dt: Double, totalElapsedSeconds: Double, cullLeft: Double, cullRight: Double) {
        val isVisible = fan.windMaxX >= cullLeft && fan.windMinX <= cullRight
        container.visible = isVisible
        if (!isVisible) return

        // Rotate blades
        bladesContainer.rotation = fan.bladeRotationAngle.radians

        // Animate wind streamers blowing backward (to the left)
        val windLen = (fan.windMaxX - fan.windMinX).coerceAtLeast(10.0)
        for (i in windStreaks.indices) {
            val streak = windStreaks[i]
            streakRelativeX[i] -= streakSpeeds[i] * dt
            if (streakRelativeX[i] < 0.0) {
                streakRelativeX[i] += windLen
            }

            val worldX = fan.windMinX + streakRelativeX[i]
            val streakProgress = (streakRelativeX[i] / windLen).coerceIn(0.0, 1.0)

            // Aerodynamic wave ripple & conical jet expansion away from fan
            val wave = sin(worldX * 0.022 + totalElapsedSeconds * 11.0 + streakWavePhases[i]) * streakWaveAmplitudes[i]
            val spread = (1.0 - streakProgress) * streakSpreadFactors[i]
            val y = streakBaseYs[i] + wave + spread

            // Natural atmospheric opacity envelope:
            // Soft emergence near blades, strong mid-duct presence, soft dissipation at wind reach boundary
            val fadeEdge = when {
                streakProgress > 0.88 -> ((1.0 - streakProgress) / 0.12).coerceIn(0.0, 1.0)
                streakProgress < 0.22 -> (streakProgress / 0.22).pow(1.3).coerceIn(0.0, 1.0)
                else -> 1.0
            }
            val shimmer = 0.82 + 0.18 * sin(totalElapsedSeconds * 15.0 + i * 1.6)
            val alpha = (0.25 + 0.50 * streakProgress) * fadeEdge * shimmer

            streak.xy(worldX, y)
            streak.alpha = alpha.coerceIn(0.0, 1.0)
        }

        // Animate atmospheric mist puffs and draft motes
        for (i in mistPuffs.indices) {
            val mist = mistPuffs[i]
            mistRelativeX[i] -= mistSpeeds[i] * dt
            if (mistRelativeX[i] < 0.0) {
                mistRelativeX[i] += windLen
            }
            val worldX = fan.windMinX + mistRelativeX[i]
            val progress = (mistRelativeX[i] / windLen).coerceIn(0.0, 1.0)
            val driftY = cos(worldX * 0.030 + totalElapsedSeconds * 7.0 + mistWavePhases[i]) * 8.0
            val y = mistBaseYs[i] + driftY
            val alpha = (0.15 + 0.25 * sin(progress * PI)) * (0.8 + 0.2 * sin(totalElapsedSeconds * 12.0 + i))
            mist.xy(worldX, y)
            mist.alpha = alpha.coerceIn(0.0, 1.0)
        }
    }

    companion object {
        fun createAll(worldView: Container, fans: List<VentFan>): List<VentFanVisual> {
            return fans.map { fan ->
                val cont = worldView.container()

                // 1. Heavy Industrial Wall Turbine Frame
                val housingCont = cont.container().xy(fan.x, fan.y)
                // Dark duct cavity behind blades
                housingCont.solidRect(fan.width, fan.height, Colors["#090d14"])

                // Steel casing rim bevels
                housingCont.solidRect(4.0, fan.height, Colors["#334155"]).xy(0.0, 0.0)
                housingCont.solidRect(4.0, fan.height, Colors["#1e293b"]).xy(fan.width - 4.0, 0.0)
                housingCont.solidRect(fan.width, 3.0, Colors["#475569"]).xy(0.0, 0.0)
                housingCont.solidRect(fan.width, 3.0, Colors["#1e293b"]).xy(0.0, fan.height - 3.0)

                // 2. Rotating Turbine Rotor
                val centerX = fan.x + fan.width / 2.0
                val centerY = fan.y + fan.height / 2.0
                val bladesCont = cont.container().xy(centerX, centerY)

                val bladeLength = (fan.height * 0.44).coerceAtLeast(10.0)
                val bladeW = 6.0
                // 4 Aerodynamic curved fan blades
                for (b in 0 until 4) {
                    val angleDeg = b * 90.0
                    val blade = bladesCont.container()
                    blade.rotation = angleDeg.degrees
                    blade.solidRect(bladeLength, bladeW, Colors["#64748b"]).xy(2.0, -bladeW / 2.0)
                    blade.solidRect(bladeLength * 0.7, bladeW * 0.5, Colors["#94a3b8"]).xy(3.0, -bladeW / 2.0)
                }
                // Central rotor hub
                bladesCont.solidRect(10.0, 10.0, Colors["#334155"]).xy(-5.0, -5.0)
                bladesCont.solidRect(6.0, 6.0, Colors["#cbd5e1"]).xy(-3.0, -3.0)

                // 3. Heavy Wire Protective Mesh Grill over the front face
                val grillCont = cont.container().xy(fan.x, fan.y)
                grillCont.solidRect(1.5, fan.height, Colors["#475569"]).xy(fan.width * 0.33, 0.0)
                grillCont.solidRect(1.5, fan.height, Colors["#475569"]).xy(fan.width * 0.66, 0.0)
                grillCont.solidRect(fan.width, 1.5, Colors["#334155"]).xy(0.0, fan.height * 0.5)

                // 4. Backward Wind Gust Streaks Container
                val windCont = cont.container()
                windCont.blendMode = BlendMode.ADD

                // 18 Streamers of varying dimensions and depths
                val streakCount = 18
                val streaks = ArrayList<Image>(streakCount)
                val streakRelativeX = DoubleArray(streakCount)
                val streakSpeeds = DoubleArray(streakCount)
                val streakBaseYs = DoubleArray(streakCount)
                val streakWaveAmplitudes = DoubleArray(streakCount)
                val streakWavePhases = DoubleArray(streakCount)
                val streakSpreadFactors = DoubleArray(streakCount)
                val windLen = (fan.windMaxX - fan.windMinX).coerceAtLeast(10.0)

                for (i in 0 until streakCount) {
                    val img = windCont.image(VentFxAssets.windStreakBitmap)
                    val streakW = 42.0 + (i % 5) * 8.0 // 42.0 .. 74.0
                    val streakH = 2.8 + (i % 4) * 0.7 // 2.8 .. 4.9
                    img.size(streakW, streakH)
                    streaks.add(img)

                    streakRelativeX[i] = (i.toDouble() / streakCount) * windLen + (i * 23.0) % 47.0
                    streakSpeeds[i] = fan.windPushSpeed * (1.30 + 0.40 * ((i % 5) / 4.0))
                    val yFraction = (i.toDouble() + 0.5) / streakCount
                    streakBaseYs[i] = fan.y + 10.0 + (fan.height - 20.0) * yFraction
                    streakWaveAmplitudes[i] = 3.5 + (i % 4) * 1.5
                    streakWavePhases[i] = i * 1.45
                    val distFromCenter = (yFraction - 0.5) * 2.0 // -1.0 .. 1.0
                    streakSpreadFactors[i] = distFromCenter * 10.0
                }

                // 10 Swirling mist motes
                val mistCount = 10
                val mistPuffs = ArrayList<Image>(mistCount)
                val mistRelativeX = DoubleArray(mistCount)
                val mistSpeeds = DoubleArray(mistCount)
                val mistBaseYs = DoubleArray(mistCount)
                val mistWavePhases = DoubleArray(mistCount)

                for (i in 0 until mistCount) {
                    val m = windCont.image(VentFxAssets.windMistBitmap)
                    val size = 12.0 + (i % 3) * 6.0
                    m.size(size, size)
                    mistPuffs.add(m)
                    mistRelativeX[i] = (i.toDouble() / mistCount) * windLen + (i * 31.0) % 53.0
                    mistSpeeds[i] = fan.windPushSpeed * (1.15 + 0.25 * ((i % 3) / 2.0))
                    mistBaseYs[i] = fan.y + 12.0 + (fan.height - 24.0) * ((i.toDouble() + 0.3) / mistCount)
                    mistWavePhases[i] = i * 2.1
                }

                VentFanVisual(
                    fan = fan,
                    container = cont,
                    bladesContainer = bladesCont,
                    windContainer = windCont,
                    windStreaks = streaks,
                    streakRelativeX = streakRelativeX,
                    streakSpeeds = streakSpeeds,
                    streakBaseYs = streakBaseYs,
                    streakWaveAmplitudes = streakWaveAmplitudes,
                    streakWavePhases = streakWavePhases,
                    streakSpreadFactors = streakSpreadFactors,
                    mistPuffs = mistPuffs,
                    mistRelativeX = mistRelativeX,
                    mistSpeeds = mistSpeeds,
                    mistBaseYs = mistBaseYs,
                    mistWavePhases = mistWavePhases
                )
            }
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
 * Visual presentation for pressurized steam / hot air pipe nozzles and lethal vapor jets.
 */
class SteamPipeVisual(
    val pipe: SteamPipe,
    val container: Container,
    val topLed: SolidRect?,
    val botLed: SolidRect?,
    val steamPlume: Container,
    val steamParticles: List<Image>,
    val particleOffsetsY: DoubleArray,
    val particleSpeedsY: DoubleArray,
    val particleWobbles: DoubleArray,
    val isDownwards: BooleanArray,
    val baseSizes: DoubleArray
) {
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

        if (active) {
            steamPlume.visible = true
            val pulse = 0.88 + 0.12 * sin(totalElapsedSeconds * 30.0)
            steamPlume.alpha = pulse

            val jetSpan = (pipe.bottomY - pipe.topY).coerceAtLeast(10.0)
            for (i in steamParticles.indices) {
                val p = steamParticles[i]
                particleOffsetsY[i] = (particleOffsetsY[i] + particleSpeedsY[i] * dt) % jetSpan
                val offset = particleOffsetsY[i]
                val fraction = (offset / jetSpan).coerceIn(0.0, 1.0)

                // Air/steam shoots strictly INWARD into the corridor:
                // TOP nozzle shoots DOWN into corridor (304.0 -> 440.0)
                // BOTTOM nozzle shoots UP into corridor (440.0 -> 304.0)
                val py = if (isDownwards[i]) {
                    pipe.topY + offset
                } else {
                    pipe.bottomY - offset
                }

                // Billowing conical expansion: starts narrow & dense at nozzle, expanding outward
                val currentSize = baseSizes[i] * (0.42 + 0.85 * fraction)
                // Aerodynamic lateral turbulence increases with distance from nozzle
                val lateralWobble = sin(totalElapsedSeconds * 16.0 + particleWobbles[i]) * (1.5 + 7.0 * fraction)
                // Density falloff: white-hot dense core at nozzle, softly dissipating cloud
                val alphaFactor = (1.0 - fraction).pow(0.65) * (0.35 + 0.65 * pulse)

                p.size(currentSize, currentSize)
                p.xy(pipe.x - currentSize / 2.0 + lateralWobble, py - currentSize / 2.0)
                p.alpha = alphaFactor.coerceIn(0.0, 1.0)
            }
        } else if (warning) {
            steamPlume.visible = true
            val jetSpan = (pipe.bottomY - pipe.topY).coerceAtLeast(10.0)
            for (i in steamParticles.indices) {
                val p = steamParticles[i]
                particleOffsetsY[i] = (particleOffsetsY[i] + particleSpeedsY[i] * dt * 0.35) % jetSpan
                val fraction = (particleOffsetsY[i] / jetSpan).coerceIn(0.0, 1.0)

                // Sputtering warning steam only hisses slightly from the nozzle mouth (first 25% of corridor)
                val warningOffset = particleOffsetsY[i] * 0.25
                val py = if (isDownwards[i]) {
                    pipe.topY + warningOffset
                } else {
                    pipe.bottomY - warningOffset
                }

                val jitter = sin(totalElapsedSeconds * 36.0 + particleWobbles[i]) * 2.0
                val currentSize = baseSizes[i] * 0.40
                p.size(currentSize, currentSize)
                p.xy(pipe.x - currentSize / 2.0 + jitter, py - currentSize / 2.0)
                p.alpha = (warningProg * 0.45 * (1.0 - fraction)).coerceIn(0.0, 1.0)
            }
        } else {
            steamPlume.visible = false
        }
    }

    companion object {
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

                // Pressurized Steam Plume (Volumetric particle column)
                val plumeCont = cont.container()
                plumeCont.blendMode = BlendMode.ADD
                val particleCount = 18
                val particles = ArrayList<Image>(particleCount)
                val offsets = DoubleArray(particleCount)
                val speeds = DoubleArray(particleCount)
                val wobbles = DoubleArray(particleCount)
                val isDownwards = BooleanArray(particleCount)
                val baseSizes = DoubleArray(particleCount)
                val jetSpan = (pipe.bottomY - pipe.topY).coerceAtLeast(10.0)

                for (i in 0 until particleCount) {
                    val p = plumeCont.image(VentFxAssets.steamParticleBitmap)
                    particles.add(p)

                    val isDown = when (pipe.mountType) {
                        PipeMountType.TOP -> true
                        PipeMountType.BOTTOM -> false
                        PipeMountType.PAIR -> (i % 2 == 0)
                    }
                    isDownwards[i] = isDown
                    offsets[i] = (i.toDouble() / particleCount) * jetSpan
                    speeds[i] = 160.0 + (i % 5) * 22.0
                    wobbles[i] = i * 1.35
                    baseSizes[i] = 34.0 + (i % 4) * 4.0
                }

                SteamPipeVisual(
                    pipe = pipe,
                    container = cont,
                    topLed = topLed,
                    botLed = botLed,
                    steamPlume = plumeCont,
                    steamParticles = particles,
                    particleOffsetsY = offsets,
                    particleSpeedsY = speeds,
                    particleWobbles = wobbles,
                    isDownwards = isDownwards,
                    baseSizes = baseSizes
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
