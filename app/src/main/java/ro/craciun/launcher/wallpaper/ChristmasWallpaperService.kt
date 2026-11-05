package ro.craciun.launcher.wallpaper

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import ro.craciun.launcher.scene.ChristmasRenderer
import ro.craciun.launcher.util.Prefs

/**
 * Fundal animat de Crăciun: aceeași scenă ca în launcher, dar merge cu orice ecran principal
 * (One UI, MIUI/HyperOS, EMUI, Pixel Launcher etc.). Se oprește singur când nu e vizibil.
 */
class ChristmasWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = XmasEngine()

    private inner class XmasEngine : Engine() {
        private val renderer = ChristmasRenderer()
        private val prefs = Prefs(this@ChristmasWallpaperService)
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var batterySaver = false
        private var sparkles = true
        private var last = 0L
        private val drawRunner = Runnable { drawFrame() }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            renderer.topInset = statusBarHeight().toFloat()
            applyPrefs()
        }

        private fun applyPrefs() {
            prefs.applyTo(renderer)
            batterySaver = prefs.batterySaver
            sparkles = prefs.sparkles
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(drawRunner)
            if (visible) {
                applyPrefs()
                last = 0L
                handler.post(drawRunner)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            renderer.setSize(width, height)
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunner)
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (sparkles && event.actionMasked == MotionEvent.ACTION_DOWN) renderer.burst(event.x, event.y)
            super.onTouchEvent(event)
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(drawRunner)
            renderer.release()
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= 26) holder.lockHardwareCanvas() else holder.lockCanvas()
                if (canvas != null) {
                    val now = System.nanoTime()
                    val dt = if (last == 0L) 0.016f else (now - last) / 1_000_000_000f
                    last = now
                    renderer.draw(canvas, dt)
                }
            } catch (e: Exception) {
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                    }
                }
            }
            handler.removeCallbacks(drawRunner)
            if (visible) handler.postDelayed(drawRunner, if (batterySaver) 33L else 16L)
        }

        @SuppressLint("DiscouragedApi", "InternalInsetResource")
        private fun statusBarHeight(): Int {
            val res = this@ChristmasWallpaperService.resources
            val id = res.getIdentifier("status_bar_height", "dimen", "android")
            return if (id > 0) res.getDimensionPixelSize(id) else 0
        }
    }
}
