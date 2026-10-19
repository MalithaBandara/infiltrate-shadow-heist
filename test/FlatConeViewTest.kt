package test

import game.model.GameWorld
import game.model.LevelData
import game.model.Vec2d
import game.model.VisionSystem
import game.scene.FlatConeView
import kotlin.math.*
import kotlin.random.Random
import kotlin.test.*

/**
 * FlatConeView draws a camera's vision polygon the way the software-rasterised Graphics it
 * replaced did: the same flat fill everywhere inside, nothing outside, and an edge that fades
 * over FEATHER world units around the outline instead of a hard step. Checked on every camera of
 * every level, across its whole sweep, by sampling points against the triangles it would draw.
 */
class FlatConeViewTest {

    private val fill = 0.32
    private val feather = FlatConeView.FEATHER
    /** How far either side of the outline counts as the edge: the old ramp's reach, and the band's. */
    private val edge = max(2.0, feather * 2)

    private class Tri(val ax: Double, val ay: Double, val aa: Double, val bx: Double, val by: Double, val ba: Double, val cx: Double, val cy: Double, val ca: Double)

    /** The alpha the GPU puts at (x, y) - normal blend, triangle by triangle - and how many triangles cover it. */
    private fun shade(tris: List<Tri>, x: Double, y: Double): Pair<Double, Int> {
        var alpha = 0.0
        var hits = 0
        for (t in tris) {
            val d = (t.by - t.cy) * (t.ax - t.cx) + (t.cx - t.bx) * (t.ay - t.cy)
            if (abs(d) < 1e-12) continue
            val l1 = ((t.by - t.cy) * (x - t.cx) + (t.cx - t.bx) * (y - t.cy)) / d
            val l2 = ((t.cy - t.ay) * (x - t.cx) + (t.ax - t.cx) * (y - t.cy)) / d
            val l3 = 1.0 - l1 - l2
            if (l1 < -1e-9 || l2 < -1e-9 || l3 < -1e-9) continue
            val a = l1 * t.aa + l2 * t.ba + l3 * t.ca
            alpha = a + alpha * (1.0 - a)
            hits++
        }
        return alpha to hits
    }

    private fun inside(poly: List<Vec2d>, x: Double, y: Double): Boolean {
        var c = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[j]
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) c = !c
            j = i
        }
        return c
    }

    private fun distanceToOutline(poly: List<Vec2d>, x: Double, y: Double): Double {
        var best = Double.MAX_VALUE
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[(i + 1) % poly.size]
            val dx = b.x - a.x; val dy = b.y - a.y
            val len2 = dx * dx + dy * dy
            val t = if (len2 < 1e-12) 0.0 else (((x - a.x) * dx + (y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
            best = min(best, hypot(x - (a.x + t * dx), y - (a.y + t * dy)))
        }
        return best
    }

    /**
     * What the old cone showed at a point: the polygon filled, anti-aliased, into a bitmap at one
     * pixel per world unit (Graphics without auto-scaling) - each pixel's coverage taken from 4x4
     * subsamples - then stretched onto the screen with bilinear filtering.
     */
    private inner class OldCone(val poly: List<Vec2d>) {
        private val cache = HashMap<Long, Double>()

        private fun coverage(i: Int, j: Int): Double = cache.getOrPut(i.toLong() shl 32 or (j.toLong() and 0xffffffffL)) {
            val cx = i + 0.5
            val cy = j + 0.5
            if (distanceToOutline(poly, cx, cy) > 0.75) {
                if (inside(poly, cx, cy)) 1.0 else 0.0
            } else {
                var n = 0
                for (a in 0 until 4) for (b in 0 until 4) if (inside(poly, i + (a + 0.5) / 4, j + (b + 0.5) / 4)) n++
                n / 16.0
            }
        }

        fun alphaAt(x: Double, y: Double): Double {
            val fx = x - 0.5
            val fy = y - 0.5
            val i = floor(fx).toInt()
            val j = floor(fy).toInt()
            val tx = fx - i
            val ty = fy - j
            val top = coverage(i, j) * (1 - tx) + coverage(i + 1, j) * tx
            val bottom = coverage(i, j + 1) * (1 - tx) + coverage(i + 1, j + 1) * tx
            return fill * (top * (1 - ty) + bottom * ty)
        }
    }

    @Test
    fun everyCameraConeIsTheSameFillWithASoftEdge() {
        val view = FlatConeView()
        val random = Random(7)
        var cones = 0
        val diffs = ArrayList<Double>()
        var worstCorner = 0.0
        for (level in (LevelData.DEFAULT_LEVELS + LevelData.DEV_LEVELS).distinct()) {
            val world = GameWorld.createDefault(level)
            for (camera in world.cameras) {
                for (step in 0..8) {
                    camera.currentAngle = camera.minAngle + (camera.maxAngle - camera.minAngle) * step / 8.0
                    val poly = VisionSystem.computeVisionPolygon(
                        camera.eyePosition, camera.facingAngle, camera.visionRange, camera.visionFov, world.visionOccluders
                    )
                    view.setPolygon(poly)
                    val tris = view.trianglesForTesting().map { t ->
                        Tri(t[0].first, t[0].second, t[0].third, t[1].first, t[1].second, t[1].third, t[2].first, t[2].second, t[2].third)
                    }
                    assertTrue(tris.isNotEmpty(), "${level.id}: a camera cone drew nothing")
                    cones++
                    val old = OldCone(poly)
                    val minX = poly.minOf { it.x } - 3; val maxX = poly.maxOf { it.x } + 3
                    val minY = poly.minOf { it.y } - 3; val maxY = poly.maxOf { it.y } + 3
                    repeat(1500) {
                        val x = minX + random.nextDouble() * (maxX - minX)
                        val y = minY + random.nextDouble() * (maxY - minY)
                        val d = distanceToOutline(poly, x, y)
                        val (alpha, hits) = shade(tris, x, y)
                        val isIn = inside(poly, x, y)
                        if (d > edge) {
                            // Well clear of the edge: exactly the old flat fill, or nothing.
                            if (isIn) {
                                assertEquals(1, hits, "${level.id}: ($x, $y) inside the cone is drawn $hits times")
                                assertEquals(fill, alpha, 1.0 / 255, "${level.id}: ($x, $y) inside the cone")
                            } else if (d > edge + 0.5) {
                                // A shadow's side has the whole band outside it, and the band's
                                // mitre at a sharp corner reaches further: at most twice its width.
                                assertEquals(0, hits, "${level.id}: ($x, $y) outside the cone is drawn")
                            } else {
                                worstCorner = max(worstCorner, alpha)
                            }
                        } else {
                            // In the edge: never brighter than the fill, and close to what the old
                            // bitmap showed there.
                            assertTrue(alpha <= fill + 1.0 / 255, "${level.id}: ($x, $y) on the edge is brighter than the fill ($alpha)")
                            // The tip at the lens is under the camera's own lens art.
                            if (hypot(x - poly[0].x, y - poly[0].y) < 3.0) return@repeat
                            val diff = abs(alpha - old.alphaAt(x, y))
                            diffs += diff
                        }
                    }
                }
            }
        }
        assertTrue(cones > 50, "only $cones cones checked")
        diffs.sort()
        val mean = diffs.average()
        val p99 = diffs[(diffs.size * 0.99).toInt().coerceAtMost(diffs.size - 1)]
        println(
            "[FlatCone] $cones cones, ${diffs.size} edge samples; alpha vs the old bitmap: mean ${"%.4f".format(mean)}, " +
                "p99 ${"%.4f".format(p99)}, max ${"%.4f".format(diffs.last())}; " +
                "brightest point past a corner: ${"%.3f".format(worstCorner)} (fill $fill)"
        )
        // Within a tenth of the fill on average, at the edge only (0.022 when FEATHER was tuned).
        assertTrue(mean < fill * 0.1, "the edge strays from the old bitmap by $mean on average")
    }
}
