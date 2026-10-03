package com.vivekray898.payvoice.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * The only text field in the app. Wraps `OutlinedTextField` so the border
 * radius, hairline, focused-indigo border and label type come from the theme
 * instead of M3 defaults (`DESIGN.md` `text-input` / `text-input-focused`).
 *
 * Always single-line unless the caller says otherwise; the 56dp floor is
 * enforced by the field's own minimum height at default type scale.
 */
@Composable
fun PvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    enabledText: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled && enabledText,
        readOnly = readOnly,
        singleLine = singleLine,
        isError = isError,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        shape = MaterialTheme.shapes.small, // rounded.sm (6dp)
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/**
 * Pairing-code field. Segmented boxes are the visual ideal but need a focus
 * owner per cell and break paste; a single field with a monospace-ish tabular
 * style, auto-uppercase and a hard length cap is the low-end-safe version.
 */
@Composable
fun PvCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Pairing code",
    maxLength: Int = 10,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    PvTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.uppercase().take(maxLength)) },
        modifier = modifier,
        label = label,
        placeholder = "ABC123DEFG",
        enabled = enabled,
        isError = isError,
        supportingText = supportingText,
        keyboardOptions = KeyboardOptions.Default.copy(
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters,
        ),
    )
}

/** Inline helper/error line under a control. */
@Composable
fun PvSupportingText(text: String, modifier: Modifier = Modifier, error: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier,
    )
}

@Suppress("unused")
private val PvFieldGutter = Spacing.lg
