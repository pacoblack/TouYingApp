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
import com.find.gang.file.media.TvChannel
import com.find.gang.file.ui.BaseTvFragment
import com.find.gang.file.viewmodel.Media3PlayerViewModel
import com.find.gang.file.viewmodel.VideoAction

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
                arguments = bundleOf(
                    ARG_CHANNEL_NAME to channel.name,
                    ARG_STREAM_URL to channel.primaryUrl,
                    ARG_STREAM_URLS to ArrayList(channel.urls),
                    ARG_AUTO_PLAY to autoPlay
                )
            }
        }

        @JvmStatic
        fun newInstance(url: String, autoPlay: Boolean = true, title: String = ""): VideoFragment {
            return VideoFragment().apply {
                arguments = bundleOf(
                    ARG_CHANNEL_NAME to title,
                    ARG_STREAM_URL to url,
                    ARG_STREAM_URLS to arrayListOf(url),
                    ARG_AUTO_PLAY to autoPlay
                )
            }
        }
    }

    private val playerViewModel: Media3PlayerViewModel by viewModels()
    private var isFullscreen = false
    private var hasHandledInitialUrl = false

    private var channelName: String = ""
    private var candidateUrls: List<String> = emptyList()
    private var currentSourceIndex = 0

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

        binding.playerView.setFullscreenButtonClickListener { enterFullscreen ->
            toggleFullscreen(enterFullscreen)
        }
        binding.playerView.setShowFastForwardButton(true)
        binding.playerView.setShowRewindButton(true)

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
        updateChannelHeaderTitle()

        if (autoPlay && !hasHandledInitialUrl) {
            hasHandledInitialUrl = true
            playerViewModel.dispatch(VideoAction.Play(targetUrl))
        }
    }

    private fun updateChannelHeaderTitle() {
        if (channelName.isNotBlank()) {
            val sourceTag = if (candidateUrls.size > 1) " (线路 ${currentSourceIndex + 1}/${candidateUrls.size})" else ""
            activity?.title = "$channelName$sourceTag"
        }
    }

    fun switchNextSource() {
        if (candidateUrls.size <= 1) {
            showToast("当前频道暂无其他备用线路")
            return
        }
        currentSourceIndex = (currentSourceIndex + 1) % candidateUrls.size
        val nextUrl = candidateUrls[currentSourceIndex]
        binding.etStreamUrl.setText(nextUrl)
        updateChannelHeaderTitle()
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
            updateChannelHeaderTitle()
            if (autoPlay) {
                playerViewModel.dispatch(VideoAction.Play(channel.primaryUrl))
            }
        }
    }

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
