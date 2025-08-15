# CalendarAlarmScheduler - General File Index

This index provides a quick overview of all source files in the codebase to help understand the project structure and locate specific functionality.

## Test Files (`src/androidTest/`)

- **CalendarTestDataProvider.kt** - Provides test calendar data and event injection for controlled testing scenarios
- **ComprehensiveE2ETest.kt** - Main end-to-end test that covers all app functionality using Espresso and UI Automator
- **TestMetricsCollector.kt** - Collects memory usage, performance metrics, and detects memory leaks during testing
- **TestTimeController.kt** - Controls and accelerates time for testing future calendar events

## Application Core (`src/main/`)

### Application Entry
- **CalendarAlarmApplication.kt** - Main application class with Hilt initialization, crash handling, and global app setup

### Data Layer (`data/`)

#### Repositories
- **AlarmRepository.kt** - Manages scheduled alarms persistence and lifecycle with auto-cleanup and tracking
- **CalendarRepository.kt** - Fetches and manages Google Calendar events with change detection and filtering
- **RuleRepository.kt** - Manages alarm rules persistence with calendar filters and pattern validation
- **SettingsRepository.kt** - Manages app settings with DataStore, migration, and battery optimization tracking

#### Database (`data/database/`)
- **AppDatabase.kt** - Room database configuration with migrations and type converters
- **AlarmDao.kt** - Data access object for scheduled alarms with complex queries and conflict resolution
- **RuleDao.kt** - Data access object for alarm rules with calendar filtering support

#### Entities (`data/database/entities/`)
- **Rule.kt** - Database entity for alarm rules with keyword patterns and calendar filters
- **ScheduledAlarm.kt** - Database entity for scheduled alarms with timezone handling 

### Domain Layer (`domain/`)

#### Core Services
- **AlarmScheduler.kt** - Core alarm scheduling system using AlarmManager with exact alarm handling
- **AlarmSchedulingService.kt** - Shared alarm scheduling logic for workers and UI components
- **RuleAlarmManager.kt** - Manages relationship between rules and alarms with lifecycle management
- **RuleMatcher.kt** - Rule matching engine that finds events matching alarm rules with validation

#### Models (`domain/models/`)
- **CalendarEvent.kt** - Domain model for calendar events with UI formatting and recurrence handling
- **ScheduledAlarm.kt** - Domain model for scheduled alarms with enhanced collision-resistant request code generation

### Dependency Injection (`di/`)
- **AlarmModule.kt** - Hilt module providing alarm-related dependencies
- **DatabaseModule.kt** - Hilt module providing Room database dependencies
- **RepositoryModule.kt** - Hilt module providing repository dependencies
- **WorkerModule.kt** - Hilt module providing worker-related dependencies

### Broadcast Receivers (`receivers/`)
- **AlarmReceiver.kt** - Handles fired alarms and triggers notification system
- **AlarmDismissReceiver.kt** - Handles alarm dismissal actions from notifications
- **BootReceiver.kt** - Reschedules alarms after device boot or app update
- **TimezoneChangeReceiver.kt** - Handles timezone changes to reschedule alarms

### UI Layer (`ui/`)

#### Base Components
- **BaseFragment.kt** - Base fragment class with common functionality
- **MainActivity.kt** - Main activity with navigation and permission handling

#### Alarm UI (`ui/alarm/`)
- **AlarmActivity.kt** - Full-screen alarm display that bypasses do-not-disturb

#### Onboarding (`ui/onboarding/`)
- **OnboardingPagerAdapter.kt** - ViewPager adapter for onboarding flow
- **OnboardingStepFragment.kt** - Individual onboarding step fragments
- **PermissionOnboardingActivity.kt** - Step-by-step permission granting flow

#### Event Preview (`ui/preview/`)
- **EventPreviewAdapter.kt** - RecyclerView adapter for displaying calendar events
- **EventPreviewFragment.kt** - Fragment showing upcoming events with rule filtering
- **EventPreviewViewModel.kt** - ViewModel managing event preview state and filtering

#### Rule Management (`ui/rules/`)
- **CalendarPickerAdapter.kt** - Adapter for calendar selection list
- **CalendarPickerDialog.kt** - Dialog for selecting calendars per rule
- **CalendarPickerItem.kt** - Data class for calendar picker items
- **CalendarPickerViewModel.kt** - ViewModel for calendar selection
- **LeadTimePickerDialog.kt** - Dialog for selecting alarm lead time
- **RuleAdapter.kt** - RecyclerView adapter for rule list
- **RuleEditFragment.kt** - Fragment for creating/editing rules
- **RuleEditViewModel.kt** - ViewModel managing rule editing state
- **RuleListFragment.kt** - Fragment displaying all rules
- **RuleListViewModel.kt** - ViewModel managing rule list state

#### Settings (`ui/settings/`)
- **SettingsFragment.kt** - App settings and configuration UI
- **SettingsViewModel.kt** - ViewModel managing settings state

### Utilities (`utils/`)
- **AlarmNotificationManager.kt** - Creates and manages alarm notifications
- **BackgroundUsageDetector.kt** - Detects background usage permissions across Android versions
- **BackgroundUsageTest.kt** - Tests background usage permission status
- **CrashHandler.kt** - Global exception handling with logging and recovery
- **DozeCompatibilityUtils.kt** - Doze mode detection and compatibility testing
- **ErrorNotificationManager.kt** - User notifications for persistent errors
- **Logger.kt** - Centralized logging with performance metrics
- **PermissionUtils.kt** - Runtime permission handling utilities
- **RetryManager.kt** - Exponential backoff retry logic for operations
- **TimezoneUtils.kt** - Timezone handling and conversion utilities

### Background Workers (`workers/`)
- **CalendarRefreshWorker.kt** - Periodic background worker that scans calendar and schedules alarms
- **WorkerManager.kt** - Manages WorkManager scheduling with battery optimization checking

## Key Architecture Patterns

1. **MVVM Architecture** - ViewModels manage UI state with StateFlow
2. **Repository Pattern** - Data access abstraction layer
3. **Dependency Injection** - Hilt for dependency management
4. **Clean Architecture** - Clear separation between data, domain, and UI layers
5. **Reactive Programming** - StateFlow and Flow for reactive updates
6. **Background Processing** - WorkManager for reliable background tasks