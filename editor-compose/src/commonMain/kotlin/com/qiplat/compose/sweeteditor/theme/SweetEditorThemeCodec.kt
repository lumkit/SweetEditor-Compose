package com.qiplat.compose.sweeteditor.theme

import androidx.compose.ui.graphics.Color
import com.qiplat.compose.sweeteditor.model.decoration.SpanFontStyle
import com.qiplat.compose.sweeteditor.model.decoration.SpanStyle as EditorTextStyle

internal object SweetEditorThemeParser {
    fun parse(
        content: String?,
        fallback: SweetEditorTheme,
    ): SweetEditorTheme {
        if (content.isNullOrBlank()) {
            return fallback
        }

        return fallback.copy(
            colors = buildColors(content, fallback.colors),
            typography = fallback.typography,
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
        val block = findObjectContent(content, "textStyles")
            ?: findObjectContent(content, "spanStyles")
            ?: return emptyMap()
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

internal object SweetEditorThemeContentCodec {
    private data class ColorEntry(val key: String, val argb: Int)

    private val styleKeyById: Map<Int, String> = mapOf(
        SweetEditorSpanStyleKeys.Keyword.id to "keyword",
        SweetEditorSpanStyleKeys.String.id to "string",
        SweetEditorSpanStyleKeys.Comment.id to "comment",
        SweetEditorSpanStyleKeys.Number.id to "number",
        SweetEditorSpanStyleKeys.Builtin.id to "builtin",
        SweetEditorSpanStyleKeys.Type.id to "type",
        SweetEditorSpanStyleKeys.Class.id to "class",
        SweetEditorSpanStyleKeys.Function.id to "function",
        SweetEditorSpanStyleKeys.Variable.id to "variable",
        SweetEditorSpanStyleKeys.Punctuation.id to "punctuation",
        SweetEditorSpanStyleKeys.Annotation.id to "annotation",
        SweetEditorSpanStyleKeys.Preprocessor.id to "preprocessor",
        SweetEditorSpanStyleKeys.Property.id to "property",
        SweetEditorSpanStyleKeys.Parameter.id to "parameter",
        SweetEditorSpanStyleKeys.Constant.id to "constant",
        SweetEditorSpanStyleKeys.Operator.id to "operator",
        SweetEditorSpanStyleKeys.Field.id to "field",
        SweetEditorSpanStyleKeys.Namespace.id to "namespace",
        SweetEditorSpanStyleKeys.EnumMember.id to "enum_member",
        SweetEditorSpanStyleKeys.Interface.id to "interface",
        SweetEditorSpanStyleKeys.Enum.id to "enum",
        SweetEditorSpanStyleKeys.Struct.id to "struct",
    )

    fun toThemeContent(theme: SweetEditorTheme): String {
        val colors = theme.colors.toInternal()
        val styles = theme.spanStyles.toMap()
        val styleEntries = styleKeyById.entries
            .sortedBy { it.key }
            .mapNotNull { (id, key) -> styles[id]?.let { key to it.toInternal() } }
        val colorEntries = listOf(
            ColorEntry("backgroundColor", colors.background),
            ColorEntry("textColor", colors.text),
            ColorEntry("cursorColor", colors.cursor),
            ColorEntry("selectionColor", colors.selection),
            ColorEntry("lineNumberColor", colors.lineNumber),
            ColorEntry("currentLineNumberColor", colors.currentLineNumber),
            ColorEntry("currentLineColor", colors.currentLine),
            ColorEntry("guideColor", colors.guide),
            ColorEntry("separatorLineColor", colors.separatorLine),
            ColorEntry("splitLineColor", colors.splitLine),
            ColorEntry("scrollbarTrackColor", colors.scrollbarTrack),
            ColorEntry("scrollbarThumbColor", colors.scrollbarThumb),
            ColorEntry("scrollbarThumbActiveColor", colors.scrollbarThumbActive),
            ColorEntry("compositionUnderlineColor", colors.compositionUnderline),
            ColorEntry("inlayHintBackgroundColor", colors.inlayHintBackground),
            ColorEntry("inlayHintTextColor", colors.inlayHintText),
            ColorEntry("foldPlaceholderBackgroundColor", colors.foldPlaceholderBackground),
            ColorEntry("foldPlaceholderTextColor", colors.foldPlaceholderText),
            ColorEntry("phantomTextColor", colors.phantomText),
            ColorEntry("inlayHintIconColor", colors.inlayHintIcon),
            ColorEntry("diagnosticErrorColor", colors.diagnosticError),
            ColorEntry("diagnosticWarningColor", colors.diagnosticWarning),
            ColorEntry("diagnosticInfoColor", colors.diagnosticInfo),
            ColorEntry("diagnosticHintColor", colors.diagnosticHint),
            ColorEntry("linkedEditingActiveColor", colors.linkedEditingActive),
            ColorEntry("linkedEditingInactiveColor", colors.linkedEditingInactive),
            ColorEntry("bracketHighlightBorderColor", colors.bracketHighlightBorder),
            ColorEntry("bracketHighlightBackgroundColor", colors.bracketHighlightBackground),
            ColorEntry("gutterBackgroundColor", colors.gutterBackground),
        )
        val builder = StringBuilder(2048)
        builder.append("{")
        colorEntries.forEachIndexed { index, entry ->
            if (index > 0) {
                builder.append(',')
            }
            builder.append('"')
                .append(entry.key)
                .append("\":\"")
                .append(toHexArgb(entry.argb))
                .append('"')
        }
        builder.append(",\"cornerRadius\":")
            .append(theme.cornerRadius)
            .append(",\"textStyles\":{")
        styleEntries.forEachIndexed { index, (styleKey, style) ->
            if (index > 0) {
                builder.append(',')
            }
            builder.append('"')
                .append(styleKey)
                .append("\":{\"color\":\"")
                .append(toHexArgb(style.color))
                .append("\",\"backgroundColor\":\"")
                .append(toHexArgb(style.backgroundColor))
                .append("\",\"fontStyle\":")
                .append(style.fontStyleBits)
                .append('}')
        }
        builder.append("}}")
        return builder.toString()
    }

    private fun toHexArgb(argb: Int): String =
        "#${argb.toUInt().toString(16).uppercase().padStart(8, '0')}"
}
