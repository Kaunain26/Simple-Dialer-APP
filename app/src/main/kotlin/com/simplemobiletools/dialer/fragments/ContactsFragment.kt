package com.simplemobiletools.dialer.fragments

import android.content.Context
import android.util.AttributeSet
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.contacts.Contact
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.activities.MainActivity
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.adapters.ContactsAdapter
import com.simplemobiletools.dialer.databinding.FragmentContactsBinding
import com.simplemobiletools.dialer.databinding.FragmentLettersLayoutBinding
import com.simplemobiletools.dialer.extensions.launchCreateNewContactIntent
import com.simplemobiletools.dialer.extensions.startContactDetailsIntent
import com.simplemobiletools.dialer.interfaces.RefreshItemsListener
import java.util.Locale

class ContactsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment<MyViewPagerFragment.LettersInnerBinding>(context, attributeSet),
    RefreshItemsListener {
    private lateinit var binding: FragmentLettersLayoutBinding
    private var allContacts = ArrayList<Contact>()
    private var activeContactsFilter = ContactsFilter.ALL
    private var currentSearchQuery = ""

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentLettersLayoutBinding.bind(FragmentContactsBinding.bind(this).contactsFragment)
        innerBinding = LettersInnerBinding(binding)
    }

    override fun setupFragment() {
        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
            R.string.no_contacts_found
        } else {
            R.string.could_not_access_contacts
        }

        binding.fragmentPlaceholder.text = context.getString(placeholderResId)

        val placeholderActionResId = if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
            R.string.create_new_contact
        } else {
            R.string.request_access
        }

        binding.fragmentPlaceholder2.apply {
            text = context.getString(placeholderActionResId)
            underlineText()
            setOnClickListener {
                if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
                    activity?.launchCreateNewContactIntent()
                } else {
                    requestReadContactsPermission()
                }
            }
        }

        binding.contactsFilterGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) {
                return@addOnButtonCheckedListener
            }

            activeContactsFilter = if (checkedId == R.id.contacts_filter_favourite) {
                ContactsFilter.FAVOURITE
            } else {
                ContactsFilter.ALL
            }
            updateDisplayedContacts()
        }
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        binding.apply {
            (fragmentList?.adapter as? MyRecyclerViewAdapter)?.updateTextColor(textColor)
            fragmentPlaceholder.setTextColor(textColor)
            fragmentPlaceholder2.setTextColor(properPrimaryColor)

            letterFastscroller.textColor = textColor.getColorStateList()
            letterFastscroller.pressedTextColor = properPrimaryColor
            letterFastscrollerThumb.setupWithFastScroller(letterFastscroller)
            letterFastscrollerThumb.textColor = properPrimaryColor.getContrastColor()
            letterFastscrollerThumb.thumbColor = properPrimaryColor.getColorStateList()
        }
    }

    override fun refreshItems(callback: (() -> Unit)?) {
        val privateCursor = context?.getMyContactsCursor(false, true)
        ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
            if (SMT_PRIVATE !in context.baseConfig.ignoredContactSources) {
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)
                if (privateContacts.isNotEmpty()) {
                    contacts.addAll(privateContacts)
                    contacts.sort()
                }
            }

            (activity as MainActivity).cacheContacts(contacts)

            activity?.runOnUiThread {
                gotContacts(contacts)
                callback?.invoke()
            }
        }
    }

    private fun gotContacts(contacts: ArrayList<Contact>) {
        allContacts = contacts
        updateDisplayedContacts()
    }

    private fun initContactsAdapter(initialContacts: List<Contact>) {
        val adapter = ContactsAdapter(
            activity = activity as SimpleActivity,
            contacts = initialContacts.toMutableList(),
            recyclerView = binding.fragmentList,
            refreshItemsListener = this
        ) {
            val contact = it as Contact
            activity?.startContactDetailsIntent(contact)
        }

        binding.fragmentList.adapter = adapter

        if (context.areSystemAnimationsEnabled) {
            binding.fragmentList.scheduleLayoutAnimation()
        }
    }

    private fun updateDisplayedContacts() {
        val displayedContacts = getDisplayedContacts()
        val hasResults = displayedContacts.isNotEmpty()
        val hasContacts = allContacts.isNotEmpty()
        val hasPermission = context.hasPermission(PERMISSION_READ_CONTACTS)

        if (binding.fragmentList.adapter == null && hasContacts) {
            initContactsAdapter(displayedContacts)
        }

        (binding.fragmentList.adapter as? ContactsAdapter)?.updateItems(displayedContacts, currentSearchQuery)
        setupLetterFastScroller(displayedContacts)

        binding.fragmentPlaceholder.beVisibleIf(!hasResults)
        val shouldShowAction = !hasPermission || allContacts.isEmpty()
        binding.fragmentPlaceholder2.beVisibleIf(!hasResults && shouldShowAction)
        binding.fragmentList.beVisibleIf(hasResults)
    }

    private fun getDisplayedContacts(): ArrayList<Contact> {
        val filtered = when (activeContactsFilter) {
            ContactsFilter.ALL -> allContacts
            ContactsFilter.FAVOURITE -> allContacts.filter { it.starred == 1 }
        }

        return applyContactsSearch(filtered, currentSearchQuery)
    }

    private fun applyContactsSearch(contacts: List<Contact>, query: String): ArrayList<Contact> {
        if (query.isBlank()) {
            return ArrayList(contacts)
        }

        val shouldNormalize = query.normalizeString() == query
        val normalizedNumberQuery = query.normalizePhoneNumber()
        val filtered = contacts.filter {
            getProperText(it.getNameToDisplay(), shouldNormalize).contains(query, true) ||
                getProperText(it.nickname, shouldNormalize).contains(query, true) ||
                it.phoneNumbers.any { number ->
                    normalizedNumberQuery.isNotEmpty() && number.normalizedNumber.contains(normalizedNumberQuery, true)
                } ||
                it.emails.any { email -> email.value.contains(query, true) } ||
                it.addresses.any { address -> getProperText(address.value, shouldNormalize).contains(query, true) } ||
                it.IMs.any { im -> im.value.contains(query, true) } ||
                getProperText(it.notes, shouldNormalize).contains(query, true) ||
                getProperText(it.organization.company, shouldNormalize).contains(query, true) ||
                getProperText(it.organization.jobPosition, shouldNormalize).contains(query, true) ||
                it.websites.any { website -> website.contains(query, true) }
        } as ArrayList<Contact>

        filtered.sortBy {
            val nameToDisplay = it.getNameToDisplay()
            !getProperText(nameToDisplay, shouldNormalize).startsWith(query, true) && !nameToDisplay.contains(query, true)
        }

        return filtered
    }

    private fun setupLetterFastScroller(contacts: List<Contact>) {
        binding.letterFastscroller.setupWithRecyclerView(binding.fragmentList, { position ->
            try {
                val name = contacts[position].getNameToDisplay()
                val character = if (name.isNotEmpty()) name.substring(0, 1) else ""
                FastScrollItemIndicator.Text(character.uppercase(Locale.getDefault()).normalizeString())
            } catch (e: Exception) {
                FastScrollItemIndicator.Text("")
            }
        })
    }

    override fun onSearchClosed() {
        currentSearchQuery = ""
        updateDisplayedContacts()
    }

    override fun onSearchQueryChanged(text: String) {
        currentSearchQuery = text
        updateDisplayedContacts()
    }

    private fun requestReadContactsPermission() {
        activity?.handlePermission(PERMISSION_READ_CONTACTS) {
            if (it) {
                binding.fragmentPlaceholder.text = context.getString(R.string.no_contacts_found)
                binding.fragmentPlaceholder2.text = context.getString(R.string.create_new_contact)
                ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
                    activity?.runOnUiThread {
                        gotContacts(contacts)
                    }
                }
            }
        }
    }

    private enum class ContactsFilter {
        ALL,
        FAVOURITE
    }
}
