package com.example.calendaralarmscheduler.data

import com.example.calendaralarmscheduler.data.database.RuleDao
import com.example.calendaralarmscheduler.data.database.entities.Rule
import com.example.calendaralarmscheduler.utils.Logger
import com.example.calendaralarmscheduler.utils.CrashHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
/**
 * Repository for managing alarm rules with basic CRUD operations.
 * Handles rule persistence and provides data access for rule management.
 */
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
    
    private suspend fun insertRules(rules: List<Rule>) {
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
    
    // Core business logic methods for rule management
    
    suspend fun getAllRulesSync(): List<Rule> = ruleDao.getAllRulesSync()
    
}