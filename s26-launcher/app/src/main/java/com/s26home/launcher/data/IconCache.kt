package com.s26home.launcher.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import com.s26home.launcher.LauncherApp
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Genera y cachea íconos ya recortados con la forma Galaxy (squircle). Los bitmaps se crean
 * bajo demanda en un hilo propio y viven en un LRU acotado: solo ocupan RAM los visibles
 * y sus vecinos, no los ~200 instalados.
 */
class IconCache(private val app: LauncherApp) {
    private val exec = Executors.newSingleThreadExecutor { r ->
        Thread(r, "s26-icons").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 10).toInt().coerceIn(8 * 1024, 32 * 1024)
    private val cache = object : LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount / 1024
    }
    private val pending = HashMap<String, MutableList<(Bitmap) -> Unit>>()
    private var generation = 0

    /** Tamaño en px del ícono que se genera (lo fija el DeviceProfile). */
    var sizePx = 160
        set(v) { if (v != field) { field = v; clear() } }

    fun clear() {
        generation++
        cache.evictAll()
        pending.clear()
    }

    fun invalidatePackage(pkg: String) {
        cache.snapshot().keys.filter { it.contains(pkg) }.forEach { cache.remove(it) }
    }

    fun peek(key: String): Bitmap? = cache.get(key)

    /** Pide el ícono; si está en caché responde en el acto, si no lo genera en segundo plano. */
    fun load(key: String, source: () -> Drawable?, cb: (Bitmap) -> Unit) {
        cache.get(key)?.let { cb(it); return }
        pending[key]?.let { it += cb; return }
        pending[key] = mutableListOf(cb)
        val gen = generation
        val size = sizePx
        val s = app.settings
        val shape = s.iconShape; val frames = s.iconFrames; val themed = s.themedIcons; val accent = s.accent
        val dark = isNight()
        exec.execute {
            val bmp = try {
                render(source(), size, shape, frames, themed, accent, dark)
            } catch (e: Throwable) { null } ?: placeholder(size, shape)
            app.main.post {
                if (gen != generation) return@post
                cache.put(key, bmp)
                pending.remove(key)?.forEach { it(bmp) }
            }
        }
    }

    private fun isNight(): Boolean =
        (app.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun placeholder(size: Int, shape: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF }
        Canvas(b).drawPath(shapePath(size.toFloat(), shape), p)
        return b
    }

    companion object {
        private val pathCache = HashMap<Long, Path>()

        /** Superelipse n=4.2: la curvatura continua característica de los íconos Galaxy. */
        @Synchronized
        fun shapePath(size: Float, shape: Int): Path {
            val k = (size.toBits().toLong() shl 4) or shape.toLong()
            pathCache[k]?.let { return Path(it) }
            val p = Path()
            when (shape) {
                Settings.SHAPE_CIRCLE -> p.addCircle(size / 2, size / 2, size / 2, Path.Direction.CW)
                Settings.SHAPE_ROUNDED -> p.addRoundRect(RectF(0f, 0f, size, size), size * 0.22f, size * 0.22f, Path.Direction.CW)
                else -> {
                    val r = size / 2; val n = 4.2; val steps = 96
                    for (i in 0 until steps) {
                        val t = 2 * Math.PI * i / steps
                        val c = cos(t); val s = sin(t)
                        val x = r + r * sign(c) * abs(c).pow(2 / n)
                        val y = r + r * sign(s) * abs(s).pow(2 / n)
                        if (i == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat())
                    }
                    p.close()
                }
            }
            pathCache[k] = p
            return Path(p)
        }

        fun render(
            d: Drawable?, size: Int, shape: Int, frames: Boolean, themed: Boolean, accent: Int, dark: Boolean
        ): Bitmap? {
            d ?: return null
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val path = shapePath(size.toFloat(), shape)
            if (d is AdaptiveIconDrawable) {
                val mono = if (themed && Build.VERSION.SDK_INT >= 33) d.monochrome else null
                c.save(); c.clipPath(path)
                val inset = (size * 0.25f).toInt() // capas de 108dp con zona visible de 72dp
                if (mono != null) {
                    c.drawColor(if (dark) blend(0xFF1F2128.toInt(), accent, 0.18f) else blend(Color.WHITE, accent, 0.16f))
                    mono.setBounds(-inset, -inset, size + inset, size + inset)
                    mono.colorFilter = PorterDuffColorFilter(
                        if (dark) blend(accent, Color.WHITE, 0.35f) else blend(accent, Color.BLACK, 0.25f),
                        PorterDuff.Mode.SRC_IN
                    )
                    mono.draw(c)
                } else {
                    d.background?.let { it.setBounds(-inset, -inset, size + inset, size + inset); it.draw(c) }
                    d.foreground?.let { it.setBounds(-inset, -inset, size + inset, size + inset); it.draw(c) }
                }
                c.restore()
            } else if (frames) {
                // Ícono antiguo: marco blanco Galaxy con el gráfico al 72 % centrado.
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (dark) 0xFFF2F2F2.toInt() else Color.WHITE }
                c.drawPath(path, p)
                val pad = (size * 0.14f).toInt()
                d.setBounds(pad, pad, size - pad, size - pad)
                d.draw(c)
            } else {
                val w = d.intrinsicWidth.coerceAtLeast(1); val h = d.intrinsicHeight.coerceAtLeast(1)
                val sc = size.toFloat() / maxOf(w, h)
                val dw = (w * sc).toInt(); val dh = (h * sc).toInt()
                d.setBounds((size - dw) / 2, (size - dh) / 2, (size + dw) / 2, (size + dh) / 2)
                d.draw(c)
            }
            return bmp
        }

        fun blend(a: Int, b: Int, t: Float): Int {
            val r = (Color.red(a) * (1 - t) + Color.red(b) * t).toInt()
            val g = (Color.green(a) * (1 - t) + Color.green(b) * t).toInt()
            val bl = (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt()
            return Color.rgb(r, g, bl)
        }
    }
}
