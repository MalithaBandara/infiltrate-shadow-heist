package game.scene

import com.sample.demo.purchases.*
import game.model.GameProfileStorage
import game.model.MapBackedGameProfileStorage
import game.model.PowerupType
import game.scene.UiComponents.COLOR_ACCENT_GOLD
import game.scene.UiComponents.COLOR_ACCENT_GREEN
import game.scene.UiComponents.COLOR_ACCENT_RED
import game.scene.UiComponents.createTexturedTab
import game.scene.UiComponents.createToast
import game.scene.UiComponents.uiGraphics
import korlibs.image.color.*
import korlibs.image.font.*
import korlibs.image.format.readBitmap
import korlibs.image.vector.*
import korlibs.io.async.launchImmediately
import korlibs.io.file.std.resourcesVfs
import korlibs.korge.input.*
import korlibs.korge.scene.*
import korlibs.korge.service.storage.*
import korlibs.korge.view.*
import korlibs.korge.view.vector.*
import korlibs.math.geom.*
import korlibs.math.geom.vector.*

enum class StoreTab { POWER_UPS, COINS }

class StoreScene(private val initialTab: StoreTab = StoreTab.POWER_UPS) : Scene() {

    private val ink = Colors["#17140F"]
    private val white = Colors.WHITE
    private val muted = Colors["#B7B7BC"]
    private val dim = Colors["#6E6E72"]

    private data class PowerupDef(
        val type: PowerupType,
        val id: String,
        val title: String,
        val description: String,
        val cost: Int,
        val icon: ShapeBuilder.(RGBA) -> Unit
    )

    private fun ShapeBuilder.drawBackChevron() {
        stroke(white, StrokeInfo(thickness = 2.4)) {
            moveTo(4.0, -8.0); lineTo(-5.0, 0.0); lineTo(4.0, 8.0)
        }
    }

    private fun ShapeBuilder.drawBoltIcon(c: RGBA) {
        fill(c) {
            moveTo(2.0, -9.0); lineTo(-6.0, 2.0); lineTo(-1.0, 2.0); lineTo(-2.0, 9.0); lineTo(6.0, -2.0); lineTo(1.0, -2.0)
            close()
        }
    }

    private fun ShapeBuilder.drawCoinStackIcon(c: RGBA) {
        stroke(c, StrokeInfo(thickness = 1.6)) {
            ellipse(Rectangle(-8.0, -5.0, 16.0, 6.0))
            ellipse(Rectangle(-8.0, -1.0, 16.0, 6.0))
            ellipse(Rectangle(-8.0, 3.0, 16.0, 6.0))
        }
    }

    private fun ShapeBuilder.drawCoinIcon(c: RGBA) {
        fill(c) { circle(Point(0.0, 0.0), 8.0) }
        stroke(Colors.BLACK.withAd(0.35), StrokeInfo(thickness = 1.0)) { circle(Point(0.0, 0.0), 8.0) }
    }

    private fun ShapeBuilder.drawPlusIcon(c: RGBA) {
        stroke(c, StrokeInfo(thickness = 2.2)) {
            moveTo(-6.0, 0.0); lineTo(6.0, 0.0)
            moveTo(0.0, -6.0); lineTo(0.0, 6.0)
        }
    }

    private fun ShapeBuilder.drawSmokeIcon(c: RGBA) {
        fill(c) { circle(Point(0.0, 5.0), 8.0) }
        stroke(c, StrokeInfo(thickness = 1.8)) {
            moveTo(-7.0, -3.0); lineTo(-11.0, -10.0)
            moveTo(0.0, -5.0); lineTo(0.0, -14.0)
            moveTo(7.0, -3.0); lineTo(11.0, -10.0)
        }
    }

    private fun ShapeBuilder.drawCloakIcon(c: RGBA) {
        fill(c) {
            moveTo(0.0, -12.0)
            lineTo(8.0, -4.0); lineTo(10.0, 10.0); lineTo(4.0, 8.0); lineTo(0.0, 12.0)
            lineTo(-4.0, 8.0); lineTo(-10.0, 10.0); lineTo(-8.0, -4.0)
            close()
        }
        stroke(Colors.BLACK.withAd(0.4), StrokeInfo(thickness = 1.5)) {
            moveTo(0.0, -12.0); lineTo(0.0, 12.0)
        }
    }

    private fun ShapeBuilder.drawInvisIcon(c: RGBA) {
        stroke(c, StrokeInfo(thickness = 1.8)) {
            ellipse(Rectangle(-11.0, -6.0, 22.0, 12.0))
            moveTo(-10.0, 8.0); lineTo(10.0, -8.0)
        }
        fill(c) { circle(Point(0.0, 0.0), 3.5) }
    }

    private fun ShapeBuilder.drawBootIcon(c: RGBA) {
        fill(c) {
            moveTo(-4.0, -14.0); lineTo(4.0, -14.0); lineTo(4.0, 3.0)
            lineTo(14.0, 5.0); lineTo(15.0, 9.0); lineTo(12.0, 11.0)
            lineTo(-9.0, 11.0); lineTo(-9.0, 6.0); lineTo(-4.0, 3.0)
            close()
        }
    }

    private val powerupDefs = listOf(
        PowerupDef(PowerupType.SMOKE_SCREEN, PowerupType.SMOKE_SCREEN.id, "SMOKE SCREEN", "Disables all cameras\nfor 10 seconds.", 600) { c -> drawSmokeIcon(c) },
        PowerupDef(PowerupType.PHANTOM_CLOAK, PowerupType.PHANTOM_CLOAK.id, "PHANTOM CLOAK", "Puts all guards to sleep\nfor 10 seconds.", 800) { c -> drawCloakIcon(c) },
        PowerupDef(PowerupType.INVISIBILITY, PowerupType.INVISIBILITY.id, "INVISIBILITY", "Total sight immunity\nfor 10 seconds.", 1000) { c -> drawInvisIcon(c) },
        PowerupDef(PowerupType.NOISE_SUPPRESSION, PowerupType.NOISE_SUPPRESSION.id, "NOISE SUPPRESSION", "Silent movement for\nentire mission.", 750) { c -> drawBootIcon(c) }
    )

    private data class CoinPack(val id: String, val amount: Int, val price: String, val bestValue: Boolean = false)
    private val coinPacks = listOf(
        CoinPack("coins_small", 500, "$0.99"),
        CoinPack("coins_medium", 1200, "$1.99", bestValue = true),
        CoinPack("coins_large", 3500, "$4.99")
    )

    override suspend fun SContainer.sceneMain() {
        val profileStorage: GameProfileStorage = MapBackedGameProfileStorage(
            getRaw = { views.storage[it] },
            setRaw = { k, v -> views.storage[k] = v }
        )
        val bridge = getPurchasesBridge()
        var profile = profileStorage.getProfile()
        val bebas = try { resourcesVfs["BebasNeue-Regular.ttf"].readTtfFont() } catch (e: Exception) { DefaultTtfFont }

        val canvasW = sceneWidth.toDouble()
        val canvasH = sceneHeight.toDouble()

        fun crisp(t: Text): Text { t.graphicsRenderer = GraphicsRenderer.GPU; return t }
        fun showFeedback(message: String, isSuccess: Boolean) {
            sceneContainer.createToast(message, color = if (isSuccess) COLOR_ACCENT_GREEN else COLOR_ACCENT_RED)
        }

        solidRect(canvasW, canvasH, Colors["#0B0B0D"])

        // --- Top bar (built once - switching tabs below never touches this) ---
        val topBarH = 62.0
        val backBtn = container().xy(20.0, 11.0)
        val backG = backBtn.uiGraphics()
        fun drawBackBg(hover: Boolean) {
            backG.updateShape {
                clear()
                fill(if (hover) Colors["#242428"] else Colors["#18181B"]) { roundRect(0.0, 0.0, 40.0, 40.0, 8.0, 8.0) }
            }
        }
        drawBackBg(false)
        backBtn.uiGraphics().xy(20.0, 20.0).updateShape { drawBackChevron() }
        backBtn.onOver { drawBackBg(true) }
        backBtn.onOut { drawBackBg(false) }
        backBtn.mouse { onClick { sceneContainer.changeTo { MainMenuScene() } } }

        val logoBmp = try { resourcesVfs["logo_main.png"].readBitmap() } catch (e: Exception) { null }
        if (logoBmp != null) {
            val logoScale = 130.0 / logoBmp.width
            val logoH = logoBmp.height * logoScale
            image(logoBmp) { size(logoBmp.width * logoScale, logoH) }.xy(72.0, (topBarH - logoH) / 2.0)
        } else {
            val logoContainer = container().xy(72.0, 12.0)
            crisp(logoContainer.text("INFILTRATE", textSize = 18.0, font = bebas, color = white))
            crisp(logoContainer.text("SHADOW HEIST", textSize = 7.0, color = dim)).xy(2.0, 22.0)
        }

        val title = crisp(text("STORE", textSize = 24.0, font = bebas, color = white))
        title.xy((canvasW - title.width) / 2.0, 18.0)

        val plusBtnW = 32.0
        val pillW = 100.0
        val pillH = 32.0
        val pillX = canvasW - 20.0 - plusBtnW - 8.0 - pillW
        val pill = container().xy(pillX, (topBarH - pillH) / 2.0)
        pill.uiGraphics().updateShape {
            fill(Colors["#18181B"]) { roundRect(0.0, 0.0, pillW, pillH, 8.0, 8.0) }
            stroke(Colors.WHITE.withAd(0.08), StrokeInfo(thickness = 1.0)) { roundRect(0.0, 0.0, pillW, pillH, 8.0, 8.0) }
        }
        pill.uiGraphics().xy(20.0, pillH / 2.0).updateShape { drawCoinIcon(COLOR_ACCENT_GOLD) }
        val pillText = crisp(pill.text("${profile.coins}", textSize = 15.0, color = white))
        pillText.xy(34.0, (pillH - 15.0) / 2.0 - 1.0)

        // --- Body: sidebar tabs, inventory list, and the tab's card grid. Switching tabs rebuilds
        // only this container in place (see renderBody below) instead of reloading the whole
        // scene via changeTo, which used to reload the backdrop/top bar/textures every time too.
        var currentTab = initialTab
        val bodyContainer = container()

        suspend fun Container.renderBody(tab: StoreTab) {
            removeChildren()
            profile = profileStorage.getProfile()
            pillText.text = "${profile.coins}"

            // --- Sidebar ---
            val sidebarX = 20.0
            val sidebarW = 210.0
            val tabH = 42.0
            val tabY0 = topBarH + 8.0

            createTexturedTab(sidebarX, tabY0, sidebarW, tabH, "POWER-UPS", tab == StoreTab.POWER_UPS, "button1.png", { drawBoltIcon(ink) }) {
                if (currentTab != StoreTab.POWER_UPS) { currentTab = StoreTab.POWER_UPS; bodyContainer.renderBody(currentTab) }
            }
            createTexturedTab(sidebarX, tabY0 + tabH + 8.0, sidebarW, tabH, "COINS", tab == StoreTab.COINS, "button2.png", { drawCoinStackIcon(ink) }) {
                if (currentTab != StoreTab.COINS) { currentTab = StoreTab.COINS; bodyContainer.renderBody(currentTab) }
            }

            val thumbY = tabY0 + 2 * (tabH + 8.0) + 4.0
            val thumbH = 120.0
            val bgBmp = try { resourcesVfs["bg_menu.jpg"].readBitmap() } catch (e: Exception) { null }
            if (bgBmp != null) {
                val img = image(bgBmp) { size(sidebarW, thumbH) }.xy(sidebarX, thumbY)
                img.colorMul = Colors["#4A4A4E"]
            } else {
                uiGraphics().xy(sidebarX, thumbY).updateShape { fill(Colors["#1A1A1D"]) { rect(0.0, 0.0, sidebarW, thumbH) } }
            }

            // Real owned counts, not decorative filler.
            val invY = thumbY + thumbH + 14.0
            crisp(text("INVENTORY", textSize = 11.0, font = bebas, color = dim)).xy(sidebarX, invY)
            for ((i, p) in powerupDefs.withIndex()) {
                val rowY = invY + 18.0 + i * 22.0
                uiGraphics().xy(sidebarX + 12.0, rowY + 6.0).updateShape { p.icon(this, muted) }
                crisp(text(p.title, textSize = 9.5, color = Colors["#C9C9CC"])).xy(sidebarX + 28.0, rowY)
                val owned = profile.getPowerupCount(p.type)
                crisp(text("x$owned", textSize = 10.0, color = if (owned > 0) COLOR_ACCENT_GOLD else dim)).xy(sidebarX + sidebarW - 28.0, rowY)
            }

            // --- Main content ---
            val mainX = sidebarX + sidebarW + 20.0
            val mainW = canvasW - mainX - 20.0

            fun sectionHeader(y: Double, label: String, iconRenderer: ShapeBuilder.() -> Unit): Double {
                uiGraphics().xy(mainX + 8.0, y + 8.0).updateShape { iconRenderer() }
                crisp(text(label, textSize = 15.0, font = bebas, color = white)).xy(mainX + 26.0, y)
                uiGraphics().xy(mainX + 130.0, y + 9.0).updateShape {
                    stroke(Colors.WHITE.withAd(0.15), StrokeInfo(thickness = 1.0)) {
                        moveTo(0.0, 0.0); lineTo(mainW - 130.0, 0.0)
                    }
                }
                return y + 34.0
            }

            // Plain rectangular price/buy button - no worn texture, matches the main menu buttons.
            fun priceButton(parent: Container, x: Double, y: Double, w: Double, h: Double, label: String, onTap: suspend () -> Unit): Container {
                val btn = parent.container().xy(x, y)
                val bg = btn.uiGraphics()
                fun draw(fillColor: RGBA) {
                    bg.updateShape {
                        clear()
                        fill(fillColor) { rect(0.0, 0.0, w, h) }
                        stroke(ink, StrokeInfo(thickness = 2.0)) { rect(0.0, 0.0, w, h) }
                    }
                }
                draw(Colors["#F6F4EE"])
                val t = crisp(btn.text(label, textSize = 13.0, font = bebas, color = ink))
                t.xy((w - t.width) / 2.0, (h - 13.0) / 2.0 - 1.0)
                btn.onOver { draw(Colors.WHITE) }
                btn.onOut { draw(Colors["#F6F4EE"]) }
                btn.mouse { onClick { onTap() } }
                return btn
            }

            when (tab) {
                StoreTab.POWER_UPS -> {
                    val gridY = sectionHeader(topBarH + 8.0, "POWER-UPS") { drawBoltIcon(COLOR_ACCENT_GOLD) }
                    val cols = 2
                    val gapX = 14.0
                    val gapY = 12.0
                    val cardW = (mainW - gapX) / 2.0
                    val cardH = ((canvasH - gridY - 20.0 - gapY) / 2.0).coerceAtMost(165.0)

                    for ((i, p) in powerupDefs.withIndex()) {
                        val col = i % cols
                        val row = i / cols
                        val cx = mainX + col * (cardW + gapX)
                        val cy = gridY + row * (cardH + gapY)
                        val card = container().xy(cx, cy)
                        card.uiGraphics().updateShape {
                            fill(Colors["#141416"]) { rect(0.0, 0.0, cardW, cardH) }
                            stroke(Colors.WHITE.withAd(0.08), StrokeInfo(thickness = 1.0)) { rect(0.0, 0.0, cardW, cardH) }
                        }

                        // Icon on left
                        card.uiGraphics().xy(36.0, 42.0).updateShape { p.icon(this, white) }

                        // Title
                        val titleT = crisp(card.text(p.title, textSize = 13.0, font = bebas, color = white))
                        titleT.xy(70.0, 14.0)

                        // Description
                        val descT = crisp(card.text(p.description, textSize = 9.5, color = muted))
                        descT.xy(70.0, 36.0)

                        // Owned count badge
                        val owned = profile.getPowerupCount(p.type)
                        val ownedT = crisp(card.text("OWNED: x$owned", textSize = 10.0, color = if (owned > 0) COLOR_ACCENT_GOLD else dim))
                        ownedT.xy(70.0, 72.0)

                        // Price/Buy button
                        val btnW = cardW - 84.0
                        val btnH = 28.0
                        priceButton(card, 70.0, cardH - btnH - 12.0, btnW, btnH, "${p.cost} COINS") {
                            val fresh = profileStorage.getProfile()
                            if (fresh.coins >= p.cost) {
                                profileStorage.buyPowerup(p.id, p.cost)
                                showFeedback("+1 ${p.title}", isSuccess = true)
                                stage?.launchImmediately { bodyContainer.renderBody(currentTab) }
                            } else {
                                showFeedback("Not enough coins.", isSuccess = false)
                            }
                        }
                    }
                }
                StoreTab.COINS -> {
                    val gridY = sectionHeader(topBarH + 8.0, "COINS") { drawCoinStackIcon(COLOR_ACCENT_GOLD) }
                    val gap = 16.0
                    val cardW = (mainW - gap * 2) / 3.0
                    val cardH = (canvasH - gridY - 20.0).coerceAtMost(240.0)

                    for ((i, pack) in coinPacks.withIndex()) {
                        val cx = mainX + i * (cardW + gap)
                        val card = container().xy(cx, gridY)
                        card.uiGraphics().updateShape {
                            fill(Colors["#141416"]) { rect(0.0, 0.0, cardW, cardH) }
                            stroke(Colors.WHITE.withAd(0.08), StrokeInfo(thickness = 1.0)) { rect(0.0, 0.0, cardW, cardH) }
                        }
                        if (pack.bestValue) {
                            // Background must be added before the label - a Container's later
                            // children paint on top of earlier ones, and the badge rect was being
                            // added after the text, hiding it completely.
                            val badgeW = 74.0
                            val badgeX = cardW - badgeW - 8.0
                            card.uiGraphics().xy(badgeX, 8.0).updateShape {
                                fill(COLOR_ACCENT_GOLD) { roundRect(0.0, 0.0, badgeW, 16.0, 4.0, 4.0) }
                            }
                            val badgeText = crisp(card.text("BEST VALUE", textSize = 9.0, color = ink))
                            badgeText.xy(badgeX + (badgeW - badgeText.width) / 2.0, 10.0)
                        }
                        val amountT = crisp(card.text("${pack.amount}", textSize = 22.0, font = bebas, color = white))
                        amountT.xy((cardW - amountT.width) / 2.0, 40.0)
                        card.uiGraphics().xy(cardW / 2.0, 96.0).updateShape { drawCoinStackIcon(COLOR_ACCENT_GOLD) }

                        val btnW = cardW - 24.0
                        val btnH = 32.0
                        priceButton(card, 12.0, cardH - btnH - 12.0, btnW, btnH, pack.price) {
                            bridge.purchase(pack.id) { success ->
                                if (success) {
                                    profileStorage.addCoins(pack.amount)
                                    showFeedback("+${pack.amount} Coins added!", isSuccess = true)
                                    stage?.launchImmediately { bodyContainer.renderBody(currentTab) }
                                } else {
                                    showFeedback("Purchase cancelled.", isSuccess = false)
                                }
                            }
                        }
                    }
                }
            }
        }

        val plusBtn = container().xy(canvasW - 20.0 - plusBtnW, (topBarH - plusBtnW) / 2.0)
        val plusG = plusBtn.uiGraphics()
        fun drawPlusBg(hover: Boolean) {
            plusG.updateShape {
                clear()
                fill(if (hover) Colors["#242428"] else Colors["#18181B"]) { roundRect(0.0, 0.0, plusBtnW, plusBtnW, 8.0, 8.0) }
                stroke(Colors.WHITE.withAd(0.08), StrokeInfo(thickness = 1.0)) { roundRect(0.0, 0.0, plusBtnW, plusBtnW, 8.0, 8.0) }
            }
        }
        drawPlusBg(false)
        plusBtn.uiGraphics().xy(plusBtnW / 2.0, plusBtnW / 2.0).updateShape { drawPlusIcon(white) }
        plusBtn.onOver { drawPlusBg(true) }
        plusBtn.onOut { drawPlusBg(false) }
        plusBtn.mouse {
            onClick {
                if (currentTab != StoreTab.COINS) { currentTab = StoreTab.COINS; bodyContainer.renderBody(currentTab) }
            }
        }

        bodyContainer.renderBody(currentTab)
    }
}
