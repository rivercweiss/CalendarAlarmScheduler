package com.example.calendaralarmscheduler.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import java.time.*

object TimezoneUtils {
    private const val TAG = "TimezoneUtils"

    /**
     * Check if current time is during daylight saving time
     */
    fun isCurrentlyDST(zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
        return ZonedDateTime.now(zoneId).zone.rules.isDaylightSavings(Instant.now())
    }

    /**
     * Get user-friendly timezone display name
     */
    fun getTimezoneDisplayName(zoneId: ZoneId = ZoneId.systemDefault()): String {
        val zonedDateTime = ZonedDateTime.now(zoneId)
        val offset = zonedDateTime.offset
        val isDST = zoneId.rules.isDaylightSavings(Instant.now())
        
        return "${zoneId.id} (UTC${offset}) ${if (isDST) "DST" else ""}"
    }

    /**
     * Register timezone change listener
     */
    fun registerTimezoneChangeListener(context: Context, onTimezoneChanged: () -> Unit): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_TIMEZONE_CHANGED -> {
                        Log.i(TAG, "Timezone changed, triggering callback")
                        onTimezoneChanged()
                    }
                    Intent.ACTION_TIME_CHANGED -> {
                        Log.i(TAG, "System time changed, triggering callback")
                        onTimezoneChanged()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
        }

        context.registerReceiver(receiver, filter)
        return receiver
    }

    /**
     * Unregister timezone change listener
     */
    fun unregisterTimezoneChangeListener(context: Context, receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver was not registered: $e")
        }
    }
}