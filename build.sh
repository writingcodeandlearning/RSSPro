#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$ROOT/tools"
BUILD="$ROOT/build"
DIST="$ROOT/dist"
SIGNING="$ROOT/signing"

JAVA_HOME="$TOOLS/jdk"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
export LD_LIBRARY_PATH="$TOOLS/root/usr/lib/7zip:$TOOLS/root/usr/lib/aarch64-linux-gnu/android:$TOOLS/root/usr/lib/aarch64-linux-gnu:$TOOLS/root/usr/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

JAVA="$JAVA_HOME/bin/java"
JAVAC="$JAVA_HOME/bin/javac"
JAR="$JAVA_HOME/bin/jar"
KEYTOOL="$JAVA_HOME/bin/keytool"
ANDROID_JAR="$TOOLS/android-platform/android-13/android.jar"
AAPT2="$TOOLS/root/usr/lib/android-sdk/build-tools/debian/aapt2"
ZIPALIGN="$TOOLS/root/usr/lib/android-sdk/build-tools/debian/zipalign"
APKSIGNER="$TOOLS/root/usr/share/java/apksigner.jar"
R8="$TOOLS/r8/r8.jar"
MIN_API=23

for tool in "$JAVA" "$JAVAC" "$KEYTOOL" "$ANDROID_JAR" "$AAPT2" "$ZIPALIGN" "$APKSIGNER" "$R8"; do
    if [ ! -e "$tool" ]; then
        echo "Falta la herramienta: $tool" >&2
        exit 1
    fi
done

mkdir -p "$BUILD" "$DIST" "$SIGNING"
rm -rf "$BUILD/classes" "$BUILD/gen" "$BUILD/dex" "$BUILD/res.zip" \
    "$BUILD/base.apk" "$BUILD/classes.jar" "$BUILD/unaligned.apk" \
    "$BUILD/aligned.apk" "$DIST/RSSPro.apk"

echo "[1/8] Compilando recursos..."
"$AAPT2" compile --dir "$ROOT/res" -o "$BUILD/res.zip"

echo "[2/8] Enlazando recursos y manifiesto..."
"$AAPT2" link \
    -o "$BUILD/base.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$ROOT/AndroidManifest.xml" \
    --java "$BUILD/gen" \
    --min-sdk-version "$MIN_API" \
    --target-sdk-version 33 \
    "$BUILD/res.zip"

echo "[3/8] Compilando Java..."
mkdir -p "$BUILD/classes"
find "$ROOT/src" "$BUILD/gen" -name '*.java' -print > "$BUILD/sources.txt"
"$JAVAC" -source 8 -target 8 \
    -bootclasspath "$ANDROID_JAR" \
    -encoding UTF-8 \
    -Xlint:-options \
    -d "$BUILD/classes" \
    @"$BUILD/sources.txt"

echo "[4/8] Empaquetando clases..."
"$JAR" cf "$BUILD/classes.jar" -C "$BUILD/classes" .

echo "[5/8] Generando classes.dex con D8..."
mkdir -p "$BUILD/dex"
"$JAVA" -cp "$R8" com.android.tools.r8.D8 \
    --min-api "$MIN_API" \
    --lib "$ANDROID_JAR" \
    --output "$BUILD/dex" \
    "$BUILD/classes.jar"

echo "[6/8] Construyendo APK..."
python3 - "$BUILD/base.apk" "$BUILD/dex/classes.dex" "$BUILD/unaligned.apk" <<'PY'
import sys
import zipfile

base, dex, out = sys.argv[1], sys.argv[2], sys.argv[3]
with zipfile.ZipFile(base) as source, zipfile.ZipFile(out, "w") as target:
    for info in source.infolist():
        if info.filename == "classes.dex":
            continue
        target.writestr(info, source.read(info.filename))
    target.write(dex, "classes.dex")
PY
"$ZIPALIGN" -f 4 "$BUILD/unaligned.apk" "$BUILD/aligned.apk"

echo "[7/8] Firmando APK..."
PROPS="$SIGNING/keystore.properties"
KEYSTORE="$SIGNING/rsspro.keystore"
if [ ! -f "$KEYSTORE" ] || [ ! -f "$PROPS" ]; then
    PASSWORD="$(python3 -c 'import secrets, string; print("".join(secrets.choice(string.ascii_letters + string.digits) for _ in range(32)))')"
    umask 077
    cat > "$PROPS" <<EOF
STORE_PASSWORD=$PASSWORD
KEY_PASSWORD=$PASSWORD
KEY_ALIAS=rsspro
EOF
    "$KEYTOOL" -genkeypair \
        -keystore "$KEYSTORE" \
        -storetype PKCS12 \
        -storepass "$PASSWORD" \
        -keypass "$PASSWORD" \
        -alias rsspro \
        -keyalg RSA \
        -keysize 4096 \
        -validity 10000 \
        -dname "CN=RSSPro, OU=Lector RSS, O=RSSPro, L=Local, C=ES" >/dev/null
    echo "     Keystore creado en signing/rsspro.keystore (conserva este archivo)."
fi
umask 022
chmod 600 "$KEYSTORE" "$PROPS"

# shellcheck disable=SC1090
source "$PROPS"
if [ -z "${STORE_PASSWORD:-}" ] || [ -z "${KEY_PASSWORD:-}" ] || [ -z "${KEY_ALIAS:-}" ]; then
    echo "signing/keystore.properties está incompleto." >&2
    exit 1
fi

"$JAVA" -jar "$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "pass:$STORE_PASSWORD" \
    --key-pass "pass:$KEY_PASSWORD" \
    --out "$DIST/RSSPro.apk" \
    "$BUILD/aligned.apk"
chmod 644 "$DIST/RSSPro.apk"

echo "[8/8] Verificando APK..."
"$ZIPALIGN" -c 4 "$DIST/RSSPro.apk"
"$JAVA" -jar "$APKSIGNER" verify --verbose --print-certs "$DIST/RSSPro.apk"
"$AAPT2" dump badging "$DIST/RSSPro.apk"

echo
echo "APK listo: $DIST/RSSPro.apk"
ls -lh "$DIST/RSSPro.apk"
