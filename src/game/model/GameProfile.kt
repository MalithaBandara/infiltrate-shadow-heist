package game.model

data class GameProfile(
    var coins: Int = 100,
    var isPremium: Boolean = false,
    var musicVolume: Float = 0.8f,
    var sfxVolume: Float = 1.0f,
    var controlsSwapped: Boolean = false,
    var language: String = "en",
    // level_4 is the side-scrolling sample level; unlocked from the start so it can be
    // played without first clearing the three single-screen levels.
    val unlockedLevelIds: MutableSet<String> = mutableSetOf("level_1", "level_4"),
    val powerupInventory: MutableMap<String, Int> = mutableMapOf(
        "smoke_screen" to 2,
        "smoke_bomb" to 1,
        "phantom_cloak" to 2,
        "invisibility" to 2,
        "noise_suppression" to 2,
        "stealth_boots" to 0,
        "radar_booster" to 0
    )
) {
    fun getPowerupCount(type: PowerupType): Int {
        val aliases = when (type) {
            PowerupType.SMOKE_SCREEN -> listOf("smoke_screen", "smoke_bomb", "camera_disable")
            PowerupType.PHANTOM_CLOAK -> listOf("phantom_cloak", "guard_sleep")
            PowerupType.INVISIBILITY -> listOf("invisibility", "invisibility_cloak")
            PowerupType.NOISE_SUPPRESSION -> listOf("noise_suppression", "stealth_boots")
        }
        return aliases.sumOf { powerupInventory[it] ?: 0 }
    }

    fun getPowerupCount(powerupId: String): Int {
        val type = PowerupType.fromId(powerupId)
        return if (type != null) getPowerupCount(type) else (powerupInventory[powerupId] ?: 0)
    }

    fun consumePowerup(type: PowerupType): Boolean {
        val aliases = when (type) {
            PowerupType.SMOKE_SCREEN -> listOf("smoke_screen", "smoke_bomb", "camera_disable")
            PowerupType.PHANTOM_CLOAK -> listOf("phantom_cloak", "guard_sleep")
            PowerupType.INVISIBILITY -> listOf("invisibility", "invisibility_cloak")
            PowerupType.NOISE_SUPPRESSION -> listOf("noise_suppression", "stealth_boots")
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
    fun unlockLevel(levelId: String)
    fun isLevelUnlocked(levelId: String, levelRegistry: List<LevelData>, levelStorage: LevelStorage): Boolean
    fun buyPowerup(powerupId: String, cost: Int): Boolean
    fun consumePowerup(type: PowerupType): Boolean
    fun consumePowerup(powerupId: String): Boolean
    fun grantDebugPowerups(amount: Int = 3)
    fun activatePremium()
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
        profile.isPremium = true
        addCoins(1000)
    }
}

class MapBackedGameProfileStorage(
    private val getRaw: (String) -> String?,
    private val setRaw: (String, String) -> Unit,
    private val inMemoryFallback: InMemoryGameProfileStorage = InMemoryGameProfileStorage()
) : GameProfileStorage {

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

            val current = inMemoryFallback.getProfile()
            coinsStr?.toIntOrNull()?.let { current.coins = it }
            isPremiumStr?.toBooleanStrictOrNull()?.let { current.isPremium = it }
            musicStr?.toFloatOrNull()?.let { current.musicVolume = it }
            sfxStr?.toFloatOrNull()?.let { current.sfxVolume = it }
            controlsSwappedStr?.toBooleanStrictOrNull()?.let { current.controlsSwapped = it }
            if (!languageStr.isNullOrBlank()) {
                current.language = languageStr
            }
            if (!unlockedStr.isNullOrBlank()) {
                current.unlockedLevelIds.addAll(unlockedStr.split(";").filter { it.isNotBlank() })
            }
            if (!powerupsStr.isNullOrBlank()) {
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

    private fun persist() {
        try {
            val current = inMemoryFallback.getProfile()
            setRaw("user_coins", current.coins.toString())
            setRaw("user_is_premium", current.isPremium.toString())
            setRaw("user_music_vol", current.musicVolume.toString())
            setRaw("user_sfx_vol", current.sfxVolume.toString())
            setRaw("user_controls_swapped", current.controlsSwapped.toString())
            setRaw("user_language", current.language)
            setRaw("user_unlocked_levels", current.unlockedLevelIds.joinToString(";"))
            setRaw("user_powerups", current.powerupInventory.map { "${it.key}:${it.value}" }.joinToString(";"))
        } catch (_: Throwable) {
        }
    }

    override fun getProfile(): GameProfile = inMemoryFallback.getProfile()

    override fun saveProfile(profile: GameProfile) {
        inMemoryFallback.saveProfile(profile)
        persist()
    }

    override fun addCoins(amount: Int): Int {
        val res = inMemoryFallback.addCoins(amount)
        persist()
        return res
    }

    override fun spendCoins(amount: Int): Boolean {
        val res = inMemoryFallback.spendCoins(amount)
        if (res) persist()
        return res
    }

    override fun setMusicVolume(volume: Float) {
        inMemoryFallback.setMusicVolume(volume)
        persist()
    }

    override fun setSfxVolume(volume: Float) {
        inMemoryFallback.setSfxVolume(volume)
        persist()
    }

    override fun setControlsSwapped(swapped: Boolean) {
        inMemoryFallback.setControlsSwapped(swapped)
        persist()
    }

    override fun setLanguage(language: String) {
        inMemoryFallback.setLanguage(language)
        persist()
    }

    override fun unlockLevel(levelId: String) {
        inMemoryFallback.unlockLevel(levelId)
        persist()
    }

    override fun isLevelUnlocked(levelId: String, levelRegistry: List<LevelData>, levelStorage: LevelStorage): Boolean {
        return inMemoryFallback.isLevelUnlocked(levelId, levelRegistry, levelStorage)
    }

    override fun buyPowerup(powerupId: String, cost: Int): Boolean {
        val res = inMemoryFallback.buyPowerup(powerupId, cost)
        if (res) persist()
        return res
    }

    override fun consumePowerup(type: PowerupType): Boolean {
        val res = inMemoryFallback.consumePowerup(type)
        if (res) persist()
        return res
    }

    override fun consumePowerup(powerupId: String): Boolean {
        val res = inMemoryFallback.consumePowerup(powerupId)
        if (res) persist()
        return res
    }

    override fun grantDebugPowerups(amount: Int) {
        inMemoryFallback.grantDebugPowerups(amount)
        persist()
    }

    override fun activatePremium() {
        inMemoryFallback.activatePremium()
        persist()
    }
}
