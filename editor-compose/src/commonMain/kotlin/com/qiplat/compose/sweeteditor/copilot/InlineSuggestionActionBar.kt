package com.qiplat.compose.sweeteditor.copilot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.qiplat.compose.sweeteditor.model.visual.Cursor
import com.qiplat.compose.sweeteditor.theme.SweetEditorThemeScheme
import kotlin.math.roundToInt

@Composable
fun InlineSuggestionActionBar(
    suggestion: InlineSuggestion?,
    cursor: Cursor?,
    theme: SweetEditorThemeScheme,
    editorWindowOffset: IntOffset,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (suggestion == null || cursor == null) {
        return
    }
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(
            x = editorWindowOffset.x + cursor.position.x.roundToInt(),
            y = editorWindowOffset.y + (cursor.position.y + cursor.height).roundToInt() + 2,
        ),
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = true,
        ),
    ) {
        val background = theme.colors.gutterBackground
        val borderColor = theme.colors.splitLine
        val acceptColor = theme.colors.text
        val dismissColor = theme.colors.lineNumber
        Box(
            modifier = Modifier
                .background(background, RoundedCornerShape(8.dp))
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ActionChip(
                    label = "Accept",
                    color = acceptColor,
                    onClick = onAccept,
                )
                ActionChip(
                    label = "Dismiss",
                    color = dismissColor,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    BasicText(
        text = label,
        style = androidx.compose.ui.text.TextStyle(color = color),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

