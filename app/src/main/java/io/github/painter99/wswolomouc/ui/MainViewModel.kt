package io.github.painter99.wswolomouc.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.painter99.wswolomouc.data.WeatherRepository
import javax.inject.Inject
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
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState(isLoading = true))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val snapshot = repository.refresh()
            _uiState.value = MainUiStateMapper.from(snapshot, clock())
        }
    }
}
