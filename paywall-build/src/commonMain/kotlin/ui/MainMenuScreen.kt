package com.infiltrate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infiltrate.storage.PlatformStorage
import game.model.GameProfile
import game.model.GameProfileStorage
import game.model.MapBackedGameProfileStorage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MainMenuScreen(
    onPlayClicked: () -> Unit,
    onMissionsClicked: () -> Unit,
    onStoreClicked: () -> Unit,
    onSettingsClicked: () -> Unit
) {
    val profileStorage: GameProfileStorage = remember {
        MapBackedGameProfileStorage(
            getRaw = { PlatformStorage.getRaw(it) },
            setRaw = { k, v -> PlatformStorage.setRaw(k, v) }
        )
    }
    val profile: GameProfile = remember { profileStorage.getProfile() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ShadowTheme.BackgroundAtmosphere)
    ) {
        val screenWidth = maxWidth
        val isCompact = screenWidth < 600.dp

        // Background tactical grid & skyline skyline accents
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawGridAndAtmosphere(size.width, size.height)
        }

        // Left silhouette dark fade (ensures button stack and title legibility on any aspect)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(if (isCompact) screenWidth else 480.dp)
                .background(ShadowTheme.LeftSilhouetteGradient)
        )

        // Main layout container
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isCompact) 16.dp else 32.dp, vertical = 20.dp)
        ) {
            // Top Bar: Operative rank status + Heist Coins pill badge
            TopProfileBar(
                coins = profile.coins,
                unlockedCount = profile.unlockedLevelIds.size,
                isPremium = profile.isPremium
            )

            Spacer(modifier = Modifier.height(if (isCompact) 16.dp else 28.dp))

            // Main Content Area (Split: Left = Title & Buttons, Right = Dossier Card)
            Box(modifier = Modifier.fillMaxSize()) {
                // Left Column: Branding + Tactical Buttons
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .widthIn(max = if (isCompact) screenWidth else 380.dp)
                ) {
                    // Branding / Logo Header
                    BrandingHeader()

                    Spacer(modifier = Modifier.height(if (isCompact) 20.dp else 32.dp))

                    // Tactical Heist Buttons Stack
                    TacticalButton(
                        text = "PLAY",
                        iconRenderer = { drawInkPlay() },
                        onClick = onPlayClicked
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    TacticalButton(
                        text = "MISSIONS",
                        iconRenderer = { drawInkTarget() },
                        onClick = onMissionsClicked
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    TacticalButton(
                        text = "STORE",
                        iconRenderer = { drawInkCart() },
                        onClick = onStoreClicked
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    TacticalButton(
                        text = "SETTINGS",
                        iconRenderer = { drawInkGear() },
                        onClick = onSettingsClicked
                    )
                }

                // Bottom-Right: Classified Mission Dossier Card (hidden on ultra-narrow portrait to prevent overlap)
                if (!isCompact) {
                    MissionDossierCard(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 8.dp, end = 8.dp),
                        onClick = onMissionsClicked
                    )
                }
            }
        }
    }
}

@Composable
private fun TopProfileBar(
    coins: Int,
    unlockedCount: Int,
    isPremium: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Operative Status Badge
        Row(
            modifier = Modifier
                .background(Color(0xD90A0C0F), ShadowTheme.PillBadgeShape)
                .border(1.dp, ShadowTheme.BorderCyan, ShadowTheme.PillBadgeShape)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(modifier = Modifier.size(10.dp)) {
                drawCircle(color = ShadowTheme.AccentCyan, radius = size.minDimension / 2f)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "OPERATIVE STATUS",
                color = ShadowTheme.AccentCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "[$unlockedCount/4 SECTORS]",
                color = ShadowTheme.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Coins & Premium Badges
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isPremium) {
                Box(
                    modifier = Modifier
                        .background(Color(0x33FFD700), ShadowTheme.PillBadgeShape)
                        .border(1.dp, ShadowTheme.AccentGold, ShadowTheme.PillBadgeShape)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "VIP PASS",
                        color = ShadowTheme.AccentGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Coin Pill Badge
            Row(
                modifier = Modifier
                    .background(Color(0xD90A0C0F), ShadowTheme.PillBadgeShape)
                    .border(1.dp, ShadowTheme.BorderWhiteSubtle, ShadowTheme.PillBadgeShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(modifier = Modifier.size(12.dp)) {
                    drawGoldDiamond()
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$coins",
                    color = ShadowTheme.Primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "HEIST COINS",
                    color = ShadowTheme.AccentGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun BrandingHeader() {
    Column {
        Text(
            text = "INFILTRATE",
            color = ShadowTheme.Primary,
            fontSize = 44.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 3.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(6.dp)) {
                drawRect(ShadowTheme.AccentCyan)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SHADOW HEIST",
                color = ShadowTheme.AccentCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(1.dp)
                    .background(ShadowTheme.AccentCyan.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
private fun TacticalButton(
    text: String,
    iconRenderer: DrawScope.() -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor = if (isPressed) Color(0xFFD8D4C8) else ShadowTheme.PaperWhite

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(bgColor, ShadowTheme.TacticalButtonShape)
            .border(2.dp, ShadowTheme.Ink, ShadowTheme.TacticalButtonShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Canvas(modifier = Modifier.size(24.dp)) {
                iconRenderer()
            }
            Spacer(modifier = Modifier.width(18.dp))
            Text(
                text = text,
                color = ShadowTheme.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
private fun MissionDossierCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgAlpha = if (isPressed) 0.98f else 0.92f
    val borderAlpha = if (isPressed) 0.35f else 0.15f

    Box(
        modifier = modifier
            .width(320.dp)
            .background(ShadowTheme.BgCard.copy(alpha = bgAlpha), ShadowTheme.DossierCardShape)
            .border(1.dp, Color.White.copy(alpha = borderAlpha), ShadowTheme.DossierCardShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(18.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        drawFolderIcon()
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "MISSION 03",
                        color = Color(0xFF9A9A9E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .background(Color(0x3300E5FF), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "ACTIVE",
                        color = ShadowTheme.AccentCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "THE WAREHOUSE",
                color = ShadowTheme.Primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Infiltrate the perimeter warehouse and retrieve classified stolen telemetry files.",
                color = Color(0xFFB7B7BC),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

// Canvas Drawing Routines matching MainMenuScene.kt & UiComponents.kt exactly

private fun DrawScope.drawGridAndAtmosphere(w: Float, h: Float) {
    // Subtle tactical grid background
    val step = 40f
    val gridColor = Color(0x08FFFFFF)
    var x = 0f
    while (x < w) {
        drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y < h) {
        drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        y += step
    }
}

private fun DrawScope.drawGoldDiamond() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val w = size.width * 0.45f
    val h = size.height * 0.55f
    val path = Path().apply {
        moveTo(cx, cy - h)
        lineTo(cx + w, cy)
        lineTo(cx, cy + h)
        lineTo(cx - w, cy)
        close()
    }
    drawPath(path, color = ShadowTheme.AccentGold)
}

private fun DrawScope.drawInkPlay() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val path = Path().apply {
        moveTo(cx - 7f, cy - 10f)
        lineTo(cx + 9f, cy)
        lineTo(cx - 7f, cy + 10f)
        close()
    }
    drawPath(path, color = ShadowTheme.Ink)
}

private fun DrawScope.drawInkTarget() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = 8f
    drawCircle(color = ShadowTheme.Ink, radius = r, style = Stroke(width = 2.5f))
    drawCircle(color = ShadowTheme.Ink, radius = 2.5f)
    drawLine(ShadowTheme.Ink, Offset(cx, cy - 13f), Offset(cx, cy - 9f), strokeWidth = 2.5f, cap = StrokeCap.Square)
    drawLine(ShadowTheme.Ink, Offset(cx, cy + 9f), Offset(cx, cy + 13f), strokeWidth = 2.5f, cap = StrokeCap.Square)
    drawLine(ShadowTheme.Ink, Offset(cx - 13f, cy), Offset(cx - 9f, cy), strokeWidth = 2.5f, cap = StrokeCap.Square)
    drawLine(ShadowTheme.Ink, Offset(cx + 9f, cy), Offset(cx + 13f, cy), strokeWidth = 2.5f, cap = StrokeCap.Square)
}

private fun DrawScope.drawInkCart() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val path = Path().apply {
        moveTo(cx - 10f, cy - 7f)
        lineTo(cx - 7f, cy - 7f)
        lineTo(cx - 4f, cy + 4f)
        lineTo(cx + 7f, cy + 4f)
        lineTo(cx + 9f, cy - 3f)
        lineTo(cx - 6f, cy - 3f)
    }
    drawPath(path, color = ShadowTheme.Ink, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
    drawCircle(color = ShadowTheme.Ink, radius = 2f, center = Offset(cx - 3f, cy + 8f))
    drawCircle(color = ShadowTheme.Ink, radius = 2f, center = Offset(cx + 5f, cy + 8f))
}

private fun DrawScope.drawInkGear() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val ringR = 6f
    drawCircle(color = ShadowTheme.Ink, radius = ringR, style = Stroke(width = 2.5f))
    val innerR = 5f
    val outerR = 10f
    for (i in 0 until 8) {
        val angle = i * PI / 4.0
        val ux = cos(angle).toFloat()
        val uy = sin(angle).toFloat()
        drawLine(
            color = ShadowTheme.Ink,
            start = Offset(cx + ux * innerR, cy + uy * innerR),
            end = Offset(cx + ux * outerR, cy + uy * outerR),
            strokeWidth = 3f,
            cap = StrokeCap.Square
        )
    }
}

private fun DrawScope.drawFolderIcon() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val path = Path().apply {
        moveTo(cx - 9f, cy - 6f)
        lineTo(cx - 3f, cy - 6f)
        lineTo(cx - 1f, cy - 4f)
        lineTo(cx + 9f, cy - 4f)
        lineTo(cx + 9f, cy + 6f)
        lineTo(cx - 9f, cy + 6f)
        close()
    }
    drawPath(path, color = Color(0xFFECE7DA))
    drawPath(path, color = Color(0x66000000), style = Stroke(width = 1f))
}
