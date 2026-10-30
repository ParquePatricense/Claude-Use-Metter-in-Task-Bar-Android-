package com.s26home.launcher.ui

import android.appwidget.AppWidgetProviderInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.s26home.launcher.R
import com.s26home.launcher.data.AppInfo
import com.s26home.launcher.data.HomeItem

/**
 * Menú contextual One UI: tarjeta redondeada con accesos directos de la app arriba y
 * una fila de acciones con ícono abajo. Aparece sobre el ícono con una escala suave.
 */
class PopupCard(private val l: HomeActivity) : LinearLayout(l) {
    private val t = l.theme
    private var actions: LinearLayout? = null

    init {
        orientation = VERTICAL
        background = roundRect(t.card, l.dpf(24))
        elevation = l.dpf(12)
        clipToOutline = true
        setPadding(0, l.dp(6), 0, l.dp(6))
    }

    fun row(icon: android.graphics.drawable.Drawable?, text: String, click: () -> Unit, longClick: (() -> Unit)? = null) {
        val r = LinearLayout(l).apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(l.dp(18), 0, l.dp(18), 0)
            background = ripple(Color.TRANSPARENT, 0f, t.cardPressed)
            if (icon != null) addView(ImageView(l).apply { setImageDrawable(icon) }, LayoutParams(l.dp(28), l.dp(28)).apply { rightMargin = l.dp(14) })
            addView(TextView(l).apply { this.text = text; style(15f, t.text); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LayoutParams(0, -2, 1f))
            setOnClickListener { l.closePopup(); click() }
            if (longClick != null) setOnLongClickListener { l.closePopup(); longClick(); true }
        }
        addView(r, LayoutParams(-1, l.dp(52)))
    }

    fun action(icon: Int, label: Int, click: () -> Unit) {
        val bar = actions ?: LinearLayout(l).apply {
            orientation = HORIZONTAL
            if (childCount > 0) { }
            this@PopupCard.addView(View(l).apply { setBackgroundColor(t.divider) }, LayoutParams(-1, 1).apply { setMargins(l.dp(16), l.dp(4), l.dp(16), l.dp(4)) })
            this@PopupCard.addView(this, LayoutParams(-1, -2))
        }.also { actions = it }
        bar.addView(LinearLayout(l).apply {
            orientation = VERTICAL; gravity = Gravity.CENTER
            setPadding(l.dp(4), l.dp(8), l.dp(4), l.dp(6))
            background = ripple(Color.TRANSPARENT, l.dpf(16), t.cardPressed)
            addView(ImageView(l).apply {
                setImageResource(icon); imageTintList = ColorStateList.valueOf(t.text)
            }, LayoutParams(l.dp(22), l.dp(22)))
            addView(TextView(l).apply {
                text = l.getString(label); style(11.5f, t.text); gravity = Gravity.CENTER; maxLines = 1
                setPadding(0, l.dp(5), 0, 0)
            })
            setOnClickListener { l.closePopup(); click() }
        }, LayoutParams(0, -2, 1f))
    }

    fun show(anchor: View) {
        val w = l.dp(236)
        val lp = FrameLayout.LayoutParams(w, -2)
        l.root.addView(this, lp)
        measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(l.root.height, View.MeasureSpec.AT_MOST))
        val r = l.drag.mapRectToRoot(anchor, RectF(0f, 0f, anchor.width.toFloat(), anchor.height.toFloat()))
        val iconTop = if (anchor is IconView) { val ir = android.graphics.Rect(); anchor.iconBounds(ir); r.top + ir.top } else r.top
        val h = measuredHeight
        val above = iconTop - h - l.dp(10)
        val y = if (above > l.dp(40)) above else (r.bottom + l.dp(6)).coerceAtMost(l.root.height - h - l.dp(16f).toFloat())
        val x = (r.centerX() - w / 2f).coerceIn(l.dp(12).toFloat(), (l.root.width - w - l.dp(12)).toFloat())
        translationX = x; translationY = y
        pivotX = (r.centerX() - x).coerceIn(0f, w.toFloat()); pivotY = if (y < iconTop) h.toFloat() else 0f
        alpha = 0f; scaleX = 0.85f; scaleY = 0.85f
        animateTo(alpha = 1f, scale = 1f, dur = l.settings.animDuration(220))
        l.updateBack()
    }

    fun dismiss() {
        val d = l.settings.animDuration(150)
        if (d == 0L) { l.root.removeView(this); return }
        animateTo(alpha = 0f, scale = 0.92f, dur = d) { l.root.removeView(this) }
        l.root.post { l.updateBack() }
    }

    companion object {
        fun forItem(l: HomeActivity, v: View): PopupCard {
            val p = PopupCard(l)
            val item = (v as? IconView)?.item
            val a: AppInfo? = (v as? IconView)?.app ?: item?.let { l.app.apps.find(it.component, it.userSerial) }
            val fromDrawer = item == null
            if (a != null) {
                val la = l.getSystemService(android.content.pm.LauncherApps::class.java)
                val dens = l.resources.displayMetrics.densityDpi
                for (s in l.app.apps.shortcutsFor(a)) {
                    val icon = try { la.getShortcutIconDrawable(s, dens) } catch (e: Exception) { null }
                    p.row(icon, (s.shortLabel ?: s.longLabel ?: "").toString(), { l.launchShortcut(s, v) }) {
                        // Mantener pulsado un acceso directo lo fija en inicio.
                        l.drag.let { _ ->
                            l.app.apps.pinShortcut(s)
                            val it = HomeItem(l.model.newId(), HomeItem.TYPE_SHORTCUT, HomeItem.DESKTOP).apply {
                                pkg = s.`package`; shortcutId = s.id; userSerial = l.app.apps.serialOf(s.userHandle)
                                title = (s.shortLabel ?: "").toString()
                            }
                            l.model.placeAnywhere(it, l.profile.cols, l.profile.rows, l.workspace.currentPage)
                            l.model.notifyChanged()
                            Toast.makeText(l, R.string.added_to_home, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                p.action(R.drawable.ic_info, R.string.app_info) { l.appInfo(a) }
            }
            if (fromDrawer && a != null) {
                p.action(R.drawable.ic_add, R.string.add_to_home) {
                    val it = l.model.newAppItem(a)
                    l.model.placeAnywhere(it, l.profile.cols, l.profile.rows, l.workspace.currentPage)
                    l.model.notifyChanged()
                    Toast.makeText(l, R.string.added_to_home, Toast.LENGTH_SHORT).show()
                }
                p.action(R.drawable.ic_hide, R.string.hide) {
                    l.settings.hiddenApps = l.settings.hiddenApps + a.key
                }
            }
            if (item != null) {
                if (item.isFolder) p.action(R.drawable.ic_edit, R.string.rename) { (v as? FolderIconView)?.let { l.openFolder(it); l.folder?.editTitle() } }
                p.action(R.drawable.ic_delete, if (item.isFolder) R.string.remove_folder else R.string.remove) {
                    l.model.remove(item); l.model.cleanupFolders(); l.model.notifyChanged()
                }
            }
            if (a != null && !a.isSystem) p.action(R.drawable.ic_close, R.string.uninstall) { l.uninstall(a) }
            return p
        }

        fun forWidget(l: HomeActivity, v: WidgetView): PopupCard {
            val p = PopupCard(l)
            val item = v.item
            val info = item?.let { l.app.widgetHost.info(it.widgetId) }
            if (info != null && info.resizeMode != AppWidgetProviderInfo.RESIZE_NONE)
                p.action(R.drawable.ic_resize, R.string.resize) { l.startResize(v) }
            if (info?.configure != null && (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0)
                p.action(R.drawable.ic_settings, R.string.widget_settings) { l.reconfigureWidget(item) }
            if (item != null) p.action(R.drawable.ic_delete, R.string.remove) {
                l.model.remove(item); l.model.notifyChanged()
            }
            return p
        }
    }
}

/** Zona superior durante el arrastre: Eliminar · Info de app · Desinstalar. */
class DropBar(private val l: HomeActivity) : LinearLayout(l) {
    private val targets = ArrayList<Pair<Int, TextView>>()
    private var hovered = NONE

    init { orientation = HORIZONTAL; gravity = Gravity.CENTER }

    fun show(canRemove: Boolean, canAppActions: Boolean, app: AppInfo?) {
        removeAllViews(); targets.clear(); hovered = NONE
        fun add(id: Int, icon: Int, label: Int) {
            val tv = TextView(l).apply {
                text = l.getString(label); style(14f, Color.WHITE, Fonts.medium); gravity = Gravity.CENTER
                setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
                compoundDrawablePadding = l.dp(6)
                compoundDrawableTintList = ColorStateList.valueOf(Color.WHITE)
                setPadding(l.dp(16), 0, l.dp(16), 0)
                background = roundRect(0x33000000, l.dpf(22))
                setShadowLayer(l.dpf(2), 0f, l.dpf(1), 0x44000000)
            }
            addView(tv, LayoutParams(-2, l.dp(44)).apply { setMargins(l.dp(5), 0, l.dp(5), 0) })
            targets += id to tv
        }
        if (canRemove) add(REMOVE, R.drawable.ic_delete, R.string.remove)
        if (app != null && canAppActions) {
            add(INFO, R.drawable.ic_info, R.string.app_info)
            if (!app.isSystem) add(UNINSTALL, R.drawable.ic_close, R.string.uninstall)
        }
        visibility = VISIBLE; alpha = 0f; translationY = -l.dpf(20)
        animateTo(alpha = 1f, ty = 0f, dur = l.settings.animDuration(200))
    }

    fun hide() {
        if (visibility != VISIBLE) return
        animateTo(alpha = 0f, ty = -l.dpf(20), dur = l.settings.animDuration(160)) { visibility = GONE }
    }

    fun hover(x: Float, y: Float): Int {
        if (visibility != VISIBLE) return NONE
        var found = NONE
        if (y < bottom + l.dp(8)) {
            for ((id, tv) in targets) {
                if (x >= tv.left - l.dp(10) && x <= tv.right + l.dp(10)) found = id
            }
            if (found == NONE && targets.size == 1) found = targets[0].first
        }
        if (found != hovered) {
            hovered = found
            for ((id, tv) in targets) {
                val on = id == found
                tv.background = roundRect(if (on) (if (id == REMOVE || id == UNINSTALL) 0xE6E5484D.toInt() else withAlpha(l.theme.accent, 0.9f)) else 0x33000000, l.dpf(22))
                tv.animate().scaleX(if (on) 1.08f else 1f).scaleY(if (on) 1.08f else 1f).setDuration(120).start()
            }
            if (found != NONE) performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        }
        return found
    }

    companion object {
        const val NONE = 0; const val REMOVE = 1; const val UNINSTALL = 2; const val INFO = 3
    }
}
