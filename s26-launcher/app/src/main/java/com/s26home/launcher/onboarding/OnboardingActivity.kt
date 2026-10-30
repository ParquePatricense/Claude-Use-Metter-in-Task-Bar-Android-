package com.s26home.launcher.onboarding

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.window.OnBackInvokedDispatcher
import com.s26home.launcher.LauncherApp
import com.s26home.launcher.R
import com.s26home.launcher.data.Settings
import com.s26home.launcher.settings.SettingsActivity
import com.s26home.launcher.system.SystemActions
import com.s26home.launcher.system.Wallpapers
import com.s26home.launcher.ui.Anim
import com.s26home.launcher.ui.Fonts
import com.s26home.launcher.ui.dp
import com.s26home.launcher.ui.dpf
import com.s26home.launcher.ui.isNight
import com.s26home.launcher.ui.ripple
import com.s26home.launcher.ui.roundRect
import com.s26home.launcher.ui.style

/**
 * Asistente de primera ejecución: 1 bienvenida · 2 diseño · 3 cuadrícula · 4 tema ·
 * 5 gestos · 6 establecer como predeterminado (con la salida a One UI Home bien visible).
 */
class OnboardingActivity : Activity() {
    private lateinit var s: Settings
    private var step = 0
    private val steps = 6
    private lateinit var body: FrameLayout
    private lateinit var next: TextView
    private lateinit var back: TextView
    private lateinit var dots: LinearLayout
    private val dark by lazy { isNight() }
    private val bg by lazy { if (dark) Color.BLACK else 0xFFF2F2F4.toInt() }
    private val card by lazy { if (dark) 0xFF1A1A1C.toInt() else Color.WHITE }
    private val fg by lazy { if (dark) 0xFFF4F4F6.toInt() else 0xFF111114.toInt() }
    private val sub by lazy { if (dark) 0xFF9C9CA3.toInt() else 0xFF717178.toInt() }
    private val accent get() = s.accent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        s = LauncherApp.get(this).settings
        step = savedInstanceState?.getInt("step") ?: 0
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = bg; window.navigationBarColor = bg
        val f = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.decorView.windowInsetsController?.setSystemBarsAppearance(if (dark) 0 else f, f)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        body = FrameLayout(this)
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        dots = LinearLayout(this).apply { gravity = Gravity.CENTER }
        root.addView(dots, LinearLayout.LayoutParams(-1, dp(24)))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(16), dp(8), dp(16), dp(16)) }
        back = TextView(this).apply {
            style(17f, fg, Fonts.medium); gravity = Gravity.CENTER; setText(R.string.back)
            background = ripple(Color.TRANSPARENT, dpf(26), if (dark) 0xFF26262A.toInt() else 0xFFE4E4E8.toInt())
            setOnClickListener { go(step - 1) }
        }
        next = TextView(this).apply {
            style(17f, Color.WHITE, Fonts.semibold); gravity = Gravity.CENTER
            setOnClickListener { if (step == steps - 1) finishWizard() else go(step + 1) }
        }
        nav.addView(back, LinearLayout.LayoutParams(0, dp(54), 1f).apply { rightMargin = dp(10) })
        nav.addView(next, LinearLayout.LayoutParams(0, dp(54), 1f))
        root.addView(nav)
        root.setOnApplyWindowInsetsListener { v, ins ->
            val b = ins.getInsets(WindowInsets.Type.systemBars()); v.setPadding(0, b.top, 0, b.bottom); ins
        }
        setContentView(root)
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
            if (step > 0) go(step - 1) else finish()
        }
        render(false)
    }

    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); out.putInt("step", step) }

    override fun onResume() { super.onResume(); if (step == steps - 1) render(false) }

    private fun go(n: Int) {
        if (n < 0 || n >= steps) return
        val dir = if (n > step) 1 else -1
        step = n
        val old = body.getChildAt(0)
        old?.animate()?.alpha(0f)?.translationX(-dir * dpf(40))?.setDuration(160)?.withEndAction { body.removeView(old) }?.start()
        render(true, dir)
    }

    private fun render(animate: Boolean, dir: Int = 1) {
        if (!animate) body.removeAllViews()
        val page = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(48), dp(24), dp(24)) }
        page.addView(col)
        when (step) {
            0 -> welcome(col)
            1 -> layoutStep(col)
            2 -> gridStep(col)
            3 -> themeStep(col)
            4 -> gestureStep(col)
            5 -> defaultStep(col)
        }
        body.addView(page)
        if (animate) { page.alpha = 0f; page.translationX = dir * dpf(40); page.animate().alpha(1f).translationX(0f).setDuration(260).setInterpolator(Anim.EASE_OUT).start() }
        back.visibility = if (step == 0) View.INVISIBLE else View.VISIBLE
        next.setText(if (step == steps - 1) R.string.finish else R.string.next)
        next.background = roundRect(accent, dpf(27))
        dots.removeAllViews()
        for (i in 0 until steps) dots.addView(View(this).apply {
            background = roundRect(if (i == step) accent else (if (dark) 0x40FFFFFF else 0x30000000), dpf(4))
        }, LinearLayout.LayoutParams(if (i == step) dp(18) else dp(7), dp(7)).apply { setMargins(dp(3), 0, dp(3), 0) })
    }

    private fun title(col: LinearLayout, t: Int, d: Int) {
        col.addView(TextView(this).apply { setText(t); style(30f, fg, Fonts.bold) })
        col.addView(TextView(this).apply { setText(d); style(16f, sub); setLineSpacing(0f, 1.2f); setPadding(0, dp(12), 0, dp(24)) })
    }

    /** Tarjeta seleccionable estilo One UI (borde de acento cuando está elegida). */
    private fun option(col: LinearLayout, t: String, d: String?, selected: Boolean, preview: View? = null, click: () -> Unit) {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = roundRect(card, dpf(24), if (selected) dp(2) else 0, accent)
            setOnClickListener { click(); render(false) }
        }
        if (preview != null) c.addView(preview, LinearLayout.LayoutParams(dp(56), dp(100)).apply { rightMargin = dp(18) })
        val tc = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tc.addView(TextView(this).apply { text = t; style(17f, fg, Fonts.medium) })
        if (d != null) tc.addView(TextView(this).apply { text = d; style(14f, sub); setPadding(0, dp(4), 0, 0) })
        c.addView(tc, LinearLayout.LayoutParams(0, -2, 1f))
        if (selected) c.addView(TextView(this).apply { text = "✓"; style(20f, accent, Fonts.bold) })
        col.addView(c, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    private fun welcome(col: LinearLayout) {
        col.addView(GridPreview(4, 5, accent, dark), LinearLayout.LayoutParams(dp(120), dp(200)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(28) })
        title(col, R.string.ob_welcome, R.string.ob_welcome_sub)
    }

    private fun layoutStep(col: LinearLayout) {
        title(col, R.string.ob_layout, R.string.ob_layout_sub)
        val cur = Triple(s.homeCols, s.homeSearchBar, s.dockBackground)
        option(col, getString(R.string.layout_galaxy), getString(R.string.layout_galaxy_sum), cur == Triple(4, true, false), GridPreview(4, 5, accent, dark)) {
            s.homeCols = 4; s.homeRows = 5; s.homeSearchBar = true; s.dockBackground = false; s.drawerCols = 5; s.drawerRows = 6
        }
        option(col, getString(R.string.layout_compact), getString(R.string.layout_compact_sum), cur == Triple(5, false, false), GridPreview(5, 6, accent, dark)) {
            s.homeCols = 5; s.homeRows = 6; s.homeSearchBar = false; s.dockBackground = false; s.dockCount = 5; s.drawerCols = 5; s.drawerRows = 6
        }
        option(col, getString(R.string.layout_minimal), getString(R.string.layout_minimal_sum), cur == Triple(4, false, true), GridPreview(4, 4, accent, dark)) {
            s.homeCols = 4; s.homeRows = 5; s.homeSearchBar = false; s.dockBackground = true; s.showLabels = true; s.drawerCols = 4; s.drawerRows = 6
        }
    }

    private fun gridStep(col: LinearLayout) {
        title(col, R.string.ob_grid, R.string.ob_grid_sub)
        for ((c, r) in listOf(4 to 5, 4 to 6, 5 to 5, 5 to 6)) {
            option(col, "$c × $r", if (c == 4 && r == 5) getString(R.string.recommended) else null, s.homeCols == c && s.homeRows == r, GridPreview(c, r, accent, dark)) {
                s.homeCols = c; s.homeRows = r
            }
        }
    }

    private fun themeStep(col: LinearLayout) {
        title(col, R.string.ob_theme, R.string.ob_theme_sub)
        val names = arrayOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark)
        for (i in 0..2) option(col, getString(names[i]), null, s.themeMode == i) {
            s.themeMode = i; SettingsActivity.applyNightMode(this, i)
        }
        col.addView(TextView(this).apply { setText(R.string.accent); style(15f, sub, Fonts.medium); setPadding(dp(6), dp(14), 0, dp(10)) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (c in Settings.ACCENTS) {
            val cell = FrameLayout(this)
            cell.addView(View(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(c)
                    if (c == s.accent) setStroke(dp(3), fg)
                }
                setOnClickListener { s.accent = c; render(false) }
            }, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER))
            row.addView(cell, LinearLayout.LayoutParams(0, dp(40), 1f))
        }
        col.addView(row)
        col.addView(TextView(this).apply { setText(R.string.wallpaper); style(15f, sub, Fonts.medium); setPadding(dp(6), dp(20), 0, dp(10)) })
        val wps = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (w in Wallpapers.styles) {
            val img = android.widget.ImageView(this).apply {
                setImageBitmap(Wallpapers.render(w, dp(64), dp(130)))
                background = roundRect(0, dpf(14)); clipToOutline = true
                setOnClickListener {
                    android.widget.Toast.makeText(this@OnboardingActivity, R.string.applying, android.widget.Toast.LENGTH_SHORT).show()
                    Wallpapers.apply(this@OnboardingActivity, w, false) { }
                }
            }
            wps.addView(img, LinearLayout.LayoutParams(0, dp(130), 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        }
        col.addView(wps)
    }

    private fun gestureStep(col: LinearLayout) {
        title(col, R.string.ob_gestures, R.string.ob_gestures_sub)
        option(col, getString(R.string.swipe_down_notif), getString(R.string.swipe_down_notif_sum), s.gestureDown == Settings.ACTION_NOTIFICATIONS) { s.gestureDown = Settings.ACTION_NOTIFICATIONS }
        option(col, getString(R.string.swipe_down_search), getString(R.string.swipe_down_search_sum), s.gestureDown == Settings.ACTION_SEARCH) { s.gestureDown = Settings.ACTION_SEARCH }
        option(col, getString(R.string.double_tap_lock), getString(R.string.double_tap_lock_sum), s.gestureDoubleTap == Settings.ACTION_LOCK) {
            s.gestureDoubleTap = if (s.gestureDoubleTap == Settings.ACTION_LOCK) Settings.ACTION_NONE else Settings.ACTION_LOCK
        }
        col.addView(TextView(this).apply { setText(R.string.gestures_note); style(13.5f, sub); setPadding(dp(6), dp(8), dp(6), 0) })
    }

    private fun defaultStep(col: LinearLayout) {
        title(col, R.string.ob_default, R.string.ob_default_sub)
        val isDef = SystemActions.isDefaultHome(this)
        option(col, getString(if (isDef) R.string.is_default else R.string.set_default), if (isDef) null else getString(R.string.set_default_sum), isDef) {
            SystemActions.requestDefaultHome(this, 7)
        }
        option(col, getString(R.string.back_to_oneui), getString(R.string.back_to_oneui_sum), false) { SystemActions.openHomeSettings(this) }
        col.addView(TextView(this).apply { setText(R.string.ob_default_note); style(13.5f, sub); setLineSpacing(0f, 1.15f); setPadding(dp(6), dp(8), dp(6), 0) })
    }

    @Deprecated("framework") override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data); render(false)
    }

    private fun finishWizard() {
        s.onboardingDone = true
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    /** Miniatura de cuadrícula dibujada (sin imágenes): teléfono con íconos squircle. */
    private inner class GridPreview(val cols: Int, val rows: Int, val accent: Int, val dark: Boolean) : View(this@OnboardingActivity) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            r.set(0f, 0f, w, h)
            p.shader = android.graphics.LinearGradient(0f, 0f, w, h, 0xFF2B3F8F.toInt(), accent, android.graphics.Shader.TileMode.CLAMP)
            c.drawRoundRect(r, w * 0.16f, w * 0.16f, p)
            p.shader = null
            p.color = 0xE6FFFFFF.toInt()
            val pad = w * 0.12f
            val gridH = h * 0.70f
            val cw = (w - 2 * pad) / cols; val ch = (gridH - pad) / rows
            val s = minOf(cw, ch) * 0.62f
            for (y in 0 until rows) for (x in 0 until cols) {
                if ((x + y * 3) % 5 == 2) continue
                val cx = pad + cw * (x + 0.5f); val cy = pad + ch * (y + 0.5f)
                r.set(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
                c.drawRoundRect(r, s * 0.3f, s * 0.3f, p)
            }
            val dy = h - pad - s
            for (x in 0 until 4) {
                val cx = pad + (w - 2 * pad) / 4 * (x + 0.5f)
                r.set(cx - s / 2, dy, cx + s / 2, dy + s)
                c.drawRoundRect(r, s * 0.3f, s * 0.3f, p)
            }
        }
    }
}
