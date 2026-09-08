package com.infiltrate.billing

import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesConfiguration

actual object StoreBilling {

    actual fun isConfigured(): Boolean = Purchases.isConfigured

    actual fun initialize(apiKey: String) {
        if (apiKey.isBlank()) return
        if (!Purchases.isConfigured) {
            Purchases.configure(PurchasesConfiguration.Builder(apiKey).build())
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
