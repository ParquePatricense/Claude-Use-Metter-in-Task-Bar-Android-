package com.s26home.launcher.data

import android.content.Context
import android.content.SharedPreferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Preferencias del launcher. Cada propiedad lee/escribe directo en SharedPreferences. */
class Settings(ctx: Context) {
    val prefs: SharedPreferences = ctx.getSharedPreferences("s26_settings", Context.MODE_PRIVATE)

    // Pantalla de inicio
    var homeCols by IntPref("home_cols", 4)
    var homeRows by IntPref("home_rows", 5)
    var dockCount by IntPref("dock_count", 4)
    var dockBackground by BoolPref("dock_bg", false)
    var homeSearchBar by BoolPref("home_search", true)
    var showLabels by BoolPref("show_labels", true)
    var marginH by IntPref("margin_h", 14)          // dp
    var marginV by IntPref("margin_v", 8)           // dp
    var addNewApps by BoolPref("add_new_apps", false)
    var lockLayout by BoolPref("lock_layout", false)
    var defaultPage by IntPref("default_page", 0)

    // Íconos
    var iconScale by IntPref("icon_scale", 100)      // % (80..125)
    var labelSize by IntPref("label_size", 12)       // sp
    var iconShape by IntPref("icon_shape", SHAPE_SQUIRCLE)
    var iconFrames by BoolPref("icon_frames", true)
    var themedIcons by BoolPref("themed_icons", false)

    // Pantalla de apps
    var drawerCols by IntPref("drawer_cols", 5)
    var drawerRows by IntPref("drawer_rows", 6)
    var drawerCategories by BoolPref("drawer_categories", true)
    var hiddenApps: Set<String>
        get() = prefs.getStringSet("hidden_apps", emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet("hidden_apps", HashSet(v)).apply()

    // Estilo
    var themeMode by IntPref("theme_mode", THEME_SYSTEM)
    var accent by IntPref("accent", ACCENTS[0])
    var panelOpacity by IntPref("panel_opacity", 78) // % (40..100)
    var cornerRadius by IntPref("corner_radius", 28) // dp
    var blur by BoolPref("blur", true)
    var folderGrid by IntPref("folder_grid", 2)      // 2 => 2x2, 3 => 3x3
    var folderColorDefault by IntPref("folder_color", 0)

    // Animaciones y gestos
    var animations by BoolPref("animations", true)
    var animSpeed by IntPref("anim_speed", 100)      // % duración (60..150)
    var gestureUp by IntPref("gesture_up", ACTION_APPS)
    var gestureDown by IntPref("gesture_down", ACTION_NOTIFICATIONS)
    var gestureDoubleTap by IntPref("gesture_double_tap", ACTION_NONE)

    var onboardingDone by BoolPref("onboarding_done", false)

    fun animDuration(base: Long): Long =
        if (!animations) 0L else base * animSpeed / 100

    companion object {
        const val SHAPE_SQUIRCLE = 0
        const val SHAPE_CIRCLE = 1
        const val SHAPE_ROUNDED = 2

        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        const val ACTION_NONE = 0
        const val ACTION_APPS = 1
        const val ACTION_NOTIFICATIONS = 2
        const val ACTION_SEARCH = 3
        const val ACTION_LOCK = 4
        const val ACTION_EDIT = 5

        /** Acentos inspirados en la paleta Galaxy: azul One UI primero. */
        val ACCENTS = intArrayOf(
            0xFF3E91FF.toInt(), 0xFF5B6BF5.toInt(), 0xFF8E6CEF.toInt(), 0xFFE5689A.toInt(),
            0xFFF0773B.toInt(), 0xFF2BB673.toInt(), 0xFF22A6B3.toInt(), 0xFF7A8594.toInt()
        )

        /** Colores de carpeta (0 = blanco translúcido estándar). */
        val FOLDER_COLORS = intArrayOf(
            0, 0xFF6C9BFF.toInt(), 0xFF7ED9A4.toInt(), 0xFFFFD36B.toInt(),
            0xFFFF8A7A.toInt(), 0xFFC49BFF.toInt(), 0xFF70D6E8.toInt(), 0xFF3A3A3C.toInt()
        )
    }
}

private class IntPref(val key: String, val def: Int) : ReadWriteProperty<Settings, Int> {
    override fun getValue(thisRef: Settings, property: KProperty<*>) = thisRef.prefs.getInt(key, def)
    override fun setValue(thisRef: Settings, property: KProperty<*>, value: Int) {
        thisRef.prefs.edit().putInt(key, value).apply()
    }
}

private class BoolPref(val key: String, val def: Boolean) : ReadWriteProperty<Settings, Boolean> {
    override fun getValue(thisRef: Settings, property: KProperty<*>) = thisRef.prefs.getBoolean(key, def)
    override fun setValue(thisRef: Settings, property: KProperty<*>, value: Boolean) {
        thisRef.prefs.edit().putBoolean(key, value).apply()
    }
}
