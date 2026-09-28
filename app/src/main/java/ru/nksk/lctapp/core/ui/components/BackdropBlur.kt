package ru.nksk.lctapp.core.ui.components

/** Three separable box passes approximate a Gaussian; clamped edges avoid dark borders. */
internal fun blurBackdropPixels(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
    require(width > 0 && height > 0 && pixels.size.toLong() == width.toLong() * height)
    require(radius in 1..32)
    var source = pixels.copyOf()
    var target = IntArray(source.size)
    val samples = radius * 2 + 1
    repeat(3) {
        for (horizontal in listOf(true, false)) {
            for (y in 0 until height) for (x in 0 until width) {
                var alpha = 0
                var red = 0
                var green = 0
                var blue = 0
                for (offset in -radius..radius) {
                    val sx = if (horizontal) (x + offset).coerceIn(0, width - 1) else x
                    val sy = if (horizontal) y else (y + offset).coerceIn(0, height - 1)
                    val pixel = source[sy * width + sx]
                    alpha += pixel ushr 24
                    red += (pixel ushr 16) and 255
                    green += (pixel ushr 8) and 255
                    blue += pixel and 255
                }
                target[y * width + x] = ((alpha / samples) shl 24) or
                    ((red / samples) shl 16) or ((green / samples) shl 8) or (blue / samples)
            }
            val previous = source
            source = target
            target = previous
        }
    }
    return source
}
