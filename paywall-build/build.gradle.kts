import org.gradle.internal.os.OperatingSystem
import java.io.ByteArrayOutputStream

plugins {
    // Bumped from 2.3.20 to 2.4.10 (2026-09-01) specifically to match app.lexilabs.basic:basic-ads
    // (AdMob KMP wrapper)'s own pin - see .junie/guidelines.md "AdMob (basic-ads) feasibility
    // spike". Unlike :game's Kotlin 2.0.20 (locked to KorGE 6.0.0, do not touch), this module's
    // Kotlin version is a free choice made to match whatever native SDK it's bridging - bumping
    // it is low-risk since a newer Kotlin/Native compiler can read older klibs (the RevenueCat
    // purchases-kmp-core:3.6.0 klib below was compiled at 2.3.20/ABI 2.3.0), just not the reverse.
    kotlin("multiplatform") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    id("org.jetbrains.compose") version "1.12.0"
    id("com.android.library") version "8.5.2"
    // AdMob feasibility spike, part 2 (2026-09-01, see .junie/guidelines.md). The first attempt
    // (a plain Maven dependency on app.lexilabs.basic:basic-ads with no cocoapods setup) failed
    // to LINK with "ld: framework 'GoogleMobileAds' not found" - confirmed from the raw CI log,
    // not assumed - because basic-ads does NOT bundle Google-Mobile-Ads-SDK's compiled binary
    // into its published klib (unlike RevenueCat 3.x). This module needs to fetch and link that
    // pod itself, the same way basic-ads' own build does.
    id("org.jetbrains.kotlin.native.cocoapods") version "2.4.10"
}

repositories {
    google()
    mavenCentral()
}

android {
    namespace = "com.infiltrate.paywall"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// The link step failed with "Undefined symbols" for swiftCompatibility56/Concurrency/Packs -
// Kotlin/Native's linker invocation searched a hardcoded, nonexistent Xcode path
// (.../Xcode-16.4.app/.../usr/lib/swift/iphonesimulator/), not this runner's actual Xcode
// install, so it never found the real back-deployment compatibility libraries. Computed here
// via xcode-select rather than hardcoded, so it tracks whatever Xcode the runner actually has.
// Guarded to macOS only - this build.gradle.kts is also configured on Windows dev machines
// (composite builds configure every included build eagerly), where xcode-select doesn't exist.
val macDeveloperDir: String? = if (OperatingSystem.current().isMacOsX) {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine("xcode-select", "-p")
        standardOutput = stdout
    }
    stdout.toString().trim()
} else null

fun swiftLibPath(platformSdkName: String): String? =
    macDeveloperDir?.let { "$it/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/$platformSdkName" }

kotlin {
    androidTarget()
    jvm()

    // Matches basic-ads' own pod versions exactly (its gradle/libs.versions.toml:
    // cocoapods-admob = "13.8.0", cocoapods-ump = "3.1.0") - the whole point is for our compiled
    // klib to link against the SAME pod version basic-ads was compiled against, not a newer one
    // we happened to pick. noPodspec(): this module is embedded into ios-shell/ as a plain
    // compiled .framework via XcodeGen, never consumed through a Podfile itself, so there's no
    // need for Kotlin to generate one.
    cocoapods {
        ios.deploymentTarget = "15.0"
        noPodspec()
        pod("Google-Mobile-Ads-SDK") {
            moduleName = "GoogleMobileAds"
            version = "13.8.0"
            extraOpts += listOf("-compiler-option", "-fmodules")
        }
        pod("GoogleUserMessagingPlatform") {
            moduleName = "UserMessagingPlatform"
            version = "3.1.0"
            extraOpts += listOf("-compiler-option", "-fmodules")
        }
    }

    iosArm64 {
        binaries.framework {
            baseName = "PaywallModule"
            freeCompilerArgs += listOf("-Xbinary=bundleId=com.infiltrate.paywallmodule")
            swiftLibPath("iphoneos")?.let { linkerOpts += listOf("-L$it") }
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "PaywallModule"
            freeCompilerArgs += listOf("-Xbinary=bundleId=com.infiltrate.paywallmodule")
            swiftLibPath("iphonesimulator")?.let { linkerOpts += listOf("-L$it") }
        }
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("../src/game/model")
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.components.resources)
            }
        }
        val androidMain by getting {
            dependencies {
                // AdMob feasibility spike (2026-09-01, see .junie/guidelines.md). basic-ads only
                // publishes androidJvm + ios_arm64/ios_simulator_arm64 variants (checked directly
                // against its Maven Central Gradle module metadata, not assumed) - no jvm()
                // desktop variant, so it can't go in commonMain now that this module also targets
                // jvm() (would fail Gradle variant resolution for that target, the exact same
                // "no matching variant" lesson already learned from purchases-kmp-core 3.x above).
                implementation("app.lexilabs.basic:basic-ads:1.2.1")
                implementation("com.google.android.gms:play-services-ads:25.4.0")
                implementation("com.google.android.ump:user-messaging-platform:4.0.0")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                val osName = System.getProperty("os.name").lowercase()
                val osClassifier = when {
                    osName.contains("win") -> "win"
                    osName.contains("mac") -> "mac"
                    else -> "linux"
                }
                implementation("org.openjfx:javafx-base:21.0.2:$osClassifier")
                implementation("org.openjfx:javafx-graphics:21.0.2:$osClassifier")
                implementation("org.openjfx:javafx-media:21.0.2:$osClassifier")
                implementation("org.openjfx:javafx-swing:21.0.2:$osClassifier")
            }
        }
        // compose.ui (needed for ComposeUIViewController, iOS-only interop entry point) is
        // deliberately NOT in commonMain - it's UIKit-specific and would break the jvm() target,
        // same reasoning as purchases-kmp-core being iosMain-only just above.
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("com.revenuecat.purchases:purchases-kmp-core:3.6.0")
                implementation(compose.ui)
                implementation("app.lexilabs.basic:basic-ads:1.2.1")
            }
        }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
    }
}

compose.desktop {
    application {
        mainClass = "com.infiltrate.MainKt"
    }
}

