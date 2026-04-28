package com.qiplat.compose.sweeteditor.example.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.qiplat.compose.sweeteditor.SweetEditorDefaults
import com.qiplat.compose.sweeteditor.theme.SweetEditorTheme
import com.qiplat.compose.sweeteditor.theme.SweetEditorThemeScheme
import com.qiplat.compose.sweeteditor.theme.SweetEditorTypography

class VisualStudioEditorTheme : SweetEditorTheme(
    darkTheme = darkThemeScheme,
    lightTheme = lightThemeScheme,
)

private val visualStudioTypography = SweetEditorTypography(
    fontSize = 14.sp,
    lineNumberFontSize = 12.sp,
    inlayHintFontSize = 11.sp,
)

private val visualStudioDarkColors = SweetEditorDefaults.darkColors(
    background = Color(0xFF252526),
    text = Color(0xFFDCDCDC),
    cursor = Color(0xFFF5F5F5),
    selection = Color(0x663E5F8A),
    lineNumber = Color(0xFF858585),
    currentLineNumber = Color(0xFFC8C8C8),
    currentLine = Color(0x262B2B2B),
    guide = Color(0x33474747),
    separatorLine = Color(0xFF3F3F46),
    splitLine = Color(0x333F3F46),
    scrollbarTrack = Color(0x1F252526),
    scrollbarThumb = Color(0x806B6B6B),
    scrollbarThumbActive = Color(0xCC8A8A8A),
    compositionUnderline = Color(0xFF569CD6),
    inlayHintBackground = Color(0x26363636),
    inlayHintText = Color(0xCCB0B0B0),
    foldPlaceholderBackground = Color(0x333A3D41),
    foldPlaceholderText = Color(0xFFC8C8C8),
    phantomText = Color(0x99A0A0A0),
    inlayHintIcon = Color(0xCCB0B0B0),
    diagnosticError = Color(0xFFF14C4C),
    diagnosticWarning = Color(0xFFFFCC66),
    diagnosticInfo = Color(0xFF75BEFF),
    diagnosticHint = Color(0xFF8A8A8A),
    linkedEditingActive = Color(0xCC569CD6),
    linkedEditingInactive = Color(0x66569CD6),
    bracketHighlightBorder = Color(0xCC9A9A9A),
    bracketHighlightBackground = Color(0x26454545),
    gutterBackground = Color(0xFF2A2D2E),
)

private val visualStudioLightColors = SweetEditorDefaults.lightColors(
    background = Color(0xFFFFFFFF),
    text = Color(0xFF1E1E1E),
    cursor = Color(0xFF1E1E1E),
    selection = Color(0x665DA9FF),
    lineNumber = Color(0xFF7A7A7A),
    currentLineNumber = Color(0xFF0F5FA8),
    currentLine = Color(0x12000000),
    guide = Color(0x224E4E4E),
    separatorLine = Color(0xFFD7D7D7),
    splitLine = Color(0x224E4E4E),
    scrollbarTrack = Color(0x12000000),
    scrollbarThumb = Color(0x66777777),
    scrollbarThumbActive = Color(0x99828282),
    compositionUnderline = Color(0xFF006FC0),
    inlayHintBackground = Color(0x12307BC4),
    inlayHintText = Color(0xB05F5F5F),
    foldPlaceholderBackground = Color(0x1FDDDDDD),
    foldPlaceholderText = Color(0xFF666666),
    phantomText = Color(0x8A666666),
    inlayHintIcon = Color(0xB0666666),
    diagnosticError = Color(0xFFD13438),
    diagnosticWarning = Color(0xFFCA8A04),
    diagnosticInfo = Color(0xFF0078D4),
    diagnosticHint = Color(0xFF7A7A7A),
    linkedEditingActive = Color(0xCC006FC0),
    linkedEditingInactive = Color(0x66006FC0),
    bracketHighlightBorder = Color(0xCC6B6B6B),
    bracketHighlightBackground = Color(0x1FD6D6D6),
    gutterBackground = Color(0xFFF3F3F3),
)

private val visualStudioDarkSpanColors = SweetEditorDefaults.darkSpanColors(
    keyword = Color(0xFF569CD6),
    string = Color(0xFFCE9178),
    comment = Color(0xFF57A64A),
    number = Color(0xFFB5CEA8),
    builtin = Color(0xFFDCDCAA),
    type = Color(0xFF569CD6),
    className = Color(0xFF4EC9B0),
    function = Color(0xFFDCDCDC),
    variable = Color(0xFFDCDCDC),
    property = Color(0xFFDCDCDC),
    parameter = Color(0xFFDCDCDC),
    constant = Color(0xFF4FC1FF),
    field = Color(0xFFDCDCDC),
    namespace = Color(0xFFDCDCDC),
    enumMember = Color(0xFFDCDCAA),
    operator = Color(0xFFD4D4D4),
    punctuation = Color(0xFFD4D4D4),
    annotation = Color(0xFFDCDCDC),
    preprocessor = Color(0xFFCE9178),
)

private val visualStudioLightSpanColors = SweetEditorDefaults.lightSpanColors(
    keyword = Color(0xFF0000FF),
    string = Color(0xFFA31515),
    comment = Color(0xFF008000),
    number = Color(0xFF098658),
    builtin = Color(0xFF1E1E1E),
    type = Color(0xFF267F99),
    className = Color(0xFF267F99),
    function = Color(0xFF1E1E1E),
    variable = Color(0xFF1E1E1E),
    property = Color(0xFF795E26),
    parameter = Color(0xFF795E26),
    constant = Color(0xFF0070C1),
    field = Color(0xFF1E1E1E),
    namespace = Color(0xFF1E1E1E),
    enumMember = Color(0xFF098658),
    operator = Color(0xFF1E1E1E),
    punctuation = Color(0xFF1E1E1E),
    annotation = Color(0xFF1E1E1E),
    preprocessor = Color(0xFFAF00DB),
)

private val darkThemeScheme = SweetEditorThemeScheme(
    colors = visualStudioDarkColors,
    typography = visualStudioTypography,
    spanStyles = SweetEditorDefaults.spanStyles(visualStudioDarkSpanColors),
    cornerRadius = 2f,
)

private val lightThemeScheme = SweetEditorThemeScheme(
    colors = visualStudioLightColors,
    typography = visualStudioTypography,
    spanStyles = SweetEditorDefaults.spanStyles(visualStudioLightSpanColors),
    cornerRadius = 2f,
)
