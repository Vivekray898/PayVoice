package com.vivekray898.payvoice

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.PvTab
import com.vivekray898.payvoice.ui.diagnostics.DiagnosticsScreen
import com.vivekray898.payvoice.ui.employee.EmployeeScreen
import com.vivekray898.payvoice.ui.onboarding.OnboardingScreen
import com.vivekray898.payvoice.ui.owner.OwnerRemoteScreen
import com.vivekray898.payvoice.ui.parenthome.ParentHomeScreen
import com.vivekray898.payvoice.ui.payments.PaymentsScreen
import com.vivekray898.payvoice.ui.reliability.ReliabilityScreen
import com.vivekray898.payvoice.ui.settings.SettingsScreen
import com.vivekray898.payvoice.ui.theme.PayVoiceTheme

/**
 * Lint's InvalidFragmentVersionForActivityResult check fires because
 * registerForActivityResult's contract historically required Fragment 1.3+
 * to avoid a lifecycle bug in the Fragment-based ActivityResultRegistry.
 *
 * PayVoice uses NO fragments — this is a pure Compose app on
 * androidx.activity:activity-compose, where the registry is owned by
 * ComponentActivity itself and the Fragment-version hazard does not apply.
 * The suppression is class-scoped (not file-scoped) so a real fragment
 * usage elsewhere in this file would still be caught.
 */
@SuppressLint("InvalidFragmentVersionForActivityResult")
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** Wall-clock at process-visible onCreate start — cold-start measurement anchor. */
    private var onCreateAtMs: Long = 0L

    /**
     * One-shot POST_NOTIFICATIONS request (Android 13+). Required for the
     * silent "payment announced" receipt that keeps the FCM high-priority
     * channel from being downgraded to normal priority. Without the grant,
     * NotificationManagerCompat.notify() silently no-ops on API 33+.
     */
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics.notificationPermission(
                if (granted) {
                    com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics.PermissionResult.GRANTED
                } else {
                    com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics.PermissionResult.DENIED
                },
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onCreateAtMs = System.currentTimeMillis()
        enableEdgeToEdge()
        ensureNotificationPermission()
        setContent {
            PayVoiceTheme {
                // No root Surface/fillMaxSize: every screen owns its own
                // Scaffold + background + safeDrawing insets (UI overhaul,
                // Phase 2). A full-bleed wrapper is how content ends up
                // ignoring the status/nav bars.
                PayVoiceNavHost(viewModel)
            }
        }
        // Structural perf signal (docs/ANALYTICS.md): measured after setContent,
        // off the critical path, no payment content. Debug builds no-op.
        reportColdStart()
    }

    /** Reports pv_app_cold_started once, shortly after the first frame is drawn. */
    private fun reportColdStart() {
        if (onCreateAtMs == 0L) return
        val durationMs = System.currentTimeMillis() - onCreateAtMs
        onCreateAtMs = 0L
        window?.decorView?.postDelayed({
            com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics.appColdStarted(durationMs)
        }, 150L)
    }

    /**
     * Spec: permission statuses must refresh when the user returns from
     * system settings. Cheap + off-main (DeviceStatusMonitor.snapshot).
     * Also re-runs the health checklist, so the Home banner updates at once.
     */
    override fun onResume() {
        super.onResume()
        viewModel.refreshStatus()
    }

    /**
     * Asks for POST_NOTIFICATIONS exactly once per install on API 33+.
     * Idempotent: if already granted, does nothing. If permanently denied,
     * the OS simply returns denied — no repeated dialogs.
     */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * The navigation graph: onboarding ahead of the tabs, four tab destinations,
 * two pushed screens (health checklist, diagnostics).
 *
 * The tab *slots* are identical for both roles; only the label, icon and
 * screen behind slot 2 and 3 change. That keeps the bar to exactly four
 * destinations per role, as specified, instead of a different navigation
 * model per user.
 */
private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val LIST = "list" // owner: payments history · employee: recent
    const val TEAM = "team" // owner: employees · employee: this device / pair
    const val SETTINGS = "settings"
    const val HEALTH = "health"
    const val DIAGNOSTICS = "diagnostics"
}

private fun routeFor(tab: PvTab): String = when (tab) {
    PvTab.HOME -> Routes.HOME
    PvTab.LIST -> Routes.LIST
    PvTab.TEAM -> Routes.TEAM
    PvTab.SETTINGS -> Routes.SETTINGS
}

private fun tabFor(route: String): PvTab = when (route) {
    Routes.HOME -> PvTab.HOME
    Routes.LIST -> PvTab.LIST
    Routes.TEAM -> PvTab.TEAM
    Routes.SETTINGS -> PvTab.SETTINGS
    else -> PvTab.HOME
}

@Composable
private fun PayVoiceNavHost(viewModel: MainViewModel) {
    val nav = rememberNavController()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val role = settings.role

    // Start at ONBOARDING until DataStore confirms setup is complete. Composing
    // only ONE screen eliminates the HOME→ONBOARDING double composition that
    // caused the first-frame frame skips (jank fix, spec: PERFORMANCE ISSUE).
    val startRoute = if (settings.onboardingComplete) Routes.HOME else Routes.ONBOARDING

    val selectTab: (PvTab) -> Unit = { tab ->
        nav.navigate(routeFor(tab)) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavHost(nav, startDestination = startRoute) {
        composable(Routes.ONBOARDING) {
            // Single completion path: the VM flips the flag, the effect navigates.
            LaunchedEffect(settings.onboardingComplete) {
                if (settings.onboardingComplete) {
                    nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
                }
            }
            OnboardingScreen(viewModel)
        }

        composable(Routes.HOME) {
            ParentHomeScreen(
                viewModel = viewModel,
                selected = tabFor(Routes.HOME),
                onSelectTab = selectTab,
                onOpenHealth = { nav.navigate(Routes.HEALTH) },
            )
        }

        composable(Routes.LIST) {
            PaymentsScreen(
                viewModel = viewModel,
                selected = tabFor(Routes.LIST),
                onSelectTab = selectTab,
                role = role,
                readOnly = role != DeviceRole.OWNER,
            )
        }

        composable(Routes.TEAM) {
            when (role) {
                DeviceRole.OWNER -> OwnerRemoteScreen(
                    viewModel = viewModel,
                    selected = tabFor(Routes.TEAM),
                    onSelectTab = selectTab,
                )
                else -> EmployeeScreen(
                    viewModel = viewModel,
                    selected = tabFor(Routes.TEAM),
                    onSelectTab = selectTab,
                    onOpenHealth = { nav.navigate(Routes.HEALTH) },
                )
            }
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                selected = tabFor(Routes.SETTINGS),
                onSelectTab = selectTab,
                onOpenDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
                onOpenHealth = { nav.navigate(Routes.HEALTH) },
            )
        }

        composable(Routes.HEALTH) {
            ReliabilityScreen(viewModel, onBack = { nav.popBackStack() })
        }

        composable(Routes.DIAGNOSTICS) {
            DiagnosticsScreen(viewModel, onBack = { nav.popBackStack() })
        }
    }
}
