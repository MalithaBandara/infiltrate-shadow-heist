package com.sample.demo.audio

// No native path on iOS - this is an Android-only fix (see GameSfxOutput's own doc comment for
// why). GameAudio.kt's playSfx falls straight back to korlibs, unchanged from before this existed.
actual fun getGameSfxOutput(): GameSfxOutput? = null
