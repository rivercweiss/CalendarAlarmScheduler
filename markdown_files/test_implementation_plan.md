*Test Implementation Plan*

Each test has: purpose → preconditions → setup (device/test-build hooks) → explicit test steps (including adb / UiAutomator / Espresso suggestions) → observables & verification points → cleanup & flakiness notes. I also include practical automation patterns, test hooks you should add to the app to make deterministic e2e testing possible, and CI/device guidance.


1) Global test prerequisites & recommended test hooks (add these to a test-only build flavor)

Why: make E2E deterministic and fast (avoid waiting for periodic WorkManager windows, avoid unreliability of system settings manipulation).

Add a debugTest build flavor or instrumentation-only entrypoints that are present only in test builds:

    Broadcast-triggered immediate refresh

        Add a debug BroadcastReceiver (only in test build) that triggers the same code path as the CalendarRefreshWorker. Example action: com.example.calendaralarmscheduler.DEBUG_TRIGGER_REFRESH. Tests will adb shell am broadcast -a com.example.calendaralarmscheduler.DEBUG_TRIGGER_REFRESH.

    Test-only AlarmManager wrapper / hooks

        Provide an IAlarmScheduler interface and in tests allow a test implementation that still uses real AlarmManager but exposes internal request codes and scheduled alarm metadata (for verification). In device tests, the real AlarmManager must still fire; the wrapper just exposes what was scheduled.

    RequestCodeGenerator injection

        Make generation of pendingIntentRequestCode pluggable. Tests should be able to inject a deterministic generator and also a collision generator to simulate collisions.

    Test-only DB access endpoint

        For instrumentation tests: expose a content provider or direct Room DAO access (in instrumentation) to read ScheduledAlarm table quickly and assert scheduled rows.

    Test-only provider-error / collision simulation

        Add a feature flag that, when enabled, causes CalendarRepository to return a transient error on the first attempt or return crafted events that induce pending-intent collisions.

    Logging tags and event markers

        Use consistent log tags and event markers, e.g. Log.i("CAS-Test", "AlarmScheduled eventId=123 ruleId=7 fireAt=..."). Tests can adb logcat -s CAS-Test:I to detect events.

2) Test harness and tooling overview

    Use AndroidJUnitRunner instrumentation tests for in-app UI flows (Espresso).

    Use UiAutomator (UiDevice) for interacting with system settings, notification/DND settings, system permission dialogs, and OEM-specific settings screens.

    Use adb for granting simple permissions, rebooting, broadcasting debug intents, and changing timezone/time when possible (emulator).

    Use Logcat and direct DB queries for verification.

    Device matrix: Pixel (stock) + one aggressive OEM (Samsung or Xiaomi). Run the two-device matrix for the full suite.

    Make tests idempotent: use unique test event titles with a run id/time suffix.

3) Test cases (detailed)

There are 8 executable tests below: the original 6 plus the 2 small focused additions (OEM battery suppression + PendingIntent uniqueness). Where possible I fold scenarios into existing tests as you requested; I kept the total small while being thorough.
Test 1 — Onboarding, Permissions & Partial Revocation

Purpose: Validate full onboarding flow, runtime permission behavior, and that the app gracefully responds to permission revocations (including “Don’t ask again” / partial grants).

Preconditions

    Fresh app install (use adb uninstall | adb install in test setup).

    Test build with debug broadcast receiver available.

Setup

    Ensure device/emulator is at target SDK (34 emulate if needed).

    (Optional) adb shell pm clear <PACKAGE> to reset state.

Test steps

    Launch app via instrumentation. (Espresso ActivityScenario.launch(MainActivity::class.java)).

    Verify onboarding pager is shown (Espresso onView(withId(R.id.onboardingPager)).check(matches(isDisplayed()))).

    When calendar permission prompt appears:

        Use UiAutomator to deny READ_CALENDAR the first time (simulate user decline).

        Verify main UI blocks features and shows actionable guidance: onView(withId(R.id.permissionStatus)).check(matches(withText(containsString("Calendar permission required")))).

    Use adb to grant calendar permission:
    adb shell pm grant com.example.calendaralarmscheduler android.permission.READ_CALENDAR

    For exact alarm permissions (special on Android 12+):

        Use UiAutomator to navigate user to the system settings page that allows "Exact alarms" enabling for the app. (UI flow is OEM-dependent — open the app's settings page with adb shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:com.example.calendaralarmscheduler and click through.)

        Alternatively, if you added a test hook, broadcast a debug intent that simulates user enabling exact alarm in the app’s settings UI.

    Grant POST_NOTIFICATIONS similarly (adb shell pm grant ... android.permission.POST_NOTIFICATIONS for API 33+).

    Verify the permission dashboard in-app shows “All set” (Espresso check).

Verification

    ScheduledAlarm DB is empty (no alarms yet) — use test DAO.

    Logcat has "CAS-Test: PermissionsGranted" entry.

    Background worker does not crash when permissions are present.

Cleanup

    Revoke granted permissions at end of test if needed or reinstall afresh for next test.

Flakiness notes

    System permission dialogs can be flaky: prefer UiAutomator to interact with them or use pm grant where possible. Use retries for UI clicks.

Test 2 — Create Rule & Alarm Fires in Silent / DND

Purpose: Ensure rule creation, scheduling, and full-screen alarm behavior under silent/DND conditions.

Preconditions

    Permissions granted (from Test 1).

    Use test build with debug-refresh broadcast.

Setup

    Ensure device in silent or DND (use UiAutomator to toggle the quick settings tile or NotificationManager.setInterruptionFilter() where available in tests). If using emulator, adb shell settings put global zen_mode 1 may work (emulator-specific).

Test steps

    Espresso: navigate to Rule Management screen.

    Create rule:

        Title/keyword: Interview-<RUN_ID>

        Lead time: 15 minutes

        Calendar: choose calendar id (UI)

        Save (Espresso perform(click())).

    Insert a calendar event directly (instrumentation) into the device calendar provider for the selected calendar that starts 30 minutes from now:

        Use ContentResolver.insert(CalendarContract.Events.CONTENT_URI, eventValues) with fields: title="Interview-<RUN_ID>", dtstart epoch ms, dtend = start + 60 * 60 * 1000, allDay=0.

    Trigger immediate refresh via debug broadcast:
    adb shell am broadcast -a com.example.calendaralarmscheduler.DEBUG_TRIGGER_REFRESH

    Verify scheduled alarm row appears in DB: scheduled time = start - 15 min. (Query ScheduledAlarm table.)

    Wait for alarm to fire (or if you want short-run automation, schedule event to start in 5 minutes with lead time 1 minute to keep test fast).

    At alarm time verify:

        Full-screen AlarmActivity appears (UiAutomator: device.findObject(By.pkg(PACKAGE).textContains("Alarm"))).

        Sound plays even when DND/silent — assert via logcat entries: CAS-Test: AlarmFired eventId=... and/or verify sound/vibration state if device allows audio queries.

        Dismiss control works and sets userDismissed flag in DB.

Verification

    DB: ScheduledAlarm had a matching entry and is now either marked dismissed or removed after firing (per design).

    Logcat: "CAS-Test: AlarmFired" entry with correct fireAt.

    UI: Full-screen AlarmActivity was foregrounded.

Cleanup

    Delete created calendar event(s).

    Reset DND/ringer state.

Flakiness notes

    Some devices may throttle audio in DND — document acceptable fallback and assert fallback behavior if app cannot bypass (but the app requirement is that it must play — so test should fail if not).

Test 3 — All-Day Event Behaviour (lead time ignored)

Purpose: Verify all-day events schedule at the configured default time and ignore lead-time rules.

Preconditions

    App permissions granted.

Setup

    Set app setting: All-day default alarm time = 21:00 day-before. (Use Espresso to set in Settings.)

Test steps

    Add an all-day calendar event titled Conference-<RUN_ID> for tomorrow (use ContentResolver insert with allDay=1, dtstart at midnight local).

    Create a rule that matches Conference- with lead time 2 hours (Espresso).

    Trigger debug refresh broadcast.

    Verify scheduled alarm time is exactly 21:00 the day before (query DB scheduledAt column).

Verification

    DB: scheduledAt equals expected epoch ms (compute local timezone offset).

    UI preview shows correct scheduled alarm and indicates “all-day time”.

Cleanup

    Remove event.

Flakiness notes

    Ensure timezone conversion consistent; use absolute epoch check rather than local-string matches.

Test 4 — Event Change Detection, Occurrence Exceptions, Dismissal, and Timezone/DST Handling

Purpose: Covers event edits, recurring exceptions, user dismissal behavior, and timezone/DST/time-jump correctness.

Preconditions

    Permissions granted. Debug hooks enabled.

Setup

    Create a recurring event (e.g., weekly) with an instance that will be changed and another instance that will be canceled.

Test steps

    Create recurring event TeamMeeting-<RUN_ID> starting 1 hour from now (repeat weekly).

    Create a rule that matches TeamMeeting- with lead time 30 min.

    Trigger refresh broadcast. Verify scheduled alarm for the next occurrence.

    Modify the next occurrence (only one occurrence) to start at +2 hours (simulate exception) in CalendarContract (set an exception using CalendarContract.Instances or by inserting an event with ORIGINAL_ID/ORIGINAL_INSTANCE_TIME depending on provider capabilities). Trigger refresh again.

        Verify DB: previous alarm was canceled (or replaced) and a new alarm is scheduled at newStart - 30 min.

    Cancel (delete) the following occurrence; trigger refresh and verify alarm canceled.

    Dismissal behavior:

        Simulate user dismissing the alarm (if it fired) or mark userDismissed=true.

        Edit the event again (change lastModified) — verify the app treats it as a new event and WILL re-create alarms for the changed instance (per spec). Also verify the app does not reschedule a dismissed alarm for the unchanged instance.

    Timezone & DST handling:

        Change device timezone (emulator: adb shell setprop persist.sys.timezone "Europe/London" — note: some devices may require reboot or special permission). Alternatively use UiAutomator to open Settings → System → Date & time → Time zone and change it.

        After timezone change, trigger refresh and verify all future alarms are re-scheduled and local-fire-times displayed in UI are correct (assert DB epoch values shifted appropriately).

        Simulate a DST boundary by moving clock forward 1 hour (emulator permitted) and verify alarms still fire correctly relative to event local time.

        Also test a manual system clock jump (forward/back) and verify robust rescheduling.

Verification

    DB shows canceled and new alarm entries with proper timestamps.

    Logcat events: CAS-Test: AlarmCanceled eventId=..., CAS-Test: AlarmScheduled ....

    UI preview timezone indicator updated to new timezone.

Cleanup

    Remove created calendar events.

Flakiness notes

    Timezone operations differ across OEMs; prefer emulator for DST/time jumps, real devices for timezone-change behavior and then verify on aggressive OEM as well.

Test 5 — Multiple Rules & DuplicateHandlingMode

Purpose: Validate duplicate resolution logic (EARLIEST_ONLY / ALLOW_MULTIPLE etc.).

Preconditions

    Permissions granted; debug refresh available.

Setup

    Create two rules:

        Rule A: keyword Budget-<RUN_ID>, lead time 15 min.

        Rule B: same keyword, lead time 60 min.

Test steps

    Insert calendar event Budget-<RUN_ID> starting 90 minutes from now.

    Set DuplicateHandlingMode = EARLIEST_ONLY (Settings via Espresso).

    Trigger refresh. Verify only the earliest (60? Hmm check: EARLIEST_ONLY = earliest fire time, so for lead times 15 and 60 -> earliest fire = start - 60?) — be explicit in implementation: verify the app uses the policy's definition. Assert scheduled alarm corresponds to expected policy.

    Change DuplicateHandlingMode = ALLOW_MULTIPLE, trigger refresh, verify both alarms scheduled (two DB rows, unique pendingIntent request codes).

    Switch to LONGEST_LEAD_TIME and verify behavior accordingly.

Verification

    DB: number of scheduled alarms equals expected count.

    Each scheduled alarm has a unique pendingIntentRequestCode (see next test for collisions).

Cleanup

    Delete event & rules.

Flakiness notes

    If policy definitions are ambiguous, assert the app log message describing which rule was chosen.

Test 6 — Boot / Reboot Recovery (with boot variants)

Purpose: Ensure BootReceiver re-registers alarms after device restart for variety of OEM behaviors.

Preconditions

    At least one future scheduled alarm exists.

Setup

    Schedule an alarm for a time sufficiently in the future (e.g., tomorrow) for easy verification after reboot.

Test steps

    Verify scheduled alarm present in DB.

    Reboot device: adb reboot (emulator) or use device reboot scenario (real device).

    Wait for BootComplete (test runner must wait; use a broadcast or check for BootReceiver log). After boot, verify:

        BootReceiver executed (logcat CAS-Test: BootReceiver).

        App re-registered alarm with AlarmManager (DB row still present and an "AlarmRescheduled" log entry exists).

    Variant: If app was never opened after install prior to reboot, test whether BootReceiver behaves according to expected policy on that OEM (document behavior; if the app must require prior launch, assert guidance is shown to the user).

Verification

    DB rows intact.

    Alarm activity fires at expected time (for full E2E, schedule a short-future alarm and observe fire — but be careful: reboot then wait might be long; instead check BootReceiver logs).

Cleanup

    Remove scheduled alarm and events.

Flakiness notes

    Device reboots are heavy; make this a nightly or on-demand test for CI. On emulator it’s faster; on physical devices this is slower and may require human observation.

Test 7 — OEM Battery-Optimization Suppression (NEW)

Purpose: Detect and handle aggressive OEM battery optimizations that delay or block background refresh / worker scheduling.

Why this is separate: OEM behavior is outside app control and often requires interactive whitelisting; test must run on an aggressive OEM device.

Preconditions

    App installed on an aggressive OEM device (Samsung/Xiaomi/OnePlus).

Test steps

    Schedule an event that should trigger an alarm within a short time window.

    Enable battery optimization for the app (via Settings → Battery → Battery optimization; UiAutomator will toggle the setting). If toggling via adb is possible on the OEM, use that.

    Trigger refresh or allow WorkManager to run; observe: work may not run or alarms not scheduled.

    App must detect that refresh has been delayed (detect via Worker backoff, or check lastSyncTime stale) and show a prominent banner/instruction guiding the user to whitelist the app.

    Follow the app's suggested remedial flow (UiAutomator drive user to whitelist setting) and whitelist the app.

    Trigger refresh again and verify alarms now schedule.

Verification

    Before whitelisting: missing scheduled alarms (DB or log), and UI shows battery-warning.

    After whitelisting: DB contains scheduled alarms and Logcat contains CAS-Test: BackgroundWorkerSuccess.

Cleanup

    Revert battery optimization.

Flakiness notes

    This test is device/OEM-specific and may require manual confirmation. Mark as a must-run on real-device smoke tests in pre-release.

Test 8 — PendingIntent Uniqueness & Collision Injection (NEW)

Purpose: Detect collisions in pending-intent request codes which could cause alarms to overwrite each other.

Why separate: This is a small, fast deterministic test you can and should run in CI.

Setup

    Use test hook to replace RequestCodeGenerator with a deterministic generator (and a collision generator to intentionally produce duplicate codes).

Test steps (uniq case)

    Create two events with different ids and two different rules producing different (eventId + ruleId) values but ensure the default generator yields distinct ints.

    Trigger refresh.

    Assert DB has two scheduled alarms and both have distinct pendingIntentRequestCode values and both show up in logs as scheduled.

Test steps (collision simulation)

    Use test hook to force a collision (RequestCodeGenerator returns same int for two different event+rule).

    Trigger refresh.

    Observe behavior — correct behavior: app must detect collision and either:

        avoid overwriting (create alternate request code using a collision-avoidance strategy), or

        fail the test and log the collision as bug.

    Assert that both alarms are still tracked in DB with unique internal IDs even if system PendingIntent had to be adjusted; and assert that both fire independently when expected (if practical, schedule short lead times).

Verification

    DB contains separate ScheduledAlarm rows for both alarms.

    Logcat contains an entry for either collision-handling or error.

    When collision injection is used, assert the app handled it deterministically (document expected strategy in code).

Cleanup

    Reset RequestCodeGenerator test hook.

Flakiness notes

    This test should be deterministic and quick; run on every CI build.

4) Concrete automation recipes & example snippets
4.1 adb commands (examples)

    Grant calendar permission:
    adb shell pm grant com.example.calendaralarmscheduler android.permission.READ_CALENDAR

    Grant notification permission (API 33+):
    adb shell pm grant com.example.calendaralarmscheduler android.permission.POST_NOTIFICATIONS

    Trigger debug refresh broadcast:
    adb shell am broadcast -a com.example.calendaralarmscheduler.DEBUG_TRIGGER_REFRESH

    Reboot device:
    adb reboot

    Change timezone on emulator (may require root/emulator):
    adb shell setprop persist.sys.timezone "Europe/London" then adb shell am broadcast -a android.intent.action.TIMEZONE_CHANGED

        Note: timezone/time settings differ by device; prefer UiAutomator for system settings navigation on real devices.

4.2 UiAutomator (Kotlin) example for toggling DND / opening app settings

val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
// Open quick settings and toggle DND (example; UI structure varies)
device.openQuickSettings()
val dndTile = device.findObject(UiSelector().descriptionContains("Do not disturb"))
if (dndTile.exists()) dndTile.click()

// Open app details settings for exact alarm toggle
val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
    data = Uri.parse("package:com.example.calendaralarmscheduler")
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
InstrumentationRegistry.getInstrumentation().context.startActivity(intent)

4.3 Espresso (Kotlin) sample to create a rule

onView(withId(R.id.fab_add_rule)).perform(click())
onView(withId(R.id.ruleTitle)).perform(typeText("Interview-RUN42"), closeSoftKeyboard())
onView(withId(R.id.ruleKeyword)).perform(typeText("Interview-RUN42"), closeSoftKeyboard())
onView(withId(R.id.leadTimePicker)).perform(setLeadTime(15)) // custom ViewAction
onView(withId(R.id.saveRule)).perform(click())

4.4 DB verification (instrumentation)

    Use Room DAO in instrumentation test to query ScheduledAlarm:

val alarms = appDatabase.scheduledAlarmDao().getAlarmsForEvent(eventId)
assertThat(alarms).isNotEmpty()

    Or shell query (if DB accessible):
    adb shell "sqlite3 /data/data/com.example.calendaralarmscheduler/databases/app-db 'SELECT * FROM ScheduledAlarm;'"

4.5 Logcat check

    Capture and grep logs:
    adb logcat -d | grep "CAS-Test"

    In CI, tail logcat for a fixed period and assert presence of specific markers.

5) CI strategy & test prioritization

    Run-on-every-PR (fast & deterministic):

        Test 1 (permission basic path using pm grant), Test 2 (fast variant with 5min event), Test 8 (PendingIntent uniqueness collision simulation). These should be kept <10 minutes total.

    Nightly / Pre-release (real-device or longer):

        Test 3 (All-day), Test 4 (event changes + timezone + recurrence), Test 5 (duplicate handling), Test 7 (OEM battery test), Test 6 (reboot) — these are slower, device-specific, or flaky on emulators.

    Device coverage:

        Must-run on Pixel (stock). Nightly run on an aggressive OEM device (Samsung/Xiaomi) for battery and boot behaviors.

6) Flakiness mitigation & observability

    Use debug triggers so you don't rely on WorkManager periodic windows.

    Use short lead times and short-durations in CI to avoid long waits. Keep one-hour+ sleeps out of CI.

    Consolidate verification across DB + logcat + UI: assert all three so false positives are rare.

    Add retry-with-backoff in tests for system UI interactions (e.g., clicking settings) because OEM settings UIs are non-deterministic.

    Emit structured logs (JSON-ish) for key lifecycle events so tests can reliably parse the logcat.

7) Deliverables for the AI coding assistant (what to implement)

    Test-only hooks in a debugTest flavor:

        Broadcast receiver to force refresh.

        Injectable RequestCodeGenerator.

        Test-mode flags for provider errors/collisions.

    Instrumentation test project:

        AndroidJUnitRunner based tests that combine Espresso + UiAutomator for each test described.

        Utility helpers: adb wrappers, logcat parsers, DB query helpers.

    CI job config:

        Fast-smoke job that runs the must-run tests on emulator (Pixel).

        Nightly device job (real Samsung/Xiaomi device) that runs full suite.

    Documentation:

        Exact environment-required flags, device settings to preconfigure, and expected log markers.

    Small sample tests (skeletons) for each of the eight tests above.


--------

Run the full 8-tests suite across 3 primary targets (plus one optional device for pre-release sanity):

    Emulator — Pixel/AOSP (API 34, Google Play system image)
    Fast, CI-friendly. Use this as the primary PR/fast-smoke runner. Covers target-SDK behavior and most runtime APIs.

    Emulator — Legacy (API 26, AOSP Google APIs image)
    Lightweight compatibility check for minSdk edge cases (old CalendarContract behavior, older permission models). Run a small subset of tests focused on compatibility.

    Physical device — Aggressive OEM (recommend: Xiaomi / OnePlus / Oppo or the most common aggressive OEM for your user base)
    One real device to exercise OEM battery optimization, vendor settings, aggressive process killing, and real audio/DND hardware behavior — this is critical.


----------

Modes to exercise (for each device) and how to simulate them

You should run tests in the following modes. Where possible use instrumentation (UiAutomator) to interact with system UI — that’s far less flaky than brittle adb commands across OEMs.

    Normal mode — default (no special flags).

    Silent / DND / Vibration-only — toggle DND and ringer to ensure alarm bypass.

        Preferred: use UiAutomator or an instrumentation helper to call NotificationManager.setInterruptionFilter() (instrumentation permission) or toggle quick settings via UiAutomator.

        Fallback: adb shell settings put global zen_mode <n> is emulator/OEM-dependent; prefer UiAutomator.

    Exact-alarm permission: allowed / denied / never-asked

        Use UiAutomator to open app system page to toggle exact alarm (platform UI varies). In test builds, provide a debug hook to simulate "exact alarm allowed/denied" so CI can assert behavior deterministically.

    Notification permission: allowed / denied

        Use adb shell pm grant ... android.permission.POST_NOTIFICATIONS / pm revoke for emulator & API levels that support it; or UiAutomator on device.

    Battery optimization enabled / whitelisted (the critical OEM scenario)

        On aggressive OEM physical device: use UiAutomator to toggle battery-optimization/whitelist for the app (Settings → Battery → Optimize battery usage). Emulators are poor proxies for OEM battery behavior.

    Doze / idle — simulate device idle to ensure alarms or WorkManager behave as expected.

        Use adb shell dumpsys deviceidle force-idle or adb shell cmd deviceidle force-idle on emulators; behavior varies by OEM. Use UiAutomator + debug hooks to verify worker is run eventually.

    Timezone / DST changes — change timezone and/or step clock forward/back (emulator supports setprop persist.sys.timezone or emulator command). On physical devices use UiAutomator to change timezone. After change, run refresh and verify rescheduling.

    Reboot / Boot variants — adb reboot works for emulator and devices; after reboot assert BootReceiver and re-registration. Also test “app never opened” boot behavior on OEM (some OEMs block receivers until app first launch).

    Low-memory / background killing — simulate memory pressure using adb shell am send-trim-memory or am kill patterns and verify WorkManager and rescheduling recover. OEM devices may behave differently — test on aggressive OEM.

    Permission revokes while app running — revoke READ_CALENDAR or SCHEDULE_EXACT_ALARM while app is open and verify graceful UI + blocking behavior.