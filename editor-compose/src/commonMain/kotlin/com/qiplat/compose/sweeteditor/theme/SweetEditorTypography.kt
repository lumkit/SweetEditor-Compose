package com.qiplat.compose.sweeteditor.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import com.qiplat.compose.sweeteditor.theme.tokens.TypographyTokens

data class SweetEditorTypography(
    val fontFamily: FontFamily = TypographyTokens.FontFamily,
    val fontSize: TextUnit = TypographyTokens.FontSize,
    val lineNumberFontSize: TextUnit = TypographyTokens.LineNumberFontSize,
    val inlayHintFontSize: TextUnit = TypographyTokens.InlayHintFontSize,
    val iconSize: TextUnit = TypographyTokens.IconSize,
)
