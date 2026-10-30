package com.s26home.launcher.system

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.s26home.launcher.R

/** Acciones de sistema hechas solo con mecanismos que Android expone a apps comunes. */
object SystemActions {

    /**
     * Abre el panel de notificaciones. No hay API pública: se usa StatusBarManager por
     * reflexión con el permiso normal EXPAND_STATUS_BAR (el mismo camino que usan Nova,
     * Lawnchair y Niagara). Si el sistema lo bloquea, devuelve false y se usa la búsqueda.
     */
    fun expandNotifications(ctx: Context): Boolean = try {
        @Suppress("WrongConstant")
        val sb = ctx.getSystemService("statusbar")
        sb.javaClass.getMethod("expandNotificationsPanel").invoke(sb)
        true
    } catch (e: Throwable) { false }

    /**
     * Apagar pantalla. Android solo lo permite a apps comunes vía AccessibilityService
     * (GLOBAL_ACTION_LOCK_SCREEN, API 28). La alternativa, DevicePolicyManager.lockNow,
     * obliga a ingresar PIN en el siguiente desbloqueo y complica desinstalar la app.
     * El servicio de este launcher no lee contenido ni eventos: solo ejecuta esta acción.
     */
    fun lockScreen(a: Activity) {
        val svc = LockService.instance
        if (svc != null) { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN); return }
        AlertDialog.Builder(a)
            .setTitle(R.string.lock_setup_title)
            .setMessage(R.string.lock_setup_msg)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                try { a.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (e: Exception) { }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun isDefaultHome(ctx: Context): Boolean {
        val rm = ctx.getSystemService(RoleManager::class.java)
        return rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && rm.isRoleHeld(RoleManager.ROLE_HOME)
    }

    /** Diálogo oficial del sistema para elegir la app de inicio (RoleManager, API 29). */
    fun requestDefaultHome(a: Activity, req: Int) {
        val rm = a.getSystemService(RoleManager::class.java)
        try {
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                a.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), req); return
            }
        } catch (e: Exception) { }
        openHomeSettings(a)
    }

    /** Ajustes > Apps > Apps predeterminadas > App de inicio. Sirve para volver a One UI Home. */
    fun openHomeSettings(a: Activity) {
        try { a.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        catch (e: Exception) {
            try { a.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) } catch (e2: Exception) { }
        }
    }

    /** Abre One UI Home directamente (útil para comparar o volver sin cambiar el predeterminado). */
    fun openOneUiHome(a: Activity) {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .setComponent(ComponentName("com.sec.android.app.launcher", "com.sec.android.app.launcher.activities.LauncherActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { a.startActivity(i) } catch (e: Exception) {
            try { a.startActivity(a.packageManager.getLaunchIntentForPackage("com.sec.android.app.launcher") ?: return) }
            catch (e2: Exception) { Toast.makeText(a, R.string.oneui_not_found, Toast.LENGTH_SHORT).show() }
        }
    }
}

/** Servicio de accesibilidad mínimo: sin eventos, sin lectura de ventanas. Solo "apagar pantalla". */
class LockService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object { @Volatile var instance: LockService? = null }
}

/**
 * Fondos de pantalla generados en el equipo (sin imágenes con derechos de terceros):
 * degradados suaves con "orbes" de luz difusa, en la línea estética Galaxy S26.
 */
object Wallpapers {
    class Style(val name: Int, val base: IntArray, val orbs: IntArray)

    val styles = listOf(
        Style(R.string.wp_aurora, intArrayOf(0xFF0B1638.toInt(), 0xFF1E3A8A.toInt(), 0xFF5B4BC4.toInt()), intArrayOf(0xFF7DB4FF.toInt(), 0xFFB18CFF.toInt(), 0xFF4FD1E8.toInt())),
        Style(R.string.wp_dawn, intArrayOf(0xFFFFE3D3.toInt(), 0xFFF7B9C4.toInt(), 0xFFC9B6F2.toInt()), intArrayOf(0xFFFFC38A.toInt(), 0xFFFF9EB5.toInt(), 0xFFAFC8FF.toInt())),
        Style(R.string.wp_titanium, intArrayOf(0xFF1C1D21.toInt(), 0xFF2E3138.toInt(), 0xFF4A4E57.toInt()), intArrayOf(0xFF8C93A3.toInt(), 0xFF5E6573.toInt(), 0xFFB5BCCB.toInt())),
        Style(R.string.wp_mint, intArrayOf(0xFFE4F6EF.toInt(), 0xFFB9E6D6.toInt(), 0xFF9FD3E8.toInt()), intArrayOf(0xFF7FE0BF.toInt(), 0xFF8EC9FF.toInt(), 0xFFD7F5B0.toInt())),
    )

    fun render(s: Style, w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        p.shader = LinearGradient(0f, 0f, w * 0.3f, h.toFloat(), s.base, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        val spots = arrayOf(floatArrayOf(0.15f, 0.22f, 0.75f), floatArrayOf(0.9f, 0.55f, 0.7f), floatArrayOf(0.35f, 0.92f, 0.65f))
        for ((i, o) in spots.withIndex()) {
            val col = s.orbs[i]
            p.shader = RadialGradient(w * o[0], h * o[1], w * o[2], intArrayOf(col and 0x00FFFFFF or (0x99 shl 24), col and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        return b
    }

    fun apply(ctx: Context, s: Style, lock: Boolean, done: (Boolean) -> Unit) {
        val wm = WallpaperManager.getInstance(ctx)
        val dm = ctx.resources.displayMetrics
        val w = maxOf(dm.widthPixels, wm.desiredMinimumWidth.coerceAtMost(dm.widthPixels * 2))
        val h = dm.heightPixels.coerceAtLeast(wm.desiredMinimumHeight)
        Thread {
            val ok = try {
                val bmp = render(s, w, h)
                val which = WallpaperManager.FLAG_SYSTEM or (if (lock) WallpaperManager.FLAG_LOCK else 0)
                wm.setBitmap(bmp, null, true, which); bmp.recycle(); true
            } catch (e: Exception) { false }
            android.os.Handler(android.os.Looper.getMainLooper()).post { done(ok) }
        }.start()
    }
}
