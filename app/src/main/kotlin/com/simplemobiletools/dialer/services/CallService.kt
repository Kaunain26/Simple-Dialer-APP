package com.simplemobiletools.dialer.services

import android.app.KeyguardManager
import android.content.Context
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.os.Handler
import android.os.Looper
import com.simplemobiletools.dialer.activities.CallActivity
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.extensions.getCallDuration
import com.simplemobiletools.dialer.extensions.getStateCompat
import com.simplemobiletools.dialer.extensions.isOutgoing
import com.simplemobiletools.dialer.extensions.powerManager
import com.simplemobiletools.dialer.helpers.CallManager
import com.simplemobiletools.dialer.helpers.CallNotificationManager
import com.simplemobiletools.dialer.helpers.NoCall
import com.simplemobiletools.dialer.helpers.CallOverlayManager
import com.simplemobiletools.dialer.helpers.getCallContact
import com.simplemobiletools.dialer.models.CallContact

class CallService : InCallService() {
    private val callNotificationManager by lazy { CallNotificationManager(this) }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private val callListener = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                callNotificationManager.cancelNotification()
            } else {
                callNotificationManager.setupNotification()
            }
            updateOverlayState(call, call.getStateCompat())
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(call)
        CallManager.inCallService = this
        call.registerCallback(callListener)

        val isScreenLocked = (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked
        if (!powerManager.isInteractive || call.isOutgoing() || isScreenLocked || config.alwaysShowFullscreen) {
            try {
                callNotificationManager.setupNotification(true)
                startActivity(CallActivity.getStartIntent(this))
            } catch (e: Exception) {
                // seems like startActivity can throw AndroidRuntimeException and ActivityNotFoundException, not yet sure when and why, lets show a notification
                callNotificationManager.setupNotification()
            }
        } else {
            callNotificationManager.setupNotification()
        }

        updateOverlayState(call, call.getStateCompat())
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callListener)
        val wasPrimaryCall = call == CallManager.getPrimaryCall()
        CallManager.onCallRemoved(call)
        if (CallManager.getPhoneState() == NoCall) {
            CallManager.inCallService = null
            callNotificationManager.cancelNotification()
        } else {
            callNotificationManager.setupNotification()
            if (wasPrimaryCall) {
                startActivity(CallActivity.getStartIntent(this))
            }
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        if (audioState != null) {
            CallManager.onAudioStateChanged(audioState)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        callNotificationManager.cancelNotification()
        CallOverlayManager.dismiss()
    }

    private fun updateOverlayState(call: Call, state: Int) {
        when (state) {
            Call.STATE_RINGING -> {
                if (!call.isOutgoing()) {
                    showIncomingOverlay(call)
                }
            }
            Call.STATE_DISCONNECTED -> showEndedOverlay(call)
            Call.STATE_ACTIVE,
            Call.STATE_CONNECTING,
            Call.STATE_DIALING,
            Call.STATE_HOLDING -> CallOverlayManager.dismiss()
        }
    }

    private fun showIncomingOverlay(call: Call) {
        loadCallContact(call) { contact, subtitle ->
            CallOverlayManager.showIncoming(this, contact, subtitle)
        }
    }

    private fun showEndedOverlay(call: Call) {
        val durationSeconds = call.getCallDuration()
        val wasMissed = isMissedCall(call, durationSeconds)
        loadCallContact(call) { contact, subtitle ->
            CallOverlayManager.showEnded(this, contact, durationSeconds, wasMissed, subtitle)
        }
    }

    private fun isMissedCall(call: Call, durationSeconds: Int): Boolean {
        val disconnectCause = call.details?.disconnectCause
        val causeCode = disconnectCause?.code
        if (causeCode == DisconnectCause.MISSED) {
            return true
        }
        if (causeCode == DisconnectCause.REJECTED) {
            return false
        }
        return durationSeconds == 0 && !call.isOutgoing()
    }

    private fun loadCallContact(call: Call, callback: (CallContact, String?) -> Unit) {
        val secondaryInfo = getSecondaryInfo(call)
        getCallContact(applicationContext, call) { contact ->
            mainHandler.post {
                callback(contact, secondaryInfo)
            }
        }
    }

    private fun getSecondaryInfo(call: Call): String? {
        val displayName = call.details?.callerDisplayName?.toString()?.trim()
        return displayName?.takeIf { it.isNotEmpty() }
    }
}
