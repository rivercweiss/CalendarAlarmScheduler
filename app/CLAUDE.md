# 📅 Calendar Alarm Scheduler (Android)

## 1. Overview

This app automatically sets **unmissable system alarms** a configurable amount of time before certain Google Calendar events. Users define **rules** to trigger alarms only for events whose titles contain specific keywords.

**Core Requirement**: Alarms MUST play regardless of phone state (silent mode, do not disturb, low power mode, etc.) to ensure users never miss important events.

The app runs **fully locally** with **no backend**, using Android APIs to read calendar data and set exact alarms.

---

# Key Coding Goals

Try to do everything as ***simply***, ***modularly*** and ***robustly*** as possible, while *deleting as much code as possible*, while *maintaining core functionality*. Think hard

---

## Development Workflow

Don't worry about compatibility, rather keep the code clean and robust.
Remember, we want to try to ONLY edit existing files, not creating new files unless absolutely necessary.
Always use output data, screenshots of the app, or logs (only hard data) to determine the root cause of bugs and other issues.

---

## Codebase Index Files

To help Claude understand the codebase quickly, two index files are maintained:

### general_index.md
- **Purpose**: Quick overview of all source files in the codebase
- **Content**: File paths with simple descriptions of what each file does
- **Use Case**: When you need to quickly locate functionality or understand project structure

### detailed_index.md  
- **Purpose**: Comprehensive function documentation for the entire codebase
- **Content**: All functions in each file with signatures, modifiers, and descriptions
- **Use Case**: When you need to understand specific function implementations or API contracts

**Important Notes:**
- These index files may not always be up-to-date with the latest code changes
- When making significant changes that affect file structure or function signatures, please update the relevant index files
- Use these files as a starting point for understanding the codebase, but always verify against the actual source files when making changes

---

## E2E Testing Framework 

- We are ONLY making ONE end to end test, no other tests.
- Please never make a new test file, only use the ComprehensiveE2ETest
- You should always keep existing tests intact and unchanged as much as possible
- You should always make sure the test is checking full functionality, including UI input
- Never allow test to pass without having checked full functionality.
- NEVER skip tests just to make the test pass.

**Single Test Approach**: We maintain ONE comprehensive end-to-end test (`ComprehensiveE2ETest`) that covers all app functionality using Espresso and UI Automator.

**Test Execution**: 
- Run with: `./run_e2e_test.sh`
- Framework handles: build, install, permissions, execution, metrics collection
- You (Claude) need to directly inspect the log outputs of the emulated or connected device to determine why the test passed or failed.

**Testing Components:**
- `TestMetricsCollector`: Memory tracking, performance metrics, memory leak detection
- `CalendarTestDataProvider`: Calendar event injection for controlled testing
- `TestTimeController`: Time acceleration for testing future calendar events
- `ComprehensiveE2ETest`: Main test with app launch, onboarding flow, metrics collection

**Test Guidance:**
- Only use `ComprehensiveE2ETest` - never create new test files
- Keep existing tests intact, add new functionality to existing methods
- Always test full user workflows, not isolated components
- Never allow tests to pass without checking complete functionality

---

## Supported Android Versions

This app only runs on min SDK version of 26, with a target of 34, so please optimize for use on 34.

---

## 2. System Architecture

**Main Components:**

1. **Permissions Layer**
   * Runtime permissions for `READ_CALENDAR`, `SCHEDULE_EXACT_ALARM` (Android 12+), `USE_EXACT_ALARM`, `POST_NOTIFICATIONS`, and background operation permissions.
   * The app also requires notification permissions.
   * **Critical**: App cannot function without these permissions - block all functionality until granted.
   * Simplified battery optimization detection and basic OEM recommendations.

2. **Google Calendar Integration**
   * Uses `CalendarContract` content provider to query local Google Calendar events.
   * Supports **background refresh** with `AlarmManager` (user-configurable: 1, 5, 15, 30, 60 minutes, default varies by build).
   * **Debug builds**: Default 1-minute interval with all options available for testing
   * **Release builds**: Default 30-minute interval, excludes 1-minute option for battery preservation
   * **Lookahead window**: 2 days from current time.

3. **Rule Engine**
   * Stores user-defined rules (keyword match + lead time + calendar filter + lead time before event start).
   * Auto-detects regex vs simple case-insensitive contains matching.
   * **Per-rule calendar filtering** - users select which calendars each rule monitors.
   * **First Event of Day Only** - optional toggle to limit rules to trigger only for the first matching event per day.

4. **Alarm Scheduler**
   * Uses `AlarmManager.setExactAndAllowWhileIdle()` to set exact alarms.
   * Creates unmissable notifications with alarm sound that bypass Do Not Disturb and silent mode.
   * Simple notification-based system - no full-screen activities.
   * Tracks scheduled and user-dismissed alarms to avoid duplicates.
   * **Premium Feature**: Event details in notifications are gated behind $2 in-app purchase.

5. **UI Layer**
   * **Rule Management Screen** – Add/edit/delete keyword-based rules with calendar filters.
   * **Calendar Event Preview** – Shows future events for the next 2 days with a single toggle to filter between "all upcoming events" and "events matching rules only". Displays scheduled alarms with timezone info.
   * **Settings Screen** – Configure refresh interval, all-day event default time, permissions status. **Premium section at top** with upgrade/purchase flow and debug toggle (debug builds only).
   * **Permission Onboarding** – Step-by-step permission granting with explanations.

6. **Background Refresh System**
   * **BackgroundRefreshManager**: Uses `AlarmManager.setExactAndAllowWhileIdle()` for guaranteed timing
   * **BackgroundRefreshReceiver**: Handles periodic and immediate refresh requests via broadcast receiver
   * Periodically scans for new/changed calendar events using `LAST_MODIFIED` field
   * Updates alarms without requiring the app to be open
   * **Self-rescheduling**: Each refresh automatically schedules the next refresh cycle
   * **Exact timing**: Bypasses Doze mode and battery optimization restrictions
   * Handles timezone changes reactively via dedicated TimezoneChangeReceiver

7. **Battery Management**
   * Simple battery optimization whitelist detection and management.
   * Background usage permission detection for reliable alarm delivery.
   * Generic OEM guidance for common manufacturers.
   * Battery optimization setup tracking.

8. **Day Tracking System**
   * **DayTrackingRepository**: Tracks which rules have triggered today for "first event of day only" functionality.
   * **DayResetService**: Schedules midnight alarms to reset day tracking at local timezone boundaries.
   * **DayResetReceiver**: Handles midnight reset broadcasts to clear day tracking state.
   * Timezone-aware day boundary calculations with automatic reset on timezone changes.

9. **Error Handling & Reliability**
   * **ErrorNotificationManager**: Simple generic error notification system.
   * **CrashHandler**: Basic uncaught exception handling with Android logcat logging.
   * **Logger**: Centralized logging with performance metrics.

10. **Settings System**
   * Simple SharedPreferences-based settings storage.
   * Settings for refresh interval, all-day event default time, and onboarding status.
   * Battery optimization completion tracking.
   * Premium purchase state tracking.

11. **Premium Features & Billing**
   * **Google Play Billing**: $2 one-time in-app purchase for event details in notifications.
   * **BillingManager**: Handles purchase flow, state management, and error handling.
   * **Notification Gating**: Free users see "Calendar Event", premium users see actual event titles.
   * **Debug Support**: Debug toggle for testing premium states (debug builds only).
   * **State Management**: Premium status cached in SharedPreferences with reactive UI updates.

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
* When multiple rules match the same calendar event, separate alarms are created for **each matching rule**
* This allows users to set up multiple alarms with different lead times for important events
* Each alarm gets unique `pendingIntentRequestCode` based on `(eventId + ruleId).hashCode()`
* Example: If you have rules for "Meeting" (30 min lead) and "Important" (60 min lead), and an event titled "Important Meeting" matches both rules, you'll get two alarms: one 60 minutes before and one 30 minutes before the event

### First Event of Day Only Rules
* **Purpose**: Allow rules to trigger only for the first matching event per calendar day
* **Use Case**: Morning routine alarms, daily standup reminders, first appointment notifications
* **Implementation**: Uses `DayTrackingRepository` to track which rules have triggered today
* **Day Boundaries**: Calculated using local timezone with automatic reset at midnight
* **Timezone Handling**: Day tracking resets when timezone changes to maintain correct day boundaries
* **Persistence**: Uses SharedPreferences with date-based keys for reliable cross-reboot tracking
* **Reset Mechanism**: `DayResetService` schedules exact midnight alarms using `AlarmManager.setExactAndAllowWhileIdle()`

### Device Reboot
* `BootReceiver` queries active alarms from database
* Re-registers all non-dismissed, future alarms with `AlarmManager`

### Calendar Preview
* The ability for users to preview calendar events to check that alarms are scheduled correctly.

### Premium Features
* **Notification Content Gating**: Free users see generic "Calendar Event" in alarm notifications, premium users see actual event titles and descriptions.
* **Purchase Flow**: $2 one-time purchase through Google Play Billing for event details in notifications.
* **Development Testing**: Debug toggle available in debug builds for testing premium states without Google Play Console setup.
* **State Persistence**: Premium status cached locally with immediate UI updates across app.

---

## 10. Background Refresh Configuration

**AlarmManager Setup:**
* **BackgroundRefreshManager**: Exact alarm scheduling with `setExactAndAllowWhileIdle()`
* **Available intervals**: 1, 5, 15, 30, 60 minutes (1-minute debug-only)
* **Debug builds**: Default 1-minute interval for rapid testing
* **Release builds**: Default 30-minute interval for battery optimization
* **Self-scheduling**: Each refresh schedules the next cycle automatically
* **Reliability**: Bypasses Doze mode and battery optimization restrictions

**Refresh Logic:**
1. **BackgroundRefreshReceiver** triggers on AlarmManager broadcast
2. Query events in next 2 days with `lastModified > lastSyncTime`
3. Apply all enabled rules to find matches
4. Schedule new alarms, update changed ones, clean up obsolete ones
5. Update `lastSyncTime`
6. **Auto-schedule**: Next refresh alarm set for configured interval

---

## Build Commands

### Quick Compile (Kotlin only)
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew compileDebugKotlin
```

### Full Debug Build
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew assembleDebug
```

### Clean Build (when dependencies change)
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew clean assembleDebug
```

**Notes:**
- Build output: `app/build/outputs/apk/debug/app-debug.apk`
- Build time: ~7s for clean build, ~3s for incremental
- **Removed WorkManager dependency** - now uses pure AlarmManager for background operations
- Premium features require Google Play Billing dependency (already included)

---

#### Logging Infrastructure

#### Log Tags & Levels
All app logs use prefix `CalendarAlarmScheduler_` with categories:
- `*_Logger`: General app information
- `*_CrashHandler`: Uncaught exceptions (basic logging only)
- `*_Performance_*`: Timing metrics for operations
- `*_Lifecycle_*`: Activity/Fragment state changes
- `*_Database_*`: Room operations with timing
- `*_Permission_*`: Permission state changes
- `*_BatteryOptimization`: Battery optimization detection and management
- `*_ErrorNotificationManager`: Generic error notification events
- `*_BillingManager`: Premium purchase flow and state changes
- `*_SettingsFragment`: Premium UI updates and debug toggles
- `*_BackgroundRefreshManager`: AlarmManager scheduling and status
- `*_BackgroundRefreshReceiver`: Background refresh execution and timing

**Note**: All logging goes to Android logcat only (no file logging for simplicity).

#### Debugging & Log Collection
For efficient debugging and log collection during development:

**Basic Log Monitoring:**
```bash
# Monitor specific components in real-time
/Users/riverweiss/Library/Android/sdk/platform-tools/adb logcat | grep -E "CalendarAlarmScheduler|CalendarRepository|BackgroundRefresh"

# Check recent logs from device buffer (faster than real-time)
/Users/riverweiss/Library/Android/sdk/platform-tools/adb logcat -d | grep "CalendarRepository" | tail -20

# Monitor background refresh activity specifically
/Users/riverweiss/Library/Android/sdk/platform-tools/adb logcat -d | grep -E "BackgroundRefreshReceiver|BackgroundRefreshManager|periodic refresh" | tail -15

# Monitor premium/billing activity specifically
/Users/riverweiss/Library/Android/sdk/platform-tools/adb logcat -d | grep -E "BillingManager|SettingsFragment.*Premium" | tail -10
```

**UI Navigation Debugging:**
```bash
# Dump UI hierarchy to find element coordinates
/Users/riverweiss/Library/Android/sdk/platform-tools/adb shell uiautomator dump /sdcard/ui_dump.xml
/Users/riverweiss/Library/Android/sdk/platform-tools/adb pull /sdcard/ui_dump.xml /tmp/ui_dump.xml

# Search for specific UI elements
grep -i "preview" /tmp/ui_dump.xml

# Tap UI elements using found coordinates
/Users/riverweiss/Library/Android/sdk/platform-tools/adb shell input tap 540 2232
```

**Manual Calendar Debugging Commands:**
```bash
# List all calendars with visibility status
/Users/riverweiss/Library/Android/sdk/platform-tools/adb shell content query --uri content://com.android.calendar/calendars --projection _id:calendar_displayName:visible:account_name

# List all calendar events to verify existence
/Users/riverweiss/Library/Android/sdk/platform-tools/adb shell content query --uri content://com.android.calendar/events --projection _id:title:dtstart:dtend:calendar_id

# Convert timestamp to readable date (macOS)
date -r 1755370800

# Check time differences for debugging lookahead windows
echo "Current time: $(date +%s)"; echo "Event start: 1755370800"; echo "Difference hours: $(( (1755370800 - $(date +%s)) / 3600 ))"
```

**Log Management Best Practices:**
- Clear logs before testing: `/Users/riverweiss/Library/Android/sdk/platform-tools/adb logcat -c`
- Use `-d` flag for device buffer (faster than real-time streaming)
- Filter by component-specific tags for focused debugging
- Use `tail -N` to limit output to recent entries
- Combine grep patterns with `|` for multiple components

---

# Key Coding Goals

Try to do everything as ***simply***, ***modularly*** and ***robustly*** as possible, while *deleting as much code as possible*, while *maintaining core functionality*. Think hard

