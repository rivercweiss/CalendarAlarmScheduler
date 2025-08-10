package com.example.calendaralarmscheduler.utils

import android.util.Log
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.random.Random

class RetryManager {
    companion object {
        private const val TAG = "CalendarAlarmScheduler_RetryManager"
        private const val MAX_RETRIES = 3
        private const val BASE_DELAY_MS = 1000L // 1 second
        private const val MAX_DELAY_MS = 30000L // 30 seconds
        
        /**
         * Executes an operation with exponential backoff retry logic
         */
        suspend fun <T> withRetry(
            operation: String,
            maxRetries: Int = MAX_RETRIES,
            onRetry: (attempt: Int, error: Exception) -> Unit = { _, _ -> },
            block: suspend () -> T
        ): Result<T> {
            var lastException: Exception? = null
            
            repeat(maxRetries + 1) { attempt ->
                try {
                    Log.d(TAG, "Executing operation '$operation' (attempt ${attempt + 1}/${maxRetries + 1})")
                    val result = block()
                    
                    if (attempt > 0) {
                        Log.i(TAG, "Operation '$operation' succeeded on attempt ${attempt + 1}")
                    }
                    
                    return Result.success(result)
                } catch (e: Exception) {
                    lastException = e
                    Log.w(TAG, "Operation '$operation' failed on attempt ${attempt + 1}: ${e.message}")
                    
                    if (attempt < maxRetries) {
                        val delayMs = calculateBackoffDelay(attempt)
                        Log.d(TAG, "Retrying operation '$operation' in ${delayMs}ms")
                        
                        onRetry(attempt + 1, e)
                        delay(delayMs)
                    }
                }
            }
            
            Log.e(TAG, "Operation '$operation' failed after ${maxRetries + 1} attempts", lastException)
            return Result.failure(lastException ?: Exception("Unknown error"))
        }
        
        /**
         * Calculate exponential backoff delay with jitter
         */
        private fun calculateBackoffDelay(attempt: Int): Long {
            // Exponential backoff: base_delay * (2^attempt)
            val exponentialDelay = BASE_DELAY_MS * (2.0.pow(attempt)).toLong()
            
            // Cap at maximum delay
            val cappedDelay = minOf(exponentialDelay, MAX_DELAY_MS)
            
            // Add jitter (±20% random variation)
            val jitterRange = (cappedDelay * 0.2).toLong()
            val jitter = Random.nextLong(-jitterRange, jitterRange + 1)
            
            return maxOf(cappedDelay + jitter, BASE_DELAY_MS)
        }
        
        /**
         * Check if an exception is worth retrying
         */
        fun isRetriableException(exception: Exception): Boolean {
            return when (exception) {
                is SecurityException -> false // Don't retry permission issues
                is IllegalArgumentException -> false // Don't retry invalid arguments
                is IllegalStateException -> true // Might be temporary state issue
                else -> true // Default to retrying other exceptions
            }
        }
    }
}