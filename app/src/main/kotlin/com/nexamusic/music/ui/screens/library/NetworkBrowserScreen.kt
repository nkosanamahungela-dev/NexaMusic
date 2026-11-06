/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nexamusic.music.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.composables.sfsymbols.SfSymbol
import com.nexamusic.music.LocalPlayerConnection
import com.nexamusic.music.R
import com.nexamusic.music.playback.queues.ListQueue
import com.nexamusic.music.ui.component.ListItem
import com.nexamusic.music.ui.component.ScreenLazyPage
import com.nexamusic.music.ui.component.SymbolIcon
import com.nexamusic.music.ui.component.sf
import com.nexamusic.music.ui.ios.compat.AppTheme
import com.nexamusic.music.ui.ios.compat.CircularProgressIndicator
import com.nexamusic.music.ui.ios.compat.Text
import com.nexamusic.music.ui.utils.bounceClick
import com.nexamusic.music.utils.listItemShape
import com.nexamusic.music.viewmodels.NetworkBrowserViewModel
import com.nexamusic.storage.domain.NetworkMedia
import com.nexamusic.storage.domain.RemotePath
import kotlinx.coroutines.launch

/** The bundled symbol library decides what exists; sf() throws on a name it lacks. */
internal val FolderSymbol: String by lazy {
    listOf("folder.fill", "folder").firstOrNull { SfSymbol.named(it) != null } ?: "music.note.list"
}

/** One SMB or WebDAV source as a folder tree: folders open in place, audio plays from that folder onward. */
@Composable
fun NetworkBrowserScreen(
    navController: NavController,
    serverId: String,
    viewModel: NetworkBrowserViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val listing by viewModel.listing.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(serverId) { viewModel.open(serverId, "") }
    BackHandler(enabled = listing.folder.isNotEmpty()) {
        viewModel.open(serverId, RemotePath.parent(listing.folder))
    }

    val title = listing.folder.substringAfterLast('/').ifEmpty { servers.firstOrNull { it.id == serverId }?.name.orEmpty() }
    val songs = listing.entries.filter { !it.directory && NetworkMedia.isAudio(it.name) }
    val rows = listing.entries.filter { it.directory || NetworkMedia.isAudio(it.name) }

    ScreenLazyPage(title = title, onBack = { navController.navigateUp() }) {
        if (listing.loading || listing.failed || rows.isEmpty()) {
            item(key = "status") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    when {
                        listing.loading -> CircularProgressIndicator()
                        listing.failed -> Text(stringResource(R.string.network_storage_err_connection), color = AppTheme.colorScheme.error)
                        else -> Text(stringResource(R.string.no_results_found), color = AppTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            itemsIndexed(rows, key = { _, entry -> entry.path }) { index, entry ->
                ListItem(
                    title = entry.name,
                    subtitle = entry.size.takeIf { !entry.directory && it > 0 }?.let { "%.1f MB".format(it / 1_048_576.0) } ?: "",
                    thumbnailContent = {
                        SymbolIcon(
                            symbol = sf(if (entry.directory) FolderSymbol else "music.note.list"),
                            contentDescription = null,
                            size = 28.dp,
                        )
                    },
                    shape = listItemShape(index, rows.size),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick {
                            if (entry.directory) {
                                viewModel.open(serverId, entry.path)
                            } else {
                                scope.launch {
                                    val items = viewModel.mediaItems(serverId, songs)
                                    if (items.isNotEmpty()) {
                                        playerConnection.playQueue(
                                            ListQueue(items = items, startIndex = songs.indexOf(entry).coerceAtLeast(0)),
                                        )
                                    }
                                }
                            }
                        },
                )
            }
        }
    }
}
