package com.vivekray898.payvoice.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Wizard step: role selection. Two large tappable cards; the driver
 * persists the choice through the ViewModel before advancing.
 */
@Composable
fun RoleStep(onRole: (DeviceRole) -> Unit) {
    StepScaffold(
        step = WizardStep.ROLE,
        onBack = null,
        icon = Icons.Filled.Person,
        title = "How will you use PayVoice?",
        body = "Choose how this device will work.",
        primaryButton = {},
    ) {
        Spacer(Modifier.height(Spacing.xl))
        RoleCard(
            icon = Icons.Filled.Storefront,
            title = "I'm the owner",
            subtitle = "I receive payments and want them announced",
            onClick = { onRole(DeviceRole.OWNER) },
        )
        Spacer(Modifier.height(Spacing.md))
        RoleCard(
            icon = Icons.Filled.Group,
            title = "I'm an employee",
            subtitle = "I want to hear my owner's payments on this device",
            onClick = { onRole(DeviceRole.EMPLOYEE) },
        )
    }
}

@Composable
private fun RoleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Spacing.xxs,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.xxl + Spacing.lg + Spacing.sm) // 56dp+ structural
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(Spacing.xxl + Spacing.lg + Spacing.sm), // 56dp circle
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(Spacing.lg))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
