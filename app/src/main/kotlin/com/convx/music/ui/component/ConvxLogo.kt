/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.convx.music.R

/**
 * The NexaMusic brand mark. Keep the source artwork's green gradient intact.
 */
@Composable
fun ConvxLogo(modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Image(
        painter = painterResource(R.drawable.ic_nexamusic_foreground),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}
