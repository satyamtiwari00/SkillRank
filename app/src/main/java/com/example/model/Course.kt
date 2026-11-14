package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class CourseCategory(val label: String) {
    ALL("All"),
    VIDEO_EDITING("Video editing"),
    CODING("Coding"),
    AI("AI"),
    AGENTIC_AI("Agentic AI"),
    AUTOMATION("Automation"),
    OTHER_SKILLS("Other skills");

    companion object {
        val scorableCategories: List<CourseCategory> = entries.filter { it != ALL }

        fun fromLabel(raw: String): CourseCategory {
            return scorableCategories.firstOrNull {
                it.label.equals(raw.trim(), ignoreCase = true)
            } ?: OTHER_SKILLS
        }
    }
}

enum class ScoreBand(val label: String) {
    TOP_PICK("Top pick"),
    RECOMMENDED("Recommended"),
    MIXED("Mixed"),
    UNSCORED("Unscored")
}

@IgnoreExtraProperties
data class Course(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val channel: String = "",
    val url: String = "",
    val type: String = "Video",
    val category: String = CourseCategory.OTHER_SKILLS.label,
    val level: String = "All levels",
    val language: String = "English",
    val status: String = "scored",
    val reasonIfUnscored: String = "",
    val clarity: Double = 0.0,
    val depth: Double = 0.0,
    val beginner: Double = 0.0,
    val freshness: Double = 0.0,
    val score: Double = 0.0,
    val summary: String = "",
    val basis: String = "",
    val confidence: String = "Limited",
    val videoId: String = "",
    val playlistId: String = "",
    val thumb: String = "",
    val durationMin: Long = 0L,
    val lessonCount: Long = 0L,
    val bookmarked: Boolean = false,
    @ServerTimestamp val scoredAt: Timestamp? = null,
    @ServerTimestamp val addedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun scoreBand(): ScoreBand {
        if (status != "scored") return ScoreBand.UNSCORED
        return when {
            score >= 8.5 -> ScoreBand.TOP_PICK
            score >= 7.0 -> ScoreBand.RECOMMENDED
            else -> ScoreBand.MIXED
        }
    }

    fun isOlderThan90Days(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val scoredMillis = scoredAt?.toDate()?.time ?: return false
        val ninetyDaysMillis = 90L * 24L * 60L * 60L * 1000L
        return (nowMillis - scoredMillis) > ninetyDaysMillis
    }

    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "title" to title.take(240).ifBlank { "Untitled course" },
        "channel" to channel.take(160).ifBlank { "Unknown channel" },
        "url" to url.take(500),
        "type" to if (type == "Playlist") "Playlist" else "Video",
        "category" to CourseCategory.fromLabel(category).label,
        "level" to normalizeLevel(level),
        "language" to language.take(60).ifBlank { "English" },
        "status" to if (status == "unscored") "unscored" else "scored",
        "reasonIfUnscored" to reasonIfUnscored.take(400),
        "clarity" to clarity.coerceIn(0.0, 10.0),
        "depth" to depth.coerceIn(0.0, 10.0),
        "beginner" to beginner.coerceIn(0.0, 10.0),
        "freshness" to freshness.coerceIn(0.0, 10.0),
        "score" to score.coerceIn(0.0, 10.0),
        "summary" to summary.take(400),
        "basis" to basis.take(500),
        "confidence" to if (confidence == "High") "High" else "Limited",
        "videoId" to videoId.take(40),
        "playlistId" to playlistId.take(80),
        "thumb" to thumb.take(500),
        "durationMin" to durationMin.coerceIn(0L, 200_000L),
        "lessonCount" to lessonCount.coerceIn(0L, 5_000L),
        "bookmarked" to bookmarked,
        "scoredAt" to FieldValue.serverTimestamp(),
        "addedAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toRescoreUpdateMap(): Map<String, Any> = mapOf(
        "title" to title.take(240).ifBlank { "Untitled course" },
        "channel" to channel.take(160).ifBlank { "Unknown channel" },
        "url" to url.take(500),
        "type" to if (type == "Playlist") "Playlist" else "Video",
        "category" to CourseCategory.fromLabel(category).label,
        "level" to normalizeLevel(level),
        "language" to language.take(60).ifBlank { "English" },
        "status" to if (status == "unscored") "unscored" else "scored",
        "reasonIfUnscored" to reasonIfUnscored.take(400),
        "clarity" to clarity.coerceIn(0.0, 10.0),
        "depth" to depth.coerceIn(0.0, 10.0),
        "beginner" to beginner.coerceIn(0.0, 10.0),
        "freshness" to freshness.coerceIn(0.0, 10.0),
        "score" to score.coerceIn(0.0, 10.0),
        "summary" to summary.take(400),
        "basis" to basis.take(500),
        "confidence" to if (confidence == "High") "High" else "Limited",
        "videoId" to videoId.take(40),
        "playlistId" to playlistId.take(80),
        "thumb" to thumb.take(500),
        "durationMin" to durationMin.coerceIn(0L, 200_000L),
        "lessonCount" to lessonCount.coerceIn(0L, 5_000L),
        "scoredAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        fun normalizeLevel(raw: String): String = when (raw.trim().lowercase()) {
            "beginner" -> "Beginner"
            "intermediate" -> "Intermediate"
            "advanced" -> "Advanced"
            else -> "All levels"
        }
    }
}

@IgnoreExtraProperties
data class CourseLesson(
    @DocumentId val id: String = "",
    val userId: String = "",
    val courseId: String = "",
    val order: Long = 0L,
    val videoId: String = "",
    val title: String = "",
    val durationMin: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(ownerUid: String, parentCourseId: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "courseId" to parentCourseId,
        "order" to order.coerceIn(0L, 5_000L),
        "videoId" to videoId.take(40),
        "title" to title.take(240).ifBlank { "Lesson ${order + 1}" },
        "durationMin" to durationMin.coerceIn(0L, 20_000L),
        "createdAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class DailyQuota(
    @DocumentId val id: String = "daily",
    val userId: String = "",
    val dateKey: String = "",
    val count: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
