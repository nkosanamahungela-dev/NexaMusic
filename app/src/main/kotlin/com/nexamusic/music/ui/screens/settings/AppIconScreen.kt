/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nexamusic.music.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import com.nexamusic.music.ui.ios.compat.Icon
import com.nexamusic.music.ui.ios.compat.AppTheme
import com.nexamusic.music.ui.ios.compat.Text
import com.nexamusic.music.ui.ios.compat.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nexamusic.music.LocalPlayerAwareWindowInsets
import com.nexamusic.music.R
import com.nexamusic.music.constants.AppIconKey
import com.nexamusic.music.ui.component.IconButton
import com.nexamusic.music.ui.component.rememberTitleCollapseProgressState
import com.nexamusic.music.ui.component.topBarContentPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.nexamusic.music.ui.utils.backToMain
import com.nexamusic.music.utils.AppIcon
import com.nexamusic.music.utils.rememberPreference

@Composable
fun AppIconScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
) {
    var showAvailableSoon by remember { mutableStateOf(false) }

    SettingsPage(
        navController = navController,
        title = stringResource(R.string.app_icon),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.nexamusic_logo),
                contentDescription = stringResource(R.string.app_icon),
                modifier = Modifier.size(112.dp).clip(RoundedCornerShape(24.dp)).clickable { showAvailableSoon = true },
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.app_icon_desc),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.app_icon_available_soon_message),
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }

    if (showAvailableSoon) {
        AlertDialog(
            onDismissRequest = { showAvailableSoon = false },
            title = { Text(stringResource(R.string.app_icon)) },
            text = { Text(stringResource(R.string.app_icon_available_soon_message)) },
            confirmButton = {
                TextButton(onClick = { showAvailableSoon = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}
