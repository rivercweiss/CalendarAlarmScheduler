package com.example.calendaralarmscheduler.data

import com.example.calendaralarmscheduler.data.database.RuleDao
import com.example.calendaralarmscheduler.data.database.entities.Rule
import com.example.calendaralarmscheduler.utils.Logger
import com.example.calendaralarmscheduler.utils.CrashHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
class RuleRepository(
    private val ruleDao: RuleDao
) {
    private val crashHandler = CrashHandler()
    
    init {
        Logger.i("RuleRepository", "RuleRepository initialized")
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