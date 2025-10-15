package app.trusted.callerid.sms.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.provider.Telephony
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.button.MaterialButtonToggleGroup
import com.simplemobiletools.commons.dialogs.PermissionRequiredDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import app.trusted.callerid.sms.activities.MainActivity
import app.trusted.callerid.sms.databinding.FragmentMessagesBinding
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.*
import com.simplemobiletools.smsmessenger.adapters.ConversationsAdapter
import com.simplemobiletools.smsmessenger.adapters.SearchResultsAdapter
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.SEARCHED_MESSAGE_ID
import com.simplemobiletools.smsmessenger.helpers.THREAD_ID
import com.simplemobiletools.smsmessenger.helpers.THREAD_TITLE
import com.simplemobiletools.smsmessenger.models.Conversation
import com.simplemobiletools.smsmessenger.models.Message
import com.simplemobiletools.smsmessenger.models.SearchResult
import java.util.Locale

class MessagesFragment(val mContext: Context, attributeSet: AttributeSet) :
    MyViewPagerFragment<MyViewPagerFragment.MessagesInnerBinding>(mContext, attributeSet) {

    lateinit var msgBinding: FragmentMessagesBinding

    private val makeDefaultSmsAppLauncher =
        activity?.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                askPermissions()
            } else {
                activity?.finish()
            }
        }


    private val MAKE_DEFAULT_APP_REQUEST = 1

    var storedTextColor = 0
    private var storedFontSize = 0
    private var lastSearchedText = ""
    private var isSearchActive = false

    //private var bus: EventBus? = null
    private var wasProtectionHandled = false
    private var filtersInitialized = false
    private var allConversations = arrayListOf<Conversation>()
    private var currentFilter = ConversationFilter.ALL
    private val spamKeywords by lazy {
        setOf(
            "win",
            "winner",
            "prize",
            "offer",
            "free",
            "trial",
            "loan",
            "credit",
            "bitcoin",
            "crypto",
            "investment",
            "cashback",
            "reward",
            "urgent",
            "limited time",
            "guaranteed",
            "click",
            "verify",
            "bank account"
        ).map { it.lowercase(Locale.getDefault()) }
    }

    private val otpKeywords by lazy {
        setOf(
            "otp",
            "one time password",
            "one-time password",
            "verification code",
            "verification",
            "security code",
            "auth code",
            "login code",
            "passcode",
            "pin code",
            "two factor",
            "two-factor",
            "2fa",
            "use this code",
            "code is"
        ).map { it.lowercase(Locale.getDefault()) }
    }

    private val otpCodeRegex by lazy { Regex("\\b\\d{4,8}\\b") }

    private val promotionalKeywords by lazy {
        setOf(
            "Levis",
            "sale",
            "discount",
            "offer",
            "deal",
            "voucher",
            "promo",
            "promotion",
            "exclusive",
            "limited",
            "coupon",
            "subscribe",
            "membership",
            "enroll",
            "upgrade",
            "new launch",
            "introducing"
        ).map { it.lowercase(Locale.getDefault()) }
    }

    private enum class ConversationFilter {
        ALL,
        INBOX,
        OTP,
        PROMOTIONAL,
        SPAM,
        BLOCKED
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        msgBinding = FragmentMessagesBinding.bind(this)
        innerBinding = MessagesInnerBinding(msgBinding)

    }

    override fun setupFragment() {
        refreshToolbarMenu()

        mContext.checkAndDeleteOldRecycleBinMessages()
        activity?.handleAppPasswordProtection {
            wasProtectionHandled = it
            if (it) {
                mContext.clearAllMessagesIfNeeded {
                    loadMessages()
                }
            } else {
                activity?.finish()
            }
        }


        if (activity?.checkAppSideloading() == true) {
            return
        }
    }

    private fun refreshToolbarMenu() {
        (activity as? MainActivity)?.refreshMenuItems()
    }


    private fun storeStateVariables() {
        storedTextColor = mContext.getProperTextColor()
        storedFontSize = config.fontSize
    }

    private fun loadMessages() {
        if (isQPlus()) {
            val roleManager = mContext.getSystemService(RoleManager::class.java)
            if (roleManager!!.isRoleAvailable(RoleManager.ROLE_SMS)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    askPermissions()
                } else {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    makeDefaultSmsAppLauncher?.launch(intent)
                }
            } else {
                mContext.toast(R.string.unknown_error_occurred)
                activity?.finish()
            }
        } else {
            if (Telephony.Sms.getDefaultSmsPackage(mContext) == mContext.packageName) {
                askPermissions()
            } else {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, mContext.packageName)
                makeDefaultSmsAppLauncher?.launch(intent)
            }
        }
    }

    // while SEND_SMS and READ_SMS permissions are mandatory, READ_CONTACTS is optional. If we don't have it, we just won't be able to show the contact name in some cases
    private fun askPermissions() {
        activity?.apply {
            handlePermission(PERMISSION_READ_SMS) {
                if (it) {
                    handlePermission(PERMISSION_SEND_SMS) {
                        if (it) {
                            handlePermission(PERMISSION_READ_CONTACTS) {
                                handleNotificationPermission { granted ->
                                    if (!granted) {
                                        PermissionRequiredDialog(
                                            activity = this,
                                            textId = R.string.allow_notifications_incoming_messages,
                                            positiveActionCallback = { openNotificationSettings() })
                                    }
                                }

                                initMessenger()
                                //bus = EventBus.getDefault()
                                //try {
                                //    bus!!.register(this)
                                //} catch (ignored: Exception) {
                                // }
                            }
                        } else {
                            finish()
                        }
                    }
                } else {
                    finish()
                }
            }
        }
    }

    private fun initMessenger() {
        //checkWhatsNewDialog()
        storeStateVariables()
        getCachedConversations()

        msgBinding.noConversationsPlaceholder2.setOnClickListener {
            launchNewConversation()
        }

        msgBinding.conversationsFab.setOnClickListener {
            launchNewConversation()
        }

        if (!filtersInitialized) {
            setupConversationFilters()
            filtersInitialized = true
        } else {
            updateFilteredConversations()
        }
    }

    private fun getCachedConversations() {
        ensureBackgroundThread {
            val conversations = try {
                mContext.conversationsDB.getNonArchived().toMutableList() as ArrayList<Conversation>
            } catch (e: Exception) {
                ArrayList()
            }

            val archived = try {
                mContext.conversationsDB.getAllArchived()
            } catch (e: Exception) {
                listOf()
            }

            mContext.updateUnreadCountBadge(conversations)
            activity?.runOnUiThread {
                setupConversations(conversations, cached = true)
                getNewConversations((conversations + archived).toMutableList() as ArrayList<Conversation>)
            }
            conversations.forEach {
                mContext.clearExpiredScheduledMessages(it.threadId)
            }
        }
    }

    private fun getNewConversations(cachedConversations: ArrayList<Conversation>) {
        val privateCursor = mContext.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ensureBackgroundThread {
            val privateContacts = MyContactsContentProvider.getSimpleContacts(mContext, privateCursor)
            val conversations = mContext.getConversations(privateContacts = privateContacts)

            conversations.forEach { clonedConversation ->
                val threadIds = cachedConversations.map { it.threadId }
                if (!threadIds.contains(clonedConversation.threadId)) {
                    mContext.conversationsDB.insertOrUpdate(clonedConversation)
                    cachedConversations.add(clonedConversation)
                }
            }

            cachedConversations.forEach { cachedConversation ->
                val threadId = cachedConversation.threadId

                val isTemporaryThread = cachedConversation.isScheduled
                val isConversationDeleted = !conversations.map { it.threadId }.contains(threadId)
                if (isConversationDeleted && !isTemporaryThread) {
                    mContext.conversationsDB.deleteThreadId(threadId)
                }

                val newConversation = conversations.find { it.phoneNumber == cachedConversation.phoneNumber }
                if (isTemporaryThread && newConversation != null) {
                    // delete the original temporary thread and move any scheduled messages to the new thread
                    mContext.conversationsDB.deleteThreadId(threadId)
                    mContext.messagesDB.getScheduledThreadMessages(threadId)
                        .forEach { message ->
                            mContext.messagesDB.insertOrUpdate(message.copy(threadId = newConversation.threadId))
                        }
                    mContext.insertOrUpdateConversation(newConversation, cachedConversation)
                }
            }

            cachedConversations.forEach { cachedConv ->
                val conv = conversations.find {
                    it.threadId == cachedConv.threadId && !Conversation.areContentsTheSame(cachedConv, it)
                }
                if (conv != null) {
                    val lastModified = maxOf(cachedConv.date, conv.date)
                    val conversation = conv.copy(date = lastModified)
                    mContext.insertOrUpdateConversation(conversation)
                }
            }

            val allConversations = mContext.conversationsDB.getNonArchived() as ArrayList<Conversation>
            activity?.runOnUiThread {
                setupConversations(allConversations)
            }

            if (config.appRunCount == 1) {
                conversations.map { it.threadId }.forEach { threadId ->
                    val messages = mContext.getMessages(threadId, getImageResolutions = false, includeScheduledMessages = false)
                    messages.chunked(30).forEach { currentMessages ->
                        mContext.messagesDB.insertMessages(*currentMessages.toTypedArray())
                    }
                }
            }
        }
    }

    fun getOrCreateConversationsAdapter(): ConversationsAdapter {
        var currAdapter = msgBinding.conversationsList.adapter
        if (currAdapter == null) {
            activity?.hideKeyboard()
            currAdapter = ConversationsAdapter(
                activity = activity!!,
                recyclerView = msgBinding.conversationsList,
                onRefresh = { notifyDatasetChanged() },
                itemClick = { handleConversationClick(it) }
            )

            msgBinding.conversationsList.adapter = currAdapter
            if (mContext.areSystemAnimationsEnabled) {
                msgBinding.conversationsList.scheduleLayoutAnimation()
            }
        }
        return currAdapter as ConversationsAdapter
    }

    private fun setupConversations(conversations: ArrayList<Conversation>, cached: Boolean = false) {
        allConversations = ArrayList(conversations)
        updateFilteredConversations(cached)
        refreshToolbarMenu()
    }

    private fun showOrHideProgress(show: Boolean) {
        if (show) {
            msgBinding.conversationsProgressBar.show()
            msgBinding.noConversationsPlaceholder.beVisible()
            msgBinding.noConversationsPlaceholder.text = mContext.getString(R.string.loading_messages)
        } else {
            msgBinding.conversationsProgressBar.hide()
            msgBinding.noConversationsPlaceholder.beGone()
        }
    }

    private fun showOrHidePlaceholder(show: Boolean) {
        msgBinding.conversationsFastscroller.beGoneIf(show)
        msgBinding.noConversationsPlaceholder.beVisibleIf(show)
        msgBinding.noConversationsPlaceholder.text = mContext.getString(R.string.no_conversations_found)
        msgBinding.noConversationsPlaceholder2.beVisibleIf(show)
    }

    private fun fadeOutSearch() {
        if (!isSearchActive && msgBinding.searchHolder.alpha == 0f) {
            searchTextChanged("", true)
            return
        }

        isSearchActive = false
        msgBinding.searchHolder.animate().alpha(0f).setDuration(SHORT_ANIMATION_DURATION).withEndAction {
            msgBinding.searchHolder.beGone()
            searchTextChanged("", true)
        }.start()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyDatasetChanged() {
        getOrCreateConversationsAdapter().notifyDataSetChanged()
    }

    private fun handleConversationClick(any: Any) {
        Intent(mContext, ThreadActivity::class.java).apply {
            val conversation = any as Conversation
            putExtra(THREAD_ID, conversation.threadId)
            putExtra(THREAD_TITLE, conversation.title)
            putExtra(WAS_PROTECTION_HANDLED, wasProtectionHandled)
            activity?.startActivity(this)
        }
    }

    private fun launchNewConversation() {
        activity?.hideKeyboard()
        Intent(mContext, NewConversationActivity::class.java).apply {
            activity?.startActivity(this)
        }
    }

    @SuppressLint("NewApi")
    fun checkShortcut() {
        val appIconColor = config.appIconColor
        if (isNougatMR1Plus() && config.lastHandledShortcutColor != appIconColor) {
            val newConversation = getCreateNewContactShortcut(appIconColor)

            val manager = mContext.getSystemService(ShortcutManager::class.java)
            try {
                manager.dynamicShortcuts = listOf(newConversation)
                config.lastHandledShortcutColor = appIconColor
            } catch (ignored: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getCreateNewContactShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = mContext.getString(R.string.new_conversation)
        val drawable = resources.getDrawable(R.drawable.shortcut_plus)
        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_plus_background).applyColorFilter(appIconColor)
        val bmp = drawable.convertToBitmap()

        val intent = Intent(mContext, NewConversationActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(mContext, "new_conversation")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .build()
    }

    private fun searchTextChanged(text: String, forceUpdate: Boolean = false) {
        if (!isSearchActive && !forceUpdate) {
            return
        }

        lastSearchedText = text
        msgBinding.searchPlaceholder2.beGoneIf(text.length >= 2)
        if (text.length >= 2) {
            ensureBackgroundThread {
                val searchQuery = "%$text%"
                val messages = mContext.messagesDB.getMessagesWithText(searchQuery)
                val conversations = mContext.conversationsDB.getConversationsWithText(searchQuery)
                if (text == lastSearchedText) {
                    showSearchResults(messages, conversations, text)
                }
            }
        } else {
            msgBinding.searchPlaceholder.beVisible()
            msgBinding.searchResultsList.beGone()
        }
    }

    private fun showSearchResults(messages: List<Message>, conversations: List<Conversation>, searchedText: String) {
        val searchResults = ArrayList<SearchResult>()
        conversations.forEach { conversation ->
            val date = conversation.date.formatDateOrTime(mContext, true, true)
            val searchResult = SearchResult(-1, conversation.title, conversation.phoneNumber, date, conversation.threadId, conversation.photoUri)
            searchResults.add(searchResult)
        }

        messages.sortedByDescending { it.id }.forEach { message ->
            var recipient = message.senderName
            if (recipient.isEmpty() && message.participants.isNotEmpty()) {
                val participantNames = message.participants.map { it.name }
                recipient = TextUtils.join(", ", participantNames)
            }

            val date = message.date.formatDateOrTime(mContext, true, true)
            val searchResult = SearchResult(message.id, recipient, message.body, date, message.threadId, message.senderPhotoUri)
            searchResults.add(searchResult)
        }

        activity?.runOnUiThread {
            msgBinding.searchResultsList.beVisibleIf(searchResults.isNotEmpty())
            msgBinding.searchPlaceholder.beVisibleIf(searchResults.isEmpty())

            val currAdapter = msgBinding.searchResultsList.adapter
            if (currAdapter == null) {
                SearchResultsAdapter(mContext, searchResults, msgBinding.searchResultsList, searchedText) {
                    activity?.hideKeyboard()
                    Intent(mContext, ThreadActivity::class.java).apply {
                        putExtra(THREAD_ID, (it as SearchResult).threadId)
                        putExtra(THREAD_TITLE, it.title)
                        putExtra(SEARCHED_MESSAGE_ID, it.messageId)
                        activity?.startActivity(this)
                    }
                }.apply {
                    msgBinding.searchResultsList.adapter = this
                }
            } else {
                (currAdapter as SearchResultsAdapter).updateItems(searchResults, searchedText)
            }
        }
    }

    fun launchRecycleBin() {
        activity?.hideKeyboard()
        mContext.startActivity(Intent(mContext, RecycleBinConversationsActivity::class.java))
    }

    fun launchArchivedConversations() {
        activity?.hideKeyboard()
        mContext.startActivity(Intent(mContext, ArchivedConversationsActivity::class.java))
    }

    fun launchSettings() {
        activity?.hideKeyboard()
        mContext.startActivity(Intent(mContext, SettingsActivity::class.java))
    }


    private fun setupConversationFilters() {
        msgBinding.conversationFilterGroup.addOnButtonCheckedListener { _: MaterialButtonToggleGroup, checkedId: Int, isChecked: Boolean ->
            if (!isChecked) {
                return@addOnButtonCheckedListener
            }

            val newFilter = when (checkedId) {
                R.id.filter_inbox -> ConversationFilter.INBOX
                R.id.filter_otp -> ConversationFilter.OTP
                R.id.filter_spam -> ConversationFilter.SPAM
                R.id.filter_promotional -> ConversationFilter.PROMOTIONAL
                R.id.filter_blocked -> ConversationFilter.BLOCKED
                else -> ConversationFilter.ALL
            }

            if (newFilter != currentFilter) {
                currentFilter = newFilter
                updateFilteredConversations()
            }
        }

        if (msgBinding.conversationFilterGroup.checkedButtonId == View.NO_ID) {
            msgBinding.conversationFilterGroup.check(R.id.filter_all)
        } else {
            currentFilter = when (msgBinding.conversationFilterGroup.checkedButtonId) {
                R.id.filter_inbox -> ConversationFilter.INBOX
                R.id.filter_otp -> ConversationFilter.OTP
                R.id.filter_spam -> ConversationFilter.SPAM
                R.id.filter_promotional -> ConversationFilter.PROMOTIONAL
                R.id.filter_blocked -> ConversationFilter.BLOCKED
                else -> ConversationFilter.ALL
            }
        }
    }

    private fun updateFilteredConversations(cached: Boolean = false) {
        val filtered = filterConversations(allConversations)
        val sortedConversations = sortConversations(filtered)

        if (cached && config.appRunCount == 1) {
            showOrHideProgress(sortedConversations.isEmpty())
        } else {
            showOrHideProgress(false)
            showOrHidePlaceholder(sortedConversations.isEmpty())
        }

        try {
            getOrCreateConversationsAdapter().apply {
                updateConversations(sortedConversations) {
                    if (!cached) {
                        showOrHidePlaceholder(currentList.isEmpty())
                    }
                }
            }
        } catch (ignored: Exception) {
        }
    }

    private fun sortConversations(conversations: List<Conversation>): ArrayList<Conversation> {
        return conversations.sortedWith(
            compareByDescending<Conversation> { config_sms.pinnedConversations.contains(it.threadId.toString()) }
                .thenByDescending { it.date }
        ).toMutableList() as ArrayList<Conversation>
    }

    private fun filterConversations(conversations: List<Conversation>): List<Conversation> {
        if (conversations.isEmpty()) {
            return emptyList()
        }

        val blockedNumbers = mContext.getBlockedNumbers()

        return when (currentFilter) {
            ConversationFilter.ALL -> conversations
            ConversationFilter.INBOX -> conversations.filterNot { conversation ->
                mContext.isNumberBlocked(conversation.phoneNumber, blockedNumbers) || conversation.isSpam()
            }

            ConversationFilter.SPAM -> conversations.filter { conversation ->
                conversation.isSpam() && !mContext.isNumberBlocked(conversation.phoneNumber, blockedNumbers)
            }

            ConversationFilter.OTP -> conversations.filter { conversation ->
                conversation.isOtp() && !mContext.isNumberBlocked(conversation.phoneNumber, blockedNumbers)
            }

            ConversationFilter.PROMOTIONAL -> conversations.filter { conversation ->
                conversation.isPromotional() && !mContext.isNumberBlocked(conversation.phoneNumber, blockedNumbers)
            }

            ConversationFilter.BLOCKED -> conversations.filter { conversation ->
                mContext.isNumberBlocked(conversation.phoneNumber, blockedNumbers)
            }
        }
    }

    private fun Conversation.isSpam(): Boolean {
        val snippetLower = snippet.lowercase(Locale.getDefault())
        val keywordMatch = spamKeywords.any { keyword ->
            snippetLower.contains(keyword)
        }

        val senderLooksUnknown = title.equals(phoneNumber, ignoreCase = true) || title.isEmpty()
        val isShortCode = phoneNumber.length in 5..7 && phoneNumber.all { it.isDigit() }
        val containsLetters = phoneNumber.any { it.isLetter() }

        return keywordMatch || ((isShortCode || containsLetters) && senderLooksUnknown)
    }

    private fun Conversation.isOtp(): Boolean {
        val snippetLower = snippet.lowercase(Locale.getDefault())
        val titleLower = title.lowercase(Locale.getDefault())
        val keywordMatch = otpKeywords.any { keyword ->
            snippetLower.contains(keyword) || titleLower.contains(keyword)
        }

        if (!keywordMatch) {
            return false
        }

        val codeMatch = otpCodeRegex.containsMatchIn(snippet)
        return codeMatch && !isSpam()
    }

    private fun Conversation.isPromotional(): Boolean {
        val snippetLower = snippet.lowercase(Locale.getDefault())
        val titleLower = title.lowercase(Locale.getDefault())
        val keywordMatch = promotionalKeywords.any { keyword ->
            snippetLower.contains(keyword) || titleLower.contains(keyword)
        }
        return keywordMatch && !isSpam() && !isOtp()
    }


    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        //binding.messagesPlaceholder.setTextColor(textColor)
        //binding.messagesPlaceholderHolder.setBackgroundColor(0)

        //binding.openMessagesButton.apply {
        //   backgroundTintList = ColorStateList.valueOf(primaryColor)
        //    setTextColor(primaryColor.getContrastColor())
        // }
    }

    fun refreshItems(callback: (() -> Unit)?) {
        callback?.invoke()
    }

    override fun onSearchClosed() {
        fadeOutSearch()
    }

    override fun onSearchQueryChanged(text: String) {
        if (text.isEmpty()) {
            fadeOutSearch()
            return
        }

        if (!isSearchActive) {
            msgBinding.searchHolder.alpha = 0f
        }

        isSearchActive = true
        msgBinding.searchHolder.beVisible()
        if (msgBinding.searchHolder.alpha < 1f) {
            msgBinding.searchHolder.fadeIn()
        }
        searchTextChanged(text, true)
    }
}
