package com.infiltrate.billing

import android.app.Application
import android.util.Log
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesConfiguration

actual object StoreBilling {

    private const val TAG = "StoreBilling"
    private const val DEFAULT_GOOGLE_API_KEY = "goog_DVKTWBbrxMSDhEimnQZBxQcGVxx"

    @Volatile
    private var lastInitError: String? = null

    actual fun isConfigured(): Boolean = Purchases.isConfigured

    fun setApplication(app: Application) {
        try {
            val providerClass = Class.forName("com.revenuecat.purchases.kmp.di.AndroidProvider")
            val instanceField = providerClass.getDeclaredField("INSTANCE").apply { isAccessible = true }
            val instance = instanceField.get(null)
            val setAppMethod = providerClass.getDeclaredMethod("setApplication", Application::class.java).apply { isAccessible = true }
            setAppMethod.invoke(instance, app)
            Log.d(TAG, "Application context explicitly registered on AndroidProvider via reflection")
        } catch (t: Throwable) {
            Log.w(TAG, "Could not set application on AndroidProvider (standard startup provider will be used)", t)
        }
    }

    private fun ensureApplicationContext() {
        try {
            val providerClass = Class.forName("com.revenuecat.purchases.kmp.di.AndroidProvider")
            val instanceField = providerClass.getDeclaredField("INSTANCE").apply { isAccessible = true }
            val instance = instanceField.get(null)
            val getAppMethod = providerClass.getDeclaredMethod("getApplication").apply { isAccessible = true }
            val app = getAppMethod.invoke(instance)
            if (app == null) {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val currentApplicationMethod = activityThreadClass.getMethod("currentApplication")
                val currentApp = currentApplicationMethod.invoke(null) as? Application
                if (currentApp != null) {
                    setApplication(currentApp)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Context check encountered: ${t.message}")
        }
    }

    actual fun initialize(apiKey: String) {
        val key = apiKey.trim().ifBlank { DEFAULT_GOOGLE_API_KEY }
        if (Purchases.isConfigured) {
            Log.d(TAG, "RevenueCat Purchases is already configured")
            return
        }

        ensureApplicationContext()

        try {
            Log.d(TAG, "Configuring RevenueCat Purchases with key: ${key.take(8)}...")
            Purchases.configure(PurchasesConfiguration.Builder(key).build())
            lastInitError = null
            Log.d(TAG, "RevenueCat Purchases successfully configured! isConfigured=${Purchases.isConfigured}")
        } catch (t: Throwable) {
            lastInitError = t.message ?: t::class.simpleName
            Log.e(TAG, "Failed to configure RevenueCat Purchases: $lastInitError", t)
        }
    }

    actual fun purchase(packageId: String, onResult: (success: Boolean, error: String?) -> Unit) {
        if (!Purchases.isConfigured) {
            Log.w(TAG, "purchase called but Purchases is not configured, attempting lazy initialization...")
            initialize(DEFAULT_GOOGLE_API_KEY)
        }

        if (!Purchases.isConfigured) {
            val diag = lastInitError ?: "SDK configuration failed"
            Log.e(TAG, "Store billing cannot proceed: $diag")
            onResult(false, "Store billing is not initialized ($diag)")
            return
        }

        Purchases.sharedInstance.getOfferings(
            onError = { error ->
                onResult(false, error.message)
            },
            onSuccess = { offerings ->
                val allPackages = offerings.all.values.flatMap { it.availablePackages }
                val currentOffering = offerings.current ?: offerings.all.values.firstOrNull()
                val pkg = currentOffering?.getPackage(packageId)
                    ?: currentOffering?.availablePackages?.find {
                        it.identifier.equals(packageId, ignoreCase = true) ||
                        it.storeProduct.id.equals(packageId, ignoreCase = true) ||
                        it.storeProduct.id.endsWith(".$packageId", ignoreCase = true)
                    }
                    ?: allPackages.find {
                        it.identifier.equals(packageId, ignoreCase = true) ||
                        it.storeProduct.id.equals(packageId, ignoreCase = true) ||
                        it.storeProduct.id.endsWith(".$packageId", ignoreCase = true)
                    }

                if (pkg == null) {
                    onResult(false, "Item not found in store offering")
                    return@getOfferings
                }

                Purchases.sharedInstance.purchase(
                    packageToPurchase = pkg,
                    onError = { error, userCancelled ->
                        if (userCancelled) {
                            onResult(false, "PURCHASE CANCELLED")
                        } else {
                            onResult(false, error.message)
                        }
                    },
                    onSuccess = { _, _ ->
                        onResult(true, null)
                    }
                )
            }
        )
    }

    actual fun restorePurchases(onResult: (success: Boolean, error: String?) -> Unit) {
        if (!Purchases.isConfigured) {
            Log.w(TAG, "restorePurchases called but Purchases is not configured, attempting lazy initialization...")
            initialize(DEFAULT_GOOGLE_API_KEY)
        }

        if (!Purchases.isConfigured) {
            val diag = lastInitError ?: "SDK configuration failed"
            Log.e(TAG, "Store billing cannot proceed: $diag")
            onResult(false, "Store billing is not initialized ($diag)")
            return
        }

        Purchases.sharedInstance.restorePurchases(
            onError = { error ->
                onResult(false, error.message)
            },
            onSuccess = { customerInfo ->
                val hasPurchases = customerInfo.entitlements.all.values.any { it.isActive }
                if (hasPurchases) {
                    onResult(true, null)
                } else {
                    onResult(false, "NO PREVIOUS PURCHASES FOUND")
                }
            }
        )
    }
}
