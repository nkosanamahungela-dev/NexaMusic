/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nexamusic.music

import com.nexamusic.music.ui.component.sf
import com.nexamusic.music.ui.component.SmoothHeaderTokens
import com.nexamusic.music.ui.component.SmoothHeaderProgress
import com.nexamusic.music.ui.component.rememberSmoothHeaderTitleMotion
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFChartBar
import com.composables.sfsymbols.monochrome.SFClock
import com.composables.sfsymbols.monochrome.SFPlus
import com.nexamusic.music.R
import com.nexamusic.music.ui.component.AccountAvatar
import com.nexamusic.music.ui.component.CreatePlaylistDialog
import com.nexamusic.music.ui.component.FrostedTopBar
import com.nexamusic.music.ui.component.GlassCircleButton
import com.nexamusic.music.ui.component.TopFadeBlur
import com.nexamusic.music.ui.component.rememberAppBackgroundTint
import com.nexamusic.music.ui.component.backdrop.Backdrop
import com.nexamusic.music.ui.ios.component.IosGlyph
import com.nexamusic.music.ui.ios.compat.DropdownMenu
import com.nexamusic.music.ui.ios.compat.DropdownMenuItem
import com.nexamusic.music.ui.ios.compat.IosMenuDivider
import com.nexamusic.music.ui.ios.compat.AppTheme
import com.nexamusic.music.ui.ios.compat.Text
import com.nexamusic.music.ui.ios.compat.menuTrigger
import com.nexamusic.music.ui.ios.foundation.IosGlassSpec
import com.nexamusic.music.ui.ios.foundation.iosGlass
import com.nexamusic.music.ui.screens.Screens
import com.nexamusic.music.ui.theme.LocalAccentTextColor
import com.nexamusic.music.ui.theme.SfProFontFamily
import com.nexamusic.music.ui.theme.isHueless
import dev.chrisbanes.haze.HazeState

/** The persistent top bar: edge blur plus the large title and the library/profile actions. */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
fun AppTopBar(
    visible: Boolean,
    showRail: Boolean,
    startInset: Dp,
    hazeState: HazeState,
    backdrop: Backdrop?,
    cutoutInsets: WindowInsets,
    statusBarTop: Dp,
    effectiveRoute: String?,
    titleRes: Int?,
    accountImageUrl: String?,
    accountName: String,
    navController: NavHostController,
    onAccountClick: () -> Unit,
    chromeVisible: Boolean = visible,
) {
        val currentEntry by navController.currentBackStackEntryAsState()
        val screenKey = currentEntry?.id ?: effectiveRoute ?: "app"
        val headerProgressProvider = remember(screenKey) { { SmoothHeaderProgress.progressFor(screenKey) } }
        val titleMotion = rememberSmoothHeaderTitleMotion(headerProgressProvider)
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = fadeOut(animationSpec = tween(durationMillis = 200))
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Explore must never sample album artwork into its title/status-bar strip.
                // Keep the route-specific chrome on the current page colour instead.
                // All tab routes share the same progressive page-colour fade.
                // Never paint a fixed surface strip behind the status bar/title.
                val artworkSafeRoute = effectiveRoute == Screens.Explore.route || effectiveRoute == Screens.Radio.route
                TopFadeBlur(
                    hazeState = hazeState,
                    pageColor = AppTheme.colorScheme.background,
                    scrimColor = AppTheme.colorScheme.background,
                    scrimOpacity = SmoothHeaderTokens.TintPeakOpacity,
                    backdrop = backdrop,
                    progressProvider = { headerProgressProvider() },
                    startFade = if (showRail) startInset else 0.dp,
                )
                // Explore's title remains theme-neutral and cannot inherit cover-derived tint.
                val topBarAmbient = LocalAmbientBackgroundColor.current.value
                val topBarTitleColor = if (!artworkSafeRoute && topBarAmbient.isSpecified && !topBarAmbient.isHueless()) {
                    LocalAccentTextColor.current
                } else {
                    AppTheme.colorScheme.onSurface
                }
                if (chromeVisible) FrostedTopBar(
                    title = titleRes?.let { stringResource(it) } ?: "",
                    titleAlphaProvider = titleMotion.compactAlpha,
                    progressProvider = { headerProgressProvider() },
                    // The blur above spans the full width; only the content clears the side bar and cutouts.
                    modifier = Modifier
                        .windowInsetsPadding(cutoutInsets.only(WindowInsetsSides.Start + WindowInsetsSides.End))
                        .padding(start = startInset)
                        .padding(top = if (showRail) 0.dp else {
                            val titleTop = if (effectiveRoute == Screens.Library.route) 61.dp else 62.dp
                            maxOf(statusBarTop, titleTop) - statusBarTop
                        }),
                    leadingInset = if (showRail) 12.dp else 16.dp,
                    largeTitle = !showRail,
                    leading = {
                        // SF Pro Text is wider than Apple's Display face at this size.
                        Text(
                            text = titleRes?.let { stringResource(it) } ?: "",
                            style = AppTheme.typography.titleLarge.copy(
                                fontFamily = SfProFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = SmoothHeaderTokens.BigTitleSize,
                                lineHeight = SmoothHeaderTokens.BigTitleLineHeight,
                                letterSpacing = SmoothHeaderTokens.BigTitleLetterSpacing,
                            ),
                            color = topBarTitleColor,
                            maxLines = 1,
                            modifier = (if (showRail) Modifier else Modifier.height(46.dp)).semantics { heading() }.graphicsLayer {
                                if (!showRail) {
                                    val p = headerProgressProvider()
                                    alpha = titleMotion.largeAlpha()
                                    translationY = if (titleMotion.transformsEnabled) -SmoothHeaderTokens.BigTitleTravel.toPx() * p else 0f
                                    val scale = if (titleMotion.transformsEnabled) SmoothHeaderTokens.bigTitleScale(p) else 1f
                                    scaleX = scale
                                    scaleY = scale
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                }
                            },
                        )
                    },
                    actions = {
                        if (effectiveRoute == Screens.Library.route) {
                            LibraryTopBarActions(navController)
                        } else {
                            Box(
                                Modifier.graphicsLayer {
                                    val p = headerProgressProvider()
                                    alpha = SmoothHeaderTokens.profileOpacity(p)
                                    val scale = if (titleMotion.transformsEnabled) SmoothHeaderTokens.profileScale(p) else 1f
                                    scaleX = scale
                                    scaleY = scale
                                },
                            ) {
                                GlassCircleButton(size = 44.dp, applyAdditiveChrome = false, forceFlat = true, onClick = onAccountClick) {
                                    BadgedBox(badge = {}) {
                                        AccountAvatar(
                                            accountImageUrl, accountName,
                                            description = stringResource(R.string.account),
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
}

@Composable
private fun LibraryTopBarActions(navController: NavHostController) {
    var menuExpanded by remember {
        mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }

    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .iosGlass(shape = CircleShape, spec = IosGlassSpec.Control, interactive = true)
                .menuTrigger { menuExpanded = true },
        ) {
            IosGlyph(
                symbol = sf("ellipsis"),
                tint = AppTheme.colorScheme.onSurface,
                box = 22.dp,
            )
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.new_playlist_ellipsis)) },
                leadingIcon = {
                    IosGlyph(sf("SFPlus"), AppTheme.colorScheme.onSurface, 19.dp, enterOnly = true)
                },
                onClick = {
                    menuExpanded = false
                    showCreatePlaylist = true
                },
            )
            IosMenuDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history)) },
                leadingIcon = {
                    IosGlyph(sf("SFClock"), AppTheme.colorScheme.onSurface, 19.dp, enterOnly = true)
                },
                onClick = {
                    menuExpanded = false
                    navController.navigate("history")
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.stats)) },
                leadingIcon = {
                    IosGlyph(sf("SFChartBar"), AppTheme.colorScheme.onSurface, 19.dp, enterOnly = true)
                },
                onClick = {
                    menuExpanded = false
                    navController.navigate("stats")
                },
            )
        }
    }

    if (showCreatePlaylist) {
        CreatePlaylistDialog(onDismiss = { showCreatePlaylist = false })
    }

    Spacer(Modifier.width(8.dp))
}
