package com.iron.fitness.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iron.fitness.R
import com.iron.fitness.core.ui.theme.Iron

val CardShape = RoundedCornerShape(6.dp)
val ButtonShape = RoundedCornerShape(4.dp)

@Composable
fun IronIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Iron.colors.text,
    size: Dp = 22.dp,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

@Composable
fun IronIconButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Iron.colors.text,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        IronIcon(icon, contentDescription, tint = if (enabled) tint else Iron.colors.textSecondary)
    }
}

/** Плотная карточка с тонкой рамкой, без теней. */
@Composable
fun IronCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = Iron.colors.surface,
    borderColor: Color = Iron.colors.border,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shapeModifier = modifier
        .clip(CardShape)
        .background(color)
        .border(BorderStroke(1.dp, borderColor), CardShape)
    Column(
        modifier = (if (onClick != null) shapeModifier.clickable(onClick = onClick) else shapeModifier)
            .padding(contentPadding),
        content = content,
    )
}

/** Заголовок секции: капс, разрядка, второстепенный цвет. */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = Iron.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke(this)
    }
}

/** Крупное моноширинное число с подписью единицы. */
@Composable
fun BigNumber(
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    label: String? = null,
    style: TextStyle = Iron.numbers.large,
    color: Color = Iron.colors.text,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value, style = style, color = color, maxLines = 1)
            if (unit != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelLarge,
                    color = Iron.colors.textSecondary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        if (label != null) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Iron.colors.textSecondary,
            )
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    color: Color = Iron.colors.accent,
    contentColor: Color = Iron.colors.onAccent,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = contentColor,
            disabledContainerColor = Iron.colors.surfaceHigh,
            disabledContentColor = Iron.colors.textSecondary,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            IronIcon(icon, null, tint = if (enabled) contentColor else Iron.colors.textSecondary, size = 20.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(text.uppercase(), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    color: Color = Iron.colors.text,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(1.dp, if (enabled) Iron.colors.border else Iron.colors.surfaceHigh),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            IronIcon(icon, null, tint = if (enabled) color else Iron.colors.textSecondary, size = 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text.uppercase(), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Iron.colors.accentText,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled, shape = ButtonShape) {
        Text(text.uppercase(), style = MaterialTheme.typography.titleSmall, color = if (enabled) color else Iron.colors.textSecondary)
    }
}

/** Плоский прямоугольный прогресс-бар. */
@Composable
fun FlatProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    color: Color = Iron.colors.accent,
    trackColor: Color = Iron.colors.surfaceHigh,
) {
    val p = progress.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(2.dp))
            .background(trackColor),
    ) {
        if (p > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(p)
                    .height(height)
                    .background(color),
            )
        }
    }
}

/** Строка меню/настроек: иконка, заголовок, подзаголовок, хвост. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    iconTint: Color = Iron.colors.text,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val base = modifier.fillMaxWidth()
    Row(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IronIcon(icon, null, tint = iconTint)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Iron.colors.text)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Iron.colors.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        modifier = modifier,
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
    ) {
        IronSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
fun IronSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Iron.colors.onAccent,
            checkedTrackColor = Iron.colors.accent,
            checkedBorderColor = Iron.colors.accent,
            uncheckedThumbColor = Iron.colors.textSecondary,
            uncheckedTrackColor = Iron.colors.surfaceHigh,
            uncheckedBorderColor = Iron.colors.border,
        ),
    )
}

@Composable
fun IronDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = Iron.colors.border)
}

@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) IronIcon(icon, null, tint = Iron.colors.textSecondary, size = 40.dp)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = Iron.colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        action?.invoke()
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String?,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Iron.colors.surface,
        shape = RoundedCornerShape(8.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = text?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        confirmButton = {
            GhostButton(
                text = confirmText,
                onClick = onConfirm,
                color = if (destructive) Iron.colors.error else Iron.colors.accentText,
            )
        },
        dismissButton = {
            GhostButton(text = stringResource(R.string.action_cancel), onClick = onDismiss, color = Iron.colors.textSecondary)
        },
    )
}

/** Квадратная метка-бейдж (например, «РЕКОРД»). */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Iron.colors.accent,
    contentColor: Color = Iron.colors.onAccent,
) {
    Surface(color = color, contentColor = contentColor, shape = RoundedCornerShape(2.dp), modifier = modifier) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}
