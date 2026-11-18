/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.nexamusic.music.ui.component

import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexamusic.music.ui.ios.compat.AppTheme
import com.nexamusic.music.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

data class SmoothHeaderTitleMotion(
    val largeAlpha: () -> Float,
    val compactAlpha: () -> Float,
    val transformsEnabled: Boolean,
)

/**
 * Respect Android's system reduce-motion setting. Only the reduced-motion
 * handoff uses a 150ms animation; normal mode remains finger-synchronous.
 */
@Composable
fun rememberSmoothHeaderTitleMotion(
    progressProvider: () -> Float,
): SmoothHeaderTitleMotion {
    val reduceMotion = !ValueAnimator.areAnimatorsEnabled()
    val compactThreshold by remember(progressProvider) {
        derivedStateOf { progressProvider() >= 0.5f }
    }
    val compactFade by animateFloatAsState(
        targetValue = if (reduceMotion && compactThreshold) 1f else 0f,
        animationSpec = tween(durationMillis = SmoothHeaderTokens.ReduceMotionCrossfadeMillis),
        label = "smoothHeaderReducedMotion",
    )
    return remember(reduceMotion, compactFade, progressProvider) {
        SmoothHeaderTitleMotion(
            largeAlpha = {
                if (reduceMotion) 1f - compactFade
                else SmoothHeaderTokens.bigTitleOpacity(progressProvider())
            },
            compactAlpha = {
                if (reduceMotion) compactFade
                else SmoothHeaderTokens.smallTitleOpacity(progressProvider())
            },
            transformsEnabled = !reduceMotion,
        )
    }
}


/** Compact title shared by artwork-detail headers. */
@Composable
fun SmoothHeaderCompactTitle(
    title: String,
    progressProvider: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colorScheme.onSurface,
) {
    val motion = rememberSmoothHeaderTitleMotion(progressProvider)
    Box(
        modifier = modifier.fillMaxWidth().height(52.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = AppTheme.typography.titleMedium.copy(fontSize = 17.sp),
            color = color,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 88.dp)
                .graphicsLayer {
                    val p = progressProvider().coerceIn(0f, 1f)
                    alpha = motion.compactAlpha()
                    translationY = if (motion.transformsEnabled) {
                        (1f - p) * SmoothHeaderTokens.SmallTitleTravel.toPx()
                    } else 0f
                }
                .clearAndSetSemantics { },
        )
    }
}
