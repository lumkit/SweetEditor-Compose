package com.qiplat.compose.sweeteditor.runtime

fun interface FontMetricsProvider {
    fun getFontMetrics(): FloatArray
}

interface EditorTextMeasurer {
    fun measureTextWidth(text: String, fontStyle: Int): Float

    fun measureInlayHintWidth(text: String): Float

    fun measureIconWidth(iconId: Int): Float

    fun getFontMetrics(): FloatArray
}
