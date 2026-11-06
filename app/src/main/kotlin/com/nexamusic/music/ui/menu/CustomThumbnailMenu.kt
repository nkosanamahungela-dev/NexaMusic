/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nexamusic.music.ui.menu

import com.nexamusic.music.ui.component.sf
import com.nexamusic.music.ui.component.SymbolIcon
import com.nexamusic.music.ui.component.menuBottomInset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import com.nexamusic.music.ui.ios.compat.Icon
import com.nexamusic.music.ui.ios.compat.ListItem
import com.nexamusic.music.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexamusic.music.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomThumbnailMenu(
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + menuBottomInset(),
        ),
    ) {
        item {
            ListItem(
                headlineContent = { 
                    Text(text = stringResource(R.string.choose_from_library)) 
                },
                leadingContent = {
                    SymbolIcon(symbol = sf("photo"), contentDescription = null)
                },
                modifier = Modifier.clickable {
                    onEdit()
                    onDismiss()
                }
            )
        }
        item {
            ListItem(
                headlineContent = { 
                    Text(text = stringResource(R.string.remove_custom_image)) 
                },
                leadingContent = {
                    SymbolIcon(symbol = sf("trash"), contentDescription = null)
                },
                modifier = Modifier.clickable {
                    onRemove()
                    onDismiss()
                }
            )
        }
    }
}
