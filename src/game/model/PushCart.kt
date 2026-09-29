package game.model

/**
 * Declarative definition of a flatbed cart the player can walk along - the first real thing the
 * braced push stance has to push (see [GameWorld.updatePushStance]).
 *
 * The cart is a step, not a puzzle piece: it parks somewhere useless, and the way onto whatever it
 * is parked short of is to walk it there. Everything about the geometry is therefore stated in
 * terms of the climb it has to set up, not in terms of the art - see [LevelData.LEVEL_8_LAYOUT],
 * where [maxX] is exactly flush against the platform's face and [height] is the same 48 the fixed
 * step crate it replaced used, so the hop-on and the mantle off the top are the numbers that were
 * already tuned there.
 *
 * @param surfaceY the ground line the wheels stand on; the cart's own top is [height] above it.
 * @param minX     how far back it can be dragged, and [maxX] how far forward it can be shoved.
 *                 Both are limits on the CART, and [GameWorld] turns them into limits on the
 *                 player while he has hold of it - he is locked to it, so they are the same thing.
 */
data class PushCartDef(
    val id: String,
    val initialX: Double,
    val surfaceY: Double,
    val width: Double,
    val height: Double,
    val minX: Double,
    val maxX: Double,
    /**
     * Starts as a bare flatbed with nothing on the deck (LEVEL_8_LAYOUT's second cart). Until a
     * [HookCrate] comes down in it the cart only collides up to its deck, so it is no step at all -
     * the handle posts are drawn but pass-through. Once loaded it is the same full-height block as
     * a cart that started loaded.
     */
    val startsEmpty: Boolean = false
)

/**
 * Runtime state of a [PushCartDef]. Position only: the cart has no motion of its own, it is
 * wherever the last person to hold it left it.
 *
 * Its [bounds] are the whole footprint, wheels to handle tops, and they are solid - there is
 * deliberately no crawling under the deck. GameWorld feeds them in as a dynamic platform the same
 * way a MovingPlatform's are, with one exception: the cart being HELD is taken back out of the
 * player's own collision list, because he is pinned to it at a fixed offset and would otherwise be
 * walking into the thing he is pushing every frame.
 */
class PushCart(private val def: PushCartDef) {
    val id: String get() = def.id
    val width: Double get() = def.width
    val height: Double get() = def.height
    val minX: Double get() = def.minX
    val maxX: Double get() = def.maxX

    var x: Double = def.initialX

    val startsEmpty: Boolean get() = def.startsEmpty

    /** False while an empty cart still has nothing on its deck - see [PushCartDef.startsEmpty]. */
    var isLoaded: Boolean = !def.startsEmpty

    /** Top of the ART (handle tops), where the scene draws it. Wheels on the ground. */
    val y: Double get() = def.surfaceY - def.height

    /** Rolling speed after a crate's impact - see GameWorld.rollCarts. Zero while held. */
    var vx: Double = 0.0

    /** The deck as a crate meets it: the flat bed between the wheels, post to post. */
    val deckRect: Rect
        get() = Rect(
            x + def.width * POST_LEFT_FRACTION, deckY,
            def.width * (POST_RIGHT_FRACTION + POST_WIDTH_FRACTION - POST_LEFT_FRACTION), def.surfaceY - deckY
        )

    /** The two handle posts above the deck, as a crate meets them. */
    val postRects: List<Rect>
        get() = listOf(
            Rect(x + def.width * POST_LEFT_FRACTION, y, def.width * POST_WIDTH_FRACTION, deckY - y),
            Rect(x + def.width * POST_RIGHT_FRACTION, y, def.width * POST_WIDTH_FRACTION, deckY - y)
        )

    /** The deck's top surface, in world y - where a load sits. */
    val deckY: Double get() = y + def.height * DECK_TOP_FRACTION

    /**
     * What a BODY collides with. Loaded, the whole cart ([bounds]). Empty, the deck plus the two
     * handle posts up to [EMPTY_POST_SOLID_HEIGHT] over it: before, it was the deck alone, and a
     * body walked straight through the handles ("right now i can just go through the empty cart.
     * i should be able to jump inside it but not go through it"). Not the posts' full drawn
     * height - 32 over a 16 deck is 48 off the floor, which a jump (51.2) clears by too little to
     * get a 36-wide body over a post; at 12 over the deck (28 off the floor) a running jump drops
     * in, and one from the deck climbs out. Not 14: a post top 14 up is 114 under LEVEL_8's high
     * platform - inside a climb (115) - and the empty cart became a step onto it. A falling crate still meets the full posts
     * ([postRects]).
     */
    val playerSolids: List<Rect>
        get() = if (isLoaded) listOf(bounds) else listOf(bounds) + postRects.map {
            Rect(it.x, deckY - EMPTY_POST_SOLID_HEIGHT, it.width, EMPTY_POST_SOLID_HEIGHT)
        }

    /**
     * What a load coming down can actually hit: the whole cart once loaded; empty, the deck and
     * the two handle posts - NOT the open space between the posts above the deck, which the art
     * rect includes. A bob coming down into that space touches nothing until it reaches the deck
     * (2026-09-29: "sometimes game ends ... even though me or the cart [do not touch] the hanging
     * crates"). Post to post: the art rect also runs past the posts at both ends, over nothing.
     */
    val crushParts: List<Rect>
        get() {
            val deck = deckRect
            return if (isLoaded) listOf(Rect(deck.x, y, deck.width, def.surfaceY - y)) else listOf(deck) + postRects
        }

    /** The solid footprint: the full height once loaded, only up to the deck while empty. */
    val bounds: Rect
        get() = if (isLoaded) Rect(x, y, def.width, def.height)
        else Rect(x, deckY, def.width, def.surfaceY - deckY)

    /**
     * Where a braced hand takes hold, in world x: the near handle's upright, at the height the
     * push pose's fist actually reaches.
     *
     * The height it is read at is the drawn fist's, [BRACED_FIST_HEIGHT_PER_HEIGHT] of
     * [Player.visualHeight] off the ground - 44.7 units against this cart's 48, which is the
     * corner where the upright turns into the curved grip. The pose is a fixed plate, so that
     * height is not adjustable except through [Player.VISUAL_HEIGHT_SCALE], which is exactly
     * what was raised to reach this corner.
     */
    fun handleGripX(fromLeft: Boolean): Double =
        x + width * (if (fromLeft) HANDLE_GRIP_FRACTION else 1.0 - HANDLE_GRIP_FRACTION)

    /** Where the load sits: between the handle posts, filling the deck up to the handle tops. */
    val loadBounds: Rect
        get() = Rect(
            x = x + def.width * LOAD_LEFT_FRACTION,
            y = y,
            width = def.width * (LOAD_RIGHT_FRACTION - LOAD_LEFT_FRACTION),
            height = def.height * DECK_TOP_FRACTION
        )

    /**
     * Whether the player is standing beside this cart, on the ground, close enough to take hold.
     *
     * The vertical test is what stops someone who has climbed ONTO the cart from grabbing it out
     * from under themselves: up there the feet are at the cart's own top, and a braced body that
     * cannot jump or crouch would have no way back down off a cart it was also dragging.
     */
    fun canGrip(player: Player): Boolean {
        if (!player.isGrounded) return false
        val p = player.bounds
        if (p.bottom <= bounds.top + 4.0) return false
        if (p.top >= bounds.bottom) return false
        val fromLeft = p.right >= bounds.left - GRIP_REACH && p.right <= bounds.left + 2.0
        val fromRight = p.left <= bounds.right + GRIP_REACH && p.left >= bounds.right - 2.0
        return fromLeft || fromRight
    }

    fun reset() {
        x = def.initialX
        vx = 0.0
        isLoaded = !def.startsEmpty
    }

    companion object {
        /** How far over the deck an empty cart's handle posts stop a body - see [playerSolids]. */
        const val EMPTY_POST_SOLID_HEIGHT = 12.0

        /** How far short of the cart's face the grab still registers, in world units. */
        const val GRIP_REACH = 22.0

        // --- cart.png's own proportions, measured off the shipped 512x256 file on alpha > 90 ---
        // (deck rows 171..200 of 256; handle posts spanning 0.0742..0.1113 and 0.8867..0.9238 of
        // the width). They are fractions rather than pixels so the art can be re-sized without
        // any of this moving - see ".junie/guidelines.md" -> "Adding new art".

        /** Top of the deck, as a fraction of the art's height measured DOWN from its top edge. */
        const val DECK_TOP_FRACTION = 0.668

        /** A cart weighs this many crates - see GameWorld.updateLooseCrate. */
        const val MASS = 2.0

        /** How fast a rolling cart that nobody is holding slows down, units/s per second. */
        const val ROLLING_FRICTION = 160.0

        /** Outer face of each handle post, and a post's width (the inner faces are the LOAD fractions). */
        const val POST_LEFT_FRACTION = 0.0742
        const val POST_RIGHT_FRACTION = 0.8867
        const val POST_WIDTH_FRACTION = 0.0371

        /** Inner faces of the two handle posts - the span a load on the deck fills. */
        const val LOAD_LEFT_FRACTION = 0.1113
        const val LOAD_RIGHT_FRACTION = 0.8867

        /**
         * Where the hand closes on the LEFT handle, as a fraction of the art's width. Mirrored
         * for the right handle.
         *
         * Read off the art AT HAND HEIGHT, so it moves with [Player.VISUAL_HEIGHT_SCALE]: the
         * drawn fist sits 44.7 units up against this cart's 48, which is row 18 of cart.png's
         * 256, where the handle spans 0.0488..0.1055 - the corner, the row at which the upright
         * starts turning into the curved grip. 0.0771 is the middle of that. At a scale of 1.0
         * the hand was 5.5 units lower, on the plain upright at 0.0918.
         */
        const val HANDLE_GRIP_FRACTION = 0.0771

        /**
         * How far forward of the body's centre the braced fist reaches, and how far off the
         * ground it sits, both as fractions of the character's height.
         *
         * Measured on `resources/player/push/0001.png` - [PlayerAnimations.PUSH_REST_FRAME], the
         * pose a braced body holds - by taking the rightmost 14 columns of the silhouette (the
         * fist alone; the forearm runs back from there) and its centroid, at (169.2, 147.5) in a
         * 180x256 frame whose feet sit on row 255.76. The sprite is anchored at the frame's
         * horizontal centre and scaled by height/244.36, so 79.2px forward of centre and 108.3px
         * above the feet become 0.3241 and 0.4430 of the character's height.
         *
         * They live here rather than in PlayerAnimations because `src/game/model` may not depend
         * on the scene (see ZeroKorlibsLintTest); re-measure them if the push plate is re-cut.
         */
        const val BRACED_FIST_REACH_PER_HEIGHT = 0.3241
        const val BRACED_FIST_HEIGHT_PER_HEIGHT = 0.4430
    }
}
