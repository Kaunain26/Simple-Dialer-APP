package com.simplemobiletools.dialer.activities

import android.content.Intent
import com.simplemobiletools.commons.activities.BaseSplashActivity
import com.simplemobiletools.dialer.helpers.PermissionManager

class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {
        if (PermissionManager.shouldShowPermissionScreen(this)) {
            PermissionOnboardingActivity.start(this, openMainOnComplete = true)
        } else {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}
