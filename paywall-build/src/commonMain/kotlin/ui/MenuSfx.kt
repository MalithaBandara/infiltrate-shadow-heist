package com.infiltrate.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The menu's one-shot UI click.
 *
 * Shaped as an `expect`/`actual` for the same reason [MenuMusic] and [LoopingVideoBackground]
 * are: this module's media are not Compose resources, they are bundled per platform (iOS reads
 * `NSBundle`, desktop reads the repo's `resources/` directory, Android reads `res/raw` or the
 * `assets/` copy KorGE's Gradle plugin makes of `resources/`). Following that existing pattern
 * rather than inventing a second one keeps all three media paths recognisable.
 *
 * Where it differs from [MenuMusic] is voices. Music is one player looping forever; a click has
 * to be able to overlap itself, because a player can tap the next button before the last click
 * has finished. Each platform therefore uses the API that handles that natively - a small
 * round-robin pool of `AVAudioPlayer`s on iOS, `SoundPool` on Android, JavaFX `AudioClip` on
 * desktop - rather than one player restarted, which would cut the previous tap off mid-sound.
 *
 * Delivered through a [CompositionLocal] rather than a parameter threaded down through every
 * screen: the click is needed on buttons several layers deep in `MenuComponents`, and passing a
 * lambda through every intermediate composable to reach them would touch far more of the UI than
 * the feature is worth. The default is a no-op, so any screen composed outside a provider - a
 * preview, a test - simply stays silent instead of failing.
 *
 * A missing or unreadable clip is silence, never a crash. The menu has to come up either way.
 */
val LocalUiClick = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Prepares the click for the current platform and returns a function that fires it.
 *
 * @param volume 0..1, taken from the player's SFX setting in GameProfile. Read at call time
 *   rather than captured, so a move of the Settings slider applies to the very next tap.
 */
@Composable
expect fun rememberUiClick(volume: Float): () -> Unit
