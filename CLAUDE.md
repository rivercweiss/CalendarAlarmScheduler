# 📅 Calendar Alarm Scheduler (Android)

## 1. Overview

This app automatically sets **unmissable system alarms** a configurable amount of time before certain Google Calendar events. Users define **rules** to trigger alarms only for events whose titles contain specific keywords.

**Core Requirement**: Alarms MUST play regardless of phone state (silent mode, do not disturb, low power mode, etc.) to ensure users never miss important events.

The app runs **fully locally** with **no backend**, using Android APIs to read calendar data and set exact alarms.

---

## Tracking

The PLAN.md file contains the implementation plan and current status. Please use this as needed, and when making changes always update the file to show what was done.

Do not skip the Quick Verification section for each step of the implementation.

## 2. System Architecture

**Main Components:**

1. **Permissions Layer**
   * Runtime permissions for `READ_CALENDAR`, `SCHEDULE_EXACT_ALARM` (Android 12+), `USE_EXACT_ALARM`, and background operation permissions.
   * **Critical**: App cannot function without these permissions - block all functionality until granted.

2. **Google Calendar Integration**
   * Uses `CalendarContract` content provider to query local Google Calendar events.
   * Supports **background refresh** with `WorkManager` (user-configurable: 5, 15, 30, 60 minutes, default 30).
   * **Lookahead window**: 2 days from current time.

3. **Rule Engine**
   * Stores user-defined rules (keyword match + lead time + calendar filter + lead time before event start).
   * Auto-detects regex vs simple case-insensitive contains matching.
   * **Per-rule calendar filtering** - users select which calendars each rule monitors.

4. **Alarm Scheduler**
   * Uses `AlarmManager.setExactAndAllowWhileIdle()` to set exact alarms.
   * Creates **full-screen alarm activity** that works in all phone states.
   * Tracks scheduled and user-dismissed alarms to avoid duplicates.

5. **UI Layer**
   * **Rule Management Screen** – Add/edit/delete keyword-based rules with calendar filters.
   * **Calendar Event Preview** – Show matching events and scheduled alarms with timezone info.
   * **Settings Screen** – Configure refresh interval, all-day event default time, permissions status.
   * **Permission Onboarding** – Step-by-step permission granting with explanations.

6. **Background Worker**
   * Periodically scans for new/changed calendar events using `LAST_MODIFIED` field.
   * Updates alarms without requiring the app to be open.
   * Handles timezone changes reactively.

---

## 3. File Structure

```
app/
 ├─ data/
 │   ├─ database/
 │   │   ├─ AppDatabase.kt          # Room database
 │   │   ├─ RuleDao.kt             # Rules table access
 │   │   └─ AlarmDao.kt            # Alarms table access  
 │   ├─ CalendarRepository.kt       # Queries Google Calendar
 │   ├─ RuleRepository.kt           # Stores user rules in Room
 │   └─ AlarmRepository.kt          # Tracks scheduled alarms
 │
 ├─ domain/
 │   ├─ models/
 │   │   ├─ CalendarEvent.kt
 │   │   ├─ Rule.kt
 │   │   └─ ScheduledAlarm.kt
 │   ├─ RuleMatcher.kt              # Matches events to rules (regex auto-detect)
 │   └─ AlarmScheduler.kt           # Sets alarms via AlarmManager
 │
 ├─ ui/
 │   ├─ MainActivity.kt
 │   ├─ onboarding/
 │   │   └─ PermissionOnboardingActivity.kt
 │   ├─ rules/
 │   │   ├─ RuleListFragment.kt
 │   │   └─ RuleEditFragment.kt
 │   ├─ preview/
 │   │   └─ EventPreviewFragment.kt
 │   ├─ settings/
 │   │   └─ SettingsFragment.kt
 │   └─ alarm/
 │       └─ AlarmActivity.kt        # Full-screen unmissable alarm
 │
 ├─ workers/
 │   └─ CalendarRefreshWorker.kt    # Periodic background refresh
 │
 ├─ receivers/
 │   ├─ AlarmReceiver.kt            # Handles alarm broadcasts
 │   └─ BootReceiver.kt             # Re-registers alarms after reboot
 │
 ├─ utils/
 │   ├─ PermissionUtils.kt
 │   └─ TimezoneUtils.kt
 │
 └─ test/
     └─ calendar_events/
         └─ TestEvents.ics          # Test calendar events for development
```

---

## 4. Database Schema (Room)

### Rules Table
```kotlin
@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val keywordPattern: String,
    val isRegex: Boolean,                    // Auto-detected
    val calendarIds: List<Long>,             // Per-rule calendar filter
    val leadTimeMinutes: Int,                // 1 minute to 7 days
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
```

### Alarms Table
```kotlin
@Entity(tableName = "alarms")
data class ScheduledAlarm(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val eventId: String,
    val ruleId: String,
    val eventTitle: String,
    val eventStartTimeUtc: Long,
    val alarmTimeUtc: Long,
    val scheduledAt: Long,
    val userDismissed: Boolean = false,      // Track manual dismissals
    val pendingIntentRequestCode: Int,
    val lastEventModified: Long              // From CalendarContract
)
```

---

## 6. Permission Handling

### Required Permissions
```xml
<uses-permission android:name="android.permission.READ_CALENDAR" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.VIBRATE" />
```

### Onboarding Flow
1. **Welcome Screen** - Explain app purpose and alarm reliability requirement
2. **Calendar Permission** - Request READ_CALENDAR with rationale
3. **Exact Alarm Permission** - Take user to system settings for SCHEDULE_EXACT_ALARM
4. **Battery Optimization** - Guide user to whitelist app with clear benefits explanation
5. **Permission Status Dashboard** - Always visible indicator of permission health

---

## 7. Core Features & Edge Cases

### Timezone Handling
* Store all times in UTC, convert to local for display
* React to timezone changes by rescheduling all active alarms
* Show timezone indicator in UI

### All-Day Events
* Global user setting for default alarm time (e.g., "9:00 PM day before" or "8:00 AM day of")
* Apply lead time from this default time

### Event Change Detection
* Use `CalendarContract.Events.LAST_MODIFIED` to detect changes
* When event changes: cancel old alarm, reschedule new one (unless user dismissed)
* Treat changed events as "new" for user-dismissed tracking

### User-Dismissed Alarms
* When user manually removes alarm from system, mark `userDismissed = true`
* Never reschedule dismissed alarms for that specific event
* If event changes (new `lastModified`), treat as new event and allow rescheduling

### Multiple Rules Matching
* If multiple rules match same event, schedule multiple alarms
* Each gets unique `pendingIntentRequestCode` based on `(eventId + ruleId).hashCode()`

### Device Reboot
* `BootReceiver` queries active alarms from database
* Re-registers all non-dismissed, future alarms with `AlarmManager`

---

## 10. Background Refresh Configuration

**WorkManager Setup:**
* Periodic work request with user-configurable intervals: 5, 15, 30, 60 minutes
* Default: 30 minutes
* No network constraints (reading local calendar provider)
* Battery optimization warning if delays detected

**Refresh Logic:**
1. Query events in next 2 days with `lastModified > lastSyncTime`
2. Apply all enabled rules to find matches
3. Schedule new alarms, update changed ones, clean up obsolete ones
4. Update `lastSyncTime`

---
### Compile

export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew compileDebugKotlin

### Debug & Logging

The app has a comprehensive logging system with automated collection tools.

#### ADB Log Collection (Automated)
- **Quick crash logs**: `./collect_logs.sh quick` or `./collect_logs.sh`
  - Collects recent crashes, fatal errors, and exceptions
  - Saves to `crash_logs.txt` (filtered for problems only)
- **Detailed app logs**: `./collect_logs.sh detailed` 
  - Collects crash logs + CalendarAlarmScheduler-specific logs with context
  - Saves to `crash_logs.txt` + `detailed_logs.txt`
- **Live monitoring**: `./collect_logs.sh live`
  - Real-time log capture for reproducing issues
  - Saves to `live_logs.txt` with timestamps
  - Press Ctrl+C to stop monitoring
- **Clear old logs**: `./collect_logs.sh clear`
  - Removes all local log files
- **Collect all logs**: `./collect_logs.sh all`
  - Combines crash + detailed collection in one command

#### Log File Contents
- **crash_logs.txt**: Fatal exceptions, AndroidRuntime crashes, process deaths
- **detailed_logs.txt**: App-specific logs with surrounding context (±5-10 lines)
- **live_logs.txt**: Real-time app logs during issue reproduction

#### App Logging System
The app uses a multi-level logging system with these components:
- **Logger Utility** (`utils/Logger.kt`): Performance tracking, file output
- **CrashHandler** (`utils/CrashHandler.kt`): Global exception handling
- **Lifecycle Logging**: Activity/Fragment state changes
- **Database Logging**: Room operations with timing
- **Permission Tracking**: User permission interactions

#### Log Tags & Levels
All app logs use prefix `CalendarAlarmScheduler_` with categories:
- `*_Logger`: General app information
- `*_CrashHandler`: Uncaught exceptions
- `*_Performance_*`: Timing metrics
- `*_Lifecycle_*`: Activity/Fragment states
- `*_Database_*`: Room operations
- `*_Permission_*`: Permission states

#### Debugging Workflow
1. **For crashes**: Run `./collect_logs.sh all`
2. **Read crash_logs.txt** for AndroidRuntime FATAL EXCEPTION
3. **Check detailed_logs.txt** for app context around crash time
4. **For intermittent issues**: Use `./collect_logs.sh live`, reproduce issue, Ctrl+C
5. **Look for patterns**: Performance issues, permission denials, database errors

#### Common Issue Patterns
- **Layout inflation errors**: Look for `InflateException` and XML line numbers
- **Permission issues**: Search logs for `Permission` and `DENIED`
- **Database crashes**: Look for `Room`, `SQLite`, or `Database` tags
- **Memory issues**: Search for `OutOfMemory` or `GC_` logs
- **Performance problems**: Check `*_Performance_*` timing logs

#### Manual ADB Commands (if needed)
- **Check devices**: `/Users/riverweiss/Library/Android/sdk/platform-tools/adb devices`
- **Recent crashes**: `/Users/riverweiss/Library/Android/sdk/platform-tools/adb logcat -t 1000 | grep -E "(CalendarAlarmScheduler|AndroidRuntime|FATAL|EXCEPTION|CRASH)"`
- **App-specific logs**: `/Users/riverweiss/Library/Android/sdk/platform-tools/adb logcat -s "CalendarAlarmScheduler:*" -v time`
- **Clear logcat**: `/Users/riverweiss/Library/Android/sdk/platform-tools/adb logcat -c`

---

## Completion Criteria

**Phase 1 Complete When:**
- All permissions can be granted through onboarding
- Users can create rules and see matching calendar events
- Alarms fire reliably and show unmissable full-screen activity
- Basic CRUD operations work for rules and alarms

**Phase 2 Complete When:**
- Background worker runs on schedule and updates alarms
- Event changes are detected and alarms updated accordingly
- User-dismissed alarms stay dismissed until events change
- Timezone changes properly trigger alarm rescheduling

**Phase 3 Complete When:**
- All edge cases handled gracefully
- Settings screen provides full control
- Event preview shows accurate real-time data
- Battery optimization guidance helps users maintain reliability

**Project Complete When:**
- All automated tests pass
- Manual testing checklist completed
- App works reliably across different Android versions
- Documentation updated with any architecture changes discovered during implementation