package com.shadowphoto.container

import com.shadowphoto.crypto.SealedBlob
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.EOFException

class ShpDecoder {
    fun readProtectedPng(container: ByteArray): ByteArray {
        if (container.size >= 12 && container.copyOfRange(0, 4).contentEquals(ShpEncoder.BACKUP_MAGIC)) {
            return unwrapBackup(container)
        }
        if (PngChunkParser.isPng(container)) return container
        throw ContainerFormatException("este archivo no es un contenedor ShadowPhoto")
    }

    fun readBlob(container: ByteArray): SealedBlob {
        val png = readProtectedPng(container)
        val payload = PngChunkParser.chunkData(png, PngChunkParser.SHP_CHUNK)
            ?: throw ContainerFormatException("el PNG no contiene el bloque privado shPd")
        return ShpPayload.decode(payload)
    }

    private fun unwrapBackup(container: ByteArray): ByteArray {
        try {
            val input = DataInputStream(ByteArrayInputStream(container))
            val magic = ByteArray(4)
            input.readFully(magic)
            if (!magic.contentEquals(ShpEncoder.BACKUP_MAGIC)) {
                throw ContainerFormatException("respaldo .shp inválido")
            }
            val version = input.readUnsignedByte()
            if (version != ShpEncoder.BACKUP_VERSION) {
                throw ContainerFormatException("versión de respaldo no soportada")
            }
            input.readFully(ByteArray(3))
            val length = input.readInt()
            if (length <= 0 || length > container.size) {
                throw ContainerFormatException("respaldo .shp truncado")
            }
            val png = ByteArray(length)
            input.readFully(png)
            if (!PngChunkParser.isPng(png)) {
                throw ContainerFormatException("el respaldo no contiene un PNG")
            }
            return png
        } catch (e: ContainerFormatException) {
            throw e
        } catch (e: EOFException) {
            throw ContainerFormatException("respaldo .shp truncado")
        }
    }
}
