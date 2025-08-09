# Comprehensive Implementation Plan

## Development Workflow Instructions
**For future Claude instances:** Always use the TodoWrite tool to track implementation progress. Mark tasks as `in_progress` before starting, `completed` when finished. This plan should be used as a living checklist. Update completion status and add discovered subtasks as needed.

## Phase 1: Core MVP Foundation (21 detailed steps)

### Step 1: Project Setup & Dependencies ✅ **COMPLETED**

#### 1.1. **Create Android Studio Project** ✅ **COMPLETED**
- Target API 34, Min API 26 ✅
- Enable ViewBinding and DataBinding ✅
- Configure Kotlin coroutines ✅

#### ⚡ Quick Verification for Step 1.1 (< 1 minute):
**Build Check (15 sec):**
- [ ] `./gradlew compileDebugKotlin` - completes in 10-15 seconds
- [ ] No red underlines in MainActivity.kt when opened in IDE
- [ ] Project window shows standard Android folder structure

**Runtime Check (30 sec):**
- [ ] Hit "Run" → app launches on emulator/device in 20-30 seconds
- [ ] Blank activity displays without crashes
- [ ] Logcat shows "MainActivity: onCreate" or similar (no critical errors)

**Config Verification (10 sec):**
- [ ] app/build.gradle: `compileSdk 34`, `minSdk 26`
- [ ] ViewBinding enabled: `buildFeatures { viewBinding true }`

---

#### 1.2. **Add Core Dependencies** ✅ **COMPLETED**
- Room database (3 components: database, dao, entities) ✅
- WorkManager for background tasks ✅
- ViewModel & LiveData/StateFlow ✅
- Navigation component ✅
- Material Design 3 ✅
- Kotlin serialization for database type converters ✅

#### ⚡ Quick Verification for Step 1.2 (< 1 minute):
**Dependencies Check (20 sec):**
- [ ] `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep room` shows Room libs
- [ ] `./gradlew compileDebugKotlin` succeeds after dependency addition
- [ ] IDE auto-complete shows Room annotations when typing `@Entity`

**Import Verification (15 sec):**
- [ ] Can import `androidx.room.Entity` without errors
- [ ] WorkManager classes: `import androidx.work.OneTimeWorkRequest` works
- [ ] Material 3: `import com.google.android.material.` shows components

**Gradle Sync (20 sec):**
- [ ] "Sync Now" completes successfully in Android Studio
- [ ] Build → Clean Project → Rebuild Project succeeds

---

#### 1.3. **Configure Build & Manifest** ✅ **COMPLETED**
- Add all required permissions to AndroidManifest.xml ✅
- Configure ProGuard rules for Room and WorkManager ✅ 
- Set up debug/release build variants ✅

#### ⚡ Quick Verification for Step 1.3 (< 1 minute):
**Manifest Check (15 sec):**
- [ ] AndroidManifest.xml contains all 6 required permissions (READ_CALENDAR, SCHEDULE_EXACT_ALARM, etc.)
- [ ] No manifest merge errors: check Build → Generate Signed Bundle/APK → Next shows no issues

**Build Variants (20 sec):**
- [ ] Build Variants panel shows "debug" and "release" options
- [ ] Switch to release → `./gradlew assembleRelease` completes
- [ ] ProGuard rules don't break Room: check build/outputs for successful APK

**Permission Syntax (10 sec):**
- [ ] Each permission follows format: `<uses-permission android:name="android.permission.READ_CALENDAR" />`
- [ ] No XML syntax errors (red underlines in manifest editor)

### Step 2: Database Foundation ✅ **COMPLETED**

#### 2.1. **Create Entity Classes** ✅ **COMPLETED** (`app/data/database/entities/`)
- `Rule.kt` with UUID primary key, List<Long> type converter ✅
- `ScheduledAlarm.kt` with pending intent tracking ✅
- Type converters for List<Long> and complex types ✅

#### ⚡ Quick Verification for Step 2.1 (< 1 minute):
**Compilation Check (20 sec):**
- [ ] `./gradlew :app:compileDebugKotlin` succeeds
- [ ] No import errors on @Entity, @PrimaryKey, @TypeConverter annotations
- [ ] IDE shows no red squiggles in entity files

**Room Schema Generation (25 sec):**
- [ ] Build project → check `app/build/generated/source/kapt/debug/.../database` for generated code
- [ ] Room generates expected SQL: check `app/schemas/` folder for database JSON
- [ ] Type converters compile: List<Long> conversion methods appear in generated files

**Quick Entity Test (10 sec):**
- [ ] Create simple entity instance: `val rule = Rule(name="test", keywordPattern="meeting")`
- [ ] No compilation errors when using entity constructors

---

#### 2.2. **Create DAO Interfaces** ✅ **COMPLETED** (`app/data/database/`)
- `RuleDao.kt` - CRUD operations, findEnabled(), findByCalendarId() ✅
- `AlarmDao.kt` - CRUD operations, findActive(), findByEventId() ✅
- Suspend functions for coroutine support ✅

#### ⚡ Quick Verification for Step 2.2 (< 1 minute):
**Interface Compilation (15 sec):**
- [ ] `./gradlew compileDebugKotlin` passes with DAO interfaces
- [ ] @Dao, @Query, @Insert annotations resolve without errors
- [ ] Suspend functions compile with proper coroutine imports

**Room Query Validation (30 sec):**
- [ ] Build project → Room validates SQL queries at compile time (no query errors in Build tab)
- [ ] IDE shows method signatures for DAO functions with autocomplete
- [ ] Check generated implementation: `app/build/generated/.../RuleDao_Impl.java` exists

**Method Signature Check (10 sec):**
- [ ] DAO methods return Flow<List<T>> for reactive queries
- [ ] Insert/Update methods properly marked as suspend functions

---

#### 2.3. **Create Room Database** ✅ **COMPLETED** (`app/data/database/`)
- `AppDatabase.kt` - Single source of truth ✅
- Migration strategy from version 1 ✅
- Database singleton with proper thread safety ✅

#### ⚡ Quick Verification for Step 2.3 (< 1 minute):
**Database Compilation (20 sec):**
- [ ] `./gradlew compileDebugKotlin` succeeds with @Database annotation
- [ ] Room generates database implementation without errors
- [ ] Database version and entities list compile correctly

**Singleton Pattern (15 sec):**
- [ ] Database class uses proper thread-safe singleton pattern
- [ ] getInstance() method returns same instance across calls
- [ ] No compile errors with synchronized access

**Migration Setup (20 sec):**
- [ ] Migration objects compile without errors
- [ ] Database builder includes migration configuration
- [ ] Room doesn't show migration warnings in build output

---

#### 2.4. **Create Repository Layer** ✅ **COMPLETED**
- `RuleRepository.kt` - Wrapper around RuleDao with caching ✅
- `AlarmRepository.kt` - Wrapper around AlarmDao with business logic ✅
- Implement offline-first pattern with Flow<List<T>> ✅

#### ⚡ Quick Verification for Step 2.4 (< 1 minute):
**Repository Compilation (15 sec):**
- [ ] `./gradlew compileDebugKotlin` succeeds with repository classes
- [ ] DAO injection and Flow types compile correctly
- [ ] Coroutine scopes and suspend functions work without errors

**Dependency Injection (20 sec):**
- [ ] Repository constructors accept DAO parameters correctly
- [ ] Database instance can be retrieved and passed to repositories
- [ ] No circular dependency issues in compilation

**Flow Implementation (20 sec):**
- [ ] Repository methods return Flow<List<T>> types
- [ ] Flow operators (map, filter, etc.) compile correctly
- [ ] LiveData/StateFlow integration compiles if using ViewModels

### Step 3: Domain Models & Business Logic ✅ **COMPLETED**

#### 3.1. **Create Domain Models** ✅ **COMPLETED** (`app/domain/models/`)
- `CalendarEvent.kt` - Parse from CalendarContract.Events ✅
- Include timezone handling, all-day event detection ✅
- Add extension functions for time calculations ✅
- Fixed bug in `getLocalEndTime()` method ✅

#### ⚡ Quick Verification for Step 3.1 (< 1 minute):
**Model Compilation (20 sec):**
- [x] `./gradlew compileDebugKotlin` succeeds with domain models
- [x] CalendarEvent data class compiles with all required fields
- [x] Time zone imports (java.time.*) resolve correctly

**Extension Functions (15 sec):**
- [x] Extension functions on CalendarEvent compile without errors
- [x] Time calculation methods (addMinutes, toUTC, etc.) work correctly
- [x] IDE autocomplete shows extension methods when typing `calendarEvent.`

**Data Class Validation (20 sec):**
- [x] Create test CalendarEvent instance: `CalendarEvent(title="Test", startTime=System.currentTimeMillis())`
- [x] All-day event detection logic returns boolean correctly
- [x] ToString/equals/hashCode methods work (test with println)

---

#### 3.2. **Implement Rule Matcher** ✅ **COMPLETED** (`app/domain/`)
- `RuleMatcher.kt` - Auto-detect regex vs contains matching ✅
- Implement calendar filtering per rule ✅
- Handle case-insensitive matching for simple strings ✅
- Unit test with edge cases (special regex characters, empty patterns) ✅

#### ⚡ Quick Verification for Step 3.2 (< 1 minute):
**Matcher Compilation (15 sec):**
- [x] `./gradlew compileDebugKotlin` succeeds with RuleMatcher class
- [x] Regex imports and Pattern class resolve correctly
- [x] No compilation errors with string matching logic

**Quick Functional Test (30 sec):**
- [x] Test simple matching: `RuleMatcher.matches("meeting", "Important Meeting")` returns true
- [x] Test regex detection: `RuleMatcher.isRegex(".*[Mm]eeting.*")` returns true
- [x] Test case insensitivity: `RuleMatcher.matches("DOCTOR", "doctor appointment")` returns true

**Unit Test Execution (10 sec):**
- [x] Run single test class: `./gradlew testDebugUnitTest --tests RuleMatcherTest`
- [x] All basic matching tests pass (3-5 simple test cases)

---

#### 3.3. **Create Alarm Scheduler** ✅ **COMPLETED** (`app/domain/`)
- `AlarmScheduler.kt` - Wrapper around AlarmManager ✅
- Generate unique request codes: `(eventId + ruleId).hashCode()` ✅
- Handle Android 12+ SCHEDULE_EXACT_ALARM permission ✅
- Implement cancel/reschedule logic ✅

#### ⚡ Quick Verification for Step 3.3 (< 1 minute):
**Scheduler Compilation (20 sec):**
- [x] `./gradlew compileDebugKotlin` succeeds with AlarmScheduler class
- [x] AlarmManager imports resolve correctly
- [x] PendingIntent creation compiles without errors

**Request Code Generation (15 sec):**
- [x] Test unique codes: `AlarmScheduler.generateRequestCode("event1", "rule1")` returns integer
- [x] Different inputs generate different codes
- [x] Same inputs generate same codes (deterministic)

**Permission Handling (20 sec):**
- [x] Permission check methods compile correctly
- [x] Android 12+ specific code compiles with proper Build.VERSION checks
- [x] No API level compatibility warnings in build output

### Step 4: Calendar Integration ✅ **COMPLETED**

#### 4.1. **Create Calendar Repository** ✅ **COMPLETED** (`app/data/`)
- `CalendarRepository.kt` - Query CalendarContract.Events ✅
- Implement 2-day lookahead window from current time ✅
- Parse all-day events correctly (midnight UTC handling) ✅
- Filter by calendar IDs efficiently ✅

#### ⚡ Quick Verification for Step 4.1 (< 1 minute):
**Repository Compilation (20 sec):**
- [x] `./gradlew compileDebugKotlin` succeeds with CalendarRepository
- [x] CalendarContract imports resolve correctly
- [x] ContentResolver queries compile without errors

**Query Logic Test (25 sec):**
- [x] Mock permission granted and test query method compiles
- [x] 2-day lookahead calculation: `startTime = System.currentTimeMillis()`, `endTime = startTime + (2 * 24 * 60 * 60 * 1000)`
- [x] Calendar ID filtering logic compiles correctly

**Data Parsing (10 sec):**
- [x] Cursor column access compiles correctly (CalendarContract.Events.TITLE, etc.)
- [x] All-day event detection logic (dtstart/dtend) compiles
- [x] No cursor resource leak warnings

---

#### 4.2. **Handle Calendar Permissions** ✅ **COMPLETED**
- Permission check functions in `utils/PermissionUtils.kt` ✅
- Runtime permission request handling ✅
- Graceful degradation when permission denied ✅

#### ⚡ Quick Verification for Step 4.2 (< 1 minute):
**Permission Utils Compilation (15 sec):**
- [x] `./gradlew compileDebugKotlin` succeeds with PermissionUtils
- [x] ContextCompat.checkSelfPermission imports work correctly
- [x] READ_CALENDAR permission string compiles correctly

**Permission Logic (20 sec):**
- [x] Permission check function returns boolean correctly
- [x] Request permission method compiles with proper ActivityResultLauncher types
- [x] Graceful degradation methods compile (return empty lists when denied)

**Runtime Test (20 sec):**
- [x] Create simple permission check: `PermissionUtils.hasCalendarPermission(context)` compiles
- [x] Permission rationale dialog logic compiles
- [x] No deprecated permission API warnings

---

#### 4.3. **Parse Calendar Data** ✅ **COMPLETED**
- Extract event title, start/end times, calendar ID ✅
- Handle recurring events (get individual instances) ✅
- Timezone conversion utilities in `utils/TimezoneUtils.kt` ✅

#### ⚡ Quick Verification for Step 4.3 (< 1 minute):
**Data Extraction (20 sec):**
- [x] `./gradlew compileDebugKotlin` succeeds with parsing logic
- [x] Cursor.getString/getLong methods compile correctly
- [x] All required calendar event fields are extracted properly

**Timezone Utils (25 sec):**
- [x] TimeZone and Calendar imports resolve correctly
- [x] UTC conversion methods compile: `toUTC()`, `fromUTC()`, etc.
- [x] Timezone change handling compiles with proper broadcast receiver setup

**Recurring Events (10 sec):**
- [x] CalendarContract.Instances queries compile correctly
- [x] Recurring event expansion logic compiles
- [x] Instance handling doesn't cause compilation errors

### Step 5: Core UI Implementation ✅ **COMPLETED**

#### 5.1. **Main Activity Setup** ✅ **COMPLETED** (`app/ui/`)
- `MainActivity.kt` - Navigation host, permission orchestration ✅
- Bottom navigation or tab layout for main sections ✅
- Handle permission results and deep linking ✅

#### ⚡ Quick Verification for Step 5.1 (< 1 minute):
**Activity Compilation (20 sec):**
- [x] `./gradlew compileDebugKotlin` succeeds with MainActivity updates
- [x] ViewBinding imports and setup compile correctly
- [x] Navigation component imports resolve properly

**UI Layout Test (25 sec):**
- [x] Run app → MainActivity launches without crashes
- [x] Bottom navigation/tabs are visible on screen
- [x] Navigation between sections works (tap different tabs)

**Permission Integration (10 sec):**
- [x] ActivityResultLauncher setup compiles correctly
- [x] Permission callback methods compile without errors
- [x] Deep link handling (if implemented) compiles correctly

---

#### 5.2. **Rule Management UI** ✅ **COMPLETED** (`app/ui/rules/`)
- `RuleListFragment.kt` - Display all rules with enable/disable toggle ✅
- `RuleEditFragment.kt` - Create/edit rules with validation ✅
- Calendar selection multi-picker dialog ✅
- Lead time picker (1 min to 7 days) with preset options ✅

#### ⚡ Quick Verification for Step 5.2 (< 1 minute):
**Fragment Compilation (20 sec):**
- [x] `./gradlew compileDebugKotlin` succeeds with rule fragments
- [x] RecyclerView and adapter imports compile correctly
- [x] Fragment lifecycle methods compile without errors

**UI Components Test (25 sec):**
- [x] Navigate to rules section → Fragment displays without crashes
- [x] RecyclerView shows (even if empty) without layout errors
- [x] Add/Edit buttons are visible and clickable

**Dialog Implementation (10 sec):**
- [x] Calendar picker dialog compiles correctly
- [x] Lead time picker dialog compiles without errors
- [x] Dialog fragment imports and setup work properly

---

#### 5.3. **Permission Onboarding** ✅ **COMPLETED** (`app/ui/onboarding/`)
- `PermissionOnboardingActivity.kt` - Step-by-step flow ✅
- Explain WHY each permission is needed ✅
- Take users to system settings for SCHEDULE_EXACT_ALARM ✅
- Battery optimization whitelist guidance ✅

#### ⚡ Quick Verification for Step 5.3 (< 1 minute):
**Onboarding Activity (20 sec):**
- [x] `./gradlew compileDebugKotlin` succeeds with onboarding activity
- [x] ViewPager/stepper imports compile correctly
- [x] Intent creation for system settings compiles

**Flow Navigation (25 sec):**
- [x] Launch onboarding activity → displays first step
- [x] "Next" button advances through steps
- [x] System settings intent launches (for SCHEDULE_EXACT_ALARM)

**Permission Explanations (10 sec):**
- [x] Permission rationale text displays correctly
- [x] Battery optimization guidance shows properly
- [x] "Grant Permission" buttons are functional

### Step 6: Alarm System Core

#### 6.1. **Alarm Receiver** (`app/receivers/`)
- `AlarmReceiver.kt` - Handle alarm broadcasts
- Launch AlarmActivity with event details
- Handle alarm cancellation and cleanup

#### ⚡ Quick Verification for Step 6.1 (< 1 minute):
**Receiver Compilation (15 sec):**
- [ ] `./gradlew compileDebugKotlin` succeeds with AlarmReceiver
- [ ] BroadcastReceiver imports and onReceive method compile correctly
- [ ] Intent creation for AlarmActivity compiles without errors

**Manifest Registration (20 sec):**
- [ ] AlarmReceiver is registered in AndroidManifest.xml
- [ ] No manifest merge errors after adding receiver
- [ ] Intent filters (if any) are properly configured

**Broadcast Handling (20 sec):**
- [ ] Test alarm intent creation: AlarmReceiver handles test broadcast correctly
- [ ] Extra data (ALARM_ID, EVENT_TITLE) extraction compiles
- [ ] Activity launch intent works without crashes

---

#### 6.2. **Unmissable Alarm Activity** (`app/ui/alarm/`)
- `AlarmActivity.kt` - Full-screen, works in all phone states
- Custom alarm sound that bypasses DND/silent mode
- Vibration patterns that work in all scenarios
- Dismiss/snooze actions with proper cleanup

#### ⚡ Quick Verification for Step 6.2 (< 1 minute):
**Activity Compilation (20 sec):**
- [ ] `./gradlew compileDebugKotlin` succeeds with AlarmActivity
- [ ] Full-screen flags and window manager imports compile correctly
- [ ] MediaPlayer and Vibrator imports resolve properly

**Full-Screen Test (25 sec):**
- [ ] Launch AlarmActivity manually → appears as full-screen
- [ ] Activity shows over lock screen (test setShowWhenLocked)
- [ ] Screen turns on when activity starts (test setTurnScreenOn)

**Sound & Vibration (10 sec):**
- [ ] MediaPlayer creation compiles without errors
- [ ] Vibrator service access compiles correctly
- [ ] Audio focus and volume handling compiles properly

---

#### 6.3. **Boot Recovery** (`app/receivers/`)
- `BootReceiver.kt` - Re-register alarms after device restart
- Query database for active alarms
- Reschedule all non-dismissed, future alarms

#### ⚡ Quick Verification for Step 6.3 (< 1 minute):
**Boot Receiver Compilation (20 sec):**
- [ ] `./gradlew compileDebugKotlin` succeeds with BootReceiver
- [ ] BOOT_COMPLETED intent filter imports compile correctly
- [ ] Database query logic in onReceive compiles

**Manifest & Permission (25 sec):**
- [ ] BootReceiver registered in AndroidManifest.xml
- [ ] RECEIVE_BOOT_COMPLETED permission present in manifest
- [ ] No boot receiver registration warnings

**Database Integration (10 sec):**
- [ ] AlarmRepository access compiles in receiver
- [ ] Alarm rescheduling logic compiles correctly
- [ ] Coroutine scope handling compiles for database operations

## Phase 2: Reliability & Background Features (15 detailed steps)

### Step 7: Background Worker Implementation

#### 7.1. **Create Calendar Refresh Worker** (`app/workers/`)
- `CalendarRefreshWorker.kt` - Periodic background sync
- Use PeriodicWorkRequest with user-configurable intervals
- Implement efficient change detection with LAST_MODIFIED

#### ⚡ Quick Verification for Step 7.1 (< 1 minute):
**Worker Compilation (20 sec):**
- [ ] `./gradlew compileDebugKotlin` succeeds with CalendarRefreshWorker
- [ ] WorkManager imports (Worker, PeriodicWorkRequest) compile correctly
- [ ] doWork() method implementation compiles without errors

**Work Request Creation (25 sec):**
- [ ] PeriodicWorkRequestBuilder compiles with constraints and intervals
- [ ] WorkManager.enqueue() method works without compilation errors
- [ ] Work scheduling code compiles in Application or MainActivity

**Background Execution (10 sec):**
- [ ] Worker class extends Worker or CoroutineWorker correctly
- [ ] Background calendar repository calls compile correctly
- [ ] Result.success()/Result.failure() returns compile properly

---

#### 7.2. **Worker Scheduling Logic**
- Schedule on app start and after settings changes
- Handle different intervals (5, 15, 30, 60 minutes)
- Respect battery optimization settings

#### ⚡ Quick Verification for Step 7.2 (< 1 minute):
**Scheduling Compilation (15 sec):**
- [ ] `./gradlew compileDebugKotlin` succeeds with worker scheduling logic
- [ ] WorkManager.enqueueUniquePeriodicWork() compiles correctly
- [ ] Interval configuration (Duration.ofMinutes()) compiles properly

**Settings Integration (25 sec):**
- [ ] Settings change listener compiles correctly
- [ ] Worker rescheduling on settings change compiles without errors
- [ ] Different interval values (5, 15, 30, 60 minutes) work properly

**App Lifecycle (15 sec):**
- [ ] Worker scheduling in Application.onCreate() compiles
- [ ] Work cancellation and re-scheduling logic compiles
- [ ] No WorkManager initialization errors in logs

---

#### 7.3. **Background Constraints**
- No network constraints (local calendar provider)
- Battery optimization detection and user warnings
- Doze mode compatibility testing

#### ⚡ Quick Verification for Step 7.3 (< 1 minute):
**Constraints Setup (20 sec):**
- [ ] `./gradlew compileDebugKotlin` succeeds with constraint configuration
- [ ] Constraints.Builder() compiles with no network requirement
- [ ] Battery optimization check methods compile correctly

**Battery Detection (25 sec):**
- [ ] PowerManager.isIgnoringBatteryOptimizations() imports and compiles
- [ ] Battery optimization warning dialog compiles correctly
- [ ] Settings intent for battery optimization compiles

**Doze Mode Compatibility (10 sec):**
- [ ] Worker constraints allow execution during doze mode
- [ ] No RequiredNetworkType constraints present in worker setup
- [ ] WorkManager respects device idle state correctly

### Step 8: Event Change Detection

#### 8.1. **Last Modified Tracking**
- Store `lastSyncTime` in SharedPreferences
- Query only events with `LAST_MODIFIED > lastSyncTime`
- Handle timezone changes that affect scheduling

#### ⚡ Quick Verification for Step 8.1 (< 1 minute):
**Preferences Storage (20 sec):**
- [ ] `./gradlew compileDebugKotlin` succeeds with SharedPreferences usage
- [ ] LastSyncTime get/set methods compile correctly
- [ ] SharedPreferences.edit().apply() pattern compiles properly

**Query Filtering (25 sec):**
- [ ] Calendar query with LAST_MODIFIED selection compiles
- [ ] Timestamp comparison logic (> lastSyncTime) compiles correctly
- [ ] Calendar cursor filtering compiles without errors

**Timezone Handling (10 sec):**
- [ ] Timezone change detection compiles correctly
- [ ] LastSyncTime adjustment for timezone changes compiles
- [ ] UTC time handling in sync logic compiles properly

---

#### 8.2. **Smart Alarm Updates**
- When event changes: cancel old alarm, schedule new one
- Preserve user-dismissed status for original event
- Treat modified events as "new" for dismissal tracking

#### ⚡ Quick Verification for Step 8.2 (< 1 minute):
**Alarm Update Logic (25 sec):**
- [ ] `./gradlew compileDebugKotlin` succeeds with update logic
- [ ] AlarmManager.cancel() for old alarms compiles correctly
- [ ] New alarm scheduling after changes compiles without errors

**Dismissal Status (20 sec):**
- [ ] User-dismissed flag preservation logic compiles
- [ ] Database update for dismissal status compiles correctly
- [ ] Modified event detection (lastModified field) compiles properly

**Event Change Detection (10 sec):**
- [ ] Event comparison logic (detect what changed) compiles
- [ ] Modified event handling compiles without errors
- [ ] "New" event flagging for dismissal tracking compiles

---

#### 8.3. **Duplicate Prevention**
- Check existing alarms before scheduling
- Handle multiple rules matching same event
- Prevent alarm spam with intelligent deduplication

#### ⚡ Quick Verification for Step 8.3 (< 1 minute):
**Duplicate Checking (20 sec):**
- [ ] `./gradlew compileDebugKotlin` succeeds with deduplication logic
- [ ] Database query for existing alarms compiles correctly
- [ ] Alarm existence check before scheduling compiles properly

**Multi-Rule Handling (25 sec):**
- [ ] Multiple rule matching logic compiles without errors
- [ ] Unique alarm creation for each rule/event pair compiles
- [ ] Request code generation for multiple alarms compiles correctly

**Spam Prevention (10 sec):**
- [ ] Rate limiting logic (if implemented) compiles correctly
- [ ] Maximum alarms per event check compiles properly
- [ ] Deduplication algorithm compiles without errors

### Step 9: User Dismissal Tracking

#### 9.1. **System Integration**
- Detect when user manually removes alarm from system
- Monitor AlarmManager state vs database state
- Mark dismissed alarms in database

#### ⚡ Quick Verification for Step 9.1 (< 1 minute):
**System State Detection (25 sec):**
- [ ] `./gradlew compileDebugKotlin` succeeds with dismissal detection logic
- [ ] AlarmManager state checking methods compile correctly
- [ ] Database state comparison logic compiles without errors

**Alarm State Monitoring (20 sec):**
- [ ] PendingIntent existence check compiles correctly
- [ ] Database vs system alarm comparison compiles properly
- [ ] Missing alarm detection logic compiles without errors

**Database Updates (10 sec):**
- [ ] Dismissed alarm marking in database compiles correctly
- [ ] AlarmRepository update methods compile properly
- [ ] Dismissal timestamp storage compiles without errors

---

#### 9.2. **Dismissal Logic**
- Never reschedule manually dismissed alarms
- Reset dismissal status only when event LAST_MODIFIED changes
- Provide UI to "un-dismiss" alarms if needed

#### ⚡ Quick Verification for Step 9.2 (< 1 minute):
**Rescheduling Prevention (20 sec):**
- [ ] `./gradlew compileDebugKotlin` succeeds with dismissal logic
- [ ] Dismissed alarm check before scheduling compiles correctly
- [ ] Skip logic for dismissed alarms compiles properly

**Status Reset Logic (25 sec):**
- [ ] Event LAST_MODIFIED comparison compiles correctly
- [ ] Dismissal status reset on event change compiles without errors
- [ ] Modified event detection for dismissal reset compiles properly

**UI Integration (10 sec):**
- [ ] "Un-dismiss" button logic compiles correctly
- [ ] UI state updates for dismissal status compile properly
- [ ] Alarm re-enabling from UI compiles without errors

### Step 10: Advanced Timezone Handling

#### 10.1. **Timezone Change Detection**
- BroadcastReceiver for ACTION_TIMEZONE_CHANGED
- Recalculate all alarm times in new timezone
- Handle daylight saving time transitions

#### ⚡ Quick Verification for Step 10.1 (< 1 minute):
**Timezone Receiver (20 sec):**
- [ ] `./gradlew compileDebugKotlin` succeeds with timezone receiver
- [ ] ACTION_TIMEZONE_CHANGED intent filter compiles correctly
- [ ] BroadcastReceiver registration in manifest compiles properly

**Alarm Recalculation (25 sec):**
- [ ] Timezone change handling logic compiles without errors
- [ ] Alarm time recalculation methods compile correctly
- [ ] AlarmManager rescheduling on timezone change compiles properly

**DST Handling (10 sec):**
- [ ] Daylight saving time transition logic compiles correctly
- [ ] Time adjustment calculations compile without errors
- [ ] DST-aware alarm scheduling compiles properly

---

#### 10.2. **UTC Storage Strategy**
- Store all times in UTC in database
- Convert to local time only for display
- Timezone indicator in UI for clarity

#### ⚡ Quick Verification for Step 10.2 (< 1 minute):
**UTC Conversion (25 sec):**
- [ ] `./gradlew compileDebugKotlin` succeeds with UTC storage logic
- [ ] UTC conversion utilities compile correctly
- [ ] Database time storage in UTC compiles without errors

**Display Conversion (20 sec):**
- [ ] Local time conversion for UI display compiles correctly
- [ ] Time formatting for user display compiles properly
- [ ] Timezone-aware time presentation compiles without errors

**UI Indicators (10 sec):**
- [ ] Timezone indicator in UI compiles correctly
- [ ] Time zone display formatting compiles properly
- [ ] User-friendly timezone representation compiles without errors

## Phase 3: Polish & Advanced Features (12 detailed steps)

### Step 11: Settings & Configuration

#### 11.1. **Settings Screen** (`app/ui/settings/`)
- `SettingsFragment.kt` - All user preferences
- Background refresh interval picker
- All-day event default time setting
- Permission status dashboard with action buttons

#### ⚡ Quick Verification for Step 11.1 (< 1 minute):
**Settings Fragment (20 sec):**
- [ ] `./gradlew compileDebugKotlin` succeeds with SettingsFragment
- [ ] PreferenceFragmentCompat imports and setup compile correctly
- [ ] Settings XML resource compilation succeeds

**UI Components (25 sec):**
- [ ] Navigate to settings → SettingsFragment displays without crashes
- [ ] Refresh interval picker shows options (5, 15, 30, 60 minutes)
- [ ] All-day event time picker displays correctly

**Permission Dashboard (10 sec):**
- [ ] Permission status indicators show correctly in settings
- [ ] Action buttons for missing permissions are functional
- [ ] Permission state updates in real-time

---

#### 11.2. **Preference Management**
- SharedPreferences or DataStore for settings
- Live updates when settings change
- Migration handling for setting schema changes

#### ⚡ Quick Verification for Step 11.2 (< 1 minute):
**Preferences Storage (20 sec):**
- [ ] `./gradlew compileDebugKotlin` succeeds with preference management
- [ ] SharedPreferences or DataStore setup compiles correctly
- [ ] Default preference values compile without errors

**Live Updates (25 sec):**
- [ ] Settings change triggers immediate updates to app behavior
- [ ] Worker rescheduling occurs when refresh interval changes
- [ ] UI reflects preference changes immediately

**Migration Logic (10 sec):**
- [ ] Preference migration code compiles correctly
- [ ] Default value handling for new preferences compiles properly
- [ ] Schema change detection compiles without errors

### Step 12: Event Preview & Monitoring

#### 12.1. **Event Preview Screen** (`app/ui/preview/`)
- `EventPreviewFragment.kt` - Show upcoming events and their alarms
- Filter by rule, calendar, or date range
- Real-time updates as events change

#### ⚡ Quick Verification for Step 12.1 (< 1 minute):
**Preview Fragment (20 sec):**
- [ ] `./gradlew compileDebugKotlin` succeeds with EventPreviewFragment
- [ ] RecyclerView for event list compiles correctly
- [ ] Event data binding and display logic compiles without errors

**Event Display (25 sec):**
- [ ] Navigate to preview → Fragment shows upcoming events
- [ ] Events display with correct titles, times, and alarm status
- [ ] Timezone indicators show correctly for each event

**Filtering & Updates (10 sec):**
- [ ] Filter controls (rule, calendar, date) function correctly
- [ ] Real-time updates work when events change in background
- [ ] Event list refreshes automatically via LiveData/Flow

---

#### 12.2. **Alarm Status Monitoring**
- Show which alarms are scheduled in system
- Detect and warn about failed alarm scheduling
- Manual alarm testing functionality

#### ⚡ Quick Verification for Step 12.2 (< 1 minute):
**Status Monitoring (25 sec):**
- [ ] `./gradlew compileDebugKotlin` succeeds with alarm monitoring
- [ ] AlarmManager status checking compiles correctly
- [ ] System alarm vs database comparison compiles without errors

**Failure Detection (20 sec):**
- [ ] Failed alarm scheduling detection compiles correctly
- [ ] Warning notifications for scheduling failures compile properly
- [ ] Retry logic for failed alarms compiles without errors

**Testing Functionality (10 sec):**
- [ ] Manual test alarm creation compiles correctly
- [ ] Test alarm firing mechanism compiles properly
- [ ] Test cleanup logic compiles without errors

### Step 13: Edge Case Handling

#### 13.1. **All-Day Event Processing**
- Global setting for default alarm time
- Apply lead time from user-configured time
- Handle multi-day events correctly

#### ⚡ Quick Verification for Step 13.1 (< 1 minute):
**All-Day Detection (20 sec):**
- [ ] `./gradlew compileDebugKotlin` succeeds with all-day event logic
- [ ] All-day event detection from calendar data compiles correctly
- [ ] Default time application logic compiles without errors

**Time Calculation (25 sec):**
- [ ] Default alarm time setting (e.g., 9:00 PM day before) compiles correctly
- [ ] Lead time calculation from default time compiles properly
- [ ] Multi-day event handling compiles without errors

**Settings Integration (10 sec):**
- [ ] All-day event default time setting compiles correctly
- [ ] User preference for all-day alarm time compiles properly
- [ ] Settings UI for all-day events compiles without errors

---

#### 13.2. **Multiple Rules & Conflicts**
- Allow multiple alarms per event from different rules
- Intelligent conflict resolution options
- User preferences for duplicate handling

#### ⚡ Quick Verification for Step 13.2 (< 1 minute):
**Multi-Rule Handling (25 sec):**
- [ ] `./gradlew compileDebugKotlin` succeeds with multi-rule logic
- [ ] Multiple alarm scheduling per event compiles correctly
- [ ] Unique request code generation per rule/event pair compiles properly

**Conflict Resolution (20 sec):**
- [ ] Duplicate alarm detection compiles correctly
- [ ] Conflict resolution algorithm compiles without errors
- [ ] User preference handling for duplicates compiles properly

**Rule Priority (10 sec):**
- [ ] Rule priority/ordering logic compiles correctly
- [ ] Conflict resolution preferences compile without errors
- [ ] Multiple alarm management compiles properly

---

#### 13.3. **Error Recovery**
- Graceful handling of calendar provider issues
- Retry logic for failed alarm scheduling
- User notification for persistent failures

#### ⚡ Quick Verification for Step 13.3 (< 1 minute):
**Calendar Provider Errors (20 sec):**
- [ ] `./gradlew compileDebugKotlin` succeeds with error recovery
- [ ] Try-catch blocks for calendar queries compile correctly
- [ ] Graceful degradation logic compiles without errors

**Retry Logic (25 sec):**
- [ ] Failed alarm scheduling retry compiles correctly
- [ ] Exponential backoff or retry strategy compiles properly
- [ ] Maximum retry limit logic compiles without errors

**User Notifications (10 sec):**
- [ ] Error notification creation compiles correctly
- [ ] Persistent failure alerts compile properly
- [ ] User-friendly error messages compile without errors

## Phase 4: Testing & Quality Assurance (8 detailed steps)

### Step 14: Unit Testing Suite

#### 14.1. **Core Logic Tests**
- RuleMatcher regex auto-detection and matching
- AlarmScheduler request code generation
- TimezoneUtils conversion accuracy
- Database operations and migrations

#### ⚡ Quick Verification for Step 14.1 (< 1 minute):
**Test Compilation (15 sec):**
- [ ] `./gradlew testDebugUnitTest --dry-run` shows all test classes
- [ ] Unit test dependencies compile correctly
- [ ] Mock frameworks (Mockito, etc.) imports work properly

**Core Logic Tests (30 sec):**
- [ ] `./gradlew testDebugUnitTest --tests RuleMatcherTest` passes (<10 sec)
- [ ] `./gradlew testDebugUnitTest --tests AlarmSchedulerTest` passes (<10 sec)
- [ ] `./gradlew testDebugUnitTest --tests TimezoneUtilsTest` passes (<10 sec)

**Database Tests (10 sec):**
- [ ] Room database migration tests compile correctly
- [ ] DAO test methods compile and run without errors
- [ ] In-memory database tests complete successfully

---

#### 14.2. **Repository Testing**
- Mock calendar provider responses
- Test event change detection logic
- Verify alarm scheduling/cancellation

#### ⚡ Quick Verification for Step 14.2 (< 1 minute):
**Repository Test Setup (20 sec):**
- [ ] `./gradlew compileDebugUnitTest` succeeds with repository tests
- [ ] Mock objects for calendar provider compile correctly
- [ ] Test repository implementations compile without errors

**Calendar Provider Mocking (25 sec):**
- [ ] `./gradlew testDebugUnitTest --tests CalendarRepositoryTest` passes quickly
- [ ] Mock cursor responses work correctly in tests
- [ ] Event parsing tests complete successfully

**Alarm Repository Tests (10 sec):**
- [ ] `./gradlew testDebugUnitTest --tests AlarmRepositoryTest` passes
- [ ] Alarm CRUD operations test correctly
- [ ] Change detection logic tests pass successfully

### Step 15: Integration Testing

#### 15.1. **End-to-End Flows**
- Create test calendar → run worker → verify alarms
- Permission flow testing with different states
- Background worker reliability testing

#### ⚡ Quick Verification for Step 15.1 (< 1 minute):
**Integration Test Setup (20 sec):**
- [ ] `./gradlew connectedDebugAndroidTest --dry-run` shows integration tests
- [ ] Test app module compiles with androidTest dependencies
- [ ] Instrumented test imports (Espresso, AndroidJUnit) work correctly

**End-to-End Flow (25 sec):**
- [ ] `./gradlew connectedDebugAndroidTest --tests E2EAlarmTest` runs and passes
- [ ] Test creates mock calendar event and verifies alarm scheduled
- [ ] Worker execution test completes in reasonable time

**Permission Testing (10 sec):**
- [ ] Permission grant/deny test scenarios compile correctly
- [ ] UI automation for permission flows works properly
- [ ] Permission state verification tests pass

---

#### 15.2. **Device State Testing**
- Test alarms in Do Not Disturb mode
- Battery optimization enabled/disabled
- Different Android versions (API 26-34)

#### ⚡ Quick Verification for Step 15.2 (< 1 minute):
**Device State Setup (15 sec):**
- [ ] Device state testing framework compiles correctly
- [ ] DND mode testing setup compiles without errors
- [ ] Battery optimization test utilities compile properly

**DND Testing (25 sec):**
- [ ] Test alarm firing in Do Not Disturb mode works correctly
- [ ] Alarm activity bypass DND verification completes
- [ ] Sound and vibration testing in DND mode works

**Battery & API Testing (15 sec):**
- [ ] Battery optimization state detection tests compile correctly
- [ ] Multi-API level compatibility tests run successfully
- [ ] Version-specific behavior tests pass correctly

### Step 16: Manual Testing & Validation

#### 16.1. **Real-World Scenarios**
- Multiple Google Calendar accounts
- Recurring events with exceptions
- Timezone travel simulation
- Long-term reliability (multi-day testing)

#### ⚡ Quick Verification for Step 16.1 (< 1 minute):
**Multi-Account Setup (20 sec):**
- [ ] App displays events from multiple Google accounts correctly
- [ ] Calendar selection shows all available calendars
- [ ] Rules can be configured per calendar from different accounts

**Recurring Events (25 sec):**
- [ ] Recurring events show individual instances correctly
- [ ] Event exceptions (single instance changes) handled properly
- [ ] Alarm scheduling works for modified recurring event instances

**Timezone Simulation (10 sec):**
- [ ] Change device timezone → verify alarms reschedule correctly
- [ ] Time display updates appropriately in UI
- [ ] Alarm times remain accurate after timezone changes

---

#### 16.2. **Performance Testing**
- Large number of calendar events (1000+)
- Multiple rules with complex regex patterns
- Background worker battery usage monitoring

#### ⚡ Quick Verification for Step 16.2 (< 1 minute):
**Large Dataset Testing (25 sec):**
- [ ] App remains responsive with 1000+ calendar events
- [ ] Event filtering and rule matching complete in reasonable time (<5 sec)
- [ ] Database queries remain fast with large datasets

**Complex Rules (20 sec):**
- [ ] Multiple rules with regex patterns process efficiently
- [ ] Rule matching accuracy maintained with complex patterns
- [ ] UI remains responsive during rule evaluation

**Battery Monitoring (10 sec):**
- [ ] Background worker shows reasonable battery usage in system settings
- [ ] WorkManager scheduling respects device battery optimization
- [ ] No excessive wake locks or CPU usage detected