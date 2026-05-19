package com.jkanimetv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.jkanimetv.app.BuildConfig
import com.jkanimetv.app.data.Settings
import com.jkanimetv.app.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { Settings(ctx) }
    val scope = rememberCoroutineScope()

    val subLang by settings.subtitleLang.collectAsState(initial = Settings.Defaults.SUBTITLE_LANG)
    val speed by settings.playbackSpeed.collectAsState(initial = Settings.Defaults.PLAYBACK_SPEED)
    val maxH by settings.maxVideoHeight.collectAsState(initial = Settings.Defaults.MAX_VIDEO_HEIGHT)

    // Cache size — recomputed when the user clears the cache so the row updates.
    var cacheTick by remember { mutableIntStateOf(0) }
    val cacheBytes = remember(cacheTick) { vm.repository().httpCacheSizeBytes() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Ajustes",
            color = TextPrimary,
            style = MaterialTheme.typography.headlineLarge
        )

        SettingsSection("Reproducción") {
            ChoiceRow(
                label = "Idioma de subtítulos",
                options = listOf("es" to "Español", "en" to "Inglés", "off" to "Desactivado"),
                selected = subLang,
                onSelect = { scope.launch { settings.setSubtitleLang(it) } }
            )
            ChoiceRow(
                label = "Velocidad por defecto",
                options = listOf(
                    0.75f to "0.75×", 1.0f to "1.0×", 1.25f to "1.25×",
                    1.5f to "1.5×", 2.0f to "2.0×"
                ),
                selected = speed,
                onSelect = { scope.launch { settings.setPlaybackSpeed(it) } }
            )
            ChoiceRow(
                label = "Calidad máxima",
                options = listOf(
                    0 to "Auto", 480 to "480p", 720 to "720p", 1080 to "1080p"
                ),
                selected = maxH,
                onSelect = { scope.launch { settings.setMaxVideoHeight(it) } }
            )
        }

        SettingsSection("Almacenamiento") {
            ActionRow(
                label = "Caché HTTP",
                subtitle = formatBytes(cacheBytes),
                actionLabel = "Limpiar",
                onAction = {
                    vm.repository().clearHttpCache()
                    cacheTick++
                }
            )
        }

        SettingsSection("Acerca de") {
            InfoRow("Versión", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            InfoRow("Aplicación", "JKAnime TV — cliente no oficial")
            InfoRow("Fuente", "jkanime.net (scraping)")
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            color = AccentRed,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceElevated, RoundedCornerShape(12.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, display) ->
                ChoicePill(
                    label = display,
                    selected = value == selected,
                    onClick = { onSelect(value) }
                )
            }
        }
    }
}

@Composable
private fun ChoicePill(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        selected -> AccentRed
        focused -> CardBgHover
        else -> CardBg
    }
    val border = if (focused && !selected) 2.dp else 0.dp
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .then(if (border > 0.dp) Modifier.border(border, AccentRed, RoundedCornerShape(20.dp)) else Modifier)
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ActionRow(label: String, subtitle: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = onAction,
            colors = ButtonDefaults.colors(
                containerColor = AccentRed,
                focusedContainerColor = AccentRedSoft
            )
        ) {
            Text(actionLabel, color = Color.White)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f))
        Text(value, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var b = bytes.toDouble()
    var i = 0
    while (b >= 1024 && i < units.lastIndex) { b /= 1024; i++ }
    return "%.1f %s".format(b, units[i])
}
