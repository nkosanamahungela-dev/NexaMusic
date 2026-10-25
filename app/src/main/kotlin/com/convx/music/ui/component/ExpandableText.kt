/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.convx.music.R

data class LinkSegment(
    val text: String,
    val url: String? = null,
)

@Composable
fun ExpandableText(
    text: String = "",
    runs: List<LinkSegment>? = null,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 3,
    /** Render the Apple Music-style bold MORE affordance inline at the end of the collapsed description. */
    inlineMore: Boolean = false,
    style: TextStyle = AppTheme.typography.bodyMedium,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var hasOverflow by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val linkColor = AppTheme.colorScheme.primary
    val bodyColor = com.convx.music.ui.ios.compat.LocalContentColor.current

    val annotatedText: AnnotatedString = remember(text, runs, linkColor) {
        if (runs.isNullOrEmpty()) {
            AnnotatedString(text)
        } else {
            buildAnnotatedString {
                runs.forEach { segment ->
                    if (segment.url != null) {
                        pushStringAnnotation(tag = "URL", annotation = segment.url)
                        withStyle(SpanStyle(color = linkColor)) {
                            append(segment.text)
                        }
                        pop()
                    } else {
                        append(segment.text)
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier.animateContentSize()
    ) {
        if (inlineMore && !isExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.Bottom,
            ) {
                @Suppress("DEPRECATION")
                ClickableText(
                    text = annotatedText,
                    style = style.copy(color = bodyColor),
                    maxLines = collapsedMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { textLayoutResult ->
                        hasOverflow = textLayoutResult.hasVisualOverflow || textLayoutResult.lineCount > collapsedMaxLines
                    },
                    onClick = { offset ->
                        annotatedText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                uriHandler.openUri(annotation.item)
                                return@ClickableText
                            }
                        if (hasOverflow) isExpanded = true
                    },
                    modifier = Modifier.weight(1f),
                )
                if (hasOverflow) {
                    Text(
                        text = "MORE",
                        style = style.copy(fontWeight = FontWeight.Bold),
                        color = bodyColor,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable { isExpanded = true },
                    )
                }
            }
        } else {
            @Suppress("DEPRECATION")
            ClickableText(
                text = annotatedText,
                style = style.copy(color = bodyColor),
                maxLines = if (isExpanded) Int.MAX_VALUE else collapsedMaxLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { textLayoutResult ->
                    hasOverflow = textLayoutResult.hasVisualOverflow || textLayoutResult.lineCount > collapsedMaxLines
                },
                onClick = { offset ->
                    annotatedText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            uriHandler.openUri(annotation.item)
                            return@ClickableText
                        }
                    if (hasOverflow) isExpanded = !isExpanded
                }
            )
            if (hasOverflow && !inlineMore) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppTheme.colorScheme.secondaryContainer)
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(if (isExpanded) R.string.show_less else R.string.show_more),
                        style = AppTheme.typography.labelSmall,
                        color = AppTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
