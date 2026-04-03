package com.qiplat.compose.sweeteditor

import androidx.compose.runtime.staticCompositionLocalOf

enum class PlatformType {
    Android, IOS, Desktop, Web
}

internal val LocalPlatformType = staticCompositionLocalOf {
    getPlatformType()
}

expect fun getPlatformType(): PlatformType