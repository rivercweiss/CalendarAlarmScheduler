package com.example.calendaralarmscheduler.data.database

import androidx.room.*
import com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY alarmTimeUtc ASC")
    fun getAllAlarms(): Flow<List<ScheduledAlarm>>
    
    @Query("SELECT * FROM alarms WHERE userDismissed = 0 AND alarmTimeUtc > :currentTimeUtc ORDER BY alarmTimeUtc ASC")
    fun getActiveAlarms(currentTimeUtc: Long = System.currentTimeMillis()): Flow<List<ScheduledAlarm>>
    
    @Query("SELECT * FROM alarms WHERE eventId = :eventId")
    fun getAlarmsByEventId(eventId: String): Flow<List<ScheduledAlarm>>
    
    @Query("SELECT * FROM alarms WHERE ruleId = :ruleId")
    fun getAlarmsByRuleId(ruleId: String): Flow<List<ScheduledAlarm>>
    
    @Query("SELECT * FROM alarms WHERE eventId = :eventId AND ruleId = :ruleId")
    suspend fun getAlarmByEventAndRule(eventId: String, ruleId: String): ScheduledAlarm?
    
    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarmById(id: String): ScheduledAlarm?
    
    @Query("SELECT * FROM alarms WHERE userDismissed = 0 AND alarmTimeUtc BETWEEN :startTime AND :endTime")
    fun getAlarmsInTimeRange(startTime: Long, endTime: Long): Flow<List<ScheduledAlarm>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: ScheduledAlarm)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarms(alarms: List<ScheduledAlarm>)
    
    @Update
    suspend fun updateAlarm(alarm: ScheduledAlarm)
    
    @Delete
    suspend fun deleteAlarm(alarm: ScheduledAlarm)
    
    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteAlarmById(id: String)
    
    @Query("DELETE FROM alarms WHERE eventId = :eventId")
    suspend fun deleteAlarmsByEventId(eventId: String)
    
    @Query("DELETE FROM alarms WHERE ruleId = :ruleId")
    suspend fun deleteAlarmsByRuleId(ruleId: String)
    
    @Query("UPDATE alarms SET userDismissed = :dismissed WHERE id = :id")
    suspend fun setAlarmDismissed(id: String, dismissed: Boolean)
    
    @Query("DELETE FROM alarms WHERE alarmTimeUtc < :cutoffTime")
    suspend fun deleteExpiredAlarms(cutoffTime: Long)
    
    @Query("DELETE FROM alarms")
    suspend fun deleteAllAlarms()
}