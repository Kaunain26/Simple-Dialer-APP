package com.simplemobiletools.dialer

import android.app.Application
import com.simplemobiletools.commons.extensions.checkAppIconColor
import com.simplemobiletools.commons.extensions.checkUseEnglish
import androidx.core.content.ContextCompat
import com.simplemobiletools.dialer.helpers.Config
import com.simplemobiletools.smsmessenger.helpers.Config as MessengerConfig

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        checkUseEnglish()
        enforceLightTheme()
        enforceMessengerTheme()
    }

    private fun enforceLightTheme() {
        val config = Config.newInstance(this)
        val backgroundColor = ContextCompat.getColor(this, R.color.static_background_color)
        val textColor = ContextCompat.getColor(this, R.color.static_text_color)
        val accentColor = ContextCompat.getColor(this, R.color.static_accent_color)

        if (config.isUsingAutoTheme) {
            config.isUsingAutoTheme = false
        }

        if (config.isUsingSystemTheme) {
            config.isUsingSystemTheme = false
        }

        if (config.isUsingSharedTheme) {
            config.isUsingSharedTheme = false
        }

        if (config.shouldUseSharedTheme) {
            config.shouldUseSharedTheme = false
        }

        if (config.backgroundColor != backgroundColor) {
            config.backgroundColor = backgroundColor
        }

        if (config.textColor != textColor) {
            config.textColor = textColor
        }

        if (config.primaryColor != accentColor) {
            config.primaryColor = accentColor
        }

        if (config.accentColor != accentColor) {
            config.accentColor = accentColor
        }

        if (config.appIconColor != accentColor) {
            config.appIconColor = accentColor
        }

        if (config.customBackgroundColor != backgroundColor) {
            config.customBackgroundColor = backgroundColor
        }

        if (config.customTextColor != textColor) {
            config.customTextColor = textColor
        }

        if (config.customPrimaryColor != accentColor) {
            config.customPrimaryColor = accentColor
        }

        if (config.customAccentColor != accentColor) {
            config.customAccentColor = accentColor
        }

        if (config.customAppIconColor != accentColor) {
            config.customAppIconColor = accentColor
        }
    }

    private fun enforceMessengerTheme() {
        val messengerConfig = MessengerConfig.newInstance(this)
        messengerConfig.apply {
            isUsingSystemTheme = false
            isUsingAutoTheme = false
            isUsingSharedTheme = false
            shouldUseSharedTheme = false
            wasSharedThemeEverActivated = false
            wasSharedThemeForced = false

            val brandPrimary = ContextCompat.getColor(this@App, com.simplemobiletools.smsmessenger.R.color.color_primary)
            val brandBackground = ContextCompat.getColor(this@App, com.simplemobiletools.smsmessenger.R.color.color_background)
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
