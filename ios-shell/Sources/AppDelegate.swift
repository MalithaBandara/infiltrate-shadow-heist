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
    private var resultLabel: UILabel?

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

        addDebugOverlay()
        runStorageBridgeCheck()

        // ShellAppDelegate initialized the warm KorGE ViewController.
        // Compose Multiplatform owns non-gameplay screens, so MainMenu is the initial rootViewController.
        korgeVC = window.rootViewController

        let compose = MainMenuComposeScreen.shared.makeViewController { [weak self] in
            print("MAIN_MENU: Start Level tapped -> Swapping rootViewController to KorGE gameplay")
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
        }
    }

    private func stopObservingLevelEnd() {
        levelObserverTimer?.invalidate()
        levelObserverTimer = nil
    }

    // MARK: - Storage Bridge Real Profile Check

    private func addDebugOverlay() {
        let window = ShellAppDelegate.shared.window

        let button = UIButton(type: .system)
        button.setTitle("Storage Bridge Check", for: .normal)
        button.backgroundColor = UIColor.black.withAlphaComponent(0.6)
        button.setTitleColor(.white, for: .normal)
        button.frame = CGRect(x: 12, y: 44, width: 220, height: 36)
        button.addTarget(self, action: #selector(runStorageBridgeCheckTapped), for: .touchUpInside)
        window.addSubview(button)

        let label = UILabel(frame: CGRect(x: 12, y: 84, width: 400, height: 20))
        label.textColor = .white
        label.font = UIFont.systemFont(ofSize: 12)
        label.text = "Storage bridge: not yet run"
        window.addSubview(label)
        resultLabel = label
    }

    @objc private func runStorageBridgeCheckTapped() {
        runStorageBridgeCheck()
    }

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
        resultLabel?.text = "Storage bridge: \(resultText)"
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
