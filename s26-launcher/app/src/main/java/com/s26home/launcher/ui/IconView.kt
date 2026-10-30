package com.s26home.launcher.ui

import android.animation.ValueAnimator
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.s26home.launcher.LauncherApp
import com.s26home.launcher.data.AppInfo
import com.s26home.launcher.data.HomeItem
import com.s26home.launcher.data.IconCache
import kotlin.math.abs

/**
 * Ícono + etiqueta dibujados en una sola View (sin TextView anidado): menos objetos,
 * menos layout y menos RAM; clave para mantener 120 Hz al deslizar escritorios.
 */
open class IconView(ctx: Context) : View(ctx) {
    var item: HomeItem? = null
    var app: AppInfo? = null
    var label: String = ""
        set(v) { field = v; ellipsized = null; invalidate() }
    var iconSize = dp(56)
        set(v) { field = v; requestLayout(); invalidate() }
    var showLabel = true
        set(v) { field = v; invalidate() }
    var labelLines = 1
    var lightText = true
        set(v) {
            field = v
            textPaint.color = if (v) 0xFFFFFFFF.toInt() else 0xFF1A1A1C.toInt()
            if (v) textPaint.setShadowLayer(dpf(2.5f), 0f, dpf(0.6f), 0x66000000)
            else textPaint.clearShadowLayer()
            invalidate()
        }
    /** Las etiquetas sobre paneles (pantalla de apps, carpetas) van sin sombra y con color de tema. */
    fun panelText(color: Int) { textPaint.color = color; textPaint.clearShadowLayer(); invalidate() }

    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Fonts.regular; textSize = context.sp(12)
    }
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    protected var bitmap: Bitmap? = null
    private var ellipsized: CharSequence? = null
    private var ellipsizedW = 0
    private var iconAlpha = 1f
    private var pressScale = 1f
    private var pressAnim: ValueAnimator? = null
    protected val iconRect = RectF()
    private var boundKey: String? = null

    init { lightText = true }

    fun setLabelSize(px: Float) { textPaint.textSize = px; ellipsized = null; invalidate() }

    fun bindIcon(key: String, source: () -> Drawable?) {
        boundKey = key
        val cache = LauncherApp.get(context).icons
        val hit = cache.peek(key)
        if (hit != null) { bitmap = hit; iconAlpha = 1f; invalidate(); return }
        bitmap = null
        cache.load(key, source) { b ->
            if (boundKey != key) return@load
            bitmap = b
            if (isAttachedToWindow && isShown) {
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 160
                    addUpdateListener { iconAlpha = it.animatedValue as Float; invalidate() }
                    start()
                }
            } else { iconAlpha = 1f; invalidate() }
        }
    }

    fun iconBitmap(): Bitmap? = bitmap

    /** Rectángulo del ícono en coordenadas de la vista (para animaciones y arrastre). */
    fun iconBounds(out: Rect) {
        computeIconRect()
        out.set(iconRect.left.toInt(), iconRect.top.toInt(), iconRect.right.toInt(), iconRect.bottom.toInt())
    }

    protected fun contentHeight(): Float {
        if (!showLabel) return iconSize.toFloat()
        val fm = textPaint.fontMetrics
        return iconSize + dpf(6) + (fm.descent - fm.ascent) * labelLines
    }

    private fun computeIconRect() {
        val top = (height - contentHeight()) / 2f
        val left = (width - iconSize) / 2f
        iconRect.set(left, top, left + iconSize, top + iconSize)
    }

    override fun onDraw(c: Canvas) {
        computeIconRect()
        val save = c.save()
        if (pressScale != 1f) c.scale(pressScale, pressScale, iconRect.centerX(), iconRect.centerY())
        drawIcon(c, iconRect)
        c.restoreToCount(save)
        if (showLabel && label.isNotEmpty()) {
            val avail = width - dp(4)
            if (ellipsized == null || ellipsizedW != avail) {
                ellipsized = TextUtils.ellipsize(label, textPaint, avail.toFloat(), TextUtils.TruncateAt.END)
                ellipsizedW = avail
            }
            val fm = textPaint.fontMetrics
            val y = iconRect.bottom + dpf(6) - fm.ascent
            val t = ellipsized!!
            c.drawText(t, 0, t.length, width / 2f, y, textPaint)
        }
    }

    protected open fun drawIcon(c: Canvas, r: RectF) {
        val b = bitmap ?: return
        bmpPaint.alpha = (iconAlpha * 255).toInt()
        c.drawBitmap(b, null, r, bmpPaint)
    }

    override fun setPressed(pressed: Boolean) {
        val was = isPressed
        super.setPressed(pressed)
        if (was == pressed) return
        pressAnim?.cancel()
        pressAnim = ValueAnimator.ofFloat(pressScale, if (pressed) 0.9f else 1f).apply {
            duration = if (pressed) 120 else 200
            interpolator = Anim.EASE_OUT
            addUpdateListener { pressScale = it.animatedValue as Float; invalidate() }
            start()
        }
    }
}

/** Ícono de carpeta One UI: squircle translúcido con miniaturas en 2×2 o 3×3. */
class FolderIconView(ctx: Context) : IconView(ctx) {
    var children: List<HomeItem> = emptyList()
        set(v) { field = v; loadMinis() }
    var grid = 2
    var folderColor = 0
        set(v) { field = v; invalidate() }
    var hover = false
        set(v) {
            if (field == v) return
            field = v
            ValueAnimator.ofFloat(hoverScale, if (v) 1.12f else 1f).apply {
                duration = 180; interpolator = Anim.EASE_OUT
                addUpdateListener { hoverScale = it.animatedValue as Float; invalidate() }
                start()
            }
        }
    private var hoverScale = 1f
    private val minis = HashMap<Long, Bitmap>()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val tmp = RectF()

    private fun loadMinis() {
        val app = LauncherApp.get(context)
        minis.clear()
        for (k in children.take(grid * grid)) {
            app.icons.load(k.iconKey, { Loader.drawableFor(context, k) }) { b -> minis[k.id] = b; invalidate() }
        }
        invalidate()
    }

    override fun drawIcon(c: Canvas, r: RectF) {
        val s = r.width()
        c.save()
        c.scale(hoverScale, hoverScale, r.centerX(), r.centerY())
        c.translate(r.left, r.top)
        val shape = LauncherApp.get(context).settings.iconShape
        bgPaint.color = if (folderColor == 0) 0x66F2F2F5 else withAlpha(folderColor, 0.92f)
        c.drawPath(IconCache.shapePath(s, shape), bgPaint)
        val pad = s * (if (grid == 2) 0.15f else 0.13f)
        val gap = s * (if (grid == 2) 0.08f else 0.05f)
        val m = (s - 2 * pad - (grid - 1) * gap) / grid
        children.take(grid * grid).forEachIndexed { i, k ->
            val b = minis[k.id] ?: return@forEachIndexed
            val cx = i % grid; val cy = i / grid
            val l = pad + cx * (m + gap); val t = pad + cy * (m + gap)
            tmp.set(l, t, l + m, t + m)
            c.drawBitmap(b, null, tmp, p)
        }
        c.restore()
    }
}

/**
 * AppWidgetHostView con detección propia de pulsación larga: los widgets consumen sus
 * toques, así que hay que interceptar a tiempo para permitir mover/redimensionar.
 */
class WidgetView(ctx: Context) : AppWidgetHostView(ctx) {
    var item: HomeItem? = null
    var onLongPress: ((WidgetView) -> Unit)? = null
    private var downX = 0f; private var downY = 0f
    private var longPressed = false
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val check = Runnable {
        if (parent != null && hasWindowFocus()) {
            longPressed = true
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onLongPress?.invoke(this)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (longPressed) { if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) longPressed = false; return true }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; longPressed = false
                postDelayed(check, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) removeCallbacks(check)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(check)
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; longPressed = false
                postDelayed(check, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }
            MotionEvent.ACTION_MOVE -> if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) removeCallbacks(check)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { removeCallbacks(check); longPressed = false }
        }
        return true
    }

    fun cancelLongPress2() { removeCallbacks(check) }
}

/** Resolución de Drawables para cualquier tipo de ítem (app o acceso directo). */
object Loader {
    fun drawableFor(ctx: Context, item: HomeItem): Drawable? {
        val app = LauncherApp.get(ctx)
        val dens = ctx.resources.displayMetrics.densityDpi
        return when (item.type) {
            HomeItem.TYPE_SHORTCUT -> {
                val user = app.apps.userFor(item.userSerial) ?: return null
                val s = app.apps.pinnedShortcut(item.pkg ?: return null, item.shortcutId ?: return null, user)
                    ?: return app.apps.findByPackage(item.pkg!!, item.userSerial)?.info?.getIcon(dens)
                ctx.getSystemService(android.content.pm.LauncherApps::class.java).getShortcutIconDrawable(s, dens)
            }
            else -> app.apps.find(item.component, item.userSerial)?.let { appIcon(ctx, it) }
        }
    }

    fun appIcon(ctx: Context, a: AppInfo): Drawable? {
        val dens = ctx.resources.displayMetrics.densityDpi
        // getBadgedIcon agrega el maletín en apps del perfil de trabajo.
        val d = a.info.getIcon(dens) ?: return null
        return if (a.isWork) ctx.packageManager.getUserBadgedIcon(d, a.user) else d
    }
}
