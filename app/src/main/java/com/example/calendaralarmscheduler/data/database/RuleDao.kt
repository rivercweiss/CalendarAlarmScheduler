package com.example.calendaralarmscheduler.data.database

import androidx.room.*
import com.example.calendaralarmscheduler.data.database.entities.Rule
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY createdAt DESC")
    fun getAllRules(): Flow<List<Rule>>
    
    @Query("SELECT * FROM rules ORDER BY createdAt DESC")
    suspend fun getAllRulesSync(): List<Rule>
    
    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY createdAt DESC")
    fun getEnabledRules(): Flow<List<Rule>>
    
    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun getRuleById(id: String): Rule?
    
    @Query("SELECT * FROM rules WHERE calendarIds LIKE '%' || :calendarId || '%'")
    fun getRulesByCalendarId(calendarId: Long): Flow<List<Rule>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: Rule)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<Rule>)
    
    @Update
    suspend fun updateRule(rule: Rule)
    
    @Delete
    suspend fun deleteRule(rule: Rule)
    
    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteRuleById(id: String)
    
    @Query("DELETE FROM rules")
    suspend fun deleteAllRules()
    
    @Query("UPDATE rules SET enabled = :enabled WHERE id = :id")
    suspend fun setRuleEnabled(id: String, enabled: Boolean)
}