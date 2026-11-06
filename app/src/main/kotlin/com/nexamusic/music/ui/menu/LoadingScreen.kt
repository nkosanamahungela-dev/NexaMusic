/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nexamusic.music.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.nexamusic.music.ui.component.ShimmerLine
import com.nexamusic.music.ui.ios.compat.AppTheme
import com.nexamusic.music.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nexamusic.music.R

/** Progress dialog with a shimmer indicator and percentage. */
@Composable
fun LoadingScreen(
    isVisible: Boolean,
    value: Int,
) {
    if (isVisible) {
        Dialog(
            onDismissRequest = {}
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppTheme.colorScheme.surface)
                    .padding(24.dp)
                    .width(200.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.nexamusic_logo),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.padding(bottom = 18.dp).width(56.dp),
                )
                ShimmerLine(fraction = 0.6f, height = 8.dp)

                Text(
                    text = stringResource(R.string.progress_percent, value.toString()),
                    color = AppTheme.colorScheme.onSurface,
                    fontSize = 26.sp,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}
