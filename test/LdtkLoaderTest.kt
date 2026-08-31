import korlibs.io.file.std.*
import korlibs.korge.ldtk.*
import korlibs.korge.ldtk.view.*
import korlibs.korge.tests.*
import korlibs.korge.view.*
import kotlin.test.*

class LdtkLoaderTest : ViewsForTesting() {

    @Test
    fun testParseLdtkJsonDirectly() = viewsTest {
        val jsonString = resourcesVfs["test_minimal.ldtk"].readString()
        val ldtk = LDTKJson.load(jsonString)

        assertEquals("1.1.3", ldtk.jsonVersion)
        assertEquals(320, ldtk.worldGridWidth)
        assertEquals(240, ldtk.worldGridHeight)
        assertEquals(1, ldtk.levels.size)

        val level = ldtk.levels.first()
        assertEquals("Level_0", level.identifier)
        assertEquals(320, level.pxWid)
        assertEquals(240, level.pxHei)

        val layer = level.layerInstances?.first()
        assertNotNull(layer)
        assertEquals("Entities", layer.identifier)
        assertEquals(2, layer.entityInstances.size)

        val player = layer.entityInstances.find { it.identifier == "Player" }
        assertNotNull(player)
        assertEquals(1, player.defUid)
        assertEquals(32, player.px[0])
        assertEquals(160, player.px[1])
        assertEquals(16, player.width)
        assertEquals(32, player.height)

        val guard = layer.entityInstances.find { it.identifier == "Guard" }
        assertNotNull(guard)
        assertEquals(2, guard.defUid)
        assertEquals(160, guard.px[0])
        assertEquals(160, guard.px[1])
    }

    @Test
    fun testReadLdtkWorldAndInstantiateViews() = viewsTest {
        val world = resourcesVfs["test_minimal.ldtk"].readLDTKWorld(extrude = false)
        assertNotNull(world)
        assertEquals(1, world.levels.size)

        val level = world.levels.first()
        assertEquals("Level_0", level.level.identifier)
        assertEquals(1, level.layers.size)

        val layer = level.layers.first()
        assertEquals("Entities", layer.layer.identifier)
        assertEquals(2, layer.entities.size)

        // Instantiate KorGE view hierarchy
        val worldView = LDTKWorldView(world)
        assertNotNull(worldView)

        // Check level view and children
        val levelViews = worldView.children.filterIsInstance<LDTKLevelView>()
        assertEquals(1, levelViews.size)

        val levelView = levelViews.first()
        val layerViews = levelView.children.filterIsInstance<LDTKLayerView>()
        assertEquals(1, layerViews.size)

        val layerView = layerViews.first()
        val entityViews = layerView.children.filterIsInstance<LDTKEntityView>()
        assertEquals(2, entityViews.size)

        val playerView = entityViews.find { it.name == "Player" }
        assertNotNull(playerView)
        assertEquals(16.0, playerView.view.width)
        assertEquals(32.0, playerView.view.height)

        val guardView = entityViews.find { it.name == "Guard" }
        assertNotNull(guardView)
        assertEquals(16.0, guardView.view.width)
        assertEquals(32.0, guardView.view.height)
    }
}
