package com.shadowphoto.data.files

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Archivos de la bóveda, solo dentro del almacenamiento privado de la app.
 * El borrado sobrescribe el contenido antes de unlink; en flash no es garantía física.
 */
class PrivateFileStore(context: Context) {
    private val root = File(context.filesDir, "vault").apply { mkdirs() }
    private val captureRoot = File(context.cacheDir, "capture").apply { mkdirs() }

    fun target(name: String): File = File(root, name)

    fun writeAtomic(name: String, bytes: ByteArray): File {
        val target = File(root, name)
        val tmp = File(root, "$name.${System.nanoTime()}.tmp")
        FileOutputStream(tmp).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        if (target.exists()) {
            secureDelete(target)
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            secureDelete(tmp)
        }
        return target
    }

    fun read(file: File): ByteArray = file.readBytes()

    fun newCaptureFile(): File = File(captureRoot, "${System.nanoTime()}.jpg")

    fun secureDelete(file: File) {
        if (!file.exists() || !file.isFile) return
        try {
            RandomAccessFile(file, "rws").use { raf ->
                val zeros = ByteArray(8192)
                var remaining = raf.length()
                raf.seek(0)
                while (remaining > 0) {
                    val chunk = minOf(zeros.size.toLong(), remaining).toInt()
                    raf.write(zeros, 0, chunk)
                    remaining -= chunk
                }
                raf.fd.sync()
            }
        } catch (_: Exception) {
            // Si el sobrescrito falla, igual intentamos borrar el nombre.
        }
        file.delete()
    }
}
