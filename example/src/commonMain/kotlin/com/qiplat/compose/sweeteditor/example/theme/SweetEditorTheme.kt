package com.qiplat.compose.sweeteditor.example.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.qiplat.compose.sweeteditor.SweetEditorDefaults
import com.qiplat.compose.sweeteditor.theme.SweetEditorTypography
import com.qiplat.compose.sweeteditor.theme.SweetEditorTheme
import com.qiplat.compose.sweeteditor.theme.rememberSweetEditorTheme

@Composable
fun rememberSweetEditorTheme(
    darkMode: Boolean = isSystemInDarkTheme(),
): SweetEditorTheme {
    val parsedColors = if (darkMode) DarkEditorColors else LightEditorColors
    val typography = EditorTypography
    val spanStyles = SweetEditorDefaults.spanStyles(
        spanColors = if (darkMode) DarkEditorSpanColors else LightEditorSpanColors
    )

    return rememberSweetEditorTheme(parsedColors, typography, spanStyles)
}
