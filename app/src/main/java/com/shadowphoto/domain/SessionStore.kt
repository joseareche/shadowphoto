package com.shadowphoto.domain

import com.shadowphoto.crypto.wipe

/**
 * Bytes sensibles que solo viven en memoria: la foto privada aún sin cifrar,
 * un contenedor abierto desde fuera y la imagen ya descifrada en el visor.
 */
class SessionStore {
    private val lock = Any()
    private var privateImage: ByteArray? = null
    private var coverPng: ByteArray? = null
    private var externalContainer: ByteArray? = null
    private var unlockedOriginal: ByteArray? = null

    fun setPrivate(bytes: ByteArray) = synchronized(lock) {
        privateImage?.wipe()
        privateImage = bytes
    }

    fun peekPrivate(): ByteArray? = synchronized(lock) { privateImage }

    fun setCover(bytes: ByteArray) = synchronized(lock) {
        coverPng?.wipe()
        coverPng = bytes
    }

    fun peekCover(): ByteArray? = synchronized(lock) { coverPng }

    fun setExternal(bytes: ByteArray) = synchronized(lock) {
        externalContainer?.wipe()
        externalContainer = bytes
    }

    fun peekExternal(): ByteArray? = synchronized(lock) { externalContainer }

    fun setUnlocked(bytes: ByteArray) = synchronized(lock) {
        unlockedOriginal?.wipe()
        unlockedOriginal = bytes
    }

    fun peekUnlocked(): ByteArray? = synchronized(lock) { unlockedOriginal }

    fun clearPrivate() = synchronized(lock) {
        privateImage?.wipe()
        privateImage = null
    }

    fun clearCapture() = synchronized(lock) {
        privateImage?.wipe()
        coverPng?.wipe()
        privateImage = null
        coverPng = null
    }

    fun clearUnlocked() = synchronized(lock) {
        unlockedOriginal?.wipe()
        unlockedOriginal = null
    }

    fun clearExternal() = synchronized(lock) {
        externalContainer?.wipe()
        externalContainer = null
    }

    fun clearAll() = synchronized(lock) {
        privateImage?.wipe()
        coverPng?.wipe()
        externalContainer?.wipe()
        unlockedOriginal?.wipe()
        privateImage = null
        coverPng = null
        externalContainer = null
        unlockedOriginal = null
    }
}
