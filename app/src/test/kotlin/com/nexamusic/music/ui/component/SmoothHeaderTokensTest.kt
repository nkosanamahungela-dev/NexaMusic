package com.nexamusic.music.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmoothHeaderTokensTest {
    @Test
    fun `scroll progress clamps at both ends`() {
        assertEquals(0f, SmoothHeaderTokens.progress(0f), 0.001f)
        assertEquals(0.5f, SmoothHeaderTokens.progress(45f), 0.001f)
        assertEquals(1f, SmoothHeaderTokens.progress(180f), 0.001f)
    }

    @Test
    fun `large and compact title opacity handoff is bounded`() {
        assertEquals(1f, SmoothHeaderTokens.bigTitleOpacity(0f), 0.001f)
        assertEquals(0f, SmoothHeaderTokens.bigTitleOpacity(1f), 0.001f)
        assertEquals(0f, SmoothHeaderTokens.smallTitleOpacity(0.45f), 0.001f)
        assertEquals(1f, SmoothHeaderTokens.smallTitleOpacity(0.95f), 0.001f)
    }

    @Test
    fun `blur and profile transforms remain within safe bounds`() {
        assertEquals(0f, SmoothHeaderTokens.blurOpacity(0f), 0.001f)
        assertEquals(1f, SmoothHeaderTokens.blurOpacity(1f), 0.001f)
        assertEquals(1f, SmoothHeaderTokens.profileScale(0f), 0.001f)
        assertTrue(SmoothHeaderTokens.profileScale(1f) in 0.74f..0.76f)
        assertEquals(1f, SmoothHeaderTokens.bigTitleScale(0f), 0.001f)
        assertEquals(0.8f, SmoothHeaderTokens.bigTitleScale(1f), 0.001f)
    }
    @Test
    fun `six blur layers match the progressive mask contract`() {
        assertEquals(
            listOf(0.5f, 1.5f, 3f, 6f, 12f, 24f),
            SmoothHeaderTokens.BlurLayers.map { it.radius },
        )
        assertEquals(
            listOf(1f, 0.84f, 0.68f, 0.52f, 0.36f, 0.20f),
            SmoothHeaderTokens.BlurLayers.map { it.fadeEndFraction },
        )
        assertEquals(1.2f, SmoothHeaderTokens.BlurLayerSaturation, 0.001f)
    }

    @Test
    fun `fallback and header zone values match the design contract`() {
        assertEquals(90f, SmoothHeaderTokens.HeaderScrollDistance.value, 0.001f)
        assertEquals(80f, SmoothHeaderTokens.HeaderContentHeight.value, 0.001f)
        assertEquals(0.62f, SmoothHeaderTokens.HeaderTintPeak, 0.001f)
        assertEquals(0.85f, SmoothHeaderTokens.ScrimFallbackPeak, 0.001f)
        assertEquals(0.92f, SmoothHeaderTokens.ReducedTransparencyPeak, 0.001f)
        assertEquals(150, SmoothHeaderTokens.ReduceMotionCrossfadeMillis)
    }

}
