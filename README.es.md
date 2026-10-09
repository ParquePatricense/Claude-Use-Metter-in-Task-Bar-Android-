# 📊 Claude Uso para Android

🇬🇧 [Read in English](README.md)

**Tu consumo de Claude, siempre a la vista.** App de Android que muestra en tiempo real el uso de tu sesión (0–100%) y de tu semana: en la barra de estado, en una notificación fija, en un widget animado y en una app llena de estadísticas, mascotas pixel art y temas retro. 🎮

> ⚠️ App personal, **no oficial**. No está afiliada a Anthropic. Lee el mismo dato que Claude muestra en *Ajustes → Uso* (sesión de 5 horas y límite semanal).

🌍 **Disponible en 7 idiomas**: español, inglés, italiano, francés, alemán, japonés y chino. La app sigue el idioma del teléfono; con cualquier otro idioma queda en español.

<p align="center">
  <img src="docs/principal.jpg" width="250" alt="Pantalla principal con barra de vida">
  &nbsp;
  <img src="docs/medidor-arco.jpg" width="250" alt="Medidor de arco con mascota">
</p>

<p align="center">
  <img src="docs/notificacion.jpg" width="520" alt="Notificación con sesión y semana">
</p>

---

## 📲 Instalar

1. ⬇️ Descargá el `.apk` desde la **[última versión](../../releases/latest)**.
2. 📂 Abrilo en el teléfono y permití instalar apps de origen desconocido.
3. 🔑 Iniciá sesión en Claude dentro de la app. Listo: se configura sola.

📱 Requiere **Android 8 o superior**. Probada en Samsung Galaxy S22 Ultra (One UI).
🔄 Para actualizar, instalá la nueva APK encima: se conservan el historial y los ajustes.

---

## ✨ Qué hace

### 🔔 Barra de estado y notificación
- 🔢 El porcentaje de la sesión aparece en la barra de estado, al lado de la hora. Android pinta esos íconos de un solo color, así que elegís una **forma** que se distinga: número grande, número con marco o número dentro de un anillo de progreso.
- 📋 Una notificación fija muestra el % de la sesión, la hora de reinicio y la cuenta regresiva, el % semanal con su día de reinicio y dos barras, con el color de tu paleta y ajustado para leerse en modo claro y oscuro.
- 🖼️ La imagen de la notificación puede ser tu mascota, el estilo del widget o solo el ícono de la app.
- 🔘 Hasta 3 botones: **Actualizar**, **Ir a Claude**, **Claude Code**, **Ver uso en Claude**.
- ⚡ **Botón en Ajustes rápidos** con el porcentaje; al tocarlo actualiza.
- 🔒 En la pantalla de bloqueo se ve la notificación completa, y el número aparece en el Always On Display.

### 🧩 Widget de inicio
- 📐 Redimensionable de **1×1 a 5×5**, con un diseño distinto por tamaño: desde un anillo solo hasta un panel completo con barras y gráfico de 24 horas.
- 🎨 **7 estilos**: anillo, barra, solo número, batería retro, corazones, punto y mascota.
- 🎞️ Animado hasta la tasa de refresco de tu pantalla (24 a 165 FPS, o automático). Si la pantalla de inicio no acepta tantos cuadros, la app baja sola.
- 🌫️ Opacidad del fondo, modo minimalista (solo la imagen en cualquier tamaño) y tocar para actualizar o abrir la app.
- 🔐 Se puede poner en la pantalla de bloqueo si tu versión de One UI lo permite.

### 👾 Mascotas
- 🐾 **37 mascotas pixel art** en 4 categorías: **Clásicas** (blob, slime, fantasma, gato, robot, invasor, dragoncito, ninja, hongo, capibara, pingüino, calavera), **Animales** (perro, gato negro, loro, hámster, pez), **Juegos y cultura** (personajes propios inspirados en clásicos de los videojuegos: fontaneros rojo y verde, erizo azul, seis rangers, mineros cúbicos, explosivo verde, comecocos, bola rosa, héroe del bosque, robot azul, dinosaurio verde) y **Armas medievales** (espada, hacha, arco y flecha).
- 😄 Reaccionan a tu uso: festejan con la sesión fresca, se cansan y transpiran desde el 75%, tiemblan con ojos en X desde el 90% y se duermen con "Zzz" al 100%.
- 👆 Animadas a la tasa de refresco de tu pantalla. Al tocarlas saltan, suenan y tiran corazones pixel.
- 🍼 Estilo **chibi** (bebé) opcional y una **mascota distinta cada día**.
- 🏠 El ícono de la app puede ser tu mascota.

<p align="center">
  <img src="docs/estados.png" width="600" alt="Estados de ánimo de las mascotas">
</p>

<details>
<summary>👀 Ver las 37 mascotas</summary>
<p align="center"><img src="docs/mascotas.png" width="700" alt="Todas las mascotas"></p>
</details>

### 📈 Estadísticas
- 📉 **Gráficos de 24 horas y 7 días** de sesión y semana. Tocá y arrastrá para ver el valor exacto.
- 🔥 Resumen diario (pico, sesiones usadas, límites alcanzados), racha sin llegar al límite, horario de más uso y **comparación con la semana anterior**.
- 🗓️ **Mapa de calor** de cuándo usás Claude (día de la semana × hora, últimas 4 semanas).
- 🔮 **Predicción**: "A este ritmo (12%/h) llegás al límite a las 16:40".
- 🤔 **¿Me alcanza?**: estima si te alcanza la sesión para 30 minutos, 1 o 2 horas más a tu ritmo.
- 🏆 **Logros**: se ven solo los obtenidos; los demás quedan ocultos en un desplegable.

### ⏰ Avisos
- 🚨 Al **75%, 90% y 100%**, **5 minutos antes del reinicio** y **al reiniciarse la sesión**.
- 🎵 Sonidos 8-bit (moneda, alerta, game over, fanfarria) y 📳 **vibraciones con patrón** para saber qué pasó sin mirar.
- 😴 **No molestar inteligente**: aprende tu horario de sueño de tu historial y silencia los avisos en ese horario.
- 📅 **Resumen semanal** los lunes.
- 🔐 Aviso de **sesión vencida** con botón para volver a iniciar sesión.
- ⌚ Los avisos llegan a tu Galaxy Watch si tenés las notificaciones de la app activadas.

### 🤖 Modos y automatización
- 🎯 **Modo "Concentración Claude"**: activa No molestar al llegar al 90% o al 100% y lo apaga solo al reiniciarse la sesión. Aparece junto a tus otros modos, así *Modos y rutinas* de Samsung puede reaccionar a él.
- 🔗 **Avisos para Tasker, MacroDroid y similares**: acción `com.matias.claudeusage.EVENT`, extra `event` = `level75`, `level90`, `limit` o `reset` (más `pct`).
- 📌 **Atajos del ícono** (mantener apretado): Actualizar, Ver 7 días, Abrir Claude, Claude Code.
- 🗣️ "Ok Google, abrí Claude Uso" abre la app.

### 🔋 Batería y datos
- 🧠 **Intervalo inteligente**: cada 30 s mientras usás Claude, cada 2 min si está quieto, cada 10 min con la pantalla apagada. Actualiza al instante al prender la pantalla o al volver internet.
- 🪫 **Modo ahorro automático** con menos de 20% de batería o con el ahorro de energía activo: tope de 30 FPS, sin fondo animado y widget quieto.
- 👥 **Varias cuentas de Claude**, cada una con su historial.
- 💾 **Exportar el historial a CSV**, **copia de seguridad y restauración** manual y **copia automática semanal** en la carpeta que elijas (guarda las últimas 4).

---

## ⚙️ Guía de Ajustes

Todas las opciones, sección por sección. Las listas se abren y cierran con el botón redondo de la derecha, y la pantalla queda en el mismo lugar después de cada cambio.

| Sección | Opciones |
| --- | --- |
| 👤 **Cuenta** | Cambiar de cuenta · Agregar otra cuenta · Volver a iniciar sesión · Quitar una cuenta (y su historial) |
| 🌗 **Tema** | Sistema / Claro / Oscuro · Negro puro AMOLED en modo oscuro |
| 🎨 **Apariencia** | **Paleta** (24, por categoría: Básicos — Claude, Monocromo, Pastel, Neón · Nintendo — Game Boy, Game Boy Advance, NES, DS, Switch, Switch 2, GameCube · PlayStation — PS1 a PS5, PSP, PS Vita · PC y portátiles — PC gamer RGB, PC retro DOS, Steam Deck, ROG Ally, Legion Go, MSI Claw) · **Tipografía** (10: Normal, Pixel, Retro arcade, Terminal, Moderna, Cuadrada, Redonda, Cómic, Monoespaciada, Arcade gruesa) · **Medidor de la app** (12: Arco, Batería retro, Corazones, Mascota, Barra de vida RPG, Barra de experiencia, Bloques que caen, Comecocos, Anillos, Monedas, Velocímetro, Combustible) · Modo zen (solo el número y el tiempo) · **Fondo animado** (11: Estrellas, Lluvia, Nieve, Gato arcoíris, Código que cae, Hiperespacio, Burbujas, Luciérnagas, Fuegos artificiales, Corazones, Hojas de otoño) · Filtro retro CRT · Transiciones animadas |
| 👾 **Mascota** | Estilo chibi · Una mascota distinta cada día · Elegir mascota (por categoría) · Mostrar la mascota arriba · Ícono de la app = tu mascota · Sonidos 8-bit |
| 🏆 **Logros** | Logros obtenidos · Lista oculta de los que podés conseguir |
| 🔔 **Barra de estado y notificación** | Ícono de la barra: número grande / con marco / dentro de un anillo · Imagen de la notificación: tu mascota / estilo del widget / solo el ícono · Botones: Actualizar, Ir a Claude, Claude Code, Ver uso en Claude |
| 🎞️ **Animaciones** | Animaciones sí/no · **Fluidez de la app**: automática (tasa de refresco de la pantalla) o fija en 24, 30, 48, 60, 90, 120, 144, 165 FPS · Pulso al pasar el 90% · Confeti al reiniciarse · Segundos en la cuenta regresiva |
| ⏰ **Actualización y avisos** | Intervalo inteligente · Aviso 5 min antes del reinicio · Resumen semanal los lunes · Vibraciones con patrón · Modo ahorro automático · No molestar inteligente (muestra tu horario de sueño detectado) · Vibrar al actualizar desde el widget o Ajustes rápidos |
| 🤖 **Modos y automatización** | Modo "Concentración Claude" · Activar al 90% o 100% · Dar permiso de No molestar · Avisos para Tasker/MacroDroid (apagados por defecto) |
| 🧩 **Widget de inicio** | Opacidad del fondo · Estilo del widget (7) · Minimalista · Animar el widget · **Fluidez del widget** (automática o 24–165 FPS) · Indicador de FPS actual · Al tocar: actualizar / abrir la app |
| 💾 **Datos** | Exportar historial (CSV para Excel) · Copia de seguridad (ajustes + historial) · Restaurar copia · Respaldo automático semanal · Elegir carpeta · Respaldar ahora |
| 🔐 **Seguridad** | Bloquear la app con huella, rostro o PIN |
| ℹ️ **Otros** | Cómo usar el widget en la pantalla de bloqueo, los atajos, el Asistente de Google, el botón de Ajustes rápidos, el Always On Display y el Galaxy Watch · Permitir que funcione en segundo plano · Apagar widget y notificación |

---

## 🔒 Privacidad y seguridad

- 📱 La sesión de Claude se guarda **solo en tu teléfono**, cifrada con una llave del Keystore de Android (AES-256-GCM).
- 🌐 La app solo se conecta a `https://claude.ai` para leer el uso. El tráfico HTTP sin cifrar está bloqueado.
- 🚫 Las copias en la nube y la transferencia entre teléfonos están desactivadas; las copias de la propia app no incluyen sesiones.
- 🔐 Bloqueo opcional con huella, rostro o PIN.
- 🧾 Revisión de seguridad completa de 20 puntos: **[SECURITY.md](SECURITY.md)** (en inglés).

---

## 🛠️ Compilar

Se compila sin Android Studio ni Gradle, con `aapt`, `javac`, `dalvik-exchange`, `zipalign` y `apksigner`:

```bash
sudo apt install openjdk-21-jdk aapt apksigner zipalign dalvik-exchange
curl -o android.jar https://raw.githubusercontent.com/Sable/android-platforms/master/android-34/android.jar
./build.sh android.jar ruta/a/llave.jks contraseña
```

🔑 La llave de firma **no** está en el repositorio. Para actualizar la app instalada hay que firmar siempre con la misma llave.

## 📁 Estructura

| Carpeta / archivo | Contenido |
| --- | --- |
| `src/` | ☕ Código Java |
| `src/.../L.java` | 🌍 Traducciones (7 idiomas) |
| `res/` | 🖼️ Layouts, íconos, sonidos 8-bit y textos del ícono por idioma |
| `assets/` | 🔤 Tipografías |
| `design/` | 👾 Sprites de las mascotas (`mascots.py`), vista previa (`preview.py`), generador (`gen.py`) y fuentes de las traducciones (`i18n/`) |
| `docs/` | 📸 Capturas del README |
| `build.sh` | 🛠️ Script de compilación |

## 🙌 Créditos

- 🔤 Tipografías con licencia SIL Open Font License 1.1: [Silkscreen](https://github.com/googlefonts/silkscreen), Press Start 2P, VT323, Orbitron, Fredoka, Comic Neue y Bungee.
- 🎮 Las mascotas de "Juegos y cultura" son **diseños propios** inspirados en clásicos de los videojuegos. No son personajes oficiales.
