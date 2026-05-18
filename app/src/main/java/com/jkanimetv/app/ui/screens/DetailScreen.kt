package com.jkanimetv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.jkanimetv.app.data.Anime
import com.jkanimetv.app.data.Episode
import com.jkanimetv.app.data.WatchHistory
import com.jkanimetv.app.viewmodel.MainViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailScreen(
    slug: String,
    vm: MainViewModel,
    onEpisodeClick: (slug: String, episode: Int) -> Unit,
    onBack: () -> Unit
) {
    val state by vm.detail.collectAsState()
    val progress by vm.episodeProgress.collectAsState()

    LaunchedEffect(slug) { vm.loadDetail(slug) }
    // Re-sync watched indicators when returning from the player.
    androidx.compose.runtime.LaunchedEffect(slug, state.episodes.size) {
        if (slug.isNotBlank() && state.episodes.isNotEmpty()) vm.refreshEpisodeProgress(slug)
    }

    when {
        state.isLoading -> LoadingScreen()
        state.anime == null -> ErrorScreen(state.error ?: "No se pudo cargar el anime", onRetry = { vm.loadDetail(slug) })
        else -> {
            val anime = state.anime!!
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBg)
            ) {
                Column(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                        .background(CardBg)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = anime.coverUrl,
                        contentDescription = anime.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(200.dp)
                            .height(280.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = anime.title,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))

                    if (anime.genre.isNotBlank()) InfoChip(anime.genre)
                    if (anime.status.isNotBlank()) InfoChip(anime.status)
                    if (anime.type.isNotBlank()) InfoChip(anime.type)
                    if (anime.year.isNotBlank()) InfoChip(anime.year)

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { vm.toggleFavorite(anime) },
                        colors = ButtonDefaults.colors(
                            containerColor = if (state.isFavorite) AccentRed else CardBgHover,
                            focusedContainerColor = AccentRed
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (state.isFavorite) "♥ Favorito" else "♡ Añadir favorito",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    if (anime.synopsis.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Sinopsis",
                            color = AccentRed,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = anime.synopsis,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "${state.episodes.size} Episodios",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (state.episodes.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Sin episodios disponibles",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(8),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.episodes.size) { idx ->
                                val ep = state.episodes[idx]
                                EpisodeButton(
                                    episode = ep,
                                    progress = progress[ep.number],
                                    onClick = { onEpisodeClick(slug, ep.number) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpisodeButton(episode: Episode, progress: WatchHistory?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val completed = progress != null && progress.durationMs > 0 &&
        progress.positionMs >= progress.durationMs * 0.95
    // Lower threshold (5s instead of 30s) so a quick visit still marks
    // the episode as "started". Also handle the case where duration was
    // unknown when saving — show a fixed 30% bar so the user still sees
    // some visual marker.
    val inProgress = !completed && progress != null && progress.positionMs > 5_000L
    val progressFraction = when {
        !inProgress -> 0f
        progress!!.durationMs > 0 ->
            (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0.05f, 1f)
        else -> 0.30f
    }
    Card(
        onClick = onClick,
        modifier = Modifier
            .aspectRatio(1f)
            .shadow(
                elevation = if (focused) 10.dp else 0.dp,
                shape = RoundedCornerShape(6.dp),
                ambientColor = AccentRedSoft,
                spotColor = AccentRedSoft
            )
            .onFocusChanged { focused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(RoundedCornerShape(6.dp)),
        colors = CardDefaults.colors(
            containerColor = when {
                focused -> AccentRed
                completed -> Color(0xFF1F3A24)  // verde muy oscuro de fondo cuando completado
                else -> CardBg
            },
            focusedContainerColor = AccentRed
        ),
        border = if (completed && !focused) {
            CardDefaults.border(border = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.5.dp, SuccessGreen),
                shape = RoundedCornerShape(6.dp)
            ))
        } else CardDefaults.border(),
        scale = CardDefaults.scale(focusedScale = 1.08f),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(4.dp)) {
                Text(
                    text = "${episode.number}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            // Completed: corner check mark
            if (completed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(14.dp)
                        .background(SuccessGreen, RoundedCornerShape(50))
                ) {
                    Text(
                        text = "✓",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            // In progress: bottom progress bar
            if (inProgress) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progressFraction)
                        .height(3.dp)
                        .background(AccentRedSoft)
                )
            }
        }
    }
}

@Composable
fun InfoChip(text: String) {
    Box(
        modifier = Modifier
            .padding(vertical = 2.dp)
            .background(CardBgHover, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = text, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}
