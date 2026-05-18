# JKAnime TV

Cliente no oficial de [jkanime.net](https://jkanime.net) para Android TV. Pensado para controlarse con D-pad / control remoto en TVs, dispositivos Leanback (Chromecast con Google TV, Nvidia Shield, etc.) o emulador Android TV.

## Estado

**v1.0 — estable** ✅

- 🏠 Home, Horario, Explorar (con filtros Tipo + Estado + Género combinables), Buscar y Favoritos.
- 📺 Detalle de anime con cover, sinopsis, estado y listado de episodios (AJAX/CSRF de jkanime con bypass de OCSP para Cloudflare).
- ▶️ Reproductor ExoPlayer (Media3 1.5) con controles navegables por D-pad:
  - **CENTER / PLAY/PAUSE**: pausa / reanuda
  - **FF (avance rápido)**: +1:30 min
  - **REW (retroceso)**: −10 s
  - **BACK**: oculta controles; doble BACK para salir
- 💾 Progreso guardado por episodio (Room) + diálogo "Continuar desde X / Desde el inicio" al reabrir.
- ✅ Indicador tri-estado en cada episodio (completado / en progreso / no visto).
- 🧭 Navegación con sidebar permanente de 200 dp + indicador rojo de la sección activa.
- 🔠 Tipografía Inter empacada (4 weights, ~1.6 MB) y paleta charcoal neutra.

## Instalación rápida

Descarga el APK del último release y:

```
adb install -r JKAnimeTV-v1.0-release.apk
```

O copia el APK a tu TV vía USB / red y ábrelo con un file manager (debes habilitar "instalar de fuentes desconocidas" si tu TV lo pide).

## Build local

Requiere Android Studio con JDK 21 (el JBR embebido funciona). Desde la raíz del proyecto:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleRelease
```

APK firmado y minificado (~10 MB) en `app/build/outputs/apk/release/app-release.apk`.

Para una build de desarrollo (sin R8, mayor tamaño):

```powershell
.\gradlew.bat assembleDebug
```

APK en `app/build/outputs/apk/debug/app-debug.apk`.

## Permisos

La app usa el conjunto mínimo de permisos:

- `INTERNET` — scraping de jkanime + streaming de video.
- `ACCESS_NETWORK_STATE` — saber si hay red antes de cargar.
- Feature `android.software.leanback` (no requerido) — para que aparezca en el launcher de Android TV.

Tráfico cleartext deshabilitado (`usesCleartextTraffic="false"`); todo es HTTPS.

## Estructura

- `app/src/main/java/com/jkanimetv/app/data/JKAnimeRepository.kt` — scraping y AJAX contra jkanime.
- `app/src/main/java/com/jkanimetv/app/viewmodel/MainViewModel.kt` — estado de pantallas + Room (favoritos + historial).
- `app/src/main/java/com/jkanimetv/app/ui/screens/` — pantallas Compose (Home, Browse, Detail, Search, etc.).
- `app/src/main/java/com/jkanimetv/app/ui/player/PlayerActivity.kt` — reproductor con manejo de teclas TV.
- `app/src/main/java/com/jkanimetv/app/MainActivity.kt` — navegación principal (sidebar + NavHost).

## Firma

El release oficial está firmado con el **debug keystore local** de Android Studio (`~/.android/debug.keystore`). Esto permite sideload directo en cualquier TV. Si quieres publicar en Play Store o asegurar actualizaciones reproducibles entre máquinas, genera un keystore propio y reemplaza el `signingConfigs.getByName("debug")` en `app/build.gradle.kts`.

## Créditos

Basado en la lógica de scraping de [RipJKAnimeNX](https://github.com/darkxex/RipJKAnimeNX) y la app Android `rjkanimetv` original (decompilada con jadx para referencia).
