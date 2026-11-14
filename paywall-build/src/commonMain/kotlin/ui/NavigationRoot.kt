package com.infiltrate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.infiltrate.ads.cancelMenuInterstitialIfNotDisplayed
import com.infiltrate.ads.isMenuInterstitialActive
import com.infiltrate.ads.requestMenuInterstitial
import com.infiltrate.storage.PlatformStorage
import game.model.GameProfileStorage
import game.model.LevelData
import game.model.MapBackedGameProfileStorage
import game.model.MapBackedLevelStorage
import game.model.ProgressRepair
import kotlinx.coroutines.delay

/** How long a level start waits for a ready ad to reach the screen. Not a limit on the ad itself. */
private const val MENU_AD_DISPLAY_TIMEOUT_MS = 10_000L
private const val MENU_AD_POLL_MS = 100L

val LocalAppLanguage = compositionLocalOf { "en" }

enum class AppScreen {
    MainMenu,
    LevelSelect,
    Store,
    Settings
}

@Composable
fun NavigationRoot(
    onStartLevel: (levelId: String) -> Unit = {}
) {
    var currentScreen by remember { mutableStateOf(AppScreen.MainMenu) }
    var storeInitialTab by remember { mutableStateOf(StoreTab.POWER_UPS) }
    var settingsInitialTab by remember { mutableStateOf(SettingsTab.GENERAL) }

    // Music lives here rather than in MainMenuScreen so it plays continuously across the whole
    // menu. Started per-screen it would restart from the top every time the player backed out of
    // Missions or the Store, which on a 2:24 track is the only part anyone would ever hear.
    val profileStorage: GameProfileStorage = remember {
        MapBackedGameProfileStorage(
            getRaw = { PlatformStorage.getRaw(it) },
            setRaw = { k, v -> PlatformStorage.setRaw(k, v) }
        )
    }
    // Real state, not `remember(currentScreen) { profileStorage.getProfile()... }` - the latter
    // was the actual bug behind "the Settings sliders don't do anything": it only re-read storage
    // when currentScreen itself changed, so dragging a slider while still on the Settings screen
    // had no audible effect at all until the player navigated away and back. Read once here at
    // NavigationRoot's own creation (which already happens fresh every time the host reveals the
    // menu again - see MainActivity.kt's `if (!gameplayVisible)`), and from here on both are
    // updated directly by SettingsScreen's callbacks, so a slider drag recomposes MenuMusic/
    // rememberUiClick on the same frame instead of waiting for a screen change.
    // Recovery from build 33's overwritten saves (see ProgressRepair): unlocks and the
    // completed-level count from the untouched level results. Only ever adds, so it runs on every
    // menu start.
    LaunchedEffect(Unit) {
        runCatching {
            val levels = MapBackedLevelStorage(
                getRaw = { PlatformStorage.getRaw(it) },
                setRaw = { k, v -> PlatformStorage.setRaw(k, v) },
                removeRaw = { PlatformStorage.removeRaw(it) }
            )
            if (ProgressRepair.repair(profileStorage, levels)) println("[ProgressRepair] restored unlocks / level count from level results")
        }
    }
    var musicVolume by remember { mutableStateOf(profileStorage.getProfile().musicVolume) }
    var sfxVolume by remember { mutableStateOf(profileStorage.getProfile().sfxVolume) }
    var currentLanguage by remember { mutableStateOf(profileStorage.getProfile().language) }

    // Cold launch only: the intro video, then the logo, then this menu. The menu, its video and
    // its music are not composed at all until then, so nothing plays under the intro's own sound.
    var introDone by remember { mutableStateOf(IntroState.played) }
    if (!introDone) {
        IntroSequence(
            volume = musicVolume,
            language = currentLanguage,
            subtitles = profileStorage.getProfile().subtitlesOn(),
            onDone = {
                IntroState.played = true
                introDone = true
            }
        )
        return
    }

    MenuMusic(volume = musicVolume)

    // The menu's navigation buttons can each carry an interstitial, under the same limiter as every
    // other placement. Deliberately not on Store purchases and rewarded cards, the coin top-up
    // shortcuts, or the controls inside Settings: an ad there interrupts a purchase or a setting.
    fun requestAd(): Boolean {
        val profile = profileStorage.getProfile()
        return requestMenuInterstitial(profile.totalLevelsCompleted, profile.isPremium)
    }

    // Moving between menu screens needs no waiting: the screen changes underneath and the ad covers it.
    fun navigateWithAd(navigate: () -> Unit) {
        navigate()
        requestAd()
    }

    // Starting a level does wait, so that play never begins underneath the ad.
    var levelAwaitingAd by remember { mutableStateOf<String?>(null) }
    fun startLevelAfterAd(levelId: String) {
        if (levelAwaitingAd != null) return
        if (requestAd()) levelAwaitingAd = levelId else onStartLevel(levelId)
    }
    LaunchedEffect(levelAwaitingAd) {
        val levelId = levelAwaitingAd ?: return@LaunchedEffect
        var waitedMs = 0L
        while (isMenuInterstitialActive()) {
            if (waitedMs >= MENU_AD_DISPLAY_TIMEOUT_MS) cancelMenuInterstitialIfNotDisplayed()
            delay(MENU_AD_POLL_MS)
            waitedMs += MENU_AD_POLL_MS
        }
        levelAwaitingAd = null
        onStartLevel(levelId)
    }

    // The click is provided here, alongside the music, and for the same reason: every menu
    // screen needs it, and mounting it per-screen would rebuild the voice pool on every
    // navigation. UI_CLICK_RELATIVE_GAIN/MENU_CLIP_RELATIVE_GAIN keep these from playing at the
    // player's full raw SFX level - see their doc comment in MenuSfx.kt.
    val uiClick = rememberUiClick(volume = sfxVolume * UI_CLICK_RELATIVE_GAIN)
    val toastSuccess = rememberMenuClip(MenuClip.TOAST_SUCCESS, volume = sfxVolume * MENU_CLIP_RELATIVE_GAIN)
    val toastError = rememberMenuClip(MenuClip.TOAST_ERROR, volume = sfxVolume * MENU_CLIP_RELATIVE_GAIN)

    CompositionLocalProvider(
        LocalUiClick provides uiClick,
        LocalToastSuccess provides toastSuccess,
        LocalToastError provides toastError,
        LocalAppLanguage provides currentLanguage
    ) {
        ShadowHeistTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0E1115))
            ) {
                // Persistent MainMenuScreen at the root of the menu hierarchy.
                // Keeping this in composition avoids tearing down and restarting the looping
                // video background (which causes frame drops or surface cutouts) when backing
                // out of Settings, Store, or Missions.
                MainMenuScreen(
                    onPlayClicked = { levelId -> startLevelAfterAd(levelId) },
                    onMissionsClicked = { navigateWithAd { currentScreen = AppScreen.LevelSelect } },
                    onStoreClicked = {
                        navigateWithAd {
                            storeInitialTab = StoreTab.POWER_UPS
                            currentScreen = AppScreen.Store
                        }
                    },
                    onSettingsClicked = {
                        navigateWithAd {
                            settingsInitialTab = SettingsTab.GENERAL
                            currentScreen = AppScreen.Settings
                        }
                    },
                    refreshTrigger = currentScreen
                )

                if (currentScreen != AppScreen.MainMenu) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            )
                    ) {
                        when (currentScreen) {
                            AppScreen.MainMenu -> {}
                            AppScreen.LevelSelect -> {
                                LevelSelectScreen(
                                    onStartMission = { levelData ->
                                        println("[Navigation] Launching mission ${levelData.id}")
                                        startLevelAfterAd(levelData.id)
                                    },
                                    onStoreClicked = {
                                        storeInitialTab = StoreTab.COINS
                                        currentScreen = AppScreen.Store
                                    },
                                    onBackClicked = { navigateWithAd { currentScreen = AppScreen.MainMenu } }
                                )
                            }
                            AppScreen.Store -> {
                                StoreScreen(
                                    initialTab = storeInitialTab,
                                    onBackClicked = { navigateWithAd { currentScreen = AppScreen.MainMenu } }
                                )
                            }
                            AppScreen.Settings -> {
                                SettingsScreen(
                                    initialTab = settingsInitialTab,
                                    musicVolume = musicVolume,
                                    sfxVolume = sfxVolume,
                                    language = currentLanguage,
                                    onMusicVolumeChange = {
                                        musicVolume = it
                                        profileStorage.setMusicVolume(it)
                                    },
                                    onSfxVolumeChange = {
                                        sfxVolume = it
                                        profileStorage.setSfxVolume(it)
                                    },
                                    onLanguageChange = {
                                        currentLanguage = it
                                        profileStorage.setLanguage(it)
                                    },
                                    onBackClicked = { navigateWithAd { currentScreen = AppScreen.MainMenu } },
                                    onStoreShortcutClicked = {
                                        storeInitialTab = StoreTab.COINS
                                        currentScreen = AppScreen.Store
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
