package com.example.waterfall.activity

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.waterfall.R
import com.example.waterfall.adapter.BasePageViewPagerAdapter

private val BaseActivity.viewPager: ViewPager2
    get() = findViewById(R.id.base_bottom_viewpager)

class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.base_page_layout)
        setupViewPager()
        setupBottomNavigation()
    }


    private fun setupViewPager() {
        val adapter = BasePageViewPagerAdapter(this)
        viewPager.adapter = adapter
    }

    private fun setupBottomNavigation() {
        val navHome: View = findViewById(R.id.base_bottom_nav_home)
        val navProfile: View = findViewById(R.id.base_bottom_nav_profile)

        navHome.setOnClickListener {
            if (viewPager.currentItem == 0) {
                // 滑动到首页顶部
                // 尝试找到已附加到 FragmentManager 的 HomeFragment，并滚动到顶部
                val homeFragment = supportFragmentManager
                    .fragments
                    .firstOrNull { it::class.java.simpleName == "HomeFragment" } as? androidx.fragment.app.Fragment
                homeFragment?.view?.let { v ->
                    val recycler =
                        v.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView)
                    if (recycler != null) {
                        recycler.smoothScrollToPosition(0)
                    } else {
                        v.scrollTo(0, 0)
                    }
                }
            } else {
                viewPager.setCurrentItem(0, true)
            }
        }
        navProfile.setOnClickListener {
            viewPager.setCurrentItem(1, true)
        }

        // 监听页面变化
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateNavigationState(position)
            }
        })

        // 初始化状态
        updateNavigationState(0)
    }

    private fun updateNavigationState(selectedPosition: Int) {
        val navHome: View = findViewById(R.id.base_bottom_nav_home)
        val navProfile: View = findViewById(R.id.base_bottom_nav_profile)

        // 重置所有状态
        navHome.alpha = 0.65f
        navProfile.alpha = 0.65f

        // 设置选中状态
        when (selectedPosition) {
            0 -> navHome.alpha = 1.0f
            1 -> navProfile.alpha = 1.0f
        }
    }

}