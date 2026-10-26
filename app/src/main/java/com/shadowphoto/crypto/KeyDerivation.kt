package com.shadowphoto.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * Deriva una KEK de 256 bits a partir de la contraseña y un salt aleatorio.
 * La contraseña no se guarda. Cada imagen lleva su propio salt dentro del contenedor.
 */
class KeyDerivation(
    private val params: Argon2Params = Argon2Params.PRODUCTION,
) {
    fun derive(password: CharArray, salt: ByteArray): ByteArray {
        require(salt.size >= 16) { "salt demasiado corto" }
        val passwordBytes = password.encodeUtf8()
        try {
            val argon = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withIterations(params.iterations)
                .withMemoryAsKB(params.memoryKiB)
                .withParallelism(params.parallelism)
                .build()
            val generator = Argon2BytesGenerator()
            generator.init(argon)
            val out = ByteArray(params.hashLength)
            generator.generateBytes(passwordBytes, out)
            return out
        } finally {
            passwordBytes.wipe()
        }
    }
}
