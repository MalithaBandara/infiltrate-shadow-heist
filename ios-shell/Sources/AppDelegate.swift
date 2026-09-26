import UIKit
import Darwin
import StoreKit
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
        // BEFORE ShellAppDelegate, which boots KorGE on this very line: Korge()'s virtualSize is
        // read at construction, and a canvas that does not match this screen's aspect is
        // letterboxed for the whole session (38% of a 4:3 iPad went to black bars before this).
        // No window exists yet, so only the size can be measured here - the safe area follows
        // below, once the window is laid out. See GameScreenMetricsBridge / game.model.ScreenLayout.
        publishScreenMetrics()

        ShellAppDelegate.shared.applicationDidFinishLaunching(app: application)
        let window = ShellAppDelegate.shared.window
        self.shellWindow = window

        // Initialize StoreBilling (RevenueCat In-App Purchases) for iOS
        StoreBilling.shared.initialize(apiKey: "appl_jnRvGBajbaDGqSLhCCdvqvwsaHs")

        // ShellAppDelegate initialized the warm KorGE ViewController.
        // Compose Multiplatform owns non-gameplay screens, so MainMenu is the initial rootViewController.
        korgeVC = window.rootViewController

        let compose = MainMenuComposeScreen.shared.makeViewController { [weak self] levelId in
            print("MAIN_MENU: Start Level tapped (\(levelId)) -> Swapping rootViewController to KorGE gameplay")
            // Before startLevel, not after: startLevel is what builds the GameplayScene, and a
            // Scene takes its canvas size once, when it is created.
            self?.refreshScreenMetrics()
            GameLevelStartBridge.shared.startLevel(levelId: levelId)
            self?.switchToKorGE()
        }
        composeVC = compose
        window.rootViewController = compose
        window.makeKeyAndVisible()
        // Key and visible, so safeAreaInsets is real now: in landscape that is the Dynamic
        // Island / notch on one side and the home indicator along the bottom, which the HUD's
        // D-pad and jump cluster used to sit partly underneath.
        publishSafeArea()

        // Automated verification sequence for CI:
        // MainMenu renders -> Switch to KorGE gameplay -> Dwell -> Return to MainMenu.
        // Gated behind -ci-test flag so it never auto-transitions or overwrites user storage on real devices or TestFlight.
        if CommandLine.arguments.contains("-ci-test") {
            runStorageBridgeCheck()
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) { [weak self] in
                self?.runAutomatedLevelTransition()
            }
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
        // Stops the level clock for as long as the player is somewhere else - see
        // src/AppLifecycleBridge.kt. KorGE's own loop does stop while its view is out of the
        // window here, but that is an engine detail this app should not be betting its scoring on,
        // and the same flag is what Android (where the loop genuinely keeps running) relies on.
        GameAppLifecycleBridge.shared.markBackground()
        ShellAppDelegate.shared.applicationDidEnterBackground(app: application)
    }

    func applicationWillEnterForeground(_ application: UIApplication) {
        ShellAppDelegate.shared.applicationWillEnterForeground(app: application)
        GameAppLifecycleBridge.shared.markForeground()
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        ShellAppDelegate.shared.applicationDidBecomeActive(app: application)
    }

    func applicationWillTerminate(_ application: UIApplication) {
        ShellAppDelegate.shared.applicationWillTerminate(app: application)
    }

    // MARK: - Screen Metrics (canvas size + safe area, consumed by game.model.ScreenLayout)

    /// Publishes the screen size in points. Called before KorGE boots (when only UIScreen exists)
    /// and again on every switch into gameplay, where the window itself is the better measurement
    /// - it reflects the settled orientation, which UIScreen.bounds does not always do during the
    /// first moments of launch. Landscape is assumed on the Kotlin side, so which of the two
    /// numbers is the larger one does not matter.
    ///
    /// Published to BOTH frameworks: GameMain and PaywallModule each compile their own copy of
    /// `game.model.DeviceScreen`, so one publish reaches only one of them (see
    /// MenuScreenMetricsBridge's own note). Android has a single process-wide copy and does this
    /// once, in MainActivity.
    private func publishScreenMetrics() {
        let bounds = shellWindow?.bounds ?? UIScreen.main.bounds
        GameScreenMetricsBridge.shared.publishScreenSize(
            widthPt: Double(bounds.width),
            heightPt: Double(bounds.height)
        )
        MenuScreenMetricsBridge.shared.publishScreenSize(
            widthPt: Double(bounds.width),
            heightPt: Double(bounds.height)
        )
    }

    /// Both halves, for the moments where the window is known to be laid out and settled.
    private func refreshScreenMetrics() {
        publishScreenMetrics()
        publishSafeArea()
    }

    /// Publishes `window.safeAreaInsets`. Zero until the window has been laid out, which is why
    /// this is separate from the size above.
    private func publishSafeArea() {
        guard let insets = shellWindow?.safeAreaInsets else { return }
        GameScreenMetricsBridge.shared.publishSafeArea(
            leftPt: Double(insets.left),
            topPt: Double(insets.top),
            rightPt: Double(insets.right),
            bottomPt: Double(insets.bottom)
        )
        MenuScreenMetricsBridge.shared.publishSafeArea(
            leftPt: Double(insets.left),
            topPt: Double(insets.top),
            rightPt: Double(insets.right),
            bottomPt: Double(insets.bottom)
        )
    }

    // MARK: - RootViewController Swapping (Compose <-> KorGE)

    func switchToKorGE() {
        guard let window = self.shellWindow, let korge = self.korgeVC else { return }
        print("SHELL: Swapping to KorGE (Gameplay)")
        // Also done in the start-level closure in didFinishLaunching, which runs a moment earlier
        // - this covers the CI path (runAutomatedLevelTransition) that calls switchToKorGE direct.
        refreshScreenMetrics()
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
        window.makeKeyAndVisible()
        startObservingLevelEnd()
    }

    func switchToCompose() {
        guard let window = self.shellWindow, let compose = self.composeVC else { return }
        print("SHELL: Swapping to Compose (MainMenu)")
        window.rootViewController = compose
        window.makeKeyAndVisible()
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
            if GameInAppReviewBridge.shared.consumeReviewRequest() {
                print("SHELL: In-app review requested -> requesting review")
                InAppReviewHelper.requestReview()
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
        // Same reason as applicationDidEnterBackground above: gameplay is about to leave the
        // window for the length of a rewarded ad, and none of that is time the player was playing.
        GameAppLifecycleBridge.shared.markBackground()
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
                GameAppLifecycleBridge.shared.markForeground()
                self?.switchToKorGE()
            }
        }
    }

    // MARK: - Storage Bridge Real Profile Check

    @discardableResult
    private func runStorageBridgeCheck() -> Bool {
        // Step 1: Write real profile fields through PaywallStorage (PaywallModule.framework)
        let expectedCoins: Int32 = 350
        // Written as "level_1;level_2;level_4", but GameProfile.kt's loadFromStorage() (commonMain,
        // shared with Android) merges whatever it reads INTO the profile's default unlocked set
        // rather than replacing it - and level_5 (the side-scrolling sample level) is one of those
        // defaults, "unlocked from the start" by design (see GameProfile.kt's own comment on
        // unlockedLevelIds). That's very likely intentional - it's what grandfathers level_5 in for
        // existing Android players whose saved progress predates it - so this expectation reflects
        // the real, intended round-trip result instead of the literal written string. Confirmed via
        // real on-device CI output (2026-09-12): read-back was
        // "level_1;level_2;level_4;level_5" (readUnlockedLevelsForDebug() sorts alphabetically).
        let expectedUnlocked = "level_1;level_2;level_4;level_5"

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

        // 2. Dwell in gameplay until CI acknowledges it captured a screenshot during this window
        // (screenshot_taken.txt), rather than a fixed short delay. A fixed ~1.5s dwell (this used
        // to be requestLevelEnd() after 1.0s + a further 0.5s before switching back) was too short
        // for CI's own `xcrun simctl io screenshot` call to complete before the view switched back
        // - confirmed empirically (2026-09-12): that command alone can take 1-13s on the runner
        // (see ios-build.yml), well past a 1.5s window, so gameplay_check.png kept capturing the
        // main menu again by the time the screenshot actually got taken, even though the trigger
        // (korge_visible.txt) fired at exactly the right moment. Polling for an ack avoids both
        // guessing a longer fixed dwell that still might not be enough, and stalling forever if
        // CI's screenshot step is ever skipped/fails.
        //
        // The original 20s hard cap was ALSO too short - confirmed from a real run (2026-09-12):
        // the console diagnostic below logged `screenshotAcked=false, timedOut=true` even though
        // CI's own step log showed the screenshot completing successfully, because the full round
        // trip (korge_visible.txt detected -> xcrun simctl io screenshot process spawned/connects
        // to the device -> framebuffer read -> file written -> screenshot_taken.txt written) took
        // ~46s that run, more than double the 20s cap. Bumped to 90s to match the same latency
        // budget already given to the korge_visible.txt poll itself in ios-build.yml.
        let dwellDeadline = Date().addingTimeInterval(90.0)
        var dwellPollTimer: Timer?
        dwellPollTimer = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: true) { [weak self] t in
            let acked = self?.readTextFile("screenshot_taken.txt") != nil
            let timedOut = Date() >= dwellDeadline
            guard acked || timedOut else { return }
            t.invalidate()
            print("CI_TEST: Gameplay dwell ended (screenshotAcked=\(acked), timedOut=\(timedOut)) - triggering level completion")
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
        _ = dwellPollTimer
    }

    // MARK: - AdMob On-Device Verification (see .junie/guidelines.md "AdMob (basic-ads)
    // feasibility spike" - the link-only spike proved basic-ads compiles+links; this proves
    // BasicAds.Initialize() and the non-personalized RequestConfiguration actually run on a real
    // iOS Simulator, not just that the code compiles). No longer verifies a real banner load: the
    // spike's invisible 1dp BannerAd() was a real ad impression firing on every production launch,
    // which violates AdMob's policy against ads not visible to users, so it was removed from
    // AdMobVerifyScreen.kt - only SDK init and the personalization setting are checked here now.

    private func runAdMobVerification() {
        print("ADMOB_TEST: ==== AdMob Verification START ====")
        // No rootViewController swap this round - AdMobVerifyContent() renders unconditionally
        // inside MainMenu's own ComposeUIViewController scene (see AdMobVerifyScreen.kt /
        // MainMenuComposeViewController.kt), so BasicAds.Initialize() already ran at launch.
        // This just polls the result. Round 1 swapped to a second, separate
        // ComposeUIViewController here and crashed inside Compose's own setContent machinery
        // with two scenes alive at once - see guidelines.md for the full story.

        // initializeCalled/personalizationDisabled are set synchronously during Compose's first
        // composition (no ad network round-trip involved anymore), but still polled rather than
        // read once immediately, in case that first composition is delayed behind other launch
        // work.
        let deadline = Date().addingTimeInterval(15.0)
        var pollTimer: Timer?
        pollTimer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { t in
            let initCalled = AdMobVerifyBridge.shared.initializeCalled
            let personalizationDisabled = AdMobVerifyBridge.shared.personalizationDisabled
            let timedOut = Date() >= deadline
            if initCalled || timedOut {
                t.invalidate()
                let resultText: String
                if initCalled {
                    resultText = "OK:initializeCalled=true:personalizationDisabled=\(personalizationDisabled)"
                } else {
                    resultText = "FAIL:initializeCalled=false:personalizationDisabled=\(personalizationDisabled):timedOut=\(timedOut)"
                }
                print("ADMOB_TEST: ==== AdMob Verification COMPLETE: \(resultText) ====")
                self.writeTextFile("admob_verify_result.txt", resultText)
                self.runRevenueCatVerification()
            }
        }
        _ = pollTimer
    }

    // MARK: - RevenueCat In-App Purchases On-Device Verification

    private func runRevenueCatVerification() {
        print("REVENUECAT_TEST: ==== RevenueCat Verification START ====")
        RevenueCatVerifyBridge.shared.startVerification()

        let deadline = Date().addingTimeInterval(30.0)
        var pollTimer: Timer?
        pollTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] t in
            let finished = RevenueCatVerifyBridge.shared.checkFinished
            let timedOut = Date() >= deadline
            if finished || timedOut {
                t.invalidate()
                let resultText = RevenueCatVerifyBridge.shared.resultText
                print("REVENUECAT_TEST: ==== RevenueCat Verification COMPLETE: \(resultText) ====")
                self?.writeTextFile("revenuecat_verify_result.txt", resultText)
            }
        }
        _ = pollTimer
    }

    private func writeTextFile(_ name: String, _ text: String) {
        guard let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else { return }
        let url = docs.appendingPathComponent(name)
        try? text.write(to: url, atomically: true, encoding: .utf8)
    }

    // Used by runAutomatedLevelTransition()'s dwell poll to detect ios-build.yml's
    // screenshot_taken.txt ack, written directly onto the host filesystem (the app container's
    // Documents directory is a real path on the Mac runner, not something simctl virtualizes) the
    // same way this app's own result files are read back by the CI script.
    private func readTextFile(_ name: String) -> String? {
        guard let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else { return nil }
        let url = docs.appendingPathComponent(name)
        return try? String(contentsOf: url, encoding: .utf8)
    }
}

enum InAppReviewHelper {
    static func requestReview() {
        DispatchQueue.main.async {
            if #available(iOS 14.0, *) {
                if let scene = UIApplication.shared.connectedScenes
                    .first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene {
                    SKStoreReviewController.requestReview(in: scene)
                    return
                }
                if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene {
                    SKStoreReviewController.requestReview(in: scene)
                    return
                }
            }
            SKStoreReviewController.requestReview()
        }
    }
}
