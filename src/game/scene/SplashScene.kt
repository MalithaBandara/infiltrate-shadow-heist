package game.scene

import com.sample.demo.purchases.*
import game.scene.UiComponents.COLOR_BORDER_CYAN
import game.scene.UiComponents.COLOR_TEXT_MUTED
import game.scene.UiComponents.drawAtmosphericBackdrop
import korlibs.image.color.*
import korlibs.image.format.readBitmap
import korlibs.io.async.*
import korlibs.io.file.std.resourcesVfs
import korlibs.korge.scene.*
import korlibs.korge.view.*
import korlibs.time.*

class SplashScene : Scene() {

    override suspend fun SContainer.sceneMain() {
        // Atmospheric dark skyline backdrop with scanner grid
        drawAtmosphericBackdrop(800.0, 480.0)

        // Vignette framing / scanner corners
        solidRect(800.0, 3.0, COLOR_BORDER_CYAN.withAd(0.6)).xy(0.0, 0.0)
        solidRect(800.0, 3.0, COLOR_BORDER_CYAN.withAd(0.6)).xy(0.0, 477.0)

        // Logo & Branding Container
        val logoContainer = container().xy(0.0, 90.0)

        // Tactical classification tag
        val classTag = logoContainer.text("CLASSIFIED INFILTRATION PROTOCOL // SHIPATON 2026", textSize = 11.0, color = COLOR_BORDER_CYAN)
        classTag.xy((800.0 - classTag.width) / 2.0, 0.0)

        // Main logo mark
        val logoBitmap = resourcesVfs["logo_main.png"].readBitmap()
        val logoWidth = 380.0
        val logoHeight = logoWidth * (logoBitmap.height.toDouble() / logoBitmap.width.toDouble())
        logoContainer.image(logoBitmap) { size(logoWidth, logoHeight) }.xy((800.0 - logoWidth) / 2.0, 22.0)

        // Tagline
        val tagline = logoContainer.text("PARKOUR • STEALTH • EXTRACTION", textSize = 12.0, color = COLOR_TEXT_MUTED)
        tagline.xy((800.0 - tagline.width) / 2.0, 22.0 + logoHeight + 12.0)

        // Loading Bar Container (Glassmorphic panel)
        val progressContainer = container().xy(230.0, 310.0)
        val barWidth = 340.0
        val barHeight = 8.0

        progressContainer.solidRect(barWidth, barHeight + 8.0, Colors["#101622"])
        progressContainer.solidRect(barWidth, 1.0, COLOR_BORDER_CYAN.withAd(0.4)).xy(0.0, 0.0)
        progressContainer.solidRect(barWidth, 1.0, COLOR_BORDER_CYAN.withAd(0.4)).xy(0.0, barHeight + 7.0)

        val progressTrack = progressContainer.solidRect(barWidth - 8.0, barHeight, Colors["#182338"]).xy(4.0, 4.0)
        val progressBar = progressContainer.solidRect(0.0, barHeight, COLOR_BORDER_CYAN).xy(4.0, 4.0)

        // Status Decryption Log Text
        val statusText = text("ESTABLISHING ENCRYPTED LINK...", textSize = 12.0, color = COLOR_BORDER_CYAN)
        statusText.xy((800.0 - statusText.width) / 2.0, 342.0)

        // Initialize RevenueCat SDK Bridge safely
        try {
            val bridge = getPurchasesBridge()
            bridge.initialize("goog_infiltrate_shadow_heist_key")
        } catch (e: Throwable) {
            println("[SplashScene] PurchasesBridge init warning: ${e.message}")
        }

        var timer = 0.0
        val targetDuration = 1.2
        var transitioned = false

        addUpdater { dt ->
            val dtSec = dt.seconds
            timer += dtSec
            val progress = (timer / targetDuration).coerceIn(0.0, 1.0)
            progressBar.width = (barWidth - 8.0) * progress

            when {
                progress < 0.35 -> {
                    statusText.text = "ESTABLISHING ENCRYPTED LINK..."
                }
                progress < 0.70 -> {
                    statusText.text = "CALIBRATING SHADOW SUIT SENSORS..."
                }
                progress < 0.95 -> {
                    statusText.text = "SYNCING TACTICAL RECON..."
                }
                else -> {
                    statusText.text = "INFILTRATION COMMENCED"
                    statusText.color = Colors["#00e676"]
                }
            }
            statusText.xy((800.0 - statusText.width) / 2.0, 342.0)

            if (timer >= targetDuration && !transitioned) {
                transitioned = true
                val ctx = stage?.coroutineContext ?: views.coroutineContext
                launchImmediately(ctx) {
                    sceneContainer.changeTo { MainMenuScene() }
                }
            }
        }
    }
}
