package com.qiplat.compose.sweeteditor

import androidx.compose.ui.geometry.Offset

actual fun getPlatformType(): PlatformType = PlatformType.Desktop

actual fun normalizePlatformMouseWheelScrollDelta(scrollDelta: Offset): Offset =
    if (scrollDelta == Offset.Zero) Offset.Zero else scrollDelta * 40f
