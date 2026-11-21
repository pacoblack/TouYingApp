package com.find.gang.file.ui.fragment

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.find.gang.file.databinding.FragmentVideoBinding
import com.find.gang.file.media.TvChannel
import com.find.gang.file.ui.BaseTvFragment
import com.find.gang.file.viewmodel.Media3PlayerViewModel
import com.find.gang.file.viewmodel.VideoAction
import kotlin.math.abs

/**
 * 沉浸式流媒体播放页面（支持顶部悬浮返回标题栏、底部手势抽屉控制台、多线路切换与全屏切换）
 */
@OptIn(UnstableApi::class)
class VideoFragment : BaseTvFragment<FragmentVideoBinding>() {

    companion object {
        private const val ARG_CHANNEL_NAME = "arg_channel_name"
        private const val ARG_STREAM_URL = "arg_stream_url"
        private const val ARG_STREAM_URLS = "arg_stream_urls"
        private const val ARG_AUTO_PLAY = "arg_auto_play"
        private const val DEFAULT_TEST_URL = "http://183.237.95.108:9901/tsfile/live/1008_1.m3u8?key=txiptv&playlive=0&authid=0"

        @JvmStatic
        fun newInstance(channel: TvChannel, autoPlay: Boolean = true): VideoFragment {
            return VideoFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CHANNEL_NAME, channel.name)
                    putString(ARG_STREAM_URL, channel.primaryUrl)
                    putStringArrayList(ARG_STREAM_URLS, ArrayList(channel.urls))
                    putBoolean(ARG_AUTO_PLAY, autoPlay)
                }
            }
        }

        @JvmStatic
        fun newInstance(url: String, autoPlay: Boolean = true, title: String = ""): VideoFragment {
            return VideoFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CHANNEL_NAME, title)
                    putString(ARG_STREAM_URL, url)
                    putStringArrayList(ARG_STREAM_URLS, arrayListOf(url))
                    putBoolean(ARG_AUTO_PLAY, autoPlay)
                }
            }
        }
    }

    private val playerViewModel: Media3PlayerViewModel by viewModels()
    private var isFullscreen = false
    private var isDrawerOpen = false
    private var hasHandledInitialUrl = false

    private var channelName: String = ""
    private var candidateUrls: List<String> = emptyList()
    private var currentSourceIndex = 0

    // 统一处理返回逻辑：优先收起抽屉 -> 退出横屏全屏 -> 返回上一页（频道列表）
    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            when {
                isDrawerOpen -> setDrawerExpanded(false)
                isFullscreen -> toggleFullscreen(false)
                else -> {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasHandledInitialUrl = savedInstanceState?.getBoolean("has_handled_initial_url", false) ?: false
        currentSourceIndex = savedInstanceState?.getInt("current_source_index", 0) ?: 0
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("has_handled_initial_url", hasHandledInitialUrl)
        outState.putInt("current_source_index", currentSourceIndex)
    }

    override fun initViews() {
        binding.viewModel = playerViewModel
        binding.lifecycleOwner = viewLifecycleOwner
        binding.playerView.player = playerViewModel.player

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)

        // 适配状态栏 / 刘海屏安全区域，避免悬浮返回按钮被遮挡
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutTopBar) { v, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val basePadding = (10 * resources.displayMetrics.density).toInt()
            v.setPadding(v.paddingLeft, topInset + basePadding, v.paddingRight, v.paddingBottom)
            insets
        }

        // 启用 PlayerView 内置全屏按钮与快进快退
        binding.playerView.setFullscreenButtonClickListener { enterFullscreen ->
            toggleFullscreen(enterFullscreen)
        }
        binding.playerView.setShowFastForwardButton(true)
        binding.playerView.setShowRewindButton(true)

        // 联动播控条显示状态：顶部悬浮返回标题栏与右下角“控制台”按钮随播放控制器自动显示/隐藏
        binding.playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                if (isAdded && view != null) {
                    val show = (visibility == View.VISIBLE)
                    animateOverlayVisibility(binding.layoutTopBar, show)
                    if (!isDrawerOpen) {
                        animateOverlayVisibility(binding.btnOpenDrawer, show)
                    }
                }
            }
        )

        // 初始化抽屉位置（默认隐藏在屏幕底部下方）
        binding.drawerContainer.post {
            binding.drawerContainer.translationY = binding.drawerContainer.height.toFloat()
            binding.drawerContainer.visibility = View.INVISIBLE
        }

        setupSwipeGestures()
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

    private fun animateOverlayVisibility(target: View, show: Boolean) {
        target.animate().cancel()
        if (show) {
            if (target.visibility != View.VISIBLE) {
                target.alpha = 0f
                target.visibility = View.VISIBLE
            }
            target.animate()
                .alpha(1f)
                .setDuration(180L)
                .start()
        } else {
            target.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction { target.visibility = View.GONE }
                .start()
        }
    }

    /** 设置上滑呼出抽屉、下滑收起抽屉的手势监听 */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeGestures() {
        val swipeThreshold = 80
        val velocityThreshold = 100

        // 1. 播放器区域手势：从下往上滑呼出控制台抽屉
        val playerGestureDetector = GestureDetector(
            requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    val startY = e1?.y ?: return false
                    val diffY = e2.y - startY
                    val diffX = e2.x - (e1.x)
                    if (abs(diffY) > abs(diffX) && diffY < -swipeThreshold && abs(velocityY) > velocityThreshold) {
                        if (!isDrawerOpen) {
                            setDrawerExpanded(true)
                            return true
                        }
                    }
                    return false
                }
            }
        )

        binding.playerView.setOnTouchListener { _, event ->
            playerGestureDetector.onTouchEvent(event)
            false // 返回 false 保留 PlayerView 原有的点击唤出播控条功能
        }

        // 2. 抽屉顶部拖拽把手区域手势：从上往下滑收起抽屉
        val drawerGestureDetector = GestureDetector(
            requireContext(),
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
                        setDrawerExpanded(false)
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

    /** 展开或收起底部抽屉控制台 */
    private fun setDrawerExpanded(expand: Boolean) {
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

    private fun handleInitialArguments() {
        channelName = arguments?.getString(ARG_CHANNEL_NAME).orEmpty().trim()
        val passedUrl = arguments?.getString(ARG_STREAM_URL)?.trim()
        val passedList = arguments?.getStringArrayList(ARG_STREAM_URLS)?.filter { it.isNotBlank() }.orEmpty()
        val autoPlay = arguments?.getBoolean(ARG_AUTO_PLAY, true) ?: true

        candidateUrls = when {
            passedList.isNotEmpty() -> passedList
            !passedUrl.isNullOrBlank() -> listOf(passedUrl)
            else -> listOf(DEFAULT_TEST_URL)
        }

        val targetUrl = candidateUrls.getOrElse(currentSourceIndex) { candidateUrls.first() }
        binding.etStreamUrl.setText(targetUrl)
        updateChannelHeaderUI()

        if (autoPlay && !hasHandledInitialUrl) {
            hasHandledInitialUrl = true
            playerViewModel.dispatch(VideoAction.Play(targetUrl))
        }
    }

    private fun updateChannelHeaderUI() {
        val displayTitle = channelName.ifBlank { "流媒体播放" }
        val hasMultiSource = candidateUrls.size > 1
        val sourceText = if (hasMultiSource) "线路 ${currentSourceIndex + 1}/${candidateUrls.size}" else ""

        // 顶部悬浮标题栏更新
        binding.tvTopChannelTitle.text = displayTitle
        if (hasMultiSource) {
            binding.tvTopSourceBadge.visibility = View.VISIBLE
            binding.tvTopSourceBadge.text = sourceText
        } else {
            binding.tvTopSourceBadge.visibility = View.GONE
        }

        // 抽屉控制台标题与换源按钮更新
        binding.tvPanelTitle.text = if (hasMultiSource) "$displayTitle ($sourceText)" else displayTitle
        binding.btnSwitchSource.visibility = if (hasMultiSource) View.VISIBLE else View.GONE
    }

    fun switchNextSource() {
        if (candidateUrls.size <= 1) {
            showToast("当前频道暂无其他备用线路")
            return
        }
        currentSourceIndex = (currentSourceIndex + 1) % candidateUrls.size
        val nextUrl = candidateUrls[currentSourceIndex]
        binding.etStreamUrl.setText(nextUrl)
        updateChannelHeaderUI()
        showToast("已切换至线路 ${currentSourceIndex + 1}/${candidateUrls.size}")
        playerViewModel.dispatch(VideoAction.Play(nextUrl))
    }

    fun playChannel(channel: TvChannel, autoPlay: Boolean = true) {
        if (channel.urls.isEmpty()) {
            showToast("该频道暂无可用播放地址")
            return
        }
        channelName = channel.name
        candidateUrls = channel.urls
        currentSourceIndex = 0

        arguments = (arguments ?: Bundle()).apply {
            putString(ARG_CHANNEL_NAME, channel.name)
            putString(ARG_STREAM_URL, channel.primaryUrl)
            putStringArrayList(ARG_STREAM_URLS, ArrayList(channel.urls))
            putBoolean(ARG_AUTO_PLAY, autoPlay)
        }

        if (isAdded && view != null) {
            binding.etStreamUrl.setText(channel.primaryUrl)
            updateChannelHeaderUI()
            if (autoPlay) {
                playerViewModel.dispatch(VideoAction.Play(channel.primaryUrl))
            }
        }
    }

    /** 切换横屏 / 竖屏沉浸状态 */
    private fun toggleFullscreen(enter: Boolean) {
        if (isFullscreen == enter) return
        isFullscreen = enter

        val activity = requireActivity()
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

    override fun initListeners() {
        // 顶部悬浮返回按钮
        binding.btnTopBack.setOnClickListener {
            backPressedCallback.handleOnBackPressed()
        }

        // 点击半透明遮罩或顶部把手关闭抽屉
        binding.viewDrawerScrim.setOnClickListener {
            setDrawerExpanded(false)
        }
        binding.layoutDrawerHandle.setOnClickListener {
            setDrawerExpanded(false)
        }

        // 右下角悬浮按钮快速呼出抽屉
        binding.btnOpenDrawer.setOnClickListener {
            setDrawerExpanded(true)
        }

        // 切换备用线路
        binding.btnSwitchSource.setOnClickListener {
            switchNextSource()
        }

        binding.btnPlay.setOnClickListener {
            val url = binding.etStreamUrl.text?.toString().orEmpty().trim()
            if (url.isBlank()) {
                showToast("请输入有效的视频地址")
                return@setOnClickListener
            }
            playerViewModel.dispatch(VideoAction.Play(url))
            setDrawerExpanded(false)
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
            requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.getInsetsController(requireActivity().window, requireActivity().window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
        binding.playerView.setControllerVisibilityListener(null as PlayerView.ControllerVisibilityListener?)
        binding.playerView.player = null
        super.onDestroyView()
    }
}
