# Registro de instalación y verificación

Fecha: 2026-10-26  
Sistema: Kali GNU/Linux Rolling 2026.3, `aarch64`, sin `sudo`, sin `adb` y sin emulador.  
Objetivo: compilar y firmar un APK Android de RSS sin Gradle ni AndroidX, usando herramientas portátiles locales.

## Estado final

- APK: `dist/RSSPro.apk`
- Paquete: `com.rsspro`
- Versión: `1.3.1` (`versionCode` 5)
- Nombre visible: `RSSPro`
- SDK mínimo: 23 (Android 6.0)
- SDK objetivo: 33 (Android 13)
- Tamaño: 173 KiB (176.679 bytes)
- SHA-256: `27b01efddbb56ba93a202bc3f60782c26e748ee4c857571f7b84e18a856cb320`
- Firma: esquemas v1, v2 y v3 verificados correctamente.
- Certificado: `CN=RSSPro, OU=Lector RSS, O=RSSPro, L=Local, C=ES`
- SHA-256 del certificado: `ce6f3aaa8be6235ec00c07ee4df171b8592d3684c1af50eaa8ff4751c17032fe`
- Pruebas JVM: 108 correctas, 0 fallidas.
- Instalación en dispositivo: pendiente; este entorno no dispone de `adb` ni emulador.

## Herramientas instaladas

Todas las herramientas se extrajeron localmente en `tools/`; no se instaló nada en el sistema.

| Herramienta | Versión | Ubicación | Uso |
|---|---|---|---|
| Temurin JDK | 21.0.13+9 LTS | `tools/jdk` | `java`, `javac`, `jar`, `keytool`; compilación y firma |
| Android Platform 33 | Android 13, revisión 3 | `tools/android-platform/android-13/android.jar` | Classpath de compilación y `--lib` de D8 |
| AAPT2 | 2.19-debian (`aapt` 2.19) | `tools/root/usr/lib/android-sdk/build-tools/debian/aapt2` | Compilar/enlazar recursos, `dump badging` |
| zipalign | 10.0.0+r36-1.1+b1 | `tools/root/usr/lib/android-sdk/build-tools/debian/zipalign` | Alineación de 4 bytes y comprobación |
| apksigner | 35.0.2-1 (CLI 0.9) | `tools/root/usr/share/java/apksigner.jar` | Firma y verificación APK |
| libapksig-java | 35.0.2-1 | `tools/root/usr/share/java/apksig.jar` | Dependencia de apksigner |
| R8 / D8 | 8.11.18 | `tools/r8/r8.jar` | Conversión de clases Java a `classes.dex` |
| 7-Zip | 26.03+dfsg-3 | `tools/root/usr/lib/7zip` | Extracción de paquetes y soporte de herramientas |
| libzopfli | 1.0.3-3+b1 | `tools/root/usr/lib/aarch64-linux-gnu` | Dependencia de las herramientas Android |
| Python | 3.14.8 | sistema | Empaquetado de `classes.dex`, generación de contraseña y automatización |
| Paquetes Android Debian | `aapt 1:14~beta1-5`, `android-libaapt`, `android-libandroidfw`, `android-libbase`, `android-libcutils`, `android-liblog`, `android-libutils`, `android-libziparchive`, `android-libbacktrace` | `tools/dpkg` y `tools/root` | Biblioteca y dependencias de AAPT2/zipalign |

Los paquetes Debian originales se conservan en `tools/dpkg/`. Las descargas originales del JDK y de Android Platform están en `tools/downloads/`.

## Bibliotecas compartidas necesarias

El script `build.sh` exporta esta ruta antes de invocar AAPT2 y zipalign:

```text
tools/root/usr/lib/7zip
tools/root/usr/lib/aarch64-linux-gnu/android
tools/root/usr/lib/aarch64-linux-gnu
tools/root/usr/lib
```

## Cronología de instalación

1. Se comprobó la arquitectura `aarch64`, la memoria disponible, el espacio libre y la ausencia de `sudo`.
2. Se descargaron el JDK Temurin 21 y Android Platform 33.
3. Se descargaron y extrajeron los paquetes Debian de AAPT2, zipalign, apksigner, libapksig y sus dependencias.
4. Se descargó R8 8.11.18.
5. Se verificaron `java`, `aapt2`, `zipalign` y `apksigner` con las bibliotecas locales.
6. Se creó el proyecto Java y los recursos Android.
7. Se compiló con `javac` contra `android.jar`, se convirtió a DEX con D8, se empaquetó el APK y se alineó y firmó.
8. Se corrigieron dos incompatibilidades detectadas durante la primera compilación:
   - Orden de argumentos de `JobInfo.Builder.setBackoffCriteria(long, int)`.
   - Uso de `Notification.CATEGORY_RECOMMENDATION` en lugar de una categoría inexistente.
9. Se añadió `MY_PACKAGE_REPLACED` al receptor de arranque.
10. Se ejecutaron las pruebas JVM y se verificó la firma y el alineado del APK final.
11. Para la versión 1.1 se añadieron imágenes de artículos y fuentes, con extracción desde RSS, Atom, `media:*`, `enclosure` y HTML.
12. Se implementó un cargador de imágenes con caché en memoria y disco, reducción de tamaño y limpieza periódica.
13. Se añadieron el selector por fuente, el marcado de toda la caché como leída y el acceso directo desde la pestaña `Fuentes`.
14. Se implementó la retención configurable de artículos (7/30/90/180 días o desactivada) y su aplicación al arrancar y tras cada sincronización.
15. Se enlazó el título del lector con el artículo original y se actualizaron las pruebas a 70 casos correctos.
16. Para la versión 1.2 se corrigió el parser cuando `media:content` precede a `description` y se añadieron categorías RSS, Atom y Dublin Core.
17. Se añadieron la búsqueda sin tildes, los modos Todos/Sin leer/Leídos/Buscar y la extracción automática del artículo completo con caché y reintento.
18. Se ampliaron las pruebas a 92 casos correctos y se actualizó el detalle con fecha, autor, categoría, fuente y enlace etiquetados.
19. Para la versión 1.3 se añadió el gesto de deslizar a la derecha para marcar artículos sin leer, conservando la posición de la lista.
20. Se añadió un probador de filtros `Notificar` y `Silenciar` con contenido generado o artículos reales de la caché y resultado global de visibilidad/notificación.
21. Se corrigió el falso error de detalle completo: si el feed ya aporta texto, no se muestra el aviso de reintento cuando falla una página SPA.
22. Se añadieron pruebas del simulador, del orden `media:content`/`description` de MSRC y de páginas SPA sin texto, hasta un total de 108.
23. Para la versión 1.3.1 se corrigió el gesto de deslizamiento: se eliminó el bloqueo tras movimientos no horizontales, se reevalúa la dirección en cada movimiento y se redujeron el umbral y la animación para hacerlo más fluido.

## Verificaciones ejecutadas

```bash
./build.sh
./test.sh

tools/jdk/bin/java -jar tools/root/usr/share/java/apksigner.jar verify \
    --verbose --print-certs dist/RSSPro.apk
tools/root/usr/lib/android-sdk/build-tools/debian/zipalign -c 4 dist/RSSPro.apk
tools/root/usr/lib/android-sdk/build-tools/debian/aapt2 dump badging dist/RSSPro.apk
```

Resultados relevantes:

- `Verifies`
- v1 JAR signing: `true`
- v2 APK Signature Scheme: `true`
- v3 APK Signature Scheme: `true`
- `zipalign -c 4`: correcto.
- `aapt2 dump badging`: `com.rsspro`, versión `1.3.1` (`versionCode` 5), `minSdk 23`, `targetSdk 33`.
- Actividad lanzable: `com.rsspro.MainActivity`.
- Pruebas: parser RSS/Atom/RDF, BOM, XML malformado, rechazo de DOCTYPE/entidades externas, HTML, fechas, normalización de URLs, extracción de imágenes, categorías, búsqueda, extracción de artículos completos, retención, semántica de filtros, simulador de filtros y regresiones de MSRC/SPA.

## Firma

- Keystore: `signing/rsspro.keystore`
- Alias: `rsspro`
- Tipo: PKCS12
- Clave: RSA 4096 bits
- Validez: 10.000 días
- Contraseñas: `signing/keystore.properties` (permisos `600`)

Conserva `signing/rsspro.keystore` y `signing/keystore.properties` para futuras actualizaciones. Android rechazará una actualización firmada con otra clave.

## Instalación en el teléfono

1. Transferir `dist/RSSPro.apk` al dispositivo.
2. Permitir la instalación desde el explorador de archivos o gestor usado.
3. Abrir el APK e instalar.
4. En Android 13 o superior, conceder el permiso de notificaciones.
5. Abrir RSSPro y añadir la primera fuente.

No se pudo probar la interfaz en tiempo de ejecución desde este entorno por falta de `adb` o emulador. La compilación, firma, alineado y pruebas JVM sí están verificados.
