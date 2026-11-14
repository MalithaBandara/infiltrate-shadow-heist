package game.model

/**
 * When the game asks for a store review on its own (the StoreKit / Play in-app prompt): once per
 * milestone - levels 3, 6, 9 and 12 - at the first completed level from the moment that
 * milestone level has been completed.
 *
 * It used to ask on every completion of level 3 (level 4 before 2026-11-12), with no memory of
 * having asked. Moving the trigger from 4 to 3 left out every player who had finished level 3 on
 * an older build and not yet level 4: level 4 no longer asked, so they were never asked unless
 * they replayed level 3. Now:
 * - a new player is asked on finishing levels 3, 6, 9 and 12;
 * - a player who finished a milestone level earlier (an older build, or premium skipping ahead)
 *   is asked on the next level they finish, whichever;
 * - one completion asks at most once: a player with several milestones done since the last ask
 *   is asked once, for the highest of them ([KEY_LAST_MILESTONE] records it);
 * - replays never ask again for a milestone already asked for, and Reset Progress keeps the
 *   record.
 *
 * Neither platform says whether the prompt appeared or a review was written, so this cannot ask
 * "only those who did not review". Apple shows it at most three times a year per device and
 * Google applies its own quota, and neither shows it to someone who has already reviewed.
 */
class ReviewPrompt(
    private val getRaw: (String) -> String?,
    private val setRaw: (String, String) -> Unit
) {
    /**
     * Called after a completed level's result is saved. True when a milestone level is completed
     * past the last one asked for - and then records that milestone.
     */
    fun claimAfterLevelComplete(levelStorage: LevelStorage): Boolean {
        // Unwritten until the first ask - and a store that throws for a missing key (KorGE's
        // storage[key]) must read as "never asked", not abort the level's completion.
        val lastAsked = runCatching { getRaw(KEY_LAST_MILESTONE) }.getOrNull()?.toIntOrNull() ?: 0
        val reached = MILESTONE_LEVELS.lastOrNull { levelStorage.getBestResult("level_$it")?.completed == true } ?: return false
        if (reached <= lastAsked) return false
        setRaw(KEY_LAST_MILESTONE, reached.toString())
        return true
    }

    companion object {
        /** The highest milestone level a request has been made for (0 = none yet). */
        const val KEY_LAST_MILESTONE = "user_review_milestone"
        val MILESTONE_LEVELS = listOf(3, 6, 9, 12)
    }
}
