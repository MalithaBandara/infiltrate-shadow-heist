package com.infiltrate.storage

import android.content.Context
import android.content.SharedPreferences

actual object PlatformStorage {
    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("KorgeNativeStorage", Context.MODE_PRIVATE)
    }

    actual fun getRaw(key: String): String? {
        val p = prefs ?: return null
        return p.getString(key, null)
    }

    actual fun setRaw(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    actual fun removeRaw(key: String) {
        prefs?.edit()?.remove(key)?.apply()
    }
}
