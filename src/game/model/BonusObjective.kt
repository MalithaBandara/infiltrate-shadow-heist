package game.model

/**
 * A level's own optional objective - star 2 on the results card ([LevelResult.star2]) and the
 * middle row of the HUD's objectives block. Levels without one ([LevelData.bonusObjective] null)
 * keep the old star 2, "no alerts raised".
 *
 * Two kinds: something to DO ([isAchievement] - met the moment it is done, missed if the exit comes
 * first) and something to AVOID (lost the moment it happens, met at the exit).
 */
enum class BonusObjective(val isAchievement: Boolean) {
    /** Level 1 - do each of the three basic moves at least once: a jump, a climb and a crouch. */
    BASIC_MOVES(isAchievement = true),

    /**
     * Level 2 - never come down from a hanging crate to the yard floor. Walking off solid terrain
     * to the floor is fine; landing at floor level (a rescue barrel counts) when the last thing
     * stood on was a hanging crate or moving container is not.
     */
    NO_DROP_FROM_HANGING_CRATES(isAchievement = false),

    /** Levels 3 and 6 - no guard or camera ever starts filling the alert meter. */
    STAY_UNSEEN(isAchievement = false),

    /** Level 4 - set off any gadget from the tray. */
    USE_A_GADGET(isAchievement = true),

    /** Level 5 - swing from a hook. */
    SWING_FROM_A_HOOK(isAchievement = true),

    /** Level 7 - every patrol bot switched off at some point in the attempt. */
    DISABLE_ALL_SECURITY_BOTS(isAchievement = true),

    /** Level 8 - the body never touches a hanging load: not on top, not side-on, not from below. */
    NEVER_TOUCH_A_HANGING_CRATE(isAchievement = false),

    /** Level 9 - the echo (level 8's replayed run) never has the player in its cone. */
    STAY_OUT_OF_THE_FIGURES_SIGHT(isAchievement = false),

    /**
     * Level 10 - no switch is thrown a second time: the door and the lift at the end are each
     * worked once, the first time right (2026-09-30: "asking not to press the same button twice").
     */
    USE_EACH_SWITCH_ONCE(isAchievement = false),

    /**
     * Level 11 - nothing with eyes ever has the prisoner in sight, not even for the moment it
     * takes to start noticing him (the main objective only fails once the meter fills).
     */
    KEEP_THE_PRISONER_OUT_OF_SIGHT(isAchievement = false)
}

/** 0 open, 1 met, 2 out of reach - the same three states the HUD's objective marks draw. */
enum class ObjectiveState { OPEN, MET, FAILED }

/**
 * Follows one run's [BonusObjective]. [GameWorld] feeds it once per tick with what the body is
 * doing; a failure is final for the attempt (a checkpoint respawn does not clear it, nor undo
 * progress), and only a full restart ([reset]) opens it again.
 */
class BonusObjectiveTracker(val objective: BonusObjective) {
    var hasJumped: Boolean = false
        private set
    var hasClimbed: Boolean = false
        private set
    var hasCrouched: Boolean = false
        private set
    var hasUsedGadget: Boolean = false
        private set
    var hasSwung: Boolean = false
        private set
    /** Bots switched off this attempt - a respawn switches them back on, but the credit stays. */
    val disabledBots: MutableSet<String> = mutableSetOf()
    var isFailed: Boolean = false
        private set
    private var isAchieved: Boolean = false

    // A level can start the body crouched; only going INTO a crouch counts as doing one. Starting
    // "was crouching" covers both: a standing start clears it on the first tick.
    private var wasCrouching: Boolean = true
    private var lastStoodOnHangingCrate: Boolean = false

    fun state(levelComplete: Boolean): ObjectiveState = when {
        isFailed -> ObjectiveState.FAILED
        isAchieved -> ObjectiveState.MET
        !levelComplete -> ObjectiveState.OPEN
        objective.isAchievement -> ObjectiveState.FAILED
        else -> ObjectiveState.MET
    }

    fun reset() {
        hasJumped = false
        hasClimbed = false
        hasCrouched = false
        hasUsedGadget = false
        hasSwung = false
        disabledBots.clear()
        isFailed = false
        isAchieved = false
        onRespawn()
    }

    /** A respawn puts the body somewhere new - it has not just left a hanging crate. */
    fun onRespawn() {
        wasCrouching = true
        lastStoodOnHangingCrate = false
    }

    fun onGadgetUsed() {
        hasUsedGadget = true
        if (objective == BonusObjective.USE_A_GADGET) isAchieved = true
    }

    fun observe(
        isJumping: Boolean = false,
        isClimbing: Boolean = false,
        isCrouching: Boolean = false,
        isSwinging: Boolean = false,
        isGrounded: Boolean = true,
        onHangingCrate: Boolean = false,
        touchingHangingCrate: Boolean = false,
        atFloorLevel: Boolean = false,
        alertRaised: Boolean = false,
        seenByFigure: Boolean = false,
        switchReused: Boolean = false,
        prisonerSeen: Boolean = false,
        deactivatedBots: Collection<String> = emptyList(),
        botCount: Int = 0
    ) {
        if (isFailed || isAchieved) return
        when (objective) {
            BonusObjective.BASIC_MOVES -> {
                if (isJumping) hasJumped = true
                if (isClimbing) hasClimbed = true
                if (isCrouching && !wasCrouching) hasCrouched = true
                wasCrouching = isCrouching
                if (hasJumped && hasClimbed && hasCrouched) isAchieved = true
            }
            BonusObjective.NO_DROP_FROM_HANGING_CRATES -> {
                if (!isGrounded) return
                // A vertical container can carry a body down close to the floor - riding it
                // there is still standing on it.
                if (atFloorLevel && !onHangingCrate && lastStoodOnHangingCrate) isFailed = true
                lastStoodOnHangingCrate = onHangingCrate
            }
            BonusObjective.STAY_UNSEEN -> if (alertRaised) isFailed = true
            BonusObjective.USE_A_GADGET -> if (hasUsedGadget) isAchieved = true
            BonusObjective.SWING_FROM_A_HOOK -> if (isSwinging) { hasSwung = true; isAchieved = true }
            BonusObjective.DISABLE_ALL_SECURITY_BOTS -> {
                disabledBots.addAll(deactivatedBots)
                if (botCount > 0 && disabledBots.size >= botCount) isAchieved = true
            }
            BonusObjective.NEVER_TOUCH_A_HANGING_CRATE -> if (touchingHangingCrate) isFailed = true
            BonusObjective.STAY_OUT_OF_THE_FIGURES_SIGHT -> if (seenByFigure) isFailed = true
            BonusObjective.USE_EACH_SWITCH_ONCE -> if (switchReused) isFailed = true
            BonusObjective.KEEP_THE_PRISONER_OUT_OF_SIGHT -> if (prisonerSeen) isFailed = true
        }
    }
}
