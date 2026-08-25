import korlibs.korge.gradle.*

plugins {
	alias(libs.plugins.korge)
}

korge {
	id = "com.sample.demo"

// To enable all targets at once

	//targetAll()

// To enable targets based on properties/environment variables
	//targetDefault()

// To selectively enable targets
	
	targetJvm()
	targetJs()
    targetWasm()
	targetDesktop()
	targetIos()
	targetAndroid()

	serializationJson()
}

(extensions.findByName("android") as? com.android.build.gradle.BaseExtension)?.apply {
    defaultConfig {
        minSdk = 23
    }
}


dependencies {
    add("commonMainApi", project(":deps"))
    // Android only for now. iOS deliberately does NOT depend on purchases-kmp-core -
    // every version's iOS klib is either ABI-incompatible with this toolchain (1.9.0
    // through 3.5.1, all checked) or requires linking PurchasesHybridCommon, which has
    // no prebuilt binary anywhere and would need to be compiled from source in CI (a
    // real undertaking, deliberately deferred given the timeline). src@ios/PurchasesBridge.ios.kt
    // is a stub with no real RevenueCat calls, so it doesn't need this dependency at
    // all. See .junie/guidelines.md for the full investigation history before
    // reintroducing this on iOS.
    add("androidMainApi", "com.revenuecat.purchases:purchases-kmp-core:1.9.0+14.3.0")
    //add("commonMainApi", project(":korge-dragonbones"))
}

