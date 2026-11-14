package game.model

/**
 * One free Checkpoints power-up, the first time a player starts level 4 - and never again.
 *
 * Level 4 ("Moving Target") is where players stop: Firebase, 2026-11-14 - of 127 players who had
 * cleared three levels, 116 failed a level straight after and only 31 cleared another. It is a
 * two-minute belt run with eight lasers, and a single hit sent them back to the start. A
 * Checkpoints power-up turns that into a respawn at the last checkpoint; the level's tutorial step
 * `step_use_checkpoint` recommends using it.
 *
 * Granted once per install, recorded in [KEY] BEFORE the grant, so opening the level again (or
 * restarting it, or quitting mid-load) can never hand out another one. A player who has already
 * completed level 4 is not stuck on it and gets nothing. Reset Progress leaves the record alone.
 */
object Level4CheckpointGift {
    const val LEVEL_ID = "level_4"
    const val KEY = "user_l4_checkpoint_gift"

    /** True when the power-up was granted just now. */
    fun claimOnLevelStart(
        levelId: String,
        getRaw: (String) -> String?,
        setRaw: (String, String) -> Unit,
        profiles: GameProfileStorage,
        levels: LevelStorage
    ): Boolean {
        if (levelId != LEVEL_ID) return false
        if (!runCatching { getRaw(KEY) }.getOrNull().isNullOrBlank()) return false
        if (levels.getBestResult(LEVEL_ID)?.completed == true) return false
        setRaw(KEY, "1")
        return profiles.buyPowerup(PowerupType.CHECKPOINTS.id, 0)
    }
}
