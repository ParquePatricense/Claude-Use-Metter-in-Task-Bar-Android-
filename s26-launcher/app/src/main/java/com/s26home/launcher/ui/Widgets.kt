package com.s26home.launcher.ui

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.s26home.launcher.R
import com.s26home.launcher.data.HomeItem
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

object WidgetSizing {
    private fun cells(px: Int, cell: Int, pad: Int) = ceil((px + pad).toFloat() / cell.coerceAtLeast(1)).toInt().coerceAtLeast(1)

    /** Tamaño inicial en celdas: targetCell (API 31) o el mínimo declarado. */
    fun span(ctx: Context, info: AppWidgetProviderInfo, p: DeviceProfile): Pair<Int, Int> {
        val pad = ctx.dp(12)
        var w = cells(info.minWidth, p.cellW, pad); var h = cells(info.minHeight, p.cellH, pad)
        if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            w = info.targetCellWidth; h = info.targetCellHeight
        }
        return w.coerceIn(1, p.cols) to h.coerceIn(1, p.rows)
    }

    fun minSpan(ctx: Context, info: AppWidgetProviderInfo, p: DeviceProfile): Pair<Int, Int> {
        val pad = ctx.dp(12)
        val mw = if (info.minResizeWidth in 1 until info.minWidth) info.minResizeWidth else info.minWidth
        val mh = if (info.minResizeHeight in 1 until info.minHeight) info.minResizeHeight else info.minHeight
        val canW = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
        val canH = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
        return (if (canW) cells(mw, p.cellW, pad) else 1).coerceIn(1, p.cols) to (if (canH) cells(mh, p.cellH, pad) else 1).coerceIn(1, p.rows)
    }

    fun maxSpan(ctx: Context, info: AppWidgetProviderInfo, p: DeviceProfile): Pair<Int, Int> {
        var w = p.cols; var h = p.rows
        if (Build.VERSION.SDK_INT >= 31) {
            if (info.maxResizeWidth > 0) w = (info.maxResizeWidth / p.cellW.coerceAtLeast(1)).coerceAtLeast(1)
            if (info.maxResizeHeight > 0) h = (info.maxResizeHeight / p.cellH.coerceAtLeast(1)).coerceAtLeast(1)
        }
        return w.coerceIn(1, p.cols) to h.coerceIn(1, p.rows)
    }
}

/** Selector de widgets estilo One UI: lista de apps; al tocar una se despliegan sus vistas previas. */
class WidgetPicker(private val l: HomeActivity) : FrameLayout(l) {
    private val t = l.theme
    private val list = LinearLayout(l).apply { orientation = LinearLayout.VERTICAL }
    private var groups: List<Pair<String, List<AppWidgetProviderInfo>>> = emptyList()
    private var filter = ""
    private val previewCache = HashMap<AppWidgetProviderInfo, android.graphics.drawable.Drawable?>()

    init {
        setBackgroundColor(if (t.dark) 0xFF111113.toInt() else 0xFFF3F3F6.toInt())
        isClickable = true
        val col = LinearLayout(l).apply { orientation = LinearLayout.VERTICAL; setPadding(0, l.profile.workspaceTop + l.dp(10), 0, 0) }
        val head = LinearLayout(l).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(l.dp(8), 0, l.dp(16), 0) }
        head.addView(ImageView(l).apply {
            setImageResource(R.drawable.ic_back); imageTintList = ColorStateList.valueOf(t.text)
            setPadding(l.dp(12), l.dp(12), l.dp(12), l.dp(12)); background = ripple(Color.TRANSPARENT, l.dpf(24), t.cardPressed)
            setOnClickListener { close(true) }
        }, LinearLayout.LayoutParams(l.dp(48), l.dp(48)))
        head.addView(TextView(l).apply { text = l.getString(R.string.widgets); style(26f, t.text, Fonts.bold); setPadding(l.dp(6), 0, 0, 0) })
        col.addView(head)
        val searchBox = EditText(l).apply {
            hint = l.getString(R.string.search_widgets); setHintTextColor(t.textSecondary); style(16f, t.text)
            setSingleLine(); background = roundRect(t.field, l.dpf(24)); setPadding(l.dp(18), 0, l.dp(18), 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(e: Editable?) { filter = e?.toString().orEmpty().lowercase(); bind() }
            })
        }
        col.addView(searchBox, LinearLayout.LayoutParams(-1, l.dp(48)).apply { setMargins(l.dp(18), l.dp(8), l.dp(18), l.dp(10)) })
        val scroll = ScrollView(l).apply { isVerticalScrollBarEnabled = false; clipToPadding = false; setPadding(0, 0, 0, l.profile.insetBottom + l.dp(16)) }
        scroll.addView(list)
        col.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(col, LayoutParams(-1, -1))
        l.app.bg.execute {
            val g = l.app.widgetHost.providers()
            l.app.main.post { groups = g; bind() }
        }
    }

    private fun bind() {
        list.removeAllViews()
        if (groups.isEmpty()) {
            list.addView(TextView(l).apply { text = l.getString(R.string.no_results); style(16f, t.textSecondary); gravity = Gravity.CENTER; setPadding(0, l.dp(60), 0, 0) })
            return
        }
        val pm = l.packageManager
        for ((label, infos) in groups) {
            val matches = if (filter.isEmpty()) infos else if (label.lowercase().contains(filter)) infos
                else infos.filter { it.loadLabel(pm).lowercase().contains(filter) }
            if (matches.isEmpty()) continue
            list.addView(group(label, matches), LinearLayout.LayoutParams(-1, -2).apply { setMargins(l.dp(14), l.dp(5), l.dp(14), l.dp(5)) })
        }
    }

    private fun group(label: String, infos: List<AppWidgetProviderInfo>): View {
        val box = LinearLayout(l).apply { orientation = LinearLayout.VERTICAL; background = roundRect(t.card, l.dpf(24)) }
        val head = LinearLayout(l).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(l.dp(16), l.dp(12), l.dp(16), l.dp(12))
            background = ripple(Color.TRANSPARENT, l.dpf(24), t.cardPressed)
        }
        val appIcon = ImageView(l)
        val pkg = infos[0].provider.packageName
        try { appIcon.setImageDrawable(l.packageManager.getApplicationIcon(pkg)) } catch (e: Exception) { }
        head.addView(appIcon, LinearLayout.LayoutParams(l.dp(40), l.dp(40)).apply { rightMargin = l.dp(14) })
        val texts = LinearLayout(l).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(l).apply { text = label; style(17f, t.text, Fonts.medium) })
        texts.addView(TextView(l).apply { text = l.resources.getQuantityString(R.plurals.widget_count, infos.size, infos.size); style(13.5f, t.textSecondary); setPadding(0, l.dp(3), 0, 0) })
        head.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        val chevron = ImageView(l).apply { setImageResource(R.drawable.ic_chevron); imageTintList = ColorStateList.valueOf(t.textSecondary); rotation = 90f }
        head.addView(chevron, LinearLayout.LayoutParams(l.dp(24), l.dp(24)))
        box.addView(head)
        val strip = HorizontalScrollView(l).apply { isHorizontalScrollBarEnabled = false; visibility = GONE }
        val row = LinearLayout(l).apply { orientation = LinearLayout.HORIZONTAL; setPadding(l.dp(12), 0, l.dp(12), l.dp(14)) }
        strip.addView(row)
        box.addView(strip)
        head.setOnClickListener {
            val show = strip.visibility != VISIBLE
            strip.visibility = if (show) VISIBLE else GONE
            chevron.animate().rotation(if (show) 270f else 90f).setDuration(200).start()
            if (show && row.childCount == 0) infos.forEach { row.addView(preview(it)) }
        }
        return box
    }

    private fun preview(info: AppWidgetProviderInfo): View {
        val (w, h) = WidgetSizing.span(l, info, l.profile)
        val cellPx = l.dp(64)
        val pw = (w * cellPx).coerceIn(l.dp(110), l.dp(260)); val ph = (h * cellPx * 0.9f).toInt().coerceIn(l.dp(70), l.dp(200))
        val col = LinearLayout(l).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(l.dp(6), l.dp(4), l.dp(6), l.dp(4))
            background = ripple(Color.TRANSPARENT, l.dpf(18), t.cardPressed)
        }
        val img = ImageView(l).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = roundRect(t.field, l.dpf(18))
            setPadding(l.dp(8), l.dp(8), l.dp(8), l.dp(8))
        }
        col.addView(img, LinearLayout.LayoutParams(pw, ph))
        col.addView(TextView(l).apply {
            text = info.loadLabel(l.packageManager); style(13f, t.text); gravity = Gravity.CENTER; maxLines = 2
            setPadding(0, l.dp(6), 0, 0); maxWidth = pw
        })
        col.addView(TextView(l).apply { text = "$w × $h"; style(12f, t.textSecondary); gravity = Gravity.CENTER })
        val dens = l.resources.displayMetrics.densityDpi
        if (previewCache.containsKey(info)) img.setImageDrawable(previewCache[info])
        else l.app.bg.execute {
            val d = l.app.widgetHost.preview(info, dens)
            l.app.main.post { previewCache[info] = d; img.setImageDrawable(d) }
        }
        col.setOnClickListener { l.addWidget(info) }
        return col
    }

    fun open() {
        alpha = 0f; translationY = l.dpf(60)
        animateTo(alpha = 1f, ty = 0f, dur = l.settings.animDuration(300))
    }

    fun close(animate: Boolean) {
        l.onPickerClosed()
        if (!animate) { (parent as? FrameLayout)?.removeView(this); return }
        animateTo(alpha = 0f, ty = l.dpf(60), dur = l.settings.animDuration(220)) { (parent as? FrameLayout)?.removeView(this) }
    }
}

/** Marco de redimensionado: cuatro tiradores que ajustan el widget celda por celda. */
class WidgetResizeFrame(private val l: HomeActivity, private val v: WidgetView, private val item: HomeItem) : View(l) {
    private val cl = v.parent as CellLayout
    private val info = l.app.widgetHost.info(item.widgetId)
    private val minS = info?.let { WidgetSizing.minSpan(l, it, l.profile) } ?: (1 to 1)
    private val maxS = info?.let { WidgetSizing.maxSpan(l, it, l.profile) } ?: (l.profile.cols to l.profile.rows)
    private val canW = info != null && info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
    private val canH = info != null && info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = l.dpf(2.5f); color = l.theme.accent }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val handleRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = l.dpf(2.5f); color = l.theme.accent }
    private var grab = 0 // 1 izq, 2 der, 3 arriba, 4 abajo
    private val r = RectF()

    fun show() {
        l.root.addView(this, FrameLayout.LayoutParams(-1, -1))
        alpha = 0f; animateTo(alpha = 1f, dur = 150)
    }

    private fun frameRect(): RectF {
        val rr = Rect(); cl.cellRect(item.x, item.y, item.w, item.h, rr)
        return l.drag.mapRectToRoot(cl, RectF(rr)).apply { inset(l.dpf(4), l.dpf(4)) }
    }

    override fun onDraw(c: Canvas) {
        r.set(frameRect())
        c.drawRoundRect(r, l.dpf(22), l.dpf(22), stroke)
        val hr = l.dpf(9)
        if (canW) { dot(c, r.left, r.centerY(), hr); dot(c, r.right, r.centerY(), hr) }
        if (canH) { dot(c, r.centerX(), r.top, hr); dot(c, r.centerX(), r.bottom, hr) }
    }

    private fun dot(c: Canvas, x: Float, y: Float, rad: Float) { c.drawCircle(x, y, rad, handle); c.drawCircle(x, y, rad, handleRing) }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val x = ev.x; val y = ev.y
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                r.set(frameRect())
                val tol = l.dp(30)
                grab = when {
                    canW && abs(x - r.left) < tol && y in r.top..r.bottom -> 1
                    canW && abs(x - r.right) < tol && y in r.top..r.bottom -> 2
                    canH && abs(y - r.top) < tol && x in r.left..r.right -> 3
                    canH && abs(y - r.bottom) < tol && x in r.left..r.right -> 4
                    else -> 0
                }
                if (grab == 0) { commit(); return false }
            }
            MotionEvent.ACTION_MOVE -> if (grab != 0) resizeTo(x, y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { grab = 0; l.model.save() }
        }
        return true
    }

    private fun resizeTo(x: Float, y: Float) {
        val pt = l.drag.mapPointFromRoot(cl, x, y)
        val cx = ((pt[0] - cl.paddingLeft) / cl.cellW).roundToInt()
        val cy = ((pt[1] - cl.paddingTop) / cl.cellH).roundToInt()
        var nx = item.x; var ny = item.y; var nw = item.w; var nh = item.h
        when (grab) {
            1 -> { val right = item.x + item.w; nx = cx.coerceIn(right - maxS.first, right - minS.first).coerceAtLeast(0); nw = right - nx }
            2 -> nw = (cx - item.x).coerceIn(minS.first, maxS.first).coerceAtMost(cl.cols - item.x)
            3 -> { val bottom = item.y + item.h; ny = cy.coerceIn(bottom - maxS.second, bottom - minS.second).coerceAtLeast(0); nh = bottom - ny }
            4 -> nh = (cy - item.y).coerceIn(minS.second, maxS.second).coerceAtMost(cl.rows - item.y)
        }
        if (nx == item.x && ny == item.y && nw == item.w && nh == item.h) return
        val g = l.model.occupancy(item.page, cl.cols, cl.rows, item)
        if (!l.model.isFree(g, nx, ny, nw, nh)) return
        item.x = nx; item.y = ny; item.w = nw; item.h = nh
        (v.layoutParams as CellLayout.LP).apply { this.x = nx; this.y = ny; w = nw; h = nh }
        v.requestLayout()
        l.updateWidgetSize(v, item)
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        invalidate()
    }

    fun commit() {
        if (parent == null) return
        l.model.save()
        l.onResizeDone()
        animateTo(alpha = 0f, dur = 120) { (parent as? FrameLayout)?.removeView(this) }
    }
}
