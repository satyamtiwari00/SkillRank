package com.example.data.youtube

import android.util.Log
import com.example.BuildConfig
import com.example.data.scoring.ScoringEngine
import com.example.data.scoring.YouTubeTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class YouTubeLessonEvidence(
    val order: Int,
    val videoId: String,
    val title: String,
    val durationMin: Long
)

data class YouTubeEvidence(
    val target: YouTubeTarget,
    val title: String,
    val channel: String,
    val description: String,
    val chapters: List<String>,
    val lessons: List<YouTubeLessonEvidence>,
    val transcriptSnippet: String,
    val publishedAt: String,
    val viewCount: Long,
    val likeCount: Long,
    val durationMin: Long,
    val primaryVideoId: String,
    val thumbnailUrl: String,
    val sourceNotes: List<String>,
    val usedDataApi: Boolean
)

class YouTubeEvidenceFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    fun hasConfiguredApiKey(): Boolean {
        val key = BuildConfig.YOUTUBE_API_KEY.trim()
        return key.isNotEmpty() &&
            !key.equals("YOUR_YOUTUBE_DATA_API_V3_KEY", ignoreCase = true) &&
            !key.equals("MY_YOUTUBE_API_KEY", ignoreCase = true)
    }

    suspend fun fetchEvidence(target: YouTubeTarget): YouTubeEvidence = withContext(Dispatchers.IO) {
        val apiKey = if (hasConfiguredApiKey()) BuildConfig.YOUTUBE_API_KEY.trim() else null
        when (target) {
            is YouTubeTarget.Video -> fetchVideoEvidence(target, apiKey)
            is YouTubeTarget.Playlist -> fetchPlaylistEvidence(target, apiKey)
        }
    }

    private fun fetchVideoEvidence(
        target: YouTubeTarget.Video,
        apiKey: String?
    ): YouTubeEvidence {
        val sources = mutableListOf<String>()
        var title = ""
        var channel = ""
        var description = ""
        var publishedAt = ""
        var viewCount = 0L
        var likeCount = 0L
        var durationMin = 0L
        var usedDataApi = false

        if (apiKey != null) {
            val apiUrl = "https://www.googleapis.com/youtube/v3/videos" +
                "?part=snippet,contentDetails,statistics&id=${target.videoId}&key=$apiKey"
            val json = getJson(apiUrl)
            val items = json?.optJSONArray("items")
            if (items != null && items.length() > 0) {
                val item = items.getJSONObject(0)
                val snippet = item.optJSONObject("snippet")
                val contentDetails = item.optJSONObject("contentDetails")
                val stats = item.optJSONObject("statistics")
                title = snippet?.optString("title").orEmpty()
                channel = snippet?.optString("channelTitle").orEmpty()
                description = snippet?.optString("description").orEmpty()
                publishedAt = snippet?.optString("publishedAt").orEmpty()
                durationMin = parseIso8601DurationMin(contentDetails?.optString("duration").orEmpty())
                viewCount = stats?.optString("viewCount")?.toLongOrNull() ?: 0L
                likeCount = stats?.optString("likeCount")?.toLongOrNull() ?: 0L
                usedDataApi = true
                sources.add("YouTube Data API v3")
            }
        }

        // Public oEmbed + Watch page fallback/enrichment (transcript & chapters)
        if (title.isBlank() || channel.isBlank()) {
            val oembed = fetchOEmbed(target.canonicalUrl)
            if (oembed != null) {
                if (title.isBlank()) title = oembed.optString("title").orEmpty()
                if (channel.isBlank()) channel = oembed.optString("author_name").orEmpty()
                sources.add("YouTube oEmbed")
            }
        }

        val watchHtml = getHtml("https://www.youtube.com/watch?v=${target.videoId}&hl=en")
        var transcriptSnippet = ""
        if (!watchHtml.isNullOrBlank()) {
            val playerJson = extractBalancedJson(watchHtml, "var ytInitialPlayerResponse = ")
                ?: extractBalancedJson(watchHtml, "ytInitialPlayerResponse = ")
            if (playerJson != null) {
                val videoDetails = playerJson.optJSONObject("videoDetails")
                val microformat = playerJson.optJSONObject("microformat")
                    ?.optJSONObject("playerMicroformatRenderer")
                if (title.isBlank()) title = videoDetails?.optString("title").orEmpty()
                if (channel.isBlank()) channel = videoDetails?.optString("author").orEmpty()
                if (description.isBlank()) {
                    description = videoDetails?.optString("shortDescription").orEmpty()
                    if (description.isNotBlank()) sources.add("Watch page description")
                }
                if (durationMin == 0L) {
                    val secs = videoDetails?.optString("lengthSeconds")?.toLongOrNull() ?: 0L
                    durationMin = (secs + 59L) / 60L
                }
                if (viewCount == 0L) {
                    viewCount = videoDetails?.optString("viewCount")?.toLongOrNull() ?: 0L
                }
                if (publishedAt.isBlank()) {
                    publishedAt = microformat?.optString("publishDate").orEmpty()
                }
                transcriptSnippet = fetchTranscriptFromPlayerJson(playerJson)
                if (transcriptSnippet.isNotBlank()) {
                    sources.add("Captions transcript")
                }
            }
        }

        val chapters = extractChapters(description)
        if (chapters.isNotEmpty()) {
            sources.add("Chapters (${chapters.size})")
        }

        return YouTubeEvidence(
            target = target,
            title = title.trim(),
            channel = channel.trim(),
            description = description.take(4000).trim(),
            chapters = chapters,
            lessons = emptyList(),
            transcriptSnippet = transcriptSnippet.take(6000).trim(),
            publishedAt = publishedAt.trim(),
            viewCount = viewCount,
            likeCount = likeCount,
            durationMin = durationMin,
            primaryVideoId = target.videoId,
            thumbnailUrl = ScoringEngine.buildThumbnailUrl(target.videoId),
            sourceNotes = sources.distinct(),
            usedDataApi = usedDataApi
        )
    }

    private fun fetchPlaylistEvidence(
        target: YouTubeTarget.Playlist,
        apiKey: String?
    ): YouTubeEvidence {
        val sources = mutableListOf<String>()
        var title = ""
        var channel = ""
        var description = ""
        var publishedAt = ""
        val lessons = mutableListOf<YouTubeLessonEvidence>()
        var usedDataApi = false

        if (apiKey != null) {
            val plJson = getJson(
                "https://www.googleapis.com/youtube/v3/playlists" +
                    "?part=snippet,contentDetails&id=${target.playlistId}&key=$apiKey"
            )
            val plItems = plJson?.optJSONArray("items")
            if (plItems != null && plItems.length() > 0) {
                val snippet = plItems.getJSONObject(0).optJSONObject("snippet")
                title = snippet?.optString("title").orEmpty()
                channel = snippet?.optString("channelTitle").orEmpty()
                description = snippet?.optString("description").orEmpty()
                publishedAt = snippet?.optString("publishedAt").orEmpty()
                usedDataApi = true
                sources.add("YouTube Data API v3")
            }

            // Fetch playlist items (up to 2 pages = 100 items)
            var nextPageToken: String? = null
            var pageCount = 0
            val rawItems = mutableListOf<Triple<Int, String, String>>()
            do {
                val pageParam = if (nextPageToken != null) "&pageToken=$nextPageToken" else ""
                val itemsJson = getJson(
                    "https://www.googleapis.com/youtube/v3/playlistItems" +
                        "?part=snippet,contentDetails&playlistId=${target.playlistId}&maxResults=50$pageParam&key=$apiKey"
                ) ?: break
                val array = itemsJson.optJSONArray("items") ?: break
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val snip = obj.optJSONObject("snippet")
                    val vid = obj.optJSONObject("contentDetails")?.optString("videoId")
                        ?: snip?.optJSONObject("resourceId")?.optString("videoId")
                        ?: ""
                    val lessonTitle = snip?.optString("title").orEmpty()
                    if (vid.isNotBlank() && lessonTitle != "Private video" && lessonTitle != "Deleted video") {
                        rawItems.add(Triple(rawItems.size, vid, lessonTitle))
                    }
                }
                nextPageToken = itemsJson.optString("nextPageToken").takeIf { it.isNotBlank() }
                pageCount++
            } while (nextPageToken != null && pageCount < 2)

            // Batch up to 50 video IDs per videos.list call for durations
            val durationMap = mutableMapOf<String, Long>()
            rawItems.map { it.second }.distinct().chunked(50).forEach { idChunk ->
                val idsParam = idChunk.joinToString(",")
                val vJson = getJson(
                    "https://www.googleapis.com/youtube/v3/videos" +
                        "?part=contentDetails&id=$idsParam&key=$apiKey"
                )
                val vArray = vJson?.optJSONArray("items")
                if (vArray != null) {
                    for (i in 0 until vArray.length()) {
                        val vObj = vArray.getJSONObject(i)
                        val vid = vObj.optString("id")
                        val dur = parseIso8601DurationMin(
                            vObj.optJSONObject("contentDetails")?.optString("duration").orEmpty()
                        )
                        durationMap[vid] = dur
                    }
                }
            }

            rawItems.forEach { (idx, vid, lTitle) ->
                lessons.add(
                    YouTubeLessonEvidence(
                        order = idx,
                        videoId = vid,
                        title = lTitle,
                        durationMin = durationMap[vid] ?: 0L
                    )
                )
            }
            if (lessons.isNotEmpty()) {
                sources.add("Playlist lessons (${lessons.size})")
            }
        }

        // Public fallback if unkeyed or API returned empty
        if (lessons.isEmpty()) {
            val oembed = fetchOEmbed(target.canonicalUrl)
            if (oembed != null) {
                if (title.isBlank()) title = oembed.optString("title").orEmpty()
                if (channel.isBlank()) channel = oembed.optString("author_name").orEmpty()
                sources.add("YouTube oEmbed")
            }

            val plHtml = getHtml("https://www.youtube.com/playlist?list=${target.playlistId}&hl=en")
            if (!plHtml.isNullOrBlank()) {
                val initialData = extractBalancedJson(plHtml, "var ytInitialData = ")
                    ?: extractBalancedJson(plHtml, "ytInitialData = ")
                if (initialData != null) {
                    val headerMeta = initialData.optJSONObject("metadata")
                        ?.optJSONObject("playlistMetadataRenderer")
                    if (title.isBlank()) title = headerMeta?.optString("title").orEmpty()
                    if (description.isBlank()) description = headerMeta?.optString("description").orEmpty()

                    val parsedLessons = extractPlaylistLessonsFromInitialData(initialData)
                    if (parsedLessons.isNotEmpty()) {
                        lessons.addAll(parsedLessons)
                        sources.add("Public playlist lesson list (${parsedLessons.size})")
                    }
                }
            }
        }

        val firstVideoId = lessons.firstOrNull()?.videoId
            ?: target.initialVideoId.orEmpty()
        val totalDurationMin = lessons.sumOf { it.durationMin }

        return YouTubeEvidence(
            target = target,
            title = title.trim(),
            channel = channel.trim(),
            description = description.take(4000).trim(),
            chapters = emptyList(),
            lessons = lessons,
            transcriptSnippet = "",
            publishedAt = publishedAt.trim(),
            viewCount = 0L,
            likeCount = 0L,
            durationMin = totalDurationMin,
            primaryVideoId = firstVideoId,
            thumbnailUrl = ScoringEngine.buildThumbnailUrl(firstVideoId),
            sourceNotes = sources.distinct(),
            usedDataApi = usedDataApi
        )
    }

    private fun fetchOEmbed(canonicalUrl: String): JSONObject? {
        val encoded = URLEncoder.encode(canonicalUrl, "UTF-8")
        return getJson("https://www.youtube.com/oembed?url=$encoded&format=json")
    }

    private fun fetchTranscriptFromPlayerJson(playerJson: JSONObject): String {
        val tracks = playerJson.optJSONObject("captions")
            ?.optJSONObject("playerCaptionsTracklistRenderer")
            ?.optJSONArray("captionTracks") ?: return ""
        if (tracks.length() == 0) return ""

        var chosenUrl = ""
        for (i in 0 until tracks.length()) {
            val track = tracks.getJSONObject(i)
            val lang = track.optString("languageCode")
            val url = track.optString("baseUrl")
            if (lang.startsWith("en") && url.isNotBlank()) {
                chosenUrl = url
                break
            }
            if (chosenUrl.isBlank() && url.isNotBlank()) {
                chosenUrl = url
            }
        }
        if (chosenUrl.isBlank()) return ""

        val xml = getHtml(chosenUrl) ?: return ""
        val textRegex = Regex("""<text[^>]*>(.*?)</text>""", RegexOption.DOT_MATCHES_ALL)
        val lines = textRegex.findAll(xml)
            .map { match -> decodeHtmlEntities(match.groupValues[1]).replace('\n', ' ').trim() }
            .filter { it.isNotBlank() }
            .toList()
        return lines.joinToString(" ").take(6000)
    }

    private fun extractPlaylistLessonsFromInitialData(root: JSONObject): List<YouTubeLessonEvidence> {
        val result = mutableListOf<YouTubeLessonEvidence>()
        // Walk JSON recursively for playlistVideoRenderer nodes
        fun walk(node: Any?) {
            if (result.size >= 100) return
            when (node) {
                is JSONObject -> {
                    val renderer = node.optJSONObject("playlistVideoRenderer")
                    if (renderer != null) {
                        val vid = renderer.optString("videoId")
                        val runs = renderer.optJSONObject("title")?.optJSONArray("runs")
                        val titleText = runs?.optJSONObject(0)?.optString("text")
                            ?: renderer.optJSONObject("title")?.optString("simpleText").orEmpty()
                        val lengthText = renderer.optJSONObject("lengthText")?.optString("simpleText").orEmpty()
                        if (vid.isNotBlank() && titleText.isNotBlank()) {
                            result.add(
                                YouTubeLessonEvidence(
                                    order = result.size,
                                    videoId = vid,
                                    title = titleText,
                                    durationMin = parseClockDurationMin(lengthText)
                                )
                            )
                        }
                        return
                    }
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        walk(node.opt(keys.next()))
                    }
                }
                is org.json.JSONArray -> {
                    for (i in 0 until node.length()) {
                        walk(node.opt(i))
                    }
                }
            }
        }
        walk(root)
        return result
    }

    private fun extractChapters(description: String): List<String> {
        if (description.isBlank()) return emptyList()
        val chapterRegex = Regex("""^\s*(?:\d{1,2}:)?\d{1,2}:\d{2}\s+[-–|•]?\s*.+$""", RegexOption.MULTILINE)
        return chapterRegex.findAll(description)
            .map { it.value.trim() }
            .take(50)
            .toList()
    }

    private fun extractBalancedJson(html: String, marker: String): JSONObject? {
        val markerIdx = html.indexOf(marker)
        if (markerIdx < 0) return null
        val startIdx = html.indexOf('{', markerIdx + marker.length)
        if (startIdx < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (i in startIdx until html.length) {
            val c = html[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\' && inString) {
                escaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '{') depth++
                else if (c == '}') {
                    depth--
                    if (depth == 0) {
                        val raw = html.substring(startIdx, i + 1)
                        return runCatching { JSONObject(raw) }.getOrNull()
                    }
                }
            }
        }
        return null
    }

    private fun getJson(url: String): JSONObject? {
        val body = getHtml(url) ?: return null
        return runCatching { JSONObject(body) }.getOrNull()
    }

    private fun getHtml(url: String): String? {
        if (!url.startsWith("https://")) return null
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w("YouTubeFetcher", "HTTP ${resp.code} for $url")
                    return null
                }
                resp.body?.string()
            }
        } catch (e: Exception) {
            Log.w("YouTubeFetcher", "Network error fetching $url: ${e.message}")
            null
        }
    }

    private fun parseIso8601DurationMin(iso: String): Long {
        if (iso.isBlank()) return 0L
        val regex = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""")
        val match = regex.matchEntire(iso.trim()) ?: return 0L
        val h = match.groupValues[1].toLongOrNull() ?: 0L
        val m = match.groupValues[2].toLongOrNull() ?: 0L
        val s = match.groupValues[3].toLongOrNull() ?: 0L
        val totalSeconds = h * 3600L + m * 60L + s
        return if (totalSeconds == 0L) 0L else (totalSeconds + 59L) / 60L
    }

    private fun parseClockDurationMin(clock: String): Long {
        val parts = clock.trim().split(':').mapNotNull { it.toLongOrNull() }
        val totalSeconds = when (parts.size) {
            2 -> parts[0] * 60L + parts[1]
            3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
            else -> 0L
        }
        return if (totalSeconds == 0L) 0L else (totalSeconds + 59L) / 60L
    }

    private fun decodeHtmlEntities(input: String): String = input
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
}
