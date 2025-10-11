package com.simplemobiletools.dialer.fragments

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.util.AttributeSet
import com.simplemobiletools.commons.extensions.getContrastColor
import com.simplemobiletools.dialer.databinding.FragmentMessagesBinding
import com.simplemobiletools.smsmessenger.activities.MainActivity as SmsMainActivity

class MessagesFragment(context: Context, attributeSet: AttributeSet) :
    MyViewPagerFragment<MyViewPagerFragment.MessagesInnerBinding>(context, attributeSet) {

    private lateinit var binding: FragmentMessagesBinding

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentMessagesBinding.bind(this)
        innerBinding = MessagesInnerBinding(binding)
    }

    override fun setupFragment() {
        binding.openMessagesButton.setOnClickListener {
            activity?.let {
                val intent = Intent(it, SmsMainActivity::class.java)
                it.startActivity(intent)
            }
        }
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        binding.messagesPlaceholder.setTextColor(textColor)
        binding.messagesPlaceholderHolder.setBackgroundColor(0)

        binding.openMessagesButton.apply {
            backgroundTintList = ColorStateList.valueOf(primaryColor)
            setTextColor(primaryColor.getContrastColor())
        }
    }

    fun refreshItems(callback: (() -> Unit)?) {
        callback?.invoke()
    }

    override fun onSearchClosed() {
        // No searchable content in the placeholder
    }

    override fun onSearchQueryChanged(text: String) {
        // No searchable content in the placeholder
    }
}
