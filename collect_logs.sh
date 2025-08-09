#!/bin/bash

# Calendar Alarm Scheduler - Automated Log Collection Script
# Usage: ./collect_logs.sh [quick|detailed|live|clear]

set -e  # Exit on any error

# ADB path
ADB="/Users/riverweiss/Library/Android/sdk/platform-tools/adb"

# Project directory
PROJECT_DIR="/Users/riverweiss/AndroidStudioProjects/CalendarAlarmScheduler"

# Log files
CRASH_LOGS="$PROJECT_DIR/crash_logs.txt"
DETAILED_LOGS="$PROJECT_DIR/detailed_logs.txt"
LIVE_LOGS="$PROJECT_DIR/live_logs.txt"

# Function to check if device is connected
check_device() {
    echo "Checking device connection..."
    if ! $ADB devices | grep -q "device$"; then
        echo "❌ No device/emulator connected. Please start your emulator."
        echo "Connected devices:"
        $ADB devices
        exit 1
    fi
    echo "✅ Device connected"
}

# Function to collect quick crash logs
collect_crash_logs() {
    echo "📱 Collecting recent crash logs..."
    $ADB logcat -t 1000 | grep -E "(CalendarAlarmScheduler|AndroidRuntime|FATAL|EXCEPTION|CRASH|Process.*died)" > "$CRASH_LOGS" 2>/dev/null || true
    
    if [ -s "$CRASH_LOGS" ]; then
        echo "✅ Crash logs saved to: crash_logs.txt ($(wc -l < "$CRASH_LOGS") lines)"
    else
        echo "ℹ️  No recent crash logs found"
        echo "No crash logs found at $(date)" > "$CRASH_LOGS"
    fi
}

# Function to collect detailed app logs
collect_detailed_logs() {
    echo "📋 Collecting detailed CalendarAlarmScheduler logs..."
    $ADB logcat -t 1000 | grep -A 10 -B 5 "CalendarAlarmScheduler" > "$DETAILED_LOGS" 2>/dev/null || true
    
    if [ -s "$DETAILED_LOGS" ]; then
        echo "✅ Detailed logs saved to: detailed_logs.txt ($(wc -l < "$DETAILED_LOGS") lines)"
    else
        echo "ℹ️  No CalendarAlarmScheduler logs found"
        echo "No app logs found at $(date)" > "$DETAILED_LOGS"
    fi
}

# Function to start live log monitoring
start_live_monitoring() {
    echo "🔄 Starting live log monitoring..."
    echo "Press Ctrl+C to stop monitoring"
    echo "Live monitoring started at $(date)" > "$LIVE_LOGS"
    
    # Clear existing logs first
    $ADB logcat -c
    
    # Start live monitoring (will run until Ctrl+C)
    $ADB logcat -s "CalendarAlarmScheduler:*" -v time >> "$LIVE_LOGS" &
    LOGCAT_PID=$!
    
    echo "✅ Live monitoring started (PID: $LOGCAT_PID)"
    echo "Logs being saved to: live_logs.txt"
    echo "Now launch the app to capture crash logs in real-time"
    
    # Wait for Ctrl+C
    trap "kill $LOGCAT_PID 2>/dev/null || true; echo; echo '✅ Live monitoring stopped'" EXIT
    wait $LOGCAT_PID 2>/dev/null || true
}

# Function to clear old logs
clear_logs() {
    echo "🧹 Clearing old log files..."
    rm -f "$CRASH_LOGS" "$DETAILED_LOGS" "$LIVE_LOGS"
    echo "✅ Log files cleared"
}

# Main execution
case "${1:-quick}" in
    "quick")
        check_device
        collect_crash_logs
        ;;
    "detailed")
        check_device
        collect_crash_logs
        collect_detailed_logs
        ;;
    "live")
        check_device
        start_live_monitoring
        ;;
    "clear")
        clear_logs
        ;;
    "all")
        check_device
        clear_logs
        collect_crash_logs
        collect_detailed_logs
        echo "📊 Log collection complete:"
        echo "   - crash_logs.txt: Recent crashes and fatal errors"
        echo "   - detailed_logs.txt: Detailed app logs with context"
        ;;
    *)
        echo "Usage: $0 [quick|detailed|live|clear|all]"
        echo ""
        echo "Options:"
        echo "  quick     - Collect recent crash logs only (default)"
        echo "  detailed  - Collect crash + detailed app logs"
        echo "  live      - Start real-time log monitoring"
        echo "  clear     - Clear all log files"
        echo "  all       - Clear + collect all logs"
        exit 1
        ;;
esac