package com.simplemobiletools.dialer.activities

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.telecom.TelecomManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.card.MaterialCardView
import com.simplemobiletools.commons.extensions.launchViewIntent
import com.simplemobiletools.commons.helpers.REQUEST_CODE_SET_DEFAULT_CALLER_ID
import com.simplemobiletools.commons.helpers.REQUEST_CODE_SET_DEFAULT_DIALER
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.databinding.ActivityPermissionOnboardingBinding
import com.simplemobiletools.dialer.databinding.ItemPermissionToggleBinding
import com.simplemobiletools.dialer.helpers.PermissionManager
import com.simplemobiletools.dialer.helpers.PermissionManager.isDefaultSmsApp

private const val REQUEST_CODE_SET_DEFAULT_SMS = 2030
private const val EXTRA_OPEN_MAIN_ON_COMPLETE = "open_main_on_complete"

class PermissionOnboardingActivity : SimpleActivity() {

    private lateinit var binding: ActivityPermissionOnboardingBinding

    private val defaultDialerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (PermissionManager.isDefaultPhoneApp(this)) {
                requestPhonePermissions { updatePermissionStates() }
            } else {
                updatePermissionStates()
            }
        }

    private val callerIdLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            requestCallerIdPermissions { updatePermissionStates() }
        }

    private val defaultSmsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (isDefaultSmsApp()) {
                requestSmsPermissions { updatePermissionStates() }
            } else {
                updatePermissionStates()
            }
        }

    private var openMainOnComplete = false

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        openMainOnComplete = intent?.getBooleanExtra(EXTRA_OPEN_MAIN_ON_COMPLETE, false) == true

        setupPermissionRows()
        setupButtons()
        updatePermissionStates()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
    }

    override fun onBackPressed() {
        // Disable back navigation to ensure the user completes the required setup.
    }

    private fun setupPermissionRows() {
        with(binding.defaultPhone) {
            permissionIcon.setImageResource(R.drawable.ic_permission_phone)
            permissionTitle.setText(R.string.permission_default_phone_title)
            permissionDescription.setText(R.string.permission_default_phone_desc)
        }
        with(binding.defaultSms) {
            permissionIcon.setImageResource(R.drawable.ic_permission_sms)
            permissionTitle.setText(R.string.permission_default_sms_title)
            permissionDescription.setText(R.string.permission_default_sms_desc)
        }
        with(binding.callerId) {
            permissionIcon.setImageResource(R.drawable.ic_permission_shield)
            permissionTitle.setText(R.string.permission_caller_id_title)
            permissionDescription.setText(R.string.permission_caller_id_desc)
        }

        binding.defaultPhoneCard.setOnClickListener { requestDefaultDialer() }
        binding.defaultSmsCard.setOnClickListener { requestDefaultSmsApp() }
        binding.callerIdCard.setOnClickListener { requestCallerIdRole() }
        binding.defaultPhone.root.setOnClickListener { requestDefaultDialer() }
        binding.defaultSms.root.setOnClickListener { requestDefaultSmsApp() }
        binding.callerId.root.setOnClickListener { requestCallerIdRole() }
    }

    private fun setupButtons() {
        binding.continueButton.setOnClickListener { completeFlow() }
        binding.privacyPolicyLink.setOnClickListener {
            launchViewIntent(getString(R.string.permission_onboarding_privacy_url))
        }
    }

    private fun requestDefaultDialer() {
        if (!PermissionManager.isDefaultPhoneApp(this)) {
            requestDialerRole()
        } else {
            requestPhonePermissions { updatePermissionStates() }
        }
    }

    private fun requestCallerIdRole() {
        if (!PermissionManager.isCallerIdEnabled(this)) {
            if (isQPlus()) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                    callerIdLauncher.launch(intent)
                    return
                }
            }
        }
        requestCallerIdPermissions { updatePermissionStates() }
    }

    private fun requestDefaultSmsApp() {
        if (!isDefaultSmsApp()) {
            requestSmsRole()
        } else {
            requestSmsPermissions { updatePermissionStates() }
        }
    }

    private fun requestPhonePermissions(onComplete: () -> Unit) {
        requestPermissionsSequential(PermissionManager.phonePermissionIds()) { onComplete() }
    }

    private fun requestSmsPermissions(onComplete: () -> Unit) {
        requestPermissionsSequential(PermissionManager.smsPermissionIds()) {
            requestPermissionsSequential(
                PermissionManager.smsOptionalPermissionIds(),
                ignoreFailure = true
            ) { onComplete() }
        }
    }

    private fun requestCallerIdPermissions(onComplete: () -> Unit) {
        requestPermissionsSequential(PermissionManager.callerIdPermissionIds()) { onComplete() }
    }

    private fun requestDialerRole() {
        if (isQPlus()) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                defaultDialerLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                return
            }
        }

        @Suppress("DEPRECATION")
        Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            .also {
                try {
                    startActivityForResult(it, REQUEST_CODE_SET_DEFAULT_DIALER)
                } catch (ignored: Exception) {
                    updatePermissionStates()
                }
            }
    }

    private fun requestSmsRole() {
        if (isQPlus()) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                defaultSmsLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
                return
            }
        }

        val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
            putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        }
        try {
            startActivityForResult(intent, REQUEST_CODE_SET_DEFAULT_SMS)
        } catch (ignored: Exception) {
            updatePermissionStates()
        }
    }

    private fun requestPermissionsSequential(
        permissionIds: IntArray,
        index: Int = 0,
        ignoreFailure: Boolean = false,
        onComplete: () -> Unit
    ) {
        if (index >= permissionIds.size) {
            onComplete()
            return
        }

        handlePermission(permissionIds[index]) { granted ->
            if (granted || ignoreFailure) {
                requestPermissionsSequential(permissionIds, index + 1, ignoreFailure, onComplete)
            } else {
                updatePermissionStates()
            }
        }
    }

    private fun updatePermissionStates() {
        val phoneGranted = PermissionManager.isDefaultPhoneApp(this)
        val smsGranted = isDefaultSmsApp()
        val callerGranted = PermissionManager.isCallerIdEnabled(this)

        setPermissionState(binding.defaultPhoneCard, binding.defaultPhone, phoneGranted)
        setPermissionState(binding.defaultSmsCard, binding.defaultSms, smsGranted)
        setPermissionState(binding.callerIdCard, binding.callerId, callerGranted)

        val allGranted = phoneGranted && smsGranted && callerGranted
        applyContinueButtonState(allGranted)
    }

    private fun setPermissionState(card: MaterialCardView, binding: ItemPermissionToggleBinding, granted: Boolean) {
        binding.permissionSwitch.isEnabled = false
        binding.permissionSwitch.isChecked = granted
        val strokeColor = if (granted) {
            ContextCompat.getColor(this, R.color.color_primary)
        } else {
            ContextCompat.getColor(this, R.color.permission_card_stroke)
        }
        card.strokeColor = strokeColor
        setTitleEnabled(binding.permissionTitle, granted)
    }

    private fun setTitleEnabled(textView: TextView, enabled: Boolean) {
        val colorRes = if (enabled) R.color.permission_text_primary else R.color.permission_text_secondary
        textView.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun applyContinueButtonState(enabled: Boolean) {
        binding.continueButton.isEnabled = enabled
        val tintColor = if (enabled) R.color.permission_button_enabled else R.color.permission_button_disabled
        binding.continueButton.backgroundTintList = ContextCompat.getColorStateList(this, tintColor)
        val textColor = if (enabled) android.R.color.white else R.color.permission_text_secondary
        binding.continueButton.setTextColor(ContextCompat.getColor(this, textColor))
        binding.continueButton.alpha = if (enabled) 1f else 0.8f
    }

    private fun completeFlow() {
        if (PermissionManager.shouldShowPermissionScreen(this)) {
            updatePermissionStates()
            return
        }

        if (openMainOnComplete) {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
        } else {
            setResult(Activity.RESULT_OK)
        }
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_SET_DEFAULT_DIALER -> {
                if (PermissionManager.isDefaultPhoneApp(this)) {
                    requestPhonePermissions { updatePermissionStates() }
                } else {
                    updatePermissionStates()
                }
            }
            REQUEST_CODE_SET_DEFAULT_CALLER_ID -> {
                requestCallerIdPermissions { updatePermissionStates() }
            }
            REQUEST_CODE_SET_DEFAULT_SMS -> {
                if (isDefaultSmsApp()) {
                    requestSmsPermissions { updatePermissionStates() }
                } else {
                    updatePermissionStates()
                }
            }
        }
    }

    companion object {
        fun start(activity: Activity, openMainOnComplete: Boolean) {
            val intent = Intent(activity, PermissionOnboardingActivity::class.java).apply {
                putExtra(EXTRA_OPEN_MAIN_ON_COMPLETE, openMainOnComplete)
            }
            activity.startActivity(intent)
        }
    }
}
