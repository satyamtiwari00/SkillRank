package com.example.ui.screens

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.example.model.Course
import com.example.model.CourseCategory
import com.example.model.ScoreBand
import com.example.ui.CoursesUiState
import com.example.ui.components.CategorySwitcher
import com.example.ui.components.CourseSummaryCard
import com.example.ui.theme.IbmPlexMonoFontFamily
import com.example.ui.theme.LocalSkillRankColors
import kotlin.math.abs

private data class QuickTestCourseLink(
    val title: String,
    val category: String,
    val url: String
)

private val QUICK_TEST_LINKS = listOf(
    QuickTestCourseLink(
        title = "Python Full Course for Beginners",
        category = "Coding",
        url = "https://www.youtube.com/watch?v=rfscVS0vtbw"
    ),
    QuickTestCourseLink(
        title = "Intro to Large Language Models",
        category = "AI",
        url = "https://www.youtube.com/watch?v=zjkBMFhNj_g"
    ),
    QuickTestCourseLink(
        title = "DaVinci Resolve Complete Beginner Guide",
        category = "Video editing",
        url = "https://www.youtube.com/watch?v=EEKS_3V4x0k"
    )
)

@Composable
fun HomeScreen(
    coursesState: CoursesUiState,
    selectedCategory: CourseCategory,
    reduceMotionPref: Boolean,
    onSelectCategory: (CourseCategory) -> Unit,
    onCourseClick: (String) -> Unit,
    onAddClick: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val semantic = LocalSkillRankColors.current
    val systemAnimatorScale = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
    }
    val reduceMotion = reduceMotionPref || systemAnimatorScale == 0f

    // Forward dolly-in depth transition when switching categories
    val dollyScale = remember { Animatable(1f) }
    val dollyAlpha = remember { Animatable(1f) }
    var firstCategoryRender by remember { mutableStateOf(true) }

    LaunchedEffect(selectedCategory, reduceMotion) {
        if (firstCategoryRender) {
            firstCategoryRender = false
            return@LaunchedEffect
        }
        if (reduceMotion) {
            dollyScale.snapTo(1f)
            dollyAlpha.snapTo(1f)
        } else {
            dollyScale.snapTo(0.92f)
            dollyAlpha.snapTo(0.65f)
            dollyScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
            )
            dollyAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "SkillRank",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (reduceMotion) "Ranked leaderboard (flat view)" else "3D ranked depth tunnel",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.mutedInk
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (coursesState is CoursesUiState.Success) {
                    val count = if (selectedCategory == CourseCategory.ALL) {
                        coursesState.courses.size
                    } else {
                        coursesState.courses.count { it.category == selectedCategory.label }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "$count ranked",
                            fontFamily = IbmPlexMonoFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                IconButton(
                    onClick = { onAddClick(null) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("home_add_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Add YouTube course",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        CategorySwitcher(
            selected = selectedCategory,
            onSelect = onSelectCategory
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = dollyScale.value
                    scaleY = dollyScale.value
                    alpha = dollyAlpha.value
                },
            contentAlignment = Alignment.Center
        ) {
            when (coursesState) {
                is CoursesUiState.Loading -> {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }

                is CoursesUiState.Error -> {
                    ErrorNoticeCard(
                        message = coursesState.message,
                        onAddClick = { onAddClick(null) }
                    )
                }

                is CoursesUiState.Success -> {
                    val filtered = remember(coursesState.courses, selectedCategory) {
                        if (selectedCategory == CourseCategory.ALL) {
                            coursesState.courses
                        } else {
                            coursesState.courses.filter { it.category == selectedCategory.label }
                        }
                    }

                    if (filtered.isEmpty()) {
                        EmptyLeaderboardView(
                            selectedCategory = selectedCategory,
                            hasAnyCourses = coursesState.courses.isNotEmpty(),
                            onResetCategory = { onSelectCategory(CourseCategory.ALL) },
                            onQuickTestClick = { url -> onAddClick(url) },
                            onPasteClick = { onAddClick(null) }
                        )
                    } else if (reduceMotion) {
                        FlatLeaderboardList(
                            courses = filtered,
                            onCourseClick = onCourseClick
                        )
                    } else {
                        Tunnel3DLeaderboardList(
                            courses = filtered,
                            onCourseClick = onCourseClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Tunnel3DLeaderboardList(
    courses: List<Course>,
    onCourseClick: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current.density

    val focusedIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) return@derivedStateOf -1
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo.minByOrNull { item ->
                val itemCenter = item.offset + item.size / 2f
                abs(itemCenter - viewportCenter)
            }?.index ?: -1
        }
    }

    var lastHapticIndex by remember { mutableStateOf(-1) }
    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0 && lastHapticIndex != -1 && focusedIndex != lastHapticIndex) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (focusedIndex >= 0) {
            lastHapticIndex = focusedIndex
        }
    }

    LazyColumn(
        state = listState,
        flingBehavior = snapFlingBehavior,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_3d_tunnel_list")
    ) {
        itemsIndexed(
            items = courses,
            key = { _, item -> item.id }
        ) { index, course ->
            CourseSummaryCard(
                course = course,
                rank = index + 1,
                onClick = { onCourseClick(course.id) },
                modifier = Modifier
                    .widthIn(max = 580.dp)
                    .graphicsLayer {
                        val layoutInfo = listState.layoutInfo
                        val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                        if (itemInfo != null && layoutInfo.viewportEndOffset > 0) {
                            val viewportCenter =
                                (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                            val itemCenter = itemInfo.offset + itemInfo.size / 2f
                            val halfHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2f
                            val normOffset = ((itemCenter - viewportCenter) / halfHeight.coerceAtLeast(1f))
                                .coerceIn(-1.4f, 1.4f)
                            val absOffset = abs(normOffset)
                            val depthFactor = (absOffset / 1.25f).coerceIn(0f, 1f)

                            // Perspective ~1000dp equivalent
                            cameraDistance = 16f * density
                            // Cards above/below rotate up to 25 degrees along X axis
                            rotationX = (-normOffset * 25f).coerceIn(-25f, 25f)
                            // Top picks sit slightly closer to camera at focus
                            val focusScale = if (course.scoreBand() == ScoreBand.TOP_PICK) 1.03f else 1.0f
                            val zScale = lerp(focusScale, 0.72f, depthFactor)
                            scaleX = zScale
                            scaleY = zScale
                            // Perspective compression along Y
                            translationY = -normOffset * 28f * density
                            // Dim cards away from the focus slot
                            alpha = lerp(1.0f, 0.44f, depthFactor)
                        }
                    }
            )
        }
    }
}

@Composable
private fun FlatLeaderboardList(
    courses: List<Course>,
    onCourseClick: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_flat_list")
    ) {
        itemsIndexed(
            items = courses,
            key = { _, item -> item.id }
        ) { index, course ->
            CourseSummaryCard(
                course = course,
                rank = index + 1,
                onClick = { onCourseClick(course.id) },
                modifier = Modifier.widthIn(max = 580.dp)
            )
        }
    }
}

@Composable
private fun EmptyLeaderboardView(
    selectedCategory: CourseCategory,
    hasAnyCourses: Boolean,
    onResetCategory: () -> Unit,
    onQuickTestClick: (String) -> Unit,
    onPasteClick: () -> Unit
) {
    val semantic = LocalSkillRankColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 540.dp)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, semantic.hairline)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (hasAnyCourses) {
                        "No ${selectedCategory.label} courses ranked yet"
                    } else {
                        "Your ranked course tunnel is empty"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (hasAnyCourses) {
                        "Switch back to All categories or evaluate a new YouTube course in this topic."
                    } else {
                        "Share any free YouTube course or playlist to SkillRank, or test the real scoring pipeline with one of the links below."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantic.mutedInk
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onPasteClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("empty_add_course_button")
                    ) {
                        Text("Paste a YouTube link")
                    }
                    if (hasAnyCourses) {
                        Button(
                            onClick = onResetCategory,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Show all")
                        }
                    }
                }
            }
        }

        if (!hasAnyCourses) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Quick-test YouTube links",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                QUICK_TEST_LINKS.forEachIndexed { idx, sample ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onQuickTestClick(sample.url) }
                            .testTag("quick_test_chip_$idx"),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, semantic.hairline)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .minimumInteractiveComponentSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PlayCircleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sample.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${sample.category} · ${sample.url}",
                                    fontFamily = IbmPlexMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = semantic.mutedInk,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorNoticeCard(
    message: String,
    onAddClick: () -> Unit
) {
    val semantic = LocalSkillRankColors.current
    Surface(
        modifier = Modifier
            .padding(20.dp)
            .widthIn(max = 520.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Could not load leaderboard",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = semantic.mutedInk
            )
            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Add a course")
            }
        }
    }
}
