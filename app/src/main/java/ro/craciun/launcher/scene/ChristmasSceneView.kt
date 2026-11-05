package ro.craciun.launcher.scene

import android.content.Context
import android.graphics.Canvas
import android.view.View

/** View care rulează scena animată (≈60 fps, sau ≈30 fps în modul economie baterie). */
class ChristmasSceneView(context: Context) : View(context) {

    val renderer = ChristmasRenderer()
    var batterySaver = false
    private var running = false
    private var lastNanos = 0L

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        renderer.setSize(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0.016f else (now - lastNanos) / 1_000_000_000f
        lastNanos = now
        renderer.draw(canvas, dt)
        if (running) {
            if (batterySaver) postInvalidateDelayed(33) else postInvalidateOnAnimation()
        }
    }

    fun start() {
        if (!running) {
            running = true
            lastNanos = 0L
            invalidate()
        }
    }

    fun stop() {
        running = false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        running = false
        renderer.release()
    }
}
