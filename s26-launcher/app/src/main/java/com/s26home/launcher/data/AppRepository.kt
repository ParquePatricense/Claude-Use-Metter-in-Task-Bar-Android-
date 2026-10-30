package com.s26home.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.os.UserManager
import com.s26home.launcher.LauncherApp
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

class AppInfo(
    val info: LauncherActivityInfo,
    val user: UserHandle,
    val userSerial: Long,
    val label: String,
    val category: Int,
) {
    val component: ComponentName = info.componentName
    val packageName: String get() = component.packageName
    val key: String = keyOf(component, userSerial)
    val isWork: Boolean get() = userSerial != 0L
    val isSystem: Boolean = (info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    val search: String = normalize(label)
    val initials: String = label.split(' ', '-', '.').filter { it.isNotEmpty() }
        .joinToString("") { normalize(it.take(1)) }

    companion object {
        fun keyOf(c: ComponentName, serial: Long) = c.flattenToShortString() + "#" + serial
        fun normalize(s: String): String =
            Normalizer.normalize(s.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
    }
}

/**
 * Fuente de verdad de las apps instaladas. Usa exclusivamente LauncherApps (la API
 * oficial para launchers): respeta perfiles de trabajo y no necesita QUERY_ALL_PACKAGES.
 */
class AppRepository(private val app: LauncherApp) {
    private val la = app.getSystemService(LauncherApps::class.java)
    private val um = app.getSystemService(UserManager::class.java)

    @Volatile var all: List<AppInfo> = emptyList(); private set
    var loaded = false; private set
    private val listeners = ArrayList<() -> Unit>()
    private val packageListeners = ArrayList<(String, UserHandle, Boolean) -> Unit>()

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    /** (paquete, usuario, eliminado) — para limpiar la pantalla de inicio o agregar apps nuevas. */
    fun addPackageListener(l: (String, UserHandle, Boolean) -> Unit) { packageListeners += l }
    fun removePackageListener(l: (String, UserHandle, Boolean) -> Unit) { packageListeners -= l }

    init {
        la.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(pkg: String, user: UserHandle) = changed(pkg, user, true)
            override fun onPackageAdded(pkg: String, user: UserHandle) = changed(pkg, user, false)
            override fun onPackageChanged(pkg: String, user: UserHandle) = changed(pkg, user, false)
            override fun onPackagesAvailable(pkgs: Array<out String>, user: UserHandle, replacing: Boolean) =
                pkgs.forEach { changed(it, user, false) }
            override fun onPackagesUnavailable(pkgs: Array<out String>, user: UserHandle, replacing: Boolean) =
                if (!replacing) pkgs.forEach { changed(it, user, true) } else Unit
            override fun onShortcutsChanged(pkg: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) {
                app.icons.invalidatePackage(pkg)
                listeners.toList().forEach { it() }
            }
        }, app.main)
    }

    private fun changed(pkg: String, user: UserHandle, removed: Boolean) {
        app.icons.invalidatePackage(pkg)
        load {
            packageListeners.toList().forEach { it(pkg, user, removed) }
        }
    }

    fun load(done: (() -> Unit)? = null) {
        app.bg.execute {
            val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
            val list = ArrayList<AppInfo>()
            for (user in la.profiles) {
                val serial = um.getSerialNumberForUser(user)
                for (info in la.getActivityList(null, user)) {
                    if (info.componentName.packageName == app.packageName) continue
                    val label = info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName }
                    list += AppInfo(info, user, serial, label, Categories.of(info.applicationInfo))
                }
            }
            list.sortWith { a, b -> collator.compare(a.label, b.label) }
            app.main.post {
                all = list
                loaded = true
                listeners.toList().forEach { it() }
                done?.invoke()
            }
        }
    }

    fun find(component: ComponentName?, serial: Long): AppInfo? =
        component?.let { c -> all.firstOrNull { it.component == c && it.userSerial == serial } }

    fun findByPackage(pkg: String, serial: Long = 0): AppInfo? =
        all.firstOrNull { it.packageName == pkg && it.userSerial == serial }

    fun visible(): List<AppInfo> {
        val hidden = app.settings.hiddenApps
        return if (hidden.isEmpty()) all else all.filter { it.key !in hidden }
    }

    fun userFor(serial: Long): UserHandle? = um.getUserForSerialNumber(serial)

    fun serialOf(user: UserHandle): Long = um.getSerialNumberForUser(user)

    /** Búsqueda instantánea: prefijo > inicio de palabra > iniciales > contiene. */
    fun search(query: String): List<AppInfo> {
        val q = AppInfo.normalize(query.trim())
        if (q.isEmpty()) return visible()
        val scored = ArrayList<Pair<Int, AppInfo>>()
        for (a in visible()) {
            val s = when {
                a.search.startsWith(q) -> 0
                a.search.contains(" $q") -> 1
                a.initials.startsWith(q) -> 2
                a.search.contains(q) -> 3
                q.length >= 3 && a.packageName.contains(q) -> 4
                else -> -1
            }
            if (s >= 0) scored += s to a
        }
        return scored.sortedBy { it.first }.map { it.second }
    }

    // ---- Accesos directos (solo funcionan si somos el launcher predeterminado) ----

    fun hasShortcutAccess(): Boolean = try { la.hasShortcutHostPermission() } catch (e: Exception) { false }

    fun shortcutsFor(a: AppInfo): List<ShortcutInfo> {
        if (!hasShortcutAccess()) return emptyList()
        return try {
            val q = LauncherApps.ShortcutQuery()
                .setPackage(a.packageName)
                .setActivity(a.component)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                )
            (la.getShortcuts(q, a.user) ?: emptyList())
                .filter { it.isEnabled }
                .sortedWith(compareBy({ if (it.isDeclaredInManifest) 0 else 1 }, { it.rank }))
                .take(4)
        } catch (e: Exception) { emptyList() }
    }

    fun pinnedShortcut(pkg: String, id: String, user: UserHandle): ShortcutInfo? {
        if (!hasShortcutAccess()) return null
        return try {
            val q = LauncherApps.ShortcutQuery().setPackage(pkg).setShortcutIds(listOf(id))
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                )
            la.getShortcuts(q, user)?.firstOrNull()
        } catch (e: Exception) { null }
    }

    fun pinShortcut(s: ShortcutInfo) {
        try {
            val q = LauncherApps.ShortcutQuery().setPackage(s.`package`)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            val ids = (la.getShortcuts(q, s.userHandle) ?: emptyList()).map { it.id }.toMutableList()
            if (s.id !in ids) ids += s.id
            la.pinShortcuts(s.`package`, ids, s.userHandle)
        } catch (e: Exception) { }
    }

    fun unpinShortcut(pkg: String, id: String, user: UserHandle) {
        try {
            val q = LauncherApps.ShortcutQuery().setPackage(pkg)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            val ids = (la.getShortcuts(q, user) ?: emptyList()).map { it.id }.filter { it != id }
            la.pinShortcuts(pkg, ids, user)
        } catch (e: Exception) { }
    }
}

/** Agrupa apps usando ApplicationInfo.category (API 26), sin heurísticas inventadas. */
object Categories {
    const val ALL = -100
    const val OTHER = -1

    fun of(ai: ApplicationInfo): Int = when (ai.category) {
        ApplicationInfo.CATEGORY_GAME -> ApplicationInfo.CATEGORY_GAME
        ApplicationInfo.CATEGORY_SOCIAL -> ApplicationInfo.CATEGORY_SOCIAL
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> ApplicationInfo.CATEGORY_PRODUCTIVITY
        ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO,
        ApplicationInfo.CATEGORY_IMAGE -> ApplicationInfo.CATEGORY_VIDEO
        ApplicationInfo.CATEGORY_NEWS -> ApplicationInfo.CATEGORY_NEWS
        ApplicationInfo.CATEGORY_MAPS -> ApplicationInfo.CATEGORY_MAPS
        else -> OTHER
    }

    fun label(c: Int): String = when (c) {
        ALL -> "Todas"
        ApplicationInfo.CATEGORY_GAME -> "Juegos"
        ApplicationInfo.CATEGORY_SOCIAL -> "Social"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productividad"
        ApplicationInfo.CATEGORY_VIDEO -> "Multimedia"
        ApplicationInfo.CATEGORY_NEWS -> "Noticias"
        ApplicationInfo.CATEGORY_MAPS -> "Mapas"
        else -> "Otras"
    }
}
