package com.simplemobiletools.smsmessenger.activities

import android.content.Intent
import com.simplemobiletools.commons.activities.BaseSplashActivity

class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {
        startActivity(Intent(this, MainActivity2::class.java))
        finish()
    }
}
