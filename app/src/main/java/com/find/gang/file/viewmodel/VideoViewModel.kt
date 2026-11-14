package com.find.gang.file.viewmodel

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.find.gang.file.media.Media3ControllerFacade
import com.find.gang.file.media.MediaCacheSingleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 不可变 UI 状态 */
data class MediaPlayerUiState(
    val statusText: String = "准备就绪，按确认键开始播放",
    val cacheProgress: Int = 0,
    val cacheUsageText: String = "本地缓存：0.00 MB / 2048 MB",
    val isCaching: Boolean = false
)

/** 单向交互事件 (Command / Intent 模式) */
sealed interface VideoAction {
    data class Play(val url: String) : VideoAction
    data class PreCache(val url: String) : VideoAction
    data object Pause : VideoAction
    data object StopCache : VideoAction
}

@OptIn(UnstableApi::class)
class Media3PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MediaPlayerUiState())
    val uiState: StateFlow<MediaPlayerUiState> = _uiState.asStateFlow()

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
            is VideoAction.Play -> controllerFacade.playStream(action.url)
            is VideoAction.PreCache -> {
                _uiState.update { it.copy(cacheProgress = 0, isCaching = true) }
                controllerFacade.preCacheOnly(action.url)
            }
            VideoAction.Pause -> controllerFacade.pause()
            VideoAction.StopCache -> {
                controllerFacade.stopPreCache()
                _uiState.update { it.copy(isCaching = false, statusText = "已停止后台缓存") }
            }
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
