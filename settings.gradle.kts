pluginManagement {
    repositories { mavenLocal(); mavenCentral(); google(); gradlePluginPortal() }
}

buildscript {
    val libsTomlFile = File(this.sourceFile?.parentFile, "gradle/libs.versions.toml").readText()
    var plugins = false
    var version = ""
    for (line in libsTomlFile.lines().map { it.trim() }) {
        if (line.startsWith("#")) continue
        if (line.startsWith("[plugins]")) plugins = true
        if (plugins && line.startsWith("korge") && Regex("^korge\\s*=.*").containsMatchIn(line)) version = Regex("version\\s*=\\s*\"(.*?)\"").find(line)?.groupValues?.get(1) ?: error("Can't find korge version")
    }
    if (version.isEmpty()) error("Can't find korge version in $libsTomlFile")

    repositories { mavenLocal(); mavenCentral(); google(); gradlePluginPortal() }

    dependencies {
        classpath("com.soywiz.korge.settings:com.soywiz.korge.settings.gradle.plugin:$version")
    }
}

apply(plugin = "com.soywiz.korge.settings")

rootProject.name = "korge-hello-world"

includeBuild("paywall-build")

// android-shell/ (the real Android app, hosting paywall-build's Compose UI + this project's own
// KorGE gameplay) is deliberately NOT included here as a subproject or composite build. This
// root build's Kotlin Gradle Plugin version is locked to 2.0.20 (KorGE 6.0.0's own requirement -
// the whole reason paywall-build is a SEPARATE composite build, not a subproject, in the first
// place) - a subproject can't run a different Kotlin/Compose plugin version than its root build,
// confirmed the hard way (Compose's plugin refused to apply: "Minimal supported Kotlin Gradle
// Plugin version is 2.2.0"). android-shell/ is its own fully separate Gradle build instead,
// consuming this project's and paywall-build's Android output as published `.aar` artifacts via
// mavenLocal() - see android-shell/README and .junie/guidelines.md "Watch ad to continue".
