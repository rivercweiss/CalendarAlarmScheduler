package com.example.calendaralarmscheduler.di

import android.content.Context
import androidx.room.Room
import com.example.calendaralarmscheduler.data.database.AlarmDao
import com.example.calendaralarmscheduler.data.database.AppDatabase
import com.example.calendaralarmscheduler.data.database.RuleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
        .addMigrations()
        .addCallback(object : androidx.room.RoomDatabase.Callback() {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                super.onCreate(db)
                com.example.calendaralarmscheduler.utils.Logger.i("AppDatabase", "Database created successfully")
            }
            
            override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                super.onOpen(db)
                com.example.calendaralarmscheduler.utils.Logger.d("AppDatabase", "Database opened - version: ${db.version}")
            }
        })
        .setQueryCallback({ sqlQuery, bindArgs ->
            com.example.calendaralarmscheduler.utils.Logger.v("AppDatabase", "Query: $sqlQuery with args: $bindArgs")
        }, { runnable -> runnable.run() })
        .build()
    }

    @Provides
    fun provideRuleDao(database: AppDatabase): RuleDao {
        return database.ruleDao()
    }

    @Provides
    fun provideAlarmDao(database: AppDatabase): AlarmDao {
        return database.alarmDao()
    }
}