package com.qiplat.compose.sweeteditor

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon

enum class PlatformType {
    Android, IOS, Desktop, Web
}

internal val LocalPlatformType = staticCompositionLocalOf {
    getPlatformType()
}

expect fun getPlatformType(): PlatformType

expect fun normalizePlatformMouseWheelScrollDelta(scrollDelta: Offset): Offset

expect fun textInputPointerIcon(): PointerIcon
