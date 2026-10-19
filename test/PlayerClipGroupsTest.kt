package test

import game.model.GameWorld
import game.model.LevelData
import game.model.PlayerMove
import game.scene.PlayerAnimationSet
import game.scene.PlayerAnimations
import game.scene.PlayerClipGroup
import game.scene.PlayerEyePoints
import korlibs.image.bitmap.BmpSlice
import korlibs.io.async.suspendTest
import kotlin.random.Random
import kotlin.test.*

/**
 * Per-level player clips (PlayerAnimations.groupsFor / load(needed)): the swing, push and wind
 * clips are only loaded for a level that can play them. Two things have to hold for that to be
 * invisible: every clip keeps its frames at the same indices whatever is loaded (PlayerEyePoints
 * and the level 9 echo's recorded frames index the flattened list), and no level ever makes a move
 * whose clips it did not load.
 */
class PlayerClipGroupsTest {

    private fun clips(s: PlayerAnimationSet) = listOf(
        s.idle, s.walk, s.jump, s.crouch, s.crouchwalk, s.climb, s.swing, s.pushTransition, s.push, s.windTransition, s.windWalk
    )

    private fun clipsOf(s: PlayerAnimationSet, group: PlayerClipGroup) = when (group) {
        PlayerClipGroup.SWING -> listOf(s.swing)
        PlayerClipGroup.PUSH -> listOf(s.pushTransition, s.push)
        PlayerClipGroup.WIND -> listOf(s.windTransition, s.windWalk)
    }

    private fun groupOf(move: PlayerMove) = when (move) {
        PlayerMove.SWING -> PlayerClipGroup.SWING
        PlayerMove.PUSH -> PlayerClipGroup.PUSH
        PlayerMove.WIND -> PlayerClipGroup.WIND
    }

    @Test
    fun everyClipKeepsItsFramesAtTheSameIndicesWhicheverGroupsAreLoaded() = suspendTest {
        val full = PlayerAnimations.load()
        val counts = clips(full).map { it.sprites.size }
        assertEquals(PlayerEyePoints.FRAME_COUNT, counts.sum(), "the flattened frame list must match the eye table")
        val sizes = clips(full).map { c -> c.sprites.map { it.width to it.height } }
        val apex = full.jump.getSprite(PlayerAnimations.JUMP_APEX)
        val stand = full.idle.getSprite(0)
        val corePages = listOf(full.idle, full.walk, full.jump, full.crouch, full.crouchwalk, full.climb)
            .flatMap { c -> c.sprites.map { it.base } }.toSet()

        val groups = PlayerClipGroup.entries
        for (mask in 0 until (1 shl groups.size)) {
            val needed = groups.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()
            val s = PlayerAnimations.load(needed)
            assertEquals(counts, clips(s).map { it.sprites.size }, "$needed: a clip changed length")
            // The core is loaded once and never rebuilt.
            assertSame(full.idle, s.idle); assertSame(full.walk, s.walk); assertSame(full.jump, s.jump)
            assertSame(full.crouch, s.crouch); assertSame(full.crouchwalk, s.crouchwalk); assertSame(full.climb, s.climb)
            for (group in groups) {
                assertEquals(group in needed, PlayerAnimations.isLoaded(group), "$needed: $group")
                for (clip in clipsOf(s, group)) {
                    val frames: List<BmpSlice> = clip.sprites
                    if (group in needed) {
                        // Its own frames, on its own pages, the same size as the full load's.
                        assertTrue(frames.none { it.base in corePages }, "$needed: $group drew from the core atlas")
                        val i = clips(s).indexOf(clip)
                        assertEquals(sizes[i], frames.map { it.width to it.height }, "$needed: $group frame sizes")
                    } else {
                        val standIn = if (group == PlayerClipGroup.SWING) apex else stand
                        assertTrue(frames.all { it === standIn }, "$needed: unloaded $group must be all stand-in frames")
                    }
                }
            }
        }
        // Dropping groups hands their pages over to be freed, once each.
        PlayerAnimations.load()
        PlayerAnimations.takeReleasedPages()
        PlayerAnimations.load(emptySet())
        val released = PlayerAnimations.takeReleasedPages()
        assertEquals(3, released.size, "one page per group")
        assertTrue(released.none { it in corePages })
        assertTrue(PlayerAnimations.takeReleasedPages().isEmpty())
        PlayerAnimations.load()
    }

    @Test
    fun theDocumentedLevelsLoadTheMovesTheyAreBuiltAround() {
        val byId = (LevelData.DEFAULT_LEVELS + LevelData.DEV_LEVELS).associate { level ->
            level.id to PlayerAnimations.groupsFor(level, GameWorld.createDefault(level))
        }
        println("[PlayerClips] " + byId.entries.joinToString { "${it.key}=${it.value.sorted()}" })
        assertTrue(PlayerClipGroup.SWING in byId.getValue("level_5"))
        assertTrue(PlayerClipGroup.WIND in byId.getValue("level_7"))
        assertTrue(PlayerClipGroup.PUSH in byId.getValue("level_8"))
        assertEquals(PlayerClipGroup.entries.toSet(), byId.getValue("level_9"), "the echo can show any frame")
        assertTrue(PlayerClipGroup.WIND in byId.getValue("level_10"))
        assertTrue(PlayerClipGroup.PUSH in byId.getValue("push_stance_demo"))
        assertEquals(emptySet<PlayerClipGroup>(), byId.getValue("level_1"))
    }

    /**
     * Every move the game's own walkthroughs make, in every level, checked against what that level
     * loads - they are the runs that actually swing (5, 9, 12), push (8, 12), and lean into fans
     * (7, 10), end to end. Plus random input on every level for the moves a run would not make.
     */
    @Test
    fun noLevelEverMakesAMoveWhoseClipsItDidNotLoad() {
        val seen = HashMap<String, MutableSet<PlayerMove>>()
        val violations = ArrayList<String>()
        GameWorld.moveObserver = { world, move ->
            seen.getOrPut(world.levelData.id) { HashSet() } += move
            if (groupOf(move) !in PlayerAnimations.groupsFor(world.levelData, world)) {
                violations += "${world.levelData.id}: $move without its clips"
            }
        }
        try {
            val model = GameplayModelTest()
            model.testLevel4SimulationPlayable()
            model.testSwingCarriesThePlayerOverLevel5sGapAndLandsThemOnIt()
            model.testLevel7SimulationPlayableWalkthrough()
            model.testLevel8IsBeatableOnTheGroundRoad()
            model.testLevel9IsBeatableWithoutTouchingTheGround()
            model.testLevel10SimulationPlayableWalkthrough()
            Level11EscortTest().testLevel11IsBeatableAsAnEscort()
            Level12EscapeTest().testLevel12IsBeatableAsAnEscape()
            val walk = LevelWalkthroughTest()
            walk.testLevel1CleanRunTime()
            walk.testLevel2CleanRunTime()
            walk.testLevel3CleanRunTime()
            walk.testLevel5CleanRunTime()
            walk.testLevel6CleanRunTime()

            // Random hands on every level: moving, jumping, crouching and pressing interact in
            // bursts, restarting on a catch, for two minutes of game time per seed.
            for (level in LevelData.DEFAULT_LEVELS + LevelData.DEV_LEVELS) {
                for (seed in 1..3) {
                    val rnd = Random(seed * 7919 + level.id.hashCode())
                    val world = GameWorld.createDefault(level)
                    var move = 1.0; var jump = false; var crouch = false; var interact = false
                    repeat(60 * 120) { tick ->
                        if (tick % 20 == 0) {
                            move = listOf(1.0, 1.0, 1.0, 0.0, -1.0)[rnd.nextInt(5)]
                            jump = rnd.nextInt(4) == 0
                            crouch = rnd.nextInt(6) == 0
                            interact = rnd.nextInt(3) == 0
                        }
                        world.update(1.0 / 60.0, move, jump && tick % 20 < 3, crouch, interact && tick % 20 < 2, forwardTap = tick % 10 == 0)
                        if (world.isGameOver || world.isLevelComplete) world.restartLevel()
                    }
                }
            }
        } finally {
            GameWorld.moveObserver = null
        }
        println("[PlayerClips] moves seen: " + seen.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value.sorted()}" })
        assertTrue(violations.isEmpty(), violations.distinct().joinToString("\n"))
        // The check actually saw the moves it is about.
        assertTrue(PlayerMove.SWING in seen["level_5"].orEmpty(), "no swing seen on level 5")
        assertTrue(PlayerMove.PUSH in seen["level_8"].orEmpty(), "no push seen on level 8")
        assertTrue(PlayerMove.WIND in seen["level_7"].orEmpty(), "no wind stance seen on level 7")
    }
}
