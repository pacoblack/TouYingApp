package com.find.gang.file.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class TvChannel(
    val name: String,
    val group: String,
    val urls: List<String>
) {
    val primaryUrl: String get() = urls.firstOrNull().orEmpty()
}

data class TvChannelGroup(
    val genre: String,
    val channels: List<TvChannel>
)

object IptvPlaylistParser {

    suspend fun fetchAndParse(
        playlistUrl: String = "https://live.zbds.top/tv/iptv4.txt",
        fallbackContent: String = ""
    ): List<TvChannelGroup> = withContext(Dispatchers.IO) {
        val rawText = runCatching {
            val conn = (URL(playlistUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
            }
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse { fallbackContent }

        parseTxt(rawText)
    }

    fun parseTxt(content: String): List<TvChannelGroup> {
        if (content.isBlank()) return emptyList()

        val groupMap = LinkedHashMap<String, LinkedHashMap<String, MutableList<String>>>()
        var currentGenre = "默认频道"

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@forEach

            if (line.endsWith(",#genre#", ignoreCase = true)) {
                currentGenre = line.substringBefore(",#genre#").trim().ifBlank { "未分类" }
                if (currentGenre != "更新时间") {
                    groupMap.getOrPut(currentGenre) { LinkedHashMap() }
                }
                return@forEach
            }

            if (currentGenre == "更新时间") return@forEach

            val commaIndex = line.indexOf(',')
            if (commaIndex <= 0) return@forEach

            val channelName = line.substring(0, commaIndex).trim()
            val rawUrlPart = line.substring(commaIndex + 1).trim()

            val validUrls = rawUrlPart.split('#')
                .map { it.trim().trimEnd(',', '"', '\'') }
                .filter {
                    it.startsWith("http://") ||
                        it.startsWith("https://") ||
                        it.startsWith("rtsp://") ||
                        it.startsWith("rtmp://")
                }

            if (channelName.isNotBlank() && validUrls.isNotEmpty()) {
                val channelsInGroup = groupMap.getOrPut(currentGenre) { LinkedHashMap() }
                val urlList = channelsInGroup.getOrPut(channelName) { mutableListOf() }
                validUrls.forEach { url ->
                    if (!urlList.contains(url)) {
                        urlList.add(url)
                    }
                }
            }
        }

        return groupMap.map { (genre, channelMap) ->
            TvChannelGroup(
                genre = genre,
                channels = channelMap.map { (name, urls) ->
                    TvChannel(name = name, group = genre, urls = urls)
                }
            )
        }
    }
}
