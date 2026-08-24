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
    // TEST (2026-08-25): trying 3.5.1 to see if its bundled-klib iOS approach (no
    // CocoaPods needed) resolves cleanly. klib manifest check beforehand showed
    // abi_version=2.3.0 (compiler 2.3.20) - an even bigger gap than 2.10.2's 1.201.0,
    // against this toolchain's max readable ABI of 1.8.0 (korge is still 6.0.0 here,
    // unchanged) - so this is expected to fail the same way. Confirming via CI per
    // request. If it fails, revert to 1.9.0+14.3.0 (see .junie/guidelines.md).
    add("androidMainApi", "com.revenuecat.purchases:purchases-kmp-core:3.5.1")
    add("iosMainApi", "com.revenuecat.purchases:purchases-kmp-core:3.5.1")
    //add("commonMainApi", project(":korge-dragonbones"))
}

