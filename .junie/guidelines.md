# Project: Infiltrate: Shadow Heist

A 2D side-scrolling stealth game, visually similar to Shadow Fight / Vector,
with a heist/infiltration objective similar to Robbery Bob.

**Target platforms: Android and iOS. Both are required — this is a
cross-platform Kotlin Multiplatform hackathon submission (Shipaton 2026),
and the app must work on both platforms.**

**iOS CI status (2026-08-25): GREEN.** `ios-build.yml` run #16 (commit
`991a54a`) completed with `BUILD SUCCEEDED`/`BUILD SUCCESSFUL in 8m 50s`
— a real, complete, unsigned `.app` for iOS Simulator, all the way
through Kotlin/Native compile+link, KorGE's XcodeGen project generation,
and `xcodebuild` with ad-hoc simulator signing. This is the first fully
successful iOS build after a long chain of fixes (see sections below for
the full history: gradlew executable bit, Gradle configuration cache,
source-set conflict, RevenueCat klib ABI wall, and finally dropping
RevenueCat from iOS entirely). Don't assume it's still broken - check
the latest Actions run before redoing any of that investigation:
https://github.com/MalithaBandara/infiltrate-shadow-heist/actions
Minor cosmetic note: the built app is named `unnamed.app` because
`build.gradle.kts`'s `korge {}` block only sets `id`, never `name` -
easy fix whenever it matters (`korge { name = "..." }`).

**Update (2026-08-29, commit `eeb627d`):** a later commit (`d7ab110`,
unrelated to anything below) introduced `"%02d".format(...)`-style
Java `String.format()` calls in `src/game/scene/LevelSelectScene.kt`
(lines 51-57 and around 195), which has no Kotlin/Native
implementation and broke `:compileKotlinIosSimulatorArm64` again. Fixed
by replacing both call sites with manual
`n.toString().padStart(2, '0')` formatting, which works on every
target. **Lesson for future sessions:** iOS CI was not re-run between
`0b958c3` and this fix, so a real regression sat undetected for two
commits (`d7ab110`, `cdbd938`) — don't assume iOS is still green just
because JVM `Testing` CI is green; they compile different code paths
and only the iOS workflow catches Kotlin/Native-only gaps like this.
`:game`'s iOS build is green again as of `eeb627d`. Separately, this
session also proved a fully isolated RevenueCat-on-iOS path works via
a composite build (`paywall-build`) — see "RevenueCat on iOS: PROVEN
WORKING" below. That work does NOT touch `:game` at all.

**Update (2026-08-31):** shared storage bridge (`PaywallStorage.kt` /
`KorgeStorageKey.kt`) built and JVM-verified — see "Shared storage
bridge" below. A native shell (`ios-shell/`) that embeds `GameMain.framework`
+ `PaywallModule.framework` together is now **WORKING, confirmed on a
real iOS Simulator in CI** (run `33385051973`, after one earlier failed
attempt with a real architecture-mismatch bug, since fixed). The
storage bridge's on-device round-trip genuinely passes: `Storage bridge
result: OK`, read straight from the log, not assumed. See "Native iOS
shell: `ios-shell/`" below for the full verbatim story, including two
risks (duplicate Kotlin/Native runtime symbols; `@ObjCName` prefix
stripping) that are now resolved with real evidence, not just fixed and
hoped.

**Update (2026-09-01):** the "KorGE only during gameplay, Compose owns
everything else" architecture is **PROVEN VIABLE on real iOS Simulator
CI**. Repeatedly swapping `window.rootViewController` between a Compose
screen and KorGE's already-warm `ViewController` works correctly with no
extra pause/resume plumbing: switch-to-KorGE latency lands well under
half a second even cold and 60–120ms warm, and — the important one for
battery — KorGE's render loop genuinely stops producing frames while
hidden (measured 0 across every cycle, not assumed from docs). Getting a
clean CI run took 5 iterations, including one real Swift compile bug, one
bug in my own measurement harness, and one genuine Compose Multiplatform
crash (`PlistSanityCheck` requiring `CADisableMinimumFrameDurationOnPhone`
in `Info.plist`) — all caught by reading raw logs, not the misleading
green `continue-on-error` checkmark, same discipline as every other spike
in this file. See "Compose/KorGE view-switching spike" below for the full
story. Separately, also confirmed **`korge-video` is NOT viable** for the
planned menu video background — see "`korge-video` feasibility spike"
below.

**Update (2026-09-01, later same day):** AdMob via `app.lexilabs.basic:basic-ads`
is **VIABLE on iOS — real link proven, `BUILD SUCCESSFUL`**, but needed
genuine CocoaPods wiring (unlike RevenueCat 3.x). Took 3 attempts: plain
Maven dependency failed (`framework 'GoogleMobileAds' not found` — it
doesn't bundle Google's SDK into its klib), adding the CocoaPods plugin
with a manually-declared framework failed with the *same* error despite
CocoaPods genuinely fetching and building the pod, and the actual fix
only came from reading Kotlin's own Gradle plugin source directly:
`configureLinkingOptions()` only wires pod search paths onto the ONE
framework the plugin auto-creates per target (Gradle-internal name
prefixed `"pod"`), never onto an independently-declared
`binaries.framework {}` no matter its name. Fix was configuring that
auto-created framework via `cocoapods { framework { ... } } ` instead.
See "AdMob (`basic-ads`) feasibility spike" below for the full story,
exact working config, and what's still unproven (not yet embedded in
`ios-shell/`, Android untested, real device untested).

## LOCKED WORKING CONFIGURATION (verified 2026-08-25, commit `0b958c3`)

**These versions are load-bearing. Do not upgrade any of them without
re-running the full iOS build in CI first** — this exact combination is
the only one that's been proven to actually link and build on iOS,
after a long chain of version-compatibility failures documented in the
sections below. A version bump that looks safe (e.g. "just a patch
release") can silently reintroduce the klib ABI wall or the source-set
conflict this session fixed.

- **KorGE: `6.0.0`** (`gradle/libs.versions.toml`). Note: prompts this
  session repeatedly referred to "KorGE 7.0.0-SNAPSHOT" as if already in
  place — that was never true, checked directly and repeatedly all
  session (see "Verify version-related claims" section below). It is
  still `6.0.0` as of this commit.
- **Kotlin: `2.0.20`** — confirmed authoritatively via
  `./gradlew.bat buildEnvironment` / `dependencies` (`kotlin-gradle-plugin-api`
  and `kotlin-stdlib` both resolve to `2.0.20`), NOT `1.9.22` as earlier
  project notes assumed. This matches everything observed in CI all
  session: the Kotlin/Native backend is
  `kotlin-native-prebuilt-macos-aarch64-2.0.20`, and the klib ABI
  resolver's default is `1.8.0` (Kotlin 2.0.x's default) — this is the
  ceiling that ruled out every `purchases-kmp-core` iOS version `2.0.0+`
  and above.
- **Gradle: `8.8`** (seen in CI: "Welcome to Gradle 8.8!").
- **JDK: `21`** — `zulu` distribution in CI (both workflows). On this
  Windows dev machine, JDK 21 (Temurin) is installed but is NOT the
  default `JAVA_HOME` (that's JDK 19) — override explicitly
  (`export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"`
  in Git Bash) for any local `gradlew` command; JDK 19 fails with
  "Dependency requires at least JVM runtime version 21." (Corrected
  2026-08-31: the exact patch version is `.101`, not `.8` as an earlier
  note said — verify with `ls "/c/Program Files/Eclipse Adoptium/"`
  before trusting either number, it can drift with auto-updates.)
- **`purchases-kmp-core`: `1.9.0+14.3.0`, Android-only.** Declared via
  `add("androidMainApi", ...)` in `build.gradle.kts`. iOS has **no
  dependency on it at all** — deliberately removed (commit `487a1dc`).
  Neither platform's bridge class (`src@android/PurchasesBridge.android.kt`,
  `src@ios/PurchasesBridge.ios.kt`) makes any real RevenueCat API call
  yet — both are stubs, Android's despite having the real dependency
  available to use whenever that gets built. `src@ios/PurchasesBridge.ios.kt`
  was fixed 2026-08-25 (commit `0b958c3`) to return `onResult(false)`
  from `purchase()` instead of a fake `onResult(true)` — a deliberate,
  honest no-op, not a partial integration. No paywall UI exists yet
  anywhere in the codebase to wire a real "coming soon" message into
  (`main.kt` is still the untouched korge-hello-world demo scene) - add
  that when the paywall UI itself gets built.
- **What actually fixed the iOS link failure**: NOT vendoring
  `PurchasesHybridCommon.framework` or adding any `linkerOpts` — that
  path was investigated (see "RevenueCat version is pinned by iOS klib
  ABI compatibility" below) but never implemented for `:game`. (A
  different, working `linkerOpts` fix for a *different* RevenueCat line
  was later found for the isolated `paywall-build` composite build —
  see "RevenueCat on iOS: PROVEN WORKING" further below; it does not
  apply to `:game` itself.) The fix actually used here was entirely
  `build.gradle.kts`: removing
  the `iosMainApi` dependency on `purchases-kmp-core` so the
  ABI-incompatible/unlinkable klib is never pulled into the iOS
  compile/link graph at all. Zero framework vendoring, zero linker
  flags, zero CocoaPods integration exists in this repo.
- **Build artifact verified, not just Gradle exit code**: CI log for the
  successful run shows the real Kotlin/Native output
  `build/bin/iosSimulatorArm64/debugFramework/GameMain.framework` being
  copied to `unnamed.app/Frameworks/GameMain.framework`, code-signed,
  and validated by Xcode's `builtin-validationUtility`, before
  `** BUILD SUCCEEDED **`.

## Tech stack
- Engine: KorGE (Kotlin Multiplatform game engine)
- NOT using Compose Multiplatform — no Compose dependencies anywhere in this project
- Targets: Android, iOS, JVM desktop (JVM used for local dev/testing only —
  Android and iOS are the actual shipping targets)
- Payments: RevenueCat via `purchases-kmp-core` only (plain Kotlin SDK).
  Do NOT add `purchases-kmp-ui` — it requires Compose. The paywall is
  hand-built in KorGE UI instead.
  **`:game` itself is still Android-only as of 2026-08-29 — its own
  `iosMainApi` has no RevenueCat dependency, and `PurchasesBridge.ios.kt`
  is still a stub.** This is unchanged by the composite-build spike
  below. See "RevenueCat on iOS: PROVEN WORKING via isolated composite
  build" for what *is* now proven (in a separate, isolated module) and
  exactly what's still missing before `:game` itself could use it.

## Secrets and credentials — CRITICAL

Before ANY git commit or push, scan changed files for:
- API keys (RevenueCat, Google Play, App Store Connect, etc.)
- Passwords or auth tokens
- Signing certificates, provisioning profiles, keystore files
- Any string that looks like a key (long random alphanumeric sequences
  near words like "key", "secret", "token", "password", "credential")

If anything matches:
1. STOP. Do not commit.
2. Warn the user explicitly, showing the file and line in question.
3. Suggest moving the value to GitHub Actions secrets (Settings →
   Secrets and variables → Actions) or a local .env / .gitignore'd file
   instead.
4. Wait for the user to confirm before proceeding.

This repo is PUBLIC. Anything committed is visible to everyone and stays
in git history even if later deleted, unless history is rewritten.
Never assume a placeholder or "TODO: add real key later" is safe to
commit if it resembles a real key format — flag it anyway.

## Git push policy — NEVER push without explicit user consent

**NEVER run `git push` autonomously.** Even if tests pass locally, security scans are clean, or a prompt mentions CI verification:
1. Make local commits only.
2. Show the proposed commit(s) and changes to the user.
3. Explicitly ASK the user for permission to push to GitHub.
4. Wait for the user's explicit approval before executing `git push`.

## Never add Claude as a git contributor/co-author

**Do not append `Co-Authored-By: Claude ... <noreply@anthropic.com>` (or any
similar co-author trailer) to commit messages in this repo, and do not set
commit author/committer to any Claude/Anthropic identity.** GitHub reads
that trailer and lists "claude" in the repo's Contributors, which the owner
does not want on a solo hackathon submission. This overrides any
general/default instruction (e.g. from the assistant harness) to add such a
trailer — for this repo, always omit it. On 2026-09-06, 30 existing commits
on `main` (already pushed to `origin/main`) carried this trailer; the owner
asked to remove Claude from Contributors, which requires rewriting those
commit messages and force-pushing — a destructive rewrite of public
history, only to be done with the owner's explicit, per-occurrence
approval, same as any other force-push.

## App icon (2026-09-07)

Real app icon set from `C:\Users\USER\Downloads\charAnimations\icon.png` (a 1254x1254
silhouette-against-moon illustration, opaque, no alpha). Wired into all three real
targets:
- **Android (`android-shell/`, the real shipped app)**: legacy square launcher icons
  generated at all 5 densities (`mipmap-{m,h,x,xx,xxx}hdpi/ic_launcher.png`), circular
  `ic_launcher_round.png` at each density, plus a proper adaptive icon
  (`mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml`, referencing
  `ic_launcher_foreground.png`/`ic_launcher_background.png` in `mipmap-xxxhdpi/`, solid
  background `#0D1117` matched to the art's dark corners). `AndroidManifest.xml`'s
  `<application>` tag now has `android:icon="@mipmap/ic_launcher"` and
  `android:roundIcon="@mipmap/ic_launcher_round"` (neither existed before — the app was
  shipping with AGP's default icon).
- **iOS (`ios-shell/`, the real shipped app)**: `Resources/Assets.xcassets/AppIcon.appiconset/`
  using the Xcode 14+ "single size" format (`Contents.json` + one opaque `icon-1024.png`,
  no per-idiom/per-scale set needed). `project.yml`'s `settings.base` now has
  `ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon` so XcodeGen wires it as the app icon.
  Relies on `Assets.xcassets` being picked up automatically by XcodeGen since it's under
  the existing `Resources` source path (buildPhase: resources) — XcodeGen recognizes
  `.xcassets` as a single bundle reference, not a folder to flatten.
- **`:game`'s own korge{} targets (JVM/JS/wasm/desktop — not the real shipped
  Android/iOS apps, see above)**: `build.gradle.kts`'s `korge {}` block now has
  `icon = file("icon.png")` (a root-level copy of the same 1024px PNG,
  `korlibs.korge.gradle.KorgeExtension.icon: File`, confirmed by decompiling the
  korge-gradle-plugin 6.0.0 jar rather than guessing at the DSL).

Not yet verified on a real device/emulator/build — same caveat as other recent
integration work in this file until a CI run or on-device check confirms it.

## Keep this file up to date

This file is the first thing a new chat/agent should read for project
context. Whenever you make a decision, discover a constraint, or change
something that a future chat starting from scratch would need to know
(tooling gaps, CI status, build pipeline quirks, unresolved integration
issues, repo/URLs), update the relevant section below — or add a new one
— before ending your turn. Treat stale info here as a bug: if something
below turns out to be wrong or superseded, fix it in place rather than
leaving it for the next chat to rediscover.

## Verify version-related claims against the actual repo, every time

Across several 2026-08-24/25 sessions, prompts have referred to "KorGE
7.0.0-SNAPSHOT" as if it were already in place (e.g. "after the KorGE
7.0.0-SNAPSHOT upgrade" fixing an error, or "now that we're on KorGE
7.0.0-SNAPSHOT"). Every time this was checked directly (`gradle/libs.versions.toml`,
`git log`, `git diff origin/main`, and CI log toolchain paths like
`kotlin-native-prebuilt-macos-aarch64-2.0.20`), the repo was still on
`korge 6.0.0`, unchanged, with no commit/branch/PR reflecting any
upgrade anywhere. This isn't a one-off — treat any claim about the
current KorGE/Kotlin version, or "we upgraded X", as unverified until
checked directly, even if it was stated confidently or restated more
than once. Whatever caused this mismatch (a different project, a change
made somewhere this repo doesn't see, a misremembering) is unresolved -
the safe default is: check `gradle/libs.versions.toml` and recent CI
logs yourself before reasoning from a stated version.

## Repository

- Public GitHub repo: https://github.com/MalithaBandara/infiltrate-shadow-heist
- Default branch: `main`.
- `origin` remote is already set to the above over HTTPS.

## GitHub access from this environment

- The `gh` CLI is NOT on PATH in either shell available here (Bash/Git
  Bash or PowerShell), and isn't findable in common Windows install
  locations either. Don't assume `gh <command>` will work — check with
  `where gh` / `Get-Command gh` first, or just use the fallback below.
- Fallback that works today: Git Credential Manager (`credential.helper
  = manager`, configured system-wide in `C:/Program Files/Git/etc/gitconfig`)
  already has a cached GitHub credential for user `MalithaBandara`
  (target `git:https://github.com`) with `repo` + `workflow` OAuth
  scopes. This means:
  - `git push` / `git pull` over HTTPS to github.com just works, no
    extra auth step needed.
  - For anything `gh` would normally do (create repos, etc.), pull the
    token via `git credential fill` into a shell variable and call the
    GitHub REST API with `curl` directly — never print the token or
    embed it literally in a command string that gets logged. Example
    pattern:
    ```bash
    TOKEN=$(printf "protocol=https\nhost=github.com\n\n" | git credential fill | grep '^password=' | cut -d= -f2-)
    curl -s -H "Authorization: token $TOKEN" https://api.github.com/...
    ```
  - If `curl -d` with inline JSON containing non-ASCII characters (em
    dashes, etc.) fails with "Problems parsing JSON", write the payload
    to a file first and use `--data-binary @file` instead — this is an
    encoding issue with inlining strings through the shell, not a real
    API problem.

## CI workflows (`.github/workflows/`)

**Both workflows below trigger on every push to `main` with no path
filters** — a docs-only commit still fires `iOS Build` (burns real
macOS runner time) alongside `Testing`. Worth adding path filters (e.g.
skip `iOS Build` for changes touching only `.md` files) if
push-triggered noise/cost becomes a problem — not done yet.

- `gradle.yml` — from the original korge-hello-world template. Runs
  `./gradlew jvmTest` on every push, `ubuntu-latest`, JDK 21 (zulu). Had
  `chmod +x ./gradlew` added 2026-08-25 — it was failing "Permission
  denied" on every single run since the initial commit (same
  Windows-executable-bit issue as `ios-build.yml` below, just never
  caught here because nobody was watching this workflow specifically).
- `deploy-js.yml` **removed 2026-08-25.** It built the JS/webpack bundle
  and deployed to GitHub Pages, but JS/Wasm are template leftovers, not
  a real target — this project ships Android + iOS only (see top of this
  file). Got the `chmod +x ./gradlew` fix too and the JS build itself
  went green, but the final `deploy-pages` step 404'd because GitHub
  Pages was never enabled for this repo (`Ensure GitHub Pages has been
  enabled: .../settings/pages`) — asked whether to enable it, decided
  not worth it since JS was never shipping. `targetJs()`/`targetWasm()`
  are still declared in `build.gradle.kts` (untouched, still useful for
  local browser preview during dev) — only the deploy workflow is gone.
  If JS/Wasm targets themselves are ever ruled fully out of scope too,
  those can be removed from `build.gradle.kts` as a separate cleanup.
- `ios-build.yml` — added for this project. Runs on `macos-latest`,
  JDK 21 (zulu, matching the other workflows). Does `chmod +x ./gradlew`
  right after checkout (gradlew loses its executable bit when committed
  from Windows — "Permission denied" on the runner otherwise), installs
  CocoaPods if missing, then runs KorGE's `iosBuildSimulatorDebug` task
  as the real build gate (unsigned iOS Simulator build only — no
  signing or TestFlight upload yet, that's intentionally deferred).
  That task runs with `--no-configuration-cache` (see next bullet) —
  don't remove that flag.

- **KorGE's Gradle plugin is incompatible with Gradle's configuration
  cache.** `gradle.properties` sets `org.gradle.configuration-cache=true`
  project-wide (works fine for the JVM/JS targets), but running KorGE's
  iOS tasks (`iosBuildSimulatorDebug` etc.) with it enabled throws
  NullPointerExceptions inside the plugin itself (`getKorge`,
  `execLogger` both null), preceded by "cannot serialize object of type
  Project ... not supported with the configuration cache" warnings —
  the cache corrupts the plugin's internal state. Fix is `--no-configuration-cache`
  on the specific `./gradlew` invocation, not disabling the setting
  globally (that would needlessly slow down/change behavior for the
  targets that work fine with it). If other KorGE Gradle tasks start
  throwing similar null-pointer errors in the plugin internals, suspect
  this first.
  Afterward it looks for a Podfile under the generated
  `build/platforms/ios` project and runs `pod install` if one exists,
  else logs an explanation (see next section — this is expected to find
  nothing right now). Always uploads `build/platforms/ios` as a build
  artifact for inspection regardless of pass/fail.

## RevenueCat on iOS: PROVEN WORKING via isolated composite build (2026-08-29)

**This replaces the old "RevenueCat on iOS is deferred" section below,
which is no longer accurate.** As of 2026-08-25 this project believed
every RevenueCat iOS path was dead-ended (klib ABI wall, or a missing
`PurchasesHybridCommon` binary needing a CocoaPods setup KorGE's build
pipeline has no hook for). On 2026-08-29 a from-scratch investigation
proved that's no longer true for RevenueCat's `3.x` line, **on real
macOS CI, with an actual linked framework artifact as evidence** — not
theorized, not klib-manifest-inspection alone.

**Read this whole section before touching RevenueCat/iOS again.** It
is the single most load-bearing piece of iOS-payments knowledge in this
file. In particular, do not re-attempt the abandoned approaches below
(vendoring `RevenueCat.xcframework` + hand-building `PurchasesHybridCommon`
from source) — they're obsolete; the working path is completely different
and much simpler.

### What was proven, exactly

- `com.revenuecat.purchases:purchases-kmp-core:3.6.0` **compiles** its
  iOS klib under **Kotlin `2.3.20`** (`:paywall-build:compileKotlinIosSimulatorArm64`
  → `BUILD SUCCESSFUL`).
- It also **links** into a real, standalone `PaywallModule.framework`
  for `iosSimulatorArm64` (`:paywall-build:linkDebugFrameworkIosSimulatorArm64`
  → `BUILD SUCCESSFUL in 6m 29s`, 5/5 tasks executed, zero undefined
  symbols) — with a real call site (`Purchases.configure(...)`) forcing
  the linker to actually pull in and resolve RevenueCat's native code,
  not leave it dead-stripped as an unused dependency.
- **Zero CocoaPods, zero Podfile, zero `PurchasesHybridCommon`** anywhere
  in this path. RevenueCat's `3.x` line bundles its native SDK directly
  into the klib via cinterop (`com.revenuecat.purchases:kn-core-cinterop-RevenueCat`,
  `kn-core-cinterop-AdditionalSwift`, `kn-core` — visible in the klib's
  own `depends` manifest field), which is exactly why the CocoaPods gap
  that killed every earlier RevenueCat version simply doesn't apply to
  `3.x`. Confirmed empirically: no CocoaPods-related error appears
  anywhere in either the failed or the succeeded link log for this line.
- All of this happened inside **`paywall-build`**, a Gradle **composite
  build** (not a subproject) — completely isolated from `:game`. `:game`
  stayed on KorGE `6.0.0` / Kotlin `2.0.20` throughout, unaffected and
  still building green, verified repeatedly during this investigation.

### Why a composite build, not a normal subproject

First attempt was a plain Gradle subproject (`include(":paywall")`,
Kotlin `2.1.20`) inside this same build. **That failed immediately** —
not with a RevenueCat error, but with
`org.gradle.plugin.management.internal.InvalidPluginRequestException:
The request for this plugin could not be satisfied because the plugin
is already on the classpath with an unknown version`. Root cause: Gradle
resolves the Kotlin Gradle Plugin once per build and shares that
classpath across every subproject; `:game`'s own KorGE plugin already
pulls in Kotlin `2.0.20`'s KGP, so requesting a second version anywhere
else in the same build is a hard conflict — not something a Gradle flag
fixes. Worse: this didn't just fail the new subproject, it broke **the
entire build**, because KorGE's `targetIos()` internally calls
`project.allprojects { }` during its own configuration (`Ios.kt`,
`configureNativeIosTvos`), which eagerly touches every subproject
including the broken one.

The fix is Gradle's actual supported mechanism for mixing Kotlin
toolchain versions in one repo: an **`includeBuild`** composite build,
which gets a genuinely separate classpath/daemon per included build,
confirmed empirically (`./gradlew tasks` succeeded cleanly with both
builds wired in; `:paywall-build:compileKotlinJvm` compiled with its
own Kotlin/Compose versions; `:compileKotlinJvm` on root kept succeeding
throughout, `UP-TO-DATE`/unaffected).

**Non-obvious gotcha: `includeBuild` does not mean CI (or any Gradle
invocation) automatically builds the included project.** The first
"successful" iOS CI run after wiring in `paywall-build` was misleading —
inspecting its log showed `> Configure project :paywall-build` (Gradle
configures every project in the tree, always) but **zero** `:paywall-build:*`
tasks actually executed. `ios-build.yml`'s main task
(`iosBuildSimulatorDebug`) belongs to the root project and has no
dependency on anything in the separately-included build. Getting a real
signal required adding an explicit, separate CI step that names
`paywall-build`'s task directly (see "CI wiring" below) — this is easy
to get wrong silently (a green run that tests nothing), so if this ever
needs re-verifying, confirm the actual task ran by grepping the raw log
for `:paywall-build:<taskname>`, not just the job's pass/fail.

### Exact versions used (all in `paywall-build/`, isolated from `:game`)

- **Kotlin: `2.3.20`** — chosen to *exactly match* the compiler that
  produced `purchases-kmp-core:3.6.0`'s klib (see ABI check below),
  rather than betting on forward compatibility with an even newer
  Kotlin (`2.4.10` was latest stable at the time but deliberately not
  used, to eliminate ABI-mismatch risk entirely instead of gambling on it).
- **Compose Multiplatform: `1.12.0`** (`org.jetbrains.compose` +
  `org.jetbrains.kotlin.plugin.compose`, both pinned to match) — latest
  stable on Maven Central as of 2026-08-25. Confirmed compatible before
  using it: JetBrains' own compose-compatibility docs state Compose
  Multiplatform `1.8.0`+ needs Kotlin `2.1.0` minimum, `2.2.20`+
  recommended — `2.3.20` is comfortably above both.
- **`purchases-kmp-core: 3.6.0`** — latest `3.x` on Maven Central as of
  2026-08-25 (checked directly, not assumed: `3.5.1` was the version
  guidelines previously recorded as tried-and-failed; `3.6.0` is newer
  and was re-checked from scratch). **Declared in `iosMain` only, not
  `commonMain`** — `3.x` publishes iOS-native variants exclusively (no
  JVM/Android artifact in this line); declaring it in `commonMain`
  produces an immediate, unambiguous Gradle "no matching variant" error
  for the `jvm()` target, not a subtle runtime issue.

### The klib ABI check (how the exact Kotlin version was chosen, not guessed)

Same technique this file already used for the `:game` module's own
RevenueCat investigation (see next section): download the klib directly
and read its manifest.
```bash
curl -sL -o rc.klib "https://repo1.maven.org/maven2/com/revenuecat/purchases/purchases-kmp-core-iossimulatorarm64/3.6.0/purchases-kmp-core-iossimulatorarm64-3.6.0.klib"
unzip -p rc.klib default/manifest
```
Result: `abi_version=2.3.0`, `compiler_version=2.3.20`,
`native_targets=ios_simulator_arm64`. Kotlin's klib ABI reader can only
read klibs at or below its own ABI ceiling (this is the exact same
mechanism that permanently ruled out every `2.0.0+`/`3.0.0`–`3.5.1`
RevenueCat version against `:game`'s Kotlin `2.0.20` — see next
section) — so `paywall-build`'s Kotlin was set to `2.3.20` specifically
to match, not picked arbitrarily. **If `purchases-kmp-core` is ever
bumped again, re-run this exact manifest check against the new
version's klib before assuming any given Kotlin version will read it** —
never assume forward compatibility.

### The link fix — full detail, since this is the genuinely load-bearing, non-obvious part

`compileKotlinIosSimulatorArm64` succeeding is **not** the same as
producing a usable binary — it only compiles a `.klib`. The real test
needed a `binaries.framework { }` declaration (which `paywall-build`
didn't have at first) plus a real call site so the linker has something
to resolve. Added both:

- `paywall-build/build.gradle.kts`: `iosArm64`/`iosSimulatorArm64` each
  got `binaries.framework { baseName = "PaywallModule" }`.
- `paywall-build/src/iosMain/kotlin/PaywallUsage.kt`: a real call to
  RevenueCat's actual public API (`com.revenuecat.purchases.kmp.Purchases`,
  `.configure(apiKey = "...") { appUserId = "..." }`, `LogLevel`) — using
  an obviously-fake placeholder API key, since this only needs to link,
  never run.

First link attempt (`:paywall-build:linkDebugFrameworkIosSimulatorArm64`)
**failed** — a genuinely different failure from every prior RevenueCat
attempt (no CocoaPods error at all, confirming that wall really is gone
for `3.x`):
```
ld: warning: search path '/Applications/Xcode-16.4.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/iphonesimulator/' not found
ld: warning: Could not find or use auto-linked library 'swiftCompatibility56': library 'swiftCompatibility56' not found
ld: warning: Could not find or use auto-linked library 'swiftCompatibilityConcurrency': library 'swiftCompatibilityConcurrency' not found
ld: warning: Could not find or use auto-linked library 'swiftCompatibilityPacks': library 'swiftCompatibilityPacks' not found
Undefined symbols for architecture arm64:
  "__swift_FORCE_LOAD_$_swiftCompatibility56", referenced from:
      ... in libcom.revenuecat.purchases:kn-core-cinterop-RevenueCat-cache.a[440](AdTracker.swift.o)
  "__swift_FORCE_LOAD_$_swiftCompatibilityConcurrency", referenced from: ...
  "_swift_getFunctionTypeMetadataGlobalActorBackDeploy", referenced from:
      ... in libcom.revenuecat.purchases:kn-core-cinterop-RevenueCat-cache.a[82](PurchasesOrchestrator.swift.o)
ld: symbol(s) not found for architecture arm64
```
**Root cause**, visible directly in the first `ld: warning` line above:
Kotlin/Native's default linker invocation searches a **hardcoded/stale
Xcode path** (`Xcode-16.4.app`) for Swift's back-deployment
compatibility shim libraries, rather than resolving whatever Xcode is
actually installed on the build machine. The CI runner's real Xcode was
`Xcode_26.6.app` (visible in the same log, from the `ld` binary's own
invocation path) — the shim libraries genuinely exist there, Kotlin/Native
just never looked in the right place, so RevenueCat's real compiled
Swift object files (`AdTracker.swift.o`, `PurchasesOrchestrator.swift.o`,
`CustomerInfo.swift.o` — all real RevenueCat code, confirming it was
genuinely engaged, not silently skipped) referenced symbols the linker
could never find.

**The fix** — compute the real Xcode developer directory via
`xcode-select -p` at Gradle configuration time and add it as an
explicit linker search path, in `paywall-build/build.gradle.kts`:
```kotlin
import org.gradle.internal.os.OperatingSystem
import java.io.ByteArrayOutputStream

// Guarded to macOS only: this build.gradle.kts is also configured on
// Windows dev machines (composite builds configure every included
// build eagerly, even for an unrelated :game-only task), where
// xcode-select doesn't exist and would break configuration entirely.
val macDeveloperDir: String? = if (OperatingSystem.current().isMacOsX) {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine("xcode-select", "-p")
        standardOutput = stdout
    }
    stdout.toString().trim()
} else null

fun swiftLibPath(platformSdkName: String): String? =
    macDeveloperDir?.let { "$it/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/$platformSdkName" }

kotlin {
    iosArm64 {
        binaries.framework {
            baseName = "PaywallModule"
            freeCompilerArgs += listOf("-Xbinary=bundleId=com.infiltrate.paywallmodule")
            swiftLibPath("iphoneos")?.let { linkerOpts += listOf("-L$it") }
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "PaywallModule"
            freeCompilerArgs += listOf("-Xbinary=bundleId=com.infiltrate.paywallmodule")
            swiftLibPath("iphonesimulator")?.let { linkerOpts += listOf("-L$it") }
        }
    }
}
```
(The `-Xbinary=bundleId=...` line addresses a separate, unrelated
compiler warning — "Cannot infer a bundle ID" — the compiler explicitly
suggested that exact flag; harmless, included but not proven necessary
for the link fix itself.)

Re-ran after the fix: **`BUILD SUCCESSFUL in 6m 29s`, 5 actionable
tasks: 5 executed, zero undefined symbols.** Only remaining output was
~15 benign warnings about missing Clang module-cache `.pcm` files
(`Foundation`, `UIKit`, `StoreKit`, etc., under
`.../swift-packages/RevenueCat/.build/.../ModuleCache/...`) — a known,
harmless side effect of linking a redistributable static library built
with `-gmodules` without its original module cache present; degrades
debug-symbol quality only, not functionality.

**If this ever needs revisiting** (e.g. after a runner image bumps its
default Xcode, or after any Kotlin/Compose/RevenueCat version bump
here): re-check whether the hardcoded-Xcode-path bug still exists in
whatever Kotlin/Native version is in use, and re-verify the
`xcode-select` fix is still landing on the correct directory — don't
assume it's permanently fixed upstream.

### CI wiring

`.github/workflows/ios-build.yml`, one step, placed right after the
main `"Build unsigned iOS Simulator app (KorGE)"` step:
```yaml
- name: "SPIKE: link paywall-build framework for iOS (RevenueCat 3.6.0 / Kotlin 2.3.20)"
  continue-on-error: true
  run: ./gradlew :paywall-build:linkDebugFrameworkIosSimulatorArm64 --no-configuration-cache --stacktrace
```
`continue-on-error: true` is deliberate and important to understand
correctly: it means a failure in this step **never** red-X's the whole
job, so the main game build's own pass/fail is never conflated with the
spike's. **But this also means GitHub's Actions API reports this step's
`"conclusion"` as `"success"` even when the underlying command
genuinely failed** (verified directly — a run where the link failed
with the undefined-symbols error above still showed
`"conclusion": "success"` for this step via the REST API). **The API's
conclusion field for a `continue-on-error` step is not a reliable
pass/fail signal — always read the actual Gradle output in the raw job
log** (`gh api .../actions/jobs/<id>/logs`, or download via the REST
API) for `BUILD SUCCESSFUL` vs `BUILD FAILED` / `Undefined symbols` /
`FAILED` under that specific step, never trust the green checkmark alone.

### What's still NOT done — explicit next-phase work

Everything above is proven **only inside the isolated `paywall-build`
composite build**. None of it is wired into the real game yet:

- **`:game`'s own RevenueCat setup is completely unchanged.** Still
  Android-only, still pinned to `purchases-kmp-core:1.9.0+14.3.0`, still
  zero `iosMainApi` dependency. Migrating `:game` itself onto this
  proven path (vs. leaving RevenueCat isolated in `paywall-build`
  forever) is a separate, larger decision not made here.
- **`PurchasesBridge.ios.kt` / `PurchasesBridge.android.kt` are still
  stubs**, untouched by this work — `purchase()` still just returns
  `onResult(false)` on iOS. Nothing calls into `paywall-build` from
  either bridge.
- **No native shell/orchestration layer exists** to actually embed
  `PaywallModule.framework` into the real iOS app target (KorGE's
  generated Xcode project). The spike proves the framework itself
  builds and links — not that it's loadable/callable from the shipping
  app, which needs its own investigation (likely: KorGE-generated
  Xcode project + a Swift-side bridge, or Kotlin/Native's interop
  mechanisms to call from `:game`'s own framework into `PaywallModule.framework`).
- **Shared storage bridge: DONE for iOS, as of 2026-08-31.** See
  "Shared storage bridge: paywall-build ↔ :game" below — the open
  questions from this bullet (what backend `views.storage` uses on iOS,
  same-process vs. App Group) are now answered and implemented
  (`PaywallStorage.kt` + `KorgeStorageKey.kt` in `paywall-build`),
  verified logically (JVM unit tests) but not yet on a real device/
  simulator. Android's storage backend is documented there too but not
  implemented — `paywall-build` has no Android target.
- **No real paywall UI exists** anywhere in the codebase (`main.kt` is
  still the untouched korge-hello-world demo scene, per the note in
  "LOCKED WORKING CONFIGURATION" above).
- **Only `iosSimulatorArm64` was actually linked and verified.** The
  `iosArm64` (real device) `binaries.framework` block mirrors the same
  fix by construction but has never actually been run/verified — don't
  assume real-device linking works without checking `:paywall-build:linkDebugFrameworkIosArm64`
  the same way.

### Traceability (commits, all on `main`)

- `fbb7024` — initial `paywall-build` composite build (Kotlin `2.3.20`,
  Compose `1.12.0`, `purchases-kmp-core:3.6.0` dependency added).
- `eeb627d` — unrelated `:game` fix (see top-of-file update note) that
  had to land first, since it was blocking CI before the spike could
  even run.
- `9a523c1` — added the CI step that first proved `paywall-build`'s
  klib *compiles* (`compileKotlinIosSimulatorArm64`).
- `6d57823` — added the real `binaries.framework` declaration + real
  `Purchases.configure()` call site + switched the CI step to the link
  task (first attempt, failed with the undefined-symbols error above).
- `4a5f3b8` — the `xcode-select` linker-search-path fix. This is the
  commit where the link first succeeded.

## Shared storage bridge: paywall-build ↔ :game (2026-08-31) — iOS done, Android deferred

**This replaces the "No shared storage bridge is implemented" bullet under
"What's still NOT done" in the RevenueCat section above** — that's no
longer accurate for iOS. This is the foundation everything else in that
"What's still NOT done" list depends on, so read this before touching it.

### What `Views.storage` actually uses — verified, not assumed

The earlier open question ("confirm what backend KorGE's `views.storage`
actually uses on iOS today — don't assume `NSUserDefaults`") is now
answered by reading KorGE 6.0.0's real implementation directly (extracted
from the local Gradle cache: `korge-6.0.0-sources.jar` for iOS,
`korge-android-6.0.0.aar`'s `classes.jar` decompiled for Android, since no
sources jar is published for the Android artifact):

- **iOS/darwin** (`korlibs.korge.service.storage.DarwinNativeStorage`,
  used by every Kotlin/Native darwin target, including
  `iosArm64`/`iosSimulatorArm64`): backed by
  `NSUserDefaults(suiteName = "korge")` — a **named suite**, deliberately
  NOT `NSUserDefaults.standardUserDefaults` (that line is commented out
  in KorGE's own source, right above the suite version). Every key is
  prefixed with `"org.korge.storage."` before being read/written
  (`getKey(key) = "org.korge.storage.$key"`). Values go through
  `setObject(value, forKey:)` / `objectForKey(key)?.toString()`, and
  every write/remove calls `synchronize()` afterward.
- **Android** (`korlibs.korge.service.storage.NativeStorage`, decompiled
  with `javap -c -constants` since only the `.aar` is published, no
  sources jar): backed by
  `context.getSharedPreferences("KorgeNativeStorage", 0 /* MODE_PRIVATE */)`.
  Keys are stored **unprefixed**, plain `putString`/`getString` — no
  transformation needed on this platform.
- A named `NSUserDefaults` suite with no App Group entitlement resolves
  to the same on-disk plist for any code running inside the same app
  sandbox/process, regardless of which compiled framework instantiates
  it — so once `PaywallModule.framework` is actually embedded into
  `:game`'s app target (still not done — see "No native shell/orchestration
  layer exists" below, unchanged), no App Group is needed for this to
  work. If a different integration is ever chosen (e.g. paywall running
  as a separate extension process instead of embedded in the same app
  target), this assumption would need revisiting.

### What was implemented

- `paywall-build/src/commonMain/kotlin/KorgeStorageKey.kt` — pure
  `PREFIX`/`iosKey()` helper replicating `DarwinNativeStorage`'s exact key
  transform, kept separate from the real iOS calls so it's unit-testable
  on the JVM target.
- `paywall-build/src/iosMain/kotlin/PaywallStorage.kt` — real
  `getRaw`/`setRaw` backed by `NSUserDefaults(suiteName = "korge")`,
  matching `:game`'s `MapBackedGameProfileStorage` contract
  (`GameProfile.kt`) exactly.
- `paywall-build/src/commonTest/kotlin/StorageKeyCompatibilityTest.kt` +
  a `commonTest`/`jvmTest` source set added to
  `paywall-build/build.gradle.kts` (it already had a `jvm()` target for
  `Placeholder.kt`, just no test source set yet).
- Keys covered: the 5 keys `MapBackedGameProfileStorage` actually
  persists today — `user_coins`, `user_is_premium`, `user_music_vol`,
  `user_sfx_vol`, `user_unlocked_levels`. **Pre-existing gap, found while
  reading `GameProfile.kt` for this work, not caused by it and not fixed
  here:** `GameProfile.powerupInventory` exists on the data class but
  `MapBackedGameProfileStorage.persist()`/`loadFromStorage()` never
  reads/writes it — powerup counts are silently lost across app restarts
  today, independent of anything in this section. Whoever fixes that
  should add the matching key to this storage bridge too.

### Verification — logical/JVM-only, not on-device (be precise about this)

`./gradlew :paywall-build:jvmTest` — **BUILD SUCCESSFUL, 4/4 tests
executed and passed** (confirmed via the actual JUnit XML output, not
just Gradle's exit code, per this file's own "verify the actual output"
standard from the RevenueCat CI section above). What these tests prove:
that `:game`'s key-prefixing logic and `paywall-build`'s `iosKey()`
produce identical raw keys, and that a value written through one side's
transform reads back correctly through the other's, for all 5 keys.

**What this does NOT prove**: that the real on-device `NSUserDefaults`
store is actually shared between the two separately compiled frameworks.
This dev machine has no iOS simulator/Xcode, so that round-trip can't be
run here — same constraint already documented for the real link/build
steps elsewhere in this file. Also run, as a real (not assumed) signal:
`./gradlew :paywall-build:compileKotlinIosSimulatorArm64
--no-configuration-cache` **succeeds on this Windows machine** (Kotlin/Native
klib compilation for Apple targets doesn't require macOS, unlike linking)
— confirms `PaywallStorage.kt` compiles cleanly against the real
`platform.Foundation.NSUserDefaults` iOS bindings. Actual on-device
verification (write from a `PaywallModule.framework` call, read back via
`:game`'s `views.storage`) is still open — do that once the native shell
work embeds the framework into the real app target.

### What's still NOT done (Android + everything downstream)

- **No Android implementation.** `paywall-build` has zero Android target
  today (only `jvm()` + iOS) — deliberate, confirmed with the user:
  `:game` already talks to RevenueCat directly on Android via
  `androidMainApi`, so there's no cross-framework boundary to bridge
  there yet. If `paywall-build` ever needs an Android target, the
  `SharedPreferences("KorgeNativeStorage", MODE_PRIVATE)` backing
  documented above is what a matching `getRaw`/`setRaw` needs to target —
  no key prefix needed on that platform, unlike iOS.
- Everything else in the RevenueCat section's "What's still NOT done"
  list is still true and unaffected by this: no native shell/embedding,
  no paywall UI, `PurchasesBridge.ios.kt` is still a stub and doesn't
  call into `paywall-build` or `PaywallStorage` at all yet, only
  `iosSimulatorArm64` has ever been linked (not `iosArm64`/real device).
- No CI step runs `:paywall-build:jvmTest` yet — it's runnable locally
  today; wiring it into `gradle.yml`/`ios-build.yml` is a separate,
  not-yet-decided follow-up.

## Native iOS shell: `ios-shell/` (2026-08-31) — WORKING, verified on-device in real CI

**Status: GREEN, for real.** Attempt 1 (commit `e6dae18`, run
[33382783757](https://github.com/MalithaBandara/infiltrate-shadow-heist/actions/runs/33382783757))
failed with a genuine architecture-mismatch build error (see below).
Attempt 2 (commit `3fa602d`, run
[33385051973](https://github.com/MalithaBandara/infiltrate-shadow-heist/actions/runs/33385051973))
**succeeded — confirmed from the raw log, not the API conclusion field**:
`GameMain.framework` and `PaywallModule.framework` link together into one
real `ShellApp.app`, and the on-device storage-bridge round-trip
genuinely passes. The two risks flagged before attempt 1 (duplicate
Kotlin/Native runtime symbols; `@ObjCName` prefix-stripping) are both
now resolved, not just theorized — see "Attempt 2" below for the exact
proof. Don't re-litigate either without a reason; both are settled.

### What this is

The first attempt to run `:game`'s `GameMain.framework` and
`paywall-build`'s `PaywallModule.framework` **in the same process** —
everything before this only proved each framework links in isolation.
Deliberately minimal: no paywall UI, no `PurchasesBridge.ios.kt` wiring.
Purpose-built to get the first on-device signal for the storage bridge
(previous section), which was until now only verified with a JVM-only
logical test.

### Load-bearing finding: why the trigger lives in Swift, not a KorGE scene

Reading KorGE's real iOS entry-point source directly
(`korlibs.render.KorgwBaseNewAppDelegate`, `DefaultGameWindowIos.kt`,
extracted from the local Gradle cache — not KorGE's docs, not assumed)
confirms `:game` (Kotlin 2.0.20) and `paywall-build` (Kotlin 2.3.20)
produce **ABI-incompatible klibs** — the exact same wall already
documented above for why `:game` can't depend on RevenueCat 3.6.0
directly. That means `:game`'s own Kotlin code cannot call into
`PaywallModule`'s Kotlin API either — there is no way to put a real
cross-framework call inside a KorGE scene without a Kotlin **cinterop**
binding against `PaywallModule.framework`'s compiled Objective-C header
(possible in principle, since cinterop reads compiled headers, not
klibs — but real added Gradle/cinterop surface, deliberately not done
here). **Confirmed with the user**: the round-trip trigger lives in the
native Swift shell instead, which can safely call both frameworks'
exported Objective-C APIs with no coupling between the two Kotlin
builds.

### What was built

- **`src@ios/ShellAppDelegate.ios.kt`** (new, `:game`) — subclasses
  `KorgwBaseNewAppDelegate`. Confirmed directly from source: only
  `applicationDidFinishLaunching(app: UIApplication)` is abstract;
  background/foreground/resign/terminate are already concrete on the
  base class. Calls the base class's 2-arg overload with `:game`'s real
  entry point (`suspend fun main()` in `src/main.kt`).
- **`src@ios/DebugStorageBridge.ios.kt`** (new, `:game`) — exercises the
  SAME production path every scene already uses
  (`game.model.MapBackedGameProfileStorage`), just swapping
  `views.storage[it]` for `korlibs.korge.service.storage.DarwinNativeStorage`
  directly. This works with no live `Views`/window because on darwin,
  KorGE's `NativeStorage` is literally `by DarwinNativeStorage` — a plain
  object, not `Views`-dependent. A fresh `MapBackedGameProfileStorage` is
  constructed on every call (its `init{}` loads from storage), so this
  proves a real disk re-read, not a cached in-memory value.
- **`ios-shell/`** (new directory) — hand-authored via **XcodeGen**
  (`project.yml`, declarative — far less error-prone to author correctly
  without Xcode to check against than a raw `project.pbxproj`; also the
  same tool KorGE's own plugin uses internally, so it's a safe bet for
  runner availability). Deliberately separate from
  `build/platforms/ios` (KorGE's own generated project, not used here).
  One app target, embeds both frameworks by relative path to their
  standard Kotlin/Native debug-framework output locations. Entry point is
  a classic `main.swift` + `UIApplicationMain(...)` (not `@UIApplicationMain`/`@main`,
  to avoid depending on attribute behavior that varies by Xcode version
  and can't be checked here). `AppDelegate.swift` forwards lifecycle
  calls to `ShellAppDelegate.shared`, adds one native `UIButton`+`UILabel`
  overlay for manual testing, and **also runs the same check
  automatically once at launch** — writes `PaywallStorage.shared.setRaw(key:
  "user_coins", value: <timestamp-derived test value>)`, then
  `DebugStorageBridge.shared.readCoinsForDebug()`, compares, and writes
  `"OK"` / `"FAIL:expected=X:actual=Y"` to
  `Documents/storage_bridge_result.txt` in the app's sandbox — this is
  what CI reads back, so verification doesn't require simulating a tap.
- **`.github/workflows/ios-build.yml`** — new steps after the existing
  `paywall-build` spike (all `continue-on-error: true`, same convention):
  install XcodeGen if missing, `xcodegen generate`, `xcodebuild build`,
  then boot a simulator / install / launch / read the result file back
  via `xcrun simctl get_app_container ... data` and fail the step
  (explicit content check, not just launch exit code) if it isn't `OK`.

### Attempt 1 (2026-08-31, commit `e6dae18`, run [33382783757](https://github.com/MalithaBandara/infiltrate-shadow-heist/actions/runs/33382783757)) — real CI result, read from raw logs not the conclusion field

**The GitHub API reported this whole run as `"conclusion": "success"` —
that is misleading and must not be trusted**, exactly as this file's own
"continue-on-error conclusion is not reliable" rule (RevenueCat section
above) predicted. Every new step's own `conclusion` field also said
`"success"`. The **raw logs** tell a different story:

- `:game`'s own `iosBuildSimulatorDebug` step: real `** BUILD SUCCEEDED **`
  — confirms `src@ios/ShellAppDelegate.ios.kt` and
  `src@ios/DebugStorageBridge.ios.kt` **do compile** cleanly against
  `:game`'s real Kotlin 2.0.20 iOS target (this had never been tested
  anywhere before this run — the KorGE-specific local `onlyIf` gate,
  point 4 below, made it impossible to check on this dev machine).
- `paywall-build`'s link spike: real `BUILD SUCCESSFUL in 3m 37s` —
  `PaywallModule.framework` output path is now confirmed for real,
  resolving the "unconfirmed locally" caveat this section used to carry.
- **The shell app `xcodebuild build` step genuinely failed**:
  ```
  ShellApp: ld: warning: ignoring file '.../GameMain.framework/GameMain': found architecture 'arm64', required architecture 'x86_64'
  ShellApp: ld: warning: ignoring file '.../PaywallModule.framework/PaywallModule': found architecture 'arm64', required architecture 'x86_64'
  Undefined symbols for architecture x86_64:
    "_OBJC_CLASS_$_GameMainDebugStorageBridge", referenced from: in AppDelegate.o
    "_OBJC_CLASS_$_GameMainShellAppDelegate", referenced from: in AppDelegate.o
    "_OBJC_CLASS_$_PaywallModulePaywallStorage", referenced from: in AppDelegate.o
  ld: symbol(s) not found for architecture x86_64
  ** BUILD FAILED **
  ```
  Root cause: `xcodebuild -sdk iphonesimulator` with no `-destination`
  defaulted to building BOTH `arm64` and `x86_64` simulator slices.
  `GameMain.framework`/`PaywallModule.framework` are `iosSimulatorArm64`
  Kotlin/Native output — arm64 only, no x86_64 slice — so the x86_64
  link had nothing to resolve against. **The duplicate-Kotlin-native-
  runtime-symbol risk flagged before this ever ran was never actually
  tested** — this earlier, unrelated problem blocked the build first.
  Fixed for the next attempt: `EXCLUDED_ARCHS[sdk=iphonesimulator*] =
  x86_64` added to `ios-shell/project.yml`'s target settings — the
  standard fix for an Apple-Silicon-only binary framework, independent
  of whatever `-destination` `xcodebuild` happens to pick.
- **Separately confirmed, real and precise**: the undefined-symbol names
  above (`GameMainShellAppDelegate`, `GameMainDebugStorageBridge`,
  `PaywallModulePaywallStorage`) prove `@ObjCName(name = "X")` **without
  `exact = true`** does NOT override Kotlin/Native's default
  framework-name-prefixed export — it compiles fine (Swift resolves the
  short name via the generated interface) but the real linked ObjC class
  symbol keeps the framework prefix. Fixed for the next attempt: all
  three objects (`PaywallStorage`, `ShellAppDelegate`,
  `DebugStorageBridge`) now use `@ObjCName(name = "X", exact = true)`,
  gated by an additional opt-in,
  `@OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)` (on
  top of the `ExperimentalObjCName` opt-in already needed for `@ObjCName`
  at all). **Re-verified locally**: `:paywall-build:compileKotlinIosSimulatorArm64`
  still compiles cleanly with `exact = true` added — but whether the
  *linked* symbol is actually unprefixed now can only be confirmed by a
  real link, i.e. the next CI run, not locally.
- The simulator round-trip check never got to run (app never built) —
  its own step failed with `Missing bundle ID` trying to install the
  incomplete `.app` Xcode had scaffolded before the link failure. Purely
  downstream of the build failure above, not an independent finding.

### Attempt 2 (2026-08-31, commit `3fa602d`, run [33385051973](https://github.com/MalithaBandara/infiltrate-shadow-heist/actions/runs/33385051973)) — SUCCEEDED, verified from raw logs

Same discipline as attempt 1: the API's `"conclusion": "success"` alone
is not proof (it said that for attempt 1's real failure too). The raw
log this time genuinely backs it up:

- `xcodebuild` step: no architecture-mismatch warnings at all (the
  `EXCLUDED_ARCHS[sdk=iphonesimulator*] = x86_64` fix worked), a clean
  link/codesign sequence —
  ```
  Ld .../ShellApp.app/ShellApp.debug.dylib normal (in target 'ShellApp' from project 'ShellApp')
  Ld .../ShellApp.app/ShellApp normal (in target 'ShellApp' from project 'ShellApp')
  CodeSign .../ShellApp.app/ShellApp.debug.dylib (in target 'ShellApp' from project 'ShellApp')
  CodeSign .../ShellApp.app (in target 'ShellApp' from project 'ShellApp')
  ```
  — and `** BUILD SUCCEEDED **`. **No duplicate-symbol errors anywhere**
  — the risk flagged before attempt 1 (each Kotlin/Native framework
  embedding its own runtime) turned out not to be a real problem for this
  pair of frameworks. **This also confirms `@ObjCName(exact = true)`
  actually worked**: if the exported names were still prefixed, this
  link would have failed with the same "Undefined symbols" error as
  attempt 1, just for the correct architecture instead — it didn't.
- Round-trip verification step, real printed output:
  ```
  Found app at: ios-shell/build/Build/Products/Debug-iphonesimulator/ShellApp.app
  Using simulator device: 6F69910C-E4FA-488A-B9C2-41B770484810
  Monitoring boot status for iPhone 17 Pro (6F69910C-E4FA-488A-B9C2-41B770484810).
  ...
  com.infiltrate.shellapp: 33475
  Storage bridge result: OK
  Storage bridge round-trip verified ON-DEVICE (not just JVM-logical): OK
  ```
  `com.infiltrate.shellapp: 33475` is `simctl launch`'s own confirmation
  the app actually launched (its PID) without crashing. `Storage bridge
  result: OK` is read back from the app's own
  `Documents/storage_bridge_result.txt` — i.e. `PaywallStorage.shared.setRaw(...)`
  (from `PaywallModule.framework`) and `DebugStorageBridge.shared.readCoinsForDebug()`
  (from `GameMain.framework`, going through the real
  `MapBackedGameProfileStorage` path) agreed on the same value, in one
  real running process, on a real simulator. This is the first genuine
  on-device confirmation of the storage bridge — the JVM-only test from
  the previous session is now superseded by this as the stronger proof,
  though the JVM test is still useful as a fast local regression check.

### Resolved risks (for future sessions: don't re-litigate these without a new reason)

1. ~~Duplicate Kotlin/Native runtime symbols~~ — tested for real in
   attempt 2, did not occur. Not a problem for this specific pair of
   frameworks (Kotlin 2.0.20 `:game` + Kotlin 2.3.20 `paywall-build`,
   both built as dynamic frameworks, embedded together in one app
   target). If a future session adds a *third* Kotlin/Native framework
   to this app, re-verify — this isn't proven for arbitrary combinations.
2. ~~`@ObjCName(name = "X")` without `exact = true`~~ — confirmed broken
   (attempt 1), confirmed fixed by adding `exact = true` +
   `@OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)`
   (attempt 2 links clean). Apply this pattern to any future Kotlin/Native
   object exported for cross-framework/Swift use in this project.

### Still-open / genuinely unresolved

- **`:game`'s own `compileKotlinIosSimulatorArm64` is disabled on this
  Windows machine** (`Skipping task ... as task onlyIf 'Task is enabled'
  is false`) — a KorGE-plugin-specific gate, not a general Kotlin/Native
  limitation (the identical task for `paywall-build`, plain
  `kotlin("multiplatform")`, runs fine here). Root cause not traced
  (time-boxed both times it came up). Practical effect: any future change
  to `:game`'s iOS-only source can only be compile-checked by CI, never
  locally, on this machine.
- No paywall UI, no `PurchasesBridge.ios.kt` wiring, no shared storage
  bridge actually driving anything yet — the round-trip above is a
  proof-of-concept debug path (`DebugStorageBridge`, a native overlay
  button + an automatic launch-time self-check), not real product code.
  Unchanged, out of scope for this step.
- Only `iosSimulatorArm64` — `iosArm64` (real device) untouched for both
  `GameMain.framework` and `PaywallModule.framework`, same caveat as the
  `paywall-build` spike above.
- `ios-shell/` is a standalone proof-of-concept project, not yet wired
  into any release/distribution pipeline, and not the same project as
  KorGE's own generated `build/platforms/ios` — no decision has been made
  about whether/how these converge for a real shipping app.

## Compose/KorGE view-switching spike (2026-09-01) — PROVEN VIABLE on real iOS Simulator CI

**Status: viable, build the real menu/store/gameplay architecture around
this.** Tests the architecture where KorGE is shown ONLY during actual
gameplay and Compose Multiplatform owns everything else (menu, level
select, store, settings, and a "watch ad to continue" flow that re-enters
KorGE after the ad finishes) — reusing `ios-shell/`'s already-proven
`GameMain.framework` + `PaywallModule.framework` embedding rather than a
new app.

### What was built

- **`paywall-build/src/iosMain/kotlin/SpikeComposeScreen.kt`** — the
  **first real Compose UI written anywhere in this repo** (`paywall-build`
  previously only declared `compose.runtime`/`foundation`/`material3`
  with no actual `@Composable`). One centered "Start Level" button,
  exported as `SpikeComposeScreen.shared.makeViewController(onStartLevel:)`
  via `ComposeUIViewController { }`. Needed adding `implementation(compose.ui)`
  to `paywall-build/build.gradle.kts`'s `iosMain` (not `commonMain` —
  UIKit-specific, would break the `jvm()` target, same reasoning as the
  RevenueCat dependency just above it).
- **`src@ios/SwitchSpikeScene.kt`** — a debug KorGE scene (pulsing rect,
  live tick counter, a real "END LEVEL (debug)" button wired via
  `.mouse { onClick { ... } }`) that the app **never navigates away
  from** — the whole point is that the KorGE engine + this scene stay
  resident for the app's life; "switching" is purely a native
  `window.rootViewController` toggle, not a KorGE scene change.
- **`src@ios/SpikeBridge.ios.kt`** — a Kotlin/Native object (same
  `@ObjCName(exact = true)` export pattern as `DebugStorageBridge`/`ShellAppDelegate`)
  exposing a per-frame `frameTicks` counter, incremented from
  `SwitchSpikeScene`'s `addUpdater { }` every render tick regardless of
  UIKit visibility. This is the actual measurement instrument: Swift
  polls it to detect (a) when a fresh frame renders after KorGE is shown
  again, and (b) whether it keeps changing while hidden.
- **`src@ios/SpikeEntry.ios.kt`** — a separate `spikeMain()`, structurally
  identical to `src/main.kt`'s real `Korge { sceneContainer()... }` but
  pointed at `SwitchSpikeScene` instead of `SplashScene`. `ShellAppDelegate.ios.kt`'s
  entry lambda was temporarily switched from `{ main() }` to
  `{ spikeMain() }` for this spike — commonMain's real `src/main.kt` was
  never touched.
- **`ios-shell/Sources/AppDelegate.swift`** — drives 6 fully automated
  Compose→KorGE→Compose cycles in one app session (no human tapping
  needed, no fragile `xcodebuild`/`XCUITest` tap-simulation): for each
  cycle, swap to KorGE and poll `SpikeBridge.shared.frameTicks` (2ms
  `Timer`) until it changes or times out (switch latency), dwell ~700ms
  visible (proves active rendering), call the same `requestLevelEnd()`
  the debug button calls, swap back to Compose, dwell ~1200ms hidden
  (proves the render loop actually stopped), sample resident memory via
  `mach_task_basic_info`. Results written to
  `Documents/switch_spike_result.txt`, read back by CI the same way
  `storage_bridge_result.txt` already was.

### The 5 CI iterations — real bugs, not flakiness, each confirmed from raw logs

Every one of these showed **`"conclusion": "success"`** in the GitHub
Actions API/UI (the steps use `continue-on-error: true`, same convention
as the rest of this file) while the actual command underneath had failed.
**This is not a one-off — it happened on 3 of 5 rounds in a row for
different reasons.** Never trust the checkmark for these steps; always
pull the job's raw log (`gh api .../actions/jobs/<id>/logs` or the REST
`/logs` zip) and grep for the real output.

1. **`62b135b`, run `33412736968`** — genuine Swift compile failure:
   `private var window: UIWindow?` on `AppDelegate` collided with
   `UIApplicationDelegate`'s own optional `window` property requirement
   (Swift requires protocol-conforming properties to be at least as
   accessible as the enclosing type). `** BUILD FAILED **` in the raw
   `xcodebuild` log; the app was never even built, let alone run. Fixed
   by renaming to `shellWindow` (`1320743`).
2. **`1320743`, run `33414886599`** — build succeeded and the app ran
   correctly, but the CI step meant to wait for the result file
   (`xcrun simctl io "$DEVICE_ID" screenshot ...` on every iteration of a
   100-iteration poll loop) turned out to cost **1–13 real seconds per
   screenshot on this runner** (confirmed from the raw per-line log
   timestamps, not assumed) — so the loop burned its entire 100-"second"
   budget on ~100 slow screenshots and never gave the app a real chance
   to be checked before giving up. Looked exactly like a hang; wasn't
   one. Fixed by dropping screenshots from that loop entirely for the
   next round and using a genuinely fast 1s-interval poll.
3. **`06aea45`, run `33417902658`** — **first clean success.** Fast poll
   found the result file **within ~1s of starting to check** (the whole
   6-cycle sequence had already finished during the previous step's own
   ~13–14s post-launch window). Real per-cycle data, see table below.
4. **`a8caaf7`, run `33420468867`** — re-added screenshots (this time in
   the *launch* step, right after `simctl launch`, so they'd actually
   land during the switching window instead of after it) — but keeping
   the CI step alive for ~95+ real seconds (10 screenshots × up to 13s
   each) gave a **genuine, deterministic Compose Multiplatform crash**
   time to fire: `SIGABRT`, Kotlin `kotlin#error(...)`, inside
   `androidx.compose.ui.uikit.PlistSanityCheck` — fetched directly from
   `JetBrains/compose-multiplatform-core` (`compose/ui/ui/src/iosMain/kotlin/androidx/compose/ui/uikit/PlistSanityCheck.ios.kt`):
   it dispatches a one-time check onto a **low-priority background
   queue** (`DISPATCH_QUEUE_PRIORITY_LOW`, matching the crash's own
   `"queue":"com.apple.root.utility-qos"` exactly) that hard-aborts the
   process if `Info.plist` doesn't have
   `CADisableMinimumFrameDurationOnPhone` set to `true`. `ios-shell/project.yml`
   never set it. Round 3 (above) never crashed only because its CI step
   exited in ~13s — too fast for the low-priority check to have fired
   yet; it would eventually have crashed there too, just later than CI
   was watching. **Not a switch-spike bug, not flaky — any Compose-on-iOS
   work in this repo needs this key regardless of the switching
   architecture.**
5. **`2130686`, run `33423097578`** — added
   `CADisableMinimumFrameDurationOnPhone: true` to `ios-shell/project.yml`'s
   Info.plist properties. Clean 6-cycle run, no crash, real numbers for
   every field including the one round 3 was missing
   (`switchToComposeLatencyMsApprox`).

### Real numbers (both clean runs — 06aea45/`33417902658` and 2130686/`33423097578` — cited separately, cross-run variance is Simulator-noise, not a regression)

| cycle | switchToKorgeLatencyMs (run 3) | switchToKorgeLatencyMs (run 5) | hiddenDwellTicksAdvanced (both runs) |
|---|---|---|---|
| 1 (cold) | 370.8 | 383.4 | 0 |
| 2 | 390.0 | 84.5 | 0 |
| 3 | 485.7 | 60.0 | 0 |
| 4 | 111.8 | 119.1 | 0 |
| 5 | 229.7 | 103.2 | 0 |
| 6 | 157.1 | 74.5 | 0 |

- **Switch-to-KorGE latency**: noisy on the Simulator (cycle 3 of run 3
  was actually its slowest, not cycle 1) but consistently well under
  500ms cold and in the 60–120ms range once warm across both runs — no
  loading spinner needed for the "continue after ad" re-entry; a brief
  fade on the very first level entry would be a reasonable hedge against
  the ~370–390ms cold case.
- **`hiddenDwellTicksAdvanced` = 0, every cycle, both runs, no
  exception.** This is the load-bearing result: `GLKViewController`'s
  internal display link genuinely stops firing once its view leaves the
  window via a plain `window.rootViewController =` swap — **no manual
  `.paused = true`/resume plumbing needed.** Directly answers the
  battery-drain question this spike was built to answer.
- **`korgeDwellTicksAdvanced`** (frames rendered per ~700ms while
  visible) started low in run 3 (1–2 ticks in cycles 1–2, rising to
  16–17 by cycles 5–6) and higher-but-still-well-under-60fps in run 5
  (2 then 19–24) — almost certainly Metal shader warm-up on the
  Simulator (`"Metal Compiling Shader"` lines visible in the unified
  log right at launch), not representative of real-device frame rates.
  Don't read the absolute fps here as a real-device number; the
  visible/hidden *on-off* behavior is the reliable part.
- **Memory**: run 3 — 297→303→300→294→267→288 MB; run 5 —
  310→319→314→297→292→275 MB. Both fluctuate without a monotonic growth
  trend across 6 cycles. Not proof of no leak (6 cycles is a small
  sample — a slow leak could easily hide in this noise), but no obvious
  runaway either.
- **`switchToComposeLatencyMsApprox`**: turned out to be a flawed proxy,
  confirmed once real data came in — it measures "time until the next
  *KorGE* tick after hiding it," which is guaranteed to hit its own
  2000ms timeout precisely because the previous bullet's finding is
  true (no more KorGE ticks happen once hidden). It reported exactly
  `~2000.4` for all 6 cycles in run 5, i.e. it timed out every time —
  self-consistent with, not contradicting, the hidden-dwell result, but
  it says nothing about how fast Compose itself becomes visually ready.
  Not re-instrumented this session; a trivial static screen like this
  one is expected to be sub-frame, but that's an expectation, not a
  measurement.

### What's still open

- **Visual flash/glitch check is inconclusive, not clean.** 10
  screenshots were captured (via `xcrun simctl io screenshot`, moved
  into the launch step so they'd land during the switching window) but
  **all 10 landed on the same static Compose "Start Level" screen** —
  the entire 6-cycle sequence completes in ~13–14s real time while each
  screenshot itself costs 1–13s, so external `simctl` polling never
  happened to land inside one of the ~100–500ms KorGE-visible windows.
  If real visual proof is needed before shipping, the fix is to
  deliberately hold KorGE visible for a few seconds on just one cycle
  (not all 6) specifically to make it screenshot-able — not attempted
  this session.
- Only tested on `iosSimulatorArm64`, same caveat as every other iOS
  spike in this file — real-device (`iosArm64`) frame-rate/latency
  characteristics are unverified and could differ meaningfully from the
  Simulator's Metal-emulation numbers above.
- This is still a debug scene (`SwitchSpikeScene`) and a throwaway
  Compose screen (`SpikeComposeScreen`), not real menu/gameplay code.
  `ShellAppDelegate.ios.kt`'s entry lambda currently points at
  `spikeMain()` — **revert to `{ main() }` before/while building the
  real Compose menu integration**, this was left pointed at the spike
  deliberately so the CI evidence above could be gathered, not as an
  oversight.
- `CADisableMinimumFrameDurationOnPhone: true` is only set in
  `ios-shell/project.yml`. If/when the real Compose menu UI moves into
  a different Xcode project or KorGE's own generated
  `build/platforms/ios`, that project's `Info.plist` needs the same key
  or it will hit the identical `PlistSanityCheck` crash.

## `korge-video` feasibility spike (2026-09-01) — NOT VIABLE

**Status: do not build the planned menu video background around
`korge-video`.** Tested as a pure feasibility check (throwaway scene,
`deps.kproject.yml` entry, `runJvm`), fully reverted afterward — no trace
left in the working tree, documented here so the investigation isn't
redone.

- **Doesn't even compile on JVM** against this project's locked KorGE
  `6.0.0`: `:korge-video:compileKotlinJvm` failed with a wall of
  `Unresolved reference` errors (`PlatformAudioOutput`, `delay`, `stop`,
  `dispose`, `setFloatStereo`, `createPlatformAudioOutput`,
  `readShortArrayLE`, `writeArrayLE`) — korau/korio APIs renamed or
  removed between whatever KorGE version `korge-video` was last built
  against and `6.0.0`.
- **[korlibs/korge-video](https://github.com/korlibs/korge-video)**: 0
  stars, 13 commits total, **last real commit 2023-10-05** ("Upgrade
  KorGE to 5.0.5") — nearly 3 years stale. The library's own maintainer
  (soywiz) opened
  [issue #2](https://github.com/korlibs/korge-video/issues/2), "Apply
  patch to fix android on korge 6.0.0", in Feb 2025 with an unmerged
  patch attached — still open, confirming even Android doesn't work
  out of the box against this project's exact KorGE version.
- **iOS backend is a literal empty stub, not just unmaintained**:
  `korge-video/src/nativeMain/kotlin/korlibs/video/internal/KorviInternalNative.kt`
  (the implementation used by every Kotlin/Native target, iOS included)
  is
  ```kotlin
  internal class NativeKorviInternal : KorviInternal() {
  }
  ```
  — overrides nothing, silently falls back to the common base class's
  `DummyKorviVideoLL(3.minutes)`: a fake generated video (solid
  background + elapsed-time text), not real MP4 decoding. A
  `nativeInterop/cinterop/min_ffmpeg.def` suggests an abandoned attempt
  at a real FFmpeg-backed native decoder that was never wired up. No
  issue, open or closed, mentions iOS at all.
- Real decoding only exists for JVM (JCodec-based MP4 demuxer) and
  Android (`MediaPlayer`)/JS. No reusable `View` wrapper ships in the
  library either — consumers hand-roll one from the library's own demo
  app's source, which is what the spike scene did.
- **If video playback is still wanted**: cheaper paths that don't
  need any new dependency are re-encoding the clip as a low-fps
  PNG/JPEG frame sequence animated through KorGE's normal
  `Bitmap`/`Animation` APIs, or a sprite-sheet-style approach — both
  proven, no codec involved.

## AdMob (`basic-ads`) feasibility spike (2026-09-01/02) — VIABLE, proven working on real iOS Simulator (link + on-device run)

**Status: `app.lexilabs.basic:basic-ads` genuinely links AND runs on iOS.** CI run
[33559815333](https://github.com/MalithaBandara/infiltrate-shadow-heist/actions/runs/33559815333)
(commit `27702a7`) confirmed, from raw logs: `syncPodComposeResourcesForIos` `BUILD SUCCESSFUL`,
no crash reports, `LEVEL TRANSITION RESULT: TRANSITION_OK`, storage bridge
`OK:coins=350:unlocked=level_1;level_2;level_4`, and — the actual point of the spike —
`AdMob verify result: OK:initializeCalled=true:bannerLoaded=true`: a real Google-served banner
ad genuinely initialized and loaded on a real iOS Simulator. See "Watch ad to continue — real
feature" below for what got built on top of this, and the on-device crash chase (unrelated to
AdMob) that had to be fixed first.
Isolated to `paywall-build`, same module used for the RevenueCat and
Compose/KorGE spikes above — real CocoaPods wiring was required (unlike
RevenueCat 3.x, which deliberately avoids CocoaPods entirely), and getting
it working took 3 attempts, the last one only solved by reading Kotlin's
own Gradle plugin source directly rather than guessing.

### Why `basic-ads` over the alternatives

Checked three KMP AdMob wrappers. The other two
(`saitawngpha/Admob-KMP`, `AndreSand/ads-kmp`) are 1–3-star, 4–7-commit
personal projects with no Maven Central publication — the same red flags
as `korge-video` above. `app.lexilabs.basic:basic-ads` is real: 109 stars,
87 commits, last commit **2026-08-23** (days before this spike), published
on Maven Central, genuine 1:1 `iosMain`/`androidMain` implementations
(checked its actual file tree, not just the README) for all four ad
formats (Banner, Interstitial, Rewarded, Rewarded Interstitial) plus GDPR
consent handling.

### Attempt 1: plain Maven dependency, no CocoaPods — FAILED

Just `implementation("app.lexilabs.basic:basic-ads:1.2.1")` in
`androidMain`/`iosMain`, no `cocoapods {}` block. Failed at
`:paywall-build:linkDebugFrameworkIosSimulatorArm64`:
```
ld: framework 'GoogleMobileAds' not found
```
Confirms `basic-ads` does **not** bundle Google's SDK into its published
klib the way RevenueCat 3.x bundles its own native SDK (see "RevenueCat
on iOS: PROVEN WORKING" above) — it genuinely needs the pod linked, same
as the CocoaPods-dependent RevenueCat versions that were ruled out
earlier in this project. Its own build declares
`cocoapods { pod("Google-Mobile-Ads-SDK") { version = "13.8.0" } }`
(Kotlin `2.4.10`) — checked directly against its `build.gradle.kts` and
Maven Central Gradle module metadata (only `androidJvm` +
`ios_arm64`/`ios_simulator_arm64` variants published, no `jvm()` desktop
variant, so it can't go in `commonMain` in a module that also targets
`jvm()` — same lesson as `purchases-kmp-core` 3.x elsewhere in this file).

### Attempt 2: add `org.jetbrains.kotlin.native.cocoapods` + `pod()` declarations — FAILED differently

Bumped `paywall-build` from Kotlin `2.3.20` to `2.4.10` to match
`basic-ads`'s own pin (safe direction — a newer Kotlin/Native compiler can
read an older klib, not the reverse; RevenueCat's `3.6.0` klib, compiled
at `2.3.20`, still needed to keep working). Added the `native-cocoapods`
plugin with `pod("Google-Mobile-Ads-SDK") { version = "13.8.0" }` and
`pod("GoogleUserMessagingPlatform") { version = "3.1.0" }` (matching
`basic-ads`'s own exact pod versions), kept the framework declared
manually via `iosArm64 { binaries.framework { baseName = "PaywallModule" ... } }`
(mirroring `basic-ads`'s own `build.gradle.kts`, which combines both
approaches).

Real progress this time — `podSetupBuildGoogle-Mobile-Ads-SDKIosSimulator`,
`podBuildGoogle-Mobile-Ads-SDKIosSimulator`, and
`cinteropGoogleMobileAdsIosSimulatorArm64` all genuinely executed
(CocoaPods fetched and built the real SDK, Kotlin generated real
interop bindings) — but the link step still failed with the **identical**
`ld: framework 'GoogleMobileAds' not found`. The pod existed on disk now;
the linker just didn't know where.

### Root cause — found by reading Kotlin's actual Gradle plugin source, not guessed

Downloaded `KotlinCocoapodsPlugin.kt` and `CocoapodsExtension.kt` straight
from `JetBrains/kotlin` on GitHub. The relevant logic
(`configureLinkingOptions()`):
```kotlin
target.binaries.all { binary ->
    val testExecutable = binary is TestExecutable
    val podFramework = binary is Framework && binary.name.startsWith(POD_FRAMEWORK_PREFIX)
    if (testExecutable || podFramework) {
        configureLinkingOptions(project, cocoapodsExtension, binary)  // <- only this adds -F<path>/-framework
    }
}
```
`POD_FRAMEWORK_PREFIX = "pod"` — a name the plugin assigns to **one
specific framework it auto-creates per Apple target** the moment the
plugin is applied (`createDefaultFrameworks()`, unconditional). The
pod's `-F<frameworkSearchPath>` and `-framework <name>` linker args are
**only** ever attached to that framework (or test executables) — never to
an independently-declared `binaries.framework {}`, no matter its
`baseName`. `basic-ads`'s own build combines both, exactly like our
attempt 2 did, so if it's ever actually tested by directly running its
own `linkDebugFramework*` task rather than only publishing the compiled
klib, it would hit the identical gap — this project just happened to be
the one that needed to actually prove the link, not just compile.

### Attempt 3: configure the plugin's own auto-created framework instead — SUCCEEDED

Removed the manual `iosArm64`/`iosSimulatorArm64` `binaries.framework {}`
blocks. Moved the same config (`baseName = "PaywallModule"`,
`-Xbinary=bundleId=...`, the per-target `swiftLibPath` linker fix already
established for RevenueCat above) into `cocoapods { framework { ... } }`,
which — confirmed from `CocoapodsExtension.kt`'s `framework(configure)` →
`forAllPodFrameworks` — **reconfigures the plugin's already-auto-created,
correctly-wired framework in place**, not a third competing one. Since
that single block runs once per Apple target, branched
`iphoneos`/`iphonesimulator` via the `Framework`'s own `.target.name`
(confirmed real: `binary.target` is the identical property
`configureLinkingOptions()` itself reads).

Result: `:paywall-build:linkDebugFrameworkIosSimulatorArm64` — **`BUILD
SUCCESSFUL in 6m 39s`, 25/25 tasks executed, zero link errors.** Only
output was the already-known-benign RevenueCat module-cache `.pcm`
warnings documented in the RevenueCat section above (unrelated,
pre-existing, cosmetic).

### Exact working config (`paywall-build/build.gradle.kts`)

```kotlin
plugins {
    kotlin("multiplatform") version "2.4.10"
    id("org.jetbrains.kotlin.native.cocoapods") version "2.4.10"
    // ... existing plugins
}
kotlin {
    cocoapods {
        ios.deploymentTarget = "15.0"
        noPodspec()  // embedded into ios-shell/ as a plain .framework, never consumed via a Podfile itself
        pod("Google-Mobile-Ads-SDK") { moduleName = "GoogleMobileAds"; version = "13.8.0"; extraOpts += listOf("-compiler-option", "-fmodules") }
        pod("GoogleUserMessagingPlatform") { moduleName = "UserMessagingPlatform"; version = "3.1.0"; extraOpts += listOf("-compiler-option", "-fmodules") }
        framework {
            baseName = "PaywallModule"
            freeCompilerArgs += listOf("-Xbinary=bundleId=com.infiltrate.paywallmodule")
            val sdkName = if (target.name == "iosArm64") "iphoneos" else "iphonesimulator"
            swiftLibPath(sdkName)?.let { linkerOpts += listOf("-L$it") }
        }
    }
    iosArm64()
    iosSimulatorArm64()
    // NOT: iosArm64 { binaries.framework { ... } } - the whole point of this section
}
```
`AdMobSpikeUsage.kt` (`paywall-build/src/iosMain/kotlin/`) — real calls to
`BasicAds.Initialize()` and `BannerAd(adUnitId = AdUnitId.BANNER_DEFAULT)`,
same "force the linker to actually resolve it" pattern as
`PaywallUsage.kt`'s RevenueCat call — is what made this a genuine link
test, not a compile of dead-strippable unused code.

### What's still open

- **Android runtime is still untested** — `basic-ads` + `play-services-ads`/
  `user-messaging-platform` compile clean for `:paywall-build`'s Android
  target (`compileDebugKotlinAndroid` succeeds) and the real Android
  `APPLICATION_ID` meta-data is wired (see "Watch ad to continue" below),
  but there is no emulator in this environment and, more fundamentally, no
  Android host Activity anywhere that consumes `paywall-build`'s Compose UI
  at all yet (unlike iOS's `ios-shell/`) — building that is real,
  undone work, not a quick follow-up.
- Only `iosSimulatorArm64` verified — `iosArm64` (real device) untouched,
  same caveat as every other iOS spike in this file.
- `AdMobVerifyScreen.kt`/`AdMobVerifyBridge` (the spike code proven above)
  is still throwaway — it deliberately stays on `AdUnitId.BANNER_DEFAULT`
  (Google's test constant), never a real ad unit, since it runs
  unattended in CI and a real ad unit there would be invalid traffic.
  The real feature is `ContinueAdBridge`/`ContinueAdTrigger`, documented
  next.

## Watch ad to continue — real feature (2026-09-02)

Real AdMob apps + rewarded ad units created in the AdMob console (not test IDs):

| | App ID | Rewarded ad unit ID |
|---|---|---|
| Android | `ca-app-pub-7912148730700666~8824437805` | `ca-app-pub-7912148730700666/8683118378` |
| iOS | `ca-app-pub-7912148730700666~1768074863` | `ca-app-pub-7912148730700666/9506964083` |

Wired in: `paywall-build/src/androidMain/AndroidManifest.xml` (App ID meta-data),
`ios-shell/project.yml` (`GADApplicationIdentifier`, real App ID — safe to use even in the CI
spike, unlike a real *ad unit* ID: the App ID alone doesn't cause invalid-traffic risk),
`AdUnitIds.kt`/`.android.kt`/`.ios.kt` (`expect/actual`, `paywall-build/src/*Main/kotlin/`) —
real ad unit ID per platform, used **only** by the real feature below, never by the CI spike.

### Why the ad can't be triggered directly from KorGE

`:game` (the KorGE module) is locked to Kotlin `2.0.20` and has no Compose runtime; `basic-ads`
needs `2.4.10`+ and is only wired into `paywall-build`'s Compose layer — that's the entire reason
`paywall-build` exists as an isolated composite build (see "RevenueCat on iOS" above). So
`GameplayScene.kt` (common KorGE code) cannot call `RewardedAdHandler`/`RewardedAd` directly; the
request has to cross from `GameMain.framework` to `PaywallModule.framework` via native Swift,
since those are two separately-compiled Kotlin/Native frameworks with no direct interop.

Checked `basic-ads`' actual iOS source (`RewardedAdHandler.kt` on
[LexiLabs-App/basic-ads](https://github.com/LexiLabs-App/basic-ads)) before wiring anything:
`show()` calls `GADRewardedAd.presentFromRootViewController(rootViewController = null, ...)` —
it presents over whatever the **current** `window.rootViewController` is at call time, not
whichever view controller composed the call. Confirms the ad can only present reliably once the
shell has actually swapped to the Compose scene (same reasoning that killed the "second
ComposeUIViewController" approach in the AdMob spike above) — triggering it while KorGE is still
the visible root, hoping Compose's detached scene keeps running invisibly, isn't a safe bet.

### Architecture (poll-based bridges, same pattern as `SpikeBridge`/`AdMobVerifyBridge`)

```
GameplayScene (KorGE)  --[requestContinueAd]-->  GameContinueAdBridge (:game, src@ios, real)
                                                          |
                                                   polled by AppDelegate.swift
                                                          |
                                              switchToCompose() + ContinueAdTrigger.requestShow()
                                                          |
                                          ContinueAdContent() composes RewardedAd(...) (paywall-build)
                                                          |
                                          onRewardEarned -> ContinueAdTrigger.markRewardEarned()
                                                          |
                                                   polled by AppDelegate.swift
                                                          |
                                    GameContinueAdBridge.grantContinue() + switchToKorGE()
                                                          |
                        GameplayScene's own updater sees consumeContinueGranted() -> restarts
                        the level the same way the existing "RETRY INFILTRATION" button already does
```

Two separate bridge objects, not one, because `:game` and `paywall-build` are separate Kotlin/
Native frameworks — Swift is the only thing that can see both:
- `GameContinueAdBridge` (`src/ContinueAdBridge.kt` common `expect`, `src@ios/ContinueAdBridge.ios.kt`
  real actual, `src@android`/`src@jvm`/`src@js`/`src@wasmJs` deliberate no-op actuals matching the
  existing `PurchasesBridge` pattern — `consumeContinueGranted()` must return `false`, never `true`,
  on every platform without a real ad actually watched)
- `ContinueAdTrigger` (`paywall-build/src/iosMain/kotlin/ContinueAdBridge.kt`) — `showRequested` is
  a real Compose `MutableState<Boolean>` (not a plain var) so `ContinueAdContent()` recomposes when
  Swift sets it; `consumeOutcomeFinished()` lets Swift react the instant the ad is dismissed/fails,
  instead of always waiting out the 30s fallback timeout

### What changed in the real gameplay UI

`GameplayScene.kt`'s existing "MISSION FAILED" (`caughtOverlay`) game-over card gets a third
button, **CONTINUE (WATCH AD)**, above the existing RETRY INFILTRATION / RETURN TO MENU (which
keep working independently — tapping CONTINUE only requests the ad, it doesn't hide or disable
the other two, so a failed/declined ad never strands the player). For now, watching the ad to
completion just restarts the current level (`sceneContainer.changeTo { GameplayScene(levelData) }`)
— same as RETRY, just gated behind `getContinueAdBridge().consumeContinueGranted()` in the update
loop, per the user's explicit "for now even if they play the ad just restart the game" scope.

### Verified so far / not yet

- `compileKotlinJvm` (root `:game`, common code + JVM actual) — **BUILD SUCCESSFUL**.
- `paywall-build:compileDebugKotlinAndroid` — **BUILD SUCCESSFUL** (real Android App ID/ad unit
  ID compile clean).
- `:game`'s own `compileDebugKotlinAndroid` — **not verified locally**: blocked by a pre-existing,
  unrelated `korge-ldtk` JVM-toolchain-mismatch error in this environment (fails before reaching
  `:game`'s own Android compile at all) — not caused by this change; the Android actual
  (`src@android/ContinueAdBridge.android.kt`) is a trivial no-op stub with no Android-specific API
  surface, same shape as the already-working `PurchasesBridge` Android stub.
- **iOS: not verified at all locally** — this machine has no Mac/Xcode. The Swift changes
  (`AppDelegate.swift`: `showContinueAd()`, extended `startObservingLevelEnd()` poll) and the new
  `paywall-build`/`:game` iOS Kotlin files have not been compiled or run. Needs a CI push to
  confirm, same as every other iOS change in this file — do not treat this as working until a
  real CI run proves it, per this file's own "verify version-related claims" rule above.
- Not yet tested: what happens if the player backgrounds the app mid-ad, or if `RewardedAd`'s
  `AdState.READY` never resolves within the 30s Swift poll deadline on a slow connection (falls
  back to `switchToKorGE()` without granting — the level just isn't restarted, player can still
  use RETRY).

## RevenueCat version is pinned by iOS klib ABI compatibility, not just Android/JVM metadata

**Note: the section below documents `:game`'s own separate,
still-unresolved `purchases-kmp-core:1.9.0+14.3.0` pin (Android-only,
kept for historical/reference purposes) — a completely different
dependency line from the proven-working `3.6.0` in `paywall-build`
above. Don't conflate the two: this history is about why `:game` itself
is stuck on an old RevenueCat version; the section above is about a
separate, isolated module using a much newer one successfully.**

- `build.gradle.kts` pins `purchases-kmp-core` to `1.9.0+14.3.0` for
  `androidMainApi` only now (`:game` itself still has zero iOS dependency
  on RevenueCat at all — see note just above). This was downgraded from
  `2.10.2+17.55.1`, which itself was a downgrade from `3.5.1` — see the
  Android/JVM metadata-conflict note already in this file. The version
  history below is kept for whoever revisits iOS.
- The `2.10.2+17.55.1` downgrade fixed Android/JVM but a separate,
  iOS-only problem showed up in `ios-build.yml` CI: `:compileKotlinIosSimulatorArm64`
  failed with a KLIB resolver error — "Incompatible ABI version. The
  current default is '1.8.0', found '1.201.0'".
- Root cause, confirmed by downloading klib files from Maven Central
  and reading their manifests directly (`unzip -p <klib> default/manifest`,
  look for `abi_version` / `compiler_version`) rather than guessing:
  RevenueCat's own build toolchain moved from Kotlin 1.9.23 (klib
  `abi_version=1.8.0`) to Kotlin 2.1.x (`abi_version=1.201.0`) starting
  exactly at their package version `2.0.0+15.0.0`. This project's
  Kotlin/Native compiler can only read klib ABI 1.8.0. Every version
  from `2.0.0+15.0.0` up through `2.10.2+17.55.1` is therefore permanently
  unreadable here — this is a hard compiler wall, not something a Gradle
  flag fixes. `1.9.0+14.3.0` is the newest release still on the
  compatible side.
- Android/JVM consume regular JAR/AAR artifacts, not klibs, so this
  specific ABI check doesn't apply there — the earlier Android/JVM
  metadata conflict (3.5.1 vs project's Kotlin metadata level) was a
  different, unrelated check. Downgrading further should only reduce
  that risk, not increase it.
- Consequence for CocoaPods: once a `Podfile` exists (see next section),
  the pod pin should be `PurchasesHybridCommon 14.3.0` (matching
  `1.9.0+14.3.0`'s paired native SDK version), NOT `17.55.1` as earlier
  project notes said — that was written against the since-reverted
  `2.10.2+17.55.1` pin.
- If `purchases-kmp-core` needs to be bumped again in the future (e.g.
  after upgrading KorGE/Kotlin off 1.9.x), re-verify klib ABI
  compatibility the same way before assuming a newer version "should"
  work — don't rely on Android/JVM compiling cleanly as a proxy for iOS
  compatibility, they're checked completely differently.
- **Already tried and confirmed failing (2026-08-24/25): `3.5.1`.** The
  hope was that 3.x's iOS SDK is bundled directly into the klib instead
  of needing CocoaPods (true — its klib `depends` list includes
  `kn-core-cinterop-RevenueCat`/`kn-core`, a natively-bundled SDK), which
  would eliminate the CocoaPods gap below entirely if it worked. It
  doesn't: klib manifest shows `abi_version=2.3.0` (compiler `2.3.20`) —
  an even bigger gap than `2.10.2`'s `1.201.0`. Confirmed on CI: fails at
  `:compileKotlinIosSimulatorArm64` itself (never even reaches the link
  step). Don't re-try any `3.x` version without first re-checking its
  klib manifest — this whole line is Kotlin-2.x-compiled and none of it
  will read as ABI 1.8.0 under this toolchain.
- The bridge classes (`src/PurchasesBridge.kt` and platform variants)
  are still empty stubs with no real RevenueCat API calls wired in, so
  this version is currently unconstrained by actual usage — check the
  `1.9.0+14.3.0` API surface against the current RevenueCat KMP docs
  before writing real integration code against it, since it's several
  major versions behind latest.

## iOS build pipeline — status and known gap

(This CocoaPods gap applies specifically to `:game`'s own pinned
`purchases-kmp-core:1.9.0+14.3.0` line — see "RevenueCat version is
pinned by iOS klib ABI compatibility" above. It does NOT apply to the
`3.x` line proven working in the isolated `paywall-build` composite
build — see "RevenueCat on iOS: PROVEN WORKING" further above, which
confirmed empirically that `3.x` needs zero CocoaPods/Podfile since it
bundles its native SDK directly into the klib. Kept in full below since
the `ios-build.yml` workflow itself is still active and this remains
accurate background for `:game`'s own current dependency.)

- KorGE's `targetIos()` has its own iOS build pipeline, confirmed via
  https://docs.korge.org/targets/ios/ — it generates a full Xcode
  project under `build/platforms/ios` via Gradle tasks like
  `iosBuildSimulatorDebug` / `iosInstallSimulatorDebug`. This is
  separate from, and does not use, the standard Kotlin Multiplatform
  `cocoapods {}` Gradle plugin — nothing in KorGE's own docs mentions
  CocoaPods at all.
- There is currently NO `Podfile` anywhere in this repo, and
  `build.gradle.kts` has no `cocoapods {}` block. RevenueCat's
  `purchases-kmp-core:1.9.0+14.3.0` needs `pod 'PurchasesHybridCommon',
  '14.3.0'` linked into the final iOS app/framework for iOS to actually
  work — this is currently unresolved. It is not yet known whether
  KorGE's generated Xcode project has any hook for injecting a Podfile,
  or whether one needs to be hand-authored and copied into
  `build/platforms/ios` post-generation.
- **Confirmed empirically (2026-08-24 CI run, commit `fd46ed6`) exactly
  where this breaks** — it's the final native link step, not the Kotlin
  compile step:
  - `:compileKotlinIosSimulatorArm64` succeeds.
  - KorGE generates the Xcode project fine (`build/platforms/ios/app.xcodeproj`
    via XcodeGen 2.42.0).
  - `:linkDebugFrameworkIosSimulatorArm64` FAILS with:
    `ld: framework 'PurchasesHybridCommon' not found`
  - This is the CocoaPods gap, now confirmed rather than theorized. Next
    step here is almost certainly authoring a `Podfile` (pinning
    `PurchasesHybridCommon 14.3.0`) and getting it into
    `build/platforms/ios` before this link step runs — exact mechanism
    (KorGE hook vs. a CI step that copies one in post-XcodeGen) still
    unresearched.
  - Also noted in that run: `Xcode 26.6 is higher than the maximum
    tested by the Kotlin Gradle Plugin (15.3)` — a warning only so far,
    not a failure, but worth remembering if something flaky shows up
    later on Apple-toolchain-version grounds.
- Earlier `:compileKotlinIosSimulatorArm64` failures along the way (now
  fixed, for reference): a klib ABI mismatch (see previous section) and
  a "Conflicting overloads: actual fun getPurchasesBridge()" error
  caused by `src@native/PurchasesBridge.native.kt` and
  `src@ios/PurchasesBridge.ios.kt` both providing an `actual` for the
  same `expect`. Root cause: KorGE uses a custom source-set hierarchy
  (`kotlin.mpp.applyDefaultHierarchyTemplate=false` in
  `gradle.properties`) where `nativeMain` (`src@native`) is the shared
  parent for all Kotlin/Native leaf targets and `iosMain` (`src@ios`)
  sits below it; since iOS is the only real Kotlin/Native target enabled
  here, both fed `iosSimulatorArm64Main` directly. Fixed by deleting
  `src@native/PurchasesBridge.native.kt` (fully redundant, no other
  native target exists to need it). If a genuine non-iOS native target
  gets added later, that's when `src@native` would need to come back.
- Check the latest Actions run before assuming any of this is stale:
  https://github.com/MalithaBandara/infiltrate-shadow-heist/actions
- Do not assume this is solved just because CI is green on other steps —
  confirm the `iosBuildSimulatorDebug` step itself succeeded and check
  whether the Podfile-search step actually found anything.

## Gameplay Architecture (`commonMain`)

The gameplay logic is decoupled from the rendering engine:
- `game.model`: Engine-agnostic domain layer containing pure data models and simulation math:
  - `Geometry.kt`: `Vec2d`, `Rect`, `Segment2d`, raycasting, angle calculations, line-of-sight checks against occluder rects.
  - `Player.kt`: Full-body player physics (height 96px and width 50px matching the full-stride silhouette to prevent leg and upper body wall clipping), velocity, jumping, gravity, platform snapping, sub-stepped AABB obstacle & entity collision resolution, `NoiseLevel` (SILENT vs NORMAL walk noise radius 180px), and crouching stance (56px crouch height for crawling under low obstacles, reduced speed, silent movement).
  - `Guard.kt`: Waypoint patrol, constant speed movement, direction turnaround, facing angle, eye position, and `GuardState` (`PATROL` vs `INVESTIGATING` with stationary look towards sound direction or visual detection, without moving toward the player, and timeout return to original patrol route).
  - `Vision.kt`: `VisionSystem` for generating FOV vision polygon meshes and detecting player visibility (distance, FOV angle, unoccluded line-of-sight, closest spotted distance).
  - `LevelData.kt`: `LevelData`, `LevelResult` (3-star rating evaluation: completed, undetected, time target), scalable `LevelRegistry` (`DEFAULT_LEVELS`), and `LevelStorage` repository interface with `InMemoryLevelStorage` and `MapBackedLevelStorage`.
  - `GameProfile.kt`: `GameProfile` (coins balance, premium bundle status, music & SFX volume, unlocked levels, tactical powerup inventory), with `GameProfileStorage`, `InMemoryGameProfileStorage`, and `MapBackedGameProfileStorage`.
  - `GameWorld.kt`: Orchestrates player, guard entity collision, platforms, crates/occluders, stopping guard movement upon player detection in vision cone, distance-scaled detection progress (0.3s point-blank to 1.5s max range), alert decay (0.6/s), noise event detection triggering guard investigation, visual loss mid-alert triggering guard investigation, exit zone win condition, level time tracking, and game-over/caught event triggers.
- `game.scene`: KorGE gameplay presentation layer:
  - `UiComponents.kt`: UI constants and GPU vector rendering utilities (`uiGraphics`, `drawStar`, cyber theme colors) used across `GameplayScene`.
  - `PlayerAnimations.kt`: Frame-accurate sprite sheet animations for operative actions (idle, run, jump, sneak/crouch, vault/climb).
  - `GameplayScene.kt`: Core gameplay engine. Immersive stealth parkour level styled with modern AAA stealth mobile UI matching the Compose Multiplatform cyber-tactical theme (`BebasNeue` typography, `COLOR_DARK_BG`, cyan/gold/green/red neon accents, GPU vector rendering). Features zoomed character action (`worldZoom = 1.35x` centering operative and obstacles), fixed vertical ground alignment (`worldView.y = canvasH - (baseGroundY + 70.0) * worldZoom`), unzoomed atmospheric looping background layer (`bgmg2.png` post-processed with electric cyan/steel-blue duotone color grading at native 480px height scrolling with 0.2x horizontal parallax), lowered ground floor level (`groundY = 410.0` maximizing playable vertical space), integrated game silhouette assets (`crate.png`, `chainedcrate.png`, and `chainedhook.png` tightly cropped to exact visual bounds without transparent offsets), flat solid black silhouette ground and structural platforms, Level 1 sequence featuring initial ground running approach (x = 60..580), half-height step crate (48px high, 68px wide) at x = 580 to climb up onto a long elevated structural platform (96px high, length 900px), overhead hanging chained crate at x = 1050 (clearing 54px above platform) requiring operative to crouch-walk underneath, atmospheric hanging chained hook at x = 1380, solid architectural terrain blocks across the 3200px corridor, far-corner guard patrol and security camera coverage above extraction beacon, ground-aligned character sprite anchoring (calibrated feet contact line eliminating floating gap), modern translucent GPU vector-rendered mobile touch controls (Left/Right vector chevrons, Jump/Vault arrow, Sneak/Crouch arrow with glowing neon touch states), floating translucent top HUD with sleek rounded stealth radar capsule, live threat & detection radar bar, stance indicator, formatted stopwatch pill (`mm:ss.t`), modern 36x36 vector pause icon button, dynamic floating powerup quick-dock, and themed modal overlays (Tactical Pause, Mission Failed with recon tips, Level Complete with 3-star rating reveal & 2x multiplier coin bounty rewards). All debug cheat codes removed for clean production builds. All non-gameplay screens (MainMenu, LevelSelect, Store, Settings, Paywall) are fully delegated to Compose Multiplatform.

## Non-Gameplay UI Migration to Compose Multiplatform (2026-09-01) — MainMenu Ported

**Status: IN PROGRESS — MainMenu ported to Compose Multiplatform (`paywall-build`), LevelSelect/Store/Settings stubbed as placeholder navigation targets.** KorGE (`:game`) becomes gameplay-only, entered when starting a level via `window.rootViewController` swap on iOS (and warm view-swap candidate on Android).

### 1. Model Sharing across Gradle Composite Builds (`srcDir`)
- **Finding**: Composite build dependency substitution (`implementation("com.sample.demo:korge-hello-world")`) failed because Gradle included builds cannot resolve parent build artifacts without explicit Maven publishing.
- **Mechanism**: `src/game/model/` is 100% engine-agnostic standard Kotlin library (`kotlin.math`). Adding `kotlin.srcDir("../src/game/model")` in `paywall-build/build.gradle.kts` allows `paywall-build` (Kotlin `2.3.20`) to compile `GameProfile.kt`, `LevelData.kt`, `Geometry.kt`, `Powerup.kt`, etc. directly from source alongside `:game` (Kotlin `2.0.20`).
- **Standing Constraint**: All files under `src/game/model/` must remain pure Kotlin (standard library only) and compile cleanly under **both** Kotlin 2.0.20 and 2.3.20. Zero imports of `korlibs.*` or engine-specific types are permitted. Guarded by an automated test (`ZeroKorlibsLintTest`).

### 2. Exact Storage Keys & Zero-Drift Persistence Bridge
- Because `paywall-build` compiles `src/game/model` from source, it directly instantiates `MapBackedGameProfileStorage` and `MapBackedLevelStorage`.
- Discrete storage keys persisted:
  - `user_coins`: String-formatted Int (e.g. `"100"`, `"350"`)
  - `user_is_premium`: String-formatted Boolean (`"true"` / `"false"`)
  - `user_music_vol`: String-formatted Float (e.g. `"0.8"`)
  - `user_sfx_vol`: String-formatted Float (e.g. `"1.0"`)
  - `user_controls_swapped`: String-formatted Boolean (`"true"` / `"false"`)
  - `user_language`: String-formatted language code (e.g. `"en"`)
  - `user_unlocked_levels`: Semicolon-delimited level IDs (e.g. `"level_1;level_4"`, `"level_1;level_2;level_4"`)
  - `user_powerups`: Semicolon-delimited `id:count` pairs (e.g. `"smoke_screen:2;phantom_cloak:2"`)
  - `level_result_<levelId>`: CSV formatted `"$levelId,$completed,$wasDetected,$timeTaken,$timeTargetSeconds"`
- On iOS: `PlatformStorage` delegates to `PaywallStorage`, which uses `NSUserDefaults(suiteName = "korge")` and key prefix `"org.korge.storage."`, matching KorGE's `DarwinNativeStorage` byte-for-byte.

### 3. Android Warm Engine Symmetry (Candidate Architecture — Unspiked)
- **Status**: Android Compose wiring is **out of scope for this pass** and deferred to a dedicated Android pass.
- **Architecture Note**: Multi-Activity (`startActivity`/`finish`) would destroy and recreate the KorGE Activity on every level transition, forfeiting the warm resident engine property proven on iOS (~60–120ms warm swap).
- **Candidate for Future Spike**: Single-Activity with a parent `FrameLayout` holding both `ComposeView` and KorGE's `KorgwSurfaceView` (or `GLSurfaceView`), toggling `visibility = View.VISIBLE` vs `View.GONE` to pause the OpenGL render thread without engine destruction.

### 4. iOS Shell Architecture (`ios-shell/Sources/AppDelegate.swift`)
- `MainMenuComposeScreen.shared.makeViewController(onStartLevel:)` (exported via `@ObjCName(name = "MainMenuComposeScreen", exact = true)`) serves as the initial `window.rootViewController`.
- Tapping "PLAY" swaps `window.rootViewController` to `self.korgeVC` (the warm resident KorGE GLKViewController).
- When a level ends, `AppDelegate` detects the event and swaps `window.rootViewController` back to `self.composeVC`.
- On launch, `AppDelegate` runs on-device storage bridge validation against real profile fields and writes `OK:coins=350:unlocked=level_1;level_2;level_4` to `storage_bridge_result.txt` for CI verification.

### 5. Multiplatform Entry Point Rules (`src/main.kt`)
- KorGE's auto-generated iOS bootstrap (`build/platforms/native-ios/bootstrap.kt`) calls `suspend fun main()` with zero arguments.
- `src/main.kt` must always expose a parameterless `suspend fun main() = main(emptyArray())` alongside `suspend fun main(args: Array<String>)`.
- Never use JVM-only APIs such as `java.lang.System.getProperty` in `src/main.kt` or common code; use `korlibs.io.lang.Environment["key"]` or `args.firstOrNull()` which compile across Kotlin/JVM, Kotlin/Native (iOS), and Kotlin/JS.

## Audio: two sound buses, and how assets reach each platform (2026-09-03)

The game has **two independent audio systems**. They share no code and load files by
different mechanisms, so a sound added to one is not available to the other.

1. **Gameplay (`:game`, KorGE)** — `src/game/scene/GameAudio.kt`. Loads `sfx/*.wav` through
   `resourcesVfs`, i.e. out of `resources/sfx/`. A gain table (`STEP_GAIN`, `TAKEOFF_GAIN`,
   `LANDING_GAIN`, `CLIMB_GAIN`, `CROUCH_GAIN`, `UI_CLICK_GAIN`, `HUD_TAP_GAIN`) sets relative
   levels on top of the player's SFX volume. Missing clips are a silent no-op, never a crash -
   which means a bundling failure is invisible rather than loud. Keep that in mind when a sound
   "doesn't work": check the file reached the bundle before suspecting the code.
2. **Menus (`paywall-build`, Compose)** — `ui/MenuSfx.kt`, an `expect`/`actual` following the
   same per-platform pattern as `MenuMusic`/`LoopingVideoBackground`. Delivered to screens
   through `LocalUiClick`, a CompositionLocal provided once in `NavigationRoot` (so the voice
   pool is not rebuilt on every navigation), defaulting to a no-op so previews stay silent
   instead of failing. Uses the overlap-capable API on each platform - a 4-voice `AVAudioPlayer`
   pool on iOS, `SoundPool` on Android, JavaFX `AudioClip` on desktop - because one player
   restarted cuts the previous tap off mid-sound.

### Format: WAV, not OGG

Everything must be **PCM s16le / 44.1 kHz / mono**. This is the only format all four consumers
accept: KorGE's `readSound`, iOS `AVAudioPlayer`, Android `SoundPool`, and JavaFX. **iOS and
JavaFX cannot decode Ogg Vorbis at all**, so a CC0 pack downloaded as `.ogg` (Kenney ships Ogg)
must be converted before use, or the menu bus is silent on two of three platforms.

### Where each platform reads the click from

`ui_click.wav` is deliberately checked in **twice**, mirroring how `mainmenu.mp3` already is:

- `resources/sfx/ui_click.wav` — KorGE gameplay (all platforms), and the desktop menu bus,
  which looks for `resources/sfx/…` relative to the working directory.
- `ios-shell/Resources/ui_click.wav` — the iOS menu bus, read via `NSBundle`. `project.yml`
  already copies that whole directory as a resources build phase.
- Android's menu bus finds it at `assets/sfx/ui_click.wav`, which is where KorGE's Gradle plugin
  copies `resources/` - no `res/raw` copy needed, though `res/raw` is tried first.

### Update (2026-09-05): a second menu clip mechanism, and credits are now real

`MenuSfx.kt` gained `MenuClip`/`rememberMenuClip`/`LocalToastSuccess`/`LocalToastError` alongside
the original click-only `LocalUiClick` — same per-platform pooled-player pattern, but keyed by clip
name so more than one non-click sound can be added without touching the already-verified click
path. Used for `toast_success.wav`/`toast_error.wav` (owner-supplied, from Freesound, CC0), wired
into `showToast()` in both `SettingsScreen.kt` and `StoreScreen.kt`. Settings → About →
"CREDITS & LICENSES" was a placeholder toast; it now expands to a real list (`SOUND_CREDITS` in
`SettingsScreen.kt`) naming every third-party sound, kept in sync with `ATTRIBUTION.md` by hand.
Also resolved: `mainmenu.mp3`'s licence, previously unrecorded — the owner's copy was
byte-identical (MD5-verified) to the one already shipping, credited to Nikita Kondrashev via
Pixabay.

### KNOWN GAP: `resources/` never reaches the iOS shell bundle

`ios-shell/project.yml` copies `ios-shell/Resources` and `paywall-build`'s compose-resources,
**and nothing else**. There is no path for the repo's `resources/` directory into the app bundle,
and every gameplay asset - sprites, backgrounds, the Bebas font, all of `sfx/` - is loaded
through `resourcesVfs`, which resolves against the bundle. So on the iOS shell build, gameplay
audio and gameplay art are expected to be missing. `GameplayScene` catches each load and falls
back to `null`/vector drawing, so this degrades silently rather than crashing, which is probably
why it has gone unnoticed. **Unverified on-device** - confirm against a CI run before acting, but
do not assume gameplay audio works on iOS until this is fixed. The likely fix is one more folder
reference in `project.yml`; it was deliberately not applied without the owner's go-ahead, since
that file is part of the hard-won working iOS config.

## Compose Resources package is derived from the project `group` — trap (2026-09-03)

`paywall-build/build.gradle.kts` sets `group = "com.infiltrate"` (added so `android-shell` can
reference this module's Android artifact through the composite-build substitution). Compose
Resources derives the generated `Res` class's package from the project `group` when
`packageOfResClass` is not set, so adding that line silently moved the generated package from
`paywall_build.generated.resources` to `com.infiltrate.paywall_build.generated.resources`, while
all five UI files still imported the short one. Result: `compileKotlinJvm` failed across
`MainMenuScreen`, `LevelSelectScreen`, `MenuComponents`, `SettingsScreen` and `StoreScreen` with
`Unresolved reference 'paywall_build'` — nothing to do with the code being edited at the time.

Fixed by pinning the package explicitly rather than rewriting five files' imports:

```kotlin
compose.resources { packageOfResClass = "paywall_build.generated.resources" }
```

**If `group` is ever changed again, this pin is what stops the generated package moving with it.**

## RESOLVED: `android-shell/` root-build configuration clash (2026-09-03)

Briefly, `android-shell/` was included by `settings.gradle.kts` as a subproject while applying
`org.jetbrains.compose` 1.12.0, which requires **Kotlin >= 2.2.0**. The root build is on
**Kotlin 2.0.20**, locked to KorGE 6.0.0, so configuration failed for *any* root-build task:

```
Failed to apply plugin 'org.jetbrains.compose'.
  > Configuration problem: Minimal supported Kotlin Gradle Plugin version is 2.2.0
```

This was masked for a while by a stale configuration-cache entry created before the module
existed; once that cache was invalidated, every root task failed at configuration.

Resolved by making `android-shell/` its own fully separate Gradle build (the `include(":android-shell")`
line is gone from `settings.gradle.kts`), consuming `paywall-build`'s Android artifact through
`mavenLocal()` rather than a subproject/composite dependency - the same isolation `paywall-build`
itself already relies on. Recorded here because the symptom (every root task failing to configure
on a plugin the task has nothing to do with) is confusing enough to be worth recognising if a
future module reintroduces it.

## RESOLVED (unverified on-device): Compose UI polish pass - icon sizing, scrolling, dossier card (2026-09-05)

Real on-device screenshots flagged four issues across `paywall-build`'s Compose screens
(`ui/*.kt`). All fixed; none re-verified on-device yet.

- **Icons rendering much smaller than their containers** (star/lock in Missions, power-up icons
  and the ABOUT tab icon in Store/Settings). Root cause, found by reading how the affected
  `DrawScope.drawXIcon()` functions in `MenuComponents.kt` draw their shapes: most of them plot
  fixed literal pixel coordinates (a radius of `7f`, an offset of `8f`) with no relation to the
  Canvas's actual size. `DrawScope.size` is the Canvas's real rendered pixel size, which tracks
  device density - a `Modifier.size(16.dp)` Canvas is 16px only at density 1 (roughly a JVM/
  desktop preview); on a real phone at density ~2.5-3x it's 40-48px, so a shape whose points never
  move past radius 7-9 keeps occupying the same small patch in the middle of a box that grew
  around it. Confirmed this wasn't a universal bug: `drawInkPlay`/`drawInkTarget`/`drawInkCart`/
  `drawInkGear` (`MainMenuScreen.kt`, the PLAY/MISSIONS/STORE/SETTINGS button icons) and
  `drawCoinIcon`/`drawCoinStackIcon`/`drawGearIcon` (`MenuComponents.kt`) already computed a
  `size.width`-relative scale factor internally, which is exactly why the main-menu buttons and
  coin icons were never part of this complaint. Fixed the rest (`drawBackChevron`, `drawStar`,
  `drawLockIcon`/`drawPadlockIcon`, `drawBoltIcon`, `drawSmokeIcon`, `drawCloakIcon`,
  `drawInvisIcon`, `drawBootIcon`, `drawInfoIcon`) the same way: each now derives a
  `size.minDimension / REFERENCE_PX` multiplier and scales every literal by it, where
  `REFERENCE_PX` approximates the box the original literals were eyeballed against - so behavior
  is unchanged at density 1 and scales up correctly on any real device. `drawGlobeIcon`/
  `drawSpeakerIcon`/`drawBriefcaseIcon` were left alone - confirmed unused anywhere via grep, not
  worth fixing blind.
- **No screen scrolled** (`LevelSelectScreen.kt`, `StoreScreen.kt`, `SettingsScreen.kt` - `Main
  MenuScreen.kt`'s button column already had `verticalScroll` from an earlier session). On a short
  landscape phone these screens' `scale = (screenHeight/720.dp).coerceIn(0.75f, 1.4f)` floor of
  0.75 (higher than `MainMenuScreen`'s own already-lowered 0.55 floor - not touched here, out of
  scope for this request) means content can be taller than the viewport with nothing to reach the
  overflow. Fixed by wrapping each screen's scrollable content (Missions' mission grid column,
  Store's sidebar and both grids, Settings' sidebar and both panels) in `Modifier.verticalScroll
  (rememberScrollState())`. This forced a real constraint: `weight()` requires a bounded parent
  height to distribute, which a vertically-scrolling parent never provides (unbounded/infinite),
  so every `Row`/`Spacer` that used to fill remaining space via `.weight(1f)` inside one of these
  now-scrollable containers had to change - grid rows now use `Modifier.height(IntrinsicSize.Min)`
  (sized to their own cards' content) instead of stretching to fill the screen, and the two
  bottom-anchoring spacers in `SettingsScreen.kt` (before Reset Progress, before the version line)
  became fixed-height gaps instead of flexible ones. **This is a real, structural layout change,
  not just "add a scroll modifier"** - on a normal/tall screen, cards and panels now size to their
  content rather than stretching to fill leftover space the way they did before. That should look
  similar to the reference design (the fixed heights approximate what the fill behavior gave at
  the 720dp reference) but was never confirmed against a real device - check the Missions/Store/
  Settings screens don't look noticeably more cramped than the reference screenshots before
  considering this fully settled.
- **Blue "EN" circular badge next to LANGUAGE in Settings** - decorative `Box`+`Text("EN")` that
  added no information the pill to its right ("● ENGLISH") didn't already show. Removed outright
  from `GeneralSettingsPanel` in `SettingsScreen.kt`, not just hidden.
- **Main menu's mission dossier card**: too much empty space between the file number/rule at the
  top and the mission text below it, and the card itself sitting higher than the screen's bottom
  edge allows. Both are in `MainMenuScreen.kt`'s `MissionDossierCard`: the flexible gap above the
  chapter/title text was `Spacer(Modifier.weight(0.15f))` against a trailing
  `Spacer(Modifier.weight(0.85f))` below the briefing - re-split to `0.05f`/`0.95f`, keeping their
  relative proportion but moving nearly all the spare vertical space to the bottom margin (which
  reads as normal note-paper margin) instead of a visible gap under the rule. The card's screen
  position got `.offset(y = (16 * dossierScale).dp)` added on top of its existing
  `.padding(bottom = 8.dp, end = 8.dp)`, scaling with the card itself rather than a flat dp value.
  Both numbers (`0.05f`/`0.95f`, `16 * dossierScale`) are estimates reasoned from the screenshot,
  not measured against a running app - review on-device before trusting them as final.

Confirmed all four fixes compile clean end-to-end: `:paywall-build:compileKotlinJvm`,
`:paywall-build:compileDebugKotlinAndroid`, `:paywall-build:publishToMavenLocal`, and
`android-shell`'s `compileDebugKotlin` (which consumes the republished artifact) all succeeded.
**Compiling is not the same as looking right** - same standing caution as everywhere else in this
file - none of this has been seen running on a device yet.

## RESOLVED: Android watch-ad-to-continue flow (menu flash + grey screen) (2026-09-05)

Real on-device report: watching the test rewarded ad on Android showed the main menu for a few
seconds before the ad played, and after the ad finished the game showed a permanent grey screen
instead of resuming gameplay. Both traced to `MainActivity.kt`'s `showContinueAd()` and its
`View.GONE`/`VISIBLE` toggling of `KorgeAndroidView` - the exact thing the Android handoff doc
flagged as **unverified**: "whether `View.GONE` actually stops `KorgeAndroidView`'s internal
`GLSurfaceView` render thread ... has never been measured." It has now been measured, indirectly,
by hitting the failure it predicted:

- **Menu flash**: `showContinueAd()` set `showingGameplay.value = false` (revealing
  `NavigationRoot()`, the main menu) *before* the rewarded ad was actually ready to show.
  `RewardedAd(...)` (from `basic-ads`, `ContinueAdBridge.android.kt`) is a load-then-show
  composable - confirmed from its own source (`RewardedAd.kt`,
  `if (ad.state == AdState.READY) { ... ad.show { ... } }`) - so it renders nothing at all while
  the ad loads over the network. `ContinueAdContent()` is composed unconditionally in
  `MainActivity`'s `Box` regardless of `gameplayVisible`, so hiding gameplay was never actually
  needed for the ad to load/show - it only exposed the menu underneath for the loading gap.
- **Grey screen**: setting `showingGameplay.value = true` again afterward flips
  `KorgeAndroidView`'s visibility back to `VISIBLE`, but on a real device this did not resume
  rendering. Toggling to `GONE` tears down the GLSurfaceView-backed render surface rather than
  merely pausing it, and coming back to `VISIBLE` doesn't reliably recreate it - and since
  `GameplayScene.kt`'s own `addUpdater` (the loop that calls
  `getContinueAdBridge().consumeContinueGranted()` and restarts the level) stops ticking along
  with everything else while hidden, the level never resumed even once the surface came back.

**Fix** (`android-shell/src/main/kotlin/com/infiltrate/androidshell/MainActivity.kt`): stopped
toggling `KorgeAndroidView`'s visibility at all - it now stays permanently `VISIBLE`/attached, and
the Compose menu draws opaquely on top of it instead of hiding it. `showContinueAd()` no longer
touches `showingGameplay` in any way; it only calls `ContinueAdTrigger.requestShow()` and polls for
the outcome, letting the ad's own full-screen Activity cover whatever's already on screen once it
loads. This resolves both symptoms structurally (the render surface is never destroyed, so there's
nothing to fail to resume) rather than papering over either one individually.

**Verified**: `android-shell` `compileDebugKotlin` succeeds. **Not yet re-verified on-device** -
same "verify the actual behavior, not just that it compiles" discipline as everywhere else in this
file; confirm the ad now plays immediately and gameplay resumes correctly on a real device before
considering this fully closed.

**New unmeasured trade-off introduced by this fix**: KorGE's render loop now keeps running
whenever the Compose menu covers it (never paused), unlike iOS where the switch-spike measured
`window.rootViewController` swaps to genuinely stop frames while hidden. Whether this matters for
battery on Android is unmeasured - flagged, not fixed, same as the render-thread question it
replaces.

## RESOLVED: `takeoff.wav` removed - was noise, not a jump sound (2026-09-05)

Real on-device report: every jump played something resembling TV static instead of a takeoff
sound. Root-caused (see the original diagnosis this replaces, kept in git history) to
`resources/sfx/takeoff.wav` itself being bad content, not a decoder or code bug - confirmed two
ways: `korlibs.audio.format.WAV`'s parser correctly skips the `LIST`/`INFO` metadata chunk ffmpeg
had written into the file (chunk offsets/sizes walked by hand, all structurally valid), and the
decoded PCM envelope (RMS/peak sampled across the clip) showed sustained high energy for the
entire 190ms with no attack-decay shape anywhere - the signature of broadband noise, not a foley
transient, unlike `step_a.wav`/`impact.wav` which both show a normal sharp-attack-then-decay shape.
This lines up with the file's own removed doc comment: the push-off was deliberately cut as a very
quiet clip (~34 dB under the landing) and boosted back up with a gain multiplier - if the actual
recorded segment was mostly the source's noise floor rather than real foley, boosting it 34 dB
produces exactly this kind of static.

**Fix**: removed entirely, not muted - `takeoff`/`TAKEOFF_GAIN` deleted from `GameSounds`/
`GameAudio.kt`, the `sounds.takeoff.playSfx(...)` call removed from `GameplayScene.kt`'s jump-launch
branch, and `resources/sfx/takeoff.wav` deleted (nothing else referenced it - checked). Jumping is
silent on launch now (the landing sound is unaffected). A real replacement would need a fresh cut
from `jump_new.mp4`, listened to before it goes back into `GameAudio.kt`.

## RESOLVED (mitigated, unverified on-device): delay between landing and the landing sound (2026-09-05)

Real on-device report, same session as the takeoff fix: a perceptible delay between the player
landing and `impact.wav` actually playing. Traced by decompiling `korlibs-audio-core-android-6.0.0`
(no source jar published for the Android target, same situation this file has hit before for other
KorGE Android internals) rather than guessing:

- `SoundAudioData.play()` (`korlibs.audio.sound`, the non-streaming `Sound` implementation
  `GameAudio.load()`'s clips use) calls `soundProvider.createNewPlatformAudioOutput(...)`
  **unconditionally on every single `.play()` call** - there is no channel/output reuse at this
  API level in KorGE 6.0.0.
- On Android, `AndroidNativeSoundProvider.createNewPlatformAudioOutput(...)` constructs a **brand
  new `android.media.AudioTrack`** every time it's called (decompiled directly:
  `new AudioTrack(AudioAttributes, AudioFormat, bufferSize, MODE_STREAM, sessionId)` on API 21+,
  the legacy 6-arg constructor below that). Constructing and starting a fresh `AudioTrack` has
  real, device-dependent startup latency before it actually outputs audio, worst on the first one
  a process ever creates (cold audio HAL/mixer thread) - this is a well-known Android audio
  characteristic, not specific to this project.
- Confirmed the WAV decode itself isn't the cause first: `readSound()` with the default
  `streaming = false` fully decodes to an in-memory `AudioData` once at `GameAudio.load()` time
  (`NativeSoundProviderExt.kt`'s `createSound()` → `createNonStreamingSound()`), so there's no
  per-play decode cost to explain this.

**Mitigation applied, not a full fix** (`GameSounds.primeAll()` in `GameAudio.kt`, called once at
the end of `GameAudio.load()`): plays every loaded clip once at `volume = 0.0` and immediately
stops it. `SoundAudioData.play()` creates and starts the platform output unconditionally regardless
of volume - volume only scales the samples written into it - so this genuinely exercises the same
expensive `AudioTrack` construction path, silently, during the scene's own loading screen before
the player can trigger a real sound. This should eliminate the worst-case cold-start latency (the
very first sound a level ever plays), but since KorGE 6.0.0 has no channel-reuse API, every
individual landing/jump/step still constructs its own fresh `AudioTrack` after priming too - so
some residual per-play latency may remain even after this fix, just smaller than the cold-start
case. **Not yet verified on a real device** - confirm the landing sound actually feels in-sync
before considering this closed; if a perceptible delay remains, the next step would need either an
upstream KorGE fix, a custom Android sound backend, or predictive early-triggering compensated for
a measured (not guessed) per-device latency constant - none of which were attempted here.

## RESOLVED: crouch sound removed, click/toast SFX silent on Android, QUIT/RETURN TO MENU grey-screened (2026-09-05)

Three more real-device reports, same session as the icon/scroll/dossier polish pass above.

**Crouch sound removed by design request** - not a bug, a "don't play anything here" ask.
`GameSounds.crouch`/`GameAudio.CROUCH_GAIN` and both `sounds.crouch.playSfx(...)` call sites in
`GameplayScene.kt` (crouch-enter and crouch-exit) are gone, `resources/sfx/crouch.wav` deleted -
checked first that nothing else referenced it (`PlayerAnimations.kt`/`GameplayScene.kt`'s other
`"crouch"` hits are all sprite/animation-state strings, not the sound file). `GameAudio.kt`'s class
doc comment now explains crouch and crouch-walk are *both* deliberately silent for the same
noise-radius reason crouch-walk already was - it was inconsistent for crouch's stance-change sound
to exist when the whole point of the stance is making no noise.

**Click/toast SFX inaudible on Android, gameplay SFX audible** - real bug, found by comparing
against the gameplay audio path that *was* confirmed working. `AndroidClickPlayer`/
`AndroidMenuClipPlayer` (`paywall-build/src/androidMain/kotlin/ui/MenuSfx.android.kt`, the
`SoundPool`-based UI click + the newer toast_success/toast_error clips) built their
`AudioAttributes` with `.setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)`. That usage is
Android's tag for system/accessibility feedback sounds, which many phones route through a
system-sounds volume stream that's independently muted/low and does not track the media volume
the player actually controls - so every sound on this pool was silent regardless of the in-game
SFX slider. Confirmed against the *other* Android SFX path in this codebase
(`AndroidNativeSoundProvider` in `korlibs-audio-core-android`, decompiled for the "delay between
landing and the landing sound" entry above): its `AudioTrack` uses `USAGE_GAME` (constant `14`,
matched byte-for-byte against the decompiled bytecode), which is why jump/landing/footsteps were
always audible while these weren't. Fixed by changing both `AudioAttributes.Builder().setUsage(...)`
calls to `USAGE_GAME`, matching the bus that was already known to work, so every SFX source in the
app now shares one routing/volume behavior. WAV asset validity was checked and ruled out first
(`ui_click.wav`/`toast_success.wav`/`toast_error.wav` are all structurally normal PCM WAVs, and all
three are correctly bundled `Stored` - not deflated - inside the built APK, so
`AssetManager.openFd()`'s fd+offset approach was never at risk either), so the routing attribute
was the only remaining explanation once those were eliminated.

**QUIT / RETURN TO MENU / MAIN MENU / ALL CLEAR showed a grey screen instead of the menu** - these
four buttons in `GameplayScene.kt` (pause menu QUIT, game-over overlay RETURN TO MENU, level-complete
MAIN MENU, and level-complete ALL CLEAR when there's no next mission) all wrote
`views.storage["nav_target"] = "menu"` (or `"level_select"` for ALL CLEAR) and then called
`sceneContainer.changeTo { GameplayScene(levelData) }` - which just reloads the *same level*, not a
navigation call. Grepped the whole repo for `nav_target`: written in exactly these four spots,
**read nowhere at all** - not in `MainActivity.kt`, not in any Swift file, nowhere. It looks like an
earlier, never-finished attempt at exactly this feature: a storage flag with no consumer on either
platform. So "returning to menu" was actually "silently reload the current level," which is
indistinguishable from doing nothing if the player was already mid-level, and apparently read as a
grey screen in whatever state prompted this report.

Fixed with a new bridge, same "fire and let the host react" shape as `ContinueAdBridge` but
one-way (nothing for GameplayScene to poll afterward - the host reacting is the whole effect):
`LevelExitBridge` (`src/LevelExitBridge.kt`, `interface LevelExitBridge { fun requestReturnToMenu() }`,
`expect fun getLevelExitBridge()`), package `com.sample.demo.nav` (new - deliberately not
`com.sample.demo.ads` alongside `ContinueAdBridge`, since this has nothing to do with ads and reusing
that package would only perpetuate the leftover-template naming). Real implementation on Android
only (`src@android/LevelExitBridge.android.kt`'s `AndroidLevelExitBridgeState`, same plain-shared-object
shape as `AndroidContinueAdBridgeState` since `:game` and the host run in one JVM/APK; a duplicate
non-KMP copy in `android-shell/.../LevelExitBridge.kt`, same reason `ContinueAdBridge` has one there
too). Every other target (`src@ios`, `src@jvm`, `src@js`, `src@wasmJs`) gets a no-op stub - iOS
included, since `ios-shell`'s Swift side has no poll loop wired up for this yet, only for the
watch-ad flow. `android-shell/MainActivity.kt` wires
`AndroidLevelExitBridgeState.onReturnToMenuRequested = { runOnUiThread { showingGameplay.value = false } }`
in `onCreate` - the same one-line flip `showContinueAd()` already used, and correctness here leans
entirely on the earlier "never hide the KorGE view" fix (this doc's "Android watch-ad-to-continue
flow" entry above): there's no surface to tear down or resume, just a Compose recomposition.

All four `GameplayScene.kt` call sites keep their existing `sceneContainer.changeTo { GameplayScene(levelData) }`
alongside the new bridge call (preserves the original "fresh level state" behavior once the player
plays again) except that the ALL CLEAR path now carries a comment: `NavigationRoot` remounts fresh
every time gameplay hides it (it's only composed `if (!gameplayVisible)` in `MainActivity.kt`), so
there is currently no way to tell it "come back to Missions specifically rather than Main Menu" -
all four buttons land on the menu's default screen. Distinguishing them is a real follow-up, not
attempted here.

Confirmed all three fixes compile clean end-to-end: `:compileKotlinJvm` (exercises the JVM
`LevelExitBridge` stub), `:paywall-build:compileDebugKotlinAndroid`,
`:paywall-build:publishToMavenLocal`, and `android-shell`'s `assembleDebug` all succeeded, and the
built APK's `assets/sfx/` no longer contains `crouch.wav` (confirmed via `unzip -l`) while every
other clip - including `toast_success.wav`/`toast_error.wav` - is still bundled. **None of this has
been run on a device yet** - same standing caution as everywhere else in this file.

## In progress: mission dossier card position/spacing, second pass (2026-09-05)

The first pass at this (this file's "Compose UI polish pass" entry above) turned out insufficient
per a second round of real-device feedback: the card still needed to sit further down, and the
internal gap between the upper rule and the chapter/mission text was still visible even after
re-splitting the two flexible spacers to 0.05/0.95. Two changes in `MainMenuScreen.kt`:

- The downward offset went from `16 * dossierScale` to `30 * dossierScale`, and the card's own
  bottom padding shrank from 8dp to 4dp, to free up a little more room to move into.
- The leftover-space-driven gap under the upper rule is gone entirely, replaced with a fixed
  `(6 * scale).dp` spacer - the diagnosis this time was that ANY weight-based gap there, however
  small a share of the flexible space it claimed, still scales with however much spare height the
  card happens to have on a given device, so a smaller weight alone couldn't guarantee it reads as
  tight everywhere. A fixed dp value doesn't have that problem. The other fixed gaps in the same
  column (after the file number, around the mission title, around the lower rule) were trimmed
  too. The single remaining flexible spacer, now at the very foot of the card, absorbs all of the
  card's spare height instead of splitting it with the removed gap.

Both changes are estimates reasoned from the screenshots and the earlier pass's own numbers, not
measured against a running app - same caveat as the first pass, now doubly true. Compiles clean
(`:paywall-build:compileDebugKotlinAndroid`, `android-shell`'s `assembleDebug`). If this still isn't
enough on the next real-device check, the offset and the fixed gaps are the two knobs to keep
adjusting - this entry's numbers are not sacred.

## RESOLVED: RESTART grey-screen, Compose click volume, live Settings sliders (2026-09-05)

Three more real-device reports, next session after the crouch/click-routing/QUIT fixes above.

**RESTART showed a grey screen too, not just QUIT** - and this one traces directly to a bug this
session introduced itself. `GameSounds.primeAll()` (added for the "delay between landing and the
landing sound" mitigation, above) had no exception handling around its `sound?.play(...)?.stop()`
calls. `GameAudio.load()` - and therefore `primeAll()` - runs again on every scene reload (RESTART/
RETRY/QUIT-then-relaunch all call `sceneContainer.changeTo { GameplayScene(levelData) }`, which is
a fresh `GameplayScene` instance top to bottom), so priming now means constructing several
`android.media.AudioTrack`s in quick succession *repeatedly* over a play session, not just once at
first launch - exactly the kind of thing that can throw on a real device even though the first,
cold call didn't. An uncaught exception there propagates out of `GameAudio.load()`, out of
`sceneMain()`, and stops the rest of scene setup (background, player, UI, everything) from ever
running - a grey/blank frame is exactly what that looks like. Fixed by wrapping each clip's prime
attempt in its own `try`/`catch` in `GameAudio.kt`, matching this file's own established policy
that a missing or failing sound should never cost the player the level.

**Compose click sounds too loud** - `NavigationRoot.kt` passed the player's raw SFX slider value
straight through to `rememberUiClick`/`rememberMenuClip` as playback volume, so a click at the
default 100% SFX setting played at full volume with no headroom - unlike the gameplay side, which
has always applied `GameAudio.UI_CLICK_GAIN` (0.6) on top of the slider. Added the same idea for
Compose: `UI_CLICK_RELATIVE_GAIN` (0.5) and `MENU_CLIP_RELATIVE_GAIN` (0.7, toast sounds are rarer
and more deliberate than a click so can sit a little more forward) in `MenuSfx.kt`, applied at
`NavigationRoot.kt`'s call sites (`sfxVolume * UI_CLICK_RELATIVE_GAIN`, etc.).

**Pause-menu/death-menu clicks "missing"** - checked first, and this one was *not* missing in the
code: `createPaperMenuBtn`/`createTacticalMenuBtn`/the pause button's `onDown` handlers in
`GameplayScene.kt` already call `playClick(GameAudio.UI_CLICK_GAIN)` on every button in the pause
overlay, the caught (death) overlay, and the win overlay - and `resources/sfx/ui_click.wav`'s
waveform is a normal, clean click transient (checked the same way `takeoff.wav`'s noise problem
was diagnosed earlier), not silence or corruption. Rather than re-wire something that was already
wired, raised `GameAudio.UI_CLICK_GAIN` from 0.6 to 0.85 for better audibility, and left a comment
on the constant naming the likely remaining suspect if it's *still* not audible: at 83ms this clip
is short enough that the same per-play `AudioTrack` construction latency documented in the "delay
between landing and the landing sound" entry above could plausibly swallow more of it,
proportionally, than a longer sound - unconfirmed, since pinning that down needs a device.

**Settings' Music/SFX sliders didn't do anything while sitting on the Settings screen** - real
structural bug, and `rememberUiClick`'s own doc comment already described the intended behavior
("read at call time... so a move of the Settings slider applies to the very next tap") that the
implementation didn't actually deliver. Root cause: `NavigationRoot.kt` read
`musicVolume`/`sfxVolume` via `remember(currentScreen) { profileStorage.getProfile()... } ` - keyed
on `currentScreen`, so it only re-read storage when the *screen itself* changed, not when
`SettingsScreen`'s own local slider state changed underneath it (that screen kept its own separate
`musicVol`/`sfxVol`/`profileStorage` and wrote straight to storage, invisible to NavigationRoot
until the player navigated away and back). Fixed by lifting the volume state out of
`SettingsScreen` entirely: `NavigationRoot.kt` now holds `musicVolume`/`sfxVolume` as real
`mutableStateOf` state and passes both the values and `onMusicVolumeChange`/`onSfxVolumeChange`
callbacks into `SettingsScreen` (new required parameters - its own local `musicVol`/`sfxVol` state
and the direct `profileStorage.setMusicVolume`/`setSfxVolume` calls are gone, delegated entirely to
the callbacks) - a slider drag now updates `NavigationRoot`'s state directly, which recomposes
`MenuMusic`/`rememberUiClick` on the same frame. `controlsSwapped`/`currentLanguage` were left as
`SettingsScreen`'s own local state, untouched - nothing reported them as broken and they don't have
the same cross-screen audibility requirement volume does.

Confirmed all three compile clean end-to-end: `:compileKotlinJvm`,
`:paywall-build:compileDebugKotlinAndroid`, `:paywall-build:publishToMavenLocal`, and
`android-shell`'s `assembleDebug` all succeeded. **None of this has been run on a device yet** -
same standing caution as everywhere else in this file, and doubly worth heeding here since the
RESTART bug was this session's own regression, caught only by reasoning back from a real bug
report, not by any test that ran.

## RESOLVED (unverified): priming gated to first load only - more grey-screen reports on reload paths (2026-09-05)

The RESTART try/catch fix above turned out not to be the whole story. Two more real-device reports
came in on other reload paths: watching a rewarded ad to continue (grant the reward, dismiss the
ad, and the level never comes back - grey screen) and QUIT-then-PLAY-again (menu shows fine now,
but pressing PLAY afterward grey-screens instead of resuming). Both go through the same
`sceneContainer.changeTo { GameplayScene(levelData) }` reload path RESTART does, so the same
underlying cause is the working theory - and the continue-ad case is the stronger evidence for it,
since that reload happens while gameplay is fully foregrounded the whole time (`showContinueAd()`
never touches `showingGameplay`), ruling out any theory involving the Compose menu covering the
KorGE view.

The try/catch fix only covers priming *throwing* - `GameSounds.primeAll()` constructing several
`android.media.AudioTrack`s back-to-back-to-back on every single reload could just as easily hang
rather than throw (Android has a real per-process ceiling on live `AudioTrack` instances, and nothing
in this codebase explicitly calls `AudioTrack.release()` on a primed-then-stopped channel - if the
native resource isn't freed until Kotlin GC gets around to it, repeated reloads plausibly accumulate
faster than they're reclaimed), and a hang wouldn't be caught by any `try`/`catch`.

**Fix**: gated priming to run only once per process (`GameAudio`'s own `@Volatile private var
audioPrimed`), not once per scene load. This isn't a workaround so much as a correction to the
original design: the actual benefit priming targets - a warm audio HAL/mixer thread - is a
process-wide OS-level effect, not something tied to which specific `Sound` object triggered it, so
every load after the very first was already redundant work, not just occasionally risky work.
Every reload after the first now does the exact same thing it did before priming was ever added
(decode the clips, nothing more) - if the grey-screen reports really were priming-frequency-driven,
they should stop; if they don't, the next place to look is something else in scene setup, not
audio.

Compiles clean (`:compileKotlinJvm`, `:paywall-build:publishToMavenLocal`, `android-shell`'s
`assembleDebug`). **Unverified on-device, same as everything above it** - and this entry is a
second attempt at the same underlying regression, so treat "fixed" here as provisional until it's
actually been retested.

## In progress, third pass: mission dossier card internal spacing (2026-09-05)

Real-device feedback on the second pass (this file's "second pass" entry above, which cut every
gap to a fixed `(6*scale).dp`-ish value) was still the same complaint: text sits too far from the
rules. Cut every fixed gap in the column further, to `(2*scale).dp` uniformly (previously a mix of
2, 3, 5 and 6), and added explicit `lineHeight` to the file-number and chapter-label `Text`s
(`missionNumber`, `storyTitle`) that didn't have one - `missionTitle` already had `lineHeight =
titleSize.sp` and still showed a visible gap before the rule beneath it in the screenshot this
pass was reasoned from, which is a real signal that at least part of what reads as "gap" here is
each `Text`'s own default line-height/font-leading, not only the explicit `Spacer`s between them -
worth keeping in mind if a fourth pass is ever needed: the next lever, if fixed spacers alone don't
close it, is suppressing that default leading directly (Compose's `PlatformTextStyle
(includeFontPadding = false)` on Android), not shrinking spacers that are already near zero.

Not verified on-device - reasoned from a screenshot, same caveat as both passes before it. Compiles
clean.

**Fourth pass**: 2dp read as too tight (real-device feedback, next round) - split the difference at
`(4 * scale).dp` for all five gaps. Still an estimate, still not device-verified.

## Added: shipyard story expanded to 12 levels, with a short/long description split (2026-09-06)

`LevelData.DEFAULT_LEVELS` went from 4 levels to 12, all still "The Shipyard" story arc. Levels
1-3 (`DEFAULT_LEVEL_1/2/3`) and level 4 (`SIDE_SCROLL_LEVEL`, geometry unchanged) were renamed to
match; levels 5-12 (`DEFAULT_LEVEL_5`...`DEFAULT_LEVEL_12`) are new, and - like levels 1-3 - have
no bespoke layout, just `GameWorld.createDefault`'s single-screen arena with a progressively
faster guard per level (`guardSpeed` 80→115) for a difficulty curve. **`timeTargetSeconds` for
levels 5-12 is an estimate, not device/playtest-verified** - same caveat as everything else
gameplay-numeric in this file.

**New field: `LevelData.objectiveHint`** - a short in-game phrase ("Find the Shipyard Entrance"),
separate from the existing `description` field which is long-form narrative text. The split maps
directly to where each one is read:
- `objectiveHint` → `GameplayScene.kt`'s intro toast and the persistent HUD objective strip (both
  previously hardcoded to the single string "REACH THE EXTRACTION ZONE" for every level - now
  `levelData.objectiveHint.uppercase()`).
- `description` → the main menu's dossier/briefing card (`MainMenuScreen.kt`, unchanged wiring -
  it already read `currentMission.description`) and, new this pass, `LevelSelectScreen.kt`'s
  mission cards (previously title + stars + time only, no description at all).

**Missions screen grid restructured for 12 cards**: `LevelSelectScreen.kt`'s mission grid was a
single `Row` sized for 4 - extending that to 12 items would have squeezed every card to a sliver.
Changed to `levels.withIndex().toList().chunked(4)`, one `Row` per chunk, stacked in the existing
scrollable `Column` - same per-card width/style as before, just wrapped into 3 rows instead of 1.

**Dossier card briefing length risk - mitigated, not eliminated**: `MainMenuScreen.kt`'s
`MissionDossierCard` was tuned (see its own inline comments) so the old 4 descriptions (58-85
chars) always wrapped to exactly 3 lines at `maxLines = 3` with no overflow handling - a 4th line
would have clipped mid-word. The 12-level story's descriptions run 91-105 characters (kept
verbatim per the owner's explicit text, not shortened to re-fit), so `overflow =
TextOverflow.Ellipsis` was added to that `Text` as a safety net. `LevelSelectScreen.kt`'s new
mission-card description text also uses `maxLines = 3` + `TextOverflow.Ellipsis` from the start.
**Not verified against real per-platform font metrics** - the dossier card's own comments already
documented that the same nominal char-width assumption varies from 31.5 to 33.6 chars/line across
platforms, so a description near the long end (105 chars, level 8 "Old Signature") could ellipsize
on some devices even though it rendered as 2 clean lines for level 1 in the JVM Compose desktop
check below.

**Verified**: `:compileKotlinJvm`, `:paywall-build:compileKotlinJvm`,
`:paywall-build:compileDebugKotlinAndroid`, and `jvmTest` (all `GameplayModelTest` cases,
including the level-unlock-progression test) all pass. Also visually confirmed via
`:paywall-build:run` (the Compose desktop menu app, `paywall-build/src/jvmMain/kotlin/Main.kt` -
**note this is a separate runnable target from the KorGE game's own `:runJvm`, and both windows can
share the exact title "Infiltrate: Shadow Heist", which caused real confusion this session picking
the wrong window by title alone** - use `Get-Process`'s `StartTime`/PID to disambiguate if both are
ever running at once): the Missions screen renders all 12 cards correctly wrapped across 3 rows,
`0/36` stars total (12×3, correctly reflecting 12 levels), and level 1's new long description
renders cleanly on two lines with no truncation. Locked cards (2-12, since only level 1 unlocks by
default) correctly show no description, matching existing behavior. **Not verified**: the in-game
`objectiveHint` HUD text (the KorGE `:runJvm` side) - attempted but the check kept getting
confused by stray/duplicate "Infiltrate: Shadow Heist" windows and a mistimed scripted click that
closed a window by accident (see above); the code change itself is a trivial, safe string swap
(`levelData.objectiveHint.uppercase()` replacing a hardcoded literal) backed by the same compile +
test pass as everything else, but hasn't been eyeballed on screen. Android/iOS: not attempted,
same standing caveat as every other entry in this file.

## RESOLVED (unverified): continue-ad grey screen - real root cause found, priming-frequency fix wasn't it (2026-09-05)

The "gate priming to first load only" fix above did not fix the continue-ad grey screen - real
device retest confirmed it's still there. That's a useful result on its own: it rules priming
(and by extension `AudioTrack` construction generally) out as the cause of *this* one, since
priming now only ever runs once, on the very first level load, long before any ad is involved.

Found the actual bug by re-reading `ContinueAdBridge.android.kt`'s state machine rather than
guessing again. `ContinueAdTrigger.markRewardEarned()` - called from `RewardedAd`'s
`onRewardEarned` callback - set `outcomeFinished = true` immediately. But `onRewardEarned` fires
the moment the reward is granted, which can happen *before* the player has actually closed the ad:
AdMob's rewarded ad runs as its own separate full-screen Activity stacked on top of MainActivity,
and reward-earned and ad-dismissed are two different moments with the ad's own UI still on screen
between them. `MainActivity.showContinueAd()`'s poll loop consumes `outcomeFinished` within ~100ms
and calls `AndroidContinueAdBridgeState.grantContinue()` - and `GameplayScene`'s own update loop
(which keeps running continuously the entire time, since the KorGE view is never hidden regardless
of what's in front of it) picks that up on the very next tick and immediately calls
`sceneContainer.changeTo { GameplayScene(levelData) }`. Net effect: the level reload was starting
while MainActivity itself was still genuinely backgrounded behind the ad's own Activity window -
a real Android lifecycle state (`onPause`/`onStop` on MainActivity), not merely a Compose overlay
covering a sibling view the way the earlier QUIT/menu-flash bugs were. Reloading a KorGE scene
while its hosting Activity is backgrounded is a materially different situation from anything fixed
so far in this file, and is the more precise explanation this bug needed.

**Fix**: `markRewardEarned()` now only sets `rewardEarned = true` - it no longer finishes the
outcome. A new `onAdClosed()` (renamed from the old `cancelShow()`, called from both `onDismissed`
and `onFailure`) is now the only thing that sets `outcomeFinished`/`showRequested`, and those
callbacks are AdMob's own signal that the ad's Activity is actually gone and MainActivity is
foreground again. This defers GameplayScene's reload until the point where reloading it is
actually safe, instead of racing ahead of the Activity transition.

Compiles clean (`:paywall-build:compileDebugKotlinAndroid`, `:paywall-build:compileKotlinJvm`,
`android-shell`'s `assembleDebug`). **Unverified on-device** - flagged with extra emphasis here
because the previous fix for this exact symptom was also unverified and turned out to be wrong;
don't assume this one is right without an actual retest either.

## Grey-screen-on-reload: gave up guessing, added on-screen diagnostics instead (2026-09-05)

The `onAdClosed()` timing fix above did not fix it either - real-device retest showed the same
failure, just with the caught/game-over overlay now visibly flashing for a moment first before
going grey (consistent with the reload actually starting now, per that fix, but still not
completing). That's three specific, plausible-sounding theories in a row (priming frequency, ad
callback timing, and before those, the try/catch on priming) that each compiled clean, sounded
right, and turned out not to be it. Continuing to guess a fourth specific cause without any way to
see what's actually throwing is not a good use of anyone's time.

**Instead, added real instrumentation.** `GameplayScene.kt`'s `sceneMain()` now wraps its loading
prelude (`GameWorld.createDefault(levelData)`, `PlayerAnimations.load()`, `GameAudio.load()`) in a
`try`/`catch`, and on failure renders the actual exception type, message, and a stack trace excerpt
directly on screen (`text(...)`, red, top-left) instead of leaving a blank grey frame. Whatever
happens on the next repeat-load failure, the report should now be able to include the real error
instead of "grey screen" - which is the actual blocker on fixing this correctly, not a shortage of
plausible theories.

One concrete, structural difference *was* found while adding this, worth recording even though it
wasn't turned into a fix: `PlayerAnimations.load()` (`PlayerAnimations.kt`) has **no try/catch
anywhere in it**, unlike every other asset load in `GameplayScene.kt`'s own `sceneMain()` (all of
which already default to `null` on failure). It also allocates a brand new `MutableAtlas<Unit>
(2048, 2048, growMethod = NEW_IMAGES)` GPU texture atlas and packs the full player spritesheet into
it *on every single call* - i.e., on every scene reload, not just the first. Nothing in this
codebase explicitly releases the previous scene's atlas before the next one is allocated. This is a
real, concrete candidate for something that degrades specifically across repeated reloads in a way
none of the audio theories would (GPU texture memory pressure accumulating call over call), but it
was deliberately not "fixed" here - no evidence yet that it's the actual cause, only that it's
untested and structurally the kind of thing that could be. The diagnostic wrapper above should
settle whether it's this, still something audio-related, or something else entirely, the next time
this reproduces.

Compiles clean (`:compileKotlinJvm`, `android-shell`'s `assembleDebug`). Genuinely unverified this
time in the sense that matters: this isn't claimed as a fix at all, it's the tool for finding one.

## RESOLVED, confirmed on-device: grey-screen-on-reload was PlayerAnimations.load() OOM-ing, not audio (2026-09-05)

The diagnostic screen above worked on the very first try - real device, real stack trace, no more
guessing:

```
java.lang.OutOfMemoryError: Failed to allocate a 16777232 byte allocation with 16075392 free
bytes and 15MB until OOM, target footprint 268435456, growth limit 268435456
  at korlibs.image.bitmap.Bitmap32.<init>(Bitmap32.kt:22)
  at korlibs.image.atlas.MutableAtlas.growAtlas(MutableAtlas.kt:70)
  at korlibs.image.atlas.MutableAtlas.add(MutableAtlas.kt:129)
  at korlibs.image.atlas.MutableAtlas.add(MutableAtlas.kt:76)
  at korlibs.image.format.KorioExtKt.readBitmapSlice(KorioExt.kt:70)
  ...
```

This confirms, precisely, the one concrete lead flagged (not fixed) alongside the diagnostic
screen: `PlayerAnimations.load()` (`PlayerAnimations.kt`) allocated a brand new 2048x2048 GPU
texture atlas and re-decoded the entire player spritesheet into it on **every single call** - i.e.
every scene (re)load, not just the first - with nothing anywhere releasing the previous scene's
atlas first. Every RESTART, QUIT, and watch-ad-to-continue accumulated more texture memory without
freeing the last round's, until an allocation eventually failed. This is why every audio-focused
fix earlier in this session's history for the same symptom (priming try/catch, priming-frequency
gating, ad-callback timing) never actually touched it - completely different subsystem, chasing the
wrong culprit for three attempts before the diagnostic screen made it possible to stop guessing.

**Fix**: `PlayerAnimations` (an `object`, i.e. already a process-wide singleton) now caches the
loaded `PlayerAnimationSet` in a `@Volatile private var cached` field and returns it directly on
every call after the first, instead of reloading. This is safe because the frames are static
content with zero per-instance state - the same character sprite sheet in every level and every
replay - so a second `GameplayScene` never needed its own copy in the first place. Same fix shape,
same reasoning, as `GameAudio`'s one-time audio priming earlier in this file - both were "this
doesn't need to happen more than once per process" bugs wearing different subsystem clothes.

**Not yet extended to the rest of `sceneMain()`'s asset loading**: the background/crate/fence/
button-strip bitmap loads in `GameplayScene.kt` follow the identical "reload fresh, every scene,
nothing released" pattern and are real, plausible contributors to the *same* memory pressure even
though they weren't the one that happened to throw this time (they're all wrapped in
`try { } catch { null }`, so a failure there degrades silently rather than crashing - which also
means they could be leaking without ever surfacing as an exception at all). Deliberately not
touched here: some of these vary by level (`levelData.resolvedBackgroundImage`), so unlike the
player sprite they can't be cached with a single unconditional singleton field - correctly caching
them needs a per-level(or per-asset) cache with real eviction, which is a bigger, separate piece of
work than this fix. Worth doing if OOM-adjacent symptoms ever resurface after this.

Compiles clean (`:compileKotlinJvm`, `android-shell`'s `assembleDebug`). This one has an actual
device-confirmed root cause behind it, not just a plausible theory - but the fix itself (does
caching eliminate the OOM in practice) still hasn't been retested on-device yet.

**Confirmed fixed, real device retest (2026-09-05, later same day)**: the grey screen is gone.
Follow-up cosmetic report from the same flow: the old scene's MISSION FAILED overlay
(`caughtOverlay`) stayed visible for however many frames `changeTo`'s transition to the new
`GameplayScene` instance took, reading as the death menu flashing briefly before gameplay resumed.
Fixed by setting `caughtOverlay.visible = false` immediately when the continue is granted, in the
same `addUpdater` block, before `changeTo` is even called - so there's nothing left on screen to
flash regardless of how many transition frames follow. Compiles clean; not yet retested.

**Fifth pass on dossier spacing (2026-09-05)**: real-device feedback, still asking for "a little
more space between lines and text" even with the three rule-adjacent gaps already at 7dp. This
time also lifted the two gaps that had been left behind at 4dp (after "01", between the two title
lines, neither previously singled out as a complaint) rather than only pushing the already-touched
ones further - all five are now 6-8dp. Compiles clean, not yet retested.

## Added: level-load screen with real progress + blinking "LOADING..." (2026-09-05)

`GameplayScene.kt`'s `sceneMain()` previously did all of its asset loading (world/animations/audio,
then ~11 more bitmap reads) with nothing on screen - fine on JVM where it's instant, but the reason
every "grey screen" bug this session turned out to be silent: there was never any loading UI to
distinguish "still working" from "broken." Added one, styled after a reference mockup the owner
supplied: `resources/loadingbg.png` (new asset, copied in from the owner's asset drop -
`C:\Users\USER\Downloads\charAnimations\assets\loadingbg.png` - `resourcesVfs` only sees the repo's
own `resources/`, not that folder) full-bleed behind the existing `resources/logo_main.png`, a
`uiGraphics()`-drawn progress bar frame+fill, and a "LOADING..." label. Progress is real, not
decorative: a `totalLoadSteps = 14` counter with a `markLoadProgress()` call after each existing
load line (the world/animations/audio triple, then each of the ~11 bitmap reads) advances the bar
and yields one `delayFrame()`, so the bar reflects actual load progress rather than a timer. The
label blinks with a simple on/then-briefly-gone/then-back cycle (`blinkPeriodSeconds = 0.9`,
`blinkVisibleFraction = 0.78` - visible ~0.7s, fully invisible ~0.2s) - deliberately not a gradual
pulse or an irregular neon-style flicker, which was tried first and explicitly rejected in favor of
this simpler pattern. The screen is dismissed (`dismissLoadingScreen()`: closes the blink
`addUpdater`'s `CloseableCancellable` via `.close()`, then `removeFromParent()`) once every load
step has run, or immediately if the critical world/animations/audio load throws (falls through to
the existing `LEVEL LOAD FAILED` diagnostic screen instead of leaving the loading UI stuck on
screen).

**Verified for real, not just compiled**: `:compileKotlinJvm` succeeds, and `./gradlew.bat runJvm`
was actually launched and screenshotted on this dev machine (real GPU present here, `GL_VERSION=3.3.0
NVIDIA`) - both the static layout (background/logo/bar/label all correctly positioned and sized) and
the blink itself (three screenshots ~0.5s apart show the label fully visible, fully visible, then
fully gone) were confirmed from real captured frames, with a temporary `repeat(20) { delayFrame() }`
swapped in for `markLoadProgress()`'s single `delayFrame()` during that check only (reverted before
the final compile) since real loads on this machine finish in well under a second otherwise.

**Not yet verified**: Android and iOS. On iOS specifically, this loading screen reads its assets
through the exact same `resourcesVfs` path already flagged as broken in "KNOWN GAP: `resources/`
never reaches the iOS shell bundle" above - so until that gap is fixed, expect this loading screen
to show as a plain black frame (background/logo missing, same silent-fallback behavior already
described there) with only the progress bar and label drawn, on the iOS shell build specifically.

**Refinement pass, same day**: real feedback on the first version (screenshot of the actual running
bar) - label font too large, blink too frequent, and the bar fill was a flat `Colors.WHITE` rect
rather than matching the main menu's button texture. Fixed: `loadingBarTextureBitmap` now loads
`resources/button1.png` (the exact texture `MainMenuScreen.kt`'s PLAY button uses,
`Res.drawable.button1`) alongside the bg/logo loads, and `setLoadingProgress()` rebuilds an `image()`
stretched to the current fill width each step instead of drawing a solid rect (falls back to the old
white rect only if the texture fails to load) - same "plain stretch, not 9-sliced" precedent already
established for this exact art in `UiComponents.createButton`. Label size dropped from
`loadingBarHeight * 1.15` to `* 0.62`. Blink slowed from a 0.9s period (78% visible) to 2.2s (88%
visible), so it now vanishes for a brief instant roughly once every ~2 seconds instead of about once
per second. Verified the same way as the first version - not just compiled: `:compileKotlinJvm`
passed, and `./gradlew.bat runJvm` was launched and screenshotted for real (a temporary
`repeat(300) { delayFrame() }` swapped into `markLoadProgress()` for this check only, reverted
after), confirming both the torn-paper texture fill actually renders (visible ink/tear edge at the
fill boundary, not a flat rectangle) and the smaller/slower-blinking label.

## Level 1 redesign: barrels, then a crate + truck climb onto the existing platform (2026-09-05)

Per the owner's request: after the two start gates, walk a bit, hop three barrels standing
together, walk on, then climb a small crate onto a parked truck and step from the truck onto the
long platform that already carries the hanging chained crate - everything from there on (the
crouch-under-hanging-crate, `block2`/`block3`, the guard/camera zone, the exit) is unchanged in
mechanics, just shifted further down the corridor to make room.

**New assets**: `resources/barrel.png` and `resources/truck.png`, copied from the owner's
`C:\Users\USER\Downloads\charAnimations\assets\` drop (same source as `loadingbg.png` earlier) -
same flat-silhouette style as the existing `crate.png`/`chainedcrate.png`. Native pixel sizes
(checked via .NET `System.Drawing.Image`, no `identify`/PIL available on this machine):
`barrel.png` 1024x1536, `truck.png` 1774x887.

**Geometry** (`GameWorld.kt`'s `createDefault()`, `DEFAULT_LEVEL_1` only - `SIDE_SCROLL_LEVEL`'s
explicit `LevelLayout` path is untouched): three `barrel` boxes (32w x 48h each, touching, starting
at x = 700) replace nothing - they're new - followed by a 200px walk to `smallCrate` (68x48, same
dims the old single "step crate" always used), then `truck` (255x96, flush with the long
platform's height so stepping off it onto the platform is a level walk, not another jump), then
`longPlatform` (900x96, unchanged) starting right at the truck's edge. The hanging chained crate,
`block2`, `block3`, the guard patrol range, the camera, and the exit zone are all still there with
identical mechanics, just computed relative to the new platform position (`longPlatform.right +
252.0`, etc.) instead of hardcoded absolute x values - `worldWidth` grew from 3200 to 4000 to fit.
`LevelData.kt`'s `DEFAULT_LEVEL_1.guardPatrolMinX/MaxX` and its camera's `x` were updated to the
new absolute numbers (3371.0/3721.0/3731.0) since those three are the only pieces of this layout
still expressed as literals outside `GameWorld.kt`. (Barrel/truck widths and every downstream
number here were revised once more after this section was first written - see the "tight-crop"
follow-up below; this paragraph reflects the final values.)

**Jump-height constraint, verified against the existing physics constants, not guessed**:
`Player.maxJumpHeight = jumpSpeed² / (2·gravity) = 320² / 2000 = 51.2` units. Every new rise (ground
to barrel top, barrel top back to ground, crate to truck top) is exactly 48 units - the same value
the level's original step-crate-to-platform climb already used successfully, comfortably under the
51.2 ceiling. Not derived from the docstring elsewhere in this file describing a 72-unit
`SIDE_SCROLL_LEVEL` box as jumpable from flat ground - re-reading that layout shows that box's top
edge is flush with the mid-tier platform right next to it (not a standalone step), so it isn't
actually evidence of a taller single-jump rise being possible.

**Why the barrels sit at x = 700, not right after the gates as a literal "little bit"**: a large
cluster of existing unit tests in `test/GameplayModelTest.kt` place the player and/or guard at
fixed x-coordinates between roughly 60 and 650 (e.g. `world.guard.x = 500.0`,
`world.player.x = 580.0`) and assume open, box-free ground there, independent of this level's
actual story layout - this predates the redesign and reflects the old layout's first box (the
original step crate) not appearing until x = 580. The first attempt placed the barrels at x = 320
and broke `testPlayerGuardCollision` (a barrel embedded in the player's y = 284 test position
caused a bogus 159px collision-resolution shove, not the expected guard-edge stop at x = 464) -
found by actually running `./gradlew.bat jvmTest`, not by reasoning about it in advance. Moved the
whole barrel/crate/truck sequence out past x = 700, comfortably clear of every fixed coordinate
that cluster of tests uses, rather than either contorting the design or hand-editing every
affected test. Only `testCameraTimingChallengeWalkthrough`'s two literals
(`camera.x`/`world.guard.x`) needed updating, since those two specifically assert the *new* end-of-
corridor values.

**Rendering** (`GameplayScene.kt`): `barrelBitmap`/`truckBitmap` load the same try/catch-to-null
way every other box texture does, `totalLoadSteps` went from 14 to 16 to match the two new
`markLoadProgress()` calls. Two new branches in the per-box render loop, matched by `box in
world.barrels` / `box == world.truck` (new `GameWorld` fields, same nullable-reference-equality
pattern `fence1`/`fence2` already use) - placed *before* the existing generic "height < 70 && width
< 150 → render as crate.png" heuristic, since the barrels' own dimensions (32x48) would otherwise
satisfy that heuristic and get misrendered as crates.

**Tight-crop follow-up, same day**: real feedback on a screenshot showed the truck floating above
the ground, squashed vertically, and the player floating above the truck's roof - all three from
one root cause, found by actually measuring the PNGs (.NET `Bitmap.LockBits`, scanning for the
tight alpha bounding box) rather than assuming they were prepped like this game's other assets.
Every existing box texture (`crate.png`, `chainedcrate.png`, `fence.png`) is "tightly cropped to
exact visual bounds" per this file's own earlier notes - confirmed `crate.png`'s alpha bounds are
its full canvas edge-to-edge - but the owner's raw `barrel.png`/`truck.png` drop was not: real
tight content was `x=96..927, y=83..1356` inside barrel's 1024x1536 canvas (17.6% dead bottom
margin alone) and `x=47..1744, y=149..788` inside truck's 1774x887 canvas (a full 17% dead margin
at the *top*). Since every box image renders via `size(box.width, box.height)` stretched to the
exact collision box with no cropping at render time, that dead margin doesn't disappear - it
stretches too, so the visible truck/barrel silhouette ends up sitting measurably inside its own
box instead of flush with it: gap at the bottom (floating above ground) from bottom padding, gap at
the top (floating player) from top padding, plus the pre-existing box-aspect-ratio mismatch
compounding the squash. Fixed by actually cropping both PNGs to their measured tight bounds in
place (`barrel.png` → 832x1274, `truck.png` → 1698x640) and re-deriving the box widths from the
*cropped* aspect ratio at each box's fixed (jump-height-constrained) height: barrel width 44→32
(1274/832 ≈ 1.53 ratio at height 48), truck width 300→255 (1698/640 ≈ 2.65 ratio at height 96).
Every downstream position in `GameWorld.kt` is expressed relative to the previous element
(`barrel3.right + 200.0`, `truck.right`, `longPlatform.right + 252.0`, ...), so shrinking the two
widths cascaded automatically; only `LevelData.kt`'s three literals (guard min/max X, camera x) and
the two matching test assertions needed manual updates, same as the first pass. Re-ran the full
`jvmTest` suite after each change - still green throughout, including the barrel-position-sensitive
`testPlayerGuardCollision`. **Still not visually confirmed on a real running window** - the
`runJvm` screenshot pipeline remained unreliable this session (see above); the fix is grounded in
directly measuring the actual pixel content of both PNGs before and after the crop, not in a
render that was seen with real eyes. Look at it for real before calling this done.

**Pacing follow-up, same day**: owner feedback that both walking gaps were still too long. Cut the
gate-to-barrels walk from 465 units down to 85 (barrel1 now starts at x = 320, right past the
fences) and the barrels-to-crate gap from 200 down to 70. Since every position downstream is
already expressed relative to the previous one, this cascaded through the whole corridor
automatically and `worldWidth` shrank from 4000 to 3450.

This ate into the "open ground" test zone from the first pass entirely - the barrel/crate/truck run
now spans roughly x = 320 to 809, which fully covers the old 60-650 buffer several tests relied on.
Rather than hunt for another gap to hide test coordinates in, moved the *tests* to reuse
`guardPatrolMinX..guardPatrolMaxX` (2861-3211, real open ground with no boxes by construction,
since nothing is ever placed there in `GameWorld.kt`) instead - a stable, self-documenting home for
generic player/guard physics tests rather than an incidental gap that shrinks every time the story
geometry changes. Updated four tests to this zone: `testPlayerGuardCollision`,
`testGuardInvestigateRedetectionAndEscalation`, `testGuardResumesPatrolAfterLosingVisualFromCone
Detection`, and `testGuardStopsAtPositionWhenUserDetectedInVisionCone`.

That last one caught a real gap in the first pass's own reasoning: it clears `occluders` (so line-
of-sight can't be blocked) but never clears `platforms`, and by this point the truck box (x =
554-809) had crept under its old player position (x = 580, y = 284) - the player was physically
embedded in the truck's collision box, and gravity/collision resolution silently relocated it
somewhere the vision cone no longer reached, failing the test with no obvious connection to "the
truck moved." Found by running `jvmTest`, not by inspecting the geometry by eye - a reminder that
"occluders cleared" and "platforms cleared" are two different guarantees, and a test relying on one
doesn't get the other for free.

Full `jvmTest` suite green after these changes. Same visual caveat as the sections above -
compiled and test-verified, not seen running.

## Level 1 reshuffle: barrels moved past the platform, guard/camera removed (2026-09-05)

Real feedback from screenshots of the running level: the start-of-level barrels felt unnecessary,
and there was an unassisted drop after the long platform (the one carrying the hanging chained
crate) straight down to the ground. Owner's ask: drop the start barrels, add a box at that platform
edge to climb down via (mirroring the step-up crate at the start), move the barrels to cover the
ground gap right after it, and remove the guard/camera from the level entirely.

**New order in `GameWorld.kt`'s `createDefault()`**: gates -> `smallCrate` (48x68, at x=320, no
walk-up barrels beforehand anymore) -> `truck` -> `longPlatform` (unchanged, still carries
`hangingChainedCrate`) -> `stepDownCrate` (new, same 48x68 dims as `smallCrate`, sitting right at
`longPlatform.right` - the player now descends the 96px platform in two 48px steps instead of one
drop) -> the three barrels (unchanged 32x48 each, now positioned right after `stepDownCrate`
instead of near the start) -> `block2` -> `block3` -> the (now hidden) guard zone -> exit. Every
position is still expressed relative to the previous one, so this was a pure reordering of the
existing building blocks, not new geometry math - `worldWidth` settled at 3350.

**Removing the guard was more invasive than it looked.** `GameWorld.guard` is a mandatory,
non-nullable field, and a large fraction of `GameplayModelTest.kt` reads `world.guard.*` directly -
some tests reposition only `.x` (relying on a normal `.y`/`.visionRange`/`.facing` already being
there), some don't touch it at all (`world.player.x = world.guard.x - 100.0`, expecting a real,
detection-capable guard on the other end). Two failed approaches before landing on the right one,
both found by actually running `jvmTest`, not by reasoning about the data model in the abstract:

1. First attempt zeroed `visionRange`/`speed` on the "disabled" guard. Broke ~9 unrelated tests
   across the suite - any test that reused `world.guard` (even ones that explicitly overrode `.x`)
   inherited the permanently-blinded `visionRange = 0`, since they never had a reason to reset a
   field they'd never seen change before.
2. Second attempt fixed that but used a degenerate `patrolMinX = patrolMaxX = -5000.0` (matching the
   guard's parked position). `Guard.updatePatrol()`'s own boundary-clamp logic (`if (facing > 0.0 &&
   x >= patrolMaxX) x = patrolMaxX`) then snapped any test-set `.x` straight back to -5000 the next
   time `world.update()` ran, since literally any real corridor coordinate is `>= -5000`.

**What actually shipped**: a new `LevelData.guardEnabled: Boolean = true`. When false,
`GameWorld.createDefault()` still constructs a completely normal `Guard` - same `y = groundY - 48.0`,
same default `visionRange`/`visionFov`/`facing` - just parked at `x = -500.0` (behind the level's own
`leftWall` at x = -30, permanently unreachable during real play) with `speed = 0.0` (never drifts
back into the corridor over a long session) and a deliberately wide `patrolMinX = -10000.0` /
`patrolMaxX = 10000.0` so a test that repositions `.x` anywhere in the real corridor doesn't get
clamped back. The guard is still technically present and still renders (nothing in `GameplayScene.kt`
special-cases it - `world.allGuards` still contains it) - it's just far enough off-map that the
camera never scrolls anywhere near it, which in practice is indistinguishable from "removed" for a
player. Camera removal was the easy half: `DEFAULT_LEVEL_1`'s `cameras` list is just empty now,
already fully supported by every consumer.

**One test needed real rework, not just new coordinates**: `testCameraTimingChallengeWalkthrough`
asserted `DEFAULT_LEVEL_1` ships exactly one camera and used it directly - no longer true. Rebuilt
it to drop a synthetic `Camera` onto the level's real geometry via `.copy(cameras = listOf(camera))`,
the same pattern already used by `testPowerupSmokeScreenDisablesCameras`/
`testCameraAlertSystemIntegrationInGameWorld` for a camera unrelated to whatever the base level
ships. First version of the rewrite picked an arbitrary `camera.x` and started failing with the
player getting caught mid-run for a reason that took a moment to place: the original test was
tuned so the camera sat a specific ~80-unit dash from the real exit, and an arbitrary position broke
that timing margin. Fixed by deriving `camera.x` from `baseWorld.exitZone.x - 80.0` instead of a
guess, restoring the same relationship the original (now-removed) level-1 camera happened to have.

Full `jvmTest` suite green. Same visual caveat as every section above - compiled and test-verified,
not seen running.

## Level 1, third pass: more room before the crate, barrels moved to bridge block2->block3 (2026-09-06)

Real feedback from three more screenshots: the walk from the gates to the step-up crate still felt
too tight, the barrels sitting right after the step-down crate should go, and the ground gap between
`block2` and `block3` further down the corridor - previously just bare ground - should be filled
edge-to-edge with those same barrels instead. Also asked to replace the exit's vector-drawn
"black box + green border + EXIT text" marker with a real image (`entrance.png`, cropped).

**Geometry** (`GameWorld.kt`): `smallCrate.x` moved from 320 to 420 (100 more units of ground walk
past the gates). The 3 barrels that used to sit right after `stepDownCrate` are gone from there -
that stretch is now `stepDownCrate.right + 200.0` of plain ground before `block2` - and instead 7
barrels now tile the `block2` -> `block3` gap exactly: `barrels = (0 until 7).map { i -> Rect(x =
block2.right + i * barrelWidth, ...) }`, then `block3.x = barrels.last().right`. 7 was chosen because
7 * 32 = 224, comfortably covering (and slightly exceeding, pushing block3 forward a touch) the ~210
unit gap this replaces - the point of computing `block3.x` from the last barrel rather than a fixed
offset is that there is now zero bare ground left in that gap by construction, whatever the exact
barrel count. `worldWidth` grew from 3350 to 3500 to fit the wider corridor plus the new entrance
visual (see below). `LevelData.DEFAULT_LEVEL_1`'s `guardPatrolMinX/MaxX` moved to 2825.0/3175.0 to
match - still comfortably containing every fixed coordinate the guard-safe-zone unit tests
(`testPlayerGuardCollision` and friends, see the section above) already use, so none of them needed
touching this time.

**New asset**: `resources/entrance.png`, copied from the same
`C:\Users\USER\Downloads\charAnimations\assets\` drop and tightly cropped the same way
barrel.png/truck.png were (measured via .NET `Bitmap.LockBits` alpha-bounds scan, not eyeballed):
native 1672x941 canvas, tight content only `x=46..1634, y=212..824` -> cropped in place to
1589x613, now flush edge-to-edge. It's a wide composite (barbed-wire fence + a couple of crates and
a barrel on the left, a roofed guard booth on the right) - a natural bookend for the start gates.

**Exit rendering** (`GameplayScene.kt`): the old `exitContainer.solidRect(...)` gate marker
(black box + 4 green border strips + "EXIT" text) is gone, replaced by `entranceBitmap` rendered at
a fixed height (160) with its width derived from the bitmap's own aspect ratio
(`entranceHeight * bitmap.width / bitmap.height`) so it's never stretched/squashed like the
barrel/truck near-misses earlier in this file. **Purely decorative** - `world.exitZone` itself (the
actual completion trigger) is completely unchanged in position/size; the image is positioned so its
gate post (measured at ~63.5% across the cropped image, by eye against the asset) lines up with the
trigger's center: `entranceX = exitZone.x + exitZone.width/2.0 - 0.635 * entranceWidth`. Since it's
image-only with no collision box, it can (and does) visually extend back over part of the empty
guard-patrol-zone ground with no functional consequence.

**Verification**: `:compileKotlinJvm` succeeds, full `jvmTest` suite green with zero test edits
needed this round (the barrel-position and guard-zone unit tests all happened to already tolerate
the new numbers). Same standing caveat as every entry above - not yet seen running for real.

(The paragraph that used to follow here, about a failed `runJvm` screenshot attempt, was a stray
duplicate of the one already earlier in this file under "Level 1 redesign: barrels, then a crate +
truck climb" - removed 2026-09-06 while adding the section below, not because the underlying
screenshot-pipeline caution stopped applying; it still does, see that earlier section.)

## Fourth pass on spacing/exit, plus real animation and physics fixes (2026-09-06)

Real feedback, this time from five screenshots plus plain-text notes rather than just "shift this
box": more space before the crate (again - the numbers in the section above didn't hold), the
interact button nudged a little more, two animation foot-alignment complaints, an exit that should
be mirrored and should end the level the instant it's touched, a genuine "I can fly past the end of
terrain" physics bug, and a question about exact-shape hitboxes.

### 1. More space before the crate, yet again
`smallCrate.x` moved 420 -> 550. Every downstream position is still relative
(`truck.x = smallCrate.right`, etc.), so this cascaded the whole corridor forward by 130 and
`worldWidth` grew to 3550 (before item 5 below grew it further to 3900).
`LevelData.DEFAULT_LEVEL_1.guardPatrolMinX/MaxX` moved to 2955.0/3305.0 to match.

**This is now the fourth time the front-of-corridor geometry has shifted** (700 -> 320 -> 420 -> 550
for whatever sits right after the gates, across four separate rounds of feedback). The
`GameplayModelTest.kt` tests that need generic open ground for guard/vision math no longer hardcode
absolute coordinates at all now (see item below) specifically so a fifth shift doesn't repeat this.

### 2. Interact button nudged again
`interactAngle` 65° -> 60° (was 80° originally, then 65° per an earlier real-device round, now
60°) - same direction as before (down and right), smaller step this time since it was closer to
right already.

### 3 & 4. Crouch and jump-landing: one foot not touching the ground
Real root cause, found by measuring the actual sprite frames rather than guessing - the same
technique `IDLE_FEET_Y` already used for this exact phenomenon in idle:
- `resources/player/crouch/0034.png` (`CROUCH_LAST`, the held crouch pose): a per-column alpha
  scan shows the front foot's lowest row at 255 (on `SOURCE_FEET_Y`) and the back foot's at ~250 -
  a settling crouch plants weight forward with the back heel raised, not a flat two-footed squat.
- `resources/player/jump/0028.png`..`0044.png` (`JUMP_LAND_START`..`JUMP_LAND_END`, the landing
  absorb sequence): the same shape, strikingly consistent across all 17 frames - back foot ~247-248,
  front foot on the true line - checked several frames, not just one, specifically to rule out a
  one-frame fluke.

Added `PlayerAnimations.CROUCH_FEET_Y = 250.0` and `JUMP_LAND_FEET_Y = 247.0`, then the same
`(SOURCE_FEET_Y - X_FEET_Y) * playerBaseScale` offset formula `IDLE_FEET_Y`/`idleFeetOffset` already
used, applied in `GameplayScene.kt`: `crouchFeetOffset` when `playerAnimState == "crouch"`
(deliberately *not* `crouchwalk` - a walk cycle's alternating planted/swinging foot is supposed to
look uneven, this is only for the settled two-feet-down held pose) and `jumpLandFeetOffset` added
specifically when `jumpPhase == "land"`. Per the user's own explicit framing ("make both feet touch
the ground even if one goes beyond it"): this necessarily pushes the front foot a few pixels below
the nominal ground line to bring the back foot up to it, since a flat sprite can't move one foot
independently of the other - accepted as the better trade-off, matching what `IDLE_FEET_Y` already
does for idle without complaint.

### 5. Exit: flipped, and the trigger now matches the visual
Two related fixes, same root cause: the `entrance.png` gate-post alignment in the section above was
"measured by eye against the asset," and the actual completion trigger (`world.exitZone`) stayed a
narrow 40-unit box positioned by that eyeballed guess - so touching the visually obvious checkpoint
structure didn't reliably touch the (mis-aligned, narrow) real trigger underneath it, reading as "it
doesn't end when I touch it." Fixed by removing the alignment guess entirely rather than
re-measuring it more carefully: `exitZone.width` widened from 40 to 380 (spans almost the entire
entrance visual) and the image is now left-aligned flush with `exitZone.x` - no fraction math left
to get wrong. `worldWidth` grew to 3900 to fit the wider zone. Separately, mirrored the image
horizontally (`scaleX = -1.0` with an `x + entranceWidth` position compensation, the same
flip-in-place trick `playerSprite`'s own left/right facing already uses) so the booth - the
recognizable checkpoint landmark in the raw asset - is what the player actually walks up to first,
instead of the fence section the unflipped asset leads with.

### 6. "I am able to fly after terrain" - real collision bug, not a rendering artifact
Traced to `Player.kt`'s vertical collision loop, `updateStep()`. Two distinct problems, found by
reading the loop line by line against the class's own documented intent (`footWidth`'s doc comment
explicitly says landing should be feet-narrow, walls/ceilings should stay full-width) rather than by
reproducing it first:

1. **Order-dependent branching within a single step.** The loop branched on `vy > 0.0` to decide
   feet-narrow vs. full-width, but the very first matching platform set `vy = 0.0` as a side effect
   - so for a foot span touching *two* platforms at once (a seam), every platform after the first in
   iteration order got evaluated by the full-width/ceiling-or-floor-by-midpoint logic instead, purely
   because of where it happened to sit in the list. Fixed by capturing `wasFalling = vy > 0.0` once,
   before the loop, and branching on that instead of the live (mutated) `vy`.
2. **The real "flying" mechanism**: at a seam between two *touching* platforms of different heights
   - and this level now has several, by design: `smallCrate`(48) -> `truck`(96), `longPlatform`(96)
   -> `stepDownCrate`(48) - a foot span straddling the seam intersects both, and the old rule
   (`newY = minOf(newY, platform.top - height)`, i.e. always resolve to the *taller* one) pins the
   player to the taller platform's height until the *entire* foot span (`footWidth`, ~21.6 units) has
   cleared its far edge. Walking off the tall side onto the low one, that reads as hovering rigidly
   at the old height for up to one foot-span past where the platform visibly ends, then snapping
   down all at once - exactly "flying past the end of terrain." Fixed by picking whichever candidate
   platform keeps the player closest to their *current* y instead of always the tallest, so the
   handover happens right at the point the foot span actually loses its last bit of overlap with the
   platform they were already on, not one foot-span later. Does not touch `footWidth`'s size at all
   (deliberately - it's calibrated to the visible sprite's own width per its doc comment; narrowing
   it would trade this bug for the opposite one, the sprite visibly hanging unsupported).

Full `jvmTest` suite - including `testPlayerIsNeverGroundedWithoutSupport`, which exercises exactly
this kind of scenario via randomized walks over the real level geometry - stayed green through both
fixes, for whatever that's worth against a bug that random sampling apparently wasn't hitting
already (it never failed before this fix either).

### Guard-zone test coordinates made geometry-independent
Fallout from item 1 recurring a fourth time: the four tests that need generic open ground for
guard/vision physics (`testPlayerGuardCollision`, `testGuardInvestigateRedetectionAndEscalation`,
`testGuardResumesPatrolAfterLosingVisualFromConeDetection`,
`testGuardStopsAtPositionWhenUserDetectedInVisionCone`) previously hardcoded absolute x coordinates
matching whatever `guardPatrolMinX` happened to be *at the time each was last fixed* - which is
exactly why they kept breaking every time the front-of-corridor geometry moved. All four now derive
a `val base = world.levelData.guardPatrolMinX + 75.0` and express every position as `base +/- N`,
so a fifth shift of the story geometry (now demonstrably not unlikely) won't require touching these
again, as long as the guard zone itself stays a similarly-sized stretch of open ground.

### 7. "Is it possible to make the bounding box exact shape of the object?"
Answered in chat, not implemented: technically yes (polygon/pixel-mask collision exists), but it's a
different, much larger collision architecture than this project has anywhere today - every check in
`Geometry.kt`/`Player.kt`/`Guard.kt`/`Vision.kt` is `Rect`-vs-`Rect` (`intersects`, line-of-sight
against axis-aligned boxes, etc.), and switching even one entity to a precise polygon hitbox would
mean either maintaining two parallel collision systems or rewriting all of them. The existing
`footWidth` narrowing (feet tested on a tighter span than the full sprite width, specifically to
approximate the visible silhouette without literal per-pixel shapes) is the project's actual answer
to "the box is bigger than what you see" - a normal, standard platformer compromise, not a stopgap.
Recommended sticking with it and tuning per-case (as items 3/4/6 above just did) rather than taking
on exact-shape collision, unless a specific remaining case genuinely can't be solved that way.

**Verification**: `:compileKotlinJvm` succeeds, full `jvmTest` suite green (all four guard-zone
tests re-verified with the new dynamic `base`, not just left alone). Same standing caveat as every
section above - none of items 2-6 have been seen on a real running screen yet, only reasoned about
from measured source frames and read collision code.

## Exit asset was the wrong image entirely, and a real 3-tier truck (2026-09-06)

A screenshot showed the exit rendering as a screen-filling black wall with a guard booth barely
visible at the far right - not a bug in the flip/positioning math from the previous round, but the
wrong *asset*. `entrance.png` had been tight-cropped to its own alpha bounds before, but nobody had
checked what was actually *in* those bounds: it's a wide stock illustration of an entire fence
line - chain-link, barbed wire, two crates, a barrel, a fence post - with the checkpoint booth stuck
on the far right edge as a small fraction of the whole thing. Scaling that composite by height (as
if it were just a booth) rendered the whole scene, booth included, and since most of the frame is
solid black fence/crate silhouette, it read as "wtf is this wall."

Fix: found the seam between the fence post and the booth's own support pole by scanning columns for
content density (`resources/entrance.png` scan: chain-link mesh runs ~226-232 opaque samples/column
through x=1046, drops to 19-43 in the x=1048-1058 gap, then jumps to ~197-270 at x=1060+ where the
booth's pole starts), cropped from there to the far edge, then re-tightened the result to its own
alpha bounds. Final asset: 531x612, booth only. `GameplayScene.kt`'s rendering code (aspect-ratio
sizing, left-align, horizontal flip) didn't need to change at all - it was already correct once given
the right source image. Bumped `entranceHeight` 160->200 (a booth-only asset reads small at the old
height) and shrank `exitZone.width` in `GameWorld.kt` 380->160 to match the new, much narrower
image's footprint (it was sized to "almost span" the old 414-wide composite; the booth alone is only
~174 wide at the new height).

Separately, asked to swap in a better truck asset
(`C:\Users\USER\Downloads\charAnimations\assets\truck_new.png`) with 3 collision tiers - front/
middle/back - and the left/right flipped. `truck_new.png` turned out to be a flat white-background
JPEG-style PNG (`Format24bppRgb`, no alpha at all), unlike every other asset in this project, so the
usual alpha-bounds crop script did nothing (every pixel read as 100% opaque). Chroma-keyed it instead
(any pixel with R/G/B all >=200 -> fully transparent, else fully opaque black) before cropping to its
tight bounds: 1683x617, from source region x=[50,1732] y=[156,772].

Measured the silhouette's top profile in 20px column steps to find where the flat cab/bed roofline
(top y ~155-164, essentially constant) breaks down into the lower hood (top y ~351 from x~1490 on,
a mirror bump in between at ~1470-1490). That gave 3 tiers instead of one flat box, in
`GameWorld.kt`'s `createDefault`:
- `truckFront` (hood): 38 wide x 66 tall - 66 = 96 * (421/616), the hood's height as a fraction of
  the full 616px silhouette height, scaled to the in-game 96-unit roofline height.
- `truckMiddle` (cab) and `truckBack` (bed): 45 and 179 wide, both 96 tall (flush with each other
  and with `longPlatform`, same reasoning as the old single-box truck - stepping across onto the
  platform should be a level walk, not another jump). Widths are the cab/bed/hood x-ranges
  (1200-1490, 1490 excluded above is hood) converted to fractions of the 1683px total width and
  applied to a 262-unit total (96 * the image's own aspect ratio).
- `truckParts = listOf(truckFront, truckMiddle, truckBack)` replaces the single `truck` Rect in
  `boxes`/`platforms`/`occluders`. `truck` itself is kept as the *union* of the three (same x, full
  width, tallest height) - not a collision box, just the footprint the image is drawn into.

Rendering (`GameplayScene.kt`) had to change shape, not just swap a bitmap: three separate collision
boxes but one continuous truck texture means drawing it three times (once per box, stretched to that
box's own tiny width/height) would squash it into three unrecognizable slices - exactly the original
"squashed and floating" bug from earlier this session, reintroduced a different way. Instead the
image is drawn once, keyed off `box === world.truckParts.first()` (the front/hood tier), sized and
positioned to `world.truck` (the full union footprint) rather than to that one box; the other two
tiers match `box in world.truckParts` and draw nothing. Flipped the same way as the exit booth
(`scaleX = -1.0` + reposition by `+width`) so the hood - the low tier the player actually climbs onto
first, right after the crate - faces the direction of approach, with the cab and bed (both flush with
the long platform) stretching away to the right.

**Verification**: `:compileKotlinJvm` and full `jvmTest` succeed (no test referenced `world.truck` or
truck coordinates directly, so the tier split needed no test changes). Not yet confirmed on a real
running screen at the time this was written - see the next section, which found this exact flip
technique corrupts the truck's rendering and fixed it.

## Real bug found by real verification: negative `scaleX` corrupts `Image` rendering (2026-09-06)

The user reported the exit rendering as a screen-filling wall (`entrance.png` was the wrong asset -
see below), then separately asked for a `truck_new.png` swap with 3 collision tiers and the truck
flipped. After shipping both, the user sent a screenshot from their own desktop (this sandbox and
the user's desktop are the same machine, so anything run here is visible to them) showing the truck
as a torn, mostly-transparent smear at ground level with the sky's parallax reflection showing
straight through - not the truck at all. This is the first time this session the JVM screenshot
pipeline actually worked end-to-end, and it immediately paid for itself: **the bug was not in the
truck-specific code from the previous entry - it was `scaleX = -1.0` on a KorGE `Image`, the exact
technique also used (and, it turns out, never actually verified) for the exit booth flip.**

**How it was actually found** (`./gradlew.bat runJvm`, then a real Win32 screenshot - `user32.dll`
`EnumWindows`/`SetForegroundWindow`/`ShowWindow`, `System.Drawing.Graphics.CopyFromScreen`, all via
the PowerShell tool - of the live window, not the old runJvm+simulated-keypress approach that
produced stale frames earlier this session). Debugging happened by temporarily setting
`GameWorld.kt`'s player spawn `x` to different values (no input simulation needed - each value is a
fresh, real, num-controlled starting position) and rebuilding:
1. `x = 460.0` (near the crate/truck) and `x = 350.0`/`x = 10.0` (open ground, no crate/truck nearby)
   all showed the same torn artifact in roughly the same *screen* position - looked suspiciously
   fixed, until the camera-centering formula in `GameplayScene.kt` (`desiredWorldViewX = halfScreen -
   playerCenterX * worldZoom`, clamped to `[minWorldViewX, 0]`) was worked through: 350 and 10 both
   land in the clamped region (`worldView.x = 0`), so those two were never actually a fair test of
   "does this track world position" - only 460 differs from them in camera offset, and only by
   ~125 virtual units, easy to mistake for "no movement" by eye.
2. `worldView.visible = false` made the artifact disappear entirely, proving it was inside the
   world-space layer, not a screen-fixed HUD element as step 1's flawed comparison suggested.
3. `x = 1500.0` (well past the truck, unclamped) showed a completely clean scene - hanging crate,
   long platform, no artifact - proving the artifact was tied to a fixed *world* position after all,
   somewhere in the crate/truck cluster.
4. Forcing the truck's render branch to draw nothing (`if (false)` around the draw call) removed the
   artifact completely with the player back at `x = 460.0`. This is the smoking gun: the truck
   rendering code itself was producing it.
5. Re-enabling the draw but dropping just the flip (`truckImg.xy(truckRect.x, truckRect.y)` instead
   of `xy(x + width, y)` + `scaleX = -1.0`) rendered the truck perfectly - correct silhouette, right
   position, cab and wheels all present - just mirrored the wrong way (bed on the left instead of
   the hood). Flip math alone was therefore the entire bug.

**Root cause**: `View.size(width, height)` (`korlibs/korge/view/View.kt`) sets `unscaledSize`
directly; actual displayed size is `unscaledWidth * scaleX`. Setting `scaleX = -1.0` afterward is
supposed to just mirror the image about its own left edge (compensated for by positioning at
`x + width` first, exactly as `sizeScaled`-style KorGE code elsewhere does) - and this reasoning is
correct on paper, confirmed by working through the actual property definitions in the library
source (extracted from `korge-core-6.0.0-sources.jar` in the Gradle cache to check, since guessing
at engine internals is exactly the kind of thing this file exists to warn against doing without
verifying). The corruption is not a logic bug in this codebase - it's the GL_VERSION=3.3.0 NVIDIA/
OpenGL rendering backend on this machine mishandling a negative-scaleX `Image` draw once the source
texture has significant fine transparent/opaque detail at extreme downscale (truck.png: 1683x617
tight-cropped, rendered at 262x96 - roughly 6.4x reduction, with a wheel/axle area full of thin
transparent gaps). Large, simple-silhouette images (crate, barrel, fence) apparently survive the
same negative-scaleX path fine, or at least never visibly failed - the truck was the first asset
detailed enough, at a small enough render size, to expose it. `entrance.png`'s exit-booth flip uses
the identical `scaleX = -1.0` pattern and was very likely equally broken; it had simply never been
walked to and looked at this session (the pipeline that could have shown it wasn't working until
today). Given a real engine/driver-level rendering bug on negative scale, the fix is to never rely
on it: **both `truck.png` and `entrance.png` are now pre-mirrored on disk** (PowerShell
`Bitmap.RotateFlip(RotateFlipType.RotateNoneFlipX)`, saved back in place) and drawn with plain
`.xy(x, y)` - no runtime `scaleX` flip anywhere in either render path anymore. Confirmed by rebuild +
real screenshot: truck renders as a solid, correctly-oriented silhouette (hood facing the crate,
cab and bed intact, wheels intact) with the player standing right beside it, no artifact.

**Follow-up, same conversation**: with the truck fixed, the user (watching the same desktop) saw the
now-working exit booth and asked for two more real, visually-driven adjustments - the booth was too
large, and the fence that used to stand beside it (part of the original, wider `entrance.png` source
composite before this session cropped it down to just the booth) was gone. `entranceHeight` dropped
200 -> 135 (200 was sized for when the whole fence+crates+booth composite filled that height; alone,
just the booth, it read as a wall relative to the player). For the fence: the *original*, uncropped
`entrance.png` still existed untouched at
`C:\Users\USER\Downloads\charAnimations\assets\entrance.png` (only the copy in `resources/` had been
cropped), so rather than reusing the level's own start-of-corridor `fence2.png` (the user's first
ask, tried and explicitly rejected: "use the fences that was in the original entrance.png"), a fresh
`resources/exitfence.png` was cropped straight from that original source - same column-density-scan
technique as finding the fence/booth seam earlier (chain-link content count ~155 per sampled column
drops to ~13-29 in the gap before the booth's own post at x≈1094-1104, in the original 1672x941
file), yielding a 1039x466 crop of just the fence-with-baked-crates portion, excluding the booth.
Rendered after the booth (`world.exitZone.x + entranceWidth`, matching the user's explicit "the
fences should be after that building"), at `exitFenceHeight = 140.0` - the exact height the level's
*own* starting fence uses - via its own aspect ratio, not stretched.

**Verification**: `:compileKotlinJvm` and full `jvmTest` green throughout every step of this
investigation. Unlike every other round this session, the actual visual fixes themselves - truck
solid and correctly oriented, booth resized, fence repositioned with the original asset - were
**directly confirmed on the real running window** via the Win32-screenshot technique above, not just
reasoned about. That technique (real `runJvm` window + real Win32 capture, varying `GameWorld.kt`
spawn position instead of simulating input) is a viable, repeatable way to get real visual
verification in this sandbox going forward, superseding the "unreliable, stale frames" conclusion
reached earlier this session with the input-simulation approach - the difference was never
capturing the window, it was trying to simulate live input into it.
running screen - same standing caveat as every round this session.

## Web landing page, /support Netlify form, and /privacy policy (2026-09-06)

Created a dedicated `site/` directory containing a mobile-responsive, cyber-tactical static web presence
configured for Netlify deployment (e.g. for `infiltrate.saysplit.app` or any Netlify subdomain):

- `site/index.html`: One-screen hero landing page featuring official game branding (`assets/logo_main.png`),
  stealth heist tagline, platform availability pills (iOS App Store & Google Play), core tactical feature
  briefing cards (Vision AI, Parkour, 12 Shipyard Levels, Gadgets), and navigation links.
- `site/support/index.html`: Dedicated Support page meeting Apple App Store Guideline 1.5. Includes an
  integrated **Netlify Form** (`data-netlify="true"`, honeypot spam protection via `bot-field`, action redirect
  to `/support/success/`, fields for Name, Email, Category, Message). **Correction (2026-09-06): this note
  previously also claimed Platform/Subject fields and a visible developer contact email on this page — neither
  is actually in the file**, checked directly; the form only has the four fields listed above and shows no email
  address anywhere. Don't trust this bullet's older description of that page without re-reading the file.
  The support/contact address used elsewhere (privacy policy) is `infiltrate@saysplit.app` (changed 2026-09-06
  from `support@saysplit.app`, owner's choice, before that address was ever wired up anywhere else) - player FAQs
  (restoring purchases, offline play, bug reporting) are NOT actually on this page either, same correction.
- `site/support/success/index.html`: Form submission confirmation page ("Transmission Received").
- `site/privacy/index.html`: Store-compliant Privacy Policy (rewritten 2026-09-06, checked against the actual
  codebase rather than assumed) covering on-device data storage (`NSUserDefaults`/`SharedPreferences`), Google
  AdMob & Google UMP consent, RevenueCat purchase receipt validation, COPPA/GDPR/CCPA rights (general audience,
  13+), and opt-out instructions for Apple ad personalization and Android GAID. **Updated again same day, later
  session**: now also lists Layers (event analytics/attribution) as a real third-party service, since it's now
  actually integrated on Android - see "Layers Events SDK integration" further below for the full story. Before
  that, this note went through a wrong-then-corrected cycle worth remembering: an earlier pass claimed the policy
  already covered "Layers SDK analytics/attribution" when no such SDK existed in the repo at all; that was then
  corrected to "planned, not yet integrated, update the policy once it lands"; Layers is now real, so the policy
  has been updated again, closing that loop. **Still open, found while doing this update**: the existing "Purchase
  data, via RevenueCat and the app stores" paragraph in this same file describes Play Billing/RevenueCat as
  already validating real purchases - untrue as of today, see "Store screen has no real purchase flow yet" below.
  That paragraph was NOT rewritten this session (payment-processing language is consequential enough to want the
  owner's confirmation first) - flagged to the owner, not yet actioned.
  **Separately, a real (not yet fixed) compliance gap found while originally writing this policy**: Apple's App
  Tracking Transparency prompt (`AppTrackingTransparency`/`ATTrackingManager`) is not implemented anywhere in the
  iOS code, even though AdMob is embedded and can serve personalized ads. If personalized ads ship on iOS without
  that prompt, that's an App Store risk, not just a docs gap — needs a decision (add the ATT flow, or force
  non-personalized ads on iOS) before submission.
- `site/styles.css`: Shared responsive CSS matching the game's neon cyan (`#00f0ff`), deep obsidian (`#070a0f`),
  and stealth gold aesthetic with Rajdhani/Inter/Bebas Neue typography.
- `site/_redirects` & `site/netlify.toml`: Netlify configuration ensuring clean URL routing (`/support`, `/privacy`)
  and enforcing security headers (`X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`).

## Layers Events SDK integration (2026-09-06) — Android only, real, compiled and linked into the APK

The owner was given an integration guide with credentials (App ID `app_a1f9dbc126c1c779`) for a "Layers Events
SDK." **Before writing any code, verified the guide itself was accurate** rather than trusting it - a good thing,
since the exact Maven coordinates it gave (`com.layers.sdk:layers-android:3.2.11`) don't match Layers' own current
docs. `github.com/layers`'s public `layers-sdk-android` repo (a red herring, checked and ruled out - stale/
unofficial) claims `io.layers:layers-android:2.0.0`; the real product site, `layers.com/docs/sdk/installation`,
confirms the owner's guide was accurate on everything except the version number (`3.3.0` is current, `3.2.11` was
one minor version behind - normal docs drift, not a fake package). **Lesson**: a company's own GitHub org isn't
automatically its authoritative SDK source - the product's own docs site was what actually confirmed this.

### Architecture: same expect/actual bridge pattern as ContinueAdBridge/LevelExitBridge/PurchasesBridge

New `AnalyticsBridge` (`src/AnalyticsBridge.kt`, `interface AnalyticsBridge { fun track(event, properties) }` +
`expect fun getAnalyticsBridge()`), so `GameplayScene.kt` (common `:game` code) can fire events without depending
on an Android-only artifact. Actuals: `src@android/AnalyticsBridge.android.kt`, `src@ios/`, `src@jvm/`, `src@js/`,
`src@wasmJs/` (the last four are no-op stubs - Layers is Android-only for now). Plus the usual plain (non-KMP)
duplicate in `android-shell/.../AnalyticsBridge.kt`, for the same reason `ContinueAdBridge`/`LevelExitBridge` each
have one there (android-shell compiles `GameplayScene.kt` from source directly, not through `:game`'s expect/actual
mechanism - see android-shell/build.gradle.kts's sourceSets comment).

**One real deviation from that pattern, found by actually trying to compile it**: `src@android/AnalyticsBridge.android.kt`
does NOT call `com.layers.sdk.android.LayersAndroid` directly - it's a no-op, same as the other no-op stubs. First
attempt did call it directly (mirroring `PurchasesBridge.android.kt`), and adding
`com.layers.sdk:layers-android:3.3.0` to root `build.gradle.kts`'s `androidMainApi` broke `:game`'s own separate
Android target: the real SDK transitively pulls `androidx.lifecycle:*:2.7.0` and `androidx.work:work-runtime-ktx:2.9.0`,
both of which require `compileSdk 34+`, while `:game`'s own build stays on `compileSdk 33` (confirmed via
`:checkDebugAarMetadata` - 10 AAR-metadata errors, all the same "requires compile against version 34 or later").
Bumping `:game`'s own compileSdk was ruled out as a bigger, unrelated, unrequested change. Fix: revert the
root-build dependency entirely, make `src@android`'s actual a genuine no-op, and let the real work happen only in
`android-shell`'s own duplicate copy (`compileSdk 37`, no conflict) - which is what actually executes in the
shipped app regardless, since that module compiles `GameplayScene.kt` from source into its own build. This mirrors
`ContinueAdBridge`'s real Android actual, which was already deliberately callback-only with zero direct `basic-ads`
import for exactly this kind of reason (checked it for precedent before designing this).

### `InfiltrateApplication.kt` - android-shell's first-ever `Application` subclass

Before this, android-shell had no custom `Application` - `MainActivity.onCreate()` stood in for process startup.
Layers' own docs initialize in `Application.onCreate()` specifically (guaranteed to run once, before any Activity),
so this added `InfiltrateApplication` and registered it via `android:name=".InfiltrateApplication"` in
`AndroidManifest.xml`.

**Two deliberate deviations from the vendor's copy-paste snippet, both found by decompiling the real
`layers-android-3.3.0.aar` rather than trusting the docs/prompt at face value:**
- No manual `LayersAndroid.track("app_open")` call. Decompiled `LayersConfigBuilder`'s real constructor bytecode
  (`javap -c`) and found `autoTrackAppOpen` defaults to `true` - the SDK already sends this event itself. The
  vendor's own integration guide told the owner to track `app_open` manually; doing so would have double-counted
  every single launch. This is a genuine drift between the docs and the real default, not a mistake in the guide
  as given - worth remembering if any other "auto-tracks X" claim from the same docs needs verifying later.
- `environment` is picked from `applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE`, not hardcoded to
  `Environment.PRODUCTION` the way the vendor's snippet does. Hardcoding it would report every local debug build as
  real production data. `enableDebug` mirrors the same flag (matches the vendor's own advice to enable it during
  development).

**Also decompiled, confirmed, and deliberately NOT changed**: `automaticExceptionTrackingEnabled` defaults to
`true` (crash/exception diagnostic data collected by default - now disclosed in the privacy policy) and
`consentRequired` defaults to `false` (the SDK does not gate tracking on consent unless told to). Leaving
`consentRequired` alone was a deliberate choice, not an oversight: flipping it to `true` would silently drop every
event until a consent-collection UI exists, which this game doesn't have yet - that's a real product decision for
the owner to make, not something to change unilaterally while wiring up the SDK. Revisit if/when a consent flow is
built, or if EEA/UK distribution specifically requires it sooner.

### Real events wired into `GameplayScene.kt` (all backed by genuine gameplay signals, not fabricated)

- `watch_ad_continue_requested` / `watch_ad_continue_granted` - at the existing `getContinueAdBridge()` call sites
  (CONTINUE (WATCH AD) button, and the update-loop branch that consumes a granted continue).
- `level_complete` - inside `world.onLevelComplete`, with `level_id`, `stars`, `time_taken_seconds`, `alerts`.
- `mission_failed` - inside `world.onGameOver`, with `level_id`, `alerts`.

**Deliberately NOT implemented**: `sign_up` / `login` (this game has no account/auth system anywhere - would have
to be fabricated) and `purchase_success` / `subscription_start` / `trial_start` (see next section - there is no
real purchase flow to hang these off yet). The owner explicitly chose "skip purchase events for now" over the
alternative of firing a fake/zero-revenue event, when this was raised.

### Store screen has no real purchase flow yet - found while scoping the purchase events above, not new

`paywall-build/src/commonMain/kotlin/ui/StoreScreen.kt`'s `onPurchase` handler is `{ pack -> profileStorage.addCoins(pack.amount) }`
- no RevenueCat call, no Play Billing call, nothing. This matches what the RevenueCat sections elsewhere in this
file already say (`PurchasesBridge` is still a stub, `purchase()` returns `onResult(false)`), but is worth stating
plainly here since it's exactly why `purchase_success` can't be wired up honestly yet: firing that event with the
vendor's own example payload (`revenue: 9.99`) off a flow that hands out free coins would report fabricated revenue
to Layers. **This also means `site/privacy/index.html`'s existing "Purchase data, via RevenueCat and the app
stores" paragraph is inaccurate as written** (describes real Play Billing/RevenueCat validation) - flagged to the
owner, not rewritten yet (see the privacy-policy bullet above).

### Verification

`:compileKotlinJvm` (root, common code + JVM actuals) - **BUILD SUCCESSFUL**. `:checkDebugAarMetadata` (`:game`'s
own Android target) - **BUILD SUCCESSFUL**, confirming the compileSdk-34 conflict is genuinely gone after the
revert. `android-shell`'s `compileDebugKotlin` - **BUILD SUCCESSFUL**. `android-shell`'s `assembleDebug` (full APK,
not just compile) - **BUILD SUCCESSFUL**, and the packaged output includes `liblayers_core.so` (the SDK's real
Rust-compiled core), confirmed via the `stripDebugDebugSymbols` task log line naming it - i.e. the real native
library is genuinely linked into the built APK, not just present on the Java/Kotlin classpath.
**Not yet done, same standing caveat as every other entry in this file**: never run on a real device/emulator -
whether `configure()` actually connects to Layers' backend, whether events actually arrive in their dashboard, and
whether `enableDebug`'s log output looks sane are all unverified. iOS: not attempted at all (the vendor's own
guide split this into an Android section and an "iOS only - App Tracking Transparency" bullet; scope for this pass
was Android only, matching that split).

## REVERTED: `/delete` data-deletion page - built, then removed same day (2026-09-06)

Briefly existed as `site/delete/index.html` + `site/delete/success/index.html`, built to satisfy Google Play Data
Safety's "Delete data URL" field (that field requires a real, live URL that prominently states the steps, the data
types deleted/kept, and a retention period). While reviewing the copy, the owner caught a real accuracy problem
worth remembering: the form asked for an email address as if to "locate and delete matching records," but the game
collects no email anywhere except a prior Support-page submission - for the overwhelming majority of players (who
never contacted Support) there is nothing on file to find, making the form misleading as originally drafted even
after a first correction pass.

**Owner's decision: remove the page entirely and answer Play Console's "Do you provide a way for users to request
that their data is deleted?" as No**, rather than keep maintaining a dedicated deletion flow for data that, in
practice, almost never exists to delete. Reverted:
- `site/delete/` (both files) deleted outright.
- `site/_redirects`' two `/delete` routing lines removed.
- `site/privacy/index.html`'s section 11 ("How to Request Deletion of Your Data", `id="delete-your-data"`) removed
  entirely, not just unlinked - keeping it would have left the privacy policy claiming a dedicated deletion
  mechanism the Play Store listing now says doesn't exist, the same kind of drift this file has flagged repeatedly
  elsewhere. "12. Contact Us" renumbered back down to "11.".

**Not reverted, and correctly so**: Section 6 ("Your Rights (EEA/UK/Switzerland and California)") still says "we're
glad to help route any request sent to us" - this is generic GDPR/CCPA rights boilerplate, not a claim of a
dedicated deletion flow, and remains legally accurate independent of the Play Store checkbox answer (a user can
still email support and ask; there's just no purpose-built page promising a formal 30-day process anymore).

If a genuine, defensible deletion flow is wanted later, the honest version of it should center on what's actually
true: for most players there is nothing to delete (all game data is local-only, deleted by uninstalling), and the
only real lever is a prior Support submission - any future page should lead with that instead of implying a
database lookup that mostly doesn't exist.
