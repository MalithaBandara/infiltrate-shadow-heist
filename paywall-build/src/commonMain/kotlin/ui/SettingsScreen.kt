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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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

/** One entry in the Settings language dropdown - [nativeName] is written in that language's own script. */
data class LanguageOption(val code: String, val nativeName: String)

/**
 * Shortlist picked for Shipaton store-listing reach, not yet wired to any real translated
 * strings - see .junie/guidelines.md "Language dropdown" note. Ordered by priority tier
 * (Play+App Store combined impact, then Play-heavy download volume, then optional/smaller reach),
 * English first as the only language the app actually speaks right now.
 */
val SUPPORTED_LANGUAGES = listOf(
    LanguageOption("en", "English"),
    LanguageOption("es", "Español (Latinoamérica)"),
    LanguageOption("pt-BR", "Português (Brasil)"),
    LanguageOption("ja", "日本語"),
    LanguageOption("de", "Deutsch"),
    LanguageOption("fr", "Français"),
    LanguageOption("hi", "हिन्दी"),
    LanguageOption("id", "Bahasa Indonesia"),
    LanguageOption("vi", "Tiếng Việt"),
    LanguageOption("tr", "Türkçe"),
    LanguageOption("ar", "العربية"),
    LanguageOption("th", "ไทย"),
    LanguageOption("ko", "한국어"),
    LanguageOption("ru", "Русский"),
    LanguageOption("zh-TW", "繁體中文"),
)

@Composable
fun SettingsScreen(
    initialTab: SettingsTab = SettingsTab.GENERAL,
    // Owned by NavigationRoot, not here - it's the one already holding the live volume state that
    // MenuMusic/rememberUiClick actually play at, so a drag on either slider has to reach it
    // immediately rather than only being picked up on the way back out of this screen. See
    // NavigationRoot.kt's own comment on why that used to not work.
    musicVolume: Float,
    sfxVolume: Float,
    onMusicVolumeChange: (Float) -> Unit,
    onSfxVolumeChange: (Float) -> Unit,
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

    val toastSuccessSound = LocalToastSuccess.current
    val toastErrorSound = LocalToastError.current

    fun showToast(msg: String, isSuccess: Boolean) {
        toastMessage = msg
        toastIsSuccess = isSuccess
        if (isSuccess) toastSuccessSound() else toastErrorSound()
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
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
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
                                musicVolume = musicVolume,
                                sfxVolume = sfxVolume,
                                controlsSwapped = controlsSwapped,
                                font = bebasFont,
                                scale = scale,
                                onLanguageChange = { lang ->
                                    currentLanguage = lang
                                    profileStorage.setLanguage(lang)
                                },
                                onMusicChange = onMusicVolumeChange,
                                onSfxChange = onSfxVolumeChange,
                                onControlsSwapChange = { swapped ->
                                    controlsSwapped = swapped
                                    profileStorage.setControlsSwapped(swapped)
                                },
                                onResetProgress = {
                                    // Music/SFX go through the callbacks (NavigationRoot owns
                                    // that state and persists it) - controls/language are still
                                    // local to this screen, so they're reset directly.
                                    onMusicVolumeChange(0.8f)
                                    onSfxVolumeChange(1.0f)
                                    profileStorage.setControlsSwapped(false)
                                    profileStorage.setLanguage("en")
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
    val click = LocalUiClick.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
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
        val currentLanguageOption = SUPPORTED_LANGUAGES.find { it.code == selectedLanguage } ?: SUPPORTED_LANGUAGES[0]
        var languageMenuExpanded by remember { mutableStateOf(false) }
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
                Column {
                    Text(
                        text = "LANGUAGE",
                        color = Color.White,
                        fontSize = (15 * scale).sp,
                        fontFamily = font,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Select your language (translations rolling out soon)",
                        color = Color(0xFF9A9A9E),
                        fontSize = (12 * scale).sp
                    )
                }
            }

            // Active Language Selector Pill + Dropdown
            var pillHeightPx by remember { mutableStateOf(0) }
            val density = LocalDensity.current
            Box {
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { pillHeightPx = it.size.height }
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .border(1.dp, Color.White, RoundedCornerShape(6.dp))
                        .clickable(
                            interactionSource = langInteractionSource,
                            indication = null,
                            onClick = { click(); languageMenuExpanded = true }
                        )
                        .padding(horizontal = (14 * scale).dp, vertical = (8 * scale).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy((6 * scale).dp)
                    ) {
                        Text(
                            text = currentLanguageOption.nativeName,
                            color = Color.White,
                            fontSize = (12 * scale).sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "▾",
                            color = Color.White,
                            fontSize = (12 * scale).sp
                        )
                    }
                }

                if (languageMenuExpanded) {
                    Popup(
                        alignment = Alignment.TopEnd,
                        offset = IntOffset(0, pillHeightPx + with(density) { 4.dp.roundToPx() }),
                        onDismissRequest = { languageMenuExpanded = false },
                        properties = PopupProperties(focusable = true)
                    ) {
                        val scrollState = rememberScrollState()
                        var viewportHeightPx by remember { mutableStateOf(0) }
                        Box {
                            Column(
                                modifier = Modifier
                                    .width((190 * scale).dp)
                                    .background(Color(0xFF141416), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                    .heightIn(max = 320.dp)
                                    .onGloballyPositioned { viewportHeightPx = it.size.height }
                                    .verticalScroll(scrollState)
                                    .padding(vertical = 4.dp)
                            ) {
                                for (option in SUPPORTED_LANGUAGES) {
                                    val isSelected = option.code == selectedLanguage
                                    val itemInteractionSource = remember { MutableInteractionSource() }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                interactionSource = itemInteractionSource,
                                                indication = null,
                                                onClick = {
                                                    click()
                                                    languageMenuExpanded = false
                                                    onLanguageChange(option.code)
                                                }
                                            )
                                            .padding(horizontal = (16 * scale).dp, vertical = (10 * scale).dp)
                                    ) {
                                        Text(
                                            text = option.nativeName,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                                            fontSize = (13 * scale).sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }

                            // Small scroll-position indicator - only shown once there's more content than
                            // fits (scrollState.maxValue > 0), so a short list never shows a useless thumb.
                            if (scrollState.maxValue > 0 && viewportHeightPx > 0) {
                                val totalContentPx = viewportHeightPx + scrollState.maxValue
                                val thumbHeightFraction =
                                    (viewportHeightPx.toFloat() / totalContentPx).coerceIn(0.08f, 1f)
                                val thumbProgress = scrollState.value.toFloat() / scrollState.maxValue
                                val trackHeightDp = with(density) { viewportHeightPx.toDp() }
                                val thumbHeightDp = trackHeightDp * thumbHeightFraction
                                val thumbOffsetDp = (trackHeightDp - thumbHeightDp) * thumbProgress

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 4.dp, end = 3.dp)
                                        .offset(y = thumbOffsetDp)
                                        .width(3.dp)
                                        .height(thumbHeightDp)
                                        .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    }
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
                modifier = Modifier.weight(1f),
                onValueChangeFinished = click
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
                modifier = Modifier.weight(1f),
                onValueChangeFinished = click
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
                            if (!controlsSwapped) Color.White.copy(alpha = 0.15f) else Color(0xFF1C1C20),
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            if (!controlsSwapped) Color.White else Color.White.copy(alpha = 0.12f),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable(
                            interactionSource = defaultInteractionSource,
                            indication = null,
                            onClick = { click(); onControlsSwapChange(false) }
                        )
                        .padding(horizontal = (14 * scale).dp, vertical = (8 * scale).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "DEFAULT (LEFT)",
                        color = if (!controlsSwapped) Color.White else Color.White.copy(alpha = 0.7f),
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
                            if (controlsSwapped) Color.White.copy(alpha = 0.15f) else Color(0xFF1C1C20),
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            if (controlsSwapped) Color.White else Color.White.copy(alpha = 0.12f),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable(
                            interactionSource = swappedInteractionSource,
                            indication = null,
                            onClick = { click(); onControlsSwapChange(true) }
                        )
                        .padding(horizontal = (14 * scale).dp, vertical = (8 * scale).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SWAPPED (RIGHT)",
                        color = if (controlsSwapped) Color.White else Color.White.copy(alpha = 0.7f),
                        fontSize = (12 * scale).sp,
                        fontFamily = font,
                        fontWeight = if (controlsSwapped) FontWeight.Bold else FontWeight.Normal,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // weight(1f) doesn't work here now that the panel scrolls (no bounded height to
        // distribute) - a fixed gap keeps Reset Progress visually separated from the options
        // above it instead of jammed directly underneath.
        Spacer(modifier = Modifier.height((28 * scale).dp))

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
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
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
        var creditsExpanded by remember { mutableStateOf(false) }
        val links = listOf("PRIVACY POLICY", "TERMS OF SERVICE", "CREDITS & LICENSES")
        for (link in links) {
            val interactionSource = remember { MutableInteractionSource() }
            val isCredits = link == "CREDITS & LICENSES"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141416), RoundedCornerShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            if (isCredits) creditsExpanded = !creditsExpanded
                            else onActionToast("$link OPENED")
                        }
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
                        text = if (isCredits && creditsExpanded) "▼" else "▶",
                        color = Color(0xFF6E6E72),
                        fontSize = (12 * scale).sp
                    )
                }
            }

            if (isCredits) {
                AnimatedVisibility(visible = creditsExpanded, enter = fadeIn(), exit = fadeOut()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = (8 * scale).dp),
                        verticalArrangement = Arrangement.spacedBy((10 * scale).dp)
                    ) {
                        for (credit in SOUND_CREDITS) {
                            SoundCreditRow(credit = credit, font = font, scale = scale)
                        }
                    }
                }
            }
        }

        // weight(1f) doesn't work here now that the panel scrolls (no bounded height to
        // distribute) - a fixed gap keeps the version line separated from the links above it.
        Spacer(modifier = Modifier.height((28 * scale).dp))

        Text(
            text = "INFILTRATE: SHADOW HEIST • VERSION 1.0.0 (BUILD 2026.1)",
            color = Color(0xFF6E6E72),
            fontSize = (11 * scale).sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp
        )
    }
}

/** One third-party sound credited under "CREDITS & LICENSES" - see `ATTRIBUTION.md` for the full record. */
private data class SoundCredit(val cue: String, val creditLine: String)

private val SOUND_CREDITS = listOf(
    SoundCredit("Menu music", "Nikita Kondrashev, via Pixabay"),
    SoundCredit("Button tap", "Kenney (kenney.nl), Creative Commons Zero"),
    SoundCredit("Success chime", "Sjonas88, via Freesound, Creative Commons Zero"),
    SoundCredit("Error tone", "Kastenfrosch, via Freesound, Creative Commons Zero"),
    SoundCredit("Guard alert", "Sadiquecat, via Freesound, Creative Commons Zero"),
    SoundCredit("Camera alert", "ryanconway, via Freesound, Creative Commons Zero"),
    SoundCredit("Mission music", "DELOSound, via Pixabay"),
)

@Composable
private fun SoundCreditRow(credit: SoundCredit, font: FontFamily, scale: Float) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = (4 * scale).dp)) {
        Text(
            text = credit.cue.uppercase(),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = (12 * scale).sp,
            fontFamily = font,
            letterSpacing = 0.5.sp
        )
        Text(
            text = credit.creditLine,
            color = Color(0xFF6E6E72),
            fontSize = (11 * scale).sp
        )
    }
}
