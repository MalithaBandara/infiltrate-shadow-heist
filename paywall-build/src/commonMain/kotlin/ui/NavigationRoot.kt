package com.infiltrate.ui

import androidx.compose.runtime.Composable
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
    // Re-read on every screen change so a move of the Settings slider takes effect on the way
    // back out, without the music needing to observe storage itself.
    val musicVolume = remember(currentScreen) { profileStorage.getProfile().musicVolume }
    MenuMusic(volume = musicVolume)

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
