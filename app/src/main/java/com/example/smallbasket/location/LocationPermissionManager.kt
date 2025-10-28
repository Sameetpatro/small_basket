
// File: app/src/main/java/com/example/smallbasket/location/LocationPermissionManager.kt
package com.example.smallbasket.location

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.FragmentActivity

/**
 * Manages location permission requests with user-friendly dialogs
 * Only requests "While using the app" - NO background permission
 */
class LocationPermissionManager(private val activity: FragmentActivity) {

    companion object {
        private const val TAG = "PermissionManager"

        val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACTIVITY_RECOGNITION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private var onPermissionResult: ((Boolean) -> Unit)? = null

    /**
     * Set the permission launcher (call this in onCreate)
     */
    fun setPermissionLauncher(launcher: ActivityResultLauncher<Array<String>>) {
        this.permissionLauncher = launcher
    }

    /**
     * Check if all required permissions are granted
     */
    fun hasAllPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            androidx.core.content.ContextCompat.checkSelfPermission(
                activity,
                permission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Request permissions with rationale dialog if needed
     */
    fun requestPermissions(onResult: (Boolean) -> Unit) {
        this.onPermissionResult = onResult

        val missingPermissions = REQUIRED_PERMISSIONS.filter { permission ->
            androidx.core.content.ContextCompat.checkSelfPermission(
                activity,
                permission
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            onResult(true)
            return
        }

        // Check if we should show rationale
        val shouldShowRationale = missingPermissions.any { permission ->
            activity.shouldShowRequestPermissionRationale(permission)
        }

        if (shouldShowRationale) {
            showPermissionRationaleDialog {
                launchPermissionRequest(missingPermissions.toTypedArray())
            }
        } else {
            launchPermissionRequest(missingPermissions.toTypedArray())
        }
    }

    /**
     * Launch the permission request
     */
    private fun launchPermissionRequest(permissions: Array<String>) {
        permissionLauncher?.launch(permissions) ?: run {
            android.util.Log.e(TAG, "Permission launcher not set!")
        }
    }

    /**
     * Handle permission result (call this from the launcher callback)
     */
    fun handlePermissionResult(permissions: Map<String, Boolean>) {
        val allGranted = permissions.values.all { it }

        if (!allGranted) {
            // Some permissions denied
            val permanentlyDenied = permissions.entries.any { (permission, granted) ->
                !granted && !activity.shouldShowRequestPermissionRationale(permission)
            }

            if (permanentlyDenied) {
                showPermissionDeniedDialog()
            }
        }

        onPermissionResult?.invoke(allGranted)
        onPermissionResult = null
    }

    /**
     * Show rationale dialog explaining why we need permissions
     */
    private fun showPermissionRationaleDialog(onAccept: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Location Permission Required")
            .setMessage(
                "Small Basket needs location permission to:\n\n" +
                        "• Track your location for delivery coordination\n" +
                        "• Show nearby pickup and drop locations\n" +
                        "• Optimize delivery routes\n\n" +
                        "Your privacy is important. We only track location while the app is in use."
            )
            .setPositiveButton("Grant Permission") { dialog, _ ->
                dialog.dismiss()
                onAccept()
            }
            .setNegativeButton("Not Now") { dialog, _ ->
                dialog.dismiss()
                onPermissionResult?.invoke(false)
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Show dialog when permission is permanently denied
     */
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(activity)
            .setTitle("Permission Required")
            .setMessage(
                "Location permission is required for Small Basket to work properly.\n\n" +
                        "Please enable location permission in Settings."
            )
            .setPositiveButton("Open Settings") { dialog, _ ->
                dialog.dismiss()
                openAppSettings()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Open app settings
     */
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
    }

    /**
     * Show dialog if location services are disabled
     */
    fun showLocationServicesDialog() {
        AlertDialog.Builder(activity)
            .setTitle("Location Services Disabled")
            .setMessage(
                "Please enable location services in your device settings to use location tracking."
            )
            .setPositiveButton("Open Settings") { dialog, _ ->
                dialog.dismiss()
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                activity.startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}