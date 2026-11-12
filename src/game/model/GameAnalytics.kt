package game.model

/**
 * Where analytics events go - Firebase Analytics on Android (android-shell's
 * FirebaseGameAnalyticsSink) and iOS (ios-shell's FirebaseGameAnalyticsSink.swift). Param values
 * are Long or String only, the two types every Firebase SDK accepts.
 */
interface GameAnalyticsSink {
    fun logEvent(name: String, params: Map<String, Any>)
}

/** One analytics event, built by [GameAnalytics] - separate from sending so it can be tested. */
data class AnalyticsEvent(val name: String, val params: Map<String, Any> = emptyMap())

/**
 * The in-app actions sent to Firebase, which Google Ads imports as conversions. Event names are
 * the ones to pick from in Google Ads > Goals > Conversions > Import > Firebase:
 *
 *  - `level_start`       every attempt at a level (a fresh GameplayScene - retries included)
 *  - `level_complete`    every completed level, first clears and replays alike
 *  - `level_fail`        caught with no checkpoint to fall back on
 *  - `tutorial_complete` first ever clear of level 1, the level that teaches the controls
 *  - `levels_cleared_3`, `levels_cleared_5`  a player's 3rd / 5th distinct level cleared -
 *                        the "engaged player" signals to optimize a campaign toward
 *
 * Installs (`first_open`), sessions, in-app purchases and - once AdMob is linked to the Firebase
 * project - `ad_impression` with ad revenue are collected by the Firebase SDK itself.
 *
 * Like [SystemLanguage], a settable hook rather than expect/actual: this directory is compiled
 * into both the KorGE game and paywall-build. With no [sink] set (tests, desktop, a build without
 * Firebase config) every call is a no-op. On iOS each Kotlin framework has its own copy of this
 * object; the gameplay events all come from GameMain.framework's, which is the one ios-shell sets.
 */
object GameAnalytics {
    var sink: GameAnalyticsSink? = null

    /** Clears that send a `levels_cleared_N` milestone. */
    val CLEAR_MILESTONES = listOf(3, 5)

    fun levelStart(levelId: String) = send(levelStartEvents(levelId))

    /** [distinctLevelsCleared]: the new total if this was the level's first clear, else null. */
    fun levelComplete(levelId: String, stars: Int, distinctLevelsCleared: Int?) =
        send(levelCompleteEvents(levelId, stars, distinctLevelsCleared))

    fun levelFail(levelId: String, secondsPlayed: Double) = send(levelFailEvents(levelId, secondsPlayed))

    fun levelStartEvents(levelId: String): List<AnalyticsEvent> =
        listOf(AnalyticsEvent("level_start", levelParams(levelId)))

    fun levelCompleteEvents(levelId: String, stars: Int, distinctLevelsCleared: Int?): List<AnalyticsEvent> {
        val firstClear = distinctLevelsCleared != null
        val events = mutableListOf(
            AnalyticsEvent(
                "level_complete",
                levelParams(levelId) + mapOf("stars" to stars.toLong(), "first_clear" to if (firstClear) 1L else 0L)
            )
        )
        if (firstClear && levelId == "level_1") events += AnalyticsEvent("tutorial_complete")
        if (distinctLevelsCleared != null && distinctLevelsCleared in CLEAR_MILESTONES) {
            events += AnalyticsEvent("levels_cleared_$distinctLevelsCleared")
        }
        return events
    }

    fun levelFailEvents(levelId: String, secondsPlayed: Double): List<AnalyticsEvent> =
        listOf(AnalyticsEvent("level_fail", levelParams(levelId) + ("seconds_played" to secondsPlayed.toLong())))

    /** "level_7" -> level_number 7; ids that don't follow that pattern just omit it. */
    private fun levelParams(levelId: String): Map<String, Any> {
        val number = levelId.removePrefix("level_").toLongOrNull()
        return if (number != null) mapOf("level_id" to levelId, "level_number" to number) else mapOf("level_id" to levelId)
    }

    private fun send(events: List<AnalyticsEvent>) {
        val target = sink ?: return
        // Analytics must never be what breaks a level.
        for (event in events) {
            try {
                target.logEvent(event.name, event.params)
            } catch (_: Throwable) {
            }
        }
    }
}
