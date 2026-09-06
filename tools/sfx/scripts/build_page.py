"""Inject the candidate payload and cue metadata into the audition page template."""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.dirname(HERE)
# Bulky, regenerable data (downloaded packs, slices, generated payloads) lives here, outside the
# repo. Override with SFX_WORK to point at an existing download instead of re-fetching.
WORK = os.environ.get("SFX_WORK") or os.path.join(BASE, "work")
ROOT = WORK

# Where each cue actually fires, read off the source rather than guessed.
CUES = [
    # ---- gameplay, new ----
    dict(id="alert_spotted", group="GAMEPLAY-NEW", title="Spotted — alert full",
         fires="GameWorld.kt:158 &middot; alertProgress &ge; 1.0",
         note="The moment of being caught, before the fail card. Short, hard, unambiguous."),
    dict(id="mission_failed", group="GAMEPLAY-NEW", title="Mission failed",
         fires="world.onGameOver",
         note="Plays under the Mission Failed card. Should land as a full stop, not a cartoon fail."),
    dict(id="level_complete", group="GAMEPLAY-NEW", title="Level complete",
         fires="world.onLevelComplete",
         note="The payoff. SAX and STEEL families lean heist-caper; NES leans arcade; PIZZI is plucked strings. "
              "Worth listening to all four before deciding the game's tone."),
    dict(id="star_reveal", group="GAMEPLAY-NEW", title="Star reveal",
         fires="GameplayScene.kt:959",
         note="Plays once per earned star, up to three. I'll pitch each repeat up a step so 3 stars ascends. "
              "Pick something short and clean with headroom for pitching."),
    dict(id="coin_bounty", group="GAMEPLAY-NEW", title="Coin bounty",
         fires="profileStorage.addCoins",
         note="The heist payout on the results card, doubled with Shadow Pass. Wants to sound like a lot of money."),
]

# Cues removed from the bench entirely (owner decision, not a bench pick):
#   guard_investigating — owner supplied a clip directly (guard_investigate.wav), no audition needed.
#   powerup_activate / powerup_denied / powerup_expire — decided no sound is needed for any of these.
#   mission_start — decided no sound is needed.
#   toggle_switch — decided it just reuses the primary click, no separate sound.
#   purchase_success / locked_denied / ad_reward — decided to reuse toast_success/toast_error rather
#   than source distinct sounds; see MENU_CLIP_RELATIVE_GAIN and GameAudio.TOAST_SUCCESS_GAIN.
#   toast_success / toast_error — settled directly from owner-supplied files (success.wav/error.ogg,
#   both Freesound CC0), bypassing the bench entirely. See ATTRIBUTION.md.
#   footstep_run, hud_touch, pause_open, pause_close, ui_click, ui_back, ui_tab — settled and
#   shipped; owner picked from the bench and removed them once decided. See ATTRIBUTION.md's
#   "In use" table for the file each one settled on.
#   jump_takeoff, land_impact, vault_climb, crouch_stance — the whole GAMEPLAY-REPLACE group,
#   settled: land_impact/vault_climb ship as impact.wav/climb.wav (see ATTRIBUTION.md), crouch_stance
#   stays deliberately silent (see GameAudio.kt's doc comment), and jump_takeoff stays silent too
#   (see ATTRIBUTION.md's "Notes on the plate cuts" — the only cut available was defective noise).
#   detection_rising, beacon_ambient — removed from the bench per owner instruction; still open
#   cues, not resolved as "no sound needed" the way the others above are. If either is picked up
#   again, re-add its cue brief here before re-running the pipeline.
#   slider_tick — decided it just reuses the primary click on release (onValueChangeFinished), no
#   separate tick sound; see VolumeSlider in MenuComponents.kt.


def main():
    with open(os.path.join(ROOT, "candidates.json"), encoding="utf-8") as f:
        data = json.load(f)

    # The page only needs what it renders; drop local filesystem paths.
    slim = [{k: d[k] for k in ("cue", "group", "pack", "file", "dur", "b64")} for d in data]

    have = {d["cue"] for d in slim}
    listed = {c["id"] for c in CUES}
    if have - listed:
        print("WARNING candidates with no cue entry:", have - listed)
    if listed - have:
        print("WARNING cues with no candidates:", listed - have)

    with open(os.path.join(HERE, "bench-template.html"), encoding="utf-8") as f:
        html = f.read()

    html = html.replace("/*__DATA__*/", json.dumps(slim, separators=(",", ":")))
    html = html.replace("/*__CUES__*/", json.dumps(CUES, separators=(",", ":")))

    out = os.path.join(ROOT, "foley-bench.html")
    with open(out, "w", encoding="utf-8") as f:
        f.write(html)

    print("cues: %d, candidates: %d" % (len(CUES), len(slim)))
    print("wrote %s (%.2f MB)" % (out, os.path.getsize(out) / 1048576))


if __name__ == "__main__":
    main()
