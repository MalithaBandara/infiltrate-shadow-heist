package game.scene

import game.model.GeometryUtils
import game.model.Vec2d
import korlibs.image.bitmap.*
import korlibs.image.color.*
import korlibs.korge.render.*
import korlibs.korge.view.*
import korlibs.math.geom.*
import kotlin.math.*

/**
 * A torch beam: a guard's vision polygon drawn as light rather than as a flat shape.
 *
 * The polygon (VisionSystem.computeVisionPolygon - the lens, then the ray hits in angular order)
 * is fed to the GPU as one triangle fan with a colour per vertex, so the beam is brightest at
 * the lens and dims with distance. It does NOT fade to nothing: the fill keeps a firm floor out
 * to the very end of the range and its sides stay near full strength, so the far arc, both
 * sides and every shadow edge where geometry cuts the beam end in a visible step rather than
 * dissolving. The player reads the cone to know exactly how far and how wide the guard sees, so
 * the edge has to be as legible as the light itself (the owner's explicit ask; a soft fade
 * looked more like light and told the player nothing). No outline: one was tried on top of this
 * and rejected as too loud - the step in the fill is the edge. A second, inner ring of vertices
 * bends the fall-off into a curve (quadratic-ish rather than the straight ramp a single fan
 * would give). Shadows need nothing extra: a ray the geometry stops short still gets the
 * brightness its length earns, so a wall in the beam is lit as brightly as anything at that
 * distance would be. Drawn additively, so it lights what is under it rather than veiling it.
 *
 * Why not `Graphics`: the cones were the single most expensive thing in the scene (see
 * .junie/guidelines.md, "Device heating on Android"). `GraphicsRenderer.SYSTEM` software-rasterises
 * the whole cone into a fresh bitmap and re-uploads it as a texture every frame it changes -
 * measured at ~4MB per cone per frame at phone resolution - and `GraphicsRenderer.GPU`, for a
 * non-convex fill like a cone with shadow notches in it, renders the entire framebuffer to an
 * offscreen texture with a stencil pass, per view, per frame. This view is one batched draw of
 * a couple of hundred vertices against the 1x1 white texture: no rasterising, no stencil, no
 * offscreen buffer, and nothing allocated per frame. The vertex array is only rewritten when
 * [setBeam] is called, which GameplayScene does only when the lens, the facing or the range
 * actually changed.
 */
class LightConeView : View() {

    /** Beam colour. Alpha is the brightness at the lens; everything else is scaled from it. */
    var color: RGBA = DEFAULT_COLOR

    private var tva: TexturedVertexArray = TexturedVertexArray(0, ShortArray(0))
    private var vcount = 0
    private var icount = 0
    private var localBounds = Rectangle()

    init {
        blendMode = BlendMode.ADD
    }

    /** Nothing drawn until the next [setBeam]. */
    fun clear() {
        if (vcount == 0) return
        vcount = 0
        icount = 0
        localBounds = Rectangle()
        invalidateRender()
    }

    /**
     * Rebuilds the beam from a vision polygon.
     *
     * @param polygon VisionSystem.computeVisionPolygon's output: the origin first, then the hit
     *   points in increasing angle. World coordinates (this view sits at the world container's
     *   origin).
     * @param facingAngle the beam's centre line, radians; [fov] its full angular width.
     * @param range the ray length - where the light reaches zero.
     * @param glowRadius the small soft disc drawn over the lens itself, world units.
     */
    fun setBeam(polygon: List<Vec2d>, facingAngle: Double, fov: Double, range: Double, glowRadius: Double) {
        val rimCount = polygon.size - 1
        if (rimCount < 2 || range <= 0.0) {
            clear()
            return
        }
        val origin = polygon[0]

        // Vertex layout: [0] lens, [1..N] inner ring, [N+1..2N] rim, then the glow disc's centre
        // and its ring. Triangles: the inner fan, a quad strip out to the rim, the glow fan.
        val glowRing = GLOW_SEGMENTS
        val glowCentre = 1 + rimCount * 2
        val vertexCount = glowCentre + 1 + glowRing
        val indexCount = (rimCount - 1) * 9 + glowRing * 3
        ensureCapacity(vertexCount, indexCount)
        val tva = this.tva

        val halfFov = fov / 2.0
        val baseAlpha = color.ad
        val rgb = color
        var minX = origin.x; var maxX = origin.x; var minY = origin.y; var maxY = origin.y

        fun put(index: Int, x: Double, y: Double, alpha: Double) {
            tva.set(index, x.toFloat(), y.toFloat(), 0f, 0f, rgb.withAd(alpha.coerceIn(0.0, 1.0)))
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
        }

        put(0, origin.x, origin.y, baseAlpha)
        for (i in 0 until rimCount) {
            val p = polygon[i + 1]
            val dx = p.x - origin.x
            val dy = p.y - origin.y
            val d = sqrt(dx * dx + dy * dy)
            // A touch dimmer toward the two sides so the beam has a bright core, but never
            // below EDGE_FLOOR: the sides must stay plainly lit right up to the edge.
            val u = if (d > 1e-6) abs(GeometryUtils.angleDifference(atan2(dy, dx), facingAngle)) / halfFov else 0.0
            val edge = (1.0 - (1.0 - EDGE_FLOOR) * u.pow(4)).coerceIn(EDGE_FLOOR, 1.0)
            val rimT = (d / range).coerceIn(0.0, 1.0)
            val midD = min(d, range * INNER_RING_FRACTION)
            val midT = midD / range
            val mx = if (d > 1e-6) origin.x + dx * (midD / d) else origin.x
            val my = if (d > 1e-6) origin.y + dy * (midD / d) else origin.y
            put(1 + i, mx, my, baseAlpha * edge * radial(midT))
            put(1 + rimCount + i, p.x, p.y, baseAlpha * edge * radial(rimT))
        }

        put(glowCentre, origin.x, origin.y, baseAlpha * GLOW_ALPHA)
        for (k in 0 until glowRing) {
            val a = k * (2.0 * PI / glowRing)
            put(glowCentre + 1 + k, origin.x + cos(a) * glowRadius, origin.y + sin(a) * glowRadius, 0.0)
        }

        val idx = tva.indices
        var n = 0
        for (i in 0 until rimCount - 1) {
            val m0 = 1 + i; val m1 = m0 + 1
            val r0 = 1 + rimCount + i; val r1 = r0 + 1
            idx[n++] = 0; idx[n++] = m0.toShort(); idx[n++] = m1.toShort()
            idx[n++] = m0.toShort(); idx[n++] = r0.toShort(); idx[n++] = r1.toShort()
            idx[n++] = m0.toShort(); idx[n++] = r1.toShort(); idx[n++] = m1.toShort()
        }
        for (k in 0 until glowRing) {
            val a = glowCentre + 1 + k
            val b = glowCentre + 1 + (k + 1) % glowRing
            idx[n++] = glowCentre.toShort(); idx[n++] = a.toShort(); idx[n++] = b.toShort()
        }

        vcount = vertexCount
        icount = n
        tva.vcount = vcount
        tva.icount = icount
        localBounds = Rectangle(minX, minY, maxX - minX, maxY - minY)
        invalidateRender()
    }

    /**
     * Brightness at [t] = distance / range: full at the lens, bowed down to RANGE_FLOOR at the
     * range - and then the fill simply stops, which is what makes the far edge a real edge.
     */
    private fun radial(t: Double): Double {
        val r = 1.0 - t
        return RANGE_FLOOR + (1.0 - RANGE_FLOOR) * r * r
    }

    private fun ensureCapacity(vertexCount: Int, indexCount: Int) {
        if (vertexCount <= tva.initialVcount && indexCount <= tva.indices.size) return
        // Headroom so a corner ray or two appearing (the polygon grows with occluders in range)
        // does not reallocate every frame. Each extra rim point costs 2 vertices and 9 indices.
        tva = TexturedVertexArray(vertexCount + 32, ShortArray(indexCount + 16 * 9))
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
        /**
         * Warm torch light, additive. 0.28 at the lens: the backdrops are a pale fog, not black,
         * and additive light on them saturates fast - 0.5 read as a searchlight (owner: "too
         * bright"). Tune this and RANGE_FLOOR together if the backdrop palette changes.
         */
        val DEFAULT_COLOR: RGBA = RGBA(255, 232, 178, (0.28 * 255).toInt())
        /** Where the inner ring sits, as a fraction of the range. */
        private const val INNER_RING_FRACTION = 0.4
        /** Fill brightness kept at the far end of the range, as a fraction of the lens's. */
        private const val RANGE_FLOOR = 0.4
        /** Fill brightness kept along the two sides, as a fraction of the centre line's. */
        private const val EDGE_FLOOR = 0.7
        private const val GLOW_SEGMENTS = 12
        private const val GLOW_ALPHA = 0.9
    }
}
