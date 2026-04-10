package com.qiplat.compose.sweeteditor.core.document

import com.qiplat.compose.sweeteditor.core.EditorCoreTextPosition
import com.qiplat.compose.sweeteditor.core.EditorCoreTextRange

data class DocumentPiece(
    val bufferKind: BufferKind,
    val start: Int,
    val length: Int,
)

enum class BufferKind {
    Original,
    Add,
}

data class DocumentSnapshot(
    val version: Long,
    val text: String,
    val lineCount: Int,
    val length: Int,
)

data class TextChange(
    val rangeBefore: EditorCoreTextRange,
    val rangeAfter: EditorCoreTextRange,
    val insertedText: String,
    val deletedText: String,
)

data class TextChangeSet(
    val beforeVersion: Long,
    val afterVersion: Long,
    val changes: List<TextChange>,
)

class DocumentStore(
    initialText: String,
) {
    private val originalBuffer: String = initialText
    private val addBuffer = StringBuilder()
    private var pieces: MutableList<DocumentPiece> =
        if (initialText.isEmpty()) {
            mutableListOf()
        } else {
            mutableListOf(
                DocumentPiece(
                    bufferKind = BufferKind.Original,
                    start = 0,
                    length = initialText.length,
                ),
            )
        }
    private var cachedText: String = initialText
    private var lineStarts: IntArray = computeLineStarts(initialText)

    var version: Long = 0
        private set

    fun getText(): String = cachedText

    fun getLineCount(): Int = lineStarts.size

    fun getLineText(line: Int): String {
        validateLine(line)
        val start = lineStarts[line]
        val end = lineEndOffset(line)
        return cachedText.substring(start, end)
    }

    fun getCharAtUtf16(offset: Int): Char {
        require(offset in cachedText.indices)
        return cachedText[offset]
    }

    fun getOffsetForPosition(position: EditorCoreTextPosition): Int {
        validatePosition(position)
        return lineStarts[position.line] + position.column
    }

    fun getPositionForOffset(offset: Int): EditorCoreTextPosition {
        require(offset in 0..cachedText.length)
        val line = findLineForOffset(offset)
        val lineStart = lineStarts[line]
        val lineEnd = lineEndOffset(line)
        val column = minOf(offset, lineEnd) - lineStart
        return EditorCoreTextPosition(
            line = line,
            column = column,
        )
    }

    fun replace(
        range: EditorCoreTextRange,
        text: String,
    ): TextChangeSet {
        validateRange(range)
        val beforeVersion = version
        val startOffset = getOffsetForPosition(range.start)
        val endOffset = getOffsetForPosition(range.end)
        val deletedText = cachedText.substring(startOffset, endOffset)
        mutate(
            startOffset = startOffset,
            endOffset = endOffset,
            insertedText = text,
            updateVersion = false,
        )
        version = beforeVersion + 1
        val rangeAfter = EditorCoreTextRange(
            start = getPositionForOffset(startOffset),
            end = getPositionForOffset(startOffset + text.length),
        )
        return TextChangeSet(
            beforeVersion = beforeVersion,
            afterVersion = version,
            changes = listOf(
                TextChange(
                    rangeBefore = range,
                    rangeAfter = rangeAfter,
                    insertedText = text,
                    deletedText = deletedText,
                ),
            ),
        )
    }

    fun apply(changeSet: TextChangeSet): DocumentSnapshot {
        require(changeSet.beforeVersion == version)
        if (changeSet.changes.isEmpty()) {
            version = changeSet.afterVersion
            return snapshot()
        }
        val sortedChanges = changeSet.changes.sortedByDescending { change ->
            getOffsetForPosition(change.rangeBefore.start)
        }
        sortedChanges.forEach { change ->
            mutate(
                startOffset = getOffsetForPosition(change.rangeBefore.start),
                endOffset = getOffsetForPosition(change.rangeBefore.end),
                insertedText = change.insertedText,
                updateVersion = false,
            )
        }
        version = changeSet.afterVersion
        return snapshot()
    }

    fun snapshot(): DocumentSnapshot =
        DocumentSnapshot(
            version = version,
            text = cachedText,
            lineCount = getLineCount(),
            length = cachedText.length,
        )

    private fun mutate(
        startOffset: Int,
        endOffset: Int,
        insertedText: String,
        updateVersion: Boolean,
    ) {
        require(startOffset in 0..cachedText.length)
        require(endOffset in 0..cachedText.length)
        require(startOffset <= endOffset)
        val beforePieces = mutableListOf<DocumentPiece>()
        val afterPieces = mutableListOf<DocumentPiece>()
        var absoluteOffset = 0
        pieces.forEach { piece ->
            val pieceStart = absoluteOffset
            val pieceEnd = pieceStart + piece.length
            when {
                pieceEnd <= startOffset -> beforePieces += piece
                pieceStart >= endOffset -> afterPieces += piece
                else -> {
                    val prefixLength = (startOffset - pieceStart).coerceIn(0, piece.length)
                    if (prefixLength > 0) {
                        beforePieces += piece.copy(length = prefixLength)
                    }
                    val suffixLength = (pieceEnd - endOffset).coerceIn(0, piece.length)
                    if (suffixLength > 0) {
                        afterPieces += DocumentPiece(
                            bufferKind = piece.bufferKind,
                            start = piece.start + piece.length - suffixLength,
                            length = suffixLength,
                        )
                    }
                }
            }
            absoluteOffset = pieceEnd
        }
        val mergedPieces = mutableListOf<DocumentPiece>()
        mergedPieces += beforePieces
        if (insertedText.isNotEmpty()) {
            val addStart = addBuffer.length
            addBuffer.append(insertedText)
            mergedPieces += DocumentPiece(
                bufferKind = BufferKind.Add,
                start = addStart,
                length = insertedText.length,
            )
        }
        mergedPieces += afterPieces
        pieces = mergeAdjacentPieces(mergedPieces)
        cachedText = cachedText.replaceRange(startOffset, endOffset, insertedText)
        lineStarts = computeLineStarts(cachedText)
        if (updateVersion) {
            version += 1
        }
    }

    private fun validateLine(line: Int) {
        require(line in 0 until getLineCount())
    }

    private fun validatePosition(position: EditorCoreTextPosition) {
        validateLine(position.line)
        require(position.column in 0..lineLength(position.line))
    }

    private fun validateRange(range: EditorCoreTextRange) {
        validatePosition(range.start)
        validatePosition(range.end)
        require(range.start <= range.end)
    }

    private fun lineLength(line: Int): Int = lineEndOffset(line) - lineStarts[line]

    private fun lineEndOffset(line: Int): Int {
        val start = lineStarts[line]
        var index = start
        while (index < cachedText.length && cachedText[index] != '\n' && cachedText[index] != '\r') {
            index += 1
        }
        return index
    }

    private fun findLineForOffset(offset: Int): Int {
        var low = 0
        var high = lineStarts.lastIndex
        while (low <= high) {
            val mid = (low + high).ushr(1)
            val midValue = lineStarts[mid]
            when {
                midValue < offset -> low = mid + 1
                midValue > offset -> high = mid - 1
                else -> return mid
            }
        }
        return high.coerceAtLeast(0)
    }

    internal fun debugPieces(): List<DocumentPiece> = pieces.toList()

    internal fun readPieceText(piece: DocumentPiece): String =
        when (piece.bufferKind) {
            BufferKind.Original -> originalBuffer.substring(piece.start, piece.start + piece.length)
            BufferKind.Add -> addBuffer.substring(piece.start, piece.start + piece.length)
        }

    private fun mergeAdjacentPieces(source: List<DocumentPiece>): MutableList<DocumentPiece> {
        if (source.isEmpty()) {
            return mutableListOf()
        }
        val result = mutableListOf(source.first())
        source.drop(1).forEach { piece ->
            val lastPiece = result.last()
            if (
                lastPiece.bufferKind == piece.bufferKind &&
                lastPiece.start + lastPiece.length == piece.start
            ) {
                result[result.lastIndex] = lastPiece.copy(length = lastPiece.length + piece.length)
            } else if (piece.length > 0) {
                result += piece
            }
        }
        return result
    }

    private fun computeLineStarts(text: String): IntArray {
        val starts = ArrayList<Int>()
        starts += 0
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '\r' -> {
                    if (index + 1 < text.length && text[index + 1] == '\n') {
                        index += 1
                    }
                    if (index + 1 <= text.length) {
                        starts += index + 1
                    }
                }

                '\n' -> {
                    if (index + 1 <= text.length) {
                        starts += index + 1
                    }
                }
            }
            index += 1
        }
        if (starts.isEmpty()) {
            starts += 0
        }
        return starts.toIntArray()
    }
}

private operator fun EditorCoreTextPosition.compareTo(other: EditorCoreTextPosition): Int = when {
    line != other.line -> line.compareTo(other.line)
    else -> column.compareTo(other.column)
}
