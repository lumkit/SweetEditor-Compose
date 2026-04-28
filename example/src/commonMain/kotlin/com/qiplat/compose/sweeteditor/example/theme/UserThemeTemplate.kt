package com.qiplat.compose.sweeteditor.example.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.qiplat.compose.sweeteditor.SweetEditorDefaults
import com.qiplat.compose.sweeteditor.theme.SweetEditorTheme
import com.qiplat.compose.sweeteditor.theme.SweetEditorThemeProvider
import com.qiplat.compose.sweeteditor.theme.SweetEditorTypography
import com.qiplat.compose.sweeteditor.theme.rememberSweetEditorTheme
import org.jetbrains.compose.resources.Font
import sweeteditor_compose.example.generated.resources.JetBrainsMono_Regular
import sweeteditor_compose.example.generated.resources.Res

@Composable
fun rememberUserTheme(
    themeProvider: SweetEditorThemeProvider,
    darkMode: Boolean,
): SweetEditorTheme {
    return rememberSweetEditorTheme(
        provider = themeProvider,
        darkMode = darkMode,
    )
}

@Composable
fun oceanThemeProvider(): SweetEditorThemeProvider {
    val typography = SweetEditorTypography(
        fontFamily = FontFamily(Font(Res.font.JetBrainsMono_Regular)),
        fontSize = 14.sp,
        lineNumberFontSize = 12.sp,
        inlayHintFontSize = 11.sp,
    )
    val dark = SweetEditorTheme(
        colors = SweetEditorDefaults.darkColors(
            background = Color(0xFF111827),
            text = Color(0xFFD6E3F5),
            cursor = Color(0xFF60A5FA),
            gutterBackground = Color(0xFF0F172A),
        ),
        typography = typography,
        spanStyles = SweetEditorDefaults.spanStyles(
            SweetEditorDefaults.darkSpanColors(
                keyword = Color(0xFF93C5FD),
                string = Color(0xFF86EFAC),
                comment = Color(0xFF94A3B8),
                function = Color(0xFF7DD3FC),
            ),
        ),
        cornerRadius = 2f,
    )
    val light = SweetEditorTheme(
        colors = SweetEditorDefaults.lightColors(
            background = Color(0xFFF8FAFC),
            text = Color(0xFF1E293B),
            cursor = Color(0xFF2563EB),
            gutterBackground = Color(0xFFF1F5F9),
        ),
        typography = typography,
        spanStyles = SweetEditorDefaults.spanStyles(
            SweetEditorDefaults.lightSpanColors(
                keyword = Color(0xFF1D4ED8),
                string = Color(0xFF059669),
                comment = Color(0xFF64748B),
                function = Color(0xFF0369A1),
            ),
        ),
        cornerRadius = 2f,
    )
    return object : SweetEditorThemeProvider {
        override fun darkTheme(): SweetEditorTheme = dark
        override fun lightTheme(): SweetEditorTheme = light
    }
}

@Composable
fun forestThemeProvider(): SweetEditorThemeProvider {
    val typography = SweetEditorTypography(
        fontFamily = FontFamily(Font(Res.font.JetBrainsMono_Regular)),
        fontSize = 14.sp,
        lineNumberFontSize = 12.sp,
        inlayHintFontSize = 11.sp,
    )
    val dark = SweetEditorTheme(
        colors = SweetEditorDefaults.darkColors(
            background = Color(0xFF121A16),
            text = Color(0xFFDCEADF),
            cursor = Color(0xFF4ADE80),
            gutterBackground = Color(0xFF0F1512),
        ),
        typography = typography,
        spanStyles = SweetEditorDefaults.spanStyles(
            SweetEditorDefaults.darkSpanColors(
                keyword = Color(0xFF86EFAC),
                string = Color(0xFFBBF7D0),
                comment = Color(0xFF8FA39A),
                function = Color(0xFF4ADE80),
            ),
        ),
        cornerRadius = 2f,
    )
    val light = SweetEditorTheme(
        colors = SweetEditorDefaults.lightColors(
            background = Color(0xFFF7FCF8),
            text = Color(0xFF1B2A22),
            cursor = Color(0xFF15803D),
            gutterBackground = Color(0xFFEEF8F1),
        ),
        typography = typography,
        spanStyles = SweetEditorDefaults.spanStyles(
            SweetEditorDefaults.lightSpanColors(
                keyword = Color(0xFF166534),
                string = Color(0xFF047857),
                comment = Color(0xFF5F7A6C),
                function = Color(0xFF15803D),
            ),
        ),
        cornerRadius = 2f,
    )
    return object : SweetEditorThemeProvider {
        override fun darkTheme(): SweetEditorTheme = dark
        override fun lightTheme(): SweetEditorTheme = light
    }
}
