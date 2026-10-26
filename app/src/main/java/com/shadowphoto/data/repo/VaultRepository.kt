package com.shadowphoto.data.repo

import android.content.Context
import android.net.Uri
import com.shadowphoto.container.ShpDecoder
import com.shadowphoto.container.ShpEncoder
import com.shadowphoto.data.db.ProtectedImageDao
import com.shadowphoto.data.db.ProtectedImageEntity
import com.shadowphoto.data.db.VaultFlags
import com.shadowphoto.data.files.PrivateFileStore
import com.shadowphoto.domain.ProtectionService
import com.shadowphoto.domain.UnlockOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class VaultRepository(
    private val context: Context,
    private val dao: ProtectedImageDao,
    private val files: PrivateFileStore,
    private val protection: ProtectionService,
) {
    fun observeAll(): Flow<List<ProtectedImageEntity>> = dao.observeAll()

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun get(id: String): ProtectedImageEntity? = dao.get(id)

    suspend fun protectAndStore(
        original: ByteArray,
        coverPng: ByteArray,
        password: CharArray,
    ): ProtectedImageEntity = withContext(Dispatchers.Default) {
        val packed = protection.protect(original, coverPng, password)
        val id = UUID.randomUUID().toString()
        val pngFile = files.writeAtomic("$id.png", packed.protectedPng)
        val shpFile = try {
            files.writeAtomic("$id.shp", packed.shpBackup)
        } catch (e: Exception) {
            files.secureDelete(pngFile)
            throw e
        }
        val entity = ProtectedImageEntity(
            id = id,
            path = pngFile.absolutePath,
            backupPath = shpFile.absolutePath,
            contentHash = packed.contentHash,
            createdAt = System.currentTimeMillis(),
            flags = VaultFlags.HAS_BACKUP,
            originalSize = packed.originalSize,
        )
        try {
            dao.insert(entity)
        } catch (e: Exception) {
            files.secureDelete(pngFile)
            files.secureDelete(shpFile)
            throw e
        }
        entity
    }

    suspend fun importContainer(bytes: ByteArray): ProtectedImageEntity = withContext(Dispatchers.IO) {
        val png = ShpDecoder().readProtectedPng(bytes)
        val id = UUID.randomUUID().toString()
        val pngFile = files.writeAtomic("$id.png", png)
        val shpFile = files.writeAtomic("$id.shp", ShpEncoder().writeBackup(png))
        val entity = ProtectedImageEntity(
            id = id,
            path = pngFile.absolutePath,
            backupPath = shpFile.absolutePath,
            contentHash = ProtectionService.sha256Hex(png),
            createdAt = System.currentTimeMillis(),
            flags = VaultFlags.HAS_BACKUP or VaultFlags.IMPORTED,
            originalSize = 0L,
        )
        try {
            dao.insert(entity)
        } catch (e: Exception) {
            files.secureDelete(pngFile)
            files.secureDelete(shpFile)
            throw e
        }
        entity
    }

    suspend fun readContainer(entity: ProtectedImageEntity): ByteArray =
        withContext(Dispatchers.IO) { files.read(File(entity.path)) }

    suspend fun unlock(bytes: ByteArray, password: CharArray): UnlockOutcome =
        withContext(Dispatchers.Default) { protection.unlock(bytes, password) }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val entity = dao.get(id) ?: return@withContext
        dao.delete(id)
        files.secureDelete(File(entity.path))
        if (entity.backupPath.isNotEmpty()) {
            files.secureDelete(File(entity.backupPath))
        }
    }

    suspend fun exportTo(entity: ProtectedImageEntity, uri: Uri, backup: Boolean) =
        withContext(Dispatchers.IO) {
            val source = File(if (backup) entity.backupPath else entity.path)
            context.contentResolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
                output.flush()
            } ?: error("No se pudo escribir el destino")
        }
}
