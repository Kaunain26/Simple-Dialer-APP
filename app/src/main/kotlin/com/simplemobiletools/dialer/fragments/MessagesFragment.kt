package com.simplemobiletools.dialer.fragments

import android.content.Context
import android.util.AttributeSet
import com.simplemobiletools.dialer.databinding.FragmentMessagesBinding

class MessagesFragment(context: Context, attributeSet: AttributeSet) :
    MyViewPagerFragment<MyViewPagerFragment.MessagesInnerBinding>(context, attributeSet) {

    private lateinit var binding: FragmentMessagesBinding

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentMessagesBinding.bind(this)
        innerBinding = MessagesInnerBinding(binding)
    }

    override fun setupFragment() {
        // Placeholder UI for future messaging integration
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        binding.messagesPlaceholder.setTextColor(textColor)
    }

     fun refreshItems(callback: (() -> Unit)?) {
        // No-op for placeholder
        callback?.invoke()
    }

    override fun onSearchClosed() {
        // No-op for placeholder
    }

    override fun onSearchQueryChanged(text: String) {
        // No-op for placeholder
    }
}
