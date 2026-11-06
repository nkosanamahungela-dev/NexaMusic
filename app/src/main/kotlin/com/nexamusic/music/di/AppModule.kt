/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nexamusic.music.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.room.Room
import com.nexamusic.music.constants.MaxSongCacheSizeKey
import com.nexamusic.music.db.InternalDatabase
import com.nexamusic.music.db.MusicDatabase
import com.nexamusic.music.listentogether.ListenTogetherClient
import com.nexamusic.music.listentogether.ListenTogetherManager
import com.nexamusic.music.utils.dataStore
import com.nexamusic.music.utils.get
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Singleton
    @Provides
    fun provideDao(
        database: InternalDatabase,
    ) = database.dao

    // Use the shared database builder so this provider includes configured migrations.
    @Singleton
    @Provides
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): MusicDatabase = InternalDatabase.newInstance(context)

    @Singleton
    @Provides
    fun provideInternalDatabase(
        database: MusicDatabase,
    ): InternalDatabase = database.delegate

    @Singleton
    @Provides
    fun provideDatabaseProvider(
        @ApplicationContext context: Context,
    ): DatabaseProvider = StandaloneDatabaseProvider(context)

    @Singleton
    @Provides
    @PlayerCache
    fun providePlayerCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): SimpleCache {
        val cacheSize = context.dataStore[MaxSongCacheSizeKey] ?: 1024
        return SimpleCache(
            context.filesDir.resolve("exoplayer"),
            when (cacheSize) {
                -1 -> NoOpCacheEvictor()
                else -> LeastRecentlyUsedCacheEvictor(cacheSize * 1024 * 1024L)
            },
            databaseProvider,
        )
    }

    @Singleton
    @Provides
    @DownloadCache
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): SimpleCache {
        return SimpleCache(
            context.filesDir.resolve("download"),
            NoOpCacheEvictor(),
            databaseProvider
        )
    }

    @Singleton
    @Provides
    fun provideListenTogetherClient(
        @ApplicationContext context: Context,
    ): ListenTogetherClient = ListenTogetherClient(context)

    @Singleton
    @Provides
    fun provideListenTogetherManager(
        @ApplicationContext context: Context,
        client: ListenTogetherClient,
        playerConnectionBridge: com.nexamusic.music.listentogether.ListenTogetherPlayerConnectionBridge,
    ): ListenTogetherManager = ListenTogetherManager(client, context, playerConnectionBridge)
}
