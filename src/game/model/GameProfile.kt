package game.model

data class GameProfile(
    var coins: Int = 100,
    var isPremium: Boolean = false,
    var musicVolume: Float = 0.8f,
    var sfxVolume: Float = 1.0f,
    // level_4 is the side-scrolling sample level; unlocked from the start so it can be
    // played without first clearing the three single-screen levels.
    val unlockedLevelIds: MutableSet<String> = mutableSetOf("level_1", "level_4"),
    val powerupInventory: MutableMap<String, Int> = mutableMapOf(
        "smoke_bomb" to 1,
        "stealth_boots" to 0,
        "radar_booster" to 0
    )
)

interface GameProfileStorage {
    fun getProfile(): GameProfile
    fun saveProfile(profile: GameProfile)
    fun addCoins(amount: Int): Int
    fun spendCoins(amount: Int): Boolean
    fun setMusicVolume(volume: Float)
    fun setSfxVolume(volume: Float)
    fun unlockLevel(levelId: String)
    fun isLevelUnlocked(levelId: String, levelRegistry: List<LevelData>, levelStorage: LevelStorage): Boolean
    fun buyPowerup(powerupId: String, cost: Int): Boolean
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
            val unlockedStr = getRaw("user_unlocked_levels")

            val current = inMemoryFallback.getProfile()
            coinsStr?.toIntOrNull()?.let { current.coins = it }
            isPremiumStr?.toBooleanStrictOrNull()?.let { current.isPremium = it }
            musicStr?.toFloatOrNull()?.let { current.musicVolume = it }
            sfxStr?.toFloatOrNull()?.let { current.sfxVolume = it }
            if (!unlockedStr.isNullOrBlank()) {
                current.unlockedLevelIds.addAll(unlockedStr.split(";").filter { it.isNotBlank() })
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
            setRaw("user_unlocked_levels", current.unlockedLevelIds.joinToString(";"))
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

    override fun activatePremium() {
        inMemoryFallback.activatePremium()
        persist()
    }
}
