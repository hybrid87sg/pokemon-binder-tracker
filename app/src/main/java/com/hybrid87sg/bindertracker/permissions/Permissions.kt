package com.hybrid87sg.bindertracker.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * The phone permissions the app needs before it can be used:
 * - Camera: to photograph binder pages (the scan feature).
 * - Install apps: so the app can install its own updates.
 */
object Permissions {
    fun hasCamera(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun canInstallUpdates(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()
}
