package com.s26home.launcher

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.s26home.launcher.data.AppRepository
import com.s26home.launcher.data.HomeModel
import com.s26home.launcher.data.IconCache
import com.s26home.launcher.data.Settings
import com.s26home.launcher.data.WidgetHost
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Punto único de estado compartido. Todo lo que vive acá sobrevive a la recreación
 * de la Activity (cambio de tema, densidad, etc.), así la pantalla de inicio vuelve
 * sin recargar apps ni íconos.
 */
class LauncherApp : Application() {
    lateinit var settings: Settings
    lateinit var apps: AppRepository
    lateinit var icons: IconCache
    lateinit var model: HomeModel
    lateinit var widgetHost: WidgetHost

    /** Un solo hilo para disco y consultas al sistema: orden garantizado y cero contención. */
    val bg: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "s26-bg").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    val main = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        icons = IconCache(this)
        apps = AppRepository(this)
        model = HomeModel(this)
        widgetHost = WidgetHost(this)
        apps.load()
    }

    companion object {
        fun get(c: Context): LauncherApp = c.applicationContext as LauncherApp
    }
}
