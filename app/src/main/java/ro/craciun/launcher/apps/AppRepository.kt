package ro.craciun.launcher.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.text.Collator
import java.util.Locale

class AppEntry(val label: String, val pkg: String, val cls: String, val icon: Drawable) {
    val key: String = "$pkg/$cls"
    @Volatile var festive: Bitmap? = null
    @Volatile var festiveKey: String? = null
}

object AppRepository {

    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale.forLanguageTag("ro-RO"))
        return query(pm, intent)
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                AppEntry(
                    it.loadLabel(pm).toString(),
                    it.activityInfo.packageName,
                    it.activityInfo.name,
                    it.loadIcon(pm)
                )
            }
            .distinctBy { it.key }
            .sortedWith(compareBy(collator) { it.label })
    }

    private fun query(pm: PackageManager, intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            queryLegacy(pm, intent)
        }

    @Suppress("DEPRECATION")
    private fun queryLegacy(pm: PackageManager, intent: Intent): List<ResolveInfo> = pm.queryIntentActivities(intent, 0)

    @Suppress("DEPRECATION")
    private fun resolvePackage(pm: PackageManager, intent: Intent): String? {
        val ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
        val pkg = ri.activityInfo?.packageName ?: return null
        return if (pkg == "android") null else pkg // "android" = fereastra de alegere
    }

    /** Bara de jos: telefon, mesaje, browser, cameră (aplicațiile implicite ale telefonului). */
    fun defaultDock(context: Context, apps: List<AppEntry>, max: Int): List<String> {
        val pm = context.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        )
        val result = ArrayList<String>()
        for (i in intents) {
            val pkg = resolvePackage(pm, i) ?: continue
            val entry = apps.firstOrNull { it.pkg == pkg } ?: continue
            if (entry.key !in result) result.add(entry.key)
        }
        return result.take(max)
    }

    private val popular = listOf(
        "com.android.vending", "com.google.android.youtube", "com.whatsapp", "com.facebook.katana",
        "com.instagram.android", "com.google.android.gm", "com.google.android.apps.maps",
        "com.google.android.apps.photos", "com.sec.android.gallery3d", "com.miui.gallery",
        "com.google.android.calendar", "com.samsung.android.calendar", "com.spotify.music",
        "com.android.settings", "com.google.android.deskclock", "com.sec.android.app.clockpackage",
        "com.facebook.orca", "com.zhiliaoapp.musically", "com.android.chrome"
    )

    fun defaultHome(apps: List<AppEntry>, exclude: List<String>, count: Int): List<String> {
        val result = ArrayList<String>()
        for (p in popular) {
            val e = apps.firstOrNull { it.pkg == p } ?: continue
            if (e.key !in exclude && e.key !in result) result.add(e.key)
            if (result.size >= count) return result
        }
        for (e in apps) {
            if (result.size >= count) break
            if (e.key !in exclude && e.key !in result) result.add(e.key)
        }
        return result
    }
}
