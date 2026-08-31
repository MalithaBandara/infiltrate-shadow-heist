package com.infiltrate.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import game.model.LevelData

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
    var settingsInitialTab by remember { mutableStateOf(SettingsTab.AUDIO) }

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
                        settingsInitialTab = SettingsTab.AUDIO
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
