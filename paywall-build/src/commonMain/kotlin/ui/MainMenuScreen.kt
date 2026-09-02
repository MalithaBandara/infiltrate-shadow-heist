package com.infiltrate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
import game.model.LevelData
import game.model.LevelResult
import game.model.LevelStorage
import game.model.MapBackedGameProfileStorage
import game.model.MapBackedLevelStorage
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
import paywall_build.generated.resources.dossier_paper
import paywall_build.generated.resources.logo_main
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MainMenuScreen(
    onPlayClicked: (levelId: String) -> Unit,
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
    val levelStorage: LevelStorage = remember {
        MapBackedLevelStorage(
            getRaw = { PlatformStorage.getRaw(it) },
            setRaw = { k, v -> PlatformStorage.setRaw(k, v) }
        )
    }

    val profile: GameProfile = remember { profileStorage.getProfile() }
    val allResults: Map<String, LevelResult> = remember { levelStorage.getAllResults() }
    val levels = LevelData.DEFAULT_LEVELS
    val currentMissionIndex = levels.indexOfFirst { allResults[it.id]?.completed != true }.let { if (it == -1) levels.lastIndex else it }
    val currentMission = levels[currentMissionIndex]

    val storyTitle = "THE SHIPYARD"
    val levelRawName = if (currentMission.name.contains(":")) {
        currentMission.name.substringAfter(":").trim()
    } else {
        currentMission.name
    }
    // The sheet prints the file number in its own corner, so the title drops the "1." prefix
    // it used to carry inline.
    val missionNumber = (currentMissionIndex + 1).toString().padStart(2, '0')
    val missionTitle = levelRawName.uppercase()
    val missionDescription = currentMission.description

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

        val logoWidth = if (isCompact) 280.dp else (540 * scale).dp
        val logoHeight = if (isCompact) 82.dp else (158 * scale).dp

        val buttonWidth = if (isCompact) screenWidth - 32.dp else (560 * scale).dp
        val buttonHeight = if (isCompact) 56.dp else (84 * scale).dp
        val buttonSpacing = if (isCompact) 10.dp else (16 * scale).dp
        val buttonFontSize = if (isCompact) 24.sp else (36 * scale).sp
        val iconSize = if (isCompact) 24.dp else (36 * scale).dp

        // Moved slightly to the right for better visual breathing room
        val startMargin = if (isCompact) 16.dp else (100 * scale).dp

        // The dossier card sizes off its own floor rather than the menu's 0.85.
        //
        // The card is a pure multiple of its scale, so at the true ratio it would hold a constant
        // 37% of screen height on every device. The 0.85 floor above breaks that: it exists to keep
        // the menu buttons over the 48dp touch minimum and their labels legible on a short screen,
        // and a phone in landscape is only ~390dp tall, so it clamps 0.54 up to 0.85 and inflates
        // the card to 59% of the screen - against 37% on desktop. The buttons need that floor; the
        // card does not.
        //
        // The card is pinned to a constant share of screen height on every platform - that is
        // what makes it render identically rather than merely consistently. It is written as the
        // fraction itself rather than as a divisor so the intent is readable: change
        // DOSSIER_HEIGHT_FRACTION and all three platforms move together, which is the only way
        // they can move without drifting apart.
        //
        // Nothing here has a floor. A floor is exactly what pulled the platforms apart before: it
        // stops the box shrinking while the screen keeps shrinking, so the note ends up a corner
        // card on desktop and half the screen on a phone. The fraction is set by the reference
        // design rather than by a legibility limit - on a phone in landscape the briefing lands
        // around 7sp, which is deliberate: holding the proportion was chosen over holding a
        // readable size on small screens.
        //
        // The width term is a safety guard, not part of the design: on a screen tall relative to
        // its width (a portrait desktop window) a purely height-driven card would come out wider
        // than the display. On every landscape device - desktop, phone and the 4:3-ish iPhone SE -
        // the height term is the smaller of the two, so the guard never binds.
        val dossierCardHeight = screenHeight * DOSSIER_HEIGHT_FRACTION
        val dossierCardWidth = (dossierCardHeight * DOSSIER_ASPECT).coerceAtMost(screenWidth * 0.62f)
        val dossierScale = dossierCardWidth / DOSSIER_BASE_WIDTH.dp

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
                .width(if (isCompact) screenWidth else (820 * scale).dp)
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
                    top = 20.dp,
                    bottom = 20.dp
                )
        ) {
            // Left Column: Logo + Buttons (Vertically Centered)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(buttonWidth),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo Image (logo_main.png) optically centered relative to the visual body of the buttons
                Image(
                    painter = painterResource(Res.drawable.logo_main),
                    contentDescription = "INFILTRATE: SHADOW HEIST",
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center,
                    modifier = Modifier
                        .width(logoWidth)
                        .height(logoHeight)
                        .padding(start = (14 * scale).dp) // Optical centering offset for "INFILTRATE" lettering
                )

                // Extra breathing space between logo and buttons
                Spacer(modifier = Modifier.height(if (isCompact) 16.dp else (32 * scale).dp))

                // Textured Heist Buttons Stack
                HeistTexturedButton(
                    text = "PLAY",
                    texture = Res.drawable.button1,
                    font = bebasFont,
                    buttonHeight = buttonHeight,
                    fontSize = buttonFontSize,
                    iconSize = iconSize,
                    iconRenderer = { drawInkPlay() },
                    onClick = { onPlayClicked(currentMission.id) }
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
                missionNumber = missionNumber,
                storyTitle = storyTitle,
                missionTitle = missionTitle,
                missionDescription = missionDescription,
                font = bebasFont,
                scale = dossierScale,
                onClick = { onPlayClicked(currentMission.id) }
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

    val scale = if (isPressed) 0.98f else 1.0f
    val alpha = if (isPressed) 0.85f else 1.0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(buttonHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // High-Quality Grunge Textured Button Background
        Image(
            painter = painterResource(texture),
            contentDescription = text,
            contentScale = ContentScale.FillBounds,
            alpha = alpha,
            modifier = Modifier.fillMaxSize()
        )

        // Button Content: Icon + Typography
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.weight(0.42f),
                contentAlignment = Alignment.CenterEnd
            ) {
                Canvas(modifier = Modifier.size(iconSize)) {
                    iconRenderer()
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier.weight(0.58f),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = text,
                    color = ShadowTheme.Ink,
                    fontSize = fontSize,
                    fontFamily = font,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

/**
 * How far the sheet in `dossier_paper.png` sits off square, in degrees, negative being
 * counter-clockwise on screen.
 *
 * Measured off the artwork's own alpha edges, each fitted over a band clear of the folder tab -
 * a fit that straddles the tab step reads the sign backwards, which is worth knowing before
 * trusting one.
 *
 * All five edges, fitted: tab top 4.47, main top 4.83, bottom 2.85, left 5.97, right 5.58. Their
 * length-weighted mean is 4.61.
 *
 * This sits above that mean, at 5.2, because the edges are not read equally. A block of text is
 * judged against its left margin before anything else - every line starts there - so the side
 * edges, at 5.97 and 5.58, are the ones that decide whether the type looks square to the page. At
 * 4.0 the margin ran 2 degrees shallower than the paper beside it, which over the block's height
 * opens a visible 7dp wedge and reads as the text being misaligned rather than the sheet being
 * torn.
 *
 * 5.2 is within 0.8 of both sides and both tops. Only the bottom edge disagrees, by 2.4, and it is
 * both the raggedest of the five and the one no type sits against.
 *
 * The pivot is the card's centre (Compose's default), so the rotation costs a little vertical
 * swing at the left and right insets - part of why they are not tighter.
 */
private const val DOSSIER_TILT_DEGREES = -5.2f

/**
 * The sheet artwork's aspect as shipped. The source, file4.png, is 1536x1024 and is already 1.500,
 * so unlike the sheets before it this one is resized but never distorted - the constant simply
 * matches the art. Keeping them equal is what stops FillBounds stretching the torn edge on one
 * axis.
 */
private const val DOSSIER_ASPECT = 1.500f

/** Card width at scale 1.0. Height follows from [DOSSIER_ASPECT]. */
private const val DOSSIER_BASE_WIDTH = 462f

/**
 * The card's height as a share of the screen's, held on every platform. Taken from the reference
 * design, where the sheet measured about 278dp against an 841dp-tall screen, then opened up a
 * step from there. See the call site in [MainMenuScreen] for why this is the only knob that moves
 * the card - the type, gaps, rules and insets are all fractions of it, so changing it rescales the
 * note without redesigning it.
 */
private const val DOSSIER_HEIGHT_FRACTION = 0.37f

// Bottom-Right Dossier Card Component
/**
 * The current mission, presented as a torn briefing note clipped to the screen.
 *
 * The whole card is one artwork ([Res.drawable.dossier_paper]) with the tear, the stains and the
 * paperclip already in it, stretched to the card box the same way [HeistTexturedButton] stretches
 * its button plates. That is why every inset is a fraction of the card rather than a fixed dp: the
 * ragged edge moves with the card when [scale] changes, so text measured from the card edge in dp
 * would drift onto the tear on large displays.
 *
 * There is deliberately no compact/regular split and no size floors. The sheet is one design at
 * every size - each type size, gap, rule weight and letter spacing is the same fraction of the card
 * everywhere - so a phone and a desktop render the same note, differing only in how large it is.
 *
 * The order down the page is the reference design's: the file number sitting up on the sheet's
 * folder tab, a rule under it, the chapter in small caps, the mission name, a second rule, then the
 * briefing.
 *
 * The insets come from the artwork, measured over the band the text actually occupies rather than
 * over the whole frame: across x=9%..92% the solid paper runs y=11.2% to y=91.2%. Reading it over
 * the full frame instead gives a useless 68%, because the far-right corner past x=94% is torn away
 * almost entirely.
 *
 * Horizontally the tear is what sets both sides. The sheet's left edge wanders between x=2.8% and
 * x=7.2%, its right between x=92.9% and x=97%, so 10 and 9 keep the column inside the paper at its
 * narrowest rather than at its average. The rotation is part of that sum: content below the card's
 * centre swings right by about a percent, which is why the end inset cannot be trimmed to the
 * right edge's mean of 94.8% - the lower rule would cross the tear on the columns where it runs
 * shallowest.
 *
 * The top inset is smaller than that 11.2% on purpose, because the number sits on the raised tab
 * (x=8%..40%, its top edge at y=6-8%) rather than on the main sheet, and the rotation drops it
 * another 3% of the card's height besides. Everything below the number clears 11.2% comfortably.
 *
 * The foot is the deepest inset of the four. The block rotates about the card's centre, so the
 * briefing's last line hangs about 16dp lower at its left end than it is laid out; without that
 * depth a full-height stack would put the final line onto the bottom tear.
 *
 * The upper rule stops well short of the right edge because the paperclip is painted over
 * x=83%..92%, and breaks into a dashed tail before reaching it. The lower rule sits below the clip
 * and runs the full column.
 */
@Composable
private fun MissionDossierCard(
    modifier: Modifier = Modifier,
    missionNumber: String,
    storyTitle: String,
    missionTitle: String,
    missionDescription: String,
    font: FontFamily,
    scale: Float,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cardWidth = (DOSSIER_BASE_WIDTH * scale).dp
    val cardHeight = (DOSSIER_BASE_WIDTH / DOSSIER_ASPECT * scale).dp

    // Ink on aged paper, not on a dark panel - so the type is dark and the separators are hairlines
    // rather than the light-on-black treatment the rest of the menu uses.
    val inkStrong = ShadowTheme.Ink
    val inkBody = ShadowTheme.Ink.copy(alpha = 0.78f)
    val inkFaint = ShadowTheme.Ink.copy(alpha = 0.55f)
    val rule = ShadowTheme.Ink.copy(alpha = 0.34f)

    // Every size is a fixed fraction of the card, with no floors, so the note is a scaled copy of
    // itself on every platform.
    val numberSize = 32 * scale
    val labelSize = 20 * scale
    // The mission name is held to one line, so this is sized for the worst case rather than the
    // measured one. "WAREHOUSE INFILTRATION" - the longest in LevelData.DEFAULT_LEVELS at 22
    // characters - runs 293dp to 339dp of the 351dp column across every plausible Bebas cap width
    // (0.38em to 0.44em), so it holds one line on all of them. The margin matters more here than
    // elsewhere: maxLines = 1 clips rather than wraps, so a name that overran would lose its tail
    // on whichever platform happened to have the widest metrics.
    val titleSize = 35 * scale
    // The briefing is the only thing here sized up to absorb the sheet's spare height; the number,
    // chapter and mission name are back where they were. 20 is its ceiling rather than a
    // preference, and the ceiling is set by wrapping, not by the space available.
    //
    // The block has to hold exactly three lines, so the column has to land between about 31 and 35
    // characters: below 31 the second briefing loses a word and spills to four, above 35 the first
    // one pulls up to two and stops matching the rest. At 18 the 351dp column takes 32.5 characters
    // on nominal metrics, 31.5 on the widest and 33.6 on the narrowest, so all four briefings hold
    // three lines whatever the platform's monospace measures.
    //
    // The size tracks the column, and the column narrowed twice getting here: once pulling it back
    // inside the sheet's right tear, and again moving the whole block right off the tear on the
    // left. Both cost the briefing a point.
    val bodySize = 18 * scale
    val bodyLineHeight = 22 * scale

    // Rule weight and letter spacing scale with the card too. Left absolute they would read as a
    // heavier hairline and tighter tracking on a phone than on desktop - the sort of small
    // inconsistency that makes two screens look like different designs rather than one at two sizes.
    val ruleWeight = (2 * scale).dp

    Box(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Image(
            painter = painterResource(Res.drawable.dossier_paper),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            alpha = if (isPressed) 0.86f else 1f,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .rotate(DOSSIER_TILT_DEGREES)
                .padding(
                    start = cardWidth * 0.15f,
                    end = cardWidth * 0.09f,
                    top = cardHeight * 0.055f,
                    bottom = cardHeight * 0.14f
                )
        ) {
            // File number, sitting up on the folder tab, starting on the same line as the rules
            // and the text below it. There is room for it there: the tab spans x=8.2%..39.6% of the
            // sheet, and the rotation carries content this high up about 2% of the card's width to
            // the left, so the number lands near x=13% - clear of the tab's left edge by a good
            // margin and clear of the sheet's left tear, which wanders out to x=7.2%, by more.
            Text(
                text = missionNumber,
                color = inkStrong,
                fontSize = numberSize.sp,
                fontFamily = font,
                letterSpacing = (2 * scale).sp
            )

            Spacer(modifier = Modifier.height((4 * scale).dp))

            // Upper rule, ending in a dashed tail short of the paperclip.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = cardWidth * 0.12f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(ruleWeight)
                        .background(rule)
                )
                repeat(3) {
                    Spacer(modifier = Modifier.width((6 * scale).dp))
                    Box(
                        modifier = Modifier
                            .width((10 * scale).dp)
                            .height(ruleWeight)
                            .background(rule)
                    )
                }
            }

            // Almost all of the sheet's spare height now goes to the foot rather than being split
            // with this gap, which is what lifts the chapter, name and briefing up under the rule
            // instead of leaving a hole below it. The foot keeps the larger share because it is
            // doing real work: the block is rotated about the card's centre, so the last line of
            // the briefing hangs roughly 17dp lower at its left end than it is laid out, and that
            // has to clear the bottom tear and the ink splatters sitting on it.
            Spacer(modifier = Modifier.height((5 * scale).dp))
            Spacer(modifier = Modifier.weight(0.15f))

            Text(
                text = storyTitle,
                color = inkFaint,
                fontSize = labelSize.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (2.2f * scale).sp
            )

            Spacer(modifier = Modifier.height((3 * scale).dp))

            Text(
                text = missionTitle,
                color = inkStrong,
                fontSize = titleSize.sp,
                lineHeight = titleSize.sp,
                fontFamily = font,
                letterSpacing = (1 * scale).sp,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height((9 * scale).dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ruleWeight)
                    .background(rule)
            )

            Spacer(modifier = Modifier.height((8 * scale).dp))

            // Monospace for the briefing body: it is the one block on the sheet that reads as
            // something typed onto the page rather than printed on the form.
            Text(
                text = missionDescription,
                color = inkBody,
                fontFamily = FontFamily.Monospace,
                fontSize = bodySize.sp,
                lineHeight = bodyLineHeight.sp,
                maxLines = 3
            )

            Spacer(modifier = Modifier.weight(0.85f))
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
    
    // Main ring
    val ringR = 6.5f * scale
    val thickness = 4.5f * scale
    drawCircle(color = ShadowTheme.Ink, radius = ringR, style = Stroke(width = thickness))
    
    // Teeth
    val innerR = 6.0f * scale
    val outerR = 10.2f * scale
    val toothWidth = 3.6f * scale
    for (i in 0 until 8) {
        val angle = i * PI / 4.0
        val ux = cos(angle).toFloat()
        val uy = sin(angle).toFloat()
        drawLine(
            color = ShadowTheme.Ink,
            start = Offset(cx + ux * innerR, cy + uy * innerR),
            end = Offset(cx + ux * outerR, cy + uy * outerR),
            strokeWidth = toothWidth,
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
