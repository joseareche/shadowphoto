package com.shadowphoto.container

import java.util.Random

/**
 * Portada neutra generada en el dispositivo cuando no se elige otra foto.
 * No lleva marca de ShadowPhoto: la portada exportada no debe delatar el contenido.
 */
object CoverPng {
    fun solid(width: Int, height: Int, rgb: Int): ByteArray =
        PngChunkParser.encodeRgb(width, height) { _, _ -> rgb and 0xFFFFFF }

    fun neutral(width: Int, height: Int, seed: Long = System.nanoTime()): ByteArray {
        val random = Random(seed)
        val skies = intArrayOf(0x8FB7D6, 0x6E97C2, 0xA9C3D4, 0x7AA0B8)
        val glows = intArrayOf(0xE7C7A1, 0xF0D2B4, 0xD9B89A, 0xF2C9A0)
        val lands = intArrayOf(0x6E7A62, 0x5C6B58, 0x7A6A58, 0x4E5C55)
        val sky = skies[random.nextInt(skies.size)]
        val glow = glows[random.nextInt(glows.size)]
        val land = lands[random.nextInt(lands.size)]
        val sunX = (width * (0.58 + random.nextDouble() * 0.22)).toInt()
        val sunY = (height * (0.34 + random.nextDouble() * 0.1)).toInt()
        val sunR = (width * 0.055).toInt().coerceAtLeast(6)
        val horizon = (height * (0.58 + random.nextDouble() * 0.06)).toInt().coerceIn(1, height - 2)
        return PngChunkParser.encodeRgb(width, height) { x, y ->
            if (y < horizon) {
                val t = y.toFloat() / horizon
                var color = mix(sky, glow, t)
                val dx = x - sunX
                val dy = y - sunY
                if (dx * dx + dy * dy <= sunR * sunR) {
                    color = 0xF6E6C8
                }
                color
            } else {
                val span = (height - horizon).coerceAtLeast(1)
                val t = (y - horizon).toFloat() / span
                mix(land, darken(land, 0.45f), t)
            }
        }
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        val clamped = t.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val from = (a ushr shift) and 0xFF
            val to = (b ushr shift) and 0xFF
            return (from + (to - from) * clamped).toInt().coerceIn(0, 255)
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun darken(color: Int, amount: Float): Int {
        fun channel(shift: Int): Int =
            (((color ushr shift) and 0xFF) * (1f - amount)).toInt().coerceIn(0, 255)
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
