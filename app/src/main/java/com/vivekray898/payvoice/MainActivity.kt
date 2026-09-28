package com.vivekray898.payvoice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.diagnostics.DiagnosticsScreen
import com.vivekray898.payvoice.ui.employee.EmployeeScreen
import com.vivekray898.payvoice.ui.onboarding.OnboardingScreen
import com.vivekray898.payvoice.ui.owner.OwnerRemoteScreen
import com.vivekray898.payvoice.ui.parenthome.ParentHomeScreen
import com.vivekray898.payvoice.ui.reliability.ReliabilityScreen
import com.vivekray898.payvoice.ui.settings.SettingsScreen
import com.vivekray898.payvoice.ui.theme.PayVoiceTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /**
     * One-shot POST_NOTIFICATIONS request (Android 13+). Required for the
     * silent "payment announced" receipt that keeps the FCM high-priority
     * channel from being downgraded to normal priority. Without the grant,
     * NotificationManagerCompat.notify() silently no-ops on API 33+.
     */
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureNotificationPermission()
        setContent {
            PayVoiceTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PayVoiceNavHost(viewModel)
                }
            }
        }
    }

    /**
     * Spec: permission statuses must refresh when the user returns from
     * system settings. Cheap + off-main (DeviceStatusMonitor.snapshot).
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

private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
    const val RELIABILITY = "reliability"
    const val OWNER_REMOTE = "owner_remote"
    const val EMPLOYEE_REMOTE = "employee_remote"
}

@Composable
private fun PayVoiceNavHost(viewModel: MainViewModel) {
    val nav = rememberNavController()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // Start at ONBOARDING until DataStore confirms setup is complete. Composing
    // only ONE screen eliminates the HOME→ONBOARDING double composition that
    // caused the first-frame frame skips (jank fix, spec: PERFORMANCE ISSUE).
    val startRoute = if (settings.onboardingComplete) Routes.HOME else Routes.ONBOARDING

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
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
                onOpenReliability = { nav.navigate(Routes.RELIABILITY) },
                onOpenOwnerRemote = { nav.navigate(Routes.OWNER_REMOTE) },
                onOpenEmployeeRemote = { nav.navigate(Routes.EMPLOYEE_REMOTE) },
            )
        }
        composable(Routes.OWNER_REMOTE) {
            OwnerRemoteScreen(viewModel, onBack = { nav.popBackStack() })
        }
        composable(Routes.EMPLOYEE_REMOTE) {
            EmployeeScreen(viewModel, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel,
                onBack = { nav.popBackStack() },
                onOpenDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
                onOpenReliability = { nav.navigate(Routes.RELIABILITY) },
            )
        }
        composable(Routes.DIAGNOSTICS) {
            DiagnosticsScreen(viewModel, onBack = { nav.popBackStack() })
        }
        composable(Routes.RELIABILITY) {
            ReliabilityScreen(viewModel, onBack = { nav.popBackStack() })
        }
    }
}