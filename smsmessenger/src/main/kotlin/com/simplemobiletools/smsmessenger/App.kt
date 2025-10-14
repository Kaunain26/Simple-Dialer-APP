package com.simplemobiletools.smsmessenger

import android.app.Application
import androidx.core.content.ContextCompat
import com.simplemobiletools.commons.extensions.checkAppIconColor
import com.simplemobiletools.commons.extensions.checkUseEnglish
import com.simplemobiletools.smsmessenger.extensions.config_sms

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        checkUseEnglish()
        enforceBrandTheme()
    }

    private fun enforceBrandTheme() {
        config_sms.apply {
            isUsingSystemTheme = false
            isUsingAutoTheme = false
            isUsingSharedTheme = false
            shouldUseSharedTheme = false
            wasSharedThemeEverActivated = false
            wasSharedThemeForced = false

            val brandPrimary = ContextCompat.getColor(this@App, R.color.color_primary)
            val brandBackground = ContextCompat.getColor(this@App, R.color.color_background)
            val brandText = ContextCompat.getColor(this@App, com.simplemobiletools.commons.R.color.theme_light_text_color)

            primaryColor = brandPrimary
            customPrimaryColor = brandPrimary
            accentColor = brandPrimary
            customAccentColor = brandPrimary

            backgroundColor = brandBackground
            customBackgroundColor = brandBackground

            textColor = brandText
            customTextColor = brandText
        }

        applicationContext.checkAppIconColor()
    }
}
