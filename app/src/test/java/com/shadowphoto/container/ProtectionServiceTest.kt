package com.shadowphoto.container

import com.shadowphoto.domain.ProtectionService
import com.shadowphoto.domain.UnlockOutcome
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.Inflater

class ProtectionServiceTest {
    @Test
    fun protectsAPhotoBehindAnUntouchedCoverAndRecoversTheSameBytes() {
        val service = ProtectionService()
        val original = ByteArray(4096) { index -> (index * 31 + 7).toByte() }
        original[0] = 0xFF.toByte()
        original[1] = 0xD8.toByte()
        val cover = CoverPng.solid(12, 8, 0x336699)
        val password = "frase-portatil-1".toCharArray()

        val packed = service.protect(original, cover, password)

        val decoded = inflateCover(packed.protectedPng)
        assertEquals(12, decoded.width)
        assertEquals(8, decoded.height)
        assertEquals(0x336699, decoded.pixel(1, 1))
        assertEquals(0x336699, decoded.pixel(0, 0))

        val idatBefore = PngChunkParser.parse(cover).first { it.type == "IDAT" }.data
        val idatAfter = PngChunkParser.parse(packed.protectedPng).first { it.type == "IDAT" }.data
        assertArrayEquals(idatBefore, idatAfter)
        assertFalse(contains(packed.protectedPng, original.copyOfRange(0, 64)))

        when (val opened = service.unlock(packed.protectedPng, "frase-portatil-1".toCharArray())) {
            is UnlockOutcome.Success -> assertArrayEquals(original, opened.bytes)
            else -> error("el PNG protegido no devolvió la foto original")
        }
        when (val opened = service.unlock(packed.shpBackup, "frase-portatil-1".toCharArray())) {
            is UnlockOutcome.Success -> assertArrayEquals(original, opened.bytes)
            else -> error("el respaldo .shp no devolvió la foto original")
        }

        assertTrue(
            service.unlock(packed.protectedPng, "otra-clave-distinta".toCharArray())
                is UnlockOutcome.AuthenticationFailed,
        )

        val payload = PngChunkParser.chunkData(packed.protectedPng, PngChunkParser.SHP_CHUNK)!!
        val flippedCipher = payload.copyOf()
        flippedCipher[flippedCipher.lastIndex] =
            (flippedCipher[flippedCipher.lastIndex].toInt() xor 0xFF).toByte()
        val tampered = PngChunkParser.replaceChunk(
            packed.protectedPng,
            PngChunkParser.SHP_CHUNK,
            flippedCipher,
        )
        assertTrue(service.unlock(tampered, password) is UnlockOutcome.AuthenticationFailed)

        val flippedFlags = payload.copyOf()
        flippedFlags[5] = (flippedFlags[5].toInt() xor 0x01).toByte()
        val retagged = PngChunkParser.replaceChunk(
            packed.protectedPng,
            PngChunkParser.SHP_CHUNK,
            flippedFlags,
        )
        assertTrue(service.unlock(retagged, password) is UnlockOutcome.AuthenticationFailed)

        val damaged = packed.protectedPng.copyOf()
        val damageAt = damaged.size / 2
        damaged[damageAt] = (damaged[damageAt].toInt() xor 0x01).toByte()
        val damagedOutcome = service.unlock(damaged, password)
        assertTrue(
            damagedOutcome is UnlockOutcome.InvalidContainer ||
                damagedOutcome is UnlockOutcome.AuthenticationFailed,
        )
        assertFalse(damagedOutcome is UnlockOutcome.Success)
    }

    /**
     * Infla el IDAT como haría un visor PNG: el chunk shPd no entra en los píxeles.
     * El classpath de los unit tests de Android no incluye javax.imageio.
     */
    private fun inflateCover(png: ByteArray): RgbImage {
        val chunks = PngChunkParser.parse(png)
        val ihdr = chunks.first { it.type == "IHDR" }.data
        val width = readInt(ihdr, 0)
        val height = readInt(ihdr, 4)
        require(ihdr[8].toInt() == 8 && ihdr[9].toInt() == 2) { "se esperaba RGB de 8 bits" }
        val compressed = chunks.filter { it.type == "IDAT" }
            .fold(ByteArray(0)) { acc, chunk -> acc + chunk.data }
        val inflater = Inflater()
        inflater.setInput(compressed)
        val raw = ByteArray(height * (1 + width * 3))
        val produced = inflater.inflate(raw)
        inflater.end()
        require(produced == raw.size) { "IDAT incompleto" }
        return RgbImage(width, height, raw)
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private class RgbImage(val width: Int, val height: Int, private val raw: ByteArray) {
        fun pixel(x: Int, y: Int): Int {
            val stride = 1 + width * 3
            val index = y * stride + 1 + x * 3
            require(raw[y * stride].toInt() == 0) { "filtro PNG distinto de None" }
            val r = raw[index].toInt() and 0xFF
            val g = raw[index + 1].toInt() and 0xFF
            val b = raw[index + 2].toInt() and 0xFF
            return (r shl 16) or (g shl 8) or b
        }
    }

    private fun contains(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > haystack.size) return false
        outer@ for (start in 0..haystack.size - needle.size) {
            for (index in needle.indices) {
                if (haystack[start + index] != needle[index]) continue@outer
            }
            return true
        }
        return false
    }
}
