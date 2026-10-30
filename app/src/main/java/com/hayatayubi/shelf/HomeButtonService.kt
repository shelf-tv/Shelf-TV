package com.hayatayubi.shelf

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Sends the remote's Home button to Shelf.
 *
 * Google TV routes Home through its own launcher regardless of the default home app,
 * so the only reliable hook without root is an accessibility key filter. It also
 * bounces back to Shelf if the stock home screen ever surfaces anyway (say, after a
 * system update re-enables it). It never reads screen content.
 */
class HomeButtonService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var frontPackage: CharSequence? = null

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) goHome()
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        frontPackage = event.packageName
        if (frontPackage in STOCK_HOMES) {
            // The stock home can finish its own launch after ours and land back on top,
            // so check again once things settle.
            goHome()
            for (delay in RECHECK_DELAYS_MS) {
                handler.postDelayed({ if (frontPackage in STOCK_HOMES) goHome() }, delay)
            }
        }
    }

    override fun onInterrupt() = Unit

    private fun goHome() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
    }

    companion object {
        private val RECHECK_DELAYS_MS = listOf(300L, 900L)
        private val STOCK_HOMES = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tungsten.setupwraith",
        )

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            val me = ComponentName(context, HomeButtonService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
