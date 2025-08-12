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

# Comprehensive App Cleanup and Uninstallation
print_step "Performing comprehensive app cleanup and uninstallation..."

# Force stop any running instances of the app and test app
print_step "Force stopping running app instances..."
$ADB $ADB_DEVICE shell am force-stop $PACKAGE_NAME 2>/dev/null || true
$ADB $ADB_DEVICE shell am force-stop ${PACKAGE_NAME}.test 2>/dev/null || true

# Kill any background processes
$ADB $ADB_DEVICE shell am kill $PACKAGE_NAME 2>/dev/null || true
$ADB $ADB_DEVICE shell am kill ${PACKAGE_NAME}.test 2>/dev/null || true

# Clear any existing app data before uninstalling (this ensures complete state reset)
print_step "Clearing existing app data..."
$ADB $ADB_DEVICE shell pm clear $PACKAGE_NAME 2>/dev/null || true
$ADB $ADB_DEVICE shell pm clear ${PACKAGE_NAME}.test 2>/dev/null || true

# Uninstall both main app and test app completely (all users)
print_step "Uninstalling existing app packages..."
$ADB $ADB_DEVICE shell pm uninstall $PACKAGE_NAME 2>/dev/null || true
$ADB $ADB_DEVICE shell pm uninstall ${PACKAGE_NAME}.test 2>/dev/null || true

# For devices with multiple users (work profiles), ensure complete removal
$ADB $ADB_DEVICE shell pm uninstall --user 0 $PACKAGE_NAME 2>/dev/null || true
$ADB $ADB_DEVICE shell pm uninstall --user 0 ${PACKAGE_NAME}.test 2>/dev/null || true

# Verify packages are completely removed
print_step "Verifying complete package removal..."
MAIN_PKG_EXISTS=$($ADB $ADB_DEVICE shell pm list packages | grep -c "$PACKAGE_NAME$" 2>/dev/null || echo "0")
TEST_PKG_EXISTS=$($ADB $ADB_DEVICE shell pm list packages | grep -c "${PACKAGE_NAME}.test$" 2>/dev/null || echo "0")

# Ensure we have valid integers for comparison
if [ -z "$MAIN_PKG_EXISTS" ] || ! [[ "$MAIN_PKG_EXISTS" =~ ^[0-9]+$ ]]; then
    MAIN_PKG_EXISTS=0
fi
if [ -z "$TEST_PKG_EXISTS" ] || ! [[ "$TEST_PKG_EXISTS" =~ ^[0-9]+$ ]]; then
    TEST_PKG_EXISTS=0
fi

if [ "$MAIN_PKG_EXISTS" -eq "0" ] && [ "$TEST_PKG_EXISTS" -eq "0" ]; then
    print_success "All packages successfully removed"
else
    print_warning "Some packages may still exist - attempting forced removal"
    # Additional cleanup for stubborn packages
    $ADB $ADB_DEVICE shell pm uninstall-system-updates $PACKAGE_NAME 2>/dev/null || true
    $ADB $ADB_DEVICE shell pm uninstall-system-updates ${PACKAGE_NAME}.test 2>/dev/null || true
fi

# Clear package manager cache to ensure clean state
print_step "Clearing package manager caches..."
$ADB $ADB_DEVICE shell pm trim-caches 100M 2>/dev/null || true

# Wait for system to stabilize after cleanup
sleep 2

print_success "Comprehensive app cleanup completed - device in clean state"

# Clean Install APKs (no -r flag since packages were completely removed)
print_step "Installing app and test APKs (clean installation)..."

# Install main app APK
if ! $ADB $ADB_DEVICE install "$APK_PATH"; then
    print_error "Failed to install main app APK"
    exit 1
fi

# Install test APK  
if ! $ADB $ADB_DEVICE install "$TEST_APK_PATH"; then
    print_error "Failed to install test app APK"
    exit 1
fi

# Verify both packages are properly installed
print_step "Verifying clean APK installation..."
MAIN_INSTALLED=$($ADB $ADB_DEVICE shell pm list packages | grep -c "$PACKAGE_NAME$" || echo "0")
TEST_INSTALLED=$($ADB $ADB_DEVICE shell pm list packages | grep -c "${PACKAGE_NAME}.test$" || echo "0")

if [ "$MAIN_INSTALLED" -eq "1" ] && [ "$TEST_INSTALLED" -eq "1" ]; then
    print_success "✅ Both APKs installed successfully with clean state"
else
    print_error "❌ APK installation verification failed"
    print_error "Main app installed: $MAIN_INSTALLED, Test app installed: $TEST_INSTALLED"
    exit 1
fi

# Prepare device for testing
print_step "Preparing device for testing..."

# Wake up device and dismiss any lock screens
$ADB $ADB_DEVICE shell input keyevent KEYCODE_WAKEUP  # Wake up device
sleep 1
$ADB $ADB_DEVICE shell input keyevent 82  # Menu key (dismisses keyguard on some devices)
$ADB $ADB_DEVICE shell input keyevent 26  # Power key
$ADB $ADB_DEVICE shell input keyevent 82  # Menu key again
$ADB $ADB_DEVICE shell input swipe 540 1500 540 800  # Swipe up to dismiss lock screen
sleep 1

# Note: App data is already clean due to complete uninstall/reinstall process above

# REMOVED: Permission pre-granting for proper E2E onboarding flow testing
# The test will handle permissions through the actual onboarding UI flow
print_step "Skipping permission pre-granting - will be handled by onboarding flow test"

# Note: Permissions will be granted through UI Automator during onboarding test
# This ensures we test the actual user experience of permission granting

print_success "Permissions will be handled by onboarding flow"

# STRICT: Set up LOCAL test calendar data for deterministic testing - FAIL if setup fails
print_step "Setting up LOCAL test calendar data (STRICT MODE - no fallbacks)..."
if [ -f "./setup_test_calendar.sh" ]; then
    ./setup_test_calendar.sh
    SETUP_EXIT_CODE=$?
    if [ $SETUP_EXIT_CODE -eq 0 ]; then
        print_success "✅ LOCAL test calendar data setup completed successfully"
    else
        print_error "❌ CRITICAL FAILURE: LOCAL test calendar setup failed"
        print_error "E2E tests CANNOT run without proper test calendar environment"
        print_error "This prevents accidentally using user calendar data"
        print_error "Fix the calendar setup issues and try again"
        exit 1
    fi
else
    print_error "❌ CRITICAL FAILURE: setup_test_calendar.sh script not found"
    print_error "E2E tests require LOCAL test calendar setup script"
    print_error "Cannot run tests without proper calendar isolation"
    exit 1
fi

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

# Clean up test calendar data
print_step "Cleaning up test calendar data..."
if [ -f "./teardown_test_calendar.sh" ]; then
    ./teardown_test_calendar.sh
    TEARDOWN_EXIT_CODE=$?
    if [ $TEARDOWN_EXIT_CODE -eq 0 ]; then
        print_success "✅ Test calendar cleanup completed successfully"
    else
        print_warning "⚠️ Test calendar cleanup had issues - manual cleanup may be needed"
    fi
else
    print_warning "⚠️ teardown_test_calendar.sh not found - test data may remain on device"
fi

# Post-test comprehensive cleanup for clean state
print_step "Performing post-test comprehensive cleanup..."

# Force stop any running app instances after test completion
$ADB $ADB_DEVICE shell am force-stop $PACKAGE_NAME 2>/dev/null || true
$ADB $ADB_DEVICE shell am force-stop ${PACKAGE_NAME}.test 2>/dev/null || true

# Optional: Completely remove test apps for ultimate clean state (uncomment if desired)
# print_step "Removing test applications for ultimate clean state..."
# $ADB $ADB_DEVICE shell pm uninstall $PACKAGE_NAME 2>/dev/null || true
# $ADB $ADB_DEVICE shell pm uninstall ${PACKAGE_NAME}.test 2>/dev/null || true

# Clear any remaining app data to ensure clean state for next run
$ADB $ADB_DEVICE shell pm clear $PACKAGE_NAME 2>/dev/null || true
$ADB $ADB_DEVICE shell pm clear ${PACKAGE_NAME}.test 2>/dev/null || true

print_success "✅ Post-test cleanup completed - device ready for next test run"

# Display final status
echo ""
if [ $TEST_EXIT_CODE -eq 0 ]; then
    print_success "=== COMPREHENSIVE E2E TEST PASSED ==="
    echo "✅ All tests completed successfully"
    echo "🧹 Test environment cleaned up"
    echo "📊 Performance metrics and memory analysis available in: $RESULTS_DIR"
    echo "📝 Check test logs for detailed execution information"
else
    print_error "=== COMPREHENSIVE E2E TEST FAILED ==="
    echo "❌ Test execution failed with exit code: $TEST_EXIT_CODE"
    echo "🧹 Test environment cleaned up (regardless of test result)"
    echo "📋 Check logs in $RESULTS_DIR for failure details"
    echo "🔍 Review crash_logs.txt and app_logs.txt for error information"
fi

echo ""
echo "Results directory: $RESULTS_DIR"
echo "To view detailed metrics: cat $RESULTS_DIR/test_metrics.txt"
echo "To view app logs: cat $RESULTS_DIR/app_logs.txt"
echo ""
echo "🔄 Test environment is clean and ready for next test run"

exit $TEST_EXIT_CODE