package com.qiplat.compose.sweeteditor.protocol

class BinaryReader(
    private val data: ByteArray,
) {
    var position: Int = 0
        private set

    val remaining: Int
        get() = data.size - position

    fun canRead(byteCount: Int): Boolean = remaining >= byteCount

    fun readInt(): Int {
        requireRemaining(4)
        val b0 = data[position++].toInt() and 0xFF
        val b1 = data[position++].toInt() and 0xFF
        val b2 = data[position++].toInt() and 0xFF
        val b3 = data[position++].toInt() and 0xFF
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    fun readLong(): Long {
        requireRemaining(8)
        val b0 = data[position++].toLong() and 0xFF
        val b1 = data[position++].toLong() and 0xFF
        val b2 = data[position++].toLong() and 0xFF
        val b3 = data[position++].toLong() and 0xFF
        val b4 = data[position++].toLong() and 0xFF
        val b5 = data[position++].toLong() and 0xFF
        val b6 = data[position++].toLong() and 0xFF
        val b7 = data[position++].toLong() and 0xFF
        return b0 or
            (b1 shl 8) or
            (b2 shl 16) or
            (b3 shl 24) or
            (b4 shl 32) or
            (b5 shl 40) or
            (b6 shl 48) or
            (b7 shl 56)
    }

    fun readFloat(): Float = Float.fromBits(readInt())

    fun readBooleanAsInt(): Boolean = readInt() != 0

    fun readBytes(byteCount: Int): ByteArray {
        requireRemaining(byteCount)
        val result = data.copyOfRange(position, position + byteCount)
        position += byteCount
        return result
    }

    fun readUtf8(): String {
        val size = readInt()
        if (size <= 0) {
            return ""
        }
        return readBytes(size).decodeToString()
    }

    private fun requireRemaining(byteCount: Int) {
        require(canRead(byteCount)) {
            "Not enough bytes to read: need=$byteCount remaining=$remaining"
        }
    }
}
