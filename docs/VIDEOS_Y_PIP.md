# Nube Música 0.5.0 — Videos, PiP y saltos de 5 segundos

## Instalar la actualización

1. Descarga **Nube_Musica_v0.5.0.apk** y ábrelo desde Descargas en tu teléfono.
2. Elige **Actualizar**, sin desinstalar la versión anterior. Así conservas las listas, el catálogo, las conexiones y los ajustes guardados.
3. Abre Nube Música. El paquete y el certificado son los mismos; no necesitas volver a registrar la aplicación en Google Cloud.

Si una nube solicita renovar su sesión, hazlo desde la propia aplicación.

## Encontrar los videos

La cabecera tiene las pestañas **Música**, **Videos**, **Mi Drive**, **Compartido**, **MEGA** y **Mis listas**. Puedes deslizar las pestañas horizontalmente si no caben.

Para incorporar videos al catálogo anterior, vuelve a escanear cada nube:

1. En **Videos**, toca **Buscar archivos**. Si no aparece, abre **⋮ junto al contador → Actualizar toda la biblioteca**.
2. Si tienes las dos nubes conectadas, elige **Actualizar catálogo de Drive**. El recorrido incluye Mi unidad y los compartidos accesibles, con sus subcarpetas.
3. Para MEGA, abre **MEGA → Reunir música y videos → Toda mi cuenta y los compartidos**. Espera a que termine y vuelve a **Videos**.

Solo se realiza un escaneo a la vez. Puedes pausarlo y continuar después. Se conservan las canciones del catálogo anterior mientras se realiza el nuevo recorrido; tus listas y favoritos permanecen guardados.

También puedes reproducir directamente un video desde una carpeta de Drive o MEGA, sin esperar al catálogo completo. **⋮ del menú principal → Abrir archivos multimedia del teléfono** permite elegir archivos locales.

Los listados **Música** y **Videos** tienen búsqueda por nombre o carpeta, filtro de origen y ordenación. **Más** carga los siguientes resultados del catálogo.

## Usar el reproductor de video

- Toca un video para abrirlo a pantalla completa.
- **−5 s** retrocede cinco segundos y **+5 s** adelanta cinco segundos. Se detienen en el comienzo o el final y se desactivan si el archivo no permite buscar posiciones.
- La barra permite ir a otra parte del video. El tiempo de carga depende del archivo, la conexión y la nube.
- Toca la imagen para ocultar o mostrar los controles; gira el teléfono para verlo horizontalmente.
- **Anterior**, **Siguiente** y **≡ Cola** permiten recorrer y organizar la reproducción. En la cola puedes mantener pulsado el tirador de una fila y moverla.
- Se guarda la posición de cada video periódicamente y al pausar. Al abrirlo de nuevo desde la biblioteca puede continuar donde lo dejaste; si estaba prácticamente terminado, comienza desde el principio.
- Volver desde la pantalla del video pausa la reproducción. Abrir expresamente la cola permite organizarla mientras sigue sonando.

El reproductor completo de música también tiene los nuevos botones **−5 s / +5 s**. Toca el nombre de la canción en la franja inferior para abrirlo.

## Ventana flotante PiP

1. Reproduce un video y toca **PiP**, en la parte superior.
2. También puedes ir a **Inicio** mientras el video se reproduce: la ventana flotante automática viene activada.
3. Arrastra la ventana para moverla. Tócala para ver pausa/reproducción, retroceso y avance de cinco segundos, volver a pantalla completa o cerrar.
4. Cerrar la ventana pausa el video.

Desde **⋮ del reproductor de video** puedes desactivar **Ventana flotante al ir a Inicio** y seguir usando el botón PiP manualmente.

Si Android impide abrir PiP, busca **Imagen en imagen** o **Ventana en ventana** en los ajustes del teléfono y permite esa función para Nube Música. El nombre exacto puede variar según la versión de Xiaomi/HyperOS. No hace falta conceder un permiso de superposición general.

## Formatos y conexión

La app reconoce archivos de video por su tipo o extensión, incluidos MP4, MKV, WebM y otros contenedores habituales. Que un archivo aparezca en el listado no garantiza que el teléfono pueda decodificar todos sus formatos internos. Para la primera prueba, usa un MP4 que ya sepas que funciona en tu teléfono, preferiblemente H.264 con audio AAC.

Los archivos de la nube se transmiten durante la reproducción. Requieren conexión y están sujetos a los permisos del propietario y las cuotas de MEGA. No se crea una descarga permanente, no se convierte el formato del video y no se modifican los archivos de tus nubes.

## Primera prueba en tu Xiaomi

1. Instala como actualización y comprueba que sigan tus listas y favoritos.
2. Abre una canción y prueba **−5 s / +5 s**.
3. Reproduce un MP4 de Drive y otro de MEGA.
4. En cada video prueba pausa, avance, retroceso y giro horizontal.
5. Toca **PiP**, abre otra app y comprueba sus controles; vuelve a pantalla completa y cierra la ventana.
6. Actualiza los catálogos y revisa que los videos propios y compartidos aparezcan en **Videos**.

La compilación y las comprobaciones automáticas se realizan en el entorno de desarrollo. Esta entrega no se ha ejecutado en un teléfono ni en un emulador desde aquí: la reproducción real, la apariencia y el comportamiento de PiP requieren esa prueba en el dispositivo. El instalador está preparado para Android 9 o posterior, ARM de 64 bits.
