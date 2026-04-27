package com.qiplat.compose.sweeteditor.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

internal data class SweetEditorSpanColorsInternal(
    val keyword: Int,
    val string: Int,
    val comment: Int,
    val number: Int,
    val builtin: Int,
    val type: Int,
    val className: Int,
    val function: Int,
    val variable: Int,
    val property: Int,
    val parameter: Int,
    val constant: Int,
    val field: Int,
    val namespace: Int,
    val enumMember: Int,
    val operator: Int,
    val punctuation: Int,
    val annotation: Int,
    val preprocessor: Int,
)

data class SweetEditorSpanColors(
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val builtin: Color,
    val type: Color,
    val className: Color,
    val function: Color,
    val variable: Color,
    val property: Color,
    val parameter: Color,
    val constant: Color,
    val field: Color,
    val namespace: Color,
    val enumMember: Color,
    val operator: Color,
    val punctuation: Color,
    val annotation: Color,
    val preprocessor: Color,
) {
    private val internalColors: SweetEditorSpanColorsInternal = SweetEditorSpanColorsInternal(
        keyword = keyword.toArgb(),
        string = string.toArgb(),
        comment = comment.toArgb(),
        number = number.toArgb(),
        builtin = builtin.toArgb(),
        type = type.toArgb(),
        className = className.toArgb(),
        function = function.toArgb(),
        variable = variable.toArgb(),
        property = property.toArgb(),
        parameter = parameter.toArgb(),
        constant = constant.toArgb(),
        field = field.toArgb(),
        namespace = namespace.toArgb(),
        enumMember = enumMember.toArgb(),
        operator = operator.toArgb(),
        punctuation = punctuation.toArgb(),
        annotation = annotation.toArgb(),
        preprocessor = preprocessor.toArgb(),
    )

    internal fun toInternal(): SweetEditorSpanColorsInternal = internalColors

    internal companion object {
        fun fromInternal(colors: SweetEditorSpanColorsInternal): SweetEditorSpanColors = SweetEditorSpanColors(
            keyword = Color(colors.keyword),
            string = Color(colors.string),
            comment = Color(colors.comment),
            number = Color(colors.number),
            builtin = Color(colors.builtin),
            type = Color(colors.type),
            className = Color(colors.className),
            function = Color(colors.function),
            variable = Color(colors.variable),
            property = Color(colors.property),
            parameter = Color(colors.parameter),
            constant = Color(colors.constant),
            field = Color(colors.field),
            namespace = Color(colors.namespace),
            enumMember = Color(colors.enumMember),
            operator = Color(colors.operator),
            punctuation = Color(colors.punctuation),
            annotation = Color(colors.annotation),
            preprocessor = Color(colors.preprocessor),
        )
    }
}
