package com.find.gang.file.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Downloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class Media3ControllerFacade(
    private val context: Context,
    private val onStatusUpdate: (String) -> Unit,
    private val onCacheProgressUpdate: (Float) -> Unit
) {
    // 优化直播与大屏点播的缓冲控制
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 15_000,
            /* maxBufferMs = */ 50_000,
            /* bufferForPlaybackMs = */ 1_500,
            /* bufferForPlaybackAfterRebufferMs = */ 3_000
        )
        .build()

    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setLoadControl(loadControl)
        .setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var cacheJob: Job? = null
    private var activeDownloader: Downloader? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> onStatusUpdate("正在缓冲流媒体数据...")
                    Player.STATE_READY -> onStatusUpdate("正在播放（支持边放边存）")
                    Player.STATE_ENDED -> onStatusUpdate("播放已结束")
                    Player.STATE_IDLE -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                onStatusUpdate("播放失败 [${error.errorCodeName}]: ${error.message}")
            }
        })
    }

    /** 模式 1：立即播放（支持边放边存） */
    fun playStream(url: String) {
        if (url.isBlank()) {
            onStatusUpdate("请输入有效的音视频或直播地址")
            return
        }
        stopPreCache()
        val strategy = StreamStrategyRouter.resolve(url)
        val cacheHint = if (strategy.supportsOfflineCache) "已开启边播边存" else "实时低延迟模式"
        onStatusUpdate("识别为 [${strategy.protocolName}]，$cacheHint，正在加载...")

        val mediaSource = strategy.createMediaSource(context, url)
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true
    }

    /** 模式 2：不播放，仅后台静默预缓存 */
    fun preCacheOnly(url: String) {
        if (url.isBlank()) {
            onStatusUpdate("请输入需要缓存的音视频地址")
            return
        }
        val strategy = StreamStrategyRouter.resolve(url)
        val downloader = strategy.createDownloader(context, url)
        if (downloader == null) {
            onStatusUpdate("[${strategy.protocolName}] 为无限实时流，不支持离线全量预缓存，请直接点击播放")
            return
        }

        player.stop()
        stopPreCache()
        activeDownloader = downloader
        onStatusUpdate("已进入纯后台缓存模式（当前不播放）...")

        cacheJob = scope.launch(Dispatchers.IO) {
            runCatching {
                downloader.download { _, bytesDownloaded, percentDownloaded ->
                    val mb = bytesDownloaded / (1024f * 1024f)
                    val safePercent = if (percentDownloaded >= 0f) percentDownloaded else 0f
                    scope.launch {
                        onCacheProgressUpdate(safePercent)
                        onStatusUpdate(
                            with(String) { format("后台缓存中: %.1f%% (已写入 %.2f MB)", safePercent, mb) }
                        )
                    }
                }
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    onCacheProgressUpdate(100f)
                    onStatusUpdate("后台缓存完成！再次点击“立即播放”将直接读取本地缓存秒开")
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    onStatusUpdate("后台缓存已停止: ${e.message ?: "用户取消"}")
                }
            }
        }
    }

    fun stopPreCache() {
        activeDownloader?.cancel()
        activeDownloader = null
        cacheJob?.cancel()
        cacheJob = null
    }

    fun pause() {
        if (player.isPlaying) {
            player.pause()
        }
    }

    fun release() {
        stopPreCache()
        scope.cancel()
        player.release()
    }
}
