# Nube Música TV v0.5.0-tv2-drive

Variante temporal para Android TV con Google Drive solamente.

- Conserva package `cl.fernando.nubemusica` y la misma firma debug.
- No empaqueta `libmega.so`, por lo que el APK deja de estar limitado a `arm64-v8a`.
- Min SDK: Android 8.0 (API 26).
- Mantiene Google Drive, biblioteca, música, video, cola, listas, favoritos, ecualizador y reproducción en segundo plano.
- Oculta las entradas de MEGA.

La autenticación de Google Drive requiere Google Play Services y una cuenta Google disponible en la caja Android TV.
