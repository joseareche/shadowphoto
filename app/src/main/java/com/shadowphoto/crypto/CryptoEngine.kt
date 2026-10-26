package com.shadowphoto.crypto

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrado auditado: AES-256-GCM para el contenido y para envolver la DEK.
 * La autenticidad de versión, flags y salt viaja como AAD. Un byte alterado
 * hace fallar la etiqueta y no se devuelve imagen parcial.
 */
class CryptoEngine(
    private val kdf: KeyDerivation = KeyDerivation(),
    private val random: SecureRandom = SecureRandom(),
) {
    fun seal(plaintext: ByteArray, password: CharArray): SealedBlob {
        require(plaintext.isNotEmpty()) { "la fotografía original está vacía" }
        require(password.size >= MIN_PASSWORD) { "la clave debe tener al menos $MIN_PASSWORD caracteres" }
        val salt = randomBytes(SALT_LEN)
        val dek = randomBytes(DEK_LEN)
        val kek = kdf.derive(password, salt)
        val wrapNonce = randomBytes(NONCE_LEN)
        val dataNonce = randomBytes(NONCE_LEN)
        try {
            val aad = aad(VERSION, FLAGS_NONE, salt)
            val ciphertext = aesGcm(Cipher.ENCRYPT_MODE, dek, dataNonce, plaintext, aad)
            val wrappedDek = aesGcm(Cipher.ENCRYPT_MODE, kek, wrapNonce, dek, aad)
            return SealedBlob(
                version = VERSION,
                flags = FLAGS_NONE,
                salt = salt,
                wrapNonce = wrapNonce,
                dataNonce = dataNonce,
                wrappedDek = wrappedDek,
                ciphertext = ciphertext,
            )
        } finally {
            dek.wipe()
            kek.wipe()
        }
    }

    /**
     * @return bytes originales, o null si la clave es incorrecta o el GCM no autentica.
     * Un contenedor mal formado lanza [CryptoFormatException] antes de derivar la clave
     * solo cuando ni siquiera se puede interpretar el sobre.
     */
    fun open(blob: SealedBlob, password: CharArray): ByteArray? {
        if (blob.version != VERSION) return null
        if (password.isEmpty()) return null
        val kek = kdf.derive(password, blob.salt)
        var dek: ByteArray? = null
        try {
            val aad = aad(blob.version, blob.flags, blob.salt)
            dek = try {
                aesGcm(Cipher.DECRYPT_MODE, kek, blob.wrapNonce, blob.wrappedDek, aad)
            } catch (e: AEADBadTagException) {
                return null
            } catch (e: GeneralSecurityException) {
                return null
            }
            if (dek.size != DEK_LEN) return null
            return try {
                aesGcm(Cipher.DECRYPT_MODE, dek, blob.dataNonce, blob.ciphertext, aad)
            } catch (e: AEADBadTagException) {
                null
            } catch (e: GeneralSecurityException) {
                null
            }
        } finally {
            dek?.wipe()
            kek.wipe()
        }
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    private fun aad(version: Int, flags: Int, salt: ByteArray): ByteArray {
        val out = ByteArray(AAD_PREFIX.size + 2 + salt.size)
        AAD_PREFIX.copyInto(out)
        out[AAD_PREFIX.size] = version.toByte()
        out[AAD_PREFIX.size + 1] = flags.toByte()
        salt.copyInto(out, destinationOffset = AAD_PREFIX.size + 2)
        return out
    }

    private fun aesGcm(
        mode: Int,
        key: ByteArray,
        nonce: ByteArray,
        input: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(input)
    }

    companion object {
        const val VERSION = 1
        const val FLAGS_NONE = 0
        const val SALT_LEN = 16
        const val NONCE_LEN = 12
        const val DEK_LEN = 32
        const val MIN_PASSWORD = 8
        private val AAD_PREFIX = "SHP-1".toByteArray(Charsets.US_ASCII)
    }
}

class CryptoFormatException(message: String) : Exception(message)
