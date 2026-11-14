package game.model

/**
 * Puts back what the saved level results prove the player has earned, for the progress build 33
 * wrote over (see MapBackedGameProfileStorage.getRaw: a missing user_subtitles made the game
 * load a default profile and save it over the real one at the next coin award).
 *
 * The level results (`level_result_<id>`) were never touched, so they are the record:
 * - every completed level, and the one after it, is unlocked - what finishing it did at the time
 *   (the Missions grid already opened them from the results, via isLevelUnlocked; this keeps the
 *   saved list, and the main menu's PLAY, in step);
 * - [GameProfile.totalLevelsCompleted] (the ad gate's count of distinct levels ever completed) is
 *   at least the number of completed levels.
 * Nothing is ever taken away, so running it on every launch is harmless. Coins and gadgets have no
 * record to come back from. Premium is not repaired: nobody had bought Remove Ads (owner,
 * 2026-11-14), and RESTORE in the Store brings it back for anyone who does lose it.
 */
object ProgressRepair {
    /** True when anything was put back. */
    fun repair(
        profiles: GameProfileStorage,
        levels: LevelStorage,
        registry: List<LevelData> = LevelData.DEFAULT_LEVELS
    ): Boolean {
        val completed = registry.indices.filter { levels.getBestResult(registry[it].id)?.completed == true }
        if (completed.isEmpty()) return false
        val earned = completed.flatMap { i -> listOfNotNull(registry[i].id, registry.getOrNull(i + 1)?.id) }.toSet()
        val profile = profiles.getProfile()
        val missing = earned - profile.unlockedLevelIds
        val count = maxOf(profile.totalLevelsCompleted, completed.size)
        if (missing.isEmpty() && count == profile.totalLevelsCompleted) return false
        profile.unlockedLevelIds += missing
        profile.totalLevelsCompleted = count
        profiles.saveProfile(profile)
        return true
    }
}
