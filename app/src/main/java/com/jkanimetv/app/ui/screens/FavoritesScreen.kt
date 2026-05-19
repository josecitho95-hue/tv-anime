package com.jkanimetv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.jkanimetv.app.data.Anime
import com.jkanimetv.app.data.Favorite
import com.jkanimetv.app.data.FavoriteStatus
import com.jkanimetv.app.viewmodel.MainViewModel

@Composable
fun FavoritesScreen(
    vm: MainViewModel,
    onAnimeClick: (Anime) -> Unit
) {
    val favorites by vm.favorites.collectAsState()
    var selectedTab by remember { mutableStateOf(TAB_ALL) }

    // Counts per status — drives both the tab labels and visibility.
    val counts: Map<String, Int> = remember(favorites) {
        favorites.groupingBy { it.status }.eachCount()
    }
    val filtered: List<Favorite> = remember(favorites, selectedTab) {
        if (selectedTab == TAB_ALL) favorites
        else favorites.filter { it.status == selectedTab }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(32.dp)
    ) {
        Text(
            text = "Favoritos",
            color = TextPrimary,
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabPill(
                label = "Todos (${favorites.size})",
                selected = selectedTab == TAB_ALL,
                onClick = { selectedTab = TAB_ALL }
            )
            FavoriteStatus.ALL.forEach { status ->
                TabPill(
                    label = "${FavoriteStatus.label(status)} (${counts[status] ?: 0})",
                    selected = selectedTab == status,
                    onClick = { selectedTab = status }
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (favorites.isEmpty()) "Aún no tienes favoritos"
                    else "Sin animes en esta colección",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered.size) { idx ->
                    val fav = filtered[idx]
                    val anime = Anime(slug = fav.slug, title = fav.title, coverUrl = fav.coverUrl)
                    AnimeCard(anime = anime, onClick = { onAnimeClick(anime) })
                }
            }
        }
    }
}

private const val TAB_ALL = "__all__"

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        selected -> AccentRed
        focused -> CardBgHover
        else -> CardBg
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .then(if (focused && !selected) Modifier.border(2.dp, AccentRed, RoundedCornerShape(20.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else TextPrimary,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        )
    }
}
