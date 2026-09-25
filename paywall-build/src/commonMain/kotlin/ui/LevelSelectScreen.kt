package com.infiltrate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infiltrate.storage.PlatformStorage
import game.model.GameProfile
import game.model.GameProfileStorage
import game.model.LevelData
import game.model.LevelResult
import game.model.LevelStorage
import game.model.Localization
import game.model.MapBackedGameProfileStorage
import game.model.MapBackedLevelStorage
import game.model.localizedDescription
import game.model.localizedName
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import paywall_build.generated.resources.Res
import paywall_build.generated.resources.bebas_neue_regular
import paywall_build.generated.resources.missions

@Composable
fun LevelSelectScreen(
    onStartMission: (LevelData) -> Unit,
    onStoreClicked: () -> Unit = {},
    onBackClicked: () -> Unit
) {
    val levelStorage: LevelStorage = remember {
        MapBackedLevelStorage(
            getRaw = { PlatformStorage.getRaw(it) },
            setRaw = { k, v -> PlatformStorage.setRaw(k, v) },
            removeRaw = { PlatformStorage.removeRaw(it) }
        )
    }
    val profileStorage: GameProfileStorage = remember {
        MapBackedGameProfileStorage(
            getRaw = { PlatformStorage.getRaw(it) },
            setRaw = { k, v -> PlatformStorage.setRaw(k, v) }
        )
    }

    var profile: GameProfile by remember { mutableStateOf(profileStorage.getProfile()) }
    var allResults: Map<String, LevelResult> by remember { mutableStateOf(levelStorage.getAllResults()) }

    LaunchedEffect(Unit) {
        profile = profileStorage.getProfile()
        allResults = levelStorage.getAllResults()
    }

    // Temporarily hide the unbuilt levels for Google Play production approval.
    // LevelData.DEFAULT_LEVELS has 12 levels; 1 to 8 are active and shipped (level 8 got its real
    // layout on 2026-09-25), 9 to 12 are still name-and-description stubs with no layout.
    val levels = LevelData.DEFAULT_LEVELS.take(8)
    val bebasFont = FontFamily(Font(Res.font.bebas_neue_regular))

    val completedCount = levels.count { allResults[it.id]?.completed == true }
    val starsEarned = levels.sumOf { allResults[it.id]?.starCount ?: 0 }
    val starsMax = levels.size * 3

    // Tapping a locked/gated mission used to swallow the tap in silence - reusing the error toast
    // sound rather than sourcing a dedicated "denied" clip, per the owner's call to not multiply
    // success/error sounds beyond the two already shipped.
    val toastError = LocalToastError.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0D))
    ) {
        // Two-axis scale (ui/Responsive.kt). The old height-only version floored at 0.75, which on
        // a 390dp-tall landscape phone inflated everything ~39% past the room available and pushed
        // the mission grid off the bottom - the screen was only reachable by scrolling.
        val language = LocalAppLanguage.current
        val metrics = menuMetrics(maxWidth, maxHeight)
        val scale = metrics.scale
        val safe = safeAreaPadding()

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            MenuTopBar(
                title = Localization.missions(language),
                font = bebasFont,
                onBackClicked = onBackClicked,
                scale = scale,
                startInset = safe.calculateStartPadding(LocalLayoutDirection.current),
                endInset = safe.calculateEndPadding(LocalLayoutDirection.current),
                hideLogo = !metrics.showsTopBarLogo,
                statPills = {
                    StatPill(
                        label = "$starsEarned/$starsMax",
                        icon = { drawStar(size.width / 2f, size.height / 2f, 7f, 2.8f, Color(0xFFFFD54F)) },
                        pillWidth = 95.dp,
                        scale = scale
                    )
                    CoinPill(
                        coins = profile.coins,
                        onPlusClicked = onStoreClicked,
                        scale = scale
                    )
                }
            )

            // Main Content Area. Scrollable: on a short screen (landscape phone) the chapter row
            // + section header + mission grid can add up to more than the available height -
            // previously that overflow was just clipped with no way to reach it.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = metrics.gutter + safe.calculateStartPadding(LocalLayoutDirection.current),
                        end = metrics.gutter + safe.calculateEndPadding(LocalLayoutDirection.current),
                        top = (12 * scale).dp,
                        bottom = (12 * scale).dp + safe.calculateBottomPadding(),
                    )
            ) {
                // Chapter Cards Row. Three of its four cards are COMING SOON placeholders, so on a
                // screen with no height to spare it is the first thing to give: shorter here,
                // rather than shrinking the missions the screen is actually for.
                val chapterRowHeight = if (metrics.isShort) (86 * scale).dp else (110 * scale).dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chapterRowHeight),
                    horizontalArrangement = Arrangement.spacedBy((16 * scale).dp)
                ) {
                    ChapterCard(
                        title = Localization.theShipyard(language),
                        isUnlocked = true,
                        starsText = "$starsEarned/$starsMax",
                        font = bebasFont,
                        scale = scale,
                        modifier = Modifier.weight(1f)
                    )
                    // Temporarily hide coming soon chapter boxes (Chapters 2-4) for Google Play production approval.
                    // Three spacers maintain the 4-column alignment with the mission grid below.
                    repeat(3) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Spacer(modifier = Modifier.height(if (metrics.isShort) (10 * scale).dp else (18 * scale).dp))

                // Section Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Localization.theShipyard(language),
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

                Spacer(modifier = Modifier.height(if (metrics.isShort) (8 * scale).dp else (16 * scale).dp))

                // Mission Cards Grid. 12 missions no longer fit one row, so they wrap 4-per-row
                // (matching the chapter row's own column count above) instead of squeezing all of
                // them into a single row. Each row's height still comes from its own cards'
                // content (IntrinsicSize.Min) rather than weight(1f) filling the rest of the
                // screen - a weight inside a scrollable parent has no bounded height to distribute
                // and Compose rejects it.
                val cardsPerRow = MISSION_CARDS_PER_ROW
                for (rowLevels in levels.withIndex().toList().chunked(cardsPerRow)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy((16 * scale).dp)
                    ) {
                        for ((index, levelData) in rowLevels) {
                            val result = allResults[levelData.id]
                            // TEMPORARY (for now): every mission card unlocked regardless of
                            // progress, for easier testing. Restore the commented-out check below
                            // to require completing the previous mission first.
                            val isUnlocked = true
                            // val isUnlocked = index == 0 || (allResults[levels[index - 1].id]?.completed == true)
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
                                descriptionLines = MISSION_CARD_BRIEFING_LINES,
                                onClick = { if (canPlay) onStartMission(levelData) else toastError() },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                        // Pad out a short final row so its cards keep the same width as a full
                        // row's, instead of stretching to fill the row on their own.
                        repeat(cardsPerRow - rowLevels.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(if (metrics.isShort) (10 * scale).dp else (16 * scale).dp))
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
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141416), RoundedCornerShape(8.dp))
            .border(
                1.dp,
                if (isUnlocked) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(8.dp)
            )
    ) {
        if (isUnlocked) {
            Image(
                painter = painterResource(Res.drawable.missions),
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

/** Mission cards per grid row. Matches the four chapter cards in the row above them. */
internal const val MISSION_CARDS_PER_ROW = 4

/** The briefing's type size on a mission card, before [MenuMetrics.scale]. */
internal const val MISSION_CARD_BRIEFING_SP = 11f

/**
 * Lines of briefing on a mission card - three, on every screen.
 *
 * This used to be `if (isShort) 2 else 3`, dropping a landscape phone to two lines because the
 * third was what pushed a grid row past the fold. Two things retire that: the grid scrolls now, so
 * a row past the fold is reachable rather than lost, and two lines is not enough to hold a
 * briefing. Every description in `LevelData.DEFAULT_LEVELS` is at least 91 characters, and the
 * narrowest card a phone can produce fits about 36 per line - so two lines ellipsized all twelve
 * of them on every phone. See [missionCardBriefingColumnsFor] for the budget three lines buys.
 *
 * Build 11 and earlier shipped a flat `maxLines = 3`, so this is also what the owner's own S25
 * Ultra has been showing all along.
 */
internal const val MISSION_CARD_BRIEFING_LINES = 3

/**
 * The width the briefing actually gets inside one mission card, for a screen of this size.
 *
 * The card is not a fixed size and not a fixed fraction of the screen either: its width comes from
 * the screen's, less the page gutters and the safe-area inset, split [MISSION_CARDS_PER_ROW] ways
 * with `16 * scale` between cards, then less the card's own `16 * scale` padding on each side. The
 * type inside it is sized off `scale` instead. Those two do not move together - `scale` floors at
 * [MenuMetrics.MIN_SCALE] on every phone while the card keeps narrowing with the screen - which is
 * why the character budget is a per-device number here rather than the one constant the main
 * menu's dossier gets. Pure - unit-tested in ResponsiveTest.
 */
internal fun missionCardBriefingWidthFor(
    screenWidth: Dp,
    screenHeight: Dp,
    safeHorizontal: Dp = 0.dp,
): Dp {
    val metrics = menuMetrics(screenWidth, screenHeight)
    val inset = (16 * metrics.scale).dp
    val content = screenWidth - metrics.gutter * 2 - safeHorizontal
    val cardWidth = (content - inset * (MISSION_CARDS_PER_ROW - 1)) / MISSION_CARDS_PER_ROW
    return cardWidth - inset * 2
}

/**
 * About how many characters of briefing fit on one line of a mission card.
 *
 * [advanceEm] is the average advance width of the body face as a share of its type size. The
 * briefing is set in the platform's default sans, which is Roboto or near enough to it everywhere
 * this ships; mixed-case English prose in Roboto averages close to 0.50em, and the 0.55 default
 * here is deliberately pessimistic so the budget holds on whichever platform turns out to have the
 * widest metrics. This is an estimate, not a measurement - Compose's own text measurement is the
 * only exact answer and it is not reachable from a pure function.
 */
internal fun missionCardBriefingColumnsFor(
    screenWidth: Dp,
    screenHeight: Dp,
    safeHorizontal: Dp = 0.dp,
    advanceEm: Float = 0.55f,
): Int {
    val scale = menuMetrics(screenWidth, screenHeight).scale
    val em = MISSION_CARD_BRIEFING_SP * scale * advanceEm
    if (em <= 0f) return 0
    return (missionCardBriefingWidthFor(screenWidth, screenHeight, safeHorizontal).value / em).toInt()
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
    descriptionLines: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val language = LocalAppLanguage.current
    val title = levelData.localizedName(language).replaceFirst(Regex("^\\d+:\\s*"), "").uppercase()
    val click = LocalUiClick.current

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
                onClick = { click(); onClick() }
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

                    Spacer(modifier = Modifier.height((6 * scale).dp))

                    Text(
                        text = levelData.localizedDescription(language),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = (MISSION_CARD_BRIEFING_SP * scale).sp,
                        lineHeight = (14 * scale).sp,
                        maxLines = descriptionLines,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height((10 * scale).dp))

                    // Star Row
                    Row(horizontalArrangement = Arrangement.spacedBy((4 * scale).dp)) {
                        for (s in 0 until 3) {
                            val earned = s < (result?.starCount ?: 0)
                            Canvas(modifier = Modifier.size((16 * scale).dp)) {
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
                val timeText = if (result != null) formatTime(result.timeTaken) else "--:--"
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
                        text = Localization.locked(language),
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = (14 * scale).sp,
                        fontFamily = font,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val reason = if (!isUnlocked) Localization.completePreviousMission(language) else Localization.shadowPassRequired(language)
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

/**
 * A best time as a stopwatch reads it, mm:ss. Hundredths were dropped: no time in this game is
 * decided on them (the target-time star is a whole-second threshold), and a two-decimal clock on
 * a mission card reads as telemetry rather than as a score. Truncated, not rounded, matching
 * `GameplayScene`'s own clockText so the same run reads identically on the results card and here.
 */
private fun formatTime(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val mins = total / 60
    val secs = total % 60
    return "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
}
