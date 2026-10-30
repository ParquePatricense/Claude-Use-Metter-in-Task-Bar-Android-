package com.s26home.launcher.ui

import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import com.s26home.launcher.R
import com.s26home.launcher.data.AppInfo
import com.s26home.launcher.data.Categories
import com.s26home.launcher.settings.SettingsActivity
import kotlin.math.abs

/**
 * Pantalla de apps estilo One UI: páginas horizontales en orden alfabético, buscador
 * abajo al alcance del pulgar (como One UI 8/9), filtros por categoría y menú ⋮.
 */
class AppDrawer(private val l: HomeActivity) : FrameLayout(l) {
    private val t = l.theme
    private val s = l.settings
    var progress = 0f; private set
    val isOpen get() = progress >= 1f && visibility == VISIBLE
    private var anim: ValueAnimator? = null

    private val content = LinearLayout(l).apply { orientation = LinearLayout.VERTICAL }
    private val chipsScroll = HorizontalScrollView(l).apply { isHorizontalScrollBarEnabled = false; overScrollMode = OVER_SCROLL_NEVER }
    private val chips = LinearLayout(l).apply { orientation = LinearLayout.HORIZONTAL; setPadding(l.dp(16), 0, l.dp(16), 0) }
    private val pager = PagedView(l)
    private val indicator = PageIndicator(l)
    private val bottom = LinearLayout(l).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val search = EditText(l)
    private val empty = LinearLayout(l).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; visibility = GONE }
    private var category = Categories.ALL
    private var query = ""
    private var imeBottom = 0

    init {
        setBackgroundColor(Color.TRANSPARENT)
        visibility = GONE
        val top = l.profile.workspaceTop
        content.setPadding(0, top + l.dp(14), 0, 0)
        addView(content, LayoutParams(-1, -1))

        chipsScroll.addView(chips)
        if (s.drawerCategories) content.addView(chipsScroll, LinearLayout.LayoutParams(-1, l.dp(40)).apply { bottomMargin = l.dp(6) })

        pager.durationScale = s.animSpeed / 100f
        pager.onScrollProgress = { indicator.progress = it }
        content.addView(pager, LinearLayout.LayoutParams(-1, 0, 1f))
        indicator.color = if (t.dark) Color.WHITE else 0xFF222222.toInt()
        content.addView(indicator, LinearLayout.LayoutParams(-1, l.dp(22)))

        empty.addView(TextView(l).apply { text = l.getString(R.string.no_results); style(16f, t.textSecondary); gravity = Gravity.CENTER })
        empty.addView(TextView(l).apply {
            text = l.getString(R.string.search_play); style(15f, t.accent, Fonts.medium); gravity = Gravity.CENTER
            setPadding(l.dp(20), l.dp(14), l.dp(20), l.dp(14))
            setOnClickListener { openStoreSearch() }
        })
        addView(empty, LayoutParams(-1, -1).apply { topMargin = top + l.dp(60); bottomMargin = l.dp(200) })

        // Barra inferior: buscador + menú.
        search.apply {
            hint = l.getString(R.string.search_hint)
            style(16f, t.text)
            setHintTextColor(t.textSecondary)
            background = null
            setSingleLine(); imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setPadding(l.dp(10), 0, l.dp(8), 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(e: Editable?) { query = e?.toString().orEmpty(); chipsScroll.visibility = if (query.isBlank()) VISIBLE else INVISIBLE; rebuildPages() }
            })
            setOnEditorActionListener { _, action, ev ->
                if (action == EditorInfo.IME_ACTION_GO || ev?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    (pager.getChildAt(0) as? GridPage)?.getChildAt(0)?.performClick(); true
                } else false
            }
        }
        val pill = LinearLayout(l).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(l.dp(16), 0, l.dp(6), 0)
            background = roundRect(if (t.dark) 0x29FFFFFF else 0xB3FFFFFF.toInt(), l.dpf(26))
            addView(ImageView(l).apply { setImageResource(R.drawable.ic_search); imageTintList = ColorStateList.valueOf(t.textSecondary) }, LinearLayout.LayoutParams(l.dp(22), l.dp(22)))
            addView(search, LinearLayout.LayoutParams(0, -1, 1f))
            addView(ImageView(l).apply {
                setImageResource(R.drawable.ic_close); imageTintList = ColorStateList.valueOf(t.textSecondary)
                setPadding(l.dp(10), l.dp(10), l.dp(10), l.dp(10)); visibility = GONE
                search.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(e: Editable?) { visibility = if (e.isNullOrEmpty()) GONE else VISIBLE }
                })
                setOnClickListener { search.setText("") }
            }, LinearLayout.LayoutParams(l.dp(42), l.dp(42)))
        }
        bottom.addView(pill, LinearLayout.LayoutParams(0, l.dp(52), 1f))
        bottom.addView(ImageView(l).apply {
            setImageResource(R.drawable.ic_more); imageTintList = ColorStateList.valueOf(t.text)
            contentDescription = l.getString(R.string.more_options)
            setPadding(l.dp(13), l.dp(13), l.dp(13), l.dp(13))
            background = ripple(Color.TRANSPARENT, l.dpf(26), t.cardPressed)
            setOnClickListener { showMenu(this) }
        }, LinearLayout.LayoutParams(l.dp(50), l.dp(50)).apply { leftMargin = l.dp(8) })
        bottom.setPadding(l.dp(18), l.dp(8), l.dp(14), l.dp(12) + l.profile.insetBottom)
        content.addView(bottom, LinearLayout.LayoutParams(-1, -2))
    }

    // ---------------------------------------------------------------- contenido

    fun refresh() {
        buildChips()
        rebuildPages()
    }

    private fun buildChips() {
        if (!s.drawerCategories) return
        chips.removeAllViews()
        val present = l.app.apps.visible().map { it.category }.toSet()
        val cats = listOf(Categories.ALL) + listOf(
            android.content.pm.ApplicationInfo.CATEGORY_SOCIAL, android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY,
            android.content.pm.ApplicationInfo.CATEGORY_VIDEO, android.content.pm.ApplicationInfo.CATEGORY_GAME,
            android.content.pm.ApplicationInfo.CATEGORY_NEWS, android.content.pm.ApplicationInfo.CATEGORY_MAPS, Categories.OTHER
        ).filter { it in present }
        if (category != Categories.ALL && category !in cats) category = Categories.ALL
        for (c in cats) {
            val on = c == category
            chips.addView(TextView(l).apply {
                text = Categories.label(c)
                style(14f, if (on) Color.WHITE else t.text, Fonts.medium)
                gravity = Gravity.CENTER
                setPadding(l.dp(16), 0, l.dp(16), 0)
                background = roundRect(if (on) t.accent else t.field, l.dpf(18))
                setOnClickListener { category = c; buildChips(); rebuildPages() }
            }, LinearLayout.LayoutParams(-2, l.dp(36)).apply { rightMargin = l.dp(8); gravity = Gravity.CENTER_VERTICAL })
        }
    }

    private fun currentList(): List<AppInfo> {
        val base = if (query.isNotBlank()) l.app.apps.search(query) else l.app.apps.visible()
        return if (category == Categories.ALL || query.isNotBlank()) base else base.filter { it.category == category }
    }

    private fun rebuildPages() {
        val list = currentList()
        val cols = s.drawerCols; val rows = s.drawerRows
        val per = cols * rows
        pager.removeAllViews()
        val iconPx = (l.profile.iconPx * (if (cols >= 5) 0.86f else 0.95f)).toInt()
        var page: GridPage? = null
        for ((i, a) in list.withIndex()) {
            if (i % per == 0) {
                page = GridPage(l, cols, rows).apply { setPadding(l.dp(10), 0, l.dp(10), 0) }
                pager.addView(page)
            }
            page!!.addView(appIcon(a, iconPx))
        }
        if (pager.childCount == 0) pager.addView(GridPage(l, cols, rows))
        indicator.count = pager.childCount
        pager.setCurrentPageImmediate(0)
        empty.visibility = if (list.isEmpty() && query.isNotBlank()) VISIBLE else GONE
    }

    private fun appIcon(a: AppInfo, iconPx: Int) = IconView(l).apply {
        app = a; label = a.label; iconSize = iconPx; labelLines = 1
        setLabelSize(l.profile.labelPx)
        panelText(t.text)
        contentDescription = a.label
        bindIcon("app:" + a.key) { Loader.appIcon(l, a) }
        setOnClickListener { l.launch(this); if (query.isNotEmpty()) l.root.postDelayed({ close(false) }, 400) }
        setOnLongClickListener {
            hideIme()
            l.closePopup()
            l.popup = PopupCard.forItem(l, this).also { it.show(this) }
            if (!s.lockLayout) l.drag.start(this, withPopup = true, app = a)
            true
        }
    }

    private fun showMenu(anchor: View) {
        PopupMenu(l, anchor).apply {
            menu.add(0, 1, 0, R.string.hidden_apps)
            menu.add(0, 2, 0, if (s.drawerCategories) R.string.hide_categories else R.string.show_categories)
            menu.add(0, 3, 0, R.string.settings)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> l.startActivity(Intent(l, SettingsActivity::class.java).putExtra("section", "hidden"))
                    2 -> s.drawerCategories = !s.drawerCategories
                    3 -> l.startActivity(Intent(l, SettingsActivity::class.java))
                }
                true
            }
            show()
        }
    }

    private fun openStoreSearch() {
        val q = Uri.encode(query)
        try { l.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$q")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { l.startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", query)) }
    }

    fun onImeInsets(b: Int) {
        imeBottom = b
        bottom.translationY = -(b - l.profile.insetBottom).coerceAtLeast(0).toFloat()
    }

    // ---------------------------------------------------------------- apertura / cierre

    fun setProgress(p: Float) {
        progress = p.coerceIn(0f, 1f)
        visibility = if (progress > 0f) VISIBLE else GONE
        background = null
        setBackgroundColor(withAlpha(t.panel, (t.panel ushr 24) / 255f * progress))
        content.alpha = ((progress - 0.15f) / 0.85f).coerceIn(0f, 1f)
        content.translationY = (1f - progress) * l.dp(90)
        l.onDrawerProgress(progress)
    }

    fun open(animate: Boolean, focusSearch: Boolean) {
        anim?.cancel()
        if (!animate || !s.animations) { setProgress(1f); afterOpen(focusSearch); return }
        anim = valueAnim(progress, 1f, s.animDuration((320 * (1 - progress) + 80).toLong()), Anim.EASE_OUT) { setProgress(it) }.apply {
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { if (progress >= 1f) afterOpen(focusSearch) }
            })
            start()
        }
    }

    private fun afterOpen(focusSearch: Boolean) {
        l.updateBack()
        if (focusSearch) {
            search.requestFocus()
            l.getSystemService(InputMethodManager::class.java).showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun close(animate: Boolean) {
        anim?.cancel()
        hideIme()
        val reset = {
            if (search.text.isNotEmpty()) search.setText("")
            if (category != Categories.ALL) { category = Categories.ALL; buildChips(); rebuildPages() }
            pager.setCurrentPageImmediate(0)
            l.updateBack()
        }
        if (!animate || !s.animations) { setProgress(0f); reset(); return }
        anim = valueAnim(progress, 0f, s.animDuration((260 * progress + 60).toLong()), Anim.EASE_IN_OUT) { setProgress(it) }.apply {
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { if (progress <= 0f) reset() }
            })
            start()
        }
    }

    private fun hideIme() {
        search.clearFocus()
        l.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(windowToken, 0)
    }

    // ---------------------------------------------------------------- gesto para cerrar (deslizar hacia abajo)

    private val slop = ViewConfiguration.get(l).scaledTouchSlop
    private var downX = 0f; private var downY = 0f
    private var closing = false
    private var vt: VelocityTracker? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isOpen && progress < 1f) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y; closing = false; vt?.recycle(); vt = VelocityTracker.obtain(); vt?.addMovement(ev) }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(ev)
                val dy = ev.y - downY; val dx = ev.x - downX
                if (dy > slop * 1.5f && dy > abs(dx) * 1.4f && imeBottom == 0) { closing = true; downY = ev.y; return true }
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!closing) return true // el panel consume toques para que no pasen a inicio
        vt?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> setProgress(1f - ((ev.y - downY) / (height * 0.55f)).coerceIn(0f, 1f))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                vt?.computeCurrentVelocity(1000)
                val v = vt?.yVelocity ?: 0f
                if (v > l.dp(500) || progress < 0.7f) close(true) else open(true, false)
                closing = false; vt?.recycle(); vt = null
            }
        }
        return true
    }
}
