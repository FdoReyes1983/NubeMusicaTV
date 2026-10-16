# Nube Música TV v0.5.0-tv1

Esta variante conserva el paquete `cl.fernando.nubemusica` y la misma clave debug del proyecto v0.5.0 para reutilizar la configuración OAuth de Google Drive.

## Adaptaciones TV
- Launcher Android TV (LEANBACK_LAUNCHER) y banner 320x180.
- No requiere pantalla táctil.
- Detección automática de TV y orientación horizontal.
- Navegación por D-pad/control remoto con foco visible y controles ampliados.
- Filas de biblioteca/MEGA seleccionables con OK.
- Reproductor, cola y ecualizador navegables con control remoto.
- En video, OK reproduce/pausa y flechas izquierda/derecha saltan 10 segundos cuando el foco está en el video.
- Mantiene Google Drive, MEGA, música, video, cola, favoritos, listas y Media3.

## Google Drive
No hace falta crear otro cliente OAuth si se instala con la misma firma debug, porque se conserva:
- package: `cl.fernando.nubemusica`
- SHA-1 de la clave de desarrollo ya registrada para la v0.5.0.

En algunos Android TV/cajas, la autorización depende de que existan Google Play Services y una cuenta de Google configurada en el dispositivo.

## Compilación
El workflow `.github/workflows/android-tv-debug.yml` genera el artefacto `Nube-Musica-TV-v0.5.0-tv1`.
