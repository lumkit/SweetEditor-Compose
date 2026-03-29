package com.qiplat.compose.sweeteditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.qiplat.compose.sweeteditor.runtime.EditorTextMeasurer
import com.qiplat.compose.sweeteditor.theme.EditorTheme

data class EditorFontConfig(
    val fontFamily: FontFamily = FontFamily.Monospace,
    val fontSize: TextUnit = 14.sp,
    val lineNumberFontSize: TextUnit = 13.sp,
    val inlayHintFontSize: TextUnit = 12.sp,
    val iconSize: TextUnit = 16.sp,
)

data class EditorAppearance(
    val fontConfig: EditorFontConfig,
    val theme: EditorTheme,
    val textMeasurer: EditorTextMeasurer,
)

@Composable
fun rememberEditorAppearance(
    themeContent: String? = null,
    fontConfig: EditorFontConfig = EditorFontConfig(),
    darkMode: Boolean = true,
): EditorAppearance {
    val theme = rememberEditorTheme(
        themeContent = themeContent,
        fontConfig = fontConfig,
        darkMode = darkMode,
    )
    val textMeasurer = rememberEditorTextMeasurer(fontConfig)
    return remember(fontConfig, theme, textMeasurer) {
        EditorAppearance(
            fontConfig = fontConfig,
            theme = theme,
            textMeasurer = textMeasurer,
        )
    }
}

@Composable
fun rememberEditorTheme(
    themeContent: String? = null,
    fontConfig: EditorFontConfig = EditorFontConfig(),
    darkMode: Boolean = true,
): EditorTheme {
    return remember(themeContent, fontConfig, darkMode) {
        val baseTheme = if (darkMode) {
            EditorTheme.dark(
                fontFamily = fontConfig.fontFamily,
                fontSize = fontConfig.fontSize,
            )
        } else {
            EditorTheme.light(
                fontFamily = fontConfig.fontFamily,
                fontSize = fontConfig.fontSize,
            )
        }
        EditorThemeParser.parse(
            content = themeContent,
            fallback = baseTheme,
            fontConfig = fontConfig,
        )
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun rememberEditorTextMeasurer(
    fontConfig: EditorFontConfig,
): EditorTextMeasurer {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    return remember(textMeasurer, density, fontConfig) {
        object : EditorTextMeasurer {
            private var scale: Float = 1f

            override fun setScale(scale: Float) {
                this.scale = scale.coerceAtLeast(0.1f)
            }

            override fun measureTextWidth(text: String, fontStyle: Int): Float =
                textMeasurer.measure(
                    text = text,
                    style = editorComposeTextStyle(
                        fontFamily = fontConfig.fontFamily,
                        fontSize = fontConfig.fontSize * scale,
                        fontStyleFlags = fontStyle,
                    ),
                ).size.width.toFloat()

            override fun measureInlayHintWidth(text: String): Float =
                textMeasurer.measure(
                    text = text,
                    style = editorComposeTextStyle(
                        fontFamily = fontConfig.fontFamily,
                        fontSize = fontConfig.inlayHintFontSize * scale,
                        fontStyleFlags = 0,
                    ),
                ).size.width.toFloat()

            override fun measureIconWidth(iconId: Int): Float =
                with(density) { (fontConfig.iconSize * scale).toPx() }

            override fun getFontMetrics(): FloatArray {
                val layout = textMeasurer.measure(
                    text = "Hg",
                    style = editorComposeTextStyle(
                        fontFamily = fontConfig.fontFamily,
                        fontSize = fontConfig.fontSize * scale,
                        fontStyleFlags = 0,
                    ),
                )
                val ascent = -layout.firstBaseline
                val descent = (layout.size.height.toFloat() - layout.firstBaseline).coerceAtLeast(0f)
                return floatArrayOf(ascent, descent)
            }
        }
    }
}

private object EditorThemeParser {
    fun parse(
        content: String?,
        fallback: EditorTheme,
        fontConfig: EditorFontConfig,
    ): EditorTheme {
        if (content.isNullOrBlank()) {
            return fallback.copy(
                fontFamily = fontConfig.fontFamily,
                fontSize = fontConfig.fontSize,
                lineNumberFontSize = fontConfig.lineNumberFontSize,
                inlayHintFontSize = fontConfig.inlayHintFontSize,
            )
        }

        return fallback.copy(
            backgroundColor = findColor(content, "backgroundColor") ?: fallback.backgroundColor,
            textColor = findColor(content, "textColor") ?: fallback.textColor,
            cursorColor = findColor(content, "cursorColor") ?: fallback.cursorColor,
            selectionColor = findColor(content, "selectionColor") ?: fallback.selectionColor,
            lineNumberColor = findColor(content, "lineNumberColor") ?: fallback.lineNumberColor,
            currentLineNumberColor = findColor(content, "currentLineNumberColor") ?: fallback.currentLineNumberColor,
            currentLineColor = findColor(content, "currentLineColor") ?: fallback.currentLineColor,
            guideColor = findColor(content, "guideColor") ?: fallback.guideColor,
            separatorLineColor = findColor(content, "separatorLineColor") ?: fallback.separatorLineColor,
            splitLineColor = findColor(content, "splitLineColor") ?: fallback.splitLineColor,
            scrollbarTrackColor = findColor(content, "scrollbarTrackColor") ?: fallback.scrollbarTrackColor,
            scrollbarThumbColor = findColor(content, "scrollbarThumbColor") ?: fallback.scrollbarThumbColor,
            scrollbarThumbActiveColor = findColor(content, "scrollbarThumbActiveColor") ?: fallback.scrollbarThumbActiveColor,
            compositionUnderlineColor = findColor(content, "compositionUnderlineColor") ?: fallback.compositionUnderlineColor,
            inlayHintBackgroundColor = findColor(content, "inlayHintBackgroundColor") ?: fallback.inlayHintBackgroundColor,
            inlayHintTextColor = findColor(content, "inlayHintTextColor") ?: fallback.inlayHintTextColor,
            foldPlaceholderBackgroundColor = findColor(content, "foldPlaceholderBackgroundColor") ?: fallback.foldPlaceholderBackgroundColor,
            foldPlaceholderTextColor = findColor(content, "foldPlaceholderTextColor") ?: fallback.foldPlaceholderTextColor,
            phantomTextColor = findColor(content, "phantomTextColor") ?: fallback.phantomTextColor,
            inlayHintIconColor = findColor(content, "inlayHintIconColor") ?: fallback.inlayHintIconColor,
            diagnosticErrorColor = findColor(content, "diagnosticErrorColor") ?: fallback.diagnosticErrorColor,
            diagnosticWarningColor = findColor(content, "diagnosticWarningColor") ?: fallback.diagnosticWarningColor,
            diagnosticInfoColor = findColor(content, "diagnosticInfoColor") ?: fallback.diagnosticInfoColor,
            diagnosticHintColor = findColor(content, "diagnosticHintColor") ?: fallback.diagnosticHintColor,
            linkedEditingActiveColor = findColor(content, "linkedEditingActiveColor") ?: fallback.linkedEditingActiveColor,
            linkedEditingInactiveColor = findColor(content, "linkedEditingInactiveColor") ?: fallback.linkedEditingInactiveColor,
            bracketHighlightBorderColor = findColor(content, "bracketHighlightBorderColor") ?: fallback.bracketHighlightBorderColor,
            bracketHighlightBackgroundColor = findColor(content, "bracketHighlightBackgroundColor") ?: fallback.bracketHighlightBackgroundColor,
            gutterBackgroundColor = findColor(content, "gutterBackgroundColor") ?: fallback.gutterBackgroundColor,
            fontFamily = fontConfig.fontFamily,
            fontSize = fontConfig.fontSize,
            lineNumberFontSize = fontConfig.lineNumberFontSize,
            inlayHintFontSize = fontConfig.inlayHintFontSize,
        )
    }

    private fun findColor(content: String, key: String): Int? {
        val rawValue = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"")
            .find(content)
            ?.groups
            ?.get(1)
            ?.value
            ?: Regex("\"$key\"\\s*:\\s*(-?\\d+)")
                .find(content)
                ?.groups
                ?.get(1)
                ?.value
            ?: return null

        return parseColor(rawValue)
    }

    private fun parseColor(value: String): Int? {
        val normalized = value.trim()
        if (normalized.startsWith("#")) {
            val hex = normalized.removePrefix("#")
            return when (hex.length) {
                6 -> ("FF$hex").toLongOrNull(16)?.toInt()
                8 -> hex.toLongOrNull(16)?.toInt()
                else -> null
            }
        }
        return normalized.toLongOrNull()?.toInt()
    }
}

private fun editorComposeTextStyle(
    fontFamily: FontFamily,
    fontSize: TextUnit,
    fontStyleFlags: Int,
): TextStyle = TextStyle(
    fontFamily = fontFamily,
    fontSize = fontSize,
    fontWeight = if ((fontStyleFlags and 1) != 0) FontWeight.Bold else FontWeight.Normal,
    fontStyle = if ((fontStyleFlags and 2) != 0) FontStyle.Italic else FontStyle.Normal,
)
