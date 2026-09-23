package io.github.painter99.wswolomouc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.AndroidEntryPoint
import io.github.painter99.wswolomouc.data.WeatherRepository
import io.github.painter99.wswolomouc.ui.Format
import io.github.painter99.wswolomouc.ui.MainUiState
import io.github.painter99.wswolomouc.ui.MainViewModel
import io.github.painter99.wswolomouc.ui.RelativeTimeFormatter
import io.github.painter99.wswolomouc.ui.StationUi
import io.github.painter99.wswolomouc.widget.WswWidget
import kotlinx.coroutines.delay

/**
 * Main screen (M1.5, PRD F3.1/F3.2): primary station card (large) +
 * secondary station card (compact) + overall freshness status + relative
 * measurement times.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen(viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Re-render every 60 s so "před X min" stays truthful (PRD G2).
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowMs = System.currentTimeMillis()
        }
    }

    // Push fresh data to the home-screen widget after every completed
    // refresh (M1.6a: the widget itself is cache-first, no network).
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            WswWidget().updateAll(context)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        // M1.6a.1: targetSdk 35 draws edge-to-edge — keep content below the
        // status bar (the status row collided with clock/indicators).
        modifier = Modifier.fillMaxSize().statusBarsPadding()
    ) {
        MainContent(state = state, nowMs = nowMs, onRefresh = viewModel::refresh)
    }
}

@Composable
fun MainContent(state: MainUiState, nowMs: Long, onRefresh: () -> Unit) {
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatusRow(state, nowMs)
        state.primary?.let { StationCard(it, nowMs, isPrimary = true) }
        state.secondary?.let { StationCard(it, nowMs, isPrimary = false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                if (state.isRefreshing) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.isRefreshing) "Aktualizuji…" else "Aktualizovat")
            }
        }
        state.rateLimitMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun StatusRow(state: MainUiState, nowMs: Long) {
    val color: Color
    val label: String
    when (state.freshness) {
        WeatherRepository.Freshness.FRESH -> {
            color = Color(0xFF2E7D32); label = "Data aktuální"
        }
        WeatherRepository.Freshness.STALE -> {
            color = Color(0xFFEF6C00); label = "Starší data"
        }
        WeatherRepository.Freshness.OFFLINE, null -> {
            color = Color(0xFF9E9E9E); label = "Offline"
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 8.dp)
        )
        (state.primary ?: state.secondary)?.let {
            if (it.measuredAtMs != null) {
                Text(
                    text = " · měření ${RelativeTimeFormatter.format(it.measuredAtMs, nowMs)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    // M1.6b-3: per-source outcome instead of a global "Offline" guess.
    if (state.sourceStatus.isNotEmpty()) {
        Text(
            text = state.sourceStatus.entries.joinToString(" · ") {
                "${StationUi.displayNameFor(it.key)}: ${it.value}"
            },
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun StationCard(s: StationUi, nowMs: Long, isPrimary: Boolean) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = s.displayName,
                    style = if (isPrimary) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .size(10.dp)
                        .background(
                            when {
                                !s.hasData -> Color(0xFF9E9E9E)   // no data (M1.6a.1)
                                s.isStale -> Color(0xFFEF6C00)    // stale
                                else -> Color(0xFF2E7D32)         // fresh
                            },
                            CircleShape
                        )
                )
            }

            Text(
                text = Format.temperature(s.temperatureC),
                fontSize = if (isPrimary) 44.sp else 24.sp,
                fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.SemiBold
            )

            Text(
                text = "Vlhkost ${Format.humidity(s.humidityPct)} · " +
                    "Tlak ${Format.pressure(s.pressureHpa)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Vítr ${Format.wind(s.windMs)} · Poryvy ${Format.wind(s.windGustMs)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Srážky ${Format.rain(s.rainDailyMm)} (${s.rainLabel})",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = s.measuredAtMs?.let {
                    "Měření: ${RelativeTimeFormatter.format(it, nowMs)}"
                } ?: "Bez dat",
                style = MaterialTheme.typography.bodySmall
            )
            // M1.6b-3: why the last fetch failed (null when OK / no attempt).
            s.statusNote?.let {
                Text(
                    text = "Poslední dotaz: $it",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
