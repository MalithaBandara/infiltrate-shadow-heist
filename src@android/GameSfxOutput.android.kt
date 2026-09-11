package com.sample.demo.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.Process
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import korlibs.audio.sound.AndroidNativeSoundProvider

/**
 * Set once by android-shell's MainActivity.onCreate(), same wiring style as
 * AndroidContinueAdBridgeState/AndroidLevelExitBridgeState - a plain shared object rather than a
 * constructor parameter, since GameAudio.kt (commonMain) has no way to receive a Context directly.
 */
/**
 * Just the pause/resume capability, kept separate from the concrete (file-private)
 * [AndroidGameSfxOutput] so [AndroidGameSfxOutputState] can hold a reference to it without a
 * visibility conflict - a public property can't expose a private-in-file type.
 */
interface PausableAudioEngine {
    fun pauseEngine()
    fun resumeEngine()
}

object AndroidGameSfxOutputState {
    @Volatile
    var context: Context? = null

    @Volatile
    var activeEngine: PausableAudioEngine? = null

    /**
     * Meant to be called from the host Activity's onPause/onResume - the mixer thread/AudioTrack
     * described on [AndroidGameSfxOutput] runs forever once started, with no lifecycle awareness
     * of its own, so without this it would keep playing after leaving the app entirely, not just
     * returning to an in-app menu (which already stops music via stopMusic on its own).
     */
    fun pauseEngine() {
        activeEngine?.pauseEngine()
    }

    fun resumeEngine() {
        activeEngine?.resumeEngine()
    }
}

/**
 * Real device testing worked through per-call `AudioTrack` (the original korlibs behavior),
 * `SoundPool`, a hand-rolled `MODE_STATIC` `AudioTrack` pool, and joining korlibs' own audio
 * session - each addressed a real, confirmed difference from korlibs' own bgmusic track, and
 * none of them closed the static. It also reproduced starting the phone's own screen recorder
 * (nothing to do with this app's code) yet never in any other game on the same device - narrowing
 * it to something structural about how this app opens audio, not a specific API misuse.
 *
 * This is the structural fix: **one** continuously-running `AudioTrack` (`MODE_STREAM`) for all
 * of gameplay's audio - music and every one-shot - fed by a single mixer thread that sums PCM
 * samples from a list of active "voices" before writing. This is how real engines do it (Unity,
 * Unreal, FMOD/Wwise all mix in software down to one hardware stream); the earlier attempts were
 * all still opening a separate `AudioTrack`/`SoundPool` stream per concept (music, then N SFX
 * slots), which is the opposite of that model and exactly the shape of setup a device's DSP chain
 * has to keep reconciling. With one stream, there is nothing left to reconcile.
 *
 * `bgmusic.mp3` is decoded once via `MediaExtractor`/`MediaCodec` (real 44.1kHz stereo PCM, not a
 * guess - see the `ffprobe` check that produced [prepareMusic]'s channel handling). SFX clips
 * reuse the existing WAV parsing, mono at the same 44.1kHz. The mixer upmixes mono into both
 * output channels; music and SFX share the same 44.1kHz stereo output format throughout, so no
 * resampling is needed anywhere in the mix loop.
 */
private class AndroidGameSfxOutput : GameSfxOutput, PausableAudioEngine {
    init {
        AndroidGameSfxOutputState.activeEngine = this
    }

    private class Clip(val pcm: ShortArray, val channels: Int)

    private class Voice(
        val pcm: ShortArray,
        val channels: Int,
        @Volatile var frame: Int,
        @Volatile var volume: Float,
        val looping: Boolean
    ) {
        val totalFrames: Int get() = pcm.size / channels
    }

    private val clips = mutableMapOf<String, Clip>()
    private val voices = CopyOnWriteArrayList<Voice>()
    private var musicPcm: DecodedPcm? = null
    private var musicVoice: Voice? = null
    private var sharedSessionId: Int? = null

    @Volatile private var track: AudioTrack? = null
    @Volatile private var running = false

    override fun prepare(clipFiles: List<String>) {
        val context = AndroidGameSfxOutputState.context ?: return
        resolveSharedSessionId(context)
        for (clipFile in clipFiles) {
            if (clips.containsKey(clipFile)) continue
            try {
                val bytes = context.assets.open(clipFile).use { it.readBytes() }
                val wav = parseWav(bytes) ?: continue
                if (wav.bitsPerSample != 16) continue
                clips[clipFile] = Clip(wav.toShortArray(), wav.channels)
            } catch (_: Throwable) {
                // Falls back to korlibs per playSfx's own contract - a stripped build or bad
                // asset path should cost the player nothing beyond losing this optimization.
            }
        }
        ensureEngineStarted()
    }

    override fun play(clipFile: String, volume: Float): Boolean {
        val clip = clips[clipFile] ?: return false
        if (track == null) return false
        voices.add(Voice(clip.pcm, clip.channels, 0, volume.coerceIn(0f, 1f), looping = false))
        return true
    }

    override fun prepareMusic(clipFile: String): Boolean {
        if (musicVoice != null) return true
        val context = AndroidGameSfxOutputState.context ?: return false
        resolveSharedSessionId(context)
        return try {
            // Cached separately from musicVoice so a later stopMusic() + prepareMusic() (RESTART,
            // RETRY, a fresh level) replays from the already-decoded PCM instead of re-running
            // MediaCodec every time - decoding is the expensive part, not adding a Voice.
            val pcm = musicPcm ?: decodeMp3ToPcm(context, clipFile)?.also { musicPcm = it } ?: return false
            ensureEngineStarted()
            if (track == null) return false
            val voice = Voice(pcm.data, pcm.channels, 0, 0f, looping = true)
            musicVoice = voice
            voices.add(voice)
            true
        } catch (_: Throwable) {
            false
        }
    }

    override fun setMusicVolume(volume: Float) {
        musicVoice?.volume = volume.coerceIn(0f, 1f)
    }

    override fun stopMusic() {
        musicVoice?.let { voices.remove(it) }
        musicVoice = null
    }

    /**
     * Called via [AndroidGameSfxOutputState.pauseEngine]/`resumeEngine` from the host Activity's
     * onPause/onResume. `AudioTrack.pause()` on a `MODE_STREAM` track is enough on its own - the
     * mixer thread's blocking `write()` call (see [mixerLoop]) simply stops draining and blocks
     * once the track's internal buffer fills, with no separate thread-suspend logic needed; a
     * later `play()` resumes draining and the loop picks up right where it left off.
     */
    override fun pauseEngine() {
        try { track?.pause() } catch (_: Throwable) {}
    }

    override fun resumeEngine() {
        try { track?.play() } catch (_: Throwable) {}
    }

    private fun resolveSharedSessionId(context: Context) {
        if (sharedSessionId != null) return
        try {
            AndroidNativeSoundProvider.ensureAudioManager(context)
            val id = AndroidNativeSoundProvider.audioSessionId
            sharedSessionId = if (
                id != AndroidNativeSoundProvider.UNSET_AUDIO_SESSION_ID &&
                id != AndroidNativeSoundProvider.INVALID_AUDIO_SESSION_ID
            ) id else null
        } catch (_: Throwable) {
            sharedSessionId = null
        }
    }

    @Synchronized
    private fun ensureEngineStarted() {
        if (track != null) return
        try {
            val minBufBytes = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSizeBytes = maxOf(minBufBytes, CHUNK_SAMPLES * 2 * 6)
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
            val builder = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // Matches korlibs' own (would-be) bgmusic track - see this class's doc
                        // comment history for why that mattered even before this rewrite.
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                        .build()
                )
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSizeBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
            sharedSessionId?.let { builder.setSessionId(it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_NONE)
            }
            val t = builder.build()
            t.play()
            track = t
            running = true
            Thread({ mixerLoop() }, "GameAudioMixer").apply {
                isDaemon = true
                start()
            }
        } catch (_: Throwable) {
            track = null
        }
    }

    /**
     * One thread, one stream, for the app's whole life - never stopped, matching this project's
     * existing "prime/build once per process" precedent for gameplay audio. Reads [voices] each
     * chunk, sums every active voice's samples (mono upmixed to both channels), clamps once, and
     * blocking-writes the result - `AudioTrack.write(..., WRITE_BLOCKING)` itself paces this loop
     * to real time, so there is no separate timing/sleep logic needed here.
     */
    private fun mixerLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val mixBuffer = IntArray(CHUNK_SAMPLES)
        val outBuffer = ShortArray(CHUNK_SAMPLES)
        val t = track ?: return
        while (running) {
            java.util.Arrays.fill(mixBuffer, 0)
            for (voice in voices) {
                val ch = voice.channels
                var frame = voice.frame
                val totalFrames = voice.totalFrames
                if (totalFrames <= 0) continue
                var i = 0
                while (i < CHUNK_FRAMES) {
                    if (frame >= totalFrames) {
                        if (voice.looping) frame = 0 else break
                    }
                    val base = frame * ch
                    val vol = voice.volume
                    if (ch == 1) {
                        val s = (voice.pcm[base] * vol).toInt()
                        mixBuffer[i * 2] += s
                        mixBuffer[i * 2 + 1] += s
                    } else {
                        mixBuffer[i * 2] += (voice.pcm[base] * vol).toInt()
                        mixBuffer[i * 2 + 1] += (voice.pcm[base + 1] * vol).toInt()
                    }
                    frame++
                    i++
                }
                voice.frame = frame
                if (!voice.looping && frame >= totalFrames) {
                    voices.remove(voice)
                }
            }
            for (i in 0 until CHUNK_SAMPLES) {
                outBuffer[i] = mixBuffer[i].coerceIn(-32768, 32767).toShort()
            }
            try {
                t.write(outBuffer, 0, CHUNK_SAMPLES, AudioTrack.WRITE_BLOCKING)
            } catch (_: Throwable) {
                running = false
            }
        }
    }

    private class DecodedPcm(val data: ShortArray, val channels: Int)

    /**
     * Standard `MediaExtractor`/`MediaCodec` decode-to-PCM, run once at prepareMusic() time - the
     * whole ~73s track is short enough to hold fully decoded (44.1kHz stereo 16-bit for that
     * duration is a few MB), so the mixer thread never has to decode on the fly.
     */
    private fun decodeMp3ToPcm(context: Context, assetPath: String): DecodedPcm? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            context.assets.openFd(assetPath).use { afd ->
                extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) return null
            extractor.selectTrack(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(format, null, null, 0)
            c.start()

            var outChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val chunks = ArrayList<ShortArray>()
            var totalShorts = 0
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inIndex = c.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuffer = c.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(inBuffer, 0)
                        if (sampleSize < 0) {
                            c.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            c.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = c.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIndex >= 0 -> {
                        val outBuffer = c.getOutputBuffer(outIndex)
                        if (outBuffer != null && bufferInfo.size > 0) {
                            outBuffer.order(ByteOrder.LITTLE_ENDIAN)
                            outBuffer.position(bufferInfo.offset)
                            outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val shortBuf = outBuffer.asShortBuffer()
                            val chunk = ShortArray(shortBuf.remaining())
                            shortBuf.get(chunk)
                            chunks.add(chunk)
                            totalShorts += chunk.size
                        }
                        c.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEOS = true
                        }
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outChannels = c.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
            val result = ShortArray(totalShorts)
            var offset = 0
            for (chunk in chunks) {
                chunk.copyInto(result, offset)
                offset += chunk.size
            }
            DecodedPcm(result, outChannels)
        } catch (_: Throwable) {
            null
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            try { extractor.release() } catch (_: Throwable) {}
        }
    }

    private class WavPcm(val sampleRate: Int, val channels: Int, val bitsPerSample: Int, val data: ByteArray) {
        fun toShortArray(): ShortArray {
            val n = data.size / 2
            val out = ShortArray(n)
            for (i in 0 until n) {
                val lo = data[i * 2].toInt() and 0xFF
                val hi = data[i * 2 + 1].toInt()
                out[i] = ((hi shl 8) or lo).toShort()
            }
            return out
        }
    }

    /** Minimal canonical-WAV chunk walk - RIFF/WAVE, "fmt " for format, "data" for PCM bytes. */
    private fun parseWav(bytes: ByteArray): WavPcm? {
        if (bytes.size < 44) return null
        if (String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF") return null
        if (String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE") return null
        fun le32(o: Int) = (bytes[o].toInt() and 0xFF) or ((bytes[o + 1].toInt() and 0xFF) shl 8) or
            ((bytes[o + 2].toInt() and 0xFF) shl 16) or ((bytes[o + 3].toInt() and 0xFF) shl 24)
        fun le16(o: Int) = (bytes[o].toInt() and 0xFF) or ((bytes[o + 1].toInt() and 0xFF) shl 8)

        var pos = 12
        var sampleRate = 44100
        var channels = 1
        var bitsPerSample = 16
        var dataOffset = -1
        var dataSize = -1
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = le32(pos + 4)
            val body = pos + 8
            if (body + size > bytes.size) break
            when (id) {
                "fmt " -> {
                    channels = le16(body + 2)
                    sampleRate = le32(body + 4)
                    bitsPerSample = le16(body + 14)
                }
                "data" -> {
                    dataOffset = body
                    dataSize = size
                }
            }
            pos = body + size + (size and 1) // chunks are word-aligned
        }
        if (dataOffset < 0 || dataSize < 0 || dataOffset + dataSize > bytes.size) return null
        return WavPcm(sampleRate, channels, bitsPerSample, bytes.copyOfRange(dataOffset, dataOffset + dataSize))
    }

    private companion object {
        const val SAMPLE_RATE = 44100
        const val CHUNK_FRAMES = 882 // 20ms @ 44.1kHz
        const val CHUNK_SAMPLES = CHUNK_FRAMES * 2 // stereo interleaved
    }
}

actual fun getGameSfxOutput(): GameSfxOutput? = AndroidGameSfxOutput()
