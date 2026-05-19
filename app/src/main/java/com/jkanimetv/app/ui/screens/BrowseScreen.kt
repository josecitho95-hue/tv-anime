package com.jkanimetv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.jkanimetv.app.data.Anime
import com.jkanimetv.app.viewmodel.MainViewModel

// jkanime expects the English-capitalized values from the JSON `type` field;
// lowercase Spanish values get silently ignored by the directory endpoint.
private val TYPES = listOf(
    "" to "Todos",
    "TV" to "TV",
    "OVA" to "OVA",
    "Movie" to "Película",
    "Special" to "Especial",
    "ONA" to "ONA"
)

private val STATUSES = listOf(
    "" to "Todos",
    "2" to "En emisión",
    "1" to "Finalizado"
)

private val GENRES = listOf(
    "" to "Todos",
    "accion" to "Acción",
    "aventura" to "Aventura",
    "comedia" to "Comedia",
    "drama" to "Drama",
    "fantasia" to "Fantasía",
    "romance" to "Romance",
    "ciencia-ficcion" to "Ciencia Ficción",
    "misterio" to "Misterio",
    "terror" to "Terror",
    "sobrenatural" to "Sobrenatural",
    "deportes" to "Deportes",
    "ecchi" to "Ecchi",
    "slice-of-life" to "Slice of Life"
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BrowseScreen(
    vm: MainViewModel,
    onAnimeClick: (Anime) -> Unit
) {
    val items by vm.directory.collectAsState()
    val filter by vm.directoryFilter.collectAsState()
    val loading by vm.directoryLoading.collectAsState()
    val end by vm.directoryEnd.collectAsState()

    LaunchedEffect(Unit) {
        if (items.isEmpty() && !loading) vm.loadDirectory(1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Spacer(Modifier.height(12.dp))

        FilterRow(label = "Tipo", options = TYPES, selected = filter.type) { value ->
            vm.setDirectoryFilter(genre = filter.genre, type = value, status = filter.status)
        }
        FilterRow(label = "Estado", options = STATUSES, selected = filter.status) { value ->
            vm.setDirectoryFilter(genre = filter.genre, type = filter.type, status = value)
        }
        FilterRow(label = "Género", options = GENRES, selected = filter.genre) { value ->
            vm.setDirectoryFilter(genre = value, type = filter.type, status = filter.status)
        }

        Spacer(Modifier.height(8.dp))

        when {
            items.isEmpty() && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentRed)
            }
            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Sin resultados para este filtro",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            else -> {
                // C4: restore scroll position when re-entering Browse. The grid
                // state is local but we seed it from the VM's stored values.
                val savedIdx by vm.browseScrollIndex.collectAsState()
                val savedOff by vm.browseScrollOffset.collectAsState()
                val gridState = rememberLazyGridState(
                    initialFirstVisibleItemIndex = savedIdx,
                    initialFirstVisibleItemScrollOffset = savedOff
                )
                LaunchedEffect(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) {
                    vm.setBrowseScroll(
                        gridState.firstVisibleItemIndex,
                        gridState.firstVisibleItemScrollOffset
                    )
                }
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        last >= items.size - 14
                    }
                }
                LaunchedEffect(shouldLoadMore, items.size) {
                    if (shouldLoadMore && !loading && !end) {
                        val nextPage = (items.size / 24) + 1
                        vm.loadDirectory(nextPage)
                    }
                }
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(7),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(items.size) { idx ->
                        AnimeCard(anime = items[idx], onClick = { onAnimeClick(items[idx]) })
                    }
                    if (loading) {
                        item {
                            Box(Modifier.height(120.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = AccentRed)
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
private fun FilterRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$label:",
            color = TextSecondary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(56.dp)
        )
        val listState = rememberLazyListState()
        // When the active selection of this filter row is reset to "Todos"
        // (selected == ""), snap the LazyRow back to the start so the first
        // chip isn't half-visible due to a focus-driven scroll offset.
        LaunchedEffect(selected) {
            if (selected.isEmpty()) listState.animateScrollToItem(0)
        }
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(options.size) { idx ->
                val (value, display) = options[idx]
                FilterChip(value = value, label = display, selected = selected == value, onSelect = onSelect)
            }
        }
    }
}

@Composable
private fun FilterChip(value: String, label: String, selected: Boolean, onSelect: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        selected -> AccentRed
        focused -> Color.White.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val textColor = when {
        selected -> Color.White
        focused -> Color.White
        else -> TextSecondary
    }
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clickable { onSelect(value) }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        )
    }
}
