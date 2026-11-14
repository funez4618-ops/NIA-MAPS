# NIA MAPS

Aplicación Android de mapas en desarrollo, independiente de cualquier otro repositorio.

## Primera versión
- Mapa basado en OpenStreetMap mediante MapLibre.
- Vista inicial centrada en Ciudad de México.
- Búsqueda básica de lugares mediante el servicio de geocodificación disponible en Android.
- Solicitud de permiso y acceso a la última ubicación conocida del dispositivo.
- Interfaz inicial con accesos para Auto, Caminar y Bici (los modos de ruta todavía no calculan rutas).

## Compilar
Requiere JDK 17 y Gradle 8.9:

```bash
gradle :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Cada push a `main` y cada ejecución manual de GitHub Actions intenta compilar y publicar el APK como artefacto.

## Aviso
Esta es una base MVP inicial. La navegación giro a giro, cálculo de rutas multimodales, reportes comunitarios de semáforos y modo offline aún no están implementados. Los mapas usan teselas de OpenStreetMap y deben respetarse sus políticas de uso y atribución.
