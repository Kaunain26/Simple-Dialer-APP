package app.trusted.callerid.sms.activities

import android.content.Intent
import com.simplemobiletools.commons.activities.BaseSplashActivity
import app.trusted.callerid.sms.helpers.PermissionManager
import com.simplemobiletools.dialer.activities.LauncherActivity

class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {

        startActivity(Intent(this, LauncherActivity::class.java))
        finish()
    }
}
