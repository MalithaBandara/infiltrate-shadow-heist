"""Inject the candidate payload and cue metadata into the audition page template."""
import json
import os

ROOT = os.path.dirname(os.path.abspath(__file__))

# Where each cue actually fires, read off the source rather than guessed.
CUES = [
    # ---- gameplay, replacing existing clips ----
    dict(id="footstep_run", group="GAMEPLAY-REPLACE", title="Running footstep",
         fires="GameplayScene.kt:1252, 1330",
         note="Alternates A/B on <strong>STEP_PHASES 0.16 / 0.70</strong> — the two frames a foot actually "
              "contacts the ground in the walk plate."),
    dict(id="jump_takeoff", group="GAMEPLAY-REPLACE", title="Jump take-off",
         fires="GameplayScene.kt:1095",
         note="Cut from <code>jump_new.mp4</code>'s own audio. The push-off is real but sits <strong>~34 dB "
              "below the landing</strong> — which is why an earlier pass read this file as having only one "
              "transient and made the take-off reuse the landing sample. Each cut below is normalised up "
              "individually (+33 to +36 dB); the take-off/landing balance is then restored in the gain table, "
              "not baked into the file.<br><br>"
              "<strong>A</strong> is the whole wind-up and push from 0.600s. <strong>B</strong> starts hard on "
              "the sharpest isolated transient at 0.775s. <strong>C</strong> is the last burst of ground "
              "contact before the airborne decay — if the foot leaves the ground late, C is the true take-off. "
              "I can't tell which from the envelope alone, so this one is your ear."),
    dict(id="land_impact", group="GAMEPLAY-REPLACE", title="Landing impact",
         fires="GameplayScene.kt:1111 &middot; LANDING_GAIN 0.85",
         note="Also from <code>jump_new.mp4</code>, at 1.612s. The landing is genuinely <strong>two impacts</strong> "
              "— 1.625s and 1.665s — which is both feet coming down.<br><br>"
              "<strong>full</strong> keeps both plus the settle, and is the honest recording. "
              "<strong>tight</strong> is the first impact only: punchier and more game-like, but it throws away "
              "the second foot."),
    dict(id="vault_climb", group="GAMEPLAY-REPLACE", title="Vault / climb",
         fires="GameplayScene.kt:1080 &middot; CLIMB_GAIN 0.7",
         note="Plays when mounting the step crate onto the elevated platform. Scrape and effort, not a thud."),
    dict(id="crouch_stance", group="GAMEPLAY-REPLACE", title="Crouch stance change",
         fires="GameplayScene.kt:1144, 1167",
         note="Fires entering and leaving a crouch, the second at 0.6 gain. Deliberately no crouch-<em>walk</em> "
              "sound exists — crouching reports SILENT noise, and a footstep there would contradict the mechanic."),

    # ---- gameplay, new ----
    dict(id="detection_rising", group="GAMEPLAY-NEW", title="Detection rising",
         fires="GameWorld.kt:156 &middot; alertProgress",
         note="<strong>Highest-value gap in the game.</strong> The &ldquo;spotted&rdquo; banner was deliberately "
              "deleted, so once this is wired, audio is the <em>only</em> channel telling the player they're "
              "being seen. Wants tension that reads instantly at low volume."),
    dict(id="alert_spotted", group="GAMEPLAY-NEW", title="Spotted — alert full",
         fires="GameWorld.kt:158 &middot; alertProgress &ge; 1.0",
         note="The moment of being caught, before the fail card. Short, hard, unambiguous."),
    dict(id="mission_failed", group="GAMEPLAY-NEW", title="Mission failed",
         fires="world.onGameOver",
         note="Plays under the Mission Failed card. Should land as a full stop, not a cartoon fail."),
    dict(id="guard_investigating", group="GAMEPLAY-NEW", title="Guard investigating",
         fires="Guard.kt &middot; GuardState.INVESTIGATING",
         note="A guard heard something and is looking toward it. Needs to be distinct from the detection cue — "
              "this is suspicion, not discovery."),
    dict(id="beacon_ambient", group="GAMEPLAY-NEW", title="Extraction beacon",
         fires="GameWorld.exitZone proximity",
         note="A loop that grows as the player nears extraction. These are 5s beds — I'll trim and "
              "cross-fade whichever you pick into a seamless loop."),
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
    dict(id="powerup_activate", group="GAMEPLAY-NEW", title="Powerup activate",
         fires="GameplayScene.kt:737 &middot; tryActivatePowerup",
         note="Four powerups: Smoke, Cloak, Invisibility, Silence. Pick one base and I'll pitch-shift per type, "
              "or tell me you want four separate auditions."),
    dict(id="powerup_denied", group="GAMEPLAY-NEW", title="Powerup denied",
         fires="GameplayScene.kt:739 &middot; consumePowerup false",
         note="Tapping a powerup you don't own. Currently completely silent, so the tap reads as a broken button."),
    dict(id="powerup_expire", group="GAMEPLAY-NEW", title="Powerup expired",
         fires="ActivePowerups.update &middot; timer → 0",
         note="Ten seconds of cloak just ran out and the player needs to know without looking at the HUD. "
              "A downward gesture reads as loss."),
    dict(id="hud_touch", group="GAMEPLAY-NEW", title="HUD control press",
         fires="GameplayScene.kt:678 &middot; D-pad / jump / crouch",
         note="<strong>Fires more than anything else in the game.</strong> Must be near-subliminal — anything "
              "with a tone or tail will drive players insane within a minute. I'll wire it low."),
    dict(id="pause_open", group="GAMEPLAY-NEW", title="Pause opened",
         fires="GameplayScene.kt:479 &middot; pauseBtn",
         note="Opening the Tactical Pause overlay."),
    dict(id="pause_close", group="GAMEPLAY-NEW", title="Pause closed / resume",
         fires="GameplayScene.kt:804 &middot; RESUME",
         note="Should feel like the mirror of the open sound, not an unrelated noise."),
    dict(id="ad_reward", group="GAMEPLAY-NEW", title="Continue granted",
         fires="ContinueAdBridge.consumeContinueGranted",
         note="The player watched a rewarded ad and gets their run back. This is a reward, so it should feel "
              "like one — it's the moment that justifies having watched."),

    # ---- menu ----
    dict(id="ui_click", group="MENU", title="Primary button",
         fires="MainMenuScreen.kt:225–261",
         note="PLAY, MISSIONS, STORE, SETTINGS. The most-heard sound in the menus — keep it dry and short."),
    dict(id="ui_back", group="MENU", title="Back",
         fires="MenuComponents.kt:90",
         note="Should read as the reverse of the primary click — lower, or falling."),
    dict(id="ui_tab", group="MENU", title="Tab switch",
         fires="StoreScreen.kt:167 &middot; SettingsScreen.kt:128",
         note="Store POWER-UPS/COINS and Settings GENERAL/ABOUT. Lighter than a primary click."),
    dict(id="toast_success", group="MENU", title="Success toast",
         fires="showToast(&hellip;, true)",
         note="The green toast: acquired, credits transferred, language set."),
    dict(id="toast_error", group="MENU", title="Error toast",
         fires="showToast(&hellip;, false)",
         note="The red toast: INSUFFICIENT CREDITS, settings reset. Should read as refusal, not as damage."),
    dict(id="purchase_success", group="MENU", title="Purchase complete",
         fires="StoreScreen.kt:267, 282",
         note="Bigger than a success toast — real money or real coins just changed hands."),
    dict(id="locked_denied", group="MENU", title="Locked mission",
         fires="LevelSelectScreen.kt:178",
         note="Tapping a locked or Shadow-Pass-gated mission. The code currently swallows the tap in silence, "
              "so it reads as the app ignoring you."),
    dict(id="mission_start", group="MENU", title="Mission start",
         fires="onStartMission → KorGE swap",
         note="Covers the ~60–120ms root-view-controller swap into gameplay. A sound here hides the seam."),
    dict(id="slider_tick", group="MENU", title="Volume slider",
         fires="SettingsScreen.kt:386, 417",
         note="Ticks as the music/SFX sliders move. On the SFX slider this doubles as the preview — it's how "
              "you hear what you're setting. Must be very short or it'll stutter while dragging."),
    dict(id="toggle_switch", group="MENU", title="Toggle",
         fires="SettingsScreen.kt:466, 494",
         note="Control-layout swap. Wants a mechanical, two-state feel."),
]


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

    with open(os.path.join(ROOT, "template.html"), encoding="utf-8") as f:
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
