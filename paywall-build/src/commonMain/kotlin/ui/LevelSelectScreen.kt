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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infiltrate.storage.PlatformStorage
import game.model.GameProfile
import game.model.GameProfileStorage
import game.model.LevelData
import game.model.LevelResult
import game.model.LevelStorage
import game.model.MapBackedGameProfileStorage
import game.model.MapBackedLevelStorage
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import paywall_build.generated.resources.Res
import paywall_build.generated.resources.bebas_neue_regular
import paywall_build.generated.resources.bg_menu
import kotlin.math.roundToInt

@Composable
fun LevelSelectScreen(
    onStartMission: (LevelData) -> Unit,
    onStoreClicked: () -> Unit = {},
    onBackClicked: () -> Unit
) {
    val levelStorage: LevelStorage = remember {
        MapBackedLevelStorage(
            getRaw = { PlatformStorage.getRaw(it) },
            setRaw = { k, v -> PlatformStorage.setRaw(k, v) }
        )
    }
    val profileStorage: GameProfileStorage = remember {
        MapBackedGameProfileStorage(
            getRaw = { PlatformStorage.getRaw(it) },
            setRaw = { k, v -> PlatformStorage.setRaw(k, v) }
        )
    }

    val profile: GameProfile = remember { profileStorage.getProfile() }
    val allResults: Map<String, LevelResult> = remember { levelStorage.getAllResults() }
    val levels = LevelData.DEFAULT_LEVELS
    val bebasFont = FontFamily(Font(Res.font.bebas_neue_regular))

    val completedCount = levels.count { allResults[it.id]?.completed == true }
    val starsEarned = levels.sumOf { allResults[it.id]?.starCount ?: 0 }
    val starsMax = levels.size * 3

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
                title = "MISSIONS",
                font = bebasFont,
                onBackClicked = onBackClicked,
                statPills = {
                    StatPill(
                        label = "$starsEarned/$starsMax",
                        icon = { drawStar(size.width / 2f, size.height / 2f, 7f, 2.8f, Color(0xFFFFD54F)) },
                        pillWidth = 95.dp
                    )
                    CoinPill(
                        coins = profile.coins,
                        onPlusClicked = onStoreClicked
                    )
                }
            )

            // Main Content Area
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = (30 * scale).dp, vertical = (12 * scale).dp)
            ) {
                // Chapter Cards Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((110 * scale).dp),
                    horizontalArrangement = Arrangement.spacedBy((16 * scale).dp)
                ) {
                    for (i in 0 until 4) {
                        val isReal = (i == 0)
                        ChapterCard(
                            title = if (isReal) "SHIPYARD" else "COMING SOON",
                            isUnlocked = isReal,
                            starsText = "$starsEarned/$starsMax",
                            font = bebasFont,
                            scale = scale,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height((18 * scale).dp))

                // Section Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SHIPYARD",
                        color = Color.White,
                        fontSize = (18 * scale).sp,
                        fontFamily = bebasFont,
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

                Spacer(modifier = Modifier.height((16 * scale).dp))

                // Mission Cards Grid
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy((16 * scale).dp)
                ) {
                    for ((index, levelData) in levels.withIndex()) {
                        val result = allResults[levelData.id]
                        val isUnlocked = index == 0 || (allResults[levels[index - 1].id]?.completed == true)
                        val requiresPremium = levelData.id.contains("dlc")
                        val canPlay = isUnlocked && (!requiresPremium || profile.isPremium)

                        MissionCard(
                            index = index + 1,
                            levelData = levelData,
                            result = result,
                            isUnlocked = isUnlocked,
                            canPlay = canPlay,
                            font = bebasFont,
                            scale = scale,
                            onClick = { if (canPlay) onStartMission(levelData) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterCard(
    title: String,
    isUnlocked: Boolean,
    starsText: String,
    font: FontFamily,
    scale: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(Color(0xFF141416), RoundedCornerShape(8.dp))
            .border(
                1.dp,
                if (isUnlocked) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(8.dp)
            )
    ) {
        if (isUnlocked) {
            Image(
                painter = painterResource(Res.drawable.bg_menu),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding((12 * scale).dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = (16 * scale).sp,
                    fontFamily = font,
                    letterSpacing = 1.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Canvas(modifier = Modifier.size(14.dp)) {
                        drawStar(size.width / 2f, size.height / 2f, 6f, 2.4f, Color(0xFFFFD54F))
                    }
                    Text(
                        text = starsText,
                        color = Color(0xFFD6D6D9),
                        fontSize = (13 * scale).sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Canvas(modifier = Modifier.size((22 * scale).dp)) {
                    drawLockIcon(Color.White.copy(alpha = 0.6f), scale)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = (12 * scale).sp,
                    fontFamily = font,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun MissionCard(
    index: Int,
    levelData: LevelData,
    result: LevelResult?,
    isUnlocked: Boolean,
    canPlay: Boolean,
    font: FontFamily,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val title = levelData.name.replaceFirst(Regex("^\\d+:\\s*"), "").uppercase()

    Box(
        modifier = modifier
            .background(
                if (isPressed && canPlay) Color(0xFF1E1E22) else Color(0xFF141416),
                RoundedCornerShape(10.dp)
            )
            .border(
                1.dp,
                if (canPlay) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(10.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = canPlay,
                onClick = onClick
            )
            .padding((16 * scale).dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Number
            Text(
                text = index.toString().padStart(2, '0'),
                color = if (canPlay) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f),
                fontSize = (32 * scale).sp,
                fontFamily = font,
                letterSpacing = 1.sp
            )

            // Middle Info
            if (canPlay) {
                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = (15 * scale).sp,
                        fontFamily = font,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Star Row
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (s in 0 until 3) {
                            val earned = s < (result?.starCount ?: 0)
                            Canvas(modifier = Modifier.size(16.dp)) {
                                drawStar(
                                    size.width / 2f,
                                    size.height / 2f,
                                    7f,
                                    2.8f,
                                    if (earned) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.18f)
                                )
                            }
                        }
                    }
                }

                // Bottom Best Time
                val timeText = if (result != null) formatTime(result.timeTaken) else "--:--.--"
                Text(
                    text = timeText,
                    color = Color(0xFF9A9A9E),
                    fontSize = (13 * scale).sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Column {
                    Canvas(modifier = Modifier.size((20 * scale).dp)) {
                        drawLockIcon(Color.White.copy(alpha = 0.4f), scale)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "LOCKED",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = (14 * scale).sp,
                        fontFamily = font,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val reason = if (!isUnlocked) "Complete previous mission" else "Shadow Pass required"
                    Text(
                        text = reason,
                        color = Color(0xFF6E6E72),
                        fontSize = (11 * scale).sp,
                        lineHeight = (14 * scale).sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

private fun formatTime(seconds: Float): String {
    val totalCentis = (seconds * 100).roundToInt().coerceAtLeast(0)
    val mins = totalCentis / 6000
    val secs = (totalCentis / 100) % 60
    val centis = totalCentis % 100
    return "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}.${centis.toString().padStart(2, '0')}"
}
