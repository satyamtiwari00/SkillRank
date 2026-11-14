package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.Course
import com.example.model.CourseLesson
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class CourseRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun sampleCourse(uid: String, id: String): Course = Course(
        id = id,
        userId = uid,
        title = "Jetpack Compose Architecture",
        channel = "Android Developers",
        url = "https://www.youtube.com/watch?v=testCourse01",
        type = "Playlist",
        category = "Coding",
        level = "Intermediate",
        language = "English",
        status = "scored",
        reasonIfUnscored = "",
        clarity = 9.0,
        depth = 8.5,
        beginner = 7.5,
        freshness = 9.0,
        score = 8.6,
        summary = "Structured overview of state hoisting and unidirectional data flow.",
        basis = "Playlist lesson list, descriptions, and official documentation links.",
        confidence = "High",
        videoId = "testCourse01",
        playlistId = "PLtest01",
        thumb = "https://i.ytimg.com/vi/testCourse01/hqdefault.jpg",
        durationMin = 120L,
        lessonCount = 2L,
        bookmarked = false
    )

    @Test
    fun saveCourseWithLessons_authenticatedOwner_persistsAndObserves() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = CourseRepository(firestore, auth)
        val courseId = "yt_pl_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val course = sampleCourse(uid, courseId)
        val lessons = listOf(
            CourseLesson(order = 0L, videoId = "vid_0", title = "Lesson 1: State", durationMin = 20L),
            CourseLesson(order = 1L, videoId = "vid_1", title = "Lesson 2: Side Effects", durationMin = 25L)
        )

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveCourseWithLessons(course, lessons, isUpdate = false)
        }

        val observedCourses = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCourses(uid).first { list -> list.any { it.id == courseId } }
        }
        val stored = observedCourses.first { it.id == courseId }
        assertEquals(8.6, stored.score, 0.01)

        val observedLessons = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeLessons(uid, courseId).first { it.size == 2 }
        }
        assertEquals("Lesson 1: State", observedLessons[0].title)
    }

    @Test
    fun toggleSaved_authenticatedOwner_updatesIsSaved() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = CourseRepository(firestore, auth)
        val courseId = "yt_v_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val course = sampleCourse(uid, courseId).copy(type = "Video", playlistId = "", lessonCount = 1L)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveCourseWithLessons(course, emptyList(), isUpdate = false)
            repository.toggleSaved(course)
        }

        val updated = withTimeout(DEFAULT_TIMEOUT_MS) { repository.getCourse(courseId) }
        assertNotNull(updated)
        assertTrue(updated!!.bookmarked)
    }

    @Test
    fun crossUserAccess_deniedByRules() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CourseRepository(firestore, auth)
        val courseId = "yt_v_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveCourseWithLessons(sampleCourse(aliceUid, courseId), emptyList(), isUpdate = false)
        }

        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid).collection("courses")
                    .document(courseId).get().await()
            }
            fail("Expected PERMISSION_DENIED for cross-user read")
        } catch (e: Exception) {
            val fsException = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsException!!.code)
        }
    }

    @Test
    fun observeCourses_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = CourseRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeCourses(uid).first()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val fsException = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsException!!.code)
        }
    }

    @Test
    fun consumeDailyQuota_incrementsCleanly() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = CourseRepository(firestore, auth)
        val dateKey = "2026-11-${(10 + (System.currentTimeMillis() % 18)).toString().padStart(2, '0')}"

        val count = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.consumeDailyEvaluationQuota(dateKey)
        }
        assertTrue(count >= 1L)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@skillrank.test"
        const val BOB_EMAIL = "bob@skillrank.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
