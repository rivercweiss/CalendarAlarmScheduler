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
- `getAlarmsByEventId(eventId: String): Flow<List<ScheduledAlarm>>` - Get alarms by event ID
- `getAlarmsByRuleId(ruleId: String): Flow<List<ScheduledAlarm>>` - Get alarms by rule ID
- `getAlarmByEventAndRule(eventId: String, ruleId: String): ScheduledAlarm?` - **suspend** - Get alarm by event and rule
- `getAlarmById(id: String): ScheduledAlarm?` - **suspend** - Get alarm by ID
- `getAlarmsInTimeRange(startTime: Long, endTime: Long): Flow<List<ScheduledAlarm>>` - Get alarms in time range
- `insertAlarm(alarm: ScheduledAlarm)` - **suspend** - Insert alarm
- `insertAlarms(alarms: List<ScheduledAlarm>)` - **suspend** - Insert multiple alarms
- `updateAlarm(alarm: ScheduledAlarm)` - **suspend** - Update alarm
- `deleteAlarm(alarm: ScheduledAlarm)` - **suspend** - Delete alarm
- `deleteAlarmById(id: String)` - **suspend** - Delete alarm by ID
- `deleteAlarmsByEventId(eventId: String)` - **suspend** - Delete alarms by event ID
- `deleteAlarmsByRuleId(ruleId: String)` - **suspend** - Delete alarms by rule ID
- `setAlarmDismissed(id: String, dismissed: Boolean = true)` - **suspend** - Set alarm dismissed status
- `updateAlarmRequestCode(id: String, newRequestCode: Int)` - **suspend** - Update alarm request code
- `deleteExpiredAlarms(cutoffTime: Long = System.currentTimeMillis() - (24 * 60 * 60 * 1000))` - **suspend** - Delete expired alarms
- `getUpcomingAlarms(hoursAhead: Int = 24): Flow<List<ScheduledAlarm>>` - Get upcoming alarms
- `scheduleAlarmForEvent(eventId: String, ruleId: String, eventTitle: String, eventStartTimeUtc: Long, leadTimeMinutes: Int, lastEventModified: Long): ScheduledAlarm` - **suspend** - Schedule alarm for event
- `updateAlarmForChangedEvent(eventId: String, ruleId: String, eventTitle: String, eventStartTimeUtc: Long, leadTimeMinutes: Int, lastEventModified: Long): ScheduledAlarm?` - **suspend** - Update alarm for changed event
- `dismissAlarm(alarmId: String)` - **suspend** - Dismiss alarm
- `undismissAlarm(alarmId: String)` - **suspend** - Undismiss alarm
- `reactivateAlarm(alarmId: String): Boolean` - **suspend** - Reactivate alarm
- `cleanupOldAlarms()` - **suspend** - Clean up old alarms
- `markAlarmDismissed(alarmId: String)` - **suspend** - Mark alarm as dismissed
- `shouldRescheduleAlarm(eventId: String, ruleId: String, newLastModified: Long): Boolean` - **suspend** - Check if alarm should be rescheduled
- `markMultipleAlarmsDismissed(alarmIds: List<String>)` - **suspend** - Mark multiple alarms as dismissed
- `checkSystemStateAndUpdateDismissals(onAlarmDismissed: (String, String) -> Unit = { _, _ -> }): List<String>` - **suspend** - Check system state and update dismissals
- `handleDismissedAlarms(dismissedAlarms: List<com.example.calendaralarmscheduler.domain.models.ScheduledAlarm>)` - **suspend** - Handle dismissed alarms

### CalendarRepository.kt
- `getUpcomingEvents(calendarIds: List<Long>? = null, lastSyncTime: Long? = null): List<CalendarEvent>` - **suspend** - Query calendar events within the 2-day lookahead window
- `getAvailableCalendars(): List<CalendarInfo>` - **suspend** - Get all available calendars on the device
- `parseInstancesCursor(cursor: Cursor): List<CalendarEvent>` - **private** - Parse cursor from CalendarContract.Instances query
- `parseEventsCursor(cursor: Cursor): List<CalendarEvent>` - **private** - Parse cursor from CalendarContract.Events query
- `getEventsInLookAheadWindow(): List<CalendarEvent>` - **suspend** - Get events in the lookahead window
- `getCalendarsWithNames(): Map<Long, String>` - **suspend** - Get calendars as a map of ID to display name
- `hasCalendarPermission(): Boolean` - Check if we have calendar read permission
- `queryEventsInternal(startTimeUtc: Long, endTimeUtc: Long, calendarIds: List<Long>? = null, lastModified: Long? = null): List<CalendarEvent>` - **suspend** **private** - Internal method for querying calendar events

### RuleRepository.kt
- `getAllRules(): Flow<List<Rule>>` - Get all rules
- `getEnabledRules(): Flow<List<Rule>>` - Get enabled rules
- `getRuleById(id: String): Rule?` - **suspend** - Get rule by ID
- `getRulesByCalendarId(calendarId: Long): Flow<List<Rule>>` - Get rules by calendar ID
- `insertRule(rule: Rule)` - **suspend** - Insert rule
- `insertRules(rules: List<Rule>)` - **suspend** - Insert multiple rules
- `updateRule(rule: Rule)` - **suspend** - Update rule
- `deleteRule(rule: Rule)` - **suspend** - Delete rule
- `deleteRuleById(id: String)` - **suspend** - Delete rule by ID
- `setRuleEnabled(id: String, enabled: Boolean)` - **suspend** - Set rule enabled status
- `getActiveRulesForCalendars(calendarIds: List<Long>): Flow<List<Rule>>` - Get active rules for calendars
- `toggleRuleEnabled(id: String)` - **suspend** - Toggle rule enabled status
- `getAllRulesSync(): List<Rule>` - **suspend** - Get all rules synchronously
- `createDefaultRules(): List<Rule>` - **suspend** - Create default rules

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
- `getAllDayDefaultTimeFormatted(): String` - Get formatted all-day default time
- `getRefreshIntervalDescription(): String` - Get refresh interval description
- `resetToDefaults()` - Reset all settings to defaults

### Database DAOs

#### AlarmDao.kt
- `getAllAlarms(): Flow<List<ScheduledAlarm>>` - **@Query** - Get all alarms ordered by alarm time
- `getAllAlarmsSync(): List<ScheduledAlarm>` - **@Query** **suspend** - Get all alarms synchronously
- `getActiveAlarmsAll(): Flow<List<ScheduledAlarm>>` - **@Query** - Get all active (non-dismissed) alarms
- `getActiveAlarmsSync(currentTimeUtc: Long): List<ScheduledAlarm>` - **@Query** **suspend** - Get active alarms synchronously
- `getAlarmsByEventId(eventId: String): Flow<List<ScheduledAlarm>>` - **@Query** - Get alarms by event ID
- `getAlarmsByRuleId(ruleId: String): Flow<List<ScheduledAlarm>>` - **@Query** - Get alarms by rule ID
- `getAlarmByEventAndRule(eventId: String, ruleId: String): ScheduledAlarm?` - **@Query** **suspend** - Get alarm by event and rule
- `getAlarmById(id: String): ScheduledAlarm?` - **@Query** **suspend** - Get alarm by ID
- `getAlarmsInTimeRange(startTime: Long, endTime: Long): Flow<List<ScheduledAlarm>>` - **@Query** - Get alarms in time range
- `insertAlarm(alarm: ScheduledAlarm)` - **@Insert** **suspend** - Insert alarm
- `insertAlarms(alarms: List<ScheduledAlarm>)` - **@Insert** **suspend** - Insert multiple alarms
- `updateAlarm(alarm: ScheduledAlarm)` - **@Update** **suspend** - Update alarm
- `deleteAlarm(alarm: ScheduledAlarm)` - **@Delete** **suspend** - Delete alarm
- `deleteAlarmById(id: String)` - **@Query** **suspend** - Delete alarm by ID
- `deleteAlarmsByEventId(eventId: String)` - **@Query** **suspend** - Delete alarms by event ID
- `deleteAlarmsByRuleId(ruleId: String)` - **@Query** **suspend** - Delete alarms by rule ID
- `setAlarmDismissed(id: String, dismissed: Boolean)` - **@Query** **suspend** - Set alarm dismissed status
- `updateAlarmRequestCode(id: String, newRequestCode: Int)` - **@Query** **suspend** - Update alarm request code
- `deleteExpiredAlarms(cutoffTime: Long)` - **@Query** **suspend** - Delete expired alarms
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
- `getRulesByCalendarId(calendarId: Long): Flow<List<Rule>>` - **@Query** - Get rules by calendar ID
- `insertRule(rule: Rule)` - **@Insert** **suspend** - Insert rule
- `insertRules(rules: List<Rule>)` - **@Insert** **suspend** - Insert multiple rules
- `updateRule(rule: Rule)` - **@Update** **suspend** - Update rule
- `deleteRule(rule: Rule)` - **@Delete** **suspend** - Delete rule
- `deleteRuleById(id: String)` - **@Query** **suspend** - Delete rule by ID
- `deleteAllRules()` - **@Query** **suspend** - Delete all rules
- `setRuleEnabled(id: String, enabled: Boolean)` - **@Query** **suspend** - Set rule enabled status

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
- `provideAlarmSchedulingService(alarmRepository: AlarmRepository, alarmScheduler: AlarmScheduler): AlarmSchedulingService` - **@Provides** **@Singleton** - Provide AlarmSchedulingService
- `provideRuleAlarmManager(ruleRepository: RuleRepository, alarmRepository: AlarmRepository, alarmScheduler: AlarmScheduler, calendarRepository: CalendarRepository, alarmSchedulingService: AlarmSchedulingService): RuleAlarmManager` - **@Provides** **@Singleton** - Provide RuleAlarmManager

### DatabaseModule.kt
- `provideAppDatabase(@ApplicationContext context: Context): AppDatabase` - **@Provides** **@Singleton** - Provide App Database
- `provideRuleDao(database: AppDatabase): RuleDao` - **@Provides** - Provide Rule DAO
- `provideAlarmDao(database: AppDatabase): AlarmDao` - **@Provides** - Provide Alarm DAO

### RepositoryModule.kt
- `provideRuleRepository(ruleDao: RuleDao): RuleRepository` - **@Provides** **@Singleton** - Provide Rule Repository
- `provideAlarmRepository(alarmDao: AlarmDao): AlarmRepository` - **@Provides** **@Singleton** - Provide Alarm Repository
- `provideCalendarRepository(@ApplicationContext context: Context): CalendarRepository` - **@Provides** **@Singleton** - Provide Calendar Repository
- `provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository` - **@Provides** **@Singleton** - Provide Settings Repository

### WorkerModule.kt
- `provideWorkerManager(@ApplicationContext context: Context): WorkerManager` - **@Provides** **@Singleton** - Provide Worker Manager

## Domain Layer

### AlarmScheduler.kt
- `scheduleAlarm(alarm: ScheduledAlarm): Boolean` - **suspend** - Schedule alarm with basic validation
- `cancelAlarm(alarm: ScheduledAlarm): Boolean` - **suspend** - Cancel alarm
- `rescheduleAlarm(oldAlarm: ScheduledAlarm, newAlarm: ScheduledAlarm): Boolean` - **suspend** - Reschedule alarm
- `isAlarmScheduled(alarm: ScheduledAlarm): Boolean` - Check if alarm is scheduled
- `canScheduleExactAlarms(): Boolean` - Check if can schedule exact alarms
- `createAlarmIntent(alarm: ScheduledAlarm): Intent` - **private** - Create alarm intent
- `createPendingIntent(alarm: ScheduledAlarm, intent: Intent): PendingIntent` - **private** - Create pending intent

### AlarmSchedulingService.kt
- `processMatchesAndScheduleAlarms(matches: List<RuleMatcher.MatchResult>, logPrefix: String = "AlarmSchedulingService"): SchedulingResult` - **suspend** - Process rule matches and schedule alarms with simplified result tracking

### RuleAlarmManager.kt
- `updateRuleEnabled(rule: Rule, enabled: Boolean): RuleUpdateResult` - **suspend** - Update rule enabled status and handle all associated alarm operations
- `cleanupExpiredOperations()` - **private** - Clean up expired operations
- `cancelAlarmsForRule(rule: Rule): RuleUpdateResult` - **suspend** **private** - Disable rule and cancel all associated alarms
- `rescheduleAlarmsForRule(rule: Rule): RuleUpdateResult` - **suspend** **private** - Enable rule and schedule alarms for matching events
- `updateRuleWithAlarmManagement(oldRule: Rule, newRule: Rule): RuleUpdateResult` - **suspend** - Update rule and handle all associated alarm operations
- `deleteRuleWithAlarmCleanup(rule: Rule): RuleUpdateResult` - **suspend** - Delete rule and cancel all associated alarms

### RuleMatcher.kt
- `findMatchingRules(events: List<CalendarEvent>, rules: List<Rule>, defaultAllDayHour: Int = 20, defaultAllDayMinute: Int = 0): List<MatchResult>` - Find matching rules for events
- `findMatchingRulesForEvent(event: CalendarEvent, rules: List<Rule>, defaultAllDayHour: Int = 20, defaultAllDayMinute: Int = 0): List<MatchResult>` - Find matching rules for a single event
- `findMatchingEventsForRule(rule: Rule, events: List<CalendarEvent>, defaultAllDayHour: Int = 20, defaultAllDayMinute: Int = 0): List<MatchResult>` - Find matching events for a single rule
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

### BootReceiver.kt
- `onReceive(context: Context?, intent: Intent?)` - **override** - Handle boot/update broadcasts
- `rescheduleAllAlarms()` - **private** **suspend** - Re-register all active alarms with AlarmManager
- `schedulePeriodicWorker()` - **private** - Restart periodic calendar refresh worker

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
- **SettingsFragment.kt** - Fragment functions for settings
- **SettingsViewModel.kt** - ViewModel functions for settings

## Utilities

### Logger.kt
- `initialize(context: Context, isDebug: Boolean)` - Initialize logger
- `v(tag: String, message: String, throwable: Throwable? = null)` - Log verbose
- `d(tag: String, message: String, throwable: Throwable? = null)` - Log debug
- `i(tag: String, message: String, throwable: Throwable? = null)` - Log info
- `w(tag: String, message: String, throwable: Throwable? = null)` - Log warning
- `e(tag: String, message: String, throwable: Throwable? = null)` - Log error
- `crash(tag: String, message: String, throwable: Throwable? = null)` - Log crash
- `logPerformance(tag: String, operation: String, timeMs: Long)` - Log performance metrics
- `logUserAction(action: String, details: String = "")` - Log user actions
- `logLifecycle(component: String, state: String, details: String = "")` - Log lifecycle events
- `logNavigation(from: String, to: String, action: String = "")` - Log navigation
- `logDatabase(operation: String, table: String, details: String = "", timeMs: Long? = null)` - Log database operations
- `logPermission(permission: String, granted: Boolean, rationale: String = "")` - Log permission events
- `dumpContext(tag: String, context: Any?)` - Dump context information
- `dumpSystemInfo(tag: String)` - Dump system information
- `log(level: Level, tag: String, message: String, throwable: Throwable?)` - **private** - Internal log function
- `writeToFile(level: Level, tag: String, message: String, throwable: Throwable?)` - **private** - Write to file
- `getCurrentLogFile(context: Context): File` - **private** - Get current log file
- `cleanOldLogFiles()` - **private** - Clean old log files
- `getAvailableMemory(): Long` - **private** - Get available memory
- `getFreeStorage(): Long` - **private** - Get free storage
- `getLogFiles(): List<File>` - Get log files
- `exportLogs(): String?` - Export logs

### PermissionUtils.kt
- `hasCalendarPermission(context: Context): Boolean` - Check if we have calendar read permission
- `hasExactAlarmPermission(context: Context): Boolean` - Check if we have exact alarm scheduling permission (Android 12+)
- `hasNotificationPermission(context: Context): Boolean` - Check if we have notification permission (Android 13+)
- `isBatteryOptimizationWhitelisted(context: Context): Boolean` - Check if the app has background usage permissions
- `getBackgroundUsageStatus(context: Context): BackgroundUsageDetector.BackgroundUsageStatus` - Get detailed background usage status
- `getAllPermissionStatus(context: Context): PermissionStatus` - Check all critical permissions at once
- `requestCalendarPermission(launcher: ActivityResultLauncher<String>)` - Request calendar permission
- `requestNotificationPermission(launcher: ActivityResultLauncher<String>)` - Request notification permission (Android 13+)
- `requestMultiplePermissions(launcher: ActivityResultLauncher<Array<String>>, permissions: Array<String>)` - Request multiple permissions
- `getExactAlarmSettingsIntent(context: Context): Intent?` - Get intent to open exact alarm settings (Android 12+)
- `getNotificationSettingsIntent(context: Context): Intent` - Get intent to open notification settings
- `getBestBatteryOptimizationIntent(context: Context): BatteryOptimizationResult` - Get the best battery optimization intent based on device capabilities
- `getModernBackgroundUsageIntent(context: Context, deviceInfo: DozeCompatibilityUtils.DeviceInfo): BatteryOptimizationResult` - **private** - Get intent for modern background usage controls
- `getGranularPermissionsIntent(context: Context, deviceInfo: DozeCompatibilityUtils.DeviceInfo): BatteryOptimizationResult` - **private** - Get intent for granular permissions
- `getAdaptiveBatteryIntent(context: Context, deviceInfo: DozeCompatibilityUtils.DeviceInfo): BatteryOptimizationResult` - **private** - Get intent for Adaptive Battery
- `getOEMCustomIntent(context: Context, deviceInfo: DozeCompatibilityUtils.DeviceInfo): BatteryOptimizationResult` - **private** - Get intent for OEM custom battery management
- `getLegacyOptimizationIntent(context: Context): BatteryOptimizationResult` - **private** - Get intent for legacy battery optimization
- `getUnknownDeviceIntent(context: Context): BatteryOptimizationResult` - **private** - Get intent for unknown devices
- `isDirectWhitelistReliable(context: Context): Boolean` - **private** - Check if direct whitelist intent is reliable
- `canResolveIntent(context: Context, intent: Intent): Boolean` - **private** - Check if an intent can be resolved
- `getBatteryOptimizationInstructions(context: Context): List<String>` - Get detailed instructions for manual battery optimization whitelist
- `getSamsungBatteryIntent(context: Context): BatteryOptimizationResult?` - **private** - Get Samsung-specific battery intent
- `getXiaomiBatteryIntent(context: Context): BatteryOptimizationResult?` - **private** - Get Xiaomi-specific battery intent
- `getHuaweiBatteryIntent(context: Context): BatteryOptimizationResult?` - **private** - Get Huawei-specific battery intent
- `getOnePlusBatteryIntent(context: Context): BatteryOptimizationResult?` - **private** - Get OnePlus-specific battery intent
- `getAppName(context: Context): String` - **private** - Get app name for display
- `getOppoBatteryIntent(context: Context): BatteryOptimizationResult?` - **private** - Get Oppo-specific battery intent
- `getVivoBatteryIntent(context: Context): BatteryOptimizationResult?` - **private** - Get Vivo-specific battery intent
- `getAppSettingsIntent(context: Context): Intent` - Get intent to open general app settings
- `shouldShowCalendarPermissionRationale(activity: androidx.fragment.app.FragmentActivity): Boolean` - Check if we should show permission rationale
- `shouldShowNotificationPermissionRationale(activity: androidx.fragment.app.FragmentActivity): Boolean` - Check if we should show notification permission rationale (Android 13+)
- `getPermissionStatusMessage(context: Context): List<PermissionMessage>` - Get user-friendly permission status messages
- `hasAllCriticalPermissions(context: Context): Boolean` - Check if all critical permissions are granted
- `getMissingPermissions(context: Context): List<String>` - Get the list of permissions that need to be requested

### AlarmNotificationManager.kt
- `showAlarmNotification(alarmId: String, eventTitle: String, eventStartTime: Long, ruleId: String?, isTestAlarm: Boolean)` - Show unmissable alarm notification with sound and vibration
- `dismissAlarmNotification(alarmId: String)` - Dismiss specific alarm notification
- `createNotificationChannel()` - **private** - Create high-priority notification channel that bypasses DND

### Other Utilities
- **BackgroundUsageDetector.kt** - Functions for detecting background usage permissions
- **BackgroundUsageTest.kt** - Functions for testing background usage permission status
- **CrashHandler.kt** - Global exception handling functions
- **DozeCompatibilityUtils.kt** - Functions for Doze mode detection and compatibility
- **ErrorNotificationManager.kt** - Functions for user error notifications
- **RetryManager.kt** - Functions for exponential backoff retry logic
- **TimezoneUtils.kt** - Functions for timezone handling and conversion

## Workers

### CalendarRefreshWorker.kt
- `doWork(): Result` - **override** **suspend** - Simple calendar refresh and alarm scheduling

### WorkerManager.kt
- `schedulePeriodicRefresh(intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES)` - Schedule periodic calendar refresh with specified interval
- `cancelPeriodicRefresh()` - Cancel periodic calendar refresh
- `reschedulePeriodicRefresh(newIntervalMinutes: Int)` - Reschedule with new interval
- `enqueueImmediateRefresh()` - Enqueue immediate one-time calendar refresh
- `getWorkStatus(): WorkStatus` - Get current work status information
- `createWorkConstraints(): Constraints` - **private** - Create work constraints optimized for calendar refresh
- `checkBatteryOptimizationStatus()` - **private** - Check comprehensive background usage status
- `isDeviceInDozeMode(): Boolean` - Check if the device is in Doze mode (API 23+)
- `isBatteryOptimizationIgnored(): Boolean` - Get comprehensive background usage status
- `getBackgroundUsageStatus(): BackgroundUsageDetector.BackgroundUsageStatus` - Get detailed background usage status for debugging
- `validateInterval(intervalMinutes: Int): Boolean` - Validate interval value
- `getIntervalDescription(intervalMinutes: Int): String` - Get human-readable interval description
- `checkDozeCompatibility()` - **private** - Check Doze mode compatibility and log warnings