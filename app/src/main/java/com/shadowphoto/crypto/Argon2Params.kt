package com.shadowphoto.crypto

/**
 * Parámetros Argon2id del documento técnico.
 * 19 MiB de memoria, 2 iteraciones, paralelismo 1.
 * Se pueden ajustar tras medir en dispositivos reales; no hay clave fija.
 */
data class Argon2Params(
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
    val hashLength: Int = 32,
) {
    init {
        require(memoryKiB >= 8 * parallelism) { "memoria Argon2 insuficiente" }
        require(iterations >= 1)
        require(parallelism >= 1)
        require(hashLength in 16..64)
    }

    companion object {
        val PRODUCTION = Argon2Params(
            memoryKiB = 19 * 1024,
            iterations = 2,
            parallelism = 1,
        )
    }
}
