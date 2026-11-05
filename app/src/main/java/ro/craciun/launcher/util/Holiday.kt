package ro.craciun.launcher.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object Holiday {
    private val ro: Locale = Locale.forLanguageTag("ro-RO")

    fun dateText(): String {
        val s = SimpleDateFormat("EEEE, d MMMM", ro).format(Date())
        return s.replaceFirstChar { it.titlecase(ro) }
    }

    fun countdownText(now: Calendar = Calendar.getInstance()): String {
        val y = now.get(Calendar.YEAR)
        val m = now.get(Calendar.MONTH)
        val d = now.get(Calendar.DAY_OF_MONTH)
        if (m == Calendar.DECEMBER) {
            when (d) {
                24 -> return "🎁 Mâine e Crăciunul! Ajun fericit!"
                25, 26 -> return "🎄 Crăciun fericit!"
                27, 28, 29, 30 -> return "✨ Sărbători fericite!"
                31 -> return "🥂 Revelion fericit! La mulți ani!"
            }
        }
        if (m == Calendar.JANUARY && d == 1) return "🎆 La mulți ani, $y!"
        if (m == Calendar.JANUARY && d <= 7) return "✨ Sărbători fericite!"
        val today = Calendar.getInstance().apply { clear(); set(y, m, d) }
        val target = Calendar.getInstance().apply { clear(); set(y, Calendar.DECEMBER, 25) }
        val days = ((target.timeInMillis - today.timeInMillis) / 86_400_000.0).roundToInt()
        return if (days == 1) "🎄 Mai este o zi până la Crăciun"
        else "🎄 Mai sunt ${zile(days)} până la Crăciun"
    }

    /** Pluralul românesc: 2 zile, 19 zile, 20 de zile, 101 zile, 120 de zile. */
    private fun zile(n: Int): String {
        val r = n % 100
        return if (r in 1..19) "$n zile" else "$n de zile"
    }
}
