package com.qiplat.compose.sweeteditor.example.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.qiplat.compose.sweeteditor.SweetEditorDefaults
import com.qiplat.compose.sweeteditor.theme.DefaultSweetEditorThemeProvider
import com.qiplat.compose.sweeteditor.theme.SweetEditorTheme
import com.qiplat.compose.sweeteditor.theme.SweetEditorThemeProvider
import com.qiplat.compose.sweeteditor.theme.SweetEditorTypography
import com.qiplat.compose.sweeteditor.theme.rememberSweetEditorTheme
import org.jetbrains.compose.resources.Font
import sweeteditor_compose.example.generated.resources.JetBrainsMono_Regular
import sweeteditor_compose.example.generated.resources.Res

data class ExampleEditorThemeState(
    val theme: SweetEditorTheme,
    val profile: ThemeProfile,
    val darkMode: Boolean,
    val cycleProfile: () -> Unit,
    val toggleDarkMode: () -> Unit,
)

enum class ThemeProfile {
    CustomTheme,
    DefaultTheme,
    JsonTheme,
    ;

    fun next(): ThemeProfile = when (this) {
        CustomTheme -> DefaultTheme
        DefaultTheme -> JsonTheme
        JsonTheme -> CustomTheme
    }
}

@Composable
fun rememberExampleEditorThemeState(): ExampleEditorThemeState {
    val systemDarkMode = isSystemInDarkTheme()
    var themeProfileOrdinal by rememberSaveable { mutableIntStateOf(ThemeProfile.CustomTheme.ordinal) }
    var darkThemeMode by rememberSaveable { mutableStateOf(systemDarkMode) }
    val profile = ThemeProfile.entries[themeProfileOrdinal.coerceIn(0, ThemeProfile.entries.lastIndex)]
    val customTheme = rememberCustomEditorTheme(darkThemeMode)
    val defaultTheme = rememberDefaultEditorTheme(darkThemeMode)
    val jsonTheme = rememberJsonEditorTheme(darkThemeMode)
    val theme = when (profile) {
        ThemeProfile.CustomTheme -> customTheme
        ThemeProfile.DefaultTheme -> defaultTheme
        ThemeProfile.JsonTheme -> jsonTheme
    }
    return remember(profile, darkThemeMode, theme) {
        ExampleEditorThemeState(
            theme = theme,
            profile = profile,
            darkMode = darkThemeMode,
            cycleProfile = {
                themeProfileOrdinal = profile.next().ordinal
            },
            toggleDarkMode = {
                darkThemeMode = !darkThemeMode
            },
        )
    }
}

@Composable
fun rememberCustomEditorTheme(
    darkMode: Boolean = isSystemInDarkTheme(),
): SweetEditorTheme {
    val typography = SweetEditorTypography(
        fontFamily = FontFamily(Font(Res.font.JetBrainsMono_Regular)),
        fontSize = 14.sp,
        lineNumberFontSize = 12.sp,
        inlayHintFontSize = 11.sp,
    )
    val darkTheme = SweetEditorTheme(
        colors = SweetEditorDefaults.darkColors(
            background = Color(0xFF151821),
            text = Color(0xFFDCE3EF),
            cursor = Color(0xFF61D6C7),
            selection = Color(0x55406AA8),
            lineNumber = Color(0xFF5F6B84),
            currentLineNumber = Color(0xFFB0C6EA),
            currentLine = Color(0x1F2A3650),
            guide = Color(0x364C5B78),
            separatorLine = Color(0xFF3FAE86),
            splitLine = Color(0x2C5B6B8A),
            scrollbarTrack = Color(0x1F223049),
            scrollbarThumb = Color(0x8A4E79A9),
            scrollbarThumbActive = Color(0xFF6B9ED8),
            compositionUnderline = Color(0xFF65A8FF),
            inlayHintBackground = Color(0x26354867),
            inlayHintText = Color(0xCCB7C9E8),
            foldPlaceholderBackground = Color(0x3A4A668E),
            foldPlaceholderText = Color(0xFFE4EEFF),
            phantomText = Color(0x92A8BCDB),
            inlayHintIcon = Color(0xD0A4B9D8),
            diagnosticError = Color(0xFFFF6F91),
            diagnosticWarning = Color(0xFFF4BE69),
            diagnosticInfo = Color(0xFF69D3FF),
            diagnosticHint = Color(0xFF94A9C8),
            linkedEditingActive = Color(0xD163A8FF),
            linkedEditingInactive = Color(0x7263A8FF),
            bracketHighlightBorder = Color(0xD0A6DB73),
            bracketHighlightBackground = Color(0x309DCC6C),
            gutterBackground = Color(0xFF1A1E29),
        ),
        typography = typography,
        spanStyles = SweetEditorDefaults.spanStyles(
            SweetEditorDefaults.darkSpanColors(
                keyword = Color(0xFFCA9EFF),
                string = Color(0xFFA9DFA1),
                comment = Color(0xFF6FA77E),
                number = Color(0xFFF4BF7B),
                builtin = Color(0xFF73D0C8),
                type = Color(0xFF9AC2FF),
                className = Color(0xFF8DD6FF),
                function = Color(0xFF7FB4FF),
                variable = Color(0xFFDCE3EF),
                property = Color(0xFF8FC4E8),
                parameter = Color(0xFFE3D79E),
                constant = Color(0xFFFFA9C7),
                field = Color(0xFF9CD8B8),
                namespace = Color(0xFF8EC9FF),
                enumMember = Color(0xFFF6D291),
                operator = Color(0xFFB8C4DE),
                punctuation = Color(0xFF9FAECC),
                annotation = Color(0xFF8FDBFF),
                preprocessor = Color(0xFFF1A66E),
            ),
        ),
        cornerRadius = 2f,
    )
    val lightTheme = SweetEditorTheme(
        colors = SweetEditorDefaults.lightColors(
            background = Color(0xFFF7F9FD),
            text = Color(0xFF1D2738),
            cursor = Color(0xFF0F8FD6),
            selection = Color(0x4D5DA9FF),
            lineNumber = Color(0xFF8793A9),
            currentLineNumber = Color(0xFF2F5D9E),
            currentLine = Color(0x120B4A86),
            guide = Color(0x2239557F),
            separatorLine = Color(0xFF2FA273),
            splitLine = Color(0x1F375A86),
            scrollbarTrack = Color(0x26FFFFFF),
            scrollbarThumb = Color(0x8D7289AA),
            scrollbarThumbActive = Color(0xFF9DB9DD),
            compositionUnderline = Color(0xFF1E7FD1),
            inlayHintBackground = Color(0x16307FD6),
            inlayHintText = Color(0xB0364D77),
            foldPlaceholderBackground = Color(0x2C6E8FB9),
            foldPlaceholderText = Color(0xFF28507A),
            phantomText = Color(0x8C4C6284),
            inlayHintIcon = Color(0xB24C6284),
            diagnosticError = Color(0xFFD93C3C),
            diagnosticWarning = Color(0xFFD98A2B),
            diagnosticInfo = Color(0xFF1AA4D8),
            diagnosticHint = Color(0xFF667A94),
            linkedEditingActive = Color(0xCC1E7FD1),
            linkedEditingInactive = Color(0x661E7FD1),
            bracketHighlightBorder = Color(0xCC13806D),
            bracketHighlightBackground = Color(0x2613806D),
            gutterBackground = Color(0xFFF1F4FA),
        ),
        typography = typography,
        spanStyles = SweetEditorDefaults.spanStyles(
            SweetEditorDefaults.lightSpanColors(
                keyword = Color(0xFF7A3FE0),
                string = Color(0xFF0B8A63),
                comment = Color(0xFF6F7A8E),
                number = Color(0xFFB86B1F),
                builtin = Color(0xFF0D9C95),
                type = Color(0xFF2A63CF),
                className = Color(0xFF0A7CB1),
                function = Color(0xFF2C5FD1),
                variable = Color(0xFF243244),
                property = Color(0xFF1C6D95),
                parameter = Color(0xFF8C5D14),
                constant = Color(0xFFC43D76),
                field = Color(0xFF2B8E67),
                namespace = Color(0xFF3268B8),
                enumMember = Color(0xFFAF6E1E),
                operator = Color(0xFF5A6B86),
                punctuation = Color(0xFF6E7E97),
                annotation = Color(0xFF197EA5),
                preprocessor = Color(0xFFBC6428),
            ),
        ),
        cornerRadius = 2f,
    )
    return rememberSweetEditorTheme(
        provider = remember { StaticThemeProvider(dark = darkTheme, light = lightTheme) },
        darkMode = darkMode,
    )
}

@Composable
private fun rememberDefaultEditorTheme(darkMode: Boolean): SweetEditorTheme {
    return rememberSweetEditorTheme(
        provider = DefaultSweetEditorThemeProvider,
        darkMode = darkMode,
    )
}

@Composable
private fun rememberJsonEditorTheme(darkMode: Boolean): SweetEditorTheme {
    val contentPath = if (darkMode) {
        "files/editor/theme_dark.json"
    } else {
        "files/editor/theme_light.json"
    }
    val content by produceState<String?>(initialValue = null, contentPath) {
        value = Res.readBytes(contentPath).decodeToString()
    }
    return rememberSweetEditorTheme(
        provider = DefaultSweetEditorThemeProvider,
        themeContent = content,
        darkMode = darkMode,
    )
}

private class StaticThemeProvider(
    private val dark: SweetEditorTheme,
    private val light: SweetEditorTheme,
) : SweetEditorThemeProvider {
    override fun darkTheme(): SweetEditorTheme = dark

    override fun lightTheme(): SweetEditorTheme = light
}
