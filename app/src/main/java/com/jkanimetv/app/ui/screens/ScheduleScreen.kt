package com.jkanimetv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.jkanimetv.app.data.Anime
import com.jkanimetv.app.viewmodel.MainViewModel
import java.util.Calendar

@Composable
fun ScheduleScreen(
    vm: MainViewModel,
    onAnimeClick: (Anime) -> Unit
) {
    val state by vm.schedule.collectAsState()

    val today = remember {
        val cal = Calendar.getInstance()
        val days = listOf("Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")
        days[cal.get(Calendar.DAY_OF_WEEK) - 1]
    }

    LaunchedEffect(Unit) {
        if (state.days.isEmpty() && !state.isLoading) vm.loadSchedule()
    }

    when {
        state.isLoading -> LoadingScreen()
        state.error != null -> ErrorScreen(state.error!!, onRetry = { vm.loadSchedule() })
        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBg)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                state.days.forEach { day ->
                    if (day.animes.isNotEmpty()) {
                        val isToday = day.dayName == today
                        AnimeRow(
                            title = if (isToday) "📅 ${day.dayName} (Hoy)" else day.dayName,
                            titleColor = if (isToday) AccentRed else TextPrimary,
                            animes = day.animes,
                            onAnimeClick = onAnimeClick
                        )
                    }
                }

                if (state.days.isEmpty()) {
                    Spacer(Modifier.height(48.dp))
                    Text(
                        text = "Sin datos de horario",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }
    }
}
