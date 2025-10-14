//package com.simplemobiletools.smsmessenger.ui
//
//import android.annotation.SuppressLint
//import android.app.Activity
//import android.app.role.RoleManager
//import android.content.Intent
//import android.content.pm.ShortcutInfo
//import android.content.pm.ShortcutManager
//import android.graphics.drawable.Icon
//import android.graphics.drawable.LayerDrawable
//import android.os.Bundle
//import android.provider.Telephony
//import android.text.TextUtils
//import android.view.View
//import androidx.coordinatorlayout.widget.CoordinatorLayout
//import com.google.android.material.button.MaterialButtonToggleGroup
//import com.simplemobiletools.commons.dialogs.PermissionRequiredDialog
//import com.simplemobiletools.commons.extensions.*
//import com.simplemobiletools.commons.helpers.*
//import com.simplemobiletools.commons.models.FAQItem
//import com.simplemobiletools.commons.models.Release
//import com.simplemobiletools.smsmessenger.BuildConfig
//import com.simplemobiletools.smsmessenger.R
//import com.simplemobiletools.smsmessenger.adapters.ConversationsAdapter
//import com.simplemobiletools.smsmessenger.adapters.SearchResultsAdapter
//import com.simplemobiletools.smsmessenger.ui.MessengerUiBinding
//import com.simplemobiletools.smsmessenger.extensions.*
//import com.simplemobiletools.smsmessenger.helpers.*
//import com.simplemobiletools.smsmessenger.models.Conversation
//import com.simplemobiletools.smsmessenger.models.Events
//import com.simplemobiletools.smsmessenger.models.Message
//import com.simplemobiletools.smsmessenger.models.SearchResult
//import org.greenrobot.eventbus.EventBus
//import org.greenrobot.eventbus.Subscribe
//import org.greenrobot.eventbus.ThreadMode
//import java.util.Locale
//
//class MessengerUiDelegate(private val host: MessengerHost, private val binding: MessengerUiBinding) {
//    private val activity get() = host.activity
//    private val resources get() = activity.resources
//
//    private val MAKE_DEFAULT_APP_REQUEST = 1
//
//    private var storedTextColor = 0
//    private var storedFontSize = 0
//    private var lastSearchedText = ""
//    private var bus: EventBus? = null
//    private var wasProtectionHandled = false
//    private var filtersInitialized = false
//    private var allConversations = arrayListOf<Conversation>()
//    private var currentFilter = ConversationFilter.ALL
//    private val spamKeywords by lazy {
//        setOf(
//            "win",
//            "winner",
//            "prize",
//            "offer",
//            "free",
//            "trial",
//            "loan",
//            "credit",
//            "bitcoin",
//            "crypto",
//            "investment",
//            "cashback",
//            "reward",
//            "urgent",
//            "limited time",
//            "guaranteed",
//            "click",
//            "verify",
//            "bank account"
//        ).map { it.lowercase(Locale.getDefault()) }
//    }
//
//
//    @SuppressLint("InlinedApi")
//    fun onCreate(savedInstanceState: Bundle?) {
//        isMaterialActivity = true
//                activity.appLaunched(BuildConfig.APPLICATION_ID)
//        setupOptionsMenu()
//        refreshMenuItems()
//
//        host.setupMaterialActivityViews(binding)
//        // placeholder: updateMaterialActivityViews(
//            mainCoordinatorLayout = binding.mainCoordinator,
//            nestedView = binding.conversationsList,
//            useTransparentNavigation = true,
//            useTopSearchMenu = true
//        )
//
//        if (savedInstanceState == null) {
//            checkAndDeleteOldRecycleBinMessages()
//            handleAppPasswordProtection {
//                wasProtectionHandled = it
//                if (it) {
//                    clearAllMessagesIfNeeded {
//                        loadMessages()
//                    }
//                } else {
//                    host.finishHost()
//                }
//            }
//        }
//
//        if (checkAppSideloading()) {
//            return
//        }
//    }
//
//    fun onResume() {
//                updateMenuColors()
//        refreshMenuItems()
//
//        getOrCreateConversationsAdapter().apply {
//            if (storedTextColor != getProperTextColor()) {
//                updateTextColor(getProperTextColor())
//            }
//
//            if (storedFontSize != config.fontSize) {
//                updateFontSize()
//            }
//
//            updateDrafts()
//        }
//
//        updateTextColors(binding.mainCoordinator)
//        binding.searchHolder.setBackgroundColor(getProperBackgroundColor())
//
//        val properPrimaryColor = getProperPrimaryColor()
//        binding.noConversationsPlaceholder2.setTextColor(properPrimaryColor)
//        binding.noConversationsPlaceholder2.underlineText()
//        binding.conversationsFastscroller.updateColors(properPrimaryColor)
//        binding.conversationsProgressBar.setIndicatorColor(properPrimaryColor)
//        binding.conversationsProgressBar.trackColor = properPrimaryColor.adjustAlpha(LOWER_ALPHA)
//        checkShortcut()
//        (binding.conversationsFab.layoutParams as? CoordinatorLayout.LayoutParams)?.bottomMargin =
//            navigationBarHeight + resources.getDimension(R.dimen.activity_margin).toInt()
//    }
//
//    fun onPause() {
//                storeStateVariables()
//    }
//
//    fun onDestroy() {
//                bus?.unregister(this)
//    }
//
//    fun handleOnBackPressed() {
//        if (binding.mainMenu.isSearchOpen) {
//            binding.mainMenu.closeSearch()
//            return true
//        }
//        return false
//    }
//
//    fun onSaveInstanceState(outState: Bundle) {
//                outState.putBoolean(WAS_PROTECTION_HANDLED, wasProtectionHandled)
//    }
//
//    fun onRestoreInstanceState(savedInstanceState: Bundle) {
//                wasProtectionHandled = savedInstanceState.getBoolean(WAS_PROTECTION_HANDLED, false)
//
//        if (!wasProtectionHandled) {
//            handleAppPasswordProtection {
//                wasProtectionHandled = it
//                if (it) {
//                    loadMessages()
//                } else {
//                    host.finishHost()
//                }
//            }
//        } else {
//            loadMessages()
//        }
//    }
//
//    private fun setupOptionsMenu() {
//        binding.mainMenu.getToolbar().inflateMenu(R.menu.menu_main)
//        binding.mainMenu.toggleHideOnScroll(true)
//        binding.mainMenu.setupMenu()
//
//        binding.mainMenu.onSearchClosedListener = {
//            fadeOutSearch()
//        }
//
//        binding.mainMenu.onSearchTextChangedListener = { text ->
//            if (text.isNotEmpty()) {
//                if (binding.searchHolder.alpha < 1f) {
//                    binding.searchHolder.fadeIn()
//                }
//            } else {
//                fadeOutSearch()
//            }
//            searchTextChanged(text)
//        }
//
//        binding.mainMenu.getToolbar().setOnMenuItemClickListener { menuItem ->
//            when (menuItem.itemId) {
//                // R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
//                R.id.show_recycle_bin -> launchRecycleBin()
//                R.id.show_archived -> launchArchivedConversations()
//                R.id.settings -> launchSettings()
//                //R.id.about -> launchAbout()
//                else -> return@setOnMenuItemClickListener false
//            }
//            return@setOnMenuItemClickListener true
//        }
//    }
//
//    private fun refreshMenuItems() {
//        binding.mainMenu.getToolbar().menu.apply {
//            // findItem(R.id.more_apps_from_us).isVisible = !resources.getBoolean(com.simplemobiletools.commons.R.bool.hide_google_relations)
//            findItem(R.id.show_recycle_bin).isVisible = config.useRecycleBin
//            findItem(R.id.show_archived).isVisible = config.isArchiveAvailable
//        }
//    }
//
//    fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
//        super.onActivityResult(requestCode, resultCode, resultData)
//        if (requestCode == MAKE_DEFAULT_APP_REQUEST) {
//            if (resultCode == Activity.RESULT_OK) {
//                askPermissions()
//            } else {
//                host.finishHost()
//            }
//        }
//    }
//
//    private fun storeStateVariables() {
//        storedTextColor = getProperTextColor()
//        storedFontSize = config.fontSize
//    }
//
//    private fun updateMenuColors() {
//        updateStatusbarColor(getProperBackgroundColor())
//        binding.mainMenu.updateColors()
//    }
//
//    private fun loadMessages() {
//        if (isQPlus()) {
//            val roleManager = getSystemService(RoleManager::class.java)
//            if (roleManager!!.isRoleAvailable(RoleManager.ROLE_SMS)) {
//                if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
//                    askPermissions()
//                } else {
//                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
//                    host.startActivityForResultSafely(intent, MAKE_DEFAULT_APP_REQUEST)
//                }
//            } else {
//                toast(R.string.unknown_error_occurred)
//                host.finishHost()
//            }
//        } else {
//            if (Telephony.Sms.getDefaultSmsPackage(this) == packageName) {
//                askPermissions()
//            } else {
//                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
//                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
//                host.startActivityForResultSafely(intent, MAKE_DEFAULT_APP_REQUEST)
//            }
//        }
//    }
//
//    // while SEND_SMS and READ_SMS permissions are mandatory, READ_CONTACTS is optional. If we don't have it, we just won't be able to show the contact name in some cases
//    private fun askPermissions() {
//        handlePermission(PERMISSION_READ_SMS) {
//            if (it) {
//                handlePermission(PERMISSION_SEND_SMS) {
//                    if (it) {
//                        handlePermission(PERMISSION_READ_CONTACTS) {
//                            handleNotificationPermission { granted ->
//                                if (!granted) {
//                                    PermissionRequiredDialog(
//                                        activity = this,
//                                        textId = R.string.allow_notifications_incoming_messages,
//                                        positiveActionCallback = { openNotificationSettings() })
//                                }
//                            }
//
//                            initMessenger()
//                            bus = EventBus.getDefault()
//                            try {
//                                bus!!.register(this)
//                            } catch (ignored: Exception) {
//                            }
//                        }
//                    } else {
//                        host.finishHost()
//                    }
//                }
//            } else {
//                host.finishHost()
//            }
//        }
//    }
//
//    private fun initMessenger() {
//        checkWhatsNewDialog()
//        storeStateVariables()
//        getCachedConversations()
//
//        binding.noConversationsPlaceholder2.setOnClickListener {
//            launchNewConversation()
//        }
//
//        binding.conversationsFab.setOnClickListener {
//            launchNewConversation()
//        }
//
//        if (!filtersInitialized) {
//            setupConversationFilters()
//            filtersInitialized = true
//        } else {
//            updateFilteredConversations()
//        }
//    }
//
//    private fun getCachedConversations() {
//        ensureBackgroundThread {
//            val conversations = try {
//                conversationsDB.getNonArchived().toMutableList() as ArrayList<Conversation>
//            } catch (e: Exception) {
//                ArrayList()
//            }
//
//            val archived = try {
//                conversationsDB.getAllArchived()
//            } catch (e: Exception) {
//                listOf()
//            }
//
//            updateUnreadCountBadge(conversations)
//            runOnUiThread {
//                setupConversations(conversations, cached = true)
//                getNewConversations((conversations + archived).toMutableList() as ArrayList<Conversation>)
//            }
//            conversations.forEach {
//                clearExpiredScheduledMessages(it.threadId)
//            }
//        }
//    }
//
//    private fun getNewConversations(cachedConversations: ArrayList<Conversation>) {
//        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
//        ensureBackgroundThread {
//            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
//            val conversations = getConversations(privateContacts = privateContacts)
//
//            conversations.forEach { clonedConversation ->
//                val threadIds = cachedConversations.map { it.threadId }
//                if (!threadIds.contains(clonedConversation.threadId)) {
//                    conversationsDB.insertOrUpdate(clonedConversation)
//                    cachedConversations.add(clonedConversation)
//                }
//            }
//
//            cachedConversations.forEach { cachedConversation ->
//                val threadId = cachedConversation.threadId
//
//                val isTemporaryThread = cachedConversation.isScheduled
//                val isConversationDeleted = !conversations.map { it.threadId }.contains(threadId)
//                if (isConversationDeleted && !isTemporaryThread) {
//                    conversationsDB.deleteThreadId(threadId)
//                }
//
//                val newConversation = conversations.find { it.phoneNumber == cachedConversation.phoneNumber }
//                if (isTemporaryThread && newConversation != null) {
//                    // delete the original temporary thread and move any scheduled messages to the new thread
//                    conversationsDB.deleteThreadId(threadId)
//                    messagesDB.getScheduledThreadMessages(threadId)
//                        .forEach { message ->
//                            messagesDB.insertOrUpdate(message.copy(threadId = newConversation.threadId))
//                        }
//                    insertOrUpdateConversation(newConversation, cachedConversation)
//                }
//            }
//
//            cachedConversations.forEach { cachedConv ->
//                val conv = conversations.find {
//                    it.threadId == cachedConv.threadId && !Conversation.areContentsTheSame(cachedConv, it)
//                }
//                if (conv != null) {
//                    val lastModified = maxOf(cachedConv.date, conv.date)
//                    val conversation = conv.copy(date = lastModified)
//                    insertOrUpdateConversation(conversation)
//                }
//            }
//
//            val allConversations = conversationsDB.getNonArchived() as ArrayList<Conversation>
//            runOnUiThread {
//                setupConversations(allConversations)
//            }
//
//            if (config.appRunCount == 1) {
//                conversations.map { it.threadId }.forEach { threadId ->
//                    val messages = getMessages(threadId, getImageResolutions = false, includeScheduledMessages = false)
//                    messages.chunked(30).forEach { currentMessages ->
//                        messagesDB.insertMessages(*currentMessages.toTypedArray())
//                    }
//                }
//            }
//        }
//    }
//
//    private fun getOrCreateConversationsAdapter(): ConversationsAdapter {
//        var currAdapter = binding.conversationsList.adapter
//        if (currAdapter == null) {
//            hideKeyboard()
//            currAdapter = ConversationsAdapter(
//                activity = this,
//                recyclerView = binding.conversationsList,
//                onRefresh = { notifyDatasetChanged() },
//                itemClick = { handleConversationClick(it) }
//            )
//
//            binding.conversationsList.adapter = currAdapter
//            if (areSystemAnimationsEnabled) {
//                binding.conversationsList.scheduleLayoutAnimation()
//            }
//        }
//        return currAdapter as ConversationsAdapter
//    }
//
//    private fun setupConversations(conversations: ArrayList<Conversation>, cached: Boolean = false) {
//        allConversations = ArrayList(conversations)
//        updateFilteredConversations(cached)
//    }
//
//    private fun showOrHideProgress(show: Boolean) {
//        if (show) {
//            binding.conversationsProgressBar.show()
//            binding.noConversationsPlaceholder.beVisible()
//            binding.noConversationsPlaceholder.text = getString(R.string.loading_messages)
//        } else {
//            binding.conversationsProgressBar.hide()
//            binding.noConversationsPlaceholder.beGone()
//        }
//    }
//
//    private fun showOrHidePlaceholder(show: Boolean) {
//        binding.conversationsFastscroller.beGoneIf(show)
//        binding.noConversationsPlaceholder.beVisibleIf(show)
//        binding.noConversationsPlaceholder.text = getString(R.string.no_conversations_found)
//        binding.noConversationsPlaceholder2.beVisibleIf(show)
//    }
//
//    private fun fadeOutSearch() {
//        binding.searchHolder.animate().alpha(0f).setDuration(SHORT_ANIMATION_DURATION).withEndAction {
//            binding.searchHolder.beGone()
//            searchTextChanged("", true)
//        }.start()
//    }
//
//    @SuppressLint("NotifyDataSetChanged")
//    private fun notifyDatasetChanged() {
//        getOrCreateConversationsAdapter().notifyDataSetChanged()
//    }
//
//    private fun handleConversationClick(any: Any) {
//        Intent(this, ThreadActivity::class.java).apply {
//            val conversation = any as Conversation
//            putExtra(THREAD_ID, conversation.threadId)
//            putExtra(THREAD_TITLE, conversation.title)
//            putExtra(WAS_PROTECTION_HANDLED, wasProtectionHandled)
//            startActivity(this)
//        }
//    }
//
//    private fun launchNewConversation() {
//        hideKeyboard()
//        Intent(this, NewConversationActivity::class.java).apply {
//            startActivity(this)
//        }
//    }
//
//    @SuppressLint("NewApi")
//    private fun checkShortcut() {
//        val appIconColor = config.appIconColor
//        if (isNougatMR1Plus() && config.lastHandledShortcutColor != appIconColor) {
//            val newConversation = getCreateNewContactShortcut(appIconColor)
//
//            val manager = getSystemService(ShortcutManager::class.java)
//            try {
//                manager.dynamicShortcuts = listOf(newConversation)
//                config.lastHandledShortcutColor = appIconColor
//            } catch (ignored: Exception) {
//            }
//        }
//    }
//
//    @SuppressLint("NewApi")
//    private fun getCreateNewContactShortcut(appIconColor: Int): ShortcutInfo {
//        val newEvent = getString(R.string.new_conversation)
//        val drawable = resources.getDrawable(R.drawable.shortcut_plus)
//        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_plus_background).applyColorFilter(appIconColor)
//        val bmp = drawable.convertToBitmap()
//
//        val intent = Intent(this, NewConversationActivity::class.java)
//        intent.action = Intent.ACTION_VIEW
//        return ShortcutInfo.Builder(this, "new_conversation")
//            .setShortLabel(newEvent)
//            .setLongLabel(newEvent)
//            .setIcon(Icon.createWithBitmap(bmp))
//            .setIntent(intent)
//            .build()
//    }
//
//    private fun searchTextChanged(text: String, forceUpdate: Boolean = false) {
//        if (!binding.mainMenu.isSearchOpen && !forceUpdate) {
//            return
//        }
//
//        lastSearchedText = text
//        binding.searchPlaceholder2.beGoneIf(text.length >= 2)
//        if (text.length >= 2) {
//            ensureBackgroundThread {
//                val searchQuery = "%$text%"
//                val messages = messagesDB.getMessagesWithText(searchQuery)
//                val conversations = conversationsDB.getConversationsWithText(searchQuery)
//                if (text == lastSearchedText) {
//                    showSearchResults(messages, conversations, text)
//                }
//            }
//        } else {
//            binding.searchPlaceholder.beVisible()
//            binding.searchResultsList.beGone()
//        }
//    }
//
//    private fun showSearchResults(messages: List<Message>, conversations: List<Conversation>, searchedText: String) {
//        val searchResults = ArrayList<SearchResult>()
//        conversations.forEach { conversation ->
//            val date = conversation.date.formatDateOrTime(this, true, true)
//            val searchResult = SearchResult(-1, conversation.title, conversation.phoneNumber, date, conversation.threadId, conversation.photoUri)
//            searchResults.add(searchResult)
//        }
//
//        messages.sortedByDescending { it.id }.forEach { message ->
//            var recipient = message.senderName
//            if (recipient.isEmpty() && message.participants.isNotEmpty()) {
//                val participantNames = message.participants.map { it.name }
//                recipient = TextUtils.join(", ", participantNames)
//            }
//
//            val date = message.date.formatDateOrTime(this, true, true)
//            val searchResult = SearchResult(message.id, recipient, message.body, date, message.threadId, message.senderPhotoUri)
//            searchResults.add(searchResult)
//        }
//
//        runOnUiThread {
//            binding.searchResultsList.beVisibleIf(searchResults.isNotEmpty())
//            binding.searchPlaceholder.beVisibleIf(searchResults.isEmpty())
//
//            val currAdapter = binding.searchResultsList.adapter
//            if (currAdapter == null) {
//                SearchResultsAdapter(this, searchResults, binding.searchResultsList, searchedText) {
//                    hideKeyboard()
//                    Intent(this, ThreadActivity::class.java).apply {
//                        putExtra(THREAD_ID, (it as SearchResult).threadId)
//                        putExtra(THREAD_TITLE, it.title)
//                        putExtra(SEARCHED_MESSAGE_ID, it.messageId)
//                        startActivity(this)
//                    }
//                }.apply {
//                    binding.searchResultsList.adapter = this
//                }
//            } else {
//                (currAdapter as SearchResultsAdapter).updateItems(searchResults, searchedText)
//            }
//        }
//    }
//
//    private fun launchRecycleBin() {
//        hideKeyboard()
//        startActivity(Intent(applicationContext, RecycleBinConversationsActivity::class.java))
//    }
//
//    private fun launchArchivedConversations() {
//        hideKeyboard()
//        startActivity(Intent(applicationContext, ArchivedConversationsActivity::class.java))
//    }
//
//    private fun launchSettings() {
//        hideKeyboard()
//        startActivity(Intent(applicationContext, SettingsActivity::class.java))
//    }
//
//    private fun launchAbout() {
//        val licenses = LICENSE_EVENT_BUS or LICENSE_SMS_MMS or LICENSE_INDICATOR_FAST_SCROLL
//
//        val faqItems = arrayListOf(
//            FAQItem(R.string.faq_2_title, R.string.faq_2_text),
//            FAQItem(R.string.faq_3_title, R.string.faq_3_text),
//            FAQItem(R.string.faq_9_title_commons, R.string.faq_9_text_commons)
//        )
//
//        if (!resources.getBoolean(R.bool.hide_google_relations)) {
//            faqItems.add(FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons))
//            faqItems.add(FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons))
//        }
//
//        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
//    }
//
//    @Subscribe(threadMode = ThreadMode.MAIN)
//    fun refreshMessages(event: Events.RefreshMessages) {
//        initMessenger()
//    }
//
//    private fun checkWhatsNewDialog() {
//        arrayListOf<Release>().apply {
//            add(Release(48, R.string.release_48))
//            add(Release(62, R.string.release_62))
//            checkWhatsNew(this, BuildConfig.VERSION_CODE)
//        }
//    }
//
//    private fun setupConversationFilters() {
//        binding.conversationFilterGroup.addOnButtonCheckedListener { _: MaterialButtonToggleGroup, checkedId: Int, isChecked: Boolean ->
//            if (!isChecked) {
//                return@addOnButtonCheckedListener
//            }
//
//            val newFilter = when (checkedId) {
//                R.id.filter_inbox -> ConversationFilter.INBOX
//                R.id.filter_spam -> ConversationFilter.SPAM
//                R.id.filter_blocked -> ConversationFilter.BLOCKED
//                else -> ConversationFilter.ALL
//            }
//
//            if (newFilter != currentFilter) {
//                currentFilter = newFilter
//                updateFilteredConversations()
//            }
//        }
//
//        if (binding.conversationFilterGroup.checkedButtonId == View.NO_ID) {
//            binding.conversationFilterGroup.check(R.id.filter_all)
//        } else {
//            currentFilter = when (binding.conversationFilterGroup.checkedButtonId) {
//                R.id.filter_inbox -> ConversationFilter.INBOX
//                R.id.filter_spam -> ConversationFilter.SPAM
//                R.id.filter_blocked -> ConversationFilter.BLOCKED
//                else -> ConversationFilter.ALL
//            }
//        }
//    }
//
//    private fun updateFilteredConversations(cached: Boolean = false) {
//        val filtered = filterConversations(allConversations)
//        val sortedConversations = sortConversations(filtered)
//
//        if (cached && config.appRunCount == 1) {
//            showOrHideProgress(sortedConversations.isEmpty())
//        } else {
//            showOrHideProgress(false)
//            showOrHidePlaceholder(sortedConversations.isEmpty())
//        }
//
//        try {
//            getOrCreateConversationsAdapter().apply {
//                updateConversations(sortedConversations) {
//                    if (!cached) {
//                        showOrHidePlaceholder(currentList.isEmpty())
//                    }
//                }
//            }
//        } catch (ignored: Exception) {
//        }
//    }
//
//    private fun sortConversations(conversations: List<Conversation>): ArrayList<Conversation> {
//        return conversations.sortedWith(
//            compareByDescending<Conversation> { config.pinnedConversations.contains(it.threadId.toString()) }
//                .thenByDescending { it.date }
//        ).toMutableList() as ArrayList<Conversation>
//    }
//
//    private fun filterConversations(conversations: List<Conversation>): List<Conversation> {
//        if (conversations.isEmpty()) {
//            return emptyList()
//        }
//
//        val blockedNumbers = getBlockedNumbers()
//
//        return when (currentFilter) {
//            ConversationFilter.ALL -> conversations
//            ConversationFilter.INBOX -> conversations.filterNot { conversation ->
//                isNumberBlocked(conversation.phoneNumber, blockedNumbers) || conversation.isSpam()
//            }
//
//            ConversationFilter.SPAM -> conversations.filter { conversation ->
//                conversation.isSpam() && !isNumberBlocked(conversation.phoneNumber, blockedNumbers)
//            }
//
//            ConversationFilter.BLOCKED -> conversations.filter { conversation ->
//                isNumberBlocked(conversation.phoneNumber, blockedNumbers)
//            }
//        }
//    }
//
//    private fun Conversation.isSpam(): Boolean {
//        val snippetLower = snippet.lowercase(Locale.getDefault())
//        val keywordMatch = spamKeywords.any { keyword ->
//            snippetLower.contains(keyword)
//        }
//
//        val senderLooksUnknown = title.equals(phoneNumber, ignoreCase = true) || title.isEmpty()
//        val isShortCode = phoneNumber.length in 5..7 && phoneNumber.all { it.isDigit() }
//        val containsLetters = phoneNumber.any { it.isLetter() }
//
//        return keywordMatch || ((isShortCode || containsLetters) && senderLooksUnknown)
//    }
//
//    private enum class ConversationFilter {
//        ALL,
//        INBOX,
//        SPAM,
//        BLOCKED
//    }
//}
