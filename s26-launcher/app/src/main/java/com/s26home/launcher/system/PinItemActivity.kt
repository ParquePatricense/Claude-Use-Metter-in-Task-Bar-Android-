package com.s26home.launcher.system

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.pm.LauncherApps
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.s26home.launcher.LauncherApp
import com.s26home.launcher.R
import com.s26home.launcher.data.HomeItem
import com.s26home.launcher.ui.DeviceProfile
import com.s26home.launcher.ui.Fonts
import com.s26home.launcher.ui.UiTheme
import com.s26home.launcher.ui.WidgetSizing
import com.s26home.launcher.ui.dp
import com.s26home.launcher.ui.dpf
import com.s26home.launcher.ui.ripple
import com.s26home.launcher.ui.roundRect
import com.s26home.launcher.ui.style

/**
 * Recibe pedidos de "Agregar a inicio" de otras apps (Chrome, Maps, Samsung Internet…):
 * CONFIRM_PIN_SHORTCUT y CONFIRM_PIN_APPWIDGET (LauncherApps.PinItemRequest, API 26).
 */
class PinItemActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val la = getSystemService(LauncherApps::class.java)
        val req = la.getPinItemRequest(intent)
        if (req == null || !req.isValid) { finish(); return }
        val app = LauncherApp.get(this)
        val t = UiTheme(this, app.settings)

        val label: CharSequence
        val icon: android.graphics.drawable.Drawable?
        val dens = resources.displayMetrics.densityDpi
        if (req.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            val s = req.shortcutInfo!!
            label = s.shortLabel ?: s.longLabel ?: ""
            icon = try { la.getShortcutIconDrawable(s, dens) } catch (e: Exception) { null }
        } else {
            val info = req.getAppWidgetProviderInfo(this)!!
            label = info.loadLabel(packageManager)
            icon = app.widgetHost.preview(info, dens)
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            background = roundRect(t.card, dpf(28)); setPadding(dp(24), dp(24), dp(24), dp(14))
        }
        card.addView(TextView(this).apply { text = getString(R.string.add_to_home); style(20f, t.text, Fonts.semibold) })
        card.addView(ImageView(this).apply { setImageDrawable(icon); scaleType = ImageView.ScaleType.FIT_CENTER },
            LinearLayout.LayoutParams(dp(160), dp(120)).apply { topMargin = dp(18); bottomMargin = dp(10) })
        card.addView(TextView(this).apply { text = label; style(16f, t.text); gravity = Gravity.CENTER })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(18), 0, 0) }
        fun btn(text: Int, primary: Boolean, click: () -> Unit) = TextView(this).apply {
            setText(text); style(16f, if (primary) t.accent else t.text, Fonts.semibold); gravity = Gravity.CENTER
            background = ripple(Color.TRANSPARENT, dpf(20), t.cardPressed)
            setOnClickListener { click() }
        }
        buttons.addView(btn(android.R.string.cancel, false) { finish() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        buttons.addView(btn(R.string.add, true) { accept(req, app) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        card.addView(buttons, LinearLayout.LayoutParams(-1, -2))
        val rootView = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(dp(28), 0, dp(28), 0); setOnClickListener { finish() } }
        rootView.addView(card, LinearLayout.LayoutParams(-1, -2))
        card.isClickable = true
        setContentView(rootView)
    }

    private fun accept(req: LauncherApps.PinItemRequest, app: LauncherApp) {
        val s = app.settings
        val dm = resources.displayMetrics
        val p = DeviceProfile(this, s, dm.widthPixels, dm.heightPixels, dp(32), dp(24))
        val m = app.model
        m.load {
            if (req.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
                val sc = req.shortcutInfo!!
                if (!req.accept()) { finish(); return@load }
                val item = HomeItem(m.newId(), HomeItem.TYPE_SHORTCUT, HomeItem.DESKTOP).apply {
                    pkg = sc.`package`; shortcutId = sc.id; userSerial = app.apps.serialOf(sc.userHandle)
                    title = (sc.shortLabel ?: "").toString()
                }
                m.placeAnywhere(item, p.cols, p.rows, s.defaultPage)
            } else {
                val info = req.getAppWidgetProviderInfo(this)!!
                val id = app.widgetHost.allocate()
                if (!req.accept(Bundle().apply { putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, id) })) {
                    app.widgetHost.deleteId(id); finish(); return@load
                }
                val (w, h) = WidgetSizing.span(this, info, p)
                val item = HomeItem(m.newId(), HomeItem.TYPE_WIDGET, HomeItem.DESKTOP, 0, 0, 0, w, h).apply { widgetId = id }
                m.placeAnywhere(item, p.cols, p.rows, s.defaultPage)
            }
            m.notifyChanged()
            Toast.makeText(this, R.string.added_to_home, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
