# JKAnime TV

Cliente no oficial de [jkanime.net](https://jkanime.net) para Android TV. Pensado para reproducirse con control remoto en TVs y emuladores Leanback.

## Estado

**v0.1** — primera versión funcional con:

- Listado de anime en home, horario, explorar, buscar y favoritos.
- Detalle de anime con metadata (cover, sinopsis, estado) y carga de episodios vía AJAX/CSRF de jkanime.
- Reproductor con ExoPlayer (Media3 1.5) y controles nativos navegables por D-pad.
- Guardado de progreso por episodio (Room) y diálogo "Continuar desde X / Desde el inicio" al reabrir.
- Atajos de control de TV: PAUSE/PLAY, FF (+1:30), REW (−10 s), BACK doble para salir.
- Bypass de OCSP en TLS para entornos (emuladores / TVs con tiempo offline) donde Cloudflare devuelve respuestas OCSP vencidas.

## Build

Requiere Android Studio con JDK 21 (el JBR embebido funciona). Desde la raíz del proyecto:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Estructura

- `app/src/main/java/com/jkanimetv/app/data/JKAnimeRepository.kt` — scraping y AJAX contra jkanime.
- `app/src/main/java/com/jkanimetv/app/viewmodel/MainViewModel.kt` — estado de pantallas y Room.
- `app/src/main/java/com/jkanimetv/app/ui/screens/` — pantallas Compose (Home, Browse, Detail, etc.).
- `app/src/main/java/com/jkanimetv/app/ui/player/PlayerActivity.kt` — reproductor con manejo de teclas TV.

## Créditos

Basado en la lógica de scraping de [RipJKAnimeNX](https://github.com/darkxex/RipJKAnimeNX) y la app Android `rjkanimetv` original.
