# Claude Uso

App de Android que muestra en tiempo real el uso de tu sesión de Claude (0–100%) en la barra de notificaciones, un widget animado y una app con estadísticas.

> App personal, no oficial. No está afiliada a Anthropic. Lee el mismo dato que Claude muestra en *Ajustes → Uso*.

## Qué hace
- Notificación fija con el % de la sesión (5 h) y de la semana, y el número en la barra de estado.
- Widget redimensionable de 1x1 a 5x5, animado a 60 FPS, con 7 estilos.
- 12 mascotas pixel art que reaccionan a tu uso, evolucionan y desbloquean accesorios con logros.
- Avisos al 75/90/100%, antes del reinicio, resumen semanal y sonidos 8-bit.
- Gráficos de 24 h y 7 días, mapa de calor, predicción, comparación de semanas.
- Paletas (Claude, Monocromo, Pastel, Neón, Game Boy), tipografía pixel, fondo animado y filtro CRT.
- Varias cuentas, copia de seguridad manual y automática semanal.

## Instalar
Descargá `ClaudeUso.apk` desde **Releases** e instalalo en el teléfono (Android 8 o superior).

## Compilar
Ver `build.sh`. Se compila con `aapt`, `javac`, `dalvik-exchange` y `apksigner`, sin Gradle.

## Mascotas
Los sprites se diseñan en `design/mascots.py`. `design/preview.py` genera una vista previa y `design/gen.py` regenera `MascotData.java`.

## Créditos
Tipografía pixel: [Silkscreen](https://github.com/googlefonts/silkscreen), licencia SIL Open Font License 1.1.
