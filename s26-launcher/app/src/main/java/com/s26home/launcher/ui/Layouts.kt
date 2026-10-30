package com.s26home.launcher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.Interpolator
import android.widget.OverScroller
import com.s26home.launcher.LauncherApp
import com.s26home.launcher.data.IconCache
import kotlin.math.abs
import kotlin.math.roundToInt

/** Cuadrícula de celdas. Cada hijo declara su celda y su tamaño en celdas. */
class CellLayout(ctx: Context, var cols: Int, var rows: Int) : ViewGroup(ctx) {
    class LP(var x: Int, var y: Int, var w: Int = 1, var h: Int = 1, val widget: Boolean = false) :
        ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)

    var cellW = 0; private set
    var cellH = 0; private set
    var page = 0
    var editMode = false
        set(v) { field = v; invalidate() }
    var editBgColor = 0x26FFFFFF
    private var hint: Rect? = null
    private var hintIcon = 0
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFFFFF }
    private val editPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tmp = RectF()

    init { setWillNotDraw(false); clipChildren = false; clipToPadding = false }

    fun add(v: View, lp: LP) { addView(v, lp) }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec); val h = MeasureSpec.getSize(hSpec)
        setMeasuredDimension(w, h)
        cellW = (w - paddingLeft - paddingRight) / cols
        cellH = (h - paddingTop - paddingBottom) / rows
        for (i in 0 until childCount) {
            val c = getChildAt(i); val lp = c.layoutParams as LP
            val inset = if (lp.widget) dp(6) * 2 else 0
            c.measure(
                MeasureSpec.makeMeasureSpec((lp.w * cellW - inset).coerceAtLeast(0), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((lp.h * cellH - inset).coerceAtLeast(0), MeasureSpec.EXACTLY)
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val c = getChildAt(i); val lp = c.layoutParams as LP
            val inset = if (lp.widget) dp(6) else 0
            val left = paddingLeft + lp.x * cellW + inset
            val top = paddingTop + lp.y * cellH + inset
            c.layout(left, top, left + c.measuredWidth, top + c.measuredHeight)
        }
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?) = p is LP

    /** Celda bajo un punto local; para tamaños >1 centra el bloque en el dedo. */
    fun cellAt(px: Float, py: Float, w: Int = 1, h: Int = 1): IntArray {
        val x = ((px - paddingLeft) / cellW.coerceAtLeast(1) - (w - 1) / 2f).toInt().coerceIn(0, (cols - w).coerceAtLeast(0))
        val y = ((py - paddingTop) / cellH.coerceAtLeast(1) - (h - 1) / 2f).toInt().coerceIn(0, (rows - h).coerceAtLeast(0))
        return intArrayOf(x, y)
    }

    fun cellRect(x: Int, y: Int, w: Int, h: Int, out: Rect) {
        out.set(paddingLeft + x * cellW, paddingTop + y * cellH, paddingLeft + (x + w) * cellW, paddingTop + (y + h) * cellH)
    }

    fun childAt(x: Int, y: Int): View? {
        for (i in 0 until childCount) {
            val c = getChildAt(i); val lp = c.layoutParams as LP
            if (x >= lp.x && x < lp.x + lp.w && y >= lp.y && y < lp.y + lp.h) return c
        }
        return null
    }

    fun showHint(x: Int, y: Int, w: Int, h: Int, iconPx: Int) {
        val r = Rect(); cellRect(x, y, w, h, r)
        if (r == hint) return
        hint = r; hintIcon = iconPx; invalidate()
    }

    fun clearHint() { if (hint != null) { hint = null; invalidate() } }

    override fun onDraw(c: Canvas) {
        if (editMode) {
            editPaint.color = editBgColor
            tmp.set(0f, 0f, width.toFloat(), height.toFloat())
            c.drawRoundRect(tmp, dpf(26), dpf(26), editPaint)
        }
        val h = hint ?: return
        if (h.width() <= cellW && h.height() <= cellH) {
            // Fantasma del ícono con la misma forma squircle.
            val s = hintIcon.toFloat()
            val l = h.exactCenterX() - s / 2
            val firstChild = (0 until childCount).map { getChildAt(it) }.firstOrNull { it is IconView } as IconView?
            val top = if (firstChild != null) {
                val ir = Rect(); firstChild.iconBounds(ir); h.top + ir.top.toFloat()
            } else h.exactCenterY() - s / 2
            c.save(); c.translate(l, top)
            c.drawPath(IconCache.shapePath(s, LauncherApp.get(context).settings.iconShape), hintPaint)
            c.restore()
        } else {
            tmp.set(h); tmp.inset(dpf(6), dpf(6))
            c.drawRoundRect(tmp, dpf(22), dpf(22), hintPaint)
        }
    }
}

/** Paginador horizontal liviano con física de inercia tipo One UI. */
open class PagedView(ctx: Context) : ViewGroup(ctx) {
    var currentPage = 0; private set
    var onScrollProgress: ((Float) -> Unit)? = null
    var onPageChanged: ((Int) -> Unit)? = null
    var scrollEnabled = true
    var durationScale = 1f
    private val scroller = OverScroller(ctx, Interpolator { t -> Anim.EASE_OUT.getInterpolation(t) })
    private var vt: VelocityTracker? = null
    private val slop = ViewConfiguration.get(ctx).scaledPagingTouchSlop
    private val minFling = ViewConfiguration.get(ctx).scaledMinimumFlingVelocity * 2
    private var downX = 0f; private var downY = 0f; private var lastX = 0f
    private var dragging = false

    val pageCount get() = childCount

    init { isHorizontalScrollBarEnabled = false; clipChildren = false }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec); val h = MeasureSpec.getSize(hSpec)
        setMeasuredDimension(w, h)
        val cw = MeasureSpec.makeMeasureSpec(w - paddingLeft - paddingRight, MeasureSpec.EXACTLY)
        val ch = MeasureSpec.makeMeasureSpec(h - paddingTop - paddingBottom, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) getChildAt(i).measure(cw, ch)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            c.layout(i * w + paddingLeft, paddingTop, i * w + paddingLeft + c.measuredWidth, paddingTop + c.measuredHeight)
        }
        if (changed || scroller.isFinished) {
            currentPage = currentPage.coerceIn(0, (childCount - 1).coerceAtLeast(0))
            if (scroller.isFinished) scrollTo(currentPage * w, 0)
        }
    }

    fun pageWidth() = width.coerceAtLeast(1)

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScrollProgress?.invoke(l.toFloat() / pageWidth())
    }

    fun setCurrentPageImmediate(p: Int) {
        scroller.forceFinished(true)
        currentPage = p.coerceIn(0, (childCount - 1).coerceAtLeast(0))
        scrollTo(currentPage * width, 0)
        onPageChanged?.invoke(currentPage)
    }

    fun snapToPage(p: Int, velocity: Float = 0f) {
        val target = p.coerceIn(0, (childCount - 1).coerceAtLeast(0))
        val changed = target != currentPage
        currentPage = target
        val dx = target * pageWidth() - scrollX
        val base = if (abs(velocity) > minFling) 300 else 420
        scroller.startScroll(scrollX, 0, dx, 0, (base * durationScale).toInt().coerceAtLeast(1))
        postInvalidateOnAnimation()
        if (changed) onPageChanged?.invoke(currentPage)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX, 0)
            postInvalidateOnAnimation()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!scrollEnabled || childCount < 2) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; lastX = ev.x
                dragging = !scroller.isFinished && abs(scroller.finalX - scroller.currX) > slop
                if (dragging) scroller.forceFinished(true)
                vt?.recycle(); vt = VelocityTracker.obtain(); vt?.addMovement(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(ev)
                val dx = abs(ev.x - downX); val dy = abs(ev.y - downY)
                if (!dragging && dx > slop && dx > dy * 1.1f) { dragging = true; lastX = ev.x; parent?.requestDisallowInterceptTouchEvent(true) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (!dragging) { vt?.recycle(); vt = null }
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!scrollEnabled) return false
        if (vt == null) vt = VelocityTracker.obtain()
        vt?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = ev.x; lastX = ev.x; return true }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(ev.x - downX) > slop) { dragging = true; lastX = ev.x; parent?.requestDisallowInterceptTouchEvent(true) }
                if (dragging) {
                    var dx = lastX - ev.x
                    val max = (childCount - 1) * pageWidth()
                    if (scrollX + dx < 0 || scrollX + dx > max) dx *= 0.35f // resistencia en los bordes
                    scrollBy(dx.roundToInt(), 0)
                    lastX = ev.x
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    vt?.computeCurrentVelocity(1000)
                    val v = vt?.xVelocity ?: 0f
                    val exact = scrollX.toFloat() / pageWidth()
                    val target = when {
                        v < -minFling -> kotlin.math.floor(exact).toInt() + 1
                        v > minFling -> kotlin.math.ceil(exact).toInt() - 1
                        else -> exact.roundToInt()
                    }
                    snapToPage(target.coerceIn(currentPage - 1, currentPage + 1), v)
                }
                dragging = false; vt?.recycle(); vt = null
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) snapToPage((scrollX.toFloat() / pageWidth()).roundToInt())
                dragging = false; vt?.recycle(); vt = null
            }
        }
        return true
    }
}

/** Puntos de página One UI: activos opacos, inactivos al 40 %; el actual se estira. */
class PageIndicator(ctx: Context) : View(ctx) {
    var count = 1
        set(v) { field = v; invalidate() }
    var progress = 0f
        set(v) { field = v; invalidate() }
    var color = 0xFFFFFFFF.toInt()
        set(v) { field = v; invalidate() }
    var homePage = -1
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        if (count <= 1) return
        val d = dpf(6); val gap = dpf(8)
        val total = count * d + (count - 1) * gap
        var x = (width - total) / 2f
        val cy = height / 2f
        for (i in 0 until count) {
            val dist = abs(progress - i).coerceAtMost(1f)
            val a = 0.4f + 0.6f * (1 - dist)
            p.color = withAlpha(color, a * (color ushr 24) / 255f)
            if (i == homePage) {
                // Casita para la página principal, como en One UI.
                val s = d * 1.3f
                val path = android.graphics.Path().apply {
                    moveTo(x + d / 2, cy - s / 2); lineTo(x + d / 2 + s / 2, cy); lineTo(x + d / 2 + s / 2 - s * 0.12f, cy)
                    lineTo(x + d / 2 + s / 2 - s * 0.12f, cy + s / 2); lineTo(x + d / 2 - s / 2 + s * 0.12f, cy + s / 2)
                    lineTo(x + d / 2 - s / 2 + s * 0.12f, cy); lineTo(x + d / 2 - s / 2, cy); close()
                }
                c.drawPath(path, p)
            } else {
                r.set(x, cy - d / 2, x + d, cy + d / 2)
                c.drawOval(r, p)
            }
            x += d + gap
        }
    }
}

/** Grilla simple para páginas de la pantalla de apps (sin reciclaje: ~30 vistas por página). */
class GridPage(ctx: Context, val cols: Int, val rows: Int) : ViewGroup(ctx) {
    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec); val h = MeasureSpec.getSize(hSpec)
        setMeasuredDimension(w, h)
        val cw = (w - paddingLeft - paddingRight) / cols; val ch = (h - paddingTop - paddingBottom) / rows
        val ws = MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY); val hs = MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) getChildAt(i).measure(ws, hs)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cw = (width - paddingLeft - paddingRight) / cols; val ch = (height - paddingTop - paddingBottom) / rows
        for (i in 0 until childCount) {
            val c = getChildAt(i); val x = i % cols; val y = i / cols
            c.layout(paddingLeft + x * cw, paddingTop + y * ch, paddingLeft + (x + 1) * cw, paddingTop + (y + 1) * ch)
        }
    }
}

fun View.animateTo(alpha: Float? = null, scale: Float? = null, ty: Float? = null, dur: Long, interp: android.animation.TimeInterpolator = Anim.EASE_OUT, end: (() -> Unit)? = null) {
    val a = animate().setDuration(dur).setInterpolator(interp)
    alpha?.let { a.alpha(it) }; scale?.let { a.scaleX(it).scaleY(it) }; ty?.let { a.translationY(it) }
    a.withEndAction { end?.invoke() }.start()
}

fun valueAnim(from: Float, to: Float, dur: Long, interp: android.animation.TimeInterpolator = Anim.EASE_OUT, update: (Float) -> Unit) =
    ValueAnimator.ofFloat(from, to).apply {
        duration = dur; interpolator = interp
        addUpdateListener { update(it.animatedValue as Float) }
    }
