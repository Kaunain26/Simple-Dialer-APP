package app.trusted.callerid.sms.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.viewpager.widget.ViewPager
import com.google.android.material.snackbar.Snackbar
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.dialogs.PermissionRequiredDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.FAQItem
import com.simplemobiletools.commons.models.contacts.Contact
import app.trusted.callerid.sms.BuildConfig
import app.trusted.callerid.sms.R
import app.trusted.callerid.sms.adapters.ViewPagerAdapter
import app.trusted.callerid.sms.databinding.ActivityMainBinding
import app.trusted.callerid.sms.dialogs.ChangeSortingDialog
import app.trusted.callerid.sms.dialogs.FilterContactSourcesDialog
import app.trusted.callerid.sms.extensions.config
import app.trusted.callerid.sms.extensions.launchCreateNewContactIntent
import app.trusted.callerid.sms.fragments.BlockedTabFragment
import app.trusted.callerid.sms.fragments.ContactsFragment
import app.trusted.callerid.sms.fragments.MyViewPagerFragment
import app.trusted.callerid.sms.fragments.MessagesFragment
import app.trusted.callerid.sms.fragments.RecentsFragment
import app.trusted.callerid.sms.helpers.*
import com.simplemobiletools.smsmessenger.extensions.config_sms
import me.grantland.widget.AutofitHelper

class MainActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityMainBinding::inflate)

    private var launchedDialer = false
    private var storedShowTabs = 0
    private var storedFontSize = 0
    private var storedStartNameWithSurname = false
    var cachedContacts = ArrayList<Contact>()

    private var tabLabelTypeface: Typeface? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        loadCustomTabTypeface()
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        refreshMenuItems()
        updateMaterialActivityViews(binding.mainCoordinator, binding.mainHolder, useTransparentNavigation = false, useTopSearchMenu = true)

        launchedDialer = savedInstanceState?.getBoolean(OPEN_DIAL_PAD_AT_LAUNCH) ?: false

        if (isDefaultDialer()) {
            checkContactPermissions()

            if (!config.wasOverlaySnackbarConfirmed && !Settings.canDrawOverlays(this)) {
                val snackbar = Snackbar.make(binding.mainHolder, R.string.allow_displaying_over_other_apps, Snackbar.LENGTH_INDEFINITE).setAction(R.string.ok) {
                    config.wasOverlaySnackbarConfirmed = true
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }

                snackbar.setBackgroundTint(getProperBackgroundColor().darkenColor())
                snackbar.setTextColor(getProperTextColor())
                snackbar.setActionTextColor(getProperTextColor())
                snackbar.show()
            }

            handleNotificationPermission { granted ->
                if (!granted) {
                    PermissionRequiredDialog(this, R.string.allow_notifications_incoming_calls, { openNotificationSettings() })
                }
            }
        } else {
            launchSetDefaultDialerIntent()
        }

        if (isQPlus() && (config.blockUnknownNumbers || config.blockHiddenNumbers)) {
            setDefaultCallerIdApp()
        }

        setupTabs()
        Contact.sorting = config.sorting
    }

    override fun onResume() {
        super.onResume()
        if (storedShowTabs != config.showTabs) {
            config.lastUsedViewPagerPage = 0
            System.exit(0)
            return
        }

        updateMenuColors()
        val properPrimaryColor = getProperPrimaryColor()
        val dialpadIcon = resources.getColoredDrawableWithColor(R.drawable.ic_dialpad_vector, properPrimaryColor.getContrastColor())
        binding.mainDialpadButton.setImageDrawable(dialpadIcon)

        updateTextColors(binding.mainHolder)
        setupTabColors()

        getAllFragments().forEach {
            it?.setupColors(getProperTextColor(), getProperPrimaryColor(), getProperPrimaryColor())
        }

        val configStartNameWithSurname = config.startNameWithSurname
        if (storedStartNameWithSurname != configStartNameWithSurname) {
            getContactsFragment()?.startNameWithSurnameChanged(configStartNameWithSurname)
            storedStartNameWithSurname = config.startNameWithSurname
        }

        if (!binding.mainMenu.isSearchOpen) {
            refreshItems(true)
        }

        val configFontSize = config.fontSize
        if (storedFontSize != configFontSize) {
            getAllFragments().forEach {
                it?.fontSizeChanged()
            }
        }

        checkShortcuts()
        Handler().postDelayed({
            getRecentsFragment()?.refreshItems()
        }, 2000)


        //Messages Fragment
        refreshMenuItems()

        getMessagesFragment()?.apply {
            getOrCreateConversationsAdapter().apply {
                if (storedTextColor != getProperTextColor()) {
                    updateTextColor(getProperTextColor())
                }

                if (storedFontSize != config_sms.fontSize) {
                    updateFontSize()
                }

                updateDrafts()
            }

            updateTextColors(binding.mainCoordinator)
            msgBinding.searchHolder.setBackgroundColor(getProperBackgroundColor())

            val properPrimaryColor = getProperPrimaryColor()
            msgBinding.noConversationsPlaceholder2.setTextColor(properPrimaryColor)
            msgBinding.noConversationsPlaceholder2.underlineText()
            msgBinding.conversationsFastscroller.updateColors(properPrimaryColor)
            msgBinding.conversationsProgressBar.setIndicatorColor(properPrimaryColor)
            msgBinding.conversationsProgressBar.trackColor = properPrimaryColor.adjustAlpha(LOWER_ALPHA)
            checkShortcut()
            (msgBinding.conversationsFab.layoutParams as? CoordinatorLayout.LayoutParams)?.bottomMargin =
                navigationBarHeight + resources.getDimension(com.simplemobiletools.smsmessenger.R.dimen.activity_margin).toInt()
        }


    }

    override fun onPause() {
        super.onPause()
        storedShowTabs = config.showTabs
        storedStartNameWithSurname = config.startNameWithSurname
        config.lastUsedViewPagerPage = binding.viewPager.currentItem
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        // we don't really care about the result, the app can work without being the default Dialer too
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER) {
            checkContactPermissions()
        } else if (requestCode == REQUEST_CODE_SET_DEFAULT_CALLER_ID && resultCode != Activity.RESULT_OK) {
            toast(R.string.must_make_default_caller_id_app, length = Toast.LENGTH_LONG)
            baseConfig.blockUnknownNumbers = false
            baseConfig.blockHiddenNumbers = false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(OPEN_DIAL_PAD_AT_LAUNCH, launchedDialer)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshItems()
    }

    override fun onBackPressed() {
        if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
        } else {
            super.onBackPressed()
        }
    }

    fun refreshMenuItems() {
        val currentFragment = getCurrentFragment()
        val menu = binding.mainMenu.getToolbar().menu
        val isMessagesFragment = currentFragment is MessagesFragment

        menu.findItem(R.id.change_view_type)?.isVisible = false
        menu.findItem(R.id.column_count)?.isVisible = false
        menu.findItem(R.id.settings)?.isVisible = true

        if (isMessagesFragment) {
            menu.findItem(R.id.clear_call_history)?.isVisible = false
            menu.findItem(R.id.sort)?.isVisible = false
            menu.findItem(R.id.create_new_contact)?.isVisible = false
            menu.findItem(R.id.filter)?.isVisible = false
            menu.findItem(R.id.show_recycle_bin)?.isVisible = config_sms.useRecycleBin
            menu.findItem(R.id.show_archived)?.isVisible = config_sms.isArchiveAvailable
            menu.findItem(R.id.settings)?.isVisible = true
        } else {
            val isContactsFragment = currentFragment == getContactsFragment()
            menu.findItem(R.id.clear_call_history)?.isVisible = currentFragment == getRecentsFragment()
            menu.findItem(R.id.sort)?.isVisible = isContactsFragment
            menu.findItem(R.id.create_new_contact)?.isVisible = isContactsFragment
            menu.findItem(R.id.filter)?.isVisible = isContactsFragment
            menu.findItem(R.id.show_recycle_bin)?.isVisible = false
            menu.findItem(R.id.show_archived)?.isVisible = false
        }
    }

    private fun setupOptionsMenu() {
        binding.mainMenu.apply {
            getToolbar().inflateMenu(R.menu.menu)
            toggleHideOnScroll(false)
            setupMenu()

            onSearchClosedListener = {
                getAllFragments().forEach {
                    it?.onSearchQueryChanged("")
                }
            }

            onSearchTextChangedListener = { text ->
                getCurrentFragment()?.onSearchQueryChanged(text)
            }

            getToolbar().setOnMenuItemClickListener { menuItem ->
                val messagesFragment = getMessagesFragment()
                val isMessagesTab = messagesFragment != null && getCurrentFragment() == messagesFragment

                if (isMessagesTab) {
                    when (menuItem.itemId) {
                        R.id.show_recycle_bin -> {
                            messagesFragment?.launchRecycleBin()
                            return@setOnMenuItemClickListener true
                        }

                        R.id.show_archived -> {
                            messagesFragment?.launchArchivedConversations()
                            return@setOnMenuItemClickListener true
                        }

                        R.id.settings -> {
                            messagesFragment?.launchSettings()
                            return@setOnMenuItemClickListener true
                        }
                    }
                }

                when (menuItem.itemId) {
                    R.id.clear_call_history -> clearCallHistory()
                    R.id.create_new_contact -> launchCreateNewContactIntent()
                    R.id.sort -> showSortingDialog(showCustomSorting = false)
                    R.id.filter -> showFilterDialog()
                    // R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
                    R.id.settings -> launchSettings()
                    //R.id.about -> launchAbout()
                    else -> return@setOnMenuItemClickListener false
                }
                return@setOnMenuItemClickListener true
            }
        }
    }

    private fun updateMenuColors() {
        updateStatusbarColor(getProperBackgroundColor())
        binding.mainMenu.updateColors()
    }

    private fun loadCustomTabTypeface() {
        try {
            // Looks for a file at src/main/assets/fonts/tab_label.ttf
            tabLabelTypeface = Typeface.createFromAsset(assets, "fonts/gilroy_semi_bold.ttf")
        } catch (t: Throwable) {
            tabLabelTypeface = null
        }
    }

    private fun checkContactPermissions() {
        handlePermission(PERMISSION_READ_CONTACTS) {
            initFragments()
        }
    }

    private fun clearCallHistory() {
        val confirmationText = "${getString(R.string.clear_history_confirmation)}\n\n${getString(R.string.cannot_be_undone)}"
        ConfirmationDialog(this, confirmationText) {
            RecentsHelper(this).removeAllRecentCalls(this) {
                runOnUiThread {
                    getRecentsFragment()?.refreshItems()
                }
            }
        }
    }

    @SuppressLint("NewApi")
    private fun checkShortcuts() {
        val appIconColor = config.appIconColor
        if (isNougatMR1Plus() && config.lastHandledShortcutColor != appIconColor) {
            val launchDialpad = getLaunchDialpadShortcut(appIconColor)

            try {
                shortcutManager.dynamicShortcuts = listOf(launchDialpad)
                config.lastHandledShortcutColor = appIconColor
            } catch (ignored: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getLaunchDialpadShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.dialpad)
        val drawable = resources.getDrawable(R.drawable.shortcut_dialpad)
        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_dialpad_background).applyColorFilter(appIconColor)
        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, DialpadActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "launch_dialpad")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .build()
    }

    private fun setupTabColors() {
        val activeTabs = getActiveTabs()
        val activeView = binding.mainTabsHolder.getTabAt(binding.viewPager.currentItem)?.customView
        updateBottomTabItemColors(activeView, true, activeTabs.getOrNull(binding.viewPager.currentItem) ?: TAB_CALL_HISTORY)

        getInactiveTabIndexes(binding.viewPager.currentItem).forEach { index ->
            val inactiveView = binding.mainTabsHolder.getTabAt(index)?.customView
            updateBottomTabItemColors(inactiveView, false, activeTabs.getOrNull(index) ?: TAB_CALL_HISTORY)
        }

        val bottomBarColor = getBottomNavigationBackgroundColor()
        binding.mainTabsHolder.setBackgroundColor(bottomBarColor)
        updateNavigationBarColor(bottomBarColor)
    }

    private fun getInactiveTabIndexes(activeIndex: Int) = (0 until binding.mainTabsHolder.tabCount).filter { it != activeIndex }

    private fun getActiveTabs() = tabsList.filter { config.showTabs and it != 0 }

    private fun getSelectedTabIconRes(tabId: Int): Int = when (tabId) {
        TAB_CALL_HISTORY -> R.drawable.ic_tab_calls
        TAB_CONTACTS -> R.drawable.ic_person_vector
        TAB_MESSAGES -> R.drawable.ic_tab_messages
        TAB_BLOCKED -> R.drawable.ic_tab_blocked
        else -> R.drawable.ic_tab_calls
    }

    private fun getDeselectedTabIconRes(tabId: Int): Int = when (tabId) {
        TAB_CALL_HISTORY -> R.drawable.ic_tab_calls
        TAB_CONTACTS -> R.drawable.ic_person_vector
        TAB_MESSAGES -> R.drawable.ic_tab_messages
        TAB_BLOCKED -> R.drawable.ic_tab_blocked
        else -> R.drawable.ic_tab_calls
    }

    private fun updateBottomTabItemColors(customView: View?, isSelected: Boolean, tabId: Int) {
        customView ?: return
        val iconView = customView.findViewById<ImageView>(R.id.tab_item_icon)
        val labelView = customView.findViewById<TextView>(R.id.tab_item_label)
        iconView?.setImageDrawable(getTabIconDrawable(tabId, isSelected))
        val labelColor = if (isSelected) getProperPrimaryColor() else ContextCompat.getColor(this, R.color.bottom_nav_unselected)
        labelView?.setTextColor(labelColor)
        if (tabLabelTypeface != null) {
            val style = if (isSelected) Typeface.BOLD else Typeface.NORMAL
            labelView?.typeface = Typeface.create(tabLabelTypeface, style)
        } else {
            labelView?.setTypeface(labelView.typeface, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
        }

        customView.background = if (isSelected) ContextCompat.getDrawable(this, R.drawable.bg_bottom_tab_selected) else null
    }

    private fun initFragments() {
        binding.viewPager.offscreenPageLimit = 3
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                binding.mainTabsHolder.getTabAt(position)?.select()
                getAllFragments().forEach {
                    it?.finishActMode()
                }
                refreshMenuItems()
            }
        })

        // selecting the proper tab sometimes glitches, add an extra selector to make sure we have it right
        binding.mainTabsHolder.onGlobalLayout {
            Handler().postDelayed({
                var wantedTab = getDefaultTab()

                // open the Recents tab if we got here by clicking a missed call notification
                if (intent.action == Intent.ACTION_VIEW && config.showTabs and TAB_CALL_HISTORY > 0) {
                    wantedTab = getActiveTabs().indexOf(TAB_CALL_HISTORY).takeIf { it >= 0 } ?: 0

                    ensureBackgroundThread {
                        clearMissedCalls()
                    }
                }

                binding.mainTabsHolder.getTabAt(wantedTab)?.select()
                refreshMenuItems()
            }, 100L)
        }

        binding.mainDialpadButton.setOnClickListener {
            launchDialpad()
        }

        binding.viewPager.onGlobalLayout {
            refreshMenuItems()
        }

        if (config.openDialPadAtLaunch && !launchedDialer) {
            launchDialpad()
            launchedDialer = true
        }
    }

    private fun setupTabs() {
        binding.viewPager.adapter = null
        binding.mainTabsHolder.removeAllTabs()

        val activeTabs = getActiveTabs()
        activeTabs.forEach { tabId ->
            binding.mainTabsHolder.newTab().setCustomView(R.layout.bottom_tablayout_item).apply {
                customView?.findViewById<ImageView>(R.id.tab_item_icon)?.setImageDrawable(getTabIconDrawable(tabId, false))
                customView?.findViewById<TextView>(R.id.tab_item_label)?.text = getTabLabel(tabId)
                AutofitHelper.create(customView?.findViewById(R.id.tab_item_label))
                binding.mainTabsHolder.addTab(this)
                updateBottomTabItemColors(customView, false, tabId)
            }
        }

        binding.mainTabsHolder.onTabSelectionChanged(
            tabUnselectedAction = {
                getActiveTabs().getOrNull(it.position)?.let { tabId ->
                    updateBottomTabItemColors(it.customView, false, tabId)
                }
            },
            tabSelectedAction = {
                binding.mainMenu.closeSearch()
                binding.viewPager.currentItem = it.position
                getActiveTabs().getOrNull(it.position)?.let { tabId ->
                    updateBottomTabItemColors(it.customView, true, tabId)
                    ensureDialpadVisibility(tabId)
                }
            }
        )

        binding.mainTabsHolder.beGoneIf(binding.mainTabsHolder.tabCount == 1)
        storedShowTabs = config.showTabs
        storedStartNameWithSurname = config.startNameWithSurname
    }

    private fun getTabIconDrawable(tabId: Int, isSelected: Boolean): Drawable {
        val iconRes = if (isSelected) getSelectedTabIconRes(tabId) else getDeselectedTabIconRes(tabId)
        val iconColor = if (isSelected) getProperPrimaryColor() else ContextCompat.getColor(this, R.color.bottom_nav_unselected)
        return resources.getColoredDrawableWithColor(iconRes, iconColor)
    }

    private fun getTabLabel(tabId: Int): String {
        val stringId = when (tabId) {
            TAB_CALL_HISTORY -> R.string.call_history_tab
            TAB_CONTACTS -> R.string.contacts_tab
            TAB_MESSAGES -> R.string.messages_tab
            TAB_BLOCKED -> R.string.blocked_numbers_tab
            else -> R.string.call_history_tab
        }

        return resources.getString(stringId)
    }

    private fun refreshItems(openLastTab: Boolean = false) {
        if (isDestroyed || isFinishing) {
            return
        }

        binding.apply {
            if (viewPager.adapter == null) {
                viewPager.adapter = ViewPagerAdapter(this@MainActivity)
                viewPager.currentItem = if (openLastTab) config.lastUsedViewPagerPage else getDefaultTab()
                viewPager.onGlobalLayout {
                    refreshFragments()
                    ensureDialpadVisibility(getCurrentTabId())
                }
            } else {
                refreshFragments()
                ensureDialpadVisibility(getCurrentTabId())
            }
        }
    }

    private fun launchDialpad() {
        Intent(applicationContext, DialpadActivity::class.java).apply {
            startActivity(this)
        }
    }

    fun refreshFragments() {
        getRecentsFragment()?.refreshItems()
        getContactsFragment()?.refreshItems()
        getMessagesFragment()?.refreshItems(callback = null)
        getBlockedFragment()?.refreshItems()
    }

    private fun getAllFragments(): ArrayList<MyViewPagerFragment<*>?> {
        val showTabs = config.showTabs
        val fragments = arrayListOf<MyViewPagerFragment<*>?>()

        if (showTabs and TAB_CALL_HISTORY > 0) {
            fragments.add(getRecentsFragment())
        }

        if (showTabs and TAB_CONTACTS > 0) {
            fragments.add(getContactsFragment())
        }

        if (showTabs and TAB_MESSAGES > 0) {
            fragments.add(getMessagesFragment())
        }

        if (showTabs and TAB_BLOCKED > 0) {
            fragments.add(getBlockedFragment())
        }

        return fragments
    }

    private fun getCurrentFragment(): MyViewPagerFragment<*>? = getAllFragments().getOrNull(binding.viewPager.currentItem)

    private fun getContactsFragment(): ContactsFragment? = findViewById(R.id.contacts_fragment)

    private fun getMessagesFragment(): MessagesFragment? = findViewById(R.id.messages_fragment)

    private fun getBlockedFragment(): BlockedTabFragment? = findViewById(R.id.blocked_fragment)

    private fun getRecentsFragment(): RecentsFragment? = findViewById(R.id.recents_fragment)

    private fun getDefaultTab(): Int {
        val activeTabs = getActiveTabs()
        if (activeTabs.isEmpty()) {
            return 0
        }

        return when (config.defaultTab) {
            TAB_LAST_USED -> if (config.lastUsedViewPagerPage < activeTabs.size) config.lastUsedViewPagerPage else 0
            else -> activeTabs.indexOf(config.defaultTab).takeIf { it >= 0 } ?: 0
        }
    }

    @SuppressLint("MissingPermission")
    private fun clearMissedCalls() {
        try {
            // notification cancellation triggers MissedCallNotifier.clearMissedCalls() which, in turn,
            // should update the database and reset the cached missed call count in MissedCallNotifier.java
            // https://android.googlesource.com/platform/packages/services/Telecomm/+/master/src/com/android/server/telecom/ui/MissedCallNotifierImpl.java#170
            telecomManager.cancelMissedCallsNotification()
        } catch (ignored: Exception) {
        }
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses = LICENSE_GLIDE or LICENSE_INDICATOR_FAST_SCROLL or LICENSE_AUTOFITTEXTVIEW

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_1_title, R.string.faq_1_text),
            FAQItem(R.string.faq_9_title_commons, R.string.faq_9_text_commons)
        )

        if (!resources.getBoolean(R.bool.hide_google_relations)) {
            faqItems.add(FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons))
            faqItems.add(FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons))
        }

        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
    }

    private fun showSortingDialog(showCustomSorting: Boolean) {
        ChangeSortingDialog(this, showCustomSorting) {
            getContactsFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }
        }
    }

    private fun ensureDialpadVisibility(tabId: Int?) {
        val shouldShow = tabId != TAB_MESSAGES
        if (shouldShow) {
            binding.mainDialpadButton.show()
        } else {
            binding.mainDialpadButton.hide()
        }
    }


    private fun getCurrentTabId(): Int? = getActiveTabs().getOrNull(binding.viewPager.currentItem)

    private fun showFilterDialog() {
        FilterContactSourcesDialog(this) {
            getContactsFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }

            getRecentsFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }
        }
    }

    fun cacheContacts(contacts: List<Contact>) {
        try {
            cachedContacts.clear()
            cachedContacts.addAll(contacts)
        } catch (e: Exception) {
        }
    }
}
