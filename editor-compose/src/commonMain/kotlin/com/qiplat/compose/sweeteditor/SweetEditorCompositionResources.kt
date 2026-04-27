package com.qiplat.compose.sweeteditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.qiplat.compose.sweeteditor.runtime.EditorTextMeasurer
import com.qiplat.compose.sweeteditor.theme.SweetEditorColors
import com.qiplat.compose.sweeteditor.theme.SweetEditorColorsInternal
import com.qiplat.compose.sweeteditor.theme.SweetEditorSpanStyleKeys
import com.qiplat.compose.sweeteditor.theme.SweetEditorTheme
import com.qiplat.compose.sweeteditor.model.decoration.SpanStyle as EditorTextStyle
import com.qiplat.compose.sweeteditor.model.decoration.SpanFontStyle

data class SweetEditorFontConfig(
    val fontFamily: FontFamily = FontFamily.Monospace,
    val fontSize: TextUnit = 14.sp,
    val lineNumberFontSize: TextUnit = 13.sp,
    val inlayHintFontSize: TextUnit = 12.sp,
    val iconSize: TextUnit = 16.sp,
)

data class SweetEditorAppearance(
    val fontConfig: SweetEditorFontConfig,
    val theme: SweetEditorTheme,
    val textMeasurer: EditorTextMeasurer,
)

@Composable
fun rememberSweetEditorAppearance(
    themeContent: String? = null,
    fontConfig: SweetEditorFontConfig = SweetEditorFontConfig(),
    darkMode: Boolean = true,
): SweetEditorAppearance {
    val theme = rememberSweetEditorTheme(
        themeContent = themeContent,
        fontConfig = fontConfig,
        darkMode = darkMode,
    )
    val textMeasurer = rememberSweetEditorTextMeasurer(fontConfig)
    return remember(fontConfig, theme, textMeasurer) {
        SweetEditorAppearance(
            fontConfig = fontConfig,
            theme = theme,
            textMeasurer = textMeasurer,
        )
    }
}

@Composable
fun rememberSweetEditorTheme(
    themeContent: String? = null,
    fontConfig: SweetEditorFontConfig = SweetEditorFontConfig(),
    darkMode: Boolean = true,
): SweetEditorTheme {
    return remember(themeContent, fontConfig, darkMode) {
        val baseTheme = if (darkMode) {
            SweetEditorTheme.dark(
                typography = SweetEditorDefaults.typography(
                    fontFamily = fontConfig.fontFamily,
                    fontSize = fontConfig.fontSize,
                    lineNumberFontSize = fontConfig.lineNumberFontSize,
                    inlayHintFontSize = fontConfig.inlayHintFontSize,
                ),
            )
        } else {
            SweetEditorTheme.light(
                typography = SweetEditorDefaults.typography(
                    fontFamily = fontConfig.fontFamily,
                    fontSize = fontConfig.fontSize,
                    lineNumberFontSize = fontConfig.lineNumberFontSize,
                    inlayHintFontSize = fontConfig.inlayHintFontSize,
                ),
            )
        }
        SweetEditorThemeParser.parse(
            content = themeContent,
            fallback = baseTheme,
            fontConfig = fontConfig,
        )
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun rememberSweetEditorTextMeasurer(
    fontConfig: SweetEditorFontConfig,
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

private object SweetEditorThemeParser {
    fun parse(
        content: String?,
        fallback: SweetEditorTheme,
        fontConfig: SweetEditorFontConfig,
    ): SweetEditorTheme {
        if (content.isNullOrBlank()) {
            return fallback.copy(
                typography = fallback.typography.copy(
                    fontFamily = fontConfig.fontFamily,
                    fontSize = fontConfig.fontSize,
                    lineNumberFontSize = fontConfig.lineNumberFontSize,
                    inlayHintFontSize = fontConfig.inlayHintFontSize,
                ),
            )
        }

        return fallback.copy(
            colors = buildColors(content, fallback.colors),
            typography = fallback.typography.copy(
                fontFamily = fontConfig.fontFamily,
                fontSize = fontConfig.fontSize,
                lineNumberFontSize = fontConfig.lineNumberFontSize,
                inlayHintFontSize = fontConfig.inlayHintFontSize,
            ),
            spanStyles = fallback.spanStyles.withOverrides(parseSpanStyles(content)),
            cornerRadius = findFloat(content, "cornerRadius") ?: fallback.cornerRadius,
        )
    }

    private fun buildColors(content: String, fallback: SweetEditorColors): SweetEditorColors {
        val fallbackInternal = fallback.toInternal()
        return SweetEditorColors.fromInternal(
            SweetEditorColorsInternal(
                background = findColor(content, "backgroundColor") ?: fallbackInternal.background,
                text = findColor(content, "textColor") ?: fallbackInternal.text,
                cursor = findColor(content, "cursorColor") ?: fallbackInternal.cursor,
                selection = findColor(content, "selectionColor") ?: fallbackInternal.selection,
                lineNumber = findColor(content, "lineNumberColor") ?: fallbackInternal.lineNumber,
                currentLineNumber = findColor(content, "currentLineNumberColor") ?: fallbackInternal.currentLineNumber,
                currentLine = findColor(content, "currentLineColor") ?: fallbackInternal.currentLine,
                guide = findColor(content, "guideColor") ?: fallbackInternal.guide,
                separatorLine = findColor(content, "separatorLineColor") ?: fallbackInternal.separatorLine,
                splitLine = findColor(content, "splitLineColor") ?: fallbackInternal.splitLine,
                scrollbarTrack = findColor(content, "scrollbarTrackColor") ?: fallbackInternal.scrollbarTrack,
                scrollbarThumb = findColor(content, "scrollbarThumbColor") ?: fallbackInternal.scrollbarThumb,
                scrollbarThumbActive = findColor(content, "scrollbarThumbActiveColor") ?: fallbackInternal.scrollbarThumbActive,
                compositionUnderline = findColor(content, "compositionUnderlineColor") ?: fallbackInternal.compositionUnderline,
                inlayHintBackground = findColor(content, "inlayHintBackgroundColor") ?: fallbackInternal.inlayHintBackground,
                inlayHintText = findColor(content, "inlayHintTextColor") ?: fallbackInternal.inlayHintText,
                foldPlaceholderBackground = findColor(content, "foldPlaceholderBackgroundColor") ?: fallbackInternal.foldPlaceholderBackground,
                foldPlaceholderText = findColor(content, "foldPlaceholderTextColor") ?: fallbackInternal.foldPlaceholderText,
                phantomText = findColor(content, "phantomTextColor") ?: fallbackInternal.phantomText,
                inlayHintIcon = findColor(content, "inlayHintIconColor") ?: fallbackInternal.inlayHintIcon,
                diagnosticError = findColor(content, "diagnosticErrorColor") ?: fallbackInternal.diagnosticError,
                diagnosticWarning = findColor(content, "diagnosticWarningColor") ?: fallbackInternal.diagnosticWarning,
                diagnosticInfo = findColor(content, "diagnosticInfoColor") ?: fallbackInternal.diagnosticInfo,
                diagnosticHint = findColor(content, "diagnosticHintColor") ?: fallbackInternal.diagnosticHint,
                linkedEditingActive = findColor(content, "linkedEditingActiveColor") ?: fallbackInternal.linkedEditingActive,
                linkedEditingInactive = findColor(content, "linkedEditingInactiveColor") ?: fallbackInternal.linkedEditingInactive,
                bracketHighlightBorder = findColor(content, "bracketHighlightBorderColor") ?: fallbackInternal.bracketHighlightBorder,
                bracketHighlightBackground = findColor(content, "bracketHighlightBackgroundColor") ?: fallbackInternal.bracketHighlightBackground,
                gutterBackground = findColor(content, "gutterBackgroundColor") ?: fallbackInternal.gutterBackground,
            ),
        )
    }

    private fun parseSpanStyles(content: String): Map<Int, EditorTextStyle> {
        val block = findObjectContent(content, "spanStyles") ?: return emptyMap()
        val entryPattern = Regex("\"([^\"]+)\"\\s*:\\s*\\{([^{}]*)\\}")
        return buildMap {
            entryPattern.findAll(block).forEach { match ->
                val rawStyleKey = match.groupValues[1]
                val styleId = SweetEditorSpanStyleKeys.resolve(rawStyleKey)?.id
                    ?: rawStyleKey.toIntOrNull()
                    ?: return@forEach
                val rawBody = match.groupValues[2]
                put(
                    styleId,
                    EditorTextStyle(
                        color = Color(findColor(rawBody, "color") ?: 0),
                        backgroundColor = Color(findColor(rawBody, "backgroundColor") ?: 0),
                        fontStyle = findFontStyleFlags(rawBody),
                    ),
                )
            }
        }
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

    private fun findFloat(content: String, key: String): Float? {
        return Regex("\"$key\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
            .find(content)
            ?.groups
            ?.get(1)
            ?.value
            ?.toFloatOrNull()
    }

    private fun findObjectContent(content: String, key: String): String? {
        val marker = "\"$key\""
        val keyIndex = content.indexOf(marker)
        if (keyIndex < 0) {
            return null
        }
        val startIndex = content.indexOf('{', keyIndex + marker.length)
        if (startIndex < 0) {
            return null
        }
        var depth = 0
        for (index in startIndex until content.length) {
            when (content[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return content.substring(startIndex + 1, index)
                    }
                }
            }
        }
        return null
    }

    private fun findFontStyleFlags(content: String): SpanFontStyle {
        val numericStyle = Regex("\"fontStyle\"\\s*:\\s*(-?\\d+)")
            .find(content)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
        if (numericStyle != null) {
            return SpanFontStyle.fromBits(numericStyle)
        }
        val rawStyle = Regex("\"fontStyle\"\\s*:\\s*\"([^\"]+)\"")
            .find(content)
            ?.groupValues
            ?.get(1)
            ?.lowercase()
            ?: return SpanFontStyle.Normal
        var styleFlags = SpanFontStyle.Normal
        if ("bold" in rawStyle) {
            styleFlags = styleFlags or SpanFontStyle.Bold
        }
        if ("italic" in rawStyle) {
            styleFlags = styleFlags or SpanFontStyle.Italic
        }
        if ("strikethrough" in rawStyle) {
            styleFlags = styleFlags or SpanFontStyle.Strikethrough
        }
        return styleFlags
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
