package com.infiltrate.androidshell

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import game.model.GameAnalyticsSink

/** Sends game.model.GameAnalytics events to Firebase Analytics. */
class FirebaseGameAnalyticsSink private constructor(
    private val firebase: FirebaseAnalytics
) : GameAnalyticsSink {

    override fun logEvent(name: String, params: Map<String, Any>) {
        val bundle = Bundle()
        for ((key, value) in params) {
            when (value) {
                is Long -> bundle.putLong(key, value)
                is Int -> bundle.putLong(key, value.toLong())
                is Double -> bundle.putDouble(key, value)
                else -> bundle.putString(key, value.toString())
            }
        }
        firebase.logEvent(name, bundle)
    }

    companion object {
        /**
         * Null when Firebase isn't configured - a build without google-services.json (see
         * build.gradle.kts), where FirebaseInitProvider never creates the default FirebaseApp.
         */
        fun createOrNull(context: Context): GameAnalyticsSink? =
            if (FirebaseApp.getApps(context).isEmpty()) null
            else FirebaseGameAnalyticsSink(FirebaseAnalytics.getInstance(context))
    }
}
