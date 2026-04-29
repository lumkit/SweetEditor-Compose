package com.qiplat.compose.sweeteditor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon

actual fun getPlatformType(): PlatformType = PlatformType.IOS

actual fun normalizePlatformMouseWheelScrollDelta(scrollDelta: Offset): Offset = scrollDelta

actual fun textInputPointerIcon(): PointerIcon = PointerIcon.Text
