/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package kotlin.text

private inline class CodePointParser(val shouldThrowOnMalformed: Boolean) {
    /** Returns the negative [size] if [shouldThrowOnMalformed] is false, throws [IllegalArgumentException] otherwise. */
    private fun malformed(size: Int, index: Int): Int {
        if (shouldThrowOnMalformed) throw IllegalArgumentException("Malformed sequence starting at ${index - 1}")
        return -size
    }

    /**
     * Returns code point corresponding to UTF-16 surrogate pair,
     * where the first of the pair is the [high] and the second is in the [string] at the [index].
     * Returns zero if the pair is malformed and [shouldThrowOnMalformed] is false.
     *
     * @throws IllegalArgumentException if the pair is malformed and [shouldThrowOnMalformed] is true.
     */
    fun codePointFromSurrogate(string: String, high: Int, index: Int): Int {
        if (high !in 0xD800..0xDBFF || index == string.length) {
            return malformed(0, index)
        }
        val low = string[index].toInt()
        if (low !in 0xDC00..0xDFFF) {
            return malformed(0, index)
        }
        return 0x10000 + ((high and 0x3FF) shl 10) or (low and 0x3FF)
    }

    /**
     * Returns code point corresponding to UTF-8 sequence of two bytes,
     * where the first byte of the sequence is the [byte1] and the second byte is in the [bytes] array at the [index].
     * Returns zero if the sequence is malformed and [shouldThrowOnMalformed] is false.
     *
     * @throws IllegalArgumentException if the sequence of two bytes is malformed and [shouldThrowOnMalformed] is true.
     */
    fun codePointFrom2(bytes: ByteArray, byte1: Int, index: Int): Int {
        if (byte1 and 0x1E == 0 || index >= bytes.size) {
            return malformed(0, index)
        }
        val byte2 = bytes[index].toInt()
        if (byte2 and 0xC0 != 0x80) {
            return malformed(0, index)
        }
        return (byte1 shl 6) xor byte2 xor 0xF80
    }

    /**
     * Returns code point corresponding to UTF-8 sequence of three bytes,
     * where the first byte of the sequence is the [byte1] and the others are in the [bytes] array starting from the [index].
     * Returns a non-positive value indicating number of bytes from [bytes] included in malformed sequence
     * if the sequence is malformed and [shouldThrowOnMalformed] is false.
     *
     * @throws IllegalArgumentException if the sequence of two bytes is malformed and [shouldThrowOnMalformed] is true.
     */
    fun codePointFrom3(bytes: ByteArray, byte1: Int, index: Int): Int {
        if (index >= bytes.size) {
            return malformed(0, index)
        }

        val byte2 = bytes[index].toInt()
        if (byte1 and 0xF == 0) {
            if (byte2 and 0xE0 != 0xA0) {
                return malformed(0, index)
            }
        } else if (byte2 and 0xC0 != 0x80) {
            return malformed(0, index)
        }

        if (index + 1 == bytes.size) {
            return malformed(1, index)
        }
        val byte3 = bytes[index + 1].toInt()
        if (byte3 and 0xC0 != 0x80) {
            return malformed(1, index)
        }

        val code = (byte1 shl 12) xor (byte2 shl 6) xor byte3 xor -0x1E080
        if (code in 0xD800..0xDFFF) {
            return malformed(2, index)
        }
        return code
    }

    /**
     * Returns code point corresponding to UTF-8 sequence of four bytes,
     * where the first byte of the sequence is the [byte1] and the others are in the [bytes] array starting from the [index].
     * Returns a non-positive value indicating number of bytes from [bytes] included in malformed sequence
     * if the sequence is malformed and [shouldThrowOnMalformed] is false.
     *
     * @throws IllegalArgumentException if the sequence of two bytes is malformed and [shouldThrowOnMalformed] is true.
     */
    fun codePointFrom4(bytes: ByteArray, byte1: Int, index: Int): Int {
        if (index >= bytes.size) {
            malformed(0, index)
        }

        val byte2 = bytes[index].toInt()
        if (byte1 and 0xF == 0x0) {
            if (byte2 and 0xF0 == 0x80) {
                return malformed(0, index)
            }
        } else if (byte1 and 0xF == 0x4) {
            if (byte2 and 0xF0 != 0x80) {
                return malformed(0, index)
            }
        } else if (byte2 and 0xC0 != 0x80) {
            return malformed(0, index)
        }

        if (index + 1 == bytes.size) {
            return malformed(1, index)
        }
        val byte3 = bytes[index + 1].toInt()
        if (byte3 and 0xC0 != 0x80) {
            return malformed(1, index)
        }

        if (index + 2 == bytes.size) {
            return malformed(2, index)
        }
        val byte4 = bytes[index + 2].toInt()
        if (byte4 and 0xC0 != 0x80) {
            return malformed(2, index)
        }
        return (byte1 shl 18) xor (byte2 shl 12) xor (byte3 shl 6) xor byte4 xor 0x381F80
    }
}

internal class UTF8Coder {
    companion object {
        /**
         * Maximum number of bytes needed to encode a single char.
         *
         * Code points in `0..0x7F` are encoded in a single byte.
         * Code points in `0x80..0x7FF` are encoded in two bytes.
         * Code points in `0x800..0xD7FF` or in `0xE000..0xFFFF` are encoded in three bytes.
         * Surrogate code points in `0xD800..0xDFFF` are not Unicode scalar values, therefore aren't encoded.
         * Code points in `0x10000..0x10FFFF` are represented by a pair of surrogate `Char`s and are encoded in four bytes.
         */
        private const val MAX_BYTES_PER_CHAR = 3

        /**
         * The byte a malformed UTF-16 char sequence is replaced by.
         */
        private const val REPLACEMENT_BYTE: Byte = 0x3F

        /**
         * Encodes the [string] using UTF-8 and returns the resulting [ByteArray].
         *
         * @param string the string to encode.
         * @param startIndex the start offset (inclusive) of the substring to encode.
         * @param endIndex the end offset (exclusive) of the substring to encode.
         * @param throwOnInvalidSequence weather to throw on malformed char sequence or to replace by the [REPLACEMENT_BYTE].
         */
        fun encode(string: String, startIndex: Int, endIndex: Int, throwOnInvalidSequence: Boolean): ByteArray {
            require(startIndex >= 0 && endIndex <= string.length && startIndex <= endIndex)

            val bytes = ByteArray(string.length * MAX_BYTES_PER_CHAR)
            var byteIndex = 0
            var charIndex = startIndex
            val codePointParser = CodePointParser(throwOnInvalidSequence)

            while (charIndex < endIndex) {
                val code = string[charIndex++].toInt()
                when {
                    code < 0x80 ->
                        bytes[byteIndex++] = code.toByte()
                    code < 0x800 -> {
                        bytes[byteIndex++] = ((code shr 6) or 0xC0).toByte()
                        bytes[byteIndex++] = ((code and 0x3F) or 0x80).toByte()
                    }
                    code < 0xD800 || code > 0xE000 -> {
                        bytes[byteIndex++] = ((code shr 12) or 0xE0).toByte()
                        bytes[byteIndex++] = (((code shr 6) and 0x3F) or 0x80).toByte()
                        bytes[byteIndex++] = ((code and 0x3F) or 0x80).toByte()
                    }
                    else -> { // Surrogate char value
                        val codePoint = codePointParser.codePointFromSurrogate(string, code, charIndex)
                        if (codePoint <= 0) {
                            bytes[byteIndex++] = REPLACEMENT_BYTE
                        } else {
                            bytes[byteIndex++] = ((codePoint shr 18) or 0xF0).toByte()
                            bytes[byteIndex++] = (((codePoint shr 12) and 0x3F) or 0x80).toByte()
                            bytes[byteIndex++] = (((codePoint shr 6) and 0x3F) or 0x80).toByte()
                            bytes[byteIndex++] = ((codePoint and 0x3F) or 0x80).toByte()
                        }
                    }
                }
            }

            return if (bytes.size == byteIndex) bytes else bytes.copyOf(byteIndex)
        }

        /**
         * The character a malformed UTF-8 byte sequence is replaced by.
         */
        private const val REPLACEMENT_CHAR = '\uFFFD'

        /**
         * Decodes the UTF-8 [bytes] array and returns the resulting [String].
         *
         * @param bytes the byte array to decode.
         * @param startIndex the start offset (inclusive) of the array to be decoded.
         * @param endIndex the end offset (exclusive) of the array to be encoded.
         * @param throwOnInvalidSequence weather to throw on malformed byte sequence or to replace by the [REPLACEMENT_CHAR].
         */
        fun decode(bytes: ByteArray, startIndex: Int, endIndex: Int, throwOnInvalidSequence: Boolean): String {
            require(startIndex >= 0 && endIndex <= bytes.size && startIndex <= endIndex)

            var byteIndex = startIndex
            val codePointParser = CodePointParser(throwOnInvalidSequence)
            val stringBuilder = StringBuilder()

            while (byteIndex < endIndex) {
                val byte = bytes[byteIndex++].toInt()
                when {
                    byte >= 0 ->
                        stringBuilder.append(byte.toChar())
                    byte shr 5 == -2 -> {
                        val code = codePointParser.codePointFrom2(bytes, byte, byteIndex)
                        if (code <= 0) {
                            stringBuilder.append(REPLACEMENT_CHAR)
                            byteIndex += -code
                        } else {
                            stringBuilder.append(code.toChar())
                            byteIndex += 1
                        }
                    }
                    byte shr 4 == -2 -> {
                        val code = codePointParser.codePointFrom3(bytes, byte, byteIndex)
                        if (code <= 0) {
                            stringBuilder.append(REPLACEMENT_CHAR)
                            byteIndex += -code
                        } else {
                            stringBuilder.append(code.toChar())
                            byteIndex += 2
                        }
                    }
                    byte shr 3 == -2 -> {
                        val code = codePointParser.codePointFrom4(bytes, byte, byteIndex)
                        if (code <= 0) {
                            stringBuilder.append(REPLACEMENT_CHAR)
                            byteIndex += -code
                        } else {
                            val high = (code - 0x10000) shr 10 or 0xD800
                            val low = (code and 0x3FF) or 0xDC00
                            stringBuilder.append(high.toChar())
                            stringBuilder.append(low.toChar())
                            byteIndex += 3
                        }
                    }
                    else ->
                        stringBuilder.append(REPLACEMENT_CHAR)
                }
            }

            return stringBuilder.toString()
        }
    }
}