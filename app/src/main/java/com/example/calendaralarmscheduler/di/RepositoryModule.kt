package com.example.calendaralarmscheduler.di

import android.content.Context
import com.example.calendaralarmscheduler.data.AlarmRepository
import com.example.calendaralarmscheduler.data.CalendarRepository
import com.example.calendaralarmscheduler.data.DayTrackingRepository
import com.example.calendaralarmscheduler.data.RuleRepository
import com.example.calendaralarmscheduler.data.SettingsRepository
import com.example.calendaralarmscheduler.data.database.AlarmDao
import com.example.calendaralarmscheduler.data.database.RuleDao
import com.example.calendaralarmscheduler.services.DayResetService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideRuleRepository(ruleDao: RuleDao): RuleRepository {
        return RuleRepository(ruleDao)
    }

    @Provides
    @Singleton
    fun provideAlarmRepository(alarmDao: AlarmDao): AlarmRepository {
        return AlarmRepository(alarmDao)
    }

    @Provides
    @Singleton
    fun provideCalendarRepository(@ApplicationContext context: Context): CalendarRepository {
        return CalendarRepository(context)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository {
        return SettingsRepository(
            context = context,
            onRefreshIntervalChanged = null // Will be set up in Application class
        )
    }

    @Provides
    @Singleton
    fun provideDayTrackingRepository(@ApplicationContext context: Context): DayTrackingRepository {
        return DayTrackingRepository(context)
    }

    @Provides
    @Singleton
    fun provideDayResetService(
        @ApplicationContext context: Context,
        dayTrackingRepository: DayTrackingRepository
    ): DayResetService {
        return DayResetService(context, dayTrackingRepository)
    }
}