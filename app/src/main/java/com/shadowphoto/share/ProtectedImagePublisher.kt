package com.shadowphoto.share

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * Comparte o copia el PNG ya sellado (portada + chunk shPd).
 * Nunca decodifica ni reescribe los píxeles: el archivo sale byte a byte.
 */
class ProtectedImagePublisher(private val context: Context) {
    fun whatsAppInstalled(): Boolean = whatsAppPackage() != null

    fun googlePhotosInstalled(): Boolean = isInstalled(GOOGLE_PHOTOS)

    fun shareWithSheet(file: File): ShareOutcome {
        rejectUnlessSealedPng(file)?.let { return it }
        return try {
            val send = sendIntent(file, mime = "image/png", targetPackage = null)
            val chooser = Intent.createChooser(send, "Compartir portada protegida").apply {
                clipData = send.clipData
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            launch(chooser)
            ShareOutcome("Elige una app. Se comparte el PNG protegido, no la foto privada.", error = false)
        } catch (_: ActivityNotFoundException) {
            ShareOutcome("No hay ninguna app para compartir la portada.", error = true)
        } catch (e: SecurityException) {
            ShareOutcome("No se pudo compartir. El sistema bloqueó el acceso al archivo.", error = true)
        } catch (_: IllegalArgumentException) {
            ShareOutcome("No se pudo preparar el PNG protegido para compartir.", error = true)
        } catch (e: Exception) {
            ShareOutcome(e.message ?: "No se pudo abrir el menú para compartir.", error = true)
        }
    }

    fun shareWithWhatsApp(file: File): ShareOutcome {
        rejectUnlessSealedPng(file)?.let { return it }
        val pkg = whatsAppPackage()
        if (pkg == null) {
            val fallback = shareWithSheet(file)
            return if (fallback.error) {
                fallback
            } else {
                ShareOutcome(
                    "WhatsApp no está instalado. Se abrió el menú para compartir.",
                    error = false,
                )
            }
        }
        return try {
            // Documento, no foto: WhatsApp no recomprime el PNG y el bloque shPd se conserva.
            launch(sendIntent(file, mime = "application/octet-stream", targetPackage = pkg))
            ShareOutcome("WhatsApp recibió el PNG protegido como archivo.", error = false)
        } catch (_: ActivityNotFoundException) {
            val fallback = shareWithSheet(file)
            if (fallback.error) {
                fallback
            } else {
                ShareOutcome("No se pudo abrir WhatsApp. Se abrió el menú para compartir.", error = false)
            }
        } catch (e: SecurityException) {
            ShareOutcome("No se pudo enviar por WhatsApp. El sistema bloqueó el acceso al archivo.", error = true)
        } catch (_: IllegalArgumentException) {
            ShareOutcome("No se pudo preparar el PNG protegido para WhatsApp.", error = true)
        } catch (e: Exception) {
            ShareOutcome(e.message ?: "No se pudo enviar por WhatsApp.", error = true)
        }
    }

    fun sendToGooglePhotos(file: File): ShareOutcome {
        rejectUnlessSealedPng(file)?.let { return it }
        if (!googlePhotosInstalled()) {
            return ShareOutcome("Google Fotos no está instalado.", error = true)
        }
        return try {
            launch(sendIntent(file, mime = "image/png", targetPackage = GOOGLE_PHOTOS))
            ShareOutcome(
                "Se abrió Google Fotos con la portada protegida. Si Fotos vuelve a guardar la imagen, puede perder el bloque cifrado. Para conservarlo, usa el álbum ShadowPhoto.",
                error = false,
            )
        } catch (_: ActivityNotFoundException) {
            ShareOutcome("No se pudo abrir Google Fotos.", error = true)
        } catch (e: SecurityException) {
            ShareOutcome("No se pudo enviar a Google Fotos. El sistema bloqueó el acceso al archivo.", error = true)
        } catch (_: IllegalArgumentException) {
            ShareOutcome("No se pudo preparar el PNG protegido para Google Fotos.", error = true)
        } catch (e: Exception) {
            ShareOutcome(e.message ?: "No se pudo enviar a Google Fotos.", error = true)
        }
    }

    /**
     * Inserta el PNG sellado en Pictures/ShadowPhoto vía MediaStore.
     * En Android 11+ no hace falta permiso de almacenamiento para crear este archivo.
     */
    fun saveToGalleryAlbum(file: File, displayName: String): ShareOutcome {
        rejectUnlessSealedPng(file)?.let { return it }
        val safeName = displayName.substringAfterLast('/').let { name ->
            if (name.endsWith(".png", ignoreCase = true)) name else "$name.png"
        }
        val resolver = context.contentResolver
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, safeName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, ALBUM_RELATIVE_PATH)
            put(MediaStore.Images.Media.DATE_ADDED, now / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, now)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = try {
            resolver.insert(collection, values)
        } catch (e: SecurityException) {
            return ShareOutcome(
                "No se pudo guardar en el álbum. El sistema bloqueó el acceso a Imágenes.",
                error = true,
            )
        } ?: return ShareOutcome("No se pudo crear el archivo en el álbum ShadowPhoto.", error = true)
        return try {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("No se pudo escribir en el álbum")
            val published = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(uri, published, null, null)
            ShareOutcome(
                "Portada protegida guardada en el álbum ShadowPhoto. Google Fotos puede subirla si la copia de seguridad está activada. La foto privada no se guardó.",
                error = false,
            )
        } catch (e: SecurityException) {
            resolver.delete(uri, null, null)
            ShareOutcome(
                "No se pudo guardar en el álbum. El sistema bloqueó el acceso a Imágenes.",
                error = true,
            )
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            ShareOutcome(e.message ?: "No se pudo guardar en el álbum ShadowPhoto.", error = true)
        }
    }

    private fun sendIntent(file: File, mime: String, targetPackage: String?): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        if (targetPackage != null) {
            context.grantUriPermission(targetPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (targetPackage != null) setPackage(targetPackage)
        }
    }

    private fun launch(intent: Intent) {
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun whatsAppPackage(): String? =
        WHATSAPP_PACKAGES.firstOrNull { isInstalled(it) }

    private fun isInstalled(packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private fun missingFile() = ShareOutcome("No se encontró el PNG protegido.", error = true)

    /** Solo el PNG sellado de la bóveda. Nunca un original ni un archivo de caché. */
    private fun rejectUnlessSealedPng(file: File): ShareOutcome? {
        if (!file.isFile) return missingFile()
        return try {
            val vault = File(context.filesDir, "vault").canonicalFile
            val canonical = file.canonicalFile
            val inside = canonical.path.startsWith(vault.path + File.separator)
            if (!inside || !canonical.name.endsWith(".png", ignoreCase = true)) {
                ShareOutcome("Solo se puede compartir la portada protegida de la bóveda.", error = true)
            } else {
                null
            }
        } catch (_: Exception) {
            ShareOutcome("No se pudo leer el PNG protegido.", error = true)
        }
    }

    companion object {
        const val ALBUM_RELATIVE_PATH = "Pictures/ShadowPhoto/"
        private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")
        private const val GOOGLE_PHOTOS = "com.google.android.apps.photos"
    }
}

data class ShareOutcome(val text: String, val error: Boolean)
