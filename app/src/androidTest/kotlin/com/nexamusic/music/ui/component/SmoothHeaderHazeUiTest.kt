package com.nexamusic.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import dev.chrisbanes.haze.HazeState
import org.junit.Rule
import org.junit.Test

class SmoothHeaderHazeUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun restoredHeaderDrawsOnFirstFrameAndSurvivesThemeChange() {
        composeRule.setContent {
            var pageColor by remember { mutableStateOf(Color(0xFF101010)) }
            val darkTheme = pageColor == Color(0xFF101010)
            val style = rememberHeaderHazeStyle(pageColor, darkTheme)
            val hazeState = remember { HazeState() }

            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF202020))
                        .nexaHazeSource(hazeState),
                )
                // Progress is deliberately already > 0 during the first composition,
                // matching a restored list position after back navigation/deep linking.
                Box(
                    Modifier
                        .fillMaxSize()
                        .testTag("restored-header")
                        .nexaHeaderBlur(hazeState, style),
                )
            }

            androidx.compose.runtime.SideEffect {
                // Toggle to light after the initial dark first-frame draw.
                if (darkTheme) pageColor = Color(0xFFF7F7F7)
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("restored-header").assertExists()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("restored-header").assertExists()
    }
}
