package com.find.gang.file.ui.fragment

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.find.gang.file.databinding.FragmentVideoBinding
import com.find.gang.file.ui.BaseTvFragment
import com.find.gang.file.viewmodel.Media3PlayerViewModel
import com.find.gang.file.viewmodel.VideoAction

/**
 * 手机端流媒体播放与离线缓存页面（Material 3）
 */
class VideoFragment : BaseTvFragment<FragmentVideoBinding>() {

    private val playerViewModel: Media3PlayerViewModel by viewModels()

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentVideoBinding {
        return FragmentVideoBinding.inflate(inflater, container, false)
    }

    /** 手机端不需要遥控器默认焦点，返回 0 */
    override fun getDefaultFocusViewId(): Int = 0

    override fun initViews() {
        binding.viewModel = playerViewModel
        // 使用 DataBinding 时建议绑定 lifecycleOwner，确保 uiState 变化能自动刷新 UI
        binding.lifecycleOwner = viewLifecycleOwner
        binding.playerView.player = playerViewModel.player

        binding.etStreamUrl.setText("http://vjs.zencdn.net/v/oceans.mp4")

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

    override fun observeData() {
        // 订阅共享 ViewModel 数据
    }

    override fun onDestroyView() {
        binding.playerView.player = null
        super.onDestroyView()
    }
}
