package com.infiltrate.billing

import com.revenuecat.purchases.kmp.LogLevel
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesConfiguration
import kotlin.native.ObjCName

@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "StoreBilling", exact = true)
actual object StoreBilling {

    private const val DEFAULT_APPLE_API_KEY = "appl_jnRvGBajbaDGqSLhCCdvqvwsaHs"

    actual fun isConfigured(): Boolean = Purchases.isConfigured

    actual fun initialize(apiKey: String) {
        val key = apiKey.trim().ifBlank { DEFAULT_APPLE_API_KEY }
        if (key.isBlank()) return
        if (!Purchases.isConfigured) {
            Purchases.logLevel = LogLevel.DEBUG
            Purchases.configure(PurchasesConfiguration.Builder(key).build())
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

        if (pkgId.equals(targetId, ignoreCase = true) || prodId.equals(targetId, ignoreCase = true)) return true
        if (prodId.endsWith(".$targetId", ignoreCase = true) || prodId.startsWith("$targetId:", ignoreCase = true)) return true

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

    actual fun purchase(packageId: String, onResult: (success: Boolean, error: String?) -> Unit) {
        if (!Purchases.isConfigured) {
            initialize(DEFAULT_APPLE_API_KEY)
        }
        if (!Purchases.isConfigured) {
            onResult(false, "Store billing is not initialized")
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
                    ?: currentOffering?.availablePackages?.find { packageMatches(it, packageId) }
                    ?: allPackages.find { packageMatches(it, packageId) }

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
            initialize(DEFAULT_APPLE_API_KEY)
        }
        if (!Purchases.isConfigured) {
            onResult(false, "Store billing is not initialized")
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

@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "RevenueCatVerifyBridge", exact = true)
object RevenueCatVerifyBridge {
    var checkStarted: Boolean = false
        private set
    var checkFinished: Boolean = false
        private set
    var success: Boolean = false
        private set
    var resultText: String = "PENDING"
        private set

    fun startVerification() {
        if (checkStarted) return
        checkStarted = true
        if (!Purchases.isConfigured) {
            StoreBilling.initialize("")
        }
        Purchases.sharedInstance.getOfferings(
            onError = { error ->
                success = false
                resultText = "FAIL:${error.message}"
                checkFinished = true
            },
            onSuccess = { offerings ->
                val allPackages = offerings.all.values.flatMap { it.availablePackages }
                val packageIds = allPackages.map { it.identifier }.joinToString(";")
                success = allPackages.isNotEmpty()
                resultText = "OK:currentOffering=${offerings.current?.identifier ?: "none"}:packageCount=${allPackages.size}:packages=$packageIds"
                checkFinished = true
            }
        )
    }
}

