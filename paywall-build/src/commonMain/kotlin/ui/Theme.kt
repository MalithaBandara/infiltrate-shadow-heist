package com.infiltrate.ui

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object ShadowTheme {
    val BgDark = Color(0xFF0E1115)
    val BgDeep = Color(0xFF16161D)
    val BgCard = Color(0xFF0A0A0B)
    val Ink = Color(0xFF17140F)
    val PaperWhite = Color(0xFFF6F4EE)
    val PaperHover = Color(0xFFFFFFFF)

    val Primary = Color(0xFFFFFFFF)
    val TextLight = Color(0xFFF0F0F5)
    val TextMuted = Color(0xFF8A95A5)
    val TextDim = Color(0xFF5A6472)

    val AccentCyan = Color(0xFF00E5FF)
    val AccentGold = Color(0xFFFFD700)
    val AccentRed = Color(0xFFFF2A55)
    val AccentGreen = Color(0xFF00E676)

    val BorderCyan = Color(0x6600E5FF)
    val BorderWhiteSubtle = Color(0x1FFFFFFF)
    val BorderWhiteActive = Color(0x40FFFFFF)

    val TacticalButtonShape = CutCornerShape(topStart = 0.dp, topEnd = 10.dp, bottomEnd = 0.dp, bottomStart = 10.dp)
    val DossierCardShape = RoundedCornerShape(10.dp)
    val PillBadgeShape = RoundedCornerShape(16.dp)

    val LeftSilhouetteGradient = Brush.horizontalGradient(
        0.0f to Color(0xF806080A),
        0.35f to Color(0xD8080B0F),
        0.65f to Color(0x800E1115),
        1.0f to Color.Transparent
    )

    val BackgroundAtmosphere = Brush.verticalGradient(
        0.0f to Color(0xFF12141A),
        0.5f to Color(0xFF0E1015),
        1.0f to Color(0xFF07080B)
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = ShadowTheme.Primary,
    background = ShadowTheme.BgDark,
    surface = ShadowTheme.BgCard,
    onPrimary = ShadowTheme.Ink,
    onBackground = ShadowTheme.TextLight,
    onSurface = ShadowTheme.TextLight
)

@Composable
fun ShadowHeistTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
