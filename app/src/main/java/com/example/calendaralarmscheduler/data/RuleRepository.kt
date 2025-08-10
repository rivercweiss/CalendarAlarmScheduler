package com.example.calendaralarmscheduler.data

import com.example.calendaralarmscheduler.data.database.RuleDao
import com.example.calendaralarmscheduler.data.database.entities.Rule
import com.example.calendaralarmscheduler.domain.RuleAlarmManager
import com.example.calendaralarmscheduler.utils.Logger
import com.example.calendaralarmscheduler.utils.CrashHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
class RuleRepository(
    private val ruleDao: RuleDao
) {
    private val crashHandler = CrashHandler()
    
    // RuleAlarmManager will be injected when needed
    private var ruleAlarmManager: RuleAlarmManager? = null
    
    init {
        Logger.i("RuleRepository", "RuleRepository initialized")
    }
    
    /**
     * Inject the RuleAlarmManager for operations that require alarm management.
     * This is set by the Application class or ViewModel.
     */
    fun setRuleAlarmManager(manager: RuleAlarmManager) {
        ruleAlarmManager = manager
    }
    fun getAllRules(): Flow<List<Rule>> = ruleDao.getAllRules()
    
    fun getEnabledRules(): Flow<List<Rule>> = ruleDao.getEnabledRules()
    
    suspend fun getRuleById(id: String): Rule? = ruleDao.getRuleById(id)
    
    fun getRulesByCalendarId(calendarId: Long): Flow<List<Rule>> = 
        ruleDao.getRulesByCalendarId(calendarId)
    
    suspend fun insertRule(rule: Rule) {
        try {
            val startTime = System.currentTimeMillis()
            Logger.d("RuleRepository", "Inserting rule: ${rule.name} (${rule.id})")
            ruleDao.insertRule(rule)
            val time = System.currentTimeMillis() - startTime
            Logger.logDatabase("INSERT", "rules", "Rule ${rule.name}", time)
        } catch (e: Exception) {
            Logger.e("RuleRepository", "Failed to insert rule: ${rule.name}", e)
            crashHandler.logNonFatalException("RuleRepository", "Insert rule failed", e)
            throw e
        }
    }
    
    suspend fun insertRules(rules: List<Rule>) {
        try {
            val startTime = System.currentTimeMillis()
            Logger.d("RuleRepository", "Inserting ${rules.size} rules")
            ruleDao.insertRules(rules)
            val time = System.currentTimeMillis() - startTime
            Logger.logDatabase("INSERT", "rules", "${rules.size} rules", time)
        } catch (e: Exception) {
            Logger.e("RuleRepository", "Failed to insert ${rules.size} rules", e)
            crashHandler.logNonFatalException("RuleRepository", "Insert rules failed", e)
            throw e
        }
    }
    
    suspend fun updateRule(rule: Rule) = ruleDao.updateRule(rule)
    
    suspend fun deleteRule(rule: Rule) = ruleDao.deleteRule(rule)
    
    suspend fun deleteRuleById(id: String) = ruleDao.deleteRuleById(id)
    
    suspend fun setRuleEnabled(id: String, enabled: Boolean) = 
        ruleDao.setRuleEnabled(id, enabled)
    
    // Business logic methods
    fun getActiveRulesForCalendars(calendarIds: List<Long>): Flow<List<Rule>> {
        return getEnabledRules().map { rules ->
            rules.filter { rule ->
                rule.calendarIds.any { calendarId -> calendarId in calendarIds }
            }
        }
    }
    
    suspend fun toggleRuleEnabled(id: String) {
        val rule = getRuleById(id)
        rule?.let { 
            setRuleEnabled(id, !it.enabled)
        }
    }
    
    suspend fun getAllRulesSync(): List<Rule> = ruleDao.getAllRulesSync()
    
    /**
     * Updates a rule's enabled status with proper alarm management.
     * When disabling: cancels all associated alarms.
     * When enabling: schedules alarms for matching events.
     */
    suspend fun updateRuleEnabledWithAlarmManagement(rule: Rule, enabled: Boolean): RuleAlarmManager.RuleUpdateResult {
        return ruleAlarmManager?.updateRuleEnabled(rule, enabled) ?: run {
            // Fallback to simple update if RuleAlarmManager not available
            Logger.w("RuleRepository", "RuleAlarmManager not available, falling back to simple rule update")
            updateRule(rule.copy(enabled = enabled))
            RuleAlarmManager.RuleUpdateResult(
                success = true,
                message = "Rule updated (no alarm management)",
                alarmsAffected = 0
            )
        }
    }
    
    /**
     * Updates a rule with proper alarm management.
     * Cancels old alarms and schedules new ones based on the updated rule.
     */
    suspend fun updateRuleWithAlarmManagement(oldRule: Rule, newRule: Rule): RuleAlarmManager.RuleUpdateResult {
        return ruleAlarmManager?.updateRuleWithAlarmManagement(oldRule, newRule) ?: run {
            // Fallback to simple update if RuleAlarmManager not available
            Logger.w("RuleRepository", "RuleAlarmManager not available, falling back to simple rule update")
            updateRule(newRule)
            RuleAlarmManager.RuleUpdateResult(
                success = true,
                message = "Rule updated (no alarm management)",
                alarmsAffected = 0
            )
        }
    }
    
    /**
     * Deletes a rule with proper alarm cleanup.
     */
    suspend fun deleteRuleWithAlarmCleanup(rule: Rule): RuleAlarmManager.RuleUpdateResult {
        return ruleAlarmManager?.deleteRuleWithAlarmCleanup(rule) ?: run {
            // Fallback to simple deletion if RuleAlarmManager not available
            Logger.w("RuleRepository", "RuleAlarmManager not available, falling back to simple rule deletion")
            deleteRule(rule)
            RuleAlarmManager.RuleUpdateResult(
                success = true,
                message = "Rule deleted (no alarm cleanup)",
                alarmsAffected = 0
            )
        }
    }
    
    suspend fun createDefaultRules(): List<Rule> {
        try {
            Logger.i("RuleRepository", "Creating default rules")
            val defaultRules = listOf(
                Rule(
                    name = "Meetings",
                    keywordPattern = "meeting",
                    isRegex = false,
                    calendarIds = emptyList(), // All calendars
                    leadTimeMinutes = 15
                ),
                Rule(
                    name = "Appointments", 
                    keywordPattern = "appointment",
                    isRegex = false,
                    calendarIds = emptyList(), // All calendars
                    leadTimeMinutes = 30
                )
            )
            insertRules(defaultRules)
            Logger.i("RuleRepository", "Default rules created successfully")
            return defaultRules
        } catch (e: Exception) {
            Logger.e("RuleRepository", "Failed to create default rules", e)
            crashHandler.logNonFatalException("RuleRepository", "Create default rules failed", e)
            throw e
        }
    }
}