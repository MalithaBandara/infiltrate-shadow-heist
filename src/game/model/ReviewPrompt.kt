package game.model

/**
 * When the game asks for a store review on its own (the StoreKit / Play in-app prompt): once
 * ever, at the first completed level from the moment level 3 has been completed.
 *
 * It used to ask on every completion of level 3 (level 4 before 2026-11-12), with no memory of
 * having asked. Moving the trigger from 4 to 3 left out every player who had finished level 3 on
 * an older build and not yet level 4: their level 3 came before the prompt existed there, and
 * level 4 no longer asked, so they were never asked unless they replayed level 3. Now:
 * - a new player is asked on finishing level 3, as before;
 * - a player who finished level 3 earlier is asked on the next level they finish, whichever;
 * - nobody is asked a second time by the game ([KEY_REQUESTED] is never cleared - not by Reset
 *   Progress either). Apple and Google still decide whether the prompt actually appears.
 *
 * Players asked under the old rules have no flag yet, so they get one more request on their next
 * completed level - which the platforms' own quotas usually swallow.
 */
class ReviewPrompt(
    private val getRaw: (String) -> String?,
    private val setRaw: (String, String) -> Unit
) {
    /**
     * Called after a completed level's result is saved. True exactly once: when level 3 is among
     * the completed levels and no request has been made yet - and then marks it made.
     */
    fun claimAfterLevelComplete(levelStorage: LevelStorage): Boolean {
        if (getRaw(KEY_REQUESTED) == "true") return false
        if (levelStorage.getBestResult(TRIGGER_LEVEL_ID)?.completed != true) return false
        setRaw(KEY_REQUESTED, "true")
        return true
    }

    companion object {
        const val KEY_REQUESTED = "user_review_requested"
        const val TRIGGER_LEVEL_ID = "level_3"
    }
}
