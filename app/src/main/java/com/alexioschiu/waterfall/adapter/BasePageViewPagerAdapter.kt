package com.alexioschiu.waterfall.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.alexioschiu.waterfall.fragment.HomeFragment
import com.alexioschiu.waterfall.fragment.ProfileFragment

class BasePageViewPagerAdapter(fragmentActivity: FragmentActivity) :
    FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment()
            1 -> ProfileFragment()
            else -> throw IllegalArgumentException("Invalid position for view pager: $position")
        }
    }
}