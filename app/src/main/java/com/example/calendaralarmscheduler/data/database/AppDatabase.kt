package com.example.calendaralarmscheduler.data.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.calendaralarmscheduler.data.database.entities.Rule
import com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm
import com.example.calendaralarmscheduler.utils.Logger
import com.example.calendaralarmscheduler.utils.CrashHandler

@Database(
    entities = [Rule::class, ScheduledAlarm::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Rule.Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun alarmDao(): AlarmDao

    companion object {
        const val DATABASE_NAME = "calendar_alarm_scheduler_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val startTime = System.currentTimeMillis()
                Logger.d("AppDatabase", "Starting database initialization")
                
                try {
                    Logger.d("AppDatabase", "Creating Room database builder")
                    val builder = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        DATABASE_NAME
                    )
                    
                    Logger.d("AppDatabase", "Adding callbacks (no migrations - database will be recreated)")
                    // No migrations - database version 2 will trigger schema recreation
                    builder.addCallback(object : RoomDatabase.Callback() {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                super.onCreate(db)
                                Logger.i("AppDatabase", "Database created successfully")
                            }
                            
                            override fun onOpen(db: SupportSQLiteDatabase) {
                                super.onOpen(db)
                                Logger.d("AppDatabase", "Database opened - version: ${db.version}")
                            }
                        })
                        .setQueryCallback({ sqlQuery, bindArgs ->
                            Logger.v("AppDatabase", "Query: $sqlQuery with args: $bindArgs")
                        }, { runnable -> runnable.run() })
                    
                    Logger.d("AppDatabase", "Building Room database instance")
                    val instance = builder.build()
                    
                    Logger.i("AppDatabase", "Database instance created successfully")
                    
                    INSTANCE = instance
                    val initTime = System.currentTimeMillis() - startTime
                    Logger.logPerformance("AppDatabase", "Database initialization", initTime)
                    Logger.i("AppDatabase", "AppDatabase initialized successfully in ${initTime}ms")
                    
                    instance
                } catch (e: Exception) {
                    val initTime = System.currentTimeMillis() - startTime
                    Logger.crash("AppDatabase", "FATAL: Database initialization failed after ${initTime}ms", e)
                    CrashHandler().logNonFatalException("AppDatabase", "Database initialization failed", e)
                    
                    // Clear instance to allow retry
                    INSTANCE = null
                    throw RuntimeException("Failed to initialize database", e)
                }
            }
        }

        // Database version 1: Initial schema
        // No migration needed

        fun destroyInstance() {
            Logger.i("AppDatabase", "Destroying database instance")
            INSTANCE?.close()
            INSTANCE = null
            Logger.i("AppDatabase", "Database instance destroyed")
        }
    }
}