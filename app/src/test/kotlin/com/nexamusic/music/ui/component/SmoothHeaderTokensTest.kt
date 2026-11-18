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
}
