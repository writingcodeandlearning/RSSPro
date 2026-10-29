# RSSPro

Lector de RSS/Atom nativo para Android, escrito en Java sin Gradle, sin AndroidX y sin dependencias externas. La interfaz está en español y el APK se compila y firma localmente con las herramientas incluidas en `tools/`.

## Características

- Feeds RSS 2.0, Atom y RSS 1.0/RDF.
- Soporte de `content:encoded`, Dublin Core, `guid`, enlaces relativos, BOM, gzip y redirecciones HTTP.
- Lista de artículos con miniaturas, lector interno y estado leído/no leído.
- Deslizar un artículo sin leer hacia la derecha lo marca como leído.
- Modos de lista `Todos`, `Sin leer`, `Leídos` y `Buscar` con búsqueda en título, resumen, contenido, detalle, autor, fuente, categoría y enlace.
- Carga automática del artículo completo desde la web cuando el feed solo trae un resumen.
- Detalle con fecha, autor, categoría, fuente y enlace original.
- Imagen principal en el lector y logos de fuente extraídos de RSS, Atom, `media:*`, `enclosure` o HTML.
- Título del artículo en el lector enlazado al artículo original; el título de la lista abre el lector interno.
- Selector de artículos por fuente cuando hay dos o más fuentes; tocar una fuente muestra solo sus artículos.
- Botón `Todo leído` para marcar toda la caché, incluidas las fuentes o filtros ocultos.
- Eliminación automática de artículos antiguos: 7, 30, 90, 180 días o desactivada (30 por defecto).
- Compartir artículos y copiar enlaces.
- Categorías RSS, Atom y Dublin Core visibles y buscables.
- Filtros en vivo y filtros de notificación:
  - Texto, palabra completa o expresión regular.
  - Campos: cualquier campo, título, resumen, contenido, enlace, autor o fuente.
  - Acciones: Mostrar, Ocultar, Notificar y Silenciar.
  - Ámbito global o limitado a una fuente.
  - Sensibilidad a mayúsculas configurable.
  - Probador integrado con contenido generado o artículos reales de la caché, sin enviar notificaciones.
- Tema del sistema, claro u oscuro.
- Sincronización en segundo plano con `JobScheduler` cada 15, 30, 60, 120 o 360 minutos.
- Notificaciones de artículos nuevos con agrupación y permiso de Android 13.
- Reprogramación tras reinicio o actualización del APK.
- Persistencia local: fuentes, filtros, caché de artículos, estado de lectura, retención y URLs de imágenes.

## Semántica de los filtros

Las reglas `Mostrar` y `Ocultar` afectan a la lista:

1. Si existe al menos una regla `Mostrar` activa, solo son visibles los artículos que coincidan con alguna.
2. Cualquier regla `Ocultar` coincidente prevalece y oculta el artículo.

Las reglas `Notificar` y `Silenciar` afectan solo a los avisos:

1. Si existe al menos una regla `Notificar` activa, solo se avisa de los artículos visibles que coincidan con alguna.
2. Cualquier regla `Silenciar` coincidente prevalece y evita el aviso.
3. Un artículo oculto nunca genera notificación.

La primera descarga de una fuente no genera notificaciones; solo establece la línea base.

El botón `Probar` de un filtro de notificación abre un simulador. Permite editar el contenido de prueba o elegir un artículo real de la caché y muestra si la regla coincide, si el artículo sería visible y si generaría una notificación. La prueba no modifica reglas ni envía avisos.

## Novedades 1.3.1

- Deslizamiento para marcar como leído más rápido y fluido.
- Se eliminó el bloqueo que podía dejar el gesto atascado tras un movimiento vertical inicial.
- Umbral reducido a 80 dp y animación de salida más corta.

## Novedades 1.3

- Deslizar a la derecha un artículo sin leer lo marca como leído, con conservación de la posición de la lista.
- Probador de filtros `Notificar` y `Silenciar` con contenido generado o artículos reales de la caché.
- Resultado del probador con coincidencia de la regla, visibilidad y decisión global de notificación.
- Si el detalle web no se puede extraer, se conserva el contenido del feed y se evita el aviso de error falso.
- Mejor tratamiento de páginas SPA sin contenido extraíble, como el detalle de MSRC.

## Novedades 1.2

- Modos `Todos`, `Sin leer`, `Leídos` y `Buscar`, con búsqueda sin distinguir mayúsculas ni tildes.
- Búsqueda en todos los campos del artículo, incluidos categoría y enlace.
- Corrección del parser cuando `media:content` aparece antes de `description`.
- Categorías RSS, Atom y Dublin Core, visibles en el detalle.
- Carga automática del artículo completo en segundo plano, con caché y reintento.
- Detalle con fecha, autor, categoría, fuente y enlace original etiquetados.

## Novedades 1.1

- Miniaturas en la lista, imagen principal en el lector y logos de fuente.
- Selector por fuente y acceso directo desde la pestaña `Fuentes`.
- Marcado masivo de toda la caché como leída.
- Retención configurable y limpieza automática de artículos antiguos.
- Apertura del artículo original al tocar el título dentro del lector.

## Requisitos

El proyecto incluye todo lo necesario en `tools/` y no requiere `sudo`, Gradle ni Android Studio:

- Temurin JDK 21.
- Android Platform 33 (`android.jar`).
- AAPT2, `zipalign`, `apksigner` y sus bibliotecas.
- R8/D8.
- Python 3 para empaquetar el APK.

## Compilar

```bash
./build.sh
```

El resultado es `dist/RSSPro.apk`, alineado con `zipalign` 4 y firmado con las claves de `signing/`.

Si `signing/rsspro.keystore` o `signing/keystore.properties` no existen, el script los crea con una contraseña aleatoria. Conserva ambos archivos para poder publicar actualizaciones firmadas con la misma clave.

## Pruebas

```bash
./test.sh
```

Ejecuta 108 pruebas JVM del parser RSS/Atom/RDF, extracción de imágenes, categorías, búsqueda, extracción de artículos completos, utilidades de fecha y HTML, retención, lógica completa de filtros y simulador de filtros. No requieren dispositivo ni emulador.

## Instalar

1. Copia `dist/RSSPro.apk` al dispositivo Android.
2. Permite la instalación de aplicaciones de origen desconocido para el explorador o gestor de archivos usado.
3. Abre el APK e instala.
4. En Android 13 o superior, concede el permiso de notificaciones si quieres recibir avisos.
5. Añade una fuente desde la pestaña `Fuentes`.

El APK requiere Android 6.0 (API 23) o superior y tiene como objetivo API 33.

## Estructura

- `AndroidManifest.xml`: actividades, servicio de sincronización y receptor de arranque.
- `src/com/rsspro/`: actividad principal, lector y aplicación.
- `src/com/rsspro/core/`: búsqueda, motor de filtros, simulador de filtros y política de retención.
- `src/com/rsspro/data/`: modelos de datos.
- `src/com/rsspro/net/`: descarga de feeds y páginas, parseo y extracción del detalle.
- `src/com/rsspro/notify/`: canal, notificaciones, JobScheduler y arranque.
- `src/com/rsspro/store/`: preferencias y caché local.
- `src/com/rsspro/sync/`: sincronización, deduplicación y mezcla de artículos.
- `src/com/rsspro/ui/`: utilidades visuales, tema y cargador de imágenes con caché.
- `res/`: recursos de la interfaz.
- `test/`: pruebas JVM.
- `build.sh`: compilación, empaquetado, firma y verificación.
- `test.sh`: pruebas automatizadas.
- `INSTALACIONES.md`: registro de herramientas, versiones y verificaciones.

## Privacidad

La aplicación no usa cuentas, analítica ni servidores propios. Las fuentes, las imágenes y los artículos completos se descargan directamente desde los servidores de cada fuente y todos los datos se guardan en el almacenamiento privado de la app.
