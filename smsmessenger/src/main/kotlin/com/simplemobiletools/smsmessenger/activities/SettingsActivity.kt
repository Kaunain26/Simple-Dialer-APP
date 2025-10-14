package com.simplemobiletools.smsmessenger.activities

import android.annotation.TargetApi
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.simplemobiletools.commons.activities.ManageBlockedNumbersActivity
import com.simplemobiletools.commons.dialogs.*
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.databinding.ActivitySettings2Binding
import com.simplemobiletools.smsmessenger.dialogs.ExportMessagesDialog
import com.simplemobiletools.smsmessenger.extensions.config_sms
import com.simplemobiletools.smsmessenger.extensions.emptyMessagesRecycleBin
import com.simplemobiletools.smsmessenger.extensions.messagesDB
import com.simplemobiletools.smsmessenger.helpers.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {
    private var blockedNumbersAtPause = -1
    private var recycleBinMessages = 0
    private val messagesFileType = "application/json"
    private val messageImportFileTypes = listOf("application/json", "application/xml", "text/xml")

    private val binding by viewBinding(ActivitySettings2Binding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        updateMaterialActivityViews(
            mainCoordinatorLayout = binding.settingsCoordinator,
            nestedView = binding.settingsHolder,
            useTransparentNavigation = true,
            useTopSearchMenu = false
        )
        setupMaterialScrollListener(scrollingView = binding.settingsNestedScrollview, toolbar = binding.settingsToolbar)
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.settingsToolbar, NavigationIcon.Arrow)

        setupCustomizeNotifications()
        setupUseEnglish()
        setupLanguage()
        setupManageBlockedNumbers()
        setupManageBlockedKeywords()
        setupChangeDateTimeFormat()
        setupFontSize()
        setupShowCharacterCounter()
        setupUseSimpleCharacters()
        setupSendOnEnter()
        setupEnableDeliveryReports()
        setupSendLongMessageAsMMS()
        setupGroupMessageAsMMS()
        setupLockScreenVisibility()
        setupMMSFileSizeLimit()
        setupUseRecycleBin()
        setupEmptyRecycleBin()
        setupAppPasswordProtection()
        setupMessagesExport()
        setupMessagesImport()
        updateTextColors(binding.settingsNestedScrollview)

        if (blockedNumbersAtPause != -1 && blockedNumbersAtPause != getBlockedNumbers().hashCode()) {
            refreshMessages()
        }

        arrayOf(
            binding.settingsGeneralSettingsLabel,
            binding.settingsOutgoingMessagesLabel,
            binding.settingsNotificationsLabel,
            binding.settingsRecycleBinLabel,
            binding.settingsSecurityLabel,
            binding.settingsMigratingLabel
        ).forEach {
            it.setTextColor(getProperPrimaryColor())
        }
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            MessagesImporter(this).importMessages(uri)
        }
    }

    private val saveDocument = registerForActivityResult(ActivityResultContracts.CreateDocument(messagesFileType)) { uri ->
        if (uri != null) {
            toast(R.string.exporting)
            exportMessages(uri)
        }
    }

    private fun setupMessagesExport() {
        binding.settingsExportMessagesHolder.setOnClickListener {
            ExportMessagesDialog(this) { fileName ->
                saveDocument.launch(fileName)
            }
        }
    }

    private fun setupMessagesImport() {
        binding.settingsImportMessagesHolder.setOnClickListener {
            getContent.launch(messageImportFileTypes.toTypedArray())
        }
    }

    private fun exportMessages(uri: Uri) {
        ensureBackgroundThread {
            try {
                MessagesReader(this).getMessagesToExport(config_sms.exportSms, config_sms.exportMms) { messagesToExport ->
                    if (messagesToExport.isEmpty()) {
                        toast(R.string.no_entries_for_exporting)
                        return@getMessagesToExport
                    }
                    val json = Json { encodeDefaults = true }
                    val jsonString = json.encodeToString(messagesToExport)
                    val outputStream = contentResolver.openOutputStream(uri)!!

                    outputStream.use {
                        it.write(jsonString.toByteArray())
                    }
                    toast(R.string.exporting_successful)
                }
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        blockedNumbersAtPause = getBlockedNumbers().hashCode()
    }

    private fun setupCustomizeNotifications() = binding.apply {
        settingsCustomizeNotificationsHolder.beVisibleIf(isOreoPlus())
        settingsCustomizeNotificationsHolder.setOnClickListener {
            launchCustomizeNotificationsIntent()
        }
    }

    private fun setupUseEnglish() = binding.apply {
        settingsUseEnglishHolder.beVisibleIf((config_sms.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        settingsUseEnglish.isChecked = config_sms.useEnglish
        settingsUseEnglishHolder.setOnClickListener {
            settingsUseEnglish.toggle()
            config_sms.useEnglish = settingsUseEnglish.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() = binding.apply {
        settingsLanguage.text = Locale.getDefault().displayLanguage
        settingsLanguageHolder.beVisibleIf(isTiramisuPlus())
        settingsLanguageHolder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    // support for device-wise blocking came on Android 7, rely only on that
    @TargetApi(Build.VERSION_CODES.N)
    private fun setupManageBlockedNumbers() = binding.apply {
        settingsManageBlockedNumbers.text = addLockedLabelIfNeeded(R.string.manage_blocked_numbers)
        settingsManageBlockedNumbersHolder.beVisibleIf(isNougatPlus())

        settingsManageBlockedNumbersHolder.setOnClickListener {
            Intent(this@SettingsActivity, ManageBlockedNumbersActivity::class.java).apply {
                startActivity(this)
            }
        }
    }

    private fun setupManageBlockedKeywords() = binding.apply {
        settingsManageBlockedKeywords.text = addLockedLabelIfNeeded(R.string.manage_blocked_keywords)

        settingsManageBlockedKeywordsHolder.setOnClickListener {
            Intent(this@SettingsActivity, ManageBlockedKeywordsActivity::class.java).apply {
                startActivity(this)
            }
        }
    }

    private fun setupChangeDateTimeFormat() = binding.apply {
        settingsChangeDateTimeFormatHolder.setOnClickListener {
            ChangeDateTimeFormatDialog(this@SettingsActivity) {
                refreshMessages()
            }
        }
    }

    private fun setupFontSize() = binding.apply {
        settingsFontSize.text = getFontSizeText()
        settingsFontSizeHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(FONT_SIZE_SMALL, getString(R.string.small)),
                RadioItem(FONT_SIZE_MEDIUM, getString(R.string.medium)),
                RadioItem(FONT_SIZE_LARGE, getString(R.string.large)),
                RadioItem(FONT_SIZE_EXTRA_LARGE, getString(R.string.extra_large))
            )

            RadioGroupDialog(this@SettingsActivity, items, config_sms.fontSize) {
                config_sms.fontSize = it as Int
                settingsFontSize.text = getFontSizeText()
            }
        }
    }

    private fun setupShowCharacterCounter() = binding.apply {
        settingsShowCharacterCounter.isChecked = config_sms.showCharacterCounter
        settingsShowCharacterCounterHolder.setOnClickListener {
            settingsShowCharacterCounter.toggle()
            config_sms.showCharacterCounter = settingsShowCharacterCounter.isChecked
        }
    }

    private fun setupUseSimpleCharacters() = binding.apply {
        settingsUseSimpleCharacters.isChecked = config_sms.useSimpleCharacters
        settingsUseSimpleCharactersHolder.setOnClickListener {
            settingsUseSimpleCharacters.toggle()
            config_sms.useSimpleCharacters = settingsUseSimpleCharacters.isChecked
        }
    }

    private fun setupSendOnEnter() = binding.apply {
        settingsSendOnEnter.isChecked = config_sms.sendOnEnter
        settingsSendOnEnterHolder.setOnClickListener {
            settingsSendOnEnter.toggle()
            config_sms.sendOnEnter = settingsSendOnEnter.isChecked
        }
    }

    private fun setupEnableDeliveryReports() = binding.apply {
        settingsEnableDeliveryReports.isChecked = config_sms.enableDeliveryReports
        settingsEnableDeliveryReportsHolder.setOnClickListener {
            settingsEnableDeliveryReports.toggle()
            config_sms.enableDeliveryReports = settingsEnableDeliveryReports.isChecked
        }
    }

    private fun setupSendLongMessageAsMMS() = binding.apply {
        settingsSendLongMessageMms.isChecked = config_sms.sendLongMessageMMS
        settingsSendLongMessageMmsHolder.setOnClickListener {
            settingsSendLongMessageMms.toggle()
            config_sms.sendLongMessageMMS = settingsSendLongMessageMms.isChecked
        }
    }

    private fun setupGroupMessageAsMMS() = binding.apply {
        settingsSendGroupMessageMms.isChecked = config_sms.sendGroupMessageMMS
        settingsSendGroupMessageMmsHolder.setOnClickListener {
            settingsSendGroupMessageMms.toggle()
            config_sms.sendGroupMessageMMS = settingsSendGroupMessageMms.isChecked
        }
    }

    private fun setupLockScreenVisibility() = binding.apply {
        settingsLockScreenVisibility.text = getLockScreenVisibilityText()
        settingsLockScreenVisibilityHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(LOCK_SCREEN_SENDER_MESSAGE, getString(R.string.sender_and_message)),
                RadioItem(LOCK_SCREEN_SENDER, getString(R.string.sender_only)),
                RadioItem(LOCK_SCREEN_NOTHING, getString(R.string.nothing)),
            )

            RadioGroupDialog(this@SettingsActivity, items, config_sms.lockScreenVisibilitySetting) {
                config_sms.lockScreenVisibilitySetting = it as Int
                settingsLockScreenVisibility.text = getLockScreenVisibilityText()
            }
        }
    }

    private fun getLockScreenVisibilityText() = getString(
        when (config_sms.lockScreenVisibilitySetting) {
            LOCK_SCREEN_SENDER_MESSAGE -> R.string.sender_and_message
            LOCK_SCREEN_SENDER -> R.string.sender_only
            else -> R.string.nothing
        }
    )

    private fun setupMMSFileSizeLimit() = binding.apply {
        settingsMmsFileSizeLimit.text = getMMSFileLimitText()
        settingsMmsFileSizeLimitHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(7, getString(R.string.mms_file_size_limit_none), FILE_SIZE_NONE),
                RadioItem(6, getString(R.string.mms_file_size_limit_2mb), FILE_SIZE_2_MB),
                RadioItem(5, getString(R.string.mms_file_size_limit_1mb), FILE_SIZE_1_MB),
                RadioItem(4, getString(R.string.mms_file_size_limit_600kb), FILE_SIZE_600_KB),
                RadioItem(3, getString(R.string.mms_file_size_limit_300kb), FILE_SIZE_300_KB),
                RadioItem(2, getString(R.string.mms_file_size_limit_200kb), FILE_SIZE_200_KB),
                RadioItem(1, getString(R.string.mms_file_size_limit_100kb), FILE_SIZE_100_KB),
            )

            val checkedItemId = items.find { it.value == config_sms.mmsFileSizeLimit }?.id ?: 7
            RadioGroupDialog(this@SettingsActivity, items, checkedItemId) {
                config_sms.mmsFileSizeLimit = it as Long
                settingsMmsFileSizeLimit.text = getMMSFileLimitText()
            }
        }
    }

    private fun setupUseRecycleBin() = binding.apply {
        updateRecycleBinButtons()
        settingsUseRecycleBin.isChecked = config_sms.useRecycleBin
        settingsUseRecycleBinHolder.setOnClickListener {
            settingsUseRecycleBin.toggle()
            config_sms.useRecycleBin = settingsUseRecycleBin.isChecked
            updateRecycleBinButtons()
        }
    }

    private fun updateRecycleBinButtons() = binding.apply {
        settingsEmptyRecycleBinHolder.beVisibleIf(config_sms.useRecycleBin)
    }

    private fun setupEmptyRecycleBin() = binding.apply {
        ensureBackgroundThread {
            recycleBinMessages = messagesDB.getArchivedCount()
            runOnUiThread {
                settingsEmptyRecycleBinSize.text =
                    resources.getQuantityString(R.plurals.delete_messages, recycleBinMessages, recycleBinMessages)
            }
        }

        settingsEmptyRecycleBinHolder.setOnClickListener {
            if (recycleBinMessages == 0) {
                toast(R.string.recycle_bin_empty)
            } else {
                ConfirmationDialog(
                    activity = this@SettingsActivity,
                    message = "",
                    messageId = R.string.empty_recycle_bin_messages_confirmation,
                    positive = R.string.yes,
                    negative = R.string.no
                ) {
                    ensureBackgroundThread {
                        emptyMessagesRecycleBin()
                    }
                    recycleBinMessages = 0
                    settingsEmptyRecycleBinSize.text =
                        resources.getQuantityString(R.plurals.delete_messages, recycleBinMessages, recycleBinMessages)
                }
            }
        }
    }

    private fun setupAppPasswordProtection() = binding.apply {
        settingsAppPasswordProtection.isChecked = config_sms.isAppPasswordProtectionOn
        settingsAppPasswordProtectionHolder.setOnClickListener {
            val tabToShow = if (config_sms.isAppPasswordProtectionOn) config_sms.appProtectionType else SHOW_ALL_TABS
            SecurityDialog(this@SettingsActivity, config_sms.appPasswordHash, tabToShow) { hash, type, success ->
                if (success) {
                    val hasPasswordProtection = config_sms.isAppPasswordProtectionOn
                    settingsAppPasswordProtection.isChecked = !hasPasswordProtection
                    config_sms.isAppPasswordProtectionOn = !hasPasswordProtection
                    config_sms.appPasswordHash = if (hasPasswordProtection) "" else hash
                    config_sms.appProtectionType = type

                    if (config_sms.isAppPasswordProtectionOn) {
                        val confirmationTextId = if (config_sms.appProtectionType == PROTECTION_FINGERPRINT) {
                            R.string.fingerprint_setup_successfully
                        } else {
                            R.string.protection_setup_successfully
                        }

                        ConfirmationDialog(this@SettingsActivity, "", confirmationTextId, R.string.ok, 0) { }
                    }
                }
            }
        }
    }

    private fun getMMSFileLimitText() = getString(
        when (config_sms.mmsFileSizeLimit) {
            FILE_SIZE_100_KB -> R.string.mms_file_size_limit_100kb
            FILE_SIZE_200_KB -> R.string.mms_file_size_limit_200kb
            FILE_SIZE_300_KB -> R.string.mms_file_size_limit_300kb
            FILE_SIZE_600_KB -> R.string.mms_file_size_limit_600kb
            FILE_SIZE_1_MB -> R.string.mms_file_size_limit_1mb
            FILE_SIZE_2_MB -> R.string.mms_file_size_limit_2mb
            else -> R.string.mms_file_size_limit_none
        }
    )
}
