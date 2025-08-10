package com.example.calendaralarmscheduler.di

import android.content.Context
import com.example.calendaralarmscheduler.workers.WorkerManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WorkerModule {

    @Provides
    @Singleton
    fun provideWorkerManager(@ApplicationContext context: Context): WorkerManager {
        return WorkerManager(context)
    }
}