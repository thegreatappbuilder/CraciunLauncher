package ro.craciun.launcher.apps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import ro.craciun.launcher.theme.ChristmasTheme
import ro.craciun.launcher.util.Colors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Stilurile de pictograme festive. Pictograma originală a aplicației rămâne recognoscibilă în centru. */
enum class IconStyle(val title: String) {
    ORNAMENT("Globuleț"),
    GIFT("Cadou"),
    WREATH("Coroniță"),
    SNOWCAP("Căciulă de zăpadă"),
    HOLLY("Crenguță de ilex")
}

private const val TAU = (PI * 2).toFloat()

object FestiveIconFactory {

    fun create(source: Drawable, size: Int, style: IconStyle, theme: ChristmasTheme, seed: Int): Bitmap {
        // copie separată a pictogramei, ca să nu deranjăm desenarea ei pe ecran în același timp
        val src = source.constantState?.newDrawable()?.mutate() ?: source
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val s = size.toFloat()
        val orn = theme.ornamentColors
        val col = orn[abs(seed) % orn.size]
        when (style) {
            IconStyle.ORNAMENT -> ornament(c, s, src, col)
            IconStyle.GIFT -> gift(c, s, src, col, if (col == orn[0]) orn[1 % orn.size] else orn[0])
            IconStyle.WREATH -> wreath(c, s, src, theme.accent)
            IconStyle.SNOWCAP -> snowcap(c, s, src)
            IconStyle.HOLLY -> holly(c, s, src)
        }
        return b
    }

    private fun drawSrc(c: Canvas, src: Drawable, cx: Float, cy: Float, sz: Float) {
        val half = sz / 2f
        src.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
        src.draw(c)
    }

    /** Desenează pictograma decupată într-un cerc (margini netede). */
    private fun drawSrcInCircle(c: Canvas, src: Drawable, cx: Float, cy: Float, iconSize: Float, maskR: Float) {
        val bounds = RectF(cx - iconSize / 2f, cy - iconSize / 2f, cx + iconSize / 2f, cy + iconSize / 2f)
        val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        val srcIn = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }
        val outer = c.saveLayer(bounds, null)
        c.drawCircle(cx, cy, maskR, mask)
        c.saveLayer(bounds, srcIn)
        drawSrc(c, src, cx, cy, iconSize)
        c.restoreToCount(outer)
    }

    private fun ornament(c: Canvas, s: Float, src: Drawable, col: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = s / 2f
        val cy = s * 0.56f
        val r = s * 0.42f
        // agățătoare
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.025f
        p.color = 0xFFD9B95A.toInt()
        c.drawCircle(cx, cy - r - s * 0.08f, s * 0.035f, p)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            cx - s * 0.09f, 0f, cx + s * 0.09f, 0f,
            intArrayOf(0xFF8A6A1E.toInt(), 0xFFF6DD8A.toInt(), 0xFF8A6A1E.toInt()), null, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(RectF(cx - s * 0.09f, cy - r - s * 0.06f, cx + s * 0.09f, cy - r + s * 0.03f), s * 0.02f, s * 0.02f, p)
        // globul lucios
        p.shader = RadialGradient(
            cx - r * 0.35f, cy - r * 0.4f, r * 1.55f,
            intArrayOf(Colors.lighten(col, 0.55f), col, Colors.darken(col, 0.6f)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, p)
        p.shader = null
        p.color = 0x96FFF1C2.toInt()
        for (k in 0 until 18) {
            val a = k * TAU / 18f
            c.drawCircle(cx + cos(a) * r * 0.86f, cy + sin(a) * r * 0.86f, s * 0.009f, p)
        }
        // disc mat cu pictograma
        p.color = 0x33000000
        c.drawCircle(cx, cy + s * 0.012f, r * 0.7f, p)
        p.color = 0xF0FFFFFF.toInt()
        c.drawCircle(cx, cy, r * 0.68f, p)
        drawSrcInCircle(c, src, cx, cy, r * 1.3f, r * 0.64f)
        // reflexie
        p.color = 0x59FFFFFF
        c.drawOval(RectF(cx - r * 0.62f, cy - r * 0.8f, cx - r * 0.15f, cy - r * 0.5f), p)
    }

    private fun gift(c: Canvas, s: Float, src: Drawable, col: Int, ribbon: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = s / 2f
        p.color = 0x40000000
        c.drawRoundRect(RectF(s * 0.1f, s * 0.25f, s * 0.9f, s * 0.96f), s * 0.08f, s * 0.08f, p)
        val box = RectF(s * 0.1f, s * 0.22f, s * 0.9f, s * 0.94f)
        p.shader = LinearGradient(0f, box.top, 0f, box.bottom, Colors.lighten(col, 0.15f), Colors.darken(col, 0.3f), Shader.TileMode.CLAMP)
        c.drawRoundRect(box, s * 0.08f, s * 0.08f, p)
        p.shader = null
        p.color = Colors.lighten(col, 0.22f)
        c.drawRoundRect(RectF(s * 0.06f, s * 0.2f, s * 0.94f, s * 0.36f), s * 0.05f, s * 0.05f, p)
        // panglici
        p.color = ribbon
        c.drawRect(cx - s * 0.06f, s * 0.2f, cx + s * 0.06f, s * 0.94f, p)
        c.drawRect(s * 0.1f, s * 0.57f, s * 0.9f, s * 0.69f, p)
        // fundă
        val by = s * 0.2f
        c.save(); c.rotate(-28f, cx, by)
        c.drawOval(RectF(cx - s * 0.26f, by - s * 0.1f, cx, by + s * 0.05f), p)
        c.restore()
        c.save(); c.rotate(28f, cx, by)
        c.drawOval(RectF(cx, by - s * 0.1f, cx + s * 0.26f, by + s * 0.05f), p)
        c.restore()
        p.color = Colors.darken(ribbon, 0.2f)
        c.drawCircle(cx, by, s * 0.045f, p)
        // eticheta cu pictograma
        p.color = 0x33000000
        c.drawCircle(cx, s * 0.635f, s * 0.215f, p)
        p.color = 0xF5FFFFFF.toInt()
        c.drawCircle(cx, s * 0.625f, s * 0.21f, p)
        drawSrcInCircle(c, src, cx, s * 0.625f, s * 0.4f, s * 0.195f)
    }

    private fun wreath(c: Canvas, s: Float, src: Drawable, berry: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = s / 2f
        val cy = s / 2f
        val ring = s * 0.36f
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.16f
        p.color = 0xFF0F3D24.toInt()
        c.drawCircle(cx, cy, ring, p)
        p.style = Paint.Style.FILL
        val greens = intArrayOf(0xFF1E6B3A.toInt(), 0xFF2E8B4E.toInt(), 0xFF145A30.toInt(), 0xFF3FA05F.toInt())
        val rnd = Random(7)
        repeat(150) {
            val a = rnd.nextFloat() * TAU
            val rr = ring + (rnd.nextFloat() - 0.5f) * s * 0.15f
            val x = cx + cos(a) * rr
            val y = cy + sin(a) * rr
            p.color = greens[rnd.nextInt(greens.size)]
            c.save()
            c.rotate(Math.toDegrees(a.toDouble()).toFloat() + 90f + (rnd.nextFloat() - 0.5f) * 80f, x, y)
            c.drawOval(x - s * 0.013f, y - s * 0.038f, x + s * 0.013f, y + s * 0.038f, p)
            c.restore()
        }
        p.color = 0xD9FFFFFF.toInt()
        repeat(26) {
            val a = rnd.nextFloat() * TAU
            val rr = ring + (rnd.nextFloat() - 0.7f) * s * 0.12f
            c.drawCircle(cx + cos(a) * rr, cy + sin(a) * rr, s * 0.012f, p)
        }
        for (k in 0 until 5) {
            val a = -PI.toFloat() / 2f + k * TAU / 5f + 0.3f
            for (j in 0..2) {
                val bx = cx + cos(a) * ring + cos(a + j * 2.1f) * s * 0.025f
                val by = cy + sin(a) * ring + sin(a + j * 2.1f) * s * 0.025f
                p.shader = RadialGradient(bx - s * 0.008f, by - s * 0.008f, s * 0.03f, Colors.lighten(berry, 0.4f), Colors.darken(berry, 0.3f), Shader.TileMode.CLAMP)
                c.drawCircle(bx, by, s * 0.022f, p)
            }
        }
        p.shader = null
        // fundă jos
        p.color = berry
        val by = cy + ring
        c.save(); c.rotate(-20f, cx, by)
        c.drawOval(RectF(cx - s * 0.17f, by - s * 0.06f, cx, by + s * 0.04f), p)
        c.restore()
        c.save(); c.rotate(20f, cx, by)
        c.drawOval(RectF(cx, by - s * 0.06f, cx + s * 0.17f, by + s * 0.04f), p)
        c.restore()
        c.drawRect(cx - s * 0.05f, by, cx - s * 0.015f, by + s * 0.12f, p)
        c.drawRect(cx + s * 0.015f, by, cx + s * 0.05f, by + s * 0.12f, p)
        p.color = Colors.darken(berry, 0.25f)
        c.drawCircle(cx, by, s * 0.035f, p)
        // centrul
        p.color = 0xF0FFFFFF.toInt()
        c.drawCircle(cx, cy, ring * 0.66f, p)
        drawSrcInCircle(c, src, cx, cy, ring * 1.28f, ring * 0.62f)
    }

    private fun snowcap(c: Canvas, s: Float, src: Drawable) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = s / 2f
        val cy = s * 0.54f
        drawSrc(c, src, cx, cy, s * 0.84f)
        val top = cy - s * 0.42f
        val path = Path()
        path.moveTo(cx - s * 0.42f, top + s * 0.08f)
        path.quadTo(cx - s * 0.42f, top - s * 0.06f, cx - s * 0.2f, top - s * 0.07f)
        path.quadTo(cx, top - s * 0.13f, cx + s * 0.22f, top - s * 0.06f)
        path.quadTo(cx + s * 0.44f, top - s * 0.03f, cx + s * 0.42f, top + s * 0.07f)
        path.quadTo(cx + s * 0.33f, top + s * 0.16f, cx + s * 0.26f, top + s * 0.06f)
        path.quadTo(cx + s * 0.12f, top + s * 0.1f, cx + s * 0.02f, top + s * 0.05f)
        path.quadTo(cx - s * 0.08f, top + s * 0.2f, cx - s * 0.16f, top + s * 0.06f)
        path.quadTo(cx - s * 0.3f, top + s * 0.12f, cx - s * 0.42f, top + s * 0.08f)
        path.close()
        c.save()
        c.translate(0f, s * 0.018f)
        p.color = 0x66607090
        c.drawPath(path, p)
        c.restore()
        p.shader = LinearGradient(0f, top - s * 0.12f, 0f, top + s * 0.18f, Color.WHITE, 0xFFDCE6F3.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        p.shader = null
        sparkle(c, cx - s * 0.25f, top - s * 0.02f, s * 0.035f)
        sparkle(c, cx + s * 0.18f, top - s * 0.05f, s * 0.025f)
    }

    private fun holly(c: Canvas, s: Float, src: Drawable) {
        drawSrc(c, src, s / 2f, s / 2f + s * 0.03f, s * 0.84f)
        val x = s * 0.8f
        val y = s * 0.17f
        leaf(c, x, y, -150f, s * 0.22f)
        leaf(c, x, y, 100f, s * 0.2f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for ((bx, by) in listOf(x - s * 0.02f to y + s * 0.01f, x + s * 0.035f to y + s * 0.025f, x to y + s * 0.055f)) {
            p.shader = RadialGradient(bx - s * 0.01f, by - s * 0.01f, s * 0.045f, 0xFFFF5A5A.toInt(), 0xFF9E0B1C.toInt(), Shader.TileMode.CLAMP)
            c.drawCircle(bx, by, s * 0.032f, p)
        }
    }

    private fun leaf(c: Canvas, x: Float, y: Float, angle: Float, len: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()
        path.moveTo(0f, 0f)
        path.cubicTo(len * 0.3f, -len * 0.34f, len * 0.75f, -len * 0.27f, len, 0f)
        path.cubicTo(len * 0.75f, len * 0.27f, len * 0.3f, len * 0.34f, 0f, 0f)
        path.close()
        c.save()
        c.translate(x, y)
        c.rotate(angle)
        p.shader = LinearGradient(0f, -len * 0.3f, 0f, len * 0.3f, 0xFF3FA05F.toInt(), 0xFF0F4D28.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = len * 0.04f
        p.color = 0x99B8E6C2.toInt()
        c.drawLine(len * 0.05f, 0f, len * 0.92f, 0f, p)
        c.restore()
    }

    private fun sparkle(c: Canvas, x: Float, y: Float, r: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = r * 0.25f
            strokeCap = Paint.Cap.ROUND
        }
        c.drawLine(x - r, y, x + r, y, p)
        c.drawLine(x, y - r, x, y + r, p)
    }
}
