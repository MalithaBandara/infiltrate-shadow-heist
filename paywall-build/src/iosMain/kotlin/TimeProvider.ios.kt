package com.infiltrate.ads

import platform.Foundation.NSDate

actual fun currentEpochSeconds(): Long = NSDate().timeIntervalSince1970.toLong()
