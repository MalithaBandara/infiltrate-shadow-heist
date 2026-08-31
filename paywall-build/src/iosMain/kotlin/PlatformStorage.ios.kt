package com.infiltrate.storage

import PaywallStorage

actual object PlatformStorage {
    actual fun getRaw(key: String): String? = PaywallStorage.getRaw(key)
    actual fun setRaw(key: String, value: String) = PaywallStorage.setRaw(key, value)
}
