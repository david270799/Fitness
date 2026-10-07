package com.iron.fitness.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iron.fitness.core.ui.theme.Iron

@Composable
fun IronBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Iron.colors.surface,
        contentColor = Iron.colors.text,
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title.uppercase(), style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Лист выбора одного значения (null = «любые»). */
@Composable
fun <T> SingleChoiceSheet(
    title: String,
    items: List<T>,
    selected: T?,
    anyLabel: String,
    label: @Composable (T) -> String,
    onSelect: (T?) -> Unit,
    onDismiss: () -> Unit,
) {
    IronBottomSheet(title = title, onDismiss = onDismiss) {
        val all: List<T?> = listOf<T?>(null) + items
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            all.forEach { item ->
                IronChip(
                    text = if (item == null) anyLabel else label(item),
                    selected = item == selected,
                    onClick = { onSelect(item); onDismiss() },
                )
            }
        }
    }
}
