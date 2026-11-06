package com.nexamusic.music.di

import com.nexamusic.music.history.HistoryLocalDataSource
import com.nexamusic.music.history.RoomHistoryLocalDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HistoryDataModule {
    @Binds
    @Singleton
    abstract fun bindHistoryLocalDataSource(
        source: RoomHistoryLocalDataSource,
    ): HistoryLocalDataSource
}
