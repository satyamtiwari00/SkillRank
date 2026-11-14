package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.model.ScoreBand

@Immutable
data class SkillRankSemanticColors(
    val hairline: Color,
    val mutedInk: Color,
    val topPick: Color,
    val topPickContainer: Color,
    val recommended: Color,
    val recommendedContainer: Color,
    val mixed: Color,
    val mixedContainer: Color,
    val unscored: Color,
    val unscoredContainer: Color
) {
    fun colorForBand(band: ScoreBand): Color = when (band) {
        ScoreBand.TOP_PICK -> topPick
        ScoreBand.RECOMMENDED -> recommended
        ScoreBand.MIXED -> mixed
        ScoreBand.UNSCORED -> unscored
    }

    fun containerForBand(band: ScoreBand): Color = when (band) {
        ScoreBand.TOP_PICK -> topPickContainer
        ScoreBand.RECOMMENDED -> recommendedContainer
        ScoreBand.MIXED -> mixedContainer
        ScoreBand.UNSCORED -> unscoredContainer
    }
}

val LocalSkillRankColors = staticCompositionLocalOf {
    SkillRankSemanticColors(
        hairline = LightHairline,
        mutedInk = LightMutedInk,
        topPick = LightTopPick,
        topPickContainer = LightTopPickContainer,
        recommended = LightRecommended,
        recommendedContainer = LightRecommendedContainer,
        mixed = LightMixed,
        mixedContainer = LightMixedContainer,
        unscored = LightMutedInk,
        unscoredContainer = LightSurfaceVariant
    )
}

private val LightColorScheme = lightColorScheme(
    primary = LightCobalt,
    onPrimary = LightCard,
    primaryContainer = LightRecommendedContainer,
    onPrimaryContainer = LightCobalt,
    secondary = LightMutedInk,
    onSecondary = LightCard,
    background = LightBackground,
    onBackground = LightInk,
    surface = LightCard,
    onSurface = LightInk,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightMutedInk,
    outline = LightHairline,
    outlineVariant = LightHairline,
    error = LightError,
    onError = LightCard
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkCobalt,
    onPrimary = DarkBackground,
    primaryContainer = DarkRecommendedContainer,
    onPrimaryContainer = DarkCobalt,
    secondary = DarkMutedInk,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkCard,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkMutedInk,
    outline = DarkHairline,
    outlineVariant = DarkHairline,
    error = DarkError,
    onError = DarkBackground
)

private val SkillRankShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun SkillRankTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (darkTheme) {
        SkillRankSemanticColors(
            hairline = DarkHairline,
            mutedInk = DarkMutedInk,
            topPick = DarkTopPick,
            topPickContainer = DarkTopPickContainer,
            recommended = DarkRecommended,
            recommendedContainer = DarkRecommendedContainer,
            mixed = DarkMixed,
            mixedContainer = DarkMixedContainer,
            unscored = DarkMutedInk,
            unscoredContainer = DarkSurfaceVariant
        )
    } else {
        SkillRankSemanticColors(
            hairline = LightHairline,
            mutedInk = LightMutedInk,
            topPick = LightTopPick,
            topPickContainer = LightTopPickContainer,
            recommended = LightRecommended,
            recommendedContainer = LightRecommendedContainer,
            mixed = LightMixed,
            mixedContainer = LightMixedContainer,
            unscored = LightMutedInk,
            unscoredContainer = LightSurfaceVariant
        )
    }

    CompositionLocalProvider(LocalSkillRankColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SkillRankTypography,
            shapes = SkillRankShapes,
            content = content
        )
    }
}
