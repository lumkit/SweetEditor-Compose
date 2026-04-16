package com.qiplat.compose.sweeteditor

import androidx.compose.ui.geometry.Offset

actual fun getPlatformType(): PlatformType = PlatformType.Web

actual fun normalizePlatformMouseWheelScrollDelta(scrollDelta: Offset): Offset = scrollDelta
