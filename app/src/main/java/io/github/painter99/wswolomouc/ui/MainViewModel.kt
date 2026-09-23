package io.github.painter99.wswolomouc.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.painter99.wswolomouc.Sources
import io.github.painter99.wswolomouc.data.WeatherRepository
import io.github.painter99.wswolomouc.ui.Trend
import io.github.painter99.wswolomouc.ui.TrendItem
import javax.inject.Inject
import kotlin.math.ceil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Main screen ViewModel (M1.5). Cache-first start (NF3): the initial
 * [refresh] call fetches with the repository's fallback + rate limit
 * (F1.4/F1.5) and maps the snapshot to UI state.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: WeatherRepository,
    private val themeStore: ThemeStore,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState(isLoading = true))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /** Persisted theme preference (round 2) — collected in the UI. */
    val themeMode: Flow<ThemeMode> = themeStore.mode

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { themeStore.set(mode) }
    }

    init {
        viewModelScope.launch {
            // Cache-first (NF3; round 6): show the last known values
            // IMMEDIATELY — the startup spinner must never wait for the
            // network (10 s timeouts made the app look frozen).
            val cached = repository.latestFromCache()
            val now0 = clock()
            // Round 7 fix (Pavel 23. 9.): when the newest cached measurement
            // is fresher than the rate limit, do NOT touch the network on
            // startup — opening the app must not burn the 10-min manual
            // refresh budget (the "limit za 10 min" right after opening).
            val newestCachedAt = cached.values.maxOfOrNull { it.measuredAtMs } ?: 0L
            val willRefresh = now0 - newestCachedAt >= Sources.FETCH_RATE_LIMIT_MS
            // ONE atomic assignment (isRefreshing included) — otherwise
            // awaiters could observe the cache-first state between it and
            // refresh() and read a half-final state (CI run #86).
            _uiState.value = (if (cached.isNotEmpty()) {
                val freshness = if (
                    cached.values.all {
                        now0 - it.measuredAtMs <= Sources.STALE_THRESHOLD_MIN * 60_000
                    }
                ) {
                    WeatherRepository.Freshness.FRESH
                } else {
                    WeatherRepository.Freshness.STALE
                }
                MainUiStateMapper.from(
                    WeatherRepository.Snapshot(freshness = freshness, measurements = cached),
                    now0
                )
            } else {
                // First launch, nothing cached: placeholders, no spinner.
                MainUiState(
                    isLoading = false,
                    primary = MainUiStateMapper.placeholder(Sources.STATION_INFOPOCASI),
                    secondary = MainUiStateMapper.placeholder(Sources.STATION_CHMU)
                )
            }).copy(isRefreshing = willRefresh)
            if (willRefresh) {
                refresh()
            }
        }
    }

    fun refresh() {
        // Single-flight (round 7): a refresh already running wins — the UI
        // disables the button while refreshing, and a manual tap during the
        // startup refresh must not race it (flaky NPE, merge run #88).
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, rateLimitMessage = null)
            val snapshot = repository.refresh()
            val nowMs = clock()
            val rateLimitMessage = if (snapshot.skippedByRateLimit) {
                snapshot.nextRefreshAllowedAtMs
                    ?.takeIf { it > nowMs }
                    ?.let {
                        val minutes = ceil((it - nowMs) / 60_000.0).toInt()
                        // Round 7 fix (Pavel 23. 9.): the message must be
                        // HONEST — say how old the shown data is, not just
                        // "wait 10 min" while the data is 4 minutes fresh.
                        val dataAgeMin = snapshot.measurements.values
                            .maxOfOrNull { m -> (nowMs - m.measuredAtMs) / 60_000 }
                            ?.toInt() ?: 0
                        "Data před $dataAgeMin min – další aktualizace za $minutes min"
                    }
            } else {
                null
            }
            _uiState.value = MainUiStateMapper.from(snapshot, nowMs).copy(
                isRefreshing = false,
                rateLimitMessage = rateLimitMessage,
                trends = trendItems(snapshot, nowMs)
            )
        }
    }

    /** Trend arrows for the primary station (1 h / 3 h / 6 h, Pavel 23. 9.). */
    private suspend fun trendItems(
        snapshot: WeatherRepository.Snapshot,
        nowMs: Long
    ): List<TrendItem> {
        val primary = snapshot.measurements[Sources.STATION_INFOPOCASI] ?: return emptyList()
        val current = primary.temperatureC ?: return emptyList()
        return Trend.APP_WINDOWS_MS.map { (windowMs, label) ->
            TrendItem(
                label,
                Trend.compute(
                    current,
                    repository.pastTemperature(primary.station, nowMs - windowMs)
                )
            )
        }.filter { it.direction != null } // windows without history stay hidden (round 2)
    }
}
