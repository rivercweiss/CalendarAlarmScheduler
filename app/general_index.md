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
- **DayTrackingRepository.kt** - Tracks which rules have triggered today for "first event of day only" functionality
- **RuleRepository.kt** - Manages alarm rules persistence with calendar filters and pattern validation
- **SettingsRepository.kt** - Simple app settings management with reactive StateFlows and premium purchase state

#### Database (`data/database/`)
- **AppDatabase.kt** - Room database configuration with migrations and type converters
- **AlarmDao.kt** - Data access object for scheduled alarms with complex queries and conflict resolution
- **RuleDao.kt** - Data access object for alarm rules with calendar filtering support

#### Entities (`data/database/entities/`)
- **Rule.kt** - Database entity for alarm rules with keyword patterns and calendar filters
- **ScheduledAlarm.kt** - Database entity for scheduled alarms with basic utility methods

### Domain Layer (`domain/`)

#### Core Services
- **AlarmScheduler.kt** - Simple alarm scheduling using AlarmManager with essential methods only
- **RuleAlarmManager.kt** - Manages relationship between rules and alarms with integrated scheduling logic
- **RuleMatcher.kt** - Rule matching engine that finds events matching alarm rules with validation

#### Models (`domain/models/`)
- **CalendarEvent.kt** - Domain model for calendar events with UI formatting and recurrence handling

### Dependency Injection (`di/`)
- **AlarmModule.kt** - Hilt module providing alarm-related dependencies
- **DatabaseModule.kt** - Hilt module providing Room database dependencies
- **RepositoryModule.kt** - Hilt module providing repository and billing manager dependencies
- **BackgroundRefreshModule.kt** - Hilt module providing background refresh manager dependencies

### Services (`services/`)
- **DayResetService.kt** - Manages midnight reset alarms for day tracking boundaries

### Broadcast Receivers (`receivers/`)
- **AlarmReceiver.kt** - Handles fired alarms and triggers notification system with premium content gating
- **AlarmDismissReceiver.kt** - Handles alarm dismissal actions from notifications
- **BackgroundRefreshReceiver.kt** - Handles background calendar refresh via AlarmManager broadcasts
- **BootReceiver.kt** - Reschedules alarms and background refresh after device boot or app update
- **DayResetReceiver.kt** - Handles midnight reset broadcasts for day tracking
- **TimezoneChangeReceiver.kt** - Handles timezone changes to reschedule alarms

### UI Layer (`ui/`)

#### Base Components
- **BaseFragment.kt** - Base fragment class with common functionality
- **MainActivity.kt** - Main activity with navigation and permission handling


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
- **SettingsFragment.kt** - App settings and configuration UI with premium upgrade section and debug toggle
- **SettingsViewModel.kt** - ViewModel managing settings state

### Utilities (`utils/`)
- **AlarmNotificationManager.kt** - Creates unmissable alarm notifications that bypass DND and silent mode with premium content gating
- **BillingManager.kt** - Handles Google Play Billing for premium features with robust error handling and state management
- **CrashHandler.kt** - Simplified global exception handling with basic crash logging
- **ErrorNotificationManager.kt** - Generic error notification system with consolidated error handling
- **Logger.kt** - Simple Android logcat logging with structured message formatting
- **PermissionUtils.kt** - Simplified permission handling for calendar, notifications, exact alarms, and battery optimization
- **TimezoneUtils.kt** - Essential timezone display and change detection utilities

### Background Workers (`workers/`)
- **BackgroundRefreshManager.kt** - Manages AlarmManager-based background refresh with exact timing guarantees

## Key Architecture Patterns

1. **MVVM Architecture** - ViewModels manage UI state with StateFlow
2. **Repository Pattern** - Data access abstraction layer
3. **Dependency Injection** - Hilt for dependency management
4. **Clean Architecture** - Clear separation between data, domain, and UI layers
5. **Reactive Programming** - StateFlow and Flow for reactive updates
6. **Background Processing** - AlarmManager for guaranteed exact timing in background tasks
7. **Premium Features** - Google Play Billing integration with content gating and debug support