# Emulator Snapshot Setup for Deterministic E2E Testing

This document describes how to create and use an emulator snapshot with predefined calendar events for reliable, deterministic E2E testing.

## Overview

Instead of creating calendar events during test execution (which requires WRITE_CALENDAR permission), we use a pre-configured emulator snapshot with known test calendar events. This approach provides:

- **Deterministic testing**: Same calendar events every test run
- **No additional permissions**: Only READ_CALENDAR required
- **Faster test execution**: No time spent creating events during tests
- **Reliable test data**: Eliminates calendar content provider creation failures

## Files Created

1. **`test_calendar_data_spec.md`** - Complete specification of test calendar events
2. **`setup_test_calendar.sh`** - Script to populate emulator with test events
3. **Updated `CalendarTestDataProvider.kt`** - Works with predefined events instead of creating them
4. **Updated test cases** - Expect specific known calendar events

## Setup Process

### Step 1: Prepare Emulator

1. Start a clean Android emulator (API 26+)
2. Ensure device is unlocked and responsive
3. Verify Google Calendar app is installed (usually pre-installed)

### Step 2: Populate Test Calendar Data

```bash
# Navigate to project root
cd /path/to/CalendarAlarmScheduler

# Run the calendar setup script
./setup_test_calendar.sh
```

The script will:
- Find or create a test calendar
- Create 41 specific test events based on `test_calendar_data_spec.md`
- Verify the events were created successfully
- Display summary of created events by category

### Step 3: Verify Test Data

After running the setup script, verify the calendar data:

```bash
# Check total calendar events
adb shell content query --uri content://com.android.calendar/events --projection title | wc -l

# Check events with specific keywords
adb shell content query --uri content://com.android.calendar/events --where "title LIKE '%Important%'" --projection title
adb shell content query --uri content://com.android.calendar/events --where "title LIKE '%Meeting%'" --projection title  
adb shell content query --uri content://com.android.calendar/events --where "title LIKE '%Doctor%'" --projection title
```

Expected results:
- **Total events**: ~44 events (41 test + 3 existing)
- **"Important" events**: 9 events
- **"Meeting" events**: 6 events
- **"Doctor" events**: 5 events

### Step 4: Create Emulator Snapshot

1. **Via Android Studio:**
   - Go to AVD Manager
   - Click on emulator dropdown → "Snapshots"
   - Click "Take Snapshot"
   - Name: "E2E_Test_Calendar_Snapshot"
   - Description: "Emulator with predefined calendar events for E2E testing"

2. **Via Command Line:**
   ```bash
   # Save current emulator state
   adb emu avd snapshot save E2E_Test_Calendar_Snapshot
   ```

### Step 5: Test the Snapshot

1. Close emulator
2. Start emulator from snapshot:
   ```bash
   emulator -avd YourAVDName -snapshot E2E_Test_Calendar_Snapshot
   ```
3. Verify calendar events still exist
4. Run E2E tests to confirm they work with predefined data

## Test Calendar Data Summary

The emulator snapshot contains these predefined events:

### Immediate Events (0-2 hours from test baseline)
- "Important Client Call" (+1 hour)
- "Doctor Appointment Follow-up" (+2 hours)

### Same Day Events  
- "Team Meeting Weekly" (+5 hours)
- "Important Project Review" (+7 hours)
- "Lunch Break" (+3 hours) - non-matching

### Next Day Events
- "Morning Standup Meeting" (+24 hours)
- "Doctor Visit Annual" (+29 hours) 
- "Important Conference Call" (+33 hours)

### Future Events (for background refresh testing)
- "Doctor Consultation" (+120 hours)
- "Important Quarterly Review" (+169 hours)

### Multi-Keyword Events (test multiple rule matching)
- "Important Meeting Doctor Review" - matches ALL 3 rules
- "Important Doctor Consultation Meeting" - matches ALL 3 rules

### Stress Test Events
- 20 "Stress Test Event N" events spread across 2 days

### All-Day Events
- "Conference Day Important" (Thursday)
- "Training Workshop" (Next Tuesday)

## Updated Test Workflow

With the snapshot approach, E2E tests now:

1. **Validate calendar setup** using `CalendarTestDataProvider.validateTestCalendarSetup()`
2. **Create rules** that match known events:
   - "Important Events Rule" → matches 9 events
   - "Meeting Reminders Rule" → matches 6 events
   - "Doctor Appointments Rule" → matches 5 events
3. **Verify alarms** are scheduled for matching events
4. **Test time acceleration** with events at known future times
5. **Test multi-rule matching** with events containing multiple keywords

## Benefits Achieved

✅ **No WRITE_CALENDAR permission required** - app stays production-clean  
✅ **Deterministic test data** - same events every test run  
✅ **Faster test execution** - no time creating events during tests  
✅ **Reliable calendar access** - eliminates content provider creation failures  
✅ **Professional testing practice** - proper test data management  
✅ **CI/CD ready** - snapshot can be shared and versioned  

## Maintenance

### Updating Test Events

To modify test calendar events:

1. Update `test_calendar_data_spec.md` with new event requirements
2. Modify `setup_test_calendar.sh` to create the new events
3. Run setup script on clean emulator
4. Create new snapshot
5. Update tests if needed to match new event data

### Regenerating Snapshot

```bash
# Clear existing test events
adb shell content delete --uri content://com.android.calendar/events --where "title LIKE 'Important%' OR title LIKE '%Meeting%' OR title LIKE 'Doctor%' OR title LIKE 'Stress Test%'"

# Run setup script again
./setup_test_calendar.sh

# Create new snapshot
# (via Android Studio or adb emu command)
```

## Usage in CI/CD

For automated testing environments:

1. **Store snapshot** in version control or artifact repository
2. **Load snapshot** before running E2E tests
3. **Verify calendar setup** as first test step
4. **Run full E2E test suite** with predictable data

Example CI workflow:
```yaml
- name: Start Emulator with Test Data
  run: emulator -avd test_avd -snapshot E2E_Test_Calendar_Snapshot -no-window &
  
- name: Wait for Emulator
  run: adb wait-for-device
  
- name: Run E2E Tests
  run: ./run_e2e_test.sh
```

This approach provides robust, deterministic E2E testing without compromising the production app with unnecessary permissions.