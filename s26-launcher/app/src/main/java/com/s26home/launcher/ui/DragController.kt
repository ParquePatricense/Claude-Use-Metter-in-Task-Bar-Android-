package com.s26home.launcher.ui

import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import com.s26home.launcher.R
import com.s26home.launcher.data.AppInfo
import com.s26home.launcher.data.HomeItem
import com.s26home.launcher.data.Settings
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Raíz de la jerarquía. Intercepta el toque cuando hay un arrastre activo y resuelve los
 * gestos verticales de inicio (arriba: apps / abajo: notificaciones o búsqueda).
 */
class DragLayer(val launcher: HomeActivity) : FrameLayout(launcher) {
    private val slop = ViewConfiguration.get(launcher).scaledTouchSlop
    private var downX = 0f; private var downY = 0f
    private var mode = 0 // 0 nada, 1 abriendo apps, 2 gesto hacia abajo consumido
    private var blockVertical = false
    private var vt: VelocityTracker? = null

    val lastTouch = FloatArray(2)

    init { clipChildren = false }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        lastTouch[0] = ev.x; lastTouch[1] = ev.y
        return super.dispatchTouchEvent(ev)
    }

    private var swallowing = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (launcher.drag.active) { launcher.drag.onTouch(ev); return true }
        // Tocar fuera del menú contextual lo cierra sin activar lo que hay debajo.
        val pop = launcher.popup
        if (pop != null && ev.actionMasked == MotionEvent.ACTION_DOWN && !launcher.drag.hitView(pop, ev.x, ev.y)) {
            launcher.closePopup(); swallowing = true; return true
        }
        return interceptGesture(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (launcher.drag.active) { launcher.drag.onTouch(ev); return true }
        if (swallowing) {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) swallowing = false
            return true
        }
        return gesture(ev)
    }

    private fun canHandleGesture() = !launcher.editMode && launcher.folder == null && launcher.popup == null &&
        launcher.picker == null && launcher.resize == null && !launcher.drawer.isOpen && launcher.drawer.progress == 0f

    private fun interceptGesture(ev: MotionEvent): Boolean {
        if (!canHandleGesture()) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; mode = 0
                vt?.recycle(); vt = VelocityTracker.obtain(); vt?.addMovement(ev)
                blockVertical = scrollableUnder(this, ev.x, ev.y)
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(ev)
                if (blockVertical) return false
                val dx = ev.x - downX; val dy = ev.y - downY
                if (abs(dy) > slop * 1.5f && abs(dy) > abs(dx) * 1.3f) {
                    val action = if (dy < 0) launcher.settings.gestureUp else launcher.settings.gestureDown
                    if (action == Settings.ACTION_NONE) return false
                    if (dy < 0 && action == Settings.ACTION_APPS) { mode = 1; downY = ev.y; return true }
                    mode = 2
                    launcher.performAction(action)
                    return true
                }
            }
        }
        return false
    }

    private fun gesture(ev: MotionEvent): Boolean {
        vt?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (mode == 1) {
                val p = ((downY - ev.y) / (height * 0.55f)).coerceIn(0f, 1f)
                launcher.drawer.setProgress(p)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mode == 1) {
                    vt?.computeCurrentVelocity(1000)
                    val v = vt?.yVelocity ?: 0f
                    val p = launcher.drawer.progress
                    val open = ev.actionMasked == MotionEvent.ACTION_UP && (v < -dp(500) || (p > 0.3f && v < dp(400)))
                    if (open) launcher.drawer.open(true, false) else launcher.drawer.close(true)
                }
                mode = 0; vt?.recycle(); vt = null
            }
        }
        return mode != 0
    }

    /** ¿Hay un widget con lista desplazable bajo el dedo? Entonces el gesto vertical es suyo. */
    private fun scrollableUnder(v: View, x: Float, y: Float): Boolean {
        if (v is android.view.ViewGroup) {
            for (i in v.childCount - 1 downTo 0) {
                val c = v.getChildAt(i)
                if (c.visibility != View.VISIBLE) continue
                val pt = floatArrayOf(x + v.scrollX - c.left, y + v.scrollY - c.top)
                if (!c.matrix.isIdentity) { val inv = Matrix(); c.matrix.invert(inv); inv.mapPoints(pt) }
                if (pt[0] < 0 || pt[1] < 0 || pt[0] > c.width || pt[1] > c.height) continue
                if (c is WidgetView || isInsideWidget(c)) {
                    if (c.canScrollVertically(1) || c.canScrollVertically(-1)) return true
                }
                if (scrollableUnder(c, pt[0], pt[1])) return true
            }
        }
        return false
    }

    private fun isInsideWidget(v: View): Boolean {
        var p = v.parent
        while (p != null) { if (p is WidgetView) return true; p = p.parent }
        return false
    }
}

/**
 * Arrastrar y soltar propio (no el DnD del sistema): permite la vista flotante con escala
 * y sombra, el cambio de escritorio por el borde, la creación de carpetas al soltar sobre
 * otro ícono y la animación de "aterrizaje" en la celda final.
 */
class DragController(private val l: HomeActivity) {
    var active = false; private set
    private var source: View? = null
    private var item: HomeItem? = null
    private var newApp: AppInfo? = null
    private var newShortcut: ShortcutInfo? = null
    private var fromFolder: HomeItem? = null
    private var spanW = 1; private var spanH = 1
    private var dragView: ImageView? = null
    private var offX = 0f; private var offY = 0f
    private var startX = 0f; private var startY = 0f
    private var lastX = 0f; private var lastY = 0f
    private var moved = false
    private var withPopup = false
    private var hoverView: View? = null
    private var hintLayout: CellLayout? = null
    private var dropTarget = DropBar.NONE
    private var edgeDir = 0
    private var addedPageDuringDrag = false
    private val slop by lazy { ViewConfiguration.get(l).scaledTouchSlop * 1.5f }
    private val edgeRunnable = Runnable { flipPage() }

    /** Inicia con una vista existente (ícono de inicio, dock, carpeta, widget) o de la pantalla de apps. */
    fun start(v: View, withPopup: Boolean, app: AppInfo? = null, shortcut: ShortcutInfo? = null) {
        if (active) return
        source = v
        this.withPopup = withPopup
        item = (v as? IconView)?.item ?: (v as? WidgetView)?.item
        newApp = if (item == null) app ?: (v as? IconView)?.app else null
        newShortcut = shortcut
        if (item == null && newApp == null && newShortcut == null) return
        fromFolder = item?.let { if (it.container >= 0) l.model.byId(it.container) else null }
        spanW = item?.w ?: 1; spanH = item?.h ?: 1
        active = true; moved = false; addedPageDuringDrag = false
        val (bmp, rect) = snapshot(v)
        val iv = ImageView(l).apply {
            setImageBitmap(bmp)
            elevation = dpf(10)
            pivotX = rect.width() / 2f; pivotY = rect.height() / 2f
        }
        l.root.addView(iv, FrameLayout.LayoutParams(rect.width().toInt(), rect.height().toInt()))
        iv.translationX = rect.left; iv.translationY = rect.top
        dragView = iv
        val loc = l.root.lastTouch
        startX = loc[0]; startY = loc[1]; lastX = startX; lastY = startY
        offX = startX - rect.left; offY = startY - rect.top
        iv.alpha = 0f // invisible hasta que el dedo se mueva (mientras, se ve el popup)
        l.root.requestDisallowInterceptTouchEvent(false)
    }

    private fun snapshot(v: View): Pair<Bitmap, RectF> {
        if (v is IconView) {
            val r = Rect(); v.iconBounds(r)
            val b = v.iconBitmap() ?: renderView(v)
            val rf = mapRectToRoot(v, RectF(r))
            if (v is FolderIconView) {
                val bmp = Bitmap.createBitmap(r.width().coerceAtLeast(1), r.height().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp); c.translate(-r.left.toFloat(), -r.top.toFloat()); v.draw(c)
                return bmp to rf
            }
            return b to rf
        }
        return renderView(v) to mapRectToRoot(v, RectF(0f, 0f, v.width.toFloat(), v.height.toFloat()))
    }

    private fun renderView(v: View): Bitmap {
        val b = Bitmap.createBitmap(v.width.coerceAtLeast(1), v.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        try { v.draw(Canvas(b)) } catch (e: Exception) { }
        return b
    }

    fun onTouch(ev: MotionEvent) {
        val x = ev.x; val y = ev.y
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                lastX = x; lastY = y
                if (!moved && hypot(x - startX, y - startY) > slop) beginVisibleDrag()
                if (moved) move(x, y)
            }
            MotionEvent.ACTION_UP -> if (moved) drop(x, y) else end(keepPopup = true)
            MotionEvent.ACTION_CANCEL -> cancel()
        }
    }

    private fun beginVisibleDrag() {
        moved = true
        if (withPopup) l.closePopup()
        l.resize?.commit()
        source?.visibility = View.INVISIBLE
        dragView?.apply { alpha = 1f; animate().scaleX(1.12f).scaleY(1.12f).setDuration(150).start() }
        if (l.drawer.isOpen || l.drawer.progress > 0f) l.drawer.close(true)
        l.dropBar.show(item != null, item?.isWidget != true, appForDrag())
        l.updateSystemBars()
    }

    private fun appForDrag(): AppInfo? = newApp ?: item?.let { if (it.isApp) l.app.apps.find(it.component, it.userSerial) else null }

    private fun move(x: Float, y: Float) {
        dragView?.translationX = x - offX
        dragView?.translationY = y - offY
        // 1) barra superior (Eliminar / Desinstalar / Info)
        dropTarget = l.dropBar.hover(x, y)
        if (dropTarget != DropBar.NONE) { clearHover(); return }
        // 2) carpeta abierta
        l.folder?.let { f ->
            if (f.contains(x, y)) { clearHover(); return }
            if (fromFolder != null) f.close(true)
        }
        // 3) dock
        if (hitView(l.dock, x, y) && spanW == 1 && spanH == 1) {
            stopEdge()
            val pt = mapPointFromRoot(l.dock, x, y)
            val cell = l.dock.cellAt(pt[0], pt[1])
            hover(l.dock, cell[0], 0, pt)
            return
        }
        // 4) escritorio: borde para cambiar de página
        val edge = l.dp(22)
        val dir = when { x < edge -> -1; x > l.root.width - edge -> 1; else -> 0 }
        if (dir != edgeDir) {
            stopEdge(); edgeDir = dir
            if (dir != 0) l.root.postDelayed(edgeRunnable, 550)
        }
        val cl = currentPage() ?: return
        val pt = mapPointFromRoot(cl, x - offX + (dragView?.width ?: 0) / 2f, y - offY + (dragView?.height ?: 0) / 2f)
        val cell = cl.cellAt(pt[0], pt[1], spanW, spanH)
        hover(cl, cell[0], cell[1], pt)
    }

    private fun currentPage(): CellLayout? = l.workspace.getChildAt(l.workspace.currentPage) as? CellLayout

    private fun hover(cl: CellLayout, cx: Int, cy: Int, pt: FloatArray) {
        if (hintLayout != null && hintLayout !== cl) hintLayout?.clearHint()
        hintLayout = cl
        // ¿Sobre el centro de otro ícono? => crear carpeta / agregar a carpeta
        val under = if (spanW == 1 && spanH == 1) cl.childAt(cx, cy) else null
        val canMerge = under != null && under !== source && (under is FolderIconView || (under is IconView && under.item?.isWidget != true)) &&
            item?.isFolder != true && item?.isWidget != true && nearCenter(cl, cx, cy, pt)
        if (canMerge) {
            setHover(under)
            cl.clearHint()
            return
        }
        clearHover()
        val ignore = item
        val cols = cl.cols; val rows = cl.rows
        val spot = if (cl === l.dock) dockSpot(cx) else
            l.model.findNearestVacant(cl.page, cx, cy, spanW, spanH, cols, rows, ignore)
        if (spot != null) cl.showHint(spot[0], spot[1], spanW, spanH, l.profile.iconPx) else cl.clearHint()
    }

    private fun nearCenter(cl: CellLayout, cx: Int, cy: Int, pt: FloatArray): Boolean {
        val r = Rect(); cl.cellRect(cx, cy, 1, 1, r)
        return abs(pt[0] - r.exactCenterX()) < r.width() * 0.32f && abs(pt[1] - r.exactCenterY()) < r.height() * 0.32f
    }

    private fun dockSpot(cx: Int): IntArray? {
        val used = l.model.inContainer(HomeItem.DOCK).filter { it !== item }.map { it.x }.toSet()
        if (cx !in used) return intArrayOf(cx, 0)
        return (0 until l.profile.dockCount).filter { it !in used }.minByOrNull { abs(it - cx) }?.let { intArrayOf(it, 0) }
    }

    private fun setHover(v: View) {
        if (hoverView === v) return
        clearHover()
        hoverView = v
        if (v is FolderIconView) v.hover = true
        else v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(160).start()
        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun clearHover() {
        val v = hoverView ?: return
        if (v is FolderIconView) v.hover = false else v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
        hoverView = null
    }

    private fun stopEdge() { l.root.removeCallbacks(edgeRunnable); edgeDir = 0 }

    private fun flipPage() {
        val ws = l.workspace
        val target = ws.currentPage + edgeDir
        if (target < 0) { edgeDir = 0; return }
        if (target >= ws.pageCount) {
            // Arrastrar más allá de la última página crea una nueva, como en One UI.
            if (l.model.onPage(ws.currentPage).none { it !== item } && ws.pageCount > 1) { edgeDir = 0; return }
            l.model.pageCount++
            addedPageDuringDrag = true
            val cl = CellLayout(l, l.profile.cols, l.profile.rows).apply {
                page = ws.pageCount; setPadding(l.profile.padH, l.profile.padV, l.profile.padH, l.profile.padV)
                editMode = l.editMode
            }
            ws.addView(cl)
            l.indicator.count = ws.pageCount
        }
        hintLayout?.clearHint()
        ws.snapToPage(target)
        val d = edgeDir; edgeDir = 0
        l.root.postDelayed({ if (active) { edgeDir = 0; move(lastX, lastY) } }, 450)
        if (d != 0) l.root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun drop(x: Float, y: Float) {
        stopEdge()
        val m = l.model
        val p = l.profile
        var target: RectF? = null
        var ok = true
        when {
            dropTarget == DropBar.REMOVE -> { item?.let { m.remove(it); m.cleanupFolders() }; target = null; ok = true; fadeOut() ; finish(true); return }
            dropTarget == DropBar.UNINSTALL -> { appForDrag()?.let { l.uninstall(it) }; ok = false }
            dropTarget == DropBar.INFO -> { appForDrag()?.let { l.appInfo(it) }; ok = false }
            l.folder?.contains(x, y) == true -> {
                val f = l.folder!!
                val it = materialize() ?: return cancel()
                it.container = f.folderItem.id
                it.rank = (m.folderChildren(f.folderItem.id).filter { c -> c !== it }.maxOfOrNull { c -> c.rank } ?: -1) + 1
                if (it !in m.items) m.items += it
            }
            hoverView != null -> {
                val hv = hoverView!!
                val under = (hv as? IconView)?.item
                val it = materialize()
                if (it == null || under == null) ok = false
                else if (under.isFolder) {
                    it.container = under.id
                    it.rank = (m.folderChildren(under.id).maxOfOrNull { c -> c.rank } ?: -1) + 1
                    if (it !in m.items) m.items += it
                } else {
                    val f = HomeItem(m.newId(), HomeItem.TYPE_FOLDER, under.container, under.page, under.x, under.y)
                    f.title = l.getString(R.string.folder_default)
                    f.color = l.settings.folderColorDefault
                    m.items += f
                    under.container = f.id; under.rank = 0
                    it.container = f.id; it.rank = 1
                    if (it !in m.items) m.items += it
                }
                target = mapRectToRoot(hv, RectF(0f, 0f, hv.width.toFloat(), hv.height.toFloat())).let { r ->
                    val s = p.iconPx * 0.4f; RectF(r.centerX() - s / 2, r.centerY() - s, r.centerX() + s / 2, r.centerY())
                }
            }
            hitView(l.dock, x, y) && spanW == 1 && spanH == 1 -> {
                val pt = mapPointFromRoot(l.dock, x, y)
                val spot = dockSpot(l.dock.cellAt(pt[0], pt[1])[0])
                val it = if (spot != null) materialize() else null
                if (spot == null || it == null) { ok = false; if (spot == null) toast(R.string.no_space) }
                else { it.container = HomeItem.DOCK; it.x = spot[0]; it.y = 0; it.page = 0; if (it !in m.items) m.items += it; target = cellTarget(l.dock, spot[0], 0) }
            }
            else -> {
                val cl = currentPage()
                if (cl == null) ok = false else {
                    val pt = mapPointFromRoot(cl, x - offX + (dragView?.width ?: 0) / 2f, y - offY + (dragView?.height ?: 0) / 2f)
                    val c = cl.cellAt(pt[0], pt[1], spanW, spanH)
                    val spot = m.findNearestVacant(cl.page, c[0], c[1], spanW, spanH, p.cols, p.rows, item)
                    val it = if (spot != null) materialize() else null
                    if (spot == null || it == null) { ok = false; if (spot == null) toast(R.string.no_space) }
                    else {
                        it.container = HomeItem.DESKTOP; it.page = cl.page; it.x = spot[0]; it.y = spot[1]
                        if (it !in m.items) m.items += it
                        target = cellTarget(cl, spot[0], spot[1])
                    }
                }
            }
        }
        if (ok) {
            m.cleanupFolders()
            if (addedPageDuringDrag) trimEmptyTrailingPages()
        }
        animateTo(target) { finish(ok) }
    }

    /** Convierte lo arrastrado en un HomeItem (nuevo si viene de la pantalla de apps o de un acceso directo). */
    private fun materialize(): HomeItem? {
        item?.let { return it }
        val m = l.model
        newShortcut?.let { s ->
            l.app.apps.pinShortcut(s)
            return HomeItem(m.newId(), HomeItem.TYPE_SHORTCUT, HomeItem.DESKTOP).apply {
                pkg = s.`package`; shortcutId = s.id; userSerial = l.app.apps.serialOf(s.userHandle)
                title = (s.shortLabel ?: s.longLabel ?: "").toString()
            }.also { item = it }
        }
        newApp?.let { return m.newAppItem(it).also { n -> item = n } }
        return null
    }

    private fun cellTarget(cl: CellLayout, x: Int, y: Int): RectF {
        val r = Rect(); cl.cellRect(x, y, spanW, spanH, r)
        val src = source
        val rf = if (src is IconView && spanW == 1) {
            val s = l.profile.iconPx.toFloat()
            val ic = Rect(); src.iconBounds(ic)
            val top = r.top + (ic.top.toFloat()).coerceAtMost(r.height() - s)
            RectF(r.exactCenterX() - s / 2, top, r.exactCenterX() + s / 2, top + s)
        } else RectF(r).apply { inset(l.dpf(6), l.dpf(6)) }
        return mapRectToRoot(cl, rf)
    }

    private fun animateTo(target: RectF?, end: () -> Unit) {
        val dv = dragView ?: return end()
        if (target == null || !l.settings.animations) { end(); return }
        val sx = target.width() / dv.width.coerceAtLeast(1); val sy = target.height() / dv.height.coerceAtLeast(1)
        dv.animate()
            .translationX(target.left + (target.width() - dv.width) / 2f)
            .translationY(target.top + (target.height() - dv.height) / 2f)
            .scaleX(sx).scaleY(sy)
            .setDuration(l.settings.animDuration(220)).setInterpolator(Anim.EASE_OUT)
            .withEndAction(end).start()
    }

    private fun fadeOut() {
        dragView?.let { v -> l.root.removeView(v) }
        dragView = null
    }

    private fun trimEmptyTrailingPages() {
        val m = l.model
        while (m.pageCount > 1 && m.onPage(m.pageCount - 1).isEmpty()) m.pageCount--
    }

    private fun finish(changed: Boolean) {
        if (changed) {
            active = false
            cleanup()
            l.model.notifyChanged()
            l.bindHome()
        } else end(false)
    }

    private fun end(keepPopup: Boolean) {
        val wasMoved = moved
        active = false
        if (addedPageDuringDrag) { trimEmptyTrailingPages(); l.bindHome() }
        cleanup()
        if (!keepPopup && wasMoved) l.bindHome()
    }

    fun cancel() {
        if (!active) return
        stopEdge()
        val keep = !moved
        end(keepPopup = keep)
        if (!keep) l.closePopup()
    }

    private fun cleanup() {
        stopEdge(); clearHover()
        hintLayout?.clearHint(); hintLayout = null
        dragView?.let { l.root.removeView(it) }; dragView = null
        source?.visibility = View.VISIBLE
        source = null; item = null; newApp = null; newShortcut = null; fromFolder = null
        dropTarget = DropBar.NONE
        l.dropBar.hide()
        l.updateSystemBars()
    }

    private fun toast(r: Int) = Toast.makeText(l, r, Toast.LENGTH_SHORT).show()

    // ---------------------------------------------------------------- mapeo de coordenadas

    private fun chain(v: View): List<View> {
        val list = ArrayList<View>()
        var c: View? = v
        while (c != null && c !== l.root) { list += c; c = c.parent as? View }
        return list.reversed()
    }

    fun mapPointFromRoot(v: View, x: Float, y: Float): FloatArray {
        val pt = floatArrayOf(x, y)
        var parent: View = l.root
        for (c in chain(v)) {
            pt[0] += parent.scrollX - c.left; pt[1] += parent.scrollY - c.top
            if (!c.matrix.isIdentity) { val inv = Matrix(); c.matrix.invert(inv); inv.mapPoints(pt) }
            parent = c
        }
        return pt
    }

    fun mapRectToRoot(v: View, r: RectF): RectF {
        val pts = floatArrayOf(r.left, r.top, r.right, r.bottom)
        var c: View? = v
        while (c != null && c !== l.root) {
            if (!c.matrix.isIdentity) c.matrix.mapPoints(pts)
            val parent = c.parent as? View ?: break
            for (i in pts.indices step 2) { pts[i] += c.left - parent.scrollX; pts[i + 1] += c.top - parent.scrollY }
            c = parent
        }
        return RectF(pts[0], pts[1], pts[2], pts[3])
    }

    fun hitView(v: View, x: Float, y: Float): Boolean {
        if (v.visibility != View.VISIBLE || v.alpha < 0.1f) return false
        val r = mapRectToRoot(v, RectF(0f, 0f, v.width.toFloat(), v.height.toFloat()))
        return r.contains(x, y)
    }
}
