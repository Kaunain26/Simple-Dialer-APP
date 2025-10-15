package com.simplemobiletools.smsmessenger.extensions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.CONTACT_ID
import com.simplemobiletools.commons.helpers.IS_PRIVATE
import com.simplemobiletools.commons.helpers.SimpleContactsHelper
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.models.SimpleContact
import java.util.Locale

fun Activity.dialNumber(phoneNumber: String, callback: (() -> Unit)? = null) {
    hideKeyboard()
    Intent(Intent.ACTION_DIAL).apply {
        data = Uri.fromParts("tel", phoneNumber, null)

        try {
            startActivity(this)
            callback?.invoke()
        } catch (e: ActivityNotFoundException) {
            toast(com.simplemobiletools.commons.R.string.no_app_found)
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }
}

fun Activity.launchViewIntent(uri: Uri, mimetype: String, filename: String) {
    Intent().apply {
        action = Intent.ACTION_VIEW
        setDataAndType(uri, mimetype.lowercase(Locale.getDefault()))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        try {
            hideKeyboard()
            startActivity(this)
        } catch (e: ActivityNotFoundException) {
            val newMimetype = filename.getMimeType()
            if (newMimetype.isNotEmpty() && mimetype != newMimetype) {
                launchViewIntent(uri, newMimetype, filename)
            } else {
                toast(com.simplemobiletools.commons.R.string.no_app_found)
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }
}

private val SIMPLE_CONTACTS_PACKAGES = listOf(
    "app.trusted.callerid.sms",
    "app.trusted.callerid.sms.debug",
    "com.simplemobiletools.contacts.pro",
    "com.simplemobiletools.contacts.pro.debug",
)

fun Activity.startContactDetailsIntent(contact: SimpleContact) {
    val targetPackage = SIMPLE_CONTACTS_PACKAGES.firstOrNull { isPackageInstalled(it) }
    val isPrivateContact = contact.rawId > 1000000 && contact.contactId > 1000000 && contact.rawId == contact.contactId

    if (isPrivateContact && targetPackage != null) {
        Intent().apply {
            action = Intent.ACTION_VIEW
            putExtra(CONTACT_ID, contact.rawId)
            putExtra(IS_PRIVATE, true)
            setPackage(targetPackage)
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
