package game.model

data class GameProfile(
    var coins: Int = 50,
    var isPremium: Boolean = false,
    var musicVolume: Float = 0.8f,
    var sfxVolume: Float = 1.0f,
    var controlsSwapped: Boolean = false,
    var language: String = "en",
    // The intro video's subtitles: null until the player picks in Settings, and then the
    // language decides (IntroSubtitles.defaultOn - off for English, on for the rest). See
    // subtitlesOn.
    var subtitles: Boolean? = null,
    // Distinct levels completed at least once, ever - not a per-level replay counter. Used to
    // gate the level-exit interstitial (InterstitialAdLimiter.MIN_LEVELS_COMPLETED) so a brand
    // new player's first couple of levels stay ad-free.
    var totalLevelsCompleted: Int = 0,
    // Only level_1 is open on a fresh profile; each later level opens when the one before it
    // is completed (isLevelUnlocked).
    val unlockedLevelIds: MutableSet<String> = mutableSetOf("level_1"),
    val powerupInventory: MutableMap<String, Int> = mutableMapOf(
        "invisibility" to 1
    )
) {
    fun getPowerupCount(type: PowerupType): Int {
        val aliases = when (type) {
            PowerupType.SMOKE_SCREEN -> listOf("camera_jammer", "smoke_screen", "smoke_bomb", "camera_disable")
            PowerupType.LASER_SHIELD -> listOf("guard_shield", "laser_shield", "shield", "laser_guard", "sleep_darts", "sleep_dart", "phantom_cloak", "guard_sleep")
            PowerupType.INVISIBILITY -> listOf("invisibility", "invisibility_cloak")
            PowerupType.NOISE_SUPPRESSION -> listOf("noise_suppression", "stealth_boots", "stealth", "boots")
            PowerupType.CHECKPOINTS -> listOf("checkpoints", "checkpoint", "tactical_checkpoint")
            PowerupType.REMOTE_TRIGGER -> listOf("remote_trigger", "trigger", "remote")
            PowerupType.PROTOTYPE -> listOf("prototype")
        }
        return aliases.sumOf { powerupInventory[it] ?: 0 }
    }

    fun getPowerupCount(powerupId: String): Int {
        val type = PowerupType.fromId(powerupId)
        return if (type != null) getPowerupCount(type) else (powerupInventory[powerupId] ?: 0)
    }

    fun consumePowerup(type: PowerupType): Boolean {
        val aliases = when (type) {
            PowerupType.SMOKE_SCREEN -> listOf("camera_jammer", "smoke_screen", "smoke_bomb", "camera_disable")
            PowerupType.LASER_SHIELD -> listOf("guard_shield", "laser_shield", "shield", "laser_guard", "sleep_darts", "sleep_dart", "phantom_cloak", "guard_sleep")
            PowerupType.INVISIBILITY -> listOf("invisibility", "invisibility_cloak")
            PowerupType.NOISE_SUPPRESSION -> listOf("noise_suppression", "stealth_boots", "stealth", "boots")
            PowerupType.CHECKPOINTS -> listOf("checkpoints", "checkpoint", "tactical_checkpoint")
            PowerupType.REMOTE_TRIGGER -> listOf("remote_trigger", "trigger", "remote")
            PowerupType.PROTOTYPE -> listOf("prototype")
        }
        for (key in aliases) {
            val count = powerupInventory[key] ?: 0
            if (count > 0) {
                powerupInventory[key] = count - 1
                return true
            }
        }
        return false
    }

    fun consumePowerup(powerupId: String): Boolean {
        val type = PowerupType.fromId(powerupId)
        return if (type != null) {
            consumePowerup(type)
        } else {
            val count = powerupInventory[powerupId] ?: 0
            if (count > 0) {
                powerupInventory[powerupId] = count - 1
                true
            } else {
                false
            }
        }
    }

    /** Whether the intro shows subtitles: the player's choice, or the language's default. */
    fun subtitlesOn(): Boolean = subtitles ?: IntroSubtitles.defaultOn(language)

    fun grantDebugPowerups(amount: Int = 3) {
        for (type in PowerupType.entries) {
            powerupInventory[type.id] = (powerupInventory[type.id] ?: 0) + amount
        }
    }
}

interface GameProfileStorage {
    fun getProfile(): GameProfile
    fun saveProfile(profile: GameProfile)
    fun addCoins(amount: Int): Int
    fun spendCoins(amount: Int): Boolean
    fun setMusicVolume(volume: Float)
    fun setSfxVolume(volume: Float)
    fun setControlsSwapped(swapped: Boolean)
    fun setLanguage(language: String)
    fun setSubtitles(on: Boolean)
    fun unlockLevel(levelId: String)
    fun isLevelUnlocked(levelId: String, levelRegistry: List<LevelData>, levelStorage: LevelStorage): Boolean
    fun buyPowerup(powerupId: String, cost: Int): Boolean
    fun consumePowerup(type: PowerupType): Boolean
    fun consumePowerup(powerupId: String): Boolean
    fun grantDebugPowerups(amount: Int = 3)
    fun activatePremium()
    fun deactivatePremium()
    fun incrementLevelsCompleted(): Int
    fun resetProgress(preservePremium: Boolean = true)
}

class InMemoryGameProfileStorage(
    private val profile: GameProfile = GameProfile()
) : GameProfileStorage {

    override fun getProfile(): GameProfile = profile.copy(
        unlockedLevelIds = profile.unlockedLevelIds.toMutableSet(),
        powerupInventory = profile.powerupInventory.toMutableMap()
    )

    override fun saveProfile(profile: GameProfile) {
        this.profile.coins = profile.coins
        this.profile.isPremium = profile.isPremium
        this.profile.musicVolume = profile.musicVolume
        this.profile.sfxVolume = profile.sfxVolume
        this.profile.controlsSwapped = profile.controlsSwapped
        this.profile.language = profile.language
        this.profile.subtitles = profile.subtitles
        this.profile.totalLevelsCompleted = profile.totalLevelsCompleted
        this.profile.unlockedLevelIds.clear()
        this.profile.unlockedLevelIds.addAll(profile.unlockedLevelIds)
        this.profile.powerupInventory.clear()
        this.profile.powerupInventory.putAll(profile.powerupInventory)
    }

    override fun addCoins(amount: Int): Int {
        profile.coins = (profile.coins + amount).coerceAtLeast(0)
        return profile.coins
    }

    override fun spendCoins(amount: Int): Boolean {
        if (profile.coins >= amount) {
            profile.coins -= amount
            return true
        }
        return false
    }

    override fun setMusicVolume(volume: Float) {
        profile.musicVolume = volume.coerceIn(0.0f, 1.0f)
    }

    override fun setSfxVolume(volume: Float) {
        profile.sfxVolume = volume.coerceIn(0.0f, 1.0f)
    }

    override fun setControlsSwapped(swapped: Boolean) {
        profile.controlsSwapped = swapped
    }

    override fun setLanguage(language: String) {
        profile.language = language
    }

    override fun setSubtitles(on: Boolean) {
        profile.subtitles = on
    }

    override fun unlockLevel(levelId: String) {
        profile.unlockedLevelIds.add(levelId)
    }

    override fun isLevelUnlocked(levelId: String, levelRegistry: List<LevelData>, levelStorage: LevelStorage): Boolean {
        if (profile.isPremium) return true
        if (profile.unlockedLevelIds.contains(levelId)) return true
        val targetIndex = levelRegistry.indexOfFirst { it.id == levelId }
        if (targetIndex <= 0) return true
        val prevLevel = levelRegistry[targetIndex - 1]
        val prevResult = levelStorage.getBestResult(prevLevel.id)
        return prevResult?.completed == true
    }

    override fun buyPowerup(powerupId: String, cost: Int): Boolean {
        if (spendCoins(cost)) {
            val current = profile.powerupInventory[powerupId] ?: 0
            profile.powerupInventory[powerupId] = current + 1
            return true
        }
        return false
    }

    override fun consumePowerup(type: PowerupType): Boolean {
        return profile.consumePowerup(type)
    }

    override fun consumePowerup(powerupId: String): Boolean {
        return profile.consumePowerup(powerupId)
    }

    override fun grantDebugPowerups(amount: Int) {
        profile.grantDebugPowerups(amount)
    }

    override fun activatePremium() {
        if (!profile.isPremium) {
            profile.isPremium = true
            addCoins(2000)
        }
    }

    override fun deactivatePremium() {
        profile.isPremium = false
    }

    override fun incrementLevelsCompleted(): Int {
        profile.totalLevelsCompleted += 1
        return profile.totalLevelsCompleted
    }

    override fun resetProgress(preservePremium: Boolean) {
        val keepPremium = if (preservePremium) profile.isPremium else false
        val defaultProfile = GameProfile(isPremium = keepPremium)
        saveProfile(defaultProfile)
    }
}

class MapBackedGameProfileStorage(
    getRaw: (String) -> String?,
    private val setRaw: (String, String) -> Unit,
    private val inMemoryFallback: InMemoryGameProfileStorage = InMemoryGameProfileStorage(),
    // Device languages, most preferred first - only read when no language has been saved yet.
    private val systemLanguageTags: () -> List<String> = { SystemLanguage.preferredTags() }
) : GameProfileStorage {

    /**
     * A key that cannot be read is an absent key. KorGE's `storage[key]` throws for a key never
     * written, and every key here is read inside one try: before this, one missing key (build 33's
     * new user_subtitles, for everyone who had not touched the toggle) dropped the whole saved
     * profile for defaults, which the next persist() then wrote over the real save.
     */
    private val getRaw: (String) -> String? = { key -> try { getRaw(key) } catch (_: Throwable) { null } }

    init {
        loadFromStorage()
    }

    private fun loadFromStorage() {
        try {
            val coinsStr = getRaw("user_coins")
            val isPremiumStr = getRaw("user_is_premium")
            val musicStr = getRaw("user_music_vol")
            val sfxStr = getRaw("user_sfx_vol")
            val controlsSwappedStr = getRaw("user_controls_swapped")
            val languageStr = getRaw("user_language")
            val unlockedStr = getRaw("user_unlocked_levels")
            val powerupsStr = getRaw("user_powerups")
            val totalLevelsCompletedStr = getRaw("user_total_levels_completed")
            val subtitlesStr = getRaw("user_subtitles")

            val current = inMemoryFallback.getProfile()
            coinsStr?.toIntOrNull()?.let { current.coins = it }
            isPremiumStr?.toBooleanStrictOrNull()?.let { current.isPremium = it }
            musicStr?.toFloatOrNull()?.let { current.musicVolume = it }
            sfxStr?.toFloatOrNull()?.let { current.sfxVolume = it }
            controlsSwappedStr?.toBooleanStrictOrNull()?.let { current.controlsSwapped = it }
            totalLevelsCompletedStr?.toIntOrNull()?.let { current.totalLevelsCompleted = it }
            // Absent or blank = never chosen, so the language decides (GameProfile.subtitlesOn).
            current.subtitles = subtitlesStr?.toBooleanStrictOrNull()
            if (!languageStr.isNullOrBlank()) {
                current.language = languageStr
            } else {
                // First launch: follow the device language if the game is translated into it.
                // Saved right away, so later launches - and the KorGE game's own storage instance,
                // which on iOS lives in a separate framework without this hook set - read it back
                // as an ordinary saved choice. An unsupported device language saves nothing and
                // stays English. Existing players already have user_language saved (every persist
                // writes it), so this never overrides an earlier choice.
                val detected = runCatching { Localization.firstSupportedLanguage(systemLanguageTags()) }.getOrNull()
                if (detected != null) {
                    current.language = detected
                    setRaw("user_language", detected)
                }
            }
            // Replaced, not merged, now that this runs before every change (see [reload]): a Reset
            // Progress saved by another screen's instance has to take levels and gadgets away here
            // too. level_1 is always open.
            if (!unlockedStr.isNullOrBlank()) {
                current.unlockedLevelIds.clear()
                current.unlockedLevelIds.add("level_1")
                current.unlockedLevelIds.addAll(unlockedStr.split(";").filter { it.isNotBlank() })
            }
            if (!powerupsStr.isNullOrBlank()) {
                current.powerupInventory.clear()
                powerupsStr.split(";").filter { it.isNotBlank() }.forEach { entry ->
                    val parts = entry.split(":")
                    if (parts.size == 2) {
                        parts[1].toIntOrNull()?.let { count ->
                            current.powerupInventory[parts[0]] = count
                        }
                    }
                }
            }
            inMemoryFallback.saveProfile(current)
        } catch (_: Throwable) {
        }
    }

    /**
     * Re-reads the saved profile. Every screen, the game scene and the Android activity each hold
     * their own instance, and persist() writes the WHOLE profile - so an instance acting on what it
     * loaded earlier wrote back everything that had changed since through another one. Moving a
     * Settings slider (NavigationRoot's instance, loaded when the menu opened) undid coins and Remove
     * Ads just bought in the Store; on iOS, where the menu is built once per app run, it also undid
     * every coin, unlock and gadget earned in levels since launch. Called before every read and
     * every change, so each change lands on the latest save.
     */
    private fun reload() = loadFromStorage()

    private fun persist() {
        try {
            val current = inMemoryFallback.getProfile()
            setRaw("user_coins", current.coins.toString())
            setRaw("user_is_premium", current.isPremium.toString())
            setRaw("user_music_vol", current.musicVolume.toString())
            setRaw("user_sfx_vol", current.sfxVolume.toString())
            setRaw("user_controls_swapped", current.controlsSwapped.toString())
            setRaw("user_language", current.language)
            setRaw("user_total_levels_completed", current.totalLevelsCompleted.toString())
            setRaw("user_subtitles", current.subtitles?.toString() ?: "")
            setRaw("user_unlocked_levels", current.unlockedLevelIds.joinToString(";"))
            setRaw("user_powerups", current.powerupInventory.map { "${it.key}:${it.value}" }.joinToString(";"))
        } catch (_: Throwable) {
        }
    }

    override fun getProfile(): GameProfile {
        reload()
        return inMemoryFallback.getProfile()
    }

    override fun saveProfile(profile: GameProfile) {
        inMemoryFallback.saveProfile(profile)
        persist()
    }

    override fun addCoins(amount: Int): Int {
        reload()
        val res = inMemoryFallback.addCoins(amount)
        persist()
        return res
    }

    override fun spendCoins(amount: Int): Boolean {
        reload()
        val res = inMemoryFallback.spendCoins(amount)
        if (res) persist()
        return res
    }

    override fun setMusicVolume(volume: Float) {
        reload()
        inMemoryFallback.setMusicVolume(volume)
        persist()
    }

    override fun setSfxVolume(volume: Float) {
        reload()
        inMemoryFallback.setSfxVolume(volume)
        persist()
    }

    override fun setControlsSwapped(swapped: Boolean) {
        reload()
        inMemoryFallback.setControlsSwapped(swapped)
        persist()
    }

    override fun setLanguage(language: String) {
        reload()
        inMemoryFallback.setLanguage(language)
        persist()
    }

    override fun setSubtitles(on: Boolean) {
        reload()
        inMemoryFallback.setSubtitles(on)
        persist()
    }

    override fun unlockLevel(levelId: String) {
        reload()
        inMemoryFallback.unlockLevel(levelId)
        persist()
    }

    override fun isLevelUnlocked(levelId: String, levelRegistry: List<LevelData>, levelStorage: LevelStorage): Boolean {
        reload()
        return inMemoryFallback.isLevelUnlocked(levelId, levelRegistry, levelStorage)
    }

    override fun buyPowerup(powerupId: String, cost: Int): Boolean {
        reload()
        val res = inMemoryFallback.buyPowerup(powerupId, cost)
        if (res) persist()
        return res
    }

    override fun consumePowerup(type: PowerupType): Boolean {
        reload()
        val res = inMemoryFallback.consumePowerup(type)
        if (res) persist()
        return res
    }

    override fun consumePowerup(powerupId: String): Boolean {
        reload()
        val res = inMemoryFallback.consumePowerup(powerupId)
        if (res) persist()
        return res
    }

    override fun grantDebugPowerups(amount: Int) {
        reload()
        inMemoryFallback.grantDebugPowerups(amount)
        persist()
    }

    override fun activatePremium() {
        reload()
        inMemoryFallback.activatePremium()
        persist()
    }

    override fun deactivatePremium() {
        reload()
        inMemoryFallback.deactivatePremium()
        persist()
    }

    override fun incrementLevelsCompleted(): Int {
        reload()
        val res = inMemoryFallback.incrementLevelsCompleted()
        persist()
        return res
    }

    override fun resetProgress(preservePremium: Boolean) {
        reload()
        inMemoryFallback.resetProgress(preservePremium)
        persist()
    }
}
