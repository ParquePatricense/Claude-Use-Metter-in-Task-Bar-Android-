#!/usr/bin/env bash
# Compila ClaudeUso.apk sin Android Studio ni Gradle.
# Requisitos (Ubuntu/Debian): sudo apt install openjdk-21-jdk aapt apksigner zipalign dalvik-exchange
# android.jar (API 34): https://raw.githubusercontent.com/Sable/android-platforms/master/android-34/android.jar
# Uso: ./build.sh ruta/a/android.jar ruta/a/llave.jks contraseña
set -e
JAR=${1:?falta android.jar}; KEY=${2:?falta la llave .jks}; PASS=${3:?falta la contraseña}
cd "$(dirname "$0")"
rm -rf build && mkdir -p build/gen build/obj
aapt package -f -m -J build/gen -M AndroidManifest.xml -S res -A assets -I "$JAR"
javac -source 8 -target 8 -nowarn -encoding UTF-8 -bootclasspath "$JAR" -d build/obj $(find src build/gen -name '*.java')
dalvik-exchange --dex --min-sdk-version=26 --output=build/classes.dex build/obj
aapt package -f -M AndroidManifest.xml -S res -A assets -I "$JAR" -F build/unsigned.apk
(cd build && aapt add unsigned.apk classes.dex >/dev/null)
zipalign -f 4 build/unsigned.apk build/aligned.apk
apksigner sign --ks "$KEY" --ks-pass "pass:$PASS" --out ClaudeUso.apk build/aligned.apk
echo "Listo: ClaudeUso.apk"
