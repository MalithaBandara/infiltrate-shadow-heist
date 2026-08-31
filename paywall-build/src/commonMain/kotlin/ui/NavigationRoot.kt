package com.infiltrate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AppScreen {
    MainMenu,
    LevelSelect,
    Store,
    Settings
}

@Composable
fun NavigationRoot(
    onStartLevel: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(AppScreen.MainMenu) }

    ShadowHeistTheme {
        when (currentScreen) {
            AppScreen.MainMenu -> {
                MainMenuScreen(
                    onPlayClicked = onStartLevel,
                    onMissionsClicked = { currentScreen = AppScreen.LevelSelect },
                    onStoreClicked = { currentScreen = AppScreen.Store },
                    onSettingsClicked = { currentScreen = AppScreen.Settings }
                )
            }
            AppScreen.LevelSelect -> {
                PlaceholderScreen(
                    title = "CLASSIFIED OPERATIONS",
                    subtitle = "SELECT INFILTRATION SECTOR",
                    badgeText = "MISSION REGISTRY [STUB]",
                    accentColor = ShadowTheme.AccentCyan,
                    onStartMission = onStartLevel,
                    onBack = { currentScreen = AppScreen.MainMenu }
                )
            }
            AppScreen.Store -> {
                PlaceholderScreen(
                    title = "BLACK MARKET DEPOT",
                    subtitle = "TACTICAL EQUIPMENT & CONTRABAND",
                    badgeText = "SHADOW PASS [STUB]",
                    accentColor = ShadowTheme.AccentGold,
                    onStartMission = null,
                    onBack = { currentScreen = AppScreen.MainMenu }
                )
            }
            AppScreen.Settings -> {
                PlaceholderScreen(
                    title = "TERMINAL CONFIGURATION",
                    subtitle = "AUDIO & OPERATIVE CONTROLS",
                    badgeText = "SYSTEM CONFIG [STUB]",
                    accentColor = ShadowTheme.TextMuted,
                    onStartMission = null,
                    onBack = { currentScreen = AppScreen.MainMenu }
                )
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    subtitle: String,
    badgeText: String,
    accentColor: Color,
    onStartMission: (() -> Unit)?,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ShadowTheme.BackgroundAtmosphere)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Column {
                Row(
                    modifier = Modifier
                        .background(Color(0xD90A0C0F), ShadowTheme.PillBadgeShape)
                        .border(1.dp, accentColor.copy(alpha = 0.6f), ShadowTheme.PillBadgeShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = badgeText,
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = title,
                    color = ShadowTheme.Primary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )

                Text(
                    text = subtitle,
                    color = ShadowTheme.TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
            }

            // Center Info Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ShadowTheme.BgCard.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .border(1.dp, ShadowTheme.BorderWhiteSubtle, RoundedCornerShape(12.dp))
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "CLASSIFIED MODULE UNDER CONSTRUCTION",
                        color = accentColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Full interface scheduled for migration pass. Core navigation validated.",
                        color = ShadowTheme.TextMuted,
                        fontSize = 12.sp
                    )

                    if (onStartMission != null) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Box(
                            modifier = Modifier
                                .background(ShadowTheme.PaperWhite, ShadowTheme.TacticalButtonShape)
                                .border(2.dp, ShadowTheme.Ink, ShadowTheme.TacticalButtonShape)
                                .clickable(onClick = onStartMission)
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "LAUNCH OPERATION 01",
                                color = ShadowTheme.Ink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            // Back Button at Bottom
            Box(
                modifier = Modifier
                    .width(260.dp)
                    .height(48.dp)
                    .background(Color(0xFF1B1E24), ShadowTheme.TacticalButtonShape)
                    .border(1.dp, ShadowTheme.BorderWhiteActive, ShadowTheme.TacticalButtonShape)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "◀ RETURN TO COMMAND",
                    color = ShadowTheme.Primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
        }
    }
}
