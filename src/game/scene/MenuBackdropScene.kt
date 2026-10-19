package game.scene

import com.sample.demo.ads.getContinueAdBridge
import korlibs.korge.scene.Scene
import korlibs.korge.view.SContainer
import korlibs.korge.view.addUpdater
import korlibs.platform.Platform

/**
 * What KorGE holds behind the host's Compose menu on Android and iOS after QUIT / RETURN TO MENU /
 * MAIN MENU / ALL CLEAR - an empty scene, nothing drawn but the clear colour.
 *
 * It used to be a whole dormant [GameplayScene] of the level just left: every asset bound, the
 * level built, the view tree drawn at the panel's refresh rate under the menu on Android (the
 * KorGE view is never hidden there - bug #7), and a loading screen played to nobody at every
 * QUIT. Nothing ever came back to it: starting any level from the menu builds a fresh
 * GameplayScene (MainActivity.startLevel / GameLevelStartBridge.startLevel), and the menu covers it
 * opaquely the whole time it exists.
 *
 * Only where a host menu takes over ([hostShowsMenu]). Desktop and the browser previews have no
 * menu to go back to - there the dormant GameplayScene IS what the player sees and plays - so
 * they keep it.
 */
class MenuBackdropScene : Scene() {
    override suspend fun SContainer.sceneMain() {
        // A continue-ad reward that lands after the player already left (CONTINUE pressed, then
        // RETURN TO MENU while the ad was still loading) belongs to no level. The dormant
        // GameplayScene used to swallow it by reviving its own hidden world; discard it here, or
        // it would wait in the bridge and revive the player in whatever level starts next.
        addUpdater { getContinueAdBridge().consumeContinueGranted() }
    }

    companion object {
        /** True where leaving gameplay hands the screen to a native menu (see the class comment). */
        val hostShowsMenu: Boolean get() = Platform.isAndroid || Platform.isIos
    }
}
