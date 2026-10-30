# Funcionalidades

## Pantalla de inicio
- Cuadrícula 4×5, 4×6, 5×5 o 5×6; al achicarla, lo que no entra se reubica solo.
- Varios escritorios con desplazamiento inercial, indicador de puntos y casita en la página principal.
- Botón Inicio estando en inicio → vuelve a la página principal (como One UI).
- Dock de 3, 4 o 5 íconos, con o sin fondo.
- Barra de búsqueda sobre el dock (abre la búsqueda de apps).
- Márgenes lateral y vertical ajustables.
- Arrastrar y soltar propio: ícono flotante, fantasma squircle en la celda destino, cambio de página
  por el borde, página nueva al arrastrar más allá de la última, animación de aterrizaje.
- Zona superior al arrastrar: **Eliminar · Info · Desinstalar**.
- Soltar un ícono sobre otro crea una carpeta; sobre una carpeta, lo agrega.
- Menú contextual (pulsación larga): accesos directos de la app, Info, Eliminar, Desinstalar.
  Mantener pulsado un acceso directo lo fija en inicio.
- Modo edición (pulsación larga en espacio vacío): páginas reducidas, fijar página principal,
  agregar/eliminar página, Fondo de pantalla, Estilo, Widgets, Ajustes.
- "Agregar a Inicio" desde otras apps (Chrome, Maps, Samsung Internet…): accesos directos y widgets.
- Opción de bloquear el diseño y de agregar apps nuevas automáticamente.

## Carpetas
- Ícono squircle translúcido con vista previa 2×2 (íconos grandes) o 3×3.
- Abiertas: tarjeta de vidrio centrada que se expande desde el ícono, fondo desenfocado,
  nombre editable, 8 colores, botón **+** para agregar/quitar apps, paginado 4×4.
- Una carpeta que queda con un solo elemento se disuelve.

## Pantalla de apps
- Deslizar hacia arriba (sigue al dedo) y hacia abajo para cerrar.
- Páginas horizontales en orden alfabético (cuadrícula 4×5 a 5×6).
- Buscador abajo, al alcance del pulgar (One UI 8/9): prefijo, palabra, iniciales y nombre de
  paquete, sin tildes. Enter abre el primer resultado; sin resultados, buscar en Play Store.
- Categorías (Social, Productividad, Multimedia, Juegos, Noticias, Mapas, Otras) según la
  categoría que declara cada app en Android.
- Ocultar apps (pulsación larga › Ocultar, o Ajustes › Apps ocultas).

## Widgets
- Widgets Android reales vía `AppWidgetHost` (Samsung, Google y cualquier otro).
- Selector con búsqueda y vistas previas por app; tamaño inicial según `targetCellWidth/Height`.
- Mover (arrastrar), redimensionar con tiradores celda por celda respetando mínimos/máximos,
  reconfigurar (si el widget lo admite) y eliminar.
- Vinculación por el diálogo oficial del sistema la primera vez.

## Gestos
- Arriba: pantalla de apps · Abajo: notificaciones o búsqueda · Doble toque: apagar pantalla.
  Cada uno configurable (nada / apps / notificaciones / búsqueda / apagar / editar).
- No roba el gesto vertical a widgets con listas desplazables.

## Personalización
Tamaño de íconos (80–125 %), forma (squircle Galaxy, círculo, cuadrado redondeado), marcos para
íconos antiguos, íconos temáticos (Android 13+), etiquetas y su tamaño, estilo y color de carpetas,
modo claro/oscuro/sistema por app, 8 colores de acento, opacidad de paneles, radio de bordes,
desenfoque, 4 fondos generados en el equipo (Aurora, Amanecer, Titanio, Menta) o selector del
sistema, animaciones on/off y velocidad.

## Rendimiento (S22 Ultra)
- Sin AndroidX ni Compose: APK release de 216 KB, arranque sin inflado XML.
- Íconos generados bajo demanda en un hilo propio, caché LRU acotada (≤32 MB).
- Ícono + etiqueta dibujados en una sola View; animaciones solo con propiedades de render (GPU).
- Widgets reutilizados entre re-enlaces (no se recrean ni parpadean).
- Sin servicios en segundo plano, sin alarmas, sin red. Escritura a disco diferida y atómica.
- 120 Hz: el sistema elige la tasa; las animaciones usan `postInvalidateOnAnimation` y respetan
  la tasa adaptativa (no se fuerza 120 Hz para no gastar batería).

## Permisos
Solo permisos normales (sin diálogos): `SET_WALLPAPER`, `EXPAND_STATUS_BAR`,
`REQUEST_DELETE_PACKAGES`. No usa `QUERY_ALL_PACKAGES`, internet, ubicación ni almacenamiento.

# Limitaciones (y alternativa)

| Limitación | Por qué | Alternativa usada |
|---|---|---|
| Recientes y animación de "volver a inicio" | Las dibuja One UI Home (Quickstep); requieren permisos de sistema | Animación de apertura desde el ícono (`makeScaleUpAnimation`) |
| Panel de notificaciones, ajustes rápidos, bloqueo, Edge, S Pen, Galaxy AI | Son del sistema | Se conviven; no se reemplazan |
| Abrir notificaciones con gesto | No hay API pública | `StatusBarManager.expandNotificationsPanel` por reflexión (como Nova/Lawnchair); si falla, abre la búsqueda |
| Apagar pantalla | Sin API para apps comunes salvo administrador (obliga a PIN y complica desinstalar) | Servicio de accesibilidad mínimo, opcional y desactivado por defecto |
| Accesos directos de apps | Android solo los entrega al launcher predeterminado | Se muestran cuando Inicio S26 es predeterminado |
| Puntos de notificación | Requiere acceso a notificaciones | No se pide (privacidad); fuera del alcance de la v1 |
| Desenfoque | Blur entre ventanas de Android 12+; One UI lo apaga en ahorro de energía | Siempre hay velo de color de respaldo |
| Iconos Samsung en Galaxy | Se usan los que entrega el sistema (incluye paquetes de Galaxy Themes) | — |
| Secure Folder | Es otro usuario, no un perfil visible para launchers | Se abre desde su propio ícono; el perfil de trabajo sí se muestra |
| Widget de reloj/clima en el diseño inicial | Vincular widgets requiere confirmación del usuario | Agregarlo desde Modo edición › Widgets |

# Estado de verificación
- Compilación real: ✔ (debug y release firmados, `apksigner verify` OK).
- Pruebas automáticas Robolectric 4.16 / Android 16 a 384×823 dp, 450 dpi: arranque, diseño
  inicial, pantalla de apps (abrir/cerrar/buscar/gesto), carpeta (abrir/cerrar/crear arrastrando),
  pulsación larga + arrastre, modo edición, selector de widgets, cambio de página por deslizamiento,
  persistencia, cambio de cuadrícula en vivo, todas las pantallas de ajustes y del asistente,
  claro y oscuro. Las pruebas encontraron y se corrigieron 2 cierres al iniciar.
- **No probado en el S22 Ultra físico** (el entorno de compilación no tiene emulador):
  desenfoque real, widgets reales, gestos del sistema y rendimiento a 120 Hz deben confirmarse
  en el equipo.
