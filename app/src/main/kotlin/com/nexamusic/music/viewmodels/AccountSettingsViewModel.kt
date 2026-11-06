/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nexamusic.music.viewmodels

import android.content.Context
import android.content.Intent
import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexamusic.music.App
import com.nexamusic.music.constants.AccountChannelHandleKey
import com.nexamusic.music.constants.AccountEmailKey
import com.nexamusic.music.constants.AccountNameKey
import com.nexamusic.music.constants.DataSyncIdKey
import com.nexamusic.music.constants.InnerTubeCookieKey
import com.nexamusic.music.constants.SavedAccount
import com.nexamusic.music.constants.SavedAccountsKey
import com.nexamusic.music.constants.VisitorDataKey
import com.nexamusic.music.constants.toJson
import com.nexamusic.music.utils.SyncUtils
import com.nexamusic.music.utils.dataStore
import com.music.innertube.YouTube
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.datastore.preferences.core.edit

@HiltViewModel
class AccountSettingsViewModel @Inject constructor(
    private val syncUtils: SyncUtils,
) : ViewModel() {

    /** Logs out and clears synced content. */
    fun logoutAndClearSyncedContent(context: Context, onCookieChange: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.clearAllSyncedContent()

            App.forgetAccount(context)

            onCookieChange("")
        }
    }

    /** Logs out without clearing library data. */
    fun logoutKeepData(context: Context, onCookieChange: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            App.forgetAccount(context)
            withContext(Dispatchers.Main) {
                onCookieChange("")
            }
        }
    }

    /** Saves credentials before restarting so DataStore writes finish before process exit. */
    fun saveTokenAndRestart(
        context: Context,
        cookie: String,
        visitorData: String,
        dataSyncId: String,
        accountName: String,
        accountEmail: String,
        accountChannelHandle: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit { settings ->
                settings[InnerTubeCookieKey] = cookie
                settings[VisitorDataKey] = visitorData
                settings[DataSyncIdKey] = dataSyncId
                settings[AccountNameKey] = accountName
                settings[AccountEmailKey] = accountEmail
                settings[AccountChannelHandleKey] = accountChannelHandle
            }
            withContext(Dispatchers.Main) { restartApp(context) }
        }
    }

    /** Saves the selected channel and account list, then restarts with that identity. */
    fun applyChannelAndRestart(
        context: Context,
        cookie: String,
        visitorData: String,
        chosenDataSyncId: String,
        allChannels: List<SavedAccount>,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            YouTube.cookie = cookie
            YouTube.visitorData = visitorData
            YouTube.dataSyncId = chosenDataSyncId
            // Read the profile after setting the selected channel.
            val info = YouTube.accountInfo().getOrNull()

            context.dataStore.edit { settings ->
                settings[InnerTubeCookieKey] = cookie
                settings[VisitorDataKey] = visitorData
                settings[DataSyncIdKey] = chosenDataSyncId
                settings[AccountNameKey] = info?.name.orEmpty()
                settings[AccountEmailKey] = info?.email.orEmpty()
                settings[AccountChannelHandleKey] = info?.channelHandle.orEmpty()
                settings[SavedAccountsKey] = allChannels.toJson()
            }
            withContext(Dispatchers.Main) { restartApp(context) }
        }
    }

    /** Refreshes saved channels when Account settings opens. */
    fun refreshSavedAccounts(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            if (YouTube.cookie == null) return@launch
            val channels = YouTube.getAccountChannels().getOrNull()
                ?.mapNotNull { c ->
                    c.dataSyncId?.let { SavedAccount(it, c.name, c.channelHandle, c.thumbnailUrl) }
                }
                ?.takeIf { it.isNotEmpty() }
                ?: return@launch
            context.dataStore.edit { settings ->
                settings[SavedAccountsKey] = channels.toJson()
            }
        }
    }

    /** Switches to a saved channel without changing the Google login or restarting. */
    fun switchToSavedAccount(context: Context, account: SavedAccount) {
        viewModelScope.launch(Dispatchers.IO) {
            YouTube.dataSyncId = account.dataSyncId

            // Refresh the cookie in case Google rotated it during the switch.
            CookieManager.getInstance().getCookie("https://music.youtube.com")
                ?.takeIf { it.isNotBlank() }
                ?.let { freshCookie ->
                    YouTube.cookie = freshCookie
                    context.dataStore.edit { settings ->
                        settings[InnerTubeCookieKey] = freshCookie
                    }
                }

            context.dataStore.edit { settings ->
                settings[DataSyncIdKey] = account.dataSyncId
                settings[AccountNameKey] = account.name
                settings[AccountChannelHandleKey] = account.channelHandle.orEmpty()
            }
        }
    }

    private fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
