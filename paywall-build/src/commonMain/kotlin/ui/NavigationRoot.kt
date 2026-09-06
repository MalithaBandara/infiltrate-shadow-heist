package com.infiltrate.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.infiltrate.storage.PlatformStorage
import game.model.GameProfileStorage
import game.model.LevelData
import game.model.MapBackedGameProfileStorage

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
    var musicVolume by remember { mutableStateOf(profileStorage.getProfile().musicVolume) }
    var sfxVolume by remember { mutableStateOf(profileStorage.getProfile().sfxVolume) }
    MenuMusic(volume = musicVolume)

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
        LocalToastError provides toastError
    ) {
        ShadowHeistTheme {
            when (currentScreen) {
                AppScreen.MainMenu -> {
                    MainMenuScreen(
                        onPlayClicked = {
                            // Default to Mission 1 if clicked directly from MainMenu
                            onStartLevel("level_1")
                        },
                        onMissionsClicked = { currentScreen = AppScreen.LevelSelect },
                        onStoreClicked = {
                            storeInitialTab = StoreTab.POWER_UPS
                            currentScreen = AppScreen.Store
                        },
                        onSettingsClicked = {
                            settingsInitialTab = SettingsTab.GENERAL
                            currentScreen = AppScreen.Settings
                        }
                    )
                }
                AppScreen.LevelSelect -> {
                    LevelSelectScreen(
                        onStartMission = { levelData ->
                            println("[Navigation] Launching mission ${levelData.id}")
                            onStartLevel(levelData.id)
                        },
                        onStoreClicked = {
                            storeInitialTab = StoreTab.COINS
                            currentScreen = AppScreen.Store
                        },
                        onBackClicked = { currentScreen = AppScreen.MainMenu }
                    )
                }
                AppScreen.Store -> {
                    StoreScreen(
                        initialTab = storeInitialTab,
                        onBackClicked = { currentScreen = AppScreen.MainMenu }
                    )
                }
                AppScreen.Settings -> {
                    SettingsScreen(
                        initialTab = settingsInitialTab,
                        musicVolume = musicVolume,
                        sfxVolume = sfxVolume,
                        onMusicVolumeChange = {
                            musicVolume = it
                            profileStorage.setMusicVolume(it)
                        },
                        onSfxVolumeChange = {
                            sfxVolume = it
                            profileStorage.setSfxVolume(it)
                        },
                        onBackClicked = { currentScreen = AppScreen.MainMenu },
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
