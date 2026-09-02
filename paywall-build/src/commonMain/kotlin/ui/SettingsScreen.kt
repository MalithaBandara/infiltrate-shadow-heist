package com.infiltrate.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infiltrate.storage.PlatformStorage
import game.model.GameProfile
import game.model.GameProfileStorage
import game.model.MapBackedGameProfileStorage
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.Font
import paywall_build.generated.resources.Res
import paywall_build.generated.resources.bebas_neue_regular
import paywall_build.generated.resources.button1
import paywall_build.generated.resources.button2

enum class SettingsTab {
    GENERAL,
    ABOUT
}

@Composable
fun SettingsScreen(
    initialTab: SettingsTab = SettingsTab.GENERAL,
    onBackClicked: () -> Unit,
    onStoreShortcutClicked: () -> Unit = {}
) {
    val profileStorage: GameProfileStorage = remember {
        MapBackedGameProfileStorage(
            getRaw = { PlatformStorage.getRaw(it) },
            setRaw = { k, v -> PlatformStorage.setRaw(k, v) }
        )
    }

    var profile by remember { mutableStateOf(profileStorage.getProfile()) }
    var currentTab by remember { mutableStateOf(initialTab) }
    var musicVol by remember { mutableStateOf(profile.musicVolume) }
    var sfxVol by remember { mutableStateOf(profile.sfxVolume) }
    var controlsSwapped by remember { mutableStateOf(profile.controlsSwapped) }
    var currentLanguage by remember { mutableStateOf(profile.language) }
    val bebasFont = FontFamily(Font(Res.font.bebas_neue_regular))

    var toastMessage by remember { mutableStateOf<String?>(null) }
    var toastIsSuccess by remember { mutableStateOf(true) }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2200)
            toastMessage = null
        }
    }

    fun showToast(msg: String, isSuccess: Boolean) {
        toastMessage = msg
        toastIsSuccess = isSuccess
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0D))
    ) {
        val screenHeight = maxHeight
        val scale = (screenHeight / 720.dp).coerceIn(0.75f, 1.4f)

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            MenuTopBar(
                title = "SETTINGS",
                font = bebasFont,
                onBackClicked = onBackClicked
            )

            // Content: Sidebar + Main Area
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = (24 * scale).dp, vertical = (16 * scale).dp),
                horizontalArrangement = Arrangement.spacedBy((20 * scale).dp)
            ) {
                // --- Sidebar ---
                Column(
                    modifier = Modifier
                        .width((220 * scale).dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TexturedSidebarTab(
                        text = "GENERAL",
                        isSelected = currentTab == SettingsTab.GENERAL,
                        texture = Res.drawable.button1,
                        font = bebasFont,
                        iconRenderer = { color -> drawGearIcon(color) },
                        onClick = { currentTab = SettingsTab.GENERAL },
                        tabHeight = (46 * scale).dp
                    )

                    TexturedSidebarTab(
                        text = "ABOUT",
                        isSelected = currentTab == SettingsTab.ABOUT,
                        texture = Res.drawable.button2,
                        font = bebasFont,
                        iconRenderer = { color -> drawInfoIcon(color) },
                        onClick = { currentTab = SettingsTab.ABOUT },
                        tabHeight = (46 * scale).dp
                    )
                }

                // --- Main Content Area ---
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    when (currentTab) {
                        SettingsTab.GENERAL -> {
                            GeneralSettingsPanel(
                                selectedLanguage = currentLanguage,
                                musicVolume = musicVol,
                                sfxVolume = sfxVol,
                                controlsSwapped = controlsSwapped,
                                font = bebasFont,
                                scale = scale,
                                onLanguageChange = { lang ->
                                    currentLanguage = lang
                                    profileStorage.setLanguage(lang)
                                    showToast("LANGUAGE: ENGLISH (ACTIVE)", true)
                                },
                                onMusicChange = {
                                    musicVol = it
                                    profileStorage.setMusicVolume(it)
                                },
                                onSfxChange = {
                                    sfxVol = it
                                    profileStorage.setSfxVolume(it)
                                },
                                onControlsSwapChange = { swapped ->
                                    controlsSwapped = swapped
                                    profileStorage.setControlsSwapped(swapped)
                                    showToast(
                                        if (swapped) "CONTROLS: MOVEMENT ON RIGHT (SWAPPED)" else "CONTROLS: MOVEMENT ON LEFT (DEFAULT)",
                                        true
                                    )
                                },
                                onResetProgress = {
                                    // Reset profile storage
                                    profileStorage.setMusicVolume(0.8f)
                                    profileStorage.setSfxVolume(1.0f)
                                    profileStorage.setControlsSwapped(false)
                                    profileStorage.setLanguage("en")
                                    musicVol = 0.8f
                                    sfxVol = 1.0f
                                    controlsSwapped = false
                                    currentLanguage = "en"
                                    showToast("SETTINGS & PROGRESS RESET TO DEFAULT", false)
                                }
                            )
                        }
                        SettingsTab.ABOUT -> {
                            AboutSettingsPanel(
                                font = bebasFont,
                                scale = scale,
                                onActionToast = { showToast(it, true) }
                            )
                        }
                    }
                }
            }
        }

        // Floating Toast Notification at Root Screen Level
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        if (toastIsSuccess) Color(0xFF00E5FF).copy(alpha = 0.95f) else Color(0xFFFF5252).copy(alpha = 0.95f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    text = toastMessage ?: "",
                    color = Color(0xFF0A0A0C),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun GeneralSettingsPanel(
    selectedLanguage: String,
    musicVolume: Float,
    sfxVolume: Float,
    controlsSwapped: Boolean,
    font: FontFamily,
    scale: Float,
    onLanguageChange: (String) -> Unit,
    onMusicChange: (Float) -> Unit,
    onSfxChange: (Float) -> Unit,
    onControlsSwapChange: (Boolean) -> Unit,
    onResetProgress: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy((14 * scale).dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GENERAL CONFIGURATION",
                color = Color.White,
                fontSize = (18 * scale).sp,
                fontFamily = font,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.12f))
            )
        }

        // 1. Language Row (at top of General)
        val isEnglish = selectedLanguage == "en"
        val langInteractionSource = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141416), RoundedCornerShape(8.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                .padding((14 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Language Icon / Badge
                Box(
                    modifier = Modifier
                        .size((32 * scale).dp)
                        .background(Color(0xFF00E5FF).copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "EN",
                        color = Color(0xFF00E5FF),
                        fontSize = (12 * scale).sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = font
                    )
                }

                Spacer(modifier = Modifier.width((12 * scale).dp))

                Column {
                    Text(
                        text = "LANGUAGE",
                        color = Color.White,
                        fontSize = (15 * scale).sp,
                        fontFamily = font,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Game UI and operational briefings (English)",
                        color = Color(0xFF9A9A9E),
                        fontSize = (12 * scale).sp
                    )
                }
            }

            // Active Language Selector Pill
            Box(
                modifier = Modifier
                    .background(
                        if (isEnglish) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF1C1C20),
                        RoundedCornerShape(6.dp)
                    )
                    .border(
                        1.dp,
                        if (isEnglish) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.12f),
                        RoundedCornerShape(6.dp)
                    )
                    .clickable(
                        interactionSource = langInteractionSource,
                        indication = null,
                        onClick = { onLanguageChange("en") }
                    )
                    .padding(horizontal = (14 * scale).dp, vertical = (8 * scale).dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((6 * scale).dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size((6 * scale).dp)
                            .background(Color(0xFF00E5FF), CircleShape)
                    )
                    Text(
                        text = "ENGLISH",
                        color = Color(0xFF00E5FF),
                        fontSize = (12 * scale).sp,
                        fontFamily = font,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // 2. Music Volume Slider Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141416), RoundedCornerShape(8.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                .padding((14 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.width((200 * scale).dp)) {
                Text(
                    text = "MUSIC VOLUME",
                    color = Color.White,
                    fontSize = (15 * scale).sp,
                    fontFamily = font,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Background music level",
                    color = Color(0xFF9A9A9E),
                    fontSize = (12 * scale).sp
                )
            }
            VolumeSlider(
                value = musicVolume,
                onValueChange = onMusicChange,
                modifier = Modifier.weight(1f)
            )
        }

        // 3. SFX Volume Slider Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141416), RoundedCornerShape(8.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                .padding((14 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.width((200 * scale).dp)) {
                Text(
                    text = "SFX VOLUME",
                    color = Color.White,
                    fontSize = (15 * scale).sp,
                    fontFamily = font,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Tactical sound effects",
                    color = Color(0xFF9A9A9E),
                    fontSize = (12 * scale).sp
                )
            }
            VolumeSlider(
                value = sfxVolume,
                onValueChange = onSfxChange,
                modifier = Modifier.weight(1f)
            )
        }

        // 4. Controls Side Orientation Option Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141416), RoundedCornerShape(8.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                .padding((14 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = (16 * scale).dp)) {
                Text(
                    text = "CONTROLS LAYOUT",
                    color = Color.White,
                    fontSize = (15 * scale).sp,
                    fontFamily = font,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Choose screen side for movement buttons (Actions on opposite side)",
                    color = Color(0xFF9A9A9E),
                    fontSize = (12 * scale).sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy((10 * scale).dp)) {
                // Default: Move Left, Actions Right
                val defaultInteractionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .background(
                            if (!controlsSwapped) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF1C1C20),
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            if (!controlsSwapped) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.12f),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable(
                            interactionSource = defaultInteractionSource,
                            indication = null,
                            onClick = { onControlsSwapChange(false) }
                        )
                        .padding(horizontal = (14 * scale).dp, vertical = (8 * scale).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "DEFAULT (LEFT)",
                        color = if (!controlsSwapped) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.7f),
                        fontSize = (12 * scale).sp,
                        fontFamily = font,
                        fontWeight = if (!controlsSwapped) FontWeight.Bold else FontWeight.Normal,
                        letterSpacing = 1.sp
                    )
                }

                // Swapped: Move Right, Actions Left
                val swappedInteractionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .background(
                            if (controlsSwapped) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF1C1C20),
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            if (controlsSwapped) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.12f),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable(
                            interactionSource = swappedInteractionSource,
                            indication = null,
                            onClick = { onControlsSwapChange(true) }
                        )
                        .padding(horizontal = (14 * scale).dp, vertical = (8 * scale).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SWAPPED (RIGHT)",
                        color = if (controlsSwapped) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.7f),
                        fontSize = (12 * scale).sp,
                        fontFamily = font,
                        fontWeight = if (controlsSwapped) FontWeight.Bold else FontWeight.Normal,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 5. Reset Progress (Danger Zone)
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2A1416), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onResetProgress
                )
                .padding((14 * scale).dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "RESET PROGRESS & SETTINGS",
                        color = Color(0xFFFF5252),
                        fontSize = (15 * scale).sp,
                        fontFamily = font,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Clear all mission progress, inventory, and configuration defaults",
                        color = Color(0xFF9A9A9E),
                        fontSize = (12 * scale).sp
                    )
                }
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFF5252), RoundedCornerShape(4.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "RESET",
                        color = Color(0xFF0A0A0C),
                        fontSize = (12 * scale).sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutSettingsPanel(
    font: FontFamily,
    scale: Float,
    onActionToast: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy((14 * scale).dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ABOUT INFILTRATE",
                color = Color.White,
                fontSize = (18 * scale).sp,
                fontFamily = font,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.12f))
            )
        }

        // Links
        val links = listOf("PRIVACY POLICY", "TERMS OF SERVICE", "CREDITS & LICENSES")
        for (link in links) {
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141416), RoundedCornerShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onActionToast("$link OPENED") }
                    )
                    .padding((16 * scale).dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = link,
                        color = Color.White,
                        fontSize = (14 * scale).sp,
                        fontFamily = font,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "▶",
                        color = Color(0xFF6E6E72),
                        fontSize = (12 * scale).sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "INFILTRATE: SHADOW HEIST • VERSION 1.0.0 (BUILD 2026.1)",
            color = Color(0xFF6E6E72),
            fontSize = (11 * scale).sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp
        )
    }
}
