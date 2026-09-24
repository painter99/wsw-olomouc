package io.github.painter99.wswolomouc

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.AndroidEntryPoint
import io.github.painter99.wswolomouc.data.WeatherRepository
import io.github.painter99.wswolomouc.ui.Format
import io.github.painter99.wswolomouc.ui.MainUiState
import io.github.painter99.wswolomouc.ui.MainViewModel
import io.github.painter99.wswolomouc.ui.RelativeTimeFormatter
import io.github.painter99.wswolomouc.ui.StationUi
import io.github.painter99.wswolomouc.ui.ThemeMode
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
            // Round 2 (Pavel 23. 9.): system / light / dark, persisted choice.
            val themeMode by viewModel.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // Round 5: edge-to-edge draws UNDER the status bar — in the light
            // theme the system must use DARK icons, otherwise they vanish on
            // the light background (the white strip Pavel saw 23. 9.).
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    WindowCompat
                        .getInsetsController(window, view)
                        .isAppearanceLightStatusBars = !darkTheme
                }
            }
            MaterialTheme(
                colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
            ) {
                MainScreen(viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
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
        // Round 3: the Surface fills the WHOLE screen (including the status
        // bar area) so the app background color reaches behind the clock —
        // no white strip in dark theme. The content padding moved into
        // MainContent's Column.
        modifier = Modifier.fillMaxSize()
    ) {
        MainContent(
            state = state,
            nowMs = nowMs,
            themeMode = themeMode,
            onRefresh = viewModel::refresh,
            onSetTheme = viewModel::setTheme
        )
    }
}

@Composable
fun MainContent(
    state: MainUiState,
    nowMs: Long,
    themeMode: ThemeMode,
    onRefresh: () -> Unit,
    onSetTheme: (ThemeMode) -> Unit
) {
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        // Round 2 (Pavel 23. 9.): the screen must SCROLL — without it the
        // bottom rows (sources, licenses) were unreachable. Round 3: the
        // status-bar padding lives HERE (the Surface above fills the whole
        // screen, killing the white strip in dark theme).
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatusRow(state, nowMs)
        state.primary?.let { StationCard(it, nowMs, isPrimary = true) }
        if (state.trends.isNotEmpty()) {
            Text(
                text = "Trend: " + state.trends.joinToString(" · ") { it.text },
                style = MaterialTheme.typography.bodyMedium
            )
        }
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
        // Round 4 (Pavel 23. 9.): the long explanation is collapsed behind a
        // toggle — the screen stays clean, details on demand.
        var detailsExpanded by remember { mutableStateOf(false) }
        TextButton(onClick = { detailsExpanded = !detailsExpanded }) {
            Text(
                if (detailsExpanded) "Méně o stanicích a výpočtu"
                else "Více o stanicích a výpočtu"
            )
        }
        if (detailsExpanded) {
            Text(
                text = "Aplikace kombinuje data ze dvou stanic. Hlavní " +
                    "zdroj dat v aplikaci je Infopocasi v Neředíně, měří " +
                    "zhruba každou minutu, takže zobrazené hodnoty bývají " +
                    "čerstvé. Druhou stanici provozuje ČHMÚ v Holici. " +
                    "Měří každých 10 minut a data se v otevřených datech " +
                    "objevují přibližně po 10–15 minutách — v aplikaci " +
                    "proto slouží hlavně k porovnání a jako záloha. " +
                    "Konečnou teplotu, " +
                    "vítr, poryvy, srážky i pocitovou teplotu na widgetu " +
                    "počítá aplikace jako průměr obou stanic vážený " +
                    "čerstvostí — čerstvější měření má větší váhu " +
                    "(váha = 1/(stáří + 15 min)). V případě, kdy jsou data " +
                    "ze stanice ČHMÚ starší než 30 minut, widget " +
                    "nezobrazuje průměr obou stanic, ale pouze data " +
                    "z Infopocasi.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        // Round 2 (Pavel 23. 9., #5): theme switch, persisted in DataStore.
        Text(
            text = "Téma zobrazení",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ThemeMode.entries.forEach { mode ->
                val label = when (mode) {
                    ThemeMode.SYSTEM -> "Systém"
                    ThemeMode.LIGHT -> "Světlý"
                    ThemeMode.DARK -> "Tmavý"
                }
                TextButton(onClick = { onSetTheme(mode) }, enabled = mode != themeMode) {
                    Text(label)
                }
            }
        }
        SourceLinks()
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
                text = Format.temperaturePrecise(s.temperatureC),
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

/**
 * M1.7-trend (Pavel 23. 9.): visible, clickable links to the OFFICIAL pages
 * of both data sources, licenses visible "podle standardů" (F5.4 extended).
 * Station identity per Pavel: the station operator = infopocasi-olomouc.cz (do NOT
 * conflate with the in-pocasi.cz aggregator).
 */
@Composable
fun SourceLinks() {
    val context = LocalContext.current
    Column {
        Text(
            text = "Zdroje dat",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        LinkText(
            "infopocasi-olomouc.cz — data se souhlasem provozovatele",
            Sources.INFOPOCASI_WEB,
            context
        )
        LinkText("ČHMÚ Olomouc–Holice — otevřená data", Sources.CHMU_WEB, context)
        LinkText("ČHMÚ — licence CC BY 4.0", Sources.CHMU_LICENSE_URL, context)
    }
}

@Composable
private fun LinkText(label: String, url: String, context: Context) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            // Round 2 (#6/#7): spacing between the sources, no ripple box
            // around the link text.
            .padding(top = 6.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
    )
}
