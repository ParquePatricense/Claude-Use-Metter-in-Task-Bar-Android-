# Claude Uso

App de Android que muestra en tiempo real el uso de tu sesión de Claude (0–100%) y de tu semana, en la barra de notificaciones, en un widget animado y en una app con estadísticas.

> App personal, no oficial. No está afiliada a Anthropic. Lee el mismo dato que Claude muestra en *Ajustes → Uso* (sesión de 5 horas y límite semanal).

## Instalar

1. Descargá `ClaudeUso.apk` desde **Releases**.
2. Abrilo en el teléfono y permití instalar apps de origen desconocido.
3. Iniciá sesión en Claude dentro de la app. Listo.

Requiere Android 8 o superior. Probada en Samsung Galaxy S22 Ultra (One UI).

## Funciones

### Barra de estado y notificación
- Número del uso en la barra de estado: grande, con marco o dentro de un anillo de progreso.
- Notificación fija con sesión, reinicio, semana y barras, en el color de tu paleta.
- Imagen de la notificación: tu mascota, el estilo del widget o el ícono de la app.
- Hasta 3 botones: Actualizar, Ir a Claude, Claude Code, Ver uso en Claude.
- Botón en Ajustes rápidos con el porcentaje.

### Widget
- Redimensionable de 1x1 a 5x5, con un diseño distinto por tamaño.
- 7 estilos: anillo, barra, número, batería retro, corazones, punto y mascota.
- Animado hasta la tasa de refresco de la pantalla (24 a 165 FPS, o automático).
- Opacidad del fondo, modo minimalista y tocar para actualizar.
- Compatible con pantalla de bloqueo si One UI lo permite.

### Mascotas
- 37 mascotas pixel art en 4 categorías: Clásicas, Animales, Juegos y cultura (personajes propios inspirados en clásicos) y Armas medievales.
- Reaccionan a tu uso: festejan, se cansan, transpiran, tiemblan y se duermen al 100%.
- Animadas a la tasa de refresco de la pantalla. Al tocarlas saltan y suenan.
- Estilo chibi opcional y mascota distinta cada día.
- El ícono de la app puede ser tu mascota.

### Apariencia
- 24 paletas: Básicos, Nintendo, PlayStation, y PC y portátiles.
- Tema claro, oscuro o del sistema, con opción AMOLED negro puro.
- 10 tipografías, entre ellas pixel, retro arcade, terminal, redonda y cómic.
- 12 medidores: arco, batería, corazones, mascota, barra de vida RPG, experiencia, bloques que caen, comecocos, anillos, monedas, velocímetro y combustible.
- 11 fondos animados y filtro retro CRT.

### Avisos
- Al 75%, 90% y 100%, 5 minutos antes del reinicio y al reiniciarse la sesión.
- Sonidos 8-bit y vibraciones con patrón para saber qué pasó sin mirar.
- No molestar inteligente: aprende tu horario de sueño y silencia los avisos.
- Resumen semanal los lunes.
- Aviso de sesión vencida con botón para volver a iniciar sesión.

### Estadísticas
- Gráficos de 24 horas y 7 días; tocá el gráfico para ver el valor exacto.
- Resumen diario, racha sin llegar al límite, comparación con la semana anterior.
- Mapa de calor de cuándo usás Claude y predicción de cuándo llegás al límite.
- "¿Me alcanza?": estima si te alcanza la sesión para 30 minutos, 1 o 2 horas.
- Logros: se ven los obtenidos; los pendientes quedan ocultos en un desplegable.

### Modos y automatización
- Modo "Concentración Claude": activa No molestar al llegar al 90% o 100% y lo apaga al reiniciarse la sesión.
- Avisos para Tasker, MacroDroid y similares: acción `com.matias.claudeusage.EVENT`, extra `event` = `level75`, `level90`, `limit` o `reset`.
- Atajos del ícono: Actualizar, Ver 7 días, Abrir Claude y Claude Code.

### Batería y datos
- Intervalo inteligente: 30 s mientras usás Claude, más lento si está quieto o con la pantalla apagada.
- Modo ahorro automático con batería baja o ahorro de energía activo.
- Varias cuentas de Claude.
- Exportar el historial a CSV, copia de seguridad manual y automática semanal.

## Privacidad

- La sesión de Claude se guarda solo en el teléfono.
- La app solo se conecta a claude.ai para leer el uso.
- Las copias de seguridad no incluyen las sesiones iniciadas.

## Compilar

Se compila sin Android Studio ni Gradle, con `aapt`, `javac`, `dalvik-exchange`, `zipalign` y `apksigner`:

```bash
sudo apt install openjdk-21-jdk aapt apksigner zipalign dalvik-exchange
curl -o android.jar https://raw.githubusercontent.com/Sable/android-platforms/master/android-34/android.jar
./build.sh android.jar ruta/a/llave.jks contraseña
```

La llave de firma no está en el repositorio. Para actualizar la app instalada hay que firmar siempre con la misma llave.

## Estructura

| Carpeta / archivo | Contenido |
| --- | --- |
| `src/` | Código Java de la app |
| `res/` | Layouts, íconos, sonidos 8-bit y textos |
| `assets/` | Tipografías |
| `design/` | Diseño de las mascotas: `mascots.py` (sprites), `preview.py` (vista previa) y `gen.py` (genera `MascotData.java`) |
| `build.sh` | Script de compilación |

## Créditos

- Tipografías de licencia SIL Open Font License 1.1: [Silkscreen](https://github.com/googlefonts/silkscreen), Press Start 2P, VT323, Orbitron, Fredoka, Comic Neue y Bungee.
- Los personajes de la categoría "Juegos y cultura" son diseños propios inspirados en clásicos de los videojuegos. No son personajes oficiales.
