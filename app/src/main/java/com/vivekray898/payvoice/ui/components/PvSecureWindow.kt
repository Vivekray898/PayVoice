package com.vivekray898.payvoice.ui.components

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Blocks screenshots, screen recording and Recents previews while the calling
 * screen is composed.
 *
 * `FLAG_SECURE` is a Window flag, not a layout concern, so it cannot live on
 * a Modifier. This effect sets it when the screen enters composition and
 * restores the previous value when it leaves, so securing one screen never
 * leaves the rest of the app blacked out.
 *
 * Applied to the pairing screens: the pairing code is a single-use, 10-minute
 * credential that grants an employee device standing in the owner's business.
 * It otherwise ends up in Recents thumbnails, in screenshots people take to
 * ask support for help, and in any screen-recording of a pairing walkthrough.
 *
 * Note this also blocks legitimate screenshots of these screens. That is the
 * intended trade: the code is the sensitive thing on the screen, and it can be
 * regenerated with one tap.
 */
@Composable
fun PvSecureWindow() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val activity = context.findActivity() ?: return@DisposableEffect onDispose { }
        val window = activity.window ?: return@DisposableEffect onDispose { }
        val previous = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            // Restore exactly what was there before rather than blindly
            // clearing: another screen may have secured the window already.
            if (previous == 0) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

/** Walks the ContextWrapper chain to the hosting Activity, if there is one. */
private fun android.content.Context.findActivity(): Activity? {
    var ctx: android.content.Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
