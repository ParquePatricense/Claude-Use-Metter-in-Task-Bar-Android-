#!/usr/bin/env bash
# Compila Inicio S26 sin Android Studio: kotlinc + aapt + dx + zipalign + apksigner.
# Es el build que se usó para generar los APK publicados (ver docs/COMPILAR.md).
#
# Uso:  tools/build.sh debug            -> dist/InicioS26-debug.apk   (llave de debug local)
#       tools/build.sh release          -> dist/InicioS26-release.apk (ProGuard + llave release)
#
# Variables (con valores por defecto del entorno de compilación original):
#   ANDROID_JAR_CODE  android.jar API 36 para compilar código   (APIs de Android 16)
#   ANDROID_JAR_RES   android.jar API 34 para enlazar recursos  (aapt v1 no lee el formato de 35+)
#   KOTLINC           binario kotlinc 2.1.x
#   PROGUARD_CP       classpath de ProGuard 7.7 (solo release)
#   RELEASE_KS / RELEASE_KS_PASS / RELEASE_ALIAS   llave de firma release
set -euo pipefail
MODE=${1:-debug}
cd "$(dirname "$0")/.."
ROOT=$(pwd)
APP=app/src/main
OUT=build/$MODE
JAR_CODE=${ANDROID_JAR_CODE:-/tmp/sdkdl/android36.jar}
JAR_RES=${ANDROID_JAR_RES:-/tmp/sdkdl/android34.jar}
KOTLINC=${KOTLINC:-/opt/kt/kotlinc/bin/kotlinc}
KLIB=$(dirname "$KOTLINC")/../lib
PG=${PROGUARD_CP:-/opt/pg/proguard-base.jar:/opt/pg/proguard-core.jar:/opt/pg/kotlin-metadata-jvm.jar:/opt/pg/kotlin-stdlib.jar:/opt/pg/gson.jar:/opt/pg/log4j-api.jar:/opt/pg/log4j-core.jar:/opt/pg/json.jar}
VERSION_NAME=$(grep -o 'android:versionName="[^"]*"' $APP/AndroidManifest.xml | cut -d'"' -f2)
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-}"

rm -rf "$OUT" && mkdir -p "$OUT"/{gen,rclasses,classes} dist
echo "▸ [$MODE] recursos (aapt)"
aapt package -f -m -J "$OUT/gen" -M $APP/AndroidManifest.xml -S $APP/res -I "$JAR_RES" --auto-add-overlay

echo "▸ R.java (javac)"
javac -nowarn -encoding UTF-8 --release 8 -cp "$JAR_CODE" -d "$OUT/rclasses" $(find "$OUT/gen" -name '*.java') 2>/dev/null || \
javac -nowarn -encoding UTF-8 -source 8 -target 8 -bootclasspath "$JAR_CODE" -d "$OUT/rclasses" $(find "$OUT/gen" -name '*.java')

echo "▸ Kotlin (kotlinc)"
"$KOTLINC" $(find $APP/java -name '*.kt') \
  -no-stdlib -no-reflect \
  -cp "$JAR_CODE:$KLIB/kotlin-stdlib.jar:$OUT/rclasses" \
  -jvm-target 1.8 -Xlambdas=class -Xsam-conversions=class -Xstring-concat=inline \
  -Xno-param-assertions -Xno-call-assertions -Xno-receiver-assertions \
  -nowarn -d "$OUT/classes" 2>&1 | grep -v '^warning' || true
[ -f "$OUT/classes/com/s26home/launcher/LauncherApp.class" ] || { echo "✗ falló la compilación Kotlin"; exit 1; }
cp -r "$OUT/rclasses/." "$OUT/classes/"

# kotlin-stdlib sin las clases multi-release (dx no entiende module-info de Java 9).
cp "$KLIB/kotlin-stdlib.jar" "$OUT/stdlib.jar"
zip -q -d "$OUT/stdlib.jar" 'META-INF/versions/*' 'META-INF/*.kotlin_module' >/dev/null 2>&1 || true

(cd "$OUT/classes" && jar cf ../app.jar .)
INPUT="$OUT/app.jar $OUT/stdlib.jar"
if [ "$MODE" = release ]; then
  echo "▸ ProGuard (reducción + optimización)"
  java -cp "$PG" proguard.ProGuard \
    -injars "$OUT/app.jar" -injars "$OUT/stdlib.jar"'(!META-INF/**)' \
    -outjars "$OUT/min.jar" -libraryjars "$JAR_CODE" \
    @tools/proguard.pro > "$OUT/proguard.log" 2>&1 || { tail -40 "$OUT/proguard.log"; exit 1; }
  INPUT="$OUT/min.jar"
fi

echo "▸ DEX (dx)"
dalvik-exchange --dex --min-sdk-version=26 --output="$OUT/classes.dex" $INPUT 2>&1 | grep -v '^warning\|JAVA_TOOL' || true
[ -f "$OUT/classes.dex" ] || { echo "✗ falló dx"; exit 1; }

echo "▸ Empaquetado"
DBG=""; [ "$MODE" = debug ] && DBG="--debug-mode"
aapt package -f $DBG -M $APP/AndroidManifest.xml -S $APP/res -I "$JAR_RES" -F "$OUT/unsigned.apk" --auto-add-overlay
(cd "$OUT" && aapt add -f unsigned.apk classes.dex >/dev/null)
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "▸ Firma"
if [ "$MODE" = release ]; then
  KS=${RELEASE_KS:?Definí RELEASE_KS (ruta a la llave .jks)}; PASS=${RELEASE_KS_PASS:?Definí RELEASE_KS_PASS}; ALIAS=${RELEASE_ALIAS:-inicios26}
  DEST=dist/InicioS26-$VERSION_NAME-release.apk
else
  KS=$ROOT/build/debug.jks; PASS=android; ALIAS=androiddebugkey
  [ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass $PASS -keypass $PASS -alias $ALIAS \
      -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
  DEST=dist/InicioS26-$VERSION_NAME-debug.apk
fi
apksigner sign --ks "$KS" --ks-pass "pass:$PASS" --ks-key-alias "$ALIAS" \
  --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --out "$DEST" "$OUT/aligned.apk"
apksigner verify "$DEST"
echo "✓ $DEST ($(du -h "$DEST" | cut -f1))"
