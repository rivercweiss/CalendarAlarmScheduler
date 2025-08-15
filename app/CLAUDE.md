# 📅 Calendar Alarm Scheduler (Android)

## 1. Overview

This app automatically sets **unmissable system alarms** a configurable amount of time before certain Google Calendar events. Users define **rules** to trigger alarms only for events whose titles contain specific keywords.

**Core Requirement**: Alarms MUST play regardless of phone state (silent mode, do not disturb, low power mode, etc.) to ensure users never miss important events.

The app runs **fully locally** with **no backend**, using Android APIs to read calendar data and set exact alarms.

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
   * Runtime permissions for `READ_CALENDAR`, `SCHEDULE_EXACT_ALARM` (Android 12+), `USE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, and background operation permissions.
   * The app also requires notification permissions.
   * **Critical**: App cannot function without these permissions - block all functionality until granted.
   * Advanced battery optimization tracking and OEM-specific battery management detection.

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
   * Creates alarm notification that works in all phone states.
   * Tracks scheduled and user-dismissed alarms to avoid duplicates.

5. **UI Layer**
   * **Rule Management Screen** – Add/edit/delete keyword-based rules with calendar filters.
   * **Calendar Event Preview** – Shows future events for the next 2 days with a single toggle to filter between "all upcoming events" and "events matching rules only". Displays scheduled alarms with timezone info.
   * **Settings Screen** – Configure refresh interval, all-day event default time, permissions status. All settings must display currently selected values in real-time.
   * **Permission Onboarding** – Step-by-step permission granting with explanations.

6. **Background Worker**
   * Periodically scans for new/changed calendar events using `LAST_MODIFIED` field.
   * Updates alarms without requiring the app to be open.
   * Handles timezone changes reactively via dedicated TimezoneChangeReceiver.

7. **Advanced Battery Management**
   * **DozeCompatibilityUtils**: Comprehensive Doze mode detection and compatibility testing.
   * **BackgroundUsageDetector**: Multi-method background usage permission detection across Android versions.
   * **OEM-Specific Detection**: Automatic detection and recommendations for Samsung, Xiaomi, Huawei, OnePlus, Oppo, Vivo, and other manufacturers.
   * Battery optimization setup tracking with attempt counting and method used.

8. **Error Handling & Reliability**
   * **RetryManager**: Exponential backoff retry logic for critical operations.
   * **ErrorNotificationManager**: User notification system for persistent errors with actionable intents.
   * **CrashHandler**: Global exception handling with comprehensive logging and recovery.

9. **Reactive Settings System**
   * StateFlow-based settings with atomic updates.
   * Settings migration system with versioning.
   * Battery optimization completion tracking.
   * Defensive refresh mechanisms for UI consistency.

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
* When multiple rules match the same calendar event, separate alarms are created for **each matching rule**
* This allows users to set up multiple alarms with different lead times for important events
* Each alarm gets unique `pendingIntentRequestCode` based on `(eventId + ruleId).hashCode()`
* Example: If you have rules for "Meeting" (30 min lead) and "Important" (60 min lead), and an event titled "Important Meeting" matches both rules, you'll get two alarms: one 60 minutes before and one 30 minutes before the event

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

---

#### Logging Infrastructure

#### Log Tags & Levels
All app logs MUST use prefix `CalendarAlarmScheduler_` with categories:
- `*_Logger`: General app information
- `*_CrashHandler`: Uncaught exceptions
- `*_Performance_*`: Timing metrics for operations
- `*_Lifecycle_*`: Activity/Fragment state changes
- `*_Database_*`: Room operations with timing
- `*_Permission_*`: Permission state changes
- `*_DozeCompatibility`: Battery optimization detection
- `*_BackgroundUsageDetector`: Background usage permission checks
- `*_RetryManager`: Retry attempts for failed operations
- `*_ErrorNotificationManager`: Error notification events

---