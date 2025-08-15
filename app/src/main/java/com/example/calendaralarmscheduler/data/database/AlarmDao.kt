package com.example.calendaralarmscheduler.data.database

import androidx.room.*
import com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for scheduled alarms with essential queries only.
 * Simplified to include only the methods actually used by the application.
 */
@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY alarmTimeUtc ASC")
    fun getAllAlarms(): Flow<List<ScheduledAlarm>>
    
    
    @Query("SELECT * FROM alarms WHERE userDismissed = 0 ORDER BY alarmTimeUtc ASC")
    fun getActiveAlarmsAll(): Flow<List<ScheduledAlarm>>
    
    @Query("SELECT * FROM alarms WHERE userDismissed = 0 AND alarmTimeUtc > :currentTimeUtc ORDER BY alarmTimeUtc ASC")
    suspend fun getActiveAlarmsSync(currentTimeUtc: Long): List<ScheduledAlarm>
    
    
    @Query("SELECT * FROM alarms WHERE ruleId = :ruleId")
    fun getAlarmsByRuleId(ruleId: String): Flow<List<ScheduledAlarm>>
    
    @Query("SELECT * FROM alarms WHERE eventId = :eventId AND ruleId = :ruleId")
    suspend fun getAlarmByEventAndRule(eventId: String, ruleId: String): ScheduledAlarm?
    
    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarmById(id: String): ScheduledAlarm?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: ScheduledAlarm)
    
    @Update
    suspend fun updateAlarm(alarm: ScheduledAlarm)
    
    
    @Query("DELETE FROM alarms WHERE ruleId = :ruleId")
    suspend fun deleteAlarmsByRuleId(ruleId: String)
    
    @Query("UPDATE alarms SET userDismissed = :dismissed WHERE id = :id")
    suspend fun setAlarmDismissed(id: String, dismissed: Boolean)
    
    
    @Query("DELETE FROM alarms WHERE alarmTimeUtc < :cutoffTime")
    suspend fun deleteExpiredAlarms(cutoffTime: Long)
    
}