package com.sample.demo.lifecycle

/**
 * Whether gameplay is on screen and the player can actually act on it.
 *
 * The level clock (`GameWorld.timeTaken`, the number the win/fail card shows and star 3 is judged
 * against) must count only that time. The in-scene pause overlay is handled by GameplayScene's own
 * `isPaused` flag, but that flag cannot see the two holds that come from outside the game:
 *
 *  - **the app being backgrounded** (home button, a call, the app switcher), and
 *  - **a full-screen ad covering gameplay** - on Android the KorGE view is deliberately never
 *    hidden (the Compose menu draws opaquely on top; hiding it tears down the `GLSurfaceView` for
 *    good - see .junie/guidelines.md real-device bug #7), so the render loop and its updater keep
 *    running underneath whatever is in front of them.
 *
 * Each native shell reports those two from the lifecycle callbacks it already implements, and
 * GameplayScene folds [isForeground] into the same early return as its pause overlay. Targets
 * without a shell (desktop JVM, JS/wasm previews) simply never call this and stay foreground,
 * which is the correct answer there.
 *
 * Deliberately a plain object rather than the `expect`/`actual` interface the other bridges use:
 * there is nothing platform-specific to implement, only a flag to set. iOS still needs a small
 * `@ObjCName` wrapper for Swift to reach it (`src@ios/AppLifecycleBridge.ios.kt`), because
 * Kotlin/Native only exports declarations that are named for Objective-C.
 */
object GameAppLifecycle {
    /**
     * True while gameplay is the thing in front of the player. Starts true: a target that never
     * reports lifecycle at all must play normally, not sit frozen.
     */
    var isForeground: Boolean = true
        private set

    /**
     * Told on every change, so audio can be silenced at once: on iOS the KorGE view leaves the window
     * for a full-screen ad and its update loop stops, so nothing polling [isForeground] ever runs to
     * quieten the music. One listener - the live gameplay scene sets it and clears it on teardown.
     */
    var onForegroundChanged: ((Boolean) -> Unit)? = null

    /** Backgrounded, or covered by a full-screen ad. The level clock stops here. */
    fun markBackground() {
        isForeground = false
        onForegroundChanged?.invoke(false)
    }

    /** Back in front of the player. The level clock resumes from where it stopped. */
    fun markForeground() {
        isForeground = true
        onForegroundChanged?.invoke(true)
    }
}
