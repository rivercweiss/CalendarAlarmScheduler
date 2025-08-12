#!/bin/bash

# Setup Test Calendar - Populate emulator with deterministic calendar events for E2E testing
# This script creates the exact calendar events specified in test_calendar_data_spec.md

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_step() {
    echo -e "${BLUE}[CALENDAR SETUP]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

ADB="/Users/riverweiss/Library/Android/sdk/platform-tools/adb"
PACKAGE_NAME="com.example.calendaralarmscheduler"

echo "=== Calendar Alarm Scheduler - Test Calendar Setup ==="
echo ""

# Check if device is connected
print_step "Checking for connected Android device..."
DEVICE_COUNT=$($ADB devices | grep -c "device$" || echo "0")

if [ "$DEVICE_COUNT" -eq "0" ]; then
    print_error "No Android device found. Please connect device or start emulator."
    exit 1
fi

print_success "Found Android device"

# Calculate baseline time - use current time for dynamic events that app can see
# Events will be created relative to current time to appear in 2-day lookahead window
CURRENT_TIME=$(date +%s)000  # Current time in milliseconds
BASELINE_EPOCH=$CURRENT_TIME  # Use current time as baseline for test events

# Time constants in milliseconds
ONE_HOUR=3600000
ONE_DAY=86400000

print_step "Setting up test calendar with baseline time: $(date) (current time)"

# Create a proper local calendar for testing
print_step "Creating local test calendar with proper visibility settings..."

# First, clean up any existing test calendars
$ADB shell 'content delete --uri "content://com.android.calendar/calendars?caller_is_syncadapter=true&account_name=testlocal&account_type=LOCAL"' 2>/dev/null || true

# Create local calendar using sync adapter mode for proper visibility  
$ADB shell 'content insert --uri "content://com.android.calendar/calendars?caller_is_syncadapter=true&account_name=testlocal&account_type=LOCAL" --bind account_name:s:testlocal --bind account_type:s:LOCAL --bind name:s:TestCal --bind calendar_displayName:s:TestCal --bind visible:i:1 --bind sync_events:i:1'

# Get the created calendar ID - use robust approach
print_step "Retrieving calendar ID..."

# Query for our specific testlocal calendar and extract the ID properly
CALENDAR_QUERY_RESULT=$($ADB shell content query --uri content://com.android.calendar/calendars --where "account_name='testlocal' AND account_type='LOCAL'" 2>/dev/null || echo "")

if [ ! -z "$CALENDAR_QUERY_RESULT" ]; then
    # Extract the _id value from the query result
    # Look for pattern like "_id=4" in the result and trim whitespace
    CALENDAR_ID=$(echo "$CALENDAR_QUERY_RESULT" | grep -o '_id=[0-9]*' | head -1 | cut -d'=' -f2 | tr -d '\n\r ' || echo "")
fi

# Fallback: query all calendars and look for our test calendar in the output
if [ -z "$CALENDAR_ID" ] || [ "$CALENDAR_ID" = "0" ]; then
    print_warning "Primary query failed, using fallback calendar ID detection..."
    ALL_CALENDARS=$($ADB shell content query --uri content://com.android.calendar/calendars 2>/dev/null || echo "")
    
    # Look for our calendar in the full output and extract the row number that contains testlocal
    ROW_WITH_TESTLOCAL=$(echo "$ALL_CALENDARS" | grep -n "account_name=testlocal" | head -1 | cut -d: -f1 || echo "")
    
    if [ ! -z "$ROW_WITH_TESTLOCAL" ]; then
        # Extract the actual calendar ID from the row containing testlocal
        TESTLOCAL_ROW=$(echo "$ALL_CALENDARS" | sed -n "${ROW_WITH_TESTLOCAL}p")
        CALENDAR_ID=$(echo "$TESTLOCAL_ROW" | grep -o '_id=[0-9]*' | cut -d'=' -f2 | tr -d '\n\r ' || echo "")
    fi
fi

if [ -z "$CALENDAR_ID" ] || [ "$CALENDAR_ID" = "0" ]; then
    print_error "Failed to create or identify local test calendar"
    print_error "Calendar query result was: $CALENDAR_QUERY_RESULT"
    exit 1
fi

print_success "Created local test calendar with ID: $CALENDAR_ID"

# Verify calendar is visible
print_step "Verifying calendar visibility..."
CALENDAR_VISIBLE=$($ADB shell content query --uri content://com.android.calendar/calendars --projection visible --where "_id=$CALENDAR_ID" 2>/dev/null | head -1 | grep -o '[0-9]*' || echo "1")
print_success "Calendar visibility: $CALENDAR_VISIBLE (1=visible, 0=hidden)"

# Function to create a calendar event
create_event() {
    local title="$1"
    local description="$2"
    local start_offset_hours="$3"  # Hours from baseline
    local duration_hours="$4"      # Duration in hours
    local all_day="${5:-0}"        # 1 for all-day, 0 for timed event
    
    # Keep titles with spaces - properly quote them for ADB shell
    # No longer replacing spaces with underscores
    local safe_title="$title"
    local safe_description="$description"
    
    # Handle fractional hours by converting to minutes first
    local start_time_ms=$(echo "$BASELINE_EPOCH + ($start_offset_hours * $ONE_HOUR)" | bc)
    local duration_ms=$(echo "$duration_hours * $ONE_HOUR" | bc)
    local end_time_ms=$(echo "$start_time_ms + $duration_ms" | bc)
    
    # Convert back to integers (bc may return decimals)
    local start_time=${start_time_ms%.*}
    local end_time=${end_time_ms%.*}
    
    print_step "Creating event: $safe_title"
    
    # Create events using sync adapter mode for proper visibility
    # Use double quotes to properly handle titles with spaces
    if [ "$all_day" -eq "1" ]; then
        # For all-day events, use date in milliseconds at midnight
        local day_start=$((start_time / ONE_DAY * ONE_DAY))
        local day_end=$((day_start + ONE_DAY))
        
        $ADB shell "content insert --uri 'content://com.android.calendar/events?caller_is_syncadapter=true&account_name=testlocal&account_type=LOCAL' --bind 'calendar_id:i:$CALENDAR_ID' --bind 'title:s:$safe_title' --bind 'description:s:$safe_description' --bind 'dtstart:l:$day_start' --bind 'dtend:l:$day_end' --bind 'allDay:i:1' --bind 'eventTimezone:s:America/Los_Angeles' --bind 'hasAlarm:i:0'"
    else
        $ADB shell "content insert --uri 'content://com.android.calendar/events?caller_is_syncadapter=true&account_name=testlocal&account_type=LOCAL' --bind 'calendar_id:i:$CALENDAR_ID' --bind 'title:s:$safe_title' --bind 'description:s:$safe_description' --bind 'dtstart:l:$start_time' --bind 'dtend:l:$end_time' --bind 'allDay:i:0' --bind 'eventTimezone:s:America/Los_Angeles' --bind 'hasAlarm:i:0'"
    fi
    
    # Verify event was created
    if [ $? -eq 0 ]; then
        print_success "✓ Created: $safe_title"
    else
        print_error "✗ Failed to create: $safe_title"
        return 1
    fi
}

# First, grant calendar permissions to ensure we can create/delete events
print_step "Ensuring calendar permissions are granted..."
$ADB shell pm grant $PACKAGE_NAME android.permission.READ_CALENDAR 2>/dev/null || true
$ADB shell pm grant $PACKAGE_NAME android.permission.WRITE_CALENDAR 2>/dev/null || true
print_success "Calendar permissions granted"

# Clear ONLY existing test calendar events for clean deterministic setup
print_step "Clearing existing test calendar events for clean test environment..."

# First, try to find and delete events from previous test runs in our test calendar
if [ ! -z "$CALENDAR_ID" ]; then
    $ADB shell content delete --uri content://com.android.calendar/events --where "calendar_id=$CALENDAR_ID" 2>/dev/null || true
    print_success "Cleared events from test calendar (ID: $CALENDAR_ID)"
else
    # Fallback: delete events with test-specific titles to avoid destroying user data
    $ADB shell content delete --uri content://com.android.calendar/events --where "title LIKE '%Test%' OR title LIKE '%Important Client Call%' OR title LIKE '%Doctor Appointment%'" 2>/dev/null || true
    print_success "Cleared test events by title pattern"
fi

# Verify cleanup worked - count remaining events in test calendar
if [ ! -z "$CALENDAR_ID" ]; then
    REMAINING_EVENTS=$($ADB shell content query --uri content://com.android.calendar/events --projection _id --where "calendar_id=$CALENDAR_ID" 2>/dev/null | wc -l || echo "0")
    print_success "Test calendar cleanup completed - $REMAINING_EVENTS events remaining in test calendar"
fi

print_step "Creating test calendar events based on specification..."

# IMMEDIATE EVENTS (0-2 hours from baseline)
create_event "Important Client Call" "Critical business meeting" 1 1 0
create_event "Doctor Appointment Follow-up" "Annual checkup follow-up" 2 1 0

# SAME DAY EVENTS (later today)  
create_event "Team Meeting Weekly" "Weekly team sync meeting" 5 1 0
create_event "Important Project Review" "Quarterly project review session" 7 2 0
create_event "Lunch Break" "Personal lunch time" 3 1 0

# NEXT DAY EVENTS (24-48 hours from baseline)
create_event "Morning Standup Meeting" "Daily team standup" 24 0.5 0  
create_event "Doctor Visit Annual" "Annual medical checkup" 29 1 0
create_event "Important Conference Call" "Critical client conference call" 33 1 0

# DAY 3 EVENTS (48-72 hours - edge of 2-day lookahead)
create_event "Weekly Team Meeting" "Weekly team coordination" 49 1 0
create_event "Important Budget Meeting" "Quarterly budget planning" 54 2 0

# FUTURE EVENTS (beyond 2-day lookahead)
create_event "Doctor Consultation" "Medical consultation appointment" 120 1 0
create_event "Important Quarterly Review" "Quarterly business review" 169 3 0

# ALL-DAY EVENTS
create_event "Conference Day Important" "Annual company conference" 72 24 1  # Thursday all-day
create_event "Training Workshop" "Professional development workshop" 192 24 1  # Next Tuesday all-day

# MULTI-KEYWORD EVENTS
create_event "Important Meeting Doctor Review" "Medical consultation meeting" 26 1 0
create_event "Important Doctor Consultation Meeting" "Medical team consultation" 50 1.5 0

# NON-MATCHING EVENTS
create_event "Gym Session" "Personal fitness workout" 34 1 0
create_event "Grocery Shopping" "Weekly grocery shopping" 56 1 0  
create_event "Regular Lunch" "Casual lunch break" 75 1 0

# STRESS TEST EVENTS (20 events spread across time)
for i in $(seq 1 20); do
    # Use 10# prefix to force decimal interpretation and avoid octal issues
    offset_hours=$(( 13 + (10#$i - 1) * 2 ))  # Starting at 1 PM, every 2 hours
    create_event "Stress Test Event $i" "Automated stress test event" $offset_hours 0.5 0
done

# PAST EVENTS
create_event "Past Important Event" "Historical important event" -14 1 0  # Yesterday 10 AM
create_event "Past Meeting" "Historical team meeting" -10 1 0  # Yesterday 2 PM

print_step "Verifying created events..."

# Count total events
TOTAL_EVENTS=$($ADB shell content query --uri content://com.android.calendar/events --projection title | wc -l)
print_success "Total events in calendar: $TOTAL_EVENTS"

# Verify specific test events were created
print_step "Verifying sample test events were created..."

# Verify events exist in our specific calendar
CALENDAR_EVENTS=$($ADB shell content query --uri content://com.android.calendar/events --projection _id --where "calendar_id=$CALENDAR_ID" | wc -l)
print_success "Events in test calendar (ID=$CALENDAR_ID): $CALENDAR_EVENTS"

# Show sample event titles to verify they were created correctly  
print_step "Sample events in test calendar:"
$ADB shell content query --uri content://com.android.calendar/events --projection title --where "calendar_id=$CALENDAR_ID" --sort "dtstart ASC" | head -5

# Verify calendar properties
print_step "Verifying calendar properties:"
$ADB shell content query --uri content://com.android.calendar/calendars --projection "calendar_displayName,visible,sync_events" --where "_id=$CALENDAR_ID"

# Verify app can read the events
print_step "Verifying app can read calendar events..."

# Final verification and summary
print_step "Final verification of test calendar setup..."

# Verify app can read the events
$ADB shell pm grant $PACKAGE_NAME android.permission.READ_CALENDAR 2>/dev/null || true

# Count and validate test events were created correctly  
FINAL_EVENT_COUNT=$($ADB shell content query --uri content://com.android.calendar/events --projection _id --where "calendar_id=$CALENDAR_ID" | wc -l)
IMPORTANT_EVENTS=$($ADB shell content query --uri content://com.android.calendar/events --projection _id --where "calendar_id=$CALENDAR_ID AND title LIKE '%Important%'" | wc -l)
MEETING_EVENTS=$($ADB shell content query --uri content://com.android.calendar/events --projection _id --where "calendar_id=$CALENDAR_ID AND title LIKE '%Meeting%'" | wc -l)  
DOCTOR_EVENTS=$($ADB shell content query --uri content://com.android.calendar/events --projection _id --where "calendar_id=$CALENDAR_ID AND title LIKE '%Doctor%'" | wc -l)

print_success "✅ Test calendar setup completed successfully!"
print_success "📅 Created $FINAL_EVENT_COUNT total test events in calendar ID: $CALENDAR_ID"
print_success "🎯 Events matching 'Important': $IMPORTANT_EVENTS"
print_success "🤝 Events matching 'Meeting': $MEETING_EVENTS"  
print_success "🏥 Events matching 'Doctor': $DOCTOR_EVENTS"

echo ""
echo -e "${GREEN}=== SETUP COMPLETE ===${NC}"
echo "✅ Test calendar environment is ready for E2E testing"
echo "📋 Calendar ID: $CALENDAR_ID (use this for validation)"
echo "🔑 Keywords for testing: 'Important', 'Meeting', 'Doctor'"
echo ""
echo -e "${BLUE}To clean up test data later, run:${NC}"
echo "  $ADB shell content delete --uri content://com.android.calendar/events --where \"calendar_id=$CALENDAR_ID\""
echo "  $ADB shell content delete --uri content://com.android.calendar/calendars --where \"_id=$CALENDAR_ID\""
echo ""

# Save calendar ID for teardown script
echo "$CALENDAR_ID" > .test_calendar_id