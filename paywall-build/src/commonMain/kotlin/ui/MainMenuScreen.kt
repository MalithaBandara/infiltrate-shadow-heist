package com.infiltrate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infiltrate.storage.PlatformStorage
import game.model.GameProfile
import game.model.GameProfileStorage
import game.model.MapBackedGameProfileStorage
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import paywall_build.generated.resources.Res
import paywall_build.generated.resources.bebas_neue_regular
import paywall_build.generated.resources.bg12
import paywall_build.generated.resources.button1
import paywall_build.generated.resources.button2
import paywall_build.generated.resources.button3
import paywall_build.generated.resources.button4
import paywall_build.generated.resources.logo_main
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
    val bebasFont = FontFamily(Font(Res.font.bebas_neue_regular))

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1115))
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val isCompact = screenWidth < 700.dp

        // Reference 720p scale factor for widescreen phone / desktop displays
        val scale = if (isCompact) 0.65f else (screenHeight / 720.dp).coerceIn(0.85f, 1.4f)

        val logoWidth = if (isCompact) 260.dp else (460 * scale).dp
        val logoHeight = if (isCompact) 76.dp else (135 * scale).dp

        val buttonWidth = if (isCompact) screenWidth - 32.dp else (480 * scale).dp
        val buttonHeight = if (isCompact) 56.dp else (84 * scale).dp
        val buttonSpacing = if (isCompact) 10.dp else (16 * scale).dp
        val buttonFontSize = if (isCompact) 24.sp else (36 * scale).sp
        val iconSize = if (isCompact) 24.dp else (36 * scale).dp

        val startMargin = if (isCompact) 16.dp else (72 * scale).dp
        val topMargin = if (isCompact) 20.dp else (42 * scale).dp

        // 1. Looping Video Background (bg1080p.mp4) with bg12.png fallback
        LoopingVideoBackground(
            modifier = Modifier.fillMaxSize(),
            videoName = "bg1080p",
            videoExtension = "mp4",
            fallbackDrawable = Res.drawable.bg12
        )

        // 2. Left silhouette dark gradient fade ensuring full contrast for title & button stack
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(if (isCompact) screenWidth else (680 * scale).dp)
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color(0xF206080A),
                        0.45f to Color(0xDD06080A),
                        0.75f to Color(0x7706080A),
                        1.0f to Color.Transparent
                    )
                )
        )

        // 3. Main Content Layout
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = startMargin,
                    end = 28.dp,
                    top = topMargin,
                    bottom = 28.dp
                )
        ) {
            // Left Column: Logo + Buttons
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(buttonWidth)
            ) {
                // Logo Image (logo_main.png)
                Image(
                    painter = painterResource(Res.drawable.logo_main),
                    contentDescription = "INFILTRATE: SHADOW HEIST",
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier
                        .width(logoWidth)
                        .height(logoHeight)
                )

                Spacer(modifier = Modifier.height(if (isCompact) 14.dp else (24 * scale).dp))

                // Textured Heist Buttons Stack
                HeistTexturedButton(
                    text = "PLAY",
                    texture = Res.drawable.button1,
                    font = bebasFont,
                    buttonHeight = buttonHeight,
                    fontSize = buttonFontSize,
                    iconSize = iconSize,
                    iconRenderer = { drawInkPlay() },
                    onClick = onPlayClicked
                )
                Spacer(modifier = Modifier.height(buttonSpacing))

                HeistTexturedButton(
                    text = "MISSIONS",
                    texture = Res.drawable.button2,
                    font = bebasFont,
                    buttonHeight = buttonHeight,
                    fontSize = buttonFontSize,
                    iconSize = iconSize,
                    iconRenderer = { drawInkTarget() },
                    onClick = onMissionsClicked
                )
                Spacer(modifier = Modifier.height(buttonSpacing))

                HeistTexturedButton(
                    text = "STORE",
                    texture = Res.drawable.button3,
                    font = bebasFont,
                    buttonHeight = buttonHeight,
                    fontSize = buttonFontSize,
                    iconSize = iconSize,
                    iconRenderer = { drawInkCart() },
                    onClick = onStoreClicked
                )
                Spacer(modifier = Modifier.height(buttonSpacing))

                HeistTexturedButton(
                    text = "SETTINGS",
                    texture = Res.drawable.button4,
                    font = bebasFont,
                    buttonHeight = buttonHeight,
                    fontSize = buttonFontSize,
                    iconSize = iconSize,
                    iconRenderer = { drawInkGear() },
                    onClick = onSettingsClicked
                )
            }

            // Bottom-Right: Classified Mission Dossier Card
            MissionDossierCard(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 8.dp, end = 8.dp),
                font = bebasFont,
                scale = scale,
                isCompact = isCompact,
                onClick = onMissionsClicked
            )
        }
    }
}

@Composable
private fun HeistTexturedButton(
    text: String,
    texture: DrawableResource,
    font: FontFamily,
    buttonHeight: Dp,
    fontSize: androidx.compose.ui.unit.TextUnit,
    iconSize: Dp,
    iconRenderer: DrawScope.() -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(buttonHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        // Baked worn poster texture background
        Image(
            painter = painterResource(texture),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )

        // Press overlay scrim
        if (isPressed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
            )
        }

        // Icon + Label Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = buttonHeight * 0.4f)
        ) {
            Canvas(modifier = Modifier.size(iconSize)) {
                iconRenderer()
            }
            Spacer(modifier = Modifier.width(buttonHeight * 0.35f))
            Text(
                text = text,
                color = ShadowTheme.Ink,
                fontSize = fontSize,
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
private fun MissionDossierCard(
    modifier: Modifier = Modifier,
    font: FontFamily,
    scale: Float,
    isCompact: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgAlpha = if (isPressed) 0.98f else 0.92f
    val borderAlpha = if (isPressed) 0.25f else 0.10f

    val cardWidth = if (isCompact) 280.dp else (440 * scale).dp
    val cardHeight = if (isCompact) 110.dp else (165 * scale).dp

    Box(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .background(Color(0xFF0A0A0B).copy(alpha = bgAlpha), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = borderAlpha), RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(if (isCompact) 14.dp else (22 * scale).dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxSize()
        ) {
            Canvas(
                modifier = Modifier
                    .size(if (isCompact) 28.dp else (38 * scale).dp)
                    .padding(top = 4.dp)
            ) {
                drawFolderIcon()
            }

            Spacer(modifier = Modifier.width(if (isCompact) 14.dp else (20 * scale).dp))

            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = "MISSION 03",
                    color = Color(0xFF9A9A9E),
                    fontSize = if (isCompact) 11.sp else (15 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "THE WAREHOUSE",
                    color = Color.White,
                    fontSize = if (isCompact) 22.sp else (32 * scale).sp,
                    fontFamily = font,
                    letterSpacing = 1.5.sp
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = "Infiltrate the warehouse and\nretrieve the stolen files.",
                    color = Color(0xFFB7B7BC),
                    fontSize = if (isCompact) 11.sp else (15 * scale).sp,
                    lineHeight = if (isCompact) 14.sp else (19 * scale).sp
                )
            }
        }
    }
}

// Icon Drawing Routines scaled to match button icon bounding box

private fun DrawScope.drawInkPlay() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val scale = size.width / 24f
    val path = Path().apply {
        moveTo(cx - 7f * scale, cy - 10f * scale)
        lineTo(cx + 10f * scale, cy)
        lineTo(cx - 7f * scale, cy + 10f * scale)
        close()
    }
    drawPath(path, color = ShadowTheme.Ink)
}

private fun DrawScope.drawInkTarget() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val scale = size.width / 24f
    val r = 11f * scale
    val thickness = 3.0f * scale
    drawCircle(color = ShadowTheme.Ink, radius = r, style = Stroke(width = thickness))
    drawCircle(color = ShadowTheme.Ink, radius = 3.2f * scale)
    drawLine(ShadowTheme.Ink, Offset(cx, cy - 16f * scale), Offset(cx, cy - 11f * scale), strokeWidth = thickness, cap = StrokeCap.Square)
    drawLine(ShadowTheme.Ink, Offset(cx, cy + 11f * scale), Offset(cx, cy + 16f * scale), strokeWidth = thickness, cap = StrokeCap.Square)
    drawLine(ShadowTheme.Ink, Offset(cx - 16f * scale, cy), Offset(cx - 11f * scale, cy), strokeWidth = thickness, cap = StrokeCap.Square)
    drawLine(ShadowTheme.Ink, Offset(cx + 11f * scale, cy), Offset(cx + 16f * scale, cy), strokeWidth = thickness, cap = StrokeCap.Square)
}

private fun DrawScope.drawInkCart() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val scale = size.width / 24f
    val path = Path().apply {
        moveTo(cx - 12f * scale, cy - 9f * scale)
        lineTo(cx - 9f * scale, cy - 9f * scale)
        lineTo(cx - 5.5f * scale, cy + 5f * scale)
        lineTo(cx + 9f * scale, cy + 5f * scale)
        lineTo(cx + 11.5f * scale, cy - 3.5f * scale)
        lineTo(cx - 8f * scale, cy - 3.5f * scale)
    }
    drawPath(path, color = ShadowTheme.Ink, style = Stroke(width = 2.8f * scale, cap = StrokeCap.Round))
    drawCircle(color = ShadowTheme.Ink, radius = 2.4f * scale, center = Offset(cx - 4f * scale, cy + 10f * scale))
    drawCircle(color = ShadowTheme.Ink, radius = 2.4f * scale, center = Offset(cx + 7f * scale, cy + 10f * scale))
}

private fun DrawScope.drawInkGear() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val scale = size.width / 24f
    val ringR = 7.5f * scale
    val thickness = 3.2f * scale
    drawCircle(color = ShadowTheme.Ink, radius = ringR, style = Stroke(width = thickness))
    val innerR = 6.3f * scale
    val outerR = 12.0f * scale
    for (i in 0 until 8) {
        val angle = i * PI / 4.0
        val ux = cos(angle).toFloat()
        val uy = sin(angle).toFloat()
        drawLine(
            color = ShadowTheme.Ink,
            start = Offset(cx + ux * innerR, cy + uy * innerR),
            end = Offset(cx + ux * outerR, cy + uy * outerR),
            strokeWidth = thickness,
            cap = StrokeCap.Square
        )
    }
}

private fun DrawScope.drawFolderIcon() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val scale = size.width / 28f
    val path = Path().apply {
        moveTo(cx - 12f * scale, cy - 7f * scale)
        lineTo(cx - 4f * scale, cy - 7f * scale)
        lineTo(cx - 1f * scale, cy - 4f * scale)
        lineTo(cx + 12f * scale, cy - 4f * scale)
        lineTo(cx + 12f * scale, cy + 9f * scale)
        lineTo(cx - 12f * scale, cy + 9f * scale)
        close()
    }
    drawPath(path, color = Color(0xFFECE7DA))
    drawPath(path, color = Color.Black.copy(alpha = 0.25f), style = Stroke(width = 1f * scale))
}
