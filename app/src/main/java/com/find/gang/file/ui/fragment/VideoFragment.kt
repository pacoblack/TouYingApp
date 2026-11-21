package com.find.gang.file.ui.fragment

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
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
 * 手机端流媒体播放与离线缓存页面（支持外部传参播放、全屏切换与进度条拖动）
 */
@OptIn(UnstableApi::class)
class VideoFragment : BaseTvFragment<FragmentVideoBinding>() {

    companion object {
        private const val ARG_STREAM_URL = "arg_stream_url"
        private const val ARG_AUTO_PLAY = "arg_auto_play"
        private const val DEFAULT_TEST_URL = "http://ali-m-l.cztv.com/channels/lantian/channel21/1080p.m3u8"

        /**
         * 外部调用标准入口：创建带有初始播放地址的 VideoFragment
         *
         * @param url 视频/音频流地址 (mp4 / m3u8 / rtsp / rtmp 等)
         * @param autoPlay 打开页面后是否立即自动播放，默认 true
         */
        @JvmStatic
        fun newInstance(url: String, autoPlay: Boolean = true): VideoFragment {
            return VideoFragment().apply {
                arguments = bundleOf(
                    ARG_STREAM_URL to url,
                    ARG_AUTO_PLAY to autoPlay
                )
            }
        }
    }

    private val playerViewModel: Media3PlayerViewModel by viewModels()
    private var isFullscreen = false
    private var hasHandledInitialUrl = false

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

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)

        // 启用 PlayerView 内置全屏按钮并绑定点击事件
        binding.playerView.setFullscreenButtonClickListener { enterFullscreen ->
            toggleFullscreen(enterFullscreen)
        }
        binding.playerView.setShowFastForwardButton(true)
        binding.playerView.setShowRewindButton(true)

        // 处理外部传入的 URL 参数，未传入时使用默认测试地址
        handleInitialArguments()

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

    /** 解析外部传入的参数并触发初始行为 */
    private fun handleInitialArguments() {
        val passedUrl = arguments?.getString(ARG_STREAM_URL)?.trim()
        val autoPlay = arguments?.getBoolean(ARG_AUTO_PLAY, true) ?: true

        if (!passedUrl.isNullOrBlank()) {
            binding.etStreamUrl.setText(passedUrl)
            // 防止配置变更重建时重复触发
            if (autoPlay && !hasHandledInitialUrl) {
                hasHandledInitialUrl = true
                playerViewModel.dispatch(VideoAction.Play(passedUrl))
            }
        } else {
            binding.etStreamUrl.setText(DEFAULT_TEST_URL)
        }
    }

    /**
     * 供外部宿主在 Fragment 已显示状态下动态切换播放地址
     */
    fun playExternalUrl(url: String, autoPlay: Boolean = true) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) {
            showToast("传入的视频地址无效")
            return
        }
        // 更新 arguments 保持状态一致
        arguments = (arguments ?: Bundle()).apply {
            putString(ARG_STREAM_URL, cleanUrl)
            putBoolean(ARG_AUTO_PLAY, autoPlay)
        }
        if (isAdded && view != null) {
            binding.etStreamUrl.setText(cleanUrl)
            if (autoPlay) {
                playerViewModel.dispatch(VideoAction.Play(cleanUrl))
            }
        }
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
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

            binding.scrollControls.visibility = View.GONE
            params.dimensionRatio = null
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())

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
        if (isFullscreen) {
            requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            WindowCompat.getInsetsController(requireActivity().window, requireActivity().window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
        binding.playerView.player = null
        super.onDestroyView()
    }
}
