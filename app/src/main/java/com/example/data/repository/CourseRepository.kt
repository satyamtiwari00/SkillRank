package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.model.Course
import com.example.model.CourseLesson
import com.example.model.DailyQuota
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DailyQuotaExceededException(val limit: Int) :
    IllegalStateException("Daily evaluation limit reached ($limit courses per day). Try again tomorrow.")

class CourseRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeCourses(userId: String): Flow<List<Course>> = flow {
        val path = "users/$userId/courses"
        emitAll(
            db.collection("users").document(userId).collection("courses")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Course::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCourse(userId: String, courseId: String): Flow<Course?> = flow {
        val docRef = db.collection("users").document(userId).collection("courses").document(courseId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            Course::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeLessons(userId: String, courseId: String): Flow<List<CourseLesson>> = flow {
        val colRef = db.collection("users").document(userId)
            .collection("courses").document(courseId)
            .collection("lessons")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CourseLesson::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.order }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun getCourse(courseId: String): Course? {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("courses").document(courseId)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                snap.toObject(Course::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun saveCourseWithLessons(
        course: Course,
        lessons: List<CourseLesson>,
        isUpdate: Boolean
    ) {
        val uid = requireUserId()
        require(course.id.isNotBlank()) { "Course ID must not be blank" }
        val docRef = db.collection("users").document(uid).collection("courses").document(course.id)

        try {
            if (isUpdate) {
                docRef.update(course.toRescoreUpdateMap()).await()
            } else {
                docRef.set(course.toCreateMap(uid)).await()
            }

            if (lessons.isNotEmpty()) {
                // Write parent first (above) so Parent Gate exists() check passes for lesson subcollection
                val lessonsCol = docRef.collection("lessons")
                // Replace existing lessons cleanly in batches of <= 450
                val existingLessons = lessonsCol.get().await()
                if (!existingLessons.isEmpty) {
                    val deleteBatch = db.batch()
                    existingLessons.documents.forEach { deleteBatch.delete(it.reference) }
                    deleteBatch.commit().await()
                }
                lessons.chunked(450).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { lesson ->
                        val lessonDoc = lessonsCol.document("lesson_${lesson.order}")
                        batch.set(lessonDoc, lesson.toCreateMap(uid, course.id))
                    }
                    batch.commit().await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (isUpdate) OperationType.UPDATE else OperationType.CREATE,
                docRef.path
            )
            throw e
        }
    }

    suspend fun toggleSaved(course: Course) {
        val uid = requireUserId()
        require(course.id.isNotBlank()) { "Course ID must not be blank" }
        val docRef = db.collection("users").document(uid).collection("courses").document(course.id)
        try {
            docRef.update(
                mapOf(
                    "bookmarked" to !course.bookmarked,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteCourse(courseId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("courses").document(courseId)
        try {
            val lessons = docRef.collection("lessons").get().await()
            if (!lessons.isEmpty) {
                val batch = db.batch()
                lessons.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeDailyQuota(userId: String): Flow<DailyQuota> = flow {
        val docRef = db.collection("users").document(userId).collection("quota").document("daily")
        val today = currentUtcDateKey()
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    val quota = if (snapshot.exists()) {
                        snapshot.toObject(DailyQuota::class.java)
                    } else null
                    if (quota != null && quota.dateKey == today) {
                        quota
                    } else {
                        DailyQuota(id = "daily", userId = userId, dateKey = today, count = 0L)
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    emit(DailyQuota(id = "daily", userId = userId, dateKey = today, count = 0L))
                }
        )
    }

    suspend fun consumeDailyEvaluationQuota(todayKey: String = currentUtcDateKey()): Long {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("quota").document("daily")
        try {
            val snap = docRef.get().await()
            val existing = if (snap.exists()) snap.toObject(DailyQuota::class.java) else null
            val currentCount = if (existing != null && existing.dateKey == todayKey) {
                existing.count
            } else {
                0L
            }
            if (currentCount >= DAILY_QUOTA_LIMIT) {
                throw DailyQuotaExceededException(DAILY_QUOTA_LIMIT)
            }
            val nextCount = currentCount + 1L
            val payload = mapOf(
                "userId" to uid,
                "dateKey" to todayKey,
                "count" to nextCount,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "dateKey" to todayKey,
                        "count" to nextCount,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(payload).await()
            }
            return nextCount
        } catch (e: DailyQuotaExceededException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    companion object {
        const val DAILY_QUOTA_LIMIT = 20

        fun currentUtcDateKey(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date())
        }
    }
}
