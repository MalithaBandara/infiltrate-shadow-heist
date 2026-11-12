package game.scene

/**
 * Heap limits below this keep atlas pages native (see src/AtlasMemory.kt). android-shell ships its
 * own copy of this logic (android-shell/.../AtlasMemory.kt) - keep the two in step.
 *
 * android-shell's manifest asks for `largeHeap`, which lifts most phones to ~384-512MB - plenty
 * for the heap copies, so those phones keep the rendering every earlier release shipped with.
 * Below this the copies alone (up to ~168MB) would take most of what the app is allowed, which is
 * exactly the budget-phone case that crashed in 1.1.0. 384MB rather than 512MB so phones that
 * land in the middle still keep the old path, and only the genuinely tight ones change.
 */
private const val MIN_HEAP_FOR_HEAP_ATLAS_BYTES = 384L * 1024 * 1024

/**
 * Read once: the limit is fixed for the life of the process. `maxMemory()` is the Java heap limit
 * this app actually got (largeHeap included), which is what an `OutOfMemoryError` runs into - the
 * phone's total RAM says less about it.
 */
private val heapAtlas: Boolean by lazy {
    val max = Runtime.getRuntime().maxMemory()
    val onHeap = max >= MIN_HEAP_FOR_HEAP_ATLAS_BYTES
    println("[AtlasMemory] heap limit ${max / (1024 * 1024)}MB -> atlas pages ${if (onHeap) "on heap" else "native"}")
    onHeap
}

actual fun atlasPagesOnHeap(): Boolean = heapAtlas
