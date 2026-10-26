package com.shadowphoto.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.shadowphoto.container.CoverPng
import com.shadowphoto.container.PngChunkParser
import java.io.ByteArrayOutputStream

private const val MAX_IMAGE_BYTES = 48 * 1024 * 1024

fun decodePreview(bytes: ByteArray, maxEdge: Int): Bitmap? {
    if (bytes.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxEdge || bounds.outHeight / sample > maxEdge) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

fun decodeFilePreview(path: String, maxEdge: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxEdge || bounds.outHeight / sample > maxEdge) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, options)
}

/**
 * Convierte la foto elegida como portada en PNG y quita metadatos de texto/EXIF.
 * La fotografía privada no pasa por aquí: se cifra con sus bytes originales.
 */
fun toCoverPng(bytes: ByteArray): ByteArray {
    if (bytes.size > MAX_IMAGE_BYTES) {
        error("La imagen supera el tamaño admitido")
    }
    if (PngChunkParser.isPng(bytes)) {
        return PngChunkParser.strip(bytes, METADATA_CHUNKS)
    }
    val decoded = decodePreview(bytes, MAX_COVER_EDGE) ?: error("No se pudo leer la portada")
    return try {
        val output = ByteArrayOutputStream()
        if (!decoded.compress(Bitmap.CompressFormat.PNG, 100, output)) {
            error("No se pudo preparar la portada")
        }
        output.toByteArray()
    } finally {
        decoded.recycle()
    }
}

fun neutralCoverPng(): ByteArray = CoverPng.neutral(960, 540)

fun readBounded(input: java.io.InputStream, max: Int = MAX_IMAGE_BYTES): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > max) error("La imagen supera el tamaño admitido")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private const val MAX_COVER_EDGE = 1920

private val METADATA_CHUNKS = setOf("eXIf", "tEXt", "iTXt", "zTXt", "tIME", "tEXt")
