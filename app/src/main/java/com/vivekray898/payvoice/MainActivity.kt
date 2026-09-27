package com.vivekray898.payvoice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.diagnostics.DiagnosticsScreen
import com.vivekray898.payvoice.ui.onboarding.OnboardingScreen
import com.vivekray898.payvoice.ui.parenthome.ParentHomeScreen
import com.vivekray898.payvoice.ui.settings.SettingsScreen
import com.vivekray898.payvoice.ui.theme.PayVoiceTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PayVoiceTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PayVoiceNavHost(viewModel)
                }
            }
        }
    }
}

private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
}

@Composable
private fun PayVoiceNavHost(viewModel: MainViewModel) {
    val nav = rememberNavController()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    NavHost(nav, startDestination = Routes.HOME) {
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
            // Converge late-loaded onboarding state (DataStore arrives async).
            LaunchedEffect(settings.onboardingComplete) {
                if (!settings.onboardingComplete) {
                    nav.navigate(Routes.ONBOARDING) { popUpTo(0) { inclusive = true } }
                }
            }
            ParentHomeScreen(
                viewModel = viewModel,
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(viewModel, onBack = { nav.popBackStack() })
        }
        composable(Routes.DIAGNOSTICS) {
            DiagnosticsScreen(viewModel, onBack = { nav.popBackStack() })
        }
    }
}
