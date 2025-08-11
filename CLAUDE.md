# 📅 Calendar Alarm Scheduler (Android)

## 1. Overview

This app automatically sets **unmissable system alarms** a configurable amount of time before certain Google Calendar events. Users define **rules** to trigger alarms only for events whose titles contain specific keywords.

**Core Requirement**: Alarms MUST play regardless of phone state (silent mode, do not disturb, low power mode, etc.) to ensure users never miss important events.

The app runs **fully locally** with **no backend**, using Android APIs to read calendar data and set exact alarms.

---

## Development Workflow

When working on hard problems, always tackle them small step at a time, verifying along the way things still compile and run successfully.

Don't worry about compatibility, rather keep the code clean and robust.

Debugging:
- Always use Logs or other sources of hard data to determine the root cause of bugs and other issues
- The logs are too big to directly read, you need to tail or grep (or a combination) to find relevant data

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
   * Creates **full-screen alarm activity** that works in all phone states.
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

## 3. File Structure

```
app/src/main/java/com/example/calendaralarmscheduler/
├── CalendarAlarmApplication.kt     # Application class with global initialization
├── data/
│   ├── AlarmRepository.kt         # Manages scheduled alarms
│   ├── CalendarRepository.kt      # Queries Google Calendar events
│   ├── RuleRepository.kt          # Manages user-defined rules
│   ├── SettingsRepository.kt      # Reactive settings with StateFlow
│   └── database/
│       ├── AppDatabase.kt         # Room database configuration
│       ├── AlarmDao.kt            # DAO for alarm operations
│       ├── RuleDao.kt             # DAO for rule operations
│       └── entities/
│           ├── Rule.kt            # Rule entity
│           └── ScheduledAlarm.kt  # Alarm entity
├── domain/
│   ├── AlarmScheduler.kt          # Schedules alarms via AlarmManager
│   ├── RuleMatcher.kt             # Matches events to rules
│   └── models/
│       ├── CalendarEvent.kt      # Calendar event model
│       ├── DuplicateHandlingMode.kt # Enum for duplicate handling
│       └── ScheduledAlarm.kt     # Domain alarm model
├── receivers/
│   ├── AlarmReceiver.kt          # Handles alarm broadcasts
│   ├── BootReceiver.kt           # Re-registers alarms after reboot
│   └── TimezoneChangeReceiver.kt # Handles timezone changes
├── ui/
│   ├── BaseFragment.kt           # Base fragment with lifecycle logging
│   ├── MainActivity.kt            # Main activity with navigation
│   ├── alarm/
│   │   └── AlarmActivity.kt      # Full-screen unmissable alarm
│   ├── onboarding/
│   │   ├── OnboardingPagerAdapter.kt
│   │   ├── OnboardingStepFragment.kt
│   │   └── PermissionOnboardingActivity.kt
│   ├── preview/
│   │   ├── EventPreviewAdapter.kt
│   │   ├── EventPreviewFragment.kt
│   │   └── EventPreviewViewModel.kt
│   ├── rules/
│   │   ├── CalendarPicker*.kt    # Calendar selection components
│   │   ├── LeadTimePicker*.kt    # Lead time selection
│   │   ├── RuleAdapter.kt
│   │   ├── RuleEditFragment.kt
│   │   ├── RuleEditViewModel.kt
│   │   ├── RuleListFragment.kt
│   │   └── RuleListViewModel.kt
│   └── settings/
│       ├── SettingsFragment.kt
│       └── SettingsViewModel.kt
├── utils/
│   ├── BackgroundUsageDetector.kt # Multi-method background detection
│   ├── CrashHandler.kt           # Global exception handling
│   ├── DozeCompatibilityUtils.kt # Doze mode & OEM detection
│   ├── ErrorNotificationManager.kt # Error notifications
│   ├── Logger.kt                 # Comprehensive logging system
│   ├── PermissionUtils.kt        # Permission utilities
│   ├── RetryManager.kt           # Exponential backoff retry
│   └── TimezoneUtils.kt          # Timezone conversions
└── workers/
    ├── CalendarRefreshWorker.kt  # Periodic background refresh
    └── WorkerManager.kt          # WorkManager configuration
```

---

## 4. Build Configuration

### Gradle Configuration (app/build.gradle.kts)
```kotlin
android {
    compileSdk = 34
    
    defaultConfig {
        minSdk = 26
        targetSdk = 34
    }
    
    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
}
```

### Key Dependencies
- Room database with KSP
- WorkManager for background tasks
- Navigation component with SafeArgs
- Kotlin Serialization for type converters
- Material Design Components

### Manifest Permissions
```xml
<uses-permission android:name="android.permission.READ_CALENDAR" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

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

### Debug & Logging

The app has a comprehensive multi-level logging system with automated collection tools.

#### Logging Infrastructure

**Core Components:**
- **Logger Utility** (`utils/Logger.kt`): 
  - Performance tracking with timing metrics
  - File output support for persistent logs
  - Context dumping for debugging
  - Lifecycle event tracking
- **CrashHandler** (`utils/CrashHandler.kt`): 
  - Global exception handling
  - Non-fatal exception logging
  - Stack trace capture and analysis
- **BaseFragment** (`ui/BaseFragment.kt`):
  - Automatic lifecycle logging for all fragments
  - Performance metrics for view creation
- **Error Notifications** (`utils/ErrorNotificationManager.kt`):
  - User-visible error notifications
  - Actionable intents for error recovery

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

#### Log Tags & Levels
All app logs use prefix `CalendarAlarmScheduler_` with categories:
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

#### Debugging Workflow
1. **For crashes**: Run `./collect_logs.sh all`
2. **Read crash_logs.txt** for AndroidRuntime FATAL EXCEPTION
3. **Check detailed_logs.txt** for app context around crash time
4. **For intermittent issues**: Use `./collect_logs.sh live`, reproduce issue, Ctrl+C
5. **Look for patterns**: Performance issues, permission denials, database errors
6. **Check battery optimization**: Look for DozeCompatibility and BackgroundUsageDetector logs
7. **Monitor retries**: Check RetryManager logs for failed operations

#### Manual ADB Commands (if needed)
- **Check devices**: `/Users/riverweiss/Library/Android/sdk/platform-tools/adb devices`
- **Recent crashes**: `/Users/riverweiss/Library/Android/sdk/platform-tools/adb logcat -t 1000 | grep -E "(CalendarAlarmScheduler|AndroidRuntime|FATAL|EXCEPTION|CRASH)"`
- **App-specific logs**: `/Users/riverweiss/Library/Android/sdk/platform-tools/adb logcat -s "CalendarAlarmScheduler:*" -v time`
- **Clear logcat**: `/Users/riverweiss/Library/Android/sdk/platform-tools/adb logcat -c`
- **Battery optimization logs**: `/Users/riverweiss/Library/Android/sdk/platform-tools/adb logcat -s "CalendarAlarmScheduler_DozeCompatibility:*" -v time`

---

## ADB App Interaction & Testing

### Overview
Reliable ADB-based interaction methods for testing and development tasks. All commands work consistently across different device states and screen sizes by using UI element discovery rather than fixed coordinates.

### Core ADB Utilities

#### UI Discovery & Navigation
```bash
# Get current UI structure and find elements
/Users/riverweiss/Library/Android/sdk/platform-tools/adb shell uiautomator dump
/Users/riverweiss/Library/Android/sdk/platform-tools/adb shell cat /sdcard/window_dump.xml

# Launch app fresh
/Users/riverweiss/Library/Android/sdk/platform-tools/adb shell "am start -S -n com.example.calendaralarmscheduler/.ui.MainActivity"

# Check current activity focus
/Users/riverweiss/Library/Android/sdk/platform-tools/adb shell dumpsys activity activities | grep -A 5 "mCurrentFocus"

# Get screen dimensions
/Users/riverweiss/Library/Android/sdk/platform-tools/adb shell wm size
```

### Reliable Element Finding Methods

#### Find Elements by Resource ID
```bash
# Extract clickable coordinates for any resource ID
get_element_coords() {
    local resource_id="$1"
    /Users/riverweiss/Library/Android/sdk/platform-tools/adb shell uiautomator dump
    /Users/riverweiss/Library/Android/sdk/platform-tools/adb shell cat /sdcard/window_dump.xml | grep -E "resource-id=\"$resource_id\".*bounds=\"\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]\"" | head -1
}

# Example usage:
# get_element_coords "com.example.calendaralarmscheduler:id/fab_add_rule"
```

#### Find Elements by Text Content
```bash
# Find button by visible text
find_by_text() {
    local text="$1"
    /Users/riverweiss/Library/Android/sdk/platform-tools/adb shell uiautomator dump
    /Users/riverweiss/Library/Android/sdk/platform-tools/adb shell cat /sdcard/window_dump.xml | grep -E "text=\"$text\".*bounds=\"\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]\"" | head -1
}

# Example usage:
# find_by_text "Select All"
```

#### Calculate Center Coordinates
```bash
# Given bounds [x1,y1][x2,y2], center is ((x1+x2)/2, (y1+y2)/2)
# Example: bounds="[42,706][1038,832]" -> center = (540, 769)
calc_center() {
    local bounds="$1"
    # Parse bounds and calculate center (implementation depends on shell capabilities)
    # For manual calculation: x1=42, y1=706, x2=1038, y2=832
    # Center: x=(42+1038)/2=540, y=(706+832)/2=769
}
```

### State Verification Commands

#### Verify Current Screen
```bash
# Check if we're on Rules tab (selected="true")
verify_rules_tab() {
    /Users/riverweiss/Library/Android/sdk/platform-tools/adb shell uiautomator dump
    /Users/riverweiss/Library/Android/sdk/platform-tools/adb shell cat /sdcard/window_dump.xml | grep -q 'resource-id="com.example.calendaralarmscheduler:id/nav_rules".*selected="true"'
    echo $?  # 0 if true, 1 if false
}

# Check if calendar picker dialog is open
verify_calendar_dialog() {
    /Users/riverweiss/Library/Android/sdk/platform-tools/adb shell uiautomator dump
    /Users/riverweiss/Library/Android/sdk/platform-tools/adb shell cat /sdcard/window_dump.xml | grep -q 'text="Select Calendars"'
    echo $?  # 0 if true, 1 if false
}

# Check if rule creation screen is open
verify_rule_creation() {
    /Users/riverweiss/Library/Android/sdk/platform-tools/adb shell uiautomator dump
    /Users/riverweiss/Library/Android/sdk/platform-tools/adb shell cat /sdcard/window_dump.xml | grep -q 'resource-id="com.example.calendaralarmscheduler:id/button_select_calendars"'
    echo $?  # 0 if true, 1 if false
}
```

### Troubleshooting Commands

#### App State Recovery
```bash
# Force restart app to known state
adb_restart_app() {
    /Users/riverweiss/Library/Android/sdk/platform-tools/adb shell "am force-stop com.example.calendaralarmscheduler"
    sleep 2
    /Users/riverweiss/Library/Android/sdk/platform-tools/adb shell "am start -n com.example.calendaralarmscheduler/.ui.MainActivity"
    sleep 3
}

# Navigate back if in unexpected state
/Users/riverweiss/Library/Android/sdk/platform-tools/adb shell input keyevent KEYCODE_BACK

# Return to home screen and relaunch
/Users/riverweiss/Library/Android/sdk/platform-tools/adb shell input keyevent KEYCODE_HOME
sleep 1
/Users/riverweiss/Library/Android/sdk/platform-tools/adb shell "am start -n com.example.calendaralarmscheduler/.ui.MainActivity"
```

#### UI Analysis Commands
```bash
# Save and analyze current UI state
adb_dump_ui() {
    /Users/riverweiss/Library/Android/sdk/platform-tools/adb shell uiautomator dump
    /Users/riverweiss/Library/Android/sdk/platform-tools/adb pull /sdcard/window_dump.xml ./current_ui_dump.xml
    echo "UI dump saved to current_ui_dump.xml"
}

# Find all clickable elements with coordinates
adb_find_clickable() {
    /Users/riverweiss/Library/Android/sdk/platform-tools/adb shell uiautomator dump
    /Users/riverweiss/Library/Android/sdk/platform-tools/adb shell cat /sdcard/window_dump.xml | grep -E 'clickable="true".*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"' | head -10
}

# Extract all resource IDs for debugging
adb_find_resources() {
    /Users/riverweiss/Library/Android/sdk/platform-tools/adb shell uiautomator dump
    /Users/riverweiss/Library/Android/sdk/platform-tools/adb shell cat /sdcard/window_dump.xml | grep -o 'resource-id="[^"]*"' | sort | uniq
}
```

### Screen Coordinates Reference (1080x2400 screen)

#### Bottom Navigation (always accessible)
- **Rules Tab**: `180, 2232`
- **Preview Tab**: `540, 2232` 
- **Settings Tab**: `900, 2232`

#### Rule Creation Screen
- **Rule Name Field**: `540, 360`
- **Keyword Pattern Field**: `540, 521`
- **Calendar Selection Button**: `540, 769`
- **Lead Time Button**: `540, 952`
- **Save Button**: `540, 1204`

#### Calendar Picker Dialog
- **Select/Deselect All Button**: `865, 895`
- **First Calendar Row**: `540, 1084`
- **Second Calendar Row**: `540, 1294`
- **Cancel Button**: `628, 1504`
- **Select Button**: `880, 1504`

### Notes
- **All coordinates are for 1080x2400 screen** - adjust proportionally for different sizes
- **Always wait 1-2 seconds** after UI interactions for state changes
- **Use `uiautomator dump`** to verify current state before proceeding
- **Resource IDs are the most reliable** way to find elements vs coordinates
- **Test both new rule creation and existing rule editing** for complete coverage