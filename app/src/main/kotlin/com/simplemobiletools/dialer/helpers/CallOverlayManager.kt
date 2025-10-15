package com.simplemobiletools.dialer.helpers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.simplemobiletools.commons.extensions.getFormattedDuration
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.models.CallContact
import com.simplemobiletools.dialer.helpers.CallContactAvatarHelper
import com.simplemobiletools.dialer.helpers.CallOverlayManager.CallOverlayState.*

object CallOverlayManager {
    private const val AUTO_DISMISS_DELAY = 7000L

    enum class CallOverlayState {
        INCOMING,
        ENDED,
        MISSED
    }

    private val handler = Handler(Looper.getMainLooper())
    private var currentView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null
    private var currentState: CallOverlayState? = null

    fun showIncoming(context: Context, contact: CallContact, subtitle: String?) {
        show(context, contact, INCOMING, 0, subtitle)
    }

    fun showEnded(context: Context, contact: CallContact, durationSeconds: Int, wasMissed: Boolean, subtitle: String?) {
        val state = if (wasMissed) MISSED else ENDED
        show(context, contact, state, durationSeconds, subtitle)
    }

    fun dismiss() {
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
    }

    private fun show(
        context: Context,
        contact: CallContact,
        state: CallOverlayState,
        durationSeconds: Int,
        secondaryInfo: String?
    ) {
        val appContext = context.applicationContext
        if (!appContext.config.showCallerOverlay) {
            return
        }

        if (!Settings.canDrawOverlays(appContext)) {
            return
        }

        if (windowManager == null) {
            windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }

        val overlayView = currentView ?: LayoutInflater.from(appContext).inflate(R.layout.view_call_overlay, null).also {
            bindClicks(appContext, it, contact)
            currentView = it
        }

        bindContent(appContext, overlayView, contact, state, durationSeconds, secondaryInfo)
        val params = ensureLayoutParams(appContext)
        if (overlayView.parent == null) {
            windowManager?.addView(overlayView, params)
        } else {
            windowManager?.updateViewLayout(overlayView, params)
        }

        currentState = state
        scheduleDismiss()
    }

    private fun ensureLayoutParams(context: Context): WindowManager.LayoutParams {
        val params = layoutParams
        if (params != null) {
            return params
        }

        val width = WindowManager.LayoutParams.MATCH_PARENT
        val height = WindowManager.LayoutParams.WRAP_CONTENT
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

        val newParams = WindowManager.LayoutParams(width, height, overlayType, flags, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = context.resources.getDimensionPixelSize(R.dimen.call_overlay_top_offset)
        }

        layoutParams = newParams
        return newParams
    }

    private fun bindClicks(context: Context, view: View, contact: CallContact) {
        view.findViewById<ImageButton>(R.id.call_overlay_close).setOnClickListener {
            dismiss()
        }

        view.findViewById<MaterialButton>(R.id.call_overlay_profile_action).setOnClickListener {
            openContactProfile(context, contact.number)
            dismiss()
        }

        val callAction = view.findViewById<LinearLayout>(R.id.call_overlay_call_action)
        val messageAction = view.findViewById<LinearLayout>(R.id.call_overlay_message_action)
        val editAction = view.findViewById<LinearLayout>(R.id.call_overlay_edit_action)

        callAction.setOnClickListener {
            if (!contact.number.isNullOrEmpty()) {
                openCallLog(context, contact.number)
                dismiss()
            } else {
                context.toast(R.string.no_number_available)
            }
        }

        messageAction.setOnClickListener {
            if (!contact.number.isNullOrEmpty()) {
                openMessageThread(context, contact.number)
                dismiss()
            } else {
                context.toast(R.string.no_number_available)
            }
        }

        editAction.setOnClickListener {
            if (!contact.number.isNullOrEmpty()) {
                editContact(context, contact.number)
                dismiss()
            } else {
                context.toast(R.string.no_number_available)
            }
        }
    }

    private fun bindContent(
        context: Context,
        view: View,
        contact: CallContact,
        state: CallOverlayState,
        durationSeconds: Int,
        secondaryInfo: String?
    ) {
        view.findViewById<TextView>(R.id.call_overlay_name).text = when {
            contact.name.isNotEmpty() -> contact.name
            contact.number.isNotEmpty() -> contact.number
            else -> context.getString(R.string.unknown_caller)
        }

        val subtitle = view.findViewById<TextView>(R.id.call_overlay_subtitle)
        val secondaryText = when {
            !secondaryInfo.isNullOrEmpty() -> secondaryInfo
            contact.numberLabel.isNotEmpty() -> contact.numberLabel
            else -> ""
        }
        subtitle.apply {
            text = secondaryText
            visibility = if (secondaryText.isNotEmpty()) View.VISIBLE else View.GONE
        }

        view.findViewById<TextView>(R.id.call_overlay_number).apply {
            text = contact.number.takeIf { it.isNotEmpty() } ?: context.getString(R.string.unknown_number)
        }

        val statusView = view.findViewById<TextView>(R.id.call_overlay_status)
        statusView.text = when (state) {
            INCOMING -> context.getString(R.string.call_overlay_incoming_title)
            MISSED -> context.getString(R.string.call_overlay_missed_title)
            ENDED -> {
                if (durationSeconds > 0) {
                    context.getString(R.string.call_overlay_ended_with_duration, durationSeconds.getFormattedDuration())
                } else {
                    context.getString(R.string.call_overlay_ended_title)
                }
            }
        }

        val avatar = view.findViewById<ShapeableImageView>(R.id.call_overlay_avatar)
        val callContactAvatarHelper = CallContactAvatarHelper(context)
        val bitmap = callContactAvatarHelper.getCallContactAvatar(contact)
        if (bitmap != null) {
            avatar.setImageBitmap(bitmap)
        } else {
            avatar.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_call_solid))
        }

        val callAction = view.findViewById<LinearLayout>(R.id.call_overlay_call_action)
        val messageAction = view.findViewById<LinearLayout>(R.id.call_overlay_message_action)
        val editAction = view.findViewById<LinearLayout>(R.id.call_overlay_edit_action)

        val hasNumber = contact.number.isNotEmpty()
        callAction.isEnabled = hasNumber
        messageAction.isEnabled = hasNumber
        editAction.isEnabled = hasNumber
        setActionEnabled(callAction, hasNumber)
        setActionEnabled(messageAction, hasNumber)
        setActionEnabled(editAction, hasNumber)
    }

    private fun setActionEnabled(view: View, enabled: Boolean) {
        view.alpha = if (enabled) 1f else 0.4f
        view.isClickable = enabled
    }

    private fun scheduleDismiss() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ removeOverlay() }, AUTO_DISMISS_DELAY)
    }

    private fun removeOverlay() {
        val overlayView = currentView ?: return
        try {
            if (overlayView.parent != null) {
                windowManager?.removeView(overlayView)
            }
        } catch (ignored: Exception) {
        }
        currentView = null
        currentState = null
    }

    private fun openContactProfile(context: Context, number: String) {
        if (number.isEmpty()) {
            context.toast(R.string.no_number_available)
            return
        }

        val intent = Intent(ContactsContract.Intents.SHOW_OR_CREATE_CONTACT, Uri.fromParts("tel", number, null)).apply {
            putExtra(ContactsContract.Intents.EXTRA_FORCE_CREATE, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        startActivitySafe(context, intent)
    }

    private fun openCallLog(context: Context, number: String) {
        val uri = Uri.withAppendedPath(CallLog.Calls.CONTENT_FILTER_URI, Uri.encode(number))
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivitySafe(context, intent)
    }

    private fun openMessageThread(context: Context, number: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("sms:$number")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivitySafe(context, intent)
    }

    private fun editContact(context: Context, number: String) {
        val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivitySafe(context, intent)
    }

    private fun startActivitySafe(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (ignored: ActivityNotFoundException) {
            context.toast(R.string.no_matching_app_found)
        } catch (ignored: Exception) {
            context.toast(R.string.unknown_error_occurred)
        }
    }
}
