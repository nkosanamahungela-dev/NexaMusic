/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nexamusic.music.ui.component

import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexamusic.music.LocalPlayerAwareWindowInsets
import com.nexamusic.music.ui.ios.compat.AppTheme
import com.nexamusic.music.ui.ios.compat.Text

/**
 * Shared layout for pushed screens. The scrolling content is the Haze source;
 * the fade blur and top bar stay as siblings so neither samples itself. The bar
 * keeps its back button visible and reveals the compact title after scrolling.
 */
@Composable
fun ScreenChromeBox(
    title: String,
    titleAlpha: Float,
    pageColor: Color,
    chrome: GlassScreenChrome,
    onBack: (() -> Unit)?,
    onBackLongClick: (() -> Unit)?,
    actions: @Composable () -> Unit,
    titleAlphaProvider: (() -> Float)? = null,
    progressProvider: () -> Float = { 1f },
    pinnedRow: (@Composable () -> Unit)? = null,
    pinnedRowHeight: Dp = 0.dp,
    pinnedRowVisibleProvider: () -> Boolean = { false },
    content: @Composable BoxScope.() -> Unit,
) {
    ScrollHeaderScaffold(
        title = title,
        titleAlpha = titleAlpha,
        pageColor = pageColor,
        chrome = chrome,
        onBack = onBack,
        onBackLongClick = onBackLongClick,
        actions = actions,
        titleAlphaProvider = titleAlphaProvider,
        progressProvider = progressProvider,
        pinnedRow = pinnedRow,
        pinnedRowHeight = pinnedRowHeight,
        pinnedRowVisibleProvider = pinnedRowVisibleProvider,
        content = content,
    )
}

/**
 * One reusable header scaffold for pushed screens and custom scroll containers.
 * Content, blur and controls are siblings: the blur samples only the scrolling
 * source and can never paint over the controls.
 */
@Composable
fun ScrollHeaderScaffold(
    title: String,
    titleAlpha: Float,
    pageColor: Color,
    chrome: GlassScreenChrome,
    onBack: (() -> Unit)? = null,
    onBackLongClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    titleAlphaProvider: (() -> Float)? = null,
    progressProvider: () -> Float,
    pinnedRow: (@Composable () -> Unit)? = null,
    pinnedRowHeight: Dp = 0.dp,
    pinnedRowVisibleProvider: () -> Boolean = { false },
    content: @Composable BoxScope.() -> Unit,
) {
    val topInset = androidx.compose.foundation.layout.WindowInsets.statusBars
        .asPaddingValues().calculateTopPadding()
    val zoneHeight = topInset + SmoothHeaderTokens.HeaderContentHeight +
        if (pinnedRow != null) pinnedRowHeight else 0.dp

    Box(
        modifier = Modifier.fillMaxSize().background(pageColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageColor)
                .then(chrome.screenModifier()),
            content = content,
        )

        TopFadeBlur(
            hazeState = chrome.hazeState,
            pageColor = pageColor,
            modifier = Modifier.align(Alignment.TopCenter),
            heightOverride = zoneHeight,
            progressProvider = progressProvider,
        )

        FrostedTopBar(
            onBack = onBack,
            onBackLongClick = onBackLongClick,
            title = title,
            titleAlpha = titleAlpha,
            titleAlphaProvider = titleAlphaProvider,
            actions = actions,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (pinnedRow != null && pinnedRowHeight > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topInset + TopBarContentHeight)
                    .height(pinnedRowHeight)
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (pinnedRowVisibleProvider()) 1f else 0f
                    },
            ) {
                pinnedRow()
            }
        }
    }
}

/**
 * The chrome around a body the caller lays out itself — a grid, a WebView, a
 * two-pane layout, an existing list you do not want to restructure.
 *
 * The body is handed the screen's [GlassScreenChrome]: put
 * [GlassScreenChrome.contentModifier] on the scrolling root so the bar's glass
 * has something to refract. The caller also owns [titleAlpha], since only it
 * knows what scrolling means on that layout — pass
 * [rememberTitleCollapseProgress] where there is a list, `1f` where the page
 * does not scroll and the title should simply stay.
 */
@Composable
fun ScreenScaffold(
    title: String,
    titleAlpha: Float,
    onBack: (() -> Unit)? = null,
    onBackLongClick: (() -> Unit)? = null,
    pageColor: Color = AppTheme.colorScheme.background,
    actions: @Composable () -> Unit = {},
    titleAlphaProvider: (() -> Float)? = null,
    progressProvider: () -> Float = { SmoothHeaderProgress.progress },
    content: @Composable BoxScope.(GlassScreenChrome) -> Unit,
) {
    val chrome = rememberGlassScreenChrome(background = pageColor)
    CompositionLocalProvider(LocalAppBackdrop provides chrome.backdrop) {
        ScreenChromeBox(
            title = title,
            titleAlpha = titleAlpha,
            titleAlphaProvider = titleAlphaProvider,
            progressProvider = progressProvider,
            pageColor = pageColor,
            chrome = chrome,
            onBack = onBack,
            onBackLongClick = onBackLongClick,
            actions = actions,
        ) {
            content(chrome)
        }
    }
}

/** A screen's large title, the one the compact bar takes over from. */
@Composable
fun ScreenLargeTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = AppTheme.typography.headlineLarge.copy(fontSize = 40.sp, letterSpacing = (-1.6).sp),
        fontWeight = FontWeight.ExtraBold,
        color = AppTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { heading() }.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 14.dp),
    )
}

/**
 * A page whose body is a plain scrolling [Column].
 *
 * The status-bar clearance is a [Spacer] *inside* the scroll rather than
 * padding around it, which is the whole point: content has to travel under the
 * bar for the blur to have anything to frost.
 */
@Composable
fun ScreenPage(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onBackLongClick: (() -> Unit)? = null,
    pageColor: Color = AppTheme.colorScheme.background,
    horizontalPadding: Dp = 0.dp,
    actions: @Composable () -> Unit = {},
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    scrollState: ScrollState = rememberScrollState(),
    largeTitle: @Composable () -> Unit = { ScreenLargeTitle(title) },
    content: @Composable ColumnScope.() -> Unit,
) {
    val chrome = rememberGlassScreenChrome(background = pageColor)
    val density = LocalDensity.current
    val progressProvider = remember(scrollState, density) {
        { SmoothHeaderTokens.progress(scrollState.value / density.density) }
    }

    CompositionLocalProvider(LocalAppBackdrop provides chrome.backdrop) {
        ScreenChromeBox(
            title = title,
            titleAlpha = 0f,
            titleAlphaProvider = { SmoothHeaderTokens.smallTitleOpacity(progressProvider()) },
            progressProvider = progressProvider,
            pageColor = pageColor,
            chrome = chrome,
            onBack = onBack,
            onBackLongClick = onBackLongClick,
            actions = actions,
        ) {
            Column(
                modifier = modifier
                    .then(chrome.contentModifier())
                    .verticalScroll(scrollState)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal),
                    )
                    .padding(horizontal = horizontalPadding),
                verticalArrangement = verticalArrangement,
            ) {
                Column(
                    Modifier.graphicsLayer {
                            val progress = progressProvider()
                            alpha = SmoothHeaderTokens.bigTitleOpacity(progress)
                            translationY = -SmoothHeaderTokens.BigTitleTravel.toPx() * progress
                            val scale = SmoothHeaderTokens.bigTitleScale(progress)
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        },
                ) {
                    Spacer(
                        Modifier.windowInsetsPadding(
                            LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top),
                        ),
                    )
                    largeTitle()
                }

                content()

                Spacer(
                    Modifier
                        .height(80.dp)
                        .windowInsetsPadding(
                            LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom),
                        ),
                )
            }
        }
    }
}

/**
 * The [LazyColumn] flavour, for a page whose body is a real list.
 *
 * Emits the title block as `item("title")`, so [rememberTitleCollapseProgress]
 * can measure it off the list's own layout info instead of the size callback
 * [ScreenPage] needs.
 */
@Composable
fun ScreenLazyPage(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onBackLongClick: (() -> Unit)? = null,
    pageColor: Color = AppTheme.colorScheme.background,
    horizontalPadding: Dp = 0.dp,
    actions: @Composable () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    largeTitle: @Composable () -> Unit = { ScreenLargeTitle(title) },
    content: LazyListScope.() -> Unit,
) {
    val chrome = rememberGlassScreenChrome(background = pageColor)
    val density = LocalDensity.current
    val progressProvider = remember(listState, density) {
        {
            if (listState.firstVisibleItemIndex > 0) 1f
            else SmoothHeaderTokens.progress(listState.firstVisibleItemScrollOffset / density.density)
        }
    }

    CompositionLocalProvider(LocalAppBackdrop provides chrome.backdrop) {
        ScreenChromeBox(
            title = title,
            titleAlpha = 0f,
            titleAlphaProvider = { SmoothHeaderTokens.smallTitleOpacity(progressProvider()) },
            progressProvider = progressProvider,
            pageColor = pageColor,
            chrome = chrome,
            onBack = onBack,
            onBackLongClick = onBackLongClick,
            actions = actions,
        ) {
            val sideInsets = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal).asPaddingValues()
            val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
            LazyColumn(
                state = listState,
                modifier = modifier.then(chrome.contentModifier()),
                contentPadding = PaddingValues(
                    start = sideInsets.calculateStartPadding(direction) + horizontalPadding,
                    end = sideInsets.calculateEndPadding(direction) + horizontalPadding,
                ),
            ) {
                item(key = "title") {
                    Column(
                        Modifier.graphicsLayer {
                            val progress = progressProvider()
                            alpha = SmoothHeaderTokens.bigTitleOpacity(progress)
                            translationY = -SmoothHeaderTokens.BigTitleTravel.toPx() * progress
                            val scale = SmoothHeaderTokens.bigTitleScale(progress)
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        },
                    ) {
                        Spacer(
                            Modifier.windowInsetsPadding(
                                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top),
                            ),
                        )
                        largeTitle()
                    }
                }

                content()

                item(key = "screen_lazy_page_bottom_spacer") {
                    Spacer(
                        Modifier
                            .height(80.dp)
                            .windowInsetsPadding(
                                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom),
                            ),
                    )
                }
            }
        }
    }
}
