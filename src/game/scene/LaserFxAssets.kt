package game.scene

import game.model.Laser
import korlibs.image.bitmap.Bitmap
import korlibs.image.bitmap.Bitmap32
import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.korge.view.*
import korlibs.math.geom.degrees
import kotlin.math.*

/**
 * High-performance, mobile-optimized procedural textures for realistic laser hazards.
 *
 * Generates two ultra-compact static textures once per process lifecycle (~17 KB total memory):
 * 1. [beamBitmap] (2x64 px): Smooth Gaussian cross-sectional profile transitioning from blinding
 *    white-hot core to intense neon-red plasma to soft atmospheric dissipation.
 * 2. [flareBitmap] (64x64 px): Smooth 2D radial bloom for beam contact points (the bottom
 *    target cylinder and ceiling emitter aperture).
 * 3. [cylinderBitmap] (24x14 px): 3D metallic receptor cylinder with curved top rim,
 *    central optical sensor aperture, specular sheen, and beveled mounting flange.
 * 4. [emitterBitmap] (24x12 px): Industrial ceiling mount bracket with heatsink cooling fins and
 *    conical focusing lens nozzle.
 *
 * Rendered using [BlendMode.ADD], these textures produce a realistic volumetric laser glow with
 * zero per-frame CPU rasterization, zero shader overhead, and minimal GPU draw call cost.
 */
object LaserFxAssets {

    val beamBitmap: Bitmap32 by lazy { createBeamBitmap() }
    val flareBitmap: Bitmap32 by lazy { createFlareBitmap() }
    val cylinderBitmap: Bitmap32 by lazy { createCylinderBitmap() }
    val emitterBitmap: Bitmap32 by lazy { createEmitterBitmap() }

    private fun createBeamBitmap(): Bitmap32 {
        val width = 2
        val height = 64
        val bmp = Bitmap32(width, height)
        val centerY = 31.5

        for (y in 0 until height) {
            val dist = abs(y - centerY)
            val v = (dist / 31.5).coerceIn(0.0, 1.0)

            val r: Int
            val g: Int
            val b: Int
            val a: Int

            when {
                // Ultra-hot coherent core: pure white fading to hot neon red
                v <= 0.12 -> {
                    val t = v / 0.12
                    r = 255
                    g = (255 * (1.0 - t) + 40 * t).toInt().coerceIn(0, 255)
                    b = (255 * (1.0 - t) + 90 * t).toInt().coerceIn(0, 255)
                    a = 255
                }
                // Intense laser plasma sheath: saturated ruby red with high opacity
                v <= 0.40 -> {
                    val t = (v - 0.12) / (0.40 - 0.12)
                    r = 255
                    g = (40 * (1.0 - t)).toInt().coerceIn(0, 255)
                    b = (90 * (1.0 - t * t)).toInt().coerceIn(0, 255)
                    a = (255 * (1.0 - t * 0.25)).toInt().coerceIn(0, 255)
                }
                // Outer atmospheric haze: smooth quadratic/exponential falloff to 0
                else -> {
                    val t = (v - 0.40) / (1.0 - 0.40)
                    val falloff = (1.0 - t) * (1.0 - t)
                    r = (255 * falloff).toInt().coerceIn(0, 255)
                    g = 0
                    b = (30 * falloff).toInt().coerceIn(0, 255)
                    a = (190 * falloff).toInt().coerceIn(0, 255)
                }
            }

            val rgba = RGBA(r, g, b, a)
            for (x in 0 until width) {
                bmp.setRgba(x, y, rgba)
            }
        }
        return bmp
    }

    private fun createFlareBitmap(): Bitmap32 {
        val size = 64
        val bmp = Bitmap32(size, size)
        val center = 31.5
        val maxRadius = 31.5

        for (y in 0 until size) {
            val dy = y - center
            for (x in 0 until size) {
                val dx = x - center
                val dist = sqrt(dx * dx + dy * dy)
                if (dist >= maxRadius) {
                    bmp.setRgba(x, y, RGBA(0, 0, 0, 0))
                    continue
                }

                val u = (dist / maxRadius).coerceIn(0.0, 1.0)
                val r: Int
                val g: Int
                val b: Int
                val a: Int

                when {
                    // Central focal spot
                    u <= 0.15 -> {
                        val t = u / 0.15
                        r = 255
                        g = (255 * (1.0 - t) + 70 * t).toInt().coerceIn(0, 255)
                        b = (255 * (1.0 - t) + 110 * t).toInt().coerceIn(0, 255)
                        a = 255
                    }
                    // Inner corona
                    u <= 0.45 -> {
                        val t = (u - 0.15) / (0.45 - 0.15)
                        r = 255
                        g = (70 * (1.0 - t)).toInt().coerceIn(0, 255)
                        b = (110 * (1.0 - t)).toInt().coerceIn(0, 255)
                        a = (255 * (1.0 - 0.4 * t)).toInt().coerceIn(0, 255)
                    }
                    // Outer diffuse bloom
                    else -> {
                        val t = (u - 0.45) / (1.0 - 0.45)
                        val falloff = (1.0 - t) * (1.0 - t)
                        r = (255 * falloff).toInt().coerceIn(0, 255)
                        g = 0
                        b = (25 * falloff).toInt().coerceIn(0, 255)
                        a = (160 * falloff).toInt().coerceIn(0, 255)
                    }
                }

                bmp.setRgba(x, y, RGBA(r, g, b, a))
            }
        }
        return bmp
    }

    private fun createCylinderBitmap(): Bitmap32 {
        val width = 24
        val height = 14
        val bmp = Bitmap32(width, height)
        val centerX = 11.5

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - centerX
                val absDx = abs(dx)

                // 1. Base flange / floor plate (y in 11..13, width 22 px)
                if (y >= 11) {
                    if (absDx <= 11.0) {
                        if (y == 11 && absDx > 9.5) {
                            bmp.setRgba(x, y, RGBA(35, 38, 48, 255))
                        } else if (y == 13) {
                            bmp.setRgba(x, y, RGBA(10, 11, 15, 255))
                        } else {
                            val isRivet = (x == 2 || x == 21) && y == 12
                            if (isRivet) {
                                bmp.setRgba(x, y, RGBA(90, 100, 120, 255))
                            } else {
                                val t = (dx + 11.0) / 22.0
                                val shade = (24 + 16 * sin(t * PI)).toInt()
                                bmp.setRgba(x, y, RGBA(shade, shade + 3, shade + 8, 255))
                            }
                        }
                    } else {
                        bmp.setRgba(x, y, RGBA(0, 0, 0, 0))
                    }
                    continue
                }

                // 2. Main cylinder body and top rim (x between 3 and 20, width 18 px)
                if (absDx <= 8.5) {
                    val nx = dx / 8.5 // normalized -1.0 .. +1.0 across cylinder diameter

                    // 2a. Top rim & sensor aperture (y in 0..3)
                    if (y <= 3) {
                        val topCurve = (1.0 - (nx * nx)).coerceAtLeast(0.0)
                        if (y == 0 && topCurve < 0.35) {
                            bmp.setRgba(x, y, RGBA(0, 0, 0, 0))
                            continue
                        }

                        if (absDx <= 3.0 && y in 1..2) {
                            val lensDist = hypot(dx / 3.0, (y - 1.5) / 1.0)
                            if (lensDist <= 0.6) {
                                bmp.setRgba(x, y, RGBA(140, 20, 35, 255))
                            } else if (lensDist <= 1.0) {
                                bmp.setRgba(x, y, RGBA(18, 20, 26, 255))
                            } else {
                                bmp.setRgba(x, y, RGBA(30, 34, 44, 255))
                            }
                        } else if (y == 0 || (y == 1 && absDx > 3.0)) {
                            val rimHighlight = (55 + 35 * (1.0 - abs(nx + 0.3))).toInt().coerceIn(30, 95)
                            bmp.setRgba(x, y, RGBA(rimHighlight, rimHighlight + 6, rimHighlight + 14, 255))
                        } else {
                            val bevelShade = (28 + 15 * (1.0 - abs(nx + 0.2))).toInt().coerceIn(20, 50)
                            bmp.setRgba(x, y, RGBA(bevelShade, bevelShade + 4, bevelShade + 8, 255))
                        }
                    } else {
                        // 2b. Vertical cylindrical body (y in 4..10)
                        val specular = exp(-8.0 * (nx + 0.35) * (nx + 0.35))
                        val diffuse = (1.0 - nx * 0.5).coerceIn(0.2, 1.0)
                        val bodyBase = 22.0 * diffuse + 55.0 * specular
                        val r = bodyBase.toInt().coerceIn(16, 85)
                        val g = (bodyBase * 1.08).toInt().coerceIn(18, 92)
                        val b = (bodyBase * 1.25).toInt().coerceIn(22, 110)

                        if (y == 7) {
                            bmp.setRgba(x, y, RGBA(r / 2, g / 2, b / 2, 255))
                        } else {
                            bmp.setRgba(x, y, RGBA(r, g, b, 255))
                        }
                    }
                } else {
                    bmp.setRgba(x, y, RGBA(0, 0, 0, 0))
                }
            }
        }
        return bmp
    }

    private fun createEmitterBitmap(): Bitmap32 {
        val width = 24
        val height = 12
        val bmp = Bitmap32(width, height)
        val centerX = 11.5

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - centerX
                val absDx = abs(dx)

                when {
                    y <= 2 -> {
                        val isBolt = (x == 2 || x == 21) && y == 1
                        if (isBolt) {
                            bmp.setRgba(x, y, RGBA(100, 110, 130, 255))
                        } else {
                            val edge = if (x == 0 || x == 23) 18 else 32
                            bmp.setRgba(x, y, RGBA(edge, edge + 3, edge + 8, 255))
                        }
                    }
                    y in 3..6 -> {
                        if (absDx <= 8.5) {
                            val isFin = y % 2 == 1
                            val finTone = if (isFin) 45 else 20
                            bmp.setRgba(x, y, RGBA(finTone, finTone + 3, finTone + 7, 255))
                        } else {
                            bmp.setRgba(x, y, RGBA(0, 0, 0, 0))
                        }
                    }
                    else -> {
                        val t = (y - 7) / 4.0
                        val maxHalfW = 7.0 * (1.0 - t) + 4.0 * t
                        if (absDx <= maxHalfW) {
                            if (y == 11 && absDx <= 2.5) {
                                bmp.setRgba(x, y, RGBA(200, 30, 50, 255))
                            } else {
                                val specular = exp(-6.0 * (dx / maxHalfW + 0.3) * (dx / maxHalfW + 0.3))
                                val tone = (26 + 40 * specular).toInt().coerceIn(20, 80)
                                bmp.setRgba(x, y, RGBA(tone, tone + 3, tone + 8, 255))
                            }
                        } else {
                            bmp.setRgba(x, y, RGBA(0, 0, 0, 0))
                        }
                    }
                }
            }
        }
        return bmp
    }
}

/**
 * Visual presentation and per-frame update logic for a realistic laser hazard.
 * Extracted into a dedicated class outside GameplayScene.sceneMain to stay well within the JVM 64KB method limit.
 */
class LaserVisual(
    val laser: Laser,
    val container: Container,
    val beamImage: Image,
    val beamHaze: Image,
    val emitterLed: SolidRect,
    val receiverLed: SolidRect,
    val emitterFlare: Image,
    val receiverFlare: Image,
    val receiverCorePip: SolidRect
) {
    fun update(totalElapsedSeconds: Double, cullLeft: Double, cullRight: Double) {
        val isVisibleInCull = laser.bounds.right >= cullLeft && laser.bounds.left <= cullRight
        container.visible = isVisibleInCull
        if (isVisibleInCull) {
            val active = laser.isActive
            beamImage.visible = active
            beamHaze.visible = active
            emitterFlare.visible = active
            receiverFlare.visible = active
            receiverCorePip.visible = active
            if (active) {
                val laserPulse = (0.90 + 0.10 * sin(totalElapsedSeconds * 28.0) + 0.05 * sin(totalElapsedSeconds * 67.0)).coerceIn(0.75, 1.05)
                val flarePulse = (0.85 + 0.15 * sin(totalElapsedSeconds * 36.0) + 0.08 * cos(totalElapsedSeconds * 53.0)).coerceIn(0.70, 1.15)
                beamImage.alpha = 0.95 * laserPulse
                beamHaze.alpha = 0.22 * laserPulse
                emitterLed.color = Colors["#ff2244"]
                receiverLed.color = Colors["#ff2244"]
                emitterFlare.alpha = 0.85 * flarePulse
                receiverFlare.alpha = 0.85 * flarePulse
                receiverCorePip.alpha = 1.0 * laserPulse
            } else {
                emitterLed.color = Colors["#2ecc71"]
                receiverLed.color = Colors["#2ecc71"]
            }
        }
    }

    companion object {
        fun createAll(
            worldView: Container,
            lasers: List<Laser>,
            laserEmitterBitmap: Bitmap? = null
        ): List<LaserVisual> {
            val emitterBmp = laserEmitterBitmap ?: LaserFxAssets.emitterBitmap

            return lasers.map { laser ->
                val cont = worldView.container()

                val dx = laser.bottomX - laser.topX
                val dy = laser.bottomY - laser.topY
                val totalDist = hypot(dx, dy)
                val angleDeg = atan2(dy, dx) * 180.0 / PI

                // Housing size, per beam (LaserDef.emitterScale) - the beam itself is unaffected.
                val unitLength = 32.0 * laser.emitterScale
                val unitThickness = unitLength * (148.0 / 512.0)
                val nozzleDist = unitLength - 1.0
                val beamLength = (totalDist - 2.0 * nozzleDist).coerceAtLeast(1.0)

                // 1. Top Emitter Cannon (swiveled towards receiver, zero grey boxes)
                val emitterCont = cont.container().xy(laser.topX, laser.topY)
                emitterCont.rotation = angleDeg.degrees

                // Status indicator glow behind the 3 diagonal hazard stripes
                val emitterLed = emitterCont.solidRect(unitLength * 0.20, unitThickness * 0.45, Colors["#ff2244"])
                    .xy(unitLength * 0.08, 0.0)

                // Emitter housing image
                emitterCont.image(emitterBmp)
                    .size(unitLength, unitThickness)
                    .xy(0.0, -unitThickness / 2.0)

                // 2. Bottom Receiver Cannon (aimed up towards emitter, zero grey boxes)
                val receiverCont = cont.container().xy(laser.bottomX, laser.bottomY)
                receiverCont.rotation = (angleDeg + 180.0).degrees

                // Status indicator glow behind receiver's hazard stripes
                val receiverLed = receiverCont.solidRect(unitLength * 0.20, unitThickness * 0.45, Colors["#ff2244"])
                    .xy(unitLength * 0.08, 0.0)

                // Receiver housing image
                receiverCont.image(emitterBmp)
                    .size(unitLength, unitThickness)
                    .xy(0.0, -unitThickness / 2.0)

                // 3. Thinner, sharper laser beam spanning from emitter nozzle to receiver nozzle
                val coreThickness = (laser.beamThickness * 0.55).coerceIn(2.0, 4.0)
                val hazeThickness = (laser.beamThickness * 1.50).coerceIn(5.0, 10.0)

                val beamHaze = emitterCont.image(LaserFxAssets.beamBitmap)
                    .size(beamLength, hazeThickness)
                    .xy(nozzleDist, -hazeThickness / 2.0)
                beamHaze.blendMode = BlendMode.ADD
                beamHaze.alpha = 0.22

                val beamImage = emitterCont.image(LaserFxAssets.beamBitmap)
                    .size(beamLength, coreThickness)
                    .xy(nozzleDist, -coreThickness / 2.0)
                beamImage.blendMode = BlendMode.ADD

                // 4. Optical flares at emitter aperture and receiver aperture
                val emitterFlare = emitterCont.image(LaserFxAssets.flareBitmap)
                    .size(14.0, 14.0)
                    .xy(nozzleDist - 7.0, -7.0)
                emitterFlare.blendMode = BlendMode.ADD

                val receiverFlare = receiverCont.image(LaserFxAssets.flareBitmap)
                    .size(14.0, 14.0)
                    .xy(nozzleDist - 7.0, -7.0)
                receiverFlare.blendMode = BlendMode.ADD

                val receiverCorePip = receiverCont.solidRect(2.0, 2.0, Colors.WHITE)
                    .xy(nozzleDist - 1.0, -1.0)

                LaserVisual(
                    laser = laser,
                    container = cont,
                    beamImage = beamImage,
                    beamHaze = beamHaze,
                    emitterLed = emitterLed,
                    receiverLed = receiverLed,
                    emitterFlare = emitterFlare,
                    receiverFlare = receiverFlare,
                    receiverCorePip = receiverCorePip
                )
            }
        }
    }
}
