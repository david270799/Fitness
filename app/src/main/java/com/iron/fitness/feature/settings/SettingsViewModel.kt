package com.iron.fitness.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iron.fitness.core.settings.AppSettings
import com.iron.fitness.core.settings.SettingsRepository
import com.iron.fitness.core.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun setTheme(id: String) = launch { repo.setTheme(id) }
    fun setThemeMode(mode: ThemeMode) = launch { repo.setThemeMode(mode) }
}
