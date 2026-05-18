package com.jkanimetv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.jkanimetv.app.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            @OptIn(ExperimentalTvMaterial3Api::class)
            MaterialTheme(colorScheme = darkColorScheme()) {
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
                .background(Color(0xFF0A0A1A))
                .padding(horizontal = 32.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "JKAnime TV",
                color = AccentRed,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(end = 16.dp)
            )
            tabs.forEach { (route, label) ->
                val selected = currentRoute == route
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) AccentRed else Color.Transparent)
                        .clickable {
                            navController.navigate(route) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = label,
                        color = if (selected) Color.White else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.weight(1f)
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
                                Text("Cargando video...", color = Color.White)
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
                                    videoUrlState.error ?: "Error desconocido",
                                    color = Color.White,
                                    fontSize = 16.sp
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
