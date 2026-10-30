package com.s26home.launcher.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.s26home.launcher.R
import com.s26home.launcher.ui.Fonts
import com.s26home.launcher.ui.dp
import com.s26home.launcher.ui.dpf
import com.s26home.launcher.ui.isNight
import com.s26home.launcher.ui.ripple
import com.s26home.launcher.ui.roundRect
import com.s26home.launcher.ui.style

/**
 * Mini kit de pantallas de ajustes con el lenguaje One UI: título grande que se pliega,
 * grupos en tarjetas de 26 dp, valores elegidos en color de acento y controles
 * DeviceDefault (que en Galaxy ya tienen el aspecto One UI nativo).
 */
class SettingsUi(val a: Activity, val accent: Int) {
    val dark = a.isNight()
    val bg = if (dark) Color.BLACK else 0xFFF2F2F4.toInt()
    val card = if (dark) 0xFF1A1A1C.toInt() else Color.WHITE
    val fg = if (dark) 0xFFF4F4F6.toInt() else 0xFF111114.toInt()
    val sub = if (dark) 0xFF9C9CA3.toInt() else 0xFF717178.toInt()
    val divider = if (dark) 0x1AFFFFFF else 0x12000000
    val pressed = if (dark) 0xFF26262A.toInt() else 0xFFEAEAEE.toInt()

    lateinit var list: LinearLayout
    private var group: LinearLayout? = null

    /** Crea la pantalla y devuelve el contenedor de grupos. */
    fun screen(title: String, onBack: (() -> Unit)?): View {
        val root = FrameLayout(a).apply { setBackgroundColor(bg) }
        val scroll = ScrollView(a).apply { isVerticalScrollBarEnabled = false; clipToPadding = false }
        list = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(a.dp(12), 0, a.dp(12), a.dp(32)) }
        val big = TextView(a).apply { text = title; style(32f, fg, Fonts.bold); setPadding(a.dp(14), 0, a.dp(14), a.dp(22)) }
        val header = FrameLayout(a)
        header.addView(big, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        val h = (a.resources.displayMetrics.heightPixels * 0.26f).toInt()
        list.addView(header, LinearLayout.LayoutParams(-1, h))
        scroll.addView(list)
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        // Barra compacta: aparece cuando el título grande sale de pantalla.
        val bar = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(bg)
        }
        if (onBack != null) bar.addView(ImageView(a).apply {
            setImageResource(R.drawable.ic_back); imageTintList = ColorStateList.valueOf(fg)
            setPadding(a.dp(14), a.dp(14), a.dp(14), a.dp(14)); background = ripple(Color.TRANSPARENT, a.dpf(24), pressed)
            contentDescription = a.getString(R.string.back)
            setOnClickListener { onBack() }
        }, LinearLayout.LayoutParams(a.dp(52), a.dp(52)))
        val small = TextView(a).apply { this.text = title; style(19f, fg, Fonts.semibold); alpha = 0f; setPadding(a.dp(if (onBack == null) 20 else 4), 0, 0, 0) }
        bar.addView(small)
        root.addView(bar, FrameLayout.LayoutParams(-1, -2))
        scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            val f = ((y - (h - a.dp(110))).toFloat() / a.dp(60)).coerceIn(0f, 1f)
            small.alpha = f; big.alpha = 1f - f
        }
        root.setOnApplyWindowInsetsListener { _, ins ->
            val b = ins.getInsets(WindowInsets.Type.systemBars())
            bar.setPadding(a.dp(4), b.top, 0, 0)
            list.setPadding(a.dp(12), b.top, a.dp(12), a.dp(32) + b.bottom)
            ins
        }
        return root
    }

    fun header(text: String) {
        group = null
        list.addView(TextView(a).apply { this.text = text; style(14f, sub, Fonts.medium); setPadding(a.dp(24), a.dp(18), a.dp(24), a.dp(8)) })
    }

    fun note(text: String) {
        group = null
        list.addView(TextView(a).apply { this.text = text; style(13.5f, sub); setLineSpacing(0f, 1.15f); setPadding(a.dp(22), a.dp(8), a.dp(22), a.dp(8)) })
    }

    fun endGroup() { group = null }

    private fun g(): LinearLayout {
        group?.let {
            it.addView(View(a).apply { setBackgroundColor(divider) }, LinearLayout.LayoutParams(-1, 1).apply { setMargins(a.dp(22), 0, a.dp(22), 0) })
            return it
        }
        val n = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; background = roundRect(card, a.dpf(26)); clipToOutline = true }
        list.addView(n, LinearLayout.LayoutParams(-1, -2).apply { topMargin = a.dp(10) })
        group = n
        return n
    }

    private fun row(title: String, summary: String?, icon: Int = 0, trailing: View? = null, click: (() -> Unit)?): Pair<LinearLayout, TextView?> {
        val r = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = a.dp(64); setPadding(a.dp(22), a.dp(14), a.dp(18), a.dp(14))
            if (click != null) { background = ripple(Color.TRANSPARENT, 0f, pressed); setOnClickListener { click() } }
        }
        if (icon != 0) r.addView(ImageView(a).apply { setImageResource(icon); imageTintList = ColorStateList.valueOf(accent) },
            LinearLayout.LayoutParams(a.dp(24), a.dp(24)).apply { rightMargin = a.dp(18) })
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(a).apply { text = title; style(17f, this@SettingsUi.fg) })
        var s: TextView? = null
        if (summary != null) {
            s = TextView(a).apply { text = summary; style(14f, sub); setPadding(0, a.dp(3), 0, 0) }
            col.addView(s)
        }
        r.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        if (trailing != null) r.addView(trailing)
        g().addView(r)
        return r to s
    }

    fun action(title: String, summary: String? = null, icon: Int = 0, click: () -> Unit) =
        row(title, summary, icon, ImageView(a).apply { setImageResource(R.drawable.ic_chevron); imageTintList = ColorStateList.valueOf(sub) }.takeIf { icon == 0 }, click)

    fun toggle(title: String, summary: String?, get: () -> Boolean, set: (Boolean) -> Unit) {
        val sw = Switch(a).apply {
            isChecked = get(); isClickable = false; isFocusable = false
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, Color.WHITE))
            trackTintList = ColorStateList(states, intArrayOf(accent, if (dark) 0xFF4A4A4F.toInt() else 0xFFC4C4CA.toInt()))
            trackTintMode = android.graphics.PorterDuff.Mode.SRC_IN
        }
        row(title, summary, trailing = sw) { sw.isChecked = !sw.isChecked; set(sw.isChecked) }
    }

    fun choice(title: String, options: Array<String>, get: () -> Int, set: (Int) -> Unit) {
        var tv: TextView? = null
        val (_, s) = row(title, options.getOrElse(get()) { "" }) {
            AlertDialog.Builder(a).setTitle(title)
                .setSingleChoiceItems(options, get()) { d, i -> set(i); tv?.text = options[i]; d.dismiss() }
                .setNegativeButton(android.R.string.cancel, null).show()
        }
        tv = s; s?.setTextColor(accent)
    }

    fun slider(title: String, min: Int, max: Int, step: Int, get: () -> Int, set: (Int) -> Unit, fmt: (Int) -> String) {
        val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(a.dp(22), a.dp(14), a.dp(14), a.dp(10)) }
        val top = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(TextView(a).apply { text = title; style(17f, this@SettingsUi.fg) }, LinearLayout.LayoutParams(0, -2, 1f))
        val value = TextView(a).apply { text = fmt(get()); style(15f, accent, Fonts.medium); setPadding(0, 0, a.dp(8), 0) }
        top.addView(value)
        box.addView(top)
        box.addView(SeekBar(a).apply {
            this.max = (max - min) / step; progress = (get() - min) / step
            progressTintList = ColorStateList.valueOf(accent); thumbTintList = ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, user: Boolean) { value.text = fmt(min + p * step) }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) { set(min + sb.progress * step) }
            })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = a.dp(6) })
        g().addView(box)
    }

    fun colors(title: String, colors: IntArray, get: () -> Int, set: (Int) -> Unit) {
        val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(a.dp(22), a.dp(14), a.dp(18), a.dp(16)) }
        box.addView(TextView(a).apply { text = title; style(17f, this@SettingsUi.fg) })
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, a.dp(12), 0, 0) }
        val dots = ArrayList<View>()
        fun paint() {
            val cur = get()
            dots.forEachIndexed { i, d ->
                val c = colors[i]
                d.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (c == 0) (if (dark) 0xFF3A3A3E.toInt() else 0xFFE4E4E8.toInt()) else c)
                    if (c == cur) setStroke(a.dp(3), if (dark) Color.WHITE else 0xFF111114.toInt())
                }
            }
        }
        for (c in colors) {
            val d = View(a).apply { setOnClickListener { set(c); paint() } }
            dots += d
            val cell = FrameLayout(a)
            cell.addView(d, FrameLayout.LayoutParams(a.dp(30), a.dp(30), Gravity.CENTER))
            row.addView(cell, LinearLayout.LayoutParams(0, a.dp(36), 1f))
        }
        paint()
        box.addView(row)
        g().addView(box)
    }

    fun custom(v: View) { g().addView(v, ViewGroup.LayoutParams(-1, -2)) }
}
