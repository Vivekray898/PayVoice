package com.vivekray898.payvoice.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Wizard step 2 — role selection. Two large tappable cards; tapping one
 * persists the role via the EXISTING setRole action (wired by the driver)
 * and advances the wizard.
 */
@Composable
fun RoleStep(onRole: (DeviceRole) -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.lg)) {
        Spacer(Modifier.size(Spacing.xl))
        RoleCard(
            icon = Icons.Filled.Storefront,
            title = "I'm the owner",
            body = "I receive payments and want my team to hear them",
            onClick = { onRole(DeviceRole.OWNER) },
        )
        Spacer(Modifier.size(Spacing.md))
        RoleCard(
            icon = Icons.Filled.Group,
            title = "I'm an employee",
            body = "I want to hear my owner's payments on this device",
            onClick = { onRole(DeviceRole.EMPLOYEE) },
        )
    }
}

@Composable
private fun RoleCard(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null)
                }
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
