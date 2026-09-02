plugins {
    // android-shell is a genuinely separate Gradle build (its own settings.gradle.kts) - nothing
    // is pre-resolved on a shared classpath here, unlike when this was briefly a subproject of
    // the root build, so every plugin needs an explicit version again.
    // 9.1.0, not 8.5.2 like :game/paywall-build: Compose 1.12.0's own androidx.compose.runtime
    // (runtime-saveable-android) and basic-ads' transitive basic-logging-android both require
    // compileSdk 37 + AGP 9.1.0+ - a real, current requirement of these exact library versions,
    // not a mistake here. Isolated to this module only (its own separate Gradle build, per
    // settings.gradle.kts) - doesn't affect :game's or paywall-build's own AGP/Gradle versions.
    id("com.android.application") version "9.1.0"
    // AGP 9+ has built-in Kotlin support - org.jetbrains.kotlin.android is no longer needed
    // (applying it is now an error: "no longer required for Kotlin support since AGP 9.0").
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    id("org.jetbrains.compose") version "1.12.0"
}

repositories {
    google()
    mavenCentral()
    // paywall-build's published Android artifact (see paywall-build/build.gradle.kts's
    // publishLibraryVariants("release") + ./gradlew :paywall-build:publishToMavenLocal).
    mavenLocal()
}

android {
    namespace = "com.infiltrate.androidshell"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.infiltrate.androidshell"
        // Must be >= paywall-build's own minSdk (24) - AGP fails the merge otherwise.
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    // JVM 21, not 17: com.soywiz.korge:korge:6.0.0's own compiled classes contain inline
    // functions built against JVM target 21 - compiling this module at 17 fails every call site
    // that inlines into KorGE code ("Cannot inline bytecode built with JVM target 21 into
    // bytecode that is being built with JVM target 17"), which is most of GameplayScene.kt.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    sourceSets {
        getByName("main") {
            // :game's own game/engine code, compiled directly from source (not a dependency) -
            // :game's Android target is application-shaped (KorGE's projectType.isExecutable),
            // not library-shaped, so it structurally cannot be published/depended on as an AAR
            // the way paywall-build can (traced into KorGE's own plugin source - see
            // .junie/guidelines.md "Watch ad to continue"). Mirrors the exact technique
            // paywall-build/build.gradle.kts already uses for src/game/model - just a wider
            // slice of the same source tree.
            //
            // Deliberately NOT the whole ../src (only model + scene): the root of ../src also
            // holds ContinueAdBridge.kt/PurchasesBridge.kt, which declare `expect fun` paired
            // with `actual fun` in ../src@android - expect/actual only means something across
            // separate Kotlin Multiplatform source sets. Dumped into this single plain Android
            // module's one source set, both declarations become two identically-signatured
            // functions in the same compilation ("expect/actual only in multiplatform projects",
            // "overload resolution ambiguity"). This module supplies its own plain (non-expect)
            // com.sample.demo.ads.getContinueAdBridge() instead - see ContinueAdBridge.kt here.
            kotlin.srcDirs("../src/game/model", "../src/game/scene")
            // GameplayScene loads its sprites/fonts/sounds/level data via KorGE's resourcesVfs,
            // which on Android reads from the APK's assets/ folder. :game's own Android build
            // (via KorGE's targetAndroid()) copies the repo's resources/ directory there
            // automatically as part of its own asset pipeline - android-shell never runs that
            // pipeline (separate Gradle build entirely), so without this, resourcesVfs finds
            // nothing and GameplayScene has nothing to render (root cause of the reported grey
            // screen on starting a level). MenuMusic.android.kt's own doc comment independently
            // confirms this exact path as its assets/ fallback for resources/mainmenu.mp3.
            assets.srcDirs("../resources")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    // KorGE itself, straight from Maven Central - same version :game itself pins
    // (gradle/libs.versions.toml). Consumed as a compiled library, not through :game's project,
    // so its own Kotlin 2.0.20 origin doesn't matter here (compiled-artifact consumption, not a
    // shared Kotlin Gradle Plugin version the way a subproject would need).
    implementation("com.soywiz.korge:korge:6.0.0")

    // paywall-build/'s Compose UI + basic-ads wiring, via mavenLocal() (see repositories above).
    implementation("com.infiltrate:paywall-build:1.0")
    // paywall-build declares this as `implementation`, not `api`, so it's on the runtime
    // classpath but not visible to compile against here - MainActivity.kt calls
    // BasicAds.Initialize() directly (same as AdMobVerifyContent() does on iOS), so it needs its
    // own explicit reference to the same version.
    implementation("app.lexilabs.basic:basic-ads:1.2.1")

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    // Deliberately NOT compose.ui explicitly - paywall-build's own androidMain doesn't declare
    // it either (relies on it coming transitively via foundation/material3); declaring it
    // directly here dragged in newer androidx.compose.ui-text-android/runtime-saveable-android
    // builds than the ones paywall-build's own resolved graph settled on, which need
    // compileSdk 37 + AGP 9.1.0 - way past what's set up here. AndroidView (used in
    // MainActivity.kt) is still available transitively without it.
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
}
