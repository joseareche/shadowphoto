package com.shadowphoto.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CryptoEngineTest {
    @Test
    fun argon2idUsesTheDocumentParametersAndAStableVector() {
        assertEquals(19 * 1024, Argon2Params.PRODUCTION.memoryKiB)
        assertEquals(2, Argon2Params.PRODUCTION.iterations)
        assertEquals(1, Argon2Params.PRODUCTION.parallelism)

        val salt = ByteArray(16) { it.toByte() }
        val key = KeyDerivation().derive("frase-de-prueba".toCharArray(), salt)
        assertEquals(
            "6a4fd1ff150f5d2b144a7c8cb8096a0c0f47cf65317cca72e72eab0cd7daf735",
            key.toHex(),
        )
    }

    @Test
    fun aesGcmRoundTripRejectsWrongPasswordAndTamperedCiphertext() {
        val engine = CryptoEngine()
        val plain = "fotografia-privada-byte-a-byte".toByteArray(Charsets.UTF_8)
        val password = "clave-correcta".toCharArray()
        val blob = engine.seal(plain, password)

        assertArrayEquals(plain, engine.open(blob, "clave-correcta".toCharArray()))
        assertNull(engine.open(blob, "clave-erronea".toCharArray()))

        val flipped = blob.ciphertext.copyOf()
        flipped[flipped.lastIndex] = (flipped[flipped.lastIndex].toInt() xor 0x01).toByte()
        val tampered = SealedBlob(
            version = blob.version,
            flags = blob.flags,
            salt = blob.salt,
            wrapNonce = blob.wrapNonce,
            dataNonce = blob.dataNonce,
            wrappedDek = blob.wrappedDek,
            ciphertext = flipped,
        )
        assertNull(engine.open(tampered, password))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
