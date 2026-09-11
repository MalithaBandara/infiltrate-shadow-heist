package com.infiltrate.ads

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.time

// NSDate().timeIntervalSince1970 reported "Unresolved reference" under this toolchain (Kotlin
// 2.4.10) even with @OptIn(ExperimentalForeignApi::class) - confirmed in real CI, not guessed
// (.junie/guidelines.md has the run details). Sidestepping Foundation/ObjC property interop
// entirely: platform.posix.time(null) is the plain C stdlib call, same "just get raw seconds
// since epoch" idiom Android/JVM already use via System.currentTimeMillis() / 1000L.
@OptIn(ExperimentalForeignApi::class)
actual fun currentEpochSeconds(): Long = time(null).toLong()
