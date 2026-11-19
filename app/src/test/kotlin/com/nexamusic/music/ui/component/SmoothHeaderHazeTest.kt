package com.nexamusic.music.ui.component

import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmoothHeaderHazeTest {
    @Test
    fun `dark theme style has opaque page colour and specified tint`() {
        val page = Color(0xFF101010)
        val resolved = createHeaderHazeStyle(page, darkTheme = true, debugBuild = true)

        assertTrue(resolved.blurAllowed)
        assertEquals(page, resolved.pageColor)
        assertEquals(page, resolved.hazeStyle.backgroundColor)
        assertEquals(0.62f, resolved.hazeStyle.tints.single().color.alpha, 0.001f)
        assertEquals(24f, resolved.hazeStyle.blurRadius.value, 0.001f)
        assertEquals(0f, resolved.hazeStyle.noiseFactor, 0.001f)
    }

    @Test
    fun `light theme style has opaque page colour and specified tint`() {
        val page = Color(0xFFF7F7F7)
        val resolved = createHeaderHazeStyle(page, darkTheme = false, debugBuild = true)

        assertTrue(resolved.blurAllowed)
        assertEquals(page, resolved.hazeStyle.backgroundColor)
        assertEquals(0.62f, resolved.hazeStyle.tints.single().color.alpha, 0.001f)
    }

    @Test
    fun `release mode turns invalid dark and light colours into scrim fallbacks`() {
        val dark = createHeaderHazeStyle(Color.Transparent, darkTheme = true, debugBuild = false)
        val light = createHeaderHazeStyle(Color.Unspecified, darkTheme = false, debugBuild = false)

        assertFalse(dark.blurAllowed)
        assertFalse(light.blurAllowed)
        assertEquals(Color.Black, dark.pageColor)
        assertEquals(Color.White, light.pageColor)
        assertTrue(dark.hazeStyle.backgroundColor.isSpecified)
        assertTrue(light.hazeStyle.backgroundColor.isSpecified)
    }

    @Test(expected = IllegalStateException::class)
    fun `debug mode rejects transparent page colours`() {
        createHeaderHazeStyle(Color.Transparent, darkTheme = true, debugBuild = true)
    }
}
