package com.jkanimetv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            @OptIn(ExperimentalTvMaterial3Api::class)
            MaterialTheme(colorScheme = darkColorScheme(), typography = TvTypography) {
                AppNavigation()
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppNavigation() {
    val vm: MainViewModel = viewModel()
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route?.substringBefore('/')

    val tabs = listOf(
        "home" to "Inicio",
        "schedule" to "Horario",
        "browse" to "Explorar",
        "search" to "Buscar",
        "favorites" to "Favoritos"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceElevated)
                .padding(horizontal = 32.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "JKAnime TV",
                color = AccentRed,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(end = 24.dp)
            )
            val selectedIdx = tabs.indexOfFirst { it.first == currentRoute }.coerceAtLeast(0)
            // Track which tab the D-pad is focused on so the pill follows
            // the cursor instead of waiting for OK to confirm.
            var focusedTabIndex by remember { mutableStateOf(selectedIdx) }
            TabRow(
                selectedTabIndex = selectedIdx,
                indicator = { tabPositions, doesHaveFocus ->
                    val indicatorIdx = (if (doesHaveFocus) focusedTabIndex else selectedIdx)
                        .coerceIn(0, tabPositions.lastIndex.coerceAtLeast(0))
                    if (indicatorIdx in tabPositions.indices) {
                        TabRowDefaults.PillIndicator(
                            currentTabPosition = tabPositions[indicatorIdx],
                            doesTabRowHaveFocus = doesHaveFocus,
                            activeColor = AccentRed,
                            inactiveColor = AccentRed.copy(alpha = 0.6f)
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { idx, (route, label) ->
                    val isSelected = idx == selectedIdx
                    Tab(
                        selected = isSelected,
                        // Focus = navigate: as soon as the D-pad lands on a tab,
                        // jump to that screen. saveState/restoreState makes it
                        // cheap because previously-visited tabs restore in ms.
                        onFocus = {
                            focusedTabIndex = idx
                            if (currentRoute != route) {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        onClick = { /* navigation already happened on focus */ }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.weight(1f),
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
