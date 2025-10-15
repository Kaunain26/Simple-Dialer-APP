package com.simplemobiletools.dialer.extensions

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.contacts.Contact
import com.simplemobiletools.dialer.activities.DialerActivity
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.dialogs.SelectSIMDialog

fun SimpleActivity.startCallIntent(recipient: String) {
    if (isDefaultDialer()) {
        getHandleToUse(null, recipient) { handle ->
            launchCallIntent(recipient, handle)
        }
    } else {
        launchCallIntent(recipient, null)
    }
}

fun SimpleActivity.launchCreateNewContactIntent() {
    Intent().apply {
        action = Intent.ACTION_INSERT
        data = ContactsContract.Contacts.CONTENT_URI
        launchActivityIntent(this)
    }
}

fun BaseSimpleActivity.callContactWithSim(recipient: String, useMainSIM: Boolean) {
    handlePermission(PERMISSION_READ_PHONE_STATE) {
        val wantedSimIndex = if (useMainSIM) 0 else 1
        val handle = getAvailableSIMCardLabels().sortedBy { it.id }.getOrNull(wantedSimIndex)?.handle
        launchCallIntent(recipient, handle)
    }
}

private val SIMPLE_CONTACTS_PACKAGES = listOf(
    "app.trusted.callerid.sms",
    "app.trusted.callerid.sms.debug",
    "com.simplemobiletools.contacts.pro",
    "com.simplemobiletools.contacts.pro.debug",
)

// handle private contacts differently, only Simple Contacts can open them
fun Activity.startContactDetailsIntent(contact: Contact) {
    val targetPackage = SIMPLE_CONTACTS_PACKAGES.firstOrNull { isPackageInstalled(it) }
    val isPrivateContact = contact.rawId > 1000000 && contact.contactId > 1000000 && contact.rawId == contact.contactId

    if (isPrivateContact && targetPackage != null) {
        Intent().apply {
            action = Intent.ACTION_VIEW
            putExtra(CONTACT_ID, contact.rawId)
            putExtra(IS_PRIVATE, true)
            `package` = targetPackage
            setDataAndType(ContactsContract.Contacts.CONTENT_LOOKUP_URI, "vnd.android.cursor.dir/person")
            launchActivityIntent(this)
        }
    } else {
        ensureBackgroundThread {
            val lookupKey = SimpleContactsHelper(this).getContactLookupKey((contact).rawId.toString())
            val publicUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
            runOnUiThread {
                launchViewContactIntent(publicUri)
            }
        }
    }
}

// used at devices with multiple SIM cards
@SuppressLint("MissingPermission")
fun SimpleActivity.getHandleToUse(intent: Intent?, phoneNumber: String, callback: (handle: PhoneAccountHandle?) -> Unit) {
    handlePermission(PERMISSION_READ_PHONE_STATE) {
        if (it) {
            val defaultHandle = telecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
            when {
                intent?.hasExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE) == true -> callback(intent.getParcelableExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE)!!)
                config.getCustomSIM(phoneNumber) != null -> {
                    callback(config.getCustomSIM(phoneNumber))
                }

                defaultHandle != null -> callback(defaultHandle)
                else -> {
                    SelectSIMDialog(this, phoneNumber, onDismiss = {
                        if (this is DialerActivity) {
                            finish()
                        }
                    }) { handle ->
                        callback(handle)
                    }
                }
            }
        }
    }
}
