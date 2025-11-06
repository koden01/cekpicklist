package com.example.cekpicklist.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.cekpicklist.fragment.BarcodeDashboardFragment
import com.example.cekpicklist.fragment.BarcodeHistoryFragment
import com.example.cekpicklist.fragment.BarcodeInputFragment

class BarcodeViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> BarcodeInputFragment()
            1 -> BarcodeHistoryFragment()
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}
