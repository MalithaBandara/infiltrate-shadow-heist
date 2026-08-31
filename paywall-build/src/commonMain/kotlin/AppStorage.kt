package com.infiltrate.storage

expect object PlatformStorage {
    fun getRaw(key: String): String?
    fun setRaw(key: String, value: String)
}
