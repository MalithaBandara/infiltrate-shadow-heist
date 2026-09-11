package com.infiltrate.ads

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate

@OptIn(ExperimentalForeignApi::class)
actual fun currentEpochSeconds(): Long = NSDate().timeIntervalSince1970.toLong()
