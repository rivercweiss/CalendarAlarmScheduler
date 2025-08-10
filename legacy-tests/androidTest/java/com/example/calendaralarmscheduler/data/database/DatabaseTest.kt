package com.example.calendaralarmscheduler.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.calendaralarmscheduler.data.database.entities.Rule
import com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var ruleDao: RuleDao
    private lateinit var alarmDao: AlarmDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        
        ruleDao = database.ruleDao()
        alarmDao = database.alarmDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // === Rule DAO Tests ===

    @Test
    fun insertRule_insertsSuccessfully() = runTest {
        val rule = createTestRule("Test Rule", "meeting")
        
        ruleDao.insertRule(rule)
        
        val allRules = ruleDao.getAllRules().first()
        assertThat(allRules).hasSize(1)
        assertThat(allRules[0].name).isEqualTo("Test Rule")
        assertThat(allRules[0].keywordPattern).isEqualTo("meeting")
    }

    @Test
    fun insertRule_withListConverter_preservesCalendarIds() = runTest {
        val calendarIds = listOf(1L, 2L, 3L)
        val rule = createTestRule("Test Rule", "meeting", calendarIds = calendarIds)
        
        ruleDao.insertRule(rule)
        
        val retrievedRule = ruleDao.getAllRules().first()[0]
        assertThat(retrievedRule.calendarIds).containsExactlyElementsIn(calendarIds).inOrder()
    }

    @Test
    fun updateRule_updatesExistingRule() = runTest {
        val originalRule = createTestRule("Original", "meeting")
        ruleDao.insertRule(originalRule)
        
        val updatedRule = originalRule.copy(name = "Updated", keywordPattern = "appointment")
        ruleDao.updateRule(updatedRule)
        
        val retrievedRule = ruleDao.getRuleById(originalRule.id)
        assertThat(retrievedRule?.name).isEqualTo("Updated")
        assertThat(retrievedRule?.keywordPattern).isEqualTo("appointment")
    }

    @Test
    fun deleteRule_removesRule() = runTest {
        val rule = createTestRule("Test Rule", "meeting")
        ruleDao.insertRule(rule)
        
        ruleDao.deleteRule(rule)
        
        val allRules = ruleDao.getAllRules().first()
        assertThat(allRules).isEmpty()
    }

    @Test
    fun getRuleById_returnsCorrectRule() = runTest {
        val rule1 = createTestRule("Rule 1", "meeting")
        val rule2 = createTestRule("Rule 2", "appointment")
        
        ruleDao.insertRule(rule1)
        ruleDao.insertRule(rule2)
        
        val retrievedRule = ruleDao.getRuleById(rule1.id)
        assertThat(retrievedRule?.name).isEqualTo("Rule 1")
        assertThat(retrievedRule?.id).isEqualTo(rule1.id)
    }

    @Test
    fun getRuleById_returnsNullForNonexistentRule() = runTest {
        val retrievedRule = ruleDao.getRuleById("nonexistent-id")
        assertThat(retrievedRule).isNull()
    }

    @Test
    fun getEnabledRules_filtersDisabledRules() = runTest {
        val enabledRule = createTestRule("Enabled", "meeting", enabled = true)
        val disabledRule = createTestRule("Disabled", "appointment", enabled = false)
        
        ruleDao.insertRule(enabledRule)
        ruleDao.insertRule(disabledRule)
        
        val enabledRules = ruleDao.getEnabledRules().first()
        assertThat(enabledRules).hasSize(1)
        assertThat(enabledRules[0].name).isEqualTo("Enabled")
    }

    @Test
    fun getRulesByCalendarId_filtersCorrectly() = runTest {
        val calendar1Rule = createTestRule("Calendar 1", "meeting", calendarIds = listOf(1L))
        val calendar2Rule = createTestRule("Calendar 2", "meeting", calendarIds = listOf(2L))
        val bothCalendarsRule = createTestRule("Both", "meeting", calendarIds = listOf(1L, 2L))
        
        ruleDao.insertRule(calendar1Rule)
        ruleDao.insertRule(calendar2Rule)
        ruleDao.insertRule(bothCalendarsRule)
        
        val calendar1Rules = ruleDao.getRulesByCalendarId(1L).first()
        assertThat(calendar1Rules).hasSize(2)
        assertThat(calendar1Rules.map { it.name }).containsExactly("Calendar 1", "Both")
    }

    @Test
    fun insertMultipleRules_maintainsSeparateRecords() = runTest {
        val rule1 = createTestRule("Rule 1", "meeting")
        val rule2 = createTestRule("Rule 2", "appointment")
        
        ruleDao.insertRules(listOf(rule1, rule2))
        
        val allRules = ruleDao.getAllRules().first()
        assertThat(allRules).hasSize(2)
        assertThat(allRules.map { it.name }).containsExactly("Rule 1", "Rule 2")
    }

    // === Alarm DAO Tests ===

    @Test
    fun insertAlarm_insertsSuccessfully() = runTest {
        val alarm = createTestAlarm("event1", "rule1", "Meeting")
        
        alarmDao.insertAlarm(alarm)
        
        val allAlarms = alarmDao.getAllAlarms().first()
        assertThat(allAlarms).hasSize(1)
        assertThat(allAlarms[0].eventTitle).isEqualTo("Meeting")
        assertThat(allAlarms[0].eventId).isEqualTo("event1")
    }

    @Test
    fun updateAlarm_updatesExistingAlarm() = runTest {
        val originalAlarm = createTestAlarm("event1", "rule1", "Original Meeting")
        alarmDao.insertAlarm(originalAlarm)
        
        val updatedAlarm = originalAlarm.copy(eventTitle = "Updated Meeting", userDismissed = true)
        alarmDao.updateAlarm(updatedAlarm)
        
        val retrievedAlarm = alarmDao.getAlarmById(originalAlarm.id)
        assertThat(retrievedAlarm?.eventTitle).isEqualTo("Updated Meeting")
        assertThat(retrievedAlarm?.userDismissed).isTrue()
    }

    @Test
    fun deleteAlarm_removesAlarm() = runTest {
        val alarm = createTestAlarm("event1", "rule1", "Meeting")
        alarmDao.insertAlarm(alarm)
        
        alarmDao.deleteAlarm(alarm)
        
        val allAlarms = alarmDao.getAllAlarms().first()
        assertThat(allAlarms).isEmpty()
    }

    @Test
    fun getAlarmById_returnsCorrectAlarm() = runTest {
        val alarm1 = createTestAlarm("event1", "rule1", "Meeting 1")
        val alarm2 = createTestAlarm("event2", "rule2", "Meeting 2")
        
        alarmDao.insertAlarm(alarm1)
        alarmDao.insertAlarm(alarm2)
        
        val retrievedAlarm = alarmDao.getAlarmById(alarm1.id)
        assertThat(retrievedAlarm?.eventTitle).isEqualTo("Meeting 1")
        assertThat(retrievedAlarm?.id).isEqualTo(alarm1.id)
    }

    @Test
    fun getActiveAlarms_filtersOutPastAndDismissed() = runTest {
        val now = System.currentTimeMillis()
        val future = now + 3600000 // 1 hour from now
        val past = now - 3600000 // 1 hour ago
        
        val activeAlarm = createTestAlarm("event1", "rule1", "Active", alarmTime = future)
        val pastAlarm = createTestAlarm("event2", "rule2", "Past", alarmTime = past)
        val dismissedAlarm = createTestAlarm("event3", "rule3", "Dismissed", alarmTime = future, dismissed = true)
        
        alarmDao.insertAlarms(listOf(activeAlarm, pastAlarm, dismissedAlarm))
        
        val activeAlarms = alarmDao.getActiveAlarms(now).first()
        assertThat(activeAlarms).hasSize(1)
        assertThat(activeAlarms[0].eventTitle).isEqualTo("Active")
    }

    @Test
    fun getAlarmsByEventId_filtersCorrectly() = runTest {
        val event1Alarm1 = createTestAlarm("event1", "rule1", "Event 1 Alarm 1")
        val event1Alarm2 = createTestAlarm("event1", "rule2", "Event 1 Alarm 2")
        val event2Alarm = createTestAlarm("event2", "rule1", "Event 2 Alarm")
        
        alarmDao.insertAlarms(listOf(event1Alarm1, event1Alarm2, event2Alarm))
        
        val event1Alarms = alarmDao.getAlarmsByEventId("event1").first()
        assertThat(event1Alarms).hasSize(2)
        assertThat(event1Alarms.map { it.eventTitle }).containsExactly("Event 1 Alarm 1", "Event 1 Alarm 2")
    }

    @Test
    fun getAlarmsByRuleId_filtersCorrectly() = runTest {
        val rule1Event1 = createTestAlarm("event1", "rule1", "Rule 1 Event 1")
        val rule1Event2 = createTestAlarm("event2", "rule1", "Rule 1 Event 2")
        val rule2Event1 = createTestAlarm("event1", "rule2", "Rule 2 Event 1")
        
        alarmDao.insertAlarms(listOf(rule1Event1, rule1Event2, rule2Event1))
        
        val rule1Alarms = alarmDao.getAlarmsByRuleId("rule1").first()
        assertThat(rule1Alarms).hasSize(2)
        assertThat(rule1Alarms.map { it.eventTitle }).containsExactly("Rule 1 Event 1", "Rule 1 Event 2")
    }

    @Test
    fun getAlarmsInTimeRange_filtersCorrectly() = runTest {
        val now = System.currentTimeMillis()
        val hour = 3600000L
        
        val beforeRange = createTestAlarm("event1", "rule1", "Before", alarmTime = now - 2 * hour)
        val inRange1 = createTestAlarm("event2", "rule2", "In Range 1", alarmTime = now + hour)
        val inRange2 = createTestAlarm("event3", "rule3", "In Range 2", alarmTime = now + 2 * hour)
        val afterRange = createTestAlarm("event4", "rule4", "After", alarmTime = now + 4 * hour)
        
        alarmDao.insertAlarms(listOf(beforeRange, inRange1, inRange2, afterRange))
        
        val rangeStart = now
        val rangeEnd = now + 3 * hour
        val alarmsInRange = alarmDao.getAlarmsInTimeRange(rangeStart, rangeEnd).first()
        
        assertThat(alarmsInRange).hasSize(2)
        assertThat(alarmsInRange.map { it.eventTitle }).containsExactly("In Range 1", "In Range 2")
    }

    @Test
    fun setAlarmDismissed_updatesStatus() = runTest {
        val alarm = createTestAlarm("event1", "rule1", "Meeting")
        alarmDao.insertAlarm(alarm)
        
        alarmDao.setAlarmDismissed(alarm.id, true)
        
        val updatedAlarm = alarmDao.getAlarmById(alarm.id)
        assertThat(updatedAlarm?.userDismissed).isTrue()
    }

    @Test
    fun deleteExpiredAlarms_removesAlarmsOlderThanThreshold() = runTest {
        val now = System.currentTimeMillis()
        val day = 24 * 3600000L
        
        val oldAlarm = createTestAlarm("event1", "rule1", "Old", alarmTime = now - 2 * day)
        val recentAlarm = createTestAlarm("event2", "rule2", "Recent", alarmTime = now - day / 2)
        
        alarmDao.insertAlarms(listOf(oldAlarm, recentAlarm))
        
        val thresholdTime = now - day
        alarmDao.deleteExpiredAlarms(thresholdTime)
        
        val remainingAlarms = alarmDao.getAllAlarms().first()
        assertThat(remainingAlarms).hasSize(1)
        assertThat(remainingAlarms[0].eventTitle).isEqualTo("Recent")
    }

    // === Transaction Tests ===

    @Test
    fun insertRuleAndAlarms_transactionally() = runTest {
        val rule = createTestRule("Test Rule", "meeting")
        val alarm1 = createTestAlarm("event1", rule.id, "Meeting 1")
        val alarm2 = createTestAlarm("event2", rule.id, "Meeting 2")
        
        runBlocking {
            ruleDao.insertRule(rule)
            alarmDao.insertAlarms(listOf(alarm1, alarm2))
        }
        
        val rules = ruleDao.getAllRules().first()
        val alarms = alarmDao.getAlarmsByRuleId(rule.id).first()
        
        assertThat(rules).hasSize(1)
        assertThat(alarms).hasSize(2)
    }

    @Test
    fun ruleValidation_enforcesConstraints() = runTest {
        val validRule = createTestRule("Valid", "meeting", leadTime = 30)
        ruleDao.insertRule(validRule)
        
        val retrievedRule = ruleDao.getRuleById(validRule.id)
        assertThat(retrievedRule?.isValid()).isTrue()
        
        // Test the validation logic
        val invalidRule = validRule.copy(name = "", keywordPattern = "")
        assertThat(invalidRule.isValid()).isFalse()
    }

    // === Helper Methods ===

    private fun createTestRule(
        name: String,
        keywordPattern: String,
        calendarIds: List<Long> = listOf(1L),
        leadTime: Int = 30,
        enabled: Boolean = true
    ): Rule {
        return Rule(
            id = UUID.randomUUID().toString(),
            name = name,
            keywordPattern = keywordPattern,
            isRegex = Rule.autoDetectRegex(keywordPattern),
            calendarIds = calendarIds,
            leadTimeMinutes = leadTime,
            enabled = enabled
        )
    }

    private fun createTestAlarm(
        eventId: String,
        ruleId: String,
        eventTitle: String,
        alarmTime: Long = System.currentTimeMillis() + 3600000, // 1 hour from now
        dismissed: Boolean = false
    ): ScheduledAlarm {
        return ScheduledAlarm(
            id = UUID.randomUUID().toString(),
            eventId = eventId,
            ruleId = ruleId,
            eventTitle = eventTitle,
            eventStartTimeUtc = alarmTime + 1800000, // Event 30 min after alarm
            alarmTimeUtc = alarmTime,
            scheduledAt = System.currentTimeMillis(),
            userDismissed = dismissed,
            pendingIntentRequestCode = (eventId + ruleId).hashCode(),
            lastEventModified = System.currentTimeMillis()
        )
    }
}