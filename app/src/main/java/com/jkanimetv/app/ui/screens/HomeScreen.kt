package com.jkanimetv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jkanimetv.app.data.Anime
import com.jkanimetv.app.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onAnimeClick: (Anime) -> Unit
) {
    val state by vm.home.collectAsState()
    val history by vm.history.collectAsState()
    val savedScroll by vm.homeScroll.collectAsState()

    // C4: scroll memory. Re-instantiate ScrollState with the saved offset so
    // the first frame after re-entering Home jumps to where we left off.
    val scrollState = rememberScrollState(initial = savedScroll)
    LaunchedEffect(scrollState.value) {
        vm.setHomeScroll(scrollState.value)
    }

    when {
        state.isLoading -> LoadingScreen()
        state.error != null -> ErrorScreen(state.error!!, onRetry = { vm.loadHome() })
        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBg)
                    .verticalScroll(scrollState)
                    .padding(bottom = 32.dp)
            ) {
                // Top bar — refresh button on the right, navigable by D-pad.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Spacer(Modifier.weight(1f))
                    RefreshPill(onClick = { vm.refreshHome() })
                }

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

@Composable
private fun RefreshPill(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (focused) AccentRed else CardBg)
            .then(if (focused) Modifier.border(2.dp, AccentRedSoft, RoundedCornerShape(20.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = "↻ Refrescar",
            color = if (focused) Color.White else TextPrimary,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}
