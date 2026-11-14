package com.example.data.scoring

import com.example.model.ScoreBand
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI

sealed interface YouTubeTarget {
    val courseId: String
    val canonicalUrl: String

    data class Video(
        val videoId: String
    ) : YouTubeTarget {
        override val courseId: String = "yt_v_$videoId"
        override val canonicalUrl: String = "https://www.youtube.com/watch?v=$videoId"
    }

    data class Playlist(
        val playlistId: String,
        val initialVideoId: String? = null
    ) : YouTubeTarget {
        override val courseId: String = "yt_pl_$playlistId"
        override val canonicalUrl: String = "https://www.youtube.com/playlist?list=$playlistId"
    }
}

object ScoringEngine {

    private val URL_REGEX = Regex("""https?://[^\s<>"{}|\\^`\[\]]+""", RegexOption.IGNORE_CASE)
    private val ID_SAFE_REGEX = Regex("""^[a-zA-Z0-9_\-]{6,64}$""")
    private val ALLOWED_HOSTS = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
        "youtu.be"
    )

    /**
     * Extracts and validates a YouTube video or playlist target from pasted or shared text.
     * Rejects non-https and non-youtube.com / youtu.be URLs.
     */
    fun parseYouTubeTarget(rawInput: String): YouTubeTarget? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        val candidateUrl = URL_REGEX.find(trimmed)?.value ?: trimmed
        val normalizedInput = if (candidateUrl.startsWith("http://", ignoreCase = true)) {
            "https://" + candidateUrl.substring(7)
        } else {
            candidateUrl
        }

        val uri = runCatching { URI(normalizedInput) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in ALLOWED_HOSTS) return null

        val queryParams = parseQueryParams(uri.rawQuery)
        val pathSegments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }

        if (host == "youtu.be") {
            val vid = pathSegments.firstOrNull()?.takeIf { ID_SAFE_REGEX.matches(it) } ?: return null
            val listId = queryParams["list"]?.takeIf { ID_SAFE_REGEX.matches(it) }
            return if (listId != null && pathSegments.isEmpty()) {
                YouTubeTarget.Playlist(listId)
            } else {
                // Keep playlist if explicitly a playlist link, otherwise single video
                YouTubeTarget.Video(vid)
            }
        }

        val listId = queryParams["list"]?.takeIf { ID_SAFE_REGEX.matches(it) }
        val watchVideoId = queryParams["v"]?.takeIf { ID_SAFE_REGEX.matches(it) }

        if (pathSegments.firstOrNull() == "playlist" && listId != null) {
            return YouTubeTarget.Playlist(listId)
        }

        if (watchVideoId != null) {
            return if (listId != null && !listId.startsWith("RD") && !listId.startsWith("LL")) {
                // Watch link inside a course playlist -> evaluate as playlist when list= is present unless user passed single-video?
                // Wait: if someone shares a video from a playlist, watch?v=...&list=PL... -> let's prioritize explicit /playlist or PL list!
                if (listId.startsWith("PL")) {
                    YouTubeTarget.Playlist(listId, initialVideoId = watchVideoId)
                } else {
                    YouTubeTarget.Video(watchVideoId)
                }
            } else {
                YouTubeTarget.Video(watchVideoId)
            }
        }

        if (pathSegments.size >= 2 && pathSegments[0] in setOf("shorts", "live", "embed")) {
            val vid = pathSegments[1].takeIf { ID_SAFE_REGEX.matches(it) } ?: return null
            return YouTubeTarget.Video(vid)
        }

        if (listId != null) {
            return YouTubeTarget.Playlist(listId)
        }

        return null
    }

    /**
     * Fixed scoring formula:
     * score = 0.30*clarity + 0.30*depth + 0.20*beginner + 0.20*freshness
     * Rounded half-up to 1 decimal place.
     */
    fun computeScore(
        clarity: Double,
        depth: Double,
        beginner: Double,
        freshness: Double
    ): Double {
        val c = clampSubScore(clarity)
        val d = clampSubScore(depth)
        val b = clampSubScore(beginner)
        val f = clampSubScore(freshness)
        val raw = 0.30 * c + 0.30 * d + 0.20 * b + 0.20 * f
        return BigDecimal.valueOf(raw).setScale(1, RoundingMode.HALF_UP).toDouble()
    }

    fun clampSubScore(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        val clamped = value.coerceIn(0.0, 10.0)
        return BigDecimal.valueOf(clamped).setScale(1, RoundingMode.HALF_UP).toDouble()
    }

    fun bandFor(score: Double, status: String): ScoreBand {
        if (status != "scored") return ScoreBand.UNSCORED
        return when {
            score >= 8.5 -> ScoreBand.TOP_PICK
            score >= 7.0 -> ScoreBand.RECOMMENDED
            else -> ScoreBand.MIXED
        }
    }

    fun limitSummaryTo25Words(raw: String): String {
        val words = raw.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
        return if (words.size <= 25) {
            words.joinToString(" ")
        } else {
            words.take(25).joinToString(" ") + "..."
        }
    }

    fun buildThumbnailUrl(videoId: String): String {
        if (videoId.isBlank()) return ""
        return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    }

    fun isSafeYouTubeHttpsUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && host in ALLOWED_HOSTS
    }

    private fun parseQueryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = pair.substring(0, idx)
            val value = pair.substring(idx + 1)
            key to value
        }.toMap()
    }
}
