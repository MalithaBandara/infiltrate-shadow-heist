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
        val isCompact = screenWidth < 600.dp

        // 1. Cinematic Background Image (bg12.png) - right anchored cover
        Image(
            painter = painterResource(Res.drawable.bg12),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Left silhouette dark gradient fade ensuring full contrast for title & button stack
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(if (isCompact) screenWidth else 480.dp)
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color(0xF206080A),
                        0.5f to Color(0xCC06080A),
                        0.8f to Color(0x6606080A),
                        1.0f to Color.Transparent
                    )
                )
        )

        // 3. Main Content Layout
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (isCompact) 16.dp else 48.dp,
                    end = 24.dp,
                    top = if (isCompact) 20.dp else 36.dp,
                    bottom = 24.dp
                )
        ) {
            // Left Column: Logo + Buttons
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(if (isCompact) screenWidth - 32.dp else 320.dp)
            ) {
                // Logo Image (logo_main.png)
                Image(
                    painter = painterResource(Res.drawable.logo_main),
                    contentDescription = "INFILTRATE: SHADOW HEIST",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(if (isCompact) 240.dp else 300.dp)
                        .height(if (isCompact) 70.dp else 90.dp)
                )

                Spacer(modifier = Modifier.height(if (isCompact) 18.dp else 28.dp))

                // Textured Heist Buttons Stack
                HeistTexturedButton(
                    text = "PLAY",
                    texture = Res.drawable.button1,
                    font = bebasFont,
                    iconRenderer = { drawInkPlay() },
                    onClick = onPlayClicked
                )
                Spacer(modifier = Modifier.height(12.dp))

                HeistTexturedButton(
                    text = "MISSIONS",
                    texture = Res.drawable.button2,
                    font = bebasFont,
                    iconRenderer = { drawInkTarget() },
                    onClick = onMissionsClicked
                )
                Spacer(modifier = Modifier.height(12.dp))

                HeistTexturedButton(
                    text = "STORE",
                    texture = Res.drawable.button3,
                    font = bebasFont,
                    iconRenderer = { drawInkCart() },
                    onClick = onStoreClicked
                )
                Spacer(modifier = Modifier.height(12.dp))

                HeistTexturedButton(
                    text = "SETTINGS",
                    texture = Res.drawable.button4,
                    font = bebasFont,
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
    iconRenderer: DrawScope.() -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
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
                    .background(Color.Black.copy(alpha = 0.15f))
            )
        }

        // Icon + Label Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 22.dp)
        ) {
            Canvas(modifier = Modifier.size(24.dp)) {
                iconRenderer()
            }
            Spacer(modifier = Modifier.width(18.dp))
            Text(
                text = text,
                color = ShadowTheme.Ink,
                fontSize = 24.sp,
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                letterSpacing = 1.5.sp
            )
        }
    }
}

@Composable
private fun MissionDossierCard(
    modifier: Modifier = Modifier,
    font: FontFamily,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgAlpha = if (isPressed) 0.98f else 0.92f
    val borderAlpha = if (isPressed) 0.25f else 0.10f

    Box(
        modifier = modifier
            .width(310.dp)
            .height(120.dp)
            .background(Color(0xFF0A0A0B).copy(alpha = bgAlpha), RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = borderAlpha), RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxSize()
        ) {
            Canvas(
                modifier = Modifier
                    .size(28.dp)
                    .padding(top = 2.dp)
            ) {
                drawFolderIcon()
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = "MISSION 03",
                    color = Color(0xFF9A9A9E),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "THE WAREHOUSE",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontFamily = font,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Infiltrate the warehouse and\nretrieve the stolen files.",
                    color = Color(0xFFB7B7BC),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

// Icon Drawing Routines matching MainMenuScene.kt exactly

private fun DrawScope.drawInkPlay() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val path = Path().apply {
        moveTo(cx - 7f, cy - 10f)
        lineTo(cx + 10f, cy)
        lineTo(cx - 7f, cy + 10f)
        close()
    }
    drawPath(path, color = ShadowTheme.Ink)
}

private fun DrawScope.drawInkTarget() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = 11f
    drawCircle(color = ShadowTheme.Ink, radius = r, style = Stroke(width = 3f))
    drawCircle(color = ShadowTheme.Ink, radius = 3.2f)
    drawLine(ShadowTheme.Ink, Offset(cx, cy - 16f), Offset(cx, cy - 11f), strokeWidth = 3f, cap = StrokeCap.Square)
    drawLine(ShadowTheme.Ink, Offset(cx, cy + 11f), Offset(cx, cy + 16f), strokeWidth = 3f, cap = StrokeCap.Square)
    drawLine(ShadowTheme.Ink, Offset(cx - 16f, cy), Offset(cx - 11f, cy), strokeWidth = 3f, cap = StrokeCap.Square)
    drawLine(ShadowTheme.Ink, Offset(cx + 11f, cy), Offset(cx + 16f, cy), strokeWidth = 3f, cap = StrokeCap.Square)
}

private fun DrawScope.drawInkCart() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val path = Path().apply {
        moveTo(cx - 12f, cy - 9f)
        lineTo(cx - 9f, cy - 9f)
        lineTo(cx - 5.5f, cy + 5f)
        lineTo(cx + 9f, cy + 5f)
        lineTo(cx + 11.5f, cy - 3.5f)
        lineTo(cx - 8f, cy - 3.5f)
    }
    drawPath(path, color = ShadowTheme.Ink, style = Stroke(width = 2.8f, cap = StrokeCap.Round))
    drawCircle(color = ShadowTheme.Ink, radius = 2.4f, center = Offset(cx - 4f, cy + 10f))
    drawCircle(color = ShadowTheme.Ink, radius = 2.4f, center = Offset(cx + 7f, cy + 10f))
}

private fun DrawScope.drawInkGear() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val ringR = 7.5f
    drawCircle(color = ShadowTheme.Ink, radius = ringR, style = Stroke(width = 3.2f))
    val innerR = 6.3f
    val outerR = 12.0f
    for (i in 0 until 8) {
        val angle = i * PI / 4.0
        val ux = cos(angle).toFloat()
        val uy = sin(angle).toFloat()
        drawLine(
            color = ShadowTheme.Ink,
            start = Offset(cx + ux * innerR, cy + uy * innerR),
            end = Offset(cx + ux * outerR, cy + uy * outerR),
            strokeWidth = 3.2f,
            cap = StrokeCap.Square
        )
    }
}

private fun DrawScope.drawFolderIcon() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val path = Path().apply {
        moveTo(cx - 12f, cy - 7f)
        lineTo(cx - 4f, cy - 7f)
        lineTo(cx - 1f, cy - 4f)
        lineTo(cx + 12f, cy - 4f)
        lineTo(cx + 12f, cy + 9f)
        lineTo(cx - 12f, cy + 9f)
        close()
    }
    drawPath(path, color = Color(0xFFECE7DA))
    drawPath(path, color = Color.Black.copy(alpha = 0.25f), style = Stroke(width = 1f))
}
