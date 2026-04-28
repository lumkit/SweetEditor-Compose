package com.qiplat.compose.sweeteditor.example.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.qiplat.compose.sweeteditor.SweetEditorDefaults
import com.qiplat.compose.sweeteditor.theme.SweetEditorTheme
import com.qiplat.compose.sweeteditor.theme.SweetEditorThemeScheme
import com.qiplat.compose.sweeteditor.theme.SweetEditorTypography

class JetBrainsIdeaEditorTheme : SweetEditorTheme(
    darkTheme = darkThemeScheme,
    lightTheme = lightThemeScheme,
)

private val ideaTypography = SweetEditorTypography(
    fontSize = 14.sp,
    lineNumberFontSize = 12.sp,
    inlayHintFontSize = 11.sp,
)

private val ideaDarkColors = SweetEditorDefaults.darkColors(
    background = Color(0xFF2B2D30),
    text = Color(0xFFA9B7C6),
    cursor = Color(0xFFAEAFAD),
    selection = Color(0x664D6079),
    lineNumber = Color(0xFF606366),
    currentLineNumber = Color(0xFFCFD3D8),
    currentLine = Color(0x1F32343A),
    guide = Color(0x334A4E55),
    separatorLine = Color(0xFF4B5057),
    splitLine = Color(0x334A4E55),
    scrollbarTrack = Color(0x1F2B2D30),
    scrollbarThumb = Color(0x80555A60),
    scrollbarThumbActive = Color(0xCC6A7078),
    compositionUnderline = Color(0xFF6A9EFF),
    inlayHintBackground = Color(0x26394048),
    inlayHintText = Color(0xCC8B949E),
    foldPlaceholderBackground = Color(0x333C4149),
    foldPlaceholderText = Color(0xFF9AA3AD),
    phantomText = Color(0x99828A93),
    inlayHintIcon = Color(0xCC8B949E),
    diagnosticError = Color(0xFFFF6B68),
    diagnosticWarning = Color(0xFFFFC66D),
    diagnosticInfo = Color(0xFF75BEFF),
    diagnosticHint = Color(0xFF7F8A96),
    linkedEditingActive = Color(0xCC6A9EFF),
    linkedEditingInactive = Color(0x666A9EFF),
    bracketHighlightBorder = Color(0xCC7AA2F7),
    bracketHighlightBackground = Color(0x26506A91),
    gutterBackground = Color(0xFF313335),
)

private val ideaLightColors = SweetEditorDefaults.lightColors(
    background = Color(0xFFFFFFFF),
    text = Color(0xFF1F2329),
    cursor = Color(0xFF2B2F36),
    selection = Color(0x664097F6),
    lineNumber = Color(0xFF9AA0A6),
    currentLineNumber = Color(0xFF4E5257),
    currentLine = Color(0x14000000),
    guide = Color(0x22343A40),
    separatorLine = Color(0xFFD5D8DC),
    splitLine = Color(0x22343A40),
    scrollbarTrack = Color(0x14000000),
    scrollbarThumb = Color(0x666A7078),
    scrollbarThumbActive = Color(0x99727984),
    compositionUnderline = Color(0xFF2F7CF6),
    inlayHintBackground = Color(0x123C78D8),
    inlayHintText = Color(0xB05F6772),
    foldPlaceholderBackground = Color(0x1F4A6079),
    foldPlaceholderText = Color(0xFF6D7680),
    phantomText = Color(0x8A6D7680),
    inlayHintIcon = Color(0xB06D7680),
    diagnosticError = Color(0xFFD93F3F),
    diagnosticWarning = Color(0xFFE09126),
    diagnosticInfo = Color(0xFF2E89FF),
    diagnosticHint = Color(0xFF7A828D),
    linkedEditingActive = Color(0xCC2F7CF6),
    linkedEditingInactive = Color(0x662F7CF6),
    bracketHighlightBorder = Color(0xCC4D8DF5),
    bracketHighlightBackground = Color(0x1F5D93E0),
    gutterBackground = Color(0xFFF7F8FA),
)

private val ideaDarkSpanColors = SweetEditorDefaults.darkSpanColors(
    keyword = Color(0xFFCC7832),
    string = Color(0xFF6AAB73),
    comment = Color(0xFF808080),
    number = Color(0xFF6897BB),
    builtin = Color(0xFF9876AA),
    type = Color(0xFFA9B7C6),
    className = Color(0xFFFFC66D),
    function = Color(0xFFFFC66D),
    variable = Color(0xFFA9B7C6),
    property = Color(0xFF9876AA),
    parameter = Color(0xFFA9B7C6),
    constant = Color(0xFF9876AA),
    field = Color(0xFF9876AA),
    namespace = Color(0xFFA9B7C6),
    enumMember = Color(0xFF9876AA),
    operator = Color(0xFFA9B7C6),
    punctuation = Color(0xFFA9B7C6),
    annotation = Color(0xFFBBB529),
    preprocessor = Color(0xFFCC7832),
)

private val ideaLightSpanColors = SweetEditorDefaults.lightSpanColors(
    keyword = Color(0xFF0033B3),
    string = Color(0xFF067D17),
    comment = Color(0xFF8C8C8C),
    number = Color(0xFF1750EB),
    builtin = Color(0xFF871094),
    type = Color(0xFF1F2329),
    className = Color(0xFF795E26),
    function = Color(0xFF795E26),
    variable = Color(0xFF1F2329),
    property = Color(0xFF871094),
    parameter = Color(0xFF1F2329),
    constant = Color(0xFF871094),
    field = Color(0xFF871094),
    namespace = Color(0xFF1F2329),
    enumMember = Color(0xFF871094),
    operator = Color(0xFF1F2329),
    punctuation = Color(0xFF1F2329),
    annotation = Color(0xFF808000),
    preprocessor = Color(0xFF0033B3),
)

private val darkThemeScheme = SweetEditorThemeScheme(
    colors = ideaDarkColors,
    typography = ideaTypography,
    spanStyles = SweetEditorDefaults.spanStyles(ideaDarkSpanColors),
    cornerRadius = 2f,
)

private val lightThemeScheme = SweetEditorThemeScheme(
    colors = ideaLightColors,
    typography = ideaTypography,
    spanStyles = SweetEditorDefaults.spanStyles(ideaLightSpanColors),
    cornerRadius = 2f,
)
