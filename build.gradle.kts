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
    // Pinned to 1.9.0+14.3.0: the newest purchases-kmp-core release whose iOS klib is
    // still ABI-compatible with this project's Kotlin/Native toolchain. Verified by
    // inspecting klib manifests directly - RevenueCat's build moved to a Kotlin 2.1.x
    // compiler starting at their 2.0.0+15.0.0 release (klib abi_version 1.201.0), which
    // this toolchain (klib abi_version 1.8.0) can't read. 3.5.1 was also tried and
    // confirmed to fail the same way (abi_version 2.3.0, even further out of range) -
    // see .junie/guidelines.md for the full history.
    add("androidMainApi", "com.revenuecat.purchases:purchases-kmp-core:1.9.0+14.3.0")
    add("iosMainApi", "com.revenuecat.purchases:purchases-kmp-core:1.9.0+14.3.0")
    //add("commonMainApi", project(":korge-dragonbones"))
}

