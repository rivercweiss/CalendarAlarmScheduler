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

Dismissing full screen alarm does not dismiss notification.

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

---
We are trying to set up a reusable end to end test system for the app, which has a test calendar setup and teardown. 

A previous developer was having issues getting this system to work.

You need to check their work and fix their mistakes.

They were struggling to make the system fully reusable and create entirely new test calendars and events each time (and remove them on test teardown). It is MANDATORY we setup and teardown a NEW and REPRODUCIBLE calendar system for the test.

The main issues they were running into:
1) The setup_test_calendar.sh thinks it is making calendar events, but neither I nor the app can see them in the emulator.
2) The calendar ID made by the setup script seems to be wrong, it is returning a user calendar.
3) The teardown is not fully working, but this could be an issue with the calendar ID.

The CORE ISSUE is the app needs to see the calendar events. We need to figure out how to find the root cause.

Please research the syntax of adb commands in bash scripts, how best to set up testable calendars in android emulators, and then make a plan to fix the previous developers work. Ultrathink.

---

We are going to implement the full test flow. Some of the test flow has already been implemented.

Full Test Flow:

</setup>
Start with a clean device in standard light mode with no other modes active with a fresh Scheduler install and any previous Scheduler app data cleaned.

Also start with various test calendar events in place. 
</setup>

</test_flow>
- Install the app, with no default permissions (we are going to add all the permissions as if we were a user)
- Launch the app, check memory usage stays under 30 megabytes.
- Perform the permissions workflow, enabling the permissions.
- Check there are no alarms scheduled, no rules scheduled, we can see the test calendar events in preview, and the settings permissions are shown correctly.
- Add a rule, and check the rule is added, the alarm is scheduled, there are no unexpected toasts or notifications, and the preview page shows the correct alarm scheduled.
- Change the system to dark mode, do not disturb, silent, bedtime mode and the maximum battery saver.
- Accelerate time until the alarm fires, and check the scheduled alarm fires correctly.
-  Add two more alarm rules that match different events. Check the rule is added, the alarm is scheduled, there are no unexpected toasts or notifications, and the preview page shows the correct alarm scheduled.
-  Accelerate time until both alarms have fired, and check the scheduled alarms both fire correctly.
-  Add a test calendar event 3 days out which matches one of the alarm rules. Check there is no alarm scheduled for this event.
-  Close and fully quit the app, then accelerate time until the test calendar event 3 days in the future alarm fires. This checks background refresh and alarm setting.
-  Add 100+ calendar events in the next 2 days in multiple calendars. Open the app and rapidly and randomly interact with the app only, closing and opening it and pressing all over the app to make it do random things. Check no crashes and memory usage is less than 30 mb.
-  The test ends and returns valid data
</test_flow>

Lets make a plan. Dig in, read the relevant files, research Espresso and UI Automator best practices and syntax and ultrathink. This plan MUST include running AND VERIFYING THE OUTPUT OF the full e2e test after every new test case/step addition to check the new test addition works as expected. Ultrathink

---

Renumber all the e2e test cases. Don’t changes anything else.

---

Something in the e2e test is causing the emulator to go black. Please dig deep into the logs to find a root cause. Look up how to fix any issues you find in the logs. ultrathink

---

We have an automated E2E test, but it is very slow. It needs to be as fast as possible, while still being very robust and capable of running on many devices/emulated devices with no flakiness. Your job is to make the test as fast as possible, WHILE MAINTAINING ALL TEST FUNCTIONALITY AND CURRENT COVERAGE, and making sure the test is bulletproof.

Lets make a plan. Dig in, read the relevant files, research Espresso and UI Automator best practices and syntax and ultrathink. This plan MUST include running the full e2e test after every new speed improvement to check the new speed improvement works as expected. Ultrathink


We have an automated calendar event loader setup_test_calendar.sh, but it is very slow. It needs to be as fast as possible, while still being very robust. Your job is to make the loader as fast as possible, WHILE MAINTAINING ALL FUNCTIONALITY AND CURRENT LOADED EVENTS.

I want you to become an expert in Espresso and UI Automator best practices and syntax for testing full end to end apps as efficiently as possible.

The key outcome of this app we need to test:
- Alarms ALWAYS fire correctly, with noise and a notification
We need to be able to robustly test this behavior, along with all the other app behaviors.

Lets become an expert in this topic. Dig in, deep research Espresso and UI Automator best practices and syntax for testing full end to end apps as efficiently as possible. Ultrathink

We need the run_e2e_test.sh to correctly uninstall and reinstall the application, and ensure all app files are deleted. Become an expert in any required topics to complete this exceptionally. Ultrathink.


// NOTE: Infrastructure setup (APK install, permissions, calendar data) 
// is handled by run_e2e_test.sh - no duplication here

We are working on making a single end to end test case that tests the standard user flow using UI Automator.

The flow:

- Opens the app to the permissions onboarding
- Steps through the permission onboarding, enabling permissions with UI Automator, as a user would
- Go to settings and trigger the test alarm
- Close the app
- Verify the test alarm fires by waiting for the alarm to notify, then successfully dismissing the alarm

There is one big issue with this test:

- We are not correctly detecting the alarm notification.

After each addition, run the E2E test to verify functionality. Remember, this is an end to end test, so we want to make sure we are properly testing functionality, NOT just getting the test to pass.

Ultrathink, research and become an expert in notification detection. We NEED this alarm detection to be reusable between different alarm events we will be testing.

Please start by becoming an expert, then examining the full logs (we just ran a test), and determine root cause.


warning: The following options were not recognized by an
    y processor: '[dagger.hilt.internal.useAggregatingRootPr
    ocessor, dagger.fastInit, dagger.hilt.android.internal.d


I want you to become an expert in Espresso and UI Automator best practices and syntax for testing full end to end apps as efficiently as possible.

The key outcome of this app we need to test:
- Alarms ALWAYS fire correctly, with noise and a notification

We need to be able to robustly test this behavior, along with all the other app behaviors.

Lets not read any files for now, we will read them later. For now, all I want you to do is web searches to become an expert.

Lets become an expert in this topic. Dig in, deep research Espresso and UI Automator best practices and syntax for testing full end to end apps as efficiently as possible. Ultrathink

Please deeply inspect the code and architecture and perform the following steps:

1) Update the blank file \@general_index.md to contain a list of all the files in the codebase along with a simple description of what each file does. The goal of this file to to get you (Claude) up to speed about the codebase super quickly.
2) Update the blank file \@detailed_index.md to contain the names of all the functions in each file along with an explanation/docstring for each function. The goal of this file is to allow you (Claude) to have a more in depth understanding of what each file and function in each file does without having to read all the code.
3) Update CLAUDE.md with access to this information. Make a note that these index files may or may not be up to date, and when making changes that affect them to update the relevant index files.

Think hard.

We are trying to clean up our code as much as possible.

We want our codebase to be as simple, elegant, and modular as possible, and easy to read and understand.

Can you look through \@general_index.md and \@detailed_index.md ad determine what cleanup we can do? Make a cleanup plan that prioritizes simplicity and modularity. Think hard.

Lets execute Priority 4: Fix Circular Dependencies and update the index files to reflect reality.

Lets execute Priority 6: Simplify Over-Engineering and update the index files to reflect reality. Try to do this as simply, modularly and robustly as possible, while deleting as much code as possible, while maintaining core functionality. Think hard 

When the app is freshly installed it fetches calendar events properly, but the refresh button does not actually refresh or fetch new calendar events. Please fix this issue in a memory safe way, trying to delete as much code as possible in the process. Try to do this as simply, modularly and robustly as possible. Think hard.

We are getting these warnings:

> Task :app:compileDebugKotlin
w: file:///Users/riverweiss/AndroidStudioProjects/CalendarAlarmScheduler/app/src/main/java/com/example/calendaralarmscheduler/ui/preview/EventPreviewViewModel.kt:122:10 This declaration is in a preview state and can be changed in a backwards-incompatible manner with a best-effort migration. Its usage should be marked with '@kotlinx.coroutines.FlowPreview' or '@OptIn(kotlinx.coroutines.FlowPreview::class)' if you accept the drawback of relying on preview API
w: file:///Users/riverweiss/AndroidStudioProjects/CalendarAlarmScheduler/app/src/main/java/com/example/calendaralarmscheduler/ui/preview/EventPreviewViewModel.kt:162:18 This declaration is in a preview state and can be changed in a backwards-incompatible manner with a best-effort migration. Its usage should be marked with '@kotlinx.coroutines.FlowPreview' or '@OptIn(kotlinx.coroutines.FlowPreview::class)' if you accept the drawback of relying on preview API

> Task :app:compileDebugJavaWithJavac
warning: The following options were not recognized by any processor: '[dagger.hilt.internal.useAggregatingRootProcessor, dagger.fastInit, dagger.hilt.android.internal.disableAndroidSuperclassValidation, dagger.hilt.android.internal.projectType]'
1 warning

Please fix this issue. Try to do this as simply, modularly and robustly as possible, while deleting as much code as possible. Think hard.



We removed the full screen part of the notification entirely, and just have an unmissable notification that rings an alarm independent of phone state (silent, dnd, battery saver) that we can dismiss in standard ways.

We need to update the settings UI and underlying code with this change, and not require full screen permissions.

Try to do this as simply, modularly and robustly as possible, while deleting as much code as possible, while maintaining core functionality. Think hard 