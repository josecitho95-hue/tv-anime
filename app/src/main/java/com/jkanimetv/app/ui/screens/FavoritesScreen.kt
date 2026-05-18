package com.jkanimetv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jkanimetv.app.data.Anime
import com.jkanimetv.app.viewmodel.MainViewModel

@Composable
fun FavoritesScreen(
    vm: MainViewModel,
    onAnimeClick: (Anime) -> Unit
) {
    val favorites by vm.favorites.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(32.dp)
    ) {
        Text(
            "Favoritos",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        if (favorites.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aún no tienes favoritos", color = TextSecondary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(favorites.size) { idx ->
                    val fav = favorites[idx]
                    AnimeCard(
                        anime = Anime(slug = fav.slug, title = fav.title, coverUrl = fav.coverUrl),
                        onClick = { onAnimeClick(Anime(slug = fav.slug, title = fav.title, coverUrl = fav.coverUrl)) }
                    )
                }
            }
        }
    }
}
