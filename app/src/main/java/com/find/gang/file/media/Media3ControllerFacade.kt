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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

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
    private var reconnectJob: Job? = null
    private var activeDownloader: Downloader? = null

    // 自动重连状态控制
    private var currentStreamUrl: String = ""
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> onStatusUpdate("正在缓冲流媒体数据...")
                    Player.STATE_READY -> {
                        // 播放恢复正常，清零重连计数
                        reconnectAttempts = 0
                        reconnectJob?.cancel()
                        onStatusUpdate("正在播放（支持边放边存）")
                    }
                    Player.STATE_ENDED -> {
                        // 如果是直播流意外触发 ENDED（如推流端短暂断开），尝试自动重连
                        if (player.isCurrentMediaItemLive && currentStreamUrl.isNotBlank()) {
                            scheduleReconnect("直播流信号中断")
                        } else {
                            onStatusUpdate("播放已结束")
                        }
                    }
                    Player.STATE_IDLE -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (isRecoverableError(error) && currentStreamUrl.isNotBlank()) {
                    scheduleReconnect(error.errorCodeName)
                } else {
                    onStatusUpdate("播放失败 [${error.errorCodeName}]: ${error.message}")
                }
            }
        })
    }

    /** 判断是否属于网络波动、超时或直播切片窗口丢失等可重连异常 */
    private fun isRecoverableError(error: PlaybackException): Boolean {
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> true
            else -> error.errorCode in 2000..2999 // 涵盖所有 IO 类网络异常
        }
    }

    /** 指数退避自动重连（延迟 2s -> 4s -> 8s -> 10s...） */
    private fun scheduleReconnect(reason: String) {
        if (reconnectAttempts >= maxReconnectAttempts) {
            onStatusUpdate("断流重连已达上限 ($maxReconnectAttempts 次)，请检查网络或流地址")
            return
        }

        reconnectJob?.cancel()
        reconnectAttempts++
        val delayMs = (2000L * (1 shl (reconnectAttempts - 1))).coerceAtMost(10_000L)

        reconnectJob = scope.launch {
            onStatusUpdate("断流 [$reason]，${delayMs / 1000}秒后进行第 $reconnectAttempts/$maxReconnectAttempts 次重连...")
            delay(delayMs.milliseconds)

            // 针对直播窗口落后（BEHIND_LIVE_WINDOW）直接 seekToDefaultPosition 重新同步直播边缘
            if (player.isCurrentMediaItemLive) {
                player.seekToDefaultPosition()
                player.prepare()
                player.playWhenReady = true
            } else {
                // 重新构建 MediaSource 恢复播放（点播会自动保留当前进度）
                val resumePosition = player.currentPosition.coerceAtLeast(0L)
                val strategy = StreamStrategyRouter.resolve(currentStreamUrl)
                val mediaSource = strategy.createMediaSource(context, currentStreamUrl)
                player.setMediaSource(mediaSource, if (resumePosition > 0) resumePosition else androidx.media3.common.C.TIME_UNSET)
                player.prepare()
                player.playWhenReady = true
            }
        }
    }

    /** 模式 1：立即播放（支持边放边存） */
    fun playStream(url: String) {
        if (url.isBlank()) {
            onStatusUpdate("请输入有效的音视频或直播地址")
            return
        }
        // 手动触发新播放时，重置重连状态
        currentStreamUrl = url.trim()
        reconnectAttempts = 0
        reconnectJob?.cancel()
        stopPreCache()

        val strategy = StreamStrategyRouter.resolve(currentStreamUrl)
        val cacheHint = if (strategy.supportsOfflineCache) "已开启边播边存" else "实时低延迟模式"
        onStatusUpdate("识别为 [${strategy.protocolName}]，$cacheHint，正在加载...")

        val mediaSource = strategy.createMediaSource(context, currentStreamUrl)
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
        reconnectJob?.cancel()
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
                            with(String) {
                                return@with this.format("后台缓存中: %.1f%% (已写入 %.2f MB)", safePercent, mb)
                            }
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
        reconnectJob?.cancel()
        if (player.isPlaying) {
            player.pause()
        }
    }

    fun release() {
        reconnectJob?.cancel()
        stopPreCache()
        scope.cancel()
        player.release()
    }
}
