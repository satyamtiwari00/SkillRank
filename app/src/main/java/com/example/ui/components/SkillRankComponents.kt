package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Course
import com.example.model.CourseCategory
import com.example.model.ScoreBand
import com.example.ui.theme.IbmPlexMonoFontFamily
import com.example.ui.theme.LocalSkillRankColors
import java.util.Locale

@Composable
fun TenSegmentMeter(
    score: Double,
    isScored: Boolean,
    bandColor: Color,
    modifier: Modifier = Modifier
) {
    val trackColor = LocalSkillRankColors.current.hairline
    val clamped = if (isScored) score.coerceIn(0.0, 10.0) else 0.0

    Canvas(
        modifier = modifier
            .height(8.dp)
            .width(120.dp)
    ) {
        val segments = 10
        val gap = 3.dp.toPx()
        val segWidth = (size.width - gap * (segments - 1)) / segments
        val radius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())

        for (i in 0 until segments) {
            val left = i * (segWidth + gap)
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(left, 0f),
                size = Size(segWidth, size.height),
                cornerRadius = radius
            )
            val fillFraction = (clamped - i).coerceIn(0.0, 1.0).toFloat()
            if (fillFraction > 0f) {
                drawRoundRect(
                    color = bandColor,
                    topLeft = Offset(left, 0f),
                    size = Size(segWidth * fillFraction, size.height),
                    cornerRadius = radius
                )
            }
        }
    }
}

@Composable
fun ScoreBandBadge(
    band: ScoreBand,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSkillRankColors.current
    val fg = semantic.colorForBand(band)
    val bg = semantic.containerForBand(band)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = band.label,
            style = MaterialTheme.typography.labelMedium,
            color = fg
        )
    }
}

@Composable
fun CategorySwitcher(
    selected: CourseCategory,
    onSelect: (CourseCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSkillRankColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CourseCategory.entries.forEach { category ->
            val isSelected = category == selected
            val bg = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surface
            val fg = if (isSelected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
            val border = if (isSelected) MaterialTheme.colorScheme.onBackground else semantic.hairline
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .border(1.dp, border, RoundedCornerShape(12.dp))
                    .clickable { onSelect(category) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("category_chip_${category.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = fg,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun CourseSummaryCard(
    course: Course,
    rank: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSkillRankColors.current
    val band = course.scoreBand()
    val bandColor = semantic.colorForBand(band)
    val isScored = course.status == "scored"
    val scoreText = if (isScored) String.format(Locale.US, "%.1f", course.score) else "--"
    val durationText = formatDuration(course.durationMin)
    val metaLine = buildString {
        append(course.type)
        if (course.type == "Playlist" && course.lessonCount > 0) {
            append(" · ${course.lessonCount} lessons")
        }
        if (durationText.isNotBlank()) {
            append(" · $durationText")
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("course_card_${course.id}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, semantic.hairline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top metadata strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (rank != null) {
                        Text(
                            text = "#${rank.toString().padStart(2, '0')}",
                            fontFamily = IbmPlexMonoFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = metaLine,
                        fontFamily = IbmPlexMonoFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 11.sp,
                        color = semantic.mutedInk
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${course.confidence} evidence",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.mutedInk
                    )
                }
            }

            // Thumbnail + Course title & channel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (course.thumb.isNotBlank()) {
                    AsyncImage(
                        model = course.thumb,
                        contentDescription = "Thumbnail for ${course.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 96.dp, height = 68.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(width = 96.dp, height = 68.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = course.type,
                            style = MaterialTheme.typography.labelSmall,
                            color = semantic.mutedInk
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = course.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${course.channel} · ${course.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.mutedInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (course.summary.isNotBlank()) {
                        Text(
                            text = course.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Hairline divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(semantic.hairline)
            )

            // Bottom score + 10-segment meter + band
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = scoreText,
                        fontFamily = IbmPlexMonoFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 28.sp,
                        color = bandColor
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TenSegmentMeter(
                            score = course.score,
                            isScored = isScored,
                            bandColor = bandColor
                        )
                        Text(
                            text = if (isScored) "${course.level} · ${course.language}" else course.reasonIfUnscored,
                            style = MaterialTheme.typography.bodySmall,
                            color = semantic.mutedInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                ScoreBandBadge(band = band)
            }
        }
    }
}

fun formatDuration(durationMin: Long): String {
    if (durationMin <= 0L) return ""
    val hours = durationMin / 60L
    val mins = durationMin % 60L
    return when {
        hours > 0L && mins > 0L -> "${hours}h ${mins}m"
        hours > 0L -> "${hours}h"
        else -> "${mins}m"
    }
}
