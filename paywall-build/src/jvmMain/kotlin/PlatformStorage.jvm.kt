package com.infiltrate.storage

import java.util.concurrent.ConcurrentHashMap

actual object PlatformStorage {
    private val memoryStore = ConcurrentHashMap<String, String>()

    actual fun getRaw(key: String): String? = memoryStore[key]

    actual fun setRaw(key: String, value: String) {
        memoryStore[key] = value
    }

    fun clear() {
        memoryStore.clear()
    }
}
