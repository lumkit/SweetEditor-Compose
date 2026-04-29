package com.qiplat.compose.sweeteditor.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.qiplat.compose.sweeteditor.model.decoration.SpanStyle
import com.qiplat.compose.sweeteditor.SweetEditorDefaults

enum class SweetEditorSpanStyleKeys(internal val id: Int) {
    Keyword(1),
    String(2),
    Comment(3),
    Number(4),
    Builtin(5),
    Type(6),
    Class(7),
    Function(8),
    Variable(9),
    Punctuation(10),
    Annotation(11),
    Preprocessor(12),
    Property(13),
    Parameter(14),
    Constant(15),
    Operator(16),
    Field(17),
    Namespace(18),
    EnumMember(19),
    Interface(20),
    Enum(21),
    Struct(22),
    ;

    companion object {
        const val USER_BASE: Int = 100
        internal val StyleIds: IntArray = entries.map { it.id }.sorted().toIntArray()

        private val byId: Map<Int, SweetEditorSpanStyleKeys> = entries.associateBy { it.id }
        private val aliases: Map<String, SweetEditorSpanStyleKeys> = mapOf(
            "keyword" to Keyword,
            "string" to String,
            "comment" to Comment,
            "number" to Number,
            "builtin" to Builtin,
            "type" to Type,
            "class" to Class,
            "interface" to Interface,
            "enum" to Enum,
            "struct" to Struct,
            "function" to Function,
            "method" to Function,
            "variable" to Variable,
            "property" to Property,
            "parameter" to Parameter,
            "constant" to Constant,
            "field" to Field,
            "namespace" to Namespace,
            "module" to Namespace,
            "enum_member" to EnumMember,
            "enummember" to EnumMember,
            "operator" to Operator,
            "punctuation" to Punctuation,
            "annotation" to Annotation,
            "preprocessor" to Preprocessor,
        )

        fun fromId(id: Int): SweetEditorSpanStyleKeys? = byId[id]

        fun resolve(name: String): SweetEditorSpanStyleKeys? = aliases[name.trim().lowercase()]
    }
}

data class SweetEditorThemeScheme(
    val colors: SweetEditorColors,
    val typography: SweetEditorTypography,
    val spanStyles: SweetEditorSpanStyles,
    val cornerRadius: Float,
)

open class SweetEditorTheme(
    open val darkTheme: SweetEditorThemeScheme,
    open val lightTheme: SweetEditorThemeScheme,
) {
    fun fromJson(
        darkJson: String?,
        lightJson: String?,
    ): SweetEditorTheme = SweetEditorTheme(
        darkTheme = darkTheme.fromJson(darkJson),
        lightTheme = lightTheme.fromJson(lightJson),
    )
}

fun SweetEditorThemeScheme.fromJson(json: String?): SweetEditorThemeScheme = parseSweetEditorTheme(
    themeContent = json,
    fallback = this,
)

internal fun parseSweetEditorTheme(
    themeContent: String?,
    fallback: SweetEditorThemeScheme = SweetEditorDefaults.theme().darkTheme,
): SweetEditorThemeScheme = SweetEditorThemeParser.parse(
    content = themeContent,
    fallback = fallback,
)

@Composable
fun rememberSweetEditorTheme(
    theme: SweetEditorTheme = SweetEditorDefaults.theme(),
    darkMode: Boolean = true,
): SweetEditorThemeScheme {
    return remember(theme, darkMode) {
        if (darkMode) theme.darkTheme else theme.lightTheme
    }
}

fun SweetEditorThemeScheme.toJson(): String {
    val colors = colors.toInternal()
    val spanStyles = spanStyles.toMap()
    val styleKeyNames = mapOf(
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

    val textStylesLines = mutableListOf<String>()
    for (id in SweetEditorSpanStyleKeys.StyleIds) {
        val style = spanStyles[id] ?: continue
        val key = styleKeyNames[id] ?: id.toString()
        textStylesLines += """    "$key": ${style.toJsonObject()}"""
    }
    val textStylesJson = textStylesLines.joinToString(",\n")

    return """
{
  "backgroundColor": ${colorToJson(colors.background)},
  "textColor": ${colorToJson(colors.text)},
  "cursorColor": ${colorToJson(colors.cursor)},
  "selectionColor": ${colorToJson(colors.selection)},
  "lineNumberColor": ${colorToJson(colors.lineNumber)},
  "currentLineNumberColor": ${colorToJson(colors.currentLineNumber)},
  "currentLineColor": ${colorToJson(colors.currentLine)},
  "guideColor": ${colorToJson(colors.guide)},
  "separatorLineColor": ${colorToJson(colors.separatorLine)},
  "splitLineColor": ${colorToJson(colors.splitLine)},
  "scrollbarTrackColor": ${colorToJson(colors.scrollbarTrack)},
  "scrollbarThumbColor": ${colorToJson(colors.scrollbarThumb)},
  "scrollbarThumbActiveColor": ${colorToJson(colors.scrollbarThumbActive)},
  "compositionUnderlineColor": ${colorToJson(colors.compositionUnderline)},
  "inlayHintBackgroundColor": ${colorToJson(colors.inlayHintBackground)},
  "inlayHintTextColor": ${colorToJson(colors.inlayHintText)},
  "foldPlaceholderBackgroundColor": ${colorToJson(colors.foldPlaceholderBackground)},
  "foldPlaceholderTextColor": ${colorToJson(colors.foldPlaceholderText)},
  "phantomTextColor": ${colorToJson(colors.phantomText)},
  "inlayHintIconColor": ${colorToJson(colors.inlayHintIcon)},
  "diagnosticErrorColor": ${colorToJson(colors.diagnosticError)},
  "diagnosticWarningColor": ${colorToJson(colors.diagnosticWarning)},
  "diagnosticInfoColor": ${colorToJson(colors.diagnosticInfo)},
  "diagnosticHintColor": ${colorToJson(colors.diagnosticHint)},
  "linkedEditingActiveColor": ${colorToJson(colors.linkedEditingActive)},
  "linkedEditingInactiveColor": ${colorToJson(colors.linkedEditingInactive)},
  "bracketHighlightBorderColor": ${colorToJson(colors.bracketHighlightBorder)},
  "bracketHighlightBackgroundColor": ${colorToJson(colors.bracketHighlightBackground)},
  "gutterBackgroundColor": ${colorToJson(colors.gutterBackground)},
  "cornerRadius": $cornerRadius,
  "textStyles": {
$textStylesJson
  }
}
""".trim()
}

private fun SpanStyle.toJsonObject(): String {
    val colorValue = colorToJsonColor(color)
    val backgroundValue = colorToJsonColor(backgroundColor)
    return """{"color": $colorValue, "backgroundColor": $backgroundValue, "fontStyle": ${fontStyle.bits}}"""
}

private fun colorToJsonColor(color: Color): String = if (color == Color.Unspecified) "0" else colorToJson(color.toArgb())

private fun colorToJson(color: Int): String = """"#${color.toUInt().toString(16).padStart(8, '0').uppercase()}""""
