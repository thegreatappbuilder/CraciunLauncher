package ro.craciun.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import ro.craciun.launcher.apps.AppRepository
import ro.craciun.launcher.apps.FestiveIconFactory
import ro.craciun.launcher.apps.IconStyle
import ro.craciun.launcher.scene.ChristmasRenderer
import ro.craciun.launcher.scene.ChristmasSceneView
import ro.craciun.launcher.theme.ChristmasTheme
import ro.craciun.launcher.theme.ThemeCatalog
import ro.craciun.launcher.ui.DeviceProfile
import ro.craciun.launcher.ui.Drawables
import ro.craciun.launcher.ui.Edge
import ro.craciun.launcher.util.Colors
import ro.craciun.launcher.util.Prefs
import ro.craciun.launcher.wallpaper.ChristmasWallpaperService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Alegerea temei, a stilului de pictograme, a efectelor și aplicarea pe telefon. */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var profile: DeviceProfile
    private lateinit var preview: ChristmasSceneView
    private lateinit var iconRow: LinearLayout
    private val cards = ArrayList<Pair<String, View>>()
    private val radios = ArrayList<RadioButton>()
    private val switches = ArrayList<Switch>()
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var sampleIcons: List<Drawable> = emptyList()

    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        profile = DeviceProfile(this)
        Edge.enable(window)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            background = Drawables.vertical(0xFF070D24.toInt(), 0xFF162452.toInt())
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(col, ViewGroup.LayoutParams(match, wrap))
        setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            val b = Edge.bars(insets)
            col.setPadding(b[0] + dp(18), b[1] + dp(12), b[2] + dp(18), b[3] + dp(28))
            insets
        }

        col.addView(text("Teme de Crăciun", 28f, bold = true))
        col.addView(text("Previzualizare live — atinge scena pentru sclipiri ✨", 14f, alpha = 0.75f))

        // Previzualizare animată
        preview = ChristmasSceneView(this).apply {
            background = Drawables.rounded(0xFF101830.toInt(), dp(22).toFloat())
            clipToOutline = true
            setOnTouchListener { v, ev ->
                if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) renderer.burst(ev.x, ev.y)
                if (ev.actionMasked == android.view.MotionEvent.ACTION_UP) v.performClick()
                true
            }
        }
        col.addView(preview, LinearLayout.LayoutParams(match, dp(if (profile.isLarge) 380 else 280)).apply { topMargin = dp(12) })

        // Teme
        col.addView(section("Alege varianta de temă"))
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipToPadding = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hs.addView(row)
        val (sw, sh) = screenSize()
        val cardW = dp(if (profile.isLarge) 150 else 118)
        val cardH = (cardW * (maxOf(sw, sh).toFloat() / minOf(sw, sh))).toInt().coerceAtMost(dp(300))
        for (t in ThemeCatalog.ALL) row.addView(themeCard(t, cardW, cardH))
        col.addView(hs, LinearLayout.LayoutParams(match, wrap))

        // Pictograme
        col.addView(section("Stilul pictogramelor"))
        iconRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(6))
            background = Drawables.rounded(0x22FFFFFF, dp(18).toFloat())
        }
        col.addView(iconRow, LinearLayout.LayoutParams(match, wrap).apply { bottomMargin = dp(6) })
        val group = RadioGroup(this).apply { orientation = LinearLayout.VERTICAL }
        IconStyle.entries.forEachIndexed { i, st ->
            val rb = RadioButton(this).apply {
                id = 1000 + i
                text = st.title
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(dp(8), dp(8), 0, dp(8))
            }
            radios.add(rb)
            group.addView(rb)
        }
        group.check(1000 + prefs.iconStyle.ordinal)
        group.setOnCheckedChangeListener { _, checkedId ->
            val idx = checkedId - 1000
            if (idx in IconStyle.entries.indices) {
                prefs.iconStyle = IconStyle.entries[idx]
                refreshIconRow()
            }
        }
        col.addView(group)

        // Efecte
        col.addView(section("Efecte de sărbători"))
        col.addView(switchRow("Ninsoare", "Fulgi pe trei planuri, cu vânt și cristale de zăpadă", prefs.snow) { prefs.snow = it })
        col.addView(text("Intensitatea ninsorii", 15f, alpha = 0.9f).apply { setPadding(0, dp(10), 0, 0) })
        val seek = SeekBar(this).apply {
            max = 20
            progress = ((prefs.snowDensity - 0.2f) / 0.1f).roundToInt().coerceIn(0, 20)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        prefs.snowDensity = 0.2f + progress * 0.1f
                        applyPreview()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        col.addView(seek, LinearLayout.LayoutParams(match, wrap))
        col.addView(switchRow("Ghirlandă de lumini", "Becuri colorate care se aprind pe rând, sus pe ecran", prefs.garland) { prefs.garland = it })
        col.addView(switchRow("Cer magic", "Stele căzătoare și sania lui Moș Crăciun cu renii", prefs.skyEffects) { prefs.skyEffects = it })
        col.addView(switchRow("Sclipiri la atingere", "Scântei aurii oriunde atingi ecranul", prefs.sparkles) { prefs.sparkles = it })
        col.addView(switchRow("Numărătoare inversă", "Câte zile mai sunt până la Crăciun", prefs.countdown) { prefs.countdown = it })
        col.addView(switchRow("Economie baterie", "Animație la 30 de cadre pe secundă", prefs.batterySaver) { prefs.batterySaver = it })

        // Aplicare
        col.addView(section("Aplică pe telefon"))
        col.addView(actionButton("🏠  Folosește ca ecran principal", "Setează „Teme de Crăciun” ca launcher (aplicația Acasă).") { makeDefaultLauncher() })
        col.addView(actionButton("❄️  Fundal animat, cu launcherul tău", "Păstrezi ecranul tău Samsung, Xiaomi, Huawei, Pixel… și primești zăpada și luminițele pe fundal.") { openLiveWallpaper() })
        col.addView(actionButton("🖼️  Fundal static (acasă + blocare)", "Salvează scena ca imagine, exact la rezoluția telefonului tău.") { setStaticWallpaper() })

        col.addView(
            text(
                "Funcționează pe Android 7.0 și mai nou: Samsung, Xiaomi, Redmi, POCO, Huawei, Honor, " +
                    "Oppo, Realme, OnePlus, Motorola, Nokia, Google Pixel, tablete și telefoane pliabile.",
                13f, alpha = 0.6f
            ).apply { setPadding(0, dp(22), 0, 0) }
        )

        applyTheme()
        loadSampleIcons()
    }

    override fun onResume() {
        super.onResume()
        applyPreview()
        preview.start()
    }

    override fun onPause() {
        super.onPause()
        preview.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    // ------------------------------------------------------------ elemente UI

    private fun dp(v: Int): Int = profile.dp(v)

    /** Rulează în fundal; ignoră sarcinile sosite după închiderea ecranului. */
    private fun bg(task: () -> Unit) {
        if (executor.isShutdown) return
        try {
            executor.execute { task() }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
        }
    }

    private fun text(s: String, sizeSp: Float, bold: Boolean = false, alpha: Float = 1f): TextView = TextView(this).apply {
        text = s
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        this.alpha = alpha
    }

    private fun section(title: String): View = text(title, 19f, bold = true).apply {
        setPadding(0, dp(26), 0, dp(10))
    }

    private fun themeCard(t: ChristmasTheme, w: Int, h: Int): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(8))
        }
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = Drawables.rounded(t.skyMid, dp(14).toFloat())
            clipToOutline = true
        }
        box.addView(img, LinearLayout.LayoutParams(w, h))
        box.addView(text(t.name, 14f, bold = true).apply { setPadding(dp(2), dp(8), 0, 0) }, LinearLayout.LayoutParams(w, wrap))
        box.addView(text(t.tagline, 12f, alpha = 0.7f).apply { maxLines = 2; setPadding(dp(2), dp(2), 0, 0) }, LinearLayout.LayoutParams(w, wrap))
        box.setOnClickListener { selectTheme(t) }
        cards.add(t.id to box)
        val top = dp(4).toFloat()
        bg {
            try {
                val bmp = ChristmasRenderer().apply {
                    theme = t
                    topInset = top
                }.renderStill(w, h)
                runOnUiThread { img.setImageBitmap(bmp) }
            } catch (e: Throwable) {
            }
        }
        return box
    }

    private fun switchRow(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(4))
        }
        val sw = Switch(this).apply {
            text = title
            isChecked = value
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setOnCheckedChangeListener { _, checked ->
                onChange(checked)
                applyPreview()
            }
        }
        switches.add(sw)
        box.addView(sw, LinearLayout.LayoutParams(match, wrap))
        box.addView(text(sub, 13f, alpha = 0.65f).apply { setPadding(0, 0, dp(60), 0) })
        return box
    }

    private fun actionButton(title: String, sub: String, onClick: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = Drawables.rounded(0x22FFFFFF, dp(18).toFloat(), 0x33FFFFFF, dp(1))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        box.addView(text(title, 16f, bold = true))
        box.addView(text(sub, 13f, alpha = 0.7f).apply { setPadding(0, dp(4), 0, 0) })
        return box.also {
            it.layoutParams = LinearLayout.LayoutParams(match, wrap).apply { bottomMargin = dp(10) }
        }
    }

    // ----------------------------------------------------------- comportament

    private fun selectTheme(t: ChristmasTheme) {
        prefs.themeId = t.id
        applyTheme()
        applyPreview()
        refreshIconRow()
    }

    private fun applyTheme() {
        val t = prefs.theme
        val accent = ColorStateList.valueOf(t.accent)
        for ((id, v) in cards) {
            v.background = if (id == t.id) Drawables.rounded(Colors.withAlpha(t.accent, 0x33), dp(18).toFloat(), t.accent, dp(3))
            else null
        }
        for (rb in radios) rb.buttonTintList = accent
        for (s in switches) {
            s.thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(t.accent, 0xFFB0B6C8.toInt())
            )
            s.trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Colors.withAlpha(t.accent, 0x88), 0x55FFFFFF)
            )
        }
    }

    private fun applyPreview() {
        prefs.applyTo(preview.renderer)
        preview.batterySaver = prefs.batterySaver
    }

    private fun loadSampleIcons() {
        bg {
            val icons = try {
                AppRepository.load(applicationContext).take(5).map { it.icon }
            } catch (e: Throwable) {
                emptyList()
            }
            sampleIcons = icons
            runOnUiThread { refreshIconRow() }
        }
    }

    private fun refreshIconRow() {
        val icons = sampleIcons
        val style = prefs.iconStyle
        val theme = prefs.theme
        val size = profile.iconPx
        bg {
            val bmps = icons.mapIndexedNotNull { i, d ->
                try {
                    FestiveIconFactory.create(d, size, style, theme, i * 7919)
                } catch (e: Throwable) {
                    null
                }
            }
            runOnUiThread {
                iconRow.removeAllViews()
                for (b in bmps) {
                    iconRow.addView(ImageView(this).apply { setImageBitmap(b) }, LinearLayout.LayoutParams(size, size).apply {
                        setMargins(dp(6), 0, dp(6), 0)
                    })
                }
            }
        }
    }

    private fun makeDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    toast("Este deja ecranul tău principal 🎄")
                    return
                }
                try {
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 7)
                    return
                } catch (e: Exception) {
                }
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
                toast("Caută „Aplicații implicite” → „Aplicație ecran de pornire”")
            } catch (e2: Exception) {
            }
        }
    }

    private fun openLiveWallpaper() {
        val component = ComponentName(this, ChristmasWallpaperService::class.java)
        try {
            startActivity(
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
                toast("Alege „Crăciun animat” din listă")
            } catch (e2: Exception) {
                toast("Telefonul nu acceptă fundaluri animate. Folosește fundalul static.")
            }
        }
    }

    private fun setStaticWallpaper() {
        toast("Se pregătește fundalul…")
        var (w, h) = screenSize()
        if (w > h && !profile.isLarge) {
            val tmp = w; w = h; h = tmp
        }
        val top = statusBarHeight().toFloat()
        bg {
            var ok = false
            try {
                val r = ChristmasRenderer()
                prefs.applyTo(r)
                r.topInset = top
                val bmp = r.renderStill(w, h)
                WallpaperManager.getInstance(applicationContext)
                    .setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                ok = true
            } catch (e: Throwable) {
            }
            runOnUiThread {
                toast(if (ok) "Gata! Fundalul de Crăciun a fost aplicat 🎄" else "Telefonul nu a permis schimbarea fundalului")
            }
        }
    }

    private fun screenSize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= 30) {
            val b = windowManager.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            legacyScreenSize()
        }

    @Suppress("DEPRECATION")
    private fun legacyScreenSize(): Pair<Int, Int> {
        val m = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(m)
        return m.widthPixels to m.heightPixels
    }

    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
