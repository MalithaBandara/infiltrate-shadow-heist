package com.infiltrate.review

import android.app.Activity
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory
import java.lang.ref.WeakReference

actual object InAppReview {
    private const val TAG = "InAppReview"
    private var currentActivity: WeakReference<Activity>? = null

    fun init(activity: Activity) {
        currentActivity = WeakReference(activity)
    }

    fun clear() {
        currentActivity = null
    }

    actual fun requestReview() {
        val activity = currentActivity?.get()
        if (activity == null) {
            Log.w(TAG, "Cannot request review: Activity is null")
            return
        }

        activity.runOnUiThread {
            try {
                val manager = ReviewManagerFactory.create(activity)
                val request = manager.requestReviewFlow()
                request.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val reviewInfo = task.result
                        val flow = manager.launchReviewFlow(activity, reviewInfo)
                        flow.addOnCompleteListener { _ ->
                            Log.d(TAG, "In-app review flow completed")
                        }
                    } else {
                        Log.w(TAG, "In-app review request failed: ${task.exception?.message}")
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error launching Google Play in-app review", t)
            }
        }
    }
}
