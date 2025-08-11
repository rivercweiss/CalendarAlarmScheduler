 Phase 1: Reactive Programming Modernization

  1.1 Replace LiveData with StateFlow/SharedFlow
  - Convert ViewModels from LiveData to StateFlow for state management
  - Use SharedFlow for one-time events (navigation, snackbars)
  - Benefits: Better coroutine integration, null safety, lifecycle-aware

  1.2 Implement Sealed Classes for UI States
  - Create sealed classes for loading, success, error states
  - Replace manual state management with structured state objects
  - Example: UiState<T> sealed class pattern

  Phase 2: Fragment Architecture Enhancement

  2.1 Modernize BaseFragment Pattern
  - Leverage the existing BaseFragment more effectively
  - Convert fragments to use the base class consistently
  - Add generic type support for ViewBinding

  2.2 Improve ViewBinding Usage
  - Consolidate ViewBinding patterns across fragments
  - Use delegation for cleaner binding management
  - Add extension functions for common binding operations

  Phase 3: Error Handling & State Management

  3.1 Implement Result/Either Pattern
  - Replace try-catch blocks with Result<T> return types
  - Create consistent error handling across repositories
  - Use sealed classes for error types

  3.2 Enhanced Null Safety
  - Audit and improve nullable type usage
  - Use requireNotNull() and checkNotNull() appropriately
  - Implement defensive programming patterns

  Phase 4: Modern Kotlin Features

  4.1 Enhance Data Classes & Type Safety
  - Use @JvmInline value classes for type safety
  - Implement builder patterns with DSLs where appropriate
  - Use extension functions for cleaner code

  4.2 Coroutines & Flow Optimization
  - Standardize on flowOf, asFlow() patterns
  - Use combine(), zip() for reactive programming
  - Implement proper error handling in flows

  Phase 5: UI/UX Enhancements

  5.1 Material Design 3 Consistency
  - Audit and upgrade Material components
  - Implement consistent theming patterns
  - Use Material You dynamic colors where appropriate

  5.2 Layout Optimization
  - Optimize ConstraintLayout usage
  - Implement view recycling best practices
  - Use <merge> tags where appropriate

  Phase 6: Code Organization & Architecture

  6.1 Repository Pattern Enhancement
  - Implement single source of truth patterns
  - Use caching strategies with Flow
  - Create consistent data layer interfaces

  6.2 Dependency Injection Improvements
  - Audit Hilt usage for optimal scoping
  - Use @Binds instead of @Provides where possible
  - Implement proper testing doubles

Lets work on this step:


We need to fix a bug/incorrect behavior that is still broken.

When the user toggles from "show only matching rules" to "show all”, or the opposite, the UI does not autoscroll to the top.

We need you to understand the logs, understand the code to determine where these issues come from, and make a comprehensive fix for the issues.

Please figure out how to get to the root cause. Dig in, read relevant files, read the logs, and prepare to discuss the ins and out of how it works. Make a good step by step plan to implement these fixes. think hard


We are concerned about bugs in our code hidden in the logs.

We need you to understand the logs, understand the code to determine where these issues come from, and make a comprehensive fix for the issues.

Please figure out how to get to the root cause of the app crash. Dig in, read relevant files, read the logs, and prepare to discuss the ins and out of how it works. Make a good step by step plan to implement these fixes. Ultrathink


---

We want to update the app to have a one time, 2.00 in app purchase to unlock the ability to have the alarms display the event name rather than just a generic “Alarm” display.

---

Can you build the project and check and fix all warnings? Please check along the way to see everything still builds and works.

We don’t want to suppress any lint warnings, we want to fix them. Check for any supressed lint warnings and fix the root cause. For example, @Suppress("NewApi")

Run the build, check warnings and then come up with a plan. Think hard

---

- Background app refresh should not be a soft requirement, it must be a hard requirement for the app to function. Please make it mandatory for the app to function, along with all the other permissions. Update the CLAUDE.md as needed.
- There should be no skip option in the permissions onboard.

---

Add toggle for “Only alarm for first event of the day."

---

We are planning and researching the best way to implement:

A comprehensive end to end test which tests the app in the exact same interactive way as the user.

We want to research the current best practices for how to implement this.

Don’t look for any existing test setups, we want to make a completely new one based on best practices.

Dig in, research online, read the relevant documentation, and prepare to discuss the ins and out of how it works. Think hard.

  Phase 5: UI/UX Enhancements

  5.1 Material Design 3 Consistency
  - Audit and upgrade Material components
  - Implement consistent theming patterns
  - Use Material You dynamic colors where appropriate

  5.2 Layout Optimization
  - Optimize ConstraintLayout usage
  - Implement view recycling best practices
  - Use <merge> tags where appropriate

---

We are adding an automated, end to end test which tests the app in the exact same interactive way as the user, both on an emulator or a real connected device.

We are going to use Espresso and UI Automator to accomplish this purpose.

We are only going to make ONE, SINGLE test (one test file and flow), which will comprehensively test app functionality and can be used for regression testing.

Please do these three tasks and ONLY these tasks:

1) Research current best practices for using Espresso and UI Automator to develop automated, end to end test which runs both on an emulator or a real connected device. 
2) Set up any necessary framework. We need to be able to track app memory usage compared to baseline phone memory usage, logs, and other app statistics during the test runs. We need to inject calendar events to test. We need to be able to speed up test times to test far out calendar events.
3) Set up a SINGLE test which only opens the app and returns and is able to inspect and test comprehensive data including logs, memory usage, and any other important data.

Ultrathink.

We need to set up any necessary framework

We are building an app which:

<app_description>
This app automatically sets **unmissable system alarms** a configurable amount of time before certain Google Calendar events. Users define **rules** to trigger alarms only for events whose titles contain specific keywords.

Alarms MUST play regardless of phone state (silent mode, do not disturb, low power mode, etc.) to ensure users never miss important events.

The app runs **fully locally** with **no backend**, using Android APIs to read calendar data and set exact alarms.
<app_description>

We are planning and researching the best way to implement:

An automated, end to end test which tests the app in the exact same interactive way as the user, both on an emulator or a real connected device.

We want to research the current best practices for how to implement this.

Please research best practices and determine what to use for this purpose.

Please run and verify the functionality of the test, fixing any issues.

Then add a concise summary of whatever the next claude will need to know about the test to the CLAUDE.md.