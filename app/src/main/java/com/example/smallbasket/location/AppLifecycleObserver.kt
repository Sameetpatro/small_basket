// File: app/src/main/java/com/example/smallbasket/location/AppLifecycleObserver.kt
package com.example.smallbasket.location

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Observes app lifecycle to handle foreground/background transitions
 */
class AppLifecycleObserver(
    private val context: Context,
    private val onForeground: () -> Unit,
    private val onBackground: () -> Unit
) : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "AppLifecycle"
    }

    private var isInForeground = false

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        if (!isInForeground) {
            isInForeground = true
            Log.d(TAG, "App entered foreground")
            onForeground()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        if (isInForeground) {
            isInForeground = false
            Log.d(TAG, "App entered background")
            onBackground()
        }
    }

    fun isInForeground(): Boolean = isInForeground
}