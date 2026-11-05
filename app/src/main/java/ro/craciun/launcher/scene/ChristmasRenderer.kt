package ro.craciun.launcher.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import ro.craciun.launcher.theme.ChristmasTheme
import ro.craciun.launcher.theme.ThemeCatalog
import ro.craciun.launcher.util.Colors
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private const val TAU = (PI * 2).toFloat()
private fun Random.f(a: Float, b: Float): Float = a + nextFloat() * (b - a)

/**
 * Desenează scena de Crăciun pe orice Canvas: cer, lună, aurora, dealuri înzăpezite, pădure,
 * sat, brad împodobit, cadouri, om de zăpadă, plus efectele animate (ninsoare pe 3 planuri,
 * luminițe care pâlpâie, ghirlandă, stele căzătoare, sania lui Moș Crăciun, fum din hornuri,
 * sclipiri la atingere).
 *
 * Partea statică se randează o singură dată într-un Bitmap; pe fiecare cadru se desenează doar efectele.
 * Totul este scalat după `unit` (latura mică a ecranului / 400), deci arată la fel pe orice
 * telefon, tabletă sau pliabil, în portret sau peisaj.
 */
class ChristmasRenderer {

    var theme: ChristmasTheme = ThemeCatalog.DEFAULT
        set(value) {
            if (field !== value) {
                field = value
                staticDirty = true
                flakesDirty = true
            }
        }
    var snowEnabled = true
    var lightsEnabled = true
    var skyEffectsEnabled = true
    var snowDensity = 1f
        set(value) {
            val v = value.coerceIn(0.1f, 2.5f)
            if (v != field) {
                field = v
                flakesDirty = true
            }
        }
    /** Înălțimea barei de stare, ca ghirlanda să atârne sub ea. */
    var topInset = 0f
        set(value) {
            if (value != field) {
                field = value
                staticDirty = true
            }
        }

    var width = 0
        private set
    var height = 0
        private set

    private var unit = 1f
    private var time = 0f
    private var horizon = 0f
    private var staticBmp: Bitmap? = null
    private var staticDirty = true
    private var flakesDirty = true
    private val rnd = Random(System.nanoTime())

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glow: Bitmap = SpriteFactory.glow(64)
    private val flakeSprite: Bitmap = SpriteFactory.flake(32)
    private val flakePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val tinted = HashMap<Int, Paint>()
    private val dst = RectF()
    private val oval = RectF()
    private val path = Path()
    private val starPath = Path()
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val garlandWire = Path()
    private val tmp = PointF()

    private class Light(val x: Float, val y: Float, val r: Float, val color: Int, val phase: Float, val speed: Float)
    private class Star(val x: Float, val y: Float, val r: Float, val base: Float, val phase: Float, val speed: Float)
    private class Flake(
        var x: Float, var y: Float, var r: Float, var speed: Float, var sway: Float, var phase: Float,
        var layer: Int, var alpha: Int, var crystal: Boolean, var rot: Float, var rotSpeed: Float
    )
    private class Spark(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val maxLife: Float, val color: Int, val size: Float
    )
    private class Bulb(val x: Float, val y: Float, val angle: Float, val color: Int, val index: Int)
    private class Smoke(var x: Float, var y: Float, var r: Float, var life: Float, val maxLife: Float, val vx: Float)
    private class Bokeh(var x: Float, var y: Float, val r: Float, val color: Int, val vx: Float, val vy: Float, val phase: Float)
    private class AuroraBand(val baseY: Float, val thick: Float, val phase: Float, val speed: Float, val paint: Paint)

    private class Hill(val base: Float, val amp: Float, w: Float, r: Random) {
        private val f1 = TAU / w * (0.7f + r.nextFloat() * 0.8f)
        private val f2 = f1 * (2.2f + r.nextFloat())
        private val f3 = f1 * (5f + r.nextFloat() * 2f)
        private val p1 = r.nextFloat() * TAU
        private val p2 = r.nextFloat() * TAU
        private val p3 = r.nextFloat() * TAU
        fun y(x: Float): Float =
            base + amp * (0.6f * sin(x * f1 + p1) + 0.3f * sin(x * f2 + p2) + 0.1f * sin(x * f3 + p3))
    }

    private val stars = ArrayList<Star>()
    private val glints = ArrayList<Star>()
    private val lights = ArrayList<Light>()
    private val flakes = ArrayList<Flake>()
    private val sparks = ArrayList<Spark>()
    private val bulbs = ArrayList<Bulb>()
    private val smoke = ArrayList<Smoke>()
    private val chimneys = ArrayList<PointF>()
    private val bokeh = ArrayList<Bokeh>()
    private val aurora = ArrayList<AuroraBand>()

    private var treeTopX = 0f
    private var treeTopY = 0f
    private var treeStarR = 0f
    private var moonX = 0f
    private var moonY = 0f
    private var moonR = 0f

    private var shootLife = 0f
    private var shootX = 0f
    private var shootY = 0f
    private var shootVX = 0f
    private var shootVY = 0f
    private var nextShoot = 4f
    private var sleighP = -1f
    private var nextSleigh = 5f
    private var smokeTimer = 0f

    // ---------------------------------------------------------------- API

    fun setSize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        if (w != width || h != height) {
            width = w
            height = h
            unit = min(w, h) / 400f
            staticDirty = true
            flakesDirty = true
        }
    }

    /** Desenează un cadru. [dtIn] = secunde scurse de la cadrul precedent. */
    fun draw(c: Canvas, dtIn: Float) {
        if (width == 0 || height == 0) return
        val dt = dtIn.coerceIn(0f, 0.05f)
        time += dt
        if (staticDirty) buildStatic()
        if (flakesDirty) buildFlakes()

        staticBmp?.let { c.drawBitmap(it, 0f, 0f, null) }
        if (theme.aurora) drawAurora(c)
        drawStars(c)
        if (skyEffectsEnabled) {
            drawShootingStar(c, dt)
            drawSleigh(c, dt)
        }
        if (theme.bokeh) drawBokeh(c, dt)
        drawSmoke(c, dt)
        drawLights(c)
        drawTreeStar(c)
        drawGlints(c)
        if (snowEnabled) drawSnow(c, dt)
        if (lightsEnabled) drawGarland(c)
        drawSparks(c, dt)
    }

    /** O imagine fixă a scenei (pentru previzualizări și fundalul static al telefonului). */
    fun renderStill(w: Int, h: Int): Bitmap {
        setSize(w, h)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        time = 3.3f
        draw(c, 0f)
        return b
    }

    /** Explozie de sclipiri în punctul atins. */
    fun burst(x: Float, y: Float) {
        val cols = theme.lightColors
        repeat(26) {
            val a = rnd.nextFloat() * TAU
            val sp = rnd.f(40f, 150f) * unit
            val col = if (rnd.nextFloat() < 0.3f) 0xFFFFF6D5.toInt() else cols[rnd.nextInt(cols.size)]
            sparks.add(Spark(x, y, cos(a) * sp, sin(a) * sp - 30f * unit, 0f, rnd.f(0.6f, 1.3f), col, rnd.f(0.8f, 1.8f) * unit))
        }
    }

    fun release() {
        staticBmp?.recycle()
        staticBmp = null
        staticDirty = true
    }

    // ------------------------------------------------------- partea statică

    private fun buildStatic() {
        staticDirty = false
        val wi = width
        val hi = height
        var bmp = staticBmp
        if (bmp == null || bmp.width != wi || bmp.height != hi) {
            bmp?.recycle()
            bmp = Bitmap.createBitmap(wi, hi, Bitmap.Config.ARGB_8888)
            staticBmp = bmp
        }
        bmp.eraseColor(Color.BLACK)
        val c = Canvas(bmp)
        val t = theme
        val r = Random(t.id.hashCode())
        stars.clear(); glints.clear(); lights.clear(); chimneys.clear(); smoke.clear(); bokeh.clear(); aurora.clear()

        val w = wi.toFloat()
        val h = hi.toFloat()
        val portrait = h >= w
        horizon = h * (if (portrait) 0.58f else 0.52f)

        paintSky(c)
        paintStars(c, r)
        moonR = 20f * unit * t.moonScale
        moonX = w * 0.24f
        moonY = topInset + h * 0.12f + moonR
        if (t.showMoon) paintMoon(c, r)
        if (t.aurora) buildAurora(r)

        // Dealuri îndepărtate și pădure în ceață
        val far = Hill(horizon, h * 0.03f, w, r)
        paintHill(c, far, Colors.lerp(t.farHill, t.skyBottom, 0.25f), Colors.lerp(t.farHill, t.skyBottom, 0.55f), 40)
        repeat(t.pineCount * 2) {
            val x = r.nextFloat() * w
            paintPine(c, x, far.y(x) + h * 0.004f, h * r.f(0.035f, 0.06f), 0.6f, r)
        }

        // Deal mijlociu: brazi, case, biserică
        val mid = Hill(horizon + h * 0.075f, h * 0.025f, w, r)
        paintHill(c, mid, t.midHill, Colors.darken(t.midHill, 0.15f), 60)
        repeat(t.pineCount) {
            val x = r.nextFloat() * w
            paintPine(c, x, mid.y(x) + h * 0.006f, h * r.f(0.06f, 0.1f), 0.28f, r)
        }
        if (t.church) {
            val x = w * 0.42f
            paintChurch(c, x, mid.y(x) + h * 0.008f, h * 0.075f, 0.2f)
        }
        for (k in 0 until t.houses) {
            var x = w * (0.08f + 0.84f * (k + 0.5f) / t.houses) + r.f(-0.03f, 0.03f) * w
            if (t.church && kotlin.math.abs(x - w * 0.42f) < w * 0.1f) x += w * 0.12f
            val s = h * r.f(0.045f, 0.06f)
            paintHouse(c, x, mid.y(x) + s * 0.1f, s, 0.22f, r)
        }

        // Câmpul înzăpezit din față
        val ground = Hill(horizon + h * 0.19f, h * 0.018f, w, r)
        paintHill(c, ground, t.ground, t.groundShadow, 90)
        repeat(70) {
            val x = r.nextFloat() * w
            val y = r.f(ground.y(x) + h * 0.01f, h)
            glints.add(Star(x, y, r.f(1f, 2f) * unit, 1f, r.nextFloat() * TAU, r.f(0.6f, 1.8f)))
        }
        if (t.snowman) {
            val x = w * (if (portrait) 0.24f else 0.2f)
            paintSnowman(c, x, ground.y(x) + h * 0.06f, min(h, w * 2f) * 0.05f)
        }
        if (portrait) paintPine(c, w * 0.01f, h * 0.9f, h * 0.3f, 0f, r)

        // Bradul de Crăciun
        val treeX = w * (if (portrait) 0.7f else 0.78f)
        val treeBase = h * 0.9f
        val treeH = min(h * (if (portrait) 0.36f else 0.6f), w * 0.85f)
        paintBigTree(c, treeX, treeBase, treeH, r)

        // Troian în prim-plan
        val front = Hill(h * 0.935f, h * 0.012f, w, r)
        paintHill(c, front, Colors.lighten(t.ground, 0.05f), t.groundShadow, 120)
        repeat(30) {
            val x = r.nextFloat() * w
            glints.add(Star(x, r.f(front.y(x) + h * 0.005f, h), r.f(1.2f, 2.2f) * unit, 1f, r.nextFloat() * TAU, r.f(0.6f, 1.8f)))
        }

        buildGarland()
        if (t.bokeh) buildBokeh(r)
    }

    private fun paintSky(c: Canvas) {
        val t = theme
        val w = width.toFloat()
        val h = height.toFloat()
        fill.shader = LinearGradient(
            0f, 0f, 0f, horizon + h * 0.1f,
            intArrayOf(t.skyTop, t.skyMid, t.skyBottom), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, fill)
        // ceață luminoasă la orizont
        fill.shader = LinearGradient(
            0f, horizon - h * 0.12f, 0f, horizon + h * 0.02f,
            Colors.withAlpha(t.skyBottom, 0), Colors.withAlpha(Colors.lighten(t.skyBottom, 0.25f), 110),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, horizon - h * 0.12f, w, horizon + h * 0.05f, fill)
        fill.shader = null
    }

    private fun paintStars(c: Canvas, r: Random) {
        val n = (width * horizon / (unit * unit * 260f)).toInt().coerceIn(60, 400)
        fill.color = Color.WHITE
        for (i in 0 until n) {
            val x = r.nextFloat() * width
            val y = r.nextFloat().pow(1.4f) * horizon * 0.95f
            fill.alpha = (r.f(40f, 200f) * (1f - y / horizon * 0.7f)).toInt()
            c.drawCircle(x, y, r.f(0.25f, 0.9f) * unit, fill)
        }
        fill.alpha = 255
        repeat((n / 5).coerceIn(15, 70)) {
            stars.add(
                Star(
                    r.nextFloat() * width, r.nextFloat().pow(1.3f) * horizon * 0.85f,
                    r.f(0.7f, 1.8f) * unit, r.f(0.5f, 1f), r.nextFloat() * TAU, r.f(0.8f, 3f)
                )
            )
        }
    }

    private fun paintMoon(c: Canvas, r: Random) {
        val t = theme
        fill.shader = RadialGradient(
            moonX, moonY, moonR * 6f,
            intArrayOf(Colors.withAlpha(t.moonColor, 90), Colors.withAlpha(t.moonColor, 25), Colors.withAlpha(t.moonColor, 0)),
            floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(moonX, moonY, moonR * 6f, fill)
        fill.shader = RadialGradient(
            moonX - moonR * 0.3f, moonY - moonR * 0.3f, moonR * 1.4f,
            intArrayOf(Colors.lighten(t.moonColor, 0.4f), t.moonColor, Colors.darken(t.moonColor, 0.18f)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(moonX, moonY, moonR, fill)
        fill.shader = null
        repeat(7) {
            val a = r.nextFloat() * TAU
            val d = r.nextFloat().pow(0.7f) * moonR * 0.7f
            fill.color = Colors.darken(t.moonColor, 0.25f)
            fill.alpha = r.f(25f, 55f).toInt()
            c.drawCircle(moonX + cos(a) * d, moonY + sin(a) * d, r.f(0.08f, 0.24f) * moonR, fill)
        }
        fill.alpha = 255
    }

    private fun paintHill(c: Canvas, hill: Hill, top: Int, bottom: Int, rimAlpha: Int) {
        val w = width.toFloat()
        val h = height.toFloat()
        val step = max(4f, w / 180f)
        path.reset()
        path.moveTo(-step, h)
        var x = -step
        while (x <= w + step) {
            path.lineTo(x, hill.y(x)); x += step
        }
        path.lineTo(w + step, h)
        path.close()
        fill.shader = LinearGradient(0f, hill.base - hill.amp, 0f, h, top, bottom, Shader.TileMode.CLAMP)
        c.drawPath(path, fill)
        fill.shader = null
        // muchia luminată de lună
        path.reset()
        path.moveTo(-step, hill.y(-step))
        x = 0f
        while (x <= w + step) {
            path.lineTo(x, hill.y(x)); x += step
        }
        line.color = Colors.withAlpha(Color.WHITE, rimAlpha)
        line.strokeWidth = 1.6f * unit
        c.drawPath(path, line)
    }

    /** Conturul unui etaj de crengi, cu marginea de jos ondulată (vârfuri de ramuri). */
    private fun tierPath(p: Path, x: Float, apex: Float, bottom: Float, hw: Float, r: Random, scallops: Int) {
        val th = bottom - apex
        p.reset()
        p.moveTo(x, apex)
        p.quadTo(x - hw * 0.35f, apex + th * 0.55f, x - hw, bottom)
        val seg = 2f * hw / scallops
        for (s in 0 until scallops) {
            val x0 = x - hw + seg * s
            val x1 = x0 + seg
            val dip = th * (0.06f + r.nextFloat() * 0.05f)
            val endY = if (s == scallops - 1) bottom else bottom + th * 0.03f
            p.quadTo((x0 + x1) / 2f, bottom - dip, x1, endY)
        }
        p.quadTo(x + hw * 0.35f, apex + th * 0.55f, x, apex)
        p.close()
    }

    /** Zăpadă: căciulă pe vârf (doar etajul de sus) și moțuri pe capetele ramurilor. */
    private fun paintTierSnow(
        c: Canvas, x: Float, apex: Float, bottom: Float, hw: Float, r: Random,
        scallops: Int, snow: Int, shade: Int, isTop: Boolean
    ) {
        val th = bottom - apex
        if (isTop) {
            val capY = apex + th * r.f(0.32f, 0.44f)
            val capHalf = hw * ((capY - apex) / th) * 1.05f
            path.reset()
            path.moveTo(x, apex - th * 0.02f)
            path.quadTo(x - capHalf * 0.35f, apex + (capY - apex) * 0.55f, x - capHalf, capY)
            val drips = 4
            val seg = 2f * capHalf / drips
            for (d in 0 until drips) {
                val x0 = x - capHalf + seg * d
                path.quadTo(x0 + seg / 2f, capY + th * r.f(0.05f, 0.12f), x0 + seg, capY - th * 0.01f)
            }
            path.quadTo(x + capHalf * 0.35f, apex + (capY - apex) * 0.55f, x, apex - th * 0.02f)
            path.close()
            fill.shader = LinearGradient(x - hw, 0f, x + hw, 0f, snow, shade, Shader.TileMode.CLAMP)
            c.drawPath(path, fill)
            fill.shader = null
        }
        val seg = 2f * hw / scallops
        for (s in 0..scallops) {
            val tipX = x - hw + seg * s
            val y = bottom - th * 0.04f
            val half = seg * (if (s == 0 || s == scallops) 0.3f else 0.42f)
            fill.color = shade
            oval.set(tipX - half, y - th * 0.055f, tipX + half, y + th * 0.035f)
            c.drawOval(oval, fill)
            fill.color = snow
            oval.set(tipX - half, y - th * 0.075f, tipX + half, y + th * 0.015f)
            c.drawOval(oval, fill)
        }
    }

    private fun paintPine(c: Canvas, x: Float, baseY: Float, hgt: Float, fog: Float, r: Random) {
        val t = theme
        val dark = Colors.lerp(t.treeDark, t.skyBottom, fog)
        val light = Colors.lerp(t.treeLight, t.skyBottom, fog)
        val snow = Colors.lerp(0xFFF4F8FF.toInt(), t.skyBottom, fog * 0.7f)
        val shade = Colors.lerp(0xFFAFC0DA.toInt(), t.skyBottom, fog * 0.7f)
        val trunkH = hgt * 0.07f
        fill.color = Colors.lerp(0xFF3B2716.toInt(), t.skyBottom, fog)
        c.drawRect(x - hgt * 0.03f, baseY - trunkH * 1.5f, x + hgt * 0.03f, baseY, fill)
        val tiers = (4 + (hgt / (70f * unit)).toInt()).coerceAtMost(7)
        val crown = hgt - trunkH
        val maxHalf = hgt * r.f(0.26f, 0.32f)
        for (i in 0 until tiers) {
            val apex = baseY - trunkH - crown * (i + 1) / tiers
            val bottom = min(apex + crown / tiers * 1.75f, baseY - trunkH * 0.4f)
            val hw = maxHalf * (1f - i.toFloat() / tiers * 0.78f)
            tierPath(path, x, apex, bottom, hw, r, 5)
            fill.shader = LinearGradient(x - hw, 0f, x + hw, 0f, light, dark, Shader.TileMode.CLAMP)
            c.drawPath(path, fill)
            fill.shader = null
            paintTierSnow(c, x, apex, bottom, hw, r, 5, snow, shade, i == tiers - 1)
        }
    }

    private fun paintBigTree(c: Canvas, cx: Float, baseY: Float, hgt: Float, r: Random) {
        val t = theme
        // umbra pe zăpadă
        fill.shader = RadialGradient(cx, baseY, hgt * 0.45f, intArrayOf(0x55000814, 0x00000814), null, Shader.TileMode.CLAMP)
        c.save()
        c.scale(1f, 0.22f, cx, baseY)
        c.drawCircle(cx, baseY, hgt * 0.45f, fill)
        c.restore()
        // trunchi
        val trunkH = hgt * 0.07f
        fill.shader = LinearGradient(
            cx - hgt * 0.04f, 0f, cx + hgt * 0.04f, 0f,
            0xFF5A3A22.toInt(), 0xFF2A1A10.toInt(), Shader.TileMode.CLAMP
        )
        c.drawRect(cx - hgt * 0.04f, baseY - trunkH * 2f, cx + hgt * 0.04f, baseY, fill)
        fill.shader = null

        val tiers = 7
        val crown = hgt - trunkH
        val maxHalf = hgt * 0.36f
        val geo = ArrayList<FloatArray>()
        for (i in 0 until tiers) {
            val apex = baseY - trunkH - crown * (i + 1) / tiers
            val bottom = min(apex + crown / tiers * 1.7f, baseY - trunkH * 0.5f)
            val hw = maxHalf * (1f - i.toFloat() / tiers * 0.82f)
            geo.add(floatArrayOf(apex, bottom, hw))
        }
        val snow = 0xFFF7FAFF.toInt()
        val shade = 0xFFB9C8E0.toInt()
        for ((i, g) in geo.withIndex()) {
            val (apex, bottom, hw) = g
            val th = bottom - apex
            tierPath(path, cx, apex, bottom, hw, r, 7)
            fill.shader = LinearGradient(
                cx - hw, apex, cx + hw, bottom,
                intArrayOf(Colors.lighten(t.treeLight, 0.12f), t.treeLight, t.treeDark),
                floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP
            )
            c.drawPath(path, fill)
            fill.shader = null
            // textura de ace
            line.strokeWidth = 1.1f * unit
            repeat((hw / unit * 0.9f).toInt()) {
                val yy = apex + th * r.f(0.25f, 0.95f)
                val half = hw * (yy - apex) / th * 0.95f
                val xx = cx + r.f(-1f, 1f) * half
                line.color = if (r.nextBoolean()) Colors.withAlpha(Colors.lighten(t.treeLight, 0.25f), 70)
                else Colors.withAlpha(Colors.darken(t.treeDark, 0.4f), 80)
                val len = r.f(3f, 7f) * unit
                val ang = if (xx < cx) r.f(2.2f, 2.7f) else r.f(0.45f, 0.95f)
                c.drawLine(xx, yy, xx + cos(ang) * len, yy + sin(ang) * len, line)
            }
            paintTierSnow(c, cx, apex, bottom, hw, r, 7, snow, shade, i == tiers - 1)
        }

        // globuri
        val ornR = hgt * 0.022f
        for (g in geo) {
            val (apex, bottom, hw) = g
            val th = bottom - apex
            val count = (2 + (hw / (ornR * 5f)).toInt()).coerceAtMost(7)
            repeat(count) {
                val yy = apex + th * r.f(0.45f, 0.9f)
                val half = hw * (yy - apex) / th * 0.82f
                val xx = cx + r.f(-1f, 1f) * half
                paintBauble(c, xx, yy, ornR * r.f(0.75f, 1.25f), t.ornamentColors[r.nextInt(t.ornamentColors.size)])
            }
        }

        // instalație de lumini (becurile pâlpâie pe fiecare cadru)
        var idx = 0
        for (g in geo) {
            val (apex, bottom, hw) = g
            val th = bottom - apex
            val yA = apex + th * 0.58f
            val yB = apex + th * 0.8f
            val sag = th * 0.12f
            path.reset()
            for (k in 0..18) {
                treePoint(cx, apex, th, hw, yA, yB, sag, k / 18f, tmp)
                if (k == 0) path.moveTo(tmp.x, tmp.y) else path.lineTo(tmp.x, tmp.y)
            }
            line.color = 0xCC0A1A10.toInt()
            line.strokeWidth = max(0.8f, hgt * 0.003f)
            c.drawPath(path, line)
            val bulbs = max(4, (hw * 2f / (hgt * 0.04f)).toInt())
            for (k in 0 until bulbs) {
                treePoint(cx, apex, th, hw, yA, yB, sag, (k + 0.5f) / bulbs, tmp)
                lights.add(
                    Light(tmp.x, tmp.y, hgt * 0.0075f, t.lightColors[idx % t.lightColors.size], r.nextFloat() * TAU, r.f(1.2f, 3.2f))
                )
                idx++
            }
        }
        // beteală aurie
        for (g in geo) {
            val (apex, bottom, hw) = g
            val th = bottom - apex
            path.reset()
            for (k in 0..18) {
                treePoint(cx, apex, th, hw, apex + th * 0.88f, apex + th * 0.64f, th * 0.08f, k / 18f, tmp)
                if (k == 0) path.moveTo(tmp.x, tmp.y) else path.lineTo(tmp.x, tmp.y)
            }
            line.color = 0xC8F2C14E.toInt()
            line.strokeWidth = hgt * 0.006f
            c.drawPath(path, line)
            fill.color = 0xDDFFF6D5.toInt()
            for (k in 0..6) {
                treePoint(cx, apex, th, hw, apex + th * 0.88f, apex + th * 0.64f, th * 0.08f, r.nextFloat(), tmp)
                c.drawCircle(tmp.x, tmp.y, hgt * 0.003f, fill)
            }
        }

        // steaua din vârf (desenată animat)
        treeTopX = cx
        treeTopY = geo.last()[0] - hgt * 0.01f
        treeStarR = hgt * 0.05f
        starPath.reset()
        for (k in 0 until 10) {
            val rad = if (k % 2 == 0) treeStarR else treeStarR * 0.45f
            val a = -PI.toFloat() / 2f + k * TAU / 10f
            val px = treeTopX + cos(a) * rad
            val py = treeTopY + sin(a) * rad
            if (k == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
        }
        starPath.close()
        starPaint.shader = LinearGradient(
            treeTopX, treeTopY - treeStarR, treeTopX, treeTopY + treeStarR,
            0xFFFFF4B8.toInt(), 0xFFE0A21E.toInt(), Shader.TileMode.CLAMP
        )

        // cadouri
        val orn = t.ornamentColors
        paintGift(c, cx - hgt * 0.38f, baseY + hgt * 0.02f, hgt * 0.16f, hgt * 0.12f, orn[0], 0xFFF2C14E.toInt())
        paintGift(c, cx - hgt * 0.2f, baseY + hgt * 0.04f, hgt * 0.13f, hgt * 0.09f, orn[2 % orn.size], 0xFFF4F4F8.toInt())
        paintGift(c, cx + hgt * 0.1f, baseY + hgt * 0.035f, hgt * 0.18f, hgt * 0.1f, orn[1 % orn.size], 0xFFC8102E.toInt())
    }

    /** Punct pe o ghirlandă care traversează un etaj al bradului (u = 0..1, de la stânga la dreapta). */
    private fun treePoint(
        cx: Float, apex: Float, th: Float, hw: Float,
        yA: Float, yB: Float, sag: Float, u: Float, out: PointF
    ) {
        val yy = yA + (yB - yA) * u + sag * sin(u * PI.toFloat())
        val half = hw * (yy - apex) / th * 0.92f
        out.set(cx - half + 2f * half * u, yy)
    }

    private fun paintBauble(c: Canvas, x: Float, y: Float, rad: Float, col: Int) {
        line.color = 0x66D8C27A
        line.strokeWidth = max(0.6f * unit, rad * 0.08f)
        c.drawLine(x, y - rad * 1.9f, x, y - rad, line)
        fill.color = 0xFFC9A74A.toInt()
        c.drawRect(x - rad * 0.28f, y - rad * 1.2f, x + rad * 0.28f, y - rad * 0.85f, fill)
        fill.shader = RadialGradient(
            x - rad * 0.35f, y - rad * 0.4f, rad * 1.5f,
            intArrayOf(Colors.lighten(col, 0.55f), col, Colors.darken(col, 0.55f)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(x, y, rad, fill)
        fill.shader = null
        fill.color = Color.WHITE
        fill.alpha = 170
        oval.set(x - rad * 0.55f, y - rad * 0.62f, x - rad * 0.12f, y - rad * 0.3f)
        c.drawOval(oval, fill)
        fill.alpha = 255
    }

    private fun paintGift(c: Canvas, x: Float, baseY: Float, w: Float, h: Float, col: Int, ribbon: Int) {
        val depth = w * 0.22f
        val lift = depth * 0.6f
        // capac (fața de sus)
        path.reset()
        path.moveTo(x, baseY - h); path.lineTo(x + depth, baseY - h - lift)
        path.lineTo(x + w + depth, baseY - h - lift); path.lineTo(x + w, baseY - h); path.close()
        fill.color = Colors.lighten(col, 0.18f); c.drawPath(path, fill)
        // lateral
        path.reset()
        path.moveTo(x + w, baseY - h); path.lineTo(x + w + depth, baseY - h - lift)
        path.lineTo(x + w + depth, baseY - lift); path.lineTo(x + w, baseY); path.close()
        fill.color = Colors.darken(col, 0.35f); c.drawPath(path, fill)
        // față
        fill.shader = LinearGradient(0f, baseY - h, 0f, baseY, Colors.lighten(col, 0.05f), Colors.darken(col, 0.2f), Shader.TileMode.CLAMP)
        c.drawRect(x, baseY - h, x + w, baseY, fill)
        fill.shader = null
        // panglici
        val rw = w * 0.14f
        val mx = x + w / 2f
        val my = baseY - h / 2f
        fill.color = ribbon
        c.drawRect(mx - rw / 2f, baseY - h, mx + rw / 2f, baseY, fill)
        c.drawRect(x, my - rw / 2f, x + w, my + rw / 2f, fill)
        path.reset()
        path.moveTo(mx - rw / 2f, baseY - h); path.lineTo(mx - rw / 2f + depth, baseY - h - lift)
        path.lineTo(mx + rw / 2f + depth, baseY - h - lift); path.lineTo(mx + rw / 2f, baseY - h); path.close()
        fill.color = Colors.lighten(ribbon, 0.2f); c.drawPath(path, fill)
        path.reset()
        path.moveTo(x + w, my - rw / 2f); path.lineTo(x + w + depth, my - rw / 2f - lift)
        path.lineTo(x + w + depth, my + rw / 2f - lift); path.lineTo(x + w, my + rw / 2f); path.close()
        fill.color = Colors.darken(ribbon, 0.25f); c.drawPath(path, fill)
        // fundă
        val bx = mx + depth / 2f
        val by = baseY - h - lift / 2f
        fill.color = ribbon
        c.save(); c.rotate(-25f, bx, by)
        oval.set(bx - rw * 1.9f, by - rw * 0.7f, bx, by + rw * 0.5f); c.drawOval(oval, fill)
        c.restore()
        c.save(); c.rotate(25f, bx, by)
        oval.set(bx, by - rw * 0.7f, bx + rw * 1.9f, by + rw * 0.5f); c.drawOval(oval, fill)
        c.restore()
        fill.color = Colors.darken(ribbon, 0.2f)
        c.drawCircle(bx, by, rw * 0.45f, fill)
        // puțină zăpadă pe capac
        fill.color = 0xD9F7FAFF.toInt()
        oval.set(x + w * 0.05f, baseY - h - lift * 0.8f, x + w * 0.5f, baseY - h - lift * 0.2f)
        c.drawOval(oval, fill)
    }

    private fun paintHouse(c: Canvas, cx: Float, groundY: Float, s: Float, fog: Float, r: Random) {
        val t = theme
        val palette = intArrayOf(0xFF7A4E33.toInt(), 0xFFB98A5E.toInt(), 0xFFD8CCB4.toInt(), 0xFF8C3B2E.toInt())
        val wall = Colors.lerp(Colors.darken(palette[r.nextInt(palette.size)], 0.35f), t.skyBottom, fog)
        val roof = Colors.lerp(0xFF3A2422.toInt(), t.skyBottom, fog)
        val snowC = Colors.lerp(0xFFF5F9FF.toInt(), t.skyBottom, fog * 0.6f)
        val snowShade = Colors.lerp(0xFFB4C4DE.toInt(), t.skyBottom, fog * 0.6f)
        val win = t.windowColor
        val bw = s
        val bh = s * 0.6f
        val left = cx - bw / 2f
        val top = groundY - bh

        // lumină caldă pe zăpadă
        fill.shader = RadialGradient(cx, groundY + s * 0.08f, s * 0.9f, intArrayOf(Colors.withAlpha(win, 70), Colors.withAlpha(win, 0)), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, groundY + s * 0.08f, s * 0.9f, fill)
        fill.shader = null

        // horn
        val chX = cx + bw * 0.22f
        fill.color = Colors.lerp(0xFF5A3B33.toInt(), t.skyBottom, fog)
        c.drawRect(chX, top - s * 0.42f, chX + s * 0.1f, top - s * 0.1f, fill)
        chimneys.add(PointF(chX + s * 0.05f, top - s * 0.44f))
        fill.color = snowC
        oval.set(chX - s * 0.015f, top - s * 0.46f, chX + s * 0.115f, top - s * 0.4f)
        c.drawOval(oval, fill)

        // pereți din scânduri
        fill.shader = LinearGradient(left, top, left + bw, groundY, Colors.lighten(wall, 0.08f), Colors.darken(wall, 0.25f), Shader.TileMode.CLAMP)
        c.drawRect(left, top, left + bw, groundY, fill)
        fill.shader = null
        line.color = Colors.withAlpha(Colors.darken(wall, 0.4f), 120)
        line.strokeWidth = max(0.6f, s * 0.008f)
        var py = top + bh / 6f
        while (py < groundY) {
            c.drawLine(left, py, left + bw, py, line); py += bh / 6f
        }

        // acoperiș + zăpadă
        val overhang = bw * 0.12f
        val roofTop = top - s * 0.45f
        val eaveY = top + s * 0.02f
        path.reset()
        path.moveTo(left - overhang, eaveY); path.lineTo(cx, roofTop); path.lineTo(left + bw + overhang, eaveY); path.close()
        fill.color = roof
        c.drawPath(path, fill)
        val f = 0.78f
        val lx = cx + (left - overhang - cx) * f
        val rx = cx + (left + bw + overhang - cx) * f
        val ly = roofTop + (eaveY - roofTop) * f
        path.reset()
        path.moveTo(lx - s * 0.02f, ly); path.lineTo(cx, roofTop - s * 0.04f); path.lineTo(rx + s * 0.02f, ly)
        val drips = 6
        val seg = (rx - lx + s * 0.04f) / drips
        var dx = rx + s * 0.02f
        repeat(drips) {
            val nx = dx - seg
            path.quadTo(dx - seg / 2f, ly + s * r.f(0.04f, 0.09f), nx, ly - s * 0.005f)
            dx = nx
        }
        path.close()
        fill.shader = LinearGradient(lx, 0f, rx, 0f, snowC, snowShade, Shader.TileMode.CLAMP)
        c.drawPath(path, fill)
        fill.shader = null

        // ferestre luminate
        val ww = bw * 0.18f
        val wh = bh * 0.38f
        val wy = top + bh * 0.25f
        for (wx in floatArrayOf(left + bw * 0.1f, left + bw * 0.72f)) {
            fill.shader = RadialGradient(wx + ww / 2f, wy + wh / 2f, ww * 2.4f, intArrayOf(Colors.withAlpha(win, (120 * (1f - fog)).toInt()), Colors.withAlpha(win, 0)), null, Shader.TileMode.CLAMP)
            c.drawCircle(wx + ww / 2f, wy + wh / 2f, ww * 2.4f, fill)
            fill.shader = LinearGradient(0f, wy, 0f, wy + wh, Colors.lighten(win, 0.35f), win, Shader.TileMode.CLAMP)
            c.drawRect(wx, wy, wx + ww, wy + wh, fill)
            fill.shader = null
            line.color = Colors.darken(wall, 0.5f)
            line.strokeWidth = max(1f, s * 0.012f)
            c.drawLine(wx + ww / 2f, wy, wx + ww / 2f, wy + wh, line)
            c.drawLine(wx, wy + wh / 2f, wx + ww, wy + wh / 2f, line)
            c.drawRect(wx, wy, wx + ww, wy + wh, line)
            fill.color = snowC
            oval.set(wx - ww * 0.12f, wy + wh - s * 0.01f, wx + ww * 1.12f, wy + wh + s * 0.035f)
            c.drawOval(oval, fill)
        }
        // ușa cu coroniță
        fill.color = Colors.darken(wall, 0.45f)
        c.drawRect(cx - bw * 0.08f, groundY - bh * 0.58f, cx + bw * 0.08f, groundY, fill)
        line.color = 0xFF1F6B3A.toInt()
        line.strokeWidth = max(1f, s * 0.018f)
        c.drawCircle(cx, groundY - bh * 0.42f, bw * 0.045f, line)
        fill.color = 0xFFD7263D.toInt()
        c.drawCircle(cx, groundY - bh * 0.36f, s * 0.012f, fill)

        // luminițe sub streașină
        val n = 10
        for (k in 0..n) {
            val u = k / n.toFloat()
            val x = left - overhang * 0.8f + (bw + overhang * 1.6f) * u
            val y = eaveY + s * 0.03f + sin(u * TAU * 2f).let { if (it > 0) it else -it } * s * 0.025f
            lights.add(Light(x, y, s * 0.016f, theme.lightColors[k % theme.lightColors.size], r.nextFloat() * TAU, r.f(1.5f, 3.5f)))
        }
    }

    private fun paintChurch(c: Canvas, cx: Float, groundY: Float, s: Float, fog: Float) {
        val t = theme
        val wall = Colors.lerp(0xFFE9E0CF.toInt(), t.skyBottom, fog + 0.25f)
        val roof = Colors.lerp(0xFF2E2A33.toInt(), t.skyBottom, fog)
        val snowC = Colors.lerp(0xFFF5F9FF.toInt(), t.skyBottom, fog * 0.6f)
        val win = t.windowColor

        fill.shader = RadialGradient(cx + s * 0.2f, groundY, s * 1.4f, intArrayOf(Colors.withAlpha(win, 60), Colors.withAlpha(win, 0)), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx + s * 0.2f, groundY, s * 1.4f, fill)
        fill.shader = null

        // naos
        val nl = cx - s * 0.2f
        val nr = cx + s * 0.75f
        val nt = groundY - s * 0.5f
        fill.color = wall
        c.drawRect(nl, nt, nr, groundY, fill)
        path.reset(); path.moveTo(nl - s * 0.05f, nt); path.lineTo((nl + nr) / 2f, nt - s * 0.35f); path.lineTo(nr + s * 0.05f, nt); path.close()
        fill.color = roof; c.drawPath(path, fill)
        path.reset(); path.moveTo(nl, nt - s * 0.06f); path.lineTo((nl + nr) / 2f, nt - s * 0.37f); path.lineTo(nr, nt - s * 0.06f); path.close()
        fill.color = snowC; c.drawPath(path, fill)
        // ferestre în arc
        for (k in 0..2) {
            val wx = nl + s * 0.3f + k * s * 0.2f
            val wy = nt + s * 0.14f
            val ww = s * 0.08f
            fill.color = win
            c.drawRect(wx, wy, wx + ww, wy + s * 0.18f, fill)
            c.drawCircle(wx + ww / 2f, wy, ww / 2f, fill)
        }
        // turn și turla
        val tl = cx - s * 0.38f
        val tr = cx - s * 0.05f
        val tt = groundY - s * 1.05f
        fill.color = Colors.darken(wall, 0.06f)
        c.drawRect(tl, tt, tr, groundY, fill)
        fill.color = win
        c.drawRect(tl + s * 0.11f, tt + s * 0.2f, tr - s * 0.11f, tt + s * 0.38f, fill)
        c.drawCircle((tl + tr) / 2f, tt + s * 0.2f, (tr - tl - s * 0.22f) / 2f, fill)
        fill.color = Colors.darken(wall, 0.45f)
        c.drawRect((tl + tr) / 2f - s * 0.06f, groundY - s * 0.25f, (tl + tr) / 2f + s * 0.06f, groundY, fill)
        path.reset(); path.moveTo(tl - s * 0.05f, tt); path.lineTo((tl + tr) / 2f, tt - s * 0.75f); path.lineTo(tr + s * 0.05f, tt); path.close()
        fill.color = roof; c.drawPath(path, fill)
        path.reset(); path.moveTo(tl - s * 0.03f, tt - s * 0.01f); path.lineTo((tl + tr) / 2f, tt - s * 0.72f); path.lineTo((tl + tr) / 2f - s * 0.02f, tt - s * 0.2f); path.close()
        fill.color = Colors.withAlpha(snowC, 200); c.drawPath(path, fill)
        val crX = (tl + tr) / 2f
        val crY = tt - s * 0.75f
        line.color = 0xFFE8C66A.toInt()
        line.strokeWidth = max(1.2f, s * 0.025f)
        c.drawLine(crX, crY, crX, crY - s * 0.18f, line)
        c.drawLine(crX - s * 0.06f, crY - s * 0.12f, crX + s * 0.06f, crY - s * 0.12f, line)
    }

    private fun paintSnowman(c: Canvas, x: Float, groundY: Float, s: Float) {
        fill.color = 0x33000A20
        oval.set(x - s * 0.9f, groundY - s * 0.12f, x + s * 0.9f, groundY + s * 0.12f)
        c.drawOval(oval, fill)
        val r0 = s * 0.6f
        val r1 = s * 0.44f
        val r2 = s * 0.32f
        val y0 = groundY - r0 * 0.9f
        val y1 = y0 - r0 - r1 * 0.7f
        val y2 = y1 - r1 - r2 * 0.7f
        for ((cy, rr) in listOf(y0 to r0, y1 to r1, y2 to r2)) {
            fill.shader = RadialGradient(
                x - rr * 0.35f, cy - rr * 0.4f, rr * 1.5f,
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFE6EEF8.toInt(), 0xFFA9B9D2.toInt()),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
            c.drawCircle(x, cy, rr, fill)
        }
        fill.shader = null
        line.color = 0xFF4A3020.toInt()
        line.strokeWidth = s * 0.04f
        c.drawLine(x - r1 * 0.8f, y1, x - r1 * 2f, y1 - r1 * 0.8f, line)
        c.drawLine(x - r1 * 1.6f, y1 - r1 * 0.5f, x - r1 * 1.9f, y1 - r1 * 1.1f, line)
        c.drawLine(x + r1 * 0.8f, y1, x + r1 * 2f, y1 - r1 * 0.6f, line)
        fill.color = 0xFF1A1A1F.toInt()
        for (k in 0..2) c.drawCircle(x, y1 - r1 * 0.45f + k * r1 * 0.42f, s * 0.035f, fill)
        c.drawCircle(x - r2 * 0.35f, y2 - r2 * 0.15f, s * 0.035f, fill)
        c.drawCircle(x + r2 * 0.35f, y2 - r2 * 0.15f, s * 0.035f, fill)
        path.reset()
        path.moveTo(x, y2 + r2 * 0.05f - s * 0.03f)
        path.lineTo(x + r2 * 0.9f, y2 + r2 * 0.15f)
        path.lineTo(x, y2 + r2 * 0.05f + s * 0.03f)
        path.close()
        fill.color = 0xFFF07A1A.toInt()
        c.drawPath(path, fill)
        val scarf = theme.accent
        fill.color = scarf
        oval.set(x - r2 * 1.05f, y2 + r2 * 0.6f, x + r2 * 1.05f, y2 + r2 * 1.05f)
        c.drawOval(oval, fill)
        c.drawRect(x + r2 * 0.3f, y2 + r2 * 0.8f, x + r2 * 0.65f, y2 + r2 * 1.9f, fill)
        fill.color = 0xFF16161C.toInt()
        c.drawRect(x - r2 * 0.95f, y2 - r2 * 0.85f, x + r2 * 0.95f, y2 - r2 * 0.7f, fill)
        c.drawRect(x - r2 * 0.6f, y2 - r2 * 1.75f, x + r2 * 0.6f, y2 - r2 * 0.8f, fill)
        fill.color = scarf
        c.drawRect(x - r2 * 0.6f, y2 - r2 * 1.0f, x + r2 * 0.6f, y2 - r2 * 0.85f, fill)
        fill.color = 0xFFF5F9FF.toInt()
        oval.set(x - r2 * 0.62f, y2 - r2 * 1.85f, x + r2 * 0.62f, y2 - r2 * 1.65f)
        c.drawOval(oval, fill)
    }

    private fun buildAurora(r: Random) {
        val cols = theme.auroraColors
        for (i in cols.indices) {
            val baseY = topInset + height * (0.16f + 0.07f * i)
            val thick = height * (0.12f + 0.03f * i)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(
                0f, baseY - thick, 0f, baseY + thick * 0.18f,
                intArrayOf(Colors.withAlpha(cols[i], 0), cols[i], Colors.withAlpha(cols[i], 0)),
                floatArrayOf(0f, 0.82f, 1f), Shader.TileMode.CLAMP
            )
            aurora.add(AuroraBand(baseY, thick, r.nextFloat() * TAU, r.f(0.15f, 0.35f), p))
        }
    }

    private fun buildGarland() {
        garlandWire.reset()
        bulbs.clear()
        val w = width.toFloat()
        val y0 = topInset + 2f * unit
        val swags = max(2, (w / (150f * unit)).roundToInt())
        val swagW = w / swags
        val sag = 16f * unit
        val spacing = 22f * unit
        var idx = 0
        garlandWire.moveTo(0f, y0)
        for (s in 0 until swags) {
            val xa = s * swagW
            for (k in 1..24) {
                val u = k / 24f
                garlandWire.lineTo(xa + u * swagW, y0 + sag * 4f * u * (1f - u))
            }
            val n = max(3, (swagW / spacing).toInt())
            for (k in 1 until n) {
                val u = k / n.toFloat()
                val slope = sag * 4f * (1f - 2f * u) / swagW
                bulbs.add(
                    Bulb(
                        xa + u * swagW, y0 + sag * 4f * u * (1f - u),
                        Math.toDegrees(atan(slope).toDouble()).toFloat() * 0.5f,
                        theme.lightColors[idx % theme.lightColors.size], idx
                    )
                )
                idx++
            }
        }
    }

    private fun buildBokeh(r: Random) {
        repeat(16) {
            bokeh.add(
                Bokeh(
                    r.nextFloat() * width, r.nextFloat() * height * 0.85f, r.f(14f, 42f) * unit,
                    theme.lightColors[r.nextInt(theme.lightColors.size)],
                    r.f(-3f, 3f) * unit, r.f(-8f, -3f) * unit, r.nextFloat() * TAU
                )
            )
        }
    }

    private fun buildFlakes() {
        flakesDirty = false
        flakes.clear()
        val n = (75f * snowDensity * width * height / (unit * unit * 160000f)).toInt().coerceIn(10, 600)
        repeat(n) {
            val f = Flake(0f, 0f, 1f, 1f, 1f, 0f, 0, 255, false, 0f, 0f)
            respawn(f, true)
            flakes.add(f)
        }
    }

    private fun respawn(f: Flake, anywhere: Boolean) {
        val roll = rnd.nextFloat()
        f.layer = if (roll < 0.45f) 0 else if (roll < 0.8f) 1 else 2
        f.r = unit * when (f.layer) {
            0 -> rnd.f(0.8f, 1.4f)
            1 -> rnd.f(1.6f, 2.6f)
            else -> rnd.f(3.2f, 5.5f)
        }
        f.speed = unit * when (f.layer) {
            0 -> rnd.f(14f, 24f)
            1 -> rnd.f(26f, 40f)
            else -> rnd.f(48f, 72f)
        }
        f.alpha = when (f.layer) {
            0 -> rnd.f(110f, 170f)
            1 -> rnd.f(170f, 230f)
            else -> rnd.f(130f, 200f)
        }.toInt()
        f.x = rnd.nextFloat() * width
        f.y = if (anywhere) rnd.nextFloat() * height else -f.r * 3f
        f.sway = rnd.f(0.5f, 1.6f)
        f.phase = rnd.nextFloat() * TAU
        f.crystal = f.layer == 2 && rnd.nextFloat() < 0.35f
        f.rot = rnd.nextFloat() * 360f
        f.rotSpeed = rnd.f(-40f, 40f)
    }

    // ------------------------------------------------------- efecte animate

    private fun tint(color: Int): Paint {
        val key = color or 0xFF000000.toInt()
        return tinted.getOrPut(key) {
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = PorterDuffColorFilter(key, PorterDuff.Mode.SRC_IN)
            }
        }
    }

    private fun glowAt(c: Canvas, x: Float, y: Float, radius: Float, color: Int, alpha: Int) {
        if (alpha <= 0 || radius <= 0f) return
        val p = tint(color)
        p.alpha = alpha.coerceIn(0, 255)
        dst.set(x - radius, y - radius, x + radius, y + radius)
        c.drawBitmap(glow, null, dst, p)
    }

    private fun wave(x: Float, b: AuroraBand, amp: Float): Float =
        amp * sin(x / width * TAU * 1.3f + time * b.speed + b.phase) +
            amp * 0.5f * sin(x / width * TAU * 3.1f - time * b.speed * 1.7f + b.phase * 2f)

    private fun drawAurora(c: Canvas) {
        val w = width.toFloat()
        val step = max(6f, w / 90f)
        val amp = height * 0.025f
        for (b in aurora) {
            path.reset()
            var x = -step
            path.moveTo(x, b.baseY - b.thick + wave(x, b, amp) * 1.3f)
            while (x <= w + step) {
                path.lineTo(x, b.baseY - b.thick + wave(x, b, amp) * 1.3f); x += step
            }
            x = w + step
            while (x >= -step) {
                path.lineTo(x, b.baseY + b.thick * 0.18f + wave(x, b, amp)); x -= step
            }
            path.close()
            b.paint.alpha = (170 + 70 * sin(time * 0.4f + b.phase)).toInt().coerceIn(0, 255)
            c.drawPath(path, b.paint)
        }
    }

    private fun drawStars(c: Canvas) {
        fill.color = Color.WHITE
        for (s in stars) {
            val a = s.base * (0.45f + 0.55f * (0.5f + 0.5f * sin(time * s.speed + s.phase)))
            fill.alpha = (a * 255).toInt()
            c.drawCircle(s.x, s.y, s.r * 0.6f, fill)
            if (s.r > 1.3f * unit) glowAt(c, s.x, s.y, s.r * 3f, 0xFFDDE8FF.toInt(), (a * 120).toInt())
        }
        fill.alpha = 255
    }

    private fun drawShootingStar(c: Canvas, dt: Float) {
        if (shootLife <= 0f) {
            nextShoot -= dt
            if (nextShoot <= 0f) {
                nextShoot = rnd.f(7f, 16f)
                shootLife = 1f
                shootX = rnd.f(0.35f, 1f) * width
                shootY = topInset + rnd.f(0.03f, 0.25f) * height
                val ang = rnd.f(2.45f, 2.8f)
                val sp = width * rnd.f(0.8f, 1.2f)
                shootVX = cos(ang) * sp
                shootVY = sin(ang) * sp
            }
            return
        }
        shootLife -= dt * 1.1f
        shootX += shootVX * dt
        shootY += shootVY * dt
        val fade = sin(shootLife.coerceIn(0f, 1f) * PI.toFloat())
        val tail = 0.12f
        line.color = Color.WHITE
        for (k in 0 until 10) {
            val f0 = k / 10f
            val f1 = (k + 1) / 10f
            line.strokeWidth = (1.8f - f0 * 1.4f) * unit
            line.alpha = ((1f - f0) * 230 * fade).toInt()
            c.drawLine(
                shootX - shootVX * tail * f0, shootY - shootVY * tail * f0,
                shootX - shootVX * tail * f1, shootY - shootVY * tail * f1, line
            )
        }
        glowAt(c, shootX, shootY, 6f * unit, 0xFFE8F0FF.toInt(), (220 * fade).toInt())
    }

    private fun drawSleigh(c: Canvas, dt: Float) {
        val every = theme.sleighEverySec
        if (every <= 0f) return
        if (sleighP < 0f) {
            nextSleigh -= dt
            if (nextSleigh <= 0f) {
                sleighP = 0f
                nextSleigh = every * rnd.f(0.8f, 1.3f)
            }
            return
        }
        sleighP += dt / 10f
        if (sleighP > 1f) {
            sleighP = -1f
            return
        }
        val w = width.toFloat()
        val x = -0.25f * w + 1.5f * w * sleighP
        val y = moonY + height * 0.05f - height * 0.13f * sleighP + sin(time * 3f) * 2f * unit
        val s = unit * (if (every < 15f) 1.25f else 1f)
        paintSleigh(c, x, y, s)
        if (rnd.nextFloat() < 0.8f) {
            sparks.add(
                Spark(
                    x - 14f * s, y + 6f * s, rnd.f(-20f, -5f) * unit, rnd.f(-5f, 15f) * unit,
                    0f, rnd.f(0.6f, 1.2f), 0xFFFFD86B.toInt(), rnd.f(0.8f, 1.6f) * unit
                )
            )
        }
    }

    private fun paintSleigh(c: Canvas, x: Float, y: Float, s: Float) {
        val sc = 0xEE120C12.toInt()
        fill.color = sc
        line.color = sc
        path.reset()
        path.moveTo(x - 16f * s, y - 8f * s)
        path.quadTo(x - 18f * s, y + 2f * s, x - 10f * s, y + 4f * s)
        path.lineTo(x + 8f * s, y + 4f * s)
        path.quadTo(x + 14f * s, y + 3f * s, x + 13f * s, y - 3f * s)
        path.lineTo(x + 6f * s, y - 2f * s)
        path.lineTo(x + 4f * s, y - 5f * s)
        path.lineTo(x - 8f * s, y - 5f * s)
        path.close()
        c.drawPath(path, fill)
        line.strokeWidth = 1.2f * s
        path.reset()
        path.moveTo(x - 14f * s, y + 7f * s)
        path.lineTo(x + 12f * s, y + 7f * s)
        path.quadTo(x + 17f * s, y + 7f * s, x + 16f * s, y + 3f * s)
        c.drawPath(path, line)
        c.drawLine(x - 9f * s, y + 4f * s, x - 9f * s, y + 7f * s, line)
        c.drawLine(x + 6f * s, y + 4f * s, x + 6f * s, y + 7f * s, line)
        c.drawCircle(x - 2f * s, y - 9f * s, 2.8f * s, fill)
        oval.set(x - 6f * s, y - 8f * s, x + 2f * s, y - 2f * s)
        c.drawOval(oval, fill)
        c.drawCircle(x - 10f * s, y - 8f * s, 4.2f * s, fill)
        line.strokeWidth = 0.6f * s
        c.drawLine(x + 1f * s, y - 6f * s, x + 22f * s, y - 4f * s, line)
        for (k in 0 until 4) {
            val rx = x + 24f * s + k * 15f * s
            val ry = y - 4f * s + sin(time * 6f + k) * 1.5f * s - k * 1.2f * s
            paintReindeer(c, rx, ry, s, k, sc)
        }
    }

    private fun paintReindeer(c: Canvas, x: Float, y: Float, s: Float, k: Int, sc: Int) {
        fill.color = sc
        line.color = sc
        line.strokeWidth = 1f * s
        oval.set(x - 6f * s, y - 2.5f * s, x + 6f * s, y + 2.5f * s)
        c.drawOval(oval, fill)
        c.drawLine(x + 4.5f * s, y - 1f * s, x + 7.5f * s, y - 5f * s, line)
        oval.set(x + 6.5f * s, y - 7f * s, x + 10f * s, y - 4.5f * s)
        c.drawOval(oval, fill)
        c.drawLine(x + 7.5f * s, y - 6.8f * s, x + 6f * s, y - 10f * s, line)
        c.drawLine(x + 6.6f * s, y - 8.7f * s, x + 4.8f * s, y - 9.5f * s, line)
        c.drawLine(x + 8.3f * s, y - 6.8f * s, x + 9f * s, y - 10f * s, line)
        val g = sin(time * 10f + k) * 0.6f
        val af = 0.6f + g
        val ab = -0.6f - g
        c.drawLine(x + 4f * s, y + 1.5f * s, x + 4f * s + sin(af) * 5f * s, y + 1.5f * s + cos(af) * 5f * s, line)
        c.drawLine(x + 2.5f * s, y + 1.5f * s, x + 2.5f * s + sin(af * 0.6f) * 5f * s, y + 1.5f * s + cos(af * 0.6f) * 5f * s, line)
        c.drawLine(x - 4f * s, y + 1.5f * s, x - 4f * s + sin(ab) * 5f * s, y + 1.5f * s + cos(ab) * 5f * s, line)
        c.drawLine(x - 2.5f * s, y + 1.5f * s, x - 2.5f * s + sin(ab * 0.6f) * 5f * s, y + 1.5f * s + cos(ab * 0.6f) * 5f * s, line)
        if (k == 3) {
            // Rudolf, cu nasul roșu
            glowAt(c, x + 10.3f * s, y - 5.6f * s, 6f * s, 0xFFFF2A2A.toInt(), 230)
            fill.color = 0xFFFF4040.toInt()
            c.drawCircle(x + 10.3f * s, y - 5.6f * s, 1.1f * s, fill)
            fill.color = sc
        }
    }

    private fun drawBokeh(c: Canvas, dt: Float) {
        for (b in bokeh) {
            b.x += b.vx * dt
            b.y += b.vy * dt
            if (b.y < -b.r) {
                b.y = height + b.r
                b.x = rnd.nextFloat() * width
            }
            val a = (40 + 28 * sin(time * 0.7f + b.phase)).toInt()
            fill.color = b.color
            fill.alpha = a / 2
            c.drawCircle(b.x, b.y, b.r * 0.7f, fill)
            glowAt(c, b.x, b.y, b.r * 1.2f, b.color, a)
        }
        fill.alpha = 255
    }

    private fun drawSmoke(c: Canvas, dt: Float) {
        if (chimneys.isEmpty()) return
        smokeTimer -= dt
        if (smokeTimer <= 0f && dt > 0f) {
            smokeTimer = 0.35f
            for (ch in chimneys) smoke.add(Smoke(ch.x, ch.y, 2.5f * unit, 0f, rnd.f(3f, 4.5f), rnd.f(2f, 6f) * unit))
        }
        val it = smoke.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life += dt
            if (p.life >= p.maxLife) {
                it.remove(); continue
            }
            val f = p.life / p.maxLife
            p.y -= 9f * unit * dt
            p.x += (p.vx + 4f * unit * sin(time * 0.5f)) * dt
            p.r += 3.2f * unit * dt
            glowAt(c, p.x, p.y, p.r * 2.2f, 0xFFB8C2D6.toInt(), ((1f - f) * 70f * min(1f, f * 6f)).toInt())
        }
    }

    private fun drawLights(c: Canvas) {
        for (l in lights) {
            val inten = 0.35f + 0.65f * (0.5f + 0.5f * sin(time * l.speed + l.phase))
            glowAt(c, l.x, l.y, l.r * 7f, l.color, (inten * 190).toInt())
            fill.color = Colors.lighten(l.color, 0.55f * inten)
            fill.alpha = (150 + 105 * inten).toInt()
            c.drawCircle(l.x, l.y, l.r, fill)
        }
        fill.alpha = 255
    }

    private fun drawTreeStar(c: Canvas) {
        if (treeStarR <= 0f) return
        val pulse = 0.75f + 0.25f * sin(time * 2.2f)
        glowAt(c, treeTopX, treeTopY, treeStarR * 5f * pulse, 0xFFFFE08A.toInt(), (200 * pulse).toInt())
        line.color = 0xFFFFF3C4.toInt()
        line.strokeWidth = 1.1f * unit
        line.alpha = 110
        for (k in 0 until 4) {
            val a = time * 0.25f + k * TAU / 8f
            val len = treeStarR * (2.2f + 0.6f * sin(time * 1.5f + k))
            c.drawLine(treeTopX - cos(a) * len, treeTopY - sin(a) * len, treeTopX + cos(a) * len, treeTopY + sin(a) * len, line)
        }
        c.drawPath(starPath, starPaint)
        glowAt(c, treeTopX, treeTopY, treeStarR * 0.9f, Color.WHITE, (180 * pulse).toInt())
    }

    private fun drawGlints(c: Canvas) {
        line.strokeWidth = 0.8f * unit
        for (g in glints) {
            val s = sin(time * g.speed + g.phase)
            if (s < 0.75f) continue
            val a = ((s - 0.75f) / 0.25f).pow(2)
            line.color = Color.WHITE
            line.alpha = (a * 230).toInt()
            val len = g.r * 3f * a
            c.drawLine(g.x - len, g.y, g.x + len, g.y, line)
            c.drawLine(g.x, g.y - len, g.x, g.y + len, line)
            glowAt(c, g.x, g.y, g.r * 3f, 0xFFDDEBFF.toInt(), (a * 150).toInt())
        }
    }

    private fun drawSnow(c: Canvas, dt: Float) {
        val wind = (sin(time * 0.13f) * 14f + sin(time * 0.41f) * 6f) * unit
        val w = width.toFloat()
        val h = height.toFloat()
        for (f in flakes) {
            f.y += f.speed * dt
            f.x += (wind * (0.4f + f.layer * 0.35f) + sin(time * f.sway + f.phase) * 8f * unit) * dt
            f.rot += f.rotSpeed * dt
            if (f.y > h + f.r * 3f) respawn(f, false)
            if (f.x < -f.r * 3f) f.x += w + f.r * 6f else if (f.x > w + f.r * 3f) f.x -= w + f.r * 6f
            if (f.crystal) {
                paintCrystal(c, f)
            } else {
                flakePaint.alpha = f.alpha
                val rr = f.r * 1.7f
                dst.set(f.x - rr, f.y - rr, f.x + rr, f.y + rr)
                c.drawBitmap(flakeSprite, null, dst, flakePaint)
            }
        }
    }

    private fun paintCrystal(c: Canvas, f: Flake) {
        line.color = Color.WHITE
        line.alpha = f.alpha
        line.strokeWidth = max(0.8f, f.r * 0.18f)
        val big = f.r * 1.5f
        for (k in 0 until 6) {
            val a = Math.toRadians((f.rot + k * 60f).toDouble()).toFloat()
            c.drawLine(f.x, f.y, f.x + cos(a) * big, f.y + sin(a) * big, line)
            val bx = f.x + cos(a) * big * 0.55f
            val by = f.y + sin(a) * big * 0.55f
            val bl = big * 0.35f
            c.drawLine(bx, by, bx + cos(a + 0.8f) * bl, by + sin(a + 0.8f) * bl, line)
            c.drawLine(bx, by, bx + cos(a - 0.8f) * bl, by + sin(a - 0.8f) * bl, line)
        }
        glowAt(c, f.x, f.y, big * 1.4f, Color.WHITE, f.alpha / 3)
    }

    private fun drawGarland(c: Canvas) {
        line.color = 0xFF1B2A1E.toInt()
        line.strokeWidth = 1.4f * unit
        c.drawPath(garlandWire, line)
        val bw = 3.2f * unit
        val bh = 5.4f * unit
        for (b in bulbs) {
            val inten = 0.2f + 0.8f * (0.5f + 0.5f * cos(b.index * TAU / 3f - time * 2.6f))
            c.save()
            c.rotate(b.angle, b.x, b.y)
            glowAt(c, b.x, b.y + bh * 1.2f, 13f * unit, b.color, (inten * 210).toInt())
            fill.color = 0xFF3A3F45.toInt()
            c.drawRect(b.x - bw * 0.45f, b.y, b.x + bw * 0.45f, b.y + bh * 0.45f, fill)
            fill.color = Colors.lerp(Colors.darken(b.color, 0.55f), Colors.lighten(b.color, 0.35f), inten)
            oval.set(b.x - bw, b.y + bh * 0.35f, b.x + bw, b.y + bh * 2.1f)
            c.drawOval(oval, fill)
            fill.color = Color.WHITE
            fill.alpha = (70 + 120 * inten).toInt()
            oval.set(b.x - bw * 0.55f, b.y + bh * 0.6f, b.x - bw * 0.1f, b.y + bh * 1.3f)
            c.drawOval(oval, fill)
            fill.alpha = 255
            c.restore()
        }
    }

    private fun drawSparks(c: Canvas, dt: Float) {
        line.strokeWidth = 0.9f * unit
        val it = sparks.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.life += dt
            if (s.life >= s.maxLife) {
                it.remove(); continue
            }
            s.vy += 30f * unit * dt
            s.vx *= (1f - 1.5f * dt)
            s.x += s.vx * dt
            s.y += s.vy * dt
            val f = 1f - s.life / s.maxLife
            glowAt(c, s.x, s.y, s.size * 5f, s.color, (f * 200).toInt())
            line.color = Color.WHITE
            line.alpha = (f * 255).toInt()
            val len = s.size * 2.2f * f
            c.drawLine(s.x - len, s.y, s.x + len, s.y, line)
            c.drawLine(s.x, s.y - len, s.x, s.y + len, line)
        }
        if (sparks.size > 400) sparks.subList(0, sparks.size - 400).clear()
    }
}
