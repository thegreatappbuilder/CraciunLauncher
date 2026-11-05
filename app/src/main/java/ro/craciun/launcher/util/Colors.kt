package ro.craciun.launcher.util

import android.graphics.Color
import kotlin.math.roundToInt

object Colors {
    fun lerp(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * k).roundToInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * k).roundToInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * k).roundToInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).roundToInt()
        )
    }

    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    fun lighten(color: Int, t: Float): Int = withAlpha(lerp(color, Color.WHITE, t), Color.alpha(color))

    fun darken(color: Int, t: Float): Int = withAlpha(lerp(color, Color.BLACK, t), Color.alpha(color))
}
