package com.find.gang.file.utils

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 统一管理横竖屏切换与沉浸式系统栏显隐
 */
class ImmersiveFullscreenHelper(
    private val activity: Activity
) {
    var isFullscreen: Boolean = false
        private set

    fun toggle(enter: Boolean) {
        if (isFullscreen == enter) return
        isFullscreen = enter

        val windowInsetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (enter) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    /** 在视图销毁时确保恢复默认屏幕方向与系统栏显示 */
    fun release() {
        if (isFullscreen) {
            toggle(false)
        }
    }
}
