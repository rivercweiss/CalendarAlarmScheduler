# 📅 Calendar Alarm Scheduler (Android)

## 1. Overview

This app automatically sets **unmissable system alarms** a configurable amount of time before certain Google Calendar events. Users define **rules** to trigger alarms only for events whose titles contain specific keywords.

**Core Requirement**: Alarms MUST play regardless of phone state (silent mode, do not disturb, low power mode, etc.) to ensure users never miss important events.

The app runs **fully locally** with **no backend**, using Android APIs to read calendar data and set exact alarms.

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

## 5. Alarm Implementation (Unmissable Alarms)

### AlarmScheduler Strategy
```kotlin
class AlarmScheduler {
    fun scheduleAlarm(alarm: ScheduledAlarm) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarm.id)
            putExtra("EVENT_TITLE", alarm.eventTitle)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context, 
            alarm.pendingIntentRequestCode,
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            alarm.alarmTimeUtc,
            pendingIntent
        )
    }
}
```

### AlarmActivity (Full-Screen Unmissable)
```kotlin
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make alarm unmissable
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        
        // Override system sound settings
        playAlarmSound() // Custom sound that bypasses silent/DND
        vibrate()       // Vibration that works in all modes
        
        // Full screen with dismiss/snooze actions
    }
}
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

## 8. Implementation Status

### ✅ Completed Tasks
- [x] **Project Setup**: Target API 34, Min API 26, ViewBinding, Kotlin coroutines
- [x] **Dependencies**: Room database, WorkManager, ViewModel, Navigation, Material Design 3
- [x] **AndroidManifest**: All required permissions and component declarations
- [x] **ProGuard Rules**: Room, WorkManager, kotlinx.serialization
- [x] **Database Entities**: Rule.kt and ScheduledAlarm.kt with proper type converters

### 🔄 Currently In Progress
**Phase 1: Database Foundation (Step 2 - 2/4 complete)**

### ⏳ Next Tasks (Phase 1)
- [ ] Create RuleDao interface with CRUD operations
- [ ] Create AlarmDao interface with CRUD operations  
- [ ] Create AppDatabase.kt - Room database with migrations
- [ ] Create RuleRepository.kt and AlarmRepository.kt
- [ ] Create domain models (CalendarEvent.kt)
- [ ] Implement RuleMatcher.kt with regex auto-detection
- [ ] Create AlarmScheduler.kt wrapper around AlarmManager
- [ ] Create CalendarRepository.kt for event fetching
- [ ] Create utility classes (PermissionUtils.kt, TimezoneUtils.kt)
- [ ] Create core UI components (MainActivity, fragments, activities)
- [ ] Create receivers (AlarmReceiver, BootReceiver)

### Phase 2: Reliability Features (Future)
- [ ] Background worker with user-configurable intervals
- [ ] Event change detection using LAST_MODIFIED
- [ ] User-dismissed alarm tracking
- [ ] Timezone change handling

### Phase 3: Polish & Optimization (Future)
- [ ] Battery optimization guidance
- [ ] Permission status monitoring
- [ ] Event preview with timezone info
- [ ] All edge case handling

---

## 9. Testing Strategy

### Test Calendar Events (`test/calendar_events/TestEvents.ics`)
```
Event: "Important Meeting" - Tomorrow 2:00 PM (tests basic rule matching)
Event: "Doctor Appointment" - Tomorrow 9:00 AM (tests medical keyword rule)
Event: "All Day Conference" - Day after tomorrow (tests all-day events)
Event: "Team Standup" - Every weekday 10:00 AM (tests recurring events)
```

### Unit Tests
* **RuleMatcher**: Regex auto-detection, keyword matching, calendar filtering
* **AlarmScheduler**: Request code generation, duplicate prevention
* **TimezoneUtils**: UTC conversion, timezone change handling
* **CalendarRepository**: Event parsing, change detection

### Integration Tests
* **End-to-End**: Create test events → run background worker → verify alarms scheduled
* **Permission Flow**: Test app behavior with missing permissions
* **Reboot Simulation**: Verify alarm re-registration after boot

### Manual Testing Checklist
- [ ] Create Google Calendar event matching rule → verify alarm appears in system
- [ ] Enable Do Not Disturb → verify alarm still plays at scheduled time
- [ ] Change phone timezone → verify alarms update correctly
- [ ] Dismiss system alarm manually → verify not rescheduled on next sync
- [ ] Edit calendar event → verify old alarm cancelled, new one scheduled
- [ ] Test with battery optimization enabled/disabled

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

## 11. Architecture Diagram & Flow

### Component Flow (ASCII)

```
+----------------------+        +----------------------+        +--------------------+
|   Calendar Provider  | -----> |   CalendarRepository | -----> |    Rule Engine     |
| (CalendarContract)   |        |  (reads events, maps)|        | (matches rules,    |
+----------------------+        +----------------------+        | computes leadTime) |
                                                             +--------------------+
                                                                       |
                                                                       v
                                                            +------------------------+
                                                            |   Alarm Scheduler      |
                                                            | - AlarmManager         |
                                                            |   setExactAndAllowWhileIdle |
                                                            | - Full-screen Activity |
                                                            +------------------------+
                                                                       |
                                                                       v
                                                            +------------------------+
                                                            |   Unmissable Alarm     |
                                                            |   (Works in all phone  |
                                                            |    states: silent/DND) |
                                                            +------------------------+

Background:
+---------------------------------------------------------------+
| WorkManager PeriodicWorker  <---- triggers ---- BootReceiver   |
| - Calls CalendarRepository to fetch events                    |
| - Runs Rule Engine and Alarm Scheduler                        |
+---------------------------------------------------------------+
```

### Sequence Flow

1. **WorkManager (PeriodicWorker)** wakes (every 5-60 minutes, user configurable)
2. Worker calls **CalendarRepository** which queries `CalendarContract` for events in next 2 days
3. Repository returns `CalendarEvent` domain objects with timezone info
4. **Rule Engine** filters events: for each rule (keyword, calendar filter, lead time), matches event.title
5. For each matched event, compute `alarmTime = event.start - rule.leadTime` (in UTC)
6. **Alarm Scheduler** checks `AlarmRepository` (Room) to avoid duplicates and respect user dismissals
7. `AlarmScheduler` calls `AlarmManager.setExactAndAllowWhileIdle()` with unique `PendingIntent`
8. At alarm time, `AlarmReceiver` launches full-screen `AlarmActivity` that bypasses all phone modes
9. Persist alarm metadata to Room database for tracking and reboot recovery
10. On **BOOT_COMPLETED**, `BootReceiver` re-registers all active alarms from database

---

This architecture prioritizes **alarm reliability** above all else while maintaining simplicity and comprehensive edge case handling.

---

# Comprehensive Implementation Plan

## Development Workflow Instructions
**For future Claude instances:** Always use the TodoWrite tool to track implementation progress. Mark tasks as `in_progress` before starting, `completed` when finished. This plan should be used as a living checklist. Update completion status and add discovered subtasks as needed.

## Phase 1: Core MVP Foundation (21 detailed steps)

### Step 1: Project Setup & Dependencies
1.1. **Create Android Studio Project**
   - Target API 34, Min API 26
   - Enable ViewBinding and DataBinding
   - Configure Kotlin coroutines

1.2. **Add Core Dependencies**
   - Room database (3 components: database, dao, entities)
   - WorkManager for background tasks
   - ViewModel & LiveData/StateFlow
   - Navigation component
   - Material Design 3
   - Kotlin serialization for database type converters

1.3. **Configure Build & Manifest**
   - Add all required permissions to AndroidManifest.xml
   - Configure ProGuard rules for Room and WorkManager
   - Set up debug/release build variants

### Step 2: Database Foundation
2.1. **Create Entity Classes** (`app/data/database/entities/`)
   - `Rule.kt` with UUID primary key, List<Long> type converter
   - `ScheduledAlarm.kt` with pending intent tracking
   - Type converters for List<Long> and complex types

2.2. **Create DAO Interfaces** (`app/data/database/`)
   - `RuleDao.kt` - CRUD operations, findEnabled(), findByCalendarId()
   - `AlarmDao.kt` - CRUD operations, findActive(), findByEventId()
   - Suspend functions for coroutine support

2.3. **Create Room Database** (`app/data/database/`)
   - `AppDatabase.kt` - Single source of truth
   - Migration strategy from version 1
   - Database singleton with proper thread safety

2.4. **Create Repository Layer**
   - `RuleRepository.kt` - Wrapper around RuleDao with caching
   - `AlarmRepository.kt` - Wrapper around AlarmDao with business logic
   - Implement offline-first pattern with Flow<List<T>>

### Step 3: Domain Models & Business Logic
3.1. **Create Domain Models** (`app/domain/models/`)
   - `CalendarEvent.kt` - Parse from CalendarContract.Events
   - Include timezone handling, all-day event detection
   - Add extension functions for time calculations

3.2. **Implement Rule Matcher** (`app/domain/`)
   - `RuleMatcher.kt` - Auto-detect regex vs contains matching
   - Implement calendar filtering per rule
   - Handle case-insensitive matching for simple strings
   - Unit test with edge cases (special regex characters, empty patterns)

3.3. **Create Alarm Scheduler** (`app/domain/`)
   - `AlarmScheduler.kt` - Wrapper around AlarmManager
   - Generate unique request codes: `(eventId + ruleId).hashCode()`
   - Handle Android 12+ SCHEDULE_EXACT_ALARM permission
   - Implement cancel/reschedule logic

### Step 4: Calendar Integration
4.1. **Create Calendar Repository** (`app/data/`)
   - `CalendarRepository.kt` - Query CalendarContract.Events
   - Implement 2-day lookahead window from current time
   - Parse all-day events correctly (midnight UTC handling)
   - Filter by calendar IDs efficiently

4.2. **Handle Calendar Permissions**
   - Permission check functions in `utils/PermissionUtils.kt`
   - Runtime permission request handling
   - Graceful degradation when permission denied

4.3. **Parse Calendar Data**
   - Extract event title, start/end times, calendar ID
   - Handle recurring events (get individual instances)
   - Timezone conversion utilities in `utils/TimezoneUtils.kt`

### Step 5: Core UI Implementation
5.1. **Main Activity Setup** (`app/ui/`)
   - `MainActivity.kt` - Navigation host, permission orchestration
   - Bottom navigation or tab layout for main sections
   - Handle permission results and deep linking

5.2. **Rule Management UI** (`app/ui/rules/`)
   - `RuleListFragment.kt` - Display all rules with enable/disable toggle
   - `RuleEditFragment.kt` - Create/edit rules with validation
   - Calendar selection multi-picker dialog
   - Lead time picker (1 min to 7 days) with preset options

5.3. **Permission Onboarding** (`app/ui/onboarding/`)
   - `PermissionOnboardingActivity.kt` - Step-by-step flow
   - Explain WHY each permission is needed
   - Take users to system settings for SCHEDULE_EXACT_ALARM
   - Battery optimization whitelist guidance

### Step 6: Alarm System Core
6.1. **Alarm Receiver** (`app/receivers/`)
   - `AlarmReceiver.kt` - Handle alarm broadcasts
   - Launch AlarmActivity with event details
   - Handle alarm cancellation and cleanup

6.2. **Unmissable Alarm Activity** (`app/ui/alarm/`)
   - `AlarmActivity.kt` - Full-screen, works in all phone states
   - Custom alarm sound that bypasses DND/silent mode
   - Vibration patterns that work in all scenarios
   - Dismiss/snooze actions with proper cleanup

6.3. **Boot Recovery** (`app/receivers/`)
   - `BootReceiver.kt` - Re-register alarms after device restart
   - Query database for active alarms
   - Reschedule all non-dismissed, future alarms

## Phase 2: Reliability & Background Features (15 detailed steps)

### Step 7: Background Worker Implementation
7.1. **Create Calendar Refresh Worker** (`app/workers/`)
   - `CalendarRefreshWorker.kt` - Periodic background sync
   - Use PeriodicWorkRequest with user-configurable intervals
   - Implement efficient change detection with LAST_MODIFIED

7.2. **Worker Scheduling Logic**
   - Schedule on app start and after settings changes
   - Handle different intervals (5, 15, 30, 60 minutes)
   - Respect battery optimization settings

7.3. **Background Constraints**
   - No network constraints (local calendar provider)
   - Battery optimization detection and user warnings
   - Doze mode compatibility testing

### Step 8: Event Change Detection
8.1. **Last Modified Tracking**
   - Store `lastSyncTime` in SharedPreferences
   - Query only events with `LAST_MODIFIED > lastSyncTime`
   - Handle timezone changes that affect scheduling

8.2. **Smart Alarm Updates**
   - When event changes: cancel old alarm, schedule new one
   - Preserve user-dismissed status for original event
   - Treat modified events as "new" for dismissal tracking

8.3. **Duplicate Prevention**
   - Check existing alarms before scheduling
   - Handle multiple rules matching same event
   - Prevent alarm spam with intelligent deduplication

### Step 9: User Dismissal Tracking
9.1. **System Integration**
   - Detect when user manually removes alarm from system
   - Monitor AlarmManager state vs database state
   - Mark dismissed alarms in database

9.2. **Dismissal Logic**
   - Never reschedule manually dismissed alarms
   - Reset dismissal status only when event LAST_MODIFIED changes
   - Provide UI to "un-dismiss" alarms if needed

### Step 10: Advanced Timezone Handling
10.1. **Timezone Change Detection**
   - BroadcastReceiver for ACTION_TIMEZONE_CHANGED
   - Recalculate all alarm times in new timezone
   - Handle daylight saving time transitions

10.2. **UTC Storage Strategy**
   - Store all times in UTC in database
   - Convert to local time only for display
   - Timezone indicator in UI for clarity

## Phase 3: Polish & Advanced Features (12 detailed steps)

### Step 11: Settings & Configuration
11.1. **Settings Screen** (`app/ui/settings/`)
   - `SettingsFragment.kt` - All user preferences
   - Background refresh interval picker
   - All-day event default time setting
   - Permission status dashboard with action buttons

11.2. **Preference Management**
   - SharedPreferences or DataStore for settings
   - Live updates when settings change
   - Migration handling for setting schema changes

### Step 12: Event Preview & Monitoring
12.1. **Event Preview Screen** (`app/ui/preview/`)
   - `EventPreviewFragment.kt` - Show upcoming events and their alarms
   - Filter by rule, calendar, or date range
   - Real-time updates as events change

12.2. **Alarm Status Monitoring**
   - Show which alarms are scheduled in system
   - Detect and warn about failed alarm scheduling
   - Manual alarm testing functionality

### Step 13: Edge Case Handling
13.1. **All-Day Event Processing**
   - Global setting for default alarm time
   - Apply lead time from user-configured time
   - Handle multi-day events correctly

13.2. **Multiple Rules & Conflicts**
   - Allow multiple alarms per event from different rules
   - Intelligent conflict resolution options
   - User preferences for duplicate handling

13.3. **Error Recovery**
   - Graceful handling of calendar provider issues
   - Retry logic for failed alarm scheduling
   - User notification for persistent failures

## Phase 4: Testing & Quality Assurance (8 detailed steps)

### Step 14: Unit Testing Suite
14.1. **Core Logic Tests**
   - RuleMatcher regex auto-detection and matching
   - AlarmScheduler request code generation
   - TimezoneUtils conversion accuracy
   - Database operations and migrations

14.2. **Repository Testing**
   - Mock calendar provider responses
   - Test event change detection logic
   - Verify alarm scheduling/cancellation

### Step 15: Integration Testing
15.1. **End-to-End Flows**
   - Create test calendar → run worker → verify alarms
   - Permission flow testing with different states
   - Background worker reliability testing

15.2. **Device State Testing**
   - Test alarms in Do Not Disturb mode
   - Battery optimization enabled/disabled
   - Different Android versions (API 26-34)

### Step 16: Manual Testing & Validation
16.1. **Real-World Scenarios**
   - Multiple Google Calendar accounts
   - Recurring events with exceptions
   - Timezone travel simulation
   - Long-term reliability (multi-day testing)

16.2. **Performance Testing**
   - Large number of calendar events (1000+)
   - Multiple rules with complex regex patterns
   - Background worker battery usage monitoring

## Implementation Checklist Format

Each task should be tracked as:
- [ ] **Task Name** - Brief description
  - Implementation file(s): `path/to/file.kt`
  - Key requirements: Bullet points of what must be done
  - Testing criteria: How to verify completion
  - Dependencies: What must be completed first

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