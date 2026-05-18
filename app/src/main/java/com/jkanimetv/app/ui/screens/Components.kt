package com.jkanimetv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.jkanimetv.app.data.Anime

val DarkBg = Color(0xFF0D0D1A)
val CardBg = Color(0xFF1A1A2E)
val AccentRed = Color(0xFFE94560)
val TextPrimary = Color(0xFFE0E0E0)
val TextSecondary = Color(0xFF9E9E9E)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AnimeCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Int = 140,
    height: Int = 200
) {
    var focused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .width(width.dp)
            .height(height.dp)
            .shadow(
                elevation = if (focused) 12.dp else 0.dp,
                shape = RoundedCornerShape(8.dp),
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Color.White.copy(alpha = 0.55f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            ),
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
        colors = CardDefaults.colors(containerColor = CardBg),
        scale = CardDefaults.scale(focusedScale = 1.06f),
    ) {
        Box {
            AsyncImage(
                model = anime.coverUrl,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Always-on gradient so titles stay legible; intensifies when focused.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (focused) 80.dp else 60.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(if (focused) 0xE6000000 else 0xCC000000))
                        )
                    )
            )
            Text(
                text = anime.title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
            )
        }
    }
}

@Composable
fun AnimeRow(
    title: String,
    animes: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    titleColor: Color = TextPrimary
) {
    if (animes.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = titleColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(animes.size) { idx ->
                AnimeCard(anime = animes[idx], onClick = { onAnimeClick(animes[idx]) })
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(DarkBg),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = AccentRed)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(DarkBg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = message, color = TextPrimary, fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Reintentar")
            }
        }
    }
}
