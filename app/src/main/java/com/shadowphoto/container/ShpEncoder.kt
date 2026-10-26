package com.shadowphoto.container

import com.shadowphoto.crypto.SealedBlob
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Inserta el bloque cifrado en un PNG de portada y genera el respaldo .shp.
 * El .shp no es una imagen: evita que otra app recomprima el contenedor.
 */
class ShpEncoder {
    fun embed(coverPng: ByteArray, blob: SealedBlob): ByteArray {
        if (!PngChunkParser.isPng(coverPng)) {
            throw ContainerFormatException("la portada tiene que ser un PNG")
        }
        val payload = ShpPayload.encode(blob)
        return PngChunkParser.insertBeforeIend(coverPng, PngChunkParser.SHP_CHUNK, payload)
    }

    fun writeBackup(protectedPng: ByteArray): ByteArray {
        if (!PngChunkParser.isPng(protectedPng)) {
            throw ContainerFormatException("el respaldo necesita el PNG protegido")
        }
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.write(BACKUP_MAGIC)
            data.writeByte(BACKUP_VERSION)
            data.write(byteArrayOf(0, 0, 0))
            data.writeInt(protectedPng.size)
            data.write(protectedPng)
        }
        return out.toByteArray()
    }

    companion object {
        val BACKUP_MAGIC = "SHPB".toByteArray(Charsets.US_ASCII)
        const val BACKUP_VERSION = 1
    }
}
