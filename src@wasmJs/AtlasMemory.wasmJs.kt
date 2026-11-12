package game.scene

/** Unchanged: atlas pages stay heap Bitmap32s here. Only low-heap Android differs (AtlasMemory.kt). */
actual fun atlasPagesOnHeap(): Boolean = true
