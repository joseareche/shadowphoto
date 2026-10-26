package com.shadowphoto.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Clave AES del Android Keystore, ligada a este dispositivo.
 * No envuelve la contraseña portátil: un PNG o .shp copiado a otro teléfono
 * se abre con la misma clave. Aquí solo se sella un token local de la bóveda.
 */
class KeyManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun ensureDeviceSeal(): Boolean {
        return try {
            ensureKey()
            val existing = prefs.getString(SEALED, null)
            if (existing == null) {
                val token = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
                val sealed = seal(token)
                token.wipe()
                prefs.edit().putString(SEALED, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
            } else {
                open(Base64.decode(existing, Base64.NO_WRAP)).wipe()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun ensureKey() {
        val keyStore = keyStore()
        if (keyStore.containsAlias(ALIAS)) return
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        generator.generateKey()
    }

    private fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plain)
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + encrypted
    }

    private fun open(sealed: ByteArray): ByteArray {
        val ivLength = sealed[0].toInt() and 0xFF
        val iv = sealed.copyOfRange(1, 1 + ivLength)
        val body = sealed.copyOfRange(1 + ivLength, sealed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(body)
    }

    private fun secretKey(): SecretKey {
        val keyStore = keyStore()
        return keyStore.getKey(ALIAS, null) as SecretKey
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "shadowphoto.device"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFS = "shadowphoto_keystore"
        private const val SEALED = "device_seal"
    }
}
