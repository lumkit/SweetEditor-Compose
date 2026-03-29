package com.qiplat.compose.sweeteditor.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.qiplat.compose.sweeteditor.model.decoration.TextStyle

object EditorThemeStyleIds {
    const val Keyword: Int = 1
    const val String: Int = 2
    const val Comment: Int = 3
    const val Number: Int = 4
    const val Builtin: Int = 5
    const val Type: Int = 6
    const val Class: Int = 7
    const val Function: Int = 8
    const val Variable: Int = 9
    const val Punctuation: Int = 10
    const val Annotation: Int = 11
    const val Preprocessor: Int = 12
    const val UserBase: Int = 100
}

data class EditorTheme(
    val backgroundColor: Int,
    val textColor: Int,
    val cursorColor: Int,
    val selectionColor: Int,
    val lineNumberColor: Int,
    val currentLineNumberColor: Int,
    val currentLineColor: Int,
    val guideColor: Int,
    val separatorLineColor: Int,
    val splitLineColor: Int,
    val scrollbarTrackColor: Int,
    val scrollbarThumbColor: Int,
    val scrollbarThumbActiveColor: Int,
    val compositionUnderlineColor: Int,
    val inlayHintBackgroundColor: Int,
    val inlayHintTextColor: Int,
    val foldPlaceholderBackgroundColor: Int,
    val foldPlaceholderTextColor: Int,
    val phantomTextColor: Int,
    val inlayHintIconColor: Int,
    val diagnosticErrorColor: Int,
    val diagnosticWarningColor: Int,
    val diagnosticInfoColor: Int,
    val diagnosticHintColor: Int,
    val linkedEditingActiveColor: Int,
    val linkedEditingInactiveColor: Int,
    val bracketHighlightBorderColor: Int,
    val bracketHighlightBackgroundColor: Int,
    val gutterBackgroundColor: Int,
    val fontFamily: FontFamily = FontFamily.Monospace,
    val fontSize: TextUnit = 14.sp,
    val lineNumberFontSize: TextUnit = 13.sp,
    val inlayHintFontSize: TextUnit = 12.sp,
    val textStyles: Map<Int, TextStyle>,
) {
    companion object {
        fun dark(
            fontFamily: FontFamily = FontFamily.Monospace,
            fontSize: TextUnit = 14.sp,
        ): EditorTheme = EditorTheme(
            backgroundColor = 0xFF1B1E24.toInt(),
            textColor = 0xFFD7DEE9.toInt(),
            cursorColor = 0xFF8FB8FF.toInt(),
            selectionColor = 0x553B4F72,
            lineNumberColor = 0xFF5E6778.toInt(),
            currentLineNumberColor = 0xFF9CB3D6.toInt(),
            currentLineColor = 0x163A4A66,
            guideColor = 0x2E56617A,
            separatorLineColor = 0xFF4A8F7A.toInt(),
            splitLineColor = 0x3356617A,
            scrollbarTrackColor = 0x2AFFFFFF,
            scrollbarThumbColor = 0x9A7282A0.toInt(),
            scrollbarThumbActiveColor = 0xFFAABEDD.toInt(),
            compositionUnderlineColor = 0xFF7AA2F7.toInt(),
            inlayHintBackgroundColor = 0x223A4A66,
            inlayHintTextColor = 0xC0AFC2E0.toInt(),
            foldPlaceholderBackgroundColor = 0x36506C90,
            foldPlaceholderTextColor = 0xFFE2ECFF.toInt(),
            phantomTextColor = 0x8AA3B5D1.toInt(),
            inlayHintIconColor = 0xCC9CB0CD.toInt(),
            diagnosticErrorColor = 0xFFF7768E.toInt(),
            diagnosticWarningColor = 0xFFE0AF68.toInt(),
            diagnosticInfoColor = 0xFF7DCFFF.toInt(),
            diagnosticHintColor = 0xFF8FA3BF.toInt(),
            linkedEditingActiveColor = 0xCC7AA2F7.toInt(),
            linkedEditingInactiveColor = 0x667AA2F7,
            bracketHighlightBorderColor = 0xCC9ECE6A.toInt(),
            bracketHighlightBackgroundColor = 0x2A9ECE6A,
            gutterBackgroundColor = 0xFF20242C.toInt(),
            fontFamily = fontFamily,
            fontSize = fontSize,
            textStyles = defaultDarkTextStyles(),
        )

        fun light(
            fontFamily: FontFamily = FontFamily.Monospace,
            fontSize: TextUnit = 14.sp,
        ): EditorTheme = EditorTheme(
            backgroundColor = 0xFFFAFBFD.toInt(),
            textColor = 0xFF1F2937.toInt(),
            cursorColor = 0xFF2563EB.toInt(),
            selectionColor = 0x4D60A5FA,
            lineNumberColor = 0xFF8A94A6.toInt(),
            currentLineNumberColor = 0xFF3A5FA0.toInt(),
            currentLineColor = 0x120D3B66,
            guideColor = 0x2229426B,
            separatorLineColor = 0xFF2F855A.toInt(),
            splitLineColor = 0x1F29426B,
            scrollbarTrackColor = 0x1F2A3B55,
            scrollbarThumbColor = 0x80446C9C.toInt(),
            scrollbarThumbActiveColor = 0xEE6A9AD0.toInt(),
            compositionUnderlineColor = 0xFF2563EB.toInt(),
            inlayHintBackgroundColor = 0x143B82F6,
            inlayHintTextColor = 0xB0344A73.toInt(),
            foldPlaceholderBackgroundColor = 0x2E748DB0,
            foldPlaceholderTextColor = 0xFF284A70.toInt(),
            phantomTextColor = 0x8A4B607E.toInt(),
            inlayHintIconColor = 0xB04B607E.toInt(),
            diagnosticErrorColor = 0xFFDC2626.toInt(),
            diagnosticWarningColor = 0xFFD97706.toInt(),
            diagnosticInfoColor = 0xFF0EA5E9.toInt(),
            diagnosticHintColor = 0xFF64748B.toInt(),
            linkedEditingActiveColor = 0xCC2563EB.toInt(),
            linkedEditingInactiveColor = 0x662563EB,
            bracketHighlightBorderColor = 0xCC0F766E.toInt(),
            bracketHighlightBackgroundColor = 0x260F766E,
            gutterBackgroundColor = 0xFFF3F5F8.toInt(),
            fontFamily = fontFamily,
            fontSize = fontSize,
            textStyles = defaultLightTextStyles(),
        )

        private fun defaultDarkTextStyles(): Map<Int, TextStyle> = mapOf(
            EditorThemeStyleIds.Keyword to TextStyle(0xFF7AA2F7.toInt(), fontStyle = TextStyle.Bold),
            EditorThemeStyleIds.String to TextStyle(0xFF9ECE6A.toInt()),
            EditorThemeStyleIds.Comment to TextStyle(0xFF7A8294.toInt(), fontStyle = TextStyle.Italic),
            EditorThemeStyleIds.Number to TextStyle(0xFFFF9E64.toInt()),
            EditorThemeStyleIds.Builtin to TextStyle(0xFF7DCFFF.toInt()),
            EditorThemeStyleIds.Type to TextStyle(0xFFBB9AF7.toInt()),
            EditorThemeStyleIds.Class to TextStyle(0xFFE0AF68.toInt(), fontStyle = TextStyle.Bold),
            EditorThemeStyleIds.Function to TextStyle(0xFF73DACA.toInt()),
            EditorThemeStyleIds.Variable to TextStyle(0xFFD7DEE9.toInt()),
            EditorThemeStyleIds.Punctuation to TextStyle(0xFFB0BED3.toInt()),
            EditorThemeStyleIds.Annotation to TextStyle(0xFF2AC3DE.toInt()),
            EditorThemeStyleIds.Preprocessor to TextStyle(0xFFF7768E.toInt()),
        )

        private fun defaultLightTextStyles(): Map<Int, TextStyle> = mapOf(
            EditorThemeStyleIds.Keyword to TextStyle(0xFF3559D6.toInt(), fontStyle = TextStyle.Bold),
            EditorThemeStyleIds.String to TextStyle(0xFF0F7B6C.toInt()),
            EditorThemeStyleIds.Comment to TextStyle(0xFF7B8798.toInt(), fontStyle = TextStyle.Italic),
            EditorThemeStyleIds.Number to TextStyle(0xFFB45309.toInt()),
            EditorThemeStyleIds.Builtin to TextStyle(0xFF0EA5E9.toInt()),
            EditorThemeStyleIds.Type to TextStyle(0xFF7C3AED.toInt()),
            EditorThemeStyleIds.Class to TextStyle(0xFFB7791F.toInt(), fontStyle = TextStyle.Bold),
            EditorThemeStyleIds.Function to TextStyle(0xFF0F766E.toInt()),
            EditorThemeStyleIds.Variable to TextStyle(0xFF1F2937.toInt()),
            EditorThemeStyleIds.Punctuation to TextStyle(0xFF52606D.toInt()),
            EditorThemeStyleIds.Annotation to TextStyle(0xFF0284C7.toInt()),
            EditorThemeStyleIds.Preprocessor to TextStyle(0xFFDC2626.toInt()),
        )
    }
}
