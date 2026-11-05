package ro.craciun.launcher.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

/** Texturi mici, generate o singură dată, folosite pentru strălucire și fulgi (rapide la desenare). */
object SpriteFactory {

    fun glow(size: Int): Bitmap = radial(
        size,
        intArrayOf(0xFFFFFFFF.toInt(), 0x88FFFFFF.toInt(), 0x22FFFFFF, 0x00FFFFFF),
        floatArrayOf(0f, 0.18f, 0.5f, 1f)
    )

    fun flake(size: Int): Bitmap = radial(
        size,
        intArrayOf(0xFFFFFFFF.toInt(), 0xEEFFFFFF.toInt(), 0x66FFFFFF, 0x00FFFFFF),
        floatArrayOf(0f, 0.35f, 0.65f, 1f)
    )

    private fun radial(size: Int, colors: IntArray, stops: FloatArray): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val r = size / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(r, r, r, colors, stops, Shader.TileMode.CLAMP)
        c.drawCircle(r, r, r, p)
        return b
    }
}
