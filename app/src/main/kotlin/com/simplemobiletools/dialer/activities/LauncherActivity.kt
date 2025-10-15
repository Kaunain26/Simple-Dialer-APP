package com.simplemobiletools.dialer.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import app.trusted.callerid.sms.activities.MainActivity
import app.trusted.callerid.sms.activities.PermissionOnboardingActivity
import app.trusted.callerid.sms.databinding.ActivityLauncherBinding
import app.trusted.callerid.sms.helpers.PermissionManager
import com.simplemobiletools.dialer.intro.IntroActivity
import com.simplemobiletools.dialer.helpers.fullScreenImmersiveMode

class LauncherActivity : AppCompatActivity() {

    private val binding by lazy { ActivityLauncherBinding.inflate(layoutInflater) }

    companion object {
        val MY_PREFS = "MY_PREFS"
        val IS_ONBOARD_OPENND = "IS_ONBOARD_OPENND"

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        fullScreenImmersiveMode()

        Handler(Looper.getMainLooper()).postDelayed({
            if (isOnBoardingDisplayed()) {
                if (PermissionManager.shouldShowPermissionScreen(this)) {
                    PermissionOnboardingActivity.start(this, openMainOnComplete = true)
                } else {
                    startActivity(Intent(this, MainActivity::class.java))
                }
                finish()
                return@postDelayed
            }

            startActivity(Intent(this, IntroActivity::class.java))
            finish()

        }, 2000)


    }

    private fun isOnBoardingDisplayed(): Boolean {
        val pref = applicationContext.getSharedPreferences(MY_PREFS, MODE_PRIVATE)
        return pref.getBoolean(IS_ONBOARD_OPENND, false)
    }

    override fun onBackPressed() {}
}
