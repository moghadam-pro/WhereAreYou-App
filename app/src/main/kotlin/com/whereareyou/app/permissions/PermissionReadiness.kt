package com.whereareyou.app.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.whereareyou.app.R

/** Mirrors ANDROID_ARCHITECTURE.md section 14 "Permission readiness model". */
enum class ReadinessStatus { GRANTED, MISSING, NOT_YET_REQUIRED }

data class PermissionReadinessItem(
    val id: String,
    @StringRes val featureTitleRes: Int,
    @StringRes val permissionLabelRes: Int,
    @StringRes val explanationRes: Int,
    val status: ReadinessStatus,
    val permissions: List<String>,
)

object PermissionReadiness {

    /**
     * Core permissions needed for DadFinder's primary safety loop:
     * SMS commands, missed-call escalation, location fix, and local session notifications.
     */
    val CORE_PERMISSIONS: List<String> by lazy {
        buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun areAllGranted(context: Context, permissions: List<String>): Boolean {
        return permissions.all { isPermissionGranted(context, it) }
    }

    fun hasMissingCorePermissions(context: Context): Boolean {
        return !areAllGranted(context, CORE_PERMISSIONS)
    }

    fun currentSnapshot(context: Context): List<PermissionReadinessItem> {
        val smsGranted = areAllGranted(
            context,
            listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS),
        )
        val callLogGranted = areAllGranted(
            context,
            listOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE),
        )
        val locationForegroundGranted = areAllGranted(
            context,
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
        val locationBackgroundGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isPermissionGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            true
        }
        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
        val callPhoneGranted = isPermissionGranted(context, Manifest.permission.CALL_PHONE)

        return listOf(
            PermissionReadinessItem(
                id = "sms",
                featureTitleRes = R.string.perm_sms_title,
                permissionLabelRes = R.string.perm_sms_label,
                explanationRes = R.string.perm_sms_desc,
                status = if (smsGranted) ReadinessStatus.GRANTED else ReadinessStatus.MISSING,
                permissions = listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS),
            ),
            PermissionReadinessItem(
                id = "calls",
                featureTitleRes = R.string.perm_calls_title,
                permissionLabelRes = R.string.perm_calls_label,
                explanationRes = R.string.perm_calls_desc,
                status = if (callLogGranted) ReadinessStatus.GRANTED else ReadinessStatus.MISSING,
                permissions = listOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE),
            ),
            PermissionReadinessItem(
                id = "location_fg",
                featureTitleRes = R.string.perm_location_title,
                permissionLabelRes = R.string.perm_location_label,
                explanationRes = R.string.perm_location_desc,
                status = if (locationForegroundGranted) ReadinessStatus.GRANTED else ReadinessStatus.MISSING,
                permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            ),
            PermissionReadinessItem(
                id = "notifications",
                featureTitleRes = R.string.perm_notifications_title,
                permissionLabelRes = R.string.perm_notifications_label,
                explanationRes = R.string.perm_notifications_desc,
                status = if (notificationsGranted) ReadinessStatus.GRANTED else ReadinessStatus.MISSING,
                permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(Manifest.permission.POST_NOTIFICATIONS)
                } else emptyList(),
            ),
            PermissionReadinessItem(
                id = "location_bg",
                featureTitleRes = R.string.perm_bg_location_title,
                permissionLabelRes = R.string.perm_bg_location_label,
                explanationRes = R.string.perm_bg_location_desc,
                status = if (locationBackgroundGranted) ReadinessStatus.GRANTED else ReadinessStatus.MISSING,
                permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else emptyList(),
            ),
            PermissionReadinessItem(
                id = "call_phone",
                featureTitleRes = R.string.perm_call_phone_title,
                permissionLabelRes = R.string.perm_call_phone_label,
                explanationRes = R.string.perm_call_phone_desc,
                status = if (callPhoneGranted) ReadinessStatus.GRANTED else ReadinessStatus.NOT_YET_REQUIRED,
                permissions = listOf(Manifest.permission.CALL_PHONE),
            ),
        )
    }
}
