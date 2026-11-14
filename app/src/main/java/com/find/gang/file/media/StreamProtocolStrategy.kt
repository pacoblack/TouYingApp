package com.find.gang.file.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.rtmp.RtmpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.offline.DashDownloader
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.hls.offline.HlsDownloader
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.offline.ProgressiveDownloader
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.smoothstreaming.SsMediaSource
import androidx.media3.exoplayer.smoothstreaming.offline.SsDownloader
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource

@OptIn(UnstableApi::class)
interface StreamProtocolStrategy {
    val protocolName: String
    val supportsOfflineCache: Boolean
    fun createMediaSource(context: Context, url: String): MediaSource
    fun createDownloader(context: Context, url: String): Downloader? = null
}

/** 1. RTSP 实时监控/推流策略（强制 RTP over TCP 提升 Wi-Fi 稳定性） */
@OptIn(UnstableApi::class)
class RtspStrategy : StreamProtocolStrategy {
    override val protocolName = "RTSP 实时流"
    override val supportsOfflineCache = false

    override fun createMediaSource(context: Context, url: String): MediaSource {
        return RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)
            .createMediaSource(MediaItem.fromUri(url.trim().toUri()))
    }
}

/** 2. RTMP 直播推流策略 */
@OptIn(UnstableApi::class)
class RtmpStrategy : StreamProtocolStrategy {
    override val protocolName = "RTMP 直播流"
    override val supportsOfflineCache = false

    override fun createMediaSource(context: Context, url: String): MediaSource {
        val rtmpFactory = RtmpDataSource.Factory()
        return DefaultMediaSourceFactory(rtmpFactory)
            .createMediaSource(MediaItem.fromUri(url.trim().toUri()))
    }
}

/** 3. HLS (.m3u8) 直播/点播策略（支持边播边存 + 分片预缓存） */
@OptIn(UnstableApi::class)
class HlsStrategy : StreamProtocolStrategy {
    override val protocolName = "HLS (m3u8)"
    override val supportsOfflineCache = true

    override fun createMediaSource(context: Context, url: String): MediaSource {
        val cacheFactory = MediaCacheSingleton.createCacheDataSourceFactory(context)
        return HlsMediaSource.Factory(cacheFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(MediaItem.fromUri(url.trim().toUri()))
    }

    override fun createDownloader(context: Context, url: String): Downloader {
        val cacheFactory = MediaCacheSingleton.createCacheDataSourceFactory(context)
        val mediaItem = MediaItem.fromUri(url.trim().toUri())
        return HlsDownloader.Factory(cacheFactory).create(mediaItem)
    }
}

/** 4. DASH (.mpd) 自适应流策略 */
@OptIn(UnstableApi::class)
class DashStrategy : StreamProtocolStrategy {
    override val protocolName = "DASH (mpd)"
    override val supportsOfflineCache = true

    override fun createMediaSource(context: Context, url: String): MediaSource {
        val cacheFactory = MediaCacheSingleton.createCacheDataSourceFactory(context)
        return DashMediaSource.Factory(cacheFactory)
            .createMediaSource(MediaItem.fromUri(url.trim().toUri()))
    }

    override fun createDownloader(context: Context, url: String): Downloader {
        val cacheFactory = MediaCacheSingleton.createCacheDataSourceFactory(context)
        val mediaItem = MediaItem.fromUri(url.trim().toUri())
        return DashDownloader.Factory(cacheFactory).create(mediaItem)
    }
}

/** 5. SmoothStreaming (.ism) 策略 */
@OptIn(UnstableApi::class)
class SmoothStreamingStrategy : StreamProtocolStrategy {
    override val protocolName = "SmoothStreaming"
    override val supportsOfflineCache = true

    override fun createMediaSource(context: Context, url: String): MediaSource {
        val cacheFactory = MediaCacheSingleton.createCacheDataSourceFactory(context)
        return SsMediaSource.Factory(cacheFactory)
            .createMediaSource(MediaItem.fromUri(url.trim().toUri()))
    }

    override fun createDownloader(context: Context, url: String): Downloader {
        val cacheFactory = MediaCacheSingleton.createCacheDataSourceFactory(context)
        val mediaItem = MediaItem.fromUri(url.trim().toUri())
        return SsDownloader.Factory(cacheFactory).create(mediaItem)
    }
}

/** 6. 通用点播视频/音频直链策略 (MP4 / MKV / FLV / WEBM / MP3 / AAC / FLAC 等) */
@OptIn(UnstableApi::class)
class ProgressiveMediaStrategy : StreamProtocolStrategy {
    override val protocolName = "点播音视频直链"
    override val supportsOfflineCache = true

    override fun createMediaSource(context: Context, url: String): MediaSource {
        val cacheFactory = MediaCacheSingleton.createCacheDataSourceFactory(context)
        return DefaultMediaSourceFactory(cacheFactory)
            .createMediaSource(MediaItem.fromUri(url.trim().toUri()))
    }

    override fun createDownloader(context: Context, url: String): Downloader {
        val cacheFactory = MediaCacheSingleton.createCacheDataSourceFactory(context)
        return ProgressiveDownloader(MediaItem.fromUri(url.trim().toUri()), cacheFactory)
    }
}

/** 策略路由工厂 */
object StreamStrategyRouter {
    fun resolve(url: String): StreamProtocolStrategy {
        val lower = url.trim().lowercase()
        return when {
            lower.startsWith("rtsp://") -> RtspStrategy()
            lower.startsWith("rtmp://") || lower.startsWith("rtmps://") -> RtmpStrategy()
            lower.contains(".m3u8") -> HlsStrategy()
            lower.contains(".mpd") -> DashStrategy()
            lower.contains(".ism") -> SmoothStreamingStrategy()
            else -> ProgressiveMediaStrategy()
        }
    }
}
