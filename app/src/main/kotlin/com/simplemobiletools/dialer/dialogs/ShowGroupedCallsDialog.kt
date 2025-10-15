package app.trusted.callerid.sms.dialogs

import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.extensions.viewBinding
import app.trusted.callerid.sms.activities.SimpleActivity
import app.trusted.callerid.sms.adapters.RecentCallsAdapter
import app.trusted.callerid.sms.databinding.DialogShowGroupedCallsBinding
import app.trusted.callerid.sms.helpers.RecentsHelper
import app.trusted.callerid.sms.models.RecentCall

class ShowGroupedCallsDialog(val activity: BaseSimpleActivity, callIds: ArrayList<Int>) {
    private var dialog: AlertDialog? = null
    private val binding by activity.viewBinding(DialogShowGroupedCallsBinding::inflate)

    init {
        RecentsHelper(activity).getRecentCalls(false) { allRecents ->
            val recents = allRecents.filter { callIds.contains(it.id) }.toMutableList() as ArrayList<RecentCall>
            activity.runOnUiThread {
                RecentCallsAdapter(activity as SimpleActivity, recents, binding.selectGroupedCallsList, null, false) {
                }.apply {
                    binding.selectGroupedCallsList.adapter = this
                }
            }
        }

        activity.getAlertDialogBuilder()
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }
}
