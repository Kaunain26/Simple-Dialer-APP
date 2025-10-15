package app.trusted.callerid.sms.activities

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import app.trusted.callerid.sms.R

import app.trusted.callerid.sms.helpers.PermissionManager
import com.simplemobiletools.dialer.helpers.fullScreenImmersiveMode

open class SimpleActivity : BaseSimpleActivity() {
    override fun getAppIconIDs() = arrayListOf(
        R.mipmap.ic_launcher,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        fullScreenImmersiveMode()
    }

    override fun getAppLauncherName() = getString(R.string.app_launcher_name)

    override fun onResume() {
        super.onResume()
        PermissionManager.enforcePermissions(this)
    }
}
