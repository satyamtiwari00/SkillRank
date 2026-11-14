package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.CourseEvaluatorAi
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.PreferencesRepository
import com.example.data.preferences.ThemePreference
import com.example.data.repository.CourseRepository
import com.example.data.repository.DailyQuotaExceededException
import com.example.data.scoring.ScoringEngine
import com.example.data.youtube.YouTubeEvidenceFetcher
import com.example.model.Course
import com.example.model.CourseCategory
import com.example.model.CourseLesson
import com.example.model.DailyQuota
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CoursesUiState {
    data object Loading : CoursesUiState
    data class Success(val courses: List<Course>) : CoursesUiState
    data class Error(val message: String) : CoursesUiState
}

enum class PipelineStage(val label: String, val stepIndex: Int) {
    IDLE("Ready", 0),
    FETCHING("Fetching YouTube metadata", 1),
    READING("Reading lessons & transcript", 2),
    SCORING("Scoring against rubric", 3),
    SAVED("Saved to SkillRank", 4)
}

sealed interface AddCourseUiState {
    data object Idle : AddCourseUiState
    data class Progress(
        val stage: PipelineStage,
        val url: String
    ) : AddCourseUiState
    data class Completed(
        val course: Course,
        val fromCache: Boolean
    ) : AddCourseUiState
    data class Error(
        val message: String,
        val url: String
    ) : AddCourseUiState
}

enum class LibrarySort(val label: String) {
    SCORE("Highest score"),
    RECENT("Recently added")
}

class SkillRankViewModel(
    private val courseRepository: CourseRepository,
    private val preferencesRepository: PreferencesRepository,
    private val youtubeFetcher: YouTubeEvidenceFetcher,
    private val evaluatorAi: CourseEvaluatorAi,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow(CourseCategory.ALL)
    val selectedCategory: StateFlow<CourseCategory> = _selectedCategory.asStateFlow()

    private val _libraryCategory = MutableStateFlow(CourseCategory.ALL)
    val libraryCategory: StateFlow<CourseCategory> = _libraryCategory.asStateFlow()

    private val _librarySort = MutableStateFlow(LibrarySort.SCORE)
    val librarySort: StateFlow<LibrarySort> = _librarySort.asStateFlow()

    private val _addState = MutableStateFlow<AddCourseUiState>(AddCourseUiState.Idle)
    val addState: StateFlow<AddCourseUiState> = _addState.asStateFlow()

    private val _activeCourseId = MutableStateFlow<String?>(null)

    private val _isRescoring = MutableStateFlow(false)
    val isRescoring: StateFlow<Boolean> = _isRescoring.asStateFlow()

    private val _detailActionMessage = MutableStateFlow<String?>(null)
    val detailActionMessage: StateFlow<String?> = _detailActionMessage.asStateFlow()

    val hasYouTubeApiKey: Boolean = youtubeFetcher.hasConfiguredApiKey()

    val preferences: StateFlow<AppPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = AppPreferences()
        )

    val dailyQuota: StateFlow<DailyQuota> = courseRepository.observeDailyQuota(currentUserId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = DailyQuota(userId = currentUserId, count = 0L)
        )

    val coursesState: StateFlow<CoursesUiState> = courseRepository.observeCourses(currentUserId)
        .map<List<Course>, CoursesUiState> { list ->
            val sorted = list.sortedWith(
                compareByDescending<Course> { it.status == "scored" }
                    .thenByDescending { it.score }
                    .thenByDescending { it.addedAt?.seconds ?: 0L }
            )
            CoursesUiState.Success(sorted)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing courses", rootError)
            emit(CoursesUiState.Error(rootError.message ?: "Failed to load ranked courses."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CoursesUiState.Loading
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeCourse: StateFlow<Course?> = _activeCourseId
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(null)
            else courseRepository.observeCourse(currentUserId, id)
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Error observing course $id", error)
                    emit(null)
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeCourseLessons: StateFlow<List<CourseLesson>> = _activeCourseId
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(emptyList())
            else courseRepository.observeLessons(currentUserId, id)
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Error observing lessons for $id", error)
                    emit(emptyList())
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    fun selectCategory(category: CourseCategory) {
        _selectedCategory.value = category
    }

    fun selectLibraryCategory(category: CourseCategory) {
        _libraryCategory.value = category
    }

    fun selectLibrarySort(sort: LibrarySort) {
        _librarySort.value = sort
    }

    fun setActiveCourse(courseId: String) {
        _activeCourseId.value = courseId
        _detailActionMessage.value = null
    }

    fun clearDetailMessage() {
        _detailActionMessage.value = null
    }

    fun resetAddState() {
        _addState.value = AddCourseUiState.Idle
    }

    fun addOrEvaluateCourse(rawInput: String, forceRescore: Boolean = false) {
        val target = ScoringEngine.parseYouTubeTarget(rawInput)
        if (target == null) {
            _addState.value = AddCourseUiState.Error(
                message = "Please enter a valid https://youtube.com or https://youtu.be video or playlist link.",
                url = rawInput
            )
            return
        }

        viewModelScope.launch {
            try {
                _addState.value = AddCourseUiState.Progress(PipelineStage.FETCHING, target.canonicalUrl)

                // Check Firestore cache by normalized courseId
                val existing = courseRepository.getCourse(target.courseId)
                if (existing != null && !forceRescore && !existing.isOlderThan90Days()) {
                    _addState.value = AddCourseUiState.Completed(existing, fromCache = true)
                    return@launch
                }

                // Enforce 20 courses/day rate limit before new AI evaluation
                courseRepository.consumeDailyEvaluationQuota()

                val evidence = youtubeFetcher.fetchEvidence(target)
                _addState.value = AddCourseUiState.Progress(PipelineStage.READING, target.canonicalUrl)

                // Slight transition pause is unnecessary; proceed straight to AI scoring
                _addState.value = AddCourseUiState.Progress(PipelineStage.SCORING, target.canonicalUrl)
                val evaluated = evaluatorAi.evaluate(
                    userId = currentUserId,
                    evidence = evidence,
                    preserveSavedState = existing?.bookmarked ?: false
                )

                courseRepository.saveCourseWithLessons(
                    course = evaluated.course,
                    lessons = evaluated.lessons,
                    isUpdate = existing != null
                )

                _addState.value = AddCourseUiState.Progress(PipelineStage.SAVED, target.canonicalUrl)
                val savedCourse = courseRepository.getCourse(evaluated.course.id) ?: evaluated.course
                _addState.value = AddCourseUiState.Completed(savedCourse, fromCache = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: DailyQuotaExceededException) {
                _addState.value = AddCourseUiState.Error(
                    message = e.message ?: "Daily evaluation limit reached.",
                    url = rawInput
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to evaluate YouTube course", e)
                _addState.value = AddCourseUiState.Error(
                    message = e.localizedMessage ?: "Failed to evaluate this YouTube link.",
                    url = rawInput
                )
            }
        }
    }

    fun rescoreActiveCourse(course: Course) {
        if (_isRescoring.value) return
        val target = ScoringEngine.parseYouTubeTarget(course.url) ?: return
        viewModelScope.launch {
            _isRescoring.value = true
            _detailActionMessage.value = null
            try {
                courseRepository.consumeDailyEvaluationQuota()
                val evidence = youtubeFetcher.fetchEvidence(target)
                val evaluated = evaluatorAi.evaluate(
                    userId = currentUserId,
                    evidence = evidence,
                    preserveSavedState = course.bookmarked
                )
                courseRepository.saveCourseWithLessons(
                    course = evaluated.course,
                    lessons = evaluated.lessons,
                    isUpdate = true
                )
                _detailActionMessage.value = "Course re-scored from latest YouTube evidence."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Re-score failed", e)
                _detailActionMessage.value = e.localizedMessage ?: "Re-score failed."
            } finally {
                _isRescoring.value = false
            }
        }
    }

    fun toggleSaved(course: Course) {
        viewModelScope.launch {
            try {
                courseRepository.toggleSaved(course)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle bookmark", e)
                _detailActionMessage.value = e.localizedMessage ?: "Could not update library bookmark."
            }
        }
    }

    fun deleteCourse(courseId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                courseRepository.deleteCourse(courseId)
                onDeleted()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete course", e)
                _detailActionMessage.value = e.localizedMessage ?: "Could not remove course."
            }
        }
    }

    fun setThemePreference(theme: ThemePreference) {
        viewModelScope.launch {
            preferencesRepository.setThemePreference(theme)
        }
    }

    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setReduceMotion(enabled)
        }
    }

    private companion object {
        const val TAG = "SkillRankVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
