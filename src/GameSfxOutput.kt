package com.sample.demo.audio

/**
 * Optional platform-native path for gameplay one-shot SFX, sitting alongside korlibs' own
 * `Sound.play()` in `GameAudio.kt` rather than replacing it - see that file's `playSfx` for the
 * fallback wiring.
 *
 * Real implementation only exists on Android (see src@android/GameSfxOutput.android.kt): every
 * `Sound.play()` call constructs a brand-new `android.media.AudioTrack` (confirmed by decompiling
 * korlibs-audio-core - `SoundAudioData.play()` calls `NativeSoundProvider.createNewPlatformAudioOutput`
 * unconditionally, with no pooling at that layer), and on a real device that collides with the
 * continuous `AudioTrack` gameplay's background music already holds open - reproduced on a Galaxy
 * S25 Ultra as an occasional audible "static" tick, root-caused by comparing a captured `adb logcat`
 * session against a mute-the-music A/B test. `MenuSfx.android.kt` already solved the same class of
 * problem for the Compose menu's clicks with `SoundPool`, which holds one long-lived pooled output
 * open rather than spinning up a new one per play; this does the same for gameplay's one-shots.
 *
 * Every other target returns `null` and is unaffected - `playSfx` falls straight back to the
 * existing korlibs path exactly as before, so this is additive only.
 */
interface GameSfxOutput {
    /** Best-effort async preload; safe to call repeatedly with the same paths. */
    fun prepare(clipFiles: List<String>)

    /** Returns true if this call was actually played through the native path. */
    fun play(clipFile: String, volume: Float): Boolean

    /**
     * Best-effort: decodes and starts looping background music through the same native path as
     * one-shots (see this file's own doc comment on why that now matters). Returns true if the
     * native path is handling music from here on - GameAudio.kt's own music control falls back to
     * korlibs entirely when this returns false, exactly like [play] does per clip.
     */
    fun prepareMusic(clipFile: String): Boolean

    /** No-op if [prepareMusic] was never called or returned false. */
    fun setMusicVolume(volume: Float)

    /** No-op if [prepareMusic] was never called or returned false. */
    fun stopMusic()
}

expect fun getGameSfxOutput(): GameSfxOutput?
