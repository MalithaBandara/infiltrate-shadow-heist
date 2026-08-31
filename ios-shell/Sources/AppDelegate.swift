import UIKit
import Darwin
import GameMain
import PaywallModule

/// Native shell AppDelegate. Hosts two things:
///  1. The pre-existing storage-bridge proof-of-concept (PaywallModule <-> GameMain share the
///     same on-disk storage) - unchanged, still runs automatically on launch.
///  2. NEW: the Compose<->KorGE view-switching cost spike (see .junie/guidelines.md). Measures
///     the real cost of repeatedly showing/hiding KorGE's already-warm engine view behind a
///     trivial Compose screen, simulating a player dying and continuing multiple times.
///
/// Deliberately NOT the full paywall UI or real menu/gameplay wiring - see guidelines.md.
class AppDelegate: UIResponder, UIApplicationDelegate {
    private var resultLabel: UILabel?

    // MARK: - Switch spike state

    private var korgeVC: UIViewController?
    private var composeVC: UIViewController?
    private var shellWindow: UIWindow?

    private let totalCycles = 6
    private var cycleResults: [String] = []
    private var pollTimer: Timer?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        ShellAppDelegate.shared.applicationDidFinishLaunching(app: application)
        let window = ShellAppDelegate.shared.window
        self.shellWindow = window

        addDebugOverlay()
        runStorageBridgeCheck()

        // ShellAppDelegate.applicationDidFinishLaunching already made the KorGE ViewController
        // (SwitchSpikeScene, per the TEMP entry-point swap in ShellAppDelegate.ios.kt) the
        // window's rootViewController and called makeKeyAndVisible(). For the switch spike we
        // want to START on the Compose screen instead (that's the real target architecture:
        // Compose owns everything except actual gameplay) - so immediately hand the window over
        // to a fresh Compose root, keeping a reference to the KorGE VC to swap back in later.
        korgeVC = window.rootViewController
        let compose = SpikeComposeScreen.shared.makeViewController {
            print("SPIKE: Start Level tapped (manual)")
        }
        composeVC = compose
        window.rootViewController = compose

        // Give the app a moment to fully settle (first layout pass, storage bridge check, etc.)
        // before starting the automated 6-cycle measurement run.
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
            self?.runCycle(1)
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

    // MARK: - Storage bridge proof-of-concept (pre-existing, unchanged)

    private func addDebugOverlay() {
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

    @discardableResult
    private func runStorageBridgeCheck() -> Bool {
        let testValue = String(Int(Date().timeIntervalSince1970))
        PaywallStorage.shared.setRaw(key: "user_coins", value: testValue)
        let readBack = DebugStorageBridge.shared.readCoinsForDebug()
        let expected = Int32(testValue) ?? -1
        let ok = readBack == expected

        let resultText = ok ? "OK" : "FAIL:expected=\(expected):actual=\(readBack)"
        resultLabel?.text = "Storage bridge: \(resultText)"
        writeTextFile("storage_bridge_result.txt", resultText)
        return ok
    }

    // MARK: - Compose<->KorGE switch spike

    /// Resident memory in MB, via mach_task_basic_info - a rough signal, not full profiling,
    /// per the spike's own scope.
    private func residentMemoryMB() -> Double {
        var info = mach_task_basic_info()
        var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size / MemoryLayout<natural_t>.size)
        let kerr: kern_return_t = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                task_info(mach_task_self_, task_flavor_t(MACH_TASK_BASIC_INFO), $0, &count)
            }
        }
        guard kerr == KERN_SUCCESS else { return -1 }
        return Double(info.resident_size) / 1024.0 / 1024.0
    }

    private func nowMs() -> Double {
        return CFAbsoluteTimeGetCurrent() * 1000.0
    }

    /// Polls SpikeBridge.frameTicks until it changes from `baseline`, or `timeoutMs` elapses.
    /// Returns (latencyMs, timedOut). 2ms poll interval - fine-grained enough to report
    /// switch cost to the nearest few ms, which is what matters for "needs a spinner or not".
    private func waitForNextTick(baseline: Int32, timeoutMs: Double, completion: @escaping (Double, Bool) -> Void) {
        let start = nowMs()
        var timer: Timer?
        timer = Timer.scheduledTimer(withTimeInterval: 0.002, repeats: true) { t in
            let elapsed = self.nowMs() - start
            if SpikeBridge.shared.frameTicks != baseline {
                t.invalidate()
                completion(elapsed, false)
                return
            }
            if elapsed >= timeoutMs {
                t.invalidate()
                completion(elapsed, true)
                return
            }
        }
        self.pollTimer = timer
    }

    private func runCycle(_ cycle: Int) {
        guard cycle <= totalCycles, let window = self.shellWindow, let korgeVC = self.korgeVC else {
            finishSpike()
            return
        }

        print("SPIKE: ==== cycle \(cycle)/\(totalCycles) START ====")
        let baselineTicks = SpikeBridge.shared.frameTicks

        // 1. Switch to KorGE (Compose -> KorGE)
        window.rootViewController = korgeVC

        waitForNextTick(baseline: baselineTicks, timeoutMs: 8000.0) { [weak self] latencyMs, timedOut in
            guard let self = self else { return }
            let status = timedOut ? "TIMEOUT(>8000ms, view may not be rendering)" : "ok"
            print("SPIKE: cycle \(cycle) switchToKorgeLatencyMs=\(String(format: "%.1f", latencyMs)) status=\(status)")

            // 2. Dwell while KorGE is visible - proves it's actively rendering (frameTicks
            //    advancing), and gives it a moment to feel "playable" like a human tester would.
            let dwellStartTicks = SpikeBridge.shared.frameTicks
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) {
                let dwellEndTicks = SpikeBridge.shared.frameTicks
                let ticksAdvanced = dwellEndTicks - dwellStartTicks
                print("SPIKE: cycle \(cycle) korgeVisibleDwell ticksAdvanced=\(ticksAdvanced) (over ~700ms)")

                // 3. Trigger "level ended" - identical call the in-scene debug button makes.
                SpikeBridge.shared.requestLevelEnd()
                _ = SpikeBridge.shared.consumeLevelEndRequest()
                print("SPIKE: cycle \(cycle) level end triggered")

                // 4. Switch back to Compose (KorGE -> Compose)
                let hideBaselineTicks = SpikeBridge.shared.frameTicks
                window.rootViewController = self.composeVC
                self.waitForNextTick(baseline: hideBaselineTicks, timeoutMs: 2000.0) { composeLatencyMs, composeTimedOut in
                    // Note: this measures time-to-next-KorGE-tick after hiding, which is only a
                    // rough proxy for "Compose is ready" (Compose's own recomposition isn't
                    // separately instrumented here) - see the written report for this caveat.
                    print("SPIKE: cycle \(cycle) switchToComposeLatencyMs(approx, via next-tick-or-timeout)=\(String(format: "%.1f", composeLatencyMs)) timedOut=\(composeTimedOut)")

                    // 5. Hidden dwell: KorGE view not visible - does frameTicks keep advancing?
                    let hiddenStartTicks = SpikeBridge.shared.frameTicks
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                        let hiddenEndTicks = SpikeBridge.shared.frameTicks
                        let hiddenTicksAdvanced = hiddenEndTicks - hiddenStartTicks
                        let mem = self.residentMemoryMB()
                        print("SPIKE: cycle \(cycle) hiddenDwellTicksAdvanced=\(hiddenTicksAdvanced) (over ~1200ms, KorGE view not visible) residentMemoryMB=\(String(format: "%.2f", mem))")

                        let line = "cycle=\(cycle) switchToKorgeLatencyMs=\(String(format: "%.1f", latencyMs)) switchToKorgeTimedOut=\(timedOut) korgeDwellTicksAdvanced=\(ticksAdvanced) hiddenDwellTicksAdvanced=\(hiddenTicksAdvanced) residentMemoryMB=\(String(format: "%.2f", mem))"
                        self.cycleResults.append(line)

                        print("SPIKE: ==== cycle \(cycle)/\(self.totalCycles) END ====")
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                            self.runCycle(cycle + 1)
                        }
                    }
                }
            }
        }
    }

    private func finishSpike() {
        let summary = cycleResults.joined(separator: "\n")
        print("SPIKE: ==== ALL CYCLES DONE ====")
        print(summary)
        writeTextFile("switch_spike_result.txt", summary.isEmpty ? "NO_CYCLES_RAN" : summary)
    }

    private func writeTextFile(_ name: String, _ text: String) {
        guard let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else { return }
        let url = docs.appendingPathComponent(name)
        try? text.write(to: url, atomically: true, encoding: .utf8)
    }
}
