#!/bin/bash

# Teardown Test Calendar - Clean up test calendar events and calendar after E2E testing
# This script safely removes only the test calendar created by setup_test_calendar.sh

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_step() {
    echo -e "${BLUE}[TEARDOWN]${NC} $1"
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

echo "=== Calendar Alarm Scheduler - Test Calendar Teardown ==="
echo ""

# Check if device is connected
print_step "Checking for connected Android device..."
DEVICE_COUNT=$($ADB devices | grep -c "device$" || echo "0")

if [ "$DEVICE_COUNT" -eq "0" ]; then
    print_error "No Android device found. Please connect device or start emulator."
    exit 1
fi

print_success "Found Android device"

# Read the calendar ID from setup
CALENDAR_ID=""
if [ -f ".test_calendar_id" ]; then
    CALENDAR_ID=$(cat .test_calendar_id 2>/dev/null | tr -d '\n\r ' || echo "")
    print_step "Found test calendar ID: $CALENDAR_ID"
else
    print_warning "No .test_calendar_id file found - will attempt to find test calendar"
    
    # Try to find the test calendar by account name using the same approach as setup
    CALENDAR_QUERY_RESULT=$($ADB shell content query --uri content://com.android.calendar/calendars --where "account_name='testlocal' AND account_type='LOCAL'" 2>/dev/null || echo "")
    
    if [ ! -z "$CALENDAR_QUERY_RESULT" ]; then
        CALENDAR_ID=$(echo "$CALENDAR_QUERY_RESULT" | grep -o '_id=[0-9]*' | head -1 | cut -d'=' -f2 | tr -d '\n\r ' || echo "")
    fi
    
    if [ ! -z "$CALENDAR_ID" ]; then
        print_success "Found test calendar by account: $CALENDAR_ID"
    fi
fi

if [ -z "$CALENDAR_ID" ]; then
    print_error "Could not find test calendar to clean up"
    print_warning "Attempting fallback cleanup by title pattern..."
    
    # Fallback: delete events with test-specific titles
    $ADB shell content delete --uri content://com.android.calendar/events --where "title LIKE '%Test%' OR title LIKE '%Important Client Call%' OR title LIKE '%Doctor Appointment%'" 2>/dev/null || true
    print_warning "Fallback cleanup completed"
    exit 0
fi

# Count events before cleanup
EVENTS_BEFORE=$($ADB shell content query --uri content://com.android.calendar/events --projection _id --where "calendar_id=$CALENDAR_ID" 2>/dev/null | wc -l || echo "0")
print_step "Found $EVENTS_BEFORE events in test calendar before cleanup"

if [ "$EVENTS_BEFORE" -eq "0" ]; then
    print_warning "No events found in test calendar - may already be clean"
else
    # Delete all events from the test calendar
    print_step "Deleting test calendar events..."
    $ADB shell content delete --uri content://com.android.calendar/events --where "calendar_id=$CALENDAR_ID" 2>/dev/null
    
    if [ $? -eq 0 ]; then
        print_success "✅ Deleted all events from test calendar"
    else
        print_error "❌ Failed to delete events from test calendar"
    fi
fi

# Verify events were deleted
EVENTS_AFTER=$($ADB shell content query --uri content://com.android.calendar/events --projection _id --where "calendar_id=$CALENDAR_ID" 2>/dev/null | wc -l || echo "0")
print_success "Events remaining in test calendar: $EVENTS_AFTER"

# Delete the test calendar itself
print_step "Deleting test calendar..."
$ADB shell content delete --uri "content://com.android.calendar/calendars?caller_is_syncadapter=true&account_name=testlocal&account_type=LOCAL" --where "_id=$CALENDAR_ID" 2>/dev/null

if [ $? -eq 0 ]; then
    print_success "✅ Deleted test calendar (ID: $CALENDAR_ID)"
else
    print_warning "⚠️ Could not delete test calendar - may require manual cleanup"
fi

# Clean up our tracking file
if [ -f ".test_calendar_id" ]; then
    rm .test_calendar_id
    print_success "Removed calendar ID tracking file"
fi

echo ""
echo -e "${GREEN}=== TEARDOWN COMPLETE ===${NC}"
print_success "🧹 Test calendar cleanup completed"
print_success "📊 Events removed: $EVENTS_BEFORE"
print_success "📅 Calendar removed: $CALENDAR_ID"
echo ""
echo "✅ Test environment is now clean and ready for next test run"