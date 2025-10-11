package com.simplemobiletools.dialer.adapters

import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import com.simplemobiletools.commons.helpers.TAB_CALL_HISTORY
import com.simplemobiletools.commons.helpers.TAB_CONTACTS
import com.simplemobiletools.commons.helpers.TAB_MESSAGES
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.fragments.MyViewPagerFragment
import com.simplemobiletools.dialer.helpers.tabsList

class ViewPagerAdapter(val activity: SimpleActivity) : PagerAdapter() {

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val layout = getFragmentLayout(position)
        val view = activity.layoutInflater.inflate(layout, container, false)
        container.addView(view)

        (view as MyViewPagerFragment<*>).apply {
            setupFragment(activity)
        }

        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
        container.removeView(item as View)
    }

    override fun getCount(): Int {
        val showTabs = activity.config.showTabs
        return tabsList.count { showTabs and it != 0 }
    }

    override fun isViewFromObject(view: View, item: Any) = view == item

    private fun getFragmentLayout(position: Int): Int {
        val showTabs = activity.config.showTabs
        val layouts = tabsList.filter { showTabs and it != 0 }.map {
            when (it) {
                TAB_CALL_HISTORY -> R.layout.fragment_recents
                TAB_CONTACTS -> R.layout.fragment_contacts
                TAB_MESSAGES -> R.layout.fragment_messages
                else -> R.layout.fragment_recents
            }
        }

        if (layouts.isEmpty()) {
            return R.layout.fragment_recents
        }

        return layouts.getOrElse(position) { layouts.last() }
    }
}
