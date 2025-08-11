# Comprehensive E2E Testing Framework

This document describes the automated end-to-end testing framework for the Calendar Alarm Scheduler app.

## Overview

The testing framework provides a **single, comprehensive test** that covers all app functionality using Espresso and UI Automator. It includes:

- ✅ **Memory usage tracking** compared to baseline
- ✅ **Performance metrics** collection
- ✅ **Application log monitoring**
- ✅ **Calendar event injection** for controlled testing
- ✅ **Time acceleration** for testing future events
- ✅ **Memory leak detection**
- ✅ **Complete system health monitoring**

## Quick Start

### Prerequisites

1. **Android device or emulator** connected and recognized by ADB
2. **Calendar permissions** granted (automatically handled by test framework)
3. **Java environment** set up (automatically configured for Android Studio)

### Running the Test

```bash
# Simple execution - just run the script
./run_e2e_test.sh
```

That's it! The script handles:
- Building the app and test APKs
- Installing on the connected device
- Configuring permissions
- Running the comprehensive test
- Collecting all metrics and logs
- Generating detailed reports

## Test Coverage

The single `ComprehensiveE2ETest` covers:

### 1. App Launch and Navigation (`test01_appLaunchAndBasicNavigation`)
- Launches main activity
- Tests navigation between tabs (Rules, Preview, Settings)
- Measures app launch performance
- Captures initial memory usage

### 2. Calendar Data Injection (`test02_calendarDataInjection`)
- Creates comprehensive test calendar events
- Injects time-based events for alarm testing
- Verifies event queryability
- Tests calendar ContentProvider integration

### 3. Permission Verification (`test03_permissionAndSettingsVerification`)
- Checks permission status in Settings UI
- Verifies calendar and alarm permissions
- Tests settings screen functionality

### 4. Rule Management (`test04_ruleCreationAndManagement`)
- Tests rule creation UI
- Validates rule form inputs
- Tests rule saving functionality

### 5. Event Preview (`test05_eventPreviewAndFiltering`)
- Tests event preview screen
- Validates event filtering functionality
- Checks event list display

### 6. Time Acceleration Testing (`test06_timeAccelerationAndAlarmTesting`)
- Tests multiple time scenarios (15min, 2hr, 1day, 1week)
- Simulates time passage for alarm testing
- Validates alarm scheduling behavior
- Tests edge cases with future events

### 7. Memory Analysis (`test07_memoryAndPerformanceAnalysis`)
- Performs memory stress testing
- Rapid navigation to detect leaks
- Captures memory snapshots
- Detects potential memory leaks

### 8. Log Analysis (`test08_logCollectionAndAnalysis`)
- Collects application logs during test execution
- Analyzes error and warning patterns
- Identifies potential issues

### 9. Final System Check (`test99_finalSystemCheck`)
- Comprehensive system health validation
- Final memory leak detection
- Performance metrics summary
- Test success validation

## Framework Components

### TestMetricsCollector
- **Memory tracking**: Heap usage, native memory, system memory
- **Performance metrics**: Operation timing, memory deltas
- **Log collection**: Filtered app logs with pattern analysis
- **Leak detection**: Baseline comparison with configurable thresholds

### CalendarTestDataProvider
- **Event injection**: Creates realistic test calendar events
- **Calendar management**: Isolated test calendar creation
- **Data verification**: Query injected events for validation
- **Cleanup**: Automatic test data removal

### TestTimeController
- **Time manipulation**: Fast-forward time for alarm testing
- **Scenario testing**: Predefined time scenarios (near/far future)
- **Alarm simulation**: Wait for alarm firing with time acceleration
- **Time reporting**: Track time manipulation throughout tests

### ComprehensiveE2ETest
- **Single test approach**: All functionality in one comprehensive test
- **Ordered execution**: Tests run in logical sequence
- **Comprehensive reporting**: Detailed metrics and analysis
- **Failure handling**: Graceful error handling and reporting

## Test Results

After running the test, check the `test_results/` directory:

```
test_results/YYYYMMDD_HHMMSS/
├── app_logs.txt           # Application-specific logs
├── crash_logs.txt         # System crash logs  
├── test_metrics.txt       # Performance and memory metrics
├── memory_info.txt        # Detailed memory usage
├── device_properties.txt  # Device system properties
└── test_summary.txt       # Test execution summary
```

### Key Metrics

**Memory Metrics:**
- Baseline vs final memory usage
- Heap growth detection
- Memory leak identification
- Per-operation memory deltas

**Performance Metrics:**
- Operation execution times
- UI navigation performance
- Database query performance
- Background task efficiency

**Reliability Metrics:**
- Error/warning log analysis
- Crash detection
- Permission validation
- System state verification

## Interpreting Results

### ✅ Success Indicators
```
=== COMPREHENSIVE E2E TEST PASSED ===
Memory leaks: NO
Operations measured: 8+
Test duration: <60s
No crash logs
```

### ❌ Failure Indicators
```
MEMORY LEAK DETECTED!
AndroidRuntime FATAL EXCEPTION
Test execution failed
Permission denied errors
```

### 🔍 Troubleshooting

**Build Failures:**
- Check Java environment: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- Clean build: `./gradlew clean`

**Permission Issues:**
- Manually grant calendar permissions in device settings
- Check exact alarm permission (Android 12+)

**Device Connection:**
- Verify: `adb devices`
- Enable USB debugging
- Trust computer on device

**Memory Issues:**
- Review `memory_info.txt` for excessive usage
- Check `test_metrics.txt` for memory growth patterns
- Look for memory leaks in test output

## Advanced Usage

### Running Specific Test Methods
```bash
# Run only memory testing
adb shell am instrument -w -e class com.example.calendaralarmscheduler.ComprehensiveE2ETest#test07_memoryAndPerformanceAnalysis com.example.calendaralarmscheduler.test/androidx.test.runner.AndroidJUnitRunner
```

### Custom Configuration
Edit test parameters in `ComprehensiveE2ETest.kt`:
- Memory leak thresholds
- Time acceleration scenarios  
- Performance benchmarks
- Log collection filters

### Integration with CI/CD
The framework is designed for automated execution:
- Exit codes indicate pass/fail
- All output captured in structured files
- No interactive elements
- Deterministic execution

## Best Practices

1. **Run on clean device state** - The script handles app data clearing
2. **Use consistent test environment** - Same device/emulator for comparative results
3. **Monitor memory trends** - Track memory usage over multiple test runs
4. **Review logs regularly** - Check for warning patterns that might indicate issues
5. **Validate permissions** - Ensure all required permissions are granted

## Framework Architecture

```
ComprehensiveE2ETest
├── TestMetricsCollector (memory, performance, logs)
├── CalendarTestDataProvider (event injection)
├── TestTimeController (time manipulation)
├── Espresso (UI interactions)
└── UI Automator (system-level operations)
```

This comprehensive framework ensures reliable, repeatable testing while providing detailed insights into app performance, memory usage, and system behavior.