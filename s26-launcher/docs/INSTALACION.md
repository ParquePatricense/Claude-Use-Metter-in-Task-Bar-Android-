# Instalación

## 1. Instalar el APK
1. Copiá `InicioS26-1.0.0-release.apk` al teléfono (o descargalo desde GitHub en el propio S22 Ultra).
2. Abrilo desde **Mis archivos**. Si aparece *"Por tu seguridad, el teléfono no puede instalar apps
   desconocidas"*, tocá **Ajustes** y activá **Permitir de esta fuente** para Mis archivos (o el navegador).
3. Si **Auto Blocker** (Ajustes › Seguridad y privacidad › Auto Blocker) está activo, desactivalo
   temporalmente: bloquea instalaciones fuera de Galaxy Store / Play Store.
4. Instalar. Play Protect puede mostrar un aviso por ser una app no publicada: **Instalar de todas formas**.

Alternativa por cable: `adb install InicioS26-1.0.0-release.apk`

## 2. Primera ejecución
Aparecen dos íconos: **Ajustes de Inicio S26** (configuración) y, al tocar Inicio, el asistente:
bienvenida → diseño inicial → cuadrícula → tema → gestos → establecer como predeterminado.

## 3. Establecerlo como predeterminado
- Desde el último paso del asistente o **Ajustes de Inicio S26 › Launcher predeterminado**
  (abre el diálogo oficial del sistema, `RoleManager.ROLE_HOME`), o
- **Ajustes › Apps › Elegir apps predeterminadas › App de inicio › Inicio S26**.

Recomendado en Samsung: **Ajustes › Pantalla › Barra de navegación › Gestos de deslizamiento**.
Los gestos funcionan con launchers de terceros; Recientes sigue siendo el de One UI.

## 4. Doble toque para apagar la pantalla (opcional)
Ajustes de Inicio S26 › Gestos › Doble toque › *Apagar pantalla*. Luego activá
**Ajustes › Accesibilidad › Apps instaladas › Inicio S26: apagar pantalla**.
El servicio no recibe eventos ni lee ventanas: solo llama a `GLOBAL_ACTION_LOCK_SCREEN`.

## 5. Volver a One UI Home
- **Ajustes de Inicio S26 › Volver a One UI Home**, o
- **Ajustes › Apps › Elegir apps predeterminadas › App de inicio › One UI Home**, o
- simplemente desinstalar Inicio S26: el sistema vuelve solo a One UI Home.

One UI Home no se toca en ningún momento: tu diseño original sigue intacto.

## Actualizar
Instalá el APK nuevo encima: si está firmado con la misma llave, se conserva el diseño.
La llave release (`inicios26-release.jks`) **no está en el repositorio**: guardala vos.
