package game.scene

import game.model.Vec2d
import korlibs.image.bitmap.*
import korlibs.image.color.*
import korlibs.korge.render.*
import korlibs.korge.view.*
import korlibs.math.geom.*
import kotlin.math.*

/**
 * A security camera's vision cone: its vision polygon filled with one flat colour - the look the
 * cameras always had - drawn on the GPU instead of through a software-rasterised `Graphics`.
 *
 * What it replaces: `worldView.graphics()` (GraphicsRenderer.SYSTEM, no auto-scaling) filled the
 * polygon into a bitmap at one pixel per world unit, anti-aliased, and re-uploaded it as a
 * texture on every frame the cone changed - every frame of a camera's sweep. The world zoom and
 * the screen then stretch that bitmap ~4x (worldZoom 1.35 x ~3 device px per unit on a 1440p
 * phone), so its edge was never sharp: a 1-unit anti-aliased step, bilinear-filtered, is a soft
 * ramp a world unit or two wide. A plain triangle fan would have a hard edge instead.
 *
 * So the fill is drawn as two parts from the same polygon: the solid fan, and a band [FEATHER]
 * wide around the outline that fades from the fill colour to nothing. The band is centred on the
 * outline where it can be: the far edge and every face a ray stops on are pulled in by half of
 * it along their rays (which keeps the hits in angular order from the lens, so the fan never
 * overlaps itself), and so are the two sides. A shadow's side runs along a ray, so pulling along
 * rays cannot move it: there the band sits outside the outline, half a band - half a world unit,
 * ~2 px on a phone - further out than the old edge; the old bitmap's own pixel grid moved its
 * edges by as much as it slid with the sweep. Detail finer than the band is smoothed away first
 * (see setPolygon) - the old blur hid it, and the band would fold over it.
 *
 * FlatConeViewTest checks every camera of every level across its sweep against an emulation of
 * the old bitmap: identical fill away from the edge, never brighter than the fill, and [FEATHER]
 * is the width that matched the old edge best (mean difference 0.022 alpha at the edge, against
 * a fill of 0.32).
 *
 * Normal blend, the way the old bitmap was composited; vertex colours are the fill colour with
 * the band's alpha, against the 1x1 white texture (same draw path as [LightConeView]). Nothing
 * is allocated per frame: the vertex array is rewritten only by [setPolygon], which
 * GameplayScene calls only when the lens, facing, range, fov or occluders changed.
 */
class FlatConeView : View() {

    /** The fill. The old cones were white at 0.32. */
    var color: RGBA = Colors.WHITE.withAd(0.32)

    private var tva: TexturedVertexArray = TexturedVertexArray(0, ShortArray(0))
    private var vcount = 0
    private var icount = 0
    private var localBounds = Rectangle()

    // Scratch, grown as needed and reused: the outline, its outward normals and the band's outer ring.
    private var ox = DoubleArray(0)
    private var oy = DoubleArray(0)
    private var nx = DoubleArray(0)
    private var ny = DoubleArray(0)

    /** Nothing drawn until the next [setPolygon]. */
    fun clear() {
        if (vcount == 0) return
        vcount = 0
        icount = 0
        localBounds = Rectangle()
        invalidateRender()
    }

    /**
     * Rebuilds the cone from a vision polygon: VisionSystem.computeVisionPolygon's output, the
     * lens first and then the ray hits in increasing angle, in world coordinates (this view sits
     * at the world container's origin). Fewer than two hits draws nothing - the old fill of such
     * a polygon had no area either.
     */
    fun setPolygon(polygon: List<Vec2d>) {
        if (polygon.size < 3) {
            clear()
            return
        }
        val lensX = polygon[0].x
        val lensY = polygon[0].y
        val half = FEATHER / 2.0

        // The outline: the lens, then the hits. Repeated points dropped, so every edge has a
        // direction.
        ensureScratch(polygon.size)
        var m = 0
        fun add(x: Double, y: Double) {
            if (m > 0 && abs(x - ox[m - 1]) < 1e-9 && abs(y - oy[m - 1]) < 1e-9) return
            ox[m] = x; oy[m] = y; m++
        }
        for (p in polygon) add(p.x, p.y)
        if (m > 2 && abs(ox[m - 1] - ox[0]) < 1e-9 && abs(oy[m - 1] - oy[0]) < 1e-9) m--

        // Detail finer than the band, smoothed out of the outline: every notch and every sliver
        // of shadow whose mouth is narrower than two bands (a run of hits that leaves and comes
        // back to within that of where it left becomes the straight line across), and every hit
        // within half a band of the one before it (a shadow's corner rays come in threes, a
        // hundredth of a unit apart). The old bitmap blurred detail that small away anyway; left
        // in, the band would fold over itself there and draw brighter than the fill. The hits stay
        // in angular order, so the fan still never overlaps itself. The last hit - the far end
        // of the cone's second side - is always kept.
        val mouth2 = (FEATHER * 2) * (FEATHER * 2)
        val near2 = (FEATHER / 2) * (FEATHER / 2)
        var w = 1
        var r = 1
        while (r < m) {
            if (w > 1 && r < m - 1) {
                val dx = ox[r] - ox[w - 1]
                val dy = oy[r] - oy[w - 1]
                if (dx * dx + dy * dy < near2) { r++; continue }
            }
            var cut = -1
            var j = min(r + NOTCH_SPAN, m - 1)
            while (j >= r + 2) {
                val dx = ox[j] - ox[r]
                val dy = oy[j] - oy[r]
                if (dx * dx + dy * dy < mouth2) { cut = j; break }
                j--
            }
            ox[w] = ox[r]; oy[w] = oy[r]; w++
            r = if (cut > 0) cut else r + 1
        }
        m = w
        if (m < 3) {
            clear()
            return
        }

        // The band is centred on the outline: the hits pulled in toward the lens by half of it (a
        // hit closer than a band's width is left where it is).
        for (i in 1 until m) {
            val dx = ox[i] - lensX
            val dy = oy[i] - lensY
            val d = sqrt(dx * dx + dy * dy)
            if (d > FEATHER) {
                val k = (d - half) / d
                ox[i] = lensX + dx * k
                oy[i] = lensY + dy * k
            }
        }

        // Which way is out: the hits run in increasing angle, which is one winding or the other
        // depending on the y axis; the signed area says which.
        var area2 = 0.0
        for (i in 0 until m) {
            val j = if (i + 1 == m) 0 else i + 1
            area2 += ox[i] * oy[j] - ox[j] * oy[i]
        }
        if (abs(area2) < 1e-9) {
            clear()
            return
        }
        val outSign = if (area2 > 0.0) 1.0 else -1.0

        // The two sides in by half the band too: the first and last hits, square to their side.
        insetSide(1, 2, lensX, lensY, outSign, half, first = true)
        insetSide(m - 1, m - 2, lensX, lensY, outSign, half, first = false)

        // Outward normal at each outline vertex: the bisector of its two edges' normals, scaled
        // so the band keeps its width along both edges (a mitre), capped at twice that for a
        // sharp corner.
        for (i in 0 until m) {
            val p = if (i == 0) m - 1 else i - 1
            val n = if (i + 1 == m) 0 else i + 1
            var ax = (oy[i] - oy[p]) * outSign; var ay = -(ox[i] - ox[p]) * outSign
            var bx = (oy[n] - oy[i]) * outSign; var by = -(ox[n] - ox[i]) * outSign
            val al = sqrt(ax * ax + ay * ay); ax /= al; ay /= al
            val bl = sqrt(bx * bx + by * by); bx /= bl; by /= bl
            var sx = ax + bx; var sy = ay + by
            val sl = sqrt(sx * sx + sy * sy)
            if (sl < 1e-6) { sx = ax; sy = ay } else { sx /= sl; sy /= sl }
            val cos = max(sx * ax + sy * ay, 0.5)
            nx[i] = sx / cos; ny[i] = sy / cos
        }

        // Vertices: [0, m) the outline at the fill colour, [m, 2m) the band's outer ring at alpha 0.
        // Triangles: the fan from the lens, then a quad per outline edge out to the ring.
        val vertexCount = m * 2
        val indexCount = (m - 2) * 3 + m * 6
        ensureCapacity(vertexCount, indexCount)
        val tva = this.tva
        val solid = color
        val clear = color.withAd(0.0)
        var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        for (i in 0 until m) {
            tva.set(i, ox[i].toFloat(), oy[i].toFloat(), 0f, 0f, solid)
            val x = ox[i] + nx[i] * FEATHER
            val y = oy[i] + ny[i] * FEATHER
            tva.set(m + i, x.toFloat(), y.toFloat(), 0f, 0f, clear)
            minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
        }
        val idx = tva.indices
        var k = 0
        for (i in 1 until m - 1) {
            idx[k++] = 0; idx[k++] = i.toShort(); idx[k++] = (i + 1).toShort()
        }
        for (i in 0 until m) {
            val j = if (i + 1 == m) 0 else i + 1
            idx[k++] = i.toShort(); idx[k++] = j.toShort(); idx[k++] = (m + j).toShort()
            idx[k++] = i.toShort(); idx[k++] = (m + j).toShort(); idx[k++] = (m + i).toShort()
        }
        vcount = vertexCount
        icount = k
        tva.vcount = vcount
        tva.icount = icount
        localBounds = Rectangle(minX, minY, maxX - minX, maxY - minY)
        invalidateRender()
    }

    /**
     * Moves the side's far vertex [i] (the first hit, or the last) square to the side, into the
     * cone, by [half] - unless the side is too short, or that would carry it past its neighbour
     * [next] in angle (the fan would then fold over itself).
     */
    private fun insetSide(i: Int, next: Int, lensX: Double, lensY: Double, outSign: Double, half: Double, first: Boolean) {
        if (next < 1 || next == i) return
        val dx = ox[i] - lensX
        val dy = oy[i] - lensY
        val d = sqrt(dx * dx + dy * dy)
        if (d <= FEATHER * 2) return
        // The side's outward normal: for the first side the edge runs lens -> hit, for the last
        // hit -> lens; inward is the opposite.
        val ex = if (first) dx else -dx
        val ey = if (first) dy else -dy
        val nx = ox[i] - ey * outSign / d * half
        val ny = oy[i] + ex * outSign / d * half
        fun side(px: Double, py: Double) = (px - lensX) * (oy[next] - lensY) - (py - lensY) * (ox[next] - lensX)
        if (sign(side(ox[i], oy[i])) != sign(side(nx, ny))) return
        ox[i] = nx
        oy[i] = ny
    }

    private fun ensureScratch(n: Int) {
        if (ox.size >= n) return
        val size = n + 32
        ox = DoubleArray(size); oy = DoubleArray(size); nx = DoubleArray(size); ny = DoubleArray(size)
    }

    private fun ensureCapacity(vertexCount: Int, indexCount: Int) {
        if (vertexCount <= tva.initialVcount && indexCount <= tva.indices.size) return
        // Headroom so a corner ray or two appearing does not reallocate every frame.
        tva = TexturedVertexArray(vertexCount + 32, ShortArray(indexCount + 16 * 9))
    }

    /** The drawn triangles, as (x, y, alpha) per corner, for tests. */
    internal fun trianglesForTesting(): List<List<Triple<Double, Double, Double>>> {
        val out = ArrayList<List<Triple<Double, Double, Double>>>()
        for (t in 0 until icount / 3) {
            out += (0 until 3).map { c ->
                tva.select(tva.indices[t * 3 + c].toInt())
                Triple(tva.x.toDouble(), tva.y.toDouble(), RGBA(tva.cMul).ad)
            }
        }
        return out
    }

    override fun renderInternal(ctx: RenderContext) {
        if (icount == 0) return
        ctx.useBatcher { batch ->
            batch.drawVertices(
                tva, ctx.getTex(Bitmaps.white).base, smoothing = false, blendMode = renderBlendMode,
                vcount = vcount, icount = icount, matrix = globalMatrix,
            )
        }
    }

    override fun getLocalBoundsInternal(): Rectangle = localBounds

    companion object {
        /** Width of the fading edge, world units - see the class comment. */
        const val FEATHER = 1.0
        /** The most hits a closed notch can span. */
        private const val NOTCH_SPAN = 12
    }
}
