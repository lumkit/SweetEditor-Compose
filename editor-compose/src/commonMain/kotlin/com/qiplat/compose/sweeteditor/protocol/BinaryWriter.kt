package com.qiplat.compose.sweeteditor.protocol

import kotlin.math.max

class BinaryWriter(
    initialCapacity: Int = 128,
) {
    private var buffer: ByteArray = ByteArray(max(initialCapacity, 32))
    private var position: Int = 0

    fun writeInt(value: Int) {
        ensureCapacity(4)
        buffer[position++] = value.toByte()
        buffer[position++] = (value ushr 8).toByte()
        buffer[position++] = (value ushr 16).toByte()
        buffer[position++] = (value ushr 24).toByte()
    }

    fun writeLong(value: Long) {
        ensureCapacity(8)
        buffer[position++] = value.toByte()
        buffer[position++] = (value ushr 8).toByte()
        buffer[position++] = (value ushr 16).toByte()
        buffer[position++] = (value ushr 24).toByte()
        buffer[position++] = (value ushr 32).toByte()
        buffer[position++] = (value ushr 40).toByte()
        buffer[position++] = (value ushr 48).toByte()
        buffer[position++] = (value ushr 56).toByte()
    }

    fun writeFloat(value: Float) {
        writeInt(value.toBits())
    }

    fun writeBooleanAsInt(value: Boolean) {
        writeInt(if (value) 1 else 0)
    }

    fun writeBytes(value: ByteArray) {
        ensureCapacity(value.size)
        value.copyInto(
            destination = buffer,
            destinationOffset = position,
            startIndex = 0,
            endIndex = value.size,
        )
        position += value.size
    }

    fun writeUtf8(value: String) {
        val bytes = value.encodeToByteArray()
        writeInt(bytes.size)
        writeBytes(bytes)
    }

    fun toByteArray(): ByteArray = buffer.copyOf(position)

    private fun ensureCapacity(additional: Int) {
        val required = position + additional
        if (required <= buffer.size) {
            return
        }
        var newSize = buffer.size
        while (newSize < required) {
            newSize *= 2
        }
        buffer = buffer.copyOf(newSize)
    }
}
