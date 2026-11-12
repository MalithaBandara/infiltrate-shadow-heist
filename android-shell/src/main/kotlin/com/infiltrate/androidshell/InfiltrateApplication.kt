package com.infiltrate.androidshell

import android.app.Application
import com.infiltrate.billing.StoreBilling
import com.infiltrate.storage.PlatformStorage
import game.model.GameAnalytics

/**
 * Application subclass for android-shell handling process-startup initialization
 * (PlatformStorage, StoreBilling and analytics).
 */
class InfiltrateApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PlatformStorage.init(this)
        StoreBilling.setApplication(this)
        StoreBilling.initialize(BuildConfig.REVENUECAT_GOOGLE_KEY)
        GameAnalytics.sink = FirebaseGameAnalyticsSink.createOrNull(this)
    }
}
