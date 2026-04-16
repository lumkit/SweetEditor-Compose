package com.qiplat.compose.sweeteditor

import androidx.compose.ui.geometry.Offset

actual fun getPlatformType(): PlatformType = PlatformType.Android

actual fun normalizePlatformMouseWheelScrollDelta(scrollDelta: Offset): Offset = scrollDelta
