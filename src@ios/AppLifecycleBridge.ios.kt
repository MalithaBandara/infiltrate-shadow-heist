package com.sample.demo.lifecycle

import kotlin.native.ObjCName

/**
 * Swift-visible face of [GameAppLifecycle]. `AppDelegate.swift` calls these from
 * `applicationDidEnterBackground`/`applicationWillEnterForeground` and around the rewarded
 * "continue" ad, so the level clock does not run while the shell has swapped the window away from
 * the KorGE view.
 *
 * `exact = true` is mandatory on every Swift-visible object here - without it the framework prefix
 * stays on the linked symbol and Swift gets an undefined symbol (see .junie/guidelines.md, native
 * iOS shell, settled point 2).
 */
@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "GameAppLifecycleBridge", exact = true)
object GameAppLifecycleBridge {
    fun markBackground() = GameAppLifecycle.markBackground()
    fun markForeground() = GameAppLifecycle.markForeground()
}
