package com.jkanimetv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jkanimetv.app.data.Anime
import com.jkanimetv.app.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onAnimeClick: (Anime) -> Unit
) {
    val state by vm.home.collectAsState()
    val history by vm.history.collectAsState()

    when {
        state.isLoading -> LoadingScreen()
        state.error != null -> ErrorScreen(state.error!!, onRetry = { vm.loadHome() })
        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBg)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                if (history.isNotEmpty()) {
                    AnimeRow(
                        title = "Continuar viendo",
                        animes = history.map { h ->
                            Anime(
                                slug = h.animeSlug,
                                title = "${h.animeTitle} · Ep ${h.episodeNumber}",
                                coverUrl = h.animeCover
                            )
                        },
                        onAnimeClick = onAnimeClick
                    )
                }

                if (state.topAnime.isNotEmpty()) {
                    AnimeRow("Top Anime", state.topAnime, onAnimeClick, titleColor = AccentRed)
                }

                state.sections.forEach { section ->
                    if (section.animes.isNotEmpty()) {
                        AnimeRow(section.title, section.animes, onAnimeClick)
                    }
                }
            }
        }
    }
}
