package com.shadowphoto.crypto

import java.nio.CharBuffer

fun ByteArray.wipe() {
    fill(0)
}

/**
 * UTF-8 sin crear un String intermedio que no se pueda borrar.
 * El array resultante hay que pasarlo por [wipe] cuando ya no haga falta.
 */
fun CharArray.encodeUtf8(): ByteArray {
    val encoded = Charsets.UTF_8.encode(CharBuffer.wrap(this))
    val out = ByteArray(encoded.remaining())
    encoded.get(out)
    if (encoded.hasArray()) {
        encoded.array().fill(0)
    }
    return out
}
