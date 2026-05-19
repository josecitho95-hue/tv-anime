package com.jkanimetv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.jkanimetv.app.data.Anime
import com.jkanimetv.app.viewmodel.MainViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    vm: MainViewModel,
    onAnimeClick: (Anime) -> Unit
) {
    val state by vm.search.collectAsState()
    val history by vm.searchHistory.collectAsState()
    var query by remember { mutableStateOf("") }
    var fieldFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    fun doSearch() {
        if (query.isNotBlank()) vm.search(query)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(32.dp)
    ) {
        Text(
            text = "Buscar anime",
            color = TextPrimary,
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(CardBg, RoundedCornerShape(8.dp))
                    .border(
                        width = if (fieldFocused) 2.dp else 0.dp,
                        color = AccentRed,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Escribe el nombre del anime…",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        // Live search — VM debounces by 350 ms before hitting
                        // the network, so this is cheap even on every keystroke.
                        vm.search(it)
                    },
                    textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
                    cursorBrush = SolidColor(AccentRed),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { fieldFocused = it.isFocused || it.hasFocus }
                )
            }

            Button(
                onClick = { doSearch() },
                colors = ButtonDefaults.colors(
                    containerColor = AccentRed,
                    focusedContainerColor = Color(0xFFFF6B6B)
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    text = "Buscar",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        // C3: recent searches chips. Only visible when the user hasn't typed
        // anything — we don't want to compete with live results.
        if (query.isBlank() && history.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Búsquedas recientes",
                color = TextSecondary,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                history.take(6).forEach { q ->
                    HistoryChip(
                        label = q,
                        onClick = {
                            query = q
                            vm.search(q)
                        },
                        onRemove = { vm.deleteSearchHistoryItem(q) }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        when {
            state.isLoading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentRed)
            }
            state.error != null -> Text(
                text = state.error!!,
                color = AccentRed,
                style = MaterialTheme.typography.bodyMedium
            )
            state.results.isEmpty() && state.query.isNotBlank() && !state.isLoading -> Box(
                Modifier.fillMaxWidth(), contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sin resultados para \"${state.query}\"",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            state.results.isNotEmpty() -> LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.results.size) { idx ->
                    AnimeCard(anime = state.results[idx], onClick = { onAnimeClick(state.results[idx]) })
                }
            }
        }
    }
}

// Compact recent-search chip with an inline "×" to forget the query. Pressing
// the chip body re-runs the search; pressing × removes it.
@Composable
private fun HistoryChip(label: String, onClick: () -> Unit, onRemove: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (focused) CardBgHover else CardBg)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Text(label, color = TextPrimary, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Text("×", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
