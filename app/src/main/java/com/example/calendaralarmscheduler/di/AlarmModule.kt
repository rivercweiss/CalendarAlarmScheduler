package com.example.calendaralarmscheduler.di

import android.app.AlarmManager
import android.content.Context
import com.example.calendaralarmscheduler.data.AlarmRepository
import com.example.calendaralarmscheduler.data.CalendarRepository
import com.example.calendaralarmscheduler.data.RuleRepository
import com.example.calendaralarmscheduler.domain.AlarmScheduler
import com.example.calendaralarmscheduler.domain.AlarmSchedulingService
import com.example.calendaralarmscheduler.domain.RuleAlarmManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AlarmModule {

    @Provides
    fun provideAlarmManager(@ApplicationContext context: Context): AlarmManager {
        return context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    @Provides
    @Singleton
    fun provideAlarmScheduler(
        @ApplicationContext context: Context,
        alarmManager: AlarmManager
    ): AlarmScheduler {
        return AlarmScheduler(context, alarmManager)
    }

    @Provides
    @Singleton
    fun provideAlarmSchedulingService(
        alarmRepository: AlarmRepository,
        alarmScheduler: AlarmScheduler
    ): AlarmSchedulingService {
        return AlarmSchedulingService(alarmRepository, alarmScheduler)
    }

    @Provides
    @Singleton
    fun provideRuleAlarmManager(
        ruleRepository: RuleRepository,
        alarmRepository: AlarmRepository,
        alarmScheduler: AlarmScheduler,
        calendarRepository: CalendarRepository,
        alarmSchedulingService: AlarmSchedulingService
    ): RuleAlarmManager {
        return RuleAlarmManager(
            ruleRepository,
            alarmRepository,
            alarmScheduler,
            calendarRepository,
            alarmSchedulingService
        )
    }
}