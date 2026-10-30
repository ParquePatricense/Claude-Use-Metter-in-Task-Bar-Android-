# Diseño — referencia One UI 9 (Galaxy S26 Ultra)

Fuente: lenguaje visual de One UI 7/8 (base directa de One UI 9) y lo publicado del S26 Ultra
hasta la fecha de desarrollo. No se usó código, íconos ni fondos de Samsung: todo se dibuja por
código o con símbolos Material (Apache 2.0); los fondos se generan en el equipo.

| Elemento | One UI | Implementación |
|---|---|---|
| Forma de ícono | Squircle de curvatura continua | Superelipse n = 4,2 (`IconCache.shapePath`) |
| Íconos antiguos | Marco blanco opcional | Marco blanco, gráfico al 72 % |
| Etiquetas en inicio | Blancas, 1 línea, sombra suave | 12 sp, sombra 2,5 dp al 40 % |
| Dock | Sin fondo, sin etiquetas | Igual; fondo opcional |
| Indicador | Puntos, casita en principal | Puntos 6 dp, activo 100 %, resto 40 %, casita |
| Carpeta (ícono) | Squircle translúcido, miniaturas grandes | 2×2 por defecto, 3×3 opcional, 8 colores |
| Carpeta (abierta) | Tarjeta centrada, vidrio, título grande | Radio 28 dp, título 22 sp seminegrita, blur |
| Apps | Páginas horizontales, buscador inferior | Igual; categorías como chips opcionales |
| Menú contextual | Tarjeta redondeada, fila de acciones con ícono | Radio 24 dp, elevación 12 dp |
| Ajustes | Título grande que se pliega, grupos en tarjetas | Cabecera 26 % de alto, tarjetas 26 dp |
| Controles | Switch/SeekBar/diálogos One UI | Tema `DeviceDefault`: en Galaxy son los nativos de One UI |
| Tipografía | SamsungOne | `Typeface.DEFAULT` (en Galaxy = SamsungOne o la fuente elegida) |
| Movimiento | Curvas "sine", 300–400 ms | `PathInterpolator(0.22, 0.25, 0, 1)`, 220–340 ms, velocidad ajustable |
| Transparencias | Vidrio esmerilado | Blur entre ventanas (API 31) + `RenderEffect` + velo de respaldo |
| Acento | Azul One UI | #3E91FF + 7 alternativos |

Evitado a propósito: colores saturados en superficies, sombras duras, íconos chicos, rebotes
exagerados y la estética Material/Pixel (sin FAB, sin cápsula de búsqueda Google, sin "At a glance").
