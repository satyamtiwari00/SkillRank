package com.example.data.scoring

import com.example.model.ScoreBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringEngineUnitTest {

    @Test
    fun computeScore_appliesExactWeightsAndHalfUpRounding() {
        // 0.30*9.2 + 0.30*8.5 + 0.20*7.8 + 0.20*8.1 = 2.76 + 2.55 + 1.56 + 1.62 = 8.49 -> 8.5
        val score = ScoringEngine.computeScore(
            clarity = 9.2,
            depth = 8.5,
            beginner = 7.8,
            freshness = 8.1
        )
        assertEquals(8.5, score, 0.0001)
        assertEquals(ScoreBand.TOP_PICK, ScoringEngine.bandFor(score, "scored"))

        // 0.30*7 + 0.30*7 + 0.20*7 + 0.20*7 = 7.0
        assertEquals(ScoreBand.RECOMMENDED, ScoringEngine.bandFor(7.0, "scored"))
        assertEquals(ScoreBand.MIXED, ScoringEngine.bandFor(6.9, "scored"))
        assertEquals(ScoreBand.UNSCORED, ScoringEngine.bandFor(9.5, "unscored"))
    }

    @Test
    fun computeScore_clampsOutOfBoundsInputs() {
        val score = ScoringEngine.computeScore(
            clarity = 15.0,
            depth = -2.0,
            beginner = 10.0,
            freshness = 10.0
        )
        // 0.30*10 + 0.30*0 + 0.20*10 + 0.20*10 = 7.0
        assertEquals(7.0, score, 0.0001)
    }

    @Test
    fun parseYouTubeTarget_acceptsValidAndRejectsNonYouTubeLinks() {
        val watchTarget = ScoringEngine.parseYouTubeTarget(
            "Check out this course: https://www.youtube.com/watch?v=rfscVS0vtbw"
        )
        assertTrue(watchTarget is YouTubeTarget.Video)
        assertEquals("yt_v_rfscVS0vtbw", watchTarget!!.courseId)

        val shortTarget = ScoringEngine.parseYouTubeTarget("https://youtu.be/rfscVS0vtbw?si=xyz")
        assertTrue(shortTarget is YouTubeTarget.Video)
        assertEquals("rfscVS0vtbw", (shortTarget as YouTubeTarget.Video).videoId)

        val playlistTarget = ScoringEngine.parseYouTubeTarget(
            "https://youtube.com/playlist?list=PLillGF-RfqbYeckUaD1z6nviTp31GLTH8"
        )
        assertTrue(playlistTarget is YouTubeTarget.Playlist)
        assertEquals(
            "yt_pl_PLillGF-RfqbYeckUaD1z6nviTp31GLTH8",
            playlistTarget!!.courseId
        )

        assertNull(ScoringEngine.parseYouTubeTarget("https://vimeo.com/12345678"))
        assertNull(ScoringEngine.parseYouTubeTarget("https://evil.youtube.com.attacker.org/watch?v=123456"))
        assertNull(ScoringEngine.parseYouTubeTarget("javascript:alert(1)"))
    }

    @Test
    fun limitSummaryTo25Words_truncatesLongText() {
        val words30 = (1..30).joinToString(" ") { "word$it" }
        val limited = ScoringEngine.limitSummaryTo25Words(words30)
        assertNotNull(limited)
        assertTrue(limited.split(" ").size <= 25)
    }
}
