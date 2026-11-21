package com.find.gang.file.media

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.find.gang.file.databinding.FragmentVideoBinding
import kotlin.math.abs

/**
 * 封装底部控制台抽屉的展开/收起动画与上下滑动手势识别
 */
class PlayerDrawerHelper(
    private val binding: FragmentVideoBinding
) {
    var isDrawerOpen: Boolean = false
        private set

    fun init() {
        // 初始化抽屉位置（默认隐藏在屏幕底部下方）
        binding.drawerContainer.post {
            binding.drawerContainer.translationY = binding.drawerContainer.height.toFloat()
            binding.drawerContainer.visibility = View.INVISIBLE
        }
        setupSwipeGestures()
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.viewDrawerScrim.setOnClickListener { setExpanded(false) }
        binding.layoutDrawerHandle.setOnClickListener { setExpanded(false) }
        binding.btnOpenDrawer.setOnClickListener { setExpanded(true) }
    }

    /** 展开或收起底部抽屉控制台 */
    fun setExpanded(expand: Boolean) {
        if (isDrawerOpen == expand) return
        isDrawerOpen = expand

        val drawer = binding.drawerContainer
        val scrim = binding.viewDrawerScrim

        if (expand) {
            drawer.visibility = View.VISIBLE
            scrim.visibility = View.VISIBLE
            binding.btnOpenDrawer.visibility = View.GONE

            scrim.animate()
                .alpha(1f)
                .setDuration(250L)
                .start()

            drawer.animate()
                .translationY(0f)
                .setDuration(280L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            scrim.animate()
                .alpha(0f)
                .setDuration(220L)
                .withEndAction { scrim.visibility = View.GONE }
                .start()

            drawer.animate()
                .translationY(drawer.height.toFloat())
                .setDuration(250L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    drawer.visibility = View.INVISIBLE
                    if (binding.playerView.isControllerFullyVisible) {
                        binding.btnOpenDrawer.alpha = 1f
                        binding.btnOpenDrawer.visibility = View.VISIBLE
                    }
                }
                .start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeGestures() {
        val context = binding.root.context
        val swipeThreshold = 80
        val velocityThreshold = 100

        // 1. 播放器区域手势：从下往上滑呼出控制台抽屉
        val playerGestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    val startY = e1?.y ?: return false
                    val diffY = e2.y - startY
                    val diffX = e2.x - e1.x
                    if (abs(diffY) > abs(diffX) && diffY < -swipeThreshold && abs(velocityY) > velocityThreshold) {
                        if (!isDrawerOpen) {
                            setExpanded(true)
                            return true
                        }
                    }
                    return false
                }
            }
        )

        binding.playerView.setOnTouchListener { _, event ->
            playerGestureDetector.onTouchEvent(event)
            false // 保留 PlayerView 原有的点击唤出播控条功能
        }

        // 2. 抽屉顶部拖拽把手区域手势：从上往下滑收起抽屉
        val drawerGestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    val startY = e1?.y ?: return false
                    val diffY = e2.y - startY
                    if (diffY > swipeThreshold && abs(velocityY) > velocityThreshold) {
                        setExpanded(false)
                        return true
                    }
                    return false
                }
            }
        )

        binding.layoutDrawerHandle.setOnTouchListener { _, event ->
            drawerGestureDetector.onTouchEvent(event)
            true
        }
    }
}
