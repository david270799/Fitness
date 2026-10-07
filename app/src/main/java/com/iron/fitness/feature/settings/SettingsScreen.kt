package com.iron.fitness.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iron.fitness.R
import com.iron.fitness.core.settings.AppSettings
import com.iron.fitness.core.settings.ThemeMode
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.ListRow
import com.iron.fitness.core.ui.components.SectionTitle
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.ui.theme.IronPalette
import com.iron.fitness.core.ui.theme.IronPalettes

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    navigate: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    IronScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppearanceSection(settings, viewModel)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AppearanceSection(settings: AppSettings, viewModel: SettingsViewModel) {
    SectionTitle(stringResource(R.string.settings_section_appearance))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IronPalettes.all.forEach { palette ->
            ThemeTile(
                palette = palette,
                selected = palette.id == settings.themeId,
                onClick = { viewModel.setTheme(palette.id) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    IronCard(contentPadding = PaddingValues(0.dp)) {
        Text(
            stringResource(R.string.settings_theme_mode),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp),
        )
        ThemeModeRow(
            title = stringResource(R.string.settings_theme_mode_fixed),
            subtitle = stringResource(R.string.settings_theme_mode_fixed_sub),
            selected = settings.themeMode == ThemeMode.FIXED,
            onClick = { viewModel.setThemeMode(ThemeMode.FIXED) },
        )
        ThemeModeRow(
            title = stringResource(R.string.settings_theme_mode_system),
            subtitle = stringResource(R.string.settings_theme_mode_system_sub),
            selected = settings.themeMode == ThemeMode.SYSTEM,
            onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
        )
    }
}

@Composable
private fun ThemeModeRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    ListRow(title = title, subtitle = subtitle, onClick = onClick) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Iron.colors.accent, unselectedColor = Iron.colors.textSecondary),
        )
    }
}

@Composable
private fun ThemeTile(
    palette: IronPalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier
            .clip(shape)
            .background(palette.background)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Iron.colors.accent else Iron.colors.border), shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 22.dp, height = 22.dp).background(palette.surface).border(1.dp, palette.border))
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(width = 22.dp, height = 22.dp).background(palette.accent))
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(palette.text))
        Text(
            stringResource(palette.nameRes).uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = palette.text,
            maxLines = 1,
        )
    }
}
