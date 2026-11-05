package ro.craciun.launcher.ui

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import ro.craciun.launcher.apps.AppEntry

/** Celula unei aplicații: pictograma festivă + numele, cu umbră ca să se citească peste scenă. */
class AppCell(context: Context, profile: DeviceProfile, showLabel: Boolean) : LinearLayout(context) {
    val icon = ImageView(context)
    private val label = TextView(context)
    var entry: AppEntry? = null
        private set

    init {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(profile.dp(2), profile.dp(6), profile.dp(2), profile.dp(6))
        icon.scaleType = ImageView.ScaleType.FIT_CENTER
        addView(icon, LinearLayout.LayoutParams(profile.iconPx, profile.iconPx))
        label.setTextColor(Color.WHITE)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, profile.labelSp)
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        label.gravity = Gravity.CENTER
        label.setShadowLayer(profile.dp(3).toFloat(), 0f, profile.dp(1).toFloat(), 0xCC000000.toInt())
        label.visibility = if (showLabel) View.VISIBLE else View.GONE
        addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = profile.dp(4) })
    }

    fun bind(e: AppEntry) {
        entry = e
        val f = e.festive
        if (f != null) icon.setImageBitmap(f) else icon.setImageDrawable(e.icon)
        label.text = e.label
        contentDescription = e.label
    }
}

class AppAdapter(private val context: Context, private val profile: DeviceProfile) : BaseAdapter() {
    var items: List<AppEntry> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Any = items[position]
    override fun getItemId(position: Int): Long = items[position].key.hashCode().toLong()
    override fun hasStableIds(): Boolean = true

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val cell = (convertView as? AppCell) ?: AppCell(context, profile, true)
        cell.bind(items[position])
        return cell
    }
}
