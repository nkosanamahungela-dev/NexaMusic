package com.nexamusic.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.chrisbanes.haze.HazeState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class SmoothHeaderHazeUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun restoredHeaderDrawsOnFirstFrameInDarkAndLightTheme() {
        val darkThemeState = mutableStateOf(true)

        composeRule.setContent {
            val darkTheme = darkThemeState.value
            val pageColor = if (darkTheme) Color(0xFF101010) else Color(0xFFF7F7F7)
            val style = rememberHeaderHazeStyle(pageColor, darkTheme)
            val hazeState = remember { HazeState() }

            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF202020))
                        .nexaHazeSource(hazeState),
                )
                // The first composed frame is already scrolled (progress = 1f).
                Box(
                    Modifier
                        .fillMaxSize()
                        .testTag("restored-header")
                        .graphicsLayer { alpha = SmoothHeaderTokens.blurOpacity(1f) }
                        .nexaHeaderBlur(hazeState, style),
                )
            }
        }

        // First frame: dark theme with a restored non-zero scroll offset.
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("restored-header").assertExists()

        // Change Appearance while the same scrolled screen remains composed.
        composeRule.runOnUiThread { darkThemeState.value = false }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("restored-header").assertExists()
    }
}
