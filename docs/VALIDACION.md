# Validación de Nube Música 0.5.0

Fecha: 30 de septiembre de 2026. Paquete `cl.fernando.nubemusica`, código 6.

## Comprobaciones realizadas

| Comprobación | Resultado |
| --- | --- |
| Compilación de la versión final | Correcta: `:app:assembleDebug`. |
| Pruebas Java | 50 aprobadas; 0 fallos, errores u omitidas. |
| Análisis Android lint | 0 errores; 44 advertencias. |
| Firma APK | Válida y el mismo certificado de la versión 0.4.0. |
| Paquete, versión y arquitectura | 0.5.0 / 6, Android mínimo API 28, objetivo 36; arm64-v8a. |
| Manifest | VideoActivity privada, soporte PiP y cambios de configuración declarados. |
| Alineación ZIP | `zipalign -c -P 16 -v 4` correcta. |
| Biblioteca nativa MEGA | Idéntica byte por byte a la de 0.4.0; no se recompiló. |
| Migración SQL 1 → 2 | Se ejecutaron las sentencias reales sobre una base de prueba: conserva canciones y fechas, añade media_type y reinicia los puntos de escaneo antiguos. |

Certificado SHA-256: `774f194c25f25d5b70d87857b0bb796531d8243d4359a2bc3e50e3b68cb5194d`.

APK: `77167024` bytes.

SHA-256 APK: `9aee25e46536346dcdbdfaeb1cc0314f88acd587d74b9bc6b202255768d39fc1`.

## Cobertura

Se mantienen las pruebas de búsqueda y recorrido de carpetas propias, compartidas, unidades compartidas, paginación, accesos directos, recuperación y errores de red, reglas de biblioteca, cola y MEGA. Las nuevas pruebas verifican detección de video sin perder audio, descubrimiento en carpetas anidadas y compartidas, saltos de cinco segundos, límites de inicio/final, duración desconocida, desbordamiento y puntos de reanudación.

Los informes XML y de lint, la compilación, firma, manifest y alineación están en `pruebas/`.

Advertencias de lint: ClickableViewAccessibility: 1, DataExtractionRules: 1, ExportedService: 1, NotifyDataSetChanged: 3, SetTextI18n: 33, UnsafeDynamicallyLoadedCode: 5. Se mantienen advertencias de textos en Java, refresco de adaptadores, arrastre accesible, copia de seguridad, servicio multimedia y rutas de carga nativa generadas por el SDK de MEGA. El servicio comprueba UID o controlador de confianza. No se desactivaron comprobaciones de lint.

## Prueba pendiente en el dispositivo

No se ejecutó esta versión en un teléfono ni en un emulador desde este entorno. Las pruebas Java no verifican por sí solas la apariencia, decodificación de video, superficies gráficas, PiP, controles de Android, sesión real ni las transferencias desde una cuenta real. No se usaron credenciales ni se accedió a las cuentas del usuario.

La guía `VIDEOS_Y_PIP.md` incluye la prueba de actualización, listas existentes, MP4 desde Drive y MEGA, controles ±5 s, giro, ventana flotante y escaneo de videos. La disponibilidad de formatos y búsqueda de posiciones depende del archivo y de las capacidades del teléfono. Las cuotas y permisos de las nubes se respetan.

## Herramientas y referencias

JDK 17, Gradle 8.13, Android Gradle Plugin 8.13.2, Android Platform 36, Build Tools 35.0.0, Media3 1.9.4. El checksum del Gradle Wrapper se corrigió usando la suma oficial de la distribución 8.13. Las fuentes, receta y licencias del SDK MEGA 10.19.0 se mantienen en el proyecto.

- [Android: Picture in Picture](https://developer.android.com/develop/ui/views/picture-in-picture)
- [Android: PlayerView](https://developer.android.com/media/media3/ui/playerview)
- [Gradle: comprobaciones de integridad](https://gradle.org/release-checksums/)
