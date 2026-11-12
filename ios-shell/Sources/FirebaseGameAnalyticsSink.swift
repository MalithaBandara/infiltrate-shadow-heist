import FirebaseAnalytics
import FirebaseCore
import GameMain

/// Sends game.model.GameAnalytics events (level_start, level_complete, tutorial_complete, ...)
/// to Firebase Analytics - the iOS counterpart of android-shell's FirebaseGameAnalyticsSink.kt.
/// Registered on GameMain.framework's copy of GameAnalytics, because every gameplay event comes
/// from GameplayScene, which lives in that framework.
final class FirebaseGameAnalyticsSink: NSObject, GameMain.GameAnalyticsSink {
    func logEvent(name: String, params: [String: Any]) {
        Analytics.logEvent(name, parameters: params)
    }

    /// Starts Firebase and registers the sink - a no-op, with analytics off, when the app
    /// bundle has no GoogleService-Info.plist (FirebaseApp.configure() would crash without it).
    /// Add the Firebase console's GoogleService-Info.plist to ios-shell/Resources to turn it on.
    static func configureIfAvailable() {
        guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else {
            print("Firebase: no GoogleService-Info.plist in the bundle - analytics off")
            return
        }
        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }
        GameMain.GameAnalytics.shared.sink = FirebaseGameAnalyticsSink()
    }
}
