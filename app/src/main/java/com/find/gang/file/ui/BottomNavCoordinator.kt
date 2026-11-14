package com.find.gang.file.ui

import android.view.KeyEvent
import android.view.View
import androidx.annotation.IdRes
import androidx.fragment.app.FragmentManager

class BottomNavCoordinator(
    private val fragmentManager: FragmentManager,
    @IdRes private val containerId: Int,
    private val tabViews: Map<NavTab, View>
) {
    var currentTab: NavTab? = null
        private set

    init {
        tabViews.forEach { (tab, view) ->
            view.setOnClickListener { switchTo(tab) }
            // 在底部按钮按遥控器“上键”时，委托给当前 BaseTvFragment 聚焦默认控件
            view.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    val currentFragment = currentTab?.let {
                        fragmentManager.findFragmentByTag(it.tag) as? BaseTvFragment<*>
                    }
                    currentFragment?.requestDefaultFocus() ?: false
                } else {
                    false
                }
            }
        }
    }

    fun switchTo(targetTab: NavTab) {
        if (currentTab == targetTab) return

        val transaction = fragmentManager.beginTransaction()

        currentTab?.let { oldTab ->
            fragmentManager.findFragmentByTag(oldTab.tag)?.let { transaction.hide(it) }
        }

        val existingFragment = fragmentManager.findFragmentByTag(targetTab.tag) as? BaseTvFragment<*>
        if (existingFragment == null) {
            transaction.add(containerId, targetTab.createFragment(), targetTab.tag)
        } else {
            transaction.show(existingFragment)
        }

        transaction.commitNowAllowingStateLoss()

        currentTab = targetTab
        tabViews.forEach { (tab, view) ->
            view.isSelected = (tab == targetTab)
        }
    }
}
