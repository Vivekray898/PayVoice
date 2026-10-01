package com.vivekray898.payvoice.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.service.setup.SetupNotifications
import com.vivekray898.payvoice.ui.MainViewModel

/**
 * Wizard step 4 — POST_NOTIFICATIONS (PayVoice's own notifications, needed so
 * the wake/announcement notifications are legal on API 33+). Auto-advances
 * on grant.
 */
@Composable
fun PermissionsStep(viewModel: MainViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val granted = status?.notificationsEnabled == true

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> viewModel.refreshStatus() }

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
                viewModel.openAppNotificationSettings(context)
            }
        },
        linkLabel = "Skip for now",
        onLink = onNext,
    )
}
