package com.shadowphoto.container

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

data class PngChunk(
    val type: String,
    val data: ByteArray,
)

/**
 * Lee y escribe PNG lo bastante como para insertar el bloque privado `shPd`
 * sin tocar IHDR ni IDAT. La portada sigue siendo un PNG convencional.
 */
object PngChunkParser {
    val SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    const val SHP_CHUNK = "shPd"

    fun isPng(bytes: ByteArray): Boolean {
        if (bytes.size < SIGNATURE.size + 12) return false
        return bytes.copyOfRange(0, SIGNATURE.size).contentEquals(SIGNATURE)
    }

    fun parse(png: ByteArray): List<PngChunk> {
        if (!isPng(png)) throw ContainerFormatException("no es un PNG")
        val chunks = mutableListOf<PngChunk>()
        var offset = SIGNATURE.size
        while (offset + 12 <= png.size) {
            val length = readInt(png, offset)
            if (length < 0 || offset + 12L + length > png.size) {
                throw ContainerFormatException("chunk PNG truncado")
            }
            val type = png.decodeAscii(offset + 4, 4)
            if (type.length != 4 || type.any { it.code !in 65..90 && it.code !in 97..122 }) {
                throw ContainerFormatException("tipo de chunk inválido")
            }
            val dataStart = offset + 8
            val data = png.copyOfRange(dataStart, dataStart + length)
            val crcRead = readInt(png, dataStart + length)
            val crc = CRC32()
            crc.update(png, offset + 4, 4 + length)
            if (crc.value.toInt() != crcRead) {
                throw ContainerFormatException("CRC inválido en $type")
            }
            chunks += PngChunk(type, data)
            offset += 12 + length
            if (type == "IEND") break
        }
        if (chunks.isEmpty() || chunks.last().type != "IEND") {
            throw ContainerFormatException("PNG sin IEND")
        }
        return chunks
    }

    fun insertBeforeIend(png: ByteArray, type: String, data: ByteArray): ByteArray {
        val chunks = parse(png).filterNot { it.type == type }
        val pairs = ArrayList<Pair<String, ByteArray>>(chunks.size + 1)
        for (chunk in chunks) {
            if (chunk.type == "IEND") {
                pairs += type to data
            }
            pairs += chunk.type to chunk.data
        }
        return assemble(pairs)
    }

    fun replaceChunk(png: ByteArray, type: String, data: ByteArray): ByteArray =
        insertBeforeIend(png, type, data)

    fun strip(png: ByteArray, types: Set<String>): ByteArray {
        val chunks = parse(png).filter { it.type !in types }
        return assemble(chunks.map { it.type to it.data })
    }

    fun chunkData(png: ByteArray, type: String): ByteArray? =
        parse(png).lastOrNull { it.type == type }?.data

    fun encodeRgb(width: Int, height: Int, pixel: (x: Int, y: Int) -> Int): ByteArray {
        require(width > 0 && height > 0)
        val raw = ByteArray(height * (1 + width * 3))
        var cursor = 0
        for (y in 0 until height) {
            raw[cursor++] = 0
            for (x in 0 until width) {
                val color = pixel(x, y)
                raw[cursor++] = ((color ushr 16) and 0xFF).toByte()
                raw[cursor++] = ((color ushr 8) and 0xFF).toByte()
                raw[cursor++] = (color and 0xFF).toByte()
            }
        }
        val ihdr = ByteArray(13)
        writeInt(ihdr, 0, width)
        writeInt(ihdr, 4, height)
        ihdr[8] = 8
        ihdr[9] = 2
        return assemble(
            listOf(
                "IHDR" to ihdr,
                "IDAT" to zlib(raw),
                "IEND" to ByteArray(0),
            ),
        )
    }

    private fun assemble(chunks: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(SIGNATURE)
        for ((type, data) in chunks) {
            out.write(encodeChunk(type, data))
        }
        return out.toByteArray()
    }

    private fun encodeChunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        require(typeBytes.size == 4)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        val out = ByteArray(12 + data.size)
        writeInt(out, 0, data.size)
        typeBytes.copyInto(out, destinationOffset = 4)
        data.copyInto(out, destinationOffset = 8)
        writeInt(out, 8 + data.size, crc.value.toInt())
        return out
    }

    private fun zlib(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_SPEED)
        deflater.setInput(data)
        deflater.finish()
        val buffer = ByteArray(8192)
        val out = ByteArrayOutputStream()
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            if (count == 0 && deflater.finished()) break
            out.write(buffer, 0, count)
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun writeInt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    private fun ByteArray.decodeAscii(offset: Int, length: Int): String =
        String(this, offset, length, Charsets.US_ASCII)
}

class ContainerFormatException(message: String) : Exception(message)
