# JKAnime TV

Cliente no oficial de [jkanime.net](https://jkanime.net) para Android TV. Pensado para controlarse con D-pad / control remoto en TVs, dispositivos Leanback (Chromecast con Google TV, Nvidia Shield, etc.) o emulador Android TV.

## Estado

**v2.0 — estable** ✅

### Lo que ya estaba en v1.0

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
- 🔠 Tipografía Inter empacada y paleta charcoal neutra.

### Novedades v2.0

**Robustez y performance**
- 💨 **Caché HTTP** (OkHttp, 20 MB, 4 buckets de TTL): Home/Top/Horario 30 min, Directorio 15 min, Detalle 6 h. Sirve respuestas cacheadas en modo avión.
- 🔁 **Reintentos exponenciales con jitter** en todas las peticiones (3 intentos, 250 ms base).
- ⏱️ **Debounce de 350 ms** en búsqueda — se acabaron los spam de requests al teclear.
- 🧩 Parsing migrado a jsoup (parcial: `parseAnimeItemList`) con fallback al parser original.

**Reproductor**
- 🛡️ ExoPlayer ahora handshakea con el mismo `OkHttpDataSource` lenient que el scraper → adiós errores de cadena SSL en Cloudflare.
- 💬 **Subtítulos (CC)** — preferencia de idioma persistida (`es` / `en` / `off`).
- 🎚️ **Velocidad variable** 0.5×–2.0×.
- 📺 **Selector de calidad** — Auto / 480p / 720p / 1080p.
- 🪟 **Picture-in-Picture** (botón en menú + autoEnter en API 31+).
- 🎛️ Menú overlay accesible con la tecla **MENU / GUIDE / CAPTIONS / INFO** del control.

**Engagement y descubrimiento**
- ⚙️ **Pantalla de Ajustes** (DataStore): idioma de subs, velocidad/calidad por defecto, limpiar caché HTTP, info de versión.
- 🔔 **Notificaciones de nuevos episodios** — WorkManager periódico (6 h) compara `lastSeenEpisode` con la lista actual y avisa. Tap en la notificación abre el detalle directamente.
- 🕘 **Historial de búsquedas** — chips de búsquedas recientes con eliminación individual.
- ↻ **Refrescar Home** — pill focusable que invalida la caché y recarga.
- 📍 **Scroll memory** en Home y Explorar — al volver del detalle, el scroll está donde lo dejaste.

**Organización**
- 📚 **Colecciones de favoritos** — Viendo / Completado / En pausa / Pendiente. Tabs con contadores en Favoritos; selector vertical en el panel del Detalle.
- 🔎 **Filtros de episodios** en Detalle — Todos / No vistos / En progreso / Completados, con botón "▶ Continuar" que hace scroll al primer episodio en progreso.

## Instalación rápida

Descarga el APK del último release y:

```
adb install -r JKAnimeTV-v2.0-release.apk
```

O copia el APK a tu TV vía USB / red y ábrelo con un file manager (debes habilitar "instalar de fuentes desconocidas" si tu TV lo pide).

## Build local

Requiere Android Studio con JDK 21 (el JBR embebido funciona). Desde la raíz del proyecto:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleRelease
```

APK firmado y minificado (~5 MB) en `app/build/outputs/apk/release/app-release.apk`.

Para una build de desarrollo (sin R8, mayor tamaño):

```powershell
.\gradlew.bat assembleDebug
```

APK en `app/build/outputs/apk/debug/app-debug.apk`.

## Permisos

- `INTERNET` — scraping de jkanime + streaming de video.
- `ACCESS_NETWORK_STATE` — modo offline del caché HTTP.
- `POST_NOTIFICATIONS` — avisos de nuevos episodios (Android 13+).
- Feature `android.software.leanback` (no requerido) — para que aparezca en el launcher de Android TV.
- Feature `android.software.picture_in_picture` (implícito por el flag en la activity del reproductor).

Tráfico cleartext deshabilitado (`usesCleartextTraffic="false"`); todo es HTTPS.

## Estructura

- `app/src/main/java/com/jkanimetv/app/data/`
  - `JKAnimeRepository.kt` — scraping, AJAX, caché HTTP, retry exponencial.
  - `AppDatabase.kt` — Room v2 con migración aditiva (watch_history, favorites, search_history).
  - `Models.kt` — entidades + `FavoriteStatus`.
  - `Settings.kt` — wrapper de DataStore (sub, velocidad, calidad).
- `app/src/main/java/com/jkanimetv/app/viewmodel/MainViewModel.kt` — estado de pantallas, debounce de búsqueda, scroll memory, refresh.
- `app/src/main/java/com/jkanimetv/app/ui/screens/` — pantallas Compose (Home, Browse, Detail, Search, Favorites, Settings).
- `app/src/main/java/com/jkanimetv/app/ui/player/PlayerActivity.kt` — reproductor con OkHttpDataSource, TrackSelector configurable, menú overlay y PiP.
- `app/src/main/java/com/jkanimetv/app/work/EpisodeCheckWorker.kt` — worker periódico de notificaciones.
- `app/src/main/java/com/jkanimetv/app/MainActivity.kt` — navegación, deep-link de notificaciones, encolado del worker.

## Notas conocidas

- **PiP en algunos Android TV**: con `setAutoEnterEnabled(true)` y entrada manual desde el menú del reproductor, PiP funciona en API 31+ con feature `picture_in_picture`. Algunas TVs reportan el feature pero el WindowManager rechaza la entrada — comportamiento del firmware, no de la app. En esos casos el botón del menú simplemente no produce efecto y el video sigue normal.
- **Migración jsoup parcial**: solo `parseAnimeItemList` (cards estándar) usa selectores CSS. El resto del parsing sigue por índices de string como en v1.0, con la idea de migrarlo cuando jkanime rompa algo concreto.

## Firma

El release oficial está firmado con el **debug keystore local** de Android Studio (`~/.android/debug.keystore`). Esto permite sideload directo en cualquier TV. Si quieres publicar en Play Store o asegurar actualizaciones reproducibles entre máquinas, genera un keystore propio y reemplaza el `signingConfigs.getByName("debug")` en `app/build.gradle.kts`.

## Créditos

Basado en la lógica de scraping de [RipJKAnimeNX](https://github.com/darkxex/RipJKAnimeNX) y la app Android `rjkanimetv` original (decompilada con jadx para referencia).
