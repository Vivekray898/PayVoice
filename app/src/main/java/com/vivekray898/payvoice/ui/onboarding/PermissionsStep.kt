package com.vivekray898.payvoice.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.service.setup.SetupNotifications
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.PvPrimaryButton
import com.vivekray898.payvoice.ui.components.PvTextButton

/**
 * Wizard step: POST_NOTIFICATIONS. Auto-advances on grant (the activity
 * result callback refreshes status; PermissionStep fires the transition).
 */
@Composable
fun PermissionsStep(viewModel: MainViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val granted = status?.notificationsEnabled == true

    PermissionStep(
        status = status,
        isGranted = { it?.notificationsEnabled == true },
        onContinue = onNext,
    )

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        SetupNotifications.ensureChannels(context)
        viewModel.refreshStatus()
    }

    StepScaffold(
        step = WizardStep.NOTIF_PERMISSION,
        onBack = onBack,
        icon = Icons.Filled.Notifications,
        title = "Allow announcements",
        body = "Android needs your permission to show the notification that " +
            "keeps announcements working in the background.",
        primaryButton = {
            PvPrimaryButton(
                text = if (granted) "Continue" else "Allow notifications",
                onClick = {
                    SetupNotifications.ensureChannels(context)
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS,
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onNext()
                    }
                },
            )
        },
        secondaryAction = {
            PvTextButton(
                text = "Skip for now",
                onClick = onNext,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
