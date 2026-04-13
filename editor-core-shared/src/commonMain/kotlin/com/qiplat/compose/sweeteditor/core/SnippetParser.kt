package com.qiplat.compose.sweeteditor.core

internal data class ParsedSnippet(
    val text: String,
    val groups: List<EditorCoreTabStopGroup>,
    val finalCursorOffset: Int?,
)

internal object SnippetParser {
    fun parse(template: String): ParsedSnippet {
        val text = StringBuilder(template.length)
        val groups = linkedMapOf<Int, MutableList<Pair<Int, Int>>>()
        val defaultTexts = linkedMapOf<Int, String?>()
        var finalCursorOffset: Int? = null
        var index = 0
        while (index < template.length) {
            val char = template[index]
            when {
                char == '\\' && index + 1 < template.length -> {
                    text.append(template[index + 1])
                    index += 2
                }
                char == '$' && index + 1 < template.length && template[index + 1].isDigit() -> {
                    val token = readNumericToken(
                        template = template,
                        startIndex = index + 1,
                    )
                    appendTabStop(
                        index = token.index,
                        explicitDefaultText = null,
                        text = text,
                        groups = groups,
                        defaultTexts = defaultTexts,
                        finalCursorOffsetSetter = { finalCursorOffset = it },
                    )
                    index = token.nextIndex
                }
                char == '$' && index + 1 < template.length && template[index + 1] == '{' -> {
                    val token = readBracedToken(
                        template = template,
                        startIndex = index + 2,
                    )
                    if (token != null) {
                        appendTabStop(
                            index = token.index,
                            explicitDefaultText = token.defaultText,
                            text = text,
                            groups = groups,
                            defaultTexts = defaultTexts,
                            finalCursorOffsetSetter = { finalCursorOffset = it },
                        )
                        index = token.nextIndex
                    } else {
                        text.append(char)
                        index += 1
                    }
                }
                else -> {
                    text.append(char)
                    index += 1
                }
            }
        }
        return ParsedSnippet(
            text = text.toString(),
            groups = groups.entries
                .sortedBy { (groupIndex, _) -> groupIndex }
                .map { (groupIndex, ranges) ->
                    EditorCoreTabStopGroup(
                        index = groupIndex,
                        ranges = ranges.map { range ->
                            EditorCoreTextRange(
                                start = EditorCoreTextPosition(0, range.first),
                                end = EditorCoreTextPosition(0, range.second),
                            )
                        },
                        defaultText = defaultTexts[groupIndex],
                    )
                },
            finalCursorOffset = finalCursorOffset,
        )
    }

    private fun appendTabStop(
        index: Int,
        explicitDefaultText: String?,
        text: StringBuilder,
        groups: MutableMap<Int, MutableList<Pair<Int, Int>>>,
        defaultTexts: MutableMap<Int, String?>,
        finalCursorOffsetSetter: (Int) -> Unit,
    ) {
        val resolvedDefaultText = explicitDefaultText ?: defaultTexts[index].orEmpty()
        val startOffset = text.length
        text.append(resolvedDefaultText)
        val endOffset = text.length
        if (index == 0) {
            finalCursorOffsetSetter(startOffset)
            return
        }
        groups.getOrPut(index) { mutableListOf() } += startOffset to endOffset
        if (explicitDefaultText != null || index !in defaultTexts) {
            defaultTexts[index] = explicitDefaultText
        }
    }

    private fun readNumericToken(
        template: String,
        startIndex: Int,
    ): NumericToken {
        var cursor = startIndex
        while (cursor < template.length && template[cursor].isDigit()) {
            cursor += 1
        }
        return NumericToken(
            index = template.substring(startIndex, cursor).toInt(),
            nextIndex = cursor,
        )
    }

    private fun readBracedToken(
        template: String,
        startIndex: Int,
    ): BracedToken? {
        var cursor = startIndex
        while (cursor < template.length && template[cursor].isDigit()) {
            cursor += 1
        }
        if (cursor == startIndex) {
            return null
        }
        val index = template.substring(startIndex, cursor).toInt()
        return when {
            cursor < template.length && template[cursor] == '}' -> BracedToken(
                index = index,
                defaultText = null,
                nextIndex = cursor + 1,
            )
            cursor < template.length && template[cursor] == ':' -> {
                val defaultTextBuilder = StringBuilder()
                cursor += 1
                while (cursor < template.length) {
                    val char = template[cursor]
                    when {
                        char == '\\' && cursor + 1 < template.length -> {
                            defaultTextBuilder.append(template[cursor + 1])
                            cursor += 2
                        }
                        char == '}' -> {
                            return BracedToken(
                                index = index,
                                defaultText = defaultTextBuilder.toString(),
                                nextIndex = cursor + 1,
                            )
                        }
                        else -> {
                            defaultTextBuilder.append(char)
                            cursor += 1
                        }
                    }
                }
                null
            }
            else -> null
        }
    }

    private data class NumericToken(
        val index: Int,
        val nextIndex: Int,
    )

    private data class BracedToken(
        val index: Int,
        val defaultText: String?,
        val nextIndex: Int,
    )
}
