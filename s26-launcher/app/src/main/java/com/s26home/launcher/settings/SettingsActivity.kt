package com.s26home.launcher.settings

import android.app.Activity
import android.app.AlertDialog
import android.app.UiModeManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.s26home.launcher.LauncherApp
import com.s26home.launcher.R
import com.s26home.launcher.data.Settings
import com.s26home.launcher.onboarding.OnboardingActivity
import com.s26home.launcher.system.LockService
import com.s26home.launcher.system.SystemActions
import com.s26home.launcher.system.Wallpapers
import com.s26home.launcher.ui.dp
import com.s26home.launcher.ui.dpf
import com.s26home.launcher.ui.roundRect
import com.s26home.launcher.ui.style

class SettingsActivity : Activity() {
    private lateinit var s: Settings
    private lateinit var ui: SettingsUi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        s = LauncherApp.get(this).settings
        ui = SettingsUi(this, s.accent)
        window.statusBarColor = ui.bg; window.navigationBarColor = ui.bg
        window.decorView.windowInsetsController?.let { c ->
            val f = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            c.setSystemBarsAppearance(if (ui.dark) 0 else f, f)
        }
        when (intent.getStringExtra("section")) {
            "home" -> home()
            "icons" -> icons()
            "drawer" -> drawer()
            "style" -> style()
            "gestures" -> gestures()
            "hidden" -> hidden()
            "about" -> about()
            else -> main()
        }
    }

    private fun open(section: String) = startActivity(Intent(this, SettingsActivity::class.java).putExtra("section", section))
    private fun str(id: Int) = getString(id)

    private fun main() {
        setContentView(ui.screen(str(R.string.settings_title), null))
        ui.action(str(R.string.sec_home), str(R.string.sec_home_sum), R.drawable.ic_home) { open("home") }
        ui.action(str(R.string.sec_icons), str(R.string.sec_icons_sum), R.drawable.ic_apps) { open("icons") }
        ui.action(str(R.string.sec_drawer), str(R.string.sec_drawer_sum), R.drawable.ic_search) { open("drawer") }
        ui.endGroup()
        ui.action(str(R.string.sec_style), str(R.string.sec_style_sum), R.drawable.ic_palette) { open("style") }
        ui.action(str(R.string.sec_gestures), str(R.string.sec_gestures_sum), R.drawable.ic_resize) { open("gestures") }
        ui.endGroup()
        ui.action(str(R.string.sec_default), if (SystemActions.isDefaultHome(this)) str(R.string.is_default) else str(R.string.not_default), R.drawable.ic_check) {
            SystemActions.requestDefaultHome(this, 1)
        }
        ui.action(str(R.string.back_to_oneui), str(R.string.back_to_oneui_sum), R.drawable.ic_back) { SystemActions.openHomeSettings(this) }
        ui.endGroup()
        ui.action(str(R.string.sec_about), str(R.string.sec_about_sum), R.drawable.ic_info) { open("about") }
    }

    private fun home() {
        setContentView(ui.screen(str(R.string.sec_home), ::finish))
        val grids = arrayOf("4 × 5", "4 × 6", "5 × 5", "5 × 6")
        val gv = arrayOf(4 to 5, 4 to 6, 5 to 5, 5 to 6)
        ui.choice(str(R.string.home_grid), grids, { gv.indexOf(s.homeCols to s.homeRows).coerceAtLeast(0) }) {
            s.homeCols = gv[it].first; s.homeRows = gv[it].second
        }
        ui.choice(str(R.string.dock_icons), arrayOf("3", "4", "5"), { (s.dockCount - 3).coerceIn(0, 2) }) { s.dockCount = it + 3 }
        ui.toggle(str(R.string.dock_bg), null, { s.dockBackground }) { s.dockBackground = it }
        ui.toggle(str(R.string.home_search), str(R.string.home_search_sum), { s.homeSearchBar }) { s.homeSearchBar = it }
        ui.endGroup()
        ui.slider(str(R.string.margin_h), 0, 36, 2, { s.marginH }, { s.marginH = it }) { "$it dp" }
        ui.slider(str(R.string.margin_v), 0, 32, 2, { s.marginV }, { s.marginV = it }) { "$it dp" }
        ui.endGroup()
        ui.toggle(str(R.string.add_new_apps), null, { s.addNewApps }) { s.addNewApps = it }
        ui.toggle(str(R.string.lock_layout), str(R.string.lock_layout_sum), { s.lockLayout }) { s.lockLayout = it }
    }

    private fun icons() {
        setContentView(ui.screen(str(R.string.sec_icons), ::finish))
        ui.slider(str(R.string.icon_size), 80, 125, 5, { s.iconScale }, { s.iconScale = it }) { "$it %" }
        ui.choice(str(R.string.icon_shape), arrayOf(str(R.string.shape_squircle), str(R.string.shape_circle), str(R.string.shape_rounded)), { s.iconShape }) { s.iconShape = it }
        ui.toggle(str(R.string.icon_frames), str(R.string.icon_frames_sum), { s.iconFrames }) { s.iconFrames = it }
        ui.toggle(str(R.string.themed_icons), str(R.string.themed_icons_sum), { s.themedIcons }) { s.themedIcons = it }
        ui.endGroup()
        ui.toggle(str(R.string.show_labels), null, { s.showLabels }) { s.showLabels = it }
        ui.slider(str(R.string.label_size), 10, 16, 1, { s.labelSize }, { s.labelSize = it }) { "$it sp" }
        ui.endGroup()
        ui.choice(str(R.string.folder_style), arrayOf(str(R.string.folder_2x2), str(R.string.folder_3x3)), { if (s.folderGrid == 3) 1 else 0 }) { s.folderGrid = if (it == 1) 3 else 2 }
        ui.colors(str(R.string.folder_color_default), Settings.FOLDER_COLORS, { s.folderColorDefault }) { s.folderColorDefault = it }
    }

    private fun drawer() {
        setContentView(ui.screen(str(R.string.sec_drawer), ::finish))
        val grids = arrayOf("4 × 5", "4 × 6", "5 × 5", "5 × 6")
        val gv = arrayOf(4 to 5, 4 to 6, 5 to 5, 5 to 6)
        ui.choice(str(R.string.drawer_grid), grids, { gv.indexOf(s.drawerCols to s.drawerRows).coerceAtLeast(3) }) {
            s.drawerCols = gv[it].first; s.drawerRows = gv[it].second
        }
        ui.toggle(str(R.string.categories), str(R.string.categories_sum), { s.drawerCategories }) { s.drawerCategories = it }
        ui.action(str(R.string.hidden_apps), str(R.string.hidden_apps_sum)) { open("hidden") }
        ui.endGroup()
        ui.note(str(R.string.drawer_note))
    }

    private fun style() {
        setContentView(ui.screen(str(R.string.sec_style), ::finish))
        ui.choice(str(R.string.dark_mode), arrayOf(str(R.string.theme_system), str(R.string.theme_light), str(R.string.theme_dark)), { s.themeMode }) {
            s.themeMode = it; applyNightMode(this, it)
        }
        ui.colors(str(R.string.accent), Settings.ACCENTS, { s.accent }) { s.accent = it; recreate() }
        ui.endGroup()
        ui.slider(str(R.string.transparency), 40, 100, 5, { s.panelOpacity }, { s.panelOpacity = it }) { "$it %" }
        ui.slider(str(R.string.corner_radius), 12, 36, 2, { s.cornerRadius }, { s.cornerRadius = it }) { "$it dp" }
        ui.toggle(str(R.string.blur), str(R.string.blur_sum), { s.blur }) { s.blur = it }
        ui.endGroup()
        ui.header(str(R.string.wallpaper))
        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(14), dp(16), dp(14), dp(16)) }
        for (w in Wallpapers.styles) {
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            val img = ImageView(this).apply {
                setImageBitmap(Wallpapers.render(w, dp(72), dp(150)))
                clipToOutline = true; background = roundRect(0, dpf(16)); outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            }
            col.addView(img, LinearLayout.LayoutParams(dp(72), dp(150)))
            col.addView(TextView(this).apply { setText(w.name); style(13f, ui.fg); setPadding(0, dp(6), 0, 0) })
            col.setOnClickListener { askWallpaper(w) }
            strip.addView(col, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(6), 0, dp(6), 0) })
        }
        ui.custom(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(strip) })
        ui.action(str(R.string.wallpaper_system), str(R.string.wallpaper_system_sum)) {
            try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), str(R.string.wallpaper))) } catch (e: Exception) { }
        }
        ui.endGroup()
        ui.toggle(str(R.string.animations), null, { s.animations }) { s.animations = it }
        ui.slider(str(R.string.anim_speed), 60, 150, 10, { s.animSpeed }, { s.animSpeed = it }) {
            when { it < 90 -> str(R.string.fast); it > 110 -> str(R.string.relaxed); else -> str(R.string.normal) }
        }
    }

    private fun askWallpaper(w: Wallpapers.Style) {
        AlertDialog.Builder(this).setTitle(R.string.apply_wallpaper)
            .setItems(arrayOf(str(R.string.wp_home), str(R.string.wp_home_lock))) { _, i ->
                Toast.makeText(this, R.string.applying, Toast.LENGTH_SHORT).show()
                Wallpapers.apply(this, w, i == 1) { ok -> Toast.makeText(this, if (ok) R.string.done else R.string.error, Toast.LENGTH_SHORT).show() }
            }.show()
    }

    private fun gestures() {
        setContentView(ui.screen(str(R.string.sec_gestures), ::finish))
        val actions = arrayOf(str(R.string.act_none), str(R.string.act_apps), str(R.string.act_notifications), str(R.string.act_search), str(R.string.act_lock), str(R.string.act_edit))
        ui.choice(str(R.string.swipe_up), actions, { s.gestureUp }) { s.gestureUp = it }
        ui.choice(str(R.string.swipe_down), actions, { s.gestureDown }) { s.gestureDown = it }
        ui.choice(str(R.string.double_tap), actions, { s.gestureDoubleTap }) {
            s.gestureDoubleTap = it
            if (it == Settings.ACTION_LOCK && LockService.instance == null) SystemActions.lockScreen(this)
        }
        ui.endGroup()
        ui.note(str(R.string.gestures_note))
        ui.action(str(R.string.lock_service), if (LockService.instance != null) str(R.string.enabled) else str(R.string.disabled)) {
            try { startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (e: Exception) { }
        }
    }

    private fun hidden() {
        setContentView(ui.screen(str(R.string.hidden_apps), ::finish))
        val app = LauncherApp.get(this)
        val hidden = s.hiddenApps.toMutableSet()
        for (a in app.apps.all) {
            val cb = CheckBox(this).apply { isChecked = a.key in hidden; isClickable = false; buttonTintList = android.content.res.ColorStateList.valueOf(s.accent) }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(10), dp(16), dp(10))
                addView(ImageView(context).apply { setImageDrawable(a.info.getIcon(resources.displayMetrics.densityDpi)) }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(16) })
                addView(TextView(context).apply { text = a.label; style(16f, ui.fg) }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(cb)
                setOnClickListener {
                    cb.isChecked = !cb.isChecked
                    if (cb.isChecked) hidden += a.key else hidden -= a.key
                    s.hiddenApps = hidden
                }
            }
            ui.custom(row)
        }
    }

    private fun about() {
        setContentView(ui.screen(str(R.string.sec_about), ::finish))
        val v = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "" }
        ui.action(str(R.string.app_name), "${str(R.string.version)} $v") { }
        ui.action(str(R.string.run_wizard), null) { s.onboardingDone = false; startActivity(Intent(this, OnboardingActivity::class.java)) }
        ui.action(str(R.string.open_oneui), str(R.string.open_oneui_sum)) { SystemActions.openOneUiHome(this) }
        ui.action(str(R.string.back_to_oneui), str(R.string.back_to_oneui_sum)) { SystemActions.openHomeSettings(this) }
        ui.endGroup()
        ui.header(str(R.string.limitations))
        ui.note(str(R.string.limitations_text))
    }

    override fun onResume() {
        super.onResume()
        if (intent.getStringExtra("section") == null && ::ui.isInitialized && lastDefault != SystemActions.isDefaultHome(this)) {
            lastDefault = SystemActions.isDefaultHome(this); if (lastDefaultInit) recreate()
        }
        lastDefaultInit = true
    }
    private var lastDefault = false
    private var lastDefaultInit = false

    companion object {
        /** Modo claro/oscuro por app (API 31): no toca el modo del sistema. */
        fun applyNightMode(a: Activity, mode: Int) {
            val um = a.getSystemService(UiModeManager::class.java)
            um.setApplicationNightMode(
                when (mode) {
                    Settings.THEME_LIGHT -> UiModeManager.MODE_NIGHT_NO
                    Settings.THEME_DARK -> UiModeManager.MODE_NIGHT_YES
                    else -> UiModeManager.MODE_NIGHT_AUTO
                }
            )
        }
    }
}
