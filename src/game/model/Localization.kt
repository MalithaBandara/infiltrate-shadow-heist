package game.model

object Localization {
    const val EN = "en"
    const val FR = "fr"

    fun isFrench(lang: String): Boolean = lang.equals(FR, ignoreCase = true) || lang.startsWith("fr-", ignoreCase = true)

    fun levelName(levelId: String, lang: String): String {
        if (!isFrench(lang)) {
            return when (levelId) {
                "level_1" -> "01: Night Arrival"
                "level_2" -> "02: Cargo Yard"
                "level_3" -> "03: First Contact"
                "level_4" -> "04: Moving Target"
                "level_5" -> "05: The Crane Yard"
                "level_6" -> "06: Stolen Manifest"
                "level_7" -> "07: Service Tunnel"
                "level_8" -> "08: Relocation"
                "level_9" -> "09: Déjà Vu"
                "level_10" -> "10: Below the Yard"
                "level_11" -> "11: The Prisoner"
                "level_12" -> "12: Final Escape"
                else -> ""
            }
        }
        return when (levelId) {
            "level_1" -> "01: Arrivée nocturne"
            "level_2" -> "02: Zone de fret"
            "level_3" -> "03: Premier contact"
            "level_4" -> "04: Cible mobile"
            "level_5" -> "05: Le parc à grues"
            "level_6" -> "06: Manifeste volé"
            "level_7" -> "07: Tunnel de service"
            "level_8" -> "08: Relocalisation"
            "level_9" -> "09: Déjà Vu"
            "level_10" -> "10: Sous le chantier"
            "level_11" -> "11: Le prisonnier"
            "level_12" -> "12: Échappée finale"
            else -> ""
        }
    }

    fun levelDescription(levelId: String, lang: String): String {
        if (!isFrench(lang)) {
            return when (levelId) {
                "level_1" -> "Reach the shipyard under cover of darkness and find a way inside."
                "level_2" -> "Cross the empty container yard and reach the restricted section."
                "level_3" -> "Security is active. Avoid guards and cameras to reach the conveyor belt."
                "level_4" -> "Search the moving conveyor belt for Container 17 while avoiding lasers."
                "level_5" -> "Container 17 is gone. Search the crane yard for signs of where it went."
                "level_6" -> "Break into the office and recover records revealing Container 17’s location."
                "level_7" -> "Avoid the heavily guarded security room through the underground service tunnel."
                "level_8" -> "The guards moved Container 17. Follow the trail to its new location."
                "level_9" -> "The trail feels strangely familiar, as if you’ve done this before."
                "level_10" -> "Follow the underground tunnels in search of the person you were tracking."
                "level_11" -> "Rescue the prisoner and escort him to safety. Something about him feels familiar."
                "level_12" -> "Guards are closing in. Get the prisoner out of the shipyard before it’s too late."
                else -> ""
            }
        }
        return when (levelId) {
            "level_1" -> "Rejoignez le chantier naval sous couvert de l'obscurité et trouvez un moyen d'entrer."
            "level_2" -> "Traversez le dépôt de conteneurs vide et atteignez la zone d'accès restreint."
            "level_3" -> "La sécurité est active. Évitez les gardes et les caméras pour atteindre le tapis roulant."
            "level_4" -> "Fouillez le tapis roulant en mouvement pour trouver le conteneur 17 tout en évitant les lasers."
            "level_5" -> "Le conteneur 17 a disparu. Fouillez le parc à grues pour découvrir où il a été emmené."
            "level_6" -> "Infiltrez le bureau et récupérez les documents révélant l'emplacement du conteneur 17."
            "level_7" -> "Évitez la salle de sécurité lourdement gardée en empruntant le tunnel de service souterrain."
            "level_8" -> "Les gardes ont déplacé le conteneur 17. Suivez la piste jusqu'à son nouvel emplacement."
            "level_9" -> "Cette piste semble étrangement familière, comme si vous étiez déjà venu ici."
            "level_10" -> "Suivez les tunnels souterrains à la recherche de la personne que vous traquiez."
            "level_11" -> "Libérez le prisonnier et escortez-le en lieu sûr. Quelque chose chez lui vous semble familier."
            "level_12" -> "Les gardes se rapprochent. Faites sortir le prisonnier du chantier naval avant qu'il ne soit trop tard."
            else -> ""
        }
    }

    fun levelObjectiveHint(levelId: String, lang: String): String {
        if (!isFrench(lang)) {
            return when (levelId) {
                "level_1" -> "Find the Shipyard Entrance"
                "level_2" -> "Find a Way Through the Yard"
                "level_3" -> "Reach the Conveyor Belt"
                "level_4" -> "Traverse the Conveyor Line"
                "level_5" -> "Get Into the Restricted Area"
                "level_6" -> "Find Container 17"
                "level_7" -> "Infiltrate Facility"
                "level_8" -> "Push the load to extraction"
                "level_9" -> "Find Your Crew's Mark"
                "level_10" -> "Cross the Yard Undetected"
                "level_11" -> "Follow the Stranger"
                "level_12" -> "Open Container 17"
                else -> ""
            }
        }
        return when (levelId) {
            "level_1" -> "Trouvez l'entrée du chantier naval"
            "level_2" -> "Trouvez un chemin à travers le dépôt"
            "level_3" -> "Atteignez le tapis roulant"
            "level_4" -> "Traversez la ligne de convoyage"
            "level_5" -> "Entrez dans la zone réglementée"
            "level_6" -> "Trouvez le conteneur 17"
            "level_7" -> "Infiltrez le complexe"
            "level_8" -> "Poussez la cargaison vers l'extraction"
            "level_9" -> "Trouvez la marque de votre équipe"
            "level_10" -> "Traversez le chantier sans vous faire repérer"
            "level_11" -> "Suivez l'inconnu"
            "level_12" -> "Ouvrez le conteneur 17"
            else -> ""
        }
    }

    fun powerupName(type: PowerupType, lang: String): String {
        if (!isFrench(lang)) return type.displayName
        return when (type) {
            PowerupType.INVISIBILITY -> "CAPE D'INVISIBILITÉ"
            PowerupType.NOISE_SUPPRESSION -> "BOTTES SILENCIEUSES"
            PowerupType.LASER_SHIELD -> "BOUCLIER DE PROTECTION"
            PowerupType.REMOTE_TRIGGER -> "DÉCLENCHEUR À DISTANCE"
            PowerupType.CHECKPOINTS -> "POINTS DE CONTRÔLE"
            PowerupType.SMOKE_SCREEN -> "ÉCRAN DE FUMÉE"
            PowerupType.PROTOTYPE -> "PROTOTYPE"
        }
    }

    fun powerupDescription(type: PowerupType, lang: String): String {
        if (!isFrench(lang)) {
            return when (type) {
                PowerupType.INVISIBILITY -> "Become invisible to guards and cameras for 10 seconds."
                PowerupType.NOISE_SUPPRESSION -> "Silent movement for entire mission."
                PowerupType.LASER_SHIELD -> "Protects from 1 laser or steam hazard contact."
                PowerupType.REMOTE_TRIGGER -> "Triggers closest mechanism without needing to find its switch."
                PowerupType.CHECKPOINTS -> "Respawn at activated checkpoints after being caught or restarting"
                PowerupType.SMOKE_SCREEN -> "Deploy smoke to block line of sight."
                PowerupType.PROTOTYPE -> "Experimental gadget under development."
            }
        }
        return when (type) {
            PowerupType.INVISIBILITY -> "Devenez invisible pour les gardes et les caméras pendant 10 secondes."
            PowerupType.NOISE_SUPPRESSION -> "Déplacement silencieux pendant toute la mission."
            PowerupType.LASER_SHIELD -> "Protège d'un contact avec un laser ou un jet de vapeur."
            PowerupType.REMOTE_TRIGGER -> "Active le mécanisme le plus proche sans avoir à trouver son interrupteur."
            PowerupType.CHECKPOINTS -> "Réapparaissez aux points de contrôle après avoir été capturé ou en recommençant"
            PowerupType.SMOKE_SCREEN -> "Déploie de la fumée pour bloquer la ligne de vue."
            PowerupType.PROTOTYPE -> "Gadget expérimental en cours de développement."
        }
    }

    fun mysteryGadgetName(lang: String): String = if (isFrench(lang)) "GADGET MYSTÈRE" else "MYSTERY GADGET"
    fun mysteryGadgetDescription(lang: String): String =
        if (isFrench(lang)) "Obtenez 1 gadget aléatoire. Chaque gadget a une chance égale d'apparaître."
        else "Get 1 random gadget. Every gadget has an equal chance of appearing."

    fun coinPackName(packId: String, lang: String): String {
        if (!isFrench(lang)) {
            return when (packId) {
                "coins_loose" -> "LOOSE COINS"
                "coins_pouch" -> "SMUGGLER'S POUCH"
                "coins_briefcase" -> "TACTICAL BRIEFCASE"
                "coins_stash" -> "OPERATIVE STASH"
                "coins_duffle" -> "HEIST DUFFLE BAG"
                "coins_vault" -> "BLACK MARKET VAULT"
                else -> packId.uppercase()
            }
        }
        return when (packId) {
            "coins_loose" -> "PIÈCES EN VRAC"
            "coins_pouch" -> "BOURSE DU CONTREBANDIER"
            "coins_briefcase" -> "MALLETTE TACTIQUE"
            "coins_stash" -> "RÉSERVE D'AGENT"
            "coins_duffle" -> "SAC DE BRAQUAGE"
            "coins_vault" -> "COFFRE DU MARCHÉ NOIR"
            else -> packId.uppercase()
        }
    }

    // --- Main Menu ---
    fun play(lang: String): String = if (isFrench(lang)) "JOUER" else "PLAY"
    fun missions(lang: String): String = "MISSIONS"
    fun store(lang: String): String = if (isFrench(lang)) "BOUTIQUE" else "STORE"
    fun settings(lang: String): String = if (isFrench(lang)) "PARAMÈTRES" else "SETTINGS"
    fun theShipyard(lang: String): String = if (isFrench(lang)) "LE CHANTIER NAVAL" else "THE SHIPYARD"
    fun situationBriefing(lang: String): String = if (isFrench(lang)) "RAPPORT DE SITUATION" else "SITUATION BRIEFING"
    fun fileNo(lang: String): String = if (isFrench(lang)) "DOSSIER N°" else "FILE NO."
    fun status(lang: String): String = if (isFrench(lang)) "STATUT" else "STATUS"
    fun readyForDeployment(lang: String): String = if (isFrench(lang)) "PRÊT AU DÉPLOIEMENT" else "READY FOR DEPLOYMENT"
    fun primaryObjective(lang: String): String = if (isFrench(lang)) "OBJECTIF PRINCIPAL" else "PRIMARY OBJECTIVE"
    fun securityLevel(lang: String): String = if (isFrench(lang)) "NIVEAU DE SÉCURITÉ" else "SECURITY LEVEL"
    fun highRisk(lang: String): String = if (isFrench(lang)) "RISQUE ÉLEVÉ" else "HIGH RISK"
    fun payout(lang: String): String = if (isFrench(lang)) "PRIME" else "PAYOUT"
    fun authorizationPending(lang: String): String = if (isFrench(lang)) "EN ATTENTE D'AUTORISATION" else "AUTHORIZATION PENDING"
    fun shadowHeistDossier(lang: String): String = if (isFrench(lang)) "DOSSIER D'AGENT SHADOW HEIST" else "SHADOW HEIST OPERATIVE DOSSIER"

    // --- Level Select ---
    fun missionsAvailable(lang: String): String = if (isFrench(lang)) "12 MISSIONS DISPONIBLES" else "12 MISSIONS AVAILABLE"
    fun comingSoon(lang: String): String = if (isFrench(lang)) "BIENTÔT DISPONIBLE" else "COMING SOON"
    fun locked(lang: String): String = if (isFrench(lang)) "VERROUILLÉ" else "LOCKED"
    fun completePreviousMission(lang: String): String = if (isFrench(lang)) "Terminez la mission précédente" else "Complete previous mission"
    fun shadowPassRequired(lang: String): String = if (isFrench(lang)) "Passe de l'Ombre requis" else "Shadow Pass required"
    fun missionLabel(lang: String): String = "MISSION"
    fun startMission(lang: String): String = if (isFrench(lang)) "DÉMARRER LA MISSION" else "START MISSION"
    fun bestTime(lang: String): String = if (isFrench(lang)) "MEILLEUR TEMPS" else "BEST TIME"
    fun alerts(lang: String): String = if (isFrench(lang)) "ALERTES" else "ALERTS"
    fun reward(lang: String): String = if (isFrench(lang)) "RÉCOMPENSE" else "REWARD"
    fun stars(lang: String): String = if (isFrench(lang)) "ÉTOILES" else "STARS"

    // --- Store Screen ---
    fun powerupsTab(lang: String): String = if (isFrench(lang)) "GADGETS" else "POWER-UPS"
    fun coinsTab(lang: String): String = if (isFrench(lang)) "PIÈCES" else "COINS"
    fun removeAdsTab(lang: String): String = if (isFrench(lang)) "SUPPRIMER LES PUBS" else "REMOVE ADS"
    fun inventory(lang: String): String = if (isFrench(lang)) "INVENTAIRE" else "INVENTORY"
    fun gadgetsAndEquipment(lang: String): String = if (isFrench(lang)) "GADGETS ET ÉQUIPEMENT" else "GADGETS & EQUIPMENT"
    fun coinPacks(lang: String): String = if (isFrench(lang)) "PACKS DE PIÈCES" else "COIN PACKS"
    fun free(lang: String): String = if (isFrench(lang)) "GRATUIT" else "FREE"
    fun buy(lang: String): String = if (isFrench(lang)) "ACHETER" else "BUY"
    fun equipped(lang: String): String = if (isFrench(lang)) "ÉQUIPÉ" else "EQUIPPED"
    fun owned(count: Int, lang: String): String = if (isFrench(lang)) "POSSÉDÉ : $count" else "OWNED: $count"
    fun coinsAmount(amount: Int, lang: String): String = if (isFrench(lang)) "$amount PIÈCES" else "$amount COINS"
    fun watchAd(lang: String): String = if (isFrench(lang)) "REGARDER UNE PUB" else "WATCH AD"
    fun watchAdLeft(count: Int, lang: String): String =
        if (isFrench(lang)) "REGARDER UNE PUB ($count RESTANTE${if (count > 1) "S" else ""})" else "WATCH AD ($count LEFT)"
    fun comeBackTomorrow(lang: String): String = if (isFrench(lang)) "REVENEZ DEMAIN" else "COME BACK TOMORROW"
    fun availableIn(time: String, lang: String): String = if (isFrench(lang)) "DISPONIBLE DANS $time" else "AVAILABLE IN $time"
    fun lifetimePass(lang: String): String = if (isFrench(lang)) "PASSE À VIE" else "LIFETIME PASS"
    fun active(lang: String): String = if (isFrench(lang)) "ACTIF" else "ACTIVE"
    fun permanentAdRemoval(lang: String): String = if (isFrench(lang)) "SUPPRESSION PERMANENTE DES PUBS" else "PERMANENT AD REMOVAL"
    fun lifetimeDesc(lang: String): String =
        if (isFrench(lang)) "Achat unique. Profitez d'une infiltration furtive sans interruption pour toujours."
        else "One-time purchase. Enjoy seamless stealth infiltration forever."
    fun zeroAds(lang: String): String = if (isFrench(lang)) "ZÉRO PUB" else "ZERO ADS"
    fun zeroAdsDesc(lang: String): String =
        if (isFrench(lang)) "Supprime totalement toutes les publicités interstitielles."
        else "Completely removes all interstitial advertisements."
    fun doubleBounty(lang: String): String = if (isFrench(lang)) "BUTIN DE BRAQUAGE x2" else "2X HEIST BOUNTY"
    fun doubleBountyDesc(lang: String): String =
        if (isFrench(lang)) "Double définitivement tous les gains en pièces pour les victoires à 1, 2 et 3 étoiles."
        else "Permanently doubles all coin payouts for 1-star, 2-star, and 3-star level clears."
    fun bonusCoins(lang: String): String = if (isFrench(lang)) "+2 000 PIÈCES BONUS" else "+2,000 BONUS COINS"
    fun bonusCoinsDesc(lang: String): String =
        if (isFrench(lang)) "Ajout immédiat de 2 000 pièces d'or à votre solde d'agent."
        else "Immediate injection of 2,000 gold coins into your operative balance."
    fun purchaseLifetimePass(lang: String): String =
        if (isFrench(lang)) "ACHETER LE PASSE À VIE — 2,99 $" else "PURCHASE LIFETIME PASS — $2.99"
    fun allAdsRemoved(lang: String): String =
        if (isFrench(lang)) "✓ TOUTES LES PUBS SUPPRIMÉES — PASSE À VIE ACTIF" else "✓ ALL ADS REMOVED — LIFETIME PASS ACTIVE"
    fun restorePurchases(lang: String): String = if (isFrench(lang)) "RESTAURER LES ACHATS" else "RESTORE PURCHASES"
    fun purchased(item: String, lang: String): String = if (isFrench(lang)) "$item acheté !" else "Purchased $item!"
    fun notEnoughCoins(lang: String): String = if (isFrench(lang)) "Pas assez de pièces" else "Not enough coins"
    fun adWatchedCoins(coins: Int, lang: String): String =
        if (isFrench(lang)) "Publicité visionnée ! +$coins pièces" else "Ad watched! +$coins coins"
    fun adWatchedGadget(name: String, lang: String): String =
        if (isFrench(lang)) "Publicité visionnée ! $name reçu" else "Ad watched! Received $name"
    fun adNotReady(lang: String): String =
        if (isFrench(lang)) "Publicité non prête. Veuillez réessayer plus tard." else "Ad not ready. Please try again later."
    fun dailyLimitReached(lang: String): String =
        if (isFrench(lang)) "Limite quotidienne atteinte. Revenez demain !" else "Daily limit reached. Come back tomorrow!"
    fun purchasesRestored(lang: String): String = if (isFrench(lang)) "Achats restaurés" else "Purchases restored"
    fun nothingToRestore(lang: String): String = if (isFrench(lang)) "Rien à restaurer" else "Nothing to restore"

    // --- Settings Screen ---
    fun generalTab(lang: String): String = if (isFrench(lang)) "GÉNÉRAL" else "GENERAL"
    fun aboutTab(lang: String): String = if (isFrench(lang)) "À PROPOS" else "ABOUT"
    fun generalConfig(lang: String): String = if (isFrench(lang)) "CONFIGURATION GÉNÉRALE" else "GENERAL CONFIGURATION"
    fun languageSetting(lang: String): String = if (isFrench(lang)) "LANGUE" else "LANGUAGE"
    fun languageDesc(lang: String): String = if (isFrench(lang)) "Sélectionnez votre langue" else "Select your language"
    fun controlsSetting(lang: String): String = if (isFrench(lang)) "COMMANDES" else "CONTROLS"
    fun swapControls(lang: String): String = if (isFrench(lang)) "INVERSER LES COMMANDES" else "SWAP CONTROLS"
    fun swapControlsDesc(lang: String): String =
        if (isFrench(lang)) "Placer le pavé directionnel à droite et les actions à gauche" else "Move D-Pad to the right, actions to the left"
    fun defaultControls(lang: String): String = if (isFrench(lang)) "PAR DÉFAUT" else "DEFAULT"
    fun swappedControls(lang: String): String = if (isFrench(lang)) "INVERSÉ" else "SWAPPED"
    fun audioSetting(lang: String): String = "AUDIO"
    fun musicVolume(lang: String): String = if (isFrench(lang)) "VOLUME DE LA MUSIQUE" else "MUSIC VOLUME"
    fun soundEffects(lang: String): String = if (isFrench(lang)) "EFFETS SONORES" else "SOUND EFFECTS"
    fun resetProgress(lang: String): String = if (isFrench(lang)) "RÉINITIALISER LA PROGRESSION" else "RESET PROGRESS"
    fun resetAllProgress(lang: String): String = if (isFrench(lang)) "RÉINITIALISER TOUTE LA PROGRESSION" else "RESET ALL PROGRESS"
    fun resetProgressDesc(lang: String): String =
        if (isFrench(lang)) "Effacer toutes les étoiles de mission, les temps et l'inventaire" else "Clear all mission stars, times, and inventory"
    fun resetBtn(lang: String): String = if (isFrench(lang)) "RÉINITIALISER" else "RESET"
    fun confirmProgressReset(lang: String): String = if (isFrench(lang)) "CONFIRMER LA RÉINITIALISATION" else "CONFIRM PROGRESS RESET"
    fun confirmResetDesc(lang: String): String =
        if (isFrench(lang)) "Êtes-vous sûr de vouloir réinitialiser toutes les données de jeu ? Cela effacera définitivement :\n• Toutes les missions terminées, les meilleurs temps et les étoiles\n• Le solde de pièces et l'inventaire de gadgets\n• Les préférences audio, de langue et de commandes\n\nRemarque : Tout achat actif de suppression des publicités sera conservé."
        else "Are you sure you want to reset all game data? This will permanently erase:\n• All completed missions, best times, and star ratings\n• Coin balance and gadget inventory\n• Audio, language, and control preferences\n\nNote: Any active Remove Ads purchase will be preserved."
    fun cancel(lang: String): String = if (isFrench(lang)) "ANNULER" else "CANCEL"
    fun resetEverything(lang: String): String = if (isFrench(lang)) "TOUT RÉINITIALISER" else "RESET EVERYTHING"
    fun resetSuccessToast(lang: String): String =
        if (isFrench(lang)) "PARAMÈTRES ET PROGRESSION RÉINITIALISÉS" else "SETTINGS & PROGRESS RESET TO DEFAULT"
    fun version(lang: String): String = "VERSION"
    fun build(lang: String): String = "BUILD"
    fun privacyPolicy(lang: String): String = if (isFrench(lang)) "POLITIQUE DE CONFIDENTIALITÉ" else "PRIVACY POLICY"
    fun contactUs(lang: String): String = if (isFrench(lang)) "CONTACTEZ-NOUS" else "CONTACT US"
    fun creditsLicenses(lang: String): String = if (isFrench(lang)) "CRÉDITS ET LICENCES" else "CREDITS & LICENSES"
    fun rateUs(lang: String): String = if (isFrench(lang)) "ÉVALUER LE JEU" else "RATE US"
    fun soundCredits(lang: String): String = if (isFrench(lang)) "CRÉDITS SONORES" else "SOUND CREDITS"
    fun close(lang: String): String = if (isFrench(lang)) "FERMER" else "CLOSE"

    // --- In-Game Gameplay Overlays & HUD ---
    fun loading(lang: String): String = if (isFrench(lang)) "C H A R G E M E N T . . ." else "L O A D I N G . . ."
    fun paused(lang: String): String = if (isFrench(lang)) "PAUSE" else "PAUSED"
    fun resume(lang: String): String = if (isFrench(lang)) "REPRENDRE" else "RESUME"
    fun restart(lang: String): String = if (isFrench(lang)) "RECOMMENCER" else "RESTART"
    fun quit(lang: String): String = if (isFrench(lang)) "QUITTER" else "QUIT"
    fun continueGame(lang: String): String = if (isFrench(lang)) "CONTINUER" else "CONTINUE"
    fun retry(lang: String): String = if (isFrench(lang)) "RÉESSAYER" else "RETRY"
    fun mainMenu(lang: String): String = if (isFrench(lang)) "MENU PRINCIPAL" else "MAIN MENU"
    fun nextMission(lang: String): String = if (isFrench(lang)) "MISSION SUIVANTE" else "NEXT MISSION"
    fun allClear(lang: String): String = if (isFrench(lang)) "TOUT EST CLAIR !" else "ALL CLEAR!"
    fun objectives(lang: String): String = if (isFrench(lang)) "OBJECTIFS" else "OBJECTIVES"
    fun optional(lang: String): String = if (isFrench(lang)) "(FACULTATIF)" else "(OPTIONAL)"
    fun finishUnder(time: String, lang: String): String = if (isFrench(lang)) "TERMINER EN MOINS DE $time" else "FINISH UNDER $time"
    fun noAlertsRaised(lang: String): String = if (isFrench(lang)) "AUCUNE ALERTE DÉCLENCHÉE" else "NO ALERTS RAISED"
    fun targetTime(time: String, lang: String): String = if (isFrench(lang)) "TEMPS CIBLE $time" else "TARGET TIME $time"
    fun bounty(lang: String): String = if (isFrench(lang)) "PRIME" else "BOUNTY"
}

fun LevelData.localizedName(lang: String): String {
    val translated = Localization.levelName(id, lang)
    return if (translated.isNotEmpty()) translated else name
}

fun LevelData.localizedDescription(lang: String): String {
    val translated = Localization.levelDescription(id, lang)
    return if (translated.isNotEmpty()) translated else description
}

fun LevelData.localizedObjectiveHint(lang: String): String {
    val translated = Localization.levelObjectiveHint(id, lang)
    return if (translated.isNotEmpty()) translated else objectiveHint
}
