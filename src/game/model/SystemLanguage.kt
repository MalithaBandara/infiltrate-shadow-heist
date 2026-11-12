package game.model

/**
 * The device's preferred UI languages as BCP-47 tags, most preferred first (e.g. ["zh-Hant-TW",
 * "en-US"]). Each platform host sets [preferredTags] at startup, before the first profile storage
 * is created: MainActivity.onCreate on Android, MainMenuComposeScreen.makeViewController on iOS,
 * main() on desktop.
 *
 * It's a settable hook rather than expect/actual because this directory is compiled into two
 * modules (the KorGE game and paywall-build) across five-plus targets. The default (no tags)
 * means "no detection", so tests and any host that never sets it keep the old English default.
 *
 * Read only by MapBackedGameProfileStorage, and only when no language has been saved yet - see
 * its loadFromStorage().
 */
object SystemLanguage {
    var preferredTags: () -> List<String> = { emptyList() }
}
