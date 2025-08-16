package com.example.calendaralarmscheduler.di

import android.content.Context
import com.example.calendaralarmscheduler.workers.BackgroundRefreshManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BackgroundRefreshModule {

    @Provides
    @Singleton
    fun provideBackgroundRefreshManager(@ApplicationContext context: Context): BackgroundRefreshManager {
        return BackgroundRefreshManager(context)
    }
}