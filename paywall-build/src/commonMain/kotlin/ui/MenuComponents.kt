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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import paywall_build.generated.resources.Res
import paywall_build.generated.resources.logo_main
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// --- Top Bar ---

@Composable
fun MenuTopBar(
    title: String,
    font: FontFamily,
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier,
    statPills: @Composable () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(74.dp)
            .background(Color(0xFF0B0B0D))
            .padding(horizontal = 24.dp, vertical = 10.dp)
    ) {
        // Left: Back Button + Branding
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(if (isPressed) Color(0xFF242428) else Color(0xFF18181B), RoundedCornerShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onBackClicked
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(16.dp)) {
                    drawBackChevron(Color.White)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Graphic Logo Mark (enlarged for high clarity and visibility)
            Image(
                painter = painterResource(Res.drawable.logo_main),
                contentDescription = "Infiltrate: Shadow Heist",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(48.dp)
                    .width(180.dp)
            )
        }

        // Center: Screen Title
        Text(
            text = title,
            color = Color.White,
            fontSize = 26.sp,
            fontFamily = font,
            letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.Center)
        )

        // Right: Stats / Pills
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            statPills()
        }
    }
}

// --- Stat Pill ---

@Composable
fun StatPill(
    label: String,
    icon: DrawScope.() -> Unit,
    modifier: Modifier = Modifier,
    pillWidth: Dp = 100.dp,
    pillHeight: Dp = 34.dp
) {
    Box(
        modifier = modifier
            .width(pillWidth)
            .height(pillHeight)
            .background(Color(0xFF18181B), RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Canvas(modifier = Modifier.size(16.dp)) {
                icon()
            }
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// --- Interactive Coin Pill with + Button ---

@Composable
fun CoinPill(
    coins: Int,
    onPlusClicked: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    pillHeight: Dp = 34.dp
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(pillHeight)
            .background(Color(0xFF18181B), RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(start = 10.dp, end = if (onPlusClicked != null) 4.dp else 12.dp)
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            drawCoinIcon(Color(0xFFFFD54F))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$coins",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (onPlusClicked != null) {
            Spacer(modifier = Modifier.width(8.dp))
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(
                        if (isPressed) Color(0xFFFFD54F).copy(alpha = 0.35f) else Color(0xFFFFD54F).copy(alpha = 0.2f),
                        RoundedCornerShape(6.dp)
                    )
                    .border(1.dp, Color(0xFFFFD54F).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onPlusClicked
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    color = Color(0xFFFFD54F),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

// --- Textured Sidebar Tab ---

@Composable
fun TexturedSidebarTab(
    text: String,
    isSelected: Boolean,
    texture: DrawableResource,
    font: FontFamily,
    iconRenderer: DrawScope.(Color) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tabHeight: Dp = 48.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val iconColor = if (isSelected) ShadowTheme.Ink else Color.White
    val textColor = if (isSelected) ShadowTheme.Ink else Color.White

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(tabHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        if (isSelected) {
            Image(
                painter = painterResource(texture),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF141417), RoundedCornerShape(6.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp)
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                iconRenderer(iconColor)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                color = textColor,
                fontSize = 18.sp,
                fontFamily = font,
                letterSpacing = 1.sp
            )
        }
    }
}

// --- Volume Slider ---

@Composable
fun VolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color(0xFF2A2A2E)
            ),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "${(value * 100).roundToInt()}%",
            color = Color(0xFFC9C9CC),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(42.dp)
        )
    }
}

// --- Icons & Drawing Helpers ---

fun DrawScope.drawBackChevron(c: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val path = Path().apply {
        moveTo(cx + 4f, cy - 8f)
        lineTo(cx - 4f, cy)
        lineTo(cx + 4f, cy + 8f)
    }
    drawPath(path, color = c, style = Stroke(width = 2.4f))
}

fun DrawScope.drawStar(
    cx: Float,
    cy: Float,
    outerR: Float,
    innerR: Float,
    color: Color = Color(0xFFFFD54F)
) {
    val path = Path()
    val points = 5
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outerR else innerR
        val angle = i * PI.toFloat() / points - PI.toFloat() / 2f
        val x = cx + r * cos(angle)
        val y = cy + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = color, style = Fill)
}

fun DrawScope.drawBriefcaseIcon(c: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val s = 1.1f
    drawRoundRect(
        color = c,
        topLeft = Offset(cx - 3f * s, cy - 5.5f * s),
        size = Size(6f * s, 4f * s),
        cornerRadius = CornerRadius(1.5f * s, 1.5f * s),
        style = Stroke(width = 1.5f * s)
    )
    drawRoundRect(
        color = c,
        topLeft = Offset(cx - 7f * s, cy - 2f * s),
        size = Size(14f * s, 10f * s),
        cornerRadius = CornerRadius(2.5f * s, 2.5f * s)
    )
    drawLine(
        color = Color(0xFF18181B),
        start = Offset(cx - 7f * s, cy + 2f * s),
        end = Offset(cx + 7f * s, cy + 2f * s),
        strokeWidth = 1.2f * s
    )
}

fun DrawScope.drawPadlockIcon(c: Color) {
    drawLockIcon(c, 1.1f)
}

fun DrawScope.drawLockIcon(c: Color, s: Float = 1.0f) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawCircle(
        color = c,
        radius = 4f * s,
        center = Offset(cx, cy - 2.5f * s),
        style = Stroke(width = 1.8f * s)
    )
    drawRoundRect(
        color = c,
        topLeft = Offset(cx - 6f * s, cy - 1.5f * s),
        size = Size(12f * s, 9f * s),
        cornerRadius = CornerRadius(2f * s, 2f * s)
    )
}

fun DrawScope.drawCoinIcon(c: Color = Color(0xFFFFD54F)) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = minOf(size.width, size.height) * 0.44f

    // 1. Dark outer drop shadow
    drawCircle(
        color = Color(0xFF3E2723).copy(alpha = 0.6f),
        radius = r,
        center = Offset(cx, cy + 1f)
    )

    // 2. Outer rim (Rich warm gold)
    drawCircle(
        color = Color(0xFFFFB300),
        radius = r,
        center = Offset(cx, cy)
    )

    // 3. Highlight bevel on top edge
    drawCircle(
        color = Color(0xFFFFE57F),
        radius = r,
        center = Offset(cx, cy),
        style = Stroke(width = 1.6f)
    )

    // 4. Recessed inner face (Vibrant gold)
    drawCircle(
        color = Color(0xFFFFD54F),
        radius = r * 0.76f,
        center = Offset(cx, cy)
    )

    // 5. Inner grooved ring
    drawCircle(
        color = Color(0xFFC67100).copy(alpha = 0.7f),
        radius = r * 0.76f,
        center = Offset(cx, cy),
        style = Stroke(width = 1.0f)
    )

    // 6. Embossed Tactical Diamond Emblem at center
    val emblemR = r * 0.40f
    val path = Path().apply {
        moveTo(cx, cy - emblemR)
        lineTo(cx + emblemR * 0.75f, cy)
        lineTo(cx, cy + emblemR)
        lineTo(cx - emblemR * 0.75f, cy)
        close()
    }
    // Shadow under emblem
    drawPath(
        path = path,
        color = Color(0xFF9E5700),
        style = Fill
    )
    // Gleam on emblem center
    drawCircle(
        color = Color.White.copy(alpha = 0.9f),
        radius = 1.2f,
        center = Offset(cx - 0.8f, cy - 0.8f)
    )
}

fun DrawScope.drawBoltIcon(c: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val path = Path().apply {
        moveTo(cx + 2f, cy - 8f)
        lineTo(cx - 5f, cy + 1f)
        lineTo(cx - 1f, cy + 1f)
        lineTo(cx - 2f, cy + 8f)
        lineTo(cx + 5f, cy - 1f)
        lineTo(cx + 1f, cy - 1f)
        close()
    }
    drawPath(path, color = c, style = Fill)
}

fun DrawScope.drawCoinStackIcon(c: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val rx = size.width * 0.42f
    val ry = size.height * 0.18f

    val coinYs = listOf(cy + size.height * 0.22f, cy, cy - size.height * 0.22f)

    for (coinY in coinYs) {
        // Coin side thickness (3D cylinder wall)
        val sidePath = Path().apply {
            moveTo(cx - rx, coinY)
            lineTo(cx - rx, coinY + 3.5f)
            arcTo(
                rect = Rect(cx - rx, coinY + 3.5f - ry, cx + rx, coinY + 3.5f + ry),
                startAngleDegrees = 180f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false
            )
            lineTo(cx + rx, coinY)
            close()
        }
        drawPath(sidePath, color = c.copy(alpha = 0.55f))

        // Outer coin face
        drawOval(
            color = c,
            topLeft = Offset(cx - rx, coinY - ry),
            size = Size(rx * 2f, ry * 2f)
        )
        // Inner rim groove / highlight
        drawOval(
            color = if (c == Color.White) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.35f),
            topLeft = Offset(cx - rx * 0.82f, coinY - ry * 0.82f),
            size = Size(rx * 1.64f, ry * 1.64f),
            style = Stroke(width = 1.0f)
        )
    }
}

fun DrawScope.drawSmokeIcon(c: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawCircle(color = c, radius = 6.5f, center = Offset(cx, cy + 3f))
    drawLine(c, Offset(cx - 5f, cy - 2f), Offset(cx - 8f, cy - 7f), strokeWidth = 1.6f)
    drawLine(c, Offset(cx, cy - 4f), Offset(cx, cy - 10f), strokeWidth = 1.6f)
    drawLine(c, Offset(cx + 5f, cy - 2f), Offset(cx + 8f, cy - 7f), strokeWidth = 1.6f)
}

fun DrawScope.drawCloakIcon(c: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val path = Path().apply {
        moveTo(cx, cy - 9f)
        lineTo(cx + 7f, cy - 3f)
        lineTo(cx + 8f, cy + 8f)
        lineTo(cx + 3f, cy + 6f)
        lineTo(cx, cy + 9f)
        lineTo(cx - 3f, cy + 6f)
        lineTo(cx - 8f, cy + 8f)
        lineTo(cx - 7f, cy - 3f)
        close()
    }
    drawPath(path, color = c)
    drawLine(Color.Black.copy(alpha = 0.35f), Offset(cx, cy - 9f), Offset(cx, cy + 9f), strokeWidth = 1.4f)
}

fun DrawScope.drawInvisIcon(c: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawOval(color = c, topLeft = Offset(cx - 9f, cy - 5f), size = Size(18f, 10f), style = Stroke(width = 1.6f))
    drawLine(c, Offset(cx - 8f, cy + 6f), Offset(cx + 8f, cy - 6f), strokeWidth = 1.6f)
    drawCircle(color = c, radius = 2.8f, center = Offset(cx, cy))
}

fun DrawScope.drawBootIcon(c: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val path = Path().apply {
        moveTo(cx - 3f, cy - 10f)
        lineTo(cx + 3f, cy - 10f)
        lineTo(cx + 3f, cy + 2f)
        lineTo(cx + 10f, cy + 4f)
        lineTo(cx + 11f, cy + 7f)
        lineTo(cx + 9f, cy + 9f)
        lineTo(cx - 7f, cy + 9f)
        lineTo(cx - 7f, cy + 5f)
        lineTo(cx - 3f, cy + 2f)
        close()
    }
    drawPath(path, color = c)
}

fun DrawScope.drawSpeakerIcon(c: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val path = Path().apply {
        moveTo(cx - 7f, cy - 2f)
        lineTo(cx - 3f, cy - 2f)
        lineTo(cx + 2f, cy - 7f)
        lineTo(cx + 2f, cy + 7f)
        lineTo(cx - 3f, cy + 2f)
        lineTo(cx - 7f, cy + 2f)
        close()
    }
    drawPath(path, color = c)
    drawLine(c, Offset(cx + 6f, cy - 4f), Offset(cx + 8f, cy - 2f), strokeWidth = 1.4f)
    drawLine(c, Offset(cx + 8f, cy - 2f), Offset(cx + 8f, cy + 2f), strokeWidth = 1.4f)
    drawLine(c, Offset(cx + 8f, cy + 2f), Offset(cx + 6f, cy + 4f), strokeWidth = 1.4f)
}

fun DrawScope.drawInfoIcon(c: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawCircle(color = c, radius = 7.5f, center = Offset(cx, cy), style = Stroke(width = 1.5f))
    drawCircle(color = c, radius = 1.3f, center = Offset(cx, cy - 3.5f))
    drawRoundRect(
        color = c,
        topLeft = Offset(cx - 1f, cy - 1f),
        size = Size(2f, 5.5f),
        cornerRadius = CornerRadius(0.8f, 0.8f)
    )
}
