package com.shadowphoto.crypto

/**
 * Resultado de cifrar la fotografía original.
 * La DEK aleatoria va envuelta con la KEK; el archivo no contiene la contraseña.
 */
class SealedBlob(
    val version: Int,
    val flags: Int,
    val salt: ByteArray,
    val wrapNonce: ByteArray,
    val dataNonce: ByteArray,
    val wrappedDek: ByteArray,
    val ciphertext: ByteArray,
) {
    override fun toString(): String =
        "SealedBlob(version=$version, flags=$flags, ciphertextBytes=${ciphertext.size})"
}
