package com.find.gang.file.viewmodel

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.find.gang.file.media.Media3ControllerFacade
import com.find.gang.file.media.MediaCacheSingleton
import com.find.gang.file.media.TvChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 不可变 UI 状态（唯一可信数据源） */
data class MediaPlayerUiState(
    val channelName: String = "",
    val candidateUrls: List<String> = emptyList(),
    val currentSourceIndex: Int = 0,
    val currentUrl: String = "",
    val statusText: String = "准备就绪，按确认键开始播放",
    val cacheProgress: Int = 0,
    val cacheUsageText: String = "本地缓存：0.00 MB / 2048 MB",
    val isCaching: Boolean = false,
    val showUrlDialog: Boolean = false
) {
    val displayTitle: String
        get() = channelName.ifBlank { "流媒体播放" }

    val hasMultiSource: Boolean
        get() = candidateUrls.size > 1

    val sourceBadgeText: String
        get() = if (hasMultiSource) "线路 ${currentSourceIndex + 1}/${candidateUrls.size}" else ""

    val panelTitleText: String
        get() = if (hasMultiSource) "$displayTitle ($sourceBadgeText)" else displayTitle
}

/** 单向交互意图 (Command / Intent 模式) */
sealed interface VideoAction {
    data class InitArguments(
        val channelName: String,
        val urls: List<String>,
        val autoPlay: Boolean
    ) : VideoAction

    data class LoadChannel(val channel: TvChannel, val autoPlay: Boolean = true) : VideoAction
    data class Play(val url: String) : VideoAction
    data class PreCache(val url: String) : VideoAction
    data object SwitchNextSource : VideoAction
    data object Pause : VideoAction
    data object StopCache : VideoAction
    data object DismissUrlDialog : VideoAction
    data object CheckUrlPromptOnVisible : VideoAction
}

/** 一次性 UI 副作用事件（Toast 提示） */
sealed interface VideoEffect {
    data class ShowToast(val message: String) : VideoEffect
}

@OptIn(UnstableApi::class)
class Media3PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MediaPlayerUiState())
    val uiState: StateFlow<MediaPlayerUiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<VideoEffect>(Channel.BUFFERED)
    val uiEffect = _uiEffect.receiveAsFlow()

    private var hasInitialized = false

    private val controllerFacade = Media3ControllerFacade(
        context = application,
        onStatusUpdate = { status ->
            _uiState.update { it.copy(statusText = status) }
            refreshCacheUsage()
        },
        onCacheProgressUpdate = { progress ->
            _uiState.update {
                it.copy(
                    cacheProgress = progress.toInt(),
                    isCaching = progress in 0.1f..99.9f
                )
            }
        }
    )

    val player: ExoPlayer
        get() = controllerFacade.player

    init {
        refreshCacheUsage()
    }

    fun dispatch(action: VideoAction) {
        when (action) {
            is VideoAction.InitArguments -> handleInitArguments(action)
            is VideoAction.LoadChannel -> handleLoadChannel(action)
            is VideoAction.Play -> handlePlaySingleUrl(action.url)
            is VideoAction.PreCache -> handlePreCache(action.url)
            VideoAction.SwitchNextSource -> handleSwitchNextSource()
            VideoAction.Pause -> controllerFacade.pause()
            VideoAction.StopCache -> {
                controllerFacade.stopPreCache()
                _uiState.update { it.copy(isCaching = false, statusText = "已停止后台缓存") }
            }
            VideoAction.DismissUrlDialog -> _uiState.update { it.copy(showUrlDialog = false) }
            VideoAction.CheckUrlPromptOnVisible -> {
                if (_uiState.value.currentUrl.isBlank()) {
                    _uiState.update { it.copy(showUrlDialog = true) }
                }
            }
        }
    }

    private fun handleInitArguments(action: VideoAction.InitArguments) {
        if (hasInitialized) return
        hasInitialized = true

        val validUrls = action.urls.filter { it.isNotBlank() }
        if (validUrls.isEmpty()) {
            _uiState.update {
                it.copy(
                    channelName = action.channelName,
                    candidateUrls = emptyList(),
                    currentSourceIndex = 0,
                    currentUrl = "",
                    showUrlDialog = true
                )
            }
            return
        }

        val firstUrl = validUrls.first()
        _uiState.update {
            it.copy(
                channelName = action.channelName,
                candidateUrls = validUrls,
                currentSourceIndex = 0,
                currentUrl = firstUrl,
                showUrlDialog = false
            )
        }
        if (action.autoPlay) {
            controllerFacade.playStream(firstUrl)
        }
    }

    private fun handleLoadChannel(action: VideoAction.LoadChannel) {
        val validUrls = action.channel.urls.filter { it.isNotBlank() }
        if (validUrls.isEmpty()) {
            emitEffect(VideoEffect.ShowToast("该频道暂无可用播放地址"))
            return
        }
        hasInitialized = true
        val firstUrl = validUrls.first()
        _uiState.update {
            it.copy(
                channelName = action.channel.name,
                candidateUrls = validUrls,
                currentSourceIndex = 0,
                currentUrl = firstUrl,
                showUrlDialog = false
            )
        }
        if (action.autoPlay) {
            controllerFacade.playStream(firstUrl)
        }
    }

    private fun handlePlaySingleUrl(rawUrl: String) {
        val url = rawUrl.trim()
        if (url.isBlank()) {
            emitEffect(VideoEffect.ShowToast("请输入有效的视频地址"))
            return
        }
        _uiState.update { state ->
            val existingIndex = state.candidateUrls.indexOf(url)
            if (existingIndex >= 0) {
                state.copy(currentSourceIndex = existingIndex, currentUrl = url, showUrlDialog = false)
            } else {
                state.copy(candidateUrls = listOf(url), currentSourceIndex = 0, currentUrl = url, showUrlDialog = false)
            }
        }
        controllerFacade.playStream(url)
    }

    private fun handleSwitchNextSource() {
        val state = _uiState.value
        if (state.candidateUrls.size <= 1) {
            emitEffect(VideoEffect.ShowToast("当前频道暂无其他备用线路"))
            return
        }
        val nextIndex = (state.currentSourceIndex + 1) % state.candidateUrls.size
        val nextUrl = state.candidateUrls[nextIndex]
        _uiState.update {
            it.copy(
                currentSourceIndex = nextIndex,
                currentUrl = nextUrl
            )
        }
        emitEffect(VideoEffect.ShowToast("已切换至线路 ${nextIndex + 1}/${state.candidateUrls.size}"))
        controllerFacade.playStream(nextUrl)
    }

    private fun handlePreCache(rawUrl: String) {
        val url = rawUrl.trim()
        if (url.isBlank()) {
            emitEffect(VideoEffect.ShowToast("请输入需要预缓存的地址"))
            return
        }
        _uiState.update { it.copy(cacheProgress = 0, isCaching = true) }
        controllerFacade.preCacheOnly(url)
    }

    private fun emitEffect(effect: VideoEffect) {
        viewModelScope.launch {
            _uiEffect.send(effect)
        }
    }

    private fun refreshCacheUsage() {
        val usedMb = MediaCacheSingleton.getUsedCacheMb(getApplication())
        _uiState.update {
            it.copy(cacheUsageText = String.format("本地缓存：%.2f MB / 2048 MB", usedMb))
        }
    }

    override fun onCleared() {
        super.onCleared()
        controllerFacade.release()
    }
}
