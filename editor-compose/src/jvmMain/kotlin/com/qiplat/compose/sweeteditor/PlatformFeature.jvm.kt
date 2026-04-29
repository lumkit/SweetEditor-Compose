package com.qiplat.compose.sweeteditor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor

actual fun getPlatformType(): PlatformType = PlatformType.Desktop

actual fun normalizePlatformMouseWheelScrollDelta(scrollDelta: Offset): Offset =
    if (scrollDelta == Offset.Zero) Offset.Zero else scrollDelta * -40f

actual fun textInputPointerIcon(): PointerIcon =
    PointerIcon(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR))
