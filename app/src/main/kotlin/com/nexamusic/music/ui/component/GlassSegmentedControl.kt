/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Lightweight, cached glass segmented control. No backdrop sampling, shaders or blur.
 */
package com.nexamusic.music.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import com.nexamusic.music.ui.theme.SfProFontFamily
import kotlin.math.abs
import kotlin.math.roundToInt

/** All dimensions, colours and motion values for this control live here. */
object GlassSegmentedTokens {
    val trackHeight = 44.dp
    val innerPadding = 3.dp
    val minTouchHeight = 44.dp
    val baseLabelSize = 15.sp
    val minLabelSize = 13.sp
    val labelWeight = FontWeight.SemiBold
    val springDamping = 0.78f
    val springStiffness = 820f
    const val springResponseSeconds = 0.35f
    const val stretchX = 1.08f
    const val stretchY = 0.96f
    const val pressScale = 1.04f
    const val labelPressAlpha = 0.70f
    const val labelFadeMs = 200
    const val reduceMotionFadeMs = 100
    const val contentExitMs = 120
    const val contentEnterMs = 180

    object Dark {
        val track = Color(0x3D767680)
        val border = Color(0x1AFFFFFF)
        val thumb = Color(0x33FFFFFF)
        val thumbHighlight = Color(0x59FFFFFF)
        val thumbShadow = Color(0x40000000)
        val selectedLabel = Color(0xFFFFFFFF)
        val unselectedLabel = Color(0xFFB8B8BD)
    }
    object Light {
        val track = Color(0x24767680)
        val border = Color(0x0F000000)
        val thumb = Color(0xEBFFFFFF)
        val thumbHighlight = Color(0xE6FFFFFF)
        val thumbShadow = Color(0x1F000000)
        val selectedLabel = Color(0xFF000000)
        val unselectedLabel = Color(0xFF4A4A4F)
    }
}

/**
 * Two or more equal-width capsule segments. [selectedIndex] remains the source of truth;
 * this component never owns or persists the caller's selection.
 */
@Composable
fun GlassSegmentedControl(
    titles: List<String>,
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    if (titles.isEmpty()) return

    val colors = com.nexamusic.music.ui.ios.foundation.IosTheme.colors
    val dark = colors.isDark
    val trackColor = if (dark) GlassSegmentedTokens.Dark.track else GlassSegmentedTokens.Light.track
    val borderColor = if (dark) GlassSegmentedTokens.Dark.border else GlassSegmentedTokens.Light.border
    val thumbColor = if (dark) GlassSegmentedTokens.Dark.thumb else GlassSegmentedTokens.Light.thumb
    val thumbHighlight = if (dark) GlassSegmentedTokens.Dark.thumbHighlight else GlassSegmentedTokens.Light.thumbHighlight
    val thumbShadow = if (dark) GlassSegmentedTokens.Dark.thumbShadow else GlassSegmentedTokens.Light.thumbShadow
    val selectedText = if (dark) GlassSegmentedTokens.Dark.selectedLabel else GlassSegmentedTokens.Light.selectedLabel
    val unselectedText = if (dark) GlassSegmentedTokens.Dark.unselectedLabel else GlassSegmentedTokens.Light.unselectedLabel

    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val direction = LocalLayoutDirection.current
    val accessibility = LocalAccessibilityManager.current
    val systemReduceMotion = accessibility?.animationsEnabled == false
    val noMotion = reduceMotion || systemReduceMotion

    val safeIndex = selectedIndex.coerceIn(0, titles.lastIndex)
    val position = remember { Animatable(safeIndex.toFloat()) }
    var dragVelocity by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var stretchDirection by remember { mutableFloatStateOf(1f) }
    val stretchX by animateFloatAsState(
        targetValue = if (dragging) GlassSegmentedTokens.stretchX else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 500f),
        label = "glassSegmentStretchX",
    )
    val stretchY by animateFloatAsState(
        targetValue = if (dragging) GlassSegmentedTokens.stretchY else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 560f),
        label = "glassSegmentStretchY",
    )

    LaunchedEffect(safeIndex, noMotion) {
        if (noMotion) {
            position.snapTo(safeIndex.toFloat())
        } else if (!dragging) {
            position.animateTo(
                targetValue = safeIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = GlassSegmentedTokens.springDamping,
                    stiffness = GlassSegmentedTokens.springStiffness,
                ),
                initialVelocity = dragVelocity,
            )
            dragVelocity = 0f
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = GlassSegmentedTokens.minTouchHeight)
            .height(GlassSegmentedTokens.trackHeight)
            .clip(CircleShape)
            .background(trackColor)
            .border(1.dp, borderColor, CircleShape)
            // Cached once for the measured size; only the thumb's layer transforms.
            .drawWithCache {
                val topShade = Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = if (dark) 0.10f else 0.035f),
                    1f to Color.Transparent,
                    startY = 0f,
                    endY = size.height * 0.42f,
                )
                onDrawWithContent {
                    drawRect(topShade)
                    drawContent()
                }
            },
    ) {
        val inner = GlassSegmentedTokens.innerPadding
        val thumbWidth = (maxWidth - inner * 2) / titles.size
        val thumbHeight = maxHeight - inner * 2
        val maxIndex = titles.lastIndex
        val visualPosition = if (direction == LayoutDirection.Rtl) maxIndex - position.value else position.value
        val pressScale = if (dragging) GlassSegmentedTokens.pressScale else 1f

        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = inner)
                .width(thumbWidth)
                .height(thumbHeight)
                .graphicsLayer {
                    translationX = (thumbWidth * visualPosition).toPx()
                    scaleX = stretchX * pressScale
                    scaleY = stretchY * pressScale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                        pivotFractionX = if (stretchDirection > 0f) 0f else 1f,
                        pivotFractionY = 0.5f,
                    )
                }
                .zIndex(0f)
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    ambientColor = thumbShadow,
                    spotColor = thumbShadow,
                )
                .clip(CircleShape)
                .background(thumbColor)
                .drawWithCache {
                    val highlight = Brush.verticalGradient(
                        0f to thumbHighlight,
                        0.20f to thumbHighlight.copy(alpha = 0.18f),
                        1f to Color.Transparent,
                    )
                    onDrawWithContent {
                        drawContent()
                        drawLine(
                            brush = highlight,
                            start = Offset(0f, 0.5.dp.toPx()),
                            end = Offset(size.width, 0.5.dp.toPx()),
                            strokeWidth = 1.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
                .pointerInput(thumbWidth, maxIndex, direction, noMotion) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            val logical = if (direction == LayoutDirection.Rtl) maxIndex - position.value else position.value
                            val target = logical.roundToInt().coerceIn(0, maxIndex)
                            dragVelocity = 0f
                            if (target != safeIndex) {
                                onSelectionChange(target)
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            }
                        },
                        onDragCancel = { dragging = false },
                    ) { change, dragAmount ->
                        change.consume()
                        val delta = dragAmount.x / thumbWidth.toPx() * if (direction == LayoutDirection.Rtl) -1f else 1f
                        stretchDirection = if (delta >= 0f) 1f else -1f
                        val raw = position.value + delta
                        val resisted = when {
                            raw < 0f -> raw * 0.22f
                            raw > maxIndex -> maxIndex + (raw - maxIndex) * 0.22f
                            else -> raw
                        }
                        position.snapTo(resisted.coerceIn(-0.16f, maxIndex + 0.16f))
                    }
                },
        )

        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            titles.forEachIndexed { index, title ->
                val interactions = remember(index) { MutableInteractionSource() }
                val pressed by interactions.collectIsPressedAsState()
                val isSelected = index == safeIndex
                val labelAlpha by animateFloatAsState(
                    targetValue = if (pressed) GlassSegmentedTokens.labelPressAlpha else 1f,
                    animationSpec = androidx.compose.animation.core.tween(120),
                    label = "segmentPressAlpha$index",
                )
                val selectionFraction = if (maxIndex == 0) 1f else (1f - abs(position.value - index)).coerceIn(0f, 1f)
                val color = androidx.compose.ui.graphics.lerp(unselectedText, selectedText, if (noMotion) if (isSelected) 1f else 0f else selectionFraction)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .zIndex(1f)
                        .semantics {
                            role = Role.Tab
                            selected = isSelected
                            contentDescription = "$title, ${if (isSelected) "selected" else ""}, ${index + 1} of ${titles.size}"
                        }
                        .clickable(
                            interactionSource = interactions,
                            indication = null,
                            onClick = {
                                if (index != safeIndex) {
                                    onSelectionChange(index)
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title,
                        color = color.copy(alpha = labelAlpha),
                        fontFamily = SfProFontFamily,
                        fontWeight = GlassSegmentedTokens.labelWeight,
                        fontSize = GlassSegmentedTokens.baseLabelSize,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 5.dp),
                    )
                }
            }
        }
    }
}
