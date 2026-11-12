package game.scene

/**
 * android-shell's plain (non-`expect`) copy of `atlasPagesOnHeap()` - see src/AtlasMemory.kt for
 * what it decides and why, and src@android/AtlasMemory.android.kt for the same logic on :game's
 * own Android target. This is the one the shipped Android app runs; keep the two in step.
 *
 * Heap limits below [MIN_HEAP_FOR_HEAP_ATLAS_BYTES] keep atlas pages native (off the Java heap).
 * This app's manifest asks for `largeHeap`, which lifts most phones to ~384-512MB - plenty for the
 * heap copies, so those phones keep the rendering every earlier release shipped with. Below it the
 * copies alone (up to ~168MB) take most of what the app is allowed: the budget-phone case that
 * crashed with OutOfMemoryError in 1.1.0.
 */
private const val MIN_HEAP_FOR_HEAP_ATLAS_BYTES = 384L * 1024 * 1024

/** `maxMemory()` is the Java heap limit this app got (largeHeap included), fixed per process. */
private val heapAtlas: Boolean by lazy {
    val max = Runtime.getRuntime().maxMemory()
    val onHeap = max >= MIN_HEAP_FOR_HEAP_ATLAS_BYTES
    println("[AtlasMemory] heap limit ${max / (1024 * 1024)}MB -> atlas pages ${if (onHeap) "on heap" else "native"}")
    onHeap
}

fun atlasPagesOnHeap(): Boolean = heapAtlas
