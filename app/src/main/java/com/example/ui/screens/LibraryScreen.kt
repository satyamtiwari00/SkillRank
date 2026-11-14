package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CourseCategory
import com.example.ui.CoursesUiState
import com.example.ui.LibrarySort
import com.example.ui.components.CategorySwitcher
import com.example.ui.components.CourseSummaryCard
import com.example.ui.theme.IbmPlexMonoFontFamily
import com.example.ui.theme.LocalSkillRankColors

@Composable
fun LibraryScreen(
    coursesState: CoursesUiState,
    selectedCategory: CourseCategory,
    selectedSort: LibrarySort,
    onSelectCategory: (CourseCategory) -> Unit,
    onSelectSort: (LibrarySort) -> Unit,
    onCourseClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSkillRankColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Saved YouTube courses for focused study",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.mutedInk
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LibrarySort.entries.forEach { sort ->
                    val active = sort == selectedSort
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (active) MaterialTheme.colorScheme.onBackground
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable { onSelectSort(sort) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("library_sort_${sort.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sort.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) MaterialTheme.colorScheme.background
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
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
                .fillMaxWidth(),
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
                    Text(
                        text = coursesState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(20.dp)
                    )
                }

                is CoursesUiState.Success -> {
                    val saved = remember(coursesState.courses, selectedCategory, selectedSort) {
                        coursesState.courses
                            .filter { it.bookmarked }
                            .filter {
                                selectedCategory == CourseCategory.ALL ||
                                    it.category == selectedCategory.label
                            }
                            .let { list ->
                                when (selectedSort) {
                                    LibrarySort.SCORE -> list.sortedByDescending { it.score }
                                    LibrarySort.RECENT -> list.sortedByDescending { it.addedAt?.seconds ?: 0L }
                                }
                            }
                    }

                    if (saved.isEmpty()) {
                        Surface(
                            modifier = Modifier
                                .padding(20.dp)
                                .widthIn(max = 520.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, semantic.hairline)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "No saved courses yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Open any course from the leaderboard and tap the bookmark icon to keep it in your study library.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = semantic.mutedInk
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("library_list")
                        ) {
                            items(
                                items = saved,
                                key = { it.id }
                            ) { course ->
                                CourseSummaryCard(
                                    course = course,
                                    rank = null,
                                    onClick = { onCourseClick(course.id) },
                                    modifier = Modifier.widthIn(max = 580.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
