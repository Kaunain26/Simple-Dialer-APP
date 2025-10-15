package app.trusted.callerid.sms.dialogs

import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.*
import app.trusted.callerid.sms.R
import app.trusted.callerid.sms.activities.SimpleActivity
import app.trusted.callerid.sms.databinding.DialogExportCallHistoryBinding

class ExportCallHistoryDialog(val activity: SimpleActivity, callback: (filename: String) -> Unit) {

    init {
        val binding = DialogExportCallHistoryBinding.inflate(activity.layoutInflater).apply {
            exportCallHistoryFilename.setText("call_history_${activity.getCurrentFormattedDateTime()}")
        }

        activity.getAlertDialogBuilder().setPositiveButton(R.string.ok, null).setNegativeButton(R.string.cancel, null).apply {
            activity.setupDialogStuff(binding.root, this, R.string.export_call_history) { alertDialog ->
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {

                    val filename = binding.exportCallHistoryFilename.value
                    when {
                        filename.isEmpty() -> activity.toast(R.string.empty_name)
                        filename.isAValidFilename() -> {
                            callback(filename)
                            alertDialog.dismiss()
                        }

                        else -> activity.toast(R.string.invalid_name)
                    }
                }
            }
        }
    }
}
