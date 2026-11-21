package com.find.gang.file.ui.fragment

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.find.gang.file.databinding.FragmentVideoBinding
import com.find.gang.file.media.PlayerDrawerHelper
import com.find.gang.file.media.TvChannel
import com.find.gang.file.ui.BaseTvFragment
import com.find.gang.file.utils.ImmersiveFullscreenHelper
import com.find.gang.file.viewmodel.Media3PlayerViewModel
import com.find.gang.file.viewmodel.MediaPlayerUiState
import com.find.gang.file.viewmodel.VideoAction
import com.find.gang.file.viewmodel.VideoEffect
import kotlinx.coroutines.launch

/**
 * 沉浸式流媒体播放页面（Passive View：只负责视图绑定与状态渲染）
 */
@OptIn(UnstableApi::class)
class VideoFragment : BaseTvFragment<FragmentVideoBinding>() {

    companion object {
        private const val ARG_CHANNEL_NAME = "arg_channel_name"
        private const val ARG_STREAM_URL = "arg_stream_url"
        private const val ARG_STREAM_URLS = "arg_stream_urls"
        private const val ARG_AUTO_PLAY = "arg_auto_play"

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

    private var drawerHelper: PlayerDrawerHelper? = null
    private var fullscreenHelper: ImmersiveFullscreenHelper? = null
    private var urlDialog: AlertDialog? = null

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            when {
                drawerHelper?.isDrawerOpen == true -> drawerHelper?.setExpanded(false)
                fullscreenHelper?.isFullscreen == true -> fullscreenHelper?.toggle(false)
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

    override fun onResume() {
        super.onResume()
        if (!isHidden) {
            playerViewModel.dispatch(VideoAction.CheckUrlPromptOnVisible)
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            playerViewModel.dispatch(VideoAction.CheckUrlPromptOnVisible)
        }
    }

    override fun initViews() {
        binding.viewModel = playerViewModel
        binding.lifecycleOwner = viewLifecycleOwner
        binding.playerView.player = playerViewModel.player

        drawerHelper = PlayerDrawerHelper(binding).apply { init() }
        fullscreenHelper = ImmersiveFullscreenHelper(requireActivity())

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)

        // 适配状态栏安全区域
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutTopBar) { v, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val basePadding = (10 * resources.displayMetrics.density).toInt()
            v.setPadding(v.paddingLeft, topInset + basePadding, v.paddingRight, v.paddingBottom)
            insets
        }

        // PlayerView 内置按钮配置
        binding.playerView.setFullscreenButtonClickListener { enterFullscreen ->
            fullscreenHelper?.toggle(enterFullscreen)
        }
        binding.playerView.setShowFastForwardButton(true)
        binding.playerView.setShowRewindButton(true)

        // 联动播控条显隐
        binding.playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                if (isAdded && view != null) {
                    val show = (visibility == View.VISIBLE)
                    animateOverlayVisibility(binding.layoutTopBar, show)
                    if (drawerHelper?.isDrawerOpen != true) {
                        animateOverlayVisibility(binding.btnOpenDrawer, show)
                    }
                }
            }
        )

        dispatchInitialArguments()

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

    private fun dispatchInitialArguments() {
        val channelName = arguments?.getString(ARG_CHANNEL_NAME).orEmpty().trim()
        val passedUrl = arguments?.getString(ARG_STREAM_URL)?.trim()
        val passedList = arguments?.getStringArrayList(ARG_STREAM_URLS)?.filter { it.isNotBlank() }.orEmpty()
        val autoPlay = arguments?.getBoolean(ARG_AUTO_PLAY, true) ?: true

        val urls = when {
            passedList.isNotEmpty() -> passedList
            !passedUrl.isNullOrBlank() -> listOf(passedUrl)
            else -> emptyList()
        }

        playerViewModel.dispatch(VideoAction.InitArguments(channelName, urls, autoPlay))
    }
    override fun initListeners() {
        binding.btnTopBack.setOnClickListener {
            backPressedCallback.handleOnBackPressed()
        }
        binding.btnSwitchSource.setOnClickListener {
            playerViewModel.dispatch(VideoAction.SwitchNextSource)
        }
        binding.btnPlay.setOnClickListener {
            val url = binding.etStreamUrl.text?.toString().orEmpty()
            playerViewModel.dispatch(VideoAction.Play(url))
            if (url.isNotBlank()) {
                drawerHelper?.setExpanded(false)
            }
        }
        binding.btnPrecache.setOnClickListener {
            val url = binding.etStreamUrl.text?.toString().orEmpty()
            playerViewModel.dispatch(VideoAction.PreCache(url))
        }
        binding.btnPause.setOnClickListener {
            playerViewModel.dispatch(VideoAction.Pause)
        }
        binding.btnStopCache.setOnClickListener {
            playerViewModel.dispatch(VideoAction.StopCache)
        }
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    playerViewModel.uiState.collect { state -> renderState(state) }
                }
                launch {
                    playerViewModel.uiEffect.collect { effect -> handleEffect(effect) }
                }
            }
        }
    }

    private fun renderState(state: MediaPlayerUiState) {
        binding.tvTopChannelTitle.text = state.displayTitle
        binding.tvPanelTitle.text = state.panelTitleText

        if (state.hasMultiSource) {
            binding.tvTopSourceBadge.visibility = View.VISIBLE
            binding.tvTopSourceBadge.text = state.sourceBadgeText
            binding.btnSwitchSource.visibility = View.VISIBLE
        } else {
            binding.tvTopSourceBadge.visibility = View.GONE
            binding.btnSwitchSource.visibility = View.GONE
        }

        // 仅当输入框内容与当前状态地址不一致时更新，避免干扰用户输入焦点
        if (state.currentUrl.isNotBlank() && binding.etStreamUrl.text?.toString() != state.currentUrl) {
            binding.etStreamUrl.setText(state.currentUrl)
        }

        // 直接由状态字段驱动输入框显隐，彻底避免一次性事件丢失
        if (state.showUrlDialog) {
            if (urlDialog?.isShowing != true) {
                showStreamUrlInputDialog()
            }
        } else {
            urlDialog?.dismiss()
            urlDialog = null
        }
    }

    private fun handleEffect(effect: VideoEffect) {
        when (effect) {
            is VideoEffect.ShowToast -> showToast(effect.message)
        }
    }

    /** 弹出流媒体播放地址输入框 */
    private fun showStreamUrlInputDialog() {
        val context = requireContext()
        val density = resources.displayMetrics.density
        val horizontalMargin = (20 * density).toInt()
        val topMargin = (12 * density).toInt()

        val editText = EditText(context).apply {
            hint = "请输入直播流或视频地址 (如 m3u8 / mp4)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            maxLines = 3
            isSingleLine = false
            setText(binding.etStreamUrl.text?.toString().orEmpty())
            setSelection(text.length)
        }

        val container = FrameLayout(context).apply {
            setPadding(horizontalMargin, topMargin, horizontalMargin, 0)
            addView(editText)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("输入播放地址")
            .setView(container)
            .setPositiveButton("立即播放", null)
            .setNegativeButton("取消") { d, _ ->
                playerViewModel.dispatch(VideoAction.DismissUrlDialog)
                d.dismiss()
            }
            .setOnCancelListener {
                playerViewModel.dispatch(VideoAction.DismissUrlDialog)
            }
            .create()

        urlDialog = dialog

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val inputUrl = editText.text?.toString().orEmpty().trim()
                if (inputUrl.isBlank()) {
                    showToast("请输入有效的视频地址")
                    return@setOnClickListener
                }
                playerViewModel.dispatch(VideoAction.Play(inputUrl))
                dialog.dismiss()
            }
            editText.requestFocus()
        }

        dialog.show()
    }

    fun switchNextSource() {
        playerViewModel.dispatch(VideoAction.SwitchNextSource)
    }

    fun playChannel(channel: TvChannel, autoPlay: Boolean = true) {
        arguments = (arguments ?: Bundle()).apply {
            putString(ARG_CHANNEL_NAME, channel.name)
            putString(ARG_STREAM_URL, channel.primaryUrl)
            putStringArrayList(ARG_STREAM_URLS, ArrayList(channel.urls))
            putBoolean(ARG_AUTO_PLAY, autoPlay)
        }
        playerViewModel.dispatch(VideoAction.LoadChannel(channel, autoPlay))
    }

    private fun animateOverlayVisibility(target: View, show: Boolean) {
        target.animate().cancel()
        if (show) {
            if (target.visibility != View.VISIBLE) {
                target.alpha = 0f
                target.visibility = View.VISIBLE
            }
            target.animate().alpha(1f).setDuration(180L).start()
        } else {
            target.animate().alpha(0f).setDuration(180L)
                .withEndAction { target.visibility = View.GONE }
                .start()
        }
    }

    override fun onDestroyView() {
        urlDialog?.dismiss()
        urlDialog = null
        fullscreenHelper?.release()
        fullscreenHelper = null
        drawerHelper = null
        binding.playerView.setControllerVisibilityListener(null as PlayerView.ControllerVisibilityListener?)
        binding.playerView.player = null
        super.onDestroyView()
    }
}
