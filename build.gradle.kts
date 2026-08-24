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
    add("androidMainApi", "com.revenuecat.purchases:purchases-kmp-core:2.10.2+17.55.1")
    add("iosMainApi", "com.revenuecat.purchases:purchases-kmp-core:2.10.2+17.55.1")
    //add("commonMainApi", project(":korge-dragonbones"))
}

