package app.trusted.callerid.sms.adapters

import android.content.Intent
import android.graphics.drawable.Drawable
import android.provider.CallLog.Calls
import android.text.SpannableString
import android.text.TextUtils
import android.util.TypedValue
import android.view.*
import android.widget.PopupMenu
import com.bumptech.glide.Glide
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.CallConfirmationDialog
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.dialogs.FeatureLockedDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.contacts.Contact
import com.simplemobiletools.commons.views.MyRecyclerView
import app.trusted.callerid.sms.R
import app.trusted.callerid.sms.activities.MainActivity
import app.trusted.callerid.sms.activities.SimpleActivity
import app.trusted.callerid.sms.databinding.ItemRecentCallBinding
import app.trusted.callerid.sms.dialogs.ShowGroupedCallsDialog
import app.trusted.callerid.sms.extensions.*
import app.trusted.callerid.sms.helpers.RecentsHelper
import app.trusted.callerid.sms.interfaces.RefreshItemsListener
import app.trusted.callerid.sms.models.RecentCall

class RecentCallsAdapter(
    activity: SimpleActivity,
    private var recentCalls: MutableList<RecentCall>,
    recyclerView: MyRecyclerView,
    private val refreshItemsListener: RefreshItemsListener?,
    private val showOverflowMenu: Boolean,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    private lateinit var outgoingCallIcon: Drawable
    private lateinit var incomingCallIcon: Drawable
    private lateinit var incomingMissedCallIcon: Drawable
    var fontSize: Float = activity.getTextSize()
    private val areMultipleSIMsAvailable = activity.areMultipleSIMsAvailable()
    private val redColor = resources.getColor(R.color.md_red_700)
    private val blue_color = resources.getColor(R.color.color_primary)
    private val green_color = resources.getColor(R.color.md_green)
    private var textToHighlight = ""
    private var durationPadding = resources.getDimension(R.dimen.normal_margin).toInt()

    init {
        initDrawables()
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_recent_calls

    override fun prepareActionMode(menu: Menu) {
        val hasMultipleSIMs = activity.areMultipleSIMsAvailable()
        val selectedItems = getSelectedItems()
        val isOneItemSelected = selectedItems.size == 1
        val selectedNumber = "tel:${getSelectedPhoneNumber()}"

        menu.apply {
            findItem(R.id.cab_call_sim_1).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_call_sim_2).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_remove_default_sim).isVisible = isOneItemSelected && (activity.config.getCustomSIM(selectedNumber) ?: "") != ""

            findItem(R.id.cab_block_number).title = activity.addLockedLabelIfNeeded(R.string.block_number)
            findItem(R.id.cab_block_number).isVisible = isNougatPlus()
            findItem(R.id.cab_add_number).isVisible = isOneItemSelected
            findItem(R.id.cab_copy_number).isVisible = isOneItemSelected
            findItem(R.id.cab_show_call_details).isVisible = isOneItemSelected
            findItem(R.id.cab_view_details).isVisible = isOneItemSelected && findContactByCall(selectedItems.first()) != null
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_call_sim_1 -> callContact(true)
            R.id.cab_call_sim_2 -> callContact(false)
            R.id.cab_remove_default_sim -> removeDefaultSIM()
            R.id.cab_block_number -> tryBlocking()
            R.id.cab_add_number -> addNumberToContact()
            R.id.cab_send_sms -> sendSMS()
            R.id.cab_show_call_details -> showCallDetails()
            R.id.cab_copy_number -> copyNumber()
            R.id.cab_remove -> askConfirmRemove()
            R.id.cab_select_all -> selectAll()
            R.id.cab_view_details -> launchContactDetailsIntent(findContactByCall(getSelectedItems().first()))
        }
    }

    override fun getSelectableItemCount() = recentCalls.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = recentCalls.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = recentCalls.indexOfFirst { it.id == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(ItemRecentCallBinding.inflate(layoutInflater, parent, false).root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recentCall = recentCalls[position]
        holder.bindView(
            any = recentCall,
            allowSingleClick = refreshItemsListener != null && !recentCall.isUnknownNumber,
            allowLongClick = refreshItemsListener != null && !recentCall.isUnknownNumber
        ) { itemView, _ ->
            val binding = ItemRecentCallBinding.bind(itemView)
            setupView(binding, recentCall)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = recentCalls.size

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            ItemRecentCallBinding.bind(holder.itemView).apply {
                Glide.with(activity).clear(itemRecentsImage)
            }
        }
    }

    fun initDrawables() {
        outgoingCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_outgoing_call_vector, blue_color)
        incomingCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_incoming_call_vector, green_color)
        incomingMissedCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_missed, redColor)
    }

    private fun callContact(useSimOne: Boolean) {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        activity.callContactWithSim(phoneNumber, useSimOne)
    }

    private fun callContact() {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        (activity as SimpleActivity).startCallIntent(phoneNumber)
    }

    private fun removeDefaultSIM() {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        activity.config.removeCustomSIM("tel:$phoneNumber")
        finishActMode()
    }

    private fun tryBlocking() {
        askConfirmBlock()
    }

    private fun askConfirmBlock() {
        val numbers = TextUtils.join(", ", getSelectedItems().distinctBy { it.phoneNumber }.map { it.phoneNumber })
        val baseString = R.string.block_confirmation
        val question = String.format(resources.getString(baseString), numbers)

        ConfirmationDialog(activity, question) {
            blockNumbers()
        }
    }

    private fun blockNumbers() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val callsToBlock = getSelectedItems()
        val positions = getSelectedItemPositions()
        recentCalls.removeAll(callsToBlock)

        ensureBackgroundThread {
            callsToBlock.map { it.phoneNumber }.forEach { number ->
                activity.addBlockedNumber(number)
            }

            activity.runOnUiThread {
                removeSelectedItems(positions)
                finishActMode()
            }
        }
    }

    private fun addNumberToContact() {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, phoneNumber)
            activity.launchActivityIntent(this)
        }
    }

    private fun sendSMS() {
        val numbers = getSelectedItems().map { it.phoneNumber }
        val recipient = TextUtils.join(";", numbers)
        activity.launchSendSMSIntent(recipient)
    }

    private fun showCallDetails() {
        val recentCall = getSelectedItems().firstOrNull() ?: return
        val callIds = recentCall.neighbourIDs.map { it }.toMutableList() as ArrayList<Int>
        callIds.add(recentCall.id)
        ShowGroupedCallsDialog(activity, callIds)
    }

    private fun copyNumber() {
        val recentCall = getSelectedItems().firstOrNull() ?: return
        activity.copyToClipboard(recentCall.phoneNumber)
        finishActMode()
    }

    private fun askConfirmRemove() {
        ConfirmationDialog(activity, activity.getString(R.string.remove_confirmation)) {
            activity.handlePermission(PERMISSION_WRITE_CALL_LOG) {
                removeRecents()
            }
        }
    }

    private fun removeRecents() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val callsToRemove = getSelectedItems()
        val positions = getSelectedItemPositions()
        val idsToRemove = ArrayList<Int>()
        callsToRemove.forEach {
            idsToRemove.add(it.id)
            it.neighbourIDs.mapTo(idsToRemove, { it })
        }

        RecentsHelper(activity).removeRecentCalls(idsToRemove) {
            recentCalls.removeAll(callsToRemove)
            activity.runOnUiThread {
                refreshItemsListener?.refreshItems()
                if (recentCalls.isEmpty()) {
                    finishActMode()
                } else {
                    removeSelectedItems(positions)
                }
            }
        }
    }

    private fun findContactByCall(recentCall: RecentCall): Contact? {
        return (activity as MainActivity).cachedContacts.find { it.name == recentCall.name && it.doesHavePhoneNumber(recentCall.phoneNumber) }
    }

    private fun launchContactDetailsIntent(contact: Contact?) {
        if (contact != null) {
            activity.startContactDetailsIntent(contact)
        }
    }

    fun updateItems(newItems: List<RecentCall>, highlightText: String = "") {
        if (newItems.hashCode() != recentCalls.hashCode()) {
            recentCalls = newItems.toMutableList()
            textToHighlight = highlightText
            recyclerView.resetItemCount()
            notifyDataSetChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    private fun getSelectedItems() = recentCalls.filter { selectedKeys.contains(it.id) } as ArrayList<RecentCall>

    private fun getSelectedPhoneNumber() = getSelectedItems().firstOrNull()?.phoneNumber

    private fun setupView(binding: ItemRecentCallBinding, call: RecentCall) {
        binding.apply {
            val currentFontSize = fontSize
            itemRecentsHolder.isSelected = selectedKeys.contains(call.id)
            val name = findContactByCall(call)?.getNameToDisplay() ?: call.name
            var nameToShow = SpannableString(name)
            if (call.specificType.isNotEmpty()) {
                nameToShow = SpannableString("${name} - ${call.specificType}")

                // show specific number at "Show call details" dialog too
                if (refreshItemsListener == null) {
                    nameToShow = SpannableString("${name} - ${call.specificType}, ${call.specificNumber}")
                }
            }

            if (call.neighbourIDs.isNotEmpty()) {
                nameToShow = SpannableString("$nameToShow (${call.neighbourIDs.size + 1})")
            }

            if (textToHighlight.isNotEmpty() && nameToShow.contains(textToHighlight, true)) {
                nameToShow = SpannableString(nameToShow.toString().highlightTextPart(textToHighlight, properPrimaryColor))
            }

            itemRecentsName.apply {
                text = nameToShow
                setTextColor(textColor)
               // setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize)
            }

            itemRecentsDateTime.apply {
                text = call.startTS.formatDateOrTime(context, refreshItemsListener != null, false)
                setTextColor(if (call.type == Calls.MISSED_TYPE) redColor else textColor)
                //setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * 0.8f)
            }

            itemRecentsDuration.apply {
                text = call.duration.getFormattedDuration()
                setTextColor(textColor)
                beVisibleIf(call.type != Calls.MISSED_TYPE && call.type != Calls.REJECTED_TYPE && call.duration > 0)
               // setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * 0.8f)
                if (!showOverflowMenu) {
                    itemRecentsDuration.setPadding(0, 0, durationPadding, 0)
                }
            }

            itemRecentsSimImage.beVisibleIf(areMultipleSIMsAvailable && call.simID != -1)
            itemRecentsSimId.beVisibleIf(areMultipleSIMsAvailable && call.simID != -1)
            if (areMultipleSIMsAvailable && call.simID != -1) {
                itemRecentsSimImage.applyColorFilter(textColor)
                itemRecentsSimId.setTextColor(textColor.getContrastColor())
                itemRecentsSimId.text = call.simID.toString()
            }

            SimpleContactsHelper(root.context).loadContactImage(call.photoUri, itemRecentsImage, call.name)

            val drawable = when (call.type) {
                Calls.OUTGOING_TYPE -> {
                    binding.itemCallStatus.text = root.context.getString(R.string.outgoing)
                    outgoingCallIcon
                }
                Calls.MISSED_TYPE -> {
                    binding.itemCallStatus.text = root.context.getString(R.string.missed_call)
                    incomingMissedCallIcon
                }
                else -> {
                    binding.itemCallStatus.text = root.context.getString(R.string.incoming)
                    incomingCallIcon
                }
            }

            itemRecentsType.setImageDrawable(drawable)

            overflowMenuIcon.beVisibleIf(showOverflowMenu)
            if (showOverflowMenu) {
                overflowMenuIcon.setOnClickListener {
                    if (activity.config.showCallConfirmation) {
                        CallConfirmationDialog(activity, call.name) {
                            activity.launchCallIntent(call.phoneNumber)
                        }
                    } else {
                        activity.launchCallIntent(call.phoneNumber)
                    }
                }
                overflowMenuIcon.setOnLongClickListener {
                    showPopupMenu(overflowMenuAnchor, call)
                    true
                }
            } else {
                overflowMenuIcon.setOnClickListener(null)
                overflowMenuIcon.setOnLongClickListener(null)
            }
        }
    }

    private fun showPopupMenu(view: View, call: RecentCall) {
        finishActMode()
        val theme = activity.getPopupMenuTheme()
        val contextTheme = ContextThemeWrapper(activity, theme)
        val contact = findContactByCall(call)
        val selectedNumber = "tel:${call.phoneNumber}"

        PopupMenu(contextTheme, view, Gravity.END).apply {
            inflate(R.menu.menu_recent_item_options)
            menu.apply {
                val areMultipleSIMsAvailable = activity.areMultipleSIMsAvailable()
                findItem(R.id.cab_call).isVisible = !areMultipleSIMsAvailable && !call.isUnknownNumber
                findItem(R.id.cab_call_sim_1).isVisible = areMultipleSIMsAvailable && !call.isUnknownNumber
                findItem(R.id.cab_call_sim_2).isVisible = areMultipleSIMsAvailable && !call.isUnknownNumber
                findItem(R.id.cab_send_sms).isVisible = !call.isUnknownNumber
                findItem(R.id.cab_view_details).isVisible = contact != null && !call.isUnknownNumber
                findItem(R.id.cab_add_number).isVisible = !call.isUnknownNumber
                findItem(R.id.cab_copy_number).isVisible = !call.isUnknownNumber
                findItem(R.id.cab_show_call_details).isVisible = !call.isUnknownNumber
                findItem(R.id.cab_block_number).title = activity.addLockedLabelIfNeeded(R.string.block_number)
                findItem(R.id.cab_block_number).isVisible = isNougatPlus() && !call.isUnknownNumber
                findItem(R.id.cab_remove_default_sim).isVisible = (activity.config.getCustomSIM(selectedNumber) ?: "") != "" && !call.isUnknownNumber
            }

            setOnMenuItemClickListener { item ->
                val callId = call.id
                when (item.itemId) {
                    R.id.cab_call -> {
                        executeItemMenuOperation(callId) {
                            callContact()
                        }
                    }

                    R.id.cab_call_sim_1 -> {
                        executeItemMenuOperation(callId) {
                            callContact(true)
                        }
                    }

                    R.id.cab_call_sim_2 -> {
                        executeItemMenuOperation(callId) {
                            callContact(false)
                        }
                    }

                    R.id.cab_send_sms -> {
                        executeItemMenuOperation(callId) {
                            sendSMS()
                        }
                    }

                    R.id.cab_view_details -> {
                        executeItemMenuOperation(callId) {
                            launchContactDetailsIntent(contact)
                        }
                    }

                    R.id.cab_add_number -> {
                        executeItemMenuOperation(callId) {
                            addNumberToContact()
                        }
                    }

                    R.id.cab_show_call_details -> {
                        executeItemMenuOperation(callId) {
                            showCallDetails()
                        }
                    }

                    R.id.cab_block_number -> {
                        selectedKeys.add(callId)
                        tryBlocking()
                    }

                    R.id.cab_remove -> {
                        selectedKeys.add(callId)
                        askConfirmRemove()
                    }

                    R.id.cab_copy_number -> {
                        executeItemMenuOperation(callId) {
                            copyNumber()
                        }
                    }

                    R.id.cab_remove_default_sim -> {
                        executeItemMenuOperation(callId) {
                            removeDefaultSIM()
                        }
                    }
                }
                true
            }
            show()
        }
    }

    private fun executeItemMenuOperation(callId: Int, callback: () -> Unit) {
        selectedKeys.add(callId)
        callback()
        selectedKeys.remove(callId)
    }
}
