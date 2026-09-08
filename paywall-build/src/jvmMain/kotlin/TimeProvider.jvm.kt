package com.infiltrate.ads

actual fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1000L
