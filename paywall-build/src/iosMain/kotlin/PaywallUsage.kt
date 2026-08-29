import com.revenuecat.purchases.kmp.LogLevel
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.configure

fun initializePaywallSpike() {
    Purchases.logLevel = LogLevel.DEBUG
    Purchases.configure(apiKey = "spike_test_api_key") {
        appUserId = "spike_test_user"
    }
}
