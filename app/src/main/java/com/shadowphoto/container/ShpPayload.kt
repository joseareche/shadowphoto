package com.shadowphoto.container

import com.shadowphoto.crypto.SealedBlob
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

/**
 * Sobre binario que vive dentro del chunk `shPd` (y, envuelto, en el .shp).
 *
 * SHP1 | version u8 | flags u8 | salt 16 | wrapNonce 12 | dataNonce 12 |
 * wrappedDekLen u16 | wrappedDek | cipherLen u32 | ciphertext
 */
object ShpPayload {
    private val MAGIC = "SHP1".toByteArray(Charsets.US_ASCII)
    private const val MAX_WRAPPED = 512
    private const val MAX_CIPHER = 64 * 1024 * 1024

    fun encode(blob: SealedBlob): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.write(MAGIC)
            data.writeByte(blob.version)
            data.writeByte(blob.flags)
            data.write(blob.salt)
            data.write(blob.wrapNonce)
            data.write(blob.dataNonce)
            data.writeShort(blob.wrappedDek.size)
            data.write(blob.wrappedDek)
            data.writeInt(blob.ciphertext.size)
            data.write(blob.ciphertext)
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): SealedBlob {
        try {
            val input = DataInputStream(ByteArrayInputStream(bytes))
            val magic = ByteArray(4)
            input.readFully(magic)
            if (!magic.contentEquals(MAGIC)) {
                throw ContainerFormatException("el bloque privado no es SHP-1")
            }
            val version = input.readUnsignedByte()
            if (version != 1) {
                throw ContainerFormatException("versión SHP-1 no soportada")
            }
            val flags = input.readUnsignedByte()
            val salt = ByteArray(16).also { input.readFully(it) }
            val wrapNonce = ByteArray(12).also { input.readFully(it) }
            val dataNonce = ByteArray(12).also { input.readFully(it) }
            val wrappedLen = input.readUnsignedShort()
            if (wrappedLen !in 32..MAX_WRAPPED) {
                throw ContainerFormatException("sobre de clave inválido")
            }
            val wrapped = ByteArray(wrappedLen).also { input.readFully(it) }
            val cipherLen = input.readInt()
            if (cipherLen !in 16..MAX_CIPHER) {
                throw ContainerFormatException("tamaño de datos cifrados inválido")
            }
            val cipher = ByteArray(cipherLen).also { input.readFully(it) }
            if (input.available() != 0) {
                throw ContainerFormatException("bytes sobrantes en el bloque privado")
            }
            return SealedBlob(version, flags, salt, wrapNonce, dataNonce, wrapped, cipher)
        } catch (e: ContainerFormatException) {
            throw e
        } catch (e: EOFException) {
            throw ContainerFormatException("bloque privado truncado")
        }
    }
}
