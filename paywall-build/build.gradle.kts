import org.gradle.internal.os.OperatingSystem
import java.io.ByteArrayOutputStream

plugins {
    kotlin("multiplatform") version "2.3.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
    id("org.jetbrains.compose") version "1.12.0"
}

repositories {
    google()
    mavenCentral()
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
    jvm()
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
            }
        }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
    }
}
