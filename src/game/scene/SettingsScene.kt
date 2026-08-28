package game.scene

import game.scene.UiComponents.COLOR_ACCENT_CYAN
import game.scene.UiComponents.COLOR_ACCENT_GREEN
import game.scene.UiComponents.COLOR_ACCENT_RED
import game.scene.UiComponents.COLOR_PRIMARY
import game.scene.UiComponents.COLOR_TEXT_LIGHT
import game.scene.UiComponents.COLOR_TEXT_MUTED
import game.scene.UiComponents.createMenuButton
import game.scene.UiComponents.createTacticalCard
import game.scene.UiComponents.createToast
import game.scene.UiComponents.drawAtmosphericBackdrop
import korlibs.image.color.*
import korlibs.korge.scene.*
import korlibs.korge.service.storage.*
import korlibs.korge.view.*
import korlibs.korge.view.vector.*
import korlibs.korge.input.*

class SettingsScene : Scene() {

    override suspend fun SContainer.sceneMain() {
        val storage = views.storage

        drawAtmosphericBackdrop(800.0, 480.0)

        // Logo / Header
        val logoContainer = container().xy(40.0, 40.0)
        logoContainer.text("SETTINGS", textSize = 64.0, color = COLOR_PRIMARY)

        val cardWidth = 460.0
        val panel = createTacticalCard(40.0, 140.0, cardWidth, 260.0, accentColor = COLOR_ACCENT_CYAN)

        var sfxEnabled = storage.getOrNull("setting_sfx")?.toBoolean() ?: true
        var musicEnabled = storage.getOrNull("setting_music")?.toBoolean() ?: true

        fun drawToggle(btn: Container, isEnabled: Boolean) {
            btn.removeChildren()
            val g = btn.graphics()
            val color = if (isEnabled) COLOR_ACCENT_GREEN else COLOR_ACCENT_RED
            g.updateShape {
                fill(Colors.BLACK.withAd(0.8)) { roundRect(0.0, 0.0, 60.0, 30.0, 15.0, 15.0) }
                fill(color) {
                    if (isEnabled) {
                        roundRect(30.0, 4.0, 26.0, 22.0, 11.0, 11.0)
                    } else {
                        roundRect(4.0, 4.0, 26.0, 22.0, 11.0, 11.0)
                    }
                }
            }
        }

        // SFX Toggle
        panel.text("SOUND EFFECTS", textSize = 20.0, color = COLOR_PRIMARY).xy(40.0, 30.0)
        
        val sfxToggle = panel.container().xy(360.0, 26.0)
        drawToggle(sfxToggle, sfxEnabled)
        val sfxBtn = panel.solidRect(60.0, 30.0, Colors.TRANSPARENT).xy(360.0, 26.0)
        sfxBtn.mouse {
            onClick {
                sfxEnabled = !sfxEnabled
                storage["setting_sfx"] = sfxEnabled.toString()
                drawToggle(sfxToggle, sfxEnabled)
                sceneContainer.createToast("SFX ${if (sfxEnabled) "ENABLED" else "MUTED"}")
            }
        }

        // Music Toggle
        panel.text("BACKGROUND MUSIC", textSize = 20.0, color = COLOR_PRIMARY).xy(40.0, 80.0)
        
        val musicToggle = panel.container().xy(360.0, 76.0)
        drawToggle(musicToggle, musicEnabled)
        val musicBtn = panel.solidRect(60.0, 30.0, Colors.TRANSPARENT).xy(360.0, 76.0)
        musicBtn.mouse {
            onClick {
                musicEnabled = !musicEnabled
                storage["setting_music"] = musicEnabled.toString()
                drawToggle(musicToggle, musicEnabled)
                sceneContainer.createToast("MUSIC ${if (musicEnabled) "ENABLED" else "MUTED"}")
            }
        }

        panel.solidRect(cardWidth - 80.0, 1.0, Colors.WHITE.withAd(0.1)).xy(40.0, 140.0)

        panel.createMenuButton(
            text = "RESET PROGRESS",
            width = 240.0,
            iconPath = null
        ) {
            storage.removeAll()
            sceneContainer.createToast("ALL DATA PURGED.", color = COLOR_ACCENT_RED)
            sceneContainer.changeTo { MainMenuScene() }
        }.xy(40.0, 170.0)

        // Back Button
        createMenuButton(
            text = "BACK",
            iconPath = null
        ) {
            sceneContainer.changeTo { MainMenuScene() }
        }.xy(40.0, 420.0)
    }
}
