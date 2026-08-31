package com.infiltrate.storage

import android.content.Context
import android.content.SharedPreferences

actual object PlatformStorage {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("korge", Context.MODE_PRIVATE)
    }

    actual fun getRaw(key: String): String? {
        val p = prefs ?: return null
        return p.getString("org.korge.storage.$key", null)
    }

    actual fun setRaw(key: String, value: String) {
        prefs?.edit()?.putString("org.korge.storage.$key", value)?.apply()
    }
}
