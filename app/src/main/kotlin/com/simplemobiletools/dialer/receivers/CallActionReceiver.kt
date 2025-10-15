package app.trusted.callerid.sms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.trusted.callerid.sms.activities.CallActivity
import app.trusted.callerid.sms.helpers.ACCEPT_CALL
import app.trusted.callerid.sms.helpers.CallManager
import app.trusted.callerid.sms.helpers.DECLINE_CALL

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACCEPT_CALL -> {
                context.startActivity(CallActivity.getStartIntent(context))
                CallManager.accept()
            }
            DECLINE_CALL -> CallManager.reject()
        }
    }
}
