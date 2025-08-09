package com.example.calendaralarmscheduler.data

import com.example.calendaralarmscheduler.data.database.AlarmDao
import com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
class AlarmRepository(
    private val alarmDao: AlarmDao
) {
    fun getAllAlarms(): Flow<List<ScheduledAlarm>> = alarmDao.getAllAlarms()
    
    fun getActiveAlarms(): Flow<List<ScheduledAlarm>> = alarmDao.getActiveAlarms()
    
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
        val requestCode = ScheduledAlarm.generateRequestCode(eventId, ruleId)
        
        val alarm = ScheduledAlarm(
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
    
    suspend fun cleanupOldAlarms() {
        deleteExpiredAlarms()
    }
    
    // Check if alarm should be rescheduled based on event changes
    suspend fun shouldRescheduleAlarm(eventId: String, ruleId: String, newLastModified: Long): Boolean {
        val existingAlarm = getAlarmByEventAndRule(eventId, ruleId)
        return existingAlarm?.let { alarm ->
            !alarm.userDismissed && newLastModified > alarm.lastEventModified
        } ?: true
    }
}