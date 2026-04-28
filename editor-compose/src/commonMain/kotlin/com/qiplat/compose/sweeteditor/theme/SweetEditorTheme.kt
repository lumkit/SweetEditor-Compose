package com.qiplat.compose.sweeteditor.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

data class SweetEditorTheme(
    val colors: SweetEditorColors,
    val typography: SweetEditorTypography,
    val spanStyles: SweetEditorSpanStyles,
    val cornerRadius: Float,
)

interface SweetEditorThemeProvider {
    fun darkTheme(): SweetEditorTheme

    fun lightTheme(): SweetEditorTheme
}

object DefaultSweetEditorThemeProvider : SweetEditorThemeProvider {
    override fun darkTheme(): SweetEditorTheme = SweetEditorDefaults.theme(
        colors = SweetEditorDefaults.darkColors(),
        spanStyles = SweetEditorDefaults.spanStyles(SweetEditorDefaults.darkSpanColors()),
    )

    override fun lightTheme(): SweetEditorTheme = SweetEditorDefaults.theme(
        colors = SweetEditorDefaults.lightColors(),
        spanStyles = SweetEditorDefaults.spanStyles(SweetEditorDefaults.lightSpanColors()),
    )
}

internal fun parseSweetEditorTheme(
    themeContent: String?,
    fallback: SweetEditorTheme = SweetEditorDefaults.theme(),
): SweetEditorTheme = SweetEditorThemeParser.parse(
    content = themeContent,
    fallback = fallback,
)

@Composable
fun rememberSweetEditorTheme(
    provider: SweetEditorThemeProvider = DefaultSweetEditorThemeProvider,
    darkMode: Boolean = true,
): SweetEditorTheme {
    return remember(provider, darkMode) {
        if (darkMode) provider.darkTheme() else provider.lightTheme()
    }
}

@Composable
fun rememberSweetEditorTheme(
    provider: SweetEditorThemeProvider = DefaultSweetEditorThemeProvider,
    themeContent: String? = null,
    darkMode: Boolean = true,
): SweetEditorTheme {
    val fallback = rememberSweetEditorTheme(
        provider = provider,
        darkMode = darkMode,
    )
    return remember(themeContent, fallback, provider, darkMode) {
        parseSweetEditorTheme(
            themeContent = themeContent,
            fallback = fallback,
        )
    }
}
