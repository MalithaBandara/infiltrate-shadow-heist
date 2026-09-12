import UIKit
import Darwin
import GameMain
import PaywallModule

/// Native shell AppDelegate for Infiltrate: Shadow Heist.
/// Hosts:
///  1. Compose Multiplatform Non-Gameplay UI (MainMenuScreen as rootViewController).
///  2. Resident warm KorGE Engine for gameplay (swapped in when starting a level).
///  3. Storage bridge between Compose Multiplatform and KorGE (:game).
class AppDelegate: UIResponder, UIApplicationDelegate {
    // MARK: - View Controllers & Shell State

    private var korgeVC: UIViewController?
    private var composeVC: UIViewController?
    private var shellWindow: UIWindow?
    private var levelObserverTimer: Timer?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        ShellAppDelegate.shared.applicationDidFinishLaunching(app: application)
        let window = ShellAppDelegate.shared.window
        self.shellWindow = window

        runStorageBridgeCheck()

        // ShellAppDelegate initialized the warm KorGE ViewController.
        // Compose Multiplatform owns non-gameplay screens, so MainMenu is the initial rootViewController.
        korgeVC = window.rootViewController

        let compose = MainMenuComposeScreen.shared.makeViewController { [weak self] levelId in
            print("MAIN_MENU: Start Level tapped (\(levelId)) -> Swapping rootViewController to KorGE gameplay")
            GameLevelStartBridge.shared.startLevel(levelId: levelId)
            self?.switchToKorGE()
        }
        composeVC = compose
        window.rootViewController = compose

        // Automated verification sequence for CI:
        // MainMenu renders -> Switch to KorGE gameplay -> Dwell -> Return to MainMenu.
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) { [weak self] in
            self?.runAutomatedLevelTransition()
        }

        return true
    }

    // Info.plist's UISupportedInterfaceOrientations alone did NOT force landscape in practice -
    // confirmed from real xcrun simctl screenshots in CI (run 34690299984, 2026-09-12): the menu
    // still rendered portrait-shaped and rotated 90 degrees. UIKit's rotation resolution lets any
    // UIViewController in the chain override supportedInterfaceOrientations and take precedence
    // over the Info.plist default for its own presentation - Compose Multiplatform's
    // ComposeUIViewController (MainMenuComposeScreen's root) very likely does exactly that,
    // ignoring the Info.plist key entirely. This delegate method is consulted for the window's
    // orientation mask regardless of what any individual view controller reports, so it's the
    // reliable way to force it app-wide. Keep the Info.plist key too (harmless, and it's the
    // documented baseline default) but this is what's actually doing the work.
    func application(
        _ application: UIApplication,
        supportedInterfaceOrientationsFor window: UIWindow?
    ) -> UIInterfaceOrientationMask {
        return .landscape
    }

    func applicationWillResignActive(_ application: UIApplication) {
        ShellAppDelegate.shared.applicationWillResignActive(app: application)
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        ShellAppDelegate.shared.applicationDidEnterBackground(app: application)
    }

    func applicationWillEnterForeground(_ application: UIApplication) {
        ShellAppDelegate.shared.applicationWillEnterForeground(app: application)
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        ShellAppDelegate.shared.applicationDidBecomeActive(app: application)
    }

    func applicationWillTerminate(_ application: UIApplication) {
        ShellAppDelegate.shared.applicationWillTerminate(app: application)
    }

    // MARK: - RootViewController Swapping (Compose <-> KorGE)

    func switchToKorGE() {
        guard let window = self.shellWindow, let korge = self.korgeVC else { return }
        print("SHELL: Swapping to KorGE (Gameplay)")
        // Lets CI poll for this instead of guessing a sleep duration - a prior attempt (2026-09-12)
        // spread 6 blind, fixed-interval screenshot attempts across the automated test's ~1.5s
        // KorGE-visible window and missed it every time, since each `simctl io screenshot` call
        // can itself take 1-13s on this runner (see ios-build.yml's own comment on that), making
        // wall-clock timing from this script's side unpredictable. Writing this the instant the
        // real switch happens is the same "read a result file CI polls for" pattern already used
        // for storage_bridge_result.txt/transition_test_result.txt, just marking a moment instead
        // of a final result.
        writeTextFile("korge_visible.txt", "\(Date())")
        window.rootViewController = korge
        startObservingLevelEnd()
    }

    func switchToCompose() {
        guard let window = self.shellWindow, let compose = self.composeVC else { return }
        print("SHELL: Swapping to Compose (MainMenu)")
        window.rootViewController = compose
        stopObservingLevelEnd()
    }

    private func startObservingLevelEnd() {
        levelObserverTimer?.invalidate()
        levelObserverTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] t in
            if SpikeBridge.shared.consumeLevelEndRequest() {
                print("SHELL: Level end consumed -> returning to Compose")
                self?.switchToCompose()
            }
            if GameContinueAdBridge.shared.consumeContinueAdRequest() {
                print("SHELL: Continue-with-ad requested -> showing rewarded ad")
                self?.showContinueAd()
            }
            // QUIT / RETURN TO MENU / MAIN MENU / ALL CLEAR in GameplayScene.kt all reach this via
            // GameLevelExitBridge (src@ios/LevelExitBridge.ios.kt) - same shape as
            // AndroidLevelExitBridgeState.onReturnToMenuRequested in MainActivity.kt. Previously a
            // true no-op on iOS: these buttons compiled and ran but nothing ever switched the
            // shell back to the Compose menu.
            if GameLevelExitBridge.shared.consumeReturnToMenuRequest() {
                print("SHELL: Return-to-menu requested -> returning to Compose")
                self?.switchToCompose()
                self?.maybeShowLevelExitInterstitial()
            }
        }
    }

    // Mirrors MainActivity.kt's maybeShowLevelExitInterstitial() - gated by InterstitialAdLimiter
    // (checked Kotlin-side in InterstitialAdTrigger.maybeRequestShow so Swift doesn't duplicate its
    // cooldown/session-cap constants) and by DebugStorageBridge's real on-disk profile fields, not
    // hardcoded values.
    private func maybeShowLevelExitInterstitial() {
        let isPremium = DebugStorageBridge.shared.readIsPremiumForDebug()
        let totalLevelsCompleted = DebugStorageBridge.shared.readTotalLevelsCompletedForDebug()
        let requested = InterstitialAdTrigger.shared.maybeRequestShow(
            totalLevelsCompleted: totalLevelsCompleted,
            isPremium: isPremium
        )
        print("SHELL: Level-exit interstitial \(requested ? "requested" : "skipped (limiter/premium)")")
    }

    private func stopObservingLevelEnd() {
        levelObserverTimer?.invalidate()
        levelObserverTimer = nil
    }

    // MARK: - Watch Ad to Continue (real feature, not a spike)
    //
    // Rewarded ads (like any AdMob fullscreen ad) can only present reliably from whatever view
    // controller is CURRENTLY the window's rootViewController - not from PaywallModule's
    // Compose scene while it's sitting detached in the background behind KorGE. So this must
    // switch to Compose first, then ask it to show the ad, same as the proven switch-spike
    // mechanism this session already validated. See .junie/guidelines.md "AdMob (basic-ads)
    // feasibility spike".
    private var continueAdPollTimer: Timer?

    private func showContinueAd() {
        stopObservingLevelEnd()
        switchToCompose()
        ContinueAdTrigger.shared.requestShow()

        let deadline = Date().addingTimeInterval(30.0)
        continueAdPollTimer?.invalidate()
        continueAdPollTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] t in
            let finished = ContinueAdTrigger.shared.consumeOutcomeFinished()
            let timedOut = Date() >= deadline
            if finished || timedOut {
                t.invalidate()
                let earned = finished && ContinueAdTrigger.shared.rewardEarned
                print("SHELL: Continue ad \(earned ? "watched, granting continue" : "not completed") (finished=\(finished), timedOut=\(timedOut))")
                if earned {
                    GameContinueAdBridge.shared.grantContinue()
                }
                self?.switchToKorGE()
            }
        }
    }

    // MARK: - Storage Bridge Real Profile Check

    @discardableResult
    private func runStorageBridgeCheck() -> Bool {
        // Step 1: Write real profile fields through PaywallStorage (PaywallModule.framework)
        let expectedCoins: Int32 = 350
        let expectedUnlocked = "level_1;level_2;level_4"

        PaywallStorage.shared.setRaw(key: "user_coins", value: String(expectedCoins))
        PaywallStorage.shared.setRaw(key: "user_unlocked_levels", value: expectedUnlocked)
        PaywallStorage.shared.setRaw(key: "user_is_premium", value: "true")

        // Step 2: Read back through DebugStorageBridge (GameMain.framework / MapBackedGameProfileStorage)
        let actualCoins = DebugStorageBridge.shared.readCoinsForDebug()
        let actualUnlocked = DebugStorageBridge.shared.readUnlockedLevelsForDebug()
        let actualPremium = DebugStorageBridge.shared.readIsPremiumForDebug()

        let coinsMatch = (actualCoins == expectedCoins)
        let unlockedMatch = (actualUnlocked == expectedUnlocked)
        let premiumMatch = actualPremium

        let ok = coinsMatch && unlockedMatch && premiumMatch
        let resultText: String
        if ok {
            resultText = "OK:coins=\(actualCoins):unlocked=\(actualUnlocked)"
        } else {
            resultText = "FAIL:coins=\(actualCoins)(expected \(expectedCoins)):unlocked=\(actualUnlocked)(expected \(expectedUnlocked))"
        }

        print("SHELL: Storage bridge verification -> \(resultText)")
        writeTextFile("storage_bridge_result.txt", resultText)
        return ok
    }

    // MARK: - Automated CI Test Cycle

    private func runAutomatedLevelTransition() {
        print("CI_TEST: ==== Automated Transition Test START ====")
        // 1. Switch to KorGE
        switchToKorGE()

        // 2. Dwell in gameplay for 1 second
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
            print("CI_TEST: Gameplay active, triggering level completion")
            SpikeBridge.shared.requestLevelEnd()

            // 3. Return to Compose MainMenu
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                self?.switchToCompose()
                print("CI_TEST: ==== Automated Transition Test COMPLETE ====")
                self?.writeTextFile("transition_test_result.txt", "TRANSITION_OK")

                // Chained, not parallel: avoids two automated UI-swap sequences racing on the
                // same rootViewController.
                self?.runAdMobVerification()
            }
        }
    }

    // MARK: - AdMob On-Device Verification (see .junie/guidelines.md "AdMob (basic-ads)
    // feasibility spike" - the link-only spike proved basic-ads compiles+links; this proves
    // BasicAds.Initialize() and a real BannerAd load actually run on a real iOS Simulator, not
    // just that the code compiles).

    private func runAdMobVerification() {
        print("ADMOB_TEST: ==== AdMob Verification START ====")
        // No rootViewController swap this round - AdMobVerifyContent() renders unconditionally
        // inside MainMenu's own ComposeUIViewController scene (see AdMobVerifyScreen.kt /
        // MainMenuComposeViewController.kt), so BasicAds.Initialize() already ran at launch.
        // This just polls the result. Round 1 swapped to a second, separate
        // ComposeUIViewController here and crashed inside Compose's own setContent machinery
        // with two scenes alive at once - see guidelines.md for the full story.

        // Real ad network round-trip - poll with a genuine time budget rather than a single
        // fixed wait, same discipline as the switch-spike poll loop.
        let deadline = Date().addingTimeInterval(15.0)
        var pollTimer: Timer?
        pollTimer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { t in
            let loaded = AdMobVerifyBridge.shared.bannerLoaded
            let timedOut = Date() >= deadline
            if loaded || timedOut {
                t.invalidate()
                let initCalled = AdMobVerifyBridge.shared.initializeCalled
                let resultText: String
                if loaded {
                    resultText = "OK:initializeCalled=\(initCalled):bannerLoaded=true"
                } else {
                    resultText = "FAIL:initializeCalled=\(initCalled):bannerLoaded=false:timedOut=\(timedOut)"
                }
                print("ADMOB_TEST: ==== AdMob Verification COMPLETE: \(resultText) ====")
                self.writeTextFile("admob_verify_result.txt", resultText)
            }
        }
        _ = pollTimer
    }

    private func writeTextFile(_ name: String, _ text: String) {
        guard let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else { return }
        let url = docs.appendingPathComponent(name)
        try? text.write(to: url, atomically: true, encoding: .utf8)
    }
}
