package com.vivekray898.payvoice.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.service.setup.SetupNotifications
import com.vivekray898.payvoice.ui.MainViewModel

/**
 * Wizard step 4 — POST_NOTIFICATIONS (PayVoice's own notifications, needed so
 * the wake/announcement notifications are legal on API 33+).
 *
 * Bug 1 fix: auto-advances when the grant lands. The activity result
 * callback AND the shared [PermissionAutoAdvance] (fed by the ON_RESUME
 * status refresh) both cover the return-from-dialog path, so the user never
 * has to tap "Skip for now" after granting.
 */
@Composable
fun PermissionsStep(viewModel: MainViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val granted = status?.notificationsEnabled == true

    PermissionAutoAdvance(
        status = status,
        isGranted = { it?.notificationsEnabled == true },
        onNext = onNext,
    )

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        SetupNotifications.ensureChannels(context)
        viewModel.refreshStatus()
    }

    WizardPage(
        icon = Icons.Filled.NotificationsActive,
        heading = "Allow announcements",
        body = "Android needs your permission to show the notification that " +
            "keeps announcements working in the background.",
        primaryLabel = if (granted) "Continue" else "Allow notifications",
        onPrimary = {
            SetupNotifications.ensureChannels(context)
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS,
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Already granted (or API < 33): "Continue" continues.
                onNext()
            }
        },
        linkLabel = "Skip for now",
        onLink = onNext,
    )
}
