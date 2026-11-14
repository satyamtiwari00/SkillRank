package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.CourseRepository
import com.example.model.DailyQuota
import com.example.ui.AddCourseUiState
import com.example.ui.PipelineStage
import com.example.ui.components.CourseSummaryCard
import com.example.ui.theme.IbmPlexMonoFontFamily
import com.example.ui.theme.LocalSkillRankColors

private val SAMPLE_COURSE_LINKS = listOf(
    "Python Full Course (freeCodeCamp)" to "https://www.youtube.com/watch?v=rfscVS0vtbw",
    "Intro to Large Language Models" to "https://www.youtube.com/watch?v=zjkBMFhNj_g",
    "Git & GitHub Crash Course" to "https://www.youtube.com/watch?v=RGOj5yH7evk"
)

@Composable
fun AddCourseScreen(
    initialUrl: String?,
    autoStart: Boolean,
    addState: AddCourseUiState,
    dailyQuota: DailyQuota,
    onEvaluate: (String) -> Unit,
    onReset: () -> Unit,
    onOpenCourse: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSkillRankColors.current
    val clipboard = LocalClipboardManager.current
    var urlInput by remember(initialUrl) { mutableStateOf(initialUrl.orEmpty()) }
    var triggeredAutoStart by remember(initialUrl, autoStart) { mutableStateOf(false) }

    LaunchedEffect(initialUrl, autoStart) {
        if (autoStart && !initialUrl.isNullOrBlank() && !triggeredAutoStart) {
            triggeredAutoStart = true
            onEvaluate(initialUrl)
        }
    }

    val isInProgress = addState is AddCourseUiState.Progress

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 580.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Add course",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Paste or share a YouTube video or playlist link",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.mutedInk
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${dailyQuota.count}/${CourseRepository.DAILY_QUOTA_LIMIT} today",
                        fontFamily = IbmPlexMonoFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, semantic.hairline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        enabled = !isInProgress,
                        label = { Text("YouTube URL") },
                        placeholder = { Text("https://www.youtube.com/watch?v=... or playlist") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Link,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val clip = clipboard.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        urlInput = clip
                                    }
                                },
                                enabled = !isInProgress,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentPaste,
                                    contentDescription = "Paste link from clipboard"
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("course_url_input")
                    )

                    Button(
                        onClick = { onEvaluate(urlInput) },
                        enabled = !isInProgress && urlInput.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("evaluate_course_button")
                    ) {
                        if (isInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Research & score course")
                        }
                    }
                }
            }

            // Live 4-stage pipeline progress card
            if (addState is AddCourseUiState.Progress) {
                PipelineProgressCard(stage = addState.stage, url = addState.url)
            }

            if (addState is AddCourseUiState.Error) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Evaluation stopped",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = addState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (addState is AddCourseUiState.Completed) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (addState.fromCache) {
                                "Loaded from cache (< 90 days)"
                            } else {
                                "Evaluation complete"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = semantic.topPick
                        )
                    }

                    CourseSummaryCard(
                        course = addState.course,
                        rank = null,
                        onClick = { onOpenCourse(addState.course.id) }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onOpenCourse(addState.course.id) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("open_evaluated_course_button")
                        ) {
                            Text("Open course detail")
                        }
                        OutlinedButton(
                            onClick = {
                                urlInput = ""
                                onReset()
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, semantic.hairline),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("evaluate_another_button")
                        ) {
                            Text("Another link")
                        }
                    }
                }
            }

            // Quick-test sample links
            if (!isInProgress) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Try a real YouTube course",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    SAMPLE_COURSE_LINKS.forEachIndexed { idx, (label, sampleUrl) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    urlInput = sampleUrl
                                    onEvaluate(sampleUrl)
                                }
                                .testTag("add_sample_link_$idx"),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, semantic.hairline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                                    .minimumInteractiveComponentSize(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PlayCircleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = sampleUrl,
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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PipelineProgressCard(
    stage: PipelineStage,
    url: String
) {
    val semantic = LocalSkillRankColors.current
    val stages = listOf(
        PipelineStage.FETCHING,
        PipelineStage.READING,
        PipelineStage.SCORING,
        PipelineStage.SAVED
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, semantic.hairline),
        modifier = Modifier.testTag("pipeline_progress_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Scoring...",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = url,
                fontFamily = IbmPlexMonoFontFamily,
                fontSize = 11.sp,
                color = semantic.mutedInk,
                maxLines = 1
            )
            stages.forEach { item ->
                val done = stage.stepIndex > item.stepIndex
                val active = stage == item
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (done) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = semantic.topPick,
                            modifier = Modifier.size(18.dp)
                        )
                    } else if (active) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(semantic.hairline)
                        )
                    }
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (active || done) MaterialTheme.colorScheme.onSurface else semantic.mutedInk
                    )
                }
            }
        }
    }
}
