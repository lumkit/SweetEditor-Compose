package com.qiplat.compose.sweeteditor.protocol

import com.qiplat.compose.sweeteditor.model.foundation.EditorOptions
import com.qiplat.compose.sweeteditor.model.foundation.PointerCursorType
import com.qiplat.compose.sweeteditor.model.visual.VisualLineKind
import kotlin.test.*

class ProtocolCommonTest {
    @Test
    fun binaryWriterAndReaderRoundTrip() {
        val writer = BinaryWriter()
        writer.writeInt(7)
        writer.writeLong(9L)
        writer.writeFloat(1.5f)
        writer.writeBooleanAsInt(true)
        writer.writeUtf8("sweet")

        val reader = BinaryReader(writer.toByteArray())
        assertEquals(7, reader.readInt())
        assertEquals(9L, reader.readLong())
        assertEquals(1.5f, reader.readFloat())
        assertTrue(reader.readBooleanAsInt())
        assertEquals("sweet", reader.readUtf8())
        assertEquals(0, reader.remaining)
    }

    @Test
    fun editorOptionsEncodingMatchesNativeLayoutSize() {
        val payload = ProtocolEncoder.encodeEditorOptions(EditorOptions())
        assertEquals(48, payload.size)

        val reader = BinaryReader(payload)
        assertEquals(10f, reader.readFloat())
        assertEquals(300L, reader.readLong())
        assertEquals(500L, reader.readLong())
        assertEquals(3.5f, reader.readFloat())
        assertEquals(50f, reader.readFloat())
        assertEquals(8000f, reader.readFloat())
        assertEquals(512L, reader.readLong())
        assertEquals(1000L, reader.readLong())
    }

    @Test
    fun decodeTextEditResultReadsChanges() {
        val writer = BinaryWriter()
        writer.writeBooleanAsInt(true)
        writer.writeInt(1)
        writer.writeInt(1)
        writer.writeInt(2)
        writer.writeInt(1)
        writer.writeInt(4)
        writer.writeUtf8("abc")

        val result = ProtocolDecoder.decodeTextEditResult(writer.toByteArray())
        assertTrue(result.changed)
        assertEquals(1, result.changes.size)
        assertEquals(1, result.changes.first().range.start.line)
        assertEquals(2, result.changes.first().range.start.column)
        assertEquals("abc", result.changes.first().newText)
    }

    @Test
    fun decodeRenderModelSupportsScrollbarTail() {
        val writer = BinaryWriter()
        writer.writeFloat(1f)
        writer.writeBooleanAsInt(true)
        writer.writeFloat(2f)
        writer.writeFloat(3f)
        writer.writeFloat(100f)
        writer.writeFloat(200f)
        writer.writeFloat(4f)
        writer.writeFloat(5f)
        writer.writeInt(0)

        writer.writeInt(1)
        writer.writeInt(7)
        writer.writeInt(0)
        writer.writeFloat(10f)
        writer.writeFloat(20f)
        writer.writeInt(2)
        writer.writeBooleanAsInt(false)
        writer.writeInt(1)
        writer.writeInt(1)
        writer.writeInt(4)
        writer.writeFloat(11f)
        writer.writeFloat(22f)
        writer.writeUtf8("fold")
        writer.writeInt(3)
        writer.writeInt(0xFFAA00)
        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeFloat(50f)
        writer.writeFloat(1f)
        writer.writeFloat(2f)
        writer.writeBooleanAsInt(true)

        writer.writeInt(0)
        writer.writeInt(0)

        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeFloat(0f)
        writer.writeFloat(0f)
        writer.writeFloat(0f)
        writer.writeBooleanAsInt(false)
        writer.writeBooleanAsInt(false)

        writer.writeInt(0)

        repeat(2) {
            writer.writeFloat(0f)
            writer.writeFloat(0f)
            writer.writeFloat(0f)
            writer.writeBooleanAsInt(false)
        }

        writer.writeBooleanAsInt(false)
        writer.writeFloat(0f)
        writer.writeFloat(0f)
        writer.writeFloat(0f)
        writer.writeFloat(0f)

        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeBooleanAsInt(true)
        writer.writeFloat(0.7f)
        repeat(2) {
            writer.writeFloat(1f)
            writer.writeFloat(2f)
            writer.writeFloat(3f)
            writer.writeFloat(4f)
        }
        writer.writeBooleanAsInt(false)
        writer.writeFloat(0.1f)
        repeat(2) {
            writer.writeFloat(5f)
            writer.writeFloat(6f)
            writer.writeFloat(7f)
            writer.writeFloat(8f)
        }
        writer.writeBooleanAsInt(true)
        writer.writeBooleanAsInt(false)
        writer.writeInt(2)

        val model = ProtocolDecoder.decodeRenderModel(writer.toByteArray())
        assertNotNull(model)
        assertEquals(1f, model.splitX)
        assertEquals(1, model.lines.size)
        assertEquals(VisualLineKind.CodeLens, model.lines.single().kind)
        assertFalse(model.lines.single().ownsGutterSemantics)
        assertTrue(model.lines.single().runs.single().active)
        assertTrue(model.verticalScrollbar.visible)
        assertFalse(model.verticalScrollbar.thumbActive)
        assertFalse(model.horizontalScrollbar.visible)
        assertTrue(model.gutterSticky)
        assertFalse(model.gutterVisible)
        assertEquals(PointerCursorType.Hand, model.pointerCursorType)
    }

    @Test
    fun decodeRenderModelNormalizesSwappedScrollbarOrientation() {
        val writer = BinaryWriter()
        writer.writeFloat(1f)
        writer.writeBooleanAsInt(true)
        writer.writeFloat(2f)
        writer.writeFloat(3f)
        writer.writeFloat(300f)
        writer.writeFloat(200f)
        writer.writeFloat(4f)
        writer.writeFloat(5f)
        writer.writeInt(0)

        writer.writeInt(1)
        writer.writeInt(7)
        writer.writeInt(0)
        writer.writeFloat(10f)
        writer.writeFloat(20f)
        writer.writeInt(2)
        writer.writeBooleanAsInt(false)
        writer.writeInt(1)
        writer.writeInt(1)
        writer.writeInt(4)
        writer.writeFloat(11f)
        writer.writeFloat(22f)
        writer.writeUtf8("fold")
        writer.writeInt(3)
        writer.writeInt(0xFFAA00)
        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeFloat(50f)
        writer.writeFloat(1f)
        writer.writeFloat(2f)
        writer.writeBooleanAsInt(true)

        writer.writeInt(0)
        writer.writeInt(0)

        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeFloat(0f)
        writer.writeFloat(0f)
        writer.writeFloat(0f)
        writer.writeBooleanAsInt(false)
        writer.writeBooleanAsInt(false)

        writer.writeInt(0)

        repeat(2) {
            writer.writeFloat(0f)
            writer.writeFloat(0f)
            writer.writeFloat(0f)
            writer.writeBooleanAsInt(false)
        }
        writer.writeBooleanAsInt(false)
        writer.writeFloat(0f)
        writer.writeFloat(0f)
        writer.writeFloat(0f)
        writer.writeFloat(0f)
        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeInt(0)

        // Native tail writes horizontal first, vertical second.
        writer.writeBooleanAsInt(true)
        writer.writeFloat(1f)
        repeat(2) {
            writer.writeFloat(10f)
            writer.writeFloat(190f)
            writer.writeFloat(120f)
            writer.writeFloat(3f)
        }
        writer.writeBooleanAsInt(true)
        writer.writeFloat(1f)
        repeat(2) {
            writer.writeFloat(294f)
            writer.writeFloat(0f)
            writer.writeFloat(6f)
            writer.writeFloat(80f)
        }
        writer.writeBooleanAsInt(true)
        writer.writeBooleanAsInt(true)
        writer.writeInt(0)

        val model = ProtocolDecoder.decodeRenderModel(writer.toByteArray())

        assertNotNull(model)
        assertTrue(model.verticalScrollbar.visible)
        assertTrue(model.verticalScrollbar.track.height > model.verticalScrollbar.track.width)
        assertTrue(model.horizontalScrollbar.visible)
        assertTrue(model.horizontalScrollbar.track.width > model.horizontalScrollbar.track.height)
    }

    @Test
    fun decodeRenderModelFallsBackToLegacyLineLayout() {
        val writer = BinaryWriter()
        writer.writeFloat(1f)
        writer.writeBooleanAsInt(true)
        writer.writeFloat(2f)
        writer.writeFloat(3f)
        writer.writeFloat(100f)
        writer.writeFloat(200f)
        writer.writeFloat(4f)
        writer.writeFloat(5f)
        writer.writeInt(0)

        writer.writeInt(1)
        writer.writeInt(7)
        writer.writeInt(0)
        writer.writeFloat(10f)
        writer.writeFloat(20f)
        writer.writeBooleanAsInt(false)
        writer.writeInt(1)
        writer.writeInt(1)
        writer.writeInt(4)
        writer.writeFloat(11f)
        writer.writeFloat(22f)
        writer.writeUtf8("fold")
        writer.writeInt(3)
        writer.writeInt(0xFFAA00)
        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeFloat(50f)
        writer.writeFloat(1f)
        writer.writeFloat(2f)

        writer.writeInt(0)
        writer.writeInt(0)

        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeFloat(0f)
        writer.writeFloat(0f)
        writer.writeFloat(0f)
        writer.writeBooleanAsInt(false)
        writer.writeBooleanAsInt(false)

        writer.writeInt(0)

        repeat(2) {
            writer.writeFloat(0f)
            writer.writeFloat(0f)
            writer.writeFloat(0f)
            writer.writeBooleanAsInt(false)
        }

        writer.writeBooleanAsInt(false)
        writer.writeFloat(0f)
        writer.writeFloat(0f)
        writer.writeFloat(0f)
        writer.writeFloat(0f)

        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeBooleanAsInt(true)
        writer.writeFloat(0.7f)
        writer.writeBooleanAsInt(true)
        repeat(2) {
            writer.writeFloat(1f)
            writer.writeFloat(2f)
            writer.writeFloat(3f)
            writer.writeFloat(4f)
        }
        writer.writeBooleanAsInt(false)
        writer.writeFloat(0.1f)
        writer.writeBooleanAsInt(false)
        repeat(2) {
            writer.writeFloat(5f)
            writer.writeFloat(6f)
            writer.writeFloat(7f)
            writer.writeFloat(8f)
        }
        writer.writeBooleanAsInt(true)
        writer.writeBooleanAsInt(false)

        val model = ProtocolDecoder.decodeRenderModel(writer.toByteArray())
        assertNotNull(model)
        assertEquals(1, model.lines.size)
        assertEquals(VisualLineKind.Content, model.lines.single().kind)
        assertTrue(model.lines.single().ownsGutterSemantics)
        assertFalse(model.lines.single().runs.single().active)
        assertTrue(model.verticalScrollbar.thumbActive)
        assertEquals(PointerCursorType.Default, model.pointerCursorType)
    }

    @Test
    fun decodeGestureResultReadsPointerCursorType() {
        val writer = BinaryWriter()
        writer.writeInt(1)
        writer.writeFloat(12f)
        writer.writeFloat(24f)
        writer.writeInt(3)
        writer.writeInt(4)
        writer.writeBooleanAsInt(false)
        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeFloat(0f)
        writer.writeFloat(0f)
        writer.writeFloat(0f)
        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeBooleanAsInt(false)
        writer.writeBooleanAsInt(false)
        writer.writeBooleanAsInt(false)
        writer.writeBooleanAsInt(false)
        writer.writeInt(1)

        val result = ProtocolDecoder.decodeGestureResult(writer.toByteArray())
        assertEquals(PointerCursorType.Text, result.pointerCursorType)
    }
}
