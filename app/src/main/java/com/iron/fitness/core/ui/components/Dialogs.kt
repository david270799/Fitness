package com.iron.fitness.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iron.fitness.R
import com.iron.fitness.core.ui.theme.Iron

/** Диалог ввода одной строки или числа. */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    label: String? = null,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    supportingText: String? = null,
    validate: (String) -> Boolean = { true },
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Iron.colors.surface,
        shape = RoundedCornerShape(8.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            IronTextField(
                value = text,
                onValueChange = { text = it },
                label = label,
                placeholder = placeholder,
                keyboardType = keyboardType,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 3,
                supportingText = supportingText,
            )
        },
        confirmButton = {
            GhostButton(
                text = stringResource(R.string.action_save),
                onClick = { onConfirm(text) },
                enabled = validate(text),
            )
        },
        dismissButton = {
            GhostButton(stringResource(R.string.action_cancel), onDismiss, color = Iron.colors.textSecondary)
        },
    )
}

/** Меню в стиле приложения. */
@Composable
fun IronDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = Iron.colors.surfaceHigh,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Iron.colors.border),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
        content = content,
    )
}

@Composable
fun IronMenuItem(
    text: String,
    onClick: () -> Unit,
    @DrawableRes icon: Int? = null,
    color: Color = Iron.colors.text,
    enabled: Boolean = true,
) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = icon?.let { { IronIcon(it, null, tint = if (enabled) color else Iron.colors.textSecondary, size = 20.dp) } },
        colors = MenuDefaults.itemColors(
            textColor = color,
            disabledTextColor = Iron.colors.textSecondary,
        ),
    )
}

/**
 * Компактное поле для чисел в таблице подходов: моноширинный шрифт, подсказка-плейсхолдер.
 * Текст хранится снаружи, чтобы курсор не прыгал при обновлении из базы.
 */
@Composable
fun CompactNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    decimal: Boolean = true,
    /** Свой набор допустимых символов (например, «8-12» для диапазона повторов). */
    allowedChars: ((Char) -> Boolean)? = null,
    keyboardType: KeyboardType? = null,
    background: Color = Iron.colors.surfaceHigh,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    textStyle: TextStyle = Iron.numbers.small,
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) {
        if (value != field.text) field = TextFieldValue(value, TextRange(value.length))
    }
    val shape = RoundedCornerShape(4.dp)
    BasicTextField(
        value = field,
        onValueChange = { v ->
            val allowed = allowedChars ?: { c: Char -> c.isDigit() || (decimal && (c == ',' || c == '.')) }
            val filtered = v.text.filter(allowed).take(7)
            field = v.copy(text = filtered, selection = if (filtered == v.text) v.selection else TextRange(filtered.length))
            if (filtered != value) onValueChange(filtered)
        },
        singleLine = true,
        textStyle = textStyle.copy(color = Iron.colors.text, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(Iron.colors.accent),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType ?: if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = imeAction,
        ),
        keyboardActions = if (onDone != null) KeyboardActions(onDone = { onDone() }) else KeyboardActions.Default,
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(1.dp, Iron.colors.border, shape)
            .height(40.dp),
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                if (field.text.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        placeholder,
                        style = textStyle,
                        color = Iron.colors.textSecondary.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
                inner()
            }
        },
    )
}
