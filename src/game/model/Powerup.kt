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
        displayName = "CAMERA JAMMER",
        shortName = "JAMMER",
        duration = 10.0,
        defaultCost = 150
    ),
    PHANTOM_CLOAK(
        id = "phantom_cloak",
        displayName = "SLEEP DARTS",
        shortName = "DARTS",
        duration = 10.0,
        defaultCost = 250
    ),
    INVISIBILITY(
        id = "invisibility",
        displayName = "INVISIBILITY CLOAK",
        shortName = "INVIS",
        duration = 10.0,
        defaultCost = 350
    ),
    NOISE_SUPPRESSION(
        id = "noise_suppression",
        displayName = "NOISE SUPPRESSION BOOTS",
        shortName = "SILENCE",
        duration = -1.0, // Level-duration
        defaultCost = 500
    ),
    REMOTE_TRIGGER(
        id = "remote_trigger",
        displayName = "REMOTE TRIGGER",
        shortName = "TRIGGER",
        duration = 0.0,
        defaultCost = 750
    ),

    /**
     * Placeholder for the sixth gadget, so the store grid and the in-game quick-slot can both be
     * built and balanced against their final counts rather than being retrofitted later. It is a
     * real, buyable, spendable powerup with a real 10s timer - it simply has no world effect yet,
     * because nothing in GameWorld reads [ActivePowerups.prototypeTimer]. Give it a behaviour and
     * a name and it stops being a placeholder; nothing else has to change.
     */
    PROTOTYPE(
        id = "prototype",
        displayName = "PROTOTYPE GADGET",
        shortName = "PROTO",
        duration = 10.0,
        defaultCost = 400
    );

    val isLevelDuration: Boolean get() = duration <= 0.0

    companion object {
        fun fromId(id: String): PowerupType? {
            return when (id.lowercase().trim()) {
                "camera_jammer", "jammer", "smoke_screen", "smoke_bomb", "camera_disable", "smoke" -> SMOKE_SCREEN
                "sleep_darts", "sleep_dart", "darts", "phantom_cloak", "guard_sleep", "cloak" -> PHANTOM_CLOAK
                "invisibility", "invisibility_cloak", "invis" -> INVISIBILITY
                "noise_suppression", "stealth_boots", "silence" -> NOISE_SUPPRESSION
                "remote_trigger", "trigger", "remote", "checkpoint", "checkpoints", "tactical_checkpoint" -> REMOTE_TRIGGER
                "prototype", "proto", "prototype_gadget" -> PROTOTYPE
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
    var isNoiseSuppressed: Boolean = false,
    var prototypeTimer: Double = 0.0
) {
    val isSmokeScreenActive: Boolean get() = smokeScreenTimer > 0.0
    val isPhantomCloakActive: Boolean get() = phantomCloakTimer > 0.0
    val isInvisibilityActive: Boolean get() = invisibilityTimer > 0.0
    val isPrototypeActive: Boolean get() = prototypeTimer > 0.0

    val anyActive: Boolean
        get() = isSmokeScreenActive || isPhantomCloakActive || isInvisibilityActive || isNoiseSuppressed

    fun activate(type: PowerupType) {
        when (type) {
            PowerupType.SMOKE_SCREEN -> smokeScreenTimer = type.duration
            PowerupType.PHANTOM_CLOAK -> phantomCloakTimer = type.duration
            PowerupType.INVISIBILITY -> invisibilityTimer = type.duration
            PowerupType.NOISE_SUPPRESSION -> isNoiseSuppressed = true
            PowerupType.REMOTE_TRIGGER -> Unit
            PowerupType.PROTOTYPE -> prototypeTimer = type.duration
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
        if (prototypeTimer > 0.0) {
            prototypeTimer = (prototypeTimer - dt).coerceAtLeast(0.0)
        }
    }

    fun isActive(type: PowerupType): Boolean = when (type) {
        PowerupType.SMOKE_SCREEN -> isSmokeScreenActive
        PowerupType.PHANTOM_CLOAK -> isPhantomCloakActive
        PowerupType.INVISIBILITY -> isInvisibilityActive
        PowerupType.NOISE_SUPPRESSION -> isNoiseSuppressed
        PowerupType.REMOTE_TRIGGER -> false
        PowerupType.PROTOTYPE -> isPrototypeActive
    }

    fun getRemainingTime(type: PowerupType): Double = when (type) {
        PowerupType.SMOKE_SCREEN -> smokeScreenTimer
        PowerupType.PHANTOM_CLOAK -> phantomCloakTimer
        PowerupType.INVISIBILITY -> invisibilityTimer
        PowerupType.NOISE_SUPPRESSION -> if (isNoiseSuppressed) -1.0 else 0.0
        PowerupType.REMOTE_TRIGGER -> 0.0
        PowerupType.PROTOTYPE -> prototypeTimer
    }

    fun reset() {
        smokeScreenTimer = 0.0
        phantomCloakTimer = 0.0
        invisibilityTimer = 0.0
        isNoiseSuppressed = false
        prototypeTimer = 0.0
    }
}
