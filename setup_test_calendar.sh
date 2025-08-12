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
    print_error "CRITICAL ERROR: Failed to create or identify local test calendar"
    print_error "Calendar query result was: $CALENDAR_QUERY_RESULT"
    print_error "Cannot proceed without proper test calendar setup"
    exit 1
fi

# Consolidated calendar verification - verify type and visibility in parallel
print_step "Verifying calendar properties in parallel..."

# Create temp directory for verification results
VERIFY_TEMP_DIR=$(mktemp -d)

# Run both verification queries in parallel
$ADB shell content query --uri content://com.android.calendar/calendars --projection account_type --where "_id=$CALENDAR_ID" 2>/dev/null | head -1 | grep -o 'account_type=[^,]*' | cut -d'=' -f2 > "$VERIFY_TEMP_DIR/account_type" &
$ADB shell content query --uri content://com.android.calendar/calendars --projection visible --where "_id=$CALENDAR_ID" 2>/dev/null | head -1 | grep -o '[0-9]*' > "$VERIFY_TEMP_DIR/visibility" &

# Wait for verification queries to complete
wait

# Read results
CALENDAR_ACCOUNT_TYPE=$(cat "$VERIFY_TEMP_DIR/account_type" 2>/dev/null || echo "")
CALENDAR_VISIBLE=$(cat "$VERIFY_TEMP_DIR/visibility" 2>/dev/null || echo "1")

# Cleanup verification temp directory
rm -rf "$VERIFY_TEMP_DIR"

# Verify account type
if [ "$CALENDAR_ACCOUNT_TYPE" != "LOCAL" ]; then
    print_error "SAFETY CHECK FAILED: Calendar ID $CALENDAR_ID is not a LOCAL test calendar (type: $CALENDAR_ACCOUNT_TYPE)"
    print_error "This could be a user calendar - refusing to proceed to protect user data"
    exit 1
fi

print_success "✅ Created LOCAL test calendar with ID: $CALENDAR_ID"
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

# Optimized batch event creation function
create_event_batch() {
    local events_array=("$@")
    local batch_size=15  # Further increased batch size for maximum performance
    local batch_count=0
    local commands=()
    local total_events=${#events_array[@]}
    local current_index=0
    
    print_step "Creating events in optimized batches (batch size: $batch_size)..."
    
    for event_data in "${events_array[@]}"; do
        IFS='|' read -r title description start_offset_hours duration_hours all_day <<< "$event_data"
        current_index=$((current_index + 1))
        
        # Pre-calculate time values
        local start_time_ms=$(echo "$BASELINE_EPOCH + ($start_offset_hours * $ONE_HOUR)" | bc)
        local duration_ms=$(echo "$duration_hours * $ONE_HOUR" | bc)
        local end_time_ms=$(echo "$start_time_ms + $duration_ms" | bc)
        local start_time=${start_time_ms%.*}
        local end_time=${end_time_ms%.*}
        
        local cmd
        if [ "$all_day" -eq "1" ]; then
            local day_start=$((start_time / ONE_DAY * ONE_DAY))
            local day_end=$((day_start + ONE_DAY))
            cmd="content insert --uri 'content://com.android.calendar/events?caller_is_syncadapter=true&account_name=testlocal&account_type=LOCAL' --bind 'calendar_id:i:$CALENDAR_ID' --bind 'title:s:$title' --bind 'description:s:$description' --bind 'dtstart:l:$day_start' --bind 'dtend:l:$day_end' --bind 'allDay:i:1' --bind 'eventTimezone:s:America/Los_Angeles' --bind 'hasAlarm:i:0'"
        else
            cmd="content insert --uri 'content://com.android.calendar/events?caller_is_syncadapter=true&account_name=testlocal&account_type=LOCAL' --bind 'calendar_id:i:$CALENDAR_ID' --bind 'title:s:$title' --bind 'description:s:$description' --bind 'dtstart:l:$start_time' --bind 'dtend:l:$end_time' --bind 'allDay:i:0' --bind 'eventTimezone:s:America/Los_Angeles' --bind 'hasAlarm:i:0'"
        fi
        
        commands+=("$cmd")
        batch_count=$((batch_count + 1))
        
        # Execute batch when we reach batch_size or at the end
        if [ $batch_count -eq $batch_size ] || [ $current_index -eq $total_events ]; then
            print_step "Executing batch of $batch_count events..."
            
            # Execute commands in parallel within the batch
            local pids=()
            for cmd in "${commands[@]}"; do
                $ADB shell "$cmd" &
                pids+=($!)
            done
            
            # Wait for all commands in this batch to complete
            for pid in "${pids[@]}"; do
                wait $pid
            done
            
            print_success "✓ Batch of $batch_count events completed"
            
            # Reset for next batch
            commands=()
            batch_count=0
        fi
    done
}

# Grant calendar permissions in parallel for speed
print_step "Ensuring calendar permissions are granted..."
$ADB shell pm grant $PACKAGE_NAME android.permission.READ_CALENDAR 2>/dev/null &
$ADB shell pm grant $PACKAGE_NAME android.permission.WRITE_CALENDAR 2>/dev/null &
wait  # Wait for both permission grants to complete
print_success "Calendar permissions granted"

# Clear ONLY existing test calendar events for clean deterministic setup
print_step "Clearing existing test calendar events for clean test environment..."

# Optimized calendar cleanup with consolidated verification
if [ ! -z "$CALENDAR_ID" ]; then
    # We already verified the calendar type above, so we can proceed directly to cleanup
    print_success "✅ Using verified LOCAL test calendar ID $CALENDAR_ID - safe to clear"
    
    # Run cleanup and verification in parallel
    $ADB shell content delete --uri content://com.android.calendar/events --where "calendar_id=$CALENDAR_ID" 2>/dev/null &
    CLEANUP_PID=$!
    
    # Wait for cleanup to complete
    wait $CLEANUP_PID
    print_success "Cleared events from test calendar (ID: $CALENDAR_ID)"
    
    # Verify cleanup worked
    REMAINING_EVENTS=$($ADB shell content query --uri content://com.android.calendar/events --projection _id --where "calendar_id=$CALENDAR_ID" 2>/dev/null | wc -l || echo "0")
    print_success "Test calendar cleanup completed - $REMAINING_EVENTS events remaining in test calendar"
else
    print_error "CRITICAL ERROR: Could not identify test calendar ID"
    print_error "Refusing to perform any cleanup without verified test calendar ID"
    print_error "This prevents accidentally affecting user calendar data"
    exit 1
fi

print_step "Creating test calendar events based on specification..."

# Define all events as array data for batch processing
declare -a ALL_EVENTS=(
    # IMMEDIATE EVENTS (0-2 hours from baseline)
    "Important Client Call|Critical business meeting|1|1|0"
    "Doctor Appointment Follow-up|Annual checkup follow-up|2|1|0"
    
    # SAME DAY EVENTS (later today)  
    "Team Meeting Weekly|Weekly team sync meeting|5|1|0"
    "Important Project Review|Quarterly project review session|7|2|0"
    "Lunch Break|Personal lunch time|3|1|0"
    
    # NEXT DAY EVENTS (24-48 hours from baseline)
    "Morning Standup Meeting|Daily team standup|24|0.5|0"
    "Doctor Visit Annual|Annual medical checkup|29|1|0"
    "Important Conference Call|Critical client conference call|33|1|0"
    
    # DAY 3 EVENTS (48-72 hours - edge of 2-day lookahead)
    "Weekly Team Meeting|Weekly team coordination|49|1|0"
    "Important Budget Meeting|Quarterly budget planning|54|2|0"
    
    # FUTURE EVENTS (beyond 2-day lookahead)
    "Doctor Consultation|Medical consultation appointment|120|1|0"
    "Important Quarterly Review|Quarterly business review|169|3|0"
    
    # ALL-DAY EVENTS
    "Conference Day Important|Annual company conference|72|24|1"
    "Training Workshop|Professional development workshop|192|24|1"
    
    # MULTI-KEYWORD EVENTS
    "Important Meeting Doctor Review|Medical consultation meeting|26|1|0"
    "Important Doctor Consultation Meeting|Medical team consultation|50|1.5|0"
    
    # NON-MATCHING EVENTS
    "Gym Session|Personal fitness workout|34|1|0"
    "Grocery Shopping|Weekly grocery shopping|56|1|0"
    "Regular Lunch|Casual lunch break|75|1|0"
    
    # PAST EVENTS
    "Past Important Event|Historical important event|-14|1|0"
    "Past Meeting|Historical team meeting|-10|1|0"
)

# Add stress test events dynamically
declare -a STRESS_EVENTS=()
for i in $(seq 1 20); do
    # Use 10# prefix to force decimal interpretation and avoid octal issues
    offset_hours=$(( 13 + (10#$i - 1) * 2 ))  # Starting at 1 PM, every 2 hours
    STRESS_EVENTS+=("Stress Test Event $i|Automated stress test event|$offset_hours|0.5|0")
done

# Combine all events
ALL_EVENTS+=("${STRESS_EVENTS[@]}")

# Create all events using optimized batch processing
create_event_batch "${ALL_EVENTS[@]}"

print_step "Verifying created events..."

# Execute verification queries in parallel using efficient approach
print_step "Running parallel verification queries..."

# Create temporary directory for parallel operations
TEMP_DIR=$(mktemp -d)

# Function to retry calendar queries with backoff
retry_calendar_query() {
    local query_desc="$1"
    local query_cmd="$2"
    local output_file="$3"
    local max_retries=3
    local retry_delay=2
    
    for attempt in $(seq 1 $max_retries); do
        if eval "$query_cmd" > "$output_file" 2>/dev/null; then
            return 0
        else
            if [ $attempt -lt $max_retries ]; then
                print_warning "Calendar query '$query_desc' failed (attempt $attempt/$max_retries), retrying in ${retry_delay}s..."
                sleep $retry_delay
                retry_delay=$((retry_delay * 2))  # Exponential backoff
            else
                print_warning "Calendar query '$query_desc' failed after $max_retries attempts, using fallback"
                echo "0" > "$output_file"
                return 1
            fi
        fi
    done
}

# Run verification queries with retry logic for reliability
retry_calendar_query "total events" \
    "$ADB shell content query --uri content://com.android.calendar/events --projection title | wc -l" \
    "$TEMP_DIR/total_events"

retry_calendar_query "test calendar events" \
    "$ADB shell content query --uri content://com.android.calendar/events --projection _id --where \"calendar_id=$CALENDAR_ID\" | wc -l" \
    "$TEMP_DIR/calendar_events"

retry_calendar_query "important events" \
    "$ADB shell content query --uri content://com.android.calendar/events --projection _id --where \"calendar_id=$CALENDAR_ID AND title LIKE '%Important%'\" | wc -l" \
    "$TEMP_DIR/important_events"

retry_calendar_query "meeting events" \
    "$ADB shell content query --uri content://com.android.calendar/events --projection _id --where \"calendar_id=$CALENDAR_ID AND title LIKE '%Meeting%'\" | wc -l" \
    "$TEMP_DIR/meeting_events"

retry_calendar_query "doctor events" \
    "$ADB shell content query --uri content://com.android.calendar/events --projection _id --where \"calendar_id=$CALENDAR_ID AND title LIKE '%Doctor%'\" | wc -l" \
    "$TEMP_DIR/doctor_events"

# Read results from temporary files
TOTAL_EVENTS=$(cat "$TEMP_DIR/total_events" | tr -d '\n\r ')
CALENDAR_EVENTS=$(cat "$TEMP_DIR/calendar_events" | tr -d '\n\r ')
FINAL_EVENT_COUNT=$CALENDAR_EVENTS
IMPORTANT_EVENTS=$(cat "$TEMP_DIR/important_events" | tr -d '\n\r ')
MEETING_EVENTS=$(cat "$TEMP_DIR/meeting_events" | tr -d '\n\r ')
DOCTOR_EVENTS=$(cat "$TEMP_DIR/doctor_events" | tr -d '\n\r ')

print_success "Total events in calendar: $TOTAL_EVENTS"
print_success "Events in test calendar (ID=$CALENDAR_ID): $CALENDAR_EVENTS"

# Run final verification queries in parallel
print_step "Running final verification queries in parallel..."

# Run final verification with retry logic
retry_calendar_query "sample events" \
    "$ADB shell content query --uri content://com.android.calendar/events --projection title --where \"calendar_id=$CALENDAR_ID\" --sort \"dtstart ASC\" | head -5" \
    "$TEMP_DIR/sample_events"

retry_calendar_query "calendar properties" \
    "$ADB shell content query --uri content://com.android.calendar/calendars --projection \"_id,calendar_displayName,account_name,account_type,visible\" --where \"_id=$CALENDAR_ID\"" \
    "$TEMP_DIR/calendar_properties"

# Ensure permissions are granted
$ADB shell pm grant $PACKAGE_NAME android.permission.READ_CALENDAR 2>/dev/null || true

# Display results
print_step "Sample events in test calendar:"
cat "$TEMP_DIR/sample_events" 2>/dev/null || echo "No sample events found"

print_step "Verifying calendar properties:"
cat "$TEMP_DIR/calendar_properties" 2>/dev/null || echo "Calendar properties not available"

# Final cleanup of temp directory
rm -rf "$TEMP_DIR" 2>/dev/null || true

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