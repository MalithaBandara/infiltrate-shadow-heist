package game.scene

import game.model.*
import game.scene.UiComponents.drawStar
import game.scene.UiComponents.uiGraphics
import korlibs.image.color.*
import korlibs.image.font.*
import korlibs.image.format.readBitmap
import korlibs.image.vector.*
import korlibs.io.file.std.resourcesVfs
import korlibs.korge.input.*
import korlibs.korge.scene.*
import korlibs.korge.service.storage.*
import korlibs.korge.view.*
import korlibs.korge.view.vector.*
import korlibs.math.geom.*
import korlibs.math.geom.vector.*
import kotlin.math.*

class LevelSelectScene : Scene() {

    private val white = Colors.WHITE
    private val muted = Colors["#9A9A9E"]
    private val dim = Colors["#6E6E72"]

    private fun ShapeBuilder.drawBackChevron() {
        stroke(white, StrokeInfo(thickness = 2.4)) {
            moveTo(4.0, -8.0); lineTo(-5.0, 0.0); lineTo(4.0, 8.0)
        }
    }

    private fun ShapeBuilder.drawBriefcaseIcon(c: RGBA) {
        stroke(c, StrokeInfo(thickness = 1.6)) {
            roundRect(-9.0, -5.0, 18.0, 13.0, 2.0, 2.0)
            moveTo(-4.0, -5.0); lineTo(-4.0, -8.0); lineTo(4.0, -8.0); lineTo(4.0, -5.0)
            moveTo(-9.0, 1.0); lineTo(9.0, 1.0)
        }
    }

    // Shackle is a stroked circle whose lower half is hidden behind the body fill drawn after it -
    // avoids needing a true arc path just for a recognizable padlock silhouette.
    private fun ShapeBuilder.drawLockIcon(c: RGBA, s: Double = 1.0) {
        stroke(c, StrokeInfo(thickness = 1.8 * s)) {
            circle(Point(0.0, -3.0 * s), 5.0 * s)
        }
        fill(c) {
            roundRect(-7.0 * s, -2.0 * s, 14.0 * s, 11.0 * s, 2.0 * s, 2.0 * s)
        }
    }

    private fun formatTime(seconds: Float): String {
        val totalCentis = (seconds * 100).roundToInt().coerceAtLeast(0)
        val mins = totalCentis / 6000
        val secs = (totalCentis / 100) % 60
        val centis = totalCentis % 100
        return "%02d:%02d.%02d".format(mins, secs, centis)
    }

    // LevelData.name is authored as "01: Warehouse Infiltration" - the number is shown separately
    // (big, its own slot) so strip the "NN: " prefix here rather than showing it twice.
    private fun levelTitle(data: LevelData): String =
        data.name.replaceFirst(Regex("^\\d+:\\s*"), "").uppercase()

    override suspend fun SContainer.sceneMain() {
        val levelStorage: LevelStorage = MapBackedLevelStorage(
            getRaw = { views.storage[it] },
            setRaw = { k, v -> views.storage[k] = v }
        )
        val profileStorage: GameProfileStorage = MapBackedGameProfileStorage(
            getRaw = { views.storage[it] },
            setRaw = { k, v -> views.storage[k] = v }
        )

        val profile = profileStorage.getProfile()
        val allResults = levelStorage.getAllResults()
        val levels = LevelData.DEFAULT_LEVELS
        val bebas = try { resourcesVfs["BebasNeue-Regular.ttf"].readTtfFont() } catch (e: Exception) { DefaultTtfFont }

        val canvasW = sceneWidth.toDouble()
        val canvasH = sceneHeight.toDouble()

        fun crisp(t: Text): Text { t.graphicsRenderer = GraphicsRenderer.GPU; return t }

        solidRect(canvasW, canvasH, Colors["#0B0B0D"])

        val completedCount = levels.count { allResults[it.id]?.completed == true }
        val starsEarned = levels.sumOf { allResults[it.id]?.starCount ?: 0 }
        val starsMax = levels.size * 3

        // --- Top bar ---
        val topBarH = 70.0
        val backBtn = container().xy(24.0, 13.0)
        val backG = backBtn.uiGraphics()
        fun drawBackBg(hover: Boolean) {
            backG.updateShape {
                clear()
                fill(if (hover) Colors["#242428"] else Colors["#18181B"]) { roundRect(0.0, 0.0, 44.0, 44.0, 8.0, 8.0) }
            }
        }
        drawBackBg(false)
        backBtn.uiGraphics().xy(22.0, 22.0).updateShape { drawBackChevron() }
        backBtn.onOver { drawBackBg(true) }
        backBtn.onOut { drawBackBg(false) }
        backBtn.mouse { onClick { sceneContainer.changeTo { MainMenuScene() } } }

        val logoContainer = container().xy(82.0, 14.0)
        crisp(logoContainer.text("INFILTRATE", textSize = 20.0, font = bebas, color = white))
        crisp(logoContainer.text("SHADOW HEIST", textSize = 8.0, color = dim)).xy(2.0, 24.0)

        val title = crisp(text("MISSIONS", textSize = 26.0, font = bebas, color = white))
        title.xy((canvasW - title.width) / 2.0, 20.0)

        fun statPill(rightEdgeX: Double, label: String, iconRenderer: ShapeBuilder.() -> Unit): Double {
            val pillW = 90.0
            val pillH = 32.0
            val pill = container().xy(rightEdgeX - pillW, (topBarH - pillH) / 2.0)
            pill.uiGraphics().updateShape {
                fill(Colors["#18181B"]) { roundRect(0.0, 0.0, pillW, pillH, 8.0, 8.0) }
                stroke(Colors.WHITE.withAd(0.08), StrokeInfo(thickness = 1.0)) { roundRect(0.0, 0.0, pillW, pillH, 8.0, 8.0) }
            }
            pill.uiGraphics().xy(20.0, pillH / 2.0).updateShape { iconRenderer() }
            crisp(pill.text(label, textSize = 14.0, color = white)).xy(34.0, (pillH - 14.0) / 2.0 - 1.0)
            return rightEdgeX - pillW - 12.0
        }
        val starPillLeft = statPill(canvasW - 20.0, "$starsEarned/$starsMax") { drawStar(0.0, 0.0, 8.0, 3.2) }
        statPill(starPillLeft, "$completedCount/${levels.size}") { drawBriefcaseIcon(muted) }

        // --- Chapter row: one real chapter (this level pack) plus locked placeholders for future
        // content, matching the reference's multi-chapter row even though only one pack exists yet.
        val chapterY = topBarH + 8.0
        val chapterH = 92.0
        val chapterCount = 4
        val chapterGap = 16.0
        val chapterMargin = 30.0
        val chapterW = (canvasW - chapterMargin * 2 - chapterGap * (chapterCount - 1)) / chapterCount

        val bgBmp = try { resourcesVfs["bg_menu.jpg"].readBitmap() } catch (e: Exception) { null }

        for (i in 0 until chapterCount) {
            val cx = chapterMargin + i * (chapterW + chapterGap)
            val isReal = i == 0
            val card = container().xy(cx, chapterY)
            if (bgBmp != null) {
                val img = card.image(bgBmp) { size(chapterW, chapterH) }
                img.colorMul = if (isReal) Colors["#8A8A8E"] else Colors["#3C3C40"]
            } else {
                card.uiGraphics().updateShape { fill(Colors["#1A1A1D"]) { rect(0.0, 0.0, chapterW, chapterH) } }
            }
            card.uiGraphics().updateShape {
                fill(Colors.BLACK.withAd(0.45)) { rect(0.0, 0.0, chapterW, chapterH) }
                stroke(if (isReal) white.withAd(0.9) else Colors.WHITE.withAd(0.06), StrokeInfo(thickness = if (isReal) 2.0 else 1.0)) {
                    rect(0.5, 0.5, chapterW - 1.0, chapterH - 1.0)
                }
            }
            if (isReal) {
                crisp(card.text("SHIPYARD", textSize = 15.0, font = bebas, color = white)).xy(12.0, chapterH - 40.0)
                val starsRow = card.uiGraphics().xy(14.0, chapterH - 16.0)
                starsRow.updateShape { drawStar(0.0, 0.0, 6.0, 2.4) }
                crisp(card.text("$starsEarned/$starsMax", textSize = 12.0, color = Colors["#D6D6D9"])).xy(24.0, chapterH - 22.0)
            } else {
                card.uiGraphics().xy(chapterW / 2.0, chapterH / 2.0 - 10.0).updateShape { drawLockIcon(Colors.WHITE.withAd(0.7), 1.1) }
                val comingSoon = crisp(card.text("COMING SOON", textSize = 11.0, color = Colors.WHITE.withAd(0.55)))
                comingSoon.xy((chapterW - comingSoon.width) / 2.0, chapterH / 2.0 + 10.0)
            }
        }

        // --- Section header ---
        val sectionY = chapterY + chapterH + 14.0
        crisp(text("SHIPYARD", textSize = 16.0, font = bebas, color = white)).xy(chapterMargin, sectionY)
        uiGraphics().xy(chapterMargin + 84.0, sectionY + 9.0).updateShape {
            stroke(Colors.WHITE.withAd(0.15), StrokeInfo(thickness = 1.0)) {
                moveTo(0.0, 0.0); lineTo(canvasW - chapterMargin - (chapterMargin + 84.0), 0.0)
            }
        }

        // --- Mission grid ---
        val gridY = sectionY + 26.0
        val gridGap = 16.0
        val cardW = (canvasW - chapterMargin * 2 - gridGap * (levels.size - 1)) / levels.size
        val cardH = (canvasH - gridY - 20.0).coerceAtMost(190.0)

        for ((index, levelData) in levels.withIndex()) {
            val result = allResults[levelData.id]
            val isUnlocked = index == 0 || (allResults[levels[index - 1].id]?.completed == true)
            val requiresPremium = levelData.id.contains("dlc")
            val canPlay = isUnlocked && (!requiresPremium || profile.isPremium)

            val cx = chapterMargin + index * (cardW + gridGap)
            val card = container().xy(cx, gridY)
            card.uiGraphics().updateShape {
                fill(Colors["#141416"]) { roundRect(0.0, 0.0, cardW, cardH, 8.0, 8.0) }
                stroke(Colors.WHITE.withAd(0.08), StrokeInfo(thickness = 1.0)) { roundRect(0.0, 0.0, cardW, cardH, 8.0, 8.0) }
            }

            crisp(card.text("%02d".format(index + 1), textSize = 30.0, font = bebas, color = Colors.WHITE.withAd(0.35)))
                .xy(14.0, 10.0)

            if (canPlay) {
                val titleT = crisp(card.text(levelTitle(levelData), textSize = 13.0, font = bebas, color = white))
                titleT.xy(14.0, 52.0)

                val starsRow = card.uiGraphics().xy(20.0, 84.0)
                starsRow.updateShape {
                    for (i in 0 until 3) {
                        val earned = i < (result?.starCount ?: 0)
                        drawStar(i * 22.0, 0.0, 7.0, 2.8, if (earned) UiComponents.COLOR_ACCENT_GOLD else Colors.WHITE.withAd(0.18))
                    }
                }

                val timeLabel = if (result != null) formatTime(result.timeTaken) else "--:--.--"
                crisp(card.text(timeLabel, textSize = 12.0, color = muted)).xy(14.0, 104.0)

                card.onOver { card.colorMul = Colors["#DADADD"] }
                card.onOut { card.colorMul = Colors.WHITE }
                card.mouse { onClick { sceneContainer.changeTo { GameplayScene(levelData) } } }
            } else {
                card.uiGraphics().xy(28.0, 60.0).updateShape { drawLockIcon(Colors.WHITE.withAd(0.4)) }
                crisp(card.text("LOCKED", textSize = 12.0, color = Colors.WHITE.withAd(0.4))).xy(14.0, 84.0)
                val reason = if (!isUnlocked) "Complete previous\nmission to unlock." else "Shadow Pass required\nfor this operation."
                crisp(card.text(reason, textSize = 10.0, color = dim)).xy(14.0, 104.0)
            }
        }
    }
}
