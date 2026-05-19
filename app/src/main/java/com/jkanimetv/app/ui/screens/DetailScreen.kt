package com.jkanimetv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import com.jkanimetv.app.data.FavoriteStatus
import com.jkanimetv.app.data.WatchHistory
import com.jkanimetv.app.viewmodel.MainViewModel
import kotlinx.coroutines.launch

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

                    // D1: collection selector. Visible whenever there's a status
                    // to show (after the favorite is created). Tapping a pill
                    // also creates the favorite if it doesn't exist yet.
                    var currentStatus by remember(anime.slug, state.isFavorite) { mutableStateOf<String?>(null) }
                    LaunchedEffect(anime.slug, state.isFavorite) {
                        currentStatus = vm.getFavoriteStatus(anime.slug)
                    }
                    if (state.isFavorite || currentStatus != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Colección",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FavoriteStatus.ALL.forEach { st ->
                                StatusPill(
                                    label = FavoriteStatus.label(st),
                                    selected = currentStatus == st,
                                    onClick = {
                                        vm.setFavoriteStatus(anime, st)
                                        currentStatus = st
                                    }
                                )
                            }
                        }
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
                    // D2: filter + jump-to-progress for long series.
                    var epFilter by remember { mutableStateOf(EpisodeFilter.ALL) }
                    val gridState = rememberLazyGridState()
                    val scope = rememberCoroutineScope()

                    val visibleEpisodes = remember(state.episodes, progress, epFilter) {
                        filterEpisodes(state.episodes, progress, epFilter)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = "${visibleEpisodes.size} / ${state.episodes.size} Episodios",
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.weight(1f))
                        // Jump to the first in-progress episode. Hidden when
                        // nothing is in progress so the row stays clean.
                        val resumeIdx = remember(state.episodes, progress) {
                            findResumeIndex(state.episodes, progress)
                        }
                        if (resumeIdx >= 0) {
                            EpisodeActionPill(
                                label = "▶ Continuar",
                                onClick = {
                                    epFilter = EpisodeFilter.ALL
                                    scope.launch { gridState.animateScrollToItem(resumeIdx) }
                                }
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        EpisodeFilter.entries.forEach { f ->
                            EpisodeFilterPill(
                                label = f.label,
                                selected = epFilter == f,
                                onClick = { epFilter = f }
                            )
                        }
                    }

                    if (state.episodes.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Sin episodios disponibles",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else if (visibleEpisodes.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Sin episodios en este filtro",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(8),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(visibleEpisodes.size) { idx ->
                                val ep = visibleEpisodes[idx]
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

// D1 — collection status pill inside the detail side panel. Full-width so it
// stacks vertically without truncating long labels like "Completado".
@Composable
private fun StatusPill(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        selected -> AccentRed
        focused -> CardBgHover
        else -> CardBg
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .then(if (focused && !selected) Modifier.border(2.dp, AccentRed, RoundedCornerShape(16.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else TextPrimary,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        )
    }
}

// D2 — episode filter enum. Order matters: tabs render in declaration order.
private enum class EpisodeFilter(val label: String) {
    ALL("Todos"),
    UNWATCHED("No vistos"),
    IN_PROGRESS("En progreso"),
    COMPLETED("Completados")
}

private fun filterEpisodes(
    episodes: List<Episode>,
    progress: Map<Int, WatchHistory>,
    filter: EpisodeFilter
): List<Episode> {
    if (filter == EpisodeFilter.ALL) return episodes
    return episodes.filter { ep ->
        val p = progress[ep.number]
        when (filter) {
            EpisodeFilter.UNWATCHED -> p == null || p.positionMs <= 5_000L
            EpisodeFilter.IN_PROGRESS -> p != null && p.positionMs > 5_000L &&
                (p.durationMs <= 0 || p.positionMs < p.durationMs * 0.95)
            EpisodeFilter.COMPLETED -> p != null && p.durationMs > 0 &&
                p.positionMs >= p.durationMs * 0.95
            EpisodeFilter.ALL -> true
        }
    }
}

// Index in the unfiltered list of the first in-progress episode, or -1.
private fun findResumeIndex(episodes: List<Episode>, progress: Map<Int, WatchHistory>): Int {
    return episodes.indexOfFirst { ep ->
        val p = progress[ep.number] ?: return@indexOfFirst false
        p.positionMs > 5_000L && (p.durationMs <= 0 || p.positionMs < p.durationMs * 0.95)
    }
}

@Composable
private fun EpisodeFilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        selected -> AccentRed
        focused -> CardBgHover
        else -> CardBg
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .then(if (focused && !selected) Modifier.border(2.dp, AccentRed, RoundedCornerShape(14.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else TextPrimary,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        )
    }
}

@Composable
private fun EpisodeActionPill(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) AccentRedSoft else AccentRed)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}
