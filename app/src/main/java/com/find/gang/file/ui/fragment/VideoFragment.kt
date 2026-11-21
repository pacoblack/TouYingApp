package com.find.gang.file.ui.fragment

import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.util.UnstableApi
import com.find.gang.file.databinding.FragmentVideoBinding
import com.find.gang.file.ui.BaseTvFragment
import com.find.gang.file.viewmodel.Media3PlayerViewModel
import com.find.gang.file.viewmodel.VideoAction

/**
 * 手机端流媒体播放与离线缓存页面（支持全屏切换与进度条拖动）
 */
@OptIn(UnstableApi::class)
class VideoFragment : BaseTvFragment<FragmentVideoBinding>() {

    private val playerViewModel: Media3PlayerViewModel by viewModels()
    private var isFullscreen = false

    // 拦截系统返回键：全屏状态下按返回键优先退出全屏
    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (isFullscreen) {
                toggleFullscreen(false)
            }
        }
    }

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentVideoBinding {
        return FragmentVideoBinding.inflate(inflater, container, false)
    }

    override fun getDefaultFocusViewId(): Int = 0

    override fun initViews() {
        binding.viewModel = playerViewModel
        binding.lifecycleOwner = viewLifecycleOwner
        binding.playerView.player = playerViewModel.player

        // 注册返回键拦截器
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)

        // 使用 HTTPS 测试流地址
        binding.etStreamUrl.setText("http://vjs.zencdn.net/v/oceans.mp4")

        // 启用 PlayerView 内置全屏按钮并绑定点击事件
        binding.playerView.setFullscreenButtonClickListener { enterFullscreen ->
            toggleFullscreen(enterFullscreen)
        }

        // 确保进度条与快进快退控件可见
        binding.playerView.setShowFastForwardButton(true)
        binding.playerView.setShowRewindButton(true)

        viewLifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> binding.playerView.onResume()
                Lifecycle.Event.ON_STOP -> {
                    binding.playerView.onPause()
                    playerViewModel.dispatch(VideoAction.Pause)
                }
                else -> Unit
            }
        })
    }

    /** 切换全屏 / 竖屏状态 */
    private fun toggleFullscreen(enter: Boolean) {
        if (isFullscreen == enter) return
        isFullscreen = enter
        backPressedCallback.isEnabled = enter

        val activity = requireActivity()
        val windowInsetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        val params = binding.playerView.layoutParams as ConstraintLayout.LayoutParams

        if (enter) {
            // 1. 切换横屏并隐藏系统状态栏/导航栏
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

            // 2. 隐藏下方控制面板，播放器铺满全屏
            binding.scrollControls.visibility = View.GONE
            params.dimensionRatio = null
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        } else {
            // 1. 恢复竖屏并显示系统状态栏/导航栏
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())

            // 2. 恢复下方控制面板，播放器恢复 16:9 比例
            binding.scrollControls.visibility = View.VISIBLE
            params.dimensionRatio = "16:9"
            params.height = 0
            params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        }
        binding.playerView.layoutParams = params
    }

    override fun initListeners() {
        binding.btnPlay.setOnClickListener {
            val url = binding.etStreamUrl.text?.toString().orEmpty().trim()
            if (url.isBlank()) {
                showToast("请输入有效的视频地址")
                return@setOnClickListener
            }
            playerViewModel.dispatch(VideoAction.Play(url))
        }

        binding.btnPrecache.setOnClickListener {
            val url = binding.etStreamUrl.text?.toString().orEmpty().trim()
            if (url.isBlank()) {
                showToast("请输入需要预缓存的地址")
                return@setOnClickListener
            }
            playerViewModel.dispatch(VideoAction.PreCache(url))
        }

        binding.btnPause.setOnClickListener {
            playerViewModel.dispatch(VideoAction.Pause)
        }

        binding.btnStopCache.setOnClickListener {
            playerViewModel.dispatch(VideoAction.StopCache)
        }
    }

    override fun observeData() = Unit

    override fun onDestroyView() {
        // 退出页面时强制恢复竖屏和系统状态栏
        if (isFullscreen) {
            requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.getInsetsController(requireActivity().window, requireActivity().window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
        binding.playerView.player = null
        super.onDestroyView()
    }
}
