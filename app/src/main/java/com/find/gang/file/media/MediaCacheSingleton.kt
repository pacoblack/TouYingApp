package com.find.gang.file.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object MediaCacheSingleton {

    @Volatile
    private var simpleCache: SimpleCache? = null
    private const val MAX_CACHE_BYTES = 2L * 1024L * 1024L * 1024L // 2GB LRU 缓存上限

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

    /** 构建支持“边播边存”的 CacheDataSource.Factory */
    fun createCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(12_000)

        val upstreamFactory = DefaultDataSource.Factory(context.applicationContext, httpFactory)

        return CacheDataSource.Factory()
            .setCache(getCache(context))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun getUsedCacheMb(context: Context): Float {
        return getCache(context).cacheSpace / (1024f * 1024f)
    }
}
