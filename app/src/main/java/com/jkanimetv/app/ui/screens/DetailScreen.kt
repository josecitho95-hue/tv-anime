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

    LaunchedEffect(slug) { vm.loadDetail(slug) }

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
                    Text(anime.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))

                    if (anime.genre.isNotBlank()) InfoChip(anime.genre)
                    if (anime.status.isNotBlank()) InfoChip(anime.status)
                    if (anime.type.isNotBlank()) InfoChip(anime.type)
                    if (anime.year.isNotBlank()) InfoChip(anime.year)

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { vm.toggleFavorite(anime) },
                        colors = ButtonDefaults.colors(
                            containerColor = if (state.isFavorite) AccentRed else Color(0xFF2A2A4A),
                            focusedContainerColor = AccentRed
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.isFavorite) "♥ Favorito" else "♡ Añadir favorito", color = Color.White)
                    }

                    if (anime.synopsis.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Sinopsis", color = AccentRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(anime.synopsis, color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(16.dp)
                ) {
                    Text(
                        "${state.episodes.size} Episodios",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (state.episodes.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Sin episodios disponibles", color = TextSecondary)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(8),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.episodes.size) { idx ->
                                EpisodeButton(
                                    episode = state.episodes[idx],
                                    onClick = { onEpisodeClick(slug, state.episodes[idx].number) }
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
fun EpisodeButton(episode: Episode, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier
            .aspectRatio(1f)
            .shadow(
                elevation = if (focused) 10.dp else 0.dp,
                shape = RoundedCornerShape(6.dp),
                ambientColor = AccentRed,
                spotColor = AccentRed
            )
            .onFocusChanged { focused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(RoundedCornerShape(6.dp)),
        colors = CardDefaults.colors(
            containerColor = if (focused) AccentRed else CardBg,
            focusedContainerColor = AccentRed
        ),
        scale = CardDefaults.scale(focusedScale = 1.08f),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(4.dp)) {
            Text(
                text = "${episode.number}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun InfoChip(text: String) {
    Box(
        modifier = Modifier
            .padding(vertical = 2.dp)
            .background(Color(0xFF2A2A4A), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = TextSecondary, fontSize = 10.sp)
    }
}
