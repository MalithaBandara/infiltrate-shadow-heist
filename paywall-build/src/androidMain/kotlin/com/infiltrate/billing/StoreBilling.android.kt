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

    private fun isRemoveAdsKey(id: String): Boolean {
        val clean = id.lowercase().replace("_", "").replace("-", "")
        return clean == "removeads" || clean == "noads" || clean == "lifetime" ||
               clean == "rc_lifetime" || clean == "rclifetime" || clean.contains("removead") || clean.contains("noad")
    }

    private fun packageMatches(pkg: com.revenuecat.purchases.kmp.models.Package, targetId: String): Boolean {
        val pkgId = pkg.identifier
        val prodId = pkg.storeProduct.id

        // Direct identifier or storeProduct ID match
        if (pkgId.equals(targetId, ignoreCase = true) || prodId.equals(targetId, ignoreCase = true)) return true

        // Suffix/prefix match (e.g. com.infiltrate.shadowheist.remove_ads or remove_ads:base-plan)
        if (prodId.endsWith(".$targetId", ignoreCase = true) ||
            prodId.startsWith("$targetId:", ignoreCase = true) ||
            prodId.startsWith(targetId, ignoreCase = true)
        ) return true

        // If seeking remove_ads / no_ads, match lifetime or remove_ads variations
        if (isRemoveAdsKey(targetId)) {
            if (pkgId.equals("\$rc_lifetime", ignoreCase = true) ||
                pkgId.equals("lifetime", ignoreCase = true) ||
                isRemoveAdsKey(pkgId) ||
                isRemoveAdsKey(prodId) ||
                prodId.contains("remove_ads", ignoreCase = true) ||
                prodId.contains("no_ads", ignoreCase = true) ||
                prodId.contains("noads", ignoreCase = true)
            ) {
                return true
            }
        }

        return false
    }

    private fun purchaseDirectProduct(
        packageId: String,
        allPackages: List<com.revenuecat.purchases.kmp.models.Package> = emptyList(),
        onResult: (success: Boolean, error: String?) -> Unit
    ) {
        val candidateProductIds = if (isRemoveAdsKey(packageId)) {
            listOf(
                packageId,
                "remove_ads",
                "no_ads",
                "noads",
                "com.infiltrate.shadowheist.remove_ads",
                "com.infiltrate.shadowheist.no_ads"
            ).distinct()
        } else {
            listOf(packageId, "com.infiltrate.shadowheist.$packageId").distinct()
        }

        Log.w(TAG, "Attempting direct product lookup for: $candidateProductIds")

        Purchases.sharedInstance.getProducts(
            productIds = candidateProductIds,
            onError = { prodErr ->
                val avail = allPackages.map { "${it.identifier} (${it.storeProduct.id})" }
                val msg = if (avail.isNotEmpty()) {
                    "Item '$packageId' not found in store offering. Available packages in RevenueCat: $avail"
                } else {
                    "Item '$packageId' not found: ${prodErr.message}"
                }
                Log.e(TAG, msg)
                onResult(false, msg)
            },
            onSuccess = { products ->
                Log.d(TAG, "Direct getProducts returned ${products.size} products: ${products.map { it.id }}")
                val prod = products.find { p ->
                    p.id.equals(packageId, ignoreCase = true) ||
                    p.id.startsWith("$packageId:", ignoreCase = true) ||
                    (isRemoveAdsKey(packageId) && (isRemoveAdsKey(p.id) || p.id.contains("remove", ignoreCase = true)))
                } ?: products.firstOrNull()

                if (prod != null) {
                    Log.d(TAG, "Found direct StoreProduct: ${prod.id}. Purchasing directly...")
                    Purchases.sharedInstance.purchase(
                        storeProduct = prod,
                        onError = { error, userCancelled ->
                            if (userCancelled) {
                                onResult(false, "PURCHASE CANCELLED")
                            } else {
                                Log.e(TAG, "StoreProduct purchase failed: ${error.message}")
                                onResult(false, error.message)
                            }
                        },
                        onSuccess = { _, _ ->
                            Log.d(TAG, "StoreProduct purchase succeeded for '${prod.id}'")
                            onResult(true, null)
                        }
                    )
                } else {
                    val avail = allPackages.map { "${it.identifier} (${it.storeProduct.id})" }
                    val msg = if (avail.isNotEmpty()) {
                        "Item '$packageId' not found in store offering. Available in RevenueCat: $avail"
                    } else {
                        "Item '$packageId' not found in Google Play store products."
                    }
                    Log.e(TAG, msg)
                    onResult(false, msg)
                }
            }
        )
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

        Log.d(TAG, "Starting purchase flow for target item: '$packageId'")

        Purchases.sharedInstance.getOfferings(
            onError = { error ->
                Log.w(TAG, "getOfferings error: ${error.message}. Trying direct product fallback...")
                purchaseDirectProduct(packageId, emptyList(), onResult)
            },
            onSuccess = { offerings ->
                val allPackages = offerings.all.values.flatMap { it.availablePackages }
                val currentOffering = offerings.current ?: offerings.all.values.firstOrNull()

                Log.d(TAG, "Offerings fetched. Current offering: ${currentOffering?.identifier}. Total packages: ${allPackages.size}")
                for (p in allPackages) {
                    Log.d(TAG, "Available package: id='${p.identifier}', storeProductId='${p.storeProduct.id}'")
                }

                // 1. Try finding matching package in current offering or all offerings
                val pkg = currentOffering?.getPackage(packageId)
                    ?: currentOffering?.availablePackages?.find { packageMatches(it, packageId) }
                    ?: allPackages.find { packageMatches(it, packageId) }

                if (pkg != null) {
                    Log.d(TAG, "Found matching package: id='${pkg.identifier}', storeProductId='${pkg.storeProduct.id}'. Initiating purchase...")
                    Purchases.sharedInstance.purchase(
                        packageToPurchase = pkg,
                        onError = { error, userCancelled ->
                            if (userCancelled) {
                                onResult(false, "PURCHASE CANCELLED")
                            } else {
                                Log.e(TAG, "Purchase failed: ${error.message}")
                                onResult(false, error.message)
                            }
                        },
                        onSuccess = { _, _ ->
                            Log.d(TAG, "Package purchase succeeded for '$packageId'")
                            onResult(true, null)
                        }
                    )
                    return@getOfferings
                }

                // 2. Direct StoreProduct fallback if no package in offering matches
                purchaseDirectProduct(packageId, allPackages, onResult)
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
                Log.e(TAG, "Restore error: ${error.message}")
                onResult(false, error.message)
            },
            onSuccess = { customerInfo ->
                val hasActiveEntitlements = customerInfo.entitlements.all.values.any { it.isActive }
                val hasPurchasedProducts = customerInfo.allPurchasedProductIdentifiers.any {
                    it.contains("remove_ads", ignoreCase = true) ||
                    it.contains("no_ads", ignoreCase = true) ||
                    it.contains("noads", ignoreCase = true) ||
                    it.contains("premium", ignoreCase = true)
                }
                val hasActiveSubs = customerInfo.activeSubscriptions.isNotEmpty()
                val hasPurchases = hasActiveEntitlements || hasPurchasedProducts || hasActiveSubs

                Log.d(TAG, "Restore result: activeEntitlements=$hasActiveEntitlements, purchasedProducts=$hasPurchasedProducts, activeSubs=$hasActiveSubs")

                if (hasPurchases) {
                    onResult(true, null)
                } else {
                    onResult(false, "NO PREVIOUS PURCHASES FOUND")
                }
            }
        )
    }
}
