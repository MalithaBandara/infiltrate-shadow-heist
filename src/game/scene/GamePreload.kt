package game.scene

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Loads, behind the main menu, what every level's loading screen would otherwise load first: the
 * player's core atlas (~600 frame files, the bulk of a first level load), the guards' atlas, the
 * fonts and the art every level draws. Started once by the host at app launch (MainActivity on
 * Android, ShellAppDelegate on iOS); the first GameplayScene then takes each piece from here -
 * waiting for it if a quick tap beat the preload to it - and loads only its own level's art.
 *
 * Off the game's own thread on purpose. On iOS KorGE's loop stops while the menu is in the window
 * instead of the game view, so work queued there would not run until gameplay showed; on Android
 * it would share the render thread. Decoding and atlas packing only fill heap bitmaps - the GPU
 * upload still happens when a frame first draws them, exactly as before.
 *
 * Changes nothing a level shows: each piece is decoded by the same code the scene would have run
 * (SceneAssets.preloadBitmap / preloadFont, PlayerAnimations.preloadCore, GuardAnimations.preload),
 * and anything that fails here is simply loaded by the scene as before. Only art EVERY level asks
 * for belongs in [BITMAPS] - GameplaySceneArtTest checks that, and that each flag matches the
 * scene's - since a level releases what it does not ask for (SceneAssets.releaseOtherLevelsArt).
 *
 * Costs memory from launch rather than from the first level: mostly the core atlas, 6 pages of
 * 2048x2048 (see PlayerAnimations' atlas budget note). Everything here stays loaded once a level
 * has run anyway, so the steady state is unchanged.
 */
object GamePreload {

    /** Every level's shared art, as (name, minified) - the flag SceneAssets.bitmap is called with. */
    internal val LOADING_SCREEN_BITMAPS = listOf(
        "loadingbg.png" to false, "logo_main.png" to false, "button1.png" to false
    )
    internal val BITMAPS: List<Pair<String, Boolean>> = LOADING_SCREEN_BITMAPS + listOf(
        "crate.png" to true, "barrel.png" to true, "cart.png" to true,
        "laseremittor.png" to false,
        "conveyor_top.png" to false, "conveyor_mid.png" to false, "conveyor_bot.png" to false, "conveyor_end.png" to true,
        "robot_body.png" to true, "robot_wheel.png" to true, "steam_nozzle_up.png" to true, "steam_nozzle_down.png" to true,
        "left.png" to true, "right.png" to true, "crouch.png" to true, "jump.png" to true, "interact.png" to true,
        "button2.png" to false, "button3.png" to false, "button4.png" to false,
        "victorybutton1.png" to false, "victorybutton2.png" to false, "victorybutton3.png" to false,
        "failedbutton1.png" to false, "failedbutton2.png" to false, "failedbutton3.png" to false,
        "failedscreen.png" to false, "success3.png" to false,
        "gadget_checkpoints.png" to true, "gadget_checkpoint.png" to true, "gadget_lasershield.png" to true,
        "gadget_invis.png" to true, "gadget_boots.png" to true, "gadget_prototype.png" to true, "gadget_bolt.png" to true,
        "star1.png" to false, "star2.png" to false, "star3.png" to false
    )
    internal val FONTS = listOf("BebasNeue-Regular.ttf", "handwritten.ttf")

    private var job: Job? = null

    /**
     * Starts the preload; later calls do nothing. Call from the thread the game will later run on,
     * or before the game starts (every piece is reserved here, synchronously, before any work runs).
     * [platformContext] is added to the background coroutine - Android's resourcesVfs reads its
     * Context from there (korlibs.io.android.AndroidCoroutineContext).
     */
    fun start(platformContext: CoroutineContext = EmptyCoroutineContext) {
        if (job != null) return
        // In the order the loading screen asks for them, so a level started mid-preload waits on
        // the piece it needs next rather than on one queued behind it.
        val loadingScreen = LOADING_SCREEN_BITMAPS.map { (name, minified) -> SceneAssets.preloadBitmap(name, minified) }
        val fonts = FONTS.map { SceneAssets.preloadFont(it) }
        val playerCore = PlayerAnimations.preloadCore()
        val guards = GuardAnimations.preload()
        val rest = BITMAPS.drop(LOADING_SCREEN_BITMAPS.size).map { (name, minified) -> SceneAssets.preloadBitmap(name, minified) }
        val steps = loadingScreen + fonts + playerCore + guards + rest
        job = CoroutineScope(Dispatchers.Default + platformContext).launch {
            for (step in steps) step()
        }
    }

    /** Waits for the preload to finish, for tests. */
    internal suspend fun joinForTesting() {
        job?.join()
    }
}
