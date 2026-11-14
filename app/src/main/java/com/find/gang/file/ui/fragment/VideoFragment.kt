package com.find.gang.file.ui.fragment

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.find.gang.file.R
import com.find.gang.file.databinding.FragmentVideoBinding
import com.find.gang.file.ui.BaseTvFragment
import com.find.gang.file.viewmodel.Media3PlayerViewModel
import com.find.gang.file.viewmodel.VideoAction

/**
 * 继承 BaseTvFragment，遵循模板方法模式实现大屏流媒体播放与离线缓存页面
 */
class VideoFragment : BaseTvFragment<FragmentVideoBinding>() {

    // 独立持有播放器 ViewModel（如需跨页面共享播放状态，可改为 activityViewModels()）
    private val playerViewModel: Media3PlayerViewModel by viewModels()

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentVideoBinding {
        return FragmentVideoBinding.inflate(inflater, container, false)
    }

    /** 指定页面打开时，TV 遥控器默认聚焦在“立即播放”按钮上 */
    override fun getDefaultFocusViewId(): Int = R.id.btn_play

    /** 模板步骤 1：初始化视图与绑定 Player 生命周期 */
    override fun initViews() {
        binding.viewModel = playerViewModel
        binding.playerView.player = playerViewModel.player

        // 默认填入测试流地址（实际业务中可从 sharedViewModel 获取选中的视频地址）
        binding.etStreamUrl.setText("https://mux.dev")

        // 观察者模式：自动随 Fragment 前后台切换暂停/恢复画布渲染
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

    /** 模板步骤 2：绑定遥控器确认键 / 点击事件 */
    override fun initListeners() {
        binding.btnPlay.setOnClickListener {
            val url = binding.etStreamUrl.text?.toString().orEmpty()
            if (url.isBlank()) {
                showToast("请输入有效的视频地址")
                return@setOnClickListener
            }
            playerViewModel.dispatch(VideoAction.Play(url))
        }

        binding.btnPrecache.setOnClickListener {
            val url = binding.etStreamUrl.text?.toString().orEmpty()
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

    /** 模板步骤 3：订阅共享 ViewModel（如从文件传输列表点击某个视频跳转过来播放） */
    override fun observeData() {
        // 示例：如果你在 sharedViewModel (TransferViewModel) 中有选中的视频链接，可在此自动触发播放
        // viewLifecycleOwner.lifecycleScope.launch {
        //     sharedViewModel.selectedVideoUrl.collect { url ->
        //         if (!url.isNullOrBlank()) {
        //             binding.etStreamUrl.setText(url)
        //             playerViewModel.dispatch(VideoAction.Play(url))
        //         }
        //     }
        // }
    }

    override fun onDestroyView() {
        // 必须在 super.onDestroyView() 置空 _binding 之前解除 PlayerView 对 ExoPlayer 的引用
        binding.playerView.player = null
        super.onDestroyView()
    }
}

