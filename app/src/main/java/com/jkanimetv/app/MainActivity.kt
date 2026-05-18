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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private data class SidebarTab(val route: String, val label: String, val icon: ImageVector)

private val SidebarTabs = listOf(
    SidebarTab("home",      "Inicio",    Icons.Filled.Home),
    SidebarTab("schedule",  "Horario",   Icons.Filled.DateRange),
    SidebarTab("browse",    "Explorar",  Icons.Filled.Explore),
    SidebarTab("search",    "Buscar",    Icons.Filled.Search),
    SidebarTab("favorites", "Favoritos", Icons.Filled.Favorite)
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppNavigation() {
    val vm: MainViewModel = viewModel()
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route?.substringBefore('/')

    fun navigateTo(route: String) {
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    NavigationDrawer(
        drawerContent = { drawerValue ->
            val expanded = drawerValue == DrawerValue.Open
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(SurfaceElevated)
                    .padding(vertical = 24.dp, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Brand header — "JK" colapsado / "JK Anime TV" expandido.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expanded) "JK Anime TV" else "JK",
                        color = AccentRed,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                    )
                }
                Spacer(Modifier.height(20.dp))

                SidebarTabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationDrawerItem(
                        selected = selected,
                        onClick = { navigateTo(tab.route) },
                        leadingContent = {
                            androidx.compose.material3.Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        )
                    }
                }
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize().background(DarkBg),
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
