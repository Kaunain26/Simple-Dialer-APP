package app.trusted.callerid.sms.fragments

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.simplemobiletools.commons.activities.ManageBlockedNumbersActivity
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.deleteBlockedNumber
import com.simplemobiletools.commons.extensions.getBlockedNumbers
import com.simplemobiletools.commons.extensions.getContrastColor
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.models.BlockedNumber
import app.trusted.callerid.sms.R
import app.trusted.callerid.sms.adapters.BlockedNumbersAdapter
import app.trusted.callerid.sms.databinding.FragmentBlockedTabBinding

class BlockedTabFragment(context: Context, attrs: AttributeSet) :
    MyViewPagerFragment<MyViewPagerFragment.BlockedInnerBinding>(context, attrs) {

    private lateinit var binding: FragmentBlockedTabBinding
    private val adapter = BlockedNumbersAdapter { unblockNumber(it) }
    private var allNumbers = listOf<BlockedNumber>()
    private var currentQuery = ""

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentBlockedTabBinding.bind(this)
        innerBinding = MyViewPagerFragment.BlockedInnerBinding(binding)
    }

    override fun setupFragment() {
        binding.blockedNumbersList.adapter = adapter
        binding.manageBlockedNumbersButton.setOnClickListener { openManageBlockedNumbers() }
        binding.blockedToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) {
                return@addOnButtonCheckedListener
            }

            if (checkedId == R.id.blocked_settings_button) {
                binding.blockedToggleGroup.check(R.id.blocked_calls_button)
                openManageBlockedNumbers()
            }

            updateToggleAppearance()
        }

        updateToggleAppearance()
        refreshItems()
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        binding.blockedNumbersPlaceholder.setTextColor(textColor)
        binding.manageBlockedNumbersButton.apply {
            backgroundTintList = ColorStateList.valueOf(primaryColor)
            setTextColor(primaryColor.getContrastColor())
        }
        adapter.updateAccentColor(primaryColor)
        updateToggleAppearance()
    }

    fun refreshItems() {
        val act = activity ?: return
        allNumbers = act.getBlockedNumbers()
        applySearch()
    }

    override fun onSearchClosed() {
        if (currentQuery.isNotBlank()) {
            currentQuery = ""
            applySearch()
        }
    }

    override fun onSearchQueryChanged(text: String) {
        currentQuery = text
        applySearch()
    }

    private fun applySearch() {
        val filtered = if (currentQuery.isBlank()) {
            allNumbers
        } else {
            allNumbers.filter {
                it.number.contains(currentQuery, true) || (it.contactName?.contains(currentQuery, true) == true)
            }
        }
        adapter.submitItems(filtered)
        togglePlaceholder(filtered.isEmpty())
    }

    private fun togglePlaceholder(isEmpty: Boolean) {
        binding.blockedNumbersList.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.blockedNumbersPlaceholderHolder.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    private fun unblockNumber(blockedNumber: BlockedNumber) {
        askConfirmToUnblock(blockedNumber)
    }

    private fun openManageBlockedNumbers() {
        activity?.let {
            Intent(it, ManageBlockedNumbersActivity::class.java).apply {
                it.startActivity(this)
            }
        }
    }

    private fun updateToggleAppearance() {
        val primaryColor = activity?.getProperPrimaryColor() ?: ContextCompat.getColor(context, R.color.color_primary)
        val neutralColor = ContextCompat.getColor(context, R.color.bottom_nav_unselected)
        val strokeWidth = resources.getDimensionPixelSize(R.dimen.toggle_stroke_width)

        binding.blockedCallsButton.apply {
            this.strokeWidth = strokeWidth
            strokeColor = ColorStateList.valueOf(primaryColor)
            backgroundTintList = ColorStateList.valueOf(primaryColor)
            setTextColor(primaryColor.getContrastColor())
        }

        binding.blockedSettingsButton.apply {
            this.strokeWidth = strokeWidth
            strokeColor = ColorStateList.valueOf(neutralColor)
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            setTextColor(neutralColor)
        }
    }

    private fun askConfirmToUnblock(blockedNumber: BlockedNumber) {
        activity?.let { activity ->
            ConfirmationDialog(activity, activity.getString(R.string.unblock_number)) {
                val act = activity
                if (act.deleteBlockedNumber(blockedNumber.number)) {
                    act.toast(R.string.removed)
                    refreshItems()
                } else {
                    act.toast(R.string.unknown_error_occurred)
                }
            }
        }

    }
}
