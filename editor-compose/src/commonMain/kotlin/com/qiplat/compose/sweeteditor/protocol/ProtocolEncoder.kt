package com.qiplat.compose.sweeteditor.protocol

import com.qiplat.compose.sweeteditor.model.decoration.*
import com.qiplat.compose.sweeteditor.model.foundation.EditorOptions
import com.qiplat.compose.sweeteditor.model.snippet.LinkedEditingModel

object ProtocolEncoder {
    fun encodeEditorOptions(options: EditorOptions): ByteArray {
        val writer = BinaryWriter(initialCapacity = 40)
        writer.writeFloat(options.touchSlop)
        writer.writeLong(options.doubleTapTimeout)
        writer.writeLong(options.longPressMs)
        writer.writeFloat(options.flingFriction)
        writer.writeFloat(options.flingMinVelocity)
        writer.writeFloat(options.flingMaxVelocity)
        writer.writeLong(options.maxUndoStackSize)
        return writer.toByteArray()
    }

    fun encodeBatchTextStyles(stylesById: Map<Int, TextStyle>): ByteArray {
        val writer = BinaryWriter(initialCapacity = 4 + stylesById.size * 16)
        val orderedEntries = orderedEntries(stylesById)
        writer.writeInt(orderedEntries.size)
        orderedEntries.forEach { entry ->
            val styleId = entry.first
            val style = entry.second
            writer.writeInt(styleId)
            writer.writeInt(style.color)
            writer.writeInt(style.backgroundColor)
            writer.writeInt(style.fontStyle)
        }
        return writer.toByteArray()
    }

    fun encodeLineSpans(
        line: Int,
        layerNativeValue: Int,
        spans: List<StyleSpan>,
    ): ByteArray {
        val writer = BinaryWriter(initialCapacity = 12 + spans.size * 12)
        writer.writeInt(line)
        writer.writeInt(layerNativeValue)
        writer.writeInt(spans.size)
        spans.forEach { span ->
            writer.writeInt(span.column)
            writer.writeInt(span.length)
            writer.writeInt(span.styleId)
        }
        return writer.toByteArray()
    }

    fun encodeBatchLineSpans(
        layer: SpanLayer,
        spansByLine: Map<Int, List<StyleSpan>>,
    ): ByteArray {
        val orderedEntries = orderedEntries(spansByLine)
        val totalSpanCount = orderedEntries.sumOf { it.second.size }
        val writer = BinaryWriter(initialCapacity = 8 + orderedEntries.size * 8 + totalSpanCount * 12)
        writer.writeInt(layer.toNativeValue())
        writer.writeInt(orderedEntries.size)
        orderedEntries.forEach { entry ->
            val line = entry.first
            val spans = entry.second
            writer.writeInt(line)
            writer.writeInt(spans.size)
            spans.forEach { span ->
                writer.writeInt(span.column)
                writer.writeInt(span.length)
                writer.writeInt(span.styleId)
            }
        }
        return writer.toByteArray()
    }

    fun encodeLinkedEditingModel(model: LinkedEditingModel): ByteArray {
        val groupTexts = model.groups.map { it.defaultText?.encodeToByteArray() }
        val groupCount = model.groups.size
        val rangeCount = model.groups.sumOf { it.ranges.size }
        val stringBlobSize = groupTexts.sumOf { it?.size ?: 0 }
        val writer = BinaryWriter(
            initialCapacity = 12 + groupCount * 12 + rangeCount * 20 + stringBlobSize,
        )

        writer.writeInt(groupCount)
        writer.writeInt(rangeCount)
        writer.writeInt(stringBlobSize)

        var offset = 0
        model.groups.forEachIndexed { index, group ->
            writer.writeInt(group.index)
            val bytes = groupTexts[index]
            if (bytes == null) {
                writer.writeInt(-1)
                writer.writeInt(0)
            } else {
                writer.writeInt(offset)
                writer.writeInt(bytes.size)
                offset += bytes.size
            }
        }

        model.groups.forEachIndexed { groupOrdinal, group ->
            group.ranges.forEach { range ->
                writer.writeInt(groupOrdinal)
                writer.writeInt(range.start.line)
                writer.writeInt(range.start.column)
                writer.writeInt(range.end.line)
                writer.writeInt(range.end.column)
            }
        }

        groupTexts.forEach { bytes ->
            if (bytes != null) {
                writer.writeBytes(bytes)
            }
        }

        return writer.toByteArray()
    }

    fun encodeFoldRegions(regions: List<FoldRegion>): ByteArray {
        val writer = BinaryWriter(initialCapacity = 4 + regions.size * 8)
        writer.writeInt(regions.size)
        regions.forEach { region ->
            writer.writeInt(region.startLine)
            writer.writeInt(region.endLine)
        }
        return writer.toByteArray()
    }

    fun encodeLineInlayHints(line: Int, hints: List<InlayHint>): ByteArray {
        val writer = BinaryWriter()
        writer.writeInt(line)
        writer.writeInt(hints.size)
        hints.forEach { hint ->
            writer.writeInt(hint.type.toNativeValue())
            writer.writeInt(hint.column)
            val intValue = when (hint.type) {
                InlayType.Text -> 0
                InlayType.Icon -> hint.iconId
                InlayType.Color -> hint.color
            }
            writer.writeInt(intValue)
            writer.writeUtf8(if (hint.type == InlayType.Text) hint.text else "")
        }
        return writer.toByteArray()
    }

    fun encodeBatchLineInlayHints(hintsByLine: Map<Int, List<InlayHint>>): ByteArray {
        val writer = BinaryWriter()
        val orderedEntries = orderedEntries(hintsByLine)
        writer.writeInt(orderedEntries.size)
        orderedEntries.forEach { entry ->
            val line = entry.first
            val hints = entry.second
            writer.writeInt(line)
            writer.writeInt(hints.size)
            hints.forEach { hint ->
                writer.writeInt(hint.type.toNativeValue())
                writer.writeInt(hint.column)
                val intValue = when (hint.type) {
                    InlayType.Text -> 0
                    InlayType.Icon -> hint.iconId
                    InlayType.Color -> hint.color
                }
                writer.writeInt(intValue)
                writer.writeUtf8(if (hint.type == InlayType.Text) hint.text else "")
            }
        }
        return writer.toByteArray()
    }

    fun encodeLinePhantomTexts(line: Int, phantoms: List<PhantomText>): ByteArray {
        val writer = BinaryWriter()
        writer.writeInt(line)
        writer.writeInt(phantoms.size)
        phantoms.forEach { phantom ->
            writer.writeInt(phantom.column)
            writer.writeUtf8(phantom.text)
        }
        return writer.toByteArray()
    }

    fun encodeBatchLinePhantomTexts(phantomsByLine: Map<Int, List<PhantomText>>): ByteArray {
        val writer = BinaryWriter()
        val orderedEntries = orderedEntries(phantomsByLine)
        writer.writeInt(orderedEntries.size)
        orderedEntries.forEach { entry ->
            val line = entry.first
            val phantoms = entry.second
            writer.writeInt(line)
            writer.writeInt(phantoms.size)
            phantoms.forEach { phantom ->
                writer.writeInt(phantom.column)
                writer.writeUtf8(phantom.text)
            }
        }
        return writer.toByteArray()
    }

    fun encodeLineGutterIcons(line: Int, icons: List<GutterIcon>): ByteArray {
        val writer = BinaryWriter(initialCapacity = 8 + icons.size * 4)
        writer.writeInt(line)
        writer.writeInt(icons.size)
        icons.forEach { icon ->
            writer.writeInt(icon.iconId)
        }
        return writer.toByteArray()
    }

    fun encodeBatchLineGutterIcons(iconsByLine: Map<Int, List<GutterIcon>>): ByteArray {
        val orderedEntries = orderedEntries(iconsByLine)
        val totalCount = orderedEntries.sumOf { it.second.size }
        val writer = BinaryWriter(initialCapacity = 4 + orderedEntries.size * 8 + totalCount * 4)
        writer.writeInt(orderedEntries.size)
        orderedEntries.forEach { entry ->
            val line = entry.first
            val icons = entry.second
            writer.writeInt(line)
            writer.writeInt(icons.size)
            icons.forEach { icon ->
                writer.writeInt(icon.iconId)
            }
        }
        return writer.toByteArray()
    }

    fun encodeLineDiagnostics(line: Int, diagnostics: List<DiagnosticItem>): ByteArray {
        val writer = BinaryWriter(initialCapacity = 8 + diagnostics.size * 16)
        writer.writeInt(line)
        writer.writeInt(diagnostics.size)
        diagnostics.forEach { diagnostic ->
            writer.writeInt(diagnostic.column)
            writer.writeInt(diagnostic.length)
            writer.writeInt(diagnostic.severity.toNativeValue())
            writer.writeInt(diagnostic.color)
        }
        return writer.toByteArray()
    }

    fun encodeBatchLineDiagnostics(diagnosticsByLine: Map<Int, List<DiagnosticItem>>): ByteArray {
        val orderedEntries = orderedEntries(diagnosticsByLine)
        val totalCount = orderedEntries.sumOf { it.second.size }
        val writer = BinaryWriter(initialCapacity = 4 + orderedEntries.size * 8 + totalCount * 16)
        writer.writeInt(orderedEntries.size)
        orderedEntries.forEach { entry ->
            val line = entry.first
            val diagnostics = entry.second
            writer.writeInt(line)
            writer.writeInt(diagnostics.size)
            diagnostics.forEach { diagnostic ->
                writer.writeInt(diagnostic.column)
                writer.writeInt(diagnostic.length)
                writer.writeInt(diagnostic.severity.toNativeValue())
                writer.writeInt(diagnostic.color)
            }
        }
        return writer.toByteArray()
    }

    private fun <T> orderedEntries(values: Map<Int, T>): List<Pair<Int, T>> =
        values.entries
            .sortedBy { it.key }
            .map { it.key to it.value }
}
