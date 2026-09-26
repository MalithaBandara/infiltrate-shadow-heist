package com.infiltrate.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.lexilabs.basic.ads.AdState
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.composable.rememberRewardedAd
import kotlinx.coroutines.delay
import kotlin.native.ObjCName

/** Attempts per offer before CONTINUE is refused - see [ContinueAdContent]. */
private const val MAX_CONTINUE_LOAD_ATTEMPTS = 3

/** Backoff between attempts: an instant re-request just meets the same empty inventory. */
private const val CONTINUE_RETRY_DELAY_MS = 1200L

/**
 * Real (non-spike) "watch ad to continue" trigger. Swift calls [requestShow] once it has
 * switched the shell's rootViewController to this Compose scene (a rewarded ad can only present
 * reliably from the currently-visible view controller - see .junie/guidelines.md "AdMob
 * (basic-ads) feasibility spike" for why the earlier banner spike deliberately avoided a second,
 * detached ComposeUIViewController). [ContinueAdContent] observes [showRequested] and, while
 * true, composes basic-ads' RewardedAd trigger, which loads + shows the ad itself.
 */
@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "ContinueAdTrigger", exact = true)
object ContinueAdTrigger {
    internal val showRequested: MutableState<Boolean> = mutableStateOf(false)

    // Set the moment a definitive outcome is known (reward earned, or the ad was dismissed/
    // failed without one) - Swift polls consumeOutcomeFinished() rather than waiting out a fixed
    // timeout, so declining the ad hands control back to KorGE immediately instead of stalling.
    private var outcomeFinished: Boolean = false

    var rewardEarned: Boolean = false
        private set

    fun requestShow() {
        outcomeFinished = false
        rewardEarned = false
        showRequested.value = true
    }

    fun markRewardEarned() {
        rewardEarned = true
        outcomeFinished = true
        showRequested.value = false
    }

    // Ad failed to load/show, or the player closed it before earning the reward.
    fun cancelShow() {
        outcomeFinished = true
        showRequested.value = false
    }

    fun consumeOutcomeFinished(): Boolean {
        if (!outcomeFinished) return false
        outcomeFinished = false
        return true
    }
}

/**
 * Preloads the watch-ad-to-continue rewarded ad and shows it on request. Mirror of the Android
 * [ContinueAdContent]; see that file for the full reasoning, which applies identically here.
 *
 * In short: the [rememberRewardedAd] call sits OUTSIDE the [ContinueAdTrigger.showRequested] gate
 * so the fetch starts when this content first composes rather than after the player has already
 * died and asked to continue. Two hazards come with that, neither of which exists in the
 * load-then-show shape it replaces:
 *
 * 1. A preload that fails while nothing is pending must not call [ContinueAdTrigger.cancelShow] -
 *    Swift polls `consumeOutcomeFinished()`, so a phantom outcome would hand control back to
 *    KorGE for an offer the player was never shown. Hence the `showRequested` check inside the
 *    load-failure callback below.
 * 2. `rememberRewardedAd` only re-loads from `NONE` or `DISMISSED`, never from `FAILING`, so an
 *    early preload failure would otherwise leave the handler dead for the rest of the process and
 *    hang the continue prompt.
 *
 * Hazard 2 used to be answered by an `AdState.FAILING ->` branch that resolved the trigger as a
 * load failure did - correct, but it meant **one** unlucky preload (a request racing
 * `GADMobileAds.start()` at launch, a momentary no-fill, a network blip on a cold app) permanently
 * turned every later CONTINUE into an instant refusal for the rest of the process. On iOS that is
 * an easy state to fall into, because the SDK is only started when this Compose scene first
 * composes, in the same frame as this preload.
 *
 * So the handler is now replaceable. `rememberRewardedAd` cannot be restarted, but a **new**
 * handler always begins at `NONE`, and [key] gives us one: bumping `attempt` discards the
 * composition group the dead handler was remembered in. A failure therefore schedules another
 * attempt instead of being final, up to [MAX_CONTINUE_LOAD_ATTEMPTS]:
 *
 * - while an offer is on screen, the budget is spent quickly and `cancelShow()` still resolves the
 *   flow once it runs out, so a failed ad never strands the player behind the Swift poll's 30s
 *   timeout;
 * - while nothing is pending, retries stop once the budget is spent rather than re-requesting
 *   forever in the background, and a real offer re-arms it.
 *
 * The one iOS-specific difference is unchanged by all this: [markRewardEarned] here resolves the
 * outcome and drops `showRequested` immediately, where Android deliberately defers that to
 * `onAdClosed()` (see the Android file's note on the grey-screen reload bug). The handler still
 * lives outside the `showRequested` gate, so the ad presents correctly after that flag flips.
 */
@OptIn(DependsOnGoogleMobileAds::class)
@Composable
fun ContinueAdContent() {
    // Nothing may be requested until the consent flow settles (see AdPrivacy) - no preload, and an
    // offer made in the meantime is refused at once rather than left for Swift's 30s poll.
    if (!AdPrivacy.canRequestAds) {
        if (ContinueAdTrigger.showRequested.value) {
            LaunchedEffect(Unit) { ContinueAdTrigger.cancelShow() }
        }
        return
    }

    // The attempt composing now, and the one a failure has queued. Separate so the backoff owns
    // when the replacement handler is built: `pendingAttempt` is written from the load callback,
    // off the composition, and `attempt` only moves once the delay is up.
    var attempt by remember { mutableStateOf(0) }
    var pendingAttempt by remember { mutableStateOf(0) }
    // Spent across attempts, re-armed per offer - see the doc comment.
    var loadFailures by remember { mutableStateOf(0) }

    val showRequested = ContinueAdTrigger.showRequested.value

    LaunchedEffect(pendingAttempt) {
        if (pendingAttempt > attempt) {
            delay(CONTINUE_RETRY_DELAY_MS)
            attempt = pendingAttempt
        }
    }

    LaunchedEffect(showRequested) {
        if (showRequested) loadFailures = 0
    }

    key(attempt) {
        val ad by rememberRewardedAd(
            adUnitId = AdUnitIds.REWARDED_CONTINUE,
            onFailure = {
                loadFailures += 1
                when {
                    // An offer is on screen and we are out of attempts: resolve it, so Swift hands
                    // control back to KorGE now instead of waiting out its 30s poll.
                    ContinueAdTrigger.showRequested.value &&
                        loadFailures >= MAX_CONTINUE_LOAD_ATTEMPTS -> ContinueAdTrigger.cancelShow()
                    loadFailures < MAX_CONTINUE_LOAD_ATTEMPTS -> pendingAttempt = attempt + 1
                    // Idle preload, budget spent: stop re-requesting in the background. The
                    // effects above re-arm this the moment a real offer arrives.
                    else -> Unit
                }
            },
        )

        // If an offer arrives to find a dead handler, replace it rather than refusing. Written
        // from an effect, not from the composition, so this is an ordinary state change.
        LaunchedEffect(showRequested, ad.state) {
            if (showRequested && ad.state == AdState.FAILING && pendingAttempt <= attempt) {
                pendingAttempt = attempt + 1
            }
        }

        if (showRequested) {
            DisposableEffect(Unit) {
                AdAudioCoordinator.onAdStarted()
                onDispose { AdAudioCoordinator.onAdDismissed() }
            }
            if (ad.state == AdState.READY) {
                ad.setListeners(
                    onFailure = { ContinueAdTrigger.cancelShow() },
                    onDismissed = { ContinueAdTrigger.cancelShow() },
                )
                ad.show { ContinueAdTrigger.markRewardEarned() }
            }
        }
    }
}
