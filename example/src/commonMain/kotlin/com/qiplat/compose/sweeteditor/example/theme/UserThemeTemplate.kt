package com.qiplat.compose.sweeteditor.example.theme

import androidx.compose.ui.graphics.Color
import com.qiplat.compose.sweeteditor.SweetEditorDefaults
import com.qiplat.compose.sweeteditor.theme.SweetEditorThemeTemplate

class OceanThemeTemplate : SweetEditorThemeTemplate(
    darkTheme = SweetEditorDefaults.theme(
        colors = SweetEditorDefaults.darkColors(
            background = Color(0xFF111827),
            text = Color(0xFFD6E3F5),
            cursor = Color(0xFF60A5FA),
            gutterBackground = Color(0xFF0F172A),
        ),
        spanStyles = SweetEditorDefaults.spanStyles(
            SweetEditorDefaults.darkSpanColors(
                keyword = Color(0xFF93C5FD),
                string = Color(0xFF86EFAC),
                comment = Color(0xFF94A3B8),
                function = Color(0xFF7DD3FC),
            ),
        ),
    ),
    lightTheme = SweetEditorDefaults.theme(
        colors = SweetEditorDefaults.lightColors(
            background = Color(0xFFF8FAFC),
            text = Color(0xFF1E293B),
            cursor = Color(0xFF2563EB),
            gutterBackground = Color(0xFFF1F5F9),
        ),
        spanStyles = SweetEditorDefaults.spanStyles(
            SweetEditorDefaults.lightSpanColors(
                keyword = Color(0xFF1D4ED8),
                string = Color(0xFF059669),
                comment = Color(0xFF64748B),
                function = Color(0xFF0369A1),
            ),
        ),
    ),
)

class ForestThemeTemplate : SweetEditorThemeTemplate(
    darkTheme = SweetEditorDefaults.theme(
        colors = SweetEditorDefaults.darkColors(
            background = Color(0xFF121A16),
            text = Color(0xFFDCEADF),
            cursor = Color(0xFF4ADE80),
            gutterBackground = Color(0xFF0F1512),
        ),
        spanStyles = SweetEditorDefaults.spanStyles(
            SweetEditorDefaults.darkSpanColors(
                keyword = Color(0xFF86EFAC),
                string = Color(0xFFBBF7D0),
                comment = Color(0xFF8FA39A),
                function = Color(0xFF4ADE80),
            ),
        ),
    ),
    lightTheme = SweetEditorDefaults.theme(
        colors = SweetEditorDefaults.lightColors(
            background = Color(0xFFF7FCF8),
            text = Color(0xFF1B2A22),
            cursor = Color(0xFF15803D),
            gutterBackground = Color(0xFFEEF8F1),
        ),
        spanStyles = SweetEditorDefaults.spanStyles(
            SweetEditorDefaults.lightSpanColors(
                keyword = Color(0xFF166534),
                string = Color(0xFF047857),
                comment = Color(0xFF5F7A6C),
                function = Color(0xFF15803D),
            ),
        ),
    ),
)
