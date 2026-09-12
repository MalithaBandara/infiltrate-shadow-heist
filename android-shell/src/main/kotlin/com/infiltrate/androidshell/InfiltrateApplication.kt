package com.infiltrate.androidshell

import android.app.Application
import com.infiltrate.billing.StoreBilling
import com.infiltrate.storage.PlatformStorage

/**
 * Application subclass for android-shell handling process-startup initialization
 * (PlatformStorage and StoreBilling).
 */
class InfiltrateApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PlatformStorage.init(this)
        StoreBilling.setApplication(this)
        StoreBilling.initialize(BuildConfig.REVENUECAT_GOOGLE_KEY)
    }
}
