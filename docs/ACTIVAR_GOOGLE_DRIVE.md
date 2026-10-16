# Nube Música: activar Google Drive

La APK de prueba puede instalarse y abrir audios seleccionados en Android.
**La conexión directa a Google Drive necesita completar el registro siguiente.** No se ha autorizado tu cuenta desde aquí.

## Si apareció «Conexión cancelada»

Ese mensaje de la versión 0.1.0 era genérico: también se mostraba antes de leer posibles errores de Google.
La captura por sí sola no permite conocer la causa de la interrupción.
Instala la actualización 0.1.1 sobre la anterior, sin desinstalar. En el menú ⋮ encontrarás **Configurar acceso a Drive**.
Si vuelve a fallar, el cuadro mostrará el código de Google cuando la respuesta lo incluya.

Si únicamente instalaste la APK y no hiciste el registro de Google Cloud, completa los pasos siguientes.
Si ya lo registraste, comprueba especialmente el tipo de cliente Android, paquete, huella SHA-1 y correo de prueba.

## 1. Crear el proyecto

Abre https://console.cloud.google.com/ con la cuenta que usarás para administrar tu aplicación.
Crea un proyecto, por ejemplo **Nube Música personal**. Comprueba que esté seleccionado en la parte superior.

En **APIs y servicios → Biblioteca**, busca **Google Drive API** y pulsa **Habilitar**.

## 2. Configurar Google Auth Platform

Abre **Google Auth Platform** (o la sección de consentimiento OAuth que muestre tu consola).

- En **Branding / Desarrollo de la marca**, introduce el nombre Nube Música y tus correos de contacto.
- Completa los campos obligatorios que solicite Google.
- En **Audience / Público**, usa **Externo** si tu cuenta es Gmail personal.
- Para esta primera prueba, conserva el estado **Testing / Prueba** y añade el correo con el que escucharás la música en **Usuarios de prueba**.
- En **Data Access / Acceso a los datos**, añade el permiso:

```text
https://www.googleapis.com/auth/drive.readonly
```

Ese permiso es de solo lectura, pero abarca los archivos de tu cuenta: la aplicación filtra la vista para mostrar carpetas y audio.
No es una autorización limitada únicamente a una carpeta.

## 3. Registrar el cliente de Android

En **Clients / Clientes → Crear cliente**, elige **Android**.

| Campo | Valor para esta APK |
|---|---|
| Nombre del cliente | Nube Música Android personal |
| Nombre del paquete | `cl.fernando.nubemusica` |
| Huella SHA-1 | `5B:55:D0:A6:5D:D3:11:1D:0B:B7:15:FC:EA:68:9A:B4:60:AD:52:1A` |

Guarda el cliente. La huella anterior corresponde a la firma de desarrollo incluida en esta entrega.
Si cambias la firma o el nombre del paquete, tendrás que registrar sus nuevos datos.

No se necesita introducir el ID del cliente Android en la app para este flujo. Tampoco se necesita una contraseña de Google ni un secreto de cliente.

## 4. Instalar y conectar

1. Descarga la APK en tu teléfono y ábrela.
2. Si Android solicita permiso para instalar desde esa aplicación de archivos o navegador, concédelo a ese origen para completar la instalación.
3. Abre **Nube Música → Conectar Google Drive**.
4. Selecciona exactamente la cuenta añadida como usuario de prueba.
5. Revisa la pantalla de Google y autoriza el acceso de lectura solicitado.
6. Entra en **Mi Drive**, abre tu carpeta **MUSICA** y selecciona una canción.
7. Prueba después **Compartido** con una carpeta que realmente esté compartida con esa misma cuenta.

Si Google muestra una advertencia de app sin verificar, verifica que el nombre y el proyecto sean los tuyos. No cambies permisos de seguridad de la cuenta ni desactives Play Protect.
Google documenta una excepción a la verificación para aplicaciones de uso personal; eso no elimina sus pantallas de consentimiento, límites ni políticas.

## 5. Primera prueba breve

Empieza con una carpeta pequeña, idealmente con varios MP3.
Comprueba que puedes reproducir, avanzar dentro de una pista, pasar a la siguiente y bloquear la pantalla sin detener el audio.
Después prueba un mix largo, una carpeta compartida y los formatos adicionales que utilices.

**Reproducir visibles** añade únicamente los audios cargados de esa carpeta.
Pulsa **Cargar más** si quedan páginas. No se recorren automáticamente las subcarpetas.

## Si aparece un error

| Situación | Qué revisar |
|---|---|
| Google impide iniciar sesión | Cuenta de prueba, cliente de tipo Android, paquete y SHA-1. |
| Aparece «Código de Google: 10» | Google informa de un error de configuración; revisa el cliente Android y su paquete y huella. |
| Aparece una cancelación o no hay respuesta | No confirma que tú hayas cancelado. Comprueba si apareció la pantalla de cuenta o consentimiento y conserva la captura del cuadro completo. |
| Conecta pero no aparecen archivos | Google Drive API habilitada, cuenta elegida y permiso de lectura concedido. |
| Una carpeta compartida no abre | Que esa cuenta tenga acceso; un enlace público o acceso directo no concede por sí solo permisos adicionales. |
| Un archivo devuelve acceso restringido | Permiso del propietario y posibles límites de Drive. La app no los evita. |
| Debes autorizar nuevamente después de varios días | En modo de pruebas, Google puede hacer caducar la autorización a los siete días. |
| Se corta al bloquear el teléfono | Revisa las restricciones de batería aplicadas específicamente a Nube Música y la conectividad. |

## Conservar y actualizar

Guarda el ZIP del proyecto. Contiene el código y la firma de este prototipo para preparar actualizaciones compatibles.
Las listas y favoritos se guardan en el teléfono; desinstalar o borrar datos los elimina.
Esta primera versión no incluye descargas permanentes, Chromecast ni Android Auto.

## Fuentes oficiales

- Configuración del cliente y autorización: https://developer.android.com/identity/authorization
- Permisos de Drive: https://developers.google.com/workspace/drive/api/guides/api-specific-auth
- Uso personal y verificación: https://support.google.com/cloud/answer/13464323
- Usuarios de prueba y caducidad: https://support.google.com/cloud/answer/15549945
