package com.convx.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.TextFieldValue
import coil3.compose.AsyncImage
import com.convx.music.R
import com.convx.music.ui.ios.compat.LocalContentColor
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.screens.library.apple.SfStyle
import com.convx.music.ui.utils.SharedArtworkSeedImage
import com.convx.music.ui.utils.sharedArtworkTarget
import com.convx.music.ui.utils.sharedTitleTarget

@Composable
fun CollectionDetailHeader(
    title: String,
    artwork: String?,
    metadata: String,
    foreground: Color,
    playing: Boolean,
    enabled: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    description: String? = null,
    descriptionRuns: List<LinkSegment>? = null,
    rightSymbol: String = "ellipsis",
    onRightAction: (() -> Unit)? = null,
    onEditCover: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    searchValue: TextFieldValue? = null,
    onSearchValueChange: ((TextFieldValue) -> Unit)? = null,
    onSearchImeAction: (() -> Unit)? = null,
    searchFocusRequester: FocusRequester? = null,
    titleContent: (@Composable () -> Unit)? = null,
    artworkOverlay: (@Composable BoxScope.() -> Unit)? = null,
    /** Enables the Apple Music iOS 26.4 full-bleed treatment for Album/Playlist only. */
    immersiveArtwork: Boolean = false,
    /** Flat artwork-derived colour that fills the page below the image. */
    artworkPageColor: Color = Color.Black,
    /** 0..1 list scroll progress for a subtle image parallax. TopFadeBlur handles glass blur. */
    heroScrollProgress: Float = 0f,
    /** Start the sharp artwork behind the status bar while header content keeps its safe-area spacing. */
    artworkTopOffset: androidx.compose.ui.unit.Dp = (-24).dp,
) {
    // Full-page collection headers always use white foreground over the lower dark scrim.
    val contentForeground = if (immersiveArtwork) Color.White else foreground
    CompositionLocalProvider(LocalContentColor provides contentForeground) {
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val artworkSize = minOf(maxWidth * .68f, 300.dp)
            var artworkFailed by remember(artwork) { mutableStateOf(false) }

            // The artwork belongs to the scrollable header, not a fixed screen overlay.
            // It starts above the safe-area content and fades into the flat page colour.
            if (immersiveArtwork) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(520.dp)
                        .offset(y = artworkTopOffset)
                        .graphicsLayer { translationY = heroScrollProgress.coerceIn(0f, 1f) * 48.dp.toPx() }
                        .background(artworkPageColor)
                        .sharedArtworkTarget(),
                ) {
                    val seededArtwork = !immersiveArtwork && SharedArtworkSeedImage(Modifier.fillMaxSize(), ContentScale.Crop)
                    if (!seededArtwork && !artwork.isNullOrBlank() && !artworkFailed) {
                        AsyncImage(
                            model = artwork,
                            contentDescription = null,
                            onError = { artworkFailed = true },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    artworkOverlay?.invoke(this)

                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        // Keep the top and middle of the image completely clean.
                                        0.00f to Color.Transparent,
                                        0.52f to Color.Transparent,
                                        // Darken before blending into the artwork-derived page colour.
                                        0.62f to Color.Black.copy(alpha = 0.08f),
                                        0.72f to Color.Black.copy(alpha = 0.22f),
                                        0.80f to Color.Black.copy(alpha = 0.42f),
                                        // The tint reaches the image only at the bottom edge, never as a mid-art wash.
                                        0.88f to artworkPageColor.copy(alpha = 0.55f),
                                        0.95f to artworkPageColor.copy(alpha = 0.96f),
                                        1.00f to artworkPageColor,
                                    ),
                                ),
                            ),
                    )

                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (onSearch != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (immersiveArtwork) 20.dp else 18.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(contentForeground.copy(alpha = 0.12f))
                            .clickable(role = Role.Button) {
                                if (searchValue == null) onSearch()
                                else searchFocusRequester?.requestFocus()
                            }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SymbolIcon(sf("magnifyingglass"), null, size = 18.dp, tint = contentForeground.copy(alpha = if (immersiveArtwork) .72f else .48f))
                        Spacer(Modifier.width(8.dp))
                        if (searchValue == null || onSearchValueChange == null) {
                            Text(
                                stringResource(R.string.search),
                                style = SfStyle(17.sp, 22.sp),
                                color = contentForeground.copy(alpha = if (immersiveArtwork) .72f else .48f),
                            )
                        } else {
                            BasicTextField(
                                value = searchValue,
                                onValueChange = onSearchValueChange,
                                singleLine = true,
                                textStyle = SfStyle(17.sp, 22.sp).copy(color = contentForeground),
                                cursorBrush = SolidColor(foreground),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { onSearchImeAction?.invoke() }),
                                modifier = Modifier.weight(1f).then(
                                    searchFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
                                ),
                                decorationBox = { innerTextField ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (searchValue.text.isEmpty()) {
                                            Text(
                                                stringResource(R.string.search),
                                                style = SfStyle(17.sp, 22.sp),
                                                color = contentForeground.copy(alpha = .72f),
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(if (immersiveArtwork) 230.dp else 26.dp))
                }

                if (!immersiveArtwork) {
                    Box(
                        Modifier
                            .size(artworkSize)
                            .shadow(
                                18.dp,
                                RoundedCornerShape(12.dp),
                                clip = false,
                                ambientColor = Color.Black.copy(alpha = .18f),
                                spotColor = Color.Black.copy(alpha = .18f),
                            ),
                    ) {
                        Box(
                            Modifier
                                .sharedArtworkTarget()
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(contentForeground.copy(alpha = .12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            val seeded = SharedArtworkSeedImage(Modifier.fillMaxSize(), ContentScale.Crop)
                            if (artwork.isNullOrBlank() || artworkFailed) {
                                SymbolIcon(sf("music.note.list"), null, tint = contentForeground.copy(alpha = .5f), size = 72.dp)
                            } else if (!seeded) {
                                AsyncImage(
                                    artwork,
                                    null,
                                    onError = { if (!seeded) artworkFailed = true },
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            artworkOverlay?.invoke(this)
                        }
                        if (onEditCover != null) {
                            ContrastCircleButton(
                                foreground,
                                onEditCover,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                                size = 36.dp,
                            ) {
                                SymbolIcon(sf("square.and.pencil"), stringResource(R.string.edit_playlist_cover), size = 18.dp)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                } else if (onSearch == null) {
                    // Albums do not have an in-page search field; keep the title low in the hero.
                    Spacer(Modifier.height(285.dp))
                }

                if (titleContent != null) {
                    titleContent()
                } else {
                    Text(
                        title,
                        style = SfStyle(
                            if (immersiveArtwork) 27.sp else 22.sp,
                            if (immersiveArtwork) 33.sp else 27.sp,
                            FontWeight.Bold,
                        ),
                        color = contentForeground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.sharedTitleTarget().padding(horizontal = 24.dp),
                    )
                }

                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(if (immersiveArtwork) 3.dp else 8.dp))
                    Text(
                        subtitle,
                        style = SfStyle(
                            if (immersiveArtwork) 18.sp else 20.sp,
                            if (immersiveArtwork) 23.sp else 24.sp,
                        ),
                        color = contentForeground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .then(if (titleContent != null) Modifier.sharedTitleTarget() else Modifier)
                            .padding(horizontal = 24.dp),
                    )
                }

                if (metadata.isNotBlank()) {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        metadata,
                        style = SfStyle(
                            if (immersiveArtwork) 13.sp else 15.sp,
                            if (immersiveArtwork) 17.sp else 19.sp,
                            FontWeight.Medium,
                        ),
                        color = contentForeground.copy(alpha = if (immersiveArtwork) .88f else .48f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }

                Row(
                    Modifier.padding(top = if (immersiveArtwork) 14.dp else 17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ContrastCircleButton(contentForeground, onShuffle, size = 54.dp, enabled = enabled) {
                        SymbolIcon(sf("shuffle"), stringResource(R.string.shuffle), size = 26.dp)
                    }
                    Row(
                        Modifier
                            .width(if (immersiveArtwork) 150.dp else 140.dp)
                            .height(50.dp)
                            .clip(RoundedCornerShape(25.dp))
                            .background(
                                if (immersiveArtwork && enabled) {
                                    Color.White
                                } else {
                                    contentForeground.copy(alpha = if (enabled) .82f else .4f)
                                },
                            )
                            .clickable(enabled = enabled, role = Role.Button, onClick = onPlay),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        val inverse = if (immersiveArtwork) Color.Black else if (contentForeground == Color.White) Color.Black else Color.White
                        SymbolIcon(sf(if (playing) "pause.fill" else "play.fill"), null, tint = inverse, size = 19.dp)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            stringResource(if (playing) R.string.pause else R.string.play),
                            style = SfStyle(19.sp, 23.sp, FontWeight.Bold),
                            color = inverse,
                        )
                    }
                    if (onRightAction != null) {
                        ContrastCircleButton(contentForeground, onRightAction, size = 54.dp) {
                            SymbolIcon(sf(rightSymbol), stringResource(R.string.more_options), size = 26.dp)
                        }
                    }
                }

                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(if (immersiveArtwork) 12.dp else 20.dp))
                    CompositionLocalProvider(LocalContentColor provides contentForeground.copy(alpha = if (immersiveArtwork) .84f else .48f)) {
                        ExpandableText(
                            description,
                            runs = descriptionRuns,
                            collapsedMaxLines = if (immersiveArtwork && onSearch != null) 1 else 2,
                            inlineMore = immersiveArtwork,
                            style = if (immersiveArtwork && onSearch != null) {
                                com.convx.music.ui.ios.compat.AppTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp,
                                    lineHeight = 17.sp,
                                )
                            } else {
                                com.convx.music.ui.ios.compat.AppTheme.typography.bodyMedium
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        )
                    }
                }
            }
        }
    }
}
