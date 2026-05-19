package com.jkanimetv.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jkanimetv.app.work.EpisodeCheckWorker
import java.util.concurrent.TimeUnit
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.*
import com.jkanimetv.app.ui.player.PlayerActivity
import com.jkanimetv.app.ui.screens.*
import com.jkanimetv.app.ui.theme.TvTypography
import com.jkanimetv.app.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored — silent if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleEpisodeCheck()
        maybeRequestNotificationPermission()

        // Deep-link from a "new episode" notification: extract the slug now
        // (the composable reads it via savedInstance) and pass into nav.
        val openSlug = intent?.getStringExtra(EpisodeCheckWorker.EXTRA_OPEN_ANIME_SLUG)

        setContent {
            @OptIn(ExperimentalTvMaterial3Api::class)
            MaterialTheme(colorScheme = darkColorScheme(), typography = TvTypography) {
                AppNavigation(initialAnimeSlug = openSlug)
            }
        }
    }

    // singleTop activity: new intents come here, not through onCreate. Forward
    // the deep-link slug to the running composable via a static channel so it
    // can navigate to detail without a full restart.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val slug = intent.getStringExtra(EpisodeCheckWorker.EXTRA_OPEN_ANIME_SLUG) ?: return
        pendingDeepLinkSlug = slug
    }

    private fun scheduleEpisodeCheck() {
        val request = PeriodicWorkRequestBuilder<EpisodeCheckWorker>(6, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            EpisodeCheckWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        // Updated by onNewIntent; the composable observes this in a LaunchedEffect
        // and clears it after navigating. Static is fine because there's only
        // ever one MainActivity instance (singleTop).
        @Volatile var pendingDeepLinkSlug: String? = null
    }
}

private data class SidebarTab(val route: String, val label: String, val icon: ImageVector)

private val SidebarTabs = listOf(
    SidebarTab("home",      "Inicio",    Icons.Filled.Home),
    SidebarTab("schedule",  "Horario",   Icons.Filled.DateRange),
    SidebarTab("browse",    "Explorar",  Icons.Filled.Explore),
    SidebarTab("search",    "Buscar",    Icons.Filled.Search),
    SidebarTab("favorites", "Favoritos", Icons.Filled.Favorite),
    SidebarTab("settings",  "Ajustes",   Icons.Filled.Settings)
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppNavigation(initialAnimeSlug: String? = null) {
    val vm: MainViewModel = viewModel()
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route?.substringBefore('/')

    // Initial deep-link from a notification's PendingIntent.
    LaunchedEffect(initialAnimeSlug) {
        if (!initialAnimeSlug.isNullOrBlank()) {
            vm.selectAnime(initialAnimeSlug)
            navController.navigate("detail") { launchSingleTop = true }
        }
    }
    // Subsequent deep-links delivered via onNewIntent — poll once per recomposition.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.flow.flow {
            while (true) { emit(MainActivity.pendingDeepLinkSlug); kotlinx.coroutines.delay(300) }
        }.collect { slug ->
            if (!slug.isNullOrBlank()) {
                MainActivity.pendingDeepLinkSlug = null
                vm.selectAnime(slug)
                navController.navigate("detail") { launchSingleTop = true }
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        // Sidebar permanente — 200 dp, sin animaciones, sin tricks de foco.
        Column(
            modifier = Modifier
                .width(200.dp)
                .fillMaxHeight()
                .background(SurfaceElevated)
                .padding(vertical = 24.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "JK Anime TV",
                color = AccentRed,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(20.dp))
            SidebarTabs.forEach { tab ->
                val selected = currentRoute == tab.route
                SidebarItem(
                    tab = tab,
                    selected = selected,
                    onClick = {
                        if (currentRoute != tab.route) {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }

        // Content
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.weight(1f).fillMaxHeight(),
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) +
                    fadeIn(tween(250))
            },
            exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) +
                    fadeOut(tween(250))
            },
            popEnterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) +
                    fadeIn(tween(250))
            },
            popExitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) +
                    fadeOut(tween(250))
            }
        ) {
            composable("home") {
                HomeScreen(vm = vm, onAnimeClick = { anime ->
                    vm.selectAnime(anime.slug); navController.navigate("detail") { launchSingleTop = true }
                })
            }
            composable("schedule") {
                ScheduleScreen(vm = vm, onAnimeClick = { anime ->
                    vm.selectAnime(anime.slug); navController.navigate("detail") { launchSingleTop = true }
                })
            }
            composable("browse") {
                BrowseScreen(vm = vm, onAnimeClick = { anime ->
                    vm.selectAnime(anime.slug); navController.navigate("detail") { launchSingleTop = true }
                })
            }
            composable("search") {
                SearchScreen(vm = vm, onAnimeClick = { anime ->
                    vm.selectAnime(anime.slug); navController.navigate("detail") { launchSingleTop = true }
                })
            }
            composable("favorites") {
                FavoritesScreen(vm = vm, onAnimeClick = { anime ->
                    vm.selectAnime(anime.slug); navController.navigate("detail") { launchSingleTop = true }
                })
            }
            composable("settings") {
                SettingsScreen(vm = vm)
            }
            composable("detail") {
                val slug by vm.currentAnimeUrl.collectAsState()
                val videoUrlState by vm.videoUrl.collectAsState()
                val detailState by vm.detail.collectAsState()
                val context = LocalContext.current

                LaunchedEffect(videoUrlState.url) {
                    val url = videoUrlState.url ?: return@LaunchedEffect
                    if (url.isNotEmpty()) {
                        val anime = detailState.anime
                        PlayerActivity.start(
                            ctx = context,
                            url = url,
                            isHls = url.contains(".m3u8"),
                            title = anime?.title ?: slug,
                            slug = slug,
                            episode = videoUrlState.episode,
                            cover = anime?.coverUrl ?: ""
                        )
                        vm.clearVideoUrl()
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    DetailScreen(
                        slug = slug,
                        vm = vm,
                        onEpisodeClick = { animeUrl, ep -> vm.loadVideoUrl(animeUrl, ep) },
                        onBack = { navController.popBackStack() }
                    )

                    if (videoUrlState.isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x99000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = AccentRed)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Cargando video...",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }

                    if (videoUrlState.error != null && !videoUrlState.isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x99000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = videoUrlState.error ?: "Error desconocido",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = { vm.clearVideoUrl() }) { Text("Cerrar") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(tab: SidebarTab, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        selected -> AccentRed
        focused -> Color.White.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val contentColor = when {
        selected -> Color.White
        focused -> Color.White
        else -> TextSecondary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = tab.label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        )
    }
}
