package com.example.calendaralarmscheduler.data

import com.example.calendaralarmscheduler.data.database.AlarmDao
import com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.atomic.AtomicLong
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
    
    fun getAlarmsByEventId(eventId: String): Flow<List<ScheduledAlarm>> = 
        alarmDao.getAlarmsByEventId(eventId)
    
    fun getAlarmsByRuleId(ruleId: String): Flow<List<ScheduledAlarm>> = 
        alarmDao.getAlarmsByRuleId(ruleId)
    
    suspend fun getAlarmByEventAndRule(eventId: String, ruleId: String): ScheduledAlarm? = 
        alarmDao.getAlarmByEventAndRule(eventId, ruleId)
    
    suspend fun getAlarmById(id: String): ScheduledAlarm? = alarmDao.getAlarmById(id)
    
    fun getAlarmsInTimeRange(startTime: Long, endTime: Long): Flow<List<ScheduledAlarm>> = 
        alarmDao.getAlarmsInTimeRange(startTime, endTime)
    
    suspend fun insertAlarm(alarm: ScheduledAlarm) = alarmDao.insertAlarm(alarm)
    
    suspend fun insertAlarms(alarms: List<ScheduledAlarm>) = alarmDao.insertAlarms(alarms)
    
    suspend fun updateAlarm(alarm: ScheduledAlarm) = alarmDao.updateAlarm(alarm)
    
    suspend fun deleteAlarm(alarm: ScheduledAlarm) = alarmDao.deleteAlarm(alarm)
    
    suspend fun deleteAlarmById(id: String) = alarmDao.deleteAlarmById(id)
    
    suspend fun deleteAlarmsByEventId(eventId: String) = alarmDao.deleteAlarmsByEventId(eventId)
    
    suspend fun deleteAlarmsByRuleId(ruleId: String) = alarmDao.deleteAlarmsByRuleId(ruleId)
    
    suspend fun setAlarmDismissed(id: String, dismissed: Boolean = true) = 
        alarmDao.setAlarmDismissed(id, dismissed)
    
    suspend fun updateAlarmRequestCode(id: String, newRequestCode: Int) = 
        alarmDao.updateAlarmRequestCode(id, newRequestCode)
    
    suspend fun deleteExpiredAlarms(cutoffTime: Long = System.currentTimeMillis() - (24 * 60 * 60 * 1000)) = 
        alarmDao.deleteExpiredAlarms(cutoffTime)
    
    // Business logic methods
    fun getUpcomingAlarms(hoursAhead: Int = 24): Flow<List<ScheduledAlarm>> {
        val now = System.currentTimeMillis()
        val endTime = now + (hoursAhead * 60 * 60 * 1000)
        return getAlarmsInTimeRange(now, endTime)
    }
    
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
    
    suspend fun dismissAlarm(alarmId: String) {
        setAlarmDismissed(alarmId, true)
    }
    
    suspend fun undismissAlarm(alarmId: String) {
        setAlarmDismissed(alarmId, false)
    }
    
    suspend fun reactivateAlarm(alarmId: String): Boolean {
        return try {
            setAlarmDismissed(alarmId, false)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun cleanupOldAlarms() {
        deleteExpiredAlarms()
    }
    
    suspend fun markAlarmDismissed(alarmId: String) {
        setAlarmDismissed(alarmId, true)
    }
    
    // Check if alarm should be rescheduled based on event changes
    suspend fun shouldRescheduleAlarm(eventId: String, ruleId: String, newLastModified: Long): Boolean {
        val existingAlarm = getAlarmByEventAndRule(eventId, ruleId)
        return existingAlarm?.let { alarm ->
            !alarm.userDismissed && newLastModified > alarm.lastEventModified
        } ?: true
    }
    
    suspend fun markMultipleAlarmsDismissed(alarmIds: List<String>) {
        for (alarmId in alarmIds) {
            setAlarmDismissed(alarmId, true)
        }
    }
    
    suspend fun checkSystemStateAndUpdateDismissals(
        onAlarmDismissed: (alarmId: String, eventTitle: String) -> Unit = { _, _ -> }
    ): List<String> {
        // This method would typically be called with AlarmScheduler dependency
        // For now, it returns an empty list as the logic is handled in the worker
        return emptyList()
    }
    
    suspend fun handleDismissedAlarms(dismissedAlarms: List<ScheduledAlarm>) {
        for (alarm in dismissedAlarms) {
            setAlarmDismissed(alarm.id, true)
        }
    }
}