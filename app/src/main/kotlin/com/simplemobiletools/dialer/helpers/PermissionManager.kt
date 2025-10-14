package com.simplemobiletools.dialer.helpers

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.provider.Telephony
import androidx.core.content.ContextCompat.getSystemService
import com.simplemobiletools.commons.extensions.hasPermission
import com.simplemobiletools.commons.extensions.isDefaultDialer
import com.simplemobiletools.commons.extensions.telecomManager
import com.simplemobiletools.commons.helpers.PERMISSION_CALL_PHONE
import com.simplemobiletools.commons.helpers.PERMISSION_READ_CALL_LOG
import com.simplemobiletools.commons.helpers.PERMISSION_READ_CONTACTS
import com.simplemobiletools.commons.helpers.PERMISSION_READ_PHONE_STATE
import com.simplemobiletools.commons.helpers.PERMISSION_READ_SMS
import com.simplemobiletools.commons.helpers.PERMISSION_SEND_SMS
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.dialer.activities.PermissionOnboardingActivity

object PermissionManager {

    private val phonePermissions = intArrayOf(
        PERMISSION_READ_PHONE_STATE,
        PERMISSION_CALL_PHONE
    )

    private val smsPermissions = intArrayOf(
        PERMISSION_READ_SMS,
        PERMISSION_SEND_SMS
    )

    private val smsOptionalPermissions = intArrayOf(
        PERMISSION_READ_CONTACTS
    )

    private val callerIdPermissions = intArrayOf(
        PERMISSION_READ_CALL_LOG
    )

    fun phonePermissionIds(): IntArray = phonePermissions

    fun smsPermissionIds(): IntArray = smsPermissions

    fun smsOptionalPermissionIds(): IntArray = smsOptionalPermissions

    fun callerIdPermissionIds(): IntArray = callerIdPermissions

    fun isDefaultPhoneApp(context: Context): Boolean = context.isDefaultDialer()

    fun hasPhoneRuntimePermissions(context: Context): Boolean = phonePermissions.all { context.hasPermission(it) }

    fun isPhoneSetupComplete(context: Context): Boolean = isDefaultPhoneApp(context) && hasPhoneRuntimePermissions(context)


    fun Context.isDefaultSmsApp(): Boolean {
        return if (!packageName.startsWith("com.simplemobiletools.dialer")) {
            true
        } else if ((packageName.startsWith("com.simplemobiletools.dialer")) && isQPlus()) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager!!.isRoleAvailable(RoleManager.ROLE_SMS) && roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            telecomManager.defaultDialerPackage == packageName
        }
    }

    fun hasSmsRuntimePermissions(context: Context): Boolean = smsPermissions.all { context.hasPermission(it) }

    fun isSmsSetupComplete(context: Context): Boolean = context.isDefaultSmsApp() && hasSmsRuntimePermissions(context)

    fun hasCallerIdRuntimePermissions(context: Context): Boolean = callerIdPermissions.all { context.hasPermission(it) }

    fun isCallerIdEnabled(context: Context): Boolean = hasCallerIdRuntimePermissions(context)

    fun shouldShowPermissionScreen(context: Context): Boolean {
        return !(isPhoneSetupComplete(context) && isSmsSetupComplete(context) && isCallerIdEnabled(context))
    }

    fun enforcePermissions(activity: Activity) {
        if (activity is PermissionOnboardingActivity) {
            return
        }

        if (shouldShowPermissionScreen(activity)) {
            PermissionOnboardingActivity.start(activity, openMainOnComplete = false)
            activity.finish()
        }
    }
}
