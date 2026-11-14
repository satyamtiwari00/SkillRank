package com.example.data.ai

import android.util.Log
import com.example.data.scoring.ScoringEngine
import com.example.data.scoring.YouTubeTarget
import com.example.data.youtube.YouTubeEvidence
import com.example.model.Course
import com.example.model.CourseCategory
import com.example.model.CourseLesson
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class EvaluatedCourseResult(
    val course: Course,
    val lessons: List<CourseLesson>
)

class CourseEvaluatorAi {

    private val rubricSchema = Schema.obj(
        properties = mapOf(
            "clarity" to Schema.double(description = "Clarity score 0.0 to 10.0 based only on real evidence"),
            "depth" to Schema.double(description = "Depth score 0.0 to 10.0 based only on real evidence"),
            "beginner" to Schema.double(description = "Beginner fit score 0.0 to 10.0 based only on real evidence"),
            "freshness" to Schema.double(description = "Freshness score 0.0 to 10.0 based on published date and topic currency"),
            "summary" to Schema.string(description = "Factual summary in 25 words or fewer"),
            "basis" to Schema.string(description = "One sentence stating exactly which evidence was used"),
            "confidence" to Schema.enumeration(
                values = listOf("High", "Limited"),
                description = "High if rich transcript/chapters/lessons available; Limited if evidence is thin"
            ),
            "category" to Schema.enumeration(
                values = CourseCategory.scorableCategories.map { it.label },
                description = "Primary skill category"
            ),
            "level" to Schema.enumeration(
                values = listOf("Beginner", "Intermediate", "Advanced", "All levels"),
                description = "Target audience level"
            ),
            "language" to Schema.string(description = "Course spoken or caption language, e.g. English"),
            "status" to Schema.enumeration(
                values = listOf("scored", "unscored"),
                description = "Set unscored if there is no usable evidence"
            ),
            "reason_if_unscored" to Schema.string(
                description = "Clear explanation if status is unscored, otherwise empty string"
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = rubricSchema
            }
        )
    }

    suspend fun evaluate(
        userId: String,
        evidence: YouTubeEvidence,
        preserveSavedState: Boolean = false
    ): EvaluatedCourseResult {
        val target = evidence.target
        val isPlaylist = target is YouTubeTarget.Playlist
        val lessonModels = evidence.lessons.map { lesson ->
            CourseLesson(
                id = "lesson_${lesson.order}",
                userId = userId,
                courseId = target.courseId,
                order = lesson.order.toLong(),
                videoId = lesson.videoId,
                title = lesson.title,
                durationMin = lesson.durationMin
            )
        }

        // Strict Honesty Gate 1: Playlist with no accessible lesson list must NEVER be scored
        if (isPlaylist && evidence.lessons.isEmpty()) {
            val unscored = buildUnscoredCourse(
                userId = userId,
                evidence = evidence,
                reason = "Playlist has no accessible public lesson list to evaluate.",
                basis = "Playlist metadata returned zero public lesson items.",
                preserveSavedState = preserveSavedState
            )
            return EvaluatedCourseResult(unscored, emptyList())
        }

        // Strict Honesty Gate 2: Video with zero usable title/description/transcript
        if (evidence.title.isBlank() && evidence.description.isBlank() && evidence.transcriptSnippet.isBlank()) {
            val unscored = buildUnscoredCourse(
                userId = userId,
                evidence = evidence,
                reason = "No usable title, description, or transcript was available for this link.",
                basis = "No public metadata returned from YouTube.",
                preserveSavedState = preserveSavedState
            )
            return EvaluatedCourseResult(unscored, lessonModels)
        }

        val prompt = buildRubricPrompt(evidence)

        val responseText = try {
            withTimeout(60_000L) {
                val response = model.generateContent(prompt)
                response.text
                    ?: throw IllegalStateException("Gemini returned an empty evaluation response.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini scoring timed out", e)
            throw IllegalStateException("Course scoring timed out after 60 seconds. Please try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini scoring failed", e)
            throw IllegalStateException(
                e.localizedMessage ?: "Failed to score course with Firebase AI Logic.",
                e
            )
        }

        val json = JSONObject(responseText)
        val status = if (json.optString("status") == "unscored") "unscored" else "scored"
        val reasonIfUnscored = json.optString("reason_if_unscored").trim()
        val category = CourseCategory.fromLabel(json.optString("category")).label
        val level = Course.normalizeLevel(json.optString("level"))
        val language = json.optString("language").trim().ifBlank { "English" }
        val basis = json.optString("basis").trim().ifBlank {
            if (evidence.sourceNotes.isNotEmpty()) {
                "Scored from ${evidence.sourceNotes.joinToString(", ").lowercase()}."
            } else {
                "Scored from available YouTube metadata."
            }
        }
        val confidence = if (json.optString("confidence") == "High" &&
            (evidence.transcriptSnippet.isNotBlank() || evidence.chapters.isNotEmpty() || evidence.lessons.size >= 3)
        ) {
            "High"
        } else {
            "Limited"
        }

        if (status == "unscored") {
            val unscored = buildUnscoredCourse(
                userId = userId,
                evidence = evidence,
                reason = reasonIfUnscored.ifBlank { "Insufficient course evidence to produce an honest score." },
                basis = basis,
                category = category,
                level = level,
                language = language,
                preserveSavedState = preserveSavedState
            )
            return EvaluatedCourseResult(unscored, lessonModels)
        }

        val clarity = ScoringEngine.clampSubScore(json.optDouble("clarity", 0.0))
        val depth = ScoringEngine.clampSubScore(json.optDouble("depth", 0.0))
        val beginner = ScoringEngine.clampSubScore(json.optDouble("beginner", 0.0))
        val freshness = ScoringEngine.clampSubScore(json.optDouble("freshness", 0.0))

        // Always recompute score deterministically in Kotlin — never trust model totals
        val finalScore = ScoringEngine.computeScore(clarity, depth, beginner, freshness)
        val summary = ScoringEngine.limitSummaryTo25Words(json.optString("summary"))

        val videoId = when (target) {
            is YouTubeTarget.Video -> target.videoId
            is YouTubeTarget.Playlist -> evidence.primaryVideoId
        }
        val playlistId = when (target) {
            is YouTubeTarget.Playlist -> target.playlistId
            is YouTubeTarget.Video -> ""
        }

        val course = Course(
            id = target.courseId,
            userId = userId,
            title = evidence.title.ifBlank { "YouTube Course" },
            channel = evidence.channel.ifBlank { "YouTube Creator" },
            url = target.canonicalUrl,
            type = if (isPlaylist) "Playlist" else "Video",
            category = category,
            level = level,
            language = language,
            status = "scored",
            reasonIfUnscored = "",
            clarity = clarity,
            depth = depth,
            beginner = beginner,
            freshness = freshness,
            score = finalScore,
            summary = summary,
            basis = basis,
            confidence = confidence,
            videoId = videoId,
            playlistId = playlistId,
            thumb = evidence.thumbnailUrl,
            durationMin = evidence.durationMin,
            lessonCount = if (isPlaylist) evidence.lessons.size.toLong() else 1L,
            bookmarked = preserveSavedState
        )

        return EvaluatedCourseResult(course, lessonModels)
    }

    private fun buildUnscoredCourse(
        userId: String,
        evidence: YouTubeEvidence,
        reason: String,
        basis: String,
        category: String = CourseCategory.OTHER_SKILLS.label,
        level: String = "All levels",
        language: String = "English",
        preserveSavedState: Boolean
    ): Course {
        val target = evidence.target
        val isPlaylist = target is YouTubeTarget.Playlist
        return Course(
            id = target.courseId,
            userId = userId,
            title = evidence.title.ifBlank { if (isPlaylist) "Unscored Playlist" else "Unscored Video" },
            channel = evidence.channel.ifBlank { "Unknown channel" },
            url = target.canonicalUrl,
            type = if (isPlaylist) "Playlist" else "Video",
            category = category,
            level = level,
            language = language,
            status = "unscored",
            reasonIfUnscored = reason,
            clarity = 0.0,
            depth = 0.0,
            beginner = 0.0,
            freshness = 0.0,
            score = 0.0,
            summary = "Course could not be scored from available evidence.",
            basis = basis,
            confidence = "Limited",
            videoId = if (target is YouTubeTarget.Video) target.videoId else evidence.primaryVideoId,
            playlistId = if (target is YouTubeTarget.Playlist) target.playlistId else "",
            thumb = evidence.thumbnailUrl,
            durationMin = evidence.durationMin,
            lessonCount = if (isPlaylist) evidence.lessons.size.toLong() else 1L,
            bookmarked = preserveSavedState
        )
    }

    private fun buildRubricPrompt(evidence: YouTubeEvidence): String {
        val lessonsBlock = if (evidence.lessons.isEmpty()) {
            "None (single video)"
        } else {
            evidence.lessons.take(50).joinToString("\n") { l ->
                "${l.order + 1}. ${l.title} (${if (l.durationMin > 0) "${l.durationMin}m" else "duration unknown"})"
            }
        }
        val chaptersBlock = if (evidence.chapters.isEmpty()) {
            "None listed"
        } else {
            evidence.chapters.joinToString("\n")
        }

        return """
            You are SkillRank, a strict, honest evaluator of free YouTube courses.
            Score ONLY from the real evidence provided below. Never invent lessons, durations, or claims.

            Rubric (0.0 to 10.0 each):
            - clarity: structure, progression, chapter organization, and clear explanations shown in evidence.
            - depth: substantive coverage, hands-on depth, edge cases, and total scope relative to topic.
            - beginner: accessibility, prerequisites stated, onboarding smoothness for learners.
            - freshness: recency based on published date (${evidence.publishedAt.ifBlank { "unknown" }}) and modern tooling relevance (current year is 2026).

            Honesty rules:
            1. If evidence is thin (for example, no transcript and minimal description), set confidence to "Limited" and write one honest "basis" sentence stating exactly what evidence was used.
            2. If there is no usable educational evidence at all, set status to "unscored" and give a concrete "reason_if_unscored".
            3. summary must be <= 25 words, factual, sentence case, with no promotional hype.
            4. category must be one of: Video editing, Coding, AI, Agentic AI, Automation, Other skills.

            EVIDENCE:
            Type: ${if (evidence.target is YouTubeTarget.Playlist) "Playlist" else "Video"}
            Title: ${evidence.title}
            Channel: ${evidence.channel}
            Published: ${evidence.publishedAt.ifBlank { "Unknown" }}
            Total Duration (minutes): ${evidence.durationMin}
            Public Stats: views=${evidence.viewCount}, likes=${evidence.likeCount}
            Sources Collected: ${evidence.sourceNotes.joinToString(", ")}

            Chapters:
            $chaptersBlock

            Playlist Lessons:
            $lessonsBlock

            Description:
            ${evidence.description.ifBlank { "No description available." }}

            Transcript / Captions Excerpt:
            ${evidence.transcriptSnippet.ifBlank { "No captions/transcript available." }}
        """.trimIndent()
    }

    private companion object {
        const val TAG = "CourseEvaluatorAi"
    }
}
