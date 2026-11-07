package org.politisationpopulairefrancaise.core.media

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A decoded picture: [width] × [height] pixels, row after row, each an ARGB colour (alpha in the
 * high byte, as Android's Bitmap and Java's BufferedImage use them). Decoding and encoding belong
 * to the platform (shared/ui, ui/media); everything done to the pixels is common code.
 */
class ArgbImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0) { "An image has at least one pixel" }
        require(pixels.size == width * height) { "One colour per pixel" }
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]
}

/**
 * A zone to blur, in fractions of the picture (0 to 1 from the left and from the top), so that it
 * does not depend on the size the picture is shown at nor on its resolution.
 */
data class BlurZone(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(left in 0f..1f && right in 0f..1f && top in 0f..1f && bottom in 0f..1f) { "A zone lies inside the picture" }
        require(left < right && top < bottom) { "A zone is not empty" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top

    /** The same zone moved by ([dx], [dy]) and resized by [scale] around its centre, kept inside the picture. */
    fun moved(dx: Float = 0f, dy: Float = 0f, scale: Float = 1f): BlurZone {
        val w = (width * scale).coerceIn(MIN_SIDE, 1f)
        val h = (height * scale).coerceIn(MIN_SIDE, 1f)
        val cx = ((left + right) / 2 + dx).coerceIn(w / 2, 1f - w / 2)
        val cy = ((top + bottom) / 2 + dy).coerceIn(h / 2, 1f - h / 2)
        // Coerced again: a rounding error of the last bit must not push a side out of the picture.
        return BlurZone((cx - w / 2).coerceIn(0f, 1f), (cy - h / 2).coerceIn(0f, 1f), (cx + w / 2).coerceIn(0f, 1f), (cy + h / 2).coerceIn(0f, 1f))
    }

    companion object {
        /** Smallest side of a zone, as a fraction of the picture. */
        const val MIN_SIDE: Float = 0.04f

        /** A square zone of [side] (fraction of the shorter side of the picture) centred on ([x], [y]). */
        fun around(x: Float, y: Float, side: Float, aspectRatio: Float): BlurZone {
            // aspectRatio = width / height: a square on screen is narrower in fractions of a wide picture.
            val w = if (aspectRatio >= 1f) side / aspectRatio else side
            val h = if (aspectRatio >= 1f) side else side * aspectRatio
            return BlurZone(0f, 0f, w.coerceIn(MIN_SIDE, 1f), h.coerceIn(MIN_SIDE, 1f)).let { zone ->
                zone.moved(dx = x - (zone.left + zone.right) / 2, dy = y - (zone.top + zone.bottom) / 2)
            }
        }
    }
}

/** What the device does to the pixels before a picture is published: turn it, reduce it, blur zones. */
object ImageTransforms {
    /** Longest side of a published picture, in pixels: enough to see, too small to read a sensor's noise. */
    const val MAX_PUBLISHED_SIDE: Int = 2048

    /** How many blocks a blurred zone keeps across its longer side: too few to recognize a face. */
    const val BLOCKS_PER_ZONE: Int = 6

    /** Applies an EXIF orientation (1 to 8) to the pixels: the result is upright, with orientation 1. */
    fun applyOrientation(image: ArgbImage, orientation: Int): ArgbImage {
        if (orientation == ExifOrientation.NORMAL || !ExifOrientation.isValid(orientation)) return image
        val w = image.width
        val h = image.height
        val swap = orientation >= 5
        val outWidth = if (swap) h else w
        val outHeight = if (swap) w else h
        val out = IntArray(outWidth * outHeight)
        for (y in 0 until outHeight) {
            for (x in 0 until outWidth) {
                // Where the pixel shown at (x, y) is stored (EXIF 2.3, tag 0x0112).
                val (sx, sy) = when (orientation) {
                    2 -> (w - 1 - x) to y
                    3 -> (w - 1 - x) to (h - 1 - y)
                    4 -> x to (h - 1 - y)
                    5 -> y to x
                    6 -> y to (h - 1 - x)
                    7 -> (w - 1 - y) to (h - 1 - x)
                    else -> (w - 1 - y) to x // 8
                }
                out[y * outWidth + x] = image.pixels[sy * w + sx]
            }
        }
        return ArgbImage(outWidth, outHeight, out)
    }

    /** Reduces the picture so that its longer side is at most [maxSide] (average of the covered pixels); unchanged when already small. */
    fun downscale(image: ArgbImage, maxSide: Int = MAX_PUBLISHED_SIDE): ArgbImage {
        require(maxSide >= 1) { "At least one pixel" }
        val longer = max(image.width, image.height)
        if (longer <= maxSide) return image
        val ratio = maxSide.toDouble() / longer
        val outWidth = max(1, (image.width * ratio).roundToInt())
        val outHeight = max(1, (image.height * ratio).roundToInt())
        val out = IntArray(outWidth * outHeight)
        for (y in 0 until outHeight) {
            val y0 = y * image.height / outHeight
            val y1 = max(y0 + 1, (y + 1) * image.height / outHeight)
            for (x in 0 until outWidth) {
                val x0 = x * image.width / outWidth
                val x1 = max(x0 + 1, (x + 1) * image.width / outWidth)
                out[y * outWidth + x] = average(image, x0, y0, x1, y1)
            }
        }
        return ArgbImage(outWidth, outHeight, out)
    }

    /**
     * Blurs [zones] for good: each zone becomes a mosaic of at most [BLOCKS_PER_ZONE] blocks across,
     * each the average of the pixels it covers. Nothing of the original pixels survives inside a
     * zone but these few averages (unlike a light blur, which tools can partly undo). The image is
     * copied; the original stays untouched.
     */
    fun blur(image: ArgbImage, zones: List<BlurZone>): ArgbImage {
        if (zones.isEmpty()) return image
        val pixels = image.pixels.copyOf()
        val result = ArgbImage(image.width, image.height, pixels)
        for (zone in zones) {
            val x0 = (zone.left * image.width).toInt().coerceIn(0, image.width - 1)
            val y0 = (zone.top * image.height).toInt().coerceIn(0, image.height - 1)
            val x1 = (zone.right * image.width).roundToInt().coerceIn(x0 + 1, image.width)
            val y1 = (zone.bottom * image.height).roundToInt().coerceIn(y0 + 1, image.height)
            val block = max(1, (max(x1 - x0, y1 - y0) + BLOCKS_PER_ZONE - 1) / BLOCKS_PER_ZONE)
            var by = y0
            while (by < y1) {
                val byEnd = min(by + block, y1)
                var bx = x0
                while (bx < x1) {
                    val bxEnd = min(bx + block, x1)
                    val colour = average(result, bx, by, bxEnd, byEnd)
                    for (y in by until byEnd) pixels.fill(colour, y * image.width + bx, y * image.width + bxEnd)
                    bx = bxEnd
                }
                by = byEnd
            }
        }
        return result
    }

    private fun average(image: ArgbImage, x0: Int, y0: Int, x1: Int, y1: Int): Int {
        var a = 0L
        var r = 0L
        var g = 0L
        var b = 0L
        for (y in y0 until y1) {
            val row = y * image.width
            for (x in x0 until x1) {
                val p = image.pixels[row + x]
                a += (p ushr 24) and 0xFF
                r += (p ushr 16) and 0xFF
                g += (p ushr 8) and 0xFF
                b += p and 0xFF
            }
        }
        val n = ((x1 - x0).toLong() * (y1 - y0)).coerceAtLeast(1)
        return ((a / n).toInt() shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }
}
