# Arquitectura, compilación y pruebas

## Estructura
```
app/src/main/java/com/s26home/launcher/
  LauncherApp.kt          estado compartido (sobrevive a recreaciones)
  data/  Settings         preferencias
         AppRepository    apps vía LauncherApps (perfiles, accesos directos, búsqueda, categorías)
         IconCache        íconos squircle bajo demanda + LRU
         HomeModel        escritorios/dock/carpetas en JSON atómico
         WidgetHost       AppWidgetHost
  ui/    HomeActivity     orquestador (CATEGORY_HOME)
         DragController   DragLayer + arrastrar y soltar + gestos verticales
         Layouts          CellLayout, PagedView, PageIndicator, GridPage
         IconView         ícono, carpeta, widget
         AppDrawer · FolderPanel · Popup · Widgets · Ui (tema, medidas, curvas)
  settings/  SettingsActivity + SettingsUi (kit One UI)
  onboarding/ OnboardingActivity (asistente de 6 pasos)
  system/  SystemActions, LockService, Wallpapers, PinItemActivity
tools/build.sh, tools/proguard.pro    build reproducible sin Android Studio
tests/robolectric/                    pruebas de humo + capturas
```
Decisión técnica: **Views del framework, sin AndroidX/Compose/Material**. Motivos: menor RAM y
APK (240 KB), arranque más rápido y control total del dibujo (One UI Home también es Views).
Además, en el entorno de compilación Google Maven no era accesible.

## Compilar (como se generaron los APK)
Requisitos: JDK 17+, `kotlinc` 2.1, kotlin-stdlib 1.9.24, `dexdump`, `aapt`, `zipalign`, `apksigner`, `dalvik-exchange`
(Ubuntu: `sudo apt install aapt apksigner zipalign dalvik-exchange`), `android.jar` API 36
(código) y API 34 (recursos, por compatibilidad de aapt v1), ProGuard 7.7 para release.
```
ANDROID_JAR_CODE=…/android-36/android.jar ANDROID_JAR_RES=…/android-34/android.jar tools/build.sh debug
RELEASE_KS=inicios26-release.jks RELEASE_KS_PASS=… tools/build.sh release
```

## Controles del build
- kotlin-stdlib **1.9.24** (la 2.x usa `invokedynamic`, inejecutable tras `dx`).
- ProGuard en debug y release (reduce el stdlib a lo usado).
- Falla si el DEX contiene `invoke-custom` (requiere `dexdump`) o llama a métodos que no existen
  en `android.jar` API 36 (`tools/apicheck.py`).

## Android Studio
`settings.gradle.kts` + `app/build.gradle.kts` (AGP 8.7, Kotlin 2.1, sin dependencias). El código
fuente es el mismo; este camino no se pudo ejecutar en el entorno de compilación.

## Pruebas
```
tools/build.sh debug && gradle -p tests/robolectric test
```
Robolectric 4.16 con Android 16 y gráficos nativos; genera capturas en `tests/robolectric/build/shots`.
