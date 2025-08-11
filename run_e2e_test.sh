#!/bin/bash

# Comprehensive E2E Test Runner for Calendar Alarm Scheduler
# This script runs the single comprehensive end-to-end test with proper setup

set -e

echo "=== Calendar Alarm Scheduler - Comprehensive E2E Test Runner ==="
echo ""

# Configuration
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
TEST_APK_PATH="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
PACKAGE_NAME="com.example.calendaralarmscheduler"
TEST_RUNNER="androidx.test.runner.AndroidJUnitRunner"
ADB="/Users/riverweiss/Library/Android/sdk/platform-tools/adb"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
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

# Check if device is connected
print_step "Checking for connected Android device..."
DEVICE_COUNT=$($ADB devices | grep -c "device$" || echo "0")

if [ "$DEVICE_COUNT" -eq "0" ]; then
    print_error "No Android device found. Please connect a device or start an emulator."
    exit 1
elif [ "$DEVICE_COUNT" -gt "1" ]; then
    print_warning "Multiple devices detected. Using the first one."
    DEVICE_ID=$($ADB devices | grep "device$" | head -1 | cut -f1)
    ADB_DEVICE="-s $DEVICE_ID"
    echo "Using device: $DEVICE_ID"
else
    ADB_DEVICE=""
    print_success "Found Android device"
fi

# Set JAVA_HOME if needed
if [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    print_step "Set JAVA_HOME to: $JAVA_HOME"
fi

# Clean and build the project
print_step "Building project with tests..."
./gradlew clean assembleDebug assembleDebugAndroidTest

if [ ! -f "$APK_PATH" ] || [ ! -f "$TEST_APK_PATH" ]; then
    print_error "Failed to build APKs. Check build output above."
    exit 1
fi

print_success "Build completed successfully"

# Install APKs
print_step "Installing app and test APKs..."
$ADB $ADB_DEVICE install -r "$APK_PATH"
$ADB $ADB_DEVICE install -r "$TEST_APK_PATH"

print_success "APKs installed successfully"

# Prepare device for testing
print_step "Preparing device for testing..."

# Wake up device and dismiss any lock screens
$ADB $ADB_DEVICE shell input keyevent 82  # Menu key (dismisses keyguard on some devices)
$ADB $ADB_DEVICE shell input keyevent 26  # Power key
$ADB $ADB_DEVICE shell input keyevent 82  # Menu key again

# Clear app data for clean test environment
$ADB $ADB_DEVICE shell pm clear $PACKAGE_NAME

# Set up permissions (only runtime permissions)
print_step "Setting up permissions..."
$ADB $ADB_DEVICE shell pm grant $PACKAGE_NAME android.permission.READ_CALENDAR 2>/dev/null || true
$ADB $ADB_DEVICE shell pm grant $PACKAGE_NAME android.permission.WRITE_CALENDAR 2>/dev/null || true
$ADB $ADB_DEVICE shell pm grant $PACKAGE_NAME android.permission.POST_NOTIFICATIONS 2>/dev/null || true
# Skip non-runtime permissions

print_success "Permissions configured"

# Clear logcat for clean test logs
print_step "Clearing device logs..."
$ADB $ADB_DEVICE logcat -c

# Start comprehensive E2E test
print_step "Starting Comprehensive E2E Test..."
echo ""
echo -e "${YELLOW}=== TEST EXECUTION STARTING ===${NC}"
echo ""

# Run the single comprehensive test
$ADB $ADB_DEVICE shell am instrument \
    -w \
    -e class ${PACKAGE_NAME}.ComprehensiveE2ETest \
    -e clearPackageData true \
    ${PACKAGE_NAME}.test/${TEST_RUNNER}

TEST_EXIT_CODE=$?

echo ""
echo -e "${YELLOW}=== TEST EXECUTION COMPLETED ===${NC}"
echo ""

# Collect test results and logs
print_step "Collecting test results and logs..."

# Create results directory
RESULTS_DIR="test_results/$(date +%Y%m%d_%H%M%S)"
mkdir -p "$RESULTS_DIR"

# Collect comprehensive logs
print_step "Collecting application logs..."
$ADB $ADB_DEVICE logcat -d -s "CalendarAlarmScheduler:*" -v time > "$RESULTS_DIR/app_logs.txt"

# Collect system logs for crashes
$ADB $ADB_DEVICE logcat -d -s "AndroidRuntime:E" -v time > "$RESULTS_DIR/crash_logs.txt"

# Collect test metrics logs
$ADB $ADB_DEVICE logcat -d -s "ComprehensiveE2E:*" -s "TestMetrics:*" -v time > "$RESULTS_DIR/test_metrics.txt"

# Get memory info
print_step "Collecting system memory info..."
$ADB $ADB_DEVICE shell dumpsys meminfo $PACKAGE_NAME > "$RESULTS_DIR/memory_info.txt"

# Get system info
$ADB $ADB_DEVICE shell getprop > "$RESULTS_DIR/device_properties.txt"

# Create summary report
print_step "Generating test summary..."
cat > "$RESULTS_DIR/test_summary.txt" << EOF
=== Comprehensive E2E Test Summary ===
Test Date: $(date)
Device: $($ADB $ADB_DEVICE shell getprop ro.product.model)
Android Version: $($ADB $ADB_DEVICE shell getprop ro.build.version.release)
Test Exit Code: $TEST_EXIT_CODE
Package: $PACKAGE_NAME

Files Generated:
- app_logs.txt: Application-specific logs
- crash_logs.txt: System crash logs  
- test_metrics.txt: Test execution and performance metrics
- memory_info.txt: Memory usage information
- device_properties.txt: Device system properties

Check test_metrics.txt for detailed performance analysis and memory leak detection.
EOF

print_success "Results collected in: $RESULTS_DIR"

# Display final status
echo ""
if [ $TEST_EXIT_CODE -eq 0 ]; then
    print_success "=== COMPREHENSIVE E2E TEST PASSED ==="
    echo "✅ All tests completed successfully"
    echo "📊 Performance metrics and memory analysis available in: $RESULTS_DIR"
    echo "📝 Check test logs for detailed execution information"
else
    print_error "=== COMPREHENSIVE E2E TEST FAILED ==="
    echo "❌ Test execution failed with exit code: $TEST_EXIT_CODE"
    echo "📋 Check logs in $RESULTS_DIR for failure details"
    echo "🔍 Review crash_logs.txt and app_logs.txt for error information"
fi

echo ""
echo "Results directory: $RESULTS_DIR"
echo "To view detailed metrics: cat $RESULTS_DIR/test_metrics.txt"
echo "To view app logs: cat $RESULTS_DIR/app_logs.txt"

exit $TEST_EXIT_CODE