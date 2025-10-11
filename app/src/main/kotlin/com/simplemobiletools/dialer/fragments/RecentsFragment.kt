package com.simplemobiletools.dialer.fragments

import android.content.Context
import android.provider.CallLog.Calls
import android.util.AttributeSet
import com.simplemobiletools.commons.dialogs.CallConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ContactsHelper
import com.simplemobiletools.commons.helpers.MyContactsContentProvider
import com.simplemobiletools.commons.helpers.PERMISSION_READ_CALL_LOG
import com.simplemobiletools.commons.helpers.SMT_PRIVATE
import com.simplemobiletools.commons.models.contacts.Contact
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.adapters.RecentCallsAdapter
import com.simplemobiletools.dialer.databinding.FragmentRecentsBinding
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.helpers.MIN_RECENTS_THRESHOLD
import com.simplemobiletools.dialer.helpers.RecentsHelper
import com.simplemobiletools.dialer.interfaces.RefreshItemsListener
import com.simplemobiletools.dialer.models.RecentCall

class RecentsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment<MyViewPagerFragment.RecentsInnerBinding>(context, attributeSet),
    RefreshItemsListener {
    private lateinit var binding: FragmentRecentsBinding
    private var allRecentCalls = listOf<RecentCall>()
    private var recentsAdapter: RecentCallsAdapter? = null
    private var activeFilter = CallHistoryFilter.ALL
    private var currentSearchQuery = ""

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentRecentsBinding.bind(this)
        innerBinding = RecentsInnerBinding(binding)
    }

    override fun setupFragment() {
        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CALL_LOG)) {
            R.string.no_previous_calls
        } else {
            R.string.could_not_access_the_call_history
        }

        binding.recentsPlaceholder.text = context.getString(placeholderResId)
        binding.recentsPlaceholder2.apply {
            underlineText()
            setOnClickListener {
                requestCallLogPermission()
            }
        }

        binding.recentsFilterGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) {
                return@addOnButtonCheckedListener
            }

            activeFilter = when (checkedId) {
                R.id.recents_filter_missed -> CallHistoryFilter.MISSED
                R.id.recents_filter_incoming -> CallHistoryFilter.INCOMING
                R.id.recents_filter_outgoing -> CallHistoryFilter.OUTGOING
                R.id.recents_filter_spam -> CallHistoryFilter.SPAM
                else -> CallHistoryFilter.ALL
            }
            updateDisplayedRecents()
        }
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        binding.recentsPlaceholder.setTextColor(textColor)
        binding.recentsPlaceholder2.setTextColor(properPrimaryColor)

        recentsAdapter?.apply {
            initDrawables()
            updateTextColor(textColor)
        }
    }

    override fun refreshItems(callback: (() -> Unit)?) {
        val privateCursor = context?.getMyContactsCursor(false, true)
        val groupSubsequentCalls = context?.config?.groupSubsequentCalls ?: false
        val querySize = allRecentCalls.size.coerceAtLeast(MIN_RECENTS_THRESHOLD)
        RecentsHelper(context).getRecentCalls(groupSubsequentCalls, querySize) { recents ->
            ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)

                val processedRecents = recents
                    .setNamesIfEmpty(contacts, privateContacts)
                    .hidePrivateContacts(privateContacts, SMT_PRIVATE in context.baseConfig.ignoredContactSources)

                activity?.runOnUiThread {
                    gotRecents(processedRecents)
                    callback?.invoke()
                }
            }
        }
    }

    private fun gotRecents(recents: List<RecentCall>) {
        allRecentCalls = recents

        if (allRecentCalls.isEmpty()) {
            recentsAdapter?.updateItems(emptyList())
            binding.apply {
                recentsPlaceholder.beVisible()
                recentsPlaceholder2.beGoneIf(context.hasPermission(PERMISSION_READ_CALL_LOG))
                recentsList.beGone()
            }
            return
        }

        binding.recentsPlaceholder2.beGoneIf(context.hasPermission(PERMISSION_READ_CALL_LOG))

        if (binding.recentsList.adapter == null) {
            initRecentsAdapter(getDisplayedRecents())
        }

        updateDisplayedRecents()
    }

    private fun getMoreRecentCalls() {
        val privateCursor = context?.getMyContactsCursor(false, true)
        val groupSubsequentCalls = context?.config?.groupSubsequentCalls ?: false
        val querySize = allRecentCalls.size.plus(MIN_RECENTS_THRESHOLD)
        RecentsHelper(context).getRecentCalls(groupSubsequentCalls, querySize) { recents ->
            ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)

                val processedRecents = recents
                    .setNamesIfEmpty(contacts, privateContacts)
                    .hidePrivateContacts(privateContacts, SMT_PRIVATE in context.baseConfig.ignoredContactSources)

                activity?.runOnUiThread {
                    gotRecents(processedRecents)
                }
            }
        }
    }

    private fun initRecentsAdapter(initialItems: List<RecentCall>) {
        recentsAdapter = RecentCallsAdapter(activity as SimpleActivity, initialItems.toMutableList(), binding.recentsList, this, true) {
            val recentCall = it as RecentCall
            if (context.config.showCallConfirmation) {
                CallConfirmationDialog(activity as SimpleActivity, recentCall.name) {
                    activity?.launchCallIntent(recentCall.phoneNumber)
                }
            } else {
                activity?.launchCallIntent(recentCall.phoneNumber)
            }
        }

        binding.recentsList.adapter = recentsAdapter

        if (context.areSystemAnimationsEnabled) {
            binding.recentsList.scheduleLayoutAnimation()
        }

        binding.recentsList.endlessScrollListener = object : MyRecyclerView.EndlessScrollListener {
            override fun updateTop() {}

            override fun updateBottom() {
                getMoreRecentCalls()
            }
        }
    }

    private fun updateDisplayedRecents() {
        if (allRecentCalls.isEmpty()) {
            binding.recentsPlaceholder.beVisible()
            binding.recentsList.beGone()
            return
        }

        val displayedRecents = getDisplayedRecents()

        if (binding.recentsList.adapter == null) {
            initRecentsAdapter(displayedRecents)
        }

        recentsAdapter?.updateItems(displayedRecents, currentSearchQuery)

        val hasResults = displayedRecents.isNotEmpty()
        binding.recentsPlaceholder.beVisibleIf(!hasResults)
        binding.recentsList.beVisibleIf(hasResults)
    }

    private fun getDisplayedRecents(): List<RecentCall> {
        val filtered = allRecentCalls.filter { it.matchesFilter(activeFilter) }
        return applySearchFilter(filtered, currentSearchQuery)
    }

    private fun applySearchFilter(recents: List<RecentCall>, query: String): List<RecentCall> {
        if (query.isBlank()) {
            return recents
        }

        return recents.filter {
            it.name.contains(query, true) || it.doesContainPhoneNumber(query)
        }.sortedByDescending {
            it.name.startsWith(query, true)
        }
    }

    private fun RecentCall.matchesFilter(filter: CallHistoryFilter): Boolean {
        return when (filter) {
            CallHistoryFilter.ALL -> true
            CallHistoryFilter.MISSED -> type == Calls.MISSED_TYPE || type == Calls.REJECTED_TYPE
            CallHistoryFilter.INCOMING -> type == Calls.INCOMING_TYPE || type == Calls.ANSWERED_EXTERNALLY_TYPE
            CallHistoryFilter.OUTGOING -> type == Calls.OUTGOING_TYPE
            CallHistoryFilter.SPAM -> type == Calls.BLOCKED_TYPE
        }
    }

    private fun requestCallLogPermission() {
        activity?.handlePermission(PERMISSION_READ_CALL_LOG) {
            if (it) {
                binding.recentsPlaceholder.text = context.getString(R.string.no_previous_calls)
                binding.recentsPlaceholder2.beGone()

                val groupSubsequentCalls = context?.config?.groupSubsequentCalls ?: false
                RecentsHelper(context).getRecentCalls(groupSubsequentCalls) { recents ->
                    activity?.runOnUiThread {
                        gotRecents(recents)
                    }
                }
            }
        }
    }

    private enum class CallHistoryFilter {
        ALL,
        MISSED,
        INCOMING,
        OUTGOING,
        SPAM
    }

    override fun onSearchClosed() {
        currentSearchQuery = ""
        updateDisplayedRecents()
    }

    override fun onSearchQueryChanged(text: String) {
        currentSearchQuery = text
        updateDisplayedRecents()
    }
}

// hide private contacts from recent calls
private fun List<RecentCall>.hidePrivateContacts(privateContacts: List<Contact>, shouldHide: Boolean): List<RecentCall> {
    return if (shouldHide) {
        filterNot { recent ->
            val privateNumbers = privateContacts.flatMap { it.phoneNumbers }.map { it.value }
            recent.phoneNumber in privateNumbers
        }
    } else {
        this
    }
}

private fun List<RecentCall>.setNamesIfEmpty(contacts: List<Contact>, privateContacts: List<Contact>): ArrayList<RecentCall> {
    val contactsWithNumbers = contacts.filter { it.phoneNumbers.isNotEmpty() }
    return map { recent ->
        if (recent.phoneNumber == recent.name) {
            val privateContact = privateContacts.firstOrNull { it.doesContainPhoneNumber(recent.phoneNumber) }
            val contact = contactsWithNumbers.firstOrNull { it.phoneNumbers.first().normalizedNumber == recent.phoneNumber }

            when {
                privateContact != null -> recent.copy(name = privateContact.getNameToDisplay())
                contact != null -> recent.copy(name = contact.getNameToDisplay())
                else -> recent
            }
        } else {
            recent
        }
    } as ArrayList
}
