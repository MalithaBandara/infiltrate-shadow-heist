package com.infiltrate.review

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.StoreKit.SKStoreReviewController
import platform.UIKit.UIApplication
import platform.UIKit.UIWindowScene

/**
 * The App Store numeric id for this app - the number in an App Store URL
 * (`https://apps.apple.com/app/id6815256409`), shown in App Store Connect as the app's "Apple ID".
 *
 * Only used to build the write-review link below. If it is ever blanked out, RATE US falls back to
 * the StoreKit prompt rather than opening the wrong page.
 */
private const val APP_STORE_ID = "6815256409"

/**
 * Settings' RATE US button.
 *
 * It used to call [SKStoreReviewController] and nothing else, which is why it read as dead on a
 * device. That API is not a "rate us" action, it is Apple's *automatic* prompt, and Apple decides
 * whether it appears: it is **never shown in TestFlight builds**, it is capped at three
 * appearances per device per year in production, and it is silently ignored the rest of the time.
 * There is no callback and no error - a correct call and a suppressed one look exactly the same
 * from here, which is what made this so confusing to chase.
 *
 * Apple's own guidance is that a button the player deliberately pressed should go to the App Store
 * review page instead, and that page always opens. So [requestReview] now:
 *
 * 1. opens `.../id<APP_STORE_ID>?action=write-review`, which works on TestFlight builds too, and
 * 2. falls back to the StoreKit prompt if that cannot be opened, so the button is never worse than
 *    it was.
 *
 * The fallback also picks its scene properly now. The old code took the first `UIWindowScene`
 * `connectedScenes` happened to hand back - an unordered `NSSet`, so on any device with more than
 * one scene alive (iPad multitasking, a backgrounded-but-connected scene) that could easily be a
 * scene which is not on screen, and StoreKit drops the request for those without a word. Asking
 * for the scene that owns a key window picks the one the player is actually looking at; it is the
 * same test `basic-ads` uses to find a view controller to present ads from.
 *
 * The automatic prompt after level 4 is a separate path (`:game`'s `InAppReviewBridge` ->
 * `GameInAppReviewBridge` -> `AppDelegate.swift`'s `InAppReviewHelper`) and deliberately still
 * uses StoreKit - an unprompted moment of goodwill is exactly what that API is for.
 */
actual object InAppReview {
    actual fun requestReview() {
        if (openWriteReviewPage()) return
        requestStoreKitPrompt()
    }

    /** True if the App Store review page was handed to the system to open. */
    @OptIn(ExperimentalForeignApi::class)
    private fun openWriteReviewPage(): Boolean {
        if (APP_STORE_ID.isEmpty()) return false
        return try {
            val app = UIApplication.sharedApplication
            // itms-apps first: it lands straight in the App Store app on the review sheet. The
            // https form is the same destination but can bounce through Safari on the way, so it
            // is only the fallback - it always opens, which is why it is worth keeping.
            val candidates = listOf(
                "itms-apps://apps.apple.com/app/id$APP_STORE_ID?action=write-review",
                "https://apps.apple.com/app/id$APP_STORE_ID?action=write-review"
            )
            for (candidate in candidates) {
                val url = NSURL.URLWithString(candidate) ?: continue
                if (!app.canOpenURL(url)) continue
                app.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
                return true
            }
            false
        } catch (t: Throwable) {
            println("[InAppReview] iOS App Store review page error: ${t.message}")
            false
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun requestStoreKitPrompt() {
        try {
            val windowScenes = UIApplication.sharedApplication.connectedScenes
                .filterIsInstance<UIWindowScene>()
            // The scene showing a key window is the one in front of the player; anything else is
            // a scene StoreKit will quietly refuse to prompt in.
            val targetScene = windowScenes.firstOrNull { it.keyWindow != null } ?: windowScenes.firstOrNull()
            if (targetScene != null) {
                SKStoreReviewController.requestReviewInScene(targetScene)
            } else {
                SKStoreReviewController.requestReview()
            }
        } catch (t: Throwable) {
            println("[InAppReview] iOS StoreKit review error: ${t.message}")
        }
    }
}
