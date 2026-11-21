package com.find.gang.file.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSink
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object MediaCacheSingleton {

    @Volatile
    private var simpleCache: SimpleCache? = null
    private const val MAX_CACHE_BYTES = 2L * 1024L * 1024L * 1024L

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    fun getCache(context: Context): SimpleCache {
        return simpleCache ?: synchronized(this) {
            simpleCache ?: run {
                val cacheDir = File(context.applicationContext.cacheDir, "media3_stream_cache")
                val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
                val dbProvider = StandaloneDatabaseProvider(context.applicationContext)
                SimpleCache(cacheDir, evictor, dbProvider).also { simpleCache = it }
            }
        }
    }

    fun createHttpDataSourceFactory(): DefaultHttpDataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setUserAgent(DEFAULT_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(12_000)
    }

    private fun isDynamicManifest(uri: Uri): Boolean {
        val path = uri.path?.lowercase().orEmpty()
        return path.endsWith(".m3u8") ||
            path.endsWith(".mpd") ||
            path.contains(".ism/manifest") ||
            path.endsWith(".ism")
    }

    fun createSmartStreamCacheFactory(context: Context): DataSource.Factory {
        val cache = getCache(context)
        val upstreamFactory = DefaultDataSource.Factory(
            context.applicationContext,
            createHttpDataSourceFactory()
        )

        val smartWriteSinkFactory = DataSink.Factory {
            val defaultSink = CacheDataSink.Factory()
                .setCache(cache)
                .createDataSink()

            object : DataSink {
                private var skipWrite = false

                override fun open(dataSpec: DataSpec) {
                    skipWrite = isDynamicManifest(dataSpec.uri) || dataSpec.length == C.LENGTH_UNSET.toLong()
                    if (!skipWrite) {
                        defaultSink.open(dataSpec)
                    }
                }

                override fun write(buffer: ByteArray, offset: Int, length: Int) {
                    if (!skipWrite) {
                        defaultSink.write(buffer, offset, length)
                    }
                }

                override fun close() {
                    if (!skipWrite) {
                        defaultSink.close()
                    }
                }
            }
        }

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(smartWriteSinkFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return DataSource.Factory {
            val networkDataSource = upstreamFactory.createDataSource()
            val cacheDataSource = cacheDataSourceFactory.createDataSource()

            object : DataSource by cacheDataSource {
                private var activeSource: DataSource = cacheDataSource

                override fun open(dataSpec: DataSpec): Long {
                    activeSource = if (isDynamicManifest(dataSpec.uri)) {
                        networkDataSource
                    } else {
                        cacheDataSource
                    }
                    return activeSource.open(dataSpec)
                }

                override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
                    return activeSource.read(buffer, offset, readLength)
                }

                override fun getUri(): Uri? = activeSource.uri

                override fun getResponseHeaders(): Map<String, List<String>> = activeSource.responseHeaders

                override fun close() {
                    activeSource.close()
                }
            }
        }
    }

    fun createOfflineCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val upstreamFactory = DefaultDataSource.Factory(
            context.applicationContext,
            createHttpDataSourceFactory()
        )
        return CacheDataSource.Factory()
            .setCache(getCache(context))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun getUsedCacheMb(context: Context): Float {
        return getCache(context).cacheSpace / (1024f * 1024f)
    }
}
