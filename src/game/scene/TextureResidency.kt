package game.scene

import korlibs.image.bitmap.Bitmap
import korlibs.image.bitmap.slice
import korlibs.image.color.RGBA
import korlibs.korge.render.RenderContext
import korlibs.korge.view.View
import korlibs.korge.view.SpriteAnimation

/**
 * Keeps the sprite atlases' GPU textures uploaded for the whole level.
 *
 * KorGE's `AgBitmapTextureManager` DELETES any texture that goes [GC_FRAMES] frames (60 - one
 * second) without being drawn, and its `maxCachedMemory` - the budget for keeping them anyway -
 * defaults to 0. The player's frames are packed into ~9 atlas pages of 2048x2048 (16.8MB each, see
 * [PlayerAnimations]), one clip per page or two, so a clip that has not played for a second loses
 * its page, and the next time it starts the whole page is uploaded again inside that frame: a
 * visible hitch, and with it a jump of the camera, which is what "when pushing the cart sometimes
 * the screen moves suddenly" was (2026-09-30) - the model's push and the camera spring are both
 * smooth at a steady frame rate (measured). Push, climb, swing, wind and jump all start on a page
 * nothing else uses, and so did the guards'.
 *
 * So this view, added once per scene: for its first [WARM_FRAMES] frames it draws a 1x1,
 * alpha-1/255 quad from every page (which is what actually uploads them - a texture is sent to
 * the GPU when first bound, during the loading screen's fade rather than mid-move), and after that
 * it asks the texture manager for each page every frame, which is KorGE's own documented way to
 * keep a bitmap resident ("call any of the getTexture* methods here each frame").
 */
class TextureResidency(animations: List<SpriteAnimation>) : View() {
    private val pages: List<Bitmap> = animations
        .flatMap { anim -> anim.sprites.map { it.base } }
        .distinct()
    private var warmFramesLeft = WARM_FRAMES

    val pageCount: Int get() = pages.size

    override fun renderInternal(ctx: RenderContext) {
        val textures = ctx.agBitmapTextureManager
        if (warmFramesLeft > 0) {
            warmFramesLeft--
            ctx.useBatcher { batch ->
                for (page in pages) {
                    batch.drawQuad(ctx.getTex(page.slice()), 0f, 0f, 1f, 1f, globalMatrix, colorMul = NEARLY_INVISIBLE)
                }
            }
        } else {
            for (page in pages) textures.getTextureBase(page)
        }
    }

    companion object {
        /** KorGE's `AgBitmapTextureManager.framesBetweenGC` default - for the doc above. */
        const val GC_FRAMES = 60

        /** Drawn on more than one frame in case the first lands before the GL context is current. */
        const val WARM_FRAMES = 3

        private val NEARLY_INVISIBLE = RGBA(255, 255, 255, 1)

        /**
         * Budget for textures nothing drew this second (level props scrolled off screen, a
         * background layer behind a cutscene) to stay uploaded instead of being re-sent when they
         * come back. Above it KorGE drops them as before. The resident atlas pages count towards
         * it, so it is sized above them (~9 player + ~1 guard pages, ~170MB) plus a level's art.
         */
        const val CACHED_TEXTURE_BUDGET_BYTES = 384L * 1024L * 1024L
    }
}
