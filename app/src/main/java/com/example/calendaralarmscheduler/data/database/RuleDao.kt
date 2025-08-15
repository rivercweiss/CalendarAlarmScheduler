package com.example.calendaralarmscheduler.data.database

import androidx.room.*
import com.example.calendaralarmscheduler.data.database.entities.Rule
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for alarm rules with basic CRUD operations.
 * Simplified to include only essential rule management queries.
 */
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
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: Rule)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<Rule>)
    
    @Update
    suspend fun updateRule(rule: Rule)
    
    @Delete
    suspend fun deleteRule(rule: Rule)
    
}