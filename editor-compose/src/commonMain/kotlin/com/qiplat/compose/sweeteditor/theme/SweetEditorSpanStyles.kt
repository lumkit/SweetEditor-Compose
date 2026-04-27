package com.qiplat.compose.sweeteditor.theme

import com.qiplat.compose.sweeteditor.model.decoration.SpanStyle

data class SweetEditorSpanStyles(
    val keyword: SpanStyle,
    val string: SpanStyle,
    val comment: SpanStyle,
    val number: SpanStyle,
    val builtin: SpanStyle,
    val type: SpanStyle,
    val className: SpanStyle,
    val function: SpanStyle,
    val variable: SpanStyle,
    val punctuation: SpanStyle,
    val annotation: SpanStyle,
    val preprocessor: SpanStyle,
    val property: SpanStyle,
    val parameter: SpanStyle,
    val constant: SpanStyle,
    val operator: SpanStyle,
    val field: SpanStyle,
    val namespace: SpanStyle,
    val enumMember: SpanStyle,
    val interfaceName: SpanStyle,
    val enumName: SpanStyle,
    val struct: SpanStyle,
) {
    internal fun toMap(): Map<Int, SpanStyle> = mapCache

    internal fun withOverrides(stylesById: Map<Int, SpanStyle>): SweetEditorSpanStyles {
        if (stylesById.isEmpty()) {
            return this
        }
        var resolved = this
        stylesById.forEach { (styleId, style) ->
            resolved = resolved.override(styleId, style)
        }
        return resolved
    }

    internal fun toInternal(): SweetEditorSyntaxStylesInternal = internalCache

    private fun override(styleId: Int, style: SpanStyle): SweetEditorSpanStyles = when (styleId) {
        SweetEditorSpanStyleKeys.Keyword.id -> copy(keyword = style)
        SweetEditorSpanStyleKeys.String.id -> copy(string = style)
        SweetEditorSpanStyleKeys.Comment.id -> copy(comment = style)
        SweetEditorSpanStyleKeys.Number.id -> copy(number = style)
        SweetEditorSpanStyleKeys.Builtin.id -> copy(builtin = style)
        SweetEditorSpanStyleKeys.Type.id -> copy(type = style)
        SweetEditorSpanStyleKeys.Class.id -> copy(className = style)
        SweetEditorSpanStyleKeys.Function.id -> copy(function = style)
        SweetEditorSpanStyleKeys.Variable.id -> copy(variable = style)
        SweetEditorSpanStyleKeys.Punctuation.id -> copy(punctuation = style)
        SweetEditorSpanStyleKeys.Annotation.id -> copy(annotation = style)
        SweetEditorSpanStyleKeys.Preprocessor.id -> copy(preprocessor = style)
        SweetEditorSpanStyleKeys.Property.id -> copy(property = style)
        SweetEditorSpanStyleKeys.Parameter.id -> copy(parameter = style)
        SweetEditorSpanStyleKeys.Constant.id -> copy(constant = style)
        SweetEditorSpanStyleKeys.Operator.id -> copy(operator = style)
        SweetEditorSpanStyleKeys.Field.id -> copy(field = style)
        SweetEditorSpanStyleKeys.Namespace.id -> copy(namespace = style)
        SweetEditorSpanStyleKeys.EnumMember.id -> copy(enumMember = style)
        SweetEditorSpanStyleKeys.Interface.id -> copy(interfaceName = style)
        SweetEditorSpanStyleKeys.Enum.id -> copy(enumName = style)
        SweetEditorSpanStyleKeys.Struct.id -> copy(struct = style)
        else -> this
    }

    private val mapCache: Map<Int, SpanStyle> by lazy(LazyThreadSafetyMode.NONE) {
        mapOf(
            SweetEditorSpanStyleKeys.Keyword.id to keyword,
            SweetEditorSpanStyleKeys.String.id to string,
            SweetEditorSpanStyleKeys.Comment.id to comment,
            SweetEditorSpanStyleKeys.Number.id to number,
            SweetEditorSpanStyleKeys.Builtin.id to builtin,
            SweetEditorSpanStyleKeys.Type.id to type,
            SweetEditorSpanStyleKeys.Class.id to className,
            SweetEditorSpanStyleKeys.Function.id to function,
            SweetEditorSpanStyleKeys.Variable.id to variable,
            SweetEditorSpanStyleKeys.Punctuation.id to punctuation,
            SweetEditorSpanStyleKeys.Annotation.id to annotation,
            SweetEditorSpanStyleKeys.Preprocessor.id to preprocessor,
            SweetEditorSpanStyleKeys.Property.id to property,
            SweetEditorSpanStyleKeys.Parameter.id to parameter,
            SweetEditorSpanStyleKeys.Constant.id to constant,
            SweetEditorSpanStyleKeys.Operator.id to operator,
            SweetEditorSpanStyleKeys.Field.id to field,
            SweetEditorSpanStyleKeys.Namespace.id to namespace,
            SweetEditorSpanStyleKeys.EnumMember.id to enumMember,
            SweetEditorSpanStyleKeys.Interface.id to interfaceName,
            SweetEditorSpanStyleKeys.Enum.id to enumName,
            SweetEditorSpanStyleKeys.Struct.id to struct,
        )
    }

    private val internalCache: SweetEditorSyntaxStylesInternal by lazy(LazyThreadSafetyMode.NONE) {
        SweetEditorSyntaxStylesInternal.from(this)
    }
}

internal class SweetEditorSyntaxStylesInternal private constructor(
    val styleIds: IntArray,
    val colors: IntArray,
    val backgroundColors: IntArray,
    val fontStyles: IntArray,
) {
    fun contentEquals(other: SweetEditorSyntaxStylesInternal): Boolean =
        styleIds.contentEquals(other.styleIds) &&
            colors.contentEquals(other.colors) &&
            backgroundColors.contentEquals(other.backgroundColors) &&
            fontStyles.contentEquals(other.fontStyles)

    companion object {
        fun from(styles: SweetEditorSpanStyles): SweetEditorSyntaxStylesInternal {
            val ids = SweetEditorSpanStyleKeys.StyleIds
            val colors = IntArray(ids.size)
            val backgroundColors = IntArray(ids.size)
            val fontStyles = IntArray(ids.size)
            for (index in ids.indices) {
                val style = styles.styleById(ids[index]) ?: continue
                val internalStyle = style.toInternal()
                colors[index] = internalStyle.color
                backgroundColors[index] = internalStyle.backgroundColor
                fontStyles[index] = internalStyle.fontStyleBits
            }
            return SweetEditorSyntaxStylesInternal(
                styleIds = ids.copyOf(),
                colors = colors,
                backgroundColors = backgroundColors,
                fontStyles = fontStyles,
            )
        }
    }
}

private fun SweetEditorSpanStyles.styleById(id: Int): SpanStyle? = when (id) {
    SweetEditorSpanStyleKeys.Keyword.id -> keyword
    SweetEditorSpanStyleKeys.String.id -> string
    SweetEditorSpanStyleKeys.Comment.id -> comment
    SweetEditorSpanStyleKeys.Number.id -> number
    SweetEditorSpanStyleKeys.Builtin.id -> builtin
    SweetEditorSpanStyleKeys.Type.id -> type
    SweetEditorSpanStyleKeys.Class.id -> className
    SweetEditorSpanStyleKeys.Function.id -> function
    SweetEditorSpanStyleKeys.Variable.id -> variable
    SweetEditorSpanStyleKeys.Punctuation.id -> punctuation
    SweetEditorSpanStyleKeys.Annotation.id -> annotation
    SweetEditorSpanStyleKeys.Preprocessor.id -> preprocessor
    SweetEditorSpanStyleKeys.Property.id -> property
    SweetEditorSpanStyleKeys.Parameter.id -> parameter
    SweetEditorSpanStyleKeys.Constant.id -> constant
    SweetEditorSpanStyleKeys.Operator.id -> operator
    SweetEditorSpanStyleKeys.Field.id -> field
    SweetEditorSpanStyleKeys.Namespace.id -> namespace
    SweetEditorSpanStyleKeys.EnumMember.id -> enumMember
    SweetEditorSpanStyleKeys.Interface.id -> interfaceName
    SweetEditorSpanStyleKeys.Enum.id -> enumName
    SweetEditorSpanStyleKeys.Struct.id -> struct
    else -> null
}
