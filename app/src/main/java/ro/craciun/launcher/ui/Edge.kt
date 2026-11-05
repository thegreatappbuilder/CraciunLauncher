package ro.craciun.launcher.ui

import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets

/** Afișare de la margine la margine (sub bara de stare și sub decupajul camerei), pe orice versiune Android. */
object Edge {

    fun enable(window: Window) {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            legacyFlags(window)
        }
        transparentBars(window)
    }

    @Suppress("DEPRECATION")
    private fun legacyFlags(window: Window) {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    @Suppress("DEPRECATION")
    private fun transparentBars(window: Window) {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }

    /** Marginile ocupate de sistem: [stânga, sus, dreapta, jos]. */
    fun bars(insets: WindowInsets): IntArray =
        if (Build.VERSION.SDK_INT >= 30) {
            val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            intArrayOf(i.left, i.top, i.right, i.bottom)
        } else {
            legacyBars(insets)
        }

    @Suppress("DEPRECATION")
    private fun legacyBars(insets: WindowInsets): IntArray = intArrayOf(
        insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
        insets.systemWindowInsetRight, insets.systemWindowInsetBottom
    )
}
