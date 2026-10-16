# Nube Música 0.4.0 — Conectar MEGA

## Actualizar en tu teléfono

1. Descarga **Nube_Musica_v0.4.0.apk** y ábrelo desde Descargas.
2. Elige **Actualizar**. Conserva la instalación actual para mantener tus listas y ajustes.
3. Abre Nube Música y toca la pestaña **MEGA**. Si no aparece a simple vista, desliza las pestañas horizontalmente. También está en **⋮ → Conectar o gestionar MEGA**.
4. Escribe el correo y la contraseña de tu cuenta MEGA directamente en la app. Si usas verificación en dos pasos, añade el código actual de tu autenticador. Si MEGA pide el código después del primer intento, vuelve a escribir también la contraseña.
5. Toca **Conectar MEGA** y espera mientras se leen tus carpetas. La primera lectura de una cuenta grande puede tardar.

No necesitas crear otro proyecto ni modificar Google Cloud. La conexión con Drive conserva el paquete y el certificado de la versión anterior.

## Probar primero una canción

En **Mi MEGA** o **Compartidos**, entra en una carpeta que contenga un MP3 conocido y toca la canción. Prueba pausa, avanzar a otro punto y continuar con la pantalla apagada. Al tocar el nombre en el reproductor inferior se abre la pantalla completa; allí puedes abrir la cola y el ecualizador.

La app muestra nombres y tamaños. Artista, álbum y carátula dependen de los metadatos que encuentre el reproductor. No incorpora todos los formatos de vídeo ni convierte archivos de audio.

## Reunir toda la música

1. En la pestaña **MEGA**, toca **Reunir música de MEGA**.
2. Elige **Toda mi cuenta y los compartidos**. Incluye las subcarpetas accesibles de tu cuenta y las carpetas que otras cuentas te compartieron.
3. El escaneo muestra el avance y continúa mediante una notificación. Puedes pausarlo y retomarlo con **Continuar el escaneo anterior**.
4. Abre **Biblioteca**: allí se reúnen los catálogos de Drive y MEGA que hayas escaneado. En el menú junto al total de canciones puedes ordenar, buscar y filtrar por nube o por archivos propios/compartidos.

Dentro de una carpeta también aparece **Crear catálogo solo de esta carpeta**. Esa opción reemplaza, al terminar correctamente, el catálogo local de MEGA por el de esa carpeta y sus subcarpetas. No modifica la nube. El catálogo de Drive se conserva. Para volver a reunir todo MEGA, elige de nuevo **Toda mi cuenta y los compartidos**.

Solo se ejecuta un escaneo a la vez. Si se está buscando música en Drive, termina o pausa ese recorrido antes de escanear MEGA.

## Listas y reproducción

- El menú **⋮** de una canción permite añadirla a la cola, a Favoritos o a una lista existente.
- Puedes mezclar canciones de Drive, MEGA y archivos del teléfono en una lista.
- Crea listas desde el menú principal; reorganiza la cola desde la pantalla del reproductor.
- El audio de MEGA se recibe por fragmentos, sin guardar una descarga permanente. Se necesita conexión para reproducir.

## Sesión, cuotas y problemas habituales

La contraseña no se guarda. La sesión se cifra mediante Android Keystore y se almacena en el directorio privado sin copias de seguridad. El SDK conserva también su caché privada de metadatos. Los identificadores de las canciones no contienen contraseñas, sesiones ni claves de descifrado.

**Desconectar**, dentro de MEGA, elimina la sesión guardada y detiene la reproducción. Tus listas y registros locales no se borran; para reproducirlos debes conectar de nuevo la cuenta correspondiente. Esta acción cierra el acceso local; puedes revisar las sesiones de la cuenta también desde la aplicación oficial de MEGA.

Si aparece una cuota de transferencia agotada, hay que esperar a que MEGA vuelva a permitir la transferencia. Si vence la sesión, vuelve a conectar. Si la app permanece en **Leyendo carpetas** y termina con un error, revisa la conexión y pulsa **Reintentar conexión**. Un archivo eliminado o al que ya no tienes acceso no se podrá reproducir.

Los enlaces públicos de carpetas pegados manualmente y otras nubes aún no están incluidos: esta versión accede a los archivos y compartidos de la cuenta iniciada.

## Alcance de esta entrega

Versión personal para Android 9 o posterior, procesadores ARM de 64 bits, como tu Xiaomi. La compilación y las pruebas automáticas se realizaron aquí. La autenticación real, la reproducción desde tu cuenta y el comportamiento en tu teléfono están pendientes de esa primera prueba. No se utilizó tu contraseña ni se accedió a tu cuenta durante el desarrollo.
