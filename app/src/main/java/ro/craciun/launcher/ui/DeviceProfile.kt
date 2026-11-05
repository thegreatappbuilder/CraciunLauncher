package ro.craciun.launcher.ui

import android.content.Context
import kotlin.math.min

/**
 * Adaptează grila la ecranul fiecărui model: telefoane mici, telefoane mari, pliabile, tablete,
 * portret sau peisaj. Valorile vin din dimensiunea reală a ferestrei, în dp.
 */
class DeviceProfile(context: Context) {
    val widthDp: Int
    val heightDp: Int
    val density: Float
    val columns: Int
    val iconPx: Int
    val labelSp: Float
    val dockCount: Int
    val homeMax: Int
    val isLarge: Boolean

    init {
        val cfg = context.resources.configuration
        density = context.resources.displayMetrics.density
        widthDp = cfg.screenWidthDp
        heightDp = cfg.screenHeightDp
        isLarge = min(widthDp, heightDp) >= 600
        columns = when {
            widthDp >= 1000 -> 8
            widthDp >= 840 -> 7
            widthDp >= 600 -> 6
            widthDp >= 460 -> 5
            widthDp < 330 -> 3
            else -> 4
        }
        val iconDp = when {
            isLarge -> 64
            widthDp < 340 -> 48
            else -> 56
        }
        iconPx = (iconDp * density).toInt()
        labelSp = if (isLarge) 13f else 12f
        dockCount = min(columns, 5)
        val rows = when {
            heightDp >= 780 -> 4
            heightDp >= 640 -> 3
            heightDp >= 480 -> 2
            else -> 1
        }
        homeMax = columns * rows
    }

    fun dp(v: Int): Int = (v * density).toInt()
}
