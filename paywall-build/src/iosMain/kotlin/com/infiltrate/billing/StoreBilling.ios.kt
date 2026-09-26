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

    private fun formatError(error: com.revenuecat.purchases.kmp.models.PurchasesError): String {
        val underlying = error.underlyingErrorMessage
        return if (!underlying.isNullOrBlank()) {
            "${error.message} ($underlying)"
        } else {
            error.message
        }
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

        Purchases.sharedInstance.getProducts(
            productIds = candidateProductIds,
            onError = { prodErr ->
                val avail = allPackages.map { "${it.identifier} (${it.storeProduct.id})" }
                val underlying = prodErr.underlyingErrorMessage
                val msg = if (!underlying.isNullOrBlank()) {
                    "${prodErr.message} ($underlying)"
                } else if (avail.isNotEmpty()) {
                    "Item '$packageId' not found in store offering. Available packages in RevenueCat: $avail"
                } else {
                    "Item '$packageId' not found: ${prodErr.message}"
                }
                onResult(false, msg)
            },
            onSuccess = { products ->
                val prod = products.find { p ->
                    p.id.equals(packageId, ignoreCase = true) ||
                    p.id.startsWith("$packageId:", ignoreCase = true) ||
                    (isRemoveAdsKey(packageId) && (isRemoveAdsKey(p.id) || p.id.contains("remove", ignoreCase = true)))
                } ?: products.firstOrNull()

                if (prod != null) {
                    Purchases.sharedInstance.purchase(
                        storeProduct = prod,
                        onError = { error, userCancelled ->
                            if (userCancelled) {
                                onResult(false, "PURCHASE CANCELLED")
                            } else {
                                onResult(false, formatError(error))
                            }
                        },
                        onSuccess = { _, _ ->
                            onResult(true, null)
                        }
                    )
                } else {
                    val avail = allPackages.map { "${it.identifier} (${it.storeProduct.id})" }
                    val msg = if (avail.isNotEmpty()) {
                        "Item '$packageId' not found in store offering. Available in RevenueCat: $avail"
                    } else {
                        "Item '$packageId' not found in App Store products. Please verify in App Store Connect that '$packageId' is Cleared for Sale, Pricing is set, and the Paid Applications Agreement is Active."
                    }
                    onResult(false, msg)
                }
            }
        )
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
                purchaseDirectProduct(packageId, onResult = onResult)
            },
            onSuccess = { offerings ->
                val allPackages = offerings.all.values.flatMap { it.availablePackages }
                val currentOffering = offerings.current ?: offerings.all.values.firstOrNull()
                val pkg = currentOffering?.getPackage(packageId)
                    ?: currentOffering?.availablePackages?.find { packageMatches(it, packageId) }
                    ?: allPackages.find { packageMatches(it, packageId) }

                if (pkg == null) {
                    purchaseDirectProduct(packageId, allPackages, onResult)
                    return@getOfferings
                }

                Purchases.sharedInstance.purchase(
                    packageToPurchase = pkg,
                    onError = { error, userCancelled ->
                        if (userCancelled) {
                            onResult(false, "PURCHASE CANCELLED")
                        } else {
                            onResult(false, formatError(error))
                        }
                    },
                    onSuccess = { _, _ ->
                        onResult(true, null)
                    }
                )
            }
        )
    }

    actual fun fetchLocalizedPrices(packageIds: List<String>, onResult: (Map<String, String>) -> Unit) {
        if (!Purchases.isConfigured) {
            initialize(DEFAULT_APPLE_API_KEY)
        }
        if (!Purchases.isConfigured) {
            onResult(emptyMap())
            return
        }

        Purchases.sharedInstance.getOfferings(
            onError = { onResult(emptyMap()) },
            onSuccess = { offerings ->
                val allPackages = offerings.all.values.flatMap { it.availablePackages }
                val currentOffering = offerings.current ?: offerings.all.values.firstOrNull()
                val result = packageIds.mapNotNull { id ->
                    val pkg = currentOffering?.getPackage(id)
                        ?: currentOffering?.availablePackages?.find { packageMatches(it, id) }
                        ?: allPackages.find { packageMatches(it, id) }
                    pkg?.let { id to it.storeProduct.price.formatted }
                }.toMap()
                onResult(result)
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
                onResult(false, formatError(error))
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
                val underlying = error.underlyingErrorMessage
                resultText = if (!underlying.isNullOrBlank()) {
                    "FAIL:${error.message}:underlying=$underlying"
                } else {
                    "FAIL:${error.message}"
                }
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

