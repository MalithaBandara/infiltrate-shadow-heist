package game.model

enum class PowerupType(
    val id: String,
    val displayName: String,
    val shortName: String,
    val duration: Double,
    val defaultCost: Int
) {
    SMOKE_SCREEN(
        id = "smoke_screen",
        displayName = "SMOKE SCREEN",
        shortName = "SMOKE",
        duration = 10.0,
        defaultCost = 150
    ),
    PHANTOM_CLOAK(
        id = "phantom_cloak",
        displayName = "PHANTOM CLOAK",
        shortName = "CLOAK",
        duration = 10.0,
        defaultCost = 250
    ),
    INVISIBILITY(
        id = "invisibility",
        displayName = "INVISIBILITY",
        shortName = "INVIS",
        duration = 10.0,
        defaultCost = 350
    ),
    NOISE_SUPPRESSION(
        id = "noise_suppression",
        displayName = "NOISE SUPPRESSION",
        shortName = "SILENCE",
        duration = -1.0, // Level-duration
        defaultCost = 600
    );

    val isLevelDuration: Boolean get() = duration <= 0.0

    companion object {
        fun fromId(id: String): PowerupType? {
            return when (id.lowercase().trim()) {
                "smoke_screen", "smoke_bomb", "camera_disable", "smoke" -> SMOKE_SCREEN
                "phantom_cloak", "guard_sleep", "cloak" -> PHANTOM_CLOAK
                "invisibility", "invisibility_cloak", "invis" -> INVISIBILITY
                "noise_suppression", "stealth_boots", "silence" -> NOISE_SUPPRESSION
                else -> entries.firstOrNull {
                    it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true)
                }
            }
        }
    }
}

data class ActivePowerups(
    var smokeScreenTimer: Double = 0.0,
    var phantomCloakTimer: Double = 0.0,
    var invisibilityTimer: Double = 0.0,
    var isNoiseSuppressed: Boolean = false
) {
    val isSmokeScreenActive: Boolean get() = smokeScreenTimer > 0.0
    val isPhantomCloakActive: Boolean get() = phantomCloakTimer > 0.0
    val isInvisibilityActive: Boolean get() = invisibilityTimer > 0.0

    val anyActive: Boolean
        get() = isSmokeScreenActive || isPhantomCloakActive || isInvisibilityActive || isNoiseSuppressed

    fun activate(type: PowerupType) {
        when (type) {
            PowerupType.SMOKE_SCREEN -> smokeScreenTimer = type.duration
            PowerupType.PHANTOM_CLOAK -> phantomCloakTimer = type.duration
            PowerupType.INVISIBILITY -> invisibilityTimer = type.duration
            PowerupType.NOISE_SUPPRESSION -> isNoiseSuppressed = true
        }
    }

    fun update(dt: Double) {
        if (smokeScreenTimer > 0.0) {
            smokeScreenTimer = (smokeScreenTimer - dt).coerceAtLeast(0.0)
        }
        if (phantomCloakTimer > 0.0) {
            phantomCloakTimer = (phantomCloakTimer - dt).coerceAtLeast(0.0)
        }
        if (invisibilityTimer > 0.0) {
            invisibilityTimer = (invisibilityTimer - dt).coerceAtLeast(0.0)
        }
    }

    fun isActive(type: PowerupType): Boolean = when (type) {
        PowerupType.SMOKE_SCREEN -> isSmokeScreenActive
        PowerupType.PHANTOM_CLOAK -> isPhantomCloakActive
        PowerupType.INVISIBILITY -> isInvisibilityActive
        PowerupType.NOISE_SUPPRESSION -> isNoiseSuppressed
    }

    fun getRemainingTime(type: PowerupType): Double = when (type) {
        PowerupType.SMOKE_SCREEN -> smokeScreenTimer
        PowerupType.PHANTOM_CLOAK -> phantomCloakTimer
        PowerupType.INVISIBILITY -> invisibilityTimer
        PowerupType.NOISE_SUPPRESSION -> if (isNoiseSuppressed) -1.0 else 0.0
    }

    fun reset() {
        smokeScreenTimer = 0.0
        phantomCloakTimer = 0.0
        invisibilityTimer = 0.0
        isNoiseSuppressed = false
    }
}
