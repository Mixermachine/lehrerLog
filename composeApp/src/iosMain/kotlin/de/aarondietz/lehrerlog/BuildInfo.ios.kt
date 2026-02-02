package de.aarondietz.lehrerlog

import kotlin.native.Platform

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
actual fun isDebugBuild(): Boolean {
    return Platform.isDebugBinary
}
