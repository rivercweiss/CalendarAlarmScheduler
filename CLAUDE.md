# 📅 Calendar Alarm Scheduler (Android)

## 1. Overview

This app automatically sets **unmissable system alarms** a configurable amount of time before certain Google Calendar events. Users define **rules** to trigger alarms only for events whose titles contain specific keywords.

**Core Requirement**: Alarms MUST play regardless of phone state (silent mode, do not disturb, low power mode, etc.) to ensure users never miss important events.

The app runs **fully locally** with **no backend**, using Android APIs to read calendar data and set exact alarms.

---

## Development Workflow

When working on hard problems, always tackle them small step at a time, verifying along the way things still compile and run successfully.

Debugging:
- Always use Logs or other sources of hard data to determine the root cause of bugs and other issues

Tests:
- When developing tests, we only want to develop end to end tests
- The test development workflow for new tests should be
  - Implement a minimum test, checking if it can be added to an existing test file
  - Validate the test compiles and runs
  - If it doesn't, fix any errors
  - Add more full functionality
  - Validate the test compiles and runs
- You should always keep existing tests intact and unchanged as much as possible
- You should always make sure the test is checking full functionality, including UI input
- Never allow test to pass without having checked full functionality.

---

## Supported Android Versions

This app only runs on min SDK version of 26, with a target of 34, so please optimize for use on 34.

---

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
   * **Settings Screen** – Configure refresh interval, all-day event default time, permissions status. All settings must display currently selected values in real-time.
   * **Permission Onboarding** – Step-by-step permission granting with explanations.

6. **Background Worker**
   * Periodically scans for new/changed calendar events using `LAST_MODIFIED` field.
   * Updates alarms without requiring the app to be open.
   * Handles timezone changes reactively.

---

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
* **IMPORTANT**: For all-day events, alarms fire at EXACTLY the chosen time - NO lead time is applied
* Lead time rules only apply to regular timed events, not all-day events
* This ensures consistent alarm behavior for all-day events regardless of rule settings

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

#### Manual ADB Commands (if needed)
- **Check devices**: `/Users/riverweiss/Library/Android/sdk/platform-tools/adb devices`
- **Recent crashes**: `/Users/riverweiss/Library/Android/sdk/platform-tools/adb logcat -t 1000 | grep -E "(CalendarAlarmScheduler|AndroidRuntime|FATAL|EXCEPTION|CRASH)"`
- **App-specific logs**: `/Users/riverweiss/Library/Android/sdk/platform-tools/adb logcat -s "CalendarAlarmScheduler:*" -v time`
- **Clear logcat**: `/Users/riverweiss/Library/Android/sdk/platform-tools/adb logcat -c`