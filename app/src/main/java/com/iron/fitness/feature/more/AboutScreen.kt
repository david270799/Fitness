package com.iron.fitness.feature.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iron.fitness.BuildConfig
import com.iron.fitness.R
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.SectionTitle
import com.iron.fitness.core.ui.theme.Iron

@Composable
fun AboutScreen(onBack: () -> Unit) {
    IronScaffold(title = stringResource(R.string.about_title), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayMedium, color = Iron.colors.accent)
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = Iron.numbers.small,
                color = Iron.colors.textSecondary,
            )
            Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.bodyLarge)
            SectionTitle(stringResource(R.string.about_privacy_title))
            IronCard { Text(stringResource(R.string.about_privacy_text), style = MaterialTheme.typography.bodyMedium) }
            SectionTitle(stringResource(R.string.about_licenses_title))
            IronCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.about_license_exercises), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.about_license_icons), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.about_license_fonts), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                stringResource(R.string.about_medical),
                style = MaterialTheme.typography.bodySmall,
                color = Iron.colors.textSecondary,
            )
        }
    }
}
