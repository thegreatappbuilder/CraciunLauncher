package ro.craciun.launcher.util

import android.content.Context
import ro.craciun.launcher.apps.IconStyle
import ro.craciun.launcher.theme.ChristmasTheme
import ro.craciun.launcher.theme.ThemeCatalog
import ro.craciun.launcher.scene.ChristmasRenderer

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("craciun", Context.MODE_PRIVATE)

    var themeId: String
        get() = sp.getString("theme", ThemeCatalog.DEFAULT.id) ?: ThemeCatalog.DEFAULT.id
        set(v) = sp.edit().putString("theme", v).apply()

    val theme: ChristmasTheme get() = ThemeCatalog.byId(themeId)

    var snow: Boolean
        get() = sp.getBoolean("snow", true)
        set(v) = sp.edit().putBoolean("snow", v).apply()

    var snowDensity: Float
        get() = sp.getFloat("snowDensity", 1f)
        set(v) = sp.edit().putFloat("snowDensity", v).apply()

    var garland: Boolean
        get() = sp.getBoolean("garland", true)
        set(v) = sp.edit().putBoolean("garland", v).apply()

    var skyEffects: Boolean
        get() = sp.getBoolean("skyEffects", true)
        set(v) = sp.edit().putBoolean("skyEffects", v).apply()

    var sparkles: Boolean
        get() = sp.getBoolean("sparkles", true)
        set(v) = sp.edit().putBoolean("sparkles", v).apply()

    var countdown: Boolean
        get() = sp.getBoolean("countdown", true)
        set(v) = sp.edit().putBoolean("countdown", v).apply()

    var batterySaver: Boolean
        get() = sp.getBoolean("batterySaver", false)
        set(v) = sp.edit().putBoolean("batterySaver", v).apply()

    var iconStyle: IconStyle
        get() = IconStyle.entries.firstOrNull { it.name == sp.getString("iconStyle", null) } ?: IconStyle.ORNAMENT
        set(v) = sp.edit().putString("iconStyle", v.name).apply()

    var initialized: Boolean
        get() = sp.getBoolean("initialized", false)
        set(v) = sp.edit().putBoolean("initialized", v).apply()

    var home: List<String>
        get() = split(sp.getString("home", ""))
        set(v) = sp.edit().putString("home", v.joinToString("|")).apply()

    var dock: List<String>
        get() = split(sp.getString("dock", ""))
        set(v) = sp.edit().putString("dock", v.joinToString("|")).apply()

    private fun split(s: String?): List<String> =
        s.orEmpty().split('|').filter { it.isNotBlank() }

    /** Aplică setările curente pe un renderer (launcher, previzualizare sau fundal animat). */
    fun applyTo(r: ChristmasRenderer) {
        r.theme = theme
        r.snowEnabled = snow
        r.snowDensity = snowDensity
        r.lightsEnabled = garland
        r.skyEffectsEnabled = skyEffects
    }
}
