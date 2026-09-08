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
