package com.s26home.launcher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.drawable.Drawable
import com.s26home.launcher.LauncherApp
import com.s26home.launcher.ui.WidgetView

/** Envoltorio de AppWidgetHost: widgets reales de Android, incluidos los de Samsung y Google. */
class WidgetHost(private val app: LauncherApp) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(app)
    val host = object : AppWidgetHost(app, HOST_ID) {
        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
            WidgetView(context)
    }
    private var listening = false

    fun start() { if (!listening) try { host.startListening(); listening = true } catch (e: Exception) { } }
    fun stop() { if (listening) try { host.stopListening(); listening = false } catch (e: Exception) { } }

    fun allocate(): Int = host.allocateAppWidgetId()
    fun deleteId(id: Int) { try { host.deleteAppWidgetId(id) } catch (e: Exception) { } }
    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    fun createView(ctx: Context, id: Int, info: AppWidgetProviderInfo): AppWidgetHostView =
        host.createView(ctx, id, info)

    /** Proveedores de todos los perfiles, agrupados por paquete y ordenados por nombre de app. */
    fun providers(): List<Pair<String, List<AppWidgetProviderInfo>>> {
        val pm = app.packageManager
        val all = app.apps.let { repo ->
            app.getSystemService(android.os.UserManager::class.java).userProfiles
                .flatMap { manager.getInstalledProvidersForProfile(it) }
        }
        return all.groupBy { it.provider.packageName }
            .map { (pkg, list) ->
                val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
                label to list.sortedBy { it.loadLabel(pm) }
            }
            .sortedBy { it.first.lowercase() }
    }

    fun preview(info: AppWidgetProviderInfo, density: Int): Drawable? =
        try { info.loadPreviewImage(app, density) ?: info.loadIcon(app, density) } catch (e: Exception) { null }

    companion object { const val HOST_ID = 2026 }
}
