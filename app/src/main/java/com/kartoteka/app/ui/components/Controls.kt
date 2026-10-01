package com.kartoteka.app.ui.components

import com.kartoteka.app.i18n.t

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/*
 * Элементы в стиле RVault: выбранный чип — чёрная «таблетка», остальные — светло-серые;
 * поля ввода — залитые серые плашки с подписью внутри, без рамок и подчёркиваний.
 * Имена совпадают с Material 3, поэтому экраны переключаются заменой импорта.
 */

@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        shape = CircleShape,
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = c.surfaceContainerHighest,
            labelColor = c.onSurface,
            iconColor = c.onSurface,
            selectedContainerColor = c.primary,
            selectedLabelColor = c.onPrimary,
            selectedLeadingIconColor = c.onPrimary,
            disabledContainerColor = c.surfaceContainerHighest.copy(alpha = 0.5f),
        ),
    )
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    errorContainerColor = MaterialTheme.colorScheme.errorContainer,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
    focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
)

private val FieldShape = RoundedCornerShape(14.dp)

@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    TextField(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled,
        label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
        supportingText = supportingText, visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions, singleLine = singleLine, maxLines = maxLines,
        shape = FieldShape, colors = fieldColors(),
    )
}

@Composable
fun OutlinedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    TextField(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled,
        label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions, singleLine = singleLine, maxLines = maxLines,
        shape = FieldShape, colors = fieldColors(),
    )
}

/** Переключатель вкладок-«таблеток»: выбранная — чёрная. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Row(
        modifier
            .clip(CircleShape)
            .background(c.surfaceContainerHighest)
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, title ->
            val sel = i == selected
            androidx.compose.foundation.layout.Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (sel) c.primary else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                androidx.compose.material3.Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (sel) c.onPrimary else c.onSurface,
                )
            }
        }
    }
}

/** Вспомогательный чип (подсказки, группы): залитый серый, без рамки. */
@Composable
fun AssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    androidx.compose.material3.AssistChip(
        onClick = onClick, label = label, modifier = modifier, leadingIcon = leadingIcon,
        shape = CircleShape, border = null,
        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
            containerColor = c.surfaceContainerHighest, labelColor = c.onSurface, leadingIconContentColor = c.onSurface,
        ),
    )
}

@Composable
fun SuggestionChip(onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier) =
    AssistChip(onClick = onClick, label = label, modifier = modifier)

/** Чип выбранного человека: серый с аватаром и крестиком. */
@Composable
fun InputChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    avatar: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    androidx.compose.material3.InputChip(
        selected = selected, onClick = onClick, label = label, modifier = modifier,
        avatar = avatar, trailingIcon = trailingIcon, shape = CircleShape, border = null,
        colors = androidx.compose.material3.InputChipDefaults.inputChipColors(
            containerColor = c.surfaceContainerHighest, labelColor = c.onSurface, trailingIconColor = c.onSurfaceVariant,
        ),
    )
}
