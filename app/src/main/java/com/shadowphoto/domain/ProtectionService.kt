package com.shadowphoto.domain

import com.shadowphoto.container.ContainerFormatException
import com.shadowphoto.container.ShpDecoder
import com.shadowphoto.container.ShpEncoder
import com.shadowphoto.crypto.CryptoEngine
import com.shadowphoto.crypto.wipe
import java.security.MessageDigest

/**
 * Orquesta el motor: cifra la fotografía original byte a byte, la mete en la
 * portada y comprueba que se puede recuperar antes de devolver el archivo.
 */
class ProtectionService(
    private val crypto: CryptoEngine = CryptoEngine(),
    private val encoder: ShpEncoder = ShpEncoder(),
    private val decoder: ShpDecoder = ShpDecoder(),
) {
    fun protect(original: ByteArray, coverPng: ByteArray, password: CharArray): ProtectedPackage {
        if (original.isEmpty()) {
            throw ProtectionException("No hay fotografía para proteger.")
        }
        if (password.size < CryptoEngine.MIN_PASSWORD) {
            throw ProtectionException("La clave necesita al menos ${CryptoEngine.MIN_PASSWORD} caracteres.")
        }
        val blob = crypto.seal(original, password)
        val protectedPng = encoder.embed(coverPng, blob)
        when (val check = unlock(protectedPng, password)) {
            is UnlockOutcome.Success -> {
                val matches = check.bytes.contentEquals(original)
                check.bytes.wipe()
                if (!matches) {
                    throw ProtectionException("La verificación del contenedor falló. No se guardó nada.")
                }
            }
            else -> throw ProtectionException("La verificación del contenedor falló. No se guardó nada.")
        }
        val backup = encoder.writeBackup(protectedPng)
        return ProtectedPackage(
            protectedPng = protectedPng,
            shpBackup = backup,
            contentHash = sha256Hex(protectedPng),
            originalSize = original.size.toLong(),
        )
    }

    fun unlock(container: ByteArray, password: CharArray): UnlockOutcome {
        val blob = try {
            decoder.readBlob(container)
        } catch (e: ContainerFormatException) {
            return UnlockOutcome.InvalidContainer(e.message ?: "contenedor inválido")
        }
        val opened = crypto.open(blob, password) ?: return UnlockOutcome.AuthenticationFailed
        return UnlockOutcome.Success(opened)
    }

    companion object {
        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val builder = StringBuilder(digest.size * 2)
            for (byte in digest) {
                builder.append("%02x".format(byte))
            }
            return builder.toString()
        }
    }
}

class ProtectedPackage(
    val protectedPng: ByteArray,
    val shpBackup: ByteArray,
    val contentHash: String,
    val originalSize: Long,
)

sealed class UnlockOutcome {
    class Success(val bytes: ByteArray) : UnlockOutcome()
    data object AuthenticationFailed : UnlockOutcome()
    data class InvalidContainer(val reason: String) : UnlockOutcome()
}

class ProtectionException(message: String) : Exception(message)
