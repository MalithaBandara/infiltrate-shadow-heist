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
    LASER_SHIELD(
        id = "laser_shield",
        displayName = "LASER SHIELD",
        shortName = "SHIELD",
        duration = -1.0, // Level-duration until consumed by 1 hit
        defaultCost = 500
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
        displayName = "STEALTH BOOTS",
        shortName = "STEALTH",
        duration = -1.0, // Level-duration
        defaultCost = 400
    ),
    CHECKPOINTS(
        id = "checkpoints",
        displayName = "CHECKPOINTS",
        shortName = "CHECKPOINT",
        duration = -1.0, // Mission-wide
        defaultCost = 750
    ),
    REMOTE_TRIGGER(
        id = "remote_trigger",
        displayName = "REMOTE TRIGGER",
        shortName = "TRIGGER",
        duration = 0.0,
        defaultCost = 600
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
        @Deprecated("Replaced by LASER_SHIELD", ReplaceWith("LASER_SHIELD"))
        val PHANTOM_CLOAK: PowerupType get() = LASER_SHIELD

        val STEALTH_BOOTS: PowerupType get() = NOISE_SUPPRESSION

        fun fromId(id: String): PowerupType? {
            return when (id.lowercase().trim()) {
                "camera_jammer", "jammer", "smoke_screen", "smoke_bomb", "camera_disable", "smoke" -> SMOKE_SCREEN
                "laser_shield", "laser_guard", "shield", "guard", "sleep_darts", "sleep_dart", "darts", "phantom_cloak", "guard_sleep", "cloak" -> LASER_SHIELD
                "invisibility", "invisibility_cloak", "invis" -> INVISIBILITY
                "noise_suppression", "stealth_boots", "silence", "boots", "stealth" -> NOISE_SUPPRESSION
                "checkpoints", "checkpoint", "tactical_checkpoint" -> CHECKPOINTS
                "remote_trigger", "trigger", "remote" -> REMOTE_TRIGGER
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
    var laserShieldCharges: Int = 0,
    var invisibilityTimer: Double = 0.0,
    var isNoiseSuppressed: Boolean = false,
    var prototypeTimer: Double = 0.0,
    var isCheckpointsActive: Boolean = false
) {
    val isSmokeScreenActive: Boolean get() = smokeScreenTimer > 0.0
    val isPhantomCloakActive: Boolean get() = phantomCloakTimer > 0.0
    val isLaserShieldActive: Boolean get() = laserShieldCharges > 0
    val isInvisibilityActive: Boolean get() = invisibilityTimer > 0.0
    val isPrototypeActive: Boolean get() = prototypeTimer > 0.0

    val anyActive: Boolean
        get() = isSmokeScreenActive || isPhantomCloakActive || isLaserShieldActive || isInvisibilityActive || isNoiseSuppressed || isCheckpointsActive

    fun activate(type: PowerupType) {
        when (type) {
            PowerupType.SMOKE_SCREEN -> smokeScreenTimer = type.duration
            PowerupType.LASER_SHIELD -> laserShieldCharges = 1
            PowerupType.INVISIBILITY -> invisibilityTimer = type.duration
            PowerupType.NOISE_SUPPRESSION -> isNoiseSuppressed = true
            PowerupType.CHECKPOINTS -> isCheckpointsActive = true
            PowerupType.REMOTE_TRIGGER -> Unit
            PowerupType.PROTOTYPE -> prototypeTimer = type.duration
        }
    }

    fun consumeLaserShield(): Boolean {
        if (laserShieldCharges > 0) {
            laserShieldCharges--
            return true
        }
        return false
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
        PowerupType.LASER_SHIELD -> isLaserShieldActive
        PowerupType.INVISIBILITY -> isInvisibilityActive
        PowerupType.NOISE_SUPPRESSION -> isNoiseSuppressed
        PowerupType.CHECKPOINTS -> isCheckpointsActive
        PowerupType.REMOTE_TRIGGER -> false
        PowerupType.PROTOTYPE -> isPrototypeActive
    }

    fun getRemainingTime(type: PowerupType): Double = when (type) {
        PowerupType.SMOKE_SCREEN -> smokeScreenTimer
        PowerupType.LASER_SHIELD -> if (isLaserShieldActive) -1.0 else 0.0
        PowerupType.INVISIBILITY -> invisibilityTimer
        PowerupType.NOISE_SUPPRESSION -> if (isNoiseSuppressed) -1.0 else 0.0
        PowerupType.CHECKPOINTS -> if (isCheckpointsActive) -1.0 else 0.0
        PowerupType.REMOTE_TRIGGER -> 0.0
        PowerupType.PROTOTYPE -> prototypeTimer
    }

    fun reset() {
        smokeScreenTimer = 0.0
        phantomCloakTimer = 0.0
        laserShieldCharges = 0
        invisibilityTimer = 0.0
        isNoiseSuppressed = false
        prototypeTimer = 0.0
        isCheckpointsActive = false
    }
}
