package com.s26home.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.TextView
import com.s26home.launcher.data.Settings
import kotlin.math.min
import kotlin.math.roundToInt

fun Context.dp(v: Number): Int = (v.toFloat() * resources.displayMetrics.density).roundToInt()
fun Context.dpf(v: Number): Float = v.toFloat() * resources.displayMetrics.density
fun Context.sp(v: Number): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v.toFloat(), resources.displayMetrics)
fun View.dp(v: Number): Int = context.dp(v)
fun View.dpf(v: Number): Float = context.dpf(v)

fun Context.isNight(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

fun withAlpha(c: Int, a: Float): Int = Color.argb((a * 255).roundToInt().coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

object Anim {
    /** Curvas tipo "Sine Out" que usa One UI: arranque decidido y frenado largo y suave. */
    val EASE_OUT = PathInterpolator(0.22f, 0.25f, 0f, 1f)
    val EASE_IN_OUT = PathInterpolator(0.33f, 0f, 0.2f, 1f)
    val EASE_IN = PathInterpolator(0.4f, 0f, 1f, 1f)
}

object Fonts {
    /** En Galaxy, Typeface.DEFAULT ya es SamsungOne (o la fuente que eligió el usuario). */
    val regular: Typeface = Typeface.create(Typeface.DEFAULT, 400, false)
    val medium: Typeface = Typeface.create(Typeface.DEFAULT, 500, false)
    val semibold: Typeface = Typeface.create(Typeface.DEFAULT, 600, false)
    val bold: Typeface = Typeface.create(Typeface.DEFAULT, 700, false)
}

/** Colores y radios derivados de los ajustes + modo claro/oscuro. */
class UiTheme(ctx: Context, s: Settings) {
    val dark = ctx.isNight()
    val accent = s.accent
    val radius = ctx.dpf(s.cornerRadius)
    val opacity = s.panelOpacity / 100f
    val blur = s.blur

    /** Fondo de la pantalla de apps / carpetas (vidrio esmerilado sobre el fondo de pantalla). */
    val panel = if (dark) withAlpha(0xFF141416.toInt(), 0.35f + 0.55f * opacity)
                else withAlpha(0xFFF3F3F6.toInt(), 0.30f + 0.62f * opacity)
    val folderPanel = if (dark) withAlpha(0xFF262629.toInt(), 0.55f + 0.42f * opacity)
                      else withAlpha(0xFFF7F7F9.toInt(), 0.55f + 0.42f * opacity)
    val card = if (dark) 0xFF242426.toInt() else 0xFFFCFCFC.toInt()
    val cardPressed = if (dark) 0xFF323235.toInt() else 0xFFEDEDF0.toInt()
    val text = if (dark) 0xFFF5F5F7.toInt() else 0xFF111114.toInt()
    val textSecondary = if (dark) 0xFFA2A2A8.toInt() else 0xFF6E6E74.toInt()
    val field = if (dark) 0x33FFFFFF else 0x14000000
    val divider = if (dark) 0x1FFFFFFF else 0x14000000
    val scrim = if (dark) 0x66000000 else 0x33000000
}

/**
 * Medidas de la pantalla de inicio calculadas a partir del tamaño real de la ventana,
 * los insets de sistema y los ajustes. En el S22 Ultra (FHD+, 450 dpi ≈ 384×823 dp)
 * la cuadrícula 4×5 queda con celdas de ~89×120 dp como en One UI.
 */
class DeviceProfile(ctx: Context, s: Settings, val width: Int, val height: Int, val insetTop: Int, val insetBottom: Int) {
    val cols = s.homeCols
    val rows = s.homeRows
    val dockCount = s.dockCount
    val padH = ctx.dp(s.marginH)
    val padV = ctx.dp(s.marginV)
    val showLabels = s.showLabels
    val labelPx = ctx.sp(s.labelSize)
    val labelGap = ctx.dp(6)
    val labelH = if (showLabels) (labelPx * 1.35f).roundToInt() + labelGap else 0

    val cellW = (width - 2 * padH) / cols
    private val wantIcon = ctx.dp(58f * s.iconScale / 100f * (4f / maxOf(cols, 4)).coerceAtLeast(0.82f))
    val iconPx: Int

    val dockH: Int
    val dockBottom = insetBottom + ctx.dp(6)
    val indicatorH = ctx.dp(20)
    val searchH = if (s.homeSearchBar) ctx.dp(48) else 0
    val searchGap = if (s.homeSearchBar) ctx.dp(10) else 0
    val workspaceTop = insetTop + ctx.dp(4)
    val workspaceBottom: Int
    val cellH: Int

    init {
        var icon = min(wantIcon, (cellW * 0.80f).toInt())
        val dockIconRoom = ctx.dp(30)
        dockH = icon + dockIconRoom
        workspaceBottom = height - dockBottom - dockH - indicatorH - searchH - searchGap
        cellH = (workspaceBottom - workspaceTop - 2 * padV) / rows
        icon = min(icon, ((cellH - labelH) * 0.86f).toInt())
        iconPx = icon.coerceAtLeast(ctx.dp(36))
    }

    val dockIconPx get() = iconPx
}

fun roundRect(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
    setColor(color); cornerRadius = radius
    if (stroke > 0) setStroke(stroke, strokeColor)
}

fun ripple(color: Int, radius: Float, pressed: Int) =
    RippleDrawable(ColorStateList.valueOf(pressed), roundRect(color, radius), roundRect(Color.WHITE, radius))

fun TextView.style(sizeSp: Float, color: Int, tf: Typeface = Fonts.regular) {
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp); setTextColor(color); typeface = tf
    includeFontPadding = false
}
