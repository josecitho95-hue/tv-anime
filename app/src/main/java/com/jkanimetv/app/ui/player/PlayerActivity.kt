package com.jkanimetv.app.ui.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import com.jkanimetv.app.viewmodel.MainViewModel
import kotlinx.coroutines.delay

class PlayerActivity : ComponentActivity() {

    // References owned by the composable; populated via onPlayerReady so
    // dispatchKeyEvent can drive the player without going through Compose focus.
    private var exoPlayerRef: ExoPlayer? = null
    private var playerViewRef: PlayerView? = null
    private var backPressedTime = 0L

    companion object {
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
            MaterialTheme(colorScheme = darkColorScheme()) {
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
        super.onDestroy()
    }
}

private sealed class ResumeDecision {
    data class Pending(val savedMs: Long) : ResumeDecision()
    data class Resume(val positionMs: Long) : ResumeDecision()
    object FromStart : ResumeDecision()
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
    onExit: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var resumeDecision by remember { mutableStateOf<ResumeDecision?>(null) }
    var controlsVisible by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    errorMsg = "Error: ${error.message}"
                }
            })
        }
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
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> if (!exoPlayer.isPlaying && resumeDecision !is ResumeDecision.Pending) exoPlayer.play()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
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
