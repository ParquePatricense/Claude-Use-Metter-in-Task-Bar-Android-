package com.s26home.launcher.data

import android.content.ComponentName
import android.content.Intent
import android.provider.MediaStore
import android.util.AtomicFile
import com.s26home.launcher.LauncherApp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class HomeItem(
    var id: Long,
    var type: Int,
    var container: Long,
    var page: Int = 0,
    var x: Int = 0,
    var y: Int = 0,
    var w: Int = 1,
    var h: Int = 1,
) {
    var component: ComponentName? = null
    var userSerial: Long = 0
    var title: String = ""
    var color: Int = 0
    var widgetId: Int = -1
    var shortcutId: String? = null
    var pkg: String? = null
    var rank: Int = 0

    val isApp get() = type == TYPE_APP
    val isFolder get() = type == TYPE_FOLDER
    val isWidget get() = type == TYPE_WIDGET
    val isShortcut get() = type == TYPE_SHORTCUT

    val iconKey: String
        get() = when (type) {
            TYPE_SHORTCUT -> "sc:$pkg/$shortcutId#$userSerial"
            else -> "app:" + (component?.let { AppInfo.keyOf(it, userSerial) } ?: "")
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("t", type); put("c", container); put("p", page)
        put("x", x); put("y", y); put("w", w); put("h", h)
        component?.let { put("cmp", it.flattenToString()) }
        put("u", userSerial); put("title", title); put("col", color); put("wid", widgetId)
        shortcutId?.let { put("sid", it) }; pkg?.let { put("pkg", it) }; put("r", rank)
    }

    companion object {
        const val TYPE_APP = 0
        const val TYPE_FOLDER = 1
        const val TYPE_WIDGET = 2
        const val TYPE_SHORTCUT = 3

        const val DESKTOP = -100L
        const val DOCK = -101L

        fun fromJson(o: JSONObject) = HomeItem(
            o.getLong("id"), o.getInt("t"), o.getLong("c"), o.optInt("p"),
            o.optInt("x"), o.optInt("y"), o.optInt("w", 1), o.optInt("h", 1)
        ).apply {
            component = o.optString("cmp").takeIf { it.isNotEmpty() }?.let { ComponentName.unflattenFromString(it) }
            userSerial = o.optLong("u"); title = o.optString("title"); color = o.optInt("col")
            widgetId = o.optInt("wid", -1); shortcutId = o.optString("sid").ifEmpty { null }
            pkg = o.optString("pkg").ifEmpty { null }; rank = o.optInt("r")
        }
    }
}

/**
 * Diseño de la pantalla de inicio (escritorios, dock y carpetas). Persistido como JSON
 * con AtomicFile: una escritura cortada nunca deja el archivo corrupto.
 */
class HomeModel(private val app: LauncherApp) {
    val items = ArrayList<HomeItem>()
    var pageCount = 1
    var loaded = false; private set
    private var nextId = 1L
    private val file = AtomicFile(File(app.filesDir, "home.json"))
    private val listeners = ArrayList<() -> Unit>()
    private val saveRunnable = Runnable { saveNow() }

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }
    fun notifyChanged() { listeners.toList().forEach { it() }; save() }

    fun newId() = nextId++

    /** Carga desde disco; si es la primera vez arma un diseño inicial con apps reales del equipo. */
    fun load(done: () -> Unit) {
        if (loaded) { done(); return }
        app.bg.execute {
            var json: JSONObject? = null
            try { if (file.baseFile.exists()) json = JSONObject(String(file.readFully())) } catch (e: Exception) { }
            app.main.post {
                if (json != null) {
                    pageCount = json.optInt("pages", 1).coerceAtLeast(1)
                    val arr = json.optJSONArray("items") ?: JSONArray()
                    for (i in 0 until arr.length()) items += HomeItem.fromJson(arr.getJSONObject(i))
                    nextId = (items.maxOfOrNull { it.id } ?: 0) + 1
                    loaded = true
                    done()
                } else {
                    val finish = { buildDefault(); loaded = true; save(); done() }
                    if (app.apps.loaded) finish() else app.apps.load(finish)
                }
            }
        }
    }

    fun save() {
        app.main.removeCallbacks(saveRunnable)
        app.main.postDelayed(saveRunnable, 400)
    }

    private fun saveNow() {
        val o = JSONObject().put("v", 1).put("pages", pageCount)
            .put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
        val bytes = o.toString().toByteArray()
        app.bg.execute {
            val out = try { file.startWrite() } catch (e: Exception) { null } ?: return@execute
            try { out.write(bytes); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out) }
        }
    }

    // ---------------------------------------------------------------- consultas

    fun inContainer(c: Long) = items.filter { it.container == c }
    fun onPage(p: Int) = items.filter { it.container == HomeItem.DESKTOP && it.page == p }
    fun folderChildren(id: Long) = items.filter { it.container == id }.sortedBy { it.rank }
    fun byId(id: Long) = items.firstOrNull { it.id == id }

    fun occupancy(page: Int, cols: Int, rows: Int, ignore: HomeItem? = null): Array<BooleanArray> {
        val g = Array(cols) { BooleanArray(rows) }
        for (it in onPage(page)) {
            if (it === ignore) continue
            for (x in it.x until it.x + it.w) for (y in it.y until it.y + it.h)
                if (x in 0 until cols && y in 0 until rows) g[x][y] = true
        }
        return g
    }

    fun isFree(g: Array<BooleanArray>, x: Int, y: Int, w: Int, h: Int): Boolean {
        val cols = g.size; val rows = if (cols > 0) g[0].size else 0
        if (x < 0 || y < 0 || x + w > cols || y + h > rows) return false
        for (i in x until x + w) for (j in y until y + h) if (g[i][j]) return false
        return true
    }

    /** Primer hueco libre en orden de lectura dentro de una página. */
    fun findVacant(page: Int, w: Int, h: Int, cols: Int, rows: Int): IntArray? {
        val g = occupancy(page, cols, rows)
        for (y in 0..rows - h) for (x in 0..cols - w) if (isFree(g, x, y, w, h)) return intArrayOf(x, y)
        return null
    }

    /** Hueco libre más cercano a (cx, cy), por distancia euclídea entre celdas. */
    fun findNearestVacant(page: Int, cx: Int, cy: Int, w: Int, h: Int, cols: Int, rows: Int, ignore: HomeItem?): IntArray? {
        val g = occupancy(page, cols, rows, ignore)
        var best: IntArray? = null; var bestD = Int.MAX_VALUE
        for (y in 0..rows - h) for (x in 0..cols - w) {
            if (!isFree(g, x, y, w, h)) continue
            val d = (x - cx) * (x - cx) + (y - cy) * (y - cy)
            if (d < bestD) { bestD = d; best = intArrayOf(x, y) }
        }
        return best
    }

    /** Coloca un ítem en el primer hueco desde [startPage]; agrega página si hace falta. */
    fun placeAnywhere(item: HomeItem, cols: Int, rows: Int, startPage: Int = 0) {
        var p = startPage.coerceIn(0, pageCount - 1)
        while (true) {
            val v = findVacant(p, item.w, item.h, cols, rows)
            if (v != null) {
                item.container = HomeItem.DESKTOP; item.page = p; item.x = v[0]; item.y = v[1]
                if (item !in items) items += item
                return
            }
            p++
            if (p >= pageCount) pageCount = p + 1
            if (p > 40) return
        }
    }

    fun remove(item: HomeItem) {
        items.remove(item)
        if (item.isFolder) items.removeAll { it.container == item.id }
        if (item.isWidget && item.widgetId >= 0) app.widgetHost.deleteId(item.widgetId)
        if (item.isShortcut) unpin(item)
    }

    private fun unpin(item: HomeItem) {
        val pkg = item.pkg ?: return; val sid = item.shortcutId ?: return
        if (items.any { it !== item && it.isShortcut && it.pkg == pkg && it.shortcutId == sid }) return
        val user = app.apps.userFor(item.userSerial) ?: return
        app.apps.unpinShortcut(pkg, sid, user)
    }

    fun removePackage(pkg: String, serial: Long) {
        val gone = items.filter {
            it.userSerial == serial && (it.component?.packageName == pkg || it.pkg == pkg) && !it.isWidget
        }
        if (gone.isEmpty()) return
        gone.forEach { items.remove(it) }
        cleanupFolders()
        notifyChanged()
    }

    /** Carpetas con 0 ó 1 elemento se disuelven, como en One UI. */
    fun cleanupFolders() {
        for (f in items.filter { it.isFolder }) {
            val kids = folderChildren(f.id)
            if (kids.isEmpty()) items.remove(f)
            else if (kids.size == 1) {
                val k = kids[0]
                k.container = f.container; k.page = f.page; k.x = f.x; k.y = f.y; k.rank = 0
                items.remove(f)
            }
        }
    }

    fun addPage() { pageCount++; notifyChanged() }

    fun removePage(p: Int) {
        if (pageCount <= 1) return
        items.filter { it.container == HomeItem.DESKTOP && it.page == p }.toList().forEach { remove(it) }
        items.filter { it.container == HomeItem.DESKTOP && it.page > p }.forEach { it.page-- }
        pageCount--
        val s = app.settings
        if (s.defaultPage >= pageCount) s.defaultPage = pageCount - 1
        notifyChanged()
    }

    /** Re-ubica lo que no entra cuando se achica la cuadrícula o el dock. */
    fun reflow(cols: Int, rows: Int, dockCount: Int) {
        val dock = inContainer(HomeItem.DOCK).sortedBy { it.x }
        dock.forEachIndexed { i, it -> it.x = i; it.y = 0 }
        dock.drop(dockCount).forEach { placeAnywhere(it, cols, rows) }
        val out = items.filter {
            it.container == HomeItem.DESKTOP && (it.x + it.w > cols || it.y + it.h > rows)
        }
        if (out.isEmpty()) return
        out.forEach {
            it.w = it.w.coerceAtMost(cols); it.h = it.h.coerceAtMost(rows)
            items.remove(it)
        }
        out.forEach { placeAnywhere(it, cols, rows, it.page) }
    }

    fun newAppItem(a: AppInfo) = HomeItem(newId(), HomeItem.TYPE_APP, HomeItem.DESKTOP).apply {
        component = a.component; userSerial = a.userSerial; title = a.label
    }

    // ---------------------------------------------------------------- diseño inicial

    private fun resolve(i: Intent): AppInfo? {
        val r = app.packageManager.resolveActivity(i, 0)?.activityInfo ?: return null
        return app.apps.findByPackage(r.packageName)
    }

    private fun buildDefault() {
        val s = app.settings
        val apps = app.apps
        val used = HashSet<String>()
        fun pick(vararg intents: Intent): AppInfo? {
            for (i in intents) resolve(i)?.let { if (used.add(it.key)) return it }
            return null
        }
        fun pkg(vararg names: String): AppInfo? {
            for (n in names) apps.findByPackage(n)?.let { if (used.add(it.key)) return it }
            return null
        }
        val dock = listOfNotNull(
            pick(Intent(Intent.ACTION_DIAL)),
            pick(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)),
            pick(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)),
            pick(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
            pkg("com.sec.android.gallery3d", "com.google.android.apps.photos"),
        ).take(s.dockCount)
        dock.forEachIndexed { i, a ->
            items += newAppItem(a).apply { container = HomeItem.DOCK; x = i }
        }

        val cols = s.homeCols; val rows = s.homeRows
        // Fila inferior de la página principal: apps Galaxy de uso diario.
        val row = listOfNotNull(
            pkg("com.android.vending"),
            pkg("com.sec.android.app.myfiles", "com.google.android.apps.nbu.files"),
            pkg("com.samsung.android.calendar", "com.google.android.calendar"),
            pkg("com.android.settings"),
        )
        row.take(cols).forEachIndexed { i, a ->
            items += newAppItem(a).apply { page = 0; x = i; y = rows - 1 }
        }
        // Carpetas Samsung y Google, como en un Galaxy recién configurado.
        fun folder(title: String, pkgs: List<String>, x: Int, y: Int) {
            val kids = pkgs.mapNotNull { pkg(it) }
            if (kids.size < 2) { kids.forEach { used.remove(it.key) }; return }
            val f = HomeItem(newId(), HomeItem.TYPE_FOLDER, HomeItem.DESKTOP, 0, x, y).apply { this.title = title }
            items += f
            kids.forEachIndexed { i, a -> items += newAppItem(a).apply { container = f.id; rank = i } }
        }
        folder("Samsung", listOf(
            "com.samsung.android.app.notes", "com.sec.android.app.voicenote", "com.samsung.android.oneconnect",
            "com.samsung.android.spay", "com.samsung.android.app.reminder", "com.samsung.android.voc",
            "com.samsung.android.app.tips", "com.sec.android.app.sbrowser"
        ), 0, rows - 2)
        folder("Google", listOf(
            "com.google.android.googlequicksearchbox", "com.google.android.gm", "com.google.android.apps.maps",
            "com.google.android.youtube", "com.google.android.apps.docs", "com.google.android.apps.youtube.music",
            "com.google.android.apps.photos", "com.google.android.apps.messaging"
        ), 1, rows - 2)
        pageCount = 1
    }
}
