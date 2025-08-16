# CalendarAlarmScheduler - Detailed Function Index

This index provides comprehensive documentation of all functions in the codebase, organized by file and architectural layer. Each function includes its signature, modifiers, and description.

## Test Files

### CalendarTestDataProvider.kt
- `validateTestCalendarSetup(): Boolean` - Validates that LOCAL test calendar environment is properly set up
- `getLocalTestCalendarId(): Long?` - Gets LOCAL test calendar ID only - never returns user calendars
- `queryEventsFromTestCalendar(testCalendarId: Long): List<CalendarEvent>` - Query events only from verified LOCAL test calendar
- `getEventsMatchingKeyword(keyword: String): List<CalendarEvent>` - Get events matching keyword pattern from LOCAL test calendar only
- `queryTestEvents(): List<CalendarEvent>` - Query test events from LOCAL test calendar only
- `getStressTestEvents(): List<CalendarEvent>` - Get predefined stress test events
- `getFutureEvents(): List<CalendarEvent>` - Get predefined future events for background refresh testing
- `getMultiRuleMatchingEvents(): List<CalendarEvent>` - Get events that should match multiple rules
- `queryEvents(calendarId: Long? = null, fromTime: Long = System.currentTimeMillis()): List<CalendarEvent>` - Query events for verification
- `queryAllEvents(): List<CalendarEvent>` - Get all events (including past ones) for comprehensive testing

### ComprehensiveE2ETest.kt
- `setup()` - **@Before** - Initialize test framework components
- `cleanup()` - **@After** - Test method cleanup
- `testComprehensiveUserFlow()` - **@Test** - Comprehensive end-to-end test for standard user flow
- `launchAppAndVerifyOnboarding(): Boolean` - **private** - Launch app and verify onboarding flow appears

### TestMetricsCollector.kt
- `captureBaseline(): MemorySnapshot` - Capture baseline system metrics before test execution
- `captureMemorySnapshot(): MemorySnapshot` - Capture current memory snapshot
- `measureOperation<T>(operationName: String, operation: () -> T): T` - Time an operation and record performance metrics
- `collectAppLogs(maxEntries: Int = 1000): List<LogEntry>` - Collect application logs with filtering
- `parseLogLine(line: String): LogEntry?` - **private** - Parse log line
- `detectMemoryLeaks(): MemoryLeakReport` - Check for memory leaks by comparing current usage to baseline
- `generateTestReport(): TestReport` - Generate comprehensive test report
- `buildSummary(snapshot: MemorySnapshot, leakReport: MemoryLeakReport): String` - **private** - Build summary

### TestTimeController.kt
- `getCurrentTime(): Long` - Get current test time (may be manipulated)
- `fastForward(milliseconds: Long)` - Fast forward time by specified amount
- `jumpToTime(targetTime: Long)` - Jump to specific time
- `resetTime()` - Reset to real system time
- `createTimeScenarios(): List<TimeScenario>` - Create time scenarios for comprehensive testing
- `setupTimeScenario(scenario: TimeScenario)` - Set up time scenario for testing
- `simulateTimePassage(duration: Long, steps: Int = 10, onTimeStep: ((Long, Int) -> Unit)? = null)` - Simulate time passing for alarm testing
- `getTimeBasedTestEvents(calendarProvider: CalendarTestDataProvider): List<CalendarTestDataProvider.CalendarEvent>` - Get predefined events at specific time intervals
- `waitForAlarms(maxWaitTime: Long = 30000, checkInterval: Long = 1000): AlarmWaitResult` - Wait for alarms to potentially fire
- `generateTimeReport(): TimeTestReport` - Generate time-based test report

## Application

### CalendarAlarmApplication.kt
- `onRefreshIntervalChanged(newIntervalMinutes: Int)` - Handle settings changes that require worker rescheduling
- `setupTimezoneChangeHandling()` - **private** - Set up timezone change handling
- `handleTimezoneChange()` - **private** - Handle timezone changes by resetting last sync time
- `onCreate()` - **override** - Application initialization
- `logSystemMemoryUsage(context: String)` - **private** - Log comprehensive system memory usage
- `onTerminate()` - **override** - Application termination cleanup
- `onLowMemory()` - **override** - Handle low memory warnings
- `onTrimMemory(level: Int)` - **override** - Handle memory trim requests

## Data Layer

### AlarmRepository.kt
- `getAllAlarms(): Flow<List<ScheduledAlarm>>` - Get all alarms
- `getActiveAlarms(): Flow<List<ScheduledAlarm>>` - Get active alarms with caching
- `getCurrentTimeThreshold(): Long` - **private** - Get cached time threshold
- `getActiveAlarmsSync(): List<ScheduledAlarm>` - **suspend** - Get active alarms synchronously
- `getAlarmsByRuleId(ruleId: String): Flow<List<ScheduledAlarm>>` - Get alarms by rule ID
- `getAlarmByEventAndRule(eventId: String, ruleId: String): ScheduledAlarm?` - **suspend** - Get alarm by event and rule
- `getAlarmById(id: String): ScheduledAlarm?` - **suspend** - Get alarm by ID
- `insertAlarm(alarm: ScheduledAlarm)` - **suspend** - Insert alarm
- `updateAlarm(alarm: ScheduledAlarm)` - **suspend** **private** - Update alarm
- `deleteAlarmsByRuleId(ruleId: String)` - **suspend** - Delete alarms by rule ID
- `setAlarmDismissed(id: String, dismissed: Boolean = true)` - **suspend** - Set alarm dismissed status
- `deleteExpiredAlarms(cutoffTime: Long = System.currentTimeMillis() - (24 * 60 * 60 * 1000))` - **suspend** **private** - Delete expired alarms
- `scheduleAlarmForEvent(eventId: String, ruleId: String, eventTitle: String, eventStartTimeUtc: Long, leadTimeMinutes: Int, lastEventModified: Long): ScheduledAlarm` - **suspend** - Schedule alarm for event
- `updateAlarmForChangedEvent(eventId: String, ruleId: String, eventTitle: String, eventStartTimeUtc: Long, leadTimeMinutes: Int, lastEventModified: Long): ScheduledAlarm?` - **suspend** - Update alarm for changed event
- `undismissAlarm(alarmId: String)` - **suspend** - Undismiss alarm
- `cleanupOldAlarms()` - **suspend** - Clean up old alarms
- `markAlarmDismissed(alarmId: String)` - **suspend** - Mark alarm as dismissed

### CalendarRepository.kt
- `getUpcomingEvents(calendarIds: List<Long>? = null, lastSyncTime: Long? = null): List<CalendarEvent>` - **suspend** - Query calendar events within the 2-day lookahead window
- `getAvailableCalendars(): List<CalendarInfo>` - **suspend** - Get all available calendars on the device
- `parseInstancesCursor(cursor: Cursor): List<CalendarEvent>` - **private** - Parse cursor from CalendarContract.Instances query
- `parseEventsCursor(cursor: Cursor): List<CalendarEvent>` - **private** - Parse cursor from CalendarContract.Events query
- `getEventsInLookAheadWindow(): List<CalendarEvent>` - **suspend** - Get events in the lookahead window
- `getCalendarsWithNames(): Map<Long, String>` - **suspend** - Get calendars as a map of ID to display name

### RuleRepository.kt
- `getAllRules(): Flow<List<Rule>>` - Get all rules
- `getEnabledRules(): Flow<List<Rule>>` - Get enabled rules
- `getRuleById(id: String): Rule?` - **suspend** - Get rule by ID
- `insertRule(rule: Rule)` - **suspend** - Insert rule
- `insertRules(rules: List<Rule>)` - **suspend** **private** - Insert multiple rules
- `updateRule(rule: Rule)` - **suspend** - Update rule
- `deleteRule(rule: Rule)` - **suspend** - Delete rule
- `getAllRulesSync(): List<Rule>` - **suspend** - Get all rules synchronously

### SettingsRepository.kt
- `setOnRefreshIntervalChanged(callback: (Int) -> Unit)` - Set the callback for refresh interval changes
- `getRefreshIntervalMinutes(): Int` - Get refresh interval in minutes
- `setRefreshIntervalMinutes(minutes: Int)` - Set refresh interval in minutes
- `getAllDayDefaultHour(): Int` - Get all-day default hour
- `getAllDayDefaultMinute(): Int` - Get all-day default minute
- `setAllDayDefaultTime(hour: Int, minute: Int)` - Set all-day default time
- `isOnboardingCompleted(): Boolean` - Check if onboarding is completed
- `setOnboardingCompleted(completed: Boolean)` - Set onboarding completed status
- `isFirstLaunch(): Boolean` - Check if this is first launch
- `setFirstLaunchCompleted()` - Set first launch as completed
- `getLastSyncTime(): Long` - Get last sync time
- `setLastSyncTime(timestamp: Long)` - Set last sync time
- `updateLastSyncTime()` - Update last sync time to current time
- `hasEverSynced(): Boolean` - Check if ever synced
- `handleTimezoneChange()` - Handle timezone changes by resetting last sync time
- `isBatteryOptimizationSetupCompleted(): Boolean` - Check if battery optimization setup has been completed
- `setBatteryOptimizationSetupCompleted(completed: Boolean)` - Mark battery optimization setup as completed
- `isPremiumPurchased(): Boolean` - Check if premium features are purchased
- `setPremiumPurchased(purchased: Boolean)` - Set premium purchase status with reactive updates
- `getAllDayDefaultTimeFormatted(): String` - Get formatted all-day default time
- `getRefreshIntervalDescription(): String` - Get refresh interval description
- `resetToDefaults()` - Reset all settings to defaults including premium status

### DayTrackingRepository.kt
- `markRuleTriggeredToday(ruleId: String)` - Mark a rule as having triggered an alarm today
- `hasRuleTriggeredToday(ruleId: String): Boolean` - Check if a rule has already triggered an alarm today
- `getRulesTriggeredToday(): Set<String>` - Get all rules that have triggered today
- `forceReset()` - Force reset of day tracking (for testing or timezone changes)
- `handleTimezoneChange()` - Handle timezone change by forcing a reset
- `getDebugInfo(): Map<String, Any>` - Get current tracking status for debugging
- `getCurrentLocalDateString(): String` - **private** - Get current local date as string
- `checkAndResetForNewDay()` - **private** - Check if we've moved to a new day and reset tracking if so
- `resetDayTracking(newDate: String)` - **private** - Reset all day tracking for a new day

### Database DAOs

#### AlarmDao.kt
- `getAllAlarms(): Flow<List<ScheduledAlarm>>` - **@Query** - Get all alarms ordered by alarm time
- `getActiveAlarmsAll(): Flow<List<ScheduledAlarm>>` - **@Query** - Get all active (non-dismissed) alarms
- `getActiveAlarmsSync(currentTimeUtc: Long): List<ScheduledAlarm>` - **@Query** **suspend** - Get active alarms synchronously
- `getAlarmsByRuleId(ruleId: String): Flow<List<ScheduledAlarm>>` - **@Query** - Get alarms by rule ID
- `getAlarmByEventAndRule(eventId: String, ruleId: String): ScheduledAlarm?` - **@Query** **suspend** - Get alarm by event and rule
- `getAlarmById(id: String): ScheduledAlarm?` - **@Query** **suspend** - Get alarm by ID
- `insertAlarm(alarm: ScheduledAlarm)` - **@Insert** **suspend** - Insert alarm
- `updateAlarm(alarm: ScheduledAlarm)` - **@Update** **suspend** - Update alarm
- `deleteAlarmsByRuleId(ruleId: String)` - **@Query** **suspend** - Delete alarms by rule ID
- `setAlarmDismissed(id: String, dismissed: Boolean)` - **@Query** **suspend** - Set alarm dismissed status
- `updateAlarmRequestCode(id: String, newRequestCode: Int)` - **@Query** **suspend** - Update alarm request code
- `deleteExpiredAlarms(cutoffTime: Long)` - **@Query** **suspend** - Delete expired alarms
- `setAlarmDismissed(id: String, dismissed: Boolean)` - **@Query** **suspend** - Set alarm dismissed status
- `deleteAllAlarms()` - **@Query** **suspend** - Delete all alarms

#### AppDatabase.kt
- `ruleDao(): RuleDao` - **abstract** - Get rule DAO
- `alarmDao(): AlarmDao` - **abstract** - Get alarm DAO
- `getInstance(context: Context): AppDatabase` - **Companion** - Get database singleton instance
- `destroyInstance()` - **Companion** - Destroy database instance

#### RuleDao.kt
- `getAllRules(): Flow<List<Rule>>` - **@Query** - Get all rules ordered by creation date
- `getAllRulesSync(): List<Rule>` - **@Query** **suspend** - Get all rules synchronously
- `getEnabledRules(): Flow<List<Rule>>` - **@Query** - Get enabled rules
- `getRuleById(id: String): Rule?` - **@Query** **suspend** - Get rule by ID
- `insertRule(rule: Rule)` - **@Insert** **suspend** - Insert rule
- `insertRules(rules: List<Rule>)` - **@Insert** **suspend** - Insert multiple rules
- `updateRule(rule: Rule)` - **@Update** **suspend** - Update rule
- `deleteRule(rule: Rule)` - **@Delete** **suspend** - Delete rule

### Database Entities

#### Rule.kt
- `isValid(): Boolean` - Validate rule data
- `matchesEvent(event: com.example.calendaralarmscheduler.domain.models.CalendarEvent): Boolean` - Check if rule matches event
- `autoDetectRegex(pattern: String): Boolean` - **Companion** - Auto-detect if pattern is regex
- `fromLongList(value: List<Long>): String` - **@TypeConverter** - Convert list to string
- `toLongList(value: String): List<Long>` - **@TypeConverter** - Convert string to list

#### ScheduledAlarm.kt
- `isInPast(): Boolean` - Check if alarm time is in the past
- `isActive(): Boolean` - Check if alarm is active (not dismissed and in future)
- `getLocalAlarmTime(): ZonedDateTime` - Get local alarm time
- `generateRequestCode(eventId: String, ruleId: String): Int` - **Companion** - Generate simple request code


## DI Modules

### AlarmModule.kt
- `provideAlarmManager(@ApplicationContext context: Context): AlarmManager` - **@Provides** - Provide AlarmManager
- `provideAlarmScheduler(@ApplicationContext context: Context, alarmManager: AlarmManager): AlarmScheduler` - **@Provides** **@Singleton** - Provide AlarmScheduler
- `provideRuleAlarmManager(ruleRepository: RuleRepository, alarmRepository: AlarmRepository, alarmScheduler: AlarmScheduler, calendarRepository: CalendarRepository): RuleAlarmManager` - **@Provides** **@Singleton** - Provide RuleAlarmManager

### DatabaseModule.kt
- `provideAppDatabase(@ApplicationContext context: Context): AppDatabase` - **@Provides** **@Singleton** - Provide App Database
- `provideRuleDao(database: AppDatabase): RuleDao` - **@Provides** - Provide Rule DAO
- `provideAlarmDao(database: AppDatabase): AlarmDao` - **@Provides** - Provide Alarm DAO

### RepositoryModule.kt
- `provideRuleRepository(ruleDao: RuleDao): RuleRepository` - **@Provides** **@Singleton** - Provide Rule Repository
- `provideAlarmRepository(alarmDao: AlarmDao): AlarmRepository` - **@Provides** **@Singleton** - Provide Alarm Repository
- `provideCalendarRepository(@ApplicationContext context: Context): CalendarRepository` - **@Provides** **@Singleton** - Provide Calendar Repository
- `provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository` - **@Provides** **@Singleton** - Provide Settings Repository
- `provideBillingManager(@ApplicationContext context: Context, settingsRepository: SettingsRepository): BillingManager` - **@Provides** **@Singleton** - Provide Google Play Billing Manager

### BackgroundRefreshModule.kt
- `provideBackgroundRefreshManager(@ApplicationContext context: Context): BackgroundRefreshManager` - **@Provides** **@Singleton** - Provide Background Refresh Manager

## Domain Layer

### AlarmScheduler.kt
- `scheduleAlarm(alarm: ScheduledAlarm): Boolean` - **suspend** - Schedule alarm with basic validation
- `cancelAlarm(alarm: ScheduledAlarm): Boolean` - **suspend** - Cancel alarm
- `rescheduleAlarm(oldAlarm: ScheduledAlarm, newAlarm: ScheduledAlarm): Boolean` - **suspend** - Reschedule alarm
- `isAlarmScheduled(alarm: ScheduledAlarm): Boolean` - Check if alarm is scheduled
- `canScheduleExactAlarms(): Boolean` - Check if can schedule exact alarms
- `createAlarmIntent(alarm: ScheduledAlarm): Intent` - **private** - Create alarm intent
- `createPendingIntent(alarm: ScheduledAlarm, intent: Intent): PendingIntent` - **private** - Create pending intent

### RuleAlarmManager.kt
- `updateRuleEnabled(rule: Rule, enabled: Boolean): RuleUpdateResult` - **suspend** - Update rule enabled status and handle all associated alarm operations
- `cancelAlarmsForRule(rule: Rule): RuleUpdateResult` - **suspend** **private** - Disable rule and cancel all associated alarms
- `rescheduleAlarmsForRule(rule: Rule): RuleUpdateResult` - **suspend** **private** - Enable rule and schedule alarms for matching events
- `updateRuleWithAlarmManagement(oldRule: Rule, newRule: Rule): RuleUpdateResult` - **suspend** - Update rule and handle all associated alarm operations
- `deleteRuleWithAlarmCleanup(rule: Rule): RuleUpdateResult` - **suspend** - Delete rule and cancel all associated alarms
- `processMatchesAndScheduleAlarms(matches: List<RuleMatcher.MatchResult>, logPrefix: String = "RuleAlarmManager"): SchedulingResult` - **suspend** - Process rule matches and schedule alarms with integrated result tracking

### RuleMatcher.kt
- `findMatchingRules(events: List<CalendarEvent>, rules: List<Rule>, defaultAllDayHour: Int = 20, defaultAllDayMinute: Int = 0): List<MatchResult>` - Find matching rules for events with first-event-of-day checking
- `findMatchingRulesForEvent(event: CalendarEvent, rules: List<Rule>, defaultAllDayHour: Int = 20, defaultAllDayMinute: Int = 0): List<MatchResult>` - Find matching rules for a single event with first-event-of-day checking
- `findMatchingEventsForRule(rule: Rule, events: List<CalendarEvent>, defaultAllDayHour: Int = 20, defaultAllDayMinute: Int = 0): List<MatchResult>` - Find matching events for a single rule with first-event-of-day logic
- `markRuleTriggeredToday(ruleId: String)` - Mark a rule as triggered for today (called when alarm is actually scheduled)
- `validateRulePattern(pattern: String, isRegex: Boolean): ValidationResult` - Validate rule pattern
- `testRuleAgainstEvent(rule: Rule, event: CalendarEvent): Boolean` - Test rule against event
- `findDuplicateAlarms(newMatches: List<MatchResult>, existingAlarms: List<ScheduledAlarm>): List<ScheduledAlarm>` - Find duplicate alarms
- `filterOutDismissedAlarms(matches: List<MatchResult>, existingAlarms: List<ScheduledAlarm>): List<MatchResult>` - Filter out dismissed alarms
- `autoDetectRegex(pattern: String): Boolean` - **Companion** - Auto-detect regex pattern
- `isRegex(pattern: String): Boolean` - **Companion** - Check if pattern is regex

### Domain Models

#### CalendarEvent.kt
- `isInPast(): Boolean` - Check if event is in the past
- `getStartTimeInTimezone(zoneId: ZoneId): ZonedDateTime` - Get start time in timezone
- `getEndTimeInTimezone(zoneId: ZoneId): ZonedDateTime` - Get end time in timezone
- `getLocalStartTime(): ZonedDateTime` - Get local start time
- `getLocalEndTime(): ZonedDateTime` - Get local end time
- `getDurationMinutes(): Long` - Get duration in minutes
- `isMultiDay(): Boolean` - Check if event spans multiple days
- `computeAlarmTimeUtc(leadTimeMinutes: Int): Long` - Compute alarm time UTC
- `computeAllDayAlarmTimeUtc(defaultTimeHour: Int, defaultTimeMinute: Int, leadTimeMinutes: Int): Long` - Compute all-day alarm time UTC
- `fromCalendarContract(id: String, title: String, dtstart: Long, dtend: Long, calendarId: Long, allDay: Int, eventTimezone: String?, lastModified: Long, description: String? = null, location: String? = null): CalendarEvent` - **Companion** - Create from calendar contract
- `convertToUtc(timeMillis: Long, timezoneId: String): Long` - **Companion** **private** - Convert to UTC
- `addMinutes(minutes: Int): CalendarEvent` - **Extension** - Add minutes to event
- `toUtcString(): String` - **Extension** - Convert to UTC string
- `toLocalString(): String` - **Extension** - Convert to local string


## Receivers

### AlarmReceiver.kt
- `onReceive(context: Context?, intent: Intent?)` - **override** - Process alarm broadcasts from AlarmManager

### AlarmDismissReceiver.kt
- Functions not detailed in provided content

### BackgroundRefreshReceiver.kt
- `onReceive(context: Context, intent: Intent)` - **override** - Handle background refresh broadcasts (periodic and immediate)
- `performRefresh(context: Context, isPeriodicRefresh: Boolean)` - **private** - Execute calendar refresh and alarm scheduling
- `scheduleNextRefreshIfNeeded(backgroundRefreshManager: BackgroundRefreshManager, settingsRepository: SettingsRepository, isPeriodicRefresh: Boolean)` - **private** **suspend** - Schedule next refresh cycle for periodic refreshes

### BootReceiver.kt
- `onReceive(context: Context?, intent: Intent?)` - **override** - Handle boot/update broadcasts
- `rescheduleAllAlarms()` - **private** **suspend** - Re-register all active alarms with AlarmManager
- `schedulePeriodicBackgroundRefresh()` - **private** - Restart periodic background refresh with AlarmManager

### TimezoneChangeReceiver.kt
- Functions not detailed in provided content

## UI Layer

### BaseFragment.kt
- Functions not detailed in provided content

### MainActivity.kt
- Functions not detailed in provided content


### Onboarding UI
- **OnboardingPagerAdapter.kt** - ViewPager adapter functions
- **OnboardingStepFragment.kt** - Fragment functions for onboarding steps
- **PermissionOnboardingActivity.kt** - Permission onboarding activity functions

### Event Preview UI
- **EventPreviewAdapter.kt** - RecyclerView adapter functions
- **EventPreviewFragment.kt** - Fragment functions for event preview
- **EventPreviewViewModel.kt** - ViewModel functions for event preview

### Rule Management UI
- **CalendarPickerAdapter.kt** - Adapter functions for calendar selection
- **CalendarPickerDialog.kt** - Dialog functions for calendar picker
- **CalendarPickerItem.kt** - Data class for calendar picker items
- **CalendarPickerViewModel.kt** - ViewModel functions for calendar selection
- **LeadTimePickerDialog.kt** - Dialog functions for lead time selection
- **RuleAdapter.kt** - RecyclerView adapter functions for rule list
- **RuleEditFragment.kt** - Fragment functions for rule editing
- **RuleEditViewModel.kt** - ViewModel functions for rule editing
- **RuleListFragment.kt** - Fragment functions for rule list
- **RuleListViewModel.kt** - ViewModel functions for rule list

### Settings UI
- **SettingsFragment.kt** - Fragment functions for settings with premium UI management:
  - `setupBillingCallbacks()` - **private** - Set up billing manager callbacks for purchase flow
  - `updatePremiumUI(isPremium: Boolean)` - **private** - Update premium section UI based on purchase state
  - `togglePremiumForTesting()` - **private** - Debug toggle for testing premium states (debug builds only)
  - `launchPremiumPurchase()` - **private** - Launch Google Play purchase flow with error handling
  - `showPurchaseError(message: String)` - **private** - Display purchase error messages to user
- **SettingsViewModel.kt** - ViewModel functions for settings

## Utilities

### Logger.kt
- `initialize()` - Initialize logger (simplified - no file logging)
- `v(tag: String, message: String, throwable: Throwable? = null)` - Log verbose to Android logcat
- `d(tag: String, message: String, throwable: Throwable? = null)` - Log debug to Android logcat
- `i(tag: String, message: String, throwable: Throwable? = null)` - Log info to Android logcat
- `w(tag: String, message: String, throwable: Throwable? = null)` - Log warning to Android logcat
- `e(tag: String, message: String, throwable: Throwable? = null)` - Log error to Android logcat
- `crash(tag: String, message: String, throwable: Throwable? = null)` - Log crash to Android logcat
- `logPerformance(tag: String, operation: String, timeMs: Long)` - Log performance metrics
- `logUserAction(action: String, details: String = "")` - Log user actions
- `logLifecycle(component: String, state: String, details: String = "")` - Log lifecycle events
- `logNavigation(from: String, to: String, action: String = "")` - Log navigation
- `logDatabase(operation: String, table: String, details: String = "", timeMs: Long? = null)` - Log database operations
- `logPermission(permission: String, granted: Boolean, rationale: String = "")` - Log permission events
- `log(level: Int, tag: String, message: String, throwable: Throwable?)` - **private** - Internal log function

### PermissionUtils.kt
- `hasCalendarPermission(context: Context): Boolean` - Check if we have calendar read permission
- `hasExactAlarmPermission(context: Context): Boolean` - Check if we have exact alarm scheduling permission (Android 12+)
- `hasNotificationPermission(context: Context): Boolean` - Check if we have notification permission (Android 13+)
- `isBatteryOptimizationWhitelisted(context: Context): Boolean` - Check if the app has background usage permissions
- `getAllPermissionStatus(context: Context): PermissionStatus` - Check all critical permissions at once
- `requestCalendarPermission(launcher: ActivityResultLauncher<String>)` - Request calendar permission
- `requestNotificationPermission(launcher: ActivityResultLauncher<String>)` - Request notification permission (Android 13+)
- `requestMultiplePermissions(launcher: ActivityResultLauncher<Array<String>>, permissions: Array<String>)` - Request multiple permissions
- `getExactAlarmSettingsIntent(context: Context): Intent?` - Get intent to open exact alarm settings (Android 12+)
- `getNotificationSettingsIntent(context: Context): Intent` - Get intent to open notification settings
- `getBestBatteryOptimizationIntent(context: Context): BatteryOptimizationResult` - Get simple battery optimization intent with fallback strategy
- `getAppSettingsIntent(context: Context): Intent` - Get intent to open general app settings
- `shouldShowCalendarPermissionRationale(activity: androidx.fragment.app.FragmentActivity): Boolean` - Check if we should show permission rationale
- `shouldShowNotificationPermissionRationale(activity: androidx.fragment.app.FragmentActivity): Boolean` - Check if we should show notification permission rationale (Android 13+)
- `getPermissionStatusMessage(context: Context): List<PermissionMessage>` - Get user-friendly permission status messages
- `hasAllCriticalPermissions(context: Context): Boolean` - Check if all critical permissions are granted
- `getMissingPermissions(context: Context): List<String>` - Get the list of permissions that need to be requested

### AlarmNotificationManager.kt
- `showAlarmNotification(alarmId: String, eventTitle: String, eventStartTime: Long, ruleId: String?, isTestAlarm: Boolean)` - Show unmissable alarm notification with sound and vibration, gated by premium status
- `dismissAlarmNotification(alarmId: String)` - Dismiss specific alarm notification
- `createNotificationChannel()` - **private** - Create high-priority notification channel that bypasses DND

### BillingManager.kt
- `init` - Initialize billing client and connect to Google Play Billing service
- `setCallbacks(onStateChanged: (Boolean) -> Unit, onError: (String) -> Unit)` - Set callbacks for purchase state changes and errors
- `setupBillingClient()` - **private** - Set up billing client with purchase listener
- `connectToBillingService()` - **private** - Connect to Google Play Billing service with retry logic
- `isPremiumPurchased(): Boolean` - Check if premium features are purchased (cached state)
- `queryPurchases()` - **private** - Query existing purchases and update cached state
- `launchPurchaseFlow(activity: Activity)` - Launch purchase flow for premium upgrade with error handling
- `onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?)` - **override** - Handle purchase updates from Google Play
- `handlePurchase(purchase: Purchase)` - **private** - Process successful purchase with validation and acknowledgment
- `restorePurchases()` - Restore previous purchases for account recovery
- `disconnect()` - Disconnect billing client to prevent memory leaks

### Other Utilities
- **CrashHandler.kt** - Simplified global exception handling with basic crash logging
- **ErrorNotificationManager.kt** - Generic error notification system with consolidated error handling
- **TimezoneUtils.kt** - Essential timezone functions: DST detection, display names, change listeners

## Services

### DayResetService.kt
- `scheduleNextMidnightReset()` - Schedule the next midnight reset alarm
- `cancelMidnightReset()` - Cancel any existing midnight reset alarm
- `performDayReset()` - Perform the actual day reset and schedule the next one
- `handleTimezoneChange()` - Handle timezone change by cancelling and rescheduling reset
- `getNextMidnightInLocalTime(): ZonedDateTime` - **private** - Get the next midnight in local time
- `isResetAlarmScheduled(): Boolean` - Check if reset alarm is scheduled (for debugging)
- `getDebugInfo(): Map<String, String>` - Get debug information about next reset time

## Broadcast Receivers

### DayResetReceiver.kt
- `onReceive(context: Context, intent: Intent)` - **override** - Handle midnight reset broadcasts

### TimezoneChangeReceiver.kt (updated)
- `onReceive(context: Context, intent: Intent)` - **override** - Handle timezone and time change broadcasts
- `handleTimezoneChange(context: Context)` - **private** - Handle timezone changes including day tracking reset
- `handleTimeChange(context: Context)` - **private** - Handle system time changes
- `rescheduleAllAlarms(context: Context)` - **private** **suspend** - Reschedule all alarms after timezone/time change

## Workers

### BackgroundRefreshManager.kt
- `schedulePeriodicRefresh(intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES)` - Schedule periodic calendar refresh using AlarmManager with exact timing
- `cancelPeriodicRefresh()` - Cancel periodic calendar refresh alarm
- `reschedulePeriodicRefresh(newIntervalMinutes: Int)` - Reschedule with new interval (cancels old, creates new)
- `enqueueImmediateRefresh()` - Schedule immediate one-time calendar refresh with 1-second delay
- `getWorkStatus(): WorkStatus` - Get current background refresh status using PendingIntent existence check
- `scheduleNextPeriodicRefresh(intervalMinutes: Int)` - Schedule the next periodic refresh (used by receiver after execution)
- `checkBatteryOptimizationStatus()` - **private** - Check battery optimization status and log warnings
- `isDeviceInDozeMode(): Boolean` - Check if the device is in Doze mode (API 23+)
- `isBatteryOptimizationIgnored(): Boolean` - Check if battery optimization is ignored (app is whitelisted)
- `validateInterval(intervalMinutes: Int): Boolean` - Validate interval value against build-specific available intervals
- `getIntervalDescription(intervalMinutes: Int): String` - Get human-readable interval description with build-specific options
- `checkDozeCompatibility()` - **private** - Check battery optimization compatibility and log warnings