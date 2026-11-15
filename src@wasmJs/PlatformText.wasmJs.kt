package game.scene

/** No platform text here: GameText keeps KorGE's own path (see src/PlatformText.kt). */
actual fun renderPlatformText(text: String, pixelSize: Double, bold: Boolean, languageTag: String): PlatformTextImage? = null
