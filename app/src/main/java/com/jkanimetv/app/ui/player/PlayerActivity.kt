package com.jkanimetv.app.ui.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import com.jkanimetv.app.data.Settings
import com.jkanimetv.app.ui.theme.TvTypography
import com.jkanimetv.app.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlayerActivity : ComponentActivity() {

    // References owned by the composable; populated via onPlayerReady so
    // dispatchKeyEvent can drive the player without going through Compose focus.
    private var exoPlayerRef: ExoPlayer? = null
    private var playerViewRef: PlayerView? = null
    private var openMenuRef: (() -> Unit)? = null
    private var backPressedTime = 0L

    companion object {
        private const val TAG = "PlayerActivity"
        const val EXTRA_URL = "url"
        const val EXTRA_IS_HLS = "is_hls"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SLUG = "slug"
        const val EXTRA_EPISODE = "episode"
        const val EXTRA_COVER = "cover"

        fun start(ctx: Context, url: String, isHls: Boolean, title: String, slug: String, episode: Int, cover: String) {
            ctx.startActivity(Intent(ctx, PlayerActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_IS_HLS, isHls)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SLUG, slug)
                putExtra(EXTRA_EPISODE, episode)
                putExtra(EXTRA_COVER, cover)
            })
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val slug = intent.getStringExtra(EXTRA_SLUG) ?: ""
        val episode = intent.getIntExtra(EXTRA_EPISODE, 0)
        val cover = intent.getStringExtra(EXTRA_COVER) ?: ""

        setContent {
            @OptIn(ExperimentalTvMaterial3Api::class)
            MaterialTheme(colorScheme = darkColorScheme(), typography = TvTypography) {
                val vm: MainViewModel = viewModel()
                PlayerScreen(
                    url = url,
                    title = title,
                    slug = slug,
                    episode = episode,
                    cover = cover,
                    vm = vm,
                    onPlayerReady = { exo, pv ->
                        exoPlayerRef = exo
                        playerViewRef = pv
                    },
                    onMenuReady = { openMenuRef = it },
                    onExit = { finish() }
                )
            }
        }
    }

    // TV remote: drive the player directly so the only focusable Compose element
    // (the resume dialog, when it's up) can't intercept play/pause as a click.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val player = exoPlayerRef
            val pv = playerViewRef
            val controllerVisible = pv?.isControllerFullyVisible == true
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    if (player != null && !controllerVisible) {
                        if (player.isPlaying) player.pause() else player.play()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (pv != null && !controllerVisible) {
                        pv.showController()
                        return true
                    }
                }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    player?.let {
                        val dur = it.duration
                        val target = if (dur > 0) minOf(it.currentPosition + 90_000L, dur) else it.currentPosition + 90_000L
                        it.seekTo(target)
                        Toast.makeText(this, "Adelantado 1:30 min", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_GUIDE,
                KeyEvent.KEYCODE_TV_INPUT,
                KeyEvent.KEYCODE_INFO -> {
                    openMenuRef?.invoke()
                    return true
                }
                KeyEvent.KEYCODE_CAPTIONS -> {
                    // Quick toggle: cycle through enabled/disabled subtitles
                    // without opening the menu. Falls back to opening the menu
                    // if no text tracks are available.
                    openMenuRef?.invoke()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    player?.let {
                        it.seekTo(maxOf(it.currentPosition - 10_000L, 0L))
                        Toast.makeText(this, "Atrás 10 s", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                    if (controllerVisible) {
                        pv?.hideController()
                        return true
                    }
                    if (backPressedTime + 2000 > System.currentTimeMillis()) {
                        finish()
                        return true
                    }
                    Toast.makeText(this, "Presiona de nuevo para salir", Toast.LENGTH_SHORT).show()
                    backPressedTime = System.currentTimeMillis()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        exoPlayerRef = null
        playerViewRef = null
        openMenuRef = null
        super.onDestroy()
    }

    // B3: Picture-in-Picture. Two paths:
    //  - API 31+ (Android 12+): we register PictureInPictureParams with
    //    autoEnter=true. The system enters PiP automatically when the
    //    activity goes to background — this is the only path that works on
    //    Android TV, because Android TV does NOT call onUserLeaveHint on
    //    HOME (HOME is treated as "send to back", not "user leaving").
    //  - API 26–30: we fall back to enterPictureInPictureMode() inside
    //    onUserLeaveHint, which works on phones/tablets.
    // No-op when the device doesn't advertise the PiP system feature.
    fun refreshPipParams() = updatePipParams()

    // Explicit user-initiated PiP entry. Used by the player menu's "Picture-in-
    // Picture" item — works on any device that advertises the PiP feature,
    // regardless of whether autoEnter / onUserLeaveHint trigger correctly.
    // Returns true if PiP was successfully requested.
    fun enterPipNow(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Log.d(TAG, "enterPipNow: device has no PiP feature")
            return false
        }
        val player = exoPlayerRef ?: return false
        val w = player.videoSize.width.takeIf { it > 0 } ?: 16
        val h = player.videoSize.height.takeIf { it > 0 } ?: 9
        return runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(w, h))
                    .build()
            )
        }.onFailure { Log.w(TAG, "enterPictureInPictureMode failed", it) }
         .getOrDefault(false)
    }

    fun isPipSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Log.d(TAG, "updatePipParams: device has no PiP feature")
            return
        }
        val player = exoPlayerRef ?: return
        val w = player.videoSize.width.takeIf { it > 0 } ?: 16
        val h = player.videoSize.height.takeIf { it > 0 } ?: 9
        runCatching {
            val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(w, h))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(true)
            }
            setPictureInPictureParams(builder.build())
        }.onFailure { Log.w(TAG, "setPictureInPictureParams failed", it) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ handles this via autoEnter — don't double-trigger.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        val player = exoPlayerRef ?: return
        if (!player.isPlaying) return
        runCatching {
            val w = player.videoSize.width.takeIf { it > 0 } ?: 16
            val h = player.videoSize.height.takeIf { it > 0 } ?: 9
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(w, h))
                    .build()
            )
        }.onFailure { Log.w(TAG, "enterPictureInPictureMode failed", it) }
    }

    override fun onPictureInPictureModeChanged(isInPipMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPipMode, newConfig)
        Log.d(TAG, "onPictureInPictureModeChanged: isInPipMode=$isInPipMode")
        // Hide the controller in PiP — its buttons are too small to be useful
        // and conflict with the system's PiP gesture overlay.
        playerViewRef?.useController = !isInPipMode
    }
}

private sealed class ResumeDecision {
    data class Pending(val savedMs: Long) : ResumeDecision()
    data class Resume(val positionMs: Long) : ResumeDecision()
    object FromStart : ResumeDecision()
}

private sealed class MenuLevel {
    object Hidden : MenuLevel()
    object Main : MenuLevel()
    object Subtitles : MenuLevel()
    object Speed : MenuLevel()
    object Quality : MenuLevel()
}

// Iterates renderer indices of TEXT type. Used to enable/disable subtitles —
// DefaultTrackSelector requires renderer indices, not track types.
private inline fun forEachTextRenderer(player: ExoPlayer, block: (Int) -> Unit) {
    for (i in 0 until player.rendererCount) {
        if (player.getRendererType(i) == C.TRACK_TYPE_TEXT) block(i)
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    url: String,
    title: String,
    slug: String,
    episode: Int,
    cover: String,
    vm: MainViewModel,
    onPlayerReady: (ExoPlayer, PlayerView) -> Unit,
    onMenuReady: (() -> Unit) -> Unit,
    onExit: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var resumeDecision by remember { mutableStateOf<ResumeDecision?>(null) }
    var controlsVisible by remember { mutableStateOf(false) }
    var menuLevel by remember { mutableStateOf<MenuLevel>(MenuLevel.Hidden) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        onMenuReady { menuLevel = MenuLevel.Main }
    }

    val settings = remember { Settings(context) }
    val trackSelector = remember { DefaultTrackSelector(context) }
    var currentTracks by remember { mutableStateOf<Tracks?>(null) }

    val exoPlayer = remember {
        // Route all media HTTP through the repo's OkHttp client so the player
        // benefits from the same lenient trust manager (OCSP/revocation skip)
        // used for scraping. The default DefaultHttpDataSource (over
        // HttpsURLConnection) cannot handshake with Cloudflare on some Android
        // TVs because OCSP responses come back with stale validity intervals.
        // Browser-ish User-Agent is required: jkdesa/jkplayer CDN rejects the
        // default OkHttp UA.
        val httpFactory = OkHttpDataSource.Factory(vm.repository().httpClient())
            .setUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
            )
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpFactory)
        val activity = context as? PlayerActivity
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        errorMsg = "Error: ${error.message}"
                    }
                    override fun onTracksChanged(tracks: Tracks) {
                        currentTracks = tracks
                    }
                    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                        // B3: refresh PiP params with the real aspect ratio as
                        // soon as the first frame's dimensions are known. On
                        // API 31+ this also flips on autoEnter so the system
                        // can take us into PiP when the activity backgrounds.
                        activity?.refreshPipParams()
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) activity?.refreshPipParams()
                    }
                })
            }
    }

    // B1+B2: apply persisted preferences before prepare(). Defaults come from
    // Settings.Defaults so a fresh install still gets sane behaviour.
    LaunchedEffect(exoPlayer) {
        val subLang = settings.subtitleLang.first()
        val speed = settings.playbackSpeed.first()
        val maxHeight = settings.maxVideoHeight.first()
        trackSelector.parameters = trackSelector.buildUponParameters().apply {
            // Subtitles: "off" disables every text renderer; otherwise leave
            // them enabled and let the preferred language drive selection.
            val disable = subLang == "off"
            forEachTextRenderer(exoPlayer) { idx -> setRendererDisabled(idx, disable) }
            setPreferredTextLanguage(if (disable) null else subLang)
            // Quality: 0 = Auto (no cap). Otherwise cap by max height.
            if (maxHeight > 0) setMaxVideoSize(Int.MAX_VALUE, maxHeight)
            else clearVideoSizeConstraints()
        }.build()
        exoPlayer.playbackParameters = PlaybackParameters(speed)
    }

    // Decide whether to show the resume dialog before we touch the player.
    LaunchedEffect(slug, episode) {
        val history = vm.getProgress(slug, episode)
        val saved = history?.positionMs ?: 0L
        val dur = history?.durationMs ?: 0L
        val withinResumeWindow = dur <= 0L || saved < dur * 0.95
        resumeDecision = if (saved > 30_000L && withinResumeWindow) {
            ResumeDecision.Pending(saved)
        } else {
            ResumeDecision.FromStart
        }
    }

    // Prepare and start playback once we know where to start.
    LaunchedEffect(resumeDecision, url) {
        val startAt = when (val d = resumeDecision) {
            is ResumeDecision.Resume -> d.positionMs
            ResumeDecision.FromStart -> 0L
            else -> return@LaunchedEffect
        }
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        if (startAt > 0L) exoPlayer.seekTo(startAt)
        exoPlayer.playWhenReady = true
    }

    // Periodically persist progress while playing. Match the APK's 95% threshold
    // so a near-finished episode doesn't keep a stale resume entry.
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(10_000)
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration
            if (pos > 0 && dur > 0 && pos < dur * 0.95) {
                vm.saveProgress(slug, title, cover, episode, pos, dur)
            }
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(exoPlayer) {
        // Snapshot current playback position into Room. Called on lifecycle pause
        // and on dispose so we never lose progress to a missed 10s autosave tick.
        fun snapshotProgress() {
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration.coerceAtLeast(0L)
            if (pos > 0) vm.saveProgress(slug, title, cover, episode, pos, dur)
        }
        val activity = context as? PlayerActivity
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    snapshotProgress()
                    // Entering PiP also fires ON_PAUSE (the activity is only
                    // partially visible). Don't pause playback in that case —
                    // PiP is precisely meant to keep the video running while
                    // the user does something else.
                    if (activity?.isInPictureInPictureMode != true) exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> if (!exoPlayer.isPlaying && resumeDecision !is ResumeDecision.Pending) exoPlayer.play()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            snapshotProgress()
            exoPlayer.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    controllerAutoShow = false
                    controllerShowTimeoutMs = 4000
                    controllerHideOnTouch = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    keepScreenOn = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { v -> controlsVisible = v == View.VISIBLE }
                    )
                    requestFocus()
                    onPlayerReady(exoPlayer, this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Title overlay — non-focusable, only shown while controls are up so it
        // doesn't fight with the player for visibility.
        if (controlsVisible && title.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x88000000))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$title · Episodio $episode",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Skip hint — also non-focusable.
        if (controlsVisible) {
            Text(
                text = "▶▶ Salto 1:30 (FF) · ◀◀ 10 s (REW) · BACK para salir",
                color = Color(0xCCFFFFFF),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .background(Color(0x66000000))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        val pending = resumeDecision
        if (pending is ResumeDecision.Pending) {
            AlertDialog(
                onDismissRequest = { /* not cancelable */ },
                title = { androidx.compose.material3.Text("Continuar reproducción") },
                text = {
                    androidx.compose.material3.Text(
                        "¿Deseas continuar desde donde lo dejaste?\n\nPosición guardada: ${formatTime(pending.savedMs)}"
                    )
                },
                confirmButton = {
                    TextButton(onClick = { resumeDecision = ResumeDecision.Resume(pending.savedMs) }) {
                        androidx.compose.material3.Text("Continuar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { resumeDecision = ResumeDecision.FromStart }) {
                        androidx.compose.material3.Text("Desde el inicio")
                    }
                },
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            )
        }

        if (menuLevel != MenuLevel.Hidden) {
            PlayerMenu(
                level = menuLevel,
                tracks = currentTracks,
                player = exoPlayer,
                settings = settings,
                trackSelector = trackSelector,
                onClose = { menuLevel = MenuLevel.Hidden },
                onLevel = { menuLevel = it },
                scope = scope
            )
        }

        errorMsg?.let { msg ->
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(msg, color = Color.White, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onExit) {
                        androidx.compose.material3.Text("Volver", color = Color.White)
                    }
                }
            }
        }
    }
}

// PlayerMenu — single AlertDialog whose content swaps based on `level`. Driven
// by the player's MENU key (or CAPTIONS / INFO / GUIDE). Persists every choice
// to DataStore so it sticks across episodes; also applies it live.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerMenu(
    level: MenuLevel,
    tracks: Tracks?,
    player: ExoPlayer,
    settings: Settings,
    trackSelector: DefaultTrackSelector,
    onClose: () -> Unit,
    onLevel: (MenuLevel) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val activity = androidx.compose.ui.platform.LocalContext.current as? PlayerActivity
    val pipSupported = activity?.isPipSupported() == true
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            androidx.compose.material3.Text(
                when (level) {
                    MenuLevel.Subtitles -> "Subtítulos"
                    MenuLevel.Speed -> "Velocidad"
                    MenuLevel.Quality -> "Calidad"
                    else -> "Reproductor"
                }
            )
        },
        text = {
            when (level) {
                MenuLevel.Main -> Column {
                    MenuRow("Subtítulos") { onLevel(MenuLevel.Subtitles) }
                    MenuRow("Velocidad (${"%.2f".format(player.playbackParameters.speed)}×)") {
                        onLevel(MenuLevel.Speed)
                    }
                    MenuRow("Calidad") { onLevel(MenuLevel.Quality) }
                    if (pipSupported) {
                        MenuRow("◰ Picture-in-Picture") {
                            activity?.enterPipNow()
                            onClose()
                        }
                    }
                }
                MenuLevel.Subtitles -> Column {
                    MenuRow("Desactivados") {
                        scope.launch {
                            settings.setSubtitleLang("off")
                            trackSelector.parameters = trackSelector.buildUponParameters().apply {
                                forEachTextRenderer(player) { idx -> setRendererDisabled(idx, true) }
                                setPreferredTextLanguage(null)
                            }.build()
                            onClose()
                        }
                    }
                    MenuRow("Español") {
                        applySubLang(player, trackSelector, "es")
                        scope.launch { settings.setSubtitleLang("es"); onClose() }
                    }
                    MenuRow("Inglés") {
                        applySubLang(player, trackSelector, "en")
                        scope.launch { settings.setSubtitleLang("en"); onClose() }
                    }
                }
                MenuLevel.Speed -> Column {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { rate ->
                        MenuRow("${"%.2f".format(rate)}×") {
                            player.playbackParameters = PlaybackParameters(rate)
                            scope.launch { settings.setPlaybackSpeed(rate); onClose() }
                        }
                    }
                }
                MenuLevel.Quality -> Column {
                    val heights = videoHeights(tracks)
                    MenuRow("Auto") {
                        trackSelector.parameters = trackSelector.buildUponParameters()
                            .clearVideoSizeConstraints()
                            .build()
                        scope.launch { settings.setMaxVideoHeight(0); onClose() }
                    }
                    if (heights.isEmpty()) {
                        androidx.compose.material3.Text(
                            "Sin pistas disponibles aún",
                            color = Color(0xAAFFFFFF),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else heights.forEach { h ->
                        MenuRow("${h}p") {
                            trackSelector.parameters = trackSelector.buildUponParameters()
                                .setMaxVideoSize(Int.MAX_VALUE, h)
                                .build()
                            scope.launch { settings.setMaxVideoHeight(h); onClose() }
                        }
                    }
                }
                else -> {}
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                androidx.compose.material3.Text("Cerrar")
            }
        },
        dismissButton = if (level != MenuLevel.Main) {
            { TextButton(onClick = { onLevel(MenuLevel.Main) }) {
                androidx.compose.material3.Text("Atrás")
            } }
        } else null
    )
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        androidx.compose.material3.Text(label, color = Color.White)
    }
}

// Distinct video heights present in the current Tracks, sorted descending.
private fun videoHeights(tracks: Tracks?): List<Int> {
    if (tracks == null) return emptyList()
    val out = sortedSetOf<Int>(compareByDescending { it })
    tracks.groups.forEach { group ->
        if (group.type != C.TRACK_TYPE_VIDEO) return@forEach
        for (i in 0 until group.length) {
            val h = group.getTrackFormat(i).height
            if (h > 0) out.add(h)
        }
    }
    return out.toList()
}

private fun applySubLang(
    player: ExoPlayer,
    trackSelector: DefaultTrackSelector,
    lang: String
) {
    trackSelector.parameters = trackSelector.buildUponParameters().apply {
        forEachTextRenderer(player) { idx -> setRendererDisabled(idx, false) }
        setPreferredTextLanguage(lang)
    }.build()
}
