package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.data.scoring.ScoringEngine
import com.example.model.Course
import com.example.model.CourseLesson
import com.example.ui.components.ScoreBandBadge
import com.example.ui.components.TenSegmentMeter
import com.example.ui.components.formatDuration
import com.example.ui.theme.IbmPlexMonoFontFamily
import com.example.ui.theme.LocalSkillRankColors
import java.util.Locale

@Composable
fun CourseDetailScreen(
    course: Course?,
    lessons: List<CourseLesson>,
    isRescoring: Boolean,
    actionMessage: String?,
    reduceMotion: Boolean,
    onBack: () -> Unit,
    onToggleSave: (Course) -> Unit,
    onRescore: (Course) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val semantic = LocalSkillRankColors.current

    var isPlayerActive by remember(course?.id) { mutableStateOf(false) }
    var activeVideoId by remember(course?.id, lessons) {
        mutableStateOf(
            course?.videoId?.takeIf { it.isNotBlank() }
                ?: lessons.firstOrNull()?.videoId.orEmpty()
        )
    }

    LaunchedEffect(course?.videoId, lessons) {
        if (activeVideoId.isBlank()) {
            activeVideoId = course?.videoId?.takeIf { it.isNotBlank() }
                ?: lessons.firstOrNull()?.videoId.orEmpty()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top navigation bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("detail_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back to list"
                    )
                }
                Text(
                    text = "Course evaluation",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (course != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onToggleSave(course) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("detail_save_button")
                    ) {
                        Icon(
                            imageVector = if (course.bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (course.bookmarked) "Remove from library" else "Save to library",
                            tint = if (course.bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(
                        onClick = { onDelete(course.id) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("detail_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete course",
                            tint = semantic.mutedInk
                        )
                    }
                }
            }
        }

        if (course == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            }
            return
        }

        val band = course.scoreBand()
        val bandColor = semantic.colorForBand(band)
        val isScored = course.status == "scored"

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero Thumbnail -> Embedded YouTube IFrame Player
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, semantic.hairline, RoundedCornerShape(16.dp))
                ) {
                    if (isPlayerActive && activeVideoId.isNotBlank()) {
                        YouTubeIFramePlayer(
                            videoId = activeVideoId,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val thumbUrl = if (activeVideoId.isNotBlank()) {
                            ScoringEngine.buildThumbnailUrl(activeVideoId)
                        } else {
                            course.thumb
                        }
                        if (thumbUrl.isNotBlank()) {
                            AsyncImage(
                                model = thumbUrl,
                                contentDescription = "Video thumbnail for ${course.title}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (activeVideoId.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.28f))
                                    .clickable { isPlayerActive = true }
                                    .testTag("activate_youtube_player_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, semantic.hairline)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Play inside app",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Title & metadata
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = course.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = buildString {
                            append("${course.channel} · ${course.category} · ${course.level} · ${course.language}")
                            val dur = formatDuration(course.durationMin)
                            if (dur.isNotBlank()) append(" · $dur")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.mutedInk
                    )
                }

                // Score summary card + staggered sub-score bars
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, semantic.hairline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isScored) String.format(Locale.US, "%.1f", course.score) else "--",
                                    fontFamily = IbmPlexMonoFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 36.sp,
                                    color = bandColor
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TenSegmentMeter(
                                        score = course.score,
                                        isScored = isScored,
                                        bandColor = bandColor
                                    )
                                    Text(
                                        text = "${course.confidence} confidence",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = semantic.mutedInk
                                    )
                                }
                            }
                            ScoreBandBadge(band = band)
                        }

                        if (!isScored) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = course.reasonIfUnscored.ifBlank {
                                        "Insufficient public evidence to score this course."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(semantic.hairline)
                            )

                            val subScores = listOf(
                                Triple("Clarity (30%)", course.clarity, bandColor),
                                Triple("Depth (30%)", course.depth, bandColor),
                                Triple("Beginner fit (20%)", course.beginner, bandColor),
                                Triple("Freshness (20%)", course.freshness, bandColor)
                            )
                            subScores.forEachIndexed { idx, (label, value, barColor) ->
                                AnimatedSubScoreRow(
                                    label = label,
                                    value = value,
                                    barColor = barColor,
                                    delayMillis = idx * 90,
                                    reduceMotion = reduceMotion
                                )
                            }
                        }

                        if (course.summary.isNotBlank()) {
                            Text(
                                text = course.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (course.basis.isNotBlank()) {
                            Text(
                                text = "Evidence basis: ${course.basis}",
                                style = MaterialTheme.typography.bodySmall,
                                color = semantic.mutedInk
                            )
                        }
                    }
                }

                if (actionMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = actionMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Actions: Watch on YouTube & Re-score
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val targetUrl = if (activeVideoId.isNotBlank() && course.type == "Video") {
                                "https://www.youtube.com/watch?v=$activeVideoId"
                            } else {
                                course.url
                            }
                            if (ScoringEngine.isSafeYouTubeHttpsUrl(targetUrl)) {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                runCatching { context.startActivity(intent) }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("watch_on_youtube_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Watch on YouTube")
                    }

                    OutlinedButton(
                        onClick = { onRescore(course) },
                        enabled = !isRescoring,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (course.isOlderThan90Days()) MaterialTheme.colorScheme.primary else semantic.hairline
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("detail_rescore_button")
                    ) {
                        if (isRescoring) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(if (course.isOlderThan90Days()) "Refresh (90d+)" else "Re-score")
                        }
                    }
                }

                // Playlist Lessons Section
                if (lessons.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Playlist lessons (${lessons.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        lessons.forEach { lesson ->
                            val isPlayingThis = isPlayerActive && activeVideoId == lesson.videoId
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        activeVideoId = lesson.videoId
                                        isPlayerActive = true
                                    }
                                    .testTag("lesson_item_${lesson.order}"),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isPlayingThis) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isPlayingThis) MaterialTheme.colorScheme.primary else semantic.hairline
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                        .minimumInteractiveComponentSize(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = (lesson.order + 1).toString().padStart(2, '0'),
                                        fontFamily = IbmPlexMonoFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp,
                                        color = if (isPlayingThis) MaterialTheme.colorScheme.primary else semantic.mutedInk
                                    )
                                    Text(
                                        text = lesson.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (lesson.durationMin > 0L) {
                                        Text(
                                            text = "${lesson.durationMin}m",
                                            fontFamily = IbmPlexMonoFontFamily,
                                            fontSize = 12.sp,
                                            color = semantic.mutedInk
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AnimatedSubScoreRow(
    label: String,
    value: Double,
    barColor: androidx.compose.ui.graphics.Color,
    delayMillis: Int,
    reduceMotion: Boolean
) {
    val semantic = LocalSkillRankColors.current
    val targetFraction = (value / 10.0).coerceIn(0.0, 1.0).toFloat()
    val progress = remember { Animatable(if (reduceMotion) targetFraction else 0f) }

    LaunchedEffect(targetFraction, reduceMotion) {
        if (reduceMotion) {
            progress.snapTo(targetFraction)
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = targetFraction,
                animationSpec = tween(
                    durationMillis = 380,
                    delayMillis = delayMillis,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = String.format(Locale.US, "%.1f", value),
                fontFamily = IbmPlexMonoFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(semantic.hairline)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.value)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeIFramePlayer(
    videoId: String,
    modifier: Modifier = Modifier
) {
    val embedHtml = remember(videoId) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
          <style>
            html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #161513; overflow: hidden; }
            iframe { border: 0; width: 100%; height: 100%; }
          </style>
        </head>
        <body>
          <iframe
            src="https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&rel=0"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
            allowfullscreen>
          </iframe>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val host = request?.url?.host?.lowercase().orEmpty()
                        return !(host.endsWith("youtube.com") ||
                            host.endsWith("youtube-nocookie.com") ||
                            host.endsWith("youtu.be"))
                    }
                }
                loadDataWithBaseURL("https://www.youtube.com", embedHtml, "text/html", "utf-8", null)
            }
        },
        update = { webView ->
            val currentTag = webView.tag as? String
            if (currentTag != videoId) {
                webView.tag = videoId
                webView.loadDataWithBaseURL("https://www.youtube.com", embedHtml, "text/html", "utf-8", null)
            }
        }
    )
}
