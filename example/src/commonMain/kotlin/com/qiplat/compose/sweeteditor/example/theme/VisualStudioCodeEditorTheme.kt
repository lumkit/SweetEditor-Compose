package com.qiplat.compose.sweeteditor.example.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.qiplat.compose.sweeteditor.SweetEditorDefaults
import com.qiplat.compose.sweeteditor.theme.SweetEditorTheme
import com.qiplat.compose.sweeteditor.theme.SweetEditorThemeScheme
import com.qiplat.compose.sweeteditor.theme.SweetEditorTypography

class VisualStudioCodeEditorTheme : SweetEditorTheme(
    darkTheme = darkThemeScheme,
    lightTheme = lightThemeScheme,
)

private val vscodeTypography = SweetEditorTypography(
    fontSize = 14.sp,
    lineNumberFontSize = 12.sp,
    inlayHintFontSize = 11.sp,
)

private val vscodeDarkColors = SweetEditorDefaults.darkColors(
    background = Color(0xFF1E1E1E),
    text = Color(0xFFD4D4D4),
    cursor = Color(0xFFAEAFAD),
    selection = Color(0x6652644F),
    lineNumber = Color(0xFF858585),
    currentLineNumber = Color(0xFFC6C6C6),
    currentLine = Color(0x262A2D2E),
    guide = Color(0x33404040),
    separatorLine = Color(0xFF2A2A2A),
    splitLine = Color(0x33404040),
    scrollbarTrack = Color(0x1F1F1F1F),
    scrollbarThumb = Color(0x80606060),
    scrollbarThumbActive = Color(0xCC747474),
    compositionUnderline = Color(0xFF4FC1FF),
    inlayHintBackground = Color(0x26303030),
    inlayHintText = Color(0xCC969696),
    foldPlaceholderBackground = Color(0x33333333),
    foldPlaceholderText = Color(0xFF9A9A9A),
    phantomText = Color(0x998A8A8A),
    inlayHintIcon = Color(0xCC9A9A9A),
    diagnosticError = Color(0xFFF14C4C),
    diagnosticWarning = Color(0xFFFFCC66),
    diagnosticInfo = Color(0xFF75BEFF),
    diagnosticHint = Color(0xFF8A8A8A),
    linkedEditingActive = Color(0xCC4FC1FF),
    linkedEditingInactive = Color(0x664FC1FF),
    bracketHighlightBorder = Color(0xCC7A7A7A),
    bracketHighlightBackground = Color(0x264A4A4A),
    gutterBackground = Color(0xFF181818),
)

private val vscodeLightColors = SweetEditorDefaults.lightColors(
    background = Color(0xFFFFFFFF),
    text = Color(0xFF333333),
    cursor = Color(0xFF000000),
    selection = Color(0x6643A6F5),
    lineNumber = Color(0xFF237893),
    currentLineNumber = Color(0xFF2C2C2C),
    currentLine = Color(0x0F000000),
    guide = Color(0x223A3A3A),
    separatorLine = Color(0xFFEAEAEA),
    splitLine = Color(0x223A3A3A),
    scrollbarTrack = Color(0x12000000),
    scrollbarThumb = Color(0x66757575),
    scrollbarThumbActive = Color(0x99818181),
    compositionUnderline = Color(0xFF0066BF),
    inlayHintBackground = Color(0x122B88D8),
    inlayHintText = Color(0xB05C5C5C),
    foldPlaceholderBackground = Color(0x1FDDDDDD),
    foldPlaceholderText = Color(0xFF666666),
    phantomText = Color(0x8A666666),
    inlayHintIcon = Color(0xB0666666),
    diagnosticError = Color(0xFFD13438),
    diagnosticWarning = Color(0xFFCA8A04),
    diagnosticInfo = Color(0xFF0078D4),
    diagnosticHint = Color(0xFF7A7A7A),
    linkedEditingActive = Color(0xCC0066BF),
    linkedEditingInactive = Color(0x660066BF),
    bracketHighlightBorder = Color(0xCC6B6B6B),
    bracketHighlightBackground = Color(0x1FD6D6D6),
    gutterBackground = Color(0xFFF3F3F3),
)

private val vscodeDarkSpanColors = SweetEditorDefaults.darkSpanColors(
    keyword = Color(0xFF569CD6),
    string = Color(0xFFCE9178),
    comment = Color(0xFF6A9955),
    number = Color(0xFFB5CEA8),
    builtin = Color(0xFFD4D4D4),
    type = Color(0xFFDCDCAA),
    className = Color(0xFF4EC9B0),
    function = Color(0xFFDCDCAA),
    variable = Color(0xFFD4D4D4),
    property = Color(0xFFD4D4D4),
    parameter = Color(0xFFD4D4D4),
    constant = Color(0xFF4FC1FF),
    field = Color(0xFFD4D4D4),
    namespace = Color(0xFFD4D4D4),
    enumMember = Color(0xFFB8D7A3),
    operator = Color(0xFFD4D4D4),
    punctuation = Color(0xFFD4D4D4),
    annotation = Color(0xFFDCDCAA),
    preprocessor = Color(0xFF569CD6),
)

private val vscodeLightSpanColors = SweetEditorDefaults.lightSpanColors(
    keyword = Color(0xFFAF00DB),
    string = Color(0xFFA31515),
    comment = Color(0xFF008000),
    number = Color(0xFF098658),
    builtin = Color(0xFF795E26),
    type = Color(0xFF267F99),
    className = Color(0xFF267F99),
    function = Color(0xFF795E26),
    variable = Color(0xFF001080),
    property = Color(0xFF795E26),
    parameter = Color(0xFF001080),
    constant = Color(0xFF0070C1),
    field = Color(0xFF001080),
    namespace = Color(0xFF267F99),
    enumMember = Color(0xFF098658),
    operator = Color(0xFF333333),
    punctuation = Color(0xFF333333),
    annotation = Color(0xFF795E26),
    preprocessor = Color(0xFFAF00DB),
)

private val darkThemeScheme = SweetEditorThemeScheme(
    colors = vscodeDarkColors,
    typography = vscodeTypography,
    spanStyles = SweetEditorDefaults.spanStyles(vscodeDarkSpanColors),
    cornerRadius = 2f,
)

private val lightThemeScheme = SweetEditorThemeScheme(
    colors = vscodeLightColors,
    typography = vscodeTypography,
    spanStyles = SweetEditorDefaults.spanStyles(vscodeLightSpanColors),
    cornerRadius = 2f,
)
