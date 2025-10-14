package com.simplemobiletools.dialer.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.commons.extensions.getContrastColor
import com.simplemobiletools.commons.models.BlockedNumber
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.databinding.ItemBlockedNumberBinding

class BlockedNumbersAdapter(
    private val onUnblock: (BlockedNumber) -> Unit
) : RecyclerView.Adapter<BlockedNumbersAdapter.ViewHolder>() {

    private val items = ArrayList<BlockedNumber>()
    private var accentColor: Int = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            ItemBlockedNumberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun submitItems(newItems: List<BlockedNumber>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun updateAccentColor(color: Int) {
        accentColor = color
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemBlockedNumberBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: BlockedNumber) {
            val name = item.contactName?.takeIf { it.isNotEmpty() }
            binding.blockedNumberPrimary.text = name ?: item.number
            // binding.blockedNumberSecondary.apply {
            //     text = item.number
            //     visibility = if (name == null) View.GONE else View.VISIBLE
            // }
            val color = if (accentColor != 0) accentColor else ContextCompat.getColor(
                binding.root.context,
                R.color.color_primary
            )
            binding.unblockButton.backgroundTintList = ColorStateList.valueOf(color)
            binding.unblockButton.setTextColor(color.getContrastColor())
            binding.unblockButton.setOnClickListener {
                onUnblock(item)
            }
        }
    }
}
