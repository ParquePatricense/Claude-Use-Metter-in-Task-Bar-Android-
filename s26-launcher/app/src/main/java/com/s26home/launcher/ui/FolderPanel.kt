package com.s26home.launcher.ui

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.s26home.launcher.R
import com.s26home.launcher.data.HomeItem
import com.s26home.launcher.data.Settings

/**
 * Carpeta abierta One UI: tarjeta de vidrio centrada, título editable arriba, selector de
 * color, cuadrícula de 4 columnas paginada y botón "+" para sumar apps. Se expande desde
 * el ícono de la carpeta y el fondo se desenfoca.
 */
class FolderPanel(private val l: HomeActivity, val folderItem: HomeItem, private val icon: FolderIconView) : FrameLayout(l) {
    private val t = l.theme
    private val card = LinearLayout(l).apply { orientation = LinearLayout.VERTICAL }
    private val title = EditText(l)
    private val pager = PagedView(l)
    private val indicator = PageIndicator(l)
    private val cols = 4
    private val rows = 4

    init {
        setBackgroundColor(t.scrim)
        card.background = roundRect(t.folderPanel, t.radius)
        card.clipToOutline = true
        card.setPadding(l.dp(14), l.dp(18), l.dp(14), l.dp(10))

        val header = LinearLayout(l).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(View(l), LinearLayout.LayoutParams(l.dp(40), 1))
        title.apply {
            setText(folderItem.title)
            style(22f, t.text, Fonts.semibold)
            gravity = Gravity.CENTER
            background = null
            hint = l.getString(R.string.folder_name)
            setHintTextColor(t.textSecondary)
            setSingleLine(); imeOptions = EditorInfo.IME_ACTION_DONE
            isFocusableInTouchMode = true
            setOnEditorActionListener { _, _, _ -> commitTitle(); true }
            setOnFocusChangeListener { _, f -> if (!f) commitTitle() }
        }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(ImageView(l).apply {
            setImageResource(R.drawable.ic_palette)
            imageTintList = ColorStateList.valueOf(if (folderItem.color != 0) folderItem.color else t.textSecondary)
            setPadding(l.dp(8), l.dp(8), l.dp(8), l.dp(8))
            background = ripple(Color.TRANSPARENT, l.dpf(20), t.cardPressed)
            contentDescription = l.getString(R.string.folder_color)
            setOnClickListener { pickColor() }
        }, LinearLayout.LayoutParams(l.dp(40), l.dp(40)))
        card.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = l.dp(10) })

        pager.durationScale = l.settings.animSpeed / 100f
        pager.onScrollProgress = { indicator.progress = it }
        card.addView(pager, LinearLayout.LayoutParams(-1, 0, 1f))
        indicator.color = if (t.dark) Color.WHITE else 0xFF222222.toInt()
        card.addView(indicator, LinearLayout.LayoutParams(-1, l.dp(18)))

        addView(card)
        setOnClickListener { close(true) }
        card.setOnClickListener { title.clearFocus(); hideIme() }
        bindContent()
    }

    private fun cellH(): Int = l.profile.iconPx + l.dp(42)

    private fun layoutCard() {
        val w = l.root.width - l.dp(22) * 2
        val n = l.model.folderChildren(folderItem.id).size + 1
        val usedRows = ((n + cols - 1) / cols).coerceIn(1, rows)
        val h = l.dp(18 + 50 + 10 + 18 + 10) + usedRows * cellH()
        card.layoutParams = LayoutParams(w, h, Gravity.CENTER)
    }

    private fun bindContent() {
        val kids = l.model.folderChildren(folderItem.id)
        pager.removeAllViews()
        val per = cols * rows
        var page: GridPage? = null
        val iconPx = (l.profile.iconPx * 0.95f).toInt()
        val total = kids.size + 1
        val pageRows = ((total + cols - 1) / cols).coerceIn(1, rows)
        for (i in 0 until total) {
            if (i % per == 0) { page = GridPage(l, cols, pageRows); pager.addView(page) }
            if (i < kids.size) {
                val k = kids[i]
                val a = if (k.isApp) l.app.apps.find(k.component, k.userSerial) else null
                if (k.isApp && a == null) continue
                page!!.addView(IconView(l).apply {
                    item = k; app = a; label = a?.label ?: k.title; iconSize = iconPx
                    setLabelSize(l.profile.labelPx); panelText(t.text)
                    contentDescription = label
                    bindIcon(k.iconKey) { Loader.drawableFor(l, k) }
                    setOnClickListener { l.launch(this) }
                    setOnLongClickListener { l.onItemLongPress(this); true }
                })
            } else {
                page!!.addView(addButton(iconPx))
            }
        }
        indicator.count = pager.childCount
        layoutCard()
    }

    private fun addButton(iconPx: Int) = object : IconView(l) {
        val plus = context.getDrawable(R.drawable.ic_add)!!.mutate().apply { setTint(t.textSecondary) }
        val bg = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = t.field }
        override fun drawIcon(c: android.graphics.Canvas, r: RectF) {
            val s = r.width() * 0.62f
            c.drawCircle(r.centerX(), r.centerY(), s / 2, bg)
            val ps = (s * 0.5f).toInt()
            plus.setBounds((r.centerX() - ps / 2).toInt(), (r.centerY() - ps / 2).toInt(), (r.centerX() + ps / 2).toInt(), (r.centerY() + ps / 2).toInt())
            plus.draw(c)
        }
    }.apply {
        iconSize = iconPx; showLabel = false
        contentDescription = l.getString(R.string.add_apps)
        setOnClickListener { pickApps() }
    }

    fun rebind() {
        if (l.model.byId(folderItem.id) == null) { close(false); return }
        bindContent()
    }

    fun contains(x: Float, y: Float) = l.drag.hitView(card, x, y)

    fun editTitle() {
        post {
            title.requestFocus(); title.selectAll()
            l.getSystemService(InputMethodManager::class.java).showSoftInput(title, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun commitTitle() {
        val n = title.text.toString().trim()
        if (n != folderItem.title) { folderItem.title = n; l.model.save(); icon.label = n }
        hideIme()
    }

    private fun hideIme() = l.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(windowToken, 0)

    private fun pickColor() {
        val names = l.resources.getStringArray(R.array.folder_colors)
        AlertDialog.Builder(l)
            .setTitle(R.string.folder_color)
            .setSingleChoiceItems(names, Settings.FOLDER_COLORS.indexOf(folderItem.color).coerceAtLeast(0)) { d, which ->
                folderItem.color = Settings.FOLDER_COLORS[which]
                icon.folderColor = folderItem.color
                l.model.save(); d.dismiss()
            }.show()
    }

    /** Selector múltiple de apps (diálogo DeviceDefault: en Galaxy se ve como One UI). */
    private fun pickApps() {
        val apps = l.app.apps.visible()
        val inFolder = l.model.folderChildren(folderItem.id).mapNotNull { it.component?.let { c -> c to it.userSerial } }.toSet()
        val checked = BooleanArray(apps.size) { (apps[it].component to apps[it].userSerial) in inFolder }
        val initial = checked.copyOf()
        AlertDialog.Builder(l)
            .setTitle(R.string.add_apps)
            .setMultiChoiceItems(apps.map { it.label }.toTypedArray(), checked) { _, i, v -> checked[i] = v }
            .setPositiveButton(R.string.done) { _, _ ->
                val m = l.model
                var rank = (m.folderChildren(folderItem.id).maxOfOrNull { it.rank } ?: -1) + 1
                for (i in apps.indices) {
                    if (checked[i] && !initial[i]) m.items += m.newAppItem(apps[i]).apply { container = folderItem.id; this.rank = rank++ }
                    if (!checked[i] && initial[i]) m.folderChildren(folderItem.id)
                        .filter { it.component == apps[i].component && it.userSerial == apps[i].userSerial }
                        .forEach { m.items.remove(it) }
                }
                m.cleanupFolders()
                m.notifyChanged()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- animación

    fun open() {
        l.root.addView(this, LayoutParams(-1, -1))
        l.updateSystemBars()
        val d = l.settings.animDuration(340)
        post {
            val r = l.drag.mapRectToRoot(icon, RectF(0f, 0f, icon.width.toFloat(), icon.height.toFloat()))
            val cl = IntArray(2); card.getLocationInWindow(cl)
            card.pivotX = (r.centerX() - card.left).coerceIn(0f, card.width.toFloat())
            card.pivotY = (r.centerY() - card.top).coerceIn(0f, card.height.toFloat())
            card.scaleX = 0.25f; card.scaleY = 0.25f; card.alpha = 0f
            alpha = 0f
            icon.alpha = 0.4f
            card.animateTo(alpha = 1f, scale = 1f, dur = d)
            animateTo(alpha = 1f, dur = d)
            if (d > 0) valueAnim(0f, 1f, d) { l.setHomeBlur(it) }.start() else l.setHomeBlur(1f)
        }
    }

    fun close(animate: Boolean) {
        if (parent == null) return
        commitTitle()
        val d = if (animate) l.settings.animDuration(260) else 0L
        val done = {
            icon.alpha = 1f
            l.setHomeBlur(0f)
            (parent as? FrameLayout)?.removeView(this)
            l.onFolderClosed(this)
            l.updateSystemBars()
        }
        l.onFolderClosed(this)
        if (d == 0L) { done(); return }
        card.animateTo(alpha = 0f, scale = 0.3f, dur = d, interp = Anim.EASE_IN_OUT)
        valueAnim(1f, 0f, d) { l.setHomeBlur(it) }.start()
        animateTo(alpha = 0f, dur = d, interp = Anim.EASE_IN_OUT) { done() }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false
}
