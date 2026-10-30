package com.s26home.launcher.ui

import android.app.Activity
import android.app.ActivityOptions
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as SysSettings
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.s26home.launcher.LauncherApp
import com.s26home.launcher.R
import com.s26home.launcher.data.AppInfo
import com.s26home.launcher.data.HomeItem
import com.s26home.launcher.data.Settings
import com.s26home.launcher.onboarding.OnboardingActivity
import com.s26home.launcher.settings.SettingsActivity
import com.s26home.launcher.system.SystemActions

/**
 * Pantalla de inicio (ACTION_MAIN + CATEGORY_HOME). Orquesta escritorios, dock, pantalla
 * de apps, carpetas, widgets y gestos. Todas las vistas se construyen por código para
 * evitar inflado XML y mantener el arranque en frío por debajo de lo que tarda el sistema
 * en mostrar el fondo de pantalla.
 */
class HomeActivity : Activity() {
    lateinit var app: LauncherApp
    val settings get() = app.settings
    val model get() = app.model

    lateinit var root: DragLayer
    lateinit var profile: DeviceProfile
    lateinit var theme: UiTheme
    lateinit var home: FrameLayout
    lateinit var workspace: PagedView
    lateinit var indicator: PageIndicator
    lateinit var dock: CellLayout
    var searchPill: View? = null
    lateinit var drawer: AppDrawer
    lateinit var dropBar: DropBar
    lateinit var editBar: LinearLayout
    lateinit var pageControls: LinearLayout
    val drag = DragController(this)
    var folder: FolderPanel? = null
    var popup: PopupCard? = null
    var picker: WidgetPicker? = null
    var resize: WidgetResizeFrame? = null
    val widgetViews = HashMap<Int, WidgetView>()

    private var insetTop = 0
    private var insetBottom = 0
    private var built = false
    private var bound = false
    var editMode = false; private set
    private var darkText = false
    private var resumed = false
    private var pendingWidget: Bundle? = null
    private var blurApplied = -1

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "onboarding_done" || key == "hidden_apps") { if (built) drawer.refresh(); return@OnSharedPreferenceChangeListener }
        if (key == "default_page") { indicator.homePage = settings.defaultPage; indicator.invalidate(); return@OnSharedPreferenceChangeListener }
        root.removeCallbacks(rebuildRunnable); root.postDelayed(rebuildRunnable, 60)
    }
    private val rebuildRunnable = Runnable { if (built) rebuildAll() }
    private val modelListener: () -> Unit = { if (bound && !drag.active) bindHome() }
    private val appsListener: () -> Unit = { if (built) { drawer.refresh(); if (bound) bindHome() } }
    private val packageListener: (String, android.os.UserHandle, Boolean) -> Unit = { pkg, user, removed ->
        val serial = app.apps.serialOf(user)
        if (removed) model.removePackage(pkg, serial)
        else if (settings.addNewApps && model.loaded) {
            app.apps.all.filter { it.packageName == pkg && it.userSerial == serial }.forEach { a ->
                if (model.items.none { it.component == a.component && it.userSerial == serial }) {
                    model.placeAnywhere(model.newAppItem(a), profile.cols, profile.rows, 0)
                    model.notifyChanged()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = LauncherApp.get(this)
        pendingWidget = savedInstanceState?.getBundle("pending_widget")
        window.setDecorFitsSystemWindows(false)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        root = DragLayer(this)
        setContentView(root)
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val changed = bars.top != insetTop || bars.bottom != insetBottom
            insetTop = bars.top; insetBottom = bars.bottom
            if (!built || changed) root.post { rebuildAll() }
            if (built) drawer.onImeInsets(insets.getInsets(WindowInsets.Type.ime()).bottom)
            insets
        }
        settings.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        model.addListener(modelListener)
        app.apps.addListener(appsListener)
        app.apps.addPackageListener(packageListener)

        if (!settings.onboardingDone) startActivity(Intent(this, OnboardingActivity::class.java))
    }

    override fun onDestroy() {
        settings.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        model.removeListener(modelListener)
        app.apps.removeListener(appsListener)
        app.apps.removePackageListener(packageListener)
        widgetViews.values.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        super.onDestroy()
    }

    override fun onStart() { super.onStart(); app.widgetHost.start() }
    override fun onStop() { super.onStop(); app.widgetHost.stop(); closePopup() }
    override fun onResume() { super.onResume(); resumed = true; updateSystemBars() }
    override fun onPause() { super.onPause(); resumed = false; drag.cancel() }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        pendingWidget?.let { out.putBundle("pending_widget", it) }
    }

    /** Botón Inicio: cierra lo abierto; si ya estábamos en inicio, vuelve a la página principal. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!built) return
        val alreadyHome = resumed && hasWindowFocus()
        val anim = alreadyHome && settings.animations
        val hadOverlay = folder != null || drawer.isOpen || popup != null || picker != null || editMode || resize != null
        closeAllOverlays(anim)
        if (alreadyHome && !hadOverlay && workspace.currentPage != settings.defaultPage) {
            workspace.snapToPage(settings.defaultPage)
        }
        if (!alreadyHome) workspace.setCurrentPageImmediate(settings.defaultPage)
    }

    fun closeAllOverlays(animate: Boolean) {
        drag.cancel()
        closePopup()
        resize?.commit()
        picker?.close(animate)
        folder?.close(animate)
        if (drawer.isOpen || drawer.progress > 0f) drawer.close(animate)
        if (editMode) exitEditMode()
    }

    // ------------------------------------------------------------------ construcción

    private fun rebuildAll() {
        if (root.width == 0) { root.post { rebuildAll() }; return }
        val keepPage = if (built) workspace.currentPage else settings.defaultPage
        val wasEdit = editMode
        drag.cancel()
        root.removeAllViews()
        folder = null; popup = null; picker = null; resize = null; editMode = false
        theme = UiTheme(this, settings)
        profile = DeviceProfile(this, settings, root.width, root.height, insetTop, insetBottom)
        app.icons.sizePx = profile.iconPx
        buildViews()
        built = true
        updateSystemBars()
        if (!model.loaded || !app.apps.loaded) {
            val go = { model.load { bound = true; model.reflow(profile.cols, profile.rows, profile.dockCount); bindHome(); workspace.setCurrentPageImmediate(keepPage); restorePendingWidget() } }
            if (app.apps.loaded) go() else app.apps.load(go)
        } else {
            bound = true
            model.reflow(profile.cols, profile.rows, profile.dockCount)
            bindHome()
            workspace.setCurrentPageImmediate(keepPage)
            if (wasEdit) enterEditMode()
        }
        drawer.refresh()
    }

    private fun buildViews() {
        val p = profile
        home = FrameLayout(this).apply { clipChildren = false; clipToPadding = false }
        root.addView(home, FrameLayout.LayoutParams(-1, -1))

        workspace = PagedView(this).apply {
            clipChildren = false
            durationScale = settings.animSpeed / 100f
            setPadding(0, 0, 0, 0)
        }
        home.addView(workspace, FrameLayout.LayoutParams(-1, p.workspaceBottom - p.workspaceTop).apply { topMargin = p.workspaceTop })
        workspace.pivotX = p.width / 2f
        workspace.pivotY = (p.workspaceBottom - p.workspaceTop) * 0.42f

        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!editMode) performAction(settings.gestureDoubleTap); return true
            }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (editMode) exitEditMode(); return true
            }
            override fun onLongPress(e: MotionEvent) {
                if (!editMode && !drag.active) {
                    root.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    enterEditMode()
                }
            }
        })
        workspace.setOnTouchListener { _, ev -> detector.onTouchEvent(ev); false }

        indicator = PageIndicator(this).apply { homePage = settings.defaultPage }
        val indTop = p.workspaceBottom + p.searchGap / 2
        home.addView(indicator, FrameLayout.LayoutParams(-1, p.indicatorH).apply { topMargin = indTop })
        workspace.onScrollProgress = { indicator.progress = it }

        if (settings.homeSearchBar) {
            val pill = buildSearchPill()
            home.addView(pill, FrameLayout.LayoutParams(-1, p.searchH).apply {
                topMargin = indTop + p.indicatorH
                leftMargin = p.padH + dp(10); rightMargin = p.padH + dp(10)
            })
            searchPill = pill
        } else searchPill = null

        dock = CellLayout(this, p.dockCount, 1).apply {
            setPadding(p.padH + dp(4), 0, p.padH + dp(4), 0)
            if (settings.dockBackground) {
                background = roundRect(if (theme.dark) 0x33FFFFFF else 0x40FFFFFF, dpf(30))
            }
        }
        home.addView(dock, FrameLayout.LayoutParams(-1, p.dockH, Gravity.BOTTOM).apply {
            bottomMargin = p.dockBottom
            if (settings.dockBackground) { leftMargin = dp(10); rightMargin = dp(10) }
        })

        dropBar = DropBar(this)
        root.addView(dropBar, FrameLayout.LayoutParams(-1, insetTop + dp(76)))
        dropBar.setPadding(dp(16), insetTop + dp(10), dp(16), dp(10))
        dropBar.visibility = View.GONE

        pageControls = buildPageControls()
        root.addView(pageControls, FrameLayout.LayoutParams(-2, dp(48), Gravity.CENTER_HORIZONTAL).apply { topMargin = insetTop + dp(6) })
        pageControls.visibility = View.GONE

        editBar = buildEditBar()
        root.addView(editBar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = insetBottom + dp(18) })
        editBar.visibility = View.GONE

        drawer = AppDrawer(this)
        root.addView(drawer, FrameLayout.LayoutParams(-1, -1))
        drawer.setProgress(0f)
    }

    private fun buildSearchPill(): View {
        val light = !darkTextOnWallpaper()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(18), 0)
            background = roundRect(if (light) 0x33FFFFFF else 0x26000000, dpf(26))
            elevation = 0f
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_search)
                imageTintList = android.content.res.ColorStateList.valueOf(if (light) Color.WHITE else 0xFF222222.toInt())
            }, LinearLayout.LayoutParams(dp(22), dp(22)))
            addView(TextView(context).apply {
                text = getString(R.string.search_hint)
                style(16f, if (light) 0xE6FFFFFF.toInt() else 0xCC111111.toInt())
                setPadding(dp(12), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            setOnClickListener { openDrawer(search = true) }
        }
    }

    private fun buildPageControls(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        background = roundRect(0x40000000, dpf(24))
        setPadding(dp(6), 0, dp(6), 0)
        fun btn(icon: Int, desc: Int, click: () -> Unit) = ImageView(context).apply {
            setImageResource(icon); contentDescription = getString(desc)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = ripple(Color.TRANSPARENT, dpf(24), 0x33FFFFFF)
            setOnClickListener { click() }
        }
        addView(btn(R.drawable.ic_home, R.string.set_home_page) {
            settings.defaultPage = workspace.currentPage
            Toast.makeText(context, R.string.home_page_set, Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(btn(R.drawable.ic_add, R.string.add_page) {
            model.addPage(); bindHome(); workspace.snapToPage(model.pageCount - 1)
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(btn(R.drawable.ic_delete, R.string.delete_page) { deleteCurrentPage() }, LinearLayout.LayoutParams(dp(48), dp(48)))
    }

    private fun deleteCurrentPage() {
        if (model.pageCount <= 1) { Toast.makeText(this, R.string.cant_delete_last_page, Toast.LENGTH_SHORT).show(); return }
        val p = workspace.currentPage
        val doIt = { model.removePage(p); bindHome(); workspace.setCurrentPageImmediate((p - 1).coerceAtLeast(0)); if (editMode) applyEditToPages() }
        if (model.onPage(p).isEmpty()) doIt()
        else android.app.AlertDialog.Builder(this)
            .setTitle(R.string.delete_page_q)
            .setMessage(R.string.delete_page_msg)
            .setPositiveButton(R.string.delete) { _, _ -> doIt() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun buildEditBar(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        fun item(icon: Int, label: Int, click: () -> Unit) = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(6), dp(8), dp(6), dp(8))
            background = ripple(Color.TRANSPARENT, dpf(20), 0x33FFFFFF)
            addView(ImageView(context).apply {
                setImageResource(icon)
                imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            }, LinearLayout.LayoutParams(dp(26), dp(26)))
            addView(TextView(context).apply {
                text = getString(label); style(12.5f, Color.WHITE); gravity = Gravity.CENTER
                setShadowLayer(dpf(2), 0f, dpf(1), 0x55000000); setPadding(0, dp(6), 0, 0)
            })
            setOnClickListener { click() }
        }
        val lp = { LinearLayout.LayoutParams(0, -2, 1f) }
        addView(item(R.drawable.ic_wallpaper, R.string.wallpaper) { pickWallpaper() }, lp())
        addView(item(R.drawable.ic_palette, R.string.themes) { startActivity(Intent(context, SettingsActivity::class.java).putExtra("section", "style")) }, lp())
        addView(item(R.drawable.ic_widgets, R.string.widgets) { showWidgetPicker() }, lp())
        addView(item(R.drawable.ic_settings, R.string.settings) { startActivity(Intent(context, SettingsActivity::class.java)) }, lp())
    }

    // ------------------------------------------------------------------ enlace modelo → vistas

    fun bindHome() {
        if (!built) return
        val p = profile
        val cur = workspace.currentPage
        val wasEdit = editMode
        // Los widgets se reutilizan entre re-enlaces: recrearlos sería caro y parpadearía.
        widgetViews.values.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        workspace.removeAllViews()
        val pages = model.pageCount.coerceAtLeast(1)
        for (i in 0 until pages) {
            val cl = CellLayout(this, p.cols, p.rows).apply {
                page = i
                setPadding(p.padH, p.padV, p.padH, p.padV)
                editBgColor = if (darkText) 0x1A000000 else 0x26FFFFFF
            }
            workspace.addView(cl)
        }
        dock.removeAllViews()
        val alive = HashSet<Int>()
        for (item in model.items) {
            when (item.container) {
                HomeItem.DESKTOP -> {
                    val cl = workspace.getChildAt(item.page) as? CellLayout ?: continue
                    val v = createItemView(item, false) ?: continue
                    if (item.isWidget) alive += item.widgetId
                    cl.add(v, CellLayout.LP(item.x, item.y, item.w, item.h, item.isWidget))
                }
                HomeItem.DOCK -> {
                    if (item.x >= p.dockCount || item.isWidget) continue
                    val v = createItemView(item, true) ?: continue
                    dock.add(v, CellLayout.LP(item.x, 0))
                }
            }
        }
        widgetViews.keys.filter { it !in alive }.forEach { widgetViews.remove(it) }
        indicator.count = pages
        indicator.homePage = settings.defaultPage.coerceAtMost(pages - 1)
        workspace.setCurrentPageImmediate(cur.coerceIn(0, pages - 1))
        if (wasEdit) applyEditToPages()
        folder?.rebind()
    }

    fun createItemView(item: HomeItem, inDock: Boolean): View? {
        val p = profile
        return when (item.type) {
            HomeItem.TYPE_WIDGET -> widgetView(item)
            HomeItem.TYPE_FOLDER -> FolderIconView(this).apply {
                this.item = item
                grid = settings.folderGrid
                folderColor = item.color
                label = item.title
                iconSize = p.iconPx
                showLabel = p.showLabels && !inDock
                setLabelSize(p.labelPx)
                lightText = !darkText
                children = model.folderChildren(item.id)
                contentDescription = item.title
                setOnClickListener { openFolder(this) }
                setOnLongClickListener { onItemLongPress(this); true }
            }
            else -> {
                val a = if (item.isApp) app.apps.find(item.component, item.userSerial) else null
                if (item.isApp && a == null) return null // app desinstalada o perfil apagado
                IconView(this).apply {
                    this.item = item; this.app = a
                    label = a?.label ?: item.title
                    iconSize = p.iconPx
                    showLabel = p.showLabels && !inDock
                    setLabelSize(p.labelPx)
                    lightText = !darkText
                    contentDescription = label
                    bindIcon(item.iconKey) { Loader.drawableFor(this@HomeActivity, item) }
                    setOnClickListener { launch(this) }
                    setOnLongClickListener { onItemLongPress(this); true }
                }
            }
        }
    }

    private fun widgetView(item: HomeItem): View? {
        val info = app.widgetHost.info(item.widgetId) ?: return errorWidget(item)
        val v = widgetViews.getOrPut(item.widgetId) {
            (app.widgetHost.createView(this, item.widgetId, info) as WidgetView)
        }
        v.item = item
        v.onLongPress = { onWidgetLongPress(it) }
        updateWidgetSize(v, item)
        return v
    }

    private fun errorWidget(item: HomeItem): View = TextView(this).apply {
        text = getString(R.string.widget_error); gravity = Gravity.CENTER
        style(13f, Color.WHITE); background = roundRect(0x66000000, dpf(22))
        setOnLongClickListener {
            model.remove(item); model.notifyChanged(); true
        }
    }

    fun updateWidgetSize(v: WidgetView, item: HomeItem) {
        val d = resources.displayMetrics.density
        val w = (item.w * profile.cellW - dp(12)) / d
        val h = (item.h * profile.cellH - dp(12)) / d
        try { v.updateAppWidgetSize(Bundle(), listOf(android.util.SizeF(w, h))) } catch (e: Exception) { }
    }

    // ------------------------------------------------------------------ acciones

    fun launch(v: IconView) {
        val item = v.item
        val a = v.app ?: item?.let { app.apps.find(it.component, it.userSerial) }
        val la = getSystemService(LauncherApps::class.java)
        val r = Rect(); v.iconBounds(r)
        val loc = IntArray(2); v.getLocationOnScreen(loc)
        r.offset(loc[0], loc[1])
        val opts = if (settings.animations)
            ActivityOptions.makeScaleUpAnimation(v, r.left - loc[0], r.top - loc[1], r.width(), r.height()).toBundle() else null
        try {
            if (item != null && item.isShortcut) {
                val user = app.apps.userFor(item.userSerial) ?: return
                la.startShortcut(item.pkg!!, item.shortcutId!!, r, opts, user)
            } else if (a != null) {
                la.startMainActivity(a.component, a.user, r, opts)
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    fun launchShortcut(s: ShortcutInfo, from: View) {
        try {
            getSystemService(LauncherApps::class.java).startShortcut(s, null, null)
        } catch (e: Exception) { Toast.makeText(this, R.string.app_unavailable, Toast.LENGTH_SHORT).show() }
    }

    fun performAction(action: Int) {
        when (action) {
            Settings.ACTION_APPS -> openDrawer()
            Settings.ACTION_SEARCH -> openDrawer(search = true)
            Settings.ACTION_NOTIFICATIONS -> if (!SystemActions.expandNotifications(this)) openDrawer(search = true)
            Settings.ACTION_LOCK -> SystemActions.lockScreen(this)
            Settings.ACTION_EDIT -> enterEditMode()
        }
    }

    fun appInfo(a: AppInfo) {
        try { getSystemService(LauncherApps::class.java).startAppDetailsActivity(a.component, a.user, null, null) }
        catch (e: Exception) { }
    }

    fun uninstall(a: AppInfo) {
        if (a.isSystem) { appInfo(a); return }
        try {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", a.packageName, null))
                .putExtra(Intent.EXTRA_USER, a.user))
        } catch (e: ActivityNotFoundException) { appInfo(a) }
    }

    fun pickWallpaper() {
        try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.wallpaper))) }
        catch (e: Exception) { startActivity(Intent(this, SettingsActivity::class.java).putExtra("section", "style")) }
    }

    // ------------------------------------------------------------------ pulsación larga / popups

    fun onItemLongPress(v: View) {
        if (editMode && v !is WidgetView) { /* en modo edición se arrastra directo */ }
        closePopup()
        if (settings.lockLayout && !drawer.isOpen && folder == null) {
            popup = PopupCard.forItem(this, v).also { it.show(v) }
            return
        }
        popup = PopupCard.forItem(this, v).also { it.show(v) }
        drag.start(v, withPopup = true)
    }

    fun onWidgetLongPress(v: WidgetView) {
        closePopup()
        popup = PopupCard.forWidget(this, v).also { it.show(v) }
        if (!settings.lockLayout) drag.start(v, withPopup = true)
    }

    fun closePopup() { popup?.dismiss(); popup = null }

    // ------------------------------------------------------------------ carpetas

    fun openFolder(icon: FolderIconView) {
        val item = icon.item ?: return
        closePopup()
        folder?.close(false)
        folder = FolderPanel(this, item, icon).also { it.open() }
        updateBack()
    }

    fun onFolderClosed(p: FolderPanel) { if (folder === p) folder = null; updateBack() }

    // ------------------------------------------------------------------ pantalla de apps

    fun openDrawer(search: Boolean = false) {
        if (!built || editMode) return
        closePopup(); folder?.close(false)
        drawer.open(true, search)
    }

    fun onDrawerProgress(p: Float) {
        home.alpha = 1f - p
        val s = 1f - 0.06f * p
        home.scaleX = s; home.scaleY = s
        home.visibility = if (p >= 1f) View.INVISIBLE else View.VISIBLE
        setWindowBlur(p)
        updateSystemBars()
        updateBack()
    }

    /** Desenfoque del fondo de pantalla (blur entre ventanas, API 31). Se aplica en pasos para no saturar al compositor. */
    fun setWindowBlur(f: Float) {
        if (!settings.blur) return
        val wm = getSystemService(WindowManager::class.java)
        if (!wm.isCrossWindowBlurEnabled) return
        val r = ((f * dp(40)) / dp(4)).toInt() * dp(4)
        if (r == blurApplied) return
        blurApplied = r
        try { window.setBackgroundBlurRadius(r) } catch (e: Exception) { }
    }

    /** Desenfoque del contenido de inicio detrás de una carpeta abierta (RenderEffect, API 31). */
    fun setHomeBlur(f: Float) {
        if (!settings.blur) return
        val r = dpf(18) * f
        home.setRenderEffect(if (r < 0.5f) null else RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP))
        setWindowBlur(f)
    }

    // ------------------------------------------------------------------ modo edición

    fun enterEditMode() {
        if (editMode || !built) return
        closePopup()
        editMode = true
        applyEditToPages()
        val d = settings.animDuration(320)
        workspace.animateTo(scale = 0.78f, dur = d)
        workspace.animate().translationY(dp(24).toFloat())
        dock.animateTo(alpha = 0f, dur = d)
        searchPill?.animateTo(alpha = 0f, dur = d)
        indicator.animate().translationY(-dp(40).toFloat()).setDuration(d).start()
        editBar.visibility = View.VISIBLE; editBar.alpha = 0f; editBar.translationY = dp(30).toFloat()
        editBar.animateTo(alpha = 1f, ty = 0f, dur = d)
        pageControls.visibility = View.VISIBLE; pageControls.alpha = 0f
        pageControls.animateTo(alpha = 1f, dur = d)
        updateBack()
    }

    private fun applyEditToPages() {
        for (i in 0 until workspace.childCount) (workspace.getChildAt(i) as? CellLayout)?.editMode = editMode
    }

    fun exitEditMode() {
        if (!editMode) return
        editMode = false
        applyEditToPages()
        val d = settings.animDuration(280)
        workspace.animateTo(scale = 1f, dur = d)
        workspace.animate().translationY(0f)
        dock.animateTo(alpha = 1f, dur = d)
        searchPill?.animateTo(alpha = 1f, dur = d)
        indicator.animate().translationY(0f).setDuration(d).start()
        editBar.animateTo(alpha = 0f, ty = dp(30).toFloat(), dur = d) { if (!editMode) editBar.visibility = View.GONE }
        pageControls.animateTo(alpha = 0f, dur = d) { if (!editMode) pageControls.visibility = View.GONE }
        updateBack()
    }

    // ------------------------------------------------------------------ widgets

    fun showWidgetPicker() {
        if (picker != null) return
        picker = WidgetPicker(this).also {
            root.addView(it, FrameLayout.LayoutParams(-1, -1))
            it.open()
        }
        updateBack()
    }

    fun onPickerClosed() { picker = null; updateBack() }

    /** Paso 1: reservar id y pedir permiso de vinculación (diálogo del sistema si hace falta). */
    fun addWidget(info: AppWidgetProviderInfo, page: Int = workspace.currentPage) {
        val p = profile
        val (sw, sh) = WidgetSizing.span(this, info, p)
        var pg = page
        var spot = model.findVacant(pg, sw, sh, p.cols, p.rows)
        if (spot == null) {
            pg = (0 until model.pageCount).firstOrNull { model.findVacant(it, sw, sh, p.cols, p.rows) != null } ?: model.pageCount
            if (pg == model.pageCount) model.pageCount++
            spot = model.findVacant(pg, sw, sh, p.cols, p.rows) ?: intArrayOf(0, 0)
        }
        val id = app.widgetHost.allocate()
        val b = Bundle().apply {
            putInt("id", id); putInt("page", pg); putInt("x", spot[0]); putInt("y", spot[1])
            putInt("w", sw); putInt("h", sh)
        }
        pendingWidget = b
        val ok = try { app.widgetHost.manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) } catch (e: Exception) { false }
        if (ok) configureWidget(id) else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            try { startActivityForResult(i, REQ_BIND) } catch (e: Exception) { cancelPendingWidget() }
        }
    }

    /** Paso 2: si el widget pide configuración obligatoria, abrir su pantalla. */
    private fun configureWidget(id: Int) {
        val info = app.widgetHost.info(id) ?: return cancelPendingWidget()
        val optional = (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0
        if (info.configure != null && !optional) {
            try { app.widgetHost.host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null); return }
            catch (e: Exception) { }
        }
        finishWidget(id)
    }

    /** Paso 3: agregar al modelo. */
    private fun finishWidget(id: Int) {
        val b = pendingWidget ?: return
        pendingWidget = null
        val item = HomeItem(model.newId(), HomeItem.TYPE_WIDGET, HomeItem.DESKTOP, b.getInt("page"), b.getInt("x"), b.getInt("y"), b.getInt("w"), b.getInt("h"))
        item.widgetId = id
        val p = profile
        if (!model.isFree(model.occupancy(item.page, p.cols, p.rows), item.x, item.y, item.w, item.h)) {
            model.placeAnywhere(item, p.cols, p.rows, item.page)
        } else model.items += item
        model.notifyChanged()
        picker?.close(true)
        if (editMode) exitEditMode()
        workspace.snapToPage(item.page)
    }

    private fun cancelPendingWidget() {
        pendingWidget?.getInt("id")?.let { app.widgetHost.deleteId(it) }
        pendingWidget = null
    }

    private fun restorePendingWidget() { /* el resultado llega por onActivityResult */ }

    @Deprecated("API de framework; sin AndroidX no hay Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val id = pendingWidget?.getInt("id") ?: return
        when (requestCode) {
            REQ_BIND -> if (resultCode == RESULT_OK) configureWidget(id) else cancelPendingWidget()
            REQ_CONFIG -> if (resultCode == RESULT_OK) finishWidget(id) else cancelPendingWidget()
        }
    }

    fun reconfigureWidget(item: HomeItem) {
        try { app.widgetHost.host.startAppWidgetConfigureActivityForResult(this, item.widgetId, 0, REQ_RECONFIG, null) }
        catch (e: Exception) { Toast.makeText(this, R.string.app_unavailable, Toast.LENGTH_SHORT).show() }
    }

    fun startResize(v: WidgetView) {
        val item = v.item ?: return
        closePopup()
        resize?.commit()
        resize = WidgetResizeFrame(this, v, item).also { it.show() }
        updateBack()
    }

    fun onResizeDone() { resize = null; updateBack() }

    // ------------------------------------------------------------------ sistema

    fun darkTextOnWallpaper(): Boolean = try {
        val c = WallpaperManager.getInstance(this).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        c != null && (c.colorHints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
    } catch (e: Exception) { false }

    fun updateSystemBars() {
        if (!::theme.isInitialized) return
        darkText = darkTextOnWallpaper()
        val drawerOpen = built && drawer.progress > 0.5f
        val lightBars = if (drawerOpen || folder != null) !theme.dark else darkText
        val flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(if (lightBars) flags else 0, flags)
    }

    override fun onEnterAnimationComplete() { super.onEnterAnimationComplete(); updateSystemBars() }

    private val backCallback = OnBackInvokedCallback {
        when {
            drag.active -> drag.cancel()
            popup != null -> closePopup()
            resize != null -> resize?.commit()
            picker != null -> picker?.close(true)
            folder != null -> folder?.close(true)
            drawer.isOpen -> drawer.close(true)
            editMode -> exitEditMode()
        }
        updateBack()
    }
    private var backRegistered = false

    /** Back solo se intercepta si hay algo que cerrar: en reposo, inicio no consume Back. */
    fun updateBack() {
        if (!built) return
        val need = popup != null || resize != null || picker != null || folder != null || drawer.isOpen || editMode
        if (need && !backRegistered) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback)
            backRegistered = true
        } else if (!need && backRegistered) {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCallback)
            backRegistered = false
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) drag.cancel()
    }

    companion object {
        const val REQ_BIND = 11
        const val REQ_CONFIG = 12
        const val REQ_RECONFIG = 13
    }
}
