package com.qiplat.compose.sweeteditor.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import com.qiplat.compose.sweeteditor.SweetEditorDefaults

data class SweetEditorTypography(
    val fontFamily: FontFamily,
    val fontSize: TextUnit,
    val lineNumberFontSize: TextUnit,
    val inlayHintFontSize: TextUnit,
)
