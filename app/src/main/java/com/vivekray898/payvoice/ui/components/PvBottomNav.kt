package com.vivekray898.payvoice.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.vivekray898.payvoice.R
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.ui.theme.PvElevation
import com.vivekray898.payvoice.ui.theme.Spacing

/** The four destinations, labelled per role. Never more than four. */
enum class PvTab { HOME, LIST, TEAM, SETTINGS }

/**
 * One bottom bar, four slots, role-aware labels.
 *
 * The spec caps navigation at 3–4 destinations per role, and the previous
 * build had none at all — every screen was a push off a "quick links" row on
 * Home, which is why a non-technical owner could end up in the technical
 * diagnostics log by tapping "Show all payments".
 */
@Composable
fun PvBottomNav(
    role: DeviceRole,
    selected: PvTab,
    onSelect: (PvTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOwner = role == DeviceRole.OWNER
    val tabs = listOf(
        TabSpec(
            PvTab.HOME,
            Icons.Filled.Home,
            stringResource(R.string.nav_home),
        ),
        TabSpec(
            PvTab.LIST,
            if (isOwner) Icons.Filled.PlaylistPlay else Icons.Filled.QrCodeScanner,
            stringResource(if (isOwner) R.string.nav_payments else R.string.nav_recent),
        ),
        TabSpec(
            PvTab.TEAM,
            if (isOwner) Icons.Filled.People else Icons.Filled.Storefront,
            stringResource(if (isOwner) R.string.nav_team else R.string.nav_pair),
        ),
        TabSpec(
            PvTab.SETTINGS,
            Icons.Filled.Settings,
            stringResource(R.string.nav_settings),
        ),
    )

    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = PvElevation.flat,
    ) {
        tabs.forEach { spec ->
            NavigationBarItem(
                selected = selected == spec.tab,
                onClick = { onSelect(spec.tab) },
                icon = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            spec.icon,
                            contentDescription = null,
                            modifier = Modifier.size(Spacing.icon),
                        )
                    }
                },
                label = { Text(spec.label, style = MaterialTheme.typography.labelSmall) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private data class TabSpec(
    val tab: PvTab,
    val icon: ImageVector,
    val label: String,
)

/**
 * Scaffold + bottom bar for the four tab destinations.
 *
 * Screens stay inside `PvScaffold` (the AGENTS.md layout rule) — this just
 * wires the shared bottom bar and the nav-bar inset once, so no tab screen
 * has to remember to add it.
 */
@Composable
fun PvTabScaffold(
    role: DeviceRole,
    selected: PvTab,
    onSelect: (PvTab) -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    PvScaffold(
        modifier = modifier,
        topBar = topBar,
        floatingActionButton = floatingActionButton,
        bottomBar = { PvBottomNav(role = role, selected = selected, onSelect = onSelect) },
        content = content,
    )
}
