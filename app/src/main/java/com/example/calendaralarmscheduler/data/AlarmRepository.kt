package com.example.calendaralarmscheduler.data

import com.example.calendaralarmscheduler.data.database.AlarmDao
import com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.atomic.AtomicLong
/**
 * Repository for managing scheduled alarms with essential CRUD operations.
 * Provides data access layer between domain logic and Room database.
 */
class AlarmRepository(
    private val alarmDao: AlarmDao
) {
    // Cached time threshold to avoid frequent time calculations in Flow
    private val cachedTimeThreshold = AtomicLong(0L)
    private val TIME_CACHE_REFRESH_INTERVAL = 5 * 60 * 1000L // 5 minutes
    private var lastTimeUpdate = 0L
    
    fun getAllAlarms(): Flow<List<ScheduledAlarm>> = alarmDao.getAllAlarms()
    
    fun getActiveAlarms(): Flow<List<ScheduledAlarm>> = 
        alarmDao.getActiveAlarmsAll()
            .distinctUntilChanged() // Prevent unnecessary emissions
            .map { alarms ->
                // Use cached time threshold to avoid constant System.currentTimeMillis() calls
                val threshold = getCurrentTimeThreshold()
                alarms.filter { it.alarmTimeUtc > threshold }
            }
    
    private fun getCurrentTimeThreshold(): Long {
        val currentTime = System.currentTimeMillis()
        // Update cached threshold every 5 minutes to balance accuracy vs performance
        if (currentTime - lastTimeUpdate > TIME_CACHE_REFRESH_INTERVAL) {
            cachedTimeThreshold.set(currentTime)
            lastTimeUpdate = currentTime
        }
        return cachedTimeThreshold.get()
    }
    
    suspend fun getActiveAlarmsSync(): List<ScheduledAlarm> = 
        alarmDao.getActiveAlarmsSync(System.currentTimeMillis())
    
    fun getAlarmsByRuleId(ruleId: String): Flow<List<ScheduledAlarm>> = 
        alarmDao.getAlarmsByRuleId(ruleId)
    
    suspend fun getAlarmByEventAndRule(eventId: String, ruleId: String): ScheduledAlarm? = 
        alarmDao.getAlarmByEventAndRule(eventId, ruleId)
    
    suspend fun getAlarmById(id: String): ScheduledAlarm? = alarmDao.getAlarmById(id)
    
    suspend fun insertAlarm(alarm: ScheduledAlarm) = alarmDao.insertAlarm(alarm)
    
    private suspend fun updateAlarm(alarm: ScheduledAlarm) = alarmDao.updateAlarm(alarm)
    
    suspend fun deleteAlarmsByRuleId(ruleId: String) = alarmDao.deleteAlarmsByRuleId(ruleId)
    
    suspend fun setAlarmDismissed(id: String, dismissed: Boolean = true) = 
        alarmDao.setAlarmDismissed(id, dismissed)
    
    private suspend fun deleteExpiredAlarms(cutoffTime: Long = System.currentTimeMillis() - (24 * 60 * 60 * 1000)) = 
        alarmDao.deleteExpiredAlarms(cutoffTime)
    
    // Core business logic methods for alarm scheduling and management
    
    suspend fun scheduleAlarmForEvent(
        eventId: String,
        ruleId: String, 
        eventTitle: String,
        eventStartTimeUtc: Long,
        leadTimeMinutes: Int,
        lastEventModified: Long
    ): ScheduledAlarm {
        val alarmTimeUtc = eventStartTimeUtc - (leadTimeMinutes * 60 * 1000)
        
        // Generate unique alarm ID and simple request code
        val alarmId = java.util.UUID.randomUUID().toString()
        val requestCode = ScheduledAlarm.generateRequestCode(eventId, ruleId)
        
        val alarm = ScheduledAlarm(
            id = alarmId,
            eventId = eventId,
            ruleId = ruleId,
            eventTitle = eventTitle,
            eventStartTimeUtc = eventStartTimeUtc,
            alarmTimeUtc = alarmTimeUtc,
            pendingIntentRequestCode = requestCode,
            lastEventModified = lastEventModified
        )
        
        insertAlarm(alarm)
        return alarm
    }
    
    suspend fun updateAlarmForChangedEvent(
        eventId: String,
        ruleId: String,
        eventTitle: String,
        eventStartTimeUtc: Long,
        leadTimeMinutes: Int,
        lastEventModified: Long
    ): ScheduledAlarm? {
        val existingAlarm = getAlarmByEventAndRule(eventId, ruleId)
        return if (existingAlarm != null && !existingAlarm.userDismissed) {
            val updatedAlarm = existingAlarm.copy(
                eventTitle = eventTitle,
                eventStartTimeUtc = eventStartTimeUtc,
                alarmTimeUtc = eventStartTimeUtc - (leadTimeMinutes * 60 * 1000),
                lastEventModified = lastEventModified,
                scheduledAt = System.currentTimeMillis()
            )
            updateAlarm(updatedAlarm)
            updatedAlarm
        } else null
    }
    
    suspend fun undismissAlarm(alarmId: String) {
        setAlarmDismissed(alarmId, false)
    }
    
    suspend fun cleanupOldAlarms() {
        deleteExpiredAlarms()
    }
    
    suspend fun markAlarmDismissed(alarmId: String) {
        setAlarmDismissed(alarmId, true)
    }
    
}