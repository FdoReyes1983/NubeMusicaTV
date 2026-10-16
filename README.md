# Nube Música · Android 0.5.0

Aplicación personal para reunir y reproducir música y videos de Google Drive y MEGA. Paquete `cl.fernando.nubemusica`, versión 0.5.0, código 6. Conserva el certificado de las entregas anteriores.

## Instalar y usar

Instala `Nube_Musica_v0.5.0.apk` como actualización, sin desinstalar la versión anterior. Abre **MEGA → Conectar MEGA** y escribe tus datos en el teléfono. La guía completa está en `docs/CONECTAR_MEGA.md`. Google Cloud no necesita cambios.

Se mantienen la biblioteca compacta, la pantalla completa del reproductor, la cola que se puede reorganizar, las listas y favoritos, el ecualizador y el reproductor en segundo plano.

La biblioteca combina los catálogos escaneados de las cuentas conectadas. El escaneo de MEGA recorre Mi MEGA y las carpetas compartidas accesibles; también permite crear un catálogo limitado a una carpeta. Conserva puntos de avance y no borra registros anteriores ante recorridos parciales. La migración SQLite 1 → 2 conserva las canciones y añade el tipo de medio. Reinicia los puntos de escaneo antiguos para que un recorrido completo pueda descubrir videos; no borra el catálogo previo. Los listados Música y Videos se filtran por separado en SQLite; el esquema se mantiene compatible y los registros quedan aislados por proveedor y cuenta.

## Videos y PiP

La pestaña **Videos** muestra los videos encontrados en Drive y MEGA; el selector del teléfono admite audio y video. El reproductor de video usa la misma sesión Media3 que la cola existente, admite pantalla completa y muestra controles de −5 s / +5 s. Tocar el video oculta los controles. PiP usa la API de Android, con entrada automática al ir a Inicio mientras reproduce, botón manual y tres acciones: retroceder, pausa/reproducción y adelantar. La opción automática se puede desactivar en el menú de video.

Se guardan puntos de reanudación por video. Al cerrar PiP o volver de la pantalla de video se pausa; la navegación explícita a la cola conserva la reproducción. La reproducción de música mantiene su funcionamiento en segundo plano. Consulta **docs/VIDEOS_Y_PIP.md** para instalar y probar.

## MEGA

La integración utiliza el SDK oficial 10.19.0 compilado para `arm64-v8a`. La contraseña solo se usa para iniciar sesión; el token de sesión se cifra con AES-GCM y Android Keystore dentro del directorio privado sin copias de seguridad. No hay servidor propio ni envío de contraseñas a terceros distintos de MEGA.

El lector Media3 pide fragmentos binarios de hasta 1 MiB al SDK y conserva la posición al buscar dentro del audio. No crea descargas permanentes ni un servidor HTTP local. Se aplican las cuotas y permisos de MEGA. Las URI guardadas contienen solamente el identificador del archivo y de su cuenta.

El SDK usa su contrato JNI de archivos Android. `mega/privacy/android/data/filewrapper` implementa ese contrato para la caché privada, con validación de rutas y sin acceso a carpetas externas. No se habilita ninguna función de subida, borrado o sincronización de la nube en la interfaz.

Las clases `MegaManager`, `MegaDataSource`, `MegaRepository` y `MegaSessionStore` bajo `nz/mega/sdk` son el puente de Nube Música. Comparten paquete con los enlaces Java generados porque los listeners de bajo nivel del SDK tienen visibilidad de paquete. El resto de las clases de esa carpeta se generan mediante SWIG.

## Compilar el APK

JDK 17, Android SDK Platform 36, Build Tools 35.0.0, Gradle Wrapper 8.13 y Android Gradle Plugin 8.13.2. Mínimo Android API 28, objetivo 36. El APK incluye solo la arquitectura ARM de 64 bits. El binario nativo incluido tiene segmentos alineados a 16 KiB.

Configura `ANDROID_HOME` o `local.properties` con la ruta de tu SDK y ejecuta:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

El resultado se encuentra en `app/build/outputs/apk/debug/app-debug.apk`. La firma de desarrollo se conserva en `dev-signing/debug.keystore` para poder actualizar la aplicación personal. Alias `androiddebugkey`, contraseñas de desarrollo `android`. No es una clave de publicación comercial.

## Reconstruir MEGA

No hace falta reconstruir el SDK para cambiar la interfaz Java: su biblioteca y enlaces ya están incluidos. Para reconstruirlos, consulta `native/rebuild-mega.sh`. Necesita Linux x86_64, NDK r27b, JDK 17, CMake 3.31, SWIG 4.5, Ninja, compilador C/C++, herramientas Autotools y acceso a internet para vcpkg.

`native/mega-sdk-v10.19.0.tar.gz` conserva la fuente oficial. `native/binary-streaming-swig.patch` evita que SWIG 4.5 convierta datos binarios de audio a texto y corrige el descriptor de la devolución de datos. El script aplica ese cambio, usa la línea base vcpkg `ef7dbf94b9198bc58f45951adcf1f041fcbc5ea0` y copia el binario y los enlaces resultantes.

El SDK se compila con sincronización habilitada para satisfacer sus cabeceras Android, pero Nube Música no llama a sus funciones de sincronización. Chat, servidor HTTP, extracción nativa de metadatos y ejemplos están deshabilitados. Las licencias están en `licenses/` y también dentro de la app, en **⋮ → Licencias de software**.

## Límites y validación

La guía y `docs/VALIDACION.md` describen los resultados verificados. La conexión real con MEGA debe probarse en el teléfono con una cuenta del usuario. No se ha ejecutado esta versión en un teléfono o emulador desde este entorno.

No se incluyen enlaces públicos de MEGA introducidos manualmente, otras nubes, descargas permanentes, clasificación automática por artista/álbum, Chromecast ni Android Auto. Las carátulas, formatos y efectos de audio dependen de los metadatos y las capacidades del dispositivo.
