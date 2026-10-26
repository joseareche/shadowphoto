package com.shadowphoto.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Registro local de una fotografía protegida.
 * [path] apunta al PNG privado de la app (portada visible + bloque cifrado).
 * [contentHash] es el SHA-256 de ese PNG. La foto original no se guarda en claro.
 */
@Entity(tableName = "protected_images")
data class ProtectedImageEntity(
    @PrimaryKey val id: String,
    val path: String,
    val backupPath: String,
    val contentHash: String,
    val createdAt: Long,
    val flags: Int,
    val originalSize: Long,
)

object VaultFlags {
    const val HAS_BACKUP = 1 shl 0
    const val IMPORTED = 1 shl 1
}
