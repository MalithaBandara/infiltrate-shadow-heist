package com.infiltrate.ui

import androidx.compose.runtime.Composable

/**
 * Looping background music for the non-gameplay screens.
 *
 * Shaped as a composable with an expect/actual per platform for the same reason
 * [LoopingVideoBackground] is: this module's media assets are not Compose resources, they are
 * bundled per platform (iOS reads `NSBundle`, desktop reads the repo's `resources/` directory,
 * Android reads `res/raw`), and each platform already has the player this module needs -
 * AVAudioPlayer, JavaFX Media, and android.media.MediaPlayer respectively. Following the video's
 * pattern rather than inventing a second one keeps both media paths recognisable.
 *
 * Composed once, in NavigationRoot rather than in MainMenuScreen, so the track keeps playing
 * across Missions / Store / Settings instead of restarting from the top every time the player
 * comes back to the main menu. It stops when the composable leaves the tree, which is what
 * happens when the shell swaps the root view controller over to KorGE for gameplay.
 *
 * A missing or unreadable track is silence, never a crash - the menu has to come up either way.
 *
 * @param volume 0..1, taken from the player's music setting in GameProfile.
 */
@Composable
expect fun MenuMusic(
    trackName: String = "mainmenu",
    trackExtension: String = "mp3",
    volume: Float
)
