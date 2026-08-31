import UIKit
import GameMain
import PaywallModule

/// Minimal native shell AppDelegate: hosts KorGE's game (via `GameMain`'s `ShellAppDelegate`,
/// defined in `:game`'s `src@ios/ShellAppDelegate.ios.kt`) and adds ONE debug overlay to prove
/// `PaywallModule.framework` and `GameMain.framework` share the same on-disk storage at runtime -
/// the thing that was only verified with a JVM-only logical test before this shell existed.
///
/// Deliberately NOT the full paywall UI or PurchasesBridge wiring - see .junie/guidelines.md.
class AppDelegate: UIResponder, UIApplicationDelegate {
    private var resultLabel: UILabel?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        ShellAppDelegate.shared.applicationDidFinishLaunching(app: application)
        addDebugOverlay()
        // Runs automatically (not just on tap) so CI can verify the round-trip without
        // simulating a UI interaction - see the "Storage bridge" step in ios-build.yml.
        runStorageBridgeCheck()
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

    // MARK: - Storage bridge proof-of-concept

    private func addDebugOverlay() {
        // `window` is a Kotlin `lateinit var UIWindow` (non-null), already assigned
        // synchronously by the `applicationDidFinishLaunching(app, entry)` call above before it
        // returns - bridges to a non-optional Swift `UIWindow`, not `UIWindow?`.
        let window = ShellAppDelegate.shared.window

        let button = UIButton(type: .system)
        button.setTitle("Storage Bridge Check", for: .normal)
        button.backgroundColor = UIColor.black.withAlphaComponent(0.6)
        button.setTitleColor(.white, for: .normal)
        button.frame = CGRect(x: 12, y: 44, width: 220, height: 36)
        button.addTarget(self, action: #selector(runStorageBridgeCheckTapped), for: .touchUpInside)
        window.addSubview(button)

        let label = UILabel(frame: CGRect(x: 12, y: 84, width: 320, height: 20))
        label.textColor = .white
        label.font = UIFont.systemFont(ofSize: 12)
        label.text = "Storage bridge: not yet run"
        window.addSubview(label)
        resultLabel = label
    }

    @objc private func runStorageBridgeCheckTapped() {
        runStorageBridgeCheck()
    }

    /// Writes a test value via PaywallModule's PaywallStorage, then reads it back through
    /// :game's real MapBackedGameProfileStorage code path (DebugStorageBridge). Value is derived
    /// from the current time so a stale/cached read is caught, not just a lucky repeat.
    @discardableResult
    private func runStorageBridgeCheck() -> Bool {
        let testValue = String(Int(Date().timeIntervalSince1970))
        PaywallStorage.shared.setRaw(key: "user_coins", value: testValue)
        let readBack = DebugStorageBridge.shared.readCoinsForDebug()
        let expected = Int32(testValue) ?? -1
        let ok = readBack == expected

        let resultText = ok ? "OK" : "FAIL:expected=\(expected):actual=\(readBack)"
        resultLabel?.text = "Storage bridge: \(resultText)"
        writeResultFile(resultText)
        return ok
    }

    /// CI reads this back via `xcrun simctl get_app_container ... data` - see ios-build.yml.
    private func writeResultFile(_ text: String) {
        guard let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else { return }
        let url = docs.appendingPathComponent("storage_bridge_result.txt")
        try? text.write(to: url, atomically: true, encoding: .utf8)
    }
}
