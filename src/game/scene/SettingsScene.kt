package game.scene

import game.model.GameProfileStorage
import game.model.MapBackedGameProfileStorage
import game.scene.UiComponents.COLOR_ACCENT_CYAN
import game.scene.UiComponents.COLOR_ACCENT_GOLD
import game.scene.UiComponents.COLOR_ACCENT_RED
import game.scene.UiComponents.createTexturedTab
import game.scene.UiComponents.createToast
import game.scene.UiComponents.createVolumeSlider
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

enum class SettingsTab { AUDIO, ABOUT }

class SettingsScene(private val initialTab: SettingsTab = SettingsTab.AUDIO) : Scene() {

    private val ink = Colors["#17140F"]
    private val white = Colors.WHITE
    private val muted = Colors["#B7B7BC"]
    private val dim = Colors["#6E6E72"]

    private fun ShapeBuilder.drawBackChevron() {
        stroke(white, StrokeInfo(thickness = 2.4)) {
            moveTo(4.0, -8.0); lineTo(-5.0, 0.0); lineTo(4.0, 8.0)
        }
    }

    private fun ShapeBuilder.drawSpeakerIcon(c: RGBA) {
        fill(c) {
            moveTo(-9.0, -3.0); lineTo(-4.0, -3.0); lineTo(3.0, -9.0); lineTo(3.0, 9.0); lineTo(-4.0, 3.0); lineTo(-9.0, 3.0)
            close()
        }
        stroke(c, StrokeInfo(thickness = 1.6)) {
            moveTo(8.0, -5.0); lineTo(10.0, -3.0); lineTo(10.0, 3.0); lineTo(8.0, 5.0)
        }
    }

    private fun ShapeBuilder.drawInfoIcon(c: RGBA) {
        stroke(c, StrokeInfo(thickness = 1.6)) { circle(Point(0.0, 0.0), 10.0) }
        fill(c) {
            circle(Point(0.0, -4.5), 1.6)
            roundRect(-1.4, -1.0, 2.8, 7.0, 1.0, 1.0)
        }
    }

    override suspend fun SContainer.sceneMain() {
        val profileStorage: GameProfileStorage = MapBackedGameProfileStorage(
            getRaw = { views.storage[it] },
            setRaw = { k, v -> views.storage[k] = v }
        )
        val profile = profileStorage.getProfile()
        val bebas = try { resourcesVfs["BebasNeue-Regular.ttf"].readTtfFont() } catch (e: Exception) { DefaultTtfFont }

        val canvasW = sceneWidth.toDouble()
        val canvasH = sceneHeight.toDouble()

        fun crisp(t: Text): Text { t.graphicsRenderer = GraphicsRenderer.GPU; return t }
        fun toast(message: String, color: RGBA = COLOR_ACCENT_CYAN) {
            sceneContainer.createToast(message, color = color)
        }

        solidRect(canvasW, canvasH, Colors["#0B0B0D"])

        // --- Top bar (same layout as StoreScene, for a consistent dark "terminal" theme) ---
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

        val title = crisp(text("SETTINGS", textSize = 24.0, font = bebas, color = white))
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
        pill.uiGraphics().xy(20.0, pillH / 2.0).updateShape {
            fill(COLOR_ACCENT_GOLD) { circle(Point(0.0, 0.0), 8.0) }
            stroke(Colors.BLACK.withAd(0.35), StrokeInfo(thickness = 1.0)) { circle(Point(0.0, 0.0), 8.0) }
        }
        crisp(pill.text("${profile.coins}", textSize = 15.0, color = white)).xy(34.0, (pillH - 15.0) / 2.0 - 1.0)

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
        plusBtn.uiGraphics().xy(plusBtnW / 2.0, plusBtnW / 2.0).updateShape {
            stroke(white, StrokeInfo(thickness = 2.2)) {
                moveTo(-6.0, 0.0); lineTo(6.0, 0.0)
                moveTo(0.0, -6.0); lineTo(0.0, 6.0)
            }
        }
        plusBtn.onOver { drawPlusBg(true) }
        plusBtn.onOut { drawPlusBg(false) }
        plusBtn.mouse { onClick { sceneContainer.changeTo { StoreScene(StoreTab.COINS) } } }

        // --- Body: sidebar tabs + main content. Switching tabs rebuilds only this container in
        // place (see renderBody below) instead of reloading the whole scene via changeTo, which
        // used to reload the backdrop/top bar/textures every time too.
        var currentTab = initialTab
        val bodyContainer = container()

        suspend fun Container.renderBody(tab: SettingsTab) {
            removeChildren()

            // Only the settings that actually do something get a section: real audio sliders
            // backed by GameProfile, and an about/legal page. No tutorials/difficulty/graphics/
            // language tabs - nothing in this game reads those yet.
            val sidebarX = 20.0
            val sidebarW = 210.0
            val tabH = 42.0
            val tabY0 = topBarH + 8.0

            createTexturedTab(sidebarX, tabY0, sidebarW, tabH, "AUDIO", tab == SettingsTab.AUDIO, "button1.png", { drawSpeakerIcon(ink) }) {
                if (currentTab != SettingsTab.AUDIO) { currentTab = SettingsTab.AUDIO; bodyContainer.renderBody(currentTab) }
            }
            createTexturedTab(sidebarX, tabY0 + tabH + 8.0, sidebarW, tabH, "ABOUT", tab == SettingsTab.ABOUT, "button2.png", { drawInfoIcon(ink) }) {
                if (currentTab != SettingsTab.ABOUT) { currentTab = SettingsTab.ABOUT; bodyContainer.renderBody(currentTab) }
            }

            // --- Main content ---
            val mainX = sidebarX + sidebarW + 20.0
            val mainW = canvasW - mainX - 20.0

            fun sectionHeader(y: Double, label: String): Double {
                crisp(text(label, textSize = 15.0, font = bebas, color = white)).xy(mainX, y)
                uiGraphics().xy(mainX + 110.0, y + 9.0).updateShape {
                    stroke(Colors.WHITE.withAd(0.15), StrokeInfo(thickness = 1.0)) {
                        moveTo(0.0, 0.0); lineTo(mainW - 110.0, 0.0)
                    }
                }
                return y + 34.0
            }

            fun settingLabel(y: Double, title: String, subtitle: String) {
                crisp(text(title, textSize = 14.0, font = bebas, color = white)).xy(mainX, y)
                crisp(text(subtitle, textSize = 11.0, color = muted)).xy(mainX, y + 18.0)
            }

            when (tab) {
                SettingsTab.AUDIO -> {
                    var y = sectionHeader(topBarH + 24.0, "AUDIO")
                    val sliderW = 200.0
                    val sliderX = mainX + mainW - sliderW - 20.0

                    settingLabel(y, "MUSIC VOLUME", "Background music level")
                    createVolumeSlider(sliderX, y + 6.0, sliderW, profile.musicVolume) { v -> profileStorage.setMusicVolume(v) }
                    y += 56.0

                    settingLabel(y, "SFX VOLUME", "Sound effect level")
                    createVolumeSlider(sliderX, y + 6.0, sliderW, profile.sfxVolume) { v -> profileStorage.setSfxVolume(v) }
                    y += 56.0

                    uiGraphics().xy(mainX, y).updateShape {
                        stroke(Colors.WHITE.withAd(0.1), StrokeInfo(thickness = 1.0)) { moveTo(0.0, 0.0); lineTo(mainW, 0.0) }
                    }
                    y += 24.0

                    // Destructive action, styled distinctly (dark red) as its own "danger zone" row.
                    val dangerH = 58.0
                    val danger = container().xy(mainX, y)
                    danger.uiGraphics().updateShape {
                        fill(Colors["#2A1416"]) { rect(0.0, 0.0, mainW, dangerH) }
                        stroke(COLOR_ACCENT_RED.withAd(0.5), StrokeInfo(thickness = 1.0)) { rect(0.0, 0.0, mainW, dangerH) }
                    }
                    crisp(danger.text("RESET PROGRESS", textSize = 14.0, font = bebas, color = COLOR_ACCENT_RED)).xy(16.0, 12.0)
                    crisp(danger.text("Clear all mission progress and inventory", textSize = 11.0, color = muted)).xy(16.0, 34.0)
                    danger.mouse {
                        onClick {
                            views.storage.removeAll()
                            toast("ALL DATA PURGED.", COLOR_ACCENT_RED)
                            sceneContainer.changeTo { MainMenuScene() }
                        }
                    }
                }
                SettingsTab.ABOUT -> {
                    var y = sectionHeader(topBarH + 24.0, "ABOUT")

                    fun linkRow(label: String) {
                        crisp(text(label, textSize = 14.0, font = bebas, color = white)).xy(mainX, y)
                        val hit = solidRect(mainW, 30.0, Colors.TRANSPARENT).xy(mainX, y - 6.0)
                        hit.mouse { onClick { toast("Not available yet.") } }
                        y += 36.0
                    }
                    linkRow("PRIVACY POLICY")
                    linkRow("TERMS OF SERVICE")

                    crisp(text("v1.0.0", textSize = 11.0, color = dim)).xy(mainX, canvasH - 30.0)
                }
            }
        }

        bodyContainer.renderBody(currentTab)
    }
}
