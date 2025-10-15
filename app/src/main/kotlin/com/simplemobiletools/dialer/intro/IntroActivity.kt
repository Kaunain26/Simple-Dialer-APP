package com.simplemobiletools.dialer.intro

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import app.trusted.callerid.sms.R
import app.trusted.callerid.sms.activities.MainActivity
import app.trusted.callerid.sms.activities.PermissionOnboardingActivity
import app.trusted.callerid.sms.databinding.ActivityIntroBinding
import app.trusted.callerid.sms.helpers.PermissionManager
import app.videocompressor.videoconverter.intro.IntroViewPagerAdapter
import app.videocompressor.videoconverter.intro.ScreenItem
import com.simplemobiletools.dialer.activities.LauncherActivity.Companion.IS_ONBOARD_OPENND
import com.simplemobiletools.dialer.activities.LauncherActivity.Companion.MY_PREFS
import com.simplemobiletools.dialer.helpers.fullScreenImmersiveMode
import kotlin.system.exitProcess

class IntroActivity : AppCompatActivity() {
    private var introViewPagerAdapter: IntroViewPagerAdapter? = null
    var position = 0
    private val binding by lazy { ActivityIntroBinding.inflate(layoutInflater) }
    private var firebasePrefs: SharedPreferences? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        firebasePrefs = PreferenceManager.getDefaultSharedPreferences(this)

        enableEdgeToEdge()
        setContentView(binding.root)

        fullScreenImmersiveMode()


        val mList: MutableList<ScreenItem> = ArrayList()
        mList.add(
            ScreenItem(
                getString(R.string.on_board_1_title),
                getString(R.string.on_board_1_des),
                R.drawable.on_board_1
            )
        )
        mList.add(
            ScreenItem(
                getString(R.string.on_board_2_title),
                getString(R.string.on_board_2_des),
                R.drawable.on_board_2
            )
        )
        mList.add(
            ScreenItem(
                getString(R.string.on_board_3_title),
                getString(R.string.on_board_3_des),
                R.drawable.on_board_3
            )
        )
        mList.add(
            ScreenItem(
                getString(R.string.on_board_4_title),
                getString(R.string.on_board_4_des),
                R.drawable.on_board_4
            )
        )

        // setup viewpager
        introViewPagerAdapter = IntroViewPagerAdapter(this, mList, firebasePrefs)
        binding.screenViewpager.adapter = introViewPagerAdapter
        // setup tabLayout with viewpager
        binding.tabIndicator.attachTo(binding.screenViewpager)

        // next button click Listener
        binding.btnNext.setOnClickListener {
            position = binding.screenViewpager.currentItem
            if (position == mList.size - 1) { // when we reach to the last screen
                openNextScreen()
            }
            if (position < mList.size) {
                position++
                binding.screenViewpager.currentItem = position
            }
        }

    }

    private fun saveOnBoardPrefsData() {
        val pref = applicationContext.getSharedPreferences(MY_PREFS, MODE_PRIVATE)
        val editor = pref.edit()
        editor.putBoolean(IS_ONBOARD_OPENND, true)
        editor.apply()
    }

    private fun openNextScreen() {
        //open main activity
        saveOnBoardPrefsData()
        if (PermissionManager.shouldShowPermissionScreen(this)) {
            PermissionOnboardingActivity.start(this, openMainOnComplete = true)
        } else {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Handler(Looper.getMainLooper()).postDelayed({
            exitProcess(0)
        }, 500)
    }

    private fun finishActivity() {
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 500)
    }


}
