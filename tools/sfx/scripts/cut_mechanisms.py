"""Cut the lift / switch / lever / hanging-crate chain / mission fail + success / powerup sounds from the Pixabay downloads in Downloads/charAnimations/music3.

  loop_elevator.wav  22.05kHz mono, equal-power-crossfaded loop of the two steady running stretches
                     of the 35s source that have mid-range body (0.9-3.1s and 4.4-7.1s). The 10-25s
                     stretch is almost all sub-300Hz rumble, which a phone speaker does not play, so
                     it was inaudible; the ding / dips at 3.2-4.1s, 7.2-10s, 25-28.5s are left out.
                     High-passed at 70Hz, loud (-14dB RMS) so ELEVATOR_LOOP_GAIN lands it level with
                     the fan / steam beds.
  switch_press.wav   44.1kHz mono one-shot, the click at 0.08-0.40s of the light-switch source,
                     soft-clipped up so its body (not just the peak) carries.
  lever_throw.wav    44.1kHz mono one-shot, both clacks (0.05-0.62s) of the breaker-switch source,
                     soft-clipped up the same way.
  loop_chain.wav     22.05kHz mono, ~10.2s multi-variation normalized loop of heavy chain dragging
                     (11 varied phrases with subtle pitch/tension shifts, dynamic leveling and soft
                     peak limiting to prevent repetitive harsh clanks, equal-power seamless loop).
                     Plays while a hanging crate's rig is travelling.
  mission_fail.wav   44.1kHz mono one-shot, 0-2.0s of the cartoon-defeat stinger (its tail faded out).
  mission_success.wav  44.1kHz mono one-shot, 0-1.3s of the level-up chime (tail faded out).
  powerup_activate.wav 44.1kHz mono one-shot, 0.2-1.2s of the interface blip; the source is quiet, so it
                     is peak-normalised here and POWERUP_ACTIVATE_GAIN keeps it low.
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

BIN = (r"C:\Users\USER\AppData\Local\Microsoft\WinGet\Packages"
       r"\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe"
       r"\ffmpeg-9.0.1-full_build\bin")
FFMPEG = os.path.join(BIN, "ffmpeg.exe")
SRC = sys.argv[1] if len(sys.argv) > 1 else r"C:\Users\USER\Downloads\charAnimations\music3"
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "resources", "sfx")


def read(name, rate):
    with tempfile.TemporaryDirectory() as d:
        tmp = os.path.join(d, "x.wav")
        subprocess.run([FFMPEG, "-v", "error", "-y", "-i", os.path.join(SRC, name),
                        "-ac", "1", "-ar", str(rate), tmp], check=True)
        with wave.open(tmp) as w:
            return np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float64) / 32768.0


def write(name, x, rate):
    x = np.clip(x, -1.0, 1.0)
    with wave.open(os.path.join(OUT, name), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes((x * 32767.0).astype("<i2").tobytes())


def one_shot(x, rate, start, end, peak, fade_in=0.004, fade_out=0.03):
    seg = x[int(start * rate):int(end * rate)].copy()
    fi, fo = int(fade_in * rate), int(fade_out * rate)
    seg[:fi] *= np.linspace(0.0, 1.0, fi)
    seg[-fo:] *= np.linspace(1.0, 0.0, fo)
    return seg * (peak / np.abs(seg).max())


def highpass(x, rate, hz):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1.0 / rate)
    spec *= np.clip((f - hz * 0.5) / (hz * 0.5), 0.0, 1.0)
    return np.fft.irfft(spec, len(x))


def crossfade_join(a, b, c):
    t = np.linspace(0.0, 1.0, c)
    return np.concatenate([a[:-c], a[-c:] * np.cos(t * np.pi / 2) + b[:c] * np.sin(t * np.pi / 2), b[c:]])


def loop(x, rate, spans, xfade, rms_db, hp_hz):
    segs = [highpass(x[int(a * rate):int(b * rate)] - x[int(a * rate):int(b * rate)].mean(), rate, hp_hz) for a, b in spans]
    c = int(xfade * rate)
    seg = segs[0]
    for nxt in segs[1:]:
        seg = crossfade_join(seg, nxt, c)
    n = len(seg)
    t = np.linspace(0.0, 1.0, c)
    out = seg[:n - c].copy()
    out[:c] = seg[:c] * np.sin(t * np.pi / 2) + seg[n - c:] * np.cos(t * np.pi / 2)
    out *= 10 ** (rms_db / 20) / np.sqrt((out ** 2).mean())
    return out


def punch(x, drive, peak):
    y = np.tanh(x * drive)
    return y * (peak / np.abs(y).max())


def resample(x, pitch):
    new_len = int(len(x) / pitch)
    return np.interp(np.linspace(0, len(x) - 1, new_len), np.arange(len(x)), x)


def cut_chain_loop(src_audio, rate):
    filt = highpass(src_audio - src_audio.mean(), rate, 70.0)

    def fslice(a, b):
        return filt[int(a * rate):int(b * rate)].copy()

    # Build 11 varied phrases from the source recording to form a rich, non-repetitive loop:
    # 1. Standard steady chain drag (1.00 - 2.20s)
    p1 = fslice(1.00, 2.20)
    # 2. Fine link sliding & trailing clatter (2.40 - 3.70s), leveled
    p2 = fslice(2.40, 3.70) * 1.8
    # 3. Heavy tension pull, pitched down 0.88x (1.60 - 2.80s)
    p3 = resample(fslice(1.60, 2.80), 0.88)
    # 4. Light chain running over sprocket / pulley, pitched up 1.12x (0.95 - 2.20s)
    p4 = resample(fslice(0.95, 2.20), 1.12)
    # 5. Layered multi-chain: deep drag + fine link jingles
    l5_a, l5_b = fslice(1.30, 2.60), fslice(2.50, 3.80) * 1.6
    min_5 = min(len(l5_a), len(l5_b))
    p5 = l5_a[:min_5] + l5_b[:min_5] * 0.7
    # 6. Deep rolling chain links, pitched down 0.93x (2.10 - 3.50s)
    p6 = resample(fslice(2.10, 3.50), 0.93) * 1.5
    # 7. Resonant drag B (1.20 - 2.50s)
    p7 = fslice(1.20, 2.50)
    # 8. High-frequency link chatter with soft slide underlay (pitched up 1.06x)
    l8_a, l8_b = resample(fslice(2.40, 3.70), 1.06) * 1.7, fslice(1.50, 2.70) * 0.5
    min_8 = min(len(l8_a), len(l8_b))
    p8 = l8_a[:min_8] + l8_b[:min_8]
    # 9. Smooth slow drag, pitched down 0.95x (1.00 - 2.30s)
    p9 = resample(fslice(1.00, 2.30), 0.95)
    # 10. Subtle link settling & surface friction (2.30 - 3.60s)
    p10 = fslice(2.30, 3.60) * 1.7
    # 11. Steady transition back toward loop start (1.80 - 3.00s at 1.04x)
    p11 = resample(fslice(1.80, 3.00), 1.04) * 1.2

    phrases = [p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11]
    c = int(0.35 * rate)
    chain_seq = phrases[0]
    for p in phrases[1:]:
        chain_seq = crossfade_join(chain_seq, p, c)

    # Seamless equal-power loop crossfade
    loop_c = int(0.40 * rate)
    n = len(chain_seq)
    t = np.linspace(0.0, 1.0, loop_c)
    out = chain_seq[:n - loop_c].copy()
    out[:loop_c] = chain_seq[:loop_c] * np.sin(t * np.pi / 2) + chain_seq[n - loop_c:] * np.cos(t * np.pi / 2)

    # Dynamic leveling / normalization: moving RMS window to tame dips and peaks
    win = int(rate * 0.25)
    sq = out ** 2
    kern = np.ones(win) / win
    smooth_ms = np.convolve(sq, kern, mode="same")
    smooth_rms = np.sqrt(np.maximum(smooth_ms, 1e-6))
    med_rms = np.median(smooth_rms)
    gain_curve = np.clip(med_rms / np.maximum(smooth_rms, med_rms * 0.45), 0.4, 1.8)
    win_g = int(rate * 0.2)
    smooth_gain = np.convolve(gain_curve, np.ones(win_g) / win_g, mode="same")
    out_leveled = out * smooth_gain

    # Soft limiter above threshold to eliminate harsh clank spikes
    thresh = 0.32
    peak_limit = 0.48
    over = np.abs(out_leveled) > thresh
    out_leveled[over] = np.sign(out_leveled[over]) * (
        thresh + (peak_limit - thresh) * np.tanh((np.abs(out_leveled[over]) - thresh) / (peak_limit - thresh))
    )

    # Final RMS normalization to -19.0 dBFS
    target_rms_db = -19.0
    cur_rms = np.sqrt((out_leveled ** 2).mean())
    out_final = out_leveled * (10 ** (target_rms_db / 20) / cur_rms)
    return np.clip(out_final, -0.95, 0.95)


write("loop_elevator.wav", loop(read("freesound_community-elevator-29654.mp3", 22050), 22050,
                                [(0.9, 3.1), (4.4, 7.1)], 0.35, -14.0, 70.0), 22050)
write("switch_press.wav", punch(one_shot(read("freesound_community-light-switch-81967.mp3", 44100), 44100, 0.08, 0.40, 1.0), 3.0, 0.95), 44100)
write("lever_throw.wav", punch(one_shot(read("freesound_community-breaker-switch-45684.mp3", 44100), 44100, 0.05, 0.62, 1.0), 3.0, 0.95), 44100)

write("loop_chain.wav", cut_chain_loop(read("dragon-studio-heavy-chains-dragging-515264.mp3", 22050), 22050), 22050)
write("mission_fail.wav", one_shot(read("coghezzi-game-over-orchestral-stinger-cartoon-defeat-546515.mp3", 44100), 44100, 0.0, 2.0, 0.9, fade_out=0.35), 44100)
write("mission_success.wav", one_shot(read("universfield-level-up-191997.mp3", 44100), 44100, 0.0, 1.3, 0.9, fade_out=0.3), 44100)
write("powerup_activate.wav", one_shot(read("universfield-interface-124464.mp3", 44100), 44100, 0.2, 1.2, 0.9, fade_out=0.25), 44100)
