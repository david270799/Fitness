package com.iron.fitness.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iron.fitness.R
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.theme.Iron

val FieldShape = RoundedCornerShape(4.dp)

@Composable
fun ironFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Iron.colors.accent,
    unfocusedBorderColor = Iron.colors.border,
    focusedContainerColor = Iron.colors.surface,
    unfocusedContainerColor = Iron.colors.surface,
    cursorColor = Iron.colors.accent,
    focusedLabelColor = Iron.colors.accent,
    unfocusedLabelColor = Iron.colors.textSecondary,
    focusedTextColor = Iron.colors.text,
    unfocusedTextColor = Iron.colors.text,
    focusedPlaceholderColor = Iron.colors.textSecondary,
    unfocusedPlaceholderColor = Iron.colors.textSecondary,
)

@Composable
fun IronTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    supportingText: String? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    @DrawableRes leadingIcon: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        textStyle = textStyle,
        supportingText = supportingText?.let { { Text(it) } },
        shape = FieldShape,
        colors = ironFieldColors(),
        leadingIcon = leadingIcon?.let { { IronIcon(it, null, tint = Iron.colors.textSecondary) } },
        trailingIcon = trailing,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
        ),
    )
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    IronTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        leadingIcon = IronIcons.Search,
        modifier = modifier,
        trailing = if (value.isNotEmpty()) {
            { IronIconButton(IronIcons.Close, stringResource(R.string.action_clear), { onValueChange("") }) }
        } else {
            null
        },
    )
}

/** Прямоугольный чип-переключатель. */
@Composable
fun IronChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(if (selected) Iron.colors.accent else Iron.colors.surface)
            .border(BorderStroke(1.dp, if (selected) Iron.colors.accent else Iron.colors.border), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IronIcon(icon, null, tint = if (selected) Iron.colors.onAccent else Iron.colors.text, size = 16.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Iron.colors.onAccent else Iron.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Горизонтально прокручиваемый ряд чипов с одиночным выбором. */
@Composable
fun <T> ChipRow(
    items: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            IronChip(text = label(item), selected = item == selected, onClick = { onSelect(item) })
        }
    }
}

/** Чипы с переносом строк и множественным выбором. */
@Composable
fun <T> MultiChipFlow(
    items: List<T>,
    selected: Set<T>,
    label: @Composable (T) -> String,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            IronChip(text = label(item), selected = item in selected, onClick = { onToggle(item) })
        }
    }
}

/** Сегментированный переключатель на всю ширину. */
@Composable
fun <T> Segmented(
    items: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, Iron.colors.border, shape),
    ) {
        items.forEachIndexed { index, item ->
            val isSel = item == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .background(if (isSel) Iron.colors.accent else Iron.colors.surface)
                    .clickable { onSelect(item) }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(item),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSel) Iron.colors.onAccent else Iron.colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (index < items.lastIndex) {
                Box(Modifier.width(1.dp).heightIn(min = 44.dp).background(Iron.colors.border))
            }
        }
    }
}

/** Кнопка-«селектор» с подписью и текущим значением. */
@Composable
fun SelectorButton(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(Iron.colors.surface)
            .border(BorderStroke(1.dp, if (active) Iron.colors.accent else Iron.colors.border), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f, fill = false)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) Iron.colors.accentText else Iron.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        IronIcon(IronIcons.ChevronDown, null, tint = Iron.colors.textSecondary, size = 18.dp)
    }
}
