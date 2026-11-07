package org.politisationpopulairefrancaise.tools.map

import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.geom.PathIterator
import java.awt.geom.Path2D
import java.io.File
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The glyphs of the map labels, made from the fonts already bundled with the application (Atkinson
 * Hyperlegible, SIL Open Font License 1.1, `licenses/OFL-AtkinsonHyperlegible.txt`): no font is
 * downloaded, and the map uses the typeface of PPF (D-025).
 *
 * The format is the one MapLibre reads (`{fontstack}/{range}.pbf`, one file per range of 256
 * code points): signed distance fields of 24 px glyphs with a 3 px border, computed like the
 * reference tool (Mapbox `sdf-glyph-foundry`, used by `node-fontnik`): distance to the outline in
 * pixels, a radius of 8 px, a cutoff of 0.25, so that the edge of a glyph is at the value 191.
 * Metrics: `left` and `top` are the corners of the outline (`top` from the ascender), `advance`
 * the horizontal advance, in pixels.
 */
internal object Glyphs {
    const val SIZE = 24f
    const val BUFFER = 3
    const val RADIUS = 8.0
    const val CUTOFF = 0.25

    /** One glyph, as written in the file. */
    class Glyph(val id: Int, val bitmap: ByteArray?, val width: Int, val height: Int, val left: Int, val top: Int, val advance: Int)

    /** Writes every range that holds at least one glyph of [fontFile] into [outDir]; returns the ranges written. */
    fun write(fontFile: File, fontstack: String, outDir: File): List<String> {
        val font = Font.createFont(Font.TRUETYPE_FONT, fontFile).deriveFont(SIZE)
        outDir.mkdirs()
        val written = ArrayList<String>()
        for (start in 0 until 65_536 step 256) {
            val glyphs = (start until start + 256).filter { cp -> cp != 0 && font.canDisplay(cp) && !Character.isISOControl(cp) }.mapNotNull { glyph(font, it) }
            if (glyphs.isEmpty()) continue
            val range = "$start-${start + 255}"
            File(outDir, "$range.pbf").writeBytes(encode(fontstack, range, glyphs))
            written += range
        }
        return written
    }

    fun encode(fontstack: String, range: String, glyphs: List<Glyph>): ByteArray {
        val stack = Protobuf.Writer().string(1, fontstack).string(2, range)
        for (g in glyphs) {
            val glyph = Protobuf.Writer().uint(1, g.id.toLong())
            g.bitmap?.let { glyph.bytes(2, it) }
            glyph.uint(3, g.width.toLong()).uint(4, g.height.toLong()).sint(5, g.left).sint(6, g.top).uint(7, g.advance.toLong())
            stack.bytes(3, glyph.toByteArray())
        }
        return Protobuf.Writer().bytes(1, stack.toByteArray()).toByteArray()
    }

    fun glyph(font: Font, codePoint: Int): Glyph? {
        val frc = FontRenderContext(null, true, true)
        val vector = font.createGlyphVector(frc, String(Character.toChars(codePoint)))
        if (vector.numGlyphs != 1 || vector.getGlyphCode(0) == font.missingGlyphCode) return null
        val advance = vector.getGlyphMetrics(0).advance.roundToInt()
        val ascender = ceil(font.getLineMetrics("Hg", frc).ascent.toDouble()).toInt()
        // Outline in pixels, y downwards from the baseline (Java 2D).
        val outline = Path2D.Double(vector.getGlyphOutline(0))
        val bounds = outline.bounds2D
        if (bounds.isEmpty) return Glyph(codePoint, null, 0, 0, 0, 0, advance) // a space: only its advance
        // Corners in "y upwards" pixels, rounded like the reference tool.
        val xMin = bounds.minX.roundToInt()
        val xMax = bounds.maxX.roundToInt()
        val yMin = (-bounds.maxY).roundToInt()
        val yMax = (-bounds.minY).roundToInt()
        val width = xMax - xMin
        val height = yMax - yMin
        if (width <= 0 || height <= 0) return Glyph(codePoint, null, 0, 0, 0, 0, advance)
        val segments = segments(outline)
        val bw = width + 2 * BUFFER
        val bh = height + 2 * BUFFER
        val bitmap = ByteArray(bw * bh)
        for (row in 0 until bh) {
            // Row 0 of the bitmap is the top of the glyph.
            val yUp = yMax + BUFFER - row - 0.5
            for (col in 0 until bw) {
                val x = xMin - BUFFER + col + 0.5
                val y = -yUp // back to Java 2D coordinates
                var d = distance(segments, x, y)
                if (outline.contains(x, y)) d = -d
                val scaled = d * (256 / RADIUS) + CUTOFF * 256
                val n = min(255.0, max(0.0, scaled)).toInt()
                bitmap[row * bw + col] = (255 - n).toByte()
            }
        }
        return Glyph(codePoint, bitmap, width, height, xMin, yMax - ascender, advance)
    }

    /** The outline as straight segments (curves flattened to a tenth of a pixel). */
    private fun segments(path: Path2D): DoubleArray {
        val out = ArrayList<Double>()
        val it = path.getPathIterator(null, 0.1)
        val c = DoubleArray(6)
        var startX = 0.0
        var startY = 0.0
        var lastX = 0.0
        var lastY = 0.0
        while (!it.isDone) {
            when (it.currentSegment(c)) {
                PathIterator.SEG_MOVETO -> {
                    startX = c[0]; startY = c[1]; lastX = c[0]; lastY = c[1]
                }
                PathIterator.SEG_LINETO -> {
                    out += listOf(lastX, lastY, c[0], c[1]); lastX = c[0]; lastY = c[1]
                }
                PathIterator.SEG_CLOSE -> {
                    out += listOf(lastX, lastY, startX, startY); lastX = startX; lastY = startY
                }
            }
            it.next()
        }
        return out.toDoubleArray()
    }

    /** The distance from (x, y) to the nearest segment, at most the radius (beyond, the value saturates anyway). */
    private fun distance(s: DoubleArray, x: Double, y: Double): Double {
        var best = RADIUS + 1
        var i = 0
        while (i < s.size) {
            val ax = s[i]; val ay = s[i + 1]; val bx = s[i + 2]; val by = s[i + 3]
            val dx = bx - ax
            val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            val t = if (lengthSquared == 0.0) 0.0 else (((x - ax) * dx + (y - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
            val d = hypot(x - (ax + t * dx), y - (ay + t * dy))
            if (d < best) best = d
            i += 4
        }
        return best
    }
}
