package ro.craciun.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import ro.craciun.launcher.apps.AppEntry
import ro.craciun.launcher.apps.AppRepository
import ro.craciun.launcher.apps.FestiveIconFactory
import ro.craciun.launcher.scene.ChristmasSceneView
import ro.craciun.launcher.ui.AppAdapter
import ro.craciun.launcher.ui.AppCell
import ro.craciun.launcher.ui.DeviceProfile
import ro.craciun.launcher.ui.Drawables
import ro.craciun.launcher.ui.Edge
import ro.craciun.launcher.util.Colors
import ro.craciun.launcher.util.Holiday
import ro.craciun.launcher.util.Prefs
import java.text.Normalizer
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Ecranul principal (launcher): scena de Crăciun animată în fundal, ceas, dată, numărătoare inversă,
 * aplicațiile favorite, bara de jos și sertarul cu toate aplicațiile (glisare în sus).
 */
class MainActivity : Activity() {

    private enum class Place { HOME, DOCK, DRAWER }

    private lateinit var prefs: Prefs
    private lateinit var profile: DeviceProfile
    private lateinit var scene: ChristmasSceneView
    private lateinit var content: LinearLayout
    private lateinit var dateView: TextView
    private lateinit var countdownView: TextView
    private lateinit var themeButton: TextView
    private lateinit var homeGrid: GridView
    private lateinit var dock: LinearLayout
    private lateinit var drawer: LinearLayout
    private lateinit var drawerGrid: GridView
    private lateinit var drawerTitle: TextView
    private lateinit var search: EditText
    private lateinit var homeAdapter: AppAdapter
    private lateinit var drawerAdapter: AppAdapter
    private lateinit var gestures: GestureDetector

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var apps: List<AppEntry> = emptyList()
    private var drawerOpen = false
    private var loadGen = 0
    @Volatile private var iconKey = ""
    private var receiverRegistered = false

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            loadApps()
        }
    }

    private val minuteTick = object : Runnable {
        override fun run() {
            updateHeader()
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        profile = DeviceProfile(this)
        Edge.enable(window)
        setContentView(buildUi())
        gestures = GestureDetector(this, FlingListener())
        registerPackageReceiver()
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        applySettings()
        scene.start()
        handler.removeCallbacks(minuteTick)
        handler.post(minuteTick)
    }

    override fun onPause() {
        super.onPause()
        scene.stop()
        handler.removeCallbacks(minuteTick)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (receiverRegistered) {
            try {
                unregisterReceiver(packageReceiver)
            } catch (e: Exception) {
            }
        }
        executor.shutdownNow()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // butonul Acasă apăsat cât timp suntem deja pe ecranul principal
        closeDrawer()
        homeGrid.smoothScrollToPosition(0)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Un launcher nu se închide la „Înapoi”; doar închidem sertarul.
        if (drawerOpen) closeDrawer()
    }

    // ------------------------------------------------------------------ UI

    private fun dp(v: Int): Int = profile.dp(v)

    /** Rulează în fundal; ignoră sarcinile sosite după închiderea ecranului. */
    private fun bg(task: () -> Unit) {
        if (executor.isShutdown) return
        try {
            executor.execute { task() }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
        }
    }

    private fun label(sizeSp: Float, bold: Boolean = false): TextView = TextView(this).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setShadowLayer(dp(4).toFloat(), 0f, dp(1).toFloat(), 0xCC000000.toInt())
    }

    private fun makeGrid(a: AppAdapter): GridView = GridView(this).apply {
        numColumns = profile.columns
        stretchMode = GridView.STRETCH_COLUMN_WIDTH
        verticalSpacing = dp(6)
        selector = ColorDrawable(Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        clipToPadding = false
        cacheColorHint = Color.TRANSPARENT
        adapter = a
    }

    private fun buildUi(): View {
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val root = FrameLayout(this)

        scene = ChristmasSceneView(this)
        root.addView(scene, FrameLayout.LayoutParams(match, match))

        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Antet: ceas, dată, numărătoare inversă, buton teme
        val header = FrameLayout(this)
        header.setPadding(dp(22), dp(34), dp(16), dp(4))
        val headerText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val clock = TextClock(this).apply {
            format24Hour = "HH:mm"
            format12Hour = "h:mm"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (profile.isLarge) 76f else 62f)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(dp(6).toFloat(), 0f, dp(2).toFloat(), 0xCC000000.toInt())
            setOnClickListener { openClockApp() }
        }
        dateView = label(18f)
        countdownView = label(15f, bold = true)
        headerText.addView(clock)
        headerText.addView(dateView)
        headerText.addView(countdownView, LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = dp(6) })
        header.addView(headerText, FrameLayout.LayoutParams(wrap, wrap, Gravity.START or Gravity.TOP))
        themeButton = label(14f, bold = true).apply {
            text = "🎄 Teme"
            setShadowLayer(0f, 0f, 0f, 0)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener { openSettings() }
        }
        header.addView(themeButton, FrameLayout.LayoutParams(wrap, wrap, Gravity.END or Gravity.TOP).apply { topMargin = dp(12) })
        content.addView(header, LinearLayout.LayoutParams(match, wrap))

        // Aplicațiile favorite
        homeAdapter = AppAdapter(this, profile)
        homeGrid = makeGrid(homeAdapter)
        homeGrid.setPadding(dp(8), dp(12), dp(8), dp(8))
        homeGrid.setOnItemClickListener { _, view, position, _ -> launch(homeAdapter.items[position], view) }
        homeGrid.setOnItemLongClickListener { _, view, position, _ ->
            showMenu(view, homeAdapter.items[position], Place.HOME); true
        }
        content.addView(homeGrid, LinearLayout.LayoutParams(match, 0, 1f))

        val hint = label(13f).apply {
            text = "︿  Glisează în sus pentru toate aplicațiile"
            gravity = Gravity.CENTER
            alpha = 0.9f
            setPadding(0, dp(6), 0, dp(6))
            setOnClickListener { openDrawer() }
        }
        content.addView(hint, LinearLayout.LayoutParams(match, wrap))

        // Bara de jos
        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        content.addView(dock, LinearLayout.LayoutParams(match, wrap).apply { setMargins(dp(12), 0, dp(12), dp(10)) })
        root.addView(content, FrameLayout.LayoutParams(match, match))

        // Sertarul cu toate aplicațiile
        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            isClickable = true
        }
        val handle = View(this).apply {
            background = Drawables.rounded(0x88FFFFFF.toInt(), dp(3).toFloat())
            setOnClickListener { closeDrawer() }
        }
        drawer.addView(handle, LinearLayout.LayoutParams(dp(42), dp(5)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(14)
            bottomMargin = dp(10)
        })
        drawerTitle = label(20f, bold = true).apply { setPadding(dp(6), 0, 0, dp(10)) }
        drawer.addView(drawerTitle)
        search = EditText(this).apply {
            hint = "🔍  Caută o aplicație…"
            setSingleLine()
            setTextColor(Color.WHITE)
            setHintTextColor(0xAAFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            imeOptions = EditorInfo.IME_ACTION_GO
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    drawerAdapter.items = filter(s?.toString().orEmpty())
                }
            })
            setOnEditorActionListener { _, _, _ ->
                drawerAdapter.items.firstOrNull()?.let { launch(it, null) }
                true
            }
        }
        drawer.addView(search, LinearLayout.LayoutParams(match, wrap).apply { bottomMargin = dp(10) })
        drawerAdapter = AppAdapter(this, profile)
        drawerGrid = makeGrid(drawerAdapter)
        drawerGrid.setOnItemClickListener { _, view, position, _ -> launch(drawerAdapter.items[position], view) }
        drawerGrid.setOnItemLongClickListener { _, view, position, _ ->
            showMenu(view, drawerAdapter.items[position], Place.DRAWER); true
        }
        drawer.addView(drawerGrid, LinearLayout.LayoutParams(match, 0, 1f))
        root.addView(drawer, FrameLayout.LayoutParams(match, match))

        root.setOnApplyWindowInsetsListener { _, insets ->
            val b = Edge.bars(insets)
            content.setPadding(b[0], b[1], b[2], b[3])
            drawer.setPadding(b[0] + dp(12), b[1], b[2] + dp(12), b[3])
            scene.renderer.topInset = b[1].toFloat()
            insets
        }
        return root
    }

    private fun applySettings() {
        val t = prefs.theme
        prefs.applyTo(scene.renderer)
        scene.batterySaver = prefs.batterySaver
        countdownView.visibility = if (prefs.countdown) View.VISIBLE else View.GONE
        themeButton.background = Drawables.rounded(Colors.withAlpha(t.accent, 0xD9), dp(20).toFloat())
        themeButton.setTextColor(if (Color.luminance(t.accent) > 0.5f) 0xFF1A1206.toInt() else Color.WHITE)
        dock.background = Drawables.rounded(
            Colors.withAlpha(t.panel, 0x8C), dp(26).toFloat(), Colors.withAlpha(Color.WHITE, 0x33), dp(1)
        )
        drawer.background = Drawables.vertical(
            Colors.withAlpha(t.panel, 0xEB), Colors.withAlpha(Colors.darken(t.panel, 0.4f), 0xF5)
        )
        search.background = Drawables.rounded(0x26FFFFFF, dp(22).toFloat(), Colors.withAlpha(t.accent, 0x99), dp(1))
        val key = prefs.iconStyle.name + ":" + t.id
        if (key != iconKey) {
            iconKey = key
            generateIcons()
        }
        refreshAll()
        updateHeader()
    }

    private fun updateHeader() {
        dateView.text = Holiday.dateText()
        countdownView.text = Holiday.countdownText()
    }

    // --------------------------------------------------------------- aplicații

    private fun loadApps() {
        val gen = ++loadGen
        bg {
            val list = try {
                AppRepository.load(applicationContext)
            } catch (e: Throwable) {
                emptyList()
            }
            runOnUiThread {
                if (gen != loadGen || isFinishing) return@runOnUiThread
                val old = apps.associateBy { it.key }
                for (e in list) {
                    val o = old[e.key] ?: continue
                    if (o.festiveKey == iconKey) {
                        e.festive = o.festive
                        e.festiveKey = o.festiveKey
                    }
                }
                apps = list
                ensureDefaults()
                refreshAll()
                generateIcons()
            }
        }
    }

    private fun ensureDefaults() {
        if (prefs.initialized || apps.isEmpty()) return
        val dockKeys = AppRepository.defaultDock(this, apps, profile.dockCount)
        prefs.dock = dockKeys
        prefs.home = AppRepository.defaultHome(apps, dockKeys, profile.columns * 2)
        prefs.initialized = true
    }

    /** Generează pictogramele festive în fundal și le afișează pe măsură ce sunt gata. */
    private fun generateIcons() {
        val key = iconKey
        val style = prefs.iconStyle
        val theme = prefs.theme
        val list = apps
        val size = profile.iconPx
        if (list.isEmpty()) return
        bg {
            var n = 0
            for (e in list) {
                if (key != iconKey) return@bg
                if (e.festiveKey == key) continue
                try {
                    e.festive = FestiveIconFactory.create(e.icon, size, style, theme, e.key.hashCode())
                    e.festiveKey = key
                } catch (t: Throwable) {
                    // pictogramă neobișnuită: rămâne cea originală
                }
                n++
                if (n % 12 == 0) runOnUiThread { refreshIcons() }
            }
            runOnUiThread { refreshIcons() }
        }
    }

    private fun entriesFor(keys: List<String>): List<AppEntry> {
        val map = apps.associateBy { it.key }
        return keys.mapNotNull { map[it] }
    }

    private fun refreshAll() {
        homeAdapter.items = entriesFor(prefs.home).take(profile.homeMax)
        drawerAdapter.items = filter(search.text?.toString().orEmpty())
        drawerTitle.text = "Toate aplicațiile (${apps.size})"
        refreshDock()
    }

    private fun refreshIcons() {
        homeAdapter.notifyDataSetChanged()
        drawerAdapter.notifyDataSetChanged()
        for (i in 0 until dock.childCount) {
            val cell = dock.getChildAt(i) as? AppCell ?: continue
            cell.entry?.let { cell.bind(it) }
        }
    }

    private fun refreshDock() {
        dock.removeAllViews()
        val entries = entriesFor(prefs.dock).take(profile.dockCount)
        dock.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        for (e in entries) {
            val cell = AppCell(this, profile, false)
            cell.bind(e)
            cell.setOnClickListener { launch(e, cell.icon) }
            cell.setOnLongClickListener { showMenu(cell, e, Place.DOCK); true }
            dock.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

    private fun filter(query: String): List<AppEntry> {
        val q = normalize(query.trim())
        if (q.isEmpty()) return apps
        return apps.filter { normalize(it.label).contains(q) }
    }

    private fun launch(e: AppEntry, from: View?) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(e.pkg, e.cls))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val opts = from?.let { ActivityOptions.makeScaleUpAnimation(it, 0, 0, it.width, it.height).toBundle() }
        try {
            startActivity(intent, opts)
        } catch (ex: Exception) {
            toast("Aplicația nu poate fi deschisă")
        }
    }

    private fun showMenu(anchor: View, e: AppEntry, place: Place) {
        val popup = PopupMenu(this, anchor)
        val home = prefs.home
        val dockList = prefs.dock
        if (place != Place.HOME && e.key !in home) popup.menu.add(0, 1, 0, "Adaugă pe ecranul principal")
        if (place != Place.DOCK && e.key !in dockList) popup.menu.add(0, 2, 1, "Pune în bara de jos")
        if (place == Place.HOME) popup.menu.add(0, 3, 2, "Scoate de pe ecran")
        if (place == Place.DOCK) popup.menu.add(0, 4, 2, "Scoate din bara de jos")
        popup.menu.add(0, 5, 3, "Informații aplicație")
        popup.menu.add(0, 6, 4, "Dezinstalează")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    prefs.home = home + e.key
                    refreshAll()
                    toast("Adăugată pe ecranul principal")
                }
                2 -> {
                    if (dockList.size >= profile.dockCount) {
                        toast("Bara de jos e plină. Scoate întâi o aplicație din ea.")
                    } else {
                        prefs.dock = dockList + e.key
                        prefs.home = home - e.key
                        refreshAll()
                    }
                }
                3 -> {
                    prefs.home = home - e.key
                    refreshAll()
                }
                4 -> {
                    prefs.dock = dockList - e.key
                    refreshAll()
                }
                5 -> openAppInfo(e)
                6 -> uninstall(e)
            }
            true
        }
        popup.show()
    }

    private fun openAppInfo(e: AppEntry) {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${e.pkg}")))
        } catch (ex: Exception) {
            toast("Nu se pot deschide informațiile aplicației")
        }
    }

    private fun uninstall(e: AppEntry) {
        try {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${e.pkg}")))
        } catch (ex: Exception) {
            toast("Aplicația nu poate fi dezinstalată de aici")
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun openClockApp() {
        try {
            startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
        } catch (ex: Exception) {
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ------------------------------------------------------------- sertar

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        drawer.animate().cancel()
        drawer.visibility = View.VISIBLE
        drawer.alpha = 0f
        drawer.translationY = dp(120).toFloat()
        drawer.animate().alpha(1f).translationY(0f).setDuration(260)
            .setInterpolator(DecelerateInterpolator()).start()
        content.animate().alpha(0f).setDuration(200).start()
        drawerGrid.setSelection(0)
    }

    private fun closeDrawer() {
        if (!drawerOpen) return
        drawerOpen = false
        hideKeyboard()
        if (search.text.isNotEmpty()) search.setText("")
        drawer.animate().cancel()
        drawer.animate().alpha(0f).translationY(dp(120).toFloat()).setDuration(220)
            .withEndAction { if (!drawerOpen) drawer.visibility = View.GONE }.start()
        content.animate().alpha(1f).setDuration(220).start()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(search.windowToken, 0)
        search.clearFocus()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && !drawerOpen && prefs.sparkles) {
            scene.renderer.burst(ev.x, ev.y)
        }
        gestures.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private inner class FlingListener : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val start = e1 ?: return false
            val dy = e2.y - start.y
            val dx = e2.x - start.x
            if (abs(dy) < abs(dx) * 1.2f || abs(dy) < dp(60)) return false
            if (!drawerOpen && dy < 0 && velocityY < -600f && !homeGrid.canScrollVertically(1)) {
                openDrawer()
                return true
            }
            if (drawerOpen && dy > 0 && velocityY > 600f && !drawerGrid.canScrollVertically(-1)) {
                closeDrawer()
                return true
            }
            return false
        }
    }

    // ---------------------------------------------------------- pachete

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerPackageReceiver() {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageReceiver, f, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(packageReceiver, f)
        }
        receiverRegistered = true
    }
}
